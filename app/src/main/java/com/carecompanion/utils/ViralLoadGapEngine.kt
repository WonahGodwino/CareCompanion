package com.carecompanion.utils

import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Detects VL service & quality gaps from a patient's categorised VL history (see WINCO vl_category /
 * VIRAL_LOAD_INDICATION). Complements [ViralLoadEligibilityEngine] (which says *when* a VL is due) by
 * checking what the cascade actually shows:
 *
 *   - UNSUPPRESSED_NEEDS_EAC_POST_VL : latest VL >= 1000 and no later Post-EAC confirmation VL →
 *     the client needs Enhanced Adherence Counselling + a repeat (Post-EAC) VL.
 *   - ROUTINE_VL_OVERDUE             : latest VL suppressed but older than the routine interval.
 *   - PMTCT_VL_DUE                   : pregnant at 32–36 weeks with no PMTCT VL in this pregnancy.
 *
 * Pure/testable: feed it [VlRecord]s mapped from ViralLoadHistory (category + numeric result + date).
 *
 * SCOPE: these gaps are raised ONLY for clients who are currently on treatment (TX_CURR). A client who
 * is already IIT or any TX_ML category (IIT / death / transfer-out / stopped) is not in active care, so
 * a VL flag is inappropriate — they need return-to-care or outcome ascertainment first. Callers must
 * pass the live TX_CURR status (same coverage + on-ART + alive rule as PatientDao.observeTxCurrCount).
 */
enum class VlGapType { UNSUPPRESSED_NEEDS_EAC_POST_VL, ROUTINE_VL_OVERDUE, PMTCT_VL_DUE }

enum class VlGapSeverity { CRITICAL, HIGH, MODERATE }

data class VlRecord(
    val category: String?,       // "Baseline" | "Routine" | "Post-EAC" | "PMTCT" | "Targeted/Failure" | ...
    val resultNumeric: Long?,    // copies/mL; null if pending/non-numeric
    val date: Date?,             // result/assay/sample date
)

data class VlGap(
    val type: VlGapType,
    val severity: VlGapSeverity,
    val message: String,
    val sinceDate: Date?,
)

object ViralLoadGapEngine {
    const val UNSUPPRESSED_THRESHOLD = 1000L
    private const val ADULT_ROUTINE_INTERVAL_DAYS = 365
    private const val CHILD_ROUTINE_INTERVAL_DAYS = 180
    private const val POST_EAC = "Post-EAC"
    private const val PMTCT = "PMTCT"

    fun detectGaps(
        history: List<VlRecord>,
        ageYears: Int,
        isTxCurr: Boolean,
        isPregnant: Boolean = false,
        gestationWeeks: Int? = null,
        today: Date = Date(),
    ): List<VlGap> {
        // VL service/quality gaps are actionable only for active (TX_CURR) clients. IIT / TX_ML clients
        // need return-to-care / outcome ascertainment first — never raise a VL flag for them.
        if (!isTxCurr) return emptyList()

        val gaps = mutableListOf<VlGap>()
        val dated = history.filter { it.date != null }.sortedByDescending { it.date!!.time }
        val latestResult = dated.firstOrNull { it.resultNumeric != null }

        if (latestResult?.resultNumeric != null) {
            if (latestResult.resultNumeric >= UNSUPPRESSED_THRESHOLD) {
                // Unsuppressed and no later suppressed Post-EAC repeat (that would BE the latest result).
                // → EAC + Post-EAC confirmation VL outstanding. Critical (suspected treatment failure path).
                gaps += VlGap(
                    VlGapType.UNSUPPRESSED_NEEDS_EAC_POST_VL, VlGapSeverity.CRITICAL,
                    "Unsuppressed VL (${latestResult.resultNumeric} c/mL). Start Enhanced Adherence " +
                        "Counselling (3 sessions) and repeat (Post-EAC) VL.",
                    latestResult.date,
                )
            } else {
                // Suppressed → routine cadence. Overdue if older than the age-appropriate interval.
                val interval = if (ageYears in 0..14) CHILD_ROUTINE_INTERVAL_DAYS else ADULT_ROUTINE_INTERVAL_DAYS
                val ageDays = daysBetween(latestResult.date!!, today)
                if (ageDays > interval) {
                    gaps += VlGap(
                        VlGapType.ROUTINE_VL_OVERDUE, VlGapSeverity.MODERATE,
                        "Routine VL overdue — last suppressed result is $ageDays day(s) old " +
                            "(interval ${interval}d).",
                        latestResult.date,
                    )
                }
            }
        }

        if (isPregnant && gestationWeeks != null && gestationWeeks in 32..36) {
            val hasPmtctVl = history.any { (it.category ?: "").equals(PMTCT, ignoreCase = true) }
            if (!hasPmtctVl) {
                gaps += VlGap(
                    VlGapType.PMTCT_VL_DUE, VlGapSeverity.HIGH,
                    "Pregnant at $gestationWeeks weeks — PMTCT VL (32–36 weeks) not yet done.",
                    null,
                )
            }
        }
        return gaps
    }

    /** True when an active (TX_CURR) client's most recent VL is unsuppressed with no later Post-EAC repeat. */
    fun needsPostEacVl(history: List<VlRecord>, isTxCurr: Boolean): Boolean =
        detectGaps(history, ageYears = 30, isTxCurr = isTxCurr)
            .any { it.type == VlGapType.UNSUPPRESSED_NEEDS_EAC_POST_VL }

    private fun daysBetween(from: Date, to: Date): Int =
        TimeUnit.MILLISECONDS.toDays(to.time - from.time).toInt()
}
