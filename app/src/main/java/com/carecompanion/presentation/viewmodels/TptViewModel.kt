package com.carecompanion.presentation.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carecompanion.data.database.entities.TptEntry
import com.carecompanion.data.database.entities.TptStatus
import com.carecompanion.data.repository.PatientRepository
import com.carecompanion.utils.SharedPreferencesHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class TptUiState(
    val all: List<TptEntry> = emptyList(),
    val notScreened: List<TptEntry> = emptyList(),
    val eligible: List<TptEntry> = emptyList(),
    val onIpt: List<TptEntry> = emptyList(),
    val tbPositive: List<TptEntry> = emptyList(),
    val other: List<TptEntry> = emptyList(),
    val totalCount: Int = 0,
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class TptViewModel @Inject constructor(
    private val patientRepository: PatientRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _searchQuery  = MutableStateFlow("")
    private val _errorMessage = MutableStateFlow<String?>(null)

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val tptFlow: Flow<List<TptEntry>> = _searchQuery
        .debounce(300)
        .distinctUntilChanged()
        .flatMapLatest { query ->
            val fid = SharedPreferencesHelper.getActiveFacilityId(context)
            val base = if (fid > 0)
                patientRepository.observeTptPatientsByFacility(fid)
            else
                patientRepository.observeTptPatients()

            if (query.isBlank()) base
            else base.map { list ->
                val q = query.trim().lowercase()
                list.filter { e ->
                    e.hospitalNumber.lowercase().contains(q) ||
                    e.firstName?.lowercase()?.contains(q) == true ||
                    e.surname?.lowercase()?.contains(q) == true ||
                    e.fullName?.lowercase()?.contains(q) == true
                }
            }
        }

    val uiState: StateFlow<TptUiState> = combine(
        tptFlow.catch { e -> _errorMessage.value = e.message; emit(emptyList()) },
        _searchQuery,
        _errorMessage
    ) { entries, query, error ->
        val byStatus = entries.groupBy { it.tptStatus }
        TptUiState(
            all          = entries,
            notScreened  = byStatus[TptStatus.NOT_SCREENED] ?: emptyList(),
            eligible     = byStatus[TptStatus.ELIGIBLE] ?: emptyList(),
            onIpt        = byStatus[TptStatus.ON_IPT] ?: emptyList(),
            tbPositive   = byStatus[TptStatus.TB_POSITIVE] ?: emptyList(),
            other        = byStatus[TptStatus.SCREENED_OTHER] ?: emptyList(),
            totalCount   = entries.size,
            searchQuery  = query,
            isLoading    = false,
            errorMessage = error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TptUiState(isLoading = true))

    fun onSearchQueryChanged(q: String) { _searchQuery.value = q }
    fun clearSearch() { _searchQuery.value = "" }
    fun clearError() { _errorMessage.value = null }
}
