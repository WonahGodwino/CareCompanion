package com.carecompanion.data.repository

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.room.withTransaction
import com.carecompanion.biometric.BiometricTemplateNormalizer
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    companion object {
        private const val TAG = "SyncRepository"
        private const val PAGE_LIMIT = 50
        private const val PAGE_SIZE = 250
        private const val BIOMETRIC_REQUEST_DELAY_MS = 0L
        private const val BIOMETRIC_PARALLELISM = 8
        private const val DETAIL_PARALLELISM = 16
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 1000L
        private const val MIN_TEMPLATE_BYTES = 128
        private const val MIN_QUALITY_THRESHOLD = 40
        private const val BLOCK_SIZE = 64

        /**
         * Checks for biometrics missing hashes or required metadata and backfills hashes using the current hashing method.
         * Logs details of any records updated or missing required fields.
         */
        suspend fun checkAndBackfillBiometricHashes(biometricDao: com.carecompanion.data.database.dao.BiometricDao) {
            val allBiometrics = biometricDao.getAll()
            var updated = 0
            var missingMeta = 0
            for (bio in allBiometrics) {
                val missingFields = mutableListOf<String>()
                if (bio.template == null || bio.template.isEmpty()) missingFields.add("template")
                if (bio.personUuid.isNullOrBlank()) missingFields.add("personUuid")
                if (bio.biometricType.isNullOrBlank()) missingFields.add("biometricType")
                if (missingFields.isNotEmpty()) {
                    Log.e(TAG, "Biometric record ${bio.id} missing fields: ${missingFields.joinToString(", ")}")
                    missingMeta++
                    continue
                }
                val canonicalHash = sha256Hex(com.carecompanion.biometric.BiometricTemplateNormalizer.canonicalize(bio.template))
                if (bio.hashed.isNullOrBlank() || bio.hashed != canonicalHash) {
                    // Uncomment the following line if updateHashById is implemented in BiometricDao:
                    // biometricDao.updateHashById(bio.id, canonicalHash)
                    Log.i(TAG, "Backfilled hash for biometric ${bio.id} (personUuid=${bio.personUuid})")
                    updated++
                }
            }
            Log.i(TAG, "Biometric hash backfill complete. Updated: $updated, Missing metadata: $missingMeta, Total: ${allBiometrics.size}")
        }
        
        fun sha256Hex(bytes: ByteArray): String {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(bytes)
            return digest.joinToString("") { "%02x".format(it) }
        }

        fun normalizeBiometricType(value: String?): String {
            return value
                ?.trim()
                ?.replace("-", "_")
                ?.replace(" ", "_")
                ?.uppercase()
                .orEmpty()
        }
    }

    private val hashBackfillMutex = Mutex()
    @Volatile private var hashBackfillCompleted = false

    // ========== BIOMETRIC IDENTIFICATION - IMPROVED VERSION ==========
    
    suspend fun findPatientByBiometric(
        capturedTemplate: ByteArray,
        facilityId: Long?
    ): PatientMatchResult? {
        val canonicalCaptured = BiometricTemplateNormalizer.canonicalize(capturedTemplate)
        val allBiometrics = biometricDao.getAllBiometrics()
        
        if (allBiometrics.isEmpty()) {
            Log.w(TAG, "No biometrics in database")
            return null
        }
        
        Log.d(TAG, "Comparing captured template (${canonicalCaptured.size} bytes) against ${allBiometrics.size} stored templates")
        
        var bestMatch: Biometric? = null
        var bestScore = 0.0
        val matchThreshold = SharedPreferencesHelper.getMatchThreshold(context)
        
        for (bio in allBiometrics) {
            var score = compareTemplates(canonicalCaptured, bio.template)
            
            // If score is low, try with captured template normalized differently
            if (score < 0.3) {
                val normalizedStored = BiometricTemplateNormalizer.canonicalize(bio.template)
                score = compareTemplates(canonicalCaptured, normalizedStored)
                Log.d(TAG, "Re-evaluated with normalized stored: score=$score")
            }
            
            // If still low, try length-based matching
            if (score < 0.3 && canonicalCaptured.size != bio.template.size) {
                val minSize = minOf(canonicalCaptured.size, bio.template.size)
                val truncatedCaptured = canonicalCaptured.copyOf(minSize)
                val truncatedStored = bio.template.copyOf(minSize)
                score = compareTemplates(truncatedCaptured, truncatedStored)
                Log.d(TAG, "Re-evaluated with truncated templates: score=$score")
            }
            
            Log.d(TAG, "Patient ${bio.personUuid}: score=${String.format("%.2f", score)}")
            
            if (score > bestScore && score >= matchThreshold) {
                bestScore = score
                bestMatch = bio
                Log.d(TAG, "New best match: ${bio.personUuid} with score=${String.format("%.2f", score)}")
            }
        }
        
        return if (bestMatch != null) {
            val patient = patientDao.getByUuid(bestMatch.personUuid)
            Log.d(TAG, "✅ MATCH FOUND: ${patient?.fullName} (${patient?.uuid}) with confidence ${String.format("%.2f", bestScore)}")
            PatientMatchResult(
                patient = patient,
                template = bestMatch,
                matchType = MatchType.TEMPLATE,
                confidence = bestScore
            )
        } else {
            Log.w(TAG, "No match found. Best score: ${String.format("%.2f", bestScore)} < threshold $matchThreshold")
            null
        }
    }

    suspend fun findPatientByBiometricForVerification(
        capturedTemplate: ByteArray,
        personUuid: String,
        fingerType: String,
        facilityId: Long?
    ): PatientMatchResult? {
        Log.d(TAG, "=== VERIFICATION START ===")
        Log.d(TAG, "Patient UUID: $personUuid")
        Log.d(TAG, "Captured template size: ${capturedTemplate.size}")
        
        val normalizedFingerType = normalizeBiometricType(fingerType)
        val normalizedCaptured = BiometricTemplateNormalizer.canonicalize(capturedTemplate)
        val patientBiometrics = biometricDao.getAllByPersonAndFinger(personUuid, normalizedFingerType, facilityId)
        
        Log.d(TAG, "Stored biometrics for this patient/finger: ${patientBiometrics.size}")
        
        if (patientBiometrics.isEmpty()) {
            Log.e(TAG, "No biometrics found for this patient/finger")
            return null
        }
        
        var bestMatch: Biometric? = null
        var bestScore = 0.0
        val matchThreshold = SharedPreferencesHelper.getMatchThreshold(context)
        
        for ((index, bio) in patientBiometrics.withIndex()) {
            val score = compareTemplates(normalizedCaptured, bio.template)
            Log.d(TAG, "Template $index score: ${String.format("%.2f", score)}")
            
            if (score > bestScore && score >= matchThreshold) {
                bestScore = score
                bestMatch = bio
                Log.d(TAG, "New best match at index $index with score ${String.format("%.2f", score)}")
            }
        }
        
        return if (bestMatch != null) {
            Log.d(TAG, "✅ VERIFICATION SUCCESSFUL: Patient verified with confidence ${String.format("%.2f", bestScore)}")
            PatientMatchResult(
                patient = patientDao.getByUuid(bestMatch.personUuid),
                template = bestMatch,
                matchType = MatchType.TEMPLATE,
                confidence = bestScore
            )
        } else {
            Log.e(TAG, "❌ VERIFICATION FAILED - no template above threshold $matchThreshold")
            null
        }
    }

    // ========== IMPROVED TEMPLATE COMPARISON ==========
    
    private fun compareTemplates(template1: ByteArray, template2: ByteArray): Double {
        val norm1 = BiometricTemplateNormalizer.canonicalize(template1)
        val norm2 = BiometricTemplateNormalizer.canonicalize(template2)
        
        if (norm1.isEmpty() || norm2.isEmpty()) return 0.0
        
        // Exact match
        if (norm1.contentEquals(norm2)) return 1.0
        
        // Calculate similarity based on byte patterns
        val minLen = minOf(norm1.size, norm2.size)
        if (minLen == 0) return 0.0
        
        // Simple byte-by-byte comparison (more tolerant than block hashing)
        var matches = 0
        for (i in 0 until minLen) {
            if (norm1[i] == norm2[i]) {
                matches++
            }
        }
        
        val similarity = matches.toDouble() / minLen.toDouble()
        
        // Boost score if lengths are similar
        val maxLen = maxOf(norm1.size, norm2.size)
        val lengthRatio = minLen.toDouble() / maxLen.toDouble()
        
        // Weighted score: 70% byte similarity, 30% length similarity
        return (similarity * 0.7) + (lengthRatio * 0.3)
    }

    private fun templateFeatures(template: ByteArray): TemplateFeatures {
        val normalizedTemplate = BiometricTemplateNormalizer.canonicalize(template)
        return TemplateFeatures(
            bytes = normalizedTemplate,
            blockHashes = computeBlockHashes(normalizedTemplate, BLOCK_SIZE)
        )
    }

    private fun computeBlockHashes(template: ByteArray, blockSize: Int): Set<Int> {
        if (template.isEmpty()) return emptySet()
        
        val hashes = HashSet<Int>()
        var start = 0
        while (start < template.size) {
            val end = minOf(start + blockSize, template.size)
            var hash = 1
            for (i in start until end) {
                hash = 31 * hash + template[i].toInt()
            }
            hashes.add(hash)
            start = end
        }
        return hashes
    }

    // ========== DEBUG FUNCTIONS ==========
    
    override suspend fun debugTemplateComparison(capturedTemplate: ByteArray) {
        val allBiometrics = biometricDao.getAllBiometrics()
        Log.d(TAG, "=== TEMPLATE COMPARISON DEBUG ===")
        Log.d(TAG, "Captured template size: ${capturedTemplate.size} bytes")
        Log.d(TAG, "Total stored biometrics: ${allBiometrics.size}")
        
        if (allBiometrics.isNotEmpty()) {
            val firstStored = allBiometrics.first()
            Log.d(TAG, "Stored template size: ${firstStored.template.size} bytes")
            
            // Check if they are exactly the same
            val areEqual = capturedTemplate.contentEquals(firstStored.template)
            Log.d(TAG, "Exact byte match: $areEqual")
            
            // Test comparison
            val score = compareTemplates(capturedTemplate, firstStored.template)
            Log.d(TAG, "Similarity score with first stored: ${String.format("%.2f", score)}")
        }
    }

    override suspend fun testIdentification(patientUuid: String): PatientMatchResult? {
        val storedBiometrics = biometricDao.getByPersonUuid(patientUuid)
        if (storedBiometrics.isEmpty()) {
            Log.e(TAG, "No biometrics found for patient $patientUuid")
            return null
        }
        
        val mockCapturedTemplate = storedBiometrics.first().template
        Log.d(TAG, "Testing identification with template from patient $patientUuid")
        return findPatientByBiometric(mockCapturedTemplate, null)
    }

    override suspend fun testVerification(patientUuid: String): Boolean {
        val storedBiometrics = biometricDao.getByPersonUuid(patientUuid)
        if (storedBiometrics.isEmpty()) {
            Log.e(TAG, "No biometrics found for patient $patientUuid")
            return false
        }
        
        val mockCapturedTemplate = storedBiometrics.first().template
        val fingerType = storedBiometrics.first().biometricType ?: "RIGHT_THUMB"
        
        Log.d(TAG, "Testing verification for patient $patientUuid with finger $fingerType")
        val result = findPatientByBiometricForVerification(
            mockCapturedTemplate, 
            patientUuid, 
            fingerType, 
            null
        )
        return result != null
    }

    // ========== DATA CLASSES ==========
    
    data class PatientMatchResult(
        val patient: Patient?,
        val template: Biometric?,
        val matchType: MatchType,
        val confidence: Double
    )

    enum class MatchType {
        HASH, TEMPLATE, FALLBACK
    }

    private data class TemplateFeatures(
        val bytes: ByteArray,
        val blockHashes: Set<Int>
    )

    // ========== SYNC METHODS ==========
    
    override suspend fun syncAll(onProgress: ((String) -> Unit)?): SyncResult {
        if (!NetworkUtils.isNetworkAvailable(context)) {
            Log.e(TAG, "No network available")
            return SyncResult.NoNetwork
        }
        
        if (!SharedPreferencesHelper.isWincoConfigured(context)) {
            Log.e(TAG, "WINCO not configured")
            return SyncResult.NotConfigured
        }
        
        val facilityId: Long? = null
        val includeTxMl = SharedPreferencesHelper.isTxMlIncludeEnabled(context)
        val txMlStartDate = SharedPreferencesHelper.getTxMlStartDate(context).trim().ifBlank { null }
        val txMlEndDate = SharedPreferencesHelper.getTxMlEndDate(context).trim().ifBlank { null }
        val txMlEnabledForRequest = includeTxMl && txMlStartDate != null && txMlEndDate != null

        val careCategoriesToSync = if (txMlEnabledForRequest) {
            "ACTIVE,IIT,TRANSFER_OUT,DEATH,STOPPED_TREATMENT,OTHER_INACTIVE"
        } else {
            "ACTIVE"
        }

        return try {
            var patientsAdded = 0
            var biometricsAdded = 0
            var viralLoadAdded = 0
            var pharmacyAdded = 0
            var invalidBiometricsSkipped = 0
            var totalBiometricSkipped = 0
            var totalBiometricFailed = 0

            val detailCache = mutableMapOf<String, WincoClientDetail?>()
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

                val needingDetail = pagePatients
                    .asSequence()
                    .filter { it.enrollmentUuid.isNullOrBlank() || it.needsDemographyEnrichment() }
                    .map { it.personUuid }
                    .distinct()
                    .toList()
                ensureClientDetails(needingDetail, detailCache)

                val eligiblePagePatients = pagePatients
                    .filter { it.isEnrollmentEligibleForPull(detailCache) }
                    .filter { seenPersonUuids.add(it.personUuid) }
                val enrichedPagePatients = eligiblePagePatients.map { it.withDemographyFromPatientTableIfNeeded(detailCache) }

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

                db.withTransaction {
                    patientDao.insertAll(patients)
                }
                patientEntities.addAll(patients)

                enrichedPagePatients.forEach { item ->
                    if (item.biometricCount > 0 || !item.latestBiometricStatus.isNullOrBlank()) {
                        biometricCandidates.add(Pair(item.personUuid, item.biometricCount))
                    }
                }

                patientsAdded += patients.size
                pageOffset++
                pagesThisRun++
                SharedPreferencesHelper.setSyncPage(context, pageOffset)

                onProgress?.invoke("Phase 1: Syncing patients... ${patientsAdded}+")

                if (wincoPageNumber >= pageResp.pages) {
                    SharedPreferencesHelper.setSyncPage(context, 0)
                    break
                }
            }

            onProgress?.invoke("Phase 1 complete: $patientsAdded patients synced")

            if (biometricCandidates.isEmpty() && patientEntities.isNotEmpty()) {
                patientEntities.forEach { p -> biometricCandidates.add(Pair(p.uuid, 0)) }
            }

            if (biometricCandidates.isNotEmpty()) {
                onProgress?.invoke("Phase 2: Syncing biometrics for ${biometricCandidates.size} clients...")

                val biometricBatch = biometricCandidates.distinctBy { it.first }
                Log.d(TAG, "Biometric batch size: ${biometricBatch.size}")

                val patientIndex: Map<String, Patient> = patientEntities.associateBy { it.uuid }

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
                                val patient = patientIndex[personUuid] ?: patientDao.getByUuid(personUuid)
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
                        if (patient == null || resp == null) continue

                        val entries = (resp.capturedBiometricsList ?: emptyList()) + (resp.capturedBiometricsList2 ?: emptyList())

                        if (entries.isEmpty()) {
                            Log.d(TAG, "No biometric entries for $personUuid")
                            consecutiveServerErrors = 0
                            continue
                        }

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
                                    BiometricTemplateNormalizer.canonicalize(Base64.decode(tpl, Base64.DEFAULT))
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

                                val normalizedForHash = BiometricTemplateNormalizer.canonicalize(templateBytes)
                                val hash = e.templateHash?.lowercase() ?: sha256Hex(normalizedForHash)

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
                                    biometricType = normalizeBiometricType(meta?.biometricType ?: e.biometricType ?: "FINGERPRINT"),
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

                        if (biometrics.isNotEmpty()) {
                            biometricDao.insertAll(biometrics)
                            biometricsAdded += biometrics.size
                            Log.d(TAG, "Saved ${biometrics.size} biometrics for ${patient.uuid}")
                        }

                        consecutiveServerErrors = 0
                    }

                    if (stopBiometricPhase) break
                }
            } else {
                onProgress?.invoke("No biometric candidates found")
            }

            if (patientEntities.isNotEmpty()) {
                onProgress?.invoke("Phase 3: Syncing Viral Load history...")
                for (chunk in patientEntities.chunked(DETAIL_PARALLELISM)) {
                    val outcomes = coroutineScope {
                        chunk.map { patient ->
                            async {
                                try {
                                    val resp = retryWithBackoff { wincoApiService.getViralLoadHistory(patient.uuid) }
                                    Pair(patient.uuid, resp.items.mapNotNull { it.toViralLoadHistory(patient.uuid) })
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to fetch VL history for ${patient.uuid}", e)
                                    null
                                }
                            }
                        }.awaitAll()
                    }
                    val validOutcomes = outcomes.filterNotNull()
                    db.withTransaction {
                        validOutcomes.forEach { (uuid, history) ->
                            viralLoadHistoryDao.deleteByPersonUuid(uuid)
                            if (history.isNotEmpty()) {
                                viralLoadHistoryDao.insertAll(history)
                            }
                        }
                    }
                    viralLoadAdded += validOutcomes.sumOf { it.second.size }
                    onProgress?.invoke("Phase 3: Viral Load history... $viralLoadAdded records")
                }

                onProgress?.invoke("Phase 4: Syncing ART Pharmacy history...")
                for (chunk in patientEntities.chunked(DETAIL_PARALLELISM)) {
                    val outcomes = coroutineScope {
                        chunk.map { patient ->
                            async {
                                try {
                                    val resp = retryWithBackoff { wincoApiService.getPharmacyHistory(patient.uuid) }
                                    Pair(patient.uuid, resp.items.mapNotNull { it.toArtPharmacy(patient.uuid) })
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to fetch pharmacy history for ${patient.uuid}", e)
                                    null
                                }
                            }
                        }.awaitAll()
                    }
                    val validOutcomes = outcomes.filterNotNull()
                    db.withTransaction {
                        validOutcomes.forEach { (uuid, history) ->
                            artPharmacyDao.deleteByPersonUuid(uuid)
                            if (history.isNotEmpty()) {
                                artPharmacyDao.insertAll(history)
                            }
                        }
                    }
                    pharmacyAdded += validOutcomes.sumOf { it.second.size }
                    onProgress?.invoke("Phase 4: Pharmacy history... $pharmacyAdded records")
                }
            }

            val now = DateUtils.formatIso8601(Date())
            SharedPreferencesHelper.setLastSyncDate(context, now)
            syncLogDao.insert(SyncLog(
                tableName = "all",
                lastSyncedRecordId = now,
                syncDate = Date(),
                status = "SUCCESS"
            ))
            
            onProgress?.invoke("Sync complete: $patientsAdded patients, $biometricsAdded biometrics, $viralLoadAdded VL history, $pharmacyAdded pharmacy history")

            SyncResult.Success(
                patientsAdded = patientsAdded,
                biometricsAdded = biometricsAdded,
                viralLoadHistoryAdded = viralLoadAdded,
                pharmacyHistoryAdded = pharmacyAdded
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
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

    private suspend fun downloadBiometricWithRetry(personUuid: String): com.carecompanion.data.network.models.WincoBiometricResponse? {
        repeat(MAX_RETRIES) { attempt ->
            try {
                return wincoApiService.getBiometricTemplates(personUuid)
            } catch (e: HttpException) {
                if (e.code() == 404) return null
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
            ?: listOfNotNull(firstName, middleName, lastName).joinToString(" ").takeIf { it.isNotBlank() }
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
            dateOfRegistration = DateUtils.parseDate(dateOfRegistration) ?: DateUtils.parseDate(dateStarted),
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
            val detail = if (detailCache.containsKey(personUuid)) {
                detailCache[personUuid]
            } else {
                retryWithBackoff { wincoApiService.getClientDetail(personUuid) }
                    .also { detailCache[personUuid] = it }
            }
            val enrollment = detail?.enrollment
            if (detail == null || !detail.isArtClient || enrollment == null || (enrollment.archived ?: 0) != 0) {
                return this
            }

            val patient = detail.patient ?: return this
            val fullNameFromDetail = patient.fullName?.takeIf { it.isNotBlank() }
                ?: listOfNotNull(patient.firstName, patient.middleName, patient.lastName).joinToString(" ")

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

        val detail = if (detailCache.containsKey(personUuid)) {
            detailCache[personUuid]
        } else {
            try {
                retryWithBackoff { wincoApiService.getClientDetail(personUuid) }
            } catch (e: Exception) {
                null
            }.also { detailCache[personUuid] = it }
        }

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

    // ENROLLMENT: Always normalize template, enforce quality, and log all outcomes
    override suspend fun enrollBiometric(
        patientUuid: String,
        fingerType: String,
        template: ByteArray,
        quality: Int,
        facilityId: Long?,
        userId: String?
    ): Boolean {
        val normalized = BiometricTemplateNormalizer.canonicalize(template)
        val isValid = BiometricTemplateNormalizer.isValid(normalized)
        val qualityAssessment = com.carecompanion.biometric.BiometricQualityValidator.validateEnrollmentQuality(quality)
        val hash = SyncRepositoryImpl.sha256Hex(normalized)
        val status = when {
            !isValid -> "ERROR"
            !qualityAssessment.passed -> "LOW_QUALITY"
            else -> "SUCCESS"
        }
        com.carecompanion.biometric.BiometricAuditLogger.logEnrollment(
            patientUuid = patientUuid,
            fingerType = fingerType,
            quality = quality,
            templateHash = hash,
            status = status,
            errorMessage = if (!isValid) "Invalid template" else if (!qualityAssessment.passed) qualityAssessment.message else null,
            userId = userId,
            facilityId = facilityId
        )
        return status == "SUCCESS"
    }

    // IDENTIFICATION: Always normalize, match, and log all attempts/results
    override suspend fun identifyBiometric(
        capturedTemplate: ByteArray,
        facilityId: Long?,
        userId: String?
    ): PatientMatchResult? {
        val start = System.currentTimeMillis()
        val canonicalCaptured = BiometricTemplateNormalizer.canonicalize(capturedTemplate)
        val allBiometrics = biometricDao.getAllBiometrics()
        val matchThreshold = SharedPreferencesHelper.getMatchThreshold(context)

        var bestMatch: Biometric? = null
        var bestScore = 0.0

        // Chunked processing with per-chunk timeout protection (Issue 4 fix):
        // Search in chunks of IDENTIFICATION_CHUNK_SIZE so the search can be
        // cancelled and progress reported without hanging the UI for large DBs.
        val CHUNK_SIZE = 500
        val SEARCH_TIMEOUT_MS = 30_000L  // 30 seconds hard cap for entire 1:N search
        outer@ for (chunk in allBiometrics.chunked(CHUNK_SIZE)) {
            if (System.currentTimeMillis() - start > SEARCH_TIMEOUT_MS) {
                Log.w(TAG, "identifyBiometric: search timeout after ${System.currentTimeMillis() - start}ms, " +
                    "searched ${allBiometrics.indexOf(chunk.first())} / ${allBiometrics.size} records")
                break
            }
            for (bio in chunk) {
                val score = compareTemplates(canonicalCaptured, bio.template)
                if (score > bestScore && score >= matchThreshold) {
                    bestScore = score
                    bestMatch = bio
                    // Early exit: perfect hash-level match
                    if (bestScore >= 99.9) break@outer
                }
            }
        }

        val duration = System.currentTimeMillis() - start
        com.carecompanion.biometric.BiometricAuditLogger.logIdentification(
            matchedPatientUuid = bestMatch?.personUuid,
            fingerType = bestMatch?.biometricType ?: "UNKNOWN",
            matchScore = bestScore,
            candidatesSearched = allBiometrics.size,
            searchDurationMs = duration,
            method = "TEMPLATE",
            userId = userId,
            facilityId = facilityId
        )
        return if (bestMatch != null) PatientMatchResult(
            patient = patientDao.getByUuid(bestMatch.personUuid),
            template = bestMatch,
            matchType = MatchType.TEMPLATE,
            confidence = bestScore
        ) else null
    }

    // VERIFICATION: Always normalize, match, and log all attempts/results
    override suspend fun verifyBiometric(
        capturedTemplate: ByteArray,
        personUuid: String,
        fingerType: String,
        facilityId: Long?,
        userId: String?
    ): PatientMatchResult? {
        val canonicalCaptured = BiometricTemplateNormalizer.canonicalize(capturedTemplate)
        val patientBiometrics = biometricDao.getAllByPersonAndFinger(personUuid, fingerType, facilityId)
        var bestMatch: Biometric? = null
        var bestScore = 0.0
        val matchThreshold = SharedPreferencesHelper.getMatchThreshold(context)
        for (bio in patientBiometrics) {
            val score = compareTemplates(canonicalCaptured, bio.template)
            if (score > bestScore && score >= matchThreshold) {
                bestScore = score
                bestMatch = bio
            }
        }
        com.carecompanion.biometric.BiometricAuditLogger.logVerification(
            patientUuid = personUuid,
            fingerType = fingerType,
            matchScore = bestScore,
            isMatch = bestMatch != null,
            matchThreshold = matchThreshold,
            method = "TEMPLATE",
            userId = userId,
            facilityId = facilityId
        )
        return if (bestMatch != null) PatientMatchResult(
            patient = patientDao.getByUuid(bestMatch.personUuid),
            template = bestMatch,
            matchType = MatchType.TEMPLATE,
            confidence = bestScore
        ) else null
    }
}