package com.carecompanion.biometric.scanner
import com.carecompanion.biometric.models.MatchResult
interface BiometricScanner {
    fun connect(): Boolean
    suspend fun captureFingerprint(timeoutSeconds: Int): ByteArray?
    fun matchTemplates(template1: ByteArray, template2: ByteArray): MatchResult
    fun getQuality(): Int
    fun disconnect()
}
enum class ScannerStatus { READY, FINGER_DETECTED, NO_FINGER, ERROR }