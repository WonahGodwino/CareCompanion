package com.carecompanion.biometric.models
data class MatchResult(val score: Double, val isMatch: Boolean = score >= 60.0)
data class MatchedPatient(val patient: com.carecompanion.data.database.entities.Patient, val biometric: com.carecompanion.data.database.entities.Biometric, val matchScore: Double)
sealed class VerificationResult {
    data class Matched(val fingerType: FingerType, val matchScore: Double) : VerificationResult()
    data class NotMatched(val fingerType: FingerType, val matchScore: Double) : VerificationResult()
    data class FingerNotEnrolled(val fingerType: FingerType) : VerificationResult()
}