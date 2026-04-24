package com.carecompanion.biometric.scanner

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.carecompanion.biometric.models.MatchResult
import com.carecompanion.biometric.secugen.SecuGenDevice
import com.carecompanion.biometric.secugen.SecuGenSecurityLevel

/**
 * Device-discovery helper and factory for USB fingerprint scanners.
 *
 * Currently supports:
 *   - SecuGen (VID 0x1E80)  → delegates to [SecuGenScanner]
 *
 * To add DigitalPersona or Mantra in future: add a branch in [create].
 */
class UsbBiometricScanner(
    private val context: Context,
    private val usbDevice: UsbDevice
) : BiometricScanner {

    companion object {
        private const val VENDOR_SECUGEN         = 0x1E80   // SecuGen (newer VID)
        private const val VENDOR_SECUGEN_LEGACY  = 0x1162   // SecuGen (legacy VID)
        private const val VENDOR_DIGITAL_PERSONA = 0x05BA   // HID / DigitalPersona
        private const val VENDOR_MANTRA          = 0x08FF   // Mantra

        private fun isSecuGenDevice(device: UsbDevice): Boolean {
            if (device.vendorId == VENDOR_SECUGEN || device.vendorId == VENDOR_SECUGEN_LEGACY) {
                return true
            }
            val manufacturer = device.manufacturerName?.lowercase().orEmpty()
            val product = device.productName?.lowercase().orEmpty()
            return manufacturer.contains("secugen") ||
                product.contains("secugen") ||
                product.contains("hamster")
        }

        /** Returns the first supported USB scanner attached, or null. */
        fun findSupportedDevice(context: Context): UsbDevice? {
            val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            return usbManager.deviceList.values.firstOrNull { device ->
                isSecuGenDevice(device) ||
                    device.vendorId in setOf(VENDOR_DIGITAL_PERSONA, VENDOR_MANTRA)
            }
        }

        /**
         * Factory: instantiates the correct [BiometricScanner] for [device].
         * Returns null for unrecognised vendors.
         */
        fun create(context: Context, device: UsbDevice): BiometricScanner? = when {
            // SL_HIGH (1:100,000 FAR) required for 1:N identification against a population
            // per NPHCDA biometric guideline for HIV programme identification
            isSecuGenDevice(device) -> SecuGenScanner(context, device,
                securityLevel = SecuGenSecurityLevel.SL_HIGH)
            // VENDOR_DIGITAL_PERSONA -> DigitalPersonaScanner(context, device)
            // VENDOR_MANTRA          -> MantraScanner(context, device)
            else -> {
                android.util.Log.w("UsbBiometricScanner",
                    "Unsupported USB vendor 0x${device.vendorId.toString(16).uppercase()}")
                null
            }
        }
    }

    // Delegate to the vendor-specific scanner
    private val delegate: BiometricScanner? = create(context, usbDevice)

    override fun connect(): Boolean = delegate?.connect() ?: false

    override suspend fun captureFingerprint(timeoutSeconds: Int): ByteArray? =
        delegate?.captureFingerprint(timeoutSeconds)

    override fun matchTemplates(template1: ByteArray, template2: ByteArray): MatchResult =
        delegate?.matchTemplates(template1, template2) ?: MatchResult(0.0, false)

    override fun getQuality(): Int = delegate?.getQuality() ?: 0

    override fun disconnect() = delegate?.disconnect() ?: Unit
}