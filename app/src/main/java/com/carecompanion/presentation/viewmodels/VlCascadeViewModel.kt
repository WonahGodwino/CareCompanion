package com.carecompanion.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carecompanion.data.repository.PatientRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class VlCascadeUiState(
    val txCurr: Int = 0,
    val vlTested: Int = 0,
    val vlResultReceived: Int = 0,
    val vlSuppressed: Int = 0,
    val vlUnsuppressed: Int = 0,
    val isLoading: Boolean = true
) {
    val testedPct: Float get() = if (txCurr > 0) vlTested * 100f / txCurr else 0f
    val resultReceivedPct: Float get() = if (txCurr > 0) vlResultReceived * 100f / txCurr else 0f
    val suppressedPct: Float get() = if (txCurr > 0) vlSuppressed * 100f / txCurr else 0f
    val suppressionAmongTested: Float get() = if (vlResultReceived > 0) vlSuppressed * 100f / vlResultReceived else 0f
}

@HiltViewModel
class VlCascadeViewModel @Inject constructor(
    private val patientRepository: PatientRepository
) : ViewModel() {

    val uiState: StateFlow<VlCascadeUiState> = combine(
        patientRepository.observeTxCurrCount(),
        patientRepository.observeVlTestedCount(),
        patientRepository.observeVlResultReceivedCount(),
        patientRepository.observeVlSuppressedCount(),
        patientRepository.observeVlUnsuppressedCount()
    ) { txCurr, tested, received, suppressed, unsuppressed ->
        VlCascadeUiState(
            txCurr           = txCurr,
            vlTested         = tested,
            vlResultReceived = received,
            vlSuppressed     = suppressed,
            vlUnsuppressed   = unsuppressed,
            isLoading        = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VlCascadeUiState(isLoading = true))
}
