package com.carecompanion.presentation.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carecompanion.data.database.entities.ArtPharmacy
import com.carecompanion.data.database.entities.Biometric
import com.carecompanion.data.database.entities.Patient
import com.carecompanion.data.database.entities.ViralLoadHistory
import com.carecompanion.data.repository.PatientRepository
import com.carecompanion.biometric.models.FingerType
import com.carecompanion.utils.DateUtils
import com.carecompanion.utils.ViralLoadDueType
import com.carecompanion.utils.ViralLoadEligibilityEngine
import com.carecompanion.utils.ViralLoadEligibilityResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class ViralLoadHistoryUiItem(
    val sampleSourceId: Long?,
    val testId: String,
    val sampleNumber: String?,
    val sampleTypeId: Int?,
    val dateSampleCollected: Date?,
    val dateResultReported: Date?,
    val resultRaw: String?,
    val resultNumeric: Double?,
    val resultValueLabel: String,
    val resultStatus: String,
    val resultFlag: String,
    val isPending: Boolean,
    val isOverdueNoResult: Boolean,
)

data class ViralLoadCurrentUiState(
    val title: String = "Current Viral Load",
    val statusLabel: String = "No viral load history",
    val resultLabel: String = "N/A",
    val resultValueLabel: String = "N/A",
    val dateLabel: String = "N/A",
    val nextDueLabel: String = "N/A",
    val flagLabel: String? = null,
    val dueType: ViralLoadDueType? = null,
    val eligibility: ViralLoadEligibilityResult? = null,
)

data class PatientProfileUiState(
    val patient: Patient? = null,
    val biometrics: List<Biometric> = emptyList(),
    val artPharmacy: List<ArtPharmacy> = emptyList(),
    val viralLoadHistory: List<ViralLoadHistoryUiItem> = emptyList(),
    val currentViralLoad: ViralLoadCurrentUiState = ViralLoadCurrentUiState(),
    val enrolledFingers: Set<FingerType> = emptySet(),
    val hasBiometric: Boolean = false,
    val biometricCount: Int = 0,
    val recaptureBreakdown: Map<Int, Int> = emptyMap(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class PatientProfileViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val patientRepository: PatientRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PatientProfileUiState())
    val uiState: StateFlow<PatientProfileUiState> = _uiState.asStateFlow()

    fun loadPatient(patientUuid: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val patient = patientRepository.getPatientByUuid(patientUuid)
                val biometrics = patientRepository.getBiometricsForPatient(patientUuid)
                val artPharmacy = patientRepository.getArtPharmacyForPatient(patientUuid)
                val vlHistory = patientRepository.getViralLoadHistory(patientUuid)
                    .mapNotNull { it.toUiItem() }
                    .sortedWith(
                        compareByDescending<ViralLoadHistoryUiItem> { it.dateResultReported?.time ?: Long.MIN_VALUE }
                            .thenByDescending { it.dateSampleCollected?.time ?: Long.MIN_VALUE }
                            .thenByDescending { it.sampleSourceId ?: Long.MIN_VALUE }
                    )
                val currentViralLoad = buildCurrentViralLoad(patient, vlHistory)
                val enrolledFingers = biometrics.mapNotNull { bio ->
                    FingerType.values().firstOrNull { it.displayName.equals(bio.templateType, ignoreCase = true) }
                }.toSet()
                val recaptureBreakdown = biometrics
                    .groupingBy { it.recapture }
                    .eachCount()
                    .toSortedMap()
                _uiState.update {
                    it.copy(
                        patient = patient, biometrics = biometrics,
                        artPharmacy = artPharmacy.sortedByDescending { ap -> ap.visitDate },
                        viralLoadHistory = vlHistory,
                        currentViralLoad = currentViralLoad,
                        enrolledFingers = enrolledFingers,
                        hasBiometric = biometrics.isNotEmpty(),
                        biometricCount = biometrics.size,
                        recaptureBreakdown = recaptureBreakdown,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    private fun ViralLoadHistory.toUiItem(): ViralLoadHistoryUiItem? {
        val sampleDate = this.sampleDate
        val resultDate = this.resultDate ?: this.assayedDate
        val resultValue = this.resultNumeric?.toDouble()
        val pending = this.resultRaw.isNullOrBlank()
        val valueLabel = when {
            pending -> "Pending"
            resultValue != null -> resultValue.toInt().toString()
            else -> resultRaw ?: "N/A"
        }
        val status = when {
            pending -> "Result Pending"
            resultValue != null && resultValue <= 20 -> "Undetected"
            resultValue != null && resultValue < 1000 -> "Suppressed"
            resultValue != null -> "Unsuppressed"
            else -> "Unknown"
        }
        val flag = when {
            pending && sampleDate != null && isOlderThanOneMonth(sampleDate) -> "Overdue sample with no Result"
            pending -> "Sample collected; awaiting result"
            else -> status
        }
        return ViralLoadHistoryUiItem(
            sampleSourceId = this.sourceId,
            testId = this.testId.toString(),
            sampleNumber = this.sampleNumber,
            sampleTypeId = this.sampleTypeId,
            dateSampleCollected = sampleDate,
            dateResultReported = resultDate,
            resultRaw = resultRaw,
            resultNumeric = resultValue,
            resultValueLabel = valueLabel,
            resultStatus = status,
            resultFlag = flag,
            isPending = pending,
            isOverdueNoResult = pending && sampleDate != null && isOlderThanOneMonth(sampleDate),
        )
    }

    private fun buildCurrentViralLoad(
        patient: Patient?,
        history: List<ViralLoadHistoryUiItem>
    ): ViralLoadCurrentUiState {
        if (patient == null) return ViralLoadCurrentUiState()

        val latest = history.firstOrNull()
        val pending = latest?.isPending ?: false

        // Use history if available, otherwise fallback to summary fields in Patient entity
        val latestViralLoadDate = latest?.let { it.dateResultReported ?: it.dateSampleCollected }
            ?: patient.lastViralLoadDate

        val resultNumeric = latest?.resultNumeric ?: patient.lastViralLoadResult?.toDouble()
        val resultRaw = latest?.resultRaw ?: patient.lastViralLoadResultRaw

        // If no history and no summary data, return empty state
        if (latest == null && latestViralLoadDate == null && resultNumeric == null && resultRaw == null) {
            return ViralLoadCurrentUiState()
        }

        val eligibility = if (!pending) {
            ViralLoadEligibilityEngine.evaluate(
                dateOfBirth = patient.dateOfBirth,
                artRegistrationDate = patient.dateOfRegistration,
                latestViralLoadDate = latestViralLoadDate,
            )
        } else null

        val statusLabel = when {
            pending && (latest?.isOverdueNoResult == true) -> "Overdue sample with no Result"
            pending -> "Viral Load Sample Collection"
            else -> "Current Viral Load"
        }

        val resultLabel = when {
            pending -> "Result Pending"
            resultNumeric != null && resultNumeric <= 20 -> "Undetected"
            resultNumeric != null && resultNumeric < 1000 -> "Suppressed"
            resultNumeric != null -> "Unsuppressed"
            else -> resultRaw ?: "N/A"
        }

        val resultValueLabel = when {
            pending -> "Pending"
            resultNumeric != null -> resultNumeric.toInt().toString()
            else -> resultRaw ?: "N/A"
        }

        val dateLabel = when {
            pending -> latest?.dateSampleCollected?.let { "Date Sample Collected: ${DateUtils.formatDate(it)}" } ?: "Date Sample Collected: N/A"
            latestViralLoadDate != null -> "Viral Load Date: ${DateUtils.formatDate(latestViralLoadDate)}"
            else -> "Viral Load Date: N/A"
        }

        val nextDueLabel = when {
            pending -> if (latest?.isOverdueNoResult == true) "Next Due Date: Review immediately" else "Next Due Date: Pending result"
            eligibility != null -> "Next Due Date: ${DateUtils.formatDate(eligibility.dueDate)}"
            else -> "Next Due Date: N/A"
        }

        return ViralLoadCurrentUiState(
            statusLabel = statusLabel,
            resultLabel = resultLabel,
            resultValueLabel = resultValueLabel,
            dateLabel = dateLabel,
            nextDueLabel = nextDueLabel,
            flagLabel = latest?.resultFlag,
            dueType = eligibility?.dueType,
            eligibility = eligibility,
        )
    }

    private fun isOlderThanOneMonth(sampleDate: Date): Boolean {
        val elapsedDays = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - sampleDate.time)
        return elapsedDays > 30
    }
}