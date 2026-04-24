package com.carecompanion.presentation.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carecompanion.data.database.entities.ArtPharmacy
import com.carecompanion.data.database.entities.Biometric
import com.carecompanion.data.database.entities.Patient
import com.carecompanion.data.repository.PatientRepository
import com.carecompanion.biometric.models.FingerType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PatientProfileUiState(
    val patient: Patient? = null,
    val biometrics: List<Biometric> = emptyList(),
    val artPharmacy: List<ArtPharmacy> = emptyList(),
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
}