package com.carecompanion.presentation.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carecompanion.biometric.models.FingerLabelNormalizer
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
    val sampleDateLabel: String = "Sample Date: N/A",
    val resultDateLabel: String = "Result Date: N/A",
    val nextDueLabel: String = "N/A",
    val flagLabel: String? = null,
    val dueType: ViralLoadDueType? = null,
    val eligibility: ViralLoadEligibilityResult? = null,
    val isPendingResult: Boolean = false,
    val isOverduePendingResult: Boolean = false,
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

    // UI state for the patient profile
    private val _uiState = MutableStateFlow(PatientProfileUiState())
    val uiState: StateFlow<PatientProfileUiState> = _uiState.asStateFlow()

    // Loads patient data and updates UI state
    fun loadPatient(uuid: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val patient = patientRepository.getPatientByUuid(uuid)
                if (patient == null) {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "Patient not found")
                    return@launch
                }
                val biometrics = patientRepository.getBiometricsForPatient(uuid)
                val artPharmacy = patientRepository.getArtPharmacyForPatient(uuid)
                val vlHistoryRaw = patientRepository.getViralLoadHistory(uuid)
                val vlHistory = vlHistoryRaw.mapNotNull { it.toUiItem() }
                val currentViralLoad = buildCurrentViralLoad(patient, vlHistory)
                val enrolledFingers = biometrics.mapNotNull { it.biometricType?.let { type -> FingerType.fromDisplayName(type) } }.toSet()
                val recaptureBreakdown = biometrics.groupingBy { it.recapture }.eachCount().toSortedMap()
                _uiState.value = _uiState.value.copy(
                    patient = patient,
                    biometrics = biometrics,
                    artPharmacy = artPharmacy.sortedByDescending { it.visitDate },
                    viralLoadHistory = vlHistory,
                    currentViralLoad = currentViralLoad,
                    enrolledFingers = enrolledFingers,
                    hasBiometric = biometrics.isNotEmpty(),
                    biometricCount = biometrics.size,
                    recaptureBreakdown = recaptureBreakdown,
                    isLoading = false,
                    errorMessage = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = e.message)
            }
        }
    }

    // Biometric capture state
    private val _captureState = MutableStateFlow(false)
    val captureState: StateFlow<Boolean> = _captureState.asStateFlow()

    fun startBiometricCapture() {
        _captureState.value = true
    }

    fun onBiometricCaptured(fingerType: FingerType, template: ByteArray, templateType: String?) {
        viewModelScope.launch {
            val patient = _uiState.value.patient ?: return@launch
            val biometrics = patientRepository.getBiometricsForPatient(patient.uuid)
            val recaptures = biometrics.filter { it.templateType == templateType && it.biometricType == fingerType.displayName }
            val nextRecapture = if (recaptures.isEmpty()) 0 else (recaptures.maxOf { it.recapture } + 1)

            // Enforce minimum image quality
            val minQuality = try {
                val clazz = Class.forName("com.carecompanion.biometric.secugen.SecuGenQuality")
                clazz.getField("MIN_ENROLL").getInt(null)
            } catch (e: Exception) {
                60 // fallback
            }
            val imageQuality = try {
                // If BiometricManager is available, get last quality
                com.carecompanion.biometric.BiometricManager::class.java.getDeclaredField("lastCaptureQuality").let { field ->
                    field.isAccessible = true
                    field.getInt(null)
                }
            } catch (e: Exception) {
                minQuality // fallback
            }
            if (imageQuality < minQuality) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Biometric image quality too low ($imageQuality < $minQuality). Please recapture."
                )
                _captureState.value = false
                return@launch
            }

            // Prevent duplicate enrollment for same finger and template type
            val duplicate = biometrics.any {
                it.biometricType == fingerType.displayName && it.templateType == templateType && it.template.contentEquals(template)
            }
            if (duplicate) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Duplicate biometric detected for this finger and template type."
                )
                _captureState.value = false
                return@launch
            }

            // Audit log (could be replaced with a real logger)
            android.util.Log.i("BiometricCapture", "Enrolling biometric for patient=${patient.uuid}, finger=${fingerType.displayName}, quality=$imageQuality, recapture=$nextRecapture")

            try {
                val newBiometric = Biometric(
                    id = java.util.UUID.randomUUID().toString(),
                    personUuid = patient.uuid,
                    template = template,
                    biometricType = fingerType.displayName,
                    templateType = templateType,
                    recapture = nextRecapture,
                    enrollmentDate = java.util.Date(),
                    deviceName = null,
                    imageQuality = imageQuality,
                    iso = false,
                    versionIso20 = false,
                    lastSyncDate = java.util.Date(),
                    archived = 0,
                    count = null,
                    createdBy = null,
                    createdDate = java.util.Date(),
                    extra = null,
                    facilityId = null,
                    hashed = null,
                    lastModifiedBy = null,
                    lastModifiedDate = null,
                    matchBiometricId = null,
                    matchPersonUuid = null,
                    matchType = null,
                    rawPayload = null,
                    reason = null,
                    recaptureMessage = null,
                    replaceDate = null,
                    sourceId = null
                )
                patientRepository.saveBiometric(newBiometric)
                loadPatient(patient.uuid) // Refresh UI
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = null
                )
            } catch (e: Exception) {
                android.util.Log.e("BiometricCapture", "Failed to save biometric: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to save biometric: ${e.message}"
                )
            } finally {
                _captureState.value = false
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

        val latest = history.maxByOrNull { it.dateSampleCollected?.time ?: Long.MIN_VALUE }
        val pending = latest?.isPending ?: false

        // PEPFAR/NACA standard: Use sample collection date for eligibility (not result date)
        val sampleCollectionDate = latest?.dateSampleCollected ?: patient.lastViralLoadDate
        val resultReportedDate = latest?.dateResultReported

        val resultNumeric = latest?.resultNumeric ?: patient.lastViralLoadResult?.toDouble()
        val resultRaw = latest?.resultRaw ?: patient.lastViralLoadResultRaw

        // If no history and no summary data, return empty state
        if (latest == null && sampleCollectionDate == null && resultNumeric == null && resultRaw == null) {
            return ViralLoadCurrentUiState()
        }

        val eligibility = if (!pending) {
            ViralLoadEligibilityEngine.evaluate(
                dateOfBirth = patient.dateOfBirth,
                artRegistrationDate = patient.dateOfRegistration,
                dateSampleCollected = sampleCollectionDate,
                dateResultReported = resultReportedDate,
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

        val sampleDateLabel = sampleCollectionDate?.let { "Sample Date: ${DateUtils.formatDate(it)}" } ?: "Sample Date: N/A"
        val resultDateLabel = resultReportedDate?.let { "Result Date: ${DateUtils.formatDate(it)}" } ?: "Result Date: N/A"

        val nextDueLabel = when {
            pending -> if (latest?.isOverdueNoResult == true) "Next Due Date: Review immediately" else "Next Due Date: Pending result"
            eligibility != null -> "Next Due Date: ${DateUtils.formatDate(eligibility.dueDate)}"
            else -> "Next Due Date: N/A"
        }

        return ViralLoadCurrentUiState(
            statusLabel = statusLabel,
            resultLabel = resultLabel,
            resultValueLabel = resultValueLabel,
            sampleDateLabel = sampleDateLabel,
            resultDateLabel = resultDateLabel,
            nextDueLabel = nextDueLabel,
            flagLabel = latest?.resultFlag,
            dueType = eligibility?.dueType,
            eligibility = eligibility,
            isPendingResult = pending,
            isOverduePendingResult = latest?.isOverdueNoResult == true,
        )
    }

    private fun isOlderThanOneMonth(sampleDate: Date): Boolean {
        val elapsedDays = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - sampleDate.time)
        return elapsedDays > 30
    }
}
