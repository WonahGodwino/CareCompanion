package com.carecompanion.utils

import com.carecompanion.data.database.entities.ArtPharmacy
import com.carecompanion.data.database.entities.IITClient
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * On-device, explainable patient-monitoring risk engine.
 *
 * Runs entirely on the handset — no patient PHI ever leaves the device, which is a
 * hard requirement for HIV data under NACA / PEPFAR data-protection rules and keeps
 * the feature usable in low-connectivity facilities.
 *
 * IIT definition (PEPFAR/NACA): a client becomes Interrupted-in-Treatment on the
 * **29th day** after a missed scheduled pickup (i.e. once they are >28 days late).
 * The most valuable thing this engine does is **early detection** — flagging clients
 * in the 1–27 day window who are *trending* toward IIT, before they cross day 28,
 * using their appointment history and adherence pattern.
 *
 * Design goals:
 *  - **Explainable**: every score is a sum of named, weighted factors so a clinician
 *    can see *why* a client is flagged.
 *  - **Extensible**: scoring is split into independent clinical *dimensions*
 *    (adherence, virologic/EAC, TB/TPT, biometric, PMTCT…). Each dimension reads from
 *    [PatientSignals]; any field that isn't synced yet is simply null and contributes
 *    nothing. As TB, EAC and other data streams come online, populate the relevant
 *    [PatientSignals] fields — no change to the UI or the scoring contract is needed.
 *  - **Swappable**: the rule-based weights below can be replaced by a trained model
 *    behind the same [PatientRiskScore] output without touching callers.
 */
object PatientRiskEngine {

    /** PEPFAR/NACA grace = 28 days; with the strict '>' checks below, a client is IIT on the 29th day. */
    const val IIT_THRESHOLD_DAYS = 28

    /** Adherence-risk band. Drives colour + triage priority in the UI. */
    enum class RiskBand(val label: String, val colorHex: Long) {
        CRITICAL("Critical", 0xFFB71C1C),
        HIGH    ("High",     0xFFE65100),
        MODERATE("Moderate", 0xFFF9A825),
        LOW     ("Low",      0xFF2E7D32),
    }

    /** Clinical domains a factor can come from — lets the UI group/filter the "why". */
    enum class RiskDomain(val label: String) {
        ADHERENCE("Adherence"),
        HISTORY("Pattern / History"),
        ACCESS("Access / Distance"),
        VIROLOGIC("Viral Load / EAC"),
        TB("TB / TPT"),
        BIOMETRIC("Identity"),
        PMTCT("PMTCT"),
        EID("Exposed Infant"),
        DEMOGRAPHIC("Demographic"),
    }

    /** A single named contribution to the risk score — shown to the clinician as a chip. */
    data class RiskFactor(val domain: RiskDomain, val label: String, val points: Int)

    /**
     * Appointment-history summary mined from a client's ART-pharmacy pickup records.
     * Captures the adherence *pattern* — the single strongest predictor of recurrence.
     */
    data class AdherenceHistory(
        val totalVisits: Int = 0,
        val priorLatePickups: Int = 0,   // returned after the scheduled nextAppointment
        val priorIitEpisodes: Int = 0,   // returned >28 days late (a past IIT-and-recovery)
        val selfReportedMisses: Int = 0, // pharmacy records flagged adherence = false
    )

    /**
     * Superset of monitoring signals. Most fields are nullable on purpose: the engine
     * degrades gracefully when a data stream is not yet available for a client.
     * Populate more of these as TB screening, EAC sessions, VL results, etc. sync in.
     */
    data class PatientSignals(
        val uuid: String,
        val displayName: String,
        val hospitalNumber: String,
        val initial: Char,
        val dateOfBirth: Date? = null,
        // ── Adherence ──────────────────────────────────────────────
        val daysOverdue: Int = 0,                 // days past scheduled ART pickup (0 = not late yet)
        val daysUntilAppointment: Int? = null,    // >=0 = appointment still upcoming (forecast mode)
        val refillPeriodDays: Int? = null,
        val dsdModel: String? = null,
        val history: AdherenceHistory = AdherenceHistory(),
        // ── Access / geolocation ───────────────────────────────────
        val distanceToFacilityKm: Double? = null, // straight-line distance client → facility
        val hasPhoneContact: Boolean? = null,     // reachable by phone for reminders/tracing
        // ── Virologic / EAC ────────────────────────────────────────
        val lastViralLoadResult: Long? = null,    // copies/mL; >=1000 = unsuppressed
        val lastViralLoadDate: Date? = null,
        val viralLoadOverdue: Boolean = false,    // routine/baseline VL is due/overdue
        val eacSessionsCompleted: Int? = null,    // of 3, after an unsuppressed result
        val eacStage: String? = null,             // NOT_STARTED | IN_PROGRESS | COMPLETE | STOPPED
        // ── TB / TPT ───────────────────────────────────────────────
        val tbScreenOverdue: Boolean = false,
        val tbSymptomatic: Boolean? = null,       // presumptive / positive screen
        val onTpt: Boolean? = null,               // currently receiving TB preventive therapy
        val tptEligibleNotStarted: Boolean = false,
        // ── Identity / biometric ───────────────────────────────────
        val biometricEnrolled: Boolean? = null,
        // ── PMTCT (mother) ─────────────────────────────────────────
        val pregnantOrBreastfeeding: Boolean? = null,
        val pmtctVlGap: String? = null,           // PMTCT_VL_DUE | PMTCT_VL_OVERDUE (32–36wk, code 306)
        val pmtctGaWeeks: Int? = null,
        val fetalHighRisk: Boolean = false,       // baby will be high-risk → enhanced prophylaxis at birth
        // ── EID (this client's HIV-exposed infant) ─────────────────
        val exposedInfantGap: String? = null,     // top infant cascade gap (e.g. EID_PCR_DUE, INFANT_HIV_POSITIVE)
        val exposedInfantHighRisk: Boolean = false,
        val exposedInfantCount: Int = 0,
    )

    data class PatientRiskScore(
        val uuid: String,
        val displayName: String,
        val hospitalNumber: String,
        val initial: Char,
        val score: Int,                 // 0–100
        val band: RiskBand,
        val factors: List<RiskFactor>,  // explainability — why this score
        val recommendedAction: String,  // next best action for the clinician
        val daysOverdue: Int,
        val isApproachingIit: Boolean,  // 1–27 days late — predicted to become IIT if no action
        val daysUntilIit: Int,          // countdown to day-28 threshold (0 once IIT)
        val isForecast: Boolean,        // appointment still upcoming — pre-emptive prediction
        val daysUntilAppointment: Int,  // days until the upcoming appointment (0 if not forecast)
    )

    /**
     * Mine a client's pharmacy-pickup history into an [AdherenceHistory] pattern.
     * Compares each visit's scheduled [ArtPharmacy.nextAppointment] against when the
     * client actually returned (the next visit's date).
     */
    fun analyzeHistory(records: List<ArtPharmacy>): AdherenceHistory {
        if (records.size < 2) {
            return AdherenceHistory(
                totalVisits = records.size,
                selfReportedMisses = records.count { it.adherence == false },
            )
        }
        val byDate = records.sortedBy { it.visitDate }
        var late = 0
        var iit = 0
        for (i in 0 until byDate.size - 1) {
            val scheduled = byDate[i].nextAppointment ?: continue
            val actualReturn = byDate[i + 1].visitDate
            val daysLate = TimeUnit.MILLISECONDS.toDays(actualReturn.time - scheduled.time)
            if (daysLate > 0) late++
            if (daysLate > IIT_THRESHOLD_DAYS) iit++
        }
        return AdherenceHistory(
            totalVisits = records.size,
            priorLatePickups = late,
            priorIitEpisodes = iit,
            selfReportedMisses = records.count { it.adherence == false },
        )
    }

    /** Map an additive 0–100 risk total to a triage band. */
    fun bandForScore(total: Int): RiskBand = when {
        total >= 75 -> RiskBand.CRITICAL
        total >= 50 -> RiskBand.HIGH
        total >= 30 -> RiskBand.MODERATE
        else        -> RiskBand.LOW
    }

    /** Score any patient from the full signal set. Dimensions are independent and additive. */
    fun score(s: PatientSignals): PatientRiskScore {
        val factors = mutableListOf<RiskFactor>()
        val age = DateUtils.calculateAge(s.dateOfBirth)

        scoreAdherence(s, age, factors)
        scoreHistory(s, factors)
        scoreAccess(s, factors)
        scoreVirologic(s, factors)
        scoreTb(s, factors)
        scoreIdentity(s, factors)
        scorePmtct(s, factors)
        scoreInfant(s, factors)

        val total = factors.sumOf { it.points }.coerceIn(0, 100)
        val band = bandForScore(total)
        val approaching = s.daysOverdue in 1..IIT_THRESHOLD_DAYS
        val daysUntilIit = (IIT_THRESHOLD_DAYS + 1 - s.daysOverdue).coerceAtLeast(0)
        val forecast = s.daysOverdue == 0 && (s.daysUntilAppointment ?: -1) >= 0

        return PatientRiskScore(
            uuid = s.uuid,
            displayName = s.displayName,
            hospitalNumber = s.hospitalNumber,
            initial = s.initial,
            score = total,
            band = band,
            factors = factors.sortedByDescending { it.points },
            recommendedAction = recommendedAction(s, age, factors, approaching, daysUntilIit, forecast),
            daysOverdue = s.daysOverdue,
            isApproachingIit = approaching,
            daysUntilIit = if (approaching) daysUntilIit else 0,
            isForecast = forecast,
            daysUntilAppointment = if (forecast) (s.daysUntilAppointment ?: 0) else 0,
        )
    }

    /** Convenience adapter — supply the defaulter row plus its mined history. */
    fun score(client: IITClient, history: AdherenceHistory = AdherenceHistory()): PatientRiskScore = score(
        PatientSignals(
            uuid = client.uuid,
            displayName = client.displayName,
            hospitalNumber = client.hospitalNumber,
            initial = client.initial,
            dateOfBirth = client.dateOfBirth,
            daysOverdue = client.daysOverdue,
            refillPeriodDays = client.refillPeriod,
            dsdModel = client.dsdModel,
            history = history,
        )
    )

    // ── Dimension scorers ───────────────────────────────────────────────────────

    private fun scoreAdherence(s: PatientSignals, age: Int, out: MutableList<RiskFactor>) {
        val od = s.daysOverdue
        when {
            od > 180 ->
                out += RiskFactor(RiskDomain.ADHERENCE, "Lost to follow-up (180+ days late)", 80)
            od > 90 ->
                out += RiskFactor(RiskDomain.ADHERENCE, "Interrupted 90–179 days", 55)
            od > IIT_THRESHOLD_DAYS ->
                out += RiskFactor(RiskDomain.ADHERENCE, "IIT — interrupted 28–89 days", 30)
            od >= 1 -> {
                // Pre-IIT window: the early-detection case. Urgency climbs as day 28 nears.
                val daysLeft = (IIT_THRESHOLD_DAYS + 1 - od).coerceAtLeast(0)
                val ramp = ((IIT_THRESHOLD_DAYS - daysLeft) / 2).coerceIn(0, 14)
                out += RiskFactor(RiskDomain.ADHERENCE, "Missed pickup — $daysLeft days to IIT", 16 + ramp)
            }
        }

        // Continuous-gap escalator — capped so it never dominates the main signal.
        val gapPoints = (od / 7).coerceAtMost(15)
        if (gapPoints > 0) out += RiskFactor(RiskDomain.ADHERENCE, "$od days since scheduled pickup", gapPoints)

        if (age in 0..14) out += RiskFactor(RiskDomain.DEMOGRAPHIC, "Paediatric client (<15y)", 10)

        val refill = s.refillPeriodDays ?: 0
        if (refill >= 90) out += RiskFactor(RiskDomain.ADHERENCE, "Multi-month dispensing (${refill}d) — gap undetected longer", 6)

        val dsd = s.dsdModel?.trim()?.lowercase().orEmpty()
        if (dsd.contains("community") || dsd.contains("devolved") || dsd.contains("cpp")) {
            out += RiskFactor(RiskDomain.ADHERENCE, "Community DSD model — tracing harder", 5)
        }
    }

    /** Appointment-history pattern — the predictive core for early IIT detection. */
    private fun scoreHistory(s: PatientSignals, out: MutableList<RiskFactor>) {
        val h = s.history
        if (h.priorIitEpisodes > 0) {
            // A client who has interrupted before is far more likely to do so again.
            out += RiskFactor(
                RiskDomain.HISTORY,
                "Prior IIT episode${if (h.priorIitEpisodes > 1) "s" else ""} (${h.priorIitEpisodes}) — high recurrence risk",
                (h.priorIitEpisodes * 9).coerceAtMost(22)
            )
        }
        if (h.priorLatePickups > 0) {
            out += RiskFactor(
                RiskDomain.HISTORY,
                "Chronic late pickups (${h.priorLatePickups} of ${h.totalVisits} visits)",
                (h.priorLatePickups * 3).coerceAtMost(12)
            )
        }
        if (h.selfReportedMisses > 0) {
            out += RiskFactor(RiskDomain.HISTORY, "Self-reported missed doses (${h.selfReportedMisses})", 6)
        }
    }

    /** Access / geolocation — travel burden and reachability for reminders & tracing. */
    private fun scoreAccess(s: PatientSignals, out: MutableList<RiskFactor>) {
        when (val km = s.distanceToFacilityKm) {
            null -> { /* facility coordinates not synced yet — contributes nothing */ }
            else -> {
                val rounded = km.toInt()
                when {
                    km > 25 -> out += RiskFactor(RiskDomain.ACCESS, "Lives ~${rounded}km from facility — high travel burden", 10)
                    km > 10 -> out += RiskFactor(RiskDomain.ACCESS, "Lives ~${rounded}km from facility — travel burden", 6)
                    km > 5  -> out += RiskFactor(RiskDomain.ACCESS, "Lives ~${rounded}km from facility", 3)
                }
            }
        }
        if (s.hasPhoneContact == false) {
            out += RiskFactor(RiskDomain.ACCESS, "No phone contact — cannot remind or trace remotely", 6)
        }
    }

    private fun scoreVirologic(s: PatientSignals, out: MutableList<RiskFactor>) {
        val vl = s.lastViralLoadResult
        if (vl != null && vl >= 1000) {
            out += RiskFactor(RiskDomain.VIROLOGIC, "Unsuppressed viral load ($vl c/mL)", 25)
            // EAC: 3 sessions are required after an unsuppressed result before VL recheck.
            when {
                s.eacStage == "STOPPED" ->
                    out += RiskFactor(RiskDomain.VIROLOGIC, "EAC stopped before completion while unsuppressed", 14)
                s.eacStage == "COMPLETE" || (s.eacSessionsCompleted ?: 0) >= 3 ->
                    { /* EAC complete — on the Post-EAC VL recheck pathway, no extra penalty */ }
                (s.eacSessionsCompleted ?: 0) in 1..2 ->
                    out += RiskFactor(RiskDomain.VIROLOGIC, "EAC incomplete (${s.eacSessionsCompleted} of 3 sessions)", 8)
                else ->
                    out += RiskFactor(RiskDomain.VIROLOGIC, "EAC not started after unsuppressed VL", 15)
            }
        }
        if (s.viralLoadOverdue) out += RiskFactor(RiskDomain.VIROLOGIC, "Routine viral load overdue", 10)
    }

    private fun scoreTb(s: PatientSignals, out: MutableList<RiskFactor>) {
        if (s.tbSymptomatic == true) out += RiskFactor(RiskDomain.TB, "Presumptive / positive TB screen", 20)
        if (s.tbScreenOverdue) out += RiskFactor(RiskDomain.TB, "TB screening overdue", 8)
        if (s.tptEligibleNotStarted) out += RiskFactor(RiskDomain.TB, "TPT-eligible, not initiated", 6)
    }

    private fun scoreIdentity(s: PatientSignals, out: MutableList<RiskFactor>) {
        if (s.biometricEnrolled == false) {
            out += RiskFactor(RiskDomain.BIOMETRIC, "No biometric on file — identity unverified", 5)
        }
    }

    private fun scorePmtct(s: PatientSignals, out: MutableList<RiskFactor>) {
        val gaTxt = s.pmtctGaWeeks?.let { " (GA ${it}wk)" } ?: ""
        when (s.pmtctVlGap) {
            "PMTCT_VL_OVERDUE" -> out += RiskFactor(RiskDomain.PMTCT, "PMTCT VL overdue$gaTxt — MTCT risk", 22)
            "PMTCT_VL_DUE"     -> out += RiskFactor(RiskDomain.PMTCT, "PMTCT VL due$gaTxt (32–36wk window)", 15)
        }
        if (s.fetalHighRisk) {
            out += RiskFactor(RiskDomain.PMTCT, "Fetus high-risk — prepare enhanced infant prophylaxis at birth", 12)
        }
        // Fallback when the PMTCT cascade isn't synced but the client is flagged pregnant + at risk.
        if (s.pregnantOrBreastfeeding == true && s.pmtctVlGap == null && !s.fetalHighRisk &&
            (s.daysOverdue > 0 || (s.lastViralLoadResult ?: 0) >= 1000)) {
            out += RiskFactor(RiskDomain.PMTCT, "Pregnant/breastfeeding with adherence or VL risk", 15)
        }
    }

    /** EID — this client's HIV-exposed infant (action falls on the mother: bring the infant in). */
    private fun scoreInfant(s: PatientSignals, out: MutableList<RiskFactor>) {
        when (s.exposedInfantGap) {
            "INFANT_HIV_POSITIVE" -> out += RiskFactor(RiskDomain.EID, "Exposed infant PCR POSITIVE — initiate paediatric ART", 25)
            "INFANT_ARV_MISSING"  -> out += RiskFactor(RiskDomain.EID, "Exposed infant: no ARV prophylaxis recorded", 18)
            "EID_PCR_DUE"         -> out += RiskFactor(RiskDomain.EID, "Exposed infant: EID PCR due/overdue", 16)
            "PCR_RESULT_PENDING"  -> out += RiskFactor(RiskDomain.EID, "Exposed infant: EID PCR result pending", 8)
            "CTX_NOT_STARTED"     -> out += RiskFactor(RiskDomain.EID, "Exposed infant: cotrimoxazole not started", 8)
            "FINAL_ANTIBODY_DUE"  -> out += RiskFactor(RiskDomain.EID, "Exposed infant: 18-month confirmatory test due", 8)
        }
        if (s.exposedInfantHighRisk && s.exposedInfantGap == null && s.exposedInfantCount > 0) {
            out += RiskFactor(RiskDomain.EID, "High-risk exposed infant in follow-up", 6)
        }
    }

    // ── Recommended next action ──────────────────────────────────────────────────

    private fun recommendedAction(
        s: PatientSignals,
        age: Int,
        factors: List<RiskFactor>,
        approaching: Boolean,
        daysUntilIit: Int,
        forecast: Boolean,
    ): String {
        // Surface the most clinically urgent action first.
        if (s.exposedInfantGap == "INFANT_HIV_POSITIVE")
            return "Exposed infant PCR POSITIVE — initiate paediatric ART and confirm; counsel the mother today."
        if (factors.any { it.domain == RiskDomain.TB && it.label.startsWith("Presumptive") })
            return "Refer for TB evaluation (GeneXpert) today before routine ART services."
        if (s.pmtctVlGap == "PMTCT_VL_OVERDUE")
            return "PMTCT VL overdue (GA ${s.pmtctGaWeeks ?: "?"}wk): collect the maternal VL now to assess MTCT risk."
        if (s.exposedInfantGap == "EID_PCR_DUE" || s.exposedInfantGap == "INFANT_ARV_MISSING")
            return "Recall the mother to bring the exposed infant for ARV prophylaxis / EID DNA-PCR."
        if (s.pmtctVlGap == "PMTCT_VL_DUE")
            return "PMTCT priority: collect the 32–36 week maternal VL; confirm ART continuation."
        if (s.pregnantOrBreastfeeding == true)
            return "PMTCT priority: trace today, confirm ART continuation and infant prophylaxis."
        if ((s.lastViralLoadResult ?: 0) >= 1000)
            return "Unsuppressed VL: book Enhanced Adherence Counselling and repeat VL after 3 sessions."

        // Pre-IIT — the preventive window.
        if (approaching) {
            val repeat = if (s.history.priorIitEpisodes > 0)
                " Known defaulter pattern — assign a treatment supporter and prioritise outreach." else ""
            val ped = if (age in 0..14) " Reach the caregiver." else ""
            return "Call & SMS today — $daysUntilIit day(s) to IIT. Confirm pickup before the 28-day threshold.$repeat$ped"
        }

        // Forecast — before any missed appointment.
        if (forecast) {
            val transport = if ((s.distanceToFacilityKm ?: 0.0) > 10) " Offer transport support or a closer DSD pickup point." else ""
            val repeat = if (s.history.priorIitEpisodes > 0 || s.history.priorLatePickups >= 2)
                " History of missed pickups — proactively confirm attendance." else ""
            return "Appointment in ${s.daysUntilAppointment ?: 0} day(s): send a reminder now to prevent a miss.$repeat$transport"
        }

        val od = s.daysOverdue
        return when {
            od > 180 -> "Assign for home visit / community tracing; confirm outcome (RTT, transfer-out, or deceased)."
            od > 90  -> "Phone-trace within 48h and activate the treatment supporter; book an urgent refill."
            od > IIT_THRESHOLD_DAYS -> {
                val ped = if (age in 0..14) " Involve the caregiver." else ""
                "Already IIT — same-day phone trace and book pharmacy refill to return to treatment.$ped"
            }
            else -> "Continue routine monitoring; reinforce adherence at next visit."
        }
    }
}
