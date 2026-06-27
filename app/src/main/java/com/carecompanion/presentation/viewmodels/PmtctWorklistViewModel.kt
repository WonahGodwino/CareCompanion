package com.carecompanion.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carecompanion.data.database.dao.PmtctRecordDao
import com.carecompanion.data.database.entities.PmtctRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class PmtctWorklistItem(
    val record: PmtctRecord,
    val currentGaWeeks: Int?,   // re-derived on-device from LMP for display freshness
)

data class PmtctWorklistUiState(
    val items: List<PmtctWorklistItem> = emptyList(),
    val total: Int = 0,
    val withGap: Int = 0,
    val isLoading: Boolean = true,
)

/**
 * PMTCT worklist: currently-pregnant women (synced from WINCO), each with their PMTCT VL gap
 * (TX_CURR-gated, 32–36 wk window, code 306). GA is re-derived from LMP so "weeks" stays current
 * between syncs; the gap classification is WINCO's.
 */
@HiltViewModel
class PmtctWorklistViewModel @Inject constructor(
    pmtctRecordDao: PmtctRecordDao,
) : ViewModel() {

    val uiState: StateFlow<PmtctWorklistUiState> =
        pmtctRecordDao.observeWorklist().map { records ->
            val now = System.currentTimeMillis()
            val items = records.map { r ->
                val ga = r.lmp?.let { ((now - it.time) / (7L * 86_400_000L)).toInt() } ?: r.gaWeeks
                PmtctWorklistItem(r, ga)
            }
            PmtctWorklistUiState(
                items = items,
                total = items.size,
                withGap = items.count { it.record.gapType != null },
                isLoading = false,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PmtctWorklistUiState(isLoading = true))
}
