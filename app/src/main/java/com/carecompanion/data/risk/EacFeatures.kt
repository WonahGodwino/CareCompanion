package com.carecompanion.data.risk

import kotlin.math.log10

/**
 * Feature builder for the EAC cascade-failure head — must stay byte-for-byte in step with WINCO
 * `services/eac_training.py :: EAC_FEATURE_NAMES`. Predicts, at an unsuppressed (non-Post-EAC) VL,
 * whether the episode will fail to re-suppress within 12 months. Bump [SCHEMA_VERSION] if this changes.
 */
object EacFeatures {
    const val SCHEMA_VERSION = "eac-v1"
    val FEATURE_NAMES = listOf(
        "log10_trigger_vl", "months_on_art", "age", "prior_unsuppressed",
        "n_visits", "iit_episodes", "late_pickups", "had_prior_eac",
    )

    fun vector(
        triggerVl: Long,
        monthsOnArt: Int,
        age: Int,
        priorUnsuppressed: Int,
        nVisits: Int,
        iitEpisodes: Int,
        latePickups: Int,
        hadPriorEac: Boolean,
    ): DoubleArray = doubleArrayOf(
        log10(triggerVl + 1.0),
        monthsOnArt.toDouble(),
        age.toDouble(),
        priorUnsuppressed.toDouble(),
        nVisits.toDouble(),
        iitEpisodes.toDouble(),
        latePickups.toDouble(),
        if (hadPriorEac) 1.0 else 0.0,
    )
}
