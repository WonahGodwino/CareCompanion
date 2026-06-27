package com.carecompanion.utils

import com.carecompanion.data.database.entities.EacEpisode

/**
 * On-device EAC cascade gap detection — mirrors WINCO `_eac_assessment`. Gaps key off the CURRENT /
 * most-recent VL and are raised ONLY for active (TX_CURR) clients; a client who is IIT or any TX_ML
 * category gets no EAC flag (return-to-care first). Prior incomplete/stopped episodes surface as
 * warnings, not gaps. Reuses [VlGapSeverity].
 */
enum class EacGapType {
    EAC_NOT_INITIATED,     // unsuppressed, no episode
    EAC_NOT_COMMENCED,     // episode opened, no session
    EAC_INCOMPLETE,        // 1–2 of 3 sessions
    EAC_STOPPED,           // stopped before completion
    POST_EAC_VL_PENDING,   // complete, no Post-EAC confirmation VL
    FAILURE_NOT_ACTIONED,  // Post-EAC VL still >=1000, no regimen switch
}

data class EacGap(val type: EacGapType, val severity: VlGapSeverity, val message: String)

data class EacWarning(val episodeUuid: String?, val stage: String?, val message: String)

data class EacAssessment(
    val gaps: List<EacGap>,
    val warnings: List<EacWarning>,
    val currentEpisode: EacEpisode?,
)

object EacGapEngine {
    const val UNSUPPRESSED_THRESHOLD = 1000L
    private val UNFINISHED = setOf("NOT_STARTED", "IN_PROGRESS", "STOPPED")

    fun assess(
        episodes: List<EacEpisode>,
        latestVlNumeric: Long?,
        isTxCurr: Boolean,
    ): EacAssessment {
        val sorted = episodes.sortedByDescending { it.triggerDate?.time ?: Long.MIN_VALUE }
        val current = sorted.firstOrNull()
        val gaps = mutableListOf<EacGap>()
        val warnings = mutableListOf<EacWarning>()
        val unsuppressed = latestVlNumeric != null && latestVlNumeric >= UNSUPPRESSED_THRESHOLD

        // GAPS: only for active (TX_CURR) clients whose current VL is unsuppressed.
        if (isTxCurr && unsuppressed) {
            when (current?.stage) {
                null -> gaps += EacGap(
                    EacGapType.EAC_NOT_INITIATED, VlGapSeverity.CRITICAL,
                    "Unsuppressed VL ($latestVlNumeric c/mL) with no EAC episode started.",
                )
                "NOT_STARTED" -> gaps += EacGap(
                    EacGapType.EAC_NOT_COMMENCED, VlGapSeverity.HIGH,
                    "EAC episode opened but no session held.",
                )
                "IN_PROGRESS" -> gaps += EacGap(
                    EacGapType.EAC_INCOMPLETE, VlGapSeverity.HIGH,
                    "EAC in progress (${current.sessions} of 3 sessions) — complete the remaining sessions.",
                )
                "STOPPED" -> gaps += EacGap(
                    EacGapType.EAC_STOPPED, VlGapSeverity.HIGH,
                    "EAC stopped before completion while client still unsuppressed.",
                )
                "COMPLETE" -> {
                    val repeat = current.repeatVl
                    when {
                        repeat == null -> gaps += EacGap(
                            EacGapType.POST_EAC_VL_PENDING, VlGapSeverity.HIGH,
                            "EAC complete — Post-EAC confirmation VL not yet done.",
                        )
                        repeat >= UNSUPPRESSED_THRESHOLD && !current.regimenSwitched -> gaps += EacGap(
                            EacGapType.FAILURE_NOT_ACTIONED, VlGapSeverity.CRITICAL,
                            "Post-EAC VL still unsuppressed (${repeat.toLong()}) with no regimen switch — review for failure.",
                        )
                    }
                }
            }
        }

        // WARNINGS: prior episodes left unfinished (historical context, any TX status).
        sorted.drop(1).forEach { ep ->
            if (ep.stage in UNFINISHED) {
                warnings += EacWarning(
                    ep.episodeUuid, ep.stage,
                    "Earlier EAC episode ${ep.stage?.lowercase()} — review history.",
                )
            }
        }
        return EacAssessment(gaps, warnings, current)
    }
}
