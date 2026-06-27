package com.carecompanion.data.reminder

import com.carecompanion.data.risk.AssessedClient
import com.carecompanion.data.risk.RiskAssessment
import com.carecompanion.utils.PatientRiskEngine.RiskBand
import java.util.concurrent.TimeUnit

/** Which clients the AI is allowed to auto-remind — a clinic-configurable choice. */
enum class ReminderAudience(val label: String, val description: String) {
    GENERAL("All upcoming", "Everyone with an upcoming appointment — plus all flagged clients."),
    AT_RISK("At-risk only", "Only clients the AI has flagged (Moderate risk and above).");

    companion object {
        fun fromName(name: String): ReminderAudience =
            entries.firstOrNull { it.name == name } ?: GENERAL
    }
}

/**
 * The AI's reminder cadence policy. There is intentionally NO manual schedule to
 * configure — the engine decides, per client, when a reminder is due, from the
 * client's risk band and the proximity of their appointment.
 *
 * Pre-appointment (the key behaviour): higher-risk clients are reminded earlier and
 * more often, so a client predicted to default hears from the clinic well before the
 * appointment, while a reliable client gets a single courtesy reminder the day before.
 * Post-appointment: an active 1–27 day lapse is chased daily inside the window that
 * decides IIT; an established interruption is nudged weekly while physical tracing runs.
 */
object ReminderScheduler {

    private val WAT_OFFSET_MS = TimeUnit.HOURS.toMillis(1) // Africa/Lagos = UTC+1

    /** Monotonic day index in WAT, for gap arithmetic and de-duplication. */
    fun todayEpochDay(nowMs: Long = System.currentTimeMillis()): Int =
        ((nowMs + WAT_OFFSET_MS) / TimeUnit.DAYS.toMillis(1)).toInt()

    /** The candidate population for auto-reminders, per the clinic's audience choice. */
    fun candidates(assessment: RiskAssessment, audience: ReminderAudience): List<AssessedClient> =
        when (audience) {
            ReminderAudience.AT_RISK -> assessment.flagged
            ReminderAudience.GENERAL -> assessment.all
        }

    /**
     * Days-before-appointment on which a pre-appointment reminder should fire, by band.
     * Day 0 = appointment day itself.
     */
    fun preAppointmentOffsets(band: RiskBand): List<Int> = when (band) {
        RiskBand.CRITICAL -> listOf(14, 7, 3, 1, 0)
        RiskBand.HIGH     -> listOf(7, 3, 1)
        RiskBand.MODERATE -> listOf(3, 1)
        RiskBand.LOW      -> listOf(1)
    }

    /**
     * Should this client receive a reminder today? Encapsulates the whole policy:
     * pre-appointment cadence, in-window daily chase, and weekly post-IIT nudge,
     * with once-per-day de-duplication via [lastSentEpochDay].
     */
    fun isDueToday(client: AssessedClient, lastSentEpochDay: Int, todayEpochDay: Int): Boolean {
        if (lastSentEpochDay == todayEpochDay) return false // already reminded today
        val s = client.score
        val gap = todayEpochDay - lastSentEpochDay

        return when {
            // Pre-appointment: fire only on an AI-chosen offset day for this band.
            s.isForecast -> s.daysUntilAppointment in preAppointmentOffsets(s.band)
            // Approaching IIT (1–27 days late): chase daily through the decision window.
            s.isApproachingIit -> true
            // Established IIT (>28 days): gentle weekly nudge while tracing proceeds.
            else -> gap >= 7
        }
    }
}
