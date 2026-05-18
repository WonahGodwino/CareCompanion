package com.carecompanion.presentation.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carecompanion.biometric.BiometricManager
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

enum class RecallStep { IDLE, SCANNING, MATCHING, MATCHED, NO_MATCH, ERROR }

data class RecallBiometricUiState(
    val step: RecallStep = RecallStep.IDLE,
    val matchedPatient: MatchedPatient? = null,
    val matchScore: Double = 0.0,
    val scanQuality: Int = 0,
    val lastScanTime: Long? = null,
    val errorMessage: String? = null,
    val totalBiometricTemplates: Int = 0,
    val totalClientsWithBiometrics: Int = 0,
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
    private val syncRepository: com.carecompanion.data.repository.SyncRepository,
    private val biometricManager: BiometricManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecallBiometricUiState())
    val uiState: StateFlow<RecallBiometricUiState> = _uiState.asStateFlow()

    init {
        loadBiometricStats()
        observeScannerStatus()
    }

    private fun loadBiometricStats() {
        viewModelScope.launch {
            val allBiometrics = patientRepository.getAllBiometrics()
            _uiState.update {
                it.copy(
                    totalBiometricTemplates = allBiometrics.size,
                    totalClientsWithBiometrics = allBiometrics.map { bio -> bio.personUuid }.distinct().size
                )
            }
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
            val allBiometrics = patientRepository.getAllBiometrics()
            _uiState.update {
                it.copy(
                    totalBiometricTemplates = allBiometrics.size,
                    totalClientsWithBiometrics = allBiometrics.map { bio -> bio.personUuid }.distinct().size
                )
            }
            _uiState.update { it.copy(step = RecallStep.SCANNING) }
            try {
                val template = biometricManager.captureFingerprint(timeoutSeconds = 30)
                if (template == null) {
                    _uiState.update { it.copy(step = RecallStep.ERROR, errorMessage = "Capture timed out. Place finger firmly on scanner and try again.") }
                    return@launch
                }
                val now = System.currentTimeMillis()
                _uiState.update { it.copy(scanQuality = biometricManager.getQuality(), lastScanTime = now) }
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
            val matchResult = syncRepository.findPatientByBiometric(template)
            if (matchResult != null && matchResult.patient != null && matchResult.template != null) {
                _uiState.update {
                    it.copy(
                        step = RecallStep.MATCHED,
                        matchedPatient = MatchedPatient(matchResult.patient, matchResult.template, matchResult.confidence),
                        matchScore = matchResult.confidence * 100.0
                    )
                }
                return
            }

            val sdkFallback = runSdkIdentificationFallback(template)
            if (sdkFallback != null) {
                _uiState.update {
                    it.copy(
                        step = RecallStep.MATCHED,
                        matchedPatient = MatchedPatient(sdkFallback.first, sdkFallback.second, sdkFallback.third),
                        matchScore = sdkFallback.third
                    )
                }
                return
            }

            _uiState.update { it.copy(step = RecallStep.NO_MATCH, matchScore = (matchResult?.confidence ?: 0.0) * 100.0) }
        } catch (e: Exception) {
            _uiState.update { it.copy(step = RecallStep.ERROR, errorMessage = e.message) }
        }
    }

    private suspend fun runSdkIdentificationFallback(probe: ByteArray): Triple<Patient, Biometric, Double>? {
        val allBiometrics = patientRepository.getAllBiometrics()
        var best: Pair<Biometric, MatchResult>? = null
        // For identification (1:N), use a lower threshold than verification
        // SL_HIGH security level is conservative, so we accept scores >= 50.0
        val identificationThreshold = 50.0
        for (bio in allBiometrics) {
            val result = biometricManager.match(probe, bio.template)
            if (result.score >= identificationThreshold && (best == null || result.score > best.second.score)) {
                best = Pair(bio, result)
            }
        }
        val bestMatch = best ?: return null
        val patient = patientRepository.getPatientByUuid(bestMatch.first.personUuid) ?: return null
        return Triple(patient, bestMatch.first, bestMatch.second.score)
    }

    fun reset() {
        _uiState.update {
            RecallBiometricUiState(
                totalBiometricTemplates = it.totalBiometricTemplates,
                totalClientsWithBiometrics = it.totalClientsWithBiometrics,
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
