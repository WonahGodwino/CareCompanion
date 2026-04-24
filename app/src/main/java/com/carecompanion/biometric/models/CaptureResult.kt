package com.carecompanion.biometric.models
sealed class CaptureResult {
    data class Success(val template: ByteArray, val quality: Int, val fingerType: FingerType) : CaptureResult() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Success
            return template.contentEquals(other.template) && quality == other.quality && fingerType == other.fingerType
        }
        override fun hashCode(): Int = 31 * (31 * template.contentHashCode() + quality) + fingerType.hashCode()
    }
    data class Failed(val message: String) : CaptureResult()
}