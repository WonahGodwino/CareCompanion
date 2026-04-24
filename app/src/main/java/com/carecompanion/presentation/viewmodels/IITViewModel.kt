package com.carecompanion.presentation.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carecompanion.data.database.entities.IITClient
import com.carecompanion.data.database.entities.IITPeriod
import com.carecompanion.data.database.entities.IITTier
import com.carecompanion.data.repository.PatientRepository
import com.carecompanion.utils.SharedPreferencesHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import javax.inject.Inject

data class IITUiState(
    val clients: List<IITClient> = emptyList(),
    val groupedByPeriod: Map<IITPeriod, List<IITClient>> = emptyMap(),
    val totalCount: Int = 0,
    // Period counts
    val todayCount: Int = 0,
    val thisWeekCount: Int = 0,
    val lastWeekCount: Int = 0,
    val thisMonthCount: Int = 0,
    val thisFYCount: Int = 0,
    val previousCount: Int = 0,
    // Tier counts (Early IIT / IIT / LTFU) — kept for reference
    val earlyIITCount: Int = 0,
    val iitCount: Int = 0,
    val ltfuCount: Int = 0,
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val errorMessage: String? = null
)

@HiltViewModel
class IITViewModel @Inject constructor(
    private val patientRepository: PatientRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val watTimeZone: TimeZone = TimeZone.getTimeZone("Africa/Lagos")

    private val _searchQuery  = MutableStateFlow("")
    private val _errorMessage = MutableStateFlow<String?>(null)

    private fun facilityId() = SharedPreferencesHelper.getActiveFacilityId(context)

    /** PEPFAR IIT cutoff — today minus 28 days. Recomputed each subscription. */
    private fun iitCutoff(): Date = Calendar.getInstance(watTimeZone).apply {
        add(Calendar.DAY_OF_YEAR, -28)
    }.time

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val iitFlow: Flow<List<IITClient>> = _searchQuery
        .debounce(300)
        .distinctUntilChanged()
        .flatMapLatest { query ->
            val cutoff = iitCutoff()
            val fid = facilityId()
            when {
                query.isBlank() && fid > 0 -> patientRepository.observeIITClientsByFacility(cutoff, fid)
                query.isBlank()            -> patientRepository.observeIITClients(cutoff)
                fid > 0                    -> patientRepository.observeIITSearchByFacility(query, cutoff, fid)
                else                       -> patientRepository.observeIITSearch(query, cutoff)
            }
        }

    val uiState: StateFlow<IITUiState> = combine(
        iitFlow.catch { e -> _errorMessage.value = e.message; emit(emptyList()) },
        _searchQuery,
        _errorMessage
    ) { clients, query, error ->
        val sortedClients = clients.sortedWith(
            compareByDescending<IITClient> { it.iitEntryDate?.time ?: Long.MIN_VALUE }
                .thenByDescending { it.nextAppointment?.time ?: Long.MIN_VALUE }
        )
        val early   = clients.count { it.iitTier == IITTier.EARLY_IIT }
        val iit     = clients.count { it.iitTier == IITTier.IIT }
        val ltfu    = clients.count { it.iitTier == IITTier.LTFU }
        val grouped = IITPeriod.values().associateWith { period ->
            sortedClients.filter { it.iitPeriod() == period }
        }
        IITUiState(
            clients          = sortedClients,
            groupedByPeriod  = grouped,
            totalCount       = sortedClients.size,
            todayCount       = grouped[IITPeriod.TODAY]?.size ?: 0,
            thisWeekCount    = grouped[IITPeriod.THIS_WEEK]?.size ?: 0,
            lastWeekCount    = grouped[IITPeriod.LAST_WEEK]?.size ?: 0,
            thisMonthCount   = grouped[IITPeriod.THIS_MONTH]?.size ?: 0,
            thisFYCount      = grouped[IITPeriod.THIS_FY]?.size ?: 0,
            previousCount    = grouped[IITPeriod.PREVIOUS]?.size ?: 0,
            earlyIITCount    = early,
            iitCount         = iit,
            ltfuCount        = ltfu,
            isLoading        = false,
            searchQuery      = query,
            errorMessage     = error
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        IITUiState(isLoading = true)
    )

    fun onSearchQueryChanged(q: String) { _searchQuery.value = q }
    fun clearSearch() { _searchQuery.value = "" }
    fun clearError() { _errorMessage.value = null }
}
