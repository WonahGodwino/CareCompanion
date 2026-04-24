package com.carecompanion.presentation.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carecompanion.data.database.entities.Patient
import com.carecompanion.data.repository.PatientRepository
import com.carecompanion.utils.SharedPreferencesHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecallUiState(
    val patients: List<Patient> = emptyList(),
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val errorMessage: String? = null,
    val totalCount: Int = 0
)

@HiltViewModel
class RecallViewModel @Inject constructor(
    private val patientRepository: PatientRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _errorMessage = MutableStateFlow<String?>(null)

    private fun facilityId() = SharedPreferencesHelper.getActiveFacilityId(context)

    /**
     * Reactive patient list: Room pushes a new emission whenever patients
     * are inserted/updated (e.g. after a background sync completes).
     * The flatMapLatest re-subscribes whenever the search query changes.
     */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val patientsFlow: Flow<List<Patient>> = _searchQuery
        .debounce(300)
        .distinctUntilChanged()
        .flatMapLatest { query ->
            val fid = facilityId()
            when {
                query.isBlank() && fid > 0 -> patientRepository.observeAllActiveByFacility(fid)
                query.isBlank()            -> patientRepository.observeAllActive()
                fid > 0                    -> patientRepository.observeSearchByFacility(query, fid)
                else                       -> patientRepository.observeSearch(query)
            }
        }

    private val activeCountFlow: Flow<Int> = run {
        val fid = facilityId()
        if (fid > 0) patientRepository.observeActiveCountByFacility(fid)
        else patientRepository.observeActiveCount()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<RecallUiState> = combine(
        patientsFlow.catch { e -> _errorMessage.value = e.message; emit(emptyList()) },
        activeCountFlow.catch { emit(0) },
        _searchQuery,
        _errorMessage
    ) { patients, count, query, error ->
        RecallUiState(
            patients    = patients,
            isLoading   = false,
            searchQuery = query,
            totalCount  = count,
            errorMessage = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RecallUiState(isLoading = true)
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun clearError() { _errorMessage.value = null }
}
