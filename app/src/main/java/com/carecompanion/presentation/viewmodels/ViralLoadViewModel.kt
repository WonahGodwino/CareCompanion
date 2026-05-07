package com.carecompanion.presentation.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carecompanion.data.database.entities.Patient
import com.carecompanion.data.repository.PatientRepository
import com.carecompanion.utils.DateUtils
import com.carecompanion.utils.SharedPreferencesHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────────────
// Per-client computed item
// ─────────────────────────────────────────────────────────────────────────────
data class VLClientItem(
    val patient: Patient,
    val eligibilityGroup: String,   // ACTIVE_ELIGIBLE_BASELINE | ACTIVE_ELIGIBLE_ROUTINE | ACTIVE_NOT_DUE | ACTIVE_NO_VL_DATA | IIT | TRANSFER_OUT | STOPPED_TREATMENT | DEATH | OTHER_INACTIVE | UNKNOWN_STATUS
    val careCategory: String,
    val vlType: String,             // "baseline" | "routine" | "none"
    val dueDate: Date?,
    val daysUntilDue: Long,         // negative = overdue
    val lastResultText: String?,
)

private val VL_BASELINE_DAYS      = 181L
private val VL_ROUTINE_ADULT_DAYS = 365L
private val VL_ROUTINE_CHILD_DAYS = 180L

private val ON_TX  = setOf("ART_START", "ART_TRANSFER_IN", "ACTIVE", "ACTIVE_TX_CURR")
private val IIT_ST = setOf("INTERRUPTED_IN_TREATMENT", "IIT", "LTFU", "LOST_TO_FOLLOW_UP")
private val TO_ST  = setOf("ART_TRANSFER_OUT", "TRANSFER_OUT")
private val STP_ST = setOf("STOPPED_TREATMENT", "TREATMENT_STOPPED", "ART_STOP")
private val DTH_ST = setOf("DEATH", "DIED", "DEAD")

private fun normSt(s: String?) = s?.trim()?.uppercase()?.replace(" ", "_")?.replace("-", "_")

private fun vlResultText(result: Long?): String = when {
    result == null  -> "Result pending"
    result <= 20    -> "Undetectable (≤20 cp/mL)"
    result < 1000   -> "Suppressed (${result} cp/mL)"
    else            -> "Unsuppressed (${result} cp/mL)"
}

fun Patient.toVLClientItem(): VLClientItem {
    val todayMs = System.currentTimeMillis()
    val status  = normSt(currentStatus)

    val careCategory = when {
        status in TO_ST  -> "TRANSFER_OUT"
        status in STP_ST -> "STOPPED_TREATMENT"
        status in DTH_ST -> "DEATH"
        status in IIT_ST -> "IIT"
        status in ON_TX  -> "ACTIVE"
        status == null   -> "UNKNOWN_STATUS"
        else             -> "OTHER_INACTIVE"
    }

    if (careCategory != "ACTIVE") {
        return VLClientItem(this, careCategory, careCategory, "none", null, 0, null)
    }

    val artStart = artStartDate
    if (artStart == null) {
        return VLClientItem(this, "ACTIVE_NO_VL_DATA", "ACTIVE", "none", null, 0, null)
    }

    val ageYears    = DateUtils.calculateAge(dateOfBirth)
    val vlDate      = lastViralLoadDate
    val vlResult    = lastViralLoadResult
    val resultText  = vlResultText(vlResult)

    if (vlDate == null) {
        val baseDueMs   = artStart.time + VL_BASELINE_DAYS * 86400000L
        val daysUntil   = TimeUnit.MILLISECONDS.toDays(baseDueMs - todayMs)
        val group       = if (todayMs >= baseDueMs) "ACTIVE_ELIGIBLE_BASELINE" else "ACTIVE_NOT_DUE"
        return VLClientItem(this, group, "ACTIVE", "baseline", Date(baseDueMs), daysUntil, null)
    }

    val intervalDays  = if (ageYears > 14) VL_ROUTINE_ADULT_DAYS else VL_ROUTINE_CHILD_DAYS
    val routineDueMs  = vlDate.time + intervalDays * 86400000L
    val daysUntil     = TimeUnit.MILLISECONDS.toDays(routineDueMs - todayMs)
    val group         = if (todayMs >= routineDueMs) "ACTIVE_ELIGIBLE_ROUTINE" else "ACTIVE_NOT_DUE"
    return VLClientItem(this, group, "ACTIVE", "routine", Date(routineDueMs), daysUntil, resultText)
}

// ─────────────────────────────────────────────────────────────────────────────
// UI State
// ─────────────────────────────────────────────────────────────────────────────
data class ViralLoadUiState(
    val clients: List<VLClientItem> = emptyList(),
    val groupedByEligibility: Map<String, List<VLClientItem>> = emptyMap(),
    val totalCount: Int = 0,
    // Group counts
    val eligibleBaselineCount: Int = 0,
    val eligibleRoutineCount: Int = 0,
    val notDueCount: Int = 0,
    val noVlDataCount: Int = 0,
    val iitCount: Int = 0,
    val transferOutCount: Int = 0,
    val stoppedCount: Int = 0,
    val deathCount: Int = 0,
    val otherInactiveCount: Int = 0,
    val unknownStatusCount: Int = 0,
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val errorMessage: String? = null,
)

private val VL_GROUP_ORDER = listOf(
    "ACTIVE_ELIGIBLE_BASELINE",
    "ACTIVE_ELIGIBLE_ROUTINE",
    "ACTIVE_NOT_DUE",
    "ACTIVE_NO_VL_DATA",
    "IIT",
    "TRANSFER_OUT",
    "STOPPED_TREATMENT",
    "DEATH",
    "OTHER_INACTIVE",
    "UNKNOWN_STATUS",
)

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────
@HiltViewModel
class ViralLoadViewModel @Inject constructor(
    private val patientRepository: PatientRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _searchQuery  = MutableStateFlow("")
    private val _errorMessage = MutableStateFlow<String?>(null)

    private fun facilityId() = SharedPreferencesHelper.getActiveFacilityId(context)

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val patientFlow: Flow<List<Patient>> = _searchQuery
        .debounce(300)
        .distinctUntilChanged()
        .flatMapLatest { query ->
            val fid = facilityId()
            when {
                query.isBlank() && fid > 0 -> patientRepository.observeAllActivePatientsByFacilityFlow(fid)
                query.isBlank()            -> patientRepository.observeAllActivePatientsFlow()
                fid > 0                    -> patientRepository.observePatientSearchByFacility(query, fid)
                else                       -> patientRepository.observePatientSearch(query)
            }
        }

    val uiState: StateFlow<ViralLoadUiState> = combine(
        patientFlow.catch { e -> _errorMessage.value = e.message; emit(emptyList()) },
        _searchQuery,
        _errorMessage,
    ) { patients, query, error ->
        val items = patients
            .filter { it.artStartDate != null || it.currentStatus != null }
            .map { it.toVLClientItem() }
        val grouped = VL_GROUP_ORDER.associateWith { g -> items.filter { it.eligibilityGroup == g } }
        ViralLoadUiState(
            clients               = items,
            groupedByEligibility  = grouped,
            totalCount            = items.size,
            eligibleBaselineCount = grouped["ACTIVE_ELIGIBLE_BASELINE"]?.size ?: 0,
            eligibleRoutineCount  = grouped["ACTIVE_ELIGIBLE_ROUTINE"]?.size ?: 0,
            notDueCount           = grouped["ACTIVE_NOT_DUE"]?.size ?: 0,
            noVlDataCount         = grouped["ACTIVE_NO_VL_DATA"]?.size ?: 0,
            iitCount              = grouped["IIT"]?.size ?: 0,
            transferOutCount      = grouped["TRANSFER_OUT"]?.size ?: 0,
            stoppedCount          = grouped["STOPPED_TREATMENT"]?.size ?: 0,
            deathCount            = grouped["DEATH"]?.size ?: 0,
            otherInactiveCount    = grouped["OTHER_INACTIVE"]?.size ?: 0,
            unknownStatusCount    = grouped["UNKNOWN_STATUS"]?.size ?: 0,
            isLoading             = false,
            searchQuery           = query,
            errorMessage          = error,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ViralLoadUiState(isLoading = true),
    )

    fun onSearchQueryChanged(q: String) { _searchQuery.value = q }
    fun clearSearch() { _searchQuery.value = "" }
    fun clearError() { _errorMessage.value = null }
}
