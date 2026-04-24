package com.carecompanion.data.repository

sealed class SyncResult {
    data class Success(val patientsAdded: Int, val biometricsAdded: Int) : SyncResult()
    data class Error(val message: String) : SyncResult()
    object NoNetwork : SyncResult()
    object NotConfigured : SyncResult()
}

interface SyncRepository {
    suspend fun syncAll(onProgress: ((String) -> Unit)? = null): SyncResult
    suspend fun getLastSyncInfo(): String?
    fun resetSyncPage()
}