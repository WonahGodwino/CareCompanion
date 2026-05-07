package com.carecompanion.presentation.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carecompanion.data.database.entities.IITClient
import com.carecompanion.data.repository.PatientRepository
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
data class ArtRefillClientItem(
    val client: IITClient,
    val eligibilityGroup: String,   // ACTIVE_ELIGIBLE | ACTIVE_NOT_DUE | ACTIVE_NO_PHARMACY_HISTORY | IIT | TRANSFER_OUT | STOPPED_TREATMENT | DEATH | OTHER_INACTIVE | UNKNOWN_STATUS
    val careCategory: String,
    val medicationExpiry: Date?,    // null only when no pharmacy history
    val daysUntilExpiry: Long,      // negative = already expired
)

private val ON_TREATMENT = setOf("ART_START", "ART_TRANSFER_IN", "ACTIVE", "ACTIVE_TX_CURR")
private val IIT_STATUSES = setOf("INTERRUPTED_IN_TREATMENT", "IIT", "LTFU", "LOST_TO_FOLLOW_UP")
private val TRANSFER_OUT = setOf("ART_TRANSFER_OUT", "TRANSFER_OUT")
private val STOPPED      = setOf("STOPPED_TREATMENT", "TREATMENT_STOPPED", "ART_STOP")
private val DEATH        = setOf("DEATH", "DIED", "DEAD")
private const val GRACE_DAYS = 28L
private const val EARLY_DAYS = 7L

private fun normStatus(s: String?) = s?.trim()?.uppercase()?.replace(" ", "_")?.replace("-", "_")

fun IITClient.toArtRefillItem(): ArtRefillClientItem {
    val todayMs = System.currentTimeMillis()
    val status  = normStatus(currentStatus)
    val careCategory = when {
        status in TRANSFER_OUT -> "TRANSFER_OUT"
        status in STOPPED      -> "STOPPED_TREATMENT"
        status in DEATH        -> "DEATH"
        status in IIT_STATUSES -> "IIT"
        status in ON_TREATMENT -> {
            val vd = lastVisitDate ?: return ArtRefillClientItem(this, "IIT", "IIT", null, 0)
            val coverageEnd = vd.time + (refillPeriod ?: 0) * 86400000L + GRACE_DAYS * 86400000L
            if (coverageEnd >= todayMs) "ACTIVE" else "IIT"
        }
        status == null         -> "UNKNOWN_STATUS"
        else                   -> "OTHER_INACTIVE"
    }

    if (careCategory != "ACTIVE") {
        return ArtRefillClientItem(this, careCategory, careCategory, null, 0)
    }

    val vd = lastVisitDate ?: return ArtRefillClientItem(
        this, "ACTIVE_NO_PHARMACY_HISTORY", "ACTIVE", null, 0
    )
    val refillMs      = (refillPeriod ?: 0).toLong() * 86400000L
    val expiryMs      = vd.time + refillMs + GRACE_DAYS * 86400000L
    val earlyWindowMs = expiryMs - EARLY_DAYS * 86400000L
    val daysUntil     = TimeUnit.MILLISECONDS.toDays(expiryMs - todayMs)
    val group         = if (todayMs >= earlyWindowMs) "ACTIVE_ELIGIBLE" else "ACTIVE_NOT_DUE"
    return ArtRefillClientItem(this, group, "ACTIVE", Date(expiryMs), daysUntil)
}

// ─────────────────────────────────────────────────────────────────────────────
// UI State
// ─────────────────────────────────────────────────────────────────────────────
data class ArtRefillUiState(
    val clients: List<ArtRefillClientItem> = emptyList(),
    val groupedByEligibility: Map<String, List<ArtRefillClientItem>> = emptyMap(),
    val totalCount: Int = 0,
    // Group counts
    val activeEligibleCount: Int = 0,
    val activeNotDueCount: Int = 0,
    val activeNoPharmacyCount: Int = 0,
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

private val GROUP_ORDER = listOf(
    "ACTIVE_ELIGIBLE",
    "ACTIVE_NOT_DUE",
    "ACTIVE_NO_PHARMACY_HISTORY",
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
class ArtRefillViewModel @Inject constructor(
    private val patientRepository: PatientRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _searchQuery  = MutableStateFlow("")
    private val _errorMessage = MutableStateFlow<String?>(null)

    private fun facilityId() = SharedPreferencesHelper.getActiveFacilityId(context)

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val artRefillFlow: Flow<List<IITClient>> = _searchQuery
        .debounce(300)
        .distinctUntilChanged()
        .flatMapLatest { query ->
            val fid = facilityId()
            when {
                query.isBlank() && fid > 0 -> patientRepository.observeArtRefillClientsByFacility(fid)
                query.isBlank()            -> patientRepository.observeArtRefillClients()
                fid > 0                    -> patientRepository.observeArtRefillSearchByFacility(query, fid)
                else                       -> patientRepository.observeArtRefillSearch(query)
            }
        }

    val uiState: StateFlow<ArtRefillUiState> = combine(
        artRefillFlow.catch { e -> _errorMessage.value = e.message; emit(emptyList()) },
        _searchQuery,
        _errorMessage,
    ) { clients, query, error ->
        val items = clients.map { it.toArtRefillItem() }
        val grouped = GROUP_ORDER.associateWith { g -> items.filter { it.eligibilityGroup == g } }
        ArtRefillUiState(
            clients              = items,
            groupedByEligibility = grouped,
            totalCount           = items.size,
            activeEligibleCount  = grouped["ACTIVE_ELIGIBLE"]?.size ?: 0,
            activeNotDueCount    = grouped["ACTIVE_NOT_DUE"]?.size ?: 0,
            activeNoPharmacyCount= grouped["ACTIVE_NO_PHARMACY_HISTORY"]?.size ?: 0,
            iitCount             = grouped["IIT"]?.size ?: 0,
            transferOutCount     = grouped["TRANSFER_OUT"]?.size ?: 0,
            stoppedCount         = grouped["STOPPED_TREATMENT"]?.size ?: 0,
            deathCount           = grouped["DEATH"]?.size ?: 0,
            otherInactiveCount   = grouped["OTHER_INACTIVE"]?.size ?: 0,
            unknownStatusCount   = grouped["UNKNOWN_STATUS"]?.size ?: 0,
            isLoading            = false,
            searchQuery          = query,
            errorMessage         = error,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ArtRefillUiState(isLoading = true),
    )

    fun onSearchQueryChanged(q: String) { _searchQuery.value = q }
    fun clearSearch() { _searchQuery.value = "" }
    fun clearError() { _errorMessage.value = null }
}
