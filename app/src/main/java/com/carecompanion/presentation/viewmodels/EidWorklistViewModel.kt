package com.carecompanion.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carecompanion.data.database.dao.InfantRecordDao
import com.carecompanion.data.database.entities.InfantRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class EidWorklistUiState(
    val items: List<InfantRecord> = emptyList(),
    val total: Int = 0,
    val highRisk: Int = 0,
    val withGap: Int = 0,
    val isLoading: Boolean = true,
)

/**
 * EID worklist: HIV-exposed infants with high-risk prediction and intervention gaps (ARV, EID PCR +
 * result, CTX, 18-mo antibody), synced from WINCO. Critical gaps then high-risk first.
 */
@HiltViewModel
class EidWorklistViewModel @Inject constructor(
    infantRecordDao: InfantRecordDao,
) : ViewModel() {

    val uiState: StateFlow<EidWorklistUiState> =
        infantRecordDao.observeWorklist().map { items ->
            EidWorklistUiState(
                items = items,
                total = items.size,
                highRisk = items.count { it.highRisk },
                withGap = items.count { it.gapType != null },
                isLoading = false,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EidWorklistUiState(isLoading = true))
}
