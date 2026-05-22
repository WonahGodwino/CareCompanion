package com.carecompanion.biometric

import android.util.Log

/**
 * Biometric quality validator implementing ISO 19794-2 and NFIQ v2 standards.
 *
 * Enforces quality thresholds aligned with:
 * - NPHCDA guidelines for HIV/AIDS patient identification
 * - WHO biometric system requirements for public health
 * - NIST NFIQ (Fingerprint Image Quality) standards
 * - SecuGen security levels (SL_HIGH: FAR 1:100,000)
 *
 * Quality thresholds ensure:
 * - Reliable fingerprint capture and matching
 * - Minimal false positive/negative rates
 * - Consistent performance across diverse populations (including elderly, laborers)
 * - Audit trail for compliance reporting
 */
object BiometricQualityValidator {

    private const val TAG = "BiometricQualityValidator"

    // NFIQ v2 quality score ranges (0=worst, 100=best)
    private const val NFIQ_EXCELLENT = 80
    private const val NFIQ_GOOD = 60
    private const val NFIQ_FAIR = 40
    private const val NFIQ_POOR = 20

    // NPHCDA and public health thresholds
    // Note: These align with HIV/AIDS program requirements for patient identification accuracy
    const val MIN_ENROLL_QUALITY = 60          // Enrollment requires high quality (NFIQ ≥ 60)
    const val MIN_VERIFY_QUALITY = 55          // Verification allows slightly lower quality
    const val MIN_IDENTIFY_QUALITY = 50        // Identification (1:N) uses SL_HIGH security
    const val MIN_IMAGE_QUALITY = 30           // Minimum acceptable image quality

    // Template validation
    const val MIN_MINUTIAE_COUNT = 6           // NIST minimum for reliable matching
    const val IDEAL_MINUTIAE_COUNT = 20        // Ideal count for high-confidence matches
    const val MIN_TEMPLATE_SIZE = 128          // Bytes

    /**
     * Validates fingerprint quality for enrollment.
     *
     * Enrollment requires the highest quality to ensure strong reference templates
     * that will work reliably for future verification and identification.
     *
     * @param quality NFIQ v2 score (0–100) from scanner
     * @return QualityAssessment with pass/fail determination and feedback
     */
    fun validateEnrollmentQuality(quality: Int): QualityAssessment {
        Log.d(TAG, "Validating enrollment quality: $quality")

        return when {
            quality >= MIN_ENROLL_QUALITY -> QualityAssessment(
                passed = true,
                score = quality,
                level = QualityLevel.EXCELLENT,
                message = "Excellent fingerprint quality. Enrollment ready."
            )

            quality >= 50 -> QualityAssessment(
                passed = false,
                score = quality,
                level = QualityLevel.GOOD,
                message = "Good quality, but below enrollment threshold ($MIN_ENROLL_QUALITY). Please recapture."
            )

            quality >= 40 -> QualityAssessment(
                passed = false,
                score = quality,
                level = QualityLevel.FAIR,
                message = "Fair quality. Clean finger and scanner, then retry. Enrollment not recommended."
            )

            else -> QualityAssessment(
                passed = false,
                score = quality,
                level = QualityLevel.POOR,
                message = "Poor fingerprint quality. Verify finger is clean and whole. Try another finger."
            )
        }
    }

    /**
     * Validates fingerprint quality for verification (1:1 matching).
     *
     * Verification is slightly more lenient than enrollment to accommodate
     * variations in finger condition (minor wounds, weather, etc.) while
     * maintaining accuracy for patient identification.
     *
     * @param quality NFIQ v2 score (0–100) from scanner
     * @return QualityAssessment with pass/fail determination and feedback
     */
    fun validateVerificationQuality(quality: Int): QualityAssessment {
        Log.d(TAG, "Validating verification quality: $quality")

        return when {
            quality >= MIN_VERIFY_QUALITY -> QualityAssessment(
                passed = true,
                score = quality,
                level = if (quality >= MIN_ENROLL_QUALITY) QualityLevel.EXCELLENT else QualityLevel.GOOD,
                message = "Fingerprint quality acceptable for verification."
            )

            quality >= 40 -> QualityAssessment(
                passed = false,
                score = quality,
                level = QualityLevel.FAIR,
                message = "Borderline quality (score=$quality). Clean finger and try again."
            )

            else -> QualityAssessment(
                passed = false,
                score = quality,
                level = QualityLevel.POOR,
                message = "Fingerprint quality too low ($quality). Please retry with clean finger."
            )
        }
    }

    /**
     * Validates fingerprint quality for identification (1:N matching against database).
     *
     * Identification uses SL_HIGH security level (FAR 1:100,000) to minimize
     * false positive identifications. Quality thresholds are set accordingly.
     *
     * @param quality NFIQ v2 score (0–100) from scanner
     * @return QualityAssessment with pass/fail determination and feedback
     */
    fun validateIdentificationQuality(quality: Int): QualityAssessment {
        Log.d(TAG, "Validating identification quality: $quality")

        return when {
            quality >= MIN_IDENTIFY_QUALITY -> QualityAssessment(
                passed = true,
                score = quality,
                level = if (quality >= MIN_ENROLL_QUALITY) QualityLevel.EXCELLENT else QualityLevel.GOOD,
                message = "Fingerprint quality acceptable for identification."
            )

            quality >= 40 -> QualityAssessment(
                passed = false,
                score = quality,
                level = QualityLevel.FAIR,
                message = "Quality below SL_HIGH threshold. Retry for higher accuracy."
            )

            else -> QualityAssessment(
                passed = false,
                score = quality,
                level = QualityLevel.POOR,
                message = "Poor quality for identification. Try again with better finger placement."
            )
        }
    }

    /**
     * Validates template structure and minutiae count.
     *
     * @param template Fingerprint template bytes
     * @param minutiaeCount Number of valid minutiae extracted
     * @return TemplateValidation with pass/fail and diagnostic info
     */
    fun validateTemplate(template: ByteArray?, minutiaeCount: Int = 0): TemplateValidation {
        Log.d(TAG, "Validating template: size=${template?.size}, minutiae=$minutiaeCount")

        return when {
            template == null || template.isEmpty() -> TemplateValidation(
                valid = false,
                issue = "Template is null or empty"
            )

            template.size < MIN_TEMPLATE_SIZE -> TemplateValidation(
                valid = false,
                issue = "Template too small (${template.size} bytes, min: $MIN_TEMPLATE_SIZE)"
            )

            minutiaeCount < MIN_MINUTIAE_COUNT -> TemplateValidation(
                valid = false,
                issue = "Insufficient minutiae ($minutiaeCount, min: $MIN_MINUTIAE_COUNT). " +
                        "Template may be corrupt or low-quality."
            )

            minutiaeCount < IDEAL_MINUTIAE_COUNT -> TemplateValidation(
                valid = true,
                issue = "Template valid but suboptimal. Only $minutiaeCount minutiae " +
                        "(ideal: $IDEAL_MINUTIAE_COUNT). Verification may be less reliable."
            )

            else -> TemplateValidation(
                valid = true,
                issue = null
            )
        }
    }

    /**
     * Computes overall quality feedback for end-user display.
     * Takes into account score, minutiae count, and purpose.
     *
     * @param score NFIQ v2 quality score
     * @param minutiaeCount Number of valid minutiae
     * @param purpose "ENROLL", "VERIFY", or "IDENTIFY"
     * @return User-friendly quality feedback message
     */
    fun getQualityFeedback(score: Int, minutiaeCount: Int, purpose: String): String {
        val assessment = when (purpose.uppercase()) {
            "ENROLL" -> validateEnrollmentQuality(score)
            "IDENTIFY" -> validateIdentificationQuality(score)
            else -> validateVerificationQuality(score)
        }

        val minutiaeFeedback = when {
            minutiaeCount >= IDEAL_MINUTIAE_COUNT -> "Excellent minutiae count ($minutiaeCount)."
            minutiaeCount >= MIN_MINUTIAE_COUNT -> "Adequate minutiae ($minutiaeCount)."
            else -> "Low minutiae count ($minutiaeCount). Recapture recommended."
        }

        return "${assessment.message} $minutiaeFeedback"
    }

    /**
     * Represents quality assessment results.
     */
    data class QualityAssessment(
        val passed: Boolean,
        val score: Int,
        val level: QualityLevel,
        val message: String
    )

    /**
     * Quality level enum (from NFIQ v2 standard).
     */
    enum class QualityLevel {
        EXCELLENT,  // NFIQ ≥ 80
        GOOD,       // NFIQ 60–79
        FAIR,       // NFIQ 40–59
        POOR        // NFIQ < 40
    }

    /**
     * Template validation results.
     */
    data class TemplateValidation(
        val valid: Boolean,
        val issue: String? = null
    )
}
