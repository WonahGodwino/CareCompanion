package com.carecompanion.data.repository

import android.content.Context
import android.util.Base64
import android.util.Log
import com.carecompanion.data.database.AppDatabase
import com.carecompanion.data.database.dao.ArtPharmacyDao
import com.carecompanion.data.database.dao.BiometricDao
import com.carecompanion.data.database.dao.PatientDao
import com.carecompanion.data.database.dao.SyncLogDao
import com.carecompanion.data.database.dao.ViralLoadHistoryDao
import com.carecompanion.data.database.entities.ArtPharmacy
import com.carecompanion.data.database.entities.Biometric
import com.carecompanion.data.database.entities.Patient
import com.carecompanion.data.database.entities.SyncLog
import com.carecompanion.data.database.entities.ViralLoadHistory
import com.carecompanion.data.network.WincoApiService
import com.carecompanion.data.network.models.WincoClientDetail
import com.carecompanion.data.network.models.WincoClientItem
import com.carecompanion.data.network.models.WincoPharmacyVisit
import com.carecompanion.data.network.models.WincoViralLoadHistoryItem
import com.carecompanion.utils.DateUtils
import com.carecompanion.utils.NetworkUtils
import com.carecompanion.utils.SharedPreferencesHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import java.security.MessageDigest
import java.util.Date
import javax.inject.Inject
import retrofit2.HttpException


class SyncRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val wincoApiService: WincoApiService,
    private val db: AppDatabase,
    private val patientDao: PatientDao,
    private val biometricDao: BiometricDao,
    private val artPharmacyDao: ArtPharmacyDao,
    private val syncLogDao: SyncLogDao,
    private val viralLoadHistoryDao: ViralLoadHistoryDao,
) : SyncRepository {

    override suspend fun findPatientByBiometric(
        capturedTemplate: ByteArray,
        facilityId: Long?
    ): PatientMatchResult? {
        val capturedHash = sha256Hex(capturedTemplate)
        val hashMatch = biometricDao.findByTemplateHash(capturedHash, facilityId)
        if (hashMatch != null) {
            Log.d(TAG, "Hash match found for patient: ${hashMatch.personUuid}")
            return PatientMatchResult(
                patient = patientDao.getByUuid(hashMatch.personUuid),
                template = hashMatch,
                matchType = MatchType.HASH,
                confidence = 1.0
            )
        }
        val allBiometrics = biometricDao.getAllByFacility(facilityId)
        var bestMatch: Biometric? = null
        var bestScore = 0.0
        for (bio in allBiometrics) {
            val score = compareTemplates(capturedTemplate, bio.template)
            if (score > bestScore && score > MATCH_THRESHOLD) {
                bestScore = score
                bestMatch = bio
            }
        }
        return if (bestMatch != null) {
            PatientMatchResult(
                patient = patientDao.getByUuid(bestMatch.personUuid),
                template = bestMatch,
                matchType = MatchType.TEMPLATE,
                confidence = bestScore
            )
        } else {
            null
        }
    }

    override suspend fun findPatientByBiometricForVerification(
        capturedTemplate: ByteArray,
        personUuid: String,
        fingerType: String,
        facilityId: Long?
    ): PatientMatchResult? {
        val capturedHash = sha256Hex(capturedTemplate)
        val hashMatch = biometricDao.findByTemplateHashForPersonAndFinger(capturedHash, personUuid, fingerType, facilityId)
        if (hashMatch != null) {
            Log.d(TAG, "Hash match found for verification: ${hashMatch.personUuid} $fingerType")
            return PatientMatchResult(
                patient = patientDao.getByUuid(hashMatch.personUuid),
                template = hashMatch,
                matchType = MatchType.HASH,
                confidence = 1.0
            )
        }
        val fingerBiometrics = biometricDao.getAllByPersonAndFinger(personUuid, fingerType, facilityId)
        var bestMatch: Biometric? = null
        var bestScore = 0.0
        for (bio in fingerBiometrics) {
            val score = compareTemplates(capturedTemplate, bio.template)
            if (score > bestScore && score > MATCH_THRESHOLD) {
                bestScore = score
                bestMatch = bio
            }
        }
        return if (bestMatch != null) {
            PatientMatchResult(
                patient = patientDao.getByUuid(bestMatch.personUuid),
                template = bestMatch,
                matchType = MatchType.TEMPLATE,
                confidence = bestScore
            )
        } else {
            null
        }
    }

    private fun compareTemplates(template1: ByteArray, template2: ByteArray): Double {
        if (template1.contentEquals(template2)) {
            return 1.0
        }
        val blockSize = 64
        val blocks1 = template1.toList().chunked(blockSize).map { it.hashCode() }.toSet()
        val blocks2 = template2.toList().chunked(blockSize).map { it.hashCode() }.toSet()
        val intersection = blocks1.intersect(blocks2).size
        val union = blocks1.union(blocks2).size
        return if (union > 0) intersection.toDouble() / union.toDouble() else 0.0
    }

    data class PatientMatchResult(
        val patient: Patient?,
        val template: Biometric?,
        val matchType: MatchType,
        val confidence: Double
    )

    enum class MatchType {
        HASH,
        TEMPLATE,
        FALLBACK
    }

    companion object {
        private const val TAG = "SyncRepository"
        private const val PAGE_LIMIT = 50
        private const val PAGE_SIZE = 100
        // Keep a small pacing delay only to avoid overwhelming slower servers.
        private const val BIOMETRIC_REQUEST_DELAY_MS = 50L
        private const val BIOMETRIC_PARALLELISM = 4
        private const val DETAIL_PARALLELISM = 4
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 1000L
        private const val MIN_TEMPLATE_BYTES = 128
        private const val MIN_QUALITY_THRESHOLD = 40
        private const val MATCH_THRESHOLD = 0.8
        fun sha256Hex(bytes: ByteArray): String {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(bytes)
            return digest.joinToString("") { "%02x".format(it) }
        }
    }

    // ... other methods ...

    override suspend fun syncAll(onProgress: ((String) -> Unit)?): SyncResult {
        if (!NetworkUtils.isNetworkAvailable(context)) {
            Log.e(TAG, "No network available")
            return SyncResult.NoNetwork
        }
        
        if (!SharedPreferencesHelper.isWincoConfigured(context)) {
            Log.e(TAG, "WINCO not configured")
            return SyncResult.NotConfigured
        }
        
        // Align with dashboard summary (which is all-facility by default) so pull counts
        // match what users see on the home KPI.
        val facilityId: Long? = null
        val includeTxMl = SharedPreferencesHelper.isTxMlIncludeEnabled(context)
        val txMlStartDate = SharedPreferencesHelper.getTxMlStartDate(context).trim().ifBlank { null }
        val txMlEndDate = SharedPreferencesHelper.getTxMlEndDate(context).trim().ifBlank { null }
        val txMlEnabledForRequest = includeTxMl && txMlStartDate != null && txMlEndDate != null

        // Use WINCO's calculated care categories (ACTIVE, IIT, etc.), NOT EMR statuses
        val careCategoriesToSync = if (txMlEnabledForRequest) {
            "ACTIVE,IIT,TRANSFER_OUT,DEATH,STOPPED_TREATMENT,OTHER_INACTIVE"
        } else {
            "ACTIVE"
        }

        if (includeTxMl && !txMlEnabledForRequest) {
            onProgress?.invoke("TX_ML filter enabled but date range is incomplete; syncing ACTIVE only.")
        }

        return try {
            var patientsAdded = 0
            var biometricsAdded = 0
            var pharmacyAdded = 0
            var invalidBiometricsSkipped = 0
            var totalBiometricSkipped = 0
            var totalBiometricFailed = 0

            val detailCache = mutableMapOf<String, WincoClientDetail?>()
            // Always run a full pass from page 1 to avoid stale offset resumes causing
            // partial pulls (e.g., only the tail page after a previously interrupted run).
            var pageOffset = 0
            SharedPreferencesHelper.setSyncPage(context, 0)
            val patientEntities = mutableListOf<Patient>()
            val biometricCandidates = mutableListOf<Pair<String, Int>>()
            val seenPersonUuids = mutableSetOf<String>()
            var pagesThisRun = 0

            onProgress?.invoke("Phase 1: Syncing patient data...")

            while (pagesThisRun < PAGE_LIMIT) {
                val wincoPageNumber = pageOffset + 1
                Log.d(TAG, "Fetching page $wincoPageNumber with $PAGE_SIZE items per page")
                
                val pageResp = retryWithBackoff {
                    wincoApiService.getTxCurrClients(
                        facilityId = facilityId,
                        page = wincoPageNumber,
                        perPage = PAGE_SIZE,
                        txCurrOnly = false,
                        applyCareFilter = true,
                        careCategories = careCategoriesToSync,
                        includeTxMl = txMlEnabledForRequest,
                        txMlStartDate = txMlStartDate,
                        txMlEndDate = txMlEndDate,
                    )
                }

                val pagePatients = pageResp.items
                if (pagePatients.isEmpty()) {
                    Log.d(TAG, "No more patients on page $wincoPageNumber")
                    SharedPreferencesHelper.setSyncPage(context, 0)
                    break
                }

                val eligiblePagePatients = pagePatients
                    .filter { it.isEnrollmentEligibleForPull(detailCache) }
                    .filter { seenPersonUuids.add(it.personUuid) }
                val enrichedPagePatients = eligiblePagePatients.map { it.withDemographyFromPatientTableIfNeeded(detailCache) }

                ensureClientDetails(enrichedPagePatients.map { it.personUuid }, detailCache)

                val patients = enrichedPagePatients.map { item ->
                    val detail = detailCache[item.personUuid]
                    item.toPatient(
                        defaultFacilityId = facilityId ?: 0L,
                        lastViralLoadDate = detail?.viralLoad?.lastResultDate,
                        lastViralLoadResult = detail?.viralLoad?.lastResultValue,
                        lastViralLoadResultRaw = detail?.viralLoad?.rawResult,
                        ndrMatchedStatus = detail?.ndrMatch?.matchOutcome,
                        lastTbScreeningDate = detail?.tbScreening?.date,
                        lastTbScreeningStatus = detail?.tbScreening?.status,
                    )
                }

                val pageViralLoadHistory = enrichedPagePatients.flatMap { item ->
                    (detailCache[item.personUuid]?.viralLoadHistory ?: emptyList())
                        .mapNotNull { it.toViralLoadHistory(item.personUuid) }
                }

                val pharmacySnapshots = enrichedPagePatients.mapNotNull { it.toLatestArtPharmacy() }
                // Room withTransaction must be called from a suspend context; this is already a suspend function
                // so we can safely call suspend DAO methods directly
                patientDao.insertAll(patients)

                val pagePersonUuids = enrichedPagePatients.map { it.personUuid }.distinct()
                pagePersonUuids.forEach { personUuid ->
                    viralLoadHistoryDao.deleteByPersonUuid(personUuid)
                }
                if (pageViralLoadHistory.isNotEmpty()) {
                    viralLoadHistoryDao.insertAll(pageViralLoadHistory)
                }

                if (pharmacySnapshots.isNotEmpty()) {
                    artPharmacyDao.insertAll(pharmacySnapshots)
                    pharmacyAdded += pharmacySnapshots.size
                }
                patientEntities.addAll(patients)

                enrichedPagePatients.forEach { item ->
                    // biometric_count can lag or be zero despite available templates;
                    // keep candidates when status hints exist so we don't miss template pulls.
                    if (item.biometricCount > 0 || !item.latestBiometricStatus.isNullOrBlank()) {
                        biometricCandidates.add(Pair(item.personUuid, item.biometricCount))
                    }
                }

                patientsAdded += patients.size
                pageOffset++
                pagesThisRun++
                SharedPreferencesHelper.setSyncPage(context, pageOffset)

                val synced = pageOffset * PAGE_SIZE
                onProgress?.invoke("Phase 1: Syncing patients... $synced+")

                // Do not infer the last page from item count. WINCO pages may contain
                // fewer than requested rows due server-side joins/dedup while additional
                // pages still exist. Trust the explicit `pages` metadata from the API.
                if (wincoPageNumber >= pageResp.pages) {
                    SharedPreferencesHelper.setSyncPage(context, 0)
                    break
                }
            }

            onProgress?.invoke("Phase 1 complete: $patientsAdded patients synced")

            if (biometricCandidates.isEmpty() && patientEntities.isNotEmpty()) {
                // Last-resort fallback: attempt biometric pull for synced clients even when
                // list endpoint did not advertise biometric_count.
                patientEntities.forEach { p -> biometricCandidates.add(Pair(p.uuid, 0)) }
            }

            if (biometricCandidates.isNotEmpty()) {
                onProgress?.invoke("Phase 2: Syncing biometrics for ${biometricCandidates.size} clients...")

                val biometricBatch = biometricCandidates.distinctBy { it.first }
                Log.d(TAG, "Biometric batch size: ${biometricBatch.size}")

                data class DownloadOutcome(
                    val personUuid: String,
                    val patient: Patient?,
                    val response: com.carecompanion.data.network.models.WincoBiometricResponse?,
                    val httpCode: Int? = null,
                )

                var consecutiveServerErrors = 0
                var processedCount = 0
                var stopBiometricPhase = false

                for (chunk in biometricBatch.chunked(BIOMETRIC_PARALLELISM)) {
                    val outcomes = coroutineScope {
                        chunk.map { candidate ->
                            async {
                                val personUuid = candidate.first
                                val patient = findPatientByUuid(personUuid, patientEntities)
                                if (patient == null) {
                                    DownloadOutcome(personUuid, null, null)
                                } else {
                                    try {
                                        val resp = downloadBiometricWithRetry(personUuid)
                                        DownloadOutcome(personUuid, patient, resp)
                                    } catch (e: HttpException) {
                                        DownloadOutcome(personUuid, patient, null, e.code())
                                    }
                                }
                            }
                        }.awaitAll()
                    }

                    for (outcome in outcomes) {
                        processedCount++
                        val personUuid = outcome.personUuid
                        val patient = outcome.patient

                        if (patient != null) {
                            onProgress?.invoke("Phase 2: $processedCount/${biometricBatch.size} - ${patient.fullName ?: patient.uuid}")
                        }

                        if (outcome.httpCode == 404) {
                            Log.d(TAG, "No biometrics found for $personUuid (404)")
                            continue
                        }
                        if (outcome.httpCode != null) {
                            if (outcome.httpCode in 500..599) {
                                consecutiveServerErrors++
                                Log.w(TAG, "Server error ${outcome.httpCode} for $personUuid, consecutive errors: $consecutiveServerErrors")
                                if (consecutiveServerErrors >= 3) {
                                    onProgress?.invoke("Biometric sync paused: WINCO/EMR server unstable (HTTP ${outcome.httpCode}).")
                                    stopBiometricPhase = true
                                    break
                                }
                            } else {
                                Log.e(TAG, "HTTP error ${outcome.httpCode} for $personUuid")
                            }
                            continue
                        }

                        val resp = outcome.response
                        if (patient == null || resp == null) {
                            continue
                        }

                        val entries = (resp.capturedBiometricsList ?: emptyList()) + (resp.capturedBiometricsList2 ?: emptyList())

                        if (entries.isEmpty()) {
                            Log.d(TAG, "No biometric entries for $personUuid")
                            consecutiveServerErrors = 0
                            continue
                        }

                        // Performance optimization: avoid per-candidate detail calls during
                        // biometric phase. This call is expensive at scale and was a major
                        // contributor to slow sync runs. Use already-cached detail when present.
                        val detail = detailCache[personUuid]

                        val biometricItems = (detail?.biometric?.items ?: emptyList())
                            .filter { (it.archived ?: 0) == 0 }
                        val patientFacilityId = detail?.patient?.facilityId ?: patient.facilityId

                        val biometricsByType = biometricItems.groupBy { it.biometricType?.uppercase() }
                        val consumedIndex = mutableMapOf<String?, Int>()
                        val biometrics = mutableListOf<Biometric>()
                        var savedCount = 0
                        var failedCount = 0
                        var skippedCount = 0

                        for ((entryIdx, e) in entries.withIndex()) {
                            try {
                                val tpl = e.template
                                if (tpl == null) {
                                    Log.w(TAG, "Skipping biometric $entryIdx for $personUuid: null template")
                                    skippedCount++
                                    continue
                                }

                                if (isMemoryReferenceTemplate(tpl)) {
                                    Log.w(TAG, "Skipping memory reference/placeholder template for $personUuid")
                                    skippedCount++
                                    invalidBiometricsSkipped++
                                    continue
                                }

                                val recapture = (e.recapture ?: 0).coerceAtLeast(0)
                                val typeKey = e.templateType?.replace(" ", "_") ?: "UNKNOWN"

                                val templateBytes = try {
                                    Base64.decode(tpl, Base64.DEFAULT)
                                } catch (ex: Exception) {
                                    Log.e(TAG, "Failed to decode template for $personUuid", ex)
                                    failedCount++
                                    continue
                                }

                                if (templateBytes.size < MIN_TEMPLATE_BYTES) {
                                    Log.w(TAG, "Template too small: ${templateBytes.size} bytes for $personUuid")
                                    skippedCount++
                                    invalidBiometricsSkipped++
                                    continue
                                }

                                val imageQuality = e.imageQuality ?: 0
                                if (imageQuality > 0 && imageQuality < MIN_QUALITY_THRESHOLD) {
                                    Log.w(TAG, "Low quality template ($imageQuality) for $personUuid - skipping")
                                    skippedCount++
                                    continue
                                }

                                val hash = sha256Hex(templateBytes)

                                val meta = when {
                                    e.sourceId != null -> biometricItems.firstOrNull { it.id == e.sourceId.toString() }
                                    else -> {
                                        val matchKey = e.templateType?.uppercase()
                                        val metaCandidates = biometricsByType[matchKey] ?: emptyList()
                                        val nextIndex = (consumedIndex[matchKey] ?: -1) + 1
                                        if (nextIndex < metaCandidates.size) {
                                            consumedIndex[matchKey] = nextIndex
                                            metaCandidates[nextIndex]
                                        } else {
                                            if (entryIdx < biometricItems.size) biometricItems[entryIdx] else null
                                        }
                                    }
                                }

                                val biometricId = if (e.sourceId != null) {
                                    e.sourceId.toString()
                                } else {
                                    "${patient.uuid}_${typeKey}_$hash"
                                }

                                val biometric = Biometric(
                                    id = biometricId,
                                    personUuid = patient.uuid,
                                    template = templateBytes,
                                    biometricType = meta?.biometricType ?: e.biometricType ?: "FINGERPRINT",
                                    templateType = e.templateType,
                                    recapture = recapture,
                                    enrollmentDate = DateUtils.parseDate(meta?.enrollmentDate ?: e.enrollmentDate),
                                    deviceName = meta?.deviceName ?: e.biometricType,
                                    imageQuality = e.imageQuality,
                                    iso = meta?.iso ?: e.iso ?: false,
                                    versionIso20 = false,
                                    lastSyncDate = Date(),
                                    archived = e.archived ?: 0,
                                    facilityId = patientFacilityId,
                                    hashed = hash,
                                    sourceId = e.sourceId?.toString()
                                )

                                biometrics.add(biometric)
                                savedCount++

                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to process biometric $entryIdx for $personUuid", e)
                                failedCount++
                            }
                        }

                        Log.d(TAG, "Biometric processing for $personUuid: saved=$savedCount, skipped=$skippedCount, failed=$failedCount")
                        totalBiometricSkipped += skippedCount
                        totalBiometricFailed += failedCount

                        val pharmacyRecords = detail?.pharmacyVisits
                            ?.mapNotNull { it.toArtPharmacy(personUuid) }
                            ?: emptyList()

                        if (biometrics.isNotEmpty()) {
                            biometricDao.insertAll(biometrics)
                            biometricsAdded += biometrics.size
                            Log.d(TAG, "Saved ${biometrics.size} biometrics for ${patient.uuid}")
                        }
                        if (pharmacyRecords.isNotEmpty()) {
                            artPharmacyDao.insertAll(pharmacyRecords)
                            pharmacyAdded += pharmacyRecords.size
                        }

                        consecutiveServerErrors = 0
                    }

                    if (stopBiometricPhase) break
                    if (BIOMETRIC_REQUEST_DELAY_MS > 0) {
                        delay(BIOMETRIC_REQUEST_DELAY_MS)
                    }
                }
            } else {
                onProgress?.invoke("No biometric candidates found")
            }

            val now = DateUtils.formatIso8601(Date())
            SharedPreferencesHelper.setLastSyncDate(context, now)
            syncLogDao.insert(SyncLog(
                tableName = "all", 
                lastSyncedRecordId = now, 
                syncDate = Date(), 
                status = "SUCCESS"
            ))
            
            onProgress?.invoke("Sync complete: $patientsAdded patients, $biometricsAdded biometrics, $pharmacyAdded pharmacy records")
            onProgress?.invoke("Skipped $invalidBiometricsSkipped invalid/low-quality templates")

            val biometricBatchSize = biometricCandidates.distinctBy { it.first }.size
            val audit = SyncAudit(
                pagesRead = pagesThisRun,
                uniquePatientsSaved = patientsAdded,
                biometricCandidates = biometricBatchSize,
                biometricsSaved = biometricsAdded,
                biometricsSkipped = totalBiometricSkipped,
                biometricsFailed = totalBiometricFailed,
            )

            SyncResult.Success(patientsAdded, biometricsAdded, audit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            syncLogDao.insert(SyncLog(
                tableName = "all", 
                lastSyncedRecordId = "", 
                syncDate = Date(), 
                status = "ERROR: ${e.message}"
            ))
            SyncResult.Error(e.message ?: "Unknown error")
        }
    }

    private fun isMemoryReferenceTemplate(template: String?): Boolean {
        if (template == null) return true
        val memoryPattern = Regex("^<memory at 0x[0-9a-fA-F]+>$")
        val placeholderPattern = Regex("^binary data$", RegexOption.IGNORE_CASE)
        return memoryPattern.matches(template) || placeholderPattern.matches(template)
    }

    override suspend fun getLastSyncInfo(): String? = SharedPreferencesHelper.getLastSyncDate(context)
    override fun resetSyncPage() = SharedPreferencesHelper.setSyncPage(context, 0)

    private suspend fun findPatientByUuid(personUuid: String, patientEntities: List<Patient>): Patient? {
        Log.d(TAG, "Looking for patient with UUID: $personUuid")
        
        patientEntities.firstOrNull { it.uuid == personUuid }?.let { 
            Log.d(TAG, "Found patient by direct UUID match: ${it.uuid}")
            return it 
        }
        
        patientDao.getByUuid(personUuid)?.let { 
            Log.d(TAG, "Found patient in DB by UUID: ${it.uuid}")
            return it 
        }
        
        try {
            val detail = wincoApiService.getClientDetail(personUuid)
            val enrollmentUuid = detail.enrollment?.uuid
            if (enrollmentUuid != null) {
                patientEntities.firstOrNull { it.emrId == enrollmentUuid }?.let { 
                    Log.d(TAG, "Found patient by enrollment UUID: ${it.uuid}")
                    return it 
                }
                patientDao.getByEmrId(enrollmentUuid)?.let { 
                    Log.d(TAG, "Found patient in DB by enrollment UUID: ${it.uuid}")
                    return it 
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch detail for patient lookup: $personUuid", e)
        }
        
        patientDao.getByPersonUuid(personUuid)?.let {
            Log.d(TAG, "Found patient by person_uuid: ${it.uuid}")
            return it
        }
        
        Log.w(TAG, "No patient found for UUID: $personUuid")
        return null
    }

    private suspend fun downloadBiometricWithRetry(personUuid: String): com.carecompanion.data.network.models.WincoBiometricResponse? {
        repeat(MAX_RETRIES) { attempt ->
            try {
                return wincoApiService.getBiometricTemplates(personUuid)
            } catch (e: HttpException) {
                if (e.code() == 404) {
                    return null
                }
                if (attempt == MAX_RETRIES - 1) throw e
                Log.w(TAG, "Retry ${attempt + 1}/$MAX_RETRIES for $personUuid after error: ${e.message}")
                delay(RETRY_DELAY_MS * (attempt + 1))
            } catch (e: Exception) {
                if (attempt == MAX_RETRIES - 1) throw e
                delay(RETRY_DELAY_MS * (attempt + 1))
            }
        }
        return null
    }

    private suspend fun <T> retryWithBackoff(
        maxAttempts: Int = 3,
        initialDelayMs: Long = 500L,
        factor: Double = 2.0,
        maxDelayMs: Long = 8000L,
        block: suspend () -> T,
    ): T {
        var currentDelay = initialDelayMs
        repeat(maxAttempts - 1) {
            try {
                return block()
            } catch (e: Exception) {
                if (e is HttpException && e.code() in 400..499 && e.code() != 429) throw e
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelayMs)
            }
        }
        return block()
    }

    /**
     * Returns true if this WINCO care category or raw EMR status should be pulled
     * into the local Care Companion database.
     *
     * The WINCO server now returns pre-computed care_category values (ACTIVE, IIT,
     * TRANSFER_OUT, DEATH, STOPPED_TREATMENT, OTHER_INACTIVE). All non-archived
     * ART clients returned by the server are eligible — filtering is done server-side
     * via the applyCareFilter / careCategories parameters. This helper is retained for
     * any offline or legacy re-classification paths.
     */
    private fun isMobilePullStatus(status: String?): Boolean {
        val s = status?.trim()?.uppercase()?.replace(" ", "_")?.replace("-", "_") ?: return false
        return s in setOf(
            // WINCO care categories (server-computed)
            "ACTIVE", "ACTIVE_TX_CURR",
            "IIT", "INTERRUPTED_IN_TREATMENT", "LTFU", "LOST_TO_FOLLOW_UP",
            "TRANSFER_OUT", "ART_TRANSFER_OUT",
            "DEATH", "DIED", "DEAD",
            "STOPPED_TREATMENT", "TREATMENT_STOPPED", "ART_STOP",
            "OTHER_INACTIVE",
            // Raw EMR statuses (legacy / direct sync path)
            "ART", "ART_START", "ART_TRANSFER_IN",
        )
    }

    private fun WincoClientItem.toPatient(
        defaultFacilityId: Long,
        lastViralLoadDate: String? = null,
        lastViralLoadResult: Long? = null,
        lastViralLoadResultRaw: String? = null,
        ndrMatchedStatus: String? = null,
        lastTbScreeningDate: String? = null,
        lastTbScreeningStatus: String? = null,
    ): Patient {
        val effectiveStatus = careCategory?.takeIf { it.isNotBlank() } ?: latestHivStatus
        val effectiveFullName = patientName?.takeIf { it.isNotBlank() }
            ?: listOfNotNull(firstName, middleName, lastName)
                .joinToString(" ")
                .takeIf { it.isNotBlank() }
        val isArchived = (archived ?: 0) != 0
        val isActivePatient = !isArchived
        return Patient(
            uuid = personUuid,
            personUuid = personUuid,
            emrPatientId = 0L,
            createdDate = null,
            createdBy = null,
            lastModifiedDate = null,
            lastModifiedBy = null,
            active = isActivePatient,
            contactPoint = null,
            address = null,
            gender = gender,
            identifier = null,
            deceased = null,
            deceasedDateTime = null,
            maritalStatus = null,
            employmentStatus = null,
            education = null,
            organization = null,
            contact = null,
            hospitalNumber = hospitalNumber ?: uniqueId ?: "",
            firstName = firstName,
            surname = lastName,
            otherName = middleName,
            fullName = effectiveFullName,
            sex = gender,
            dateOfBirth = DateUtils.parseDate(dateOfBirth),
            dateOfRegistration = DateUtils.parseDate(dateOfRegistration),
            archived = archived ?: 0,
            isDateOfBirthEstimated = false,
            ninNumber = null,
            emrId = enrollmentUuid,
            phoneNumber = phoneNumber,
            caseManagerId = null,
            reason = null,
            latitude = null,
            longitude = null,
            source = "WINCO",
            currentStatus = effectiveStatus,
            currentStatusDate = DateUtils.parseDate(latestHivStatusDate),
            artStartDate = DateUtils.parseDate(dateOfRegistration),
            lastViralLoadDate = DateUtils.parseDate(lastViralLoadDate),
            lastViralLoadResult = lastViralLoadResult,
            lastViralLoadResultRaw = lastViralLoadResultRaw,
            ndrMatchedStatus = ndrMatchedStatus,
            lastTbScreeningDate = DateUtils.parseDate(lastTbScreeningDate),
            lastTbScreeningStatus = lastTbScreeningStatus,
            facilityId = facilityId ?: defaultFacilityId,
            lastSyncDate = Date(),
            isActive = isActivePatient
        )
    }

    private suspend fun WincoClientItem.withDemographyFromPatientTableIfNeeded(
        detailCache: MutableMap<String, WincoClientDetail?>
    ): WincoClientItem {
        if (!needsDemographyEnrichment()) return this

        return try {
            val detail = retryWithBackoff { wincoApiService.getClientDetail(personUuid) }
                .also { detailCache[personUuid] = it }
            val enrollment = detail.enrollment
            if (!detail.isArtClient || enrollment == null || (enrollment.archived ?: 0) != 0) {
                return this
            }

            val patient = detail.patient ?: return this
            val fullNameFromDetail = patient.fullName?.takeIf { it.isNotBlank() }
                ?: listOfNotNull(patient.firstName, patient.middleName, patient.lastName)
                    .joinToString(" ")
                    .takeIf { it.isNotBlank() }

            copy(
                firstName = patient.firstName ?: firstName,
                middleName = patient.middleName ?: middleName,
                lastName = patient.lastName ?: lastName,
                patientName = fullNameFromDetail ?: patientName,
                hospitalNumber = patient.hospitalNumber ?: hospitalNumber,
                gender = patient.gender ?: patient.sex ?: gender,
                dateOfBirth = patient.dateOfBirth ?: dateOfBirth,
                phoneNumber = patient.phoneNumber ?: phoneNumber,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to enrich demographics for $personUuid", e)
            this
        }
    }

    private fun WincoClientItem.needsDemographyEnrichment(): Boolean {
        val hasName = !patientName.isNullOrBlank() || !firstName.isNullOrBlank() || !lastName.isNullOrBlank()
        return !hasName || gender.isNullOrBlank() || dateOfBirth.isNullOrBlank() || phoneNumber.isNullOrBlank()
    }

    private suspend fun WincoClientItem.isEnrollmentEligibleForPull(
        detailCache: MutableMap<String, WincoClientDetail?>
    ): Boolean {
        if ((archived ?: 0) != 0) return false
        if (!enrollmentUuid.isNullOrBlank()) return true

        val detail = detailCache[personUuid] ?: try {
            retryWithBackoff { wincoApiService.getClientDetail(personUuid) }
        } catch (e: Exception) {
            null
        }
        if (detail != null) detailCache[personUuid] = detail

        val enrollment = detail?.enrollment
        return detail?.isArtClient == true && enrollment != null && (enrollment.archived ?: 0) == 0
    }

    private suspend fun ensureClientDetails(
        personUuids: List<String>,
        detailCache: MutableMap<String, WincoClientDetail?>,
    ) {
        val missing = personUuids.distinct().filter { !detailCache.containsKey(it) }
        if (missing.isEmpty()) return

        for (chunk in missing.chunked(DETAIL_PARALLELISM)) {
            coroutineScope {
                chunk.map { personUuid ->
                    async {
                        val detail = try {
                            retryWithBackoff { wincoApiService.getClientDetail(personUuid) }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to prefetch client detail for $personUuid", e)
                            null
                        }
                        detailCache[personUuid] = detail
                    }
                }.awaitAll()
            }
        }
    }

    private fun WincoViralLoadHistoryItem.toViralLoadHistory(personUuid: String): ViralLoadHistory? {
        val resolvedTestId = testId ?: return null
        return ViralLoadHistory(
            personUuid = personUuid,
            testId = resolvedTestId,
            sampleTypeId = sampleTypeId,
            sampleNumber = sampleNumber,
            resultRaw = resultRaw,
            resultNumeric = resultNumeric,
            resultDate = DateUtils.parseDate(dateResultReported),
            assayedDate = DateUtils.parseDate(dateAssayed),
            sampleDate = DateUtils.parseDate(dateSampleCollected),
            sourceId = sourceId,
            source = source,
            lastSyncDate = Date(),
        )
    }

    private fun sha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        return buildString(digest.size * 2) {
            digest.forEach { append("%02x".format(it)) }
        }
    }

    private fun pharmacyId(personUuid: String, visitDate: String): Long {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest("$personUuid:$visitDate".toByteArray(Charsets.UTF_8))
        return java.nio.ByteBuffer.wrap(bytes).getLong()
    }

    private fun WincoClientItem.toLatestArtPharmacy(): ArtPharmacy? {
        val vd = latestPharmacyVisitDate ?: return null
        val visitDate = DateUtils.parseDate(vd) ?: return null
        return ArtPharmacy(
            id = pharmacyId(personUuid, vd),
            personUuid = personUuid,
            visitDate = visitDate,
            nextAppointment = DateUtils.parseDate(latestPharmacyNextAppointment),
            regimenId = null,
            mmdType = latestPharmacyMmdType,
            refillPeriod = latestPharmacyRefillPeriod,
            dsdModel = null,
            adherence = null,
            lastSyncDate = Date(),
            sourceId = latestPharmacySourceId,
            source = "WINCO"
        )
    }

    private fun WincoPharmacyVisit.toArtPharmacy(personUuid: String): ArtPharmacy? {
        val vd = visitDate ?: return null
        val visitDateParsed = DateUtils.parseDate(vd) ?: return null
        return ArtPharmacy(
            id = pharmacyId(personUuid, vd),
            personUuid = personUuid,
            visitDate = visitDateParsed,
            nextAppointment = DateUtils.parseDate(nextAppointment),
            regimenId = null,
            mmdType = mmdType,
            refillPeriod = refillPeriod,
            dsdModel = dsdModel,
            adherence = adherence,
            lastSyncDate = Date(),
            dsdModelType = dsdModelType,
            refillType = refillType,
            ardScreened = ardScreened,
            prescriptionError = prescriptionError,
            deliveryPoint = deliveryPoint,
            iptType = iptType,
            ipt = ipt,
            isDevolve = isDevolve,
            latitude = latitude,
            longitude = longitude,
            sourceId = sourceId,
            source = "WINCO"
        )
    }
}