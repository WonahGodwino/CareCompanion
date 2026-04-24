package com.carecompanion.presentation.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carecompanion.biometric.BiometricManager
import com.carecompanion.biometric.models.FingerType
import com.carecompanion.biometric.models.MatchResult
import com.carecompanion.biometric.models.MatchedPatient
import com.carecompanion.biometric.secugen.PoorQualityException
import com.carecompanion.data.database.entities.Biometric
import com.carecompanion.data.database.entities.Patient
import com.carecompanion.data.repository.PatientRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class VerifyStep { IDLE, SCANNING, MATCHING, MATCHED, NO_MATCH, ERROR }

data class VerifyUiState(
    val step: VerifyStep = VerifyStep.IDLE,
    val selectedFinger: FingerType = FingerType.RIGHT_THUMB,
    val matchedPatient: MatchedPatient? = null,
    val matchScore: Double = 0.0,
    val scanQuality: Int = 0,
    val errorMessage: String? = null,
    val selectedClientTemplateGroups: Int = 0,
    val isScannerReady: Boolean = false,
    val isScannerConnected: Boolean = false,
    val isScannerAccessGranted: Boolean = false,
    val scannerInfoText: String = "No scanner detected",
    val scannerPermissionState: String = "unknown",
    val scannerConnectionState: String = "idle"
)

@HiltViewModel
class VerifyViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val patientRepository: PatientRepository,
    private val biometricManager: BiometricManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(VerifyUiState())
    val uiState: StateFlow<VerifyUiState> = _uiState.asStateFlow()

    init {
        observeScannerStatus()
    }

    fun refreshTemplateGroupCountForPatient(selectedPatient: Patient?) {
        if (selectedPatient == null) {
            _uiState.update { it.copy(selectedClientTemplateGroups = 0) }
            return
        }
        viewModelScope.launch {
            val count = patientRepository.getBiometricsForPatient(selectedPatient.uuid)
                .map { it.recapture }
                .distinct()
                .count()
            _uiState.update { it.copy(selectedClientTemplateGroups = count) }
        }
    }

    private fun observeScannerStatus() {
        viewModelScope.launch {
            biometricManager.status.collect {
                val info = biometricManager.getScannerInfo()
                val infoText = "Connected: ${if (info.isConnected) "Yes" else "No"} | Access: ${if (info.accessGranted) "Granted" else "Not granted"} | Ready: ${if (info.isReady) "Yes" else "No"}"
                _uiState.update {
                    it.copy(
                        isScannerReady = info.isReady,
                        isScannerConnected = info.isConnected,
                        isScannerAccessGranted = info.accessGranted,
                        scannerInfoText = infoText,
                        scannerPermissionState = info.permissionState,
                        scannerConnectionState = info.connectionState
                    )
                }
            }
        }
    }

    fun selectFinger(fingerType: FingerType) {
        _uiState.update { it.copy(selectedFinger = fingerType) }
    }

    fun startScan(selectedPatient: Patient?) {
        if (selectedPatient == null) {
            _uiState.update { it.copy(step = VerifyStep.ERROR, errorMessage = "Select a client before starting verification.") }
            return
        }
        if (!biometricManager.isReady()) {
            val debugInfo = biometricManager.getScannerDebugInfo()
            val info = biometricManager.getScannerInfo()
            _uiState.update {
                it.copy(
                    step = VerifyStep.ERROR,
                    errorMessage = "Scanner not ready. Connected: ${if (info.isConnected) "Yes" else "No"}, Access: ${if (info.accessGranted) "Granted" else "Not granted"}, Ready: ${if (info.isReady) "Yes" else "No"}.\n$debugInfo"
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(step = VerifyStep.SCANNING) }
            try {
                val template = biometricManager.captureFingerprint(timeoutSeconds = 30)
                if (template == null) {
                    _uiState.update { it.copy(step = VerifyStep.ERROR, errorMessage = "Capture timed out. Place finger firmly on scanner and try again.") }
                    return@launch
                }
                _uiState.update { it.copy(scanQuality = biometricManager.getQuality()) }
                onFingerprintCaptured(template, selectedPatient, _uiState.value.selectedFinger)
            } catch (e: PoorQualityException) {
                _uiState.update {
                    it.copy(step = VerifyStep.ERROR,
                        errorMessage = "Image quality too low (${e.quality}/100) after ${e.attempts} attempts. " +
                            "Clean your finger and the scanner glass, then retry.")
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(step = VerifyStep.ERROR, errorMessage = e.message ?: "Scanner error") }
            }
        }
    }

    private suspend fun onFingerprintCaptured(template: ByteArray, selectedPatient: Patient, selectedFinger: FingerType) {
        _uiState.update { it.copy(step = VerifyStep.MATCHING) }
        try {
            val patientFingerBiometrics = patientRepository.getBiometricsForPatient(selectedPatient.uuid)
                .filter { matchesSelectedFinger(it, selectedFinger) }

            if (patientFingerBiometrics.isEmpty()) {
                _uiState.update {
                    it.copy(
                        step = VerifyStep.ERROR,
                        errorMessage = "No ${selectedFinger.displayName} biometric template found for ${selectedPatient.fullName ?: selectedPatient.hospitalNumber}."
                    )
                }
                return
            }

            val match = findBestMatch(template, patientFingerBiometrics)
            if (match != null) {
                _uiState.update {
                    it.copy(
                        step = VerifyStep.MATCHED,
                        matchedPatient = MatchedPatient(selectedPatient, match.first, match.second.score),
                        matchScore = match.second.score
                    )
                }
                return
            }
            _uiState.update { it.copy(step = VerifyStep.NO_MATCH, matchScore = match?.second?.score ?: 0.0) }
        } catch (e: Exception) {
            _uiState.update { it.copy(step = VerifyStep.ERROR, errorMessage = e.message) }
        }
    }

    private fun matchesSelectedFinger(biometric: Biometric, selectedFinger: FingerType): Boolean {
        val templateType = biometric.templateType?.trim()?.replace("_", " ")?.lowercase()
        val displayName = selectedFinger.displayName.lowercase()
        val enumName = selectedFinger.name.replace("_", " ").lowercase()
        return templateType == displayName || templateType == enumName
    }

    private fun findBestMatch(probe: ByteArray, gallery: List<Biometric>): Pair<Biometric, MatchResult>? {
        var best: Pair<Biometric, MatchResult>? = null
        for (bio in gallery) {
            val result = biometricManager.match(probe, bio.template)
            if (result.isMatch && (best == null || result.score > best.second.score)) {
                best = Pair(bio, result)
            }
        }
        return best
    }

    fun reset() {
        _uiState.update {
            VerifyUiState(
                selectedClientTemplateGroups = it.selectedClientTemplateGroups,
                isScannerReady = it.isScannerReady,
                isScannerConnected = it.isScannerConnected,
                isScannerAccessGranted = it.isScannerAccessGranted,
                scannerInfoText = it.scannerInfoText,
                scannerPermissionState = it.scannerPermissionState,
                scannerConnectionState = it.scannerConnectionState
            )
        }
    }
    fun clearError() { _uiState.update { it.copy(errorMessage = null, step = VerifyStep.IDLE) } }

    /** Re-request USB permission for the scanner, then clear the error state. */
    fun retryScanner() {
        biometricManager.retryConnection()
        _uiState.update { it.copy(errorMessage = null, step = VerifyStep.IDLE) }
    }
}