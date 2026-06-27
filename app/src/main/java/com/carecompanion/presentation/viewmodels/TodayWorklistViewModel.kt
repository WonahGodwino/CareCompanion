package com.carecompanion.presentation.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carecompanion.data.database.entities.WorklistEntry
import com.carecompanion.data.repository.PatientRepository
import com.carecompanion.utils.SharedPreferencesHelper
import com.carecompanion.utils.ViralLoadEligibilityEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.inject.Inject

// ── Due-service tags ──────────────────────────────────────────────────────────

enum class DueService(
    val label: String,
    val shortLabel: String,
    val colorHex: Long
) {
    ART_REFILL        ("ART Refill",          "Refill",   0xFF01579B),
    VIRAL_LOAD        ("Viral Load Due",       "VL",       0xFF6A1B9A),
    TB_SCREENING      ("TB Screen Overdue",   "TB Screen", 0xFF2E7D32),
    BIOMETRIC_RECAPTURE("Biometric Recapture","Bio",      0xFFC62828)
}

data class WorklistUiEntry(
    val entry: WorklistEntry,
    val dueServices: List<DueService>
)

data class TodayWorklistUiState(
    val entries: List<WorklistUiEntry> = emptyList(),
    val totalCount: Int = 0,
    val todayLabel: String = "",
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class TodayWorklistViewModel @Inject constructor(
    private val patientRepository: PatientRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val watZone = TimeZone.getTimeZone("Africa/Lagos")
    private val _error = MutableStateFlow<String?>(null)

    private fun todayWindow(): Pair<Long, Long> {
        val cal = Calendar.getInstance(watZone)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        return start to cal.timeInMillis
    }

    private fun todayLabel(): String {
        val cal = Calendar.getInstance(watZone)
        val months = arrayOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
        return "${cal.get(Calendar.DAY_OF_MONTH)} ${months[cal.get(Calendar.MONTH)]} ${cal.get(Calendar.YEAR)}"
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val worklistFlow: Flow<List<WorklistEntry>> = flowOf(Unit)
        .flatMapLatest {
            val (start, end) = todayWindow()
            val fid = SharedPreferencesHelper.getActiveFacilityId(context)
            if (fid > 0)
                patientRepository.observeTodayWorklistByFacility(start, end, fid)
            else
                patientRepository.observeTodayWorklist(start, end)
        }

    val uiState: StateFlow<TodayWorklistUiState> = worklistFlow
        .catch { e -> _error.value = e.message; emit(emptyList()) }
        .combine(_error) { entries, error ->
            val uiEntries = entries.map { e ->
                WorklistUiEntry(entry = e, dueServices = computeDueServices(e))
            }
            TodayWorklistUiState(
                entries    = uiEntries,
                totalCount = uiEntries.size,
                todayLabel = todayLabel(),
                isLoading  = false,
                errorMessage = error
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayWorklistUiState(isLoading = true))

    fun clearError() { _error.value = null }

    private fun computeDueServices(e: WorklistEntry): List<DueService> {
        val services = mutableListOf<DueService>()
        services.add(DueService.ART_REFILL)

        // VL eligible today?
        val vlEligibility = ViralLoadEligibilityEngine.evaluate(
            dateOfBirth = e.dateOfBirth,
            artRegistrationDate = e.artStartDate,
            dateSampleCollected = e.lastViralLoadDate,
            referenceDate = Date()
        )
        if (vlEligibility?.isEligibleToday == true) services.add(DueService.VIRAL_LOAD)

        // TB screening overdue? (>12 months since last screen, or never screened)
        val tbOverdue = when (val tbDate = e.lastTbScreeningDate) {
            null -> true  // never screened
            else -> TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - tbDate.time) > 365
        }
        if (tbOverdue) services.add(DueService.TB_SCREENING)

        // Biometric recapture recommended (>15 days since last biometric)
        val bioRecapture = when {
            e.biometricCount == 0 -> true  // no biometrics at all
            e.lastBiometricDate == null -> false
            else -> TimeUnit.MILLISECONDS.toDays(
                System.currentTimeMillis() - e.lastBiometricDate.time
            ) >= 15
        }
        if (bioRecapture) services.add(DueService.BIOMETRIC_RECAPTURE)

        return services
    }
}
