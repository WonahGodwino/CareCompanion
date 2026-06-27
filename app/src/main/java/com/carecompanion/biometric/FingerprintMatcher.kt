package com.carecompanion.biometric

import android.util.Log
import com.carecompanion.biometric.models.MatchResult
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Offline minutiae-based fingerprint matcher aligned with NIST Bozorth3 principles
 * and NPHCDA / WHO biometric standards for HIV/AIDS programs.
 *
 * References:
 *   - NIST NBIS Bozorth3 (public-domain reference implementation)
 *   - ISO/IEC 19794-2:2005  – Fingerprint Minutiae Format
 *   - NPHCDA Biometric Technical Specification (Nigeria)
 *   - PEPFAR / WHO HIV/AIDS Patient Identification Standards
 *
 * Used as the offline fallback when the SecuGen hardware SDK is unavailable.
 * When the scanner is connected the SecuGen SDK matcher takes precedence.
 *
 * Key fixes applied (vs. previous version):
 *   1. Rotation is now applied to (x,y) coordinates, not just to the angle —
 *      the previous code produced zero effective rotation tolerance for coordinates.
 *   2. One-to-one matching: each reference minutia may only be claimed once per
 *      alignment attempt, preventing duplicate matches from inflating scores.
 *   3. Score penalty uses the actual post-transformation distance and angle error
 *      stored in MinutiaeMatch, not raw unaligned coordinates.
 *   4. Translation-overflow guard: anchor pairs whose implied translation exceeds
 *      MAX_TRANSLATION_TOLERANCE are skipped, tightening the search space.
 */
class FingerprintMatcher {

    companion object {
        private const val TAG = "FingerprintMatcher"

        // ── Alignment parameters ────────────────────────────────────────────────
        private const val MAX_TRANSLATION_TOLERANCE = 100   // pixels; skip anchors outside this range
        private const val MINUTIAE_DISTANCE_THRESHOLD = 10  // px Euclidean tolerance for a spatial match
        private const val MINUTIAE_ANGLE_TOLERANCE = 12     // ISO angle units (0–255 scale); 12 units ≈ 16.9° (1 unit = 360/256 ≈ 1.406°)
        private const val MIN_MINUTIAE_MATCHES = 6          // NIST minimum is 12 for court evidence; 6 is acceptable for clinical identification

        // ── Score penalty weights (NIST recommendation) ─────────────────────────
        private const val DISTANCE_WEIGHT = 0.4
        private const val ANGLE_WEIGHT = 0.3

        // ── Search budget ───────────────────────────────────────────────────────
        private const val MAX_ANCHORS = 200

        // Coarse rotation steps; ± 45° covers the typical finger placement range.
        // Applied as signed degrees (positive = counterclockwise in image coords).
        private val ROTATION_STEPS = listOf(0, -15, 15, -30, 30, -45, 45)

        // ── Thresholds (score 0–100, same scale as this matcher's output) ────────
        // Must stay in sync with IDENTIFICATION_MIN_SCORE and VERIFICATION_MIN_SCORE
        // in SyncRepositoryImpl (stored as 0.0–1.0, multiply by 100 to compare here).
        const val VERIFICATION_THRESHOLD   = 40.0   // 1:1  — 0.40 × 100
        const val IDENTIFICATION_THRESHOLD = 35.0   // 1:N  — 0.35 × 100
        const val ENROLLMENT_THRESHOLD     = 35.0   // quality gate during enrolment
    }

    // ── Internal types ───────────────────────────────────────────────────────────

    private data class Minutia(
        val x: Int,
        val y: Int,
        val angle: Int,   // 0–255 (raw ISO byte value; 1 unit ≈ 1.4°)
        val quality: Int  // 0–255 (raw ISO byte value after type-bits masking)
    )

    /**
     * Carries the actual post-transformation residuals for each matched pair so
     * [computeScore] can compute penalties from real alignment geometry.
     */
    private data class MinutiaeMatch(
        val probe: Minutia,
        val ref: Minutia,
        val distance: Int,   // Euclidean pixels after rotation + translation
        val angleDiff: Int   // absolute angular residual after rotation (ISO units, 0–127)
    )

    // ── Public API ───────────────────────────────────────────────────────────────

    /**
     * Matches [probe] against [reference] and returns a [MatchResult] with a
     * score in 0–100 and a boolean decision against the appropriate [purpose]
     * threshold.
     *
     * Both templates must already be in the canonical form produced by
     * [BiometricTemplateNormalizer.canonicalize] (ISO 19794-2, 28-byte header).
     *
     * @param purpose "VERIFY" (1:1), "IDENTIFY" (1:N), or "ENROLL" (quality check)
     */
    fun match(probe: ByteArray, reference: ByteArray, purpose: String = "VERIFY"): MatchResult {
        if (probe.isEmpty() || reference.isEmpty()) {
            Log.w(TAG, "Empty template — cannot match")
            return MatchResult(score = 0.0, isMatch = false)
        }
        return try {
            val probeMinutiae = extractMinutiae(probe)
            val refMinutiae   = extractMinutiae(reference)

            if (probeMinutiae.isEmpty() || refMinutiae.isEmpty()) {
                Log.w(TAG, "No minutiae extracted (probe=${probeMinutiae.size}, ref=${refMinutiae.size})")
                return MatchResult(score = 0.0, isMatch = false)
            }

            val alignment = findBestAlignment(probeMinutiae, refMinutiae)
            val score     = computeScore(alignment, probeMinutiae, refMinutiae)

            val threshold = when (purpose.uppercase()) {
                "VERIFY"   -> VERIFICATION_THRESHOLD
                "IDENTIFY" -> IDENTIFICATION_THRESHOLD
                "ENROLL"   -> ENROLLMENT_THRESHOLD
                else       -> VERIFICATION_THRESHOLD
            }
            val isMatch = score >= threshold
            Log.d(TAG, "$purpose: score=%.1f threshold=%.1f pairs=${alignment.size} probe=${probeMinutiae.size} ref=${refMinutiae.size}"
                .format(score, threshold))
            MatchResult(score = score, isMatch = isMatch)

        } catch (e: Exception) {
            Log.e(TAG, "Error during matching", e)
            MatchResult(score = 0.0, isMatch = false)
        }
    }

    // ── Minutiae extraction ──────────────────────────────────────────────────────

    /**
     * Reads minutiae records from a canonicalized ISO 19794-2 template.
     * Minutiae start at byte 28 (24-byte general header + 4-byte finger-view header),
     * matching BiometricTemplateNormalizer.HEADER_SIZE.
     * Each record is 6 bytes: x(2) y(2) angle(1) quality(1).
     */
    private fun extractMinutiae(template: ByteArray): List<Minutia> {
        val minutiae = mutableListOf<Minutia>()
        try {
            // Respect the declared minutiae count (ISO byte 27); never read into any
            // trailing extended-data block.
            val declaredCount = if (template.size > 27) template[27].toInt() and 0xFF else 0
            var offset = 28  // ISO 19794-2: general header (24 B) + finger-view header (4 B)
            var read = 0
            while (offset + 6 <= template.size && read < declaredCount) {
                val x       = ((template[offset].toInt()     and 0xFF) shl 8) or (template[offset + 1].toInt() and 0xFF)
                val y       = ((template[offset + 2].toInt() and 0xFF) shl 8) or (template[offset + 3].toInt() and 0xFF)
                val angle   = template[offset + 4].toInt() and 0xFF
                val quality = template[offset + 5].toInt() and 0xFF
                // Do NOT gate on the per-minutia quality byte: many vendor templates
                // (e.g. SecuGen Mobile) store quality 0 for every minutia, which would
                // otherwise drop the entire template and force a 0.0 match score.
                minutiae.add(Minutia(x, y, angle, quality))
                offset += 6
                read++
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing minutiae: ${e.message}")
        }
        return minutiae
    }

    // ── Alignment search ─────────────────────────────────────────────────────────

    /**
     * Searches for the (translation, rotation) pair that maximises the number of
     * matched minutia pairs.  Returns the best alignment found.
     *
     * Complexity is bounded to O(MAX_ANCHORS × |ROTATION_STEPS| × n × m) by the
     * anchor budget and early-exit on near-perfect matches.
     */
    private fun findBestAlignment(probe: List<Minutia>, reference: List<Minutia>): List<MinutiaeMatch> {
        var best = emptyList<MinutiaeMatch>()
        val budget = min(MAX_ANCHORS, reference.size * probe.size)
        var tried = 0

        outer@ for (refAnchor in reference) {
            for (probeAnchor in probe) {
                val deltaX = refAnchor.x - probeAnchor.x
                val deltaY = refAnchor.y - probeAnchor.y

                // Skip anchor pairs whose implied translation exceeds the tolerance —
                // a properly placed finger should not shift more than MAX_TRANSLATION_TOLERANCE px.
                if (abs(deltaX) > MAX_TRANSLATION_TOLERANCE || abs(deltaY) > MAX_TRANSLATION_TOLERANCE) continue

                for (rotation in ROTATION_STEPS) {
                    val alignment = alignMinutiae(probe, reference, deltaX, deltaY, rotation)
                    if (alignment.size > best.size) {
                        best = alignment
                        // Early exit: all (or all-but-one) probe minutiae accounted for
                        if (best.size >= min(probe.size, reference.size) - 1) break@outer
                    }
                }
                if (++tried >= budget) break@outer
            }
        }
        return best
    }

    /**
     * Aligns probe minutiae to the reference frame using [deltaX]/[deltaY] translation
     * and [rotationDegrees] rotation, then counts coincident pairs within the spatial
     * and angular tolerances.
     *
     * Rotation is applied to the (x, y) coordinates using the standard 2-D rotation
     * matrix — the previous implementation only rotated the angle field, providing
     * zero spatial rotation tolerance.
     *
     * One-to-one constraint: once a reference minutia has been claimed it is excluded
     * from further matching in this alignment attempt, preventing duplicate matches
     * from inflating the pair count and the score.
     */
    private fun alignMinutiae(
        probe: List<Minutia>,
        reference: List<Minutia>,
        deltaX: Int,
        deltaY: Int,
        rotationDegrees: Int
    ): List<MinutiaeMatch> {
        val aligned     = mutableListOf<MinutiaeMatch>()
        val consumedRef = mutableSetOf<Int>()  // one-to-one: each reference minutia claimed at most once

        val rad  = Math.toRadians(rotationDegrees.toDouble())
        val cosR = cos(rad)
        val sinR = sin(rad)

        for (pm in probe) {
            // Apply 2-D rotation to the probe coordinate, then translate into the reference frame.
            val rx = (pm.x * cosR - pm.y * sinR).roundToInt() + deltaX
            val ry = (pm.x * sinR + pm.y * cosR).roundToInt() + deltaY
            // ISO 19794-2 angles are stored in a byte (0–255) representing 0°–360°, 1 LSB ≈ 1.406°.
            // Convert rotationDegrees to the same ISO scale before adding, and wrap at 256.
            val rotationIso = (rotationDegrees * 256.0 / 360.0).roundToInt()
            val ra = (pm.angle + rotationIso + 256) and 0xFF

            for ((idx, rm) in reference.withIndex()) {
                if (idx in consumedRef) continue

                val dx       = (rx - rm.x).toDouble()
                val dy       = (ry - rm.y).toDouble()
                val distance = sqrt(dx * dx + dy * dy).roundToInt()

                val rawAngle  = abs(ra - rm.angle)
                val angleDiff = min(rawAngle, 256 - rawAngle)  // wrap-aware diff in ISO units

                if (distance <= MINUTIAE_DISTANCE_THRESHOLD && angleDiff <= MINUTIAE_ANGLE_TOLERANCE) {
                    aligned.add(MinutiaeMatch(pm, rm, distance, angleDiff))
                    consumedRef.add(idx)   // claim this reference minutia
                    break
                }
            }
        }
        return aligned
    }

    // ── Scoring ──────────────────────────────────────────────────────────────────

    /**
     * Computes a quality-weighted Bozorth3-style match score in the range 0–100.
     *
     * Formula (per matched pair):
     *   base       = matchedPairs / min(probeCount, refCount)
     *   distPenalty  = mean(match.distance / DISTANCE_THRESHOLD)   clamped to [0,1]
     *   anglePenalty = mean(match.angleDiff / ANGLE_TOLERANCE)      clamped to [0,1]
     *   score = base × (1 – distPenalty × DISTANCE_WEIGHT) × (1 – anglePenalty × ANGLE_WEIGHT) × 100
     *
     * Because one-to-one matching is enforced, matchedPairs ≤ min(n,m) so
     * baseRatio is always ≤ 1.0 and score is always ≤ 100 before clamping.
     *
     * Penalties are calculated from the actual post-transformation residuals stored
     * in each [MinutiaeMatch], not from raw unaligned probe/reference coordinates.
     */
    private fun computeScore(
        alignment: List<MinutiaeMatch>,
        probeMinutiae: List<Minutia>,
        referenceMinutiae: List<Minutia>
    ): Double {
        val matchedPairs = alignment.size
        if (matchedPairs < MIN_MINUTIAE_MATCHES) return 0.0

        val minCount = min(probeMinutiae.size, referenceMinutiae.size)
        if (minCount == 0) return 0.0

        val baseRatio = matchedPairs.toDouble() / minCount.toDouble()

        var totalDistError  = 0.0
        var totalAngleError = 0.0
        for (m in alignment) {
            totalDistError  += (m.distance.toDouble() / MINUTIAE_DISTANCE_THRESHOLD).coerceAtMost(1.0)
            totalAngleError += (m.angleDiff.toDouble() / MINUTIAE_ANGLE_TOLERANCE).coerceAtMost(1.0)
        }
        val meanDistPenalty  = totalDistError  / matchedPairs
        val meanAnglePenalty = totalAngleError / matchedPairs

        val score = baseRatio *
            (1.0 - meanDistPenalty  * DISTANCE_WEIGHT) *
            (1.0 - meanAnglePenalty * ANGLE_WEIGHT) *
            100.0

        return score.coerceIn(0.0, 100.0)
    }
}
