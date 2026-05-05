package com.carecompanion.data.repository

data class SyncAudit(
    val pagesRead: Int = 0,
    val uniquePatientsSaved: Int = 0,
    val biometricCandidates: Int = 0,
    val biometricsSaved: Int = 0,
    val biometricsSkipped: Int = 0,
    val biometricsFailed: Int = 0,
)

sealed class SyncResult {
    data class Success(
        val patientsAdded: Int,
        val biometricsAdded: Int,
        val audit: SyncAudit = SyncAudit(),
    ) : SyncResult()
    data class Error(val message: String) : SyncResult()
    object NoNetwork : SyncResult()
    object NotConfigured : SyncResult()
}

interface SyncRepository {
    suspend fun syncAll(onProgress: ((String) -> Unit)? = null): SyncResult
    suspend fun getLastSyncInfo(): String?
    fun resetSyncPage()

    // Biometric matching
    suspend fun findPatientByBiometric(
        capturedTemplate: ByteArray,
        facilityId: Long? = null
    ): SyncRepositoryImpl.PatientMatchResult?

    suspend fun findPatientByBiometricForVerification(
        capturedTemplate: ByteArray,
        personUuid: String,
        fingerType: String,
        facilityId: Long? = null
    ): SyncRepositoryImpl.PatientMatchResult?
}