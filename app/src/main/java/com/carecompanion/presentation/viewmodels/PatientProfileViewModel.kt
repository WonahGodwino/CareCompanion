package com.carecompanion.presentation.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carecompanion.data.database.entities.ArtPharmacy
import com.carecompanion.data.database.entities.Biometric
import com.carecompanion.data.database.entities.Patient
import com.carecompanion.data.network.models.WincoViralLoadHistoryItem
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

    private fun WincoViralLoadHistoryItem.toUiItem(): ViralLoadHistoryUiItem? {
        val sampleDate = DateUtils.parseDate(dateSampleCollected)
        val resultDate = DateUtils.parseDate(dateResultReported) ?: DateUtils.parseDate(dateAssayed)
        val resultValue = resultNumeric
        val pending = resultPending || resultRaw.isNullOrBlank()
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
            sampleSourceId = sampleSourceId ?: sourceId,
            testId = testId ?: (sampleSourceId ?: sourceId)?.toString().orEmpty(),
            sampleNumber = sampleNumber,
            sampleTypeId = sampleTypeId,
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
        if (patient == null || history.isEmpty()) return ViralLoadCurrentUiState()

        val latest = history.first()
        val pending = latest.isPending
        val latestViralLoadDate = latest.dateResultReported ?: latest.dateSampleCollected
        val eligibility = if (!pending) {
            ViralLoadEligibilityEngine.evaluate(
                dateOfBirth = patient.dateOfBirth,
                artRegistrationDate = patient.dateOfRegistration,
                latestViralLoadDate = latestViralLoadDate,
            )
        } else null

        val statusLabel = when {
            pending && latest.isOverdueNoResult -> "Overdue sample with no Result"
            pending -> "Viral Load Sample Collection"
            else -> "Current Viral Load"
        }
        val resultLabel = when {
            pending -> "Result Pending"
            latest.resultNumeric != null && latest.resultNumeric <= 20 -> "Undetected"
            latest.resultNumeric != null && latest.resultNumeric < 1000 -> "Suppressed"
            latest.resultNumeric != null -> "Unsuppressed"
            else -> latest.resultRaw ?: "N/A"
        }
        val resultValueLabel = when {
            pending -> "Pending"
            latest.resultNumeric != null -> latest.resultNumeric.toInt().toString()
            else -> latest.resultRaw ?: "N/A"
        }
        val dateLabel = when {
            pending -> latest.dateSampleCollected?.let { "Date Sample Collected: ${DateUtils.formatDate(it)}" } ?: "Date Sample Collected: N/A"
            latest.dateResultReported != null -> "Viral Load Date: ${DateUtils.formatDate(latest.dateResultReported)}"
            latest.dateSampleCollected != null -> "Viral Load Date: ${DateUtils.formatDate(latest.dateSampleCollected)}"
            else -> "Viral Load Date: N/A"
        }
        val nextDueLabel = when {
            pending -> if (latest.isOverdueNoResult) "Next Due Date: Review immediately" else "Next Due Date: Pending result"
            eligibility != null -> "Next Due Date: ${DateUtils.formatDate(eligibility.dueDate)}"
            else -> "Next Due Date: N/A"
        }

        return ViralLoadCurrentUiState(
            statusLabel = statusLabel,
            resultLabel = resultLabel,
            resultValueLabel = resultValueLabel,
            dateLabel = dateLabel,
            nextDueLabel = nextDueLabel,
            flagLabel = latest.resultFlag,
            dueType = eligibility?.dueType,
            eligibility = eligibility,
        )
    }

    private fun isOlderThanOneMonth(sampleDate: Date): Boolean {
        val elapsedDays = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - sampleDate.time)
        return elapsedDays > 30
    }
}