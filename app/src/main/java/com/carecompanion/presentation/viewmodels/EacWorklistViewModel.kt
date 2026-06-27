package com.carecompanion.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carecompanion.data.database.dao.EacEpisodeDao
import com.carecompanion.data.database.entities.Patient
import com.carecompanion.data.repository.PatientRepository
import com.carecompanion.utils.EacGap
import com.carecompanion.utils.EacGapEngine
import com.carecompanion.utils.EacWarning
import com.carecompanion.utils.VlGapSeverity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class EacWorklistItem(
    val patient: Patient,
    val gap: EacGap?,             // primary active gap (TX_CURR + unsuppressed)
    val warnings: List<EacWarning>,
    val stage: String?,
)

data class EacWorklistUiState(
    val items: List<EacWorklistItem> = emptyList(),
    val totalCount: Int = 0,
    val criticalCount: Int = 0,
    val isLoading: Boolean = true,
)

/**
 * EAC worklist: active (TX_CURR) clients whose current VL is unsuppressed, each with their EAC cascade
 * gap (from [EacGapEngine]) and any prior-episode warnings. Critical gaps (no EAC / failure-not-actioned)
 * sort to the top.
 */
@HiltViewModel
class EacWorklistViewModel @Inject constructor(
    private val patientRepository: PatientRepository,
    private val eacEpisodeDao: EacEpisodeDao,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<EacWorklistUiState> =
        patientRepository.observeUnsuppressedTxCurr(System.currentTimeMillis())
            .mapLatest { patients ->
                val items = patients.map { p ->
                    val episodes = eacEpisodeDao.getByPersonUuid(p.uuid)
                    val a = EacGapEngine.assess(episodes, p.lastViralLoadResult, isTxCurr = true)
                    EacWorklistItem(p, a.gaps.firstOrNull(), a.warnings, a.currentEpisode?.stage)
                }.sortedByDescending { it.gap?.severity == VlGapSeverity.CRITICAL }
                EacWorklistUiState(
                    items = items,
                    totalCount = items.size,
                    criticalCount = items.count { it.gap?.severity == VlGapSeverity.CRITICAL },
                    isLoading = false,
                )
            }
            .catch { emit(EacWorklistUiState(isLoading = false)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EacWorklistUiState(isLoading = true))
}
