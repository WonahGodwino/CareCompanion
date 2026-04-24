package com.carecompanion.presentation.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carecompanion.data.database.dao.FacilityDao
import com.carecompanion.data.database.dao.PatientDao
import com.carecompanion.data.database.dao.BiometricDao
import com.carecompanion.data.database.dao.SyncLogDao
import com.carecompanion.data.database.dao.ArtPharmacyDao
import com.carecompanion.data.database.entities.Facility
import com.carecompanion.data.network.WincoApiService
import com.carecompanion.data.network.models.WincoTokenRequest
import com.carecompanion.data.sync.ReminderWorker
import com.carecompanion.utils.SharedPreferencesHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class SettingsUiState(
    val emrUrl: String = "",
    val emrUsername: String = "",
    val emrPassword: String = "",
    val activeFacilityId: Long = 0L,
    val activeFacilityName: String = "",
    val facilities: List<Facility> = emptyList(),
    val scannerType: String = "",
    val isLoading: Boolean = false,
    val isReAuthLoading: Boolean = false,
    val isSaved: Boolean = false,
    val reAuthSuccess: Boolean = false,
    val needsFacilitySelection: Boolean = false,
    val errorMessage: String? = null,
    // Notification preferences
    val notificationsEnabled: Boolean = true,
    val dailyDigestEnabled: Boolean = true,
    val artRefillsEnabled: Boolean = true,
    val missedApptsEnabled: Boolean = true,
    val viralLoadEnabled: Boolean = true,
    val tptEnabled: Boolean = true,
    val ahdEnabled: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val facilityDao: FacilityDao,
    private val patientDao: PatientDao,
    private val biometricDao: BiometricDao,
    private val syncLogDao: SyncLogDao,
    private val artPharmacyDao: ArtPharmacyDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init { loadSettings() }

    private fun loadSettings() {
        viewModelScope.launch {
            val facilities = withContext(Dispatchers.IO) { facilityDao.getAll() }
            _uiState.update {
                it.copy(
                    emrUrl = SharedPreferencesHelper.getWincoBaseUrl(context) ?: "",
                    emrUsername = SharedPreferencesHelper.getEmrUsername(context) ?: "",
                    emrPassword = SharedPreferencesHelper.getEmrPassword(context) ?: "",
                    activeFacilityId = SharedPreferencesHelper.getActiveFacilityId(context),
                    activeFacilityName = SharedPreferencesHelper.getActiveFacilityName(context) ?: "",
                    facilities = facilities,
                    scannerType = SharedPreferencesHelper.getScannerType(context) ?: "",
                    notificationsEnabled = SharedPreferencesHelper.areNotificationsEnabled(context),
                    dailyDigestEnabled   = SharedPreferencesHelper.isDailyDigestEnabled(context),
                    artRefillsEnabled    = SharedPreferencesHelper.isArtRefillsReminderEnabled(context),
                    missedApptsEnabled   = SharedPreferencesHelper.isMissedApptsReminderEnabled(context),
                    viralLoadEnabled     = SharedPreferencesHelper.isViralLoadReminderEnabled(context),
                    tptEnabled           = SharedPreferencesHelper.isTptReminderEnabled(context),
                    ahdEnabled           = SharedPreferencesHelper.isAhdAlertEnabled(context)
                )
            }
        }
    }

    fun onEmrUrlChanged(v: String) = _uiState.update { it.copy(emrUrl = v, isSaved = false) }
    fun onEmrUsernameChanged(v: String) = _uiState.update { it.copy(emrUsername = v, isSaved = false) }
    fun onEmrPasswordChanged(v: String) = _uiState.update { it.copy(emrPassword = v, isSaved = false) }
    fun onScannerTypeChanged(v: String) = _uiState.update { it.copy(scannerType = v, isSaved = false) }

    fun saveSettings() {
        val state = _uiState.value
        if (state.emrUrl.isBlank()) { _uiState.update { it.copy(errorMessage = "WINCO Server URL is required") }; return }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val raw = state.emrUrl.trim()
            val normalized = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "http://$raw"
            val baseUrl = normalized.trimEnd('/') + "/"
            SharedPreferencesHelper.setWincoBaseUrl(baseUrl)
            if (state.scannerType.isNotBlank()) SharedPreferencesHelper.setScannerType(context, state.scannerType)
            _uiState.update { it.copy(isLoading = false, isSaved = true, emrUrl = baseUrl) }
        }
    }

    fun reAuthenticate() {
        val state = _uiState.value
        if (state.emrUrl.isBlank() || state.emrUsername.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Save WINCO URL and credentials first") }; return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isReAuthLoading = true, reAuthSuccess = false) }
            try {
                val raw = state.emrUrl.trim()
                val normalized = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "http://$raw"
                val baseUrl = normalized.trimEnd('/') + "/"

                // Build a plain (unauthenticated) client — no Bearer header interceptor.
                // The token endpoint itself does not require authentication.
                val plainClient = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()
                val wincoService = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(plainClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(WincoApiService::class.java)

                // Step 1: obtain Bearer token from WINCO
                val tokenResp = withContext(Dispatchers.IO) {
                    wincoService.getToken(WincoTokenRequest(state.emrUsername, state.emrPassword))
                }
                val token = tokenResp.accessToken.ifBlank {
                    throw Exception(tokenResp.error ?: "No token returned — check credentials")
                }

                // Persist URL and token
                SharedPreferencesHelper.setWincoBaseUrl(baseUrl)
                SharedPreferencesHelper.setWincoApiKey(token)
                SharedPreferencesHelper.setEmrUsername(state.emrUsername)
                SharedPreferencesHelper.setEmrPassword(state.emrPassword)

                // Step 2: fetch facility list from WINCO (authenticated call)
                val authedClient = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .addInterceptor { chain ->
                        chain.proceed(
                            chain.request().newBuilder()
                                .addHeader("Authorization", "Bearer $token")
                                .addHeader("X-Client-Type", "mobile")
                                .build()
                        )
                    }.build()
                val authedWinco = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(authedClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(WincoApiService::class.java)

                val wincoFacilities = withContext(Dispatchers.IO) {
                    try { authedWinco.getFacilities() } catch (_: Exception) { emptyList() }
                }
                if (wincoFacilities.isNotEmpty()) {
                    val dbFacilities = wincoFacilities.map {
                        com.carecompanion.data.database.entities.Facility(
                            id = it.id, name = it.name,
                            facilityCode = null, state = it.state, lga = it.lga,
                            isActive = it.id == state.activeFacilityId
                        )
                    }
                    withContext(Dispatchers.IO) { facilityDao.insertAll(dbFacilities) }
                }

                val facilities = withContext(Dispatchers.IO) { facilityDao.getAll() }
                var newActiveFacilityId   = state.activeFacilityId
                var newActiveFacilityName = state.activeFacilityName
                if (facilities.size == 1) {
                    val f = facilities[0]
                    withContext(Dispatchers.IO) {
                        facilityDao.clearActive()
                        facilityDao.setActive(f.id)
                    }
                    SharedPreferencesHelper.setActiveFacilityId(f.id)
                    SharedPreferencesHelper.setActiveFacilityName(f.name)
                    newActiveFacilityId   = f.id
                    newActiveFacilityName = f.name
                }
                val needsPicker = facilities.size > 1
                _uiState.update {
                    it.copy(
                        isReAuthLoading = false, reAuthSuccess = true,
                        facilities = facilities, needsFacilitySelection = needsPicker,
                        activeFacilityId = newActiveFacilityId, activeFacilityName = newActiveFacilityName
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isReAuthLoading = false, errorMessage = e.message ?: "Authentication failed") }
            }
        }
    }

    fun switchFacility(facility: Facility) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                facilityDao.clearActive()
                facilityDao.setActive(facility.id)
            }
            SharedPreferencesHelper.setActiveFacilityId(facility.id)
            SharedPreferencesHelper.setActiveFacilityName(facility.name)
            _uiState.update { it.copy(activeFacilityId = facility.id, activeFacilityName = facility.name, isSaved = true) }
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // Clear all database tables
                patientDao.deleteAll()
                biometricDao.deleteAll()
                syncLogDao.deleteAll()
                artPharmacyDao.deleteAll()
                // Facilities and preferences are kept for reinit
            }
            // Clear all SharedPreferences and encrypted preferences
            SharedPreferencesHelper.clearAll(context)
            loadSettings()
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
    fun clearReAuthSuccess() = _uiState.update { it.copy(reAuthSuccess = false) }
    fun clearNeedsFacilitySelection() = _uiState.update { it.copy(needsFacilitySelection = false) }

    // ── Notification toggles (persist immediately, no Save button needed) ────

    fun toggleNotifications(enabled: Boolean) {
        SharedPreferencesHelper.setNotificationsEnabled(enabled)
        _uiState.update { it.copy(notificationsEnabled = enabled) }
        if (enabled) ReminderWorker.scheduleDailyReminder(context)
        else ReminderWorker.cancel(context)
    }

    fun toggleDailyDigest(enabled: Boolean) {
        SharedPreferencesHelper.setDailyDigestEnabled(enabled)
        _uiState.update { it.copy(dailyDigestEnabled = enabled) }
    }

    fun toggleArtRefills(enabled: Boolean) {
        SharedPreferencesHelper.setArtRefillsReminderEnabled(enabled)
        _uiState.update { it.copy(artRefillsEnabled = enabled) }
    }

    fun toggleMissedAppts(enabled: Boolean) {
        SharedPreferencesHelper.setMissedApptsReminderEnabled(enabled)
        _uiState.update { it.copy(missedApptsEnabled = enabled) }
    }

    fun toggleViralLoad(enabled: Boolean) {
        SharedPreferencesHelper.setViralLoadReminderEnabled(enabled)
        _uiState.update { it.copy(viralLoadEnabled = enabled) }
    }

    fun toggleTpt(enabled: Boolean) {
        SharedPreferencesHelper.setTptReminderEnabled(enabled)
        _uiState.update { it.copy(tptEnabled = enabled) }
    }

    fun toggleAhd(enabled: Boolean) {
        SharedPreferencesHelper.setAhdAlertEnabled(enabled)
        _uiState.update { it.copy(ahdEnabled = enabled) }
    }
}
