package com.carecompanion.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carecompanion.data.database.dao.ReminderLogDao
import com.carecompanion.data.database.entities.ReminderLog
import com.carecompanion.data.repository.PatientRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A logged reminder plus its reconciled outcome (did the client attend afterwards?). */
data class ReminderLogRow(
    val log: ReminderLog,
    val attended: Boolean?,   // null = appointment still in the future (outcome unknown)
)

data class ReminderLogUiState(
    val rows: List<ReminderLogRow> = emptyList(),
    val totalAttempts: Int = 0,
    val successCount: Int = 0,
    val successRate: Int = 0,      // % of attempts delivered
    val measurable: Int = 0,       // delivered reminders whose appointment date has passed
    val attendanceRate: Int = 0,   // % of those followed by a clinic visit
    val isLoading: Boolean = true,
)

@HiltViewModel
class ReminderLogViewModel @Inject constructor(
    private val reminderLogDao: ReminderLogDao,
    private val patientRepository: PatientRepository,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ReminderLogUiState> = reminderLogDao.observeRecent(300)
        .mapLatest { logs -> reconcile(logs) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReminderLogUiState(isLoading = true))

    private suspend fun reconcile(logs: List<ReminderLog>): ReminderLogUiState {
        val now = System.currentTimeMillis()
        // Did the client attend after the reminder? A pharmacy visit on/after the
        // appointment date counts as attendance.
        val visitsByUuid = patientRepository.getArtPharmacyForPatients(logs.map { it.personUuid }.distinct())
            .groupBy { it.personUuid }

        val rows = logs.map { log ->
            val appt = log.appointmentDate
            val attended = when {
                appt == null -> null
                appt.time > now -> null // outcome not yet knowable
                else -> visitsByUuid[log.personUuid]?.any { !it.visitDate.before(appt) } ?: false
            }
            ReminderLogRow(log, attended)
        }

        val attempts = rows.size
        val successes = rows.count { it.log.success }
        val measurable = rows.count { it.log.success && it.attended != null }
        val attended = rows.count { it.log.success && it.attended == true }

        return ReminderLogUiState(
            rows = rows,
            totalAttempts = attempts,
            successCount = successes,
            successRate = if (attempts > 0) (successes * 100) / attempts else 0,
            measurable = measurable,
            attendanceRate = if (measurable > 0) (attended * 100) / measurable else 0,
            isLoading = false,
        )
    }

    fun clearLog() {
        viewModelScope.launch { reminderLogDao.deleteAll() }
    }
}
