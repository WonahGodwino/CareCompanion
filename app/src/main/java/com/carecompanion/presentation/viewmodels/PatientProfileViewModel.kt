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
import com.carecompanion.utils.RegimenLookup
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

enum class VisitEventType(val label: String) {
    ART_REFILL("ART Refill"),
    VL_SAMPLE("VL Sample"),
    VL_RESULT("VL Result"),
    APPOINTMENT("Appointment")
}

data class VisitTimelineEntry(
    val date: Date,
    val type: VisitEventType,
    val detail: String,
    val regimenName: String? = null
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
    val errorMessage: String? = null,

    // ── Advanced HIV Disease flag (VL < 200 copies/mL) ────────────────────────
    val isAhd: Boolean = false,

    // ── Current regimen from RegimenLookup ────────────────────────────────────
    val currentRegimenShortName: String? = null,
    val currentRegimenFullName: String? = null,
    val currentRegimenLine: String? = null,

    // ── Patient visit timeline (last 12 events, descending) ───────────────────
    val visitTimeline: List<VisitTimelineEntry> = emptyList(),

    // ── Biometric recapture staleness (PEPFAR / NPHCDA) ───────────────────────
    val recaptureRecommended: Boolean = false,
    val recaptureOverdue: Boolean = false,
    val daysSinceLastBiometric: Long? = null
)



@HiltViewModel
class PatientProfileViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val patientRepository: PatientRepository,
    private val syncRepository: com.carecompanion.data.repository.SyncRepository
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

                // ── Recapture staleness calculation ────────────────────────────────────
                // Use the most recent date available across all non-archived biometrics.
                val latestBioDate: Date? = biometrics
                    .mapNotNull { it.lastModifiedDate ?: it.enrollmentDate ?: it.lastSyncDate }
                    .maxOrNull()
                val daysSinceLastBio = latestBioDate?.let {
                    TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - it.time)
                }
                // Recommend recapture after 15 days (programme cadence standard).
                val recaptureRecommended = biometrics.isNotEmpty() && (daysSinceLastBio ?: 0L) >= 15
                val ndrRaw = patient.ndrMatchedStatus?.trim()?.uppercase()
                    ?.replace("_","")?.replace("-","")?.replace(" ","")
                val notNdrMatched = ndrRaw == null ||
                    (ndrRaw != "MATCHED" && ndrRaw != "MATCH" && ndrRaw != "YES" &&
                     ndrRaw != "Y" && ndrRaw != "NDRMATCHED" && ndrRaw != "TRUE" && ndrRaw != "1")
                val recaptureOverdue = biometrics.isNotEmpty() &&
                    (daysSinceLastBio ?: 0L) >= 730 &&
                    notNdrMatched

                // ── AHD flag: VL < 200 copies/mL (WHO threshold) ──────────────────────
                val latestVlResult = currentViralLoad.eligibility?.let { null }
                    ?: (vlHistory.firstOrNull { !it.isPending }?.resultNumeric
                        ?: patient.lastViralLoadResult?.toDouble())
                val isAhd = latestVlResult != null && latestVlResult < 200.0 && latestVlResult > 0.0

                // ── Current regimen from latest pharmacy record ────────────────────────
                val latestRx = artPharmacy.maxByOrNull { it.visitDate }
                val regimen = latestRx?.regimenId?.let { RegimenLookup.get(it) }

                // ── Visit timeline: merge pharmacy visits + VL events (last 12) ─────
                val sortedRx = artPharmacy.sortedByDescending { it.visitDate }
                val timeline = buildList<VisitTimelineEntry> {
                    sortedRx.forEach { rx ->
                        val rxRegimen = rx.regimenId?.let { RegimenLookup.get(it) }
                        add(VisitTimelineEntry(
                            date       = rx.visitDate,
                            type       = VisitEventType.ART_REFILL,
                            detail     = buildString {
                                append("Refill: ${rx.refillPeriod ?: "—"} days")
                                rx.dsdModel?.let { append(" · $it") }
                            },
                            regimenName = rxRegimen?.shortName
                        ))
                        rx.nextAppointment?.let { apptDate ->
                            add(VisitTimelineEntry(
                                date   = apptDate,
                                type   = VisitEventType.APPOINTMENT,
                                detail = "Next appointment"
                            ))
                        }
                    }
                    vlHistory.forEach { vl ->
                        vl.dateSampleCollected?.let { d ->
                            add(VisitTimelineEntry(
                                date   = d,
                                type   = VisitEventType.VL_SAMPLE,
                                detail = if (vl.isPending) "Sample collected — result pending"
                                         else "Sample collected"
                            ))
                        }
                        if (!vl.isPending) {
                            vl.dateResultReported?.let { d ->
                                add(VisitTimelineEntry(
                                    date   = d,
                                    type   = VisitEventType.VL_RESULT,
                                    detail = "${vl.resultValueLabel} copies/mL — ${vl.resultStatus}"
                                ))
                            }
                        }
                    }
                }.sortedByDescending { it.date }.take(15)

                _uiState.value = _uiState.value.copy(
                    patient = patient,
                    biometrics = biometrics,
                    artPharmacy = sortedRx,
                    viralLoadHistory = vlHistory,
                    currentViralLoad = currentViralLoad,
                    enrolledFingers = enrolledFingers,
                    hasBiometric = biometrics.isNotEmpty(),
                    biometricCount = biometrics.size,
                    recaptureBreakdown = recaptureBreakdown,
                    recaptureRecommended = recaptureRecommended,
                    recaptureOverdue = recaptureOverdue,
                    daysSinceLastBiometric = daysSinceLastBio,
                    isAhd = isAhd,
                    currentRegimenShortName = regimen?.shortName,
                    currentRegimenFullName  = regimen?.fullName,
                    currentRegimenLine      = regimen?.line,
                    visitTimeline = timeline,
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
            val imageQuality = try {
                com.carecompanion.biometric.BiometricManager::class.java.getDeclaredField("lastCaptureQuality").let { field ->
                    field.isAccessible = true
                    field.getInt(null)
                }
            } catch (e: Exception) {
                60 // fallback
            }
            val enrollSuccess = syncRepository.enrollBiometric(
                patientUuid = patient.uuid,
                fingerType = fingerType.displayName,
                template = template,
                quality = imageQuality,
                userId = null, // Optionally pass userId if available
                facilityId = null // Optionally pass facilityId if available
            )
            if (enrollSuccess) {
                loadPatient(patient.uuid) // Refresh UI
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = null
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Biometric enrollment failed. Please ensure quality and try again."
                )
            }
            _captureState.value = false
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
