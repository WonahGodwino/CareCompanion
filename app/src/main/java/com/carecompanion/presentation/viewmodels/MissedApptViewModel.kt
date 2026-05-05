package com.carecompanion.presentation.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carecompanion.data.database.entities.IITClient
import com.carecompanion.data.database.entities.IITPeriod
import com.carecompanion.data.repository.PatientRepository
import com.carecompanion.utils.SharedPreferencesHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────────────
// Missed appointment tier — based on days since scheduled appointment
// ─────────────────────────────────────────────────────────────────────────────
enum class MissedApptTier(val label: String, val shortLabel: String) {
    RECENT        ("Just Missed",             "1–6d"),
    OVERDUE       ("Overdue",                 "7–24d"),
    HIGH_PRIORITY ("High Priority Tracking",  "25–27d"),
    IIT_CONFIRMED ("Already IIT",             "28d+")
}

fun IITClient.missedDays(): Int =
    nextAppointment?.let { appt ->
        TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - appt.time).toInt().coerceAtLeast(0)
    } ?: 0

fun IITClient.missedApptTier(): MissedApptTier = when {
    missedDays() >= 28 -> MissedApptTier.IIT_CONFIRMED
    missedDays() >= 25 -> MissedApptTier.HIGH_PRIORITY
    missedDays() >= 7  -> MissedApptTier.OVERDUE
    else               -> MissedApptTier.RECENT
}

// ─────────────────────────────────────────────────────────────────────────────
// UI State
// ─────────────────────────────────────────────────────────────────────────────
data class MissedApptUiState(
    val clients: List<IITClient> = emptyList(),
    val groupedByPeriod: Map<IITPeriod, List<IITClient>> = emptyMap(),
    val totalCount: Int = 0,
    val todayCount: Int = 0,
    val thisWeekCount: Int = 0,
    val lastWeekCount: Int = 0,
    val thisMonthCount: Int = 0,
    val thisFYCount: Int = 0,
    val previousCount: Int = 0,
    // Tier counts
    val recentCount: Int = 0,
    val overdueCount: Int = 0,
    val highPriorityCount: Int = 0,
    val iitConfirmedCount: Int = 0,
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val errorMessage: String? = null
)

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────
@HiltViewModel
class MissedApptViewModel @Inject constructor(
    private val patientRepository: PatientRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _searchQuery  = MutableStateFlow("")
    private val _errorMessage = MutableStateFlow<String?>(null)

    private fun facilityId() = SharedPreferencesHelper.getActiveFacilityId(context)
    private fun todayMs(): Long = System.currentTimeMillis()

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val missedFlow: Flow<List<IITClient>> = _searchQuery
        .debounce(300)
        .distinctUntilChanged()
        .flatMapLatest { query ->
            val now = todayMs()
            val fid = facilityId()
            when {
                query.isBlank() && fid > 0 -> patientRepository.observeMissedApptClientsByFacility(now, fid)
                query.isBlank()            -> patientRepository.observeMissedApptClients(now)
                fid > 0                    -> patientRepository.observeMissedApptSearchByFacility(query, now, fid)
                else                       -> patientRepository.observeMissedApptSearch(query, now)
            }
        }

    val uiState: StateFlow<MissedApptUiState> = combine(
        missedFlow.catch { e -> _errorMessage.value = e.message; emit(emptyList()) },
        _searchQuery,
        _errorMessage
    ) { clients, query, error ->
        val sorted = clients.sortedWith(
            compareByDescending<IITClient> { it.nextAppointment?.time ?: Long.MIN_VALUE }
        )
        val grouped = IITPeriod.values().associateWith { period ->
            // Use nextAppointment date as the period key for missed appointments
            sorted.filter { client ->
                val entryDate = client.nextAppointment
                IITPeriod.of(entryDate) == period
            }
        }
        MissedApptUiState(
            clients         = sorted,
            groupedByPeriod = grouped,
            totalCount      = sorted.size,
            todayCount      = grouped[IITPeriod.TODAY]?.size ?: 0,
            thisWeekCount   = grouped[IITPeriod.THIS_WEEK]?.size ?: 0,
            lastWeekCount   = grouped[IITPeriod.LAST_WEEK]?.size ?: 0,
            thisMonthCount  = grouped[IITPeriod.THIS_MONTH]?.size ?: 0,
            thisFYCount     = grouped[IITPeriod.THIS_FY]?.size ?: 0,
            previousCount   = grouped[IITPeriod.PREVIOUS]?.size ?: 0,
            recentCount     = sorted.count { it.missedApptTier() == MissedApptTier.RECENT },
            overdueCount    = sorted.count { it.missedApptTier() == MissedApptTier.OVERDUE },
            highPriorityCount = sorted.count { it.missedApptTier() == MissedApptTier.HIGH_PRIORITY },
            iitConfirmedCount = sorted.count { it.missedApptTier() == MissedApptTier.IIT_CONFIRMED },
            isLoading       = false,
            searchQuery     = query,
            errorMessage    = error
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        MissedApptUiState(isLoading = true)
    )

    fun onSearchQueryChanged(q: String) { _searchQuery.value = q }
    fun clearSearch() { _searchQuery.value = "" }
    fun clearError() { _errorMessage.value = null }
}
