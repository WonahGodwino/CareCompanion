package com.carecompanion.biometric.scanner

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.carecompanion.biometric.BiometricTemplateNormalizer
import com.carecompanion.biometric.models.MatchResult
import com.carecompanion.biometric.secugen.SecuGenDeviceInfo
import com.carecompanion.biometric.secugen.SecuGenError
import com.carecompanion.biometric.secugen.SecuGenException
import com.carecompanion.biometric.secugen.SecuGenQuality
import com.carecompanion.biometric.secugen.SecuGenSecurityLevel
import com.carecompanion.biometric.secugen.SecuGenTemplateFormat
import SecuGen.FDxSDKPro.JSGFPLib
import SecuGen.FDxSDKPro.SGDeviceInfoParam
import SecuGen.FDxSDKPro.SGFDxDeviceName
import SecuGen.FDxSDKPro.SGFDxErrorCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Production SecuGen FDx SDK Pro implementation of [BiometricScanner].
 *
 * Usage:
 *   val scanner = SecuGenScanner(context, usbDevice)
 *   if (scanner.connect()) {
 *       val template = scanner.captureFingerprint(timeoutSeconds = 30)
 *       val result   = scanner.matchTemplates(template, storedTemplate)
 *       scanner.disconnect()
 *   }
 *
 * Thread safety: all SDK calls are serialised by [sdkMutex].  Call
 * [captureFingerprint] from a coroutine; [matchTemplates] is synchronous
 * and safe to call from any thread while the device is open.
 *
 * ⚠️  Requires FDxSDKProFDAndroid.jar in app/libs/ and native .so files
 *     in app/src/main/jniLibs/. Obtained from SecuGen FDx SDK Pro for Android.
 */
class SecuGenScanner(
    private val context: Context,
    private val usbDevice: UsbDevice,
    private val templateFormat: Int = SecuGenTemplateFormat.TEMPLATE_FORMAT_ISO19794,
    private val securityLevel: Int  = SecuGenSecurityLevel.SL_NORMAL
) : BiometricScanner {

    /**
     * Captures a fingerprint and returns both the canonicalized template and its SHA-256 hash.
     * The hash is always computed from the canonicalized template (ISO19794).
     */
    suspend fun captureFingerprintWithHash(timeoutSeconds: Int): Pair<ByteArray, String>? =
        withContext(Dispatchers.IO) {
            val template = captureFingerprint(timeoutSeconds)
            if (template == null) return@withContext null
            val canonical = BiometricTemplateNormalizer.canonicalize(template)
            val hash = com.carecompanion.data.repository.SyncRepositoryImpl.sha256Hex(canonical)
            Pair(canonical, hash)
        }

    companion object {
        private const val TAG = "SecuGenScanner"
    }

    private var sgfpm: JSGFPLib? = null
    private val sdkMutex = Mutex()

    private var lastQuality: Int = 0
    private var isOpen: Boolean  = false

    /** Device info populated after a successful [connect]. */
    var deviceInfo: SecuGenDeviceInfo? = null
        private set

    // ─────────────────────────────────────────────────────────────────────────
    // BiometricScanner interface
    // ─────────────────────────────────────────────────────────────────────────

    override fun connect(): Boolean {
        return try {
            val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

            // 1. Create SDK instance
            val lib = JSGFPLib(context, usbManager)
            check(lib.GetJniLoadStatus(), "JniLoad")

            // 2. Init must come before SetTemplateFormat — Init resets SDK state and would
            //    silently discard any format set earlier.
            check(lib.Init(SGFDxDeviceName.SG_DEV_AUTO), "Init")

            // 3. Open the physical USB device (index 0 = first attached SecuGen device).
            //    Permission must already be granted (ensured by BiometricManager before
            //    this call). Without OpenDevice() GetDeviceInfo() and all capture calls fail.
            check(lib.OpenDevice(0), "OpenDevice")

            // 4. Set template format AFTER device is open — this is when the setting sticks.
            check(lib.SetTemplateFormat(templateFormat.toShort()), "SetTemplateFormat")

            sgfpm = lib

            // 4. Read back hardware capabilities
            deviceInfo = readDeviceInfo()
            isOpen = true
            Log.i(TAG, "Connected: $deviceInfo")
            true
        } catch (e: SecuGenException) {
            Log.e(TAG, "connect() failed: ${e.message}")
            safeClose()
            false
        } catch (e: Exception) {
            Log.e(TAG, "connect() unexpected error", e)
            safeClose()
            false
        }
    }

    /**
     * Captures a fingerprint image, extracts a minutiae template, and returns
     * the raw template bytes.
     *
     * Delegates quality filtering to the SDK: [JSGFPLib.GetImageEx] blocks until
     * an image with quality >= [SecuGenQuality.MIN_VERIFY] is captured OR
     * [timeoutSeconds] elapses (returns null).
     */
    override suspend fun captureFingerprint(timeoutSeconds: Int): ByteArray? =
        withContext(Dispatchers.IO) {
            requireOpen()
            val lib  = sgfpm ?: throw IllegalStateException("Scanner not initialized")
            val info = deviceInfo ?: throw IllegalStateException("Device info unavailable")
            val imageBuffer = ByteArray(info.imageBufferSize)
            val template    = ByteArray(SecuGenTemplateFormat.maxSize(templateFormat))

            val captureErr = sdkMutex.withLock {
                lib.GetImageEx(
                    imageBuffer,
                    timeoutSeconds * 1_000L,              // SDK timeout in ms
                    SecuGenQuality.MIN_VERIFY.toLong()    // minimum quality threshold
                )
            }

            when (captureErr) {
                SGFDxErrorCode.SGFDX_ERROR_NONE -> {
                    // Retrieve actual quality value for logging/reporting
                    val qualityOut = IntArray(1)
                    sdkMutex.withLock {
                        lib.GetImageQuality(
                            info.imageWidth.toLong(),
                            info.imageHeight.toLong(),
                            imageBuffer,
                            qualityOut
                        )
                    }
                    lastQuality = qualityOut[0]
                    Log.d(TAG, "Captured image, quality=$lastQuality")

                    val templateErr = sdkMutex.withLock {
                        lib.CreateTemplate(null, imageBuffer, template)
                    }
                    check(templateErr, "CreateTemplate")
                    template.copyOf()
                }
                SGFDxErrorCode.SGFDX_ERROR_TIME_OUT -> {
                    Log.w(TAG, "captureFingerprint timed out after ${timeoutSeconds}s")
                    null
                }
                else -> {
                    Log.w(TAG, "GetImageEx error: ${SecuGenError.describe(captureErr.toInt())}")
                    null
                }
            }
        }

    /**
     * 1:1 template comparison.
     *
     * Uses [JSGFPLib.MatchTemplate] with the configured [securityLevel].
     * Also calls [JSGFPLib.GetMatchingScore] to return a normalised similarity
     * score (0–100 %). SDK score range is 0–199.
     */
    override fun matchTemplates(template1: ByteArray, template2: ByteArray): MatchResult {
        requireOpen()
        val lib = sgfpm ?: return MatchResult(0.0, false)
        if (template1.isEmpty() || template2.isEmpty()) return MatchResult(0.0, false)
        return try {
            val raw = attemptMatch(lib, template1, template2)
            if (raw != null) {
                return raw
            }

            val canonical1 = BiometricTemplateNormalizer.canonicalize(template1)
            val canonical2 = BiometricTemplateNormalizer.canonicalize(template2)
            val canonical = attemptMatch(lib, canonical1, canonical2)
            if (canonical != null) {
                return canonical
            }

            val targetSize = maxOf(template1.size, template2.size)
            val padded = attemptMatch(lib, padTemplate(template1, targetSize), padTemplate(template2, targetSize))
            if (padded != null) {
                return padded
            }

            MatchResult(0.0, false)
        } catch (e: Exception) {
            Log.e(TAG, "matchTemplates error", e)
            MatchResult(0.0, false)
        }
    }

    private fun attemptMatch(lib: JSGFPLib, probe: ByteArray, reference: ByteArray): MatchResult? {
        if (probe.isEmpty() || reference.isEmpty()) return null

        val matched = BooleanArray(1)
        val matchErr = lib.MatchTemplate(probe, reference, securityLevel.toLong(), matched)
        if (matchErr != SGFDxErrorCode.SGFDX_ERROR_NONE) {
            Log.w(TAG, "MatchTemplate error: ${SecuGenError.describe(matchErr.toInt())}")
            return null
        }

        val rawScore = IntArray(1)
        val scoreErr = lib.GetMatchingScore(probe, reference, rawScore)
        val normalisedScore = when {
            scoreErr == SGFDxErrorCode.SGFDX_ERROR_NONE -> (rawScore[0].toDouble() / 199.0) * 100.0
            matched[0]                                  -> 75.0
            else                                        -> 15.0
        }

        Log.d(TAG, "Match variant: matched=${matched[0]}, raw=${rawScore[0]}, score=${"%.1f".format(normalisedScore)}%")
        return MatchResult(normalisedScore.coerceIn(0.0, 100.0), matched[0])
    }

    private fun padTemplate(template: ByteArray, targetSize: Int): ByteArray {
        if (template.size >= targetSize) {
            return template
        }
        val out = ByteArray(targetSize)
        System.arraycopy(template, 0, out, 0, template.size)
        return out
    }

    override fun getQuality(): Int = lastQuality

    override fun disconnect() {
        safeClose()
        Log.i(TAG, "Scanner disconnected")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun readDeviceInfo(): SecuGenDeviceInfo {
        val lib   = sgfpm ?: throw IllegalStateException("Scanner not initialized")
        val param = SGDeviceInfoParam()
        check(lib.GetDeviceInfo(param), "GetDeviceInfo")

        val sizeOut = IntArray(1)
        lib.GetMaxTemplateSize(sizeOut)

        val snBytes = param.deviceSN()
        val serialNumber = if (snBytes != null)
            String(snBytes, Charsets.UTF_8).trimEnd('\u0000').ifEmpty { "N/A" }
        else "N/A"

        return SecuGenDeviceInfo(
            deviceName      = "SecuGen (DeviceID: ${param.deviceID})",
            deviceSN        = serialNumber,
            firmwareVersion = param.FWVersion.toString(),
            imageWidth      = param.imageWidth,
            imageHeight     = param.imageHeight,
            imageResolution = param.imageDPI,
            imageDPI        = param.imageDPI,
            maxTemplateSize = sizeOut[0],
            hasAutoCapture  = lib.AutoOnEnabled()
        )
    }

    private fun check(errorCode: Long, operation: String) {
        if (errorCode != SGFDxErrorCode.SGFDX_ERROR_NONE) {
            throw SecuGenException(errorCode, "SDK.$operation failed: ${SecuGenError.describe(errorCode.toInt())}")
        }
    }

    private fun requireOpen() {
        check(isOpen) { "Scanner is not open. Call connect() first." }
    }

    private fun safeClose() {
        try { if (isOpen) sgfpm?.CloseDevice() } catch (_: Exception) {}
        try { sgfpm?.Close() } catch (_: Exception) {}
        sgfpm  = null
        isOpen = false
    }
}