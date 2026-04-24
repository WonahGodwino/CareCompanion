package com.carecompanion.biometric.secugen

/**
 * Constants mirroring the SecuGen FDx SDK Pro for Android.
 * Sources:
 *   - SGFDxErrorCode   (SecuGen.FDxSDKPro.SGFDxErrorCode)
 *   - SGFDxDeviceName  (SecuGen.FDxSDKPro.SGFDxDeviceName)
 *   - SGFDxSecurityLevel (SecuGen.FDxSDKPro.SGFDxSecurityLevel)
 *   - SGFDxTemplateFormat (SecuGen.FDxSDKPro.SGFDxTemplateFormat)
 */

object SecuGenError {
    const val ERROR_NONE                = 0
    const val ERROR_WRONG_IMAGE         = 1
    const val ERROR_LACK_OF_BANDWIDTH   = 2
    const val ERROR_MEMORY_FAILURE      = 3
    const val ERROR_INVALID_PARAM       = 4
    const val ERROR_SITEM_NOT_FOUND     = 10
    const val ERROR_DSHOW_CANT_RUN      = 517
    const val ERROR_DEVICE_NOT_FOUND    = 512
    const val ERROR_NO_DEVICE           = 514
    const val ERROR_TIMEOUT             = 516
    const val ERROR_NOT_SUPPORTED       = 519
    const val ERROR_USB_IO              = 10006

    fun describe(code: Int): String = when (code) {
        ERROR_NONE                -> "Success"
        ERROR_WRONG_IMAGE         -> "Invalid image data"
        ERROR_LACK_OF_BANDWIDTH   -> "USB bandwidth insufficient"
        ERROR_MEMORY_FAILURE      -> "Memory allocation failed"
        ERROR_INVALID_PARAM       -> "Invalid parameter"
        ERROR_SITEM_NOT_FOUND     -> "Feature not available"
        ERROR_DSHOW_CANT_RUN      -> "DirectShow unavailable"
        ERROR_DEVICE_NOT_FOUND    -> "Scanner device not found"
        ERROR_NO_DEVICE           -> "No device opened"
        ERROR_TIMEOUT             -> "Capture timed out"
        ERROR_NOT_SUPPORTED       -> "Feature not supported by device"
        ERROR_USB_IO              -> "USB I/O error"
        else                      -> "Unknown error ($code)"
    }
}

/** SGFPLIB device name constants (SGFDxDeviceName) */
object SecuGenDevice {
    const val DEV_AUTO          = 0    // Auto-detect any connected device
    const val DEV_FDU02         = 1    // FDU02 (USB 1.1)
    const val DEV_USBIGO        = 2    // USB iGO series
    const val DEV_STD_USB2      = 3    // Hamster DX/Plus
    const val DEV_FDU03         = 4    // FDU03
    const val DEV_FDU05         = 5    // FDU05 (USB 2.0)
    const val DEV_UPx_SEC_USB   = 6    // Hamster Pro 20 / UPx (most common modern device)
    const val DEV_FAP20         = 14   // FAP20 optical

    // USB Vendor ID for SecuGen hardware (0x1E80 = 7808 decimal)
    const val VENDOR_ID = 0x1E80

    // Known PIDs for common SecuGen USB scanners
    val KNOWN_PRODUCT_IDS = setOf(
        0x0000, // Hamster III
        0x0009, // Hamster IV
        0x000A, // Hamster Pro
        0x000B, // iGO Match-on-Card
        0x000C, // Hamster Pro 20 (UPx)
        0x0010, // FDU05
        0x0011  // Hamster Plus
    )
}

/** Matching security levels (SGFDxSecurityLevel) – higher = lower FAR, higher FRR */
object SecuGenSecurityLevel {
    const val SL_LOWEST       = 1   // ~1 in 100       (testing only)
    const val SL_LOWER        = 2   // ~1 in 500
    const val SL_LOW          = 3   // ~1 in 1000
    const val SL_BELOW_NORMAL = 4   // ~1 in 5000
    const val SL_NORMAL       = 5   // ~1 in 10000     (recommended)
    const val SL_ABOVE_NORMAL = 6   // ~1 in 50000
    const val SL_HIGH         = 7   // ~1 in 100000
    const val SL_VERY_HIGH    = 8   // ~1 in 500000
    const val SL_HIGHEST      = 9   // ~1 in 1000000
}

/** Template format constants (SGFDxTemplateFormat) */
object SecuGenTemplateFormat {
    const val TEMPLATE_FORMAT_SG400      = 2   // SecuGen proprietary 400-byte fixed size
    const val TEMPLATE_FORMAT_ISO19794   = 3   // ISO/IEC 19794-2 (interoperable)
    const val TEMPLATE_FORMAT_ANSI378    = 4   // ANSI/INCITS 378

    /** Byte buffer size to allocate for each template format */
    fun maxSize(format: Int): Int = when (format) {
        TEMPLATE_FORMAT_SG400    -> 400
        TEMPLATE_FORMAT_ISO19794 -> 1566  // max for ISO 19794-2 with 12 minutiae pairs
        TEMPLATE_FORMAT_ANSI378  -> 1566
        else                     -> 1566
    }
}

/** Image quality thresholds.
 *
 * Both enrollment and verification thresholds are set to 60 to comply with
 * NPHCDA / NIST NFIQ2 minimum quality requirements for operational public
 * health biometric identification (Nigeria HIV programme).
 */
object SecuGenQuality {
    /** Minimum acceptable image quality (0-100 scale) for enrollment */
    const val MIN_ENROLL = 60
    /** Minimum acceptable image quality for verification/recall (aligned to enrollment per NPHCDA guideline) */
    const val MIN_VERIFY = 60
    /** Maximum number of capture retries before giving up */
    const val MAX_RETRIES = 5
}