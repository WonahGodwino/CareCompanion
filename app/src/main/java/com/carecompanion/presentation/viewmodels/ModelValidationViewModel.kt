package com.carecompanion.presentation.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carecompanion.data.network.WincoApiService
import com.carecompanion.data.risk.BacktestResult
import com.carecompanion.data.risk.EacFeatures
import com.carecompanion.data.risk.LearnedRiskModel
import com.carecompanion.data.risk.ModelStore
import com.carecompanion.data.risk.RiskBacktestService
import com.carecompanion.data.risk.RiskFeatures
import com.carecompanion.data.risk.RiskScoringMode
import com.carecompanion.utils.SharedPreferencesHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModelValidationUiState(
    val running: Boolean = false,
    val result: BacktestResult? = null,
    val error: String? = null,
    val activeMode: RiskScoringMode = RiskScoringMode.HEURISTIC,
    val message: String? = null,
)

@HiltViewModel
class ModelValidationViewModel @Inject constructor(
    private val backtestService: RiskBacktestService,
    private val wincoApi: WincoApiService,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelValidationUiState(activeMode = ModelStore.mode()))
    val uiState: StateFlow<ModelValidationUiState> = _uiState.asStateFlow()

    fun runValidation() {
        if (_uiState.value.running) return
        _uiState.value = _uiState.value.copy(running = true, error = null, message = null, result = null)
        viewModelScope.launch {
            runCatching { backtestService.run() }
                .onSuccess { _uiState.value = _uiState.value.copy(running = false, result = it) }
                .onFailure { _uiState.value = _uiState.value.copy(running = false, error = it.message ?: "Validation failed") }
        }
    }

    /** Adopt the trained model for live scoring (only meaningful when the guardrail passed). */
    fun adopt() {
        val model = _uiState.value.result?.model ?: return
        ModelStore.save(model)
        _uiState.value = _uiState.value.copy(
            activeMode = RiskScoringMode.LEARNED,
            message = "Learned model adopted — now used for forecast scoring.",
        )
    }

    fun revertToHeuristic() {
        ModelStore.revertToHeuristic()
        _uiState.value = _uiState.value.copy(
            activeMode = RiskScoringMode.HEURISTIC,
            message = "Reverted to the explainable heuristic.",
        )
    }

    /** Phase 2: pull this facility's federated model from WINCO. The guardrail still gates use. */
    fun pullFromServer() {
        _uiState.value = _uiState.value.copy(message = "Pulling model from WINCO…")
        viewModelScope.launch {
            runCatching {
                wincoApi.getRiskModel(SharedPreferencesHelper.getActiveFacilityId(context), outcome = "iit")
            }.onSuccess { p ->
                if (p.schemaVersion != RiskFeatures.SCHEMA_VERSION) {
                    _uiState.value = _uiState.value.copy(
                        message = "Server model schema '${p.schemaVersion}' is incompatible — ignored.")
                    return@launch
                }
                val model = LearnedRiskModel(
                    schemaVersion = p.schemaVersion,
                    featureNames = p.featureNames,
                    weights = p.weights.toDoubleArray(),
                    bias = p.bias,
                    mean = p.scaling.mean.toDoubleArray(),
                    std = p.scaling.std.toDoubleArray(),
                    nSamples = p.training.nSamples,
                    auc = p.training.auc ?: 0.0,
                    heuristicAuc = p.training.heuristicAuc ?: 1.0, // missing → guardrail fails safe
                    trainedAt = System.currentTimeMillis(),
                )
                ModelStore.save(model)

                // Also pull the EAC cascade-failure head (second prediction outcome).
                runCatching {
                    wincoApi.getRiskModel(SharedPreferencesHelper.getActiveFacilityId(context), outcome = "eac_failure")
                }.onSuccess { ep ->
                    if (ep.schemaVersion == EacFeatures.SCHEMA_VERSION) {
                        ModelStore.saveEac(
                            LearnedRiskModel(
                                schemaVersion = ep.schemaVersion, featureNames = ep.featureNames,
                                weights = ep.weights.toDoubleArray(), bias = ep.bias,
                                mean = ep.scaling.mean.toDoubleArray(), std = ep.scaling.std.toDoubleArray(),
                                nSamples = ep.training.nSamples, auc = ep.training.auc ?: 0.0,
                                heuristicAuc = ep.training.heuristicAuc ?: 0.0,
                                trainedAt = System.currentTimeMillis(),
                            )
                        )
                    }
                }

                _uiState.value = _uiState.value.copy(
                    activeMode = ModelStore.mode(),
                    message = "Pulled facility model (CV-AUC ${"%.2f".format(model.auc)}, " +
                        "n=${model.nSamples}). Guardrail gates whether it's used.",
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(message = "Pull failed: ${it.message}")
            }
        }
    }
}
