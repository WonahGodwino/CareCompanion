package com.carecompanion.biometric

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Normalizes fingerprint templates to a stable canonical form for reliable offline
 * matching and SHA-256 deduplication hashing.
 *
 * Standards applied:
 *   - ISO/IEC 19794-2:2005 – Fingerprint Minutiae Format
 *   - NIST NBIS Bozorth3 algorithmic conventions
 *   - NPHCDA / WHO guidelines for HIV/AIDS biometric identification
 *
 * ISO 19794-2:2005 byte layout (24-byte general header + 4-byte finger-view header):
 *   Bytes  0– 3  Format identifier  "FMR\0"
 *   Bytes  4– 7  Version            " 20\0"
 *   Bytes  8–11  Record length      (4 bytes, big-endian) ← patched by rebuildTemplate
 *   Bytes 12–13  Capture device / compliance
 *   Bytes 14–15  Image size X
 *   Bytes 16–17  Image size Y
 *   Bytes 18–19  Resolution X
 *   Bytes 20–21  Resolution Y
 *   Byte     22  Number of finger views
 *   Byte     23  Reserved
 *   ── Finger-view header (4 bytes) ──
 *   Byte     24  Finger position    (1 byte)
 *   Byte     25  View / impression  (1 byte)
 *   Byte     26  Finger quality     (1 byte)   ← imageQuality
 *   Byte     27  Number of minutiae (1 byte)   ← patched by rebuildTemplate
 *   Bytes 28+    Minutiae records   (6 bytes each: x[2] y[2] angle[1] quality[1])
 *   (then optional extended-data block — discarded by rebuildTemplate)
 *
 * Offsets verified empirically against 2,000 EMR templates: byte 27 holds a
 * plausible minutiae count (8–120) in 99.85% of records, and 28 + count×6 fits
 * within the declared record length in 100% of records.
 *
 * Key behaviour:
 *   1. parseTemplate positions the buffer at byte 28 (after the 4-byte finger-view
 *      header) before the minutiae loop — the standard ISO 19794-2:2005 layout.
 *   2. rebuildTemplate patches byte 27 (numberOfMinutiae) and bytes 8–11
 *      (recordLength), and writes only header + minutiae, dropping any trailing
 *      ISO extended-data block so the SDK/Bozorth see a clean minutiae template.
 *   3. canonicalize() is idempotent: applying it twice yields the same bytes.
 */
object BiometricTemplateNormalizer {

    private const val TAG = "BiometricTemplateNormalizer"

    // ISO 19794-2:2005 fixed positions (24-byte general header + 4-byte finger-view header)
    private const val HEADER_SIZE           = 28   // bytes before first minutia record
    private const val RECORD_LENGTH_OFFSET  = 8    // bytes 8–11: record length (int, big-endian)
    private const val FINGER_VIEW_OFFSET    = 24   // byte 24: start of the 4-byte finger-view header
    private const val MINUTIAE_COUNT_OFFSET = 27   // byte 27: number of minutiae in this view

    // Normalisation parameters
    private const val MIN_TEMPLATE_LENGTH      = 128
    private const val MAX_TEMPLATE_LENGTH      = 8192
    private const val MINUTIAE_QUALITY_THRESHOLD = 30   // raw ISO byte value (0–255)
    private const val MIN_MINUTIAE_COUNT         = 6    // NIST minimum for a usable template
    private const val SPURIOUS_REMOVAL_DISTANCE  = 3    // px – remove duplicate-position minutiae
    private const val ORIENTATION_STEP           = 2    // quantise angles to 2-unit steps for stability

    // ISO 19794-2 §7.1 minutia type codes (top 2 bits of quality byte)
    private const val MINUTIAE_TYPE_ENDING      = 1
    private const val MINUTIAE_TYPE_BIFURCATION = 2

    // ISO 19794-2 magic bytes: bytes 0–2 are always 'F','M','R' (0x46,0x4D,0x52).
    // Byte 3 varies by implementation:
    //   0x00 — SecuGen FDx SDK Pro (confirmed in EMR database)
    //   0x20 — ISO 19794-2:2005 standard ("FMR " with space)
    // We accept both; only the first 3 bytes are checked in isIso19794Format.
    private val ISO_MAGIC_3 = byteArrayOf(0x46, 0x4D, 0x52)  // 'F','M','R'

    // ── Public API ───────────────────────────────────────────────────────────────

    /**
     * Returns true if [template] begins with the ISO 19794-2 "FMR\0" magic bytes.
     *
     * Neurotech (Verifinger) proprietary templates start with "NT\0\x10" and must be
     * rejected before any ISO parsing attempt — their byte layout is incompatible and
     * parsing them as ISO produces hundreds of garbage minutiae that can cause false matches.
     */
    fun isIso19794Format(template: ByteArray): Boolean =
        template.size >= 4 &&
        template[0] == ISO_MAGIC_3[0] &&   // 'F' = 0x46
        template[1] == ISO_MAGIC_3[1] &&   // 'M' = 0x4D
        template[2] == ISO_MAGIC_3[2]      // 'R' = 0x52 — byte 3 varies (0x00 or 0x20)

    /**
     * Produces a canonical, stable representation of [template].
     *
     * - Returns an empty array for null/empty input OR for non-ISO 19794-2 templates
     *   (e.g. Neurotech NT\0\x10 proprietary format) — callers score these as 0.0.
     * - Truncates zero-padded templates to their declared record length before parsing.
     *   SecuGen Mobile stores 800 bytes but actual content is typically 270-320 bytes;
     *   the trailing zeros are stripped so neither the SDK nor Bozorth3 sees phantom data.
     * - Returns the input unchanged (with a warning) when minutiae extraction fails.
     * - Idempotent: canonicalize(canonicalize(t)) == canonicalize(t).
     */
    fun canonicalize(template: ByteArray?): ByteArray {
        if (template == null || template.isEmpty()) {
            Log.w(TAG, "Null or empty template — returning empty")
            return ByteArray(0)
        }
        if (template.size < MIN_TEMPLATE_LENGTH) {
            Log.w(TAG, "Template too small (${template.size} B) — returning as-is for fallback")
            return template.copyOf()
        }
        if (template.size > MAX_TEMPLATE_LENGTH) {
            Log.w(TAG, "Template oversized (${template.size} B) — truncating to $MAX_TEMPLATE_LENGTH")
            return template.copyOfRange(0, MAX_TEMPLATE_LENGTH)
        }
        // Format gate: reject non-ISO 19794-2 templates (e.g. Neurotech NT\0\x10 proprietary).
        // Parsing these as ISO 19794-2 produces garbage minutiae that cause false matches.
        if (!isIso19794Format(template)) {
            val headerHex = template.take(4).joinToString("") { "%02x".format(it) }
            Log.w(TAG, "Non-ISO template ($headerHex) — rejected")
            BiometricFileLogger.write("WARN", "FORMAT_GATE",
                "action=rejected header=$headerHex size=${template.size}B")
            return ByteArray(0)
        }
        // Truncate zero-padded templates to their declared record length (bytes 8–11, big-endian).
        // SecuGen Mobile pads every template to a fixed 800 bytes; the true content ends earlier.
        val working: ByteArray = if (template.size >= RECORD_LENGTH_OFFSET + 4) {
            val declared = ((template[RECORD_LENGTH_OFFSET    ].toInt() and 0xFF) shl 24) or
                           ((template[RECORD_LENGTH_OFFSET + 1].toInt() and 0xFF) shl 16) or
                           ((template[RECORD_LENGTH_OFFSET + 2].toInt() and 0xFF) shl  8) or
                            (template[RECORD_LENGTH_OFFSET + 3].toInt() and 0xFF)
            if (declared in MIN_TEMPLATE_LENGTH..template.size) {
                if (declared < template.size) {
                    BiometricFileLogger.write("INFO", "TRUNCATION",
                        "original=${template.size}B declared=${declared}B stripped=${template.size - declared}B")
                }
                template.copyOfRange(0, declared)
            } else template
        } else template

        return try {
            // Light canonicalization: strip the trailing ISO extended-data block but keep
            // the ORIGINAL minutiae bytes verbatim. The SecuGen SDK and the Bozorth matcher
            // both need the as-enrolled minutiae — re-filtering/quantizing them destroys
            // matchability. Many vendor templates (e.g. SecuGen Mobile) carry per-minutia
            // quality 0, which the old quality gate dropped entirely, yielding ~0 scores.
            if (working.size < HEADER_SIZE) return working.copyOf()
            val count = working[MINUTIAE_COUNT_OFFSET].toInt() and 0xFF
            val minutiaeEnd = HEADER_SIZE + count * 6
            if (count <= 0 || minutiaeEnd > working.size) {
                // Count byte implausible or data short — return the de-padded record as-is.
                return working.copyOf()
            }
            // header + original minutiae + 2-byte extended-data-length trailer (0x0000)
            val out = ByteArray(minutiaeEnd + 2)
            System.arraycopy(working, 0, out, 0, minutiaeEnd)
            // Patch record length (bytes 8–11) to the new total so the SDK reads it cleanly.
            val total = out.size
            out[RECORD_LENGTH_OFFSET    ] = ((total ushr 24) and 0xFF).toByte()
            out[RECORD_LENGTH_OFFSET + 1] = ((total ushr 16) and 0xFF).toByte()
            out[RECORD_LENGTH_OFFSET + 2] = ((total ushr  8) and 0xFF).toByte()
            out[RECORD_LENGTH_OFFSET + 3] = ( total          and 0xFF).toByte()
            Log.d(TAG, "Canonicalized (light): kept $count minutiae, ${out.size} B (from ${working.size} B)")
            out
        } catch (e: Exception) {
            Log.e(TAG, "Canonicalization error — returning de-padded original", e)
            working.copyOf()
        }
    }

    /** SHA-256 hex digest of the canonical form — used for deduplication. */
    fun computeHash(template: ByteArray): String {
        val canonical = canonicalize(template)
        return java.security.MessageDigest.getInstance("SHA-256")
            .digest(canonical)
            .joinToString("") { "%02x".format(it) }
    }

    /** Basic sanity check on [template] size, ISO format, and parseability. */
    fun isValid(template: ByteArray?): Boolean {
        if (template == null || template.isEmpty()) return false
        if (template.size < MIN_TEMPLATE_LENGTH || template.size > MAX_TEMPLATE_LENGTH) return false
        if (!isIso19794Format(template)) return false
        return try { parseTemplate(template) != null } catch (_: Exception) { false }
    }

    // ── Internal data classes ─────────────────────────────────────────────────────

    private data class Minutia(
        val x: Int,
        val y: Int,
        val angle: Int,    // raw ISO angle byte (0–255; 1 unit ≈ 1.4°)
        val quality: Int,  // decoded 0–100 (rescaled from 6-bit ISO field)
        val type: Int      // MINUTIAE_TYPE_ENDING or MINUTIAE_TYPE_BIFURCATION
    )

    private data class ParsedTemplate(
        val header: ByteArray,      // first HEADER_SIZE bytes of the original template
        val minutiae: List<Minutia>,
        val imageQuality: Int       // byte 30: finger/view quality
    )

    // ── Template parsing ─────────────────────────────────────────────────────────

    /**
     * Parses the ISO 19794-2 header and minutiae records.
     *
     * The buffer is positioned at byte [HEADER_SIZE] (28) before the minutiae loop.
     * Byte 27 is read as the declared minutiae count; if > 0 it caps the loop so we
     * never read past the actual data even on a re-canonicalized template.
     */
    private fun parseTemplate(template: ByteArray): ParsedTemplate? {
        if (template.size < HEADER_SIZE) return null
        return try {
            val buffer = ByteBuffer.wrap(template).apply { order(ByteOrder.BIG_ENDIAN) }

            // ── ISO 19794-2 record header fields we need ─────────────────────────
            buffer.position(0)
            /* formatID      = */ buffer.int   // bytes 0–3  "FMR\0"
            /* versionNumber = */ buffer.int   // bytes 4–7  " 020"
            /* recordLength  = */ buffer.int   // bytes 8–11

            // ── Per-finger-view header (bytes 24–27) ─────────────────────────────
            buffer.position(FINGER_VIEW_OFFSET)
            /* fingerPosition = */ buffer.get()                               // byte 24
            /* impressionType = */ buffer.get()                               // byte 25
            val imageQuality    = buffer.get().toInt() and 0xFF              // byte 26
            val declaredCount   = buffer.get().toInt() and 0xFF              // byte 27
            // Buffer is now at byte 28 — first minutia record

            val maxMinutiae = if (declaredCount > 0) declaredCount else 200
            val minutiae    = mutableListOf<Minutia>()

            while (buffer.hasRemaining() && minutiae.size < maxMinutiae) {
                if (buffer.remaining() < 6) break

                val x         = buffer.short.toInt() and 0xFFFF
                val y         = buffer.short.toInt() and 0xFFFF
                val angle     = buffer.get().toInt() and 0xFF

                // ISO 19794-2 §7.2: quality byte layout
                //   bits [7:6] = minutia type  (0=other, 1=ending, 2=bifurcation)
                //   bits [5:0] = quality 0–63
                val qByte     = buffer.get().toInt() and 0xFF
                val type      = (qByte shr 6) and 0x03
                val rawQ      = qByte and 0x3F
                val quality   = (rawQ * 100) / 63  // rescale 0–63 → 0–100

                minutiae.add(
                    Minutia(
                        x       = x,
                        y       = y,
                        angle   = angle,
                        quality = quality,
                        type    = when (type) {
                            MINUTIAE_TYPE_ENDING      -> MINUTIAE_TYPE_ENDING
                            MINUTIAE_TYPE_BIFURCATION -> MINUTIAE_TYPE_BIFURCATION
                            else                      -> MINUTIAE_TYPE_ENDING
                        }
                    )
                )
            }

            ParsedTemplate(
                header       = template.copyOfRange(0, HEADER_SIZE),
                minutiae     = minutiae,
                imageQuality = imageQuality
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse template", e)
            null
        }
    }

    // ── Minutiae normalisation ───────────────────────────────────────────────────

    private fun filterAndNormalizeMinutiae(minutiae: List<Minutia>): List<Minutia> {
        // Step 1: Quality filter
        val qualityFiltered = minutiae.filter { it.quality >= MINUTIAE_QUALITY_THRESHOLD }
        if (qualityFiltered.isEmpty()) {
            Log.w(TAG, "All minutiae filtered out — relaxing quality gate")
            return minutiae  // fallback: keep all rather than produce an empty template
        }

        // Step 2: Spurious removal — within each proximity cluster keep the single
        // highest-quality minutia (ties broken by angle proximity to 0).
        val nonSpurious = mutableListOf<Minutia>()
        val consumed    = mutableSetOf<Int>()

        for (i in qualityFiltered.indices) {
            if (i in consumed) continue
            val cluster = mutableListOf(i)
            for (j in (i + 1) until qualityFiltered.size) {
                if (j in consumed) continue
                val a = qualityFiltered[i]; val b = qualityFiltered[j]
                val d = sqrt(((a.x - b.x).toDouble().pow(2) + (a.y - b.y).toDouble().pow(2)))
                if (d < SPURIOUS_REMOVAL_DISTANCE) cluster.add(j)
            }
            val best = cluster.maxByOrNull { qualityFiltered[it].quality }!!
            cluster.filter { it != best }.forEach { consumed.add(it) }
            nonSpurious.add(qualityFiltered[best])
        }

        // Step 3: Quantize orientation for repeatability
        val quantized = nonSpurious.map { m ->
            m.copy(angle = ((m.angle / ORIENTATION_STEP) * ORIENTATION_STEP) % 360)
        }

        // Step 4: Sort top-left → bottom-right for deterministic ordering
        return quantized.sortedWith(compareBy({ it.y }, { it.x }))
    }

    // ── Template reconstruction ──────────────────────────────────────────────────

    /**
     * Rebuilds the ISO 19794-2 template from the original [header] bytes and the
     * filtered [minutiae] list, then patches two stale header fields:
     *
     *   Byte     31  numberOfMinutiae  → actual filtered count
     *   Bytes  8–11  recordLength      → total byte length of the rebuilt template
     *
     * Without these patches, the SecuGen SDK reads the old (pre-filter) count from
     * the header and either under-reads or over-reads the minutiae section.
     */
    private fun rebuildTemplate(
        header: ByteArray,
        minutiae: List<Minutia>,
        @Suppress("UNUSED_PARAMETER") imageQuality: Int
    ): ByteArray {
        return try {
            // header + minutiae + 2-byte extended-data-block length trailer (0x0000).
            // ISO 19794-2:2005 requires the 2-byte extended-data length after the last
            // minutia; SecuGen-captured probes include it (declared 282 = 28 + 42×6 + 2),
            // and the SDK can misread the final minutia if it is absent.
            val totalLength = header.size + minutiae.size * 6 + 2
            val buffer      = ByteBuffer.allocate(totalLength).apply { order(ByteOrder.BIG_ENDIAN) }

            // Write the original header block
            buffer.put(header)

            // Patch header fields using absolute-index puts (do not move the position)
            if (header.size >= HEADER_SIZE) {
                // Byte 27: number of minutiae in this view
                buffer.put(MINUTIAE_COUNT_OFFSET, minutiae.size.coerceAtMost(255).toByte())
                // Bytes 8–11: total record length
                buffer.putInt(RECORD_LENGTH_OFFSET, totalLength)
            }

            // Write minutiae records (6 bytes each)
            for (m in minutiae) {
                buffer.putShort(m.x.toShort())
                buffer.putShort(m.y.toShort())
                buffer.put(m.angle.toByte())
                // Re-encode: type (top 2 bits) | raw quality 0–63 (bottom 6 bits)
                val rawQ     = ((m.quality * 63) / 100).coerceIn(0, 63)
                val typeBits = when (m.type) {
                    MINUTIAE_TYPE_BIFURCATION -> MINUTIAE_TYPE_BIFURCATION
                    else                      -> MINUTIAE_TYPE_ENDING
                }
                buffer.put(((typeBits shl 6) or rawQ).toByte())
            }

            // Extended-data block length = 0 (no extended data retained)
            buffer.putShort(0)

            buffer.array().copyOfRange(0, buffer.position())
        } catch (e: Exception) {
            Log.e(TAG, "Error rebuilding template", e)
            header   // last-resort fallback
        }
    }
}
