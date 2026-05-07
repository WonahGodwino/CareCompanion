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
import com.carecompanion.utils.ServiceEligibilityEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ServiceEligibilityUI(
    val service: String,
    val eligible: Boolean,
    val reason: String?,
    val urgency: String?,         // "critical", "high", "due", "routine"
    val nextAction: String?,
    val careCategory: String? = null,
    val eligibilityGroup: String? = null,
    val details: Map<String, Any> = emptyMap()
)

data class PatientProfileUiState(
    val patient: Patient? = null,
    val biometrics: List<Biometric> = emptyList(),
    val artPharmacy: List<ArtPharmacy> = emptyList(),
    val viralLoadHistory: List<ViralLoadHistory> = emptyList(),
    val enrolledFingers: Set<FingerType> = emptySet(),
    val hasBiometric: Boolean = false,
    val biometricCount: Int = 0,
    val recaptureBreakdown: Map<Int, Int> = emptyMap(),
    val serviceEligibility: Map<String, ServiceEligibilityUI> = emptyMap(),
    val eligibleServices: List<String> = emptyList(),
    val eligibleCount: Int = 0,
    val artRefillEligibilityGroupCounts: Map<String, Int> = emptyMap(),
    val viralLoadEligibilityGroupCounts: Map<String, Int> = emptyMap(),
    val isServiceEligibilityLoading: Boolean = false,
    val serviceEligibilityError: String? = null,
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
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    serviceEligibility = emptyMap(),
                    eligibleServices = emptyList(),
                    eligibleCount = 0,
                    isServiceEligibilityLoading = true,
                    serviceEligibilityError = null
                )
            }
            try {
                val (patientValue, biometricsValue, artPharmacyValue, viralLoadHistoryValue) = coroutineScope {
                    val patientDeferred = async { patientRepository.getPatientByUuid(patientUuid) }
                    val biometricsDeferred = async { patientRepository.getBiometricsForPatient(patientUuid) }
                    val artPharmacyDeferred = async { patientRepository.getArtPharmacyForPatient(patientUuid) }
                    val vlHistoryDeferred = async { patientRepository.getViralLoadHistoryForPatient(patientUuid) }
                    Quad(
                        patientDeferred.await(),
                        biometricsDeferred.await(),
                        artPharmacyDeferred.await(),
                        vlHistoryDeferred.await(),
                    )
                }

                val enrolledFingers = biometricsValue.mapNotNull { bio ->
                    FingerType.values().firstOrNull { it.displayName.equals(bio.templateType, ignoreCase = true) }
                }.toSet()
                val recaptureBreakdown = biometricsValue
                    .groupingBy { it.recapture }
                    .eachCount()
                    .toSortedMap()

                _uiState.update {
                    it.copy(
                        patient = patientValue, biometrics = biometricsValue,
                        artPharmacy = artPharmacyValue.sortedByDescending { ap -> ap.visitDate },
                        viralLoadHistory = viralLoadHistoryValue,
                        enrolledFingers = enrolledFingers,
                        hasBiometric = biometricsValue.isNotEmpty(),
                        biometricCount = biometricsValue.size,
                        recaptureBreakdown = recaptureBreakdown,
                        isLoading = false
                    )
                }

                // Calculate service eligibility locally from Room data — no network needed.
                try {
                    val eligibilityMap = ServiceEligibilityEngine.calculateAll(
                        patient = patientValue ?: return@launch,
                        artPharmacy = artPharmacyValue
                    )
                    val eligibleServices = eligibilityMap
                        .filter { it.value.eligible }
                        .keys
                        .toList()
                    val artRefillGroupCounts = ServiceEligibilityEngine.artRefillEligibilityGroupCounts(eligibilityMap)
                    val viralLoadGroupCounts = ServiceEligibilityEngine.viralLoadEligibilityGroupCounts(eligibilityMap)
                    _uiState.update {
                        it.copy(
                            serviceEligibility = eligibilityMap,
                            eligibleServices = eligibleServices,
                            eligibleCount = eligibleServices.size,
                            artRefillEligibilityGroupCounts = artRefillGroupCounts,
                            viralLoadEligibilityGroupCounts = viralLoadGroupCounts,
                            isServiceEligibilityLoading = false,
                            serviceEligibilityError = null
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    _uiState.update {
                        it.copy(
                            isServiceEligibilityLoading = false,
                            serviceEligibilityError = "Could not calculate eligibility"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isServiceEligibilityLoading = false,
                        errorMessage = e.message
                    )
                }
            }
        }
    }
}

private data class Quad<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
)