package com.carecompanion.biometric.secugen

/**
 * Device capabilities read from SGDeviceInfoParam after OpenDevice().
 */
data class SecuGenDeviceInfo(
    val deviceName: String,
    val deviceSN: String,           // Serial number
    val firmwareVersion: String,
    val imageWidth: Int,
    val imageHeight: Int,
    val imageResolution: Int,       // DPI (500 or 1000)
    val imageDPI: Int,
    val maxTemplateSize: Int,
    val hasAutoCapture: Boolean
) {
    val imageBufferSize: Int get() = imageWidth * imageHeight

    override fun toString() =
        "$deviceName (SN: $deviceSN, fw: $firmwareVersion, ${imageWidth}x${imageHeight} @ ${imageResolution}dpi)"
}