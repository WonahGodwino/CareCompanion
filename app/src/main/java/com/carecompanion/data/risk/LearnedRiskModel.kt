package com.carecompanion.data.risk

import com.carecompanion.utils.PatientRiskEngine.RiskBand
import com.carecompanion.utils.SharedPreferencesHelper
import com.google.gson.Gson
import kotlin.math.exp

enum class RiskScoringMode { HEURISTIC, LEARNED }

/**
 * A trained, persistable logistic-regression model plus the metadata needed to serve it
 * safely (schema, sample size, and the cross-validated AUCs used for the adoption guardrail).
 */
data class LearnedRiskModel(
    val schemaVersion: String,
    val featureNames: List<String>,
    val weights: DoubleArray,
    val bias: Double,
    val mean: DoubleArray,
    val std: DoubleArray,
    val nSamples: Int,
    val auc: Double,            // cross-validated AUC of this model
    val heuristicAuc: Double,   // cross-validated AUC of the heuristic on the same data
    val trainedAt: Long,
) {
    /** P(becomes IIT) for a raw, unstandardised feature row. */
    fun probability(x: DoubleArray): Double {
        var z = bias
        for (i in weights.indices) z += weights[i] * ((x[i] - mean[i]) / std[i])
        return 1.0 / (1.0 + exp(-z))
    }
}

/** Provisional probability → risk band mapping (tunable; the validation screen shows the operating point). */
fun bandForProbability(p: Double): RiskBand = when {
    p >= 0.60 -> RiskBand.CRITICAL
    p >= 0.40 -> RiskBand.HIGH
    p >= 0.25 -> RiskBand.MODERATE
    else      -> RiskBand.LOW
}

/**
 * Stores/loads the adopted model and enforces the **local guardrail**: the learned model is
 * only used for live scoring if it is adopted, schema-compatible, and beat the heuristic in
 * cross-validation on this facility's own data.
 */
object ModelStore {
    private val gson = Gson()

    fun save(model: LearnedRiskModel) {
        SharedPreferencesHelper.setLearnedModelJson(gson.toJson(model))
        SharedPreferencesHelper.setRiskScoringMode(RiskScoringMode.LEARNED.name)
    }

    fun load(): LearnedRiskModel? =
        SharedPreferencesHelper.getLearnedModelJson()?.let {
            runCatching { gson.fromJson(it, LearnedRiskModel::class.java) }.getOrNull()
        }

    fun mode(): RiskScoringMode =
        runCatching { RiskScoringMode.valueOf(SharedPreferencesHelper.getRiskScoringMode()) }
            .getOrDefault(RiskScoringMode.HEURISTIC)

    fun revertToHeuristic() = SharedPreferencesHelper.setRiskScoringMode(RiskScoringMode.HEURISTIC.name)

    /** The model to use for live scoring, or null to fall back to the heuristic. */
    fun activeModel(): LearnedRiskModel? {
        if (mode() != RiskScoringMode.LEARNED) return null
        val m = load() ?: return null
        if (m.schemaVersion != RiskFeatures.SCHEMA_VERSION) return null   // schema drift → fallback
        if (m.auc < m.heuristicAuc) return null                          // guardrail
        return m
    }
}
