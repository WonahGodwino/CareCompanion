package com.carecompanion.data.repository

interface SyncRepository {
    // Standards-based biometric helpers
    suspend fun enrollBiometric(
        patientUuid: String,
        fingerType: String,
        template: ByteArray,
        quality: Int,
        facilityId: Long? = null,
        userId: String? = null
    ): Boolean

    /**
     * 1:N global identification.
     * [sdkMatcher] — vendor SDK scoring function (probe, reference) → score 0–100.
     * When non-null the SDK score is used; custom matcher is the offline fallback.
     */
    suspend fun identifyBiometric(
        capturedTemplate: ByteArray,
        facilityId: Long? = null,
        userId: String? = null,
        sdkMatcher: ((ByteArray, ByteArray) -> Double)? = null
    ): SyncRepositoryImpl.PatientMatchResult?

    /**
     * 1:1 facility-scoped verification.
     * [sdkMatcher] — vendor SDK scoring function (probe, reference) → score 0–100.
     * When non-null the SDK score is used; custom matcher is the offline fallback.
     */
    suspend fun verifyBiometric(
        capturedTemplate: ByteArray,
        personUuid: String,
        fingerType: String,
        facilityId: Long? = null,
        userId: String? = null,
        sdkMatcher: ((ByteArray, ByteArray) -> Double)? = null
    ): SyncRepositoryImpl.PatientMatchResult?

    // Debug methods (remove in production)
    suspend fun debugTemplateComparison(capturedTemplate: ByteArray)
    suspend fun testIdentification(patientUuid: String): SyncRepositoryImpl.PatientMatchResult?
    suspend fun testVerification(patientUuid: String): Boolean

    // Add missing methods for DI consistency
    suspend fun syncAll(onProgress: ((String) -> Unit)? = null): SyncResult
    suspend fun getLastSyncInfo(): String?
    fun resetSyncPage()
}