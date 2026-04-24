package com.carecompanion.biometric.scanner

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
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
    @Suppress("UNUSED_PARAMETER") usbDevice: UsbDevice,   // kept for API compatibility; SDK discovers device via UsbManager
    private val templateFormat: Int = SecuGenTemplateFormat.TEMPLATE_FORMAT_ISO19794,
    private val securityLevel: Int  = SecuGenSecurityLevel.SL_NORMAL
) : BiometricScanner {

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

            // 1. Create SDK instance – constructor calls Open() internally
            val lib = JSGFPLib(context, usbManager)
            check(lib.GetJniLoadStatus(), "JniLoad")

            // 2. Set the template format before opening the device
            check(lib.SetTemplateFormat(templateFormat.toShort()), "SetTemplateFormat")

            // 3. Open the device (SG_DEV_AUTO lets the SDK auto-detect any connected device)
            check(lib.Init(SGFDxDeviceName.SG_DEV_AUTO), "Init")

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
            // Primary: boolean match decision
            val matched  = BooleanArray(1)
            val matchErr = lib.MatchTemplate(template1, template2, securityLevel.toLong(), matched)
            if (matchErr != SGFDxErrorCode.SGFDX_ERROR_NONE) {
                Log.w(TAG, "MatchTemplate error: ${SecuGenError.describe(matchErr.toInt())}")
                return MatchResult(0.0, false)
            }

            // Secondary: raw similarity score (0–199 → normalise to 0–100 %)
            val rawScore = IntArray(1)
            val scoreErr = lib.GetMatchingScore(template1, template2, rawScore)

            val normalisedScore = when {
                scoreErr == SGFDxErrorCode.SGFDX_ERROR_NONE -> (rawScore[0].toDouble() / 199.0) * 100.0
                matched[0]                                  -> 75.0   // match confirmed, score unavailable
                else                                        -> 15.0   // no match
            }

            Log.d(TAG, "Match: ${matched[0]}, raw=${rawScore[0]}, score=${"%.1f".format(normalisedScore)}%")
            MatchResult(normalisedScore.coerceIn(0.0, 100.0), matched[0])
        } catch (e: Exception) {
            Log.e(TAG, "matchTemplates error", e)
            MatchResult(0.0, false)
        }
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