package com.carecompanion.biometric

import android.util.Log
import com.carecompanion.utils.DateUtils
import java.util.Date

/**
 * Biometric audit logger for security, compliance, and forensic analysis.
 *
 * Implements logging standards for:
 * - NPHCDA HIV/AIDS program compliance
 * - WHO data protection and audit requirements
 * - HIPAA/GDPR-like privacy protections for patient data
 * - Forensic investigation of biometric failures
 *
 * Logs include:
 * - All biometric operations (capture, match, enroll, identify)
 * - Match outcomes and confidence scores
 * - Quality metrics and failure reasons
 * - User actions and facility location
 * - Timestamps for audit trail
 *
 * All logs are stored locally with optional sync to secure backend.
 */
object BiometricAuditLogger {

    private const val TAG = "BiometricAuditLogger"

    /**
     * Logs a biometric capture event.
     *
     * @param fingerType e.g., "RIGHT_THUMB", "LEFT_INDEX"
     * @param quality Quality score from scanner (0–100)
     * @param status Capture status: "SUCCESS", "POOR_QUALITY", "TIMEOUT", "ERROR"
     * @param errorMessage Optional error details
     * @param userId User performing the capture (for audit)
     * @param facilityId Facility where capture occurred
     */
    fun logCapture(
        fingerType: String,
        quality: Int,
        status: String,
        errorMessage: String? = null,
        userId: String? = null,
        facilityId: Long? = null
    ) {
        val timestamp = DateUtils.formatIso8601(Date())
        val message = buildString {
            append("[$timestamp] CAPTURE event=fingerprint_capture")
            append(" fingerType=$fingerType")
            append(" quality=$quality")
            append(" status=$status")
            if (errorMessage != null) append(" error=$errorMessage")
            if (userId != null) append(" userId=$userId")
            if (facilityId != null) append(" facilityId=$facilityId")
        }

        when (status) {
            "SUCCESS" -> Log.i(TAG, message)
            "ERROR" -> Log.e(TAG, message)
            else -> Log.w(TAG, message)
        }

        // Persist to local database (future: BiometricAuditLog table)
        persistToDatabase("CAPTURE", message)
    }

    /**
     * Logs a biometric verification (1:1) event.
     *
     * @param patientUuid UUID of patient being verified
     * @param fingerType Finger used for verification
     * @param matchScore Confidence score (0.0–100.0)
     * @param isMatch Whether match threshold was exceeded
     * @param matchThreshold Threshold used for decision
     * @param method "SDK" or "FALLBACK" (algorithm used)
     * @param userId User performing verification
     * @param facilityId Facility location
     */
    fun logVerification(
        patientUuid: String,
        fingerType: String,
        matchScore: Double,
        isMatch: Boolean,
        matchThreshold: Double,
        method: String,
        userId: String? = null,
        facilityId: Long? = null
    ) {
        val timestamp = DateUtils.formatIso8601(Date())
        val result = if (isMatch) "MATCHED" else "NOT_MATCHED"
        val message = buildString {
            append("[$timestamp] VERIFICATION event=biometric_verification")
            append(" patientUuid=${anonymizeUuid(patientUuid)}")
            append(" fingerType=$fingerType")
            append(" result=$result")
            append(" score=$matchScore")
            append(" threshold=$matchThreshold")
            append(" method=$method")
            if (userId != null) append(" userId=$userId")
            if (facilityId != null) append(" facilityId=$facilityId")
        }

        Log.i(TAG, message)
        persistToDatabase("VERIFICATION", message)

        // Alert on unexpected mismatches or low scores
        if (!isMatch && matchScore > (matchThreshold - 10)) {
            Log.w(TAG, "Close miss: score=$matchScore vs threshold=$matchThreshold")
        }
    }

    /**
     * Logs a biometric identification (1:N) event.
     *
     * @param matchedPatientUuid UUID of identified patient (null if no match)
     * @param fingerType Finger used for identification
     * @param matchScore Confidence score (0.0–100.0)
     * @param candidatesSearched Number of records searched
     * @param searchDurationMs Search execution time
     * @param method "SDK" or "FALLBACK" (algorithm used)
     * @param userId User performing identification
     * @param facilityId Facility location
     */
    fun logIdentification(
        matchedPatientUuid: String?,
        fingerType: String,
        matchScore: Double,
        candidatesSearched: Int,
        searchDurationMs: Long,
        method: String,
        userId: String? = null,
        facilityId: Long? = null
    ) {
        val timestamp = DateUtils.formatIso8601(Date())
        val result = if (matchedPatientUuid != null) "IDENTIFIED" else "NO_MATCH"
        val message = buildString {
            append("[$timestamp] IDENTIFICATION event=biometric_identification")
            append(" fingerType=$fingerType")
            append(" result=$result")
            if (matchedPatientUuid != null) append(" patientUuid=${anonymizeUuid(matchedPatientUuid)}")
            append(" score=$matchScore")
            append(" candidates=$candidatesSearched")
            append(" duration_ms=$searchDurationMs")
            append(" method=$method")
            if (userId != null) append(" userId=$userId")
            if (facilityId != null) append(" facilityId=$facilityId")
        }

        Log.i(TAG, message)
        persistToDatabase("IDENTIFICATION", message)

        // Performance monitoring
        if (searchDurationMs > 5000) {
            Log.w(TAG, "Slow identification: ${searchDurationMs}ms for $candidatesSearched candidates")
        }
    }

    /**
     * Logs a biometric enrollment event.
     *
     * @param patientUuid UUID of patient being enrolled
     * @param fingerType Finger being enrolled
     * @param quality Quality score of enrolled template
     * @param templateHash Hash of stored template (for deduplication detection)
     * @param status "SUCCESS", "DUPLICATE_DETECTED", "LOW_QUALITY", "ERROR"
     * @param errorMessage Optional error details
     * @param userId User performing enrollment
     * @param facilityId Facility location
     */
    fun logEnrollment(
        patientUuid: String,
        fingerType: String,
        quality: Int,
        templateHash: String? = null,
        status: String,
        errorMessage: String? = null,
        userId: String? = null,
        facilityId: Long? = null
    ) {
        val timestamp = DateUtils.formatIso8601(Date())
        val message = buildString {
            append("[$timestamp] ENROLLMENT event=biometric_enrollment")
            append(" patientUuid=${anonymizeUuid(patientUuid)}")
            append(" fingerType=$fingerType")
            append(" quality=$quality")
            append(" status=$status")
            if (templateHash != null) append(" templateHash=${templateHash.take(16)}...")
            if (errorMessage != null) append(" error=$errorMessage")
            if (userId != null) append(" userId=$userId")
            if (facilityId != null) append(" facilityId=$facilityId")
        }

        when (status) {
            "SUCCESS" -> Log.i(TAG, message)
            "DUPLICATE_DETECTED" -> Log.w(TAG, message)
            "ERROR" -> Log.e(TAG, message)
            else -> Log.d(TAG, message)
        }

        persistToDatabase("ENROLLMENT", message)
    }

    /**
     * Logs a scanner error or availability issue.
     *
     * @param scannerModel Scanner model/vendor (e.g., "SecuGen")
     * @param errorType Type of error: "CONNECTION_FAILED", "PERMISSION_DENIED", "TIMEOUT", etc.
     * @param errorMessage Detailed error message
     * @param userId User affected
     * @param facilityId Facility location
     */
    fun logScannerError(
        scannerModel: String,
        errorType: String,
        errorMessage: String,
        userId: String? = null,
        facilityId: Long? = null
    ) {
        val timestamp = DateUtils.formatIso8601(Date())
        val message = buildString {
            append("[$timestamp] SCANNER_ERROR event=scanner_error")
            append(" scanner=$scannerModel")
            append(" errorType=$errorType")
            append(" message=$errorMessage")
            if (userId != null) append(" userId=$userId")
            if (facilityId != null) append(" facilityId=$facilityId")
        }

        Log.e(TAG, message)
        persistToDatabase("SCANNER_ERROR", message)
    }

    /**
     * Logs suspicious activity (e.g., repeated failed attempts, unusual patterns).
     *
     * @param event Event description: "REPEATED_FAILURES", "UNUSUAL_PATTERN", etc.
     * @param patientUuid Optional patient UUID if applicable
     * @param details Additional context
     * @param userId User involved
     * @param facilityId Facility location
     */
    fun logSuspiciousActivity(
        event: String,
        patientUuid: String? = null,
        details: String? = null,
        userId: String? = null,
        facilityId: Long? = null
    ) {
        val timestamp = DateUtils.formatIso8601(Date())
        val message = buildString {
            append("[$timestamp] SECURITY_ALERT event=$event")
            if (patientUuid != null) append(" patientUuid=${anonymizeUuid(patientUuid)}")
            if (details != null) append(" details=$details")
            if (userId != null) append(" userId=$userId")
            if (facilityId != null) append(" facilityId=$facilityId")
        }

        Log.w(TAG, message)
        persistToDatabase("SECURITY_ALERT", message)
    }

    /**
     * Logs data sync events related to biometric templates.
     *
     * @param action "DOWNLOAD", "UPLOAD", "DELETE"
     * @param templateCount Number of templates affected
     * @param status "SUCCESS", "PARTIAL", "FAILED"
     * @param durationMs Duration of operation
     * @param errorMessage Optional error details
     * @param facilityId Facility location
     */
    fun logSyncEvent(
        action: String,
        templateCount: Int,
        status: String,
        durationMs: Long,
        errorMessage: String? = null,
        facilityId: Long? = null
    ) {
        val timestamp = DateUtils.formatIso8601(Date())
        val message = buildString {
            append("[$timestamp] SYNC_EVENT event=biometric_sync")
            append(" action=$action")
            append(" templates=$templateCount")
            append(" status=$status")
            append(" duration_ms=$durationMs")
            if (errorMessage != null) append(" error=$errorMessage")
            if (facilityId != null) append(" facilityId=$facilityId")
        }

        when (status) {
            "SUCCESS" -> Log.i(TAG, message)
            "PARTIAL" -> Log.w(TAG, message)
            "FAILED" -> Log.e(TAG, message)
            else -> Log.d(TAG, message)
        }

        persistToDatabase("SYNC_EVENT", message)
    }

    /**
     * Anonymizes a UUID for logging (shows only first 8 chars).
     * Reduces privacy risk in logs while maintaining trackability.
     */
    private fun anonymizeUuid(uuid: String): String {
        if (uuid.length < 8) return "****"
        return uuid.take(8) + "****"
    }

    /**
     * Persists audit log to a dated file on device storage.
     * Files are written to: Android/data/com.carecompanion/files/biometric_logs/
     */
    private fun persistToDatabase(eventType: String, message: String) {
        val level = when (eventType) {
            "SCANNER_ERROR" -> "ERROR"
            "SECURITY_ALERT" -> "WARN"
            else -> "INFO"
        }
        BiometricFileLogger.write(level = level, event = eventType, details = message)
    }
}
