package com.carecompanion.presentation.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.carecompanion.data.repository.SyncRepository
import com.carecompanion.data.repository.SyncResult
import com.carecompanion.data.sync.SyncWorker
import com.carecompanion.utils.DateUtils
import com.carecompanion.utils.SharedPreferencesHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SyncUiState(
    val isSyncing: Boolean = false,
    val lastSyncDate: String = "Never",
    val statusMessage: String = "",
    val patientsAdded: Int = 0,
    val biometricsAdded: Int = 0,
    val isSuccess: Boolean? = null,
    val autoSyncEnabled: Boolean = true,
    val syncIntervalMinutes: Int = 5
)

@HiltViewModel
class SyncViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncRepository: SyncRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SyncUiState())
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    init {
        loadLastSync()
        observeWorkManager()
    }

    private fun loadLastSync() {
        val lastSync = SharedPreferencesHelper.getLastSyncDate(context)
        val interval = SharedPreferencesHelper.getSyncInterval(context)
        val autoEnabled = SharedPreferencesHelper.isAutoSyncEnabled(context)
        _uiState.update {
            it.copy(
                lastSyncDate = lastSync?.let { d -> DateUtils.parseDate(d)?.let { dd -> DateUtils.formatDateTime(dd) } } ?: "Never",
                autoSyncEnabled = autoEnabled,
                syncIntervalMinutes = interval
            )
        }

        if (autoEnabled) {
            SyncWorker.schedulePeriodicSync(context, interval.toLong())
        }
    }

    private fun observeWorkManager() {
        viewModelScope.launch {
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkFlow(SyncWorker.WORK_NAME + "_now")
                .collect { infos ->
                    val info = infos.firstOrNull()
                    val syncing = info?.state == WorkInfo.State.RUNNING
                    _uiState.update { it.copy(isSyncing = syncing) }
                }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, statusMessage = "Syncing...", isSuccess = null) }
            when (val result = syncRepository.syncAll(onProgress = { msg ->
                _uiState.update { it.copy(statusMessage = msg) }
            })) {
                is SyncResult.Success -> {
                    loadLastSync()
                    _uiState.update {
                        it.copy(isSyncing = false, isSuccess = true,
                            patientsAdded = result.patientsAdded, biometricsAdded = result.biometricsAdded,
                            statusMessage = "Sync complete! ${result.patientsAdded} patients, ${result.biometricsAdded} biometrics updated.")
                    }
                }
                is SyncResult.Error -> _uiState.update { it.copy(isSyncing = false, isSuccess = false, statusMessage = "Error: ${result.message}") }
                is SyncResult.NoNetwork -> _uiState.update { it.copy(isSyncing = false, isSuccess = false, statusMessage = "No network connection.") }
                is SyncResult.NotConfigured -> _uiState.update { it.copy(isSyncing = false, isSuccess = false, statusMessage = "App not configured. Please complete setup.") }
            }
        }
    }

    fun setAutoSync(enabled: Boolean) {
        SharedPreferencesHelper.setAutoSyncEnabled(enabled)
        _uiState.update { it.copy(autoSyncEnabled = enabled) }
        if (enabled) SyncWorker.schedulePeriodicSync(context, uiState.value.syncIntervalMinutes.toLong())
        else SyncWorker.cancelSync(context)
    }

    fun setSyncInterval(minutes: Int) {
        SharedPreferencesHelper.setSyncInterval(context, minutes)
        _uiState.update { it.copy(syncIntervalMinutes = minutes) }
        if (uiState.value.autoSyncEnabled) SyncWorker.schedulePeriodicSync(context, minutes.toLong())
    }
}