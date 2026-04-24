package com.carecompanion.presentation.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carecompanion.biometric.BiometricManager
import com.carecompanion.biometric.models.MatchedPatient
import com.carecompanion.biometric.secugen.PoorQualityException
import com.carecompanion.data.database.entities.Biometric
import com.carecompanion.data.repository.PatientRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RecallStep { IDLE, SCANNING, MATCHING, MATCHED, NO_MATCH, ERROR }

data class RecallBiometricUiState(
    val step: RecallStep = RecallStep.IDLE,
    val matchedPatient: MatchedPatient? = null,
    val matchScore: Double = 0.0,
    val scanQuality: Int = 0,
    val errorMessage: String? = null,
    val totalTemplateGroups: Int = 0,
    val isScannerReady: Boolean = false,
    val isScannerConnected: Boolean = false,
    val isScannerAccessGranted: Boolean = false,
    val scannerInfoText: String = "No scanner detected",
    val scannerPermissionState: String = "unknown",
    val scannerConnectionState: String = "idle"
)

@HiltViewModel
class RecallBiometricViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val patientRepository: PatientRepository,
    private val biometricManager: BiometricManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecallBiometricUiState())
    val uiState: StateFlow<RecallBiometricUiState> = _uiState.asStateFlow()

    init {
        loadTemplateGroupCount()
        observeScannerStatus()
    }

    private fun loadTemplateGroupCount() {
        viewModelScope.launch {
            val count = patientRepository.getAllBiometrics()
                .map { it.recapture }
                .distinct()
                .count()
            _uiState.update { it.copy(totalTemplateGroups = count) }
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

    fun startScan() {
        if (!biometricManager.isReady()) {
            val debugInfo = biometricManager.getScannerDebugInfo()
            val info = biometricManager.getScannerInfo()
            _uiState.update {
                it.copy(
                    step = RecallStep.ERROR,
                    errorMessage = "Scanner not ready. Connected: ${if (info.isConnected) "Yes" else "No"}, Access: ${if (info.accessGranted) "Granted" else "Not granted"}, Ready: ${if (info.isReady) "Yes" else "No"}.\n$debugInfo"
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(step = RecallStep.SCANNING) }
            try {
                val template = biometricManager.captureFingerprint(timeoutSeconds = 30)
                if (template == null) {
                    _uiState.update { it.copy(step = RecallStep.ERROR, errorMessage = "Capture timed out. Place finger firmly on scanner and try again.") }
                    return@launch
                }
                _uiState.update { it.copy(scanQuality = biometricManager.getQuality()) }
                matchFingerprint(template)
            } catch (e: PoorQualityException) {
                _uiState.update {
                    it.copy(step = RecallStep.ERROR,
                        errorMessage = "Image quality too low (${e.quality}/100). Clean your finger and the scanner glass, then retry.")
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(step = RecallStep.ERROR, errorMessage = e.message ?: "Scanner error") }
            }
        }
    }

    private suspend fun matchFingerprint(template: ByteArray) {
        _uiState.update { it.copy(step = RecallStep.MATCHING) }
        try {
            val allBiometrics = patientRepository.getAllBiometrics()
            var best: Pair<Biometric, Double>? = null
            for (bio in allBiometrics) {
                val result = biometricManager.match(template, bio.template)
                if (result.isMatch && (best == null || result.score > best.second)) {
                    best = Pair(bio, result.score)
                }
            }
            if (best != null) {
                val patient = patientRepository.getPatientByUuid(best.first.personUuid)
                if (patient != null) {
                    _uiState.update {
                        it.copy(
                            step = RecallStep.MATCHED,
                            matchedPatient = MatchedPatient(patient, best.first, best.second),
                            matchScore = best.second
                        )
                    }
                    return
                }
            }
            _uiState.update { it.copy(step = RecallStep.NO_MATCH, matchScore = best?.second ?: 0.0) }
        } catch (e: Exception) {
            _uiState.update { it.copy(step = RecallStep.ERROR, errorMessage = e.message) }
        }
    }

    fun reset() {
        _uiState.update {
            RecallBiometricUiState(
                totalTemplateGroups = it.totalTemplateGroups,
                isScannerReady = it.isScannerReady,
                isScannerConnected = it.isScannerConnected,
                isScannerAccessGranted = it.isScannerAccessGranted,
                scannerInfoText = it.scannerInfoText,
                scannerPermissionState = it.scannerPermissionState,
                scannerConnectionState = it.scannerConnectionState
            )
        }
    }
    fun clearError() { _uiState.update { it.copy(errorMessage = null, step = RecallStep.IDLE) } }

    /** Re-request USB permission for the scanner, then clear the error state. */
    fun retryScanner() {
        biometricManager.retryConnection()
        _uiState.update { it.copy(errorMessage = null, step = RecallStep.IDLE) }
    }
}
