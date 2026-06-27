package com.carecompanion.presentation.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.carecompanion.data.network.WincoApiService
import com.carecompanion.data.network.models.WincoSummary
import com.carecompanion.data.repository.SyncRepository
import com.carecompanion.data.repository.SyncResult
import com.carecompanion.data.sync.SyncWorker
import com.carecompanion.utils.DateUtils
import com.carecompanion.utils.SharedPreferencesHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SyncUiState(
    val isSyncing: Boolean = false,
    val lastSyncDate: String = "Never",
    val statusMessage: String = "",
    val patientsAdded: Int = 0,
    val biometricsAdded: Int = 0,
    val pagesRead: Int = 0,
    val biometricCandidates: Int = 0,
    val biometricsSkipped: Int = 0,
    val biometricsFailed: Int = 0,
    val isSuccess: Boolean? = null,
    val autoSyncEnabled: Boolean = true,
    val syncIntervalMinutes: Int = 5,
    val includeTxMl: Boolean = false,
    val txMlStartDate: String = "",
    val txMlEndDate: String = "",
    val dashboardSummary: WincoSummary? = null,
)

@HiltViewModel
class SyncViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncRepository: SyncRepository,
    private val wincoApiService: WincoApiService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SyncUiState())
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    init {
        loadLastSync()
        observeWorkManager()
        loadDashboardSummary()
    }

    fun loadDashboardSummary() {
        viewModelScope.launch {
            try {
                // Scope KPIs to THIS facility — without it WINCO returns the all-facility total,
                // inflating TX_CURR on a multi-facility server.
                val facilityId = SharedPreferencesHelper.getActiveFacilityId(context).takeIf { it > 0L }
                val summary = wincoApiService.getDashboardSummary(facilityId = facilityId)
                _uiState.update { it.copy(dashboardSummary = summary) }
            } catch (_: Exception) {
                // Non-fatal — KPI cards simply stay hidden
            }
        }
    }

    private fun loadLastSync() {
        val lastSync = SharedPreferencesHelper.getLastSyncDate(context)
        val interval = SharedPreferencesHelper.getSyncInterval(context)
        val autoEnabled = SharedPreferencesHelper.isAutoSyncEnabled(context)
        val includeTxMl = SharedPreferencesHelper.isTxMlIncludeEnabled(context)
        val txMlStartDate = SharedPreferencesHelper.getTxMlStartDate(context)
        val txMlEndDate = SharedPreferencesHelper.getTxMlEndDate(context)
        _uiState.update {
            it.copy(
                lastSyncDate = lastSync?.let { d -> DateUtils.parseDate(d)?.let { dd -> DateUtils.formatDateTime(dd) } } ?: "Never",
                autoSyncEnabled = autoEnabled,
                syncIntervalMinutes = interval,
                includeTxMl = includeTxMl,
                txMlStartDate = txMlStartDate,
                txMlEndDate = txMlEndDate,
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
            val state = _uiState.value
            if (state.includeTxMl) {
                val start = state.txMlStartDate.trim()
                val end = state.txMlEndDate.trim()
                if (!isValidIsoDate(start) || !isValidIsoDate(end)) {
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            isSuccess = false,
                            statusMessage = "TX_ML date range must be valid yyyy-MM-dd for both start and end dates."
                        )
                    }
                    return@launch
                }
                if (start > end) {
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            isSuccess = false,
                            statusMessage = "TX_ML start date cannot be after end date."
                        )
                    }
                    return@launch
                }
            }

            _uiState.update { it.copy(isSyncing = true, statusMessage = "Syncing...", isSuccess = null) }
            when (val result = syncRepository.syncAll(onProgress = { msg ->
                _uiState.update { it.copy(statusMessage = msg) }
            })) {
                is SyncResult.Success -> {
                    loadLastSync()
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            isSuccess = true,
                            patientsAdded = result.patientsAdded,
                            biometricsAdded = result.biometricsAdded,
                            pagesRead = result.audit.pagesRead,
                            biometricCandidates = result.audit.biometricCandidates,
                            biometricsSkipped = result.audit.biometricsSkipped,
                            biometricsFailed = result.audit.biometricsFailed,
                            statusMessage = "Sync complete! ${result.patientsAdded} patients, ${result.biometricsAdded} biometrics updated."
                        )
                    }
                    loadDashboardSummary()
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

    fun setIncludeTxMl(enabled: Boolean) {
        SharedPreferencesHelper.setTxMlIncludeEnabled(enabled)
        _uiState.update { it.copy(includeTxMl = enabled) }
    }

    fun setTxMlStartDate(value: String) {
        SharedPreferencesHelper.setTxMlStartDate(value.trim())
        _uiState.update { it.copy(txMlStartDate = value.trim()) }
    }

    fun setTxMlEndDate(value: String) {
        SharedPreferencesHelper.setTxMlEndDate(value.trim())
        _uiState.update { it.copy(txMlEndDate = value.trim()) }
    }

    private fun isValidIsoDate(value: String): Boolean {
        if (value.isBlank()) return false
        return runCatching {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
            parser.parse(value)
        }.isSuccess
    }
}