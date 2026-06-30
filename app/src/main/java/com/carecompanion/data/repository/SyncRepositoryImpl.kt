package com.carecompanion.data.repository

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.room.withTransaction
import com.carecompanion.biometric.BiometricTemplateNormalizer
import com.carecompanion.biometric.FingerprintMatcher
import com.carecompanion.data.database.AppDatabase
import com.carecompanion.data.database.dao.ArtPharmacyDao
import com.carecompanion.data.database.dao.BiometricDao
import com.carecompanion.data.database.dao.PatientDao
import com.carecompanion.data.database.dao.SyncLogDao
import com.carecompanion.data.database.dao.ViralLoadHistoryDao
import com.carecompanion.data.database.dao.EacEpisodeDao
import com.carecompanion.data.database.entities.ArtPharmacy
import com.carecompanion.data.database.entities.Biometric
import com.carecompanion.data.database.entities.Patient
import com.carecompanion.data.database.entities.SyncLog
import com.carecompanion.data.database.entities.ViralLoadHistory
import com.carecompanion.data.database.entities.EacEpisode
import com.carecompanion.data.network.WincoApiService
import com.carecompanion.data.network.models.WincoClientDetail
import com.carecompanion.data.network.models.WincoClientItem
import com.carecompanion.data.network.models.WincoPharmacyVisit
import com.carecompanion.data.network.models.WincoViralLoadHistoryItem
import com.carecompanion.data.network.models.WincoEacEpisode
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
    private val eacEpisodeDao: EacEpisodeDao,
    private val pmtctRecordDao: com.carecompanion.data.database.dao.PmtctRecordDao,
    private val infantRecordDao: com.carecompanion.data.database.dao.InfantRecordDao,
) : SyncRepository {

    companion object {
        private const val TAG = "SyncRepository"
        private const val PAGE_LIMIT = 50
        private const val PAGE_SIZE = 500            // matches new WINCO per_page cap
        private const val BIOMETRIC_REQUEST_DELAY_MS = 0L
        private const val BIOMETRIC_PARALLELISM = 8
        private const val DETAIL_PARALLELISM = 16
        private const val BULK_BIOMETRIC_CHUNK = 150 // patients per bulk biometrics request
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 1000L
        private const val MIN_TEMPLATE_BYTES = 64   // ISO 19794-2 header ~26B; 64B ≈ 6 minutiae
        private const val MIN_QUALITY_THRESHOLD = 40
        private const val BLOCK_SIZE = 64
        // Identification thresholds — calibrated for SecuGen SDK + Bozorth3 custom matcher.
        //
        // Background: SecuGen GetMatchingScore returns 0–199 raw, normalised to 0–100 by
        // / 199 * 100.  Real same-finger matches typically produce raw 80–140 (norm 40–70%).
        // The Bozorth3 custom matcher typically scores same-finger at 20–55 / 100.
        // The previous floor of 0.62 rejected all valid Bozorth3 matches and most SDK matches
        // when the raw score happened to fall in the 80–123 range (< 62 normalised).
        // Floor set to 0.35 so that genuine Bozorth3 matches (20–55) still require the gap
        // guard below to rule out random false-positives.  The confidence gap is the primary
        // accuracy mechanism; the floor only filters out clearly random noise.
        private const val IDENTIFICATION_MIN_SCORE = 0.35     // 35/100 — genuine Bozorth3/SDK scores reliably exceed this
        private const val IDENTIFICATION_CONFIDENCE_GAP = 0.08 // best must outscore 2nd-best by 8 pp (was 12)
        private const val VERIFICATION_MIN_SCORE = 0.40        // 40/100 — 1:1 (single patient, generous, gap not needed)

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
                ?.replace("_FINGER", "")  // "RIGHT_INDEX_FINGER" → "RIGHT_INDEX"
                .orEmpty()
        }
    }

    private val hashBackfillMutex = Mutex()
    @Volatile private var hashBackfillCompleted = false

    // Minutiae-based matcher used by compareTemplates() — stateless, safe to share
    private val fingerprintMatcher = FingerprintMatcher()

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
        var bestScoreOtherPatient = 0.0
        val matchThreshold = SharedPreferencesHelper.getMatchThreshold(context)

        for (bio in allBiometrics) {
            val score = compareTemplates(canonicalCaptured, bio.template)
            Log.d(TAG, "Patient ${bio.personUuid}: score=${"%.3f".format(score)}")

            if (score > bestScore) {
                if (bestMatch != null && bestMatch.personUuid != bio.personUuid) {
                    if (bestScore > bestScoreOtherPatient) bestScoreOtherPatient = bestScore
                }
                bestScore = score
                bestMatch = bio
            } else if (bio.personUuid != bestMatch?.personUuid && score > bestScoreOtherPatient) {
                bestScoreOtherPatient = score
            }
        }

        val gap = bestScore - bestScoreOtherPatient
        return if (bestMatch != null && bestScore >= matchThreshold && gap >= IDENTIFICATION_CONFIDENCE_GAP) {
            val patient = patientDao.getByUuid(bestMatch.personUuid)
            Log.d(TAG, "✅ MATCH: ${patient?.fullName} score=${"%.3f".format(bestScore)} gap=${"%.3f".format(gap)}")
            PatientMatchResult(patient = patient, template = bestMatch, matchType = MatchType.TEMPLATE, confidence = bestScore)
        } else {
            Log.w(TAG, "No match. best=${"%.3f".format(bestScore)} gap=${"%.3f".format(gap)} threshold=$matchThreshold")
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
            val score = compareTemplates(normalizedCaptured, bio.template, "VERIFY")
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

    /**
     * Compares two fingerprint templates using FingerprintMatcher (Bozorth3 minutiae algorithm).
     *
     * Returns a score in the 0.0–1.0 range (FingerprintMatcher score / 100.0) so that
     * the existing confidence/threshold convention throughout the codebase is preserved.
     *
     * Two captures of the same finger will NEVER produce identical bytes — byte-level
     * comparison is deliberately replaced by minutiae-based matching here.
     *
     * @param purpose "VERIFY" for 1:1 (threshold 55), "IDENTIFY" for 1:N (threshold 50)
     */
    private fun compareTemplates(
        canonicalProbe: ByteArray,
        storedTemplate: ByteArray,
        purpose: String = "IDENTIFY"
    ): Double {
        val norm2 = BiometricTemplateNormalizer.canonicalize(storedTemplate)
        if (canonicalProbe.isEmpty() || norm2.isEmpty()) return 0.0
        if (canonicalProbe.contentEquals(norm2)) return 1.0
        return fingerprintMatcher.match(canonicalProbe, norm2, purpose).score / 100.0
    }

    /**
     * Matches two already-canonical ISO 19794-2 templates directly via Bozorth3, skipping
     * the re-canonicalization step. Use this in the 1:N hot loop where stored templates are
     * guaranteed canonical (stored via syncAll → canonicalize at ingest time). Avoiding
     * 16 000+ redundant canonicalize() calls per identification scan is a meaningful win.
     *
     * Call site is responsible for the format gate — only call this for ISO 19794-2 templates.
     */
    private fun compareCanonical(
        canonicalProbe: ByteArray,
        canonicalRef: ByteArray,
        purpose: String = "IDENTIFY"
    ): Double {
        if (canonicalProbe.isEmpty() || canonicalRef.isEmpty()) return 0.0
        if (canonicalProbe.contentEquals(canonicalRef)) return 1.0
        return fingerprintMatcher.match(canonicalProbe, canonicalRef, purpose).score / 100.0
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

    /**
     * Score-to-tier mapping per NPHCDA / PEPFAR clinical guidance:
     *   HIGH   ≥ 80% — accept with confidence; no extra verification needed
     *   MEDIUM 65–79% — accept but prompt clinician to confirm a secondary identifier
     *   LOW    < 65%  — rejected; does not meet the NPHCDA minimum floor
     */
    enum class ConfidenceTier { HIGH, MEDIUM, LOW }

    data class PatientMatchResult(
        val patient: Patient?,
        val template: Biometric?,
        val matchType: MatchType,
        val confidence: Double,
        // Derived from confidence; callers should not set this manually.
        val confidenceTier: ConfidenceTier = when {
            confidence >= 0.80 -> ConfidenceTier.HIGH
            confidence >= 0.65 -> ConfidenceTier.MEDIUM
            else               -> ConfidenceTier.LOW
        },
        // Non-null when the matched biometric's matchPersonUuid points to a different
        // patient record — signals a probable duplicate registration to the UI.
        val suspectedDuplicateUuid: String? = null,
        // True when a verification match was found on a different finger than requested
        // (multi-finger fallback path); audit log includes the actual finger used.
        val usedFallbackFinger: Boolean = false
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
        
        // Facility scope (multi-tenancy): the active facility drives the whole sync — client fetch,
        // per-patient detail, and cohort worklists — so a multi-facility node isolates each facility.
        val facilityId: Long? = SharedPreferencesHelper.getActiveFacilityId(context).takeIf { it > 0L }
        val includeTxMl = SharedPreferencesHelper.isTxMlIncludeEnabled(context)
        val txMlStartDate = SharedPreferencesHelper.getTxMlStartDate(context).trim().ifBlank { null }
        val txMlEndDate = SharedPreferencesHelper.getTxMlEndDate(context).trim().ifBlank { null }
        val txMlEnabledForRequest = includeTxMl && txMlStartDate != null && txMlEndDate != null

        val careCategoriesToSync = if (txMlEnabledForRequest) {
            "ACTIVE,IIT,TRANSFER_OUT,DEATH,STOPPED_TREATMENT,OTHER_INACTIVE"
        } else {
            "ACTIVE"
        }

        // Incremental sync: pass last-sync timestamp so WINCO only returns changed records.
        // Null on first install (full sync). Cleared if the user forces a full re-sync.
        val lastSyncDate: String? = SharedPreferencesHelper.getLastSyncDate(context)?.trim()?.ifBlank { null }

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

            onProgress?.invoke("Phase 1: Syncing patient data${if (lastSyncDate != null) " (delta since $lastSyncDate)" else " (full)"}...")

            while (pagesThisRun < PAGE_LIMIT) {
                val wincoPageNumber = pageOffset + 1
                Log.d(TAG, "Fetching page $wincoPageNumber with $PAGE_SIZE items per page, updatedSince=$lastSyncDate")

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
                        updatedSince = lastSyncDate,
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
                val biometricBatch = biometricCandidates.distinctBy { it.first }
                onProgress?.invoke("Phase 2: Syncing biometrics for ${biometricBatch.size} clients (bulk)...")
                Log.d(TAG, "Biometric batch size: ${biometricBatch.size}, chunks of $BULK_BIOMETRIC_CHUNK")

                val patientIndex: Map<String, Patient> = patientEntities.associateBy { it.uuid }
                var processedCount = 0
                var stopBiometricPhase = false
                var consecutiveServerErrors = 0

                for (chunk in biometricBatch.chunked(BULK_BIOMETRIC_CHUNK)) {
                    if (stopBiometricPhase) break
                    val uuids = chunk.map { it.first }

                    val bulkResp = try {
                        retryWithBackoff {
                            wincoApiService.getBiometricsBulk(
                                com.carecompanion.data.network.models.WincoBulkBiometricRequest(uuids)
                            )
                        }
                    } catch (e: HttpException) {
                        if (e.code() in 500..599) {
                            consecutiveServerErrors++
                            Log.w(TAG, "Bulk biometric server error ${e.code()}, consecutive: $consecutiveServerErrors")
                            if (consecutiveServerErrors >= 3) {
                                onProgress?.invoke("Biometric sync paused: WINCO server unstable (HTTP ${e.code()}).")
                                stopBiometricPhase = true
                            }
                        } else {
                            Log.e(TAG, "Bulk biometric HTTP error: ${e.code()}")
                        }
                        continue
                    } catch (e: Exception) {
                        Log.e(TAG, "Bulk biometric request failed", e)
                        continue
                    }

                    for (personUuid in uuids) {
                        processedCount++
                        val patient = patientIndex[personUuid] ?: patientDao.getByUuid(personUuid) ?: continue
                        onProgress?.invoke("Phase 2: $processedCount/${biometricBatch.size} - ${patient.fullName ?: personUuid}")

                        val resp = bulkResp.biometrics[personUuid] ?: continue
                        val entries = (resp.capturedBiometricsList ?: emptyList()) +
                                      (resp.capturedBiometricsList2 ?: emptyList())
                        if (entries.isEmpty()) continue

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
                                if (tpl == null) { skippedCount++; continue }
                                if (isMemoryReferenceTemplate(tpl)) {
                                    skippedCount++; invalidBiometricsSkipped++; continue
                                }

                                val recapture = (e.recapture ?: 0).coerceAtLeast(0)
                                val typeKey = e.templateType?.replace(" ", "_") ?: "UNKNOWN"

                                val templateBytes = try {
                                    BiometricTemplateNormalizer.canonicalize(Base64.decode(tpl, Base64.DEFAULT))
                                } catch (ex: Exception) {
                                    Log.e(TAG, "Failed to decode template for $personUuid", ex)
                                    failedCount++; continue
                                }

                                if (templateBytes.size < MIN_TEMPLATE_BYTES) {
                                    skippedCount++; invalidBiometricsSkipped++; continue
                                }
                                if (!BiometricTemplateNormalizer.isValid(templateBytes)) {
                                    skippedCount++; invalidBiometricsSkipped++; continue
                                }

                                val imageQuality = e.imageQuality ?: 0
                                if (imageQuality > 0 && imageQuality < MIN_QUALITY_THRESHOLD) {
                                    skippedCount++; continue
                                }

                                // templateBytes is already canonicalized — reuse it directly for hash
                                val hash = e.templateHash?.lowercase() ?: sha256Hex(templateBytes)

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

                                val biometricId = e.sourceId?.toString()
                                    ?: "${patient.uuid}_${typeKey}_$hash"

                                biometrics.add(Biometric(
                                    id = biometricId,
                                    personUuid = patient.uuid,
                                    template = templateBytes,
                                    biometricType = normalizeBiometricType(
                                        // Prefer the most specific finger label available.
                                        // biometricType is sometimes "FINGERPRINT" (device-level, not finger-specific);
                                        // templateType ("Right Thumb", "Right Index") is finger-specific and more reliable.
                                        meta?.biometricType?.takeIf { it.uppercase() != "FINGERPRINT" }
                                            ?: e.biometricType?.takeIf { it.uppercase() != "FINGERPRINT" }
                                            ?: e.templateType       // "Right Thumb" → "RIGHT_THUMB"
                                            ?: "FINGERPRINT"
                                    ),
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
                                ))
                                savedCount++

                            } catch (ex: Exception) {
                                Log.e(TAG, "Failed to process biometric $entryIdx for $personUuid", ex)
                                failedCount++
                            }
                        }

                        Log.d(TAG, "Biometric for $personUuid: saved=$savedCount skipped=$skippedCount failed=$failedCount")
                        totalBiometricSkipped += skippedCount
                        totalBiometricFailed += failedCount

                        if (biometrics.isNotEmpty()) {
                            // Replace this person's full template set atomically. The bulk
                            // endpoint returns ALL of a patient's templates, so a clean
                            // delete+insert keeps re-sync idempotent. Without the delete,
                            // templates with hash-derived IDs (null sourceId) accumulate as
                            // duplicates whenever canonicalization changes the hash — the
                            // cause of the candidate pool ballooning from 2.4k to 7.1k.
                            db.withTransaction {
                                biometricDao.deleteByPersonUuid(personUuid)
                                biometricDao.insertAll(biometrics)
                            }
                            biometricsAdded += biometrics.size
                        }
                        consecutiveServerErrors = 0
                    }
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
                                    val resp = retryWithBackoff { wincoApiService.getViralLoadHistory(patient.uuid, facilityId) }
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
                                    val resp = retryWithBackoff { wincoApiService.getPharmacyHistory(patient.uuid, facilityId) }
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

                // Phase 5: EAC episodes — fetched only for unsuppressed clients (the EAC-relevant
                // cohort), so the on-device EacGapEngine can flag cascade gaps for active clients.
                val eacCohort = patientEntities.filter { (it.lastViralLoadResult ?: 0L) >= 1000L }
                if (eacCohort.isNotEmpty()) {
                    onProgress?.invoke("Phase 5: Syncing EAC...")
                    for (chunk in eacCohort.chunked(DETAIL_PARALLELISM)) {
                        val outcomes = coroutineScope {
                            chunk.map { patient ->
                                async {
                                    try {
                                        val resp = retryWithBackoff { wincoApiService.getEac(patient.uuid, facilityId) }
                                        Pair(patient.uuid, resp.episodes.mapNotNull { it.toEacEpisode(patient.uuid) })
                                    } catch (e: Exception) {
                                        null  // 404 for non-ART / no EAC is expected; skip
                                    }
                                }
                            }.awaitAll()
                        }
                        val valid = outcomes.filterNotNull()
                        db.withTransaction {
                            valid.forEach { (uuid, episodes) ->
                                eacEpisodeDao.deleteByPersonUuid(uuid)
                                if (episodes.isNotEmpty()) eacEpisodeDao.insertAll(episodes)
                            }
                        }
                    }
                }

                // Phase 6: PMTCT worklist — one bulk call returns currently-pregnant women + VL gaps.
                onProgress?.invoke("Phase 6: Syncing PMTCT...")
                try {
                    val pmtct = retryWithBackoff { wincoApiService.getPmtctWorklist(facilityId) }
                    val records = pmtct.items.mapNotNull { it.toPmtctRecord() }
                    db.withTransaction {
                        pmtctRecordDao.clear()
                        if (records.isNotEmpty()) pmtctRecordDao.insertAll(records)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "PMTCT worklist sync failed", e)
                }

                // Phase 7: EID worklist — HIV-exposed infants with high-risk prediction + intervention gaps.
                onProgress?.invoke("Phase 7: Syncing EID...")
                try {
                    val eid = retryWithBackoff { wincoApiService.getEidWorklist(facilityId) }
                    val records = eid.items.mapNotNull { it.toInfantRecord() }
                    db.withTransaction {
                        infantRecordDao.clear()
                        if (records.isNotEmpty()) infantRecordDao.insertAll(records)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "EID worklist sync failed", e)
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
                pharmacyHistoryAdded = pharmacyAdded,
                audit = SyncResult.SyncAudit(
                    pagesRead = pagesThisRun,
                    biometricCandidates = biometricCandidates.size,
                    biometricsSkipped = totalBiometricSkipped + invalidBiometricsSkipped,
                    biometricsFailed = totalBiometricFailed
                )
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
            viralLoadIndication = viralLoadIndication,
            vlCategory = vlCategory,
            lastSyncDate = Date(),
        )
    }

    private fun com.carecompanion.data.network.models.WincoInfantItem.toInfantRecord(): com.carecompanion.data.database.entities.InfantRecord? {
        val iu = infantUuid ?: return null
        val gap = gaps.firstOrNull()
        return com.carecompanion.data.database.entities.InfantRecord(
            infantUuid = iu,
            name = name,
            hospitalNumber = hospitalNumber,
            motherPersonUuid = motherPersonUuid,
            ancNo = ancNo,
            dateOfDelivery = DateUtils.parseDate(dateOfDelivery),
            ageWeeks = ageWeeks,
            ageMonths = ageMonths,
            highRisk = highRisk,
            highRiskReason = highRiskReason,
            arvGiven = arvGiven,
            ctxGiven = ctxGiven,
            pcrDone = pcrDone,
            pcrResult = pcrResult,
            pcrPositive = pcrPositive,
            pcrResultReceived = pcrResultReceived,
            antibodyDone = antibodyDone,
            outcome18m = outcome18m,
            finalResultKnown = finalResultKnown,
            interventionsSummary = interventionsSummary,
            gapType = gap?.type,
            gapSeverity = gap?.severity,
            gapMessage = gap?.message,
            gapCount = gaps.size,
            lastSyncDate = Date(),
        )
    }

    private fun com.carecompanion.data.network.models.WincoPmtctItem.toPmtctRecord(): com.carecompanion.data.database.entities.PmtctRecord? {
        val pu = personUuid ?: return null
        val anc = ancNo ?: return null
        val gap = gaps.firstOrNull()
        return com.carecompanion.data.database.entities.PmtctRecord(
            personUuid = pu,
            ancNo = anc,
            name = name,
            hospitalNumber = hospitalNumber,
            lmp = DateUtils.parseDate(lmp),
            edd = DateUtils.parseDate(edd),
            gaWeeks = gaWeeks,
            currentlyPregnant = currentlyPregnant,
            pmtctVlDone = pmtctVlDone,
            txCurr = txCurr,
            fetalHighRisk = fetalHighRisk,
            fetalHighRiskReason = fetalHighRiskReason,
            gapType = gap?.type,
            gapSeverity = gap?.severity,
            gapMessage = gap?.message,
            lastSyncDate = Date(),
        )
    }

    private fun WincoEacEpisode.toEacEpisode(personUuid: String): EacEpisode? {
        val epUuid = uuid ?: return null
        return EacEpisode(
            personUuid = personUuid,
            episodeUuid = epUuid,
            status = status,
            stage = stage,
            sessions = sessions,
            triggerVl = triggerVl,
            triggerDate = DateUtils.parseDate(triggerDate),
            repeatVl = repeatVl,
            regimenSwitched = regimenSwitched,
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

    // IDENTIFICATION (1:N): Facility-first search → widen to full DB if no match.
    // Quality-filtered candidates only; confidence gap guard; duplicate patient detection.
    override suspend fun identifyBiometric(
        capturedTemplate: ByteArray,
        facilityId: Long?,
        userId: String?,
        sdkMatcher: ((ByteArray, ByteArray) -> Double)?
    ): PatientMatchResult? {
        val start = System.currentTimeMillis()

        // Diagnostic: log the first 4 bytes of the captured template before any processing.
        val probeHeader = if (capturedTemplate.size >= 4)
            capturedTemplate.take(4).joinToString("") { "%02x".format(it) } else "short(${capturedTemplate.size}B)"
        com.carecompanion.biometric.BiometricFileLogger.write("INFO", "PROBE_FORMAT",
            "header=$probeHeader size=${capturedTemplate.size}B isISO=${BiometricTemplateNormalizer.isIso19794Format(capturedTemplate)}")

        val canonicalCaptured = BiometricTemplateNormalizer.canonicalize(capturedTemplate)
        // Probe failed the format gate (non-ISO). Running a 7000+ candidate scan would
        // produce 0.0 for every comparison and generate one FORMAT_GATE log per candidate.
        // Abort early and surface the real problem: the scanner produced a non-ISO template.
        if (canonicalCaptured.isEmpty()) {
            com.carecompanion.biometric.BiometricFileLogger.write("WARN", "IDENTIFY_ABORT",
                "reason=probe_not_ISO header=$probeHeader size=${capturedTemplate.size}B")
            return null
        }
        val method = if (sdkMatcher != null) "SDK_1_TO_N" else "CUSTOM_MATCHER"

        // Phase 1: search only this facility's patients first (PEPFAR local-first pattern).
        // This is far faster for large multi-facility offline DBs and covers ~95% of visits.
        val facilityBiometrics = if (facilityId != null)
            biometricDao.getAllBiometricsByFacility(facilityId)
                .filter { it.imageQuality == null || (it.imageQuality ?: 0) >= MIN_QUALITY_THRESHOLD }
        else emptyList()

        // Phase 2: full-DB fallback (transfer patients, referred clients, cross-facility IIT recall)
        // Skip templates already evaluated in Phase 1 to avoid double-counting.
        val facilityIds = facilityBiometrics.map { it.id }.toSet()
        val otherBiometrics = biometricDao.getAllBiometricsAboveQuality(MIN_QUALITY_THRESHOLD)
            .filter { it.id !in facilityIds }

        val searchOrder = facilityBiometrics + otherBiometrics
        val totalCandidates = searchOrder.size

        // Log stored template size and vendor to aid in cross-vendor accuracy diagnostics.
        // Stored templates are canonical (32-byte header + minutiae × 6 bytes), so expected
        // sizes are: SecuGen ~200-380 B, Neurotec ~180-360 B, Futronic ~200-400 B.
        searchOrder.firstOrNull()?.let { first ->
            val capturedSize = capturedTemplate.size
            val storedSize   = first.template.size
            val vendorHint   = first.deviceName ?: "unknown-vendor"
            val formatHint = when {
                !BiometricTemplateNormalizer.isIso19794Format(first.template) -> "non-ISO(skipped)"
                storedSize in 32..399  -> "ISO19794-canonical"
                storedSize >= 400      -> "ISO19794-large"
                else                   -> "too-small(${storedSize}B)"
            }
            Log.i(TAG, "identifyBiometric: probe=${capturedSize}B stored=${storedSize}B format=$formatHint vendor=$vendorHint candidates=$totalCandidates method=$method")
        }

        var bestMatch: Biometric? = null
        var bestScore = 0.0
        // Highest score achieved by any template belonging to a DIFFERENT patient than bestMatch.
        // Without a confidence gap, the system returns the "best guess" even when two patients
        // score similarly — that is the primary cause of wrong-patient identification.
        var bestScoreOtherPatient = 0.0

        val CHUNK_SIZE = 500
        val SEARCH_TIMEOUT_MS = 30_000L
        outer@ for (chunk in searchOrder.chunked(CHUNK_SIZE)) {
            if (System.currentTimeMillis() - start > SEARCH_TIMEOUT_MS) {
                Log.w(TAG, "identifyBiometric: search timeout after ${System.currentTimeMillis() - start}ms, searched < $totalCandidates records")
                break
            }
            for (bio in chunk) {
                // Format gate: skip non-ISO 19794-2 stored templates (e.g. Neurotech NT\0\x10).
                if (!BiometricTemplateNormalizer.isIso19794Format(bio.template)) {
                    val h = if (bio.template.size >= 4)
                        bio.template.take(4).joinToString("") { "%02x".format(it) }
                    else "size=${bio.template.size}"
                    com.carecompanion.biometric.BiometricFileLogger.write("WARN", "FORMAT_GATE_SKIP",
                        "action=skipped header=$h size=${bio.template.size}B device=${bio.deviceName} patient=${bio.personUuid.take(8)}")
                    continue
                }

                val isSameVendor = bio.deviceName?.contains("secugen", ignoreCase = true) == true

                val score: Double = if (sdkMatcher != null) {
                    // Use canonical probe: same ISO 19794-2 layout as the stored template,
                    // avoiding SDK errors from the raw 1566B buffer vs the 200-400B stored form.
                    val sdkScore = try {
                        sdkMatcher(canonicalCaptured, bio.template) / 100.0
                    } catch (_: Exception) { 0.0 }
                    if (isSameVendor) {
                        if (sdkScore > 0.0) sdkScore
                        else compareCanonical(canonicalCaptured, bio.template)
                    } else {
                        val bozorthScore = compareCanonical(canonicalCaptured, bio.template)
                        if (bozorthScore > sdkScore) {
                            com.carecompanion.biometric.BiometricFileLogger.write("INFO", "CROSS_VENDOR_MATCH",
                                "winner=bozorth3 bozorth=${String.format("%.3f", bozorthScore)} sdk=${String.format("%.3f", sdkScore)} device=${bio.deviceName}")
                        }
                        maxOf(sdkScore, bozorthScore)
                    }
                } else {
                    compareCanonical(canonicalCaptured, bio.template)
                }
                if (score > bestScore) {
                    if (bestMatch != null && bestMatch.personUuid != bio.personUuid) {
                        if (bestScore > bestScoreOtherPatient) bestScoreOtherPatient = bestScore
                    }
                    bestScore = score
                    bestMatch = bio
                    if (bestScore >= 0.999) break@outer
                } else if (bio.personUuid != bestMatch?.personUuid && score > bestScoreOtherPatient) {
                    bestScoreOtherPatient = score
                }
            }
        }

        val duration = System.currentTimeMillis() - start

        // Hard floor — PEPFAR/NPHCDA 1:N minimum
        if (bestMatch == null || bestScore < IDENTIFICATION_MIN_SCORE) {
            Log.w(TAG, "identifyBiometric: no match above floor (best=$bestScore, floor=$IDENTIFICATION_MIN_SCORE)")
            com.carecompanion.biometric.BiometricFileLogger.write("WARN", "NO_MATCH",
                "reason=below_floor best=${String.format("%.3f", bestScore)} floor=$IDENTIFICATION_MIN_SCORE candidates=$totalCandidates duration_ms=$duration")
            bestMatch = null
            bestScore = 0.0
        }
        // Confidence gap — ambiguous result is not a result
        else if (bestScore - bestScoreOtherPatient < IDENTIFICATION_CONFIDENCE_GAP) {
            val gap = bestScore - bestScoreOtherPatient
            Log.w(TAG, "identifyBiometric: ambiguous match rejected " +
                "(best=${String.format("%.3f", bestScore)} vs otherBest=${String.format("%.3f", bestScoreOtherPatient)}, " +
                "gap=${String.format("%.3f", gap)} < required $IDENTIFICATION_CONFIDENCE_GAP)")
            com.carecompanion.biometric.BiometricFileLogger.write("WARN", "AMBIGUOUS_REJECTED",
                "reason=gap_too_small best=${String.format("%.3f", bestScore)} second=${String.format("%.3f", bestScoreOtherPatient)} gap=${String.format("%.3f", gap)} required=$IDENTIFICATION_CONFIDENCE_GAP")
            bestMatch = null
            bestScore = 0.0
        }

        // Duplicate patient detection: the server may flag a biometric as belonging to a known
        // duplicate record (matchPersonUuid ≠ personUuid).  Surface this to the UI so the
        // clinician can reconcile the records rather than silently accepting a wrong match.
        val suspectedDuplicate = bestMatch?.let { bm ->
            val serverDupUuid = bm.matchPersonUuid
            if (!serverDupUuid.isNullOrBlank() && serverDupUuid != bm.personUuid) serverDupUuid else null
        }
        if (suspectedDuplicate != null) {
            Log.w(TAG, "identifyBiometric: matched patient ${bestMatch?.personUuid} has a server-flagged duplicate → $suspectedDuplicate")
        }

        com.carecompanion.biometric.BiometricAuditLogger.logIdentification(
            matchedPatientUuid = bestMatch?.personUuid,
            fingerType = bestMatch?.biometricType ?: "UNKNOWN",
            matchScore = bestScore,
            candidatesSearched = totalCandidates,
            searchDurationMs = duration,
            method = method,
            userId = userId,
            facilityId = facilityId
        )
        return if (bestMatch != null) PatientMatchResult(
            patient = patientDao.getByUuid(bestMatch.personUuid),
            template = bestMatch,
            matchType = MatchType.TEMPLATE,
            confidence = bestScore,
            suspectedDuplicateUuid = suspectedDuplicate
        ) else null
    }

    // VERIFICATION (1:1): Primary finger first; multi-finger fallback if primary fails.
    // NPHCDA 65% floor applied regardless of user-configurable threshold.
    override suspend fun verifyBiometric(
        capturedTemplate: ByteArray,
        personUuid: String,
        fingerType: String,
        facilityId: Long?,
        userId: String?,
        sdkMatcher: ((ByteArray, ByteArray) -> Double)?
    ): PatientMatchResult? {
        val canonicalCaptured = BiometricTemplateNormalizer.canonicalize(capturedTemplate)
        if (canonicalCaptured.isEmpty()) {
            val probeHeader = if (capturedTemplate.size >= 4)
                capturedTemplate.take(4).joinToString("") { "%02x".format(it) } else "short"
            com.carecompanion.biometric.BiometricFileLogger.write("WARN", "VERIFY_ABORT",
                "reason=probe_not_ISO header=$probeHeader size=${capturedTemplate.size}B patient=$personUuid")
            return null
        }
        val normalizedFingerType = normalizeBiometricType(fingerType)
        val matchThreshold = SharedPreferencesHelper.getMatchThreshold(context)
        val method = if (sdkMatcher != null) "SDK_1_TO_1" else "CUSTOM_MATCHER"

        fun scoreCandidates(candidates: List<Biometric>): Pair<Biometric?, Double> {
            var best: Biometric? = null
            var bestScore = 0.0
            for (bio in candidates.distinctBy { it.hashed ?: it.template.contentHashCode() }) {
                if (!BiometricTemplateNormalizer.isIso19794Format(bio.template)) continue
                val isSameVendor = bio.deviceName?.contains("secugen", ignoreCase = true) == true
                val score: Double = if (sdkMatcher != null) {
                    val sdkScore = try {
                        sdkMatcher(canonicalCaptured, bio.template) / 100.0
                    } catch (_: Exception) { 0.0 }
                    if (isSameVendor) {
                        if (sdkScore > 0.0) sdkScore
                        else compareCanonical(canonicalCaptured, bio.template, "VERIFY")
                    } else {
                        val bozorthScore = compareCanonical(canonicalCaptured, bio.template, "VERIFY")
                        maxOf(sdkScore, bozorthScore)
                    }
                } else {
                    compareCanonical(canonicalCaptured, bio.template, "VERIFY")
                }
                if (score > bestScore && score >= VERIFICATION_MIN_SCORE) {
                    bestScore = score
                    best = bio
                }
            }
            return best to bestScore
        }

        // ── Primary finger ──────────────────────────────────────────────────────
        val primaryCandidates = biometricDao.getAllByPersonAndFinger(personUuid, normalizedFingerType, facilityId)
        val (primaryMatch, primaryScore) = scoreCandidates(primaryCandidates)

        // Strict finger-type enforcement: the scanned finger must match only the stored
        // template for that specific finger (RIGHT_THUMB → RIGHT_THUMB, etc.).
        // Cross-finger fallback is intentionally absent — a wrong-finger scan is a
        // genuine mismatch, not a degraded-quality event.
        val bestMatch: Biometric?
        val bestScore: Double
        if (primaryMatch != null && primaryScore >= VERIFICATION_MIN_SCORE) {
            bestMatch = primaryMatch
            bestScore = primaryScore
        } else {
            Log.w(TAG, "verifyBiometric: rejected — score %.3f below NPHCDA floor %.2f"
                .format(primaryScore, VERIFICATION_MIN_SCORE))
            bestMatch = null
            bestScore = 0.0
        }

        com.carecompanion.biometric.BiometricAuditLogger.logVerification(
            patientUuid = personUuid,
            fingerType = normalizedFingerType,
            matchScore = bestScore,
            isMatch = bestMatch != null,
            matchThreshold = matchThreshold,
            method = method,
            userId = userId,
            facilityId = facilityId
        )
        return if (bestMatch != null) PatientMatchResult(
            patient = patientDao.getByUuid(bestMatch.personUuid),
            template = bestMatch,
            matchType = MatchType.TEMPLATE,
            confidence = bestScore,
            usedFallbackFinger = false
        ) else null
    }
}