package com.carecompanion.data.risk

import kotlin.math.log10
import kotlin.math.max

/**
 * Single source of truth for the model feature vector. Both the backtest/training path
 * ([RiskBacktestService]) and the live scoring path ([RiskAssessmentService]) MUST build
 * features through here, so a model trained on these features is served the identical
 * representation. Train/serve skew is the classic ML bug this prevents.
 *
 * **feat-v2** = feat-v1 (9) + 3 viral-load features. The order and VL maths MUST match the WINCO
 * trainer (`services/risk_training.py`: `_feature_row` + `_vl_features`) exactly.
 *
 * Bump [SCHEMA_VERSION] whenever the vector changes — a stored model with a different schema is
 * ignored at load time (graceful fallback to the heuristic).
 */
object RiskFeatures {
    const val SCHEMA_VERSION = "feat-v2"

    val NAMES = listOf(
        "Prior IIT episodes", "Prior late pickups", "Late-pickup rate",
        "Visits so far", "Age (years)", "Paediatric", "Refill period (d)",
        "Distance (km)", "Has phone", "VL known", "VL suppressed", "VL log10",
    )

    private val NEUTRAL_VL_LOG = log10(1001.0)

    /**
     * @param gaps  number of completed visit-to-next-visit intervals observed so far.
     * @param vlValue  most recent viral-load result (copies/mL) BEFORE the reference date, or null.
     */
    fun vector(
        priorIitEpisodes: Int,
        priorLatePickups: Int,
        gaps: Int,
        ageYears: Int,
        refillDays: Int,
        distanceKm: Double?,
        hasPhone: Boolean,
        vlValue: Long?,
    ): DoubleArray {
        val denom = gaps.coerceAtLeast(1)
        return doubleArrayOf(
            priorIitEpisodes.toDouble(),
            priorLatePickups.toDouble(),
            priorLatePickups.toDouble() / denom,
            gaps.toDouble(),
            ageYears.toDouble(),
            if (ageYears in 0..14) 1.0 else 0.0,
            refillDays.toDouble(),
            distanceKm ?: 0.0,
            if (hasPhone) 1.0 else 0.0,
        ) + vlFeatures(vlValue)
    }

    /** [VL known, VL suppressed, VL log10] — mirrors WINCO `_vl_features`. */
    private fun vlFeatures(vlValue: Long?): DoubleArray {
        if (vlValue == null) return doubleArrayOf(0.0, 0.0, NEUTRAL_VL_LOG)
        val v = max(0.0, vlValue.toDouble())
        return doubleArrayOf(1.0, if (v < 1000) 1.0 else 0.0, log10(v + 1.0))
    }
}
