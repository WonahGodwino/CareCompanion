package com.carecompanion.presentation.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carecompanion.data.database.entities.NoBiometricEntry
import com.carecompanion.data.repository.PatientRepository
import com.carecompanion.utils.SharedPreferencesHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class UnregisteredBiometricUiState(
    val patients: List<NoBiometricEntry> = emptyList(),
    val totalCount: Int = 0,
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class UnregisteredBiometricViewModel @Inject constructor(
    private val patientRepository: PatientRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _searchQuery  = MutableStateFlow("")
    private val _errorMessage = MutableStateFlow<String?>(null)

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val dataFlow: Flow<List<NoBiometricEntry>> = _searchQuery
        .debounce(300)
        .distinctUntilChanged()
        .flatMapLatest { query ->
            val fid = SharedPreferencesHelper.getActiveFacilityId(context)
            when {
                query.isBlank() && fid > 0 -> patientRepository.observeNoBiometricPatientsByFacility(fid)
                query.isBlank()            -> patientRepository.observeNoBiometricPatients()
                else                       -> patientRepository.observeNoBiometricSearch(query)
            }
        }

    val uiState: StateFlow<UnregisteredBiometricUiState> = combine(
        dataFlow.catch { e -> _errorMessage.value = e.message; emit(emptyList()) },
        _searchQuery,
        _errorMessage
    ) { patients, query, error ->
        UnregisteredBiometricUiState(
            patients     = patients,
            totalCount   = patients.size,
            searchQuery  = query,
            isLoading    = false,
            errorMessage = error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UnregisteredBiometricUiState(isLoading = true))

    fun onSearchQueryChanged(q: String) { _searchQuery.value = q }
    fun clearSearch() { _searchQuery.value = "" }
    fun clearError() { _errorMessage.value = null }
}
