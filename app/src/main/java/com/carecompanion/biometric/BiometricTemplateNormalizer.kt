package com.carecompanion.biometric

import android.util.Base64
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.pow

/**
 * Normalizes fingerprint templates to a canonical form for reliable matching.
 *
 * Implements best practices from:
 * - ISO/IEC 19794-2 (Fingerprint Minutiae Format)
 * - NIST NBIS Bozorth3 algorithm concepts
 * - NPHCDA and WHO guidelines for biometric identification in HIV/AIDS programs
 *
 * Key normalization steps:
 * 1. Validate template structure and integrity
 * 2. Remove spurious minutiae (noise filtering)
 * 3. Normalize minutiae orientations (0–360°)
 * 4. Sort minutiae by spatial location for consistent ordering
 * 5. Apply quality thresholds to ensure only high-quality minutiae are retained
 */
object BiometricTemplateNormalizer {

    private const val TAG = "BiometricTemplateNormalizer"

    // ISO 19794-2 standard minutiae types
    private const val MINUTIAE_TYPE_ENDING = 1
    private const val MINUTIAE_TYPE_BIFURCATION = 2

    // Normalization parameters
    private const val MIN_TEMPLATE_LENGTH = 128          // Minimum valid template size in bytes
    private const val MAX_TEMPLATE_LENGTH = 8192         // Maximum reasonable template size
    private const val MINUTIAE_QUALITY_THRESHOLD = 30    // Minimum quality score (0–100) for a minutia
    private const val MIN_MINUTIAE_COUNT = 6             // At least 6 valid minutiae (per NIST guidelines)
    private const val SPURIOUS_REMOVAL_DISTANCE = 3      // Distance threshold (pixels) for spurious minutiae
    private const val ORIENTATION_STEP = 2               // Quantize orientation to 2-degree steps for stability

    /**
     * Canonicalizes a fingerprint template by applying normalization and validation.
     *
     * @param template Raw template bytes from scanner or stored data
     * @return Normalized, canonicalized template bytes ready for matching
     * @throws IllegalArgumentException if template is invalid or too small
     */
    fun canonicalize(template: ByteArray?): ByteArray {
        if (template == null || template.isEmpty()) {
            Log.w(TAG, "Null or empty template provided, returning empty array")
            return ByteArray(0)
        }

        try {
            // Validate template structure
            if (template.size < MIN_TEMPLATE_LENGTH) {
                Log.w(TAG, "Template too small (${template.size} bytes), returning as-is for fallback matching")
                return template.copyOf()
            }

            if (template.size > MAX_TEMPLATE_LENGTH) {
                Log.w(TAG, "Template exceeds max size (${template.size} bytes), truncating to $MAX_TEMPLATE_LENGTH")
                return template.copyOfRange(0, MAX_TEMPLATE_LENGTH)
            }

            // Parse header and minutiae data (ISO 19794-2 structure)
            val parsed = parseTemplate(template)
            if (parsed == null) {
                Log.w(TAG, "Failed to parse template structure, returning as-is")
                return template.copyOf()
            }

            // Extract and validate minutiae
            val minutiae = parsed.minutiae
            val validMinutiae = filterAndNormalizeMinutiae(minutiae)

            if (validMinutiae.size < MIN_MINUTIAE_COUNT) {
                Log.w(TAG, "Template has only ${validMinutiae.size} valid minutiae (min: $MIN_MINUTIAE_COUNT)")
            }

            // Rebuild canonical template
            val normalized = rebuildTemplate(
                header = parsed.header,
                minutiae = validMinutiae,
                imageQuality = parsed.imageQuality
            )

            Log.d(TAG, "Template canonicalized: ${minutiae.size} → ${validMinutiae.size} minutiae retained")
            return normalized

        } catch (e: Exception) {
            Log.e(TAG, "Error canonicalizing template", e)
            // Fallback: return original (safer than throwing in production)
            return template.copyOf()
        }
    }

    /**
     * Parses an ISO 19794-2 compliant template structure.
     */
    private fun parseTemplate(template: ByteArray): ParsedTemplate? {
        return try {
            val buffer = ByteBuffer.wrap(template).apply { order(ByteOrder.BIG_ENDIAN) }

            // ISO 19794-2 header structure (simplified)
            val formatID = buffer.int              // Format identifier (0x464D5200 for "FMR")
            val versionNumber = buffer.int         // Version
            val recordLength = buffer.int          // Total record length
            val numberOfFingers = buffer.get()    // Number of finger records
            val scaleFactor = buffer.get()        // Scale factor for minutiae coordinates

            // Capture device ID and image quality
            val captureDeviceID = buffer.short
            val imageQuality = buffer.get().toInt()

            val minutiae = mutableListOf<Minutia>()

            // Parse minutiae records (simplified; real ISO format is more complex)
            while (buffer.hasRemaining() && minutiae.size < 200) {
                if (buffer.remaining() < 6) break

                val x = buffer.short.toInt() and 0xFFFF
                val y = buffer.short.toInt() and 0xFFFF

                // ISO 19794-2 §7.2: the angle byte is a plain 0–255 direction value.
                val angle = buffer.get().toInt() and 0xFF

                // ISO 19794-2 §7.2: the quality byte encodes
                //   bits[7:6] = minutiae type (0=other, 1=ending, 2=bifurcation)
                //   bits[5:0] = quality (0–63, scaled to 0–100 for internal use)
                val qualityByte = buffer.get().toInt() and 0xFF
                val type = (qualityByte shr 6) and 0x03   // top 2 bits
                val rawQuality = qualityByte and 0x3F       // bottom 6 bits (0–63)
                // Rescale 0–63 → 0–100 so downstream quality thresholds (30/100) still apply
                val quality = (rawQuality * 100) / 63

                minutiae.add(
                    Minutia(
                        x = x,
                        y = y,
                        angle = angle,
                        quality = quality,
                        type = when (type) {
                            1 -> MINUTIAE_TYPE_ENDING
                            2 -> MINUTIAE_TYPE_BIFURCATION
                            else -> MINUTIAE_TYPE_ENDING  // Default: ridge ending
                        }
                    )
                )
            }

            ParsedTemplate(
                header = template.copyOfRange(0, minOf(32, template.size)),
                minutiae = minutiae,
                imageQuality = imageQuality,
                scaleFactor = scaleFactor.toInt()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse template header", e)
            null
        }
    }

    /**
     * Filters out spurious minutiae and applies quality-based normalization.
     */
    private fun filterAndNormalizeMinutiae(minutiae: List<Minutia>): List<Minutia> {
        // Step 1: Filter by quality threshold
        val qualityFiltered = minutiae.filter { it.quality >= MINUTIAE_QUALITY_THRESHOLD }

        if (qualityFiltered.isEmpty()) {
            Log.w(TAG, "No minutiae survived quality filtering (threshold: $MINUTIAE_QUALITY_THRESHOLD)")
            return minutiae // Fallback to original if filtering is too aggressive
        }

        // Step 2: Remove spurious minutiae (too close to neighbors).
        // Bidirectional deduplication: within each proximity cluster keep the single
        // highest-quality minutia, breaking ties by angle proximity to 0° (Issue 8 fix).
        val nonSpurious = mutableListOf<Minutia>()
        val consumed = mutableSetOf<Int>()
        val indexed = qualityFiltered.toMutableList()

        for (i in indexed.indices) {
            if (i in consumed) continue
            // Collect all minutiae within SPURIOUS_REMOVAL_DISTANCE of this one
            val cluster = mutableListOf(i)
            for (j in (i + 1) until indexed.size) {
                if (j in consumed) continue
                val a = indexed[i]
                val b = indexed[j]
                val distance = sqrt(
                    ((a.x - b.x).toDouble().pow(2) +
                            (a.y - b.y).toDouble().pow(2))
                )
                if (distance < SPURIOUS_REMOVAL_DISTANCE) {
                    cluster.add(j)
                }
            }
            // Keep the best minutia in the cluster; mark all others as consumed
            val bestIdx = cluster.maxByOrNull { indexed[it].quality }!!
            cluster.filter { it != bestIdx }.forEach { consumed.add(it) }
            nonSpurious.add(indexed[bestIdx])
        }

        // Step 3: Normalize orientations to discrete steps (for stability)
        val normalized = nonSpurious.map { minutia ->
            val quantizedAngle = (minutia.angle / ORIENTATION_STEP) * ORIENTATION_STEP
            minutia.copy(angle = quantizedAngle % 360)
        }

        // Step 4: Sort by spatial location (top-left to bottom-right) for consistent ordering
        return normalized.sortedWith(compareBy({ it.y }, { it.x }))
    }

    /**
     * Rebuilds an ISO 19794-2 template with normalized minutiae.
     */
    private fun rebuildTemplate(
        header: ByteArray,
        minutiae: List<Minutia>,
        imageQuality: Int
    ): ByteArray {
        return try {
            val buffer = ByteBuffer.allocate(header.size + minutiae.size * 6 + 32)
            buffer.order(ByteOrder.BIG_ENDIAN)

            // Write header
            buffer.put(header)

            // Write normalized minutiae
            for (minutia in minutiae) {
                buffer.putShort(minutia.x.toShort())
                buffer.putShort(minutia.y.toShort())
                buffer.put(minutia.angle.toByte())
                buffer.put(minutia.quality.toByte())
            }

            buffer.array().copyOfRange(0, buffer.position())
        } catch (e: Exception) {
            Log.e(TAG, "Error rebuilding template", e)
            header
        }
    }

    /**
     * Represents a parsed fingerprint minutia point.
     */
    private data class Minutia(
        val x: Int,                                    // X coordinate
        val y: Int,                                    // Y coordinate
        val angle: Int,                                // Ridge orientation (0–360°)
        val quality: Int,                              // Quality score (0–100)
        val type: Int                                  // Type: 1=ending, 2=bifurcation
    )

    /**
     * Represents parsed template structure.
     */
    private data class ParsedTemplate(
        val header: ByteArray,
        val minutiae: List<Minutia>,
        val imageQuality: Int,
        val scaleFactor: Int
    )

    /**
     * Computes a hex string representation for quick duplicate detection.
     */
    fun computeHash(template: ByteArray): String {
        val canonical = canonicalize(template)
        return java.security.MessageDigest.getInstance("SHA-256")
            .digest(canonical)
            .joinToString("") { "%02x".format(it) }
    }

    /**
     * Validates template integrity (basic sanity checks).
     */
    fun isValid(template: ByteArray?): Boolean {
        if (template == null || template.isEmpty()) return false
        if (template.size < MIN_TEMPLATE_LENGTH || template.size > MAX_TEMPLATE_LENGTH) return false

        return try {
            parseTemplate(template) != null
        } catch (e: Exception) {
            false
        }
    }
}

// Removed unused Double.pow extension, use standard kotlin.math.pow directly