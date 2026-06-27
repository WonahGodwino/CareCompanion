package com.carecompanion.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carecompanion.data.messaging.ReminderGateway
import com.carecompanion.data.messaging.ReminderResult
import com.carecompanion.data.messaging.ReminderTemplates
import com.carecompanion.data.reminder.ReminderAuditLogger
import com.carecompanion.data.risk.AssessedClient
import com.carecompanion.data.risk.RiskAssessment
import com.carecompanion.data.risk.RiskAssessmentService
import com.carecompanion.utils.PatientRiskEngine.PatientRiskScore
import com.carecompanion.utils.PatientRiskEngine.RiskBand
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AiInsightsUiState(
    val forecast: List<PatientRiskScore> = emptyList(),     // upcoming appointment, predicted to miss
    val approaching: List<PatientRiskScore> = emptyList(),  // 1–27 days late, approaching IIT
    val established: List<PatientRiskScore> = emptyList(),   // already IIT (>28 days)
    val criticalCount: Int = 0,
    val highCount: Int = 0,
    val moderateCount: Int = 0,
    val totalFlagged: Int = 0,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

@HiltViewModel
class AiInsightsViewModel @Inject constructor(
    private val riskService: RiskAssessmentService,
    private val reminderGateway: ReminderGateway,
    private val auditLogger: ReminderAuditLogger,
) : ViewModel() {

    /** Latest assessed clients, keyed by uuid, for the manual "Send reminder" action. */
    private var assessedByUuid: Map<String, AssessedClient> = emptyMap()

    private val _reminderEvent = MutableStateFlow<String?>(null)
    val reminderEvent: StateFlow<String?> = _reminderEvent.asStateFlow()
    fun clearReminderEvent() { _reminderEvent.value = null }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<AiInsightsUiState> = riskService.observe()
        .mapLatest { assessment ->
            assessedByUuid = assessment.flagged.associateBy { it.score.uuid }
            toUiState(assessment)
        }
        .catch { e -> emit(AiInsightsUiState(isLoading = false, errorMessage = e.message)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiInsightsUiState(isLoading = true))

    private fun toUiState(a: RiskAssessment): AiInsightsUiState {
        val flagged = a.flagged
        return AiInsightsUiState(
            forecast = a.forecast.map { it.score },
            approaching = a.approaching.map { it.score },
            established = a.established.map { it.score },
            criticalCount = flagged.count { it.score.band == RiskBand.CRITICAL },
            highCount = flagged.count { it.score.band == RiskBand.HIGH },
            moderateCount = flagged.count { it.score.band == RiskBand.MODERATE },
            totalFlagged = flagged.size,
            isLoading = false,
            errorMessage = null,
        )
    }

    /** Manually dispatch a reminder for one client, using the saved template for its type. */
    fun sendReminder(uuid: String) {
        val client = assessedByUuid[uuid]
        if (client == null) { _reminderEvent.value = "No contact details on file."; return }
        val message = ReminderTemplates.render(client.type, client.context)
        viewModelScope.launch {
            _reminderEvent.value = "Sending reminder…"
            val r = reminderGateway.sendAppointmentReminder(client.target, message)
            auditLogger.record(client, r, auto = false)
            _reminderEvent.value = when (r) {
                is ReminderResult.Sent -> "Reminder sent via ${r.channels.joinToString(" & ")}."
                is ReminderResult.Failed -> "Reminder failed: ${r.reason}"
                ReminderResult.NotConfigured -> "Configure the SMS/Email gateway in Settings first."
                ReminderResult.NoContact -> "No phone/email on file for this client."
            }
        }
    }
}
