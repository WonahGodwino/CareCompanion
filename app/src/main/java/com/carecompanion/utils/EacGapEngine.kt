package com.carecompanion.utils

import com.carecompanion.data.database.entities.EacEpisode
import java.util.concurrent.TimeUnit

/**
 * On-device EAC cascade gap detection — mirrors WINCO `_eac_assessment`. Gaps key off the CURRENT /
 * most-recent VL and are raised ONLY for active (TX_CURR) clients; an IIT/TX_ML client gets no EAC flag
 * (return-to-care first). EAC = 3 monthly sessions (~30 days apart) over ~3 months, then a Post-EAC
 * confirmation VL; a 4th session may precede it if the 3 were inadequate.
 *
 *  - The unsuppressed VL's TYPE matters: a Post-EAC (302) sample is the confirmation step (failure
 *    pathway); any other unsuppressed VL means unsuppressed OUTSIDE the EAC pathway → (re)start EAC.
 *  - EAC_SESSION_DUE fires on the 30-day cadence from the last session (or after the trigger for s1).
 *  - "Incomplete EAC review" is a WARNING, emphasised when there is a recent unsuppressed non-Post-EAC VL.
 */
enum class EacGapType {
    EAC_NOT_INITIATED,     // unsuppressed, no episode
    EAC_NOT_COMMENCED,     // episode opened, no session yet (within grace)
    EAC_SESSION_DUE,       // next session overdue by the 30-day cadence
    EAC_INCOMPLETE,        // 1–2 of 3 sessions, next session on schedule
    EAC_STOPPED,           // stopped before completion
    POST_EAC_VL_PENDING,   // complete, no Post-EAC confirmation VL
    FAILURE_NOT_ACTIONED,  // Post-EAC VL still >=1000, no regimen switch
}

data class EacGap(val type: EacGapType, val severity: VlGapSeverity, val message: String)

data class EacWarning(val episodeUuid: String?, val stage: String?, val message: String, val severity: VlGapSeverity = VlGapSeverity.MODERATE)

data class EacAssessment(
    val gaps: List<EacGap>,
    val warnings: List<EacWarning>,
    val currentEpisode: EacEpisode?,
)

object EacGapEngine {
    const val UNSUPPRESSED_THRESHOLD = 1000L
    const val SESSION_INTERVAL_DAYS = 30L
    const val SESSION1_GRACE_DAYS = 14L
    const val TARGET_SESSIONS = 3
    private val UNFINISHED = setOf("NOT_STARTED", "IN_PROGRESS", "STOPPED")

    fun assess(
        episodes: List<EacEpisode>,
        latestVlNumeric: Long?,
        isTxCurr: Boolean,
        latestVlCategory: String? = null,
        todayMs: Long = System.currentTimeMillis(),
    ): EacAssessment {
        val sorted = episodes.sortedByDescending { it.triggerDate?.time ?: Long.MIN_VALUE }
        val current = sorted.firstOrNull()
        val gaps = mutableListOf<EacGap>()
        val warnings = mutableListOf<EacWarning>()
        val unsuppressed = latestVlNumeric != null && latestVlNumeric >= UNSUPPRESSED_THRESHOLD
        val isPostEacVl = latestVlCategory == "Post-EAC"

        fun daysSince(ms: Long): Long = TimeUnit.MILLISECONDS.toDays(todayMs - ms)

        // GAPS: only for active (TX_CURR) clients whose current VL is unsuppressed.
        if (isTxCurr && unsuppressed) {
            if (isPostEacVl) {
                // The unsuppressed reading IS the Post-EAC confirmation → treatment-failure pathway.
                if (current?.regimenSwitched != true) gaps += EacGap(
                    EacGapType.FAILURE_NOT_ACTIONED, VlGapSeverity.CRITICAL,
                    "Post-EAC VL still unsuppressed ($latestVlNumeric c/mL) — review for treatment failure / switch.",
                )
            } else when (current?.stage) {
                null -> gaps += EacGap(
                    EacGapType.EAC_NOT_INITIATED, VlGapSeverity.CRITICAL,
                    "Unsuppressed VL ($latestVlNumeric c/mL) with no EAC episode started.",
                )
                "NOT_STARTED" -> {
                    val due = current.triggerDate == null || daysSince(current.triggerDate.time) >= SESSION1_GRACE_DAYS
                    if (due) gaps += EacGap(EacGapType.EAC_SESSION_DUE, VlGapSeverity.HIGH,
                        "EAC not commenced — session 1 due after the unsuppressed VL.")
                    else gaps += EacGap(EacGapType.EAC_NOT_COMMENCED, VlGapSeverity.HIGH,
                        "EAC episode opened but no session held yet.")
                }
                "IN_PROGRESS" -> {
                    val overdue = current.lastSessionDate != null &&
                        daysSince(current.lastSessionDate.time) >= SESSION_INTERVAL_DAYS &&
                        current.sessions < TARGET_SESSIONS
                    if (overdue) gaps += EacGap(EacGapType.EAC_SESSION_DUE, VlGapSeverity.HIGH,
                        "EAC session ${current.sessions + 1} due — last session >${SESSION_INTERVAL_DAYS}d ago (${current.sessions} of 3 done).")
                    else gaps += EacGap(EacGapType.EAC_INCOMPLETE, VlGapSeverity.MODERATE,
                        "EAC in progress (${current.sessions} of 3 sessions) — next session on schedule.")
                }
                "STOPPED" -> gaps += EacGap(EacGapType.EAC_STOPPED, VlGapSeverity.HIGH,
                    "EAC stopped before completion while client still unsuppressed.")
                "COMPLETE" -> {
                    val repeat = current.repeatVl
                    when {
                        repeat == null -> gaps += EacGap(EacGapType.POST_EAC_VL_PENDING, VlGapSeverity.HIGH,
                            "EAC complete (${current.sessions} sessions) — Post-EAC confirmation VL not yet collected.")
                        repeat >= UNSUPPRESSED_THRESHOLD && !current.regimenSwitched -> gaps += EacGap(
                            EacGapType.FAILURE_NOT_ACTIONED, VlGapSeverity.CRITICAL,
                            "Post-EAC VL still unsuppressed (${repeat.toLong()}) with no regimen switch — review for failure.")
                    }
                }
            }
        }

        // WARNING: incomplete EAC review — any episode without completion OR a Post-EAC VL. Emphasised
        // (high) when the client has a recent unsuppressed VL that is NOT a Post-EAC sample.
        val incomplete = sorted.filter { it.stage in UNFINISHED || (it.stage == "COMPLETE" && it.repeatVl == null) }
        if (incomplete.isNotEmpty()) {
            val emphasize = unsuppressed && !isPostEacVl
            warnings += EacWarning(
                incomplete.first().episodeUuid, incomplete.first().stage,
                "This client has incomplete EAC review" +
                    if (emphasize) " — recent unsuppressed non-Post-EAC VL; restart/complete EAC before the next VL."
                    else " — ${incomplete.size} episode(s) without completion or a Post-EAC VL.",
                if (emphasize) VlGapSeverity.HIGH else VlGapSeverity.MODERATE,
            )
        }
        return EacAssessment(gaps, warnings, current)
    }
}
