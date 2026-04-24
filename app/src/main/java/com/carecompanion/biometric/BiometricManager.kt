package com.carecompanion.biometric

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.carecompanion.biometric.models.MatchResult
import com.carecompanion.biometric.scanner.BiometricScanner
import com.carecompanion.biometric.scanner.ScannerStatus
import com.carecompanion.biometric.scanner.UsbBiometricScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton manager for USB biometric scanners.
 *
 * Handles the full USB lifecycle:
 *   - Initial discovery on [initialize]
 *   - USB_DEVICE_ATTACHED  → auto-connect
 *   - USB_DEVICE_DETACHED  → disconnect + status update
 *   - Permission request + grant flow
 *   - Thread-safe capture via coroutines
 *
 * Observe [status] to drive UI. Call [captureFingerprint] from a coroutine.
 */
@Singleton
class BiometricManager @Inject constructor() {

    data class ScannerInfo(
        val usbDeviceDetected: Boolean,
        val accessGranted: Boolean,
        val isConnected: Boolean,
        val isReady: Boolean,
        val deviceSummary: String,
        val permissionState: String,
        val connectionState: String
    )

    companion object {
        private const val TAG = "BiometricManager"
        private const val ACTION_USB_PERMISSION = "com.carecompanion.USB_PERMISSION"
    }

    // Coroutine scope for scanner operations; cancelled in [release]
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectMutex = Mutex()

    private var scanner: BiometricScanner? = null
    private var appContext: Context? = null
    @Volatile private var lastUsbDeviceSummary: String = "none"
    @Volatile private var lastPermissionState: String = "unknown"
    @Volatile private var lastConnectionState: String = "idle"

    private val _status = MutableStateFlow(ScannerStatus.NO_FINGER)
    val status: StateFlow<ScannerStatus> = _status.asStateFlow()

    /** Latest image quality from the last capture (0–100). */
    var lastCaptureQuality: Int = 0
        private set

    // ─────────────────────────────────────────────────────────────────────────
    // USB broadcast receivers
    // ─────────────────────────────────────────────────────────────────────────

    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action != ACTION_USB_PERMISSION) return
            val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            else
                @Suppress("DEPRECATION") intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)

            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            if (granted && device != null) {
                rememberDevice(device)
                lastPermissionState = "granted"
                Log.i(TAG, "USB permission granted for ${device.productName}")
                connectToDevice(ctx, device)
            } else {
                if (device != null) rememberDevice(device)
                lastPermissionState = "denied"
                lastConnectionState = "permission_denied"
                Log.w(TAG, "USB permission denied for ${device?.productName}")
                _status.value = ScannerStatus.ERROR
            }
        }
    }

    private val usbAttachReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action != UsbManager.ACTION_USB_DEVICE_ATTACHED) return
            val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            else
                @Suppress("DEPRECATION") intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)

            device?.let {
                rememberDevice(it)
                lastConnectionState = "attached"
                Log.i(TAG, "USB device attached: ${it.productName} (VID 0x${it.vendorId.toString(16)})")
                discoverAndConnect(ctx, it)
            }
        }
    }

    private val usbDetachReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action != UsbManager.ACTION_USB_DEVICE_DETACHED) return
            Log.i(TAG, "USB device detached – disconnecting scanner")
            managerScope.launch { connectMutex.withLock { releaseScanner() } }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Call once from [CareCompanionApplication.onCreate].
     * Registers broadcast receivers and scans for an already-attached device.
     */
    fun initialize(context: Context) {
        appContext = context.applicationContext

        val receiverFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Context.RECEIVER_EXPORTED else 0

        context.registerReceiver(usbPermissionReceiver,
            IntentFilter(ACTION_USB_PERMISSION), receiverFlag)
        context.registerReceiver(usbAttachReceiver,
            IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED), receiverFlag)
        context.registerReceiver(usbDetachReceiver,
            IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED), receiverFlag)

        // Check if a scanner is already plugged in
        UsbBiometricScanner.findSupportedDevice(context)?.let { device ->
            discoverAndConnect(context, device)
        } ?: Log.i(TAG, "No USB scanner found at startup")
    }

    /** True when a scanner is open and ready to capture. */
    fun isReady(): Boolean = scanner != null && _status.value == ScannerStatus.READY

    /**
     * Re-scan USB bus and re-request permission if a SecuGen device is present.
     * Call when the user taps Retry after a permission-denied error.
     */
    fun retryConnection() {
        val ctx = appContext ?: return
        UsbBiometricScanner.findSupportedDevice(ctx)?.let { device ->
            discoverAndConnect(ctx, device)
        } ?: Log.i(TAG, "retryConnection: no USB scanner found")
    }

    /**
     * Capture a fingerprint and return the raw template bytes, or null on
     * timeout.  Throws [com.carecompanion.biometric.secugen.PoorQualityException]
     * if quality stays below threshold after max retries.
     */
    suspend fun captureFingerprint(timeoutSeconds: Int = 30): ByteArray? {
        _status.value = ScannerStatus.FINGER_DETECTED
        return try {
            val template = scanner?.captureFingerprint(timeoutSeconds)
            lastCaptureQuality = scanner?.getQuality() ?: 0
            _status.value = if (scanner != null) ScannerStatus.READY else ScannerStatus.NO_FINGER
            template
        } catch (e: Exception) {
            Log.e(TAG, "captureFingerprint error", e)
            _status.value = ScannerStatus.ERROR
            throw e   // re-throw so VerifyViewModel can handle PoorQualityException etc.
        }
    }

    /**
     * 1:1 template match using the connected scanner's native SDK.
     * Falls back to [MatchResult(0.0, false)] when no scanner is connected.
     */
    fun match(probe: ByteArray, reference: ByteArray): MatchResult =
        scanner?.matchTemplates(probe, reference) ?: MatchResult(0.0, false)

    /** Last image quality read from the scanner (0–100). */
    fun getQuality(): Int = lastCaptureQuality

    /** Compact diagnostic info to surface in UI when scanner is not ready. */
    fun getScannerDebugInfo(): String =
        "USB[$lastUsbDeviceSummary], perm=$lastPermissionState, conn=$lastConnectionState"

    /** Structured scanner state for UI (connection, permission/access, readiness). */
    fun getScannerInfo(): ScannerInfo {
        val ready = isReady()
        val connected = scanner != null && lastConnectionState == "connected"
        val accessGranted = lastPermissionState == "granted" || lastPermissionState == "already_granted"
        val usbDeviceDetected = lastUsbDeviceSummary != "none"
        return ScannerInfo(
            usbDeviceDetected = usbDeviceDetected,
            accessGranted = accessGranted,
            isConnected = connected,
            isReady = ready,
            deviceSummary = lastUsbDeviceSummary,
            permissionState = lastPermissionState,
            connectionState = lastConnectionState
        )
    }

    /**
     * Disconnect the scanner and unregister all receivers.
     * Call from [CareCompanionApplication.onTerminate] or when the app exits.
     */
    fun release() {
        managerScope.launch {
            connectMutex.withLock { releaseScanner() }
        }
        try { appContext?.unregisterReceiver(usbPermissionReceiver) } catch (_: Exception) {}
        try { appContext?.unregisterReceiver(usbAttachReceiver) }     catch (_: Exception) {}
        try { appContext?.unregisterReceiver(usbDetachReceiver) }     catch (_: Exception) {}
        managerScope.cancel()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun discoverAndConnect(ctx: Context, device: UsbDevice) {
        rememberDevice(device)
        val usbManager = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
        if (usbManager.hasPermission(device)) {
            lastPermissionState = "already_granted"
            connectToDevice(ctx, device)
        } else {
            requestPermission(ctx, device, usbManager)
        }
    }

    private fun requestPermission(ctx: Context, device: UsbDevice, usbManager: UsbManager) {
        rememberDevice(device)
        lastPermissionState = "requested"
        lastConnectionState = "awaiting_permission"
        Log.i(TAG, "Requesting USB permission for ${device.productName}")
        val flags = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                // UsbManager fills extras (EXTRA_DEVICE / EXTRA_PERMISSION_GRANTED), so intent must be mutable.
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            else -> 0
        }
        val permIntent = PendingIntent.getBroadcast(
            ctx, device.deviceId, Intent(ACTION_USB_PERMISSION), flags
        )
        usbManager.requestPermission(device, permIntent)
    }

    private fun connectToDevice(ctx: Context, device: UsbDevice) {
        managerScope.launch {
            connectMutex.withLock {
                // Don't re-connect if already open
                if (scanner != null && _status.value == ScannerStatus.READY) return@launch
                releaseScanner()

                val candidate = UsbBiometricScanner.create(ctx, device)
                if (candidate == null) {
                    lastConnectionState = "unsupported_device"
                    Log.w(TAG, "No scanner implementation for VID 0x${device.vendorId.toString(16)}")
                    return@launch
                }

                val ok = candidate.connect()
                if (ok) {
                    scanner = candidate
                    _status.value = ScannerStatus.READY
                    lastConnectionState = "connected"
                    Log.i(TAG, "Scanner ready")
                } else {
                    _status.value = ScannerStatus.ERROR
                    lastConnectionState = "connect_failed"
                    Log.e(TAG, "Failed to connect to scanner")
                }
            }
        }
    }

    private fun releaseScanner() {
        try { scanner?.disconnect() } catch (_: Exception) {}
        scanner = null
        lastConnectionState = "disconnected"
        _status.value = ScannerStatus.NO_FINGER
    }

    private fun rememberDevice(device: UsbDevice) {
        val vendorHex = device.vendorId.toString(16).uppercase()
        val productHex = device.productId.toString(16).uppercase()
        val name = device.productName?.takeIf { it.isNotBlank() } ?: "unknown"
        lastUsbDeviceSummary = "VID=0x$vendorHex,PID=0x$productHex,name=$name"
    }

}