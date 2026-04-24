package com.carecompanion.data.repository

import android.content.Context
import com.carecompanion.data.database.dao.ArtPharmacyDao
import com.carecompanion.data.database.dao.BiometricDao
import com.carecompanion.data.database.dao.PatientDao
import com.carecompanion.data.database.dao.SyncLogDao
import com.carecompanion.data.database.entities.Biometric
import com.carecompanion.data.database.entities.Patient
import com.carecompanion.data.database.entities.SyncLog
import com.carecompanion.data.network.WincoApiService
import com.carecompanion.data.network.models.WincoClientItem
import com.carecompanion.utils.DateUtils
import com.carecompanion.utils.NetworkUtils
import com.carecompanion.utils.SharedPreferencesHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.security.MessageDigest
import javax.inject.Inject
import kotlinx.coroutines.delay
import retrofit2.HttpException

class SyncRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val wincoApiService: WincoApiService,
    private val patientDao: PatientDao,
    private val biometricDao: BiometricDao,
    private val artPharmacyDao: ArtPharmacyDao,
    private val syncLogDao: SyncLogDao
) : SyncRepository {

    override suspend fun syncAll(onProgress: ((String) -> Unit)?): SyncResult {
        if (!NetworkUtils.isNetworkAvailable(context)) return SyncResult.NoNetwork
        if (!SharedPreferencesHelper.isWincoConfigured(context)) return SyncResult.NotConfigured
        val facilityId = SharedPreferencesHelper.getFacilityId(context).takeIf { it > 0L }

        val PAGE_LIMIT = 50 // max pages per sync run
        val PAGE_SIZE = 100

        return try {
            val BIOMETRIC_REQUEST_DELAY_MS = 200L   // WINCO is on the local network; 200 ms is sufficient
            var patientsAdded = 0
            var biometricsAdded = 0

            // Resume from saved page offset for incremental sync.
            // Local page offset is 0-based; WINCO page parameter is 1-based.
            var pageOffset = SharedPreferencesHelper.getSyncPage(context)
            val patientEntities = mutableListOf<Patient>()
            // Carry remote biometric_count alongside person_uuid so we can decide
            // whether to (re-)sync biometrics even when some already exist locally.
            val biometricCandidates = mutableListOf<Pair<String, Int>>()  // (personUuid, remoteCount)
            var pagesThisRun = 0

            while (pagesThisRun < PAGE_LIMIT) {
                val wincoPageNumber = pageOffset + 1
                val pageResp = wincoApiService.getTxCurrClients(
                    facilityId = facilityId,
                    page = wincoPageNumber,
                    perPage = PAGE_SIZE,
                    txCurrOnly = true,
                )

                val pagePatients = pageResp.items
                if (pagePatients.isEmpty()) {
                    SharedPreferencesHelper.setSyncPage(context, 0)
                    break
                }

                val patients = pagePatients.map { it.toPatient(defaultFacilityId = facilityId ?: 0L) }
                patientDao.insertAll(patients)
                patientEntities.addAll(patients)

                pagePatients.forEach { item ->
                    if (item.biometricCount > 0 && isTxCurrArtStatus(item.latestHivStatus)) {
                        biometricCandidates.add(Pair(item.personUuid, item.biometricCount))
                    }
                }

                patientsAdded += patients.size
                pageOffset++
                pagesThisRun++
                SharedPreferencesHelper.setSyncPage(context, pageOffset)

                val synced = pageOffset * PAGE_SIZE
                onProgress?.invoke("Syncing patients... $synced+")

                if (wincoPageNumber >= pageResp.pages || pagePatients.size < PAGE_SIZE) {
                    SharedPreferencesHelper.setSyncPage(context, 0)
                    break
                }
            }

            onProgress?.invoke("Syncing biometrics for TX_CURR clients...")
            val biometricBatch = biometricCandidates
                .distinctBy { it.first }
                .filter { (personUuid, remoteCount) ->
                    val patient = patientEntities.firstOrNull { it.uuid == personUuid }
                        ?: patientDao.getByUuid(personUuid)
                    // Sync if patient exists AND local count is behind the WINCO-reported count
                    // (covers first-time sync AND patients who get new fingers enrolled in EMR)
                    patient != null && biometricDao.countByPersonUuid(personUuid) < remoteCount
                }

            var consecutiveServerErrors = 0
            for ((idx, candidate) in biometricBatch.withIndex()) {
                val personUuid = candidate.first
                try {
                    val patient = patientEntities.firstOrNull { it.uuid == personUuid }
                        ?: patientDao.getByUuid(personUuid)
                        ?: continue

                    val resp = wincoApiService.getBiometricTemplates(personUuid)
                    val entries = (resp.capturedBiometricsList ?: emptyList()) + (resp.capturedBiometricsList2 ?: emptyList())
                    val biometrics = entries.mapNotNull { e ->
                        val tpl = e.template ?: return@mapNotNull null
                        val recapture = (e.recapture ?: 0).coerceAtLeast(0)
                        val typeKey = e.templateType?.replace(" ", "_") ?: "UNKNOWN"
                        val templateBytes = try {
                            android.util.Base64.decode(tpl, android.util.Base64.DEFAULT)
                        } catch (_: Exception) {
                            return@mapNotNull null
                        }
                        val hash = sha256Hex(templateBytes)
                        Biometric(
                            id = "${patient.uuid}_${typeKey}_$hash",
                            personUuid = patient.uuid,
                            template = templateBytes,
                            biometricType = "FINGERPRINT",
                            templateType = e.templateType,
                            recapture = recapture,
                            enrollmentDate = Date(),
                            deviceName = null,
                            imageQuality = null,
                            iso = false,
                            versionIso20 = false,
                            lastSyncDate = Date()
                        )
                    }.distinctBy { it.id }
                    biometricDao.insertAll(biometrics)
                    biometricsAdded += biometrics.size
                    consecutiveServerErrors = 0
                    onProgress?.invoke("Syncing biometrics... ${idx + 1} / ${biometricBatch.size} (WINCO clients)")
                    delay(BIOMETRIC_REQUEST_DELAY_MS)
                } catch (e: HttpException) {
                    if (e.code() in 500..599) {
                        consecutiveServerErrors++
                        if (consecutiveServerErrors >= 1) {
                            onProgress?.invoke("Biometric sync paused: WINCO/EMR server unstable (HTTP ${e.code()}).")
                            break
                        }
                    }
                } catch (_: Exception) { /* skip individual failures */ }
            }

            val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date())
            SharedPreferencesHelper.setLastSyncDate(context, now)
            syncLogDao.insert(SyncLog(tableName = "all", lastSyncedRecordId = now, syncDate = Date(), status = "SUCCESS"))
            SyncResult.Success(patientsAdded, biometricsAdded)
        } catch (e: Exception) {
            syncLogDao.insert(SyncLog(tableName = "all", lastSyncedRecordId = "", syncDate = Date(), status = "ERROR: ${e.message}"))
            SyncResult.Error(e.message ?: "Unknown error")
        }
    }

    override suspend fun getLastSyncInfo(): String? = SharedPreferencesHelper.getLastSyncDate(context)

    override fun resetSyncPage() = SharedPreferencesHelper.setSyncPage(context, 0)

    private fun isTxCurrArtStatus(status: String?): Boolean {
        val normalizedStatus = status?.trim()?.uppercase()?.replace(" ", "_")?.replace("-", "_") ?: return false
        return normalizedStatus == "ART" || normalizedStatus == "ART_START" || normalizedStatus == "ART_TRANSFER_IN"
    }

    private fun WincoClientItem.toPatient(defaultFacilityId: Long): Patient {
        return Patient(
            uuid = personUuid,
            emrPatientId = 0L,
            createdDate = null,
            createdBy = null,
            lastModifiedDate = null,
            lastModifiedBy = null,
            active = (archived ?: 0) == 0,
            contactPoint = null,
            address = null,
            gender = null,
            identifier = null,
            deceased = null,
            deceasedDateTime = null,
            maritalStatus = null,
            employmentStatus = null,
            education = null,
            organization = null,
            contact = null,
            hospitalNumber = uniqueId ?: "",
            firstName = null,
            surname = null,
            otherName = null,
            fullName = null,
            sex = null,
            dateOfBirth = null,
            dateOfRegistration = DateUtils.parseDate(dateOfRegistration),
            archived = archived ?: 0,
            isDateOfBirthEstimated = false,
            ninNumber = null,
            emrId = enrollmentUuid,
            phoneNumber = null,
            caseManagerId = null,
            reason = null,
            latitude = null,
            longitude = null,
            source = "WINCO",
            currentStatus = latestHivStatus,
            facilityId = facilityId ?: defaultFacilityId,
            lastSyncDate = Date(),
            isActive = (archived ?: 0) == 0
        )
    }

    private fun sha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        return buildString(digest.size * 2) {
            digest.forEach { append("%02x".format(it)) }
        }
    }
}