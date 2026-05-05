package com.carecompanion.data.database.entities

import androidx.room.ColumnInfo
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * POJO (not a Room @Entity) — populated by the IIT JOIN query in ArtPharmacyDao.
 *
 * IIT (Interruption in Treatment) — PEPFAR definition:
 * A patient on ART who has not returned for a scheduled pickup for ≥ 28 consecutive days.
 *
 * This combines patient demographics with their most recent ART pharmacy record so
 * the IIT screen can show name, hospital number, days overdue, last visit date and
 * missed appointment date in a single query.
 */
data class IITClient(
    @ColumnInfo(name = "patientId")       val patientId: String,
    @ColumnInfo(name = "uuid")            val uuid: String,
    @ColumnInfo(name = "hospitalNumber")  val hospitalNumber: String,
    @ColumnInfo(name = "firstName")       val firstName: String?,
    @ColumnInfo(name = "surname")         val surname: String?,
    @ColumnInfo(name = "fullName")        val fullName: String?,
    @ColumnInfo(name = "sex")             val sex: String?,
    @ColumnInfo(name = "dateOfBirth")     val dateOfBirth: Date?,
    @ColumnInfo(name = "facilityId")      val facilityId: Long,
    @ColumnInfo(name = "currentStatus")   val currentStatus: String?,
    @ColumnInfo(name = "currentStatusDate") val currentStatusDate: Date?,
    @ColumnInfo(name = "lastVisitDate")   val lastVisitDate: Date?,
    @ColumnInfo(name = "nextAppointment") val nextAppointment: Date?,
    @ColumnInfo(name = "dsdModel")        val dsdModel: String?,
    @ColumnInfo(name = "refillPeriod")    val refillPeriod: Int?
) {
    /** Display name — falls back gracefully if individual fields are missing. */
    val displayName: String
        get() = fullName?.takeIf { it.isNotBlank() }
            ?: listOfNotNull(surname?.trim(), firstName?.trim())
                .filter { it.isNotEmpty() }
                .joinToString(", ")
                .takeIf { it.isNotBlank() }
            // hospitalNumber (PEPFAR unique_id) is always populated from WINCO — use it
            // as the final fallback so the IIT list never shows a blank "Unknown" row.
            ?: hospitalNumber.takeIf { it.isNotBlank() }
            ?: "Unknown"

    /** Initial letter for the avatar circle. */
    val initial: Char
        get() = (surname?.firstOrNull() ?: firstName?.firstOrNull() ?: '?').uppercaseChar()

    /**
     * Total days since the scheduled return date (nextAppointment).
     * PEPFAR IIT threshold: ≥ 28 days. Patients are only in this list when
     * nextAppointment + 28 days has already passed (enforced at SQL layer).
     * Returns 0 if nextAppointment is null.
     */
    val daysOverdue: Int
        get() = nextAppointment?.let { appt ->
            TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - appt.time).toInt().coerceAtLeast(0)
        } ?: 0

    /**
     * The exact date this client crossed into IIT status.
     * = nextAppointment + 28-day grace period (PEPFAR/NACA definition).
     */
    val iitEntryDate: Date?
        get() = nextAppointment?.let { appt ->
            Date(appt.time + TimeUnit.DAYS.toMillis(28))
        }

    private fun isIitLikeStatus(): Boolean {
        val status = currentStatus?.trim()?.uppercase()?.replace(" ", "_")?.replace("-", "_") ?: return false
        return status in setOf(
            "IIT",
            "INTERRUPTED_IN_TREATMENT",
            "LTFU",
            "LOST_TO_FOLLOW_UP",
            "TRANSFER_OUT",
            "ART_TRANSFER_OUT",
            "DEATH",
            "DIED",
            "DEAD",
            "STOPPED_TREATMENT",
            "TREATMENT_STOPPED",
            "ART_STOP"
        )
    }

    /**
     * Prefer tracked status_date for period reporting when status is already IIT-like.
     * Fall back to established pharmacy-derived IIT date otherwise.
     */
    val effectiveIitEntryDate: Date?
        get() = if (isIitLikeStatus() && currentStatusDate != null) currentStatusDate else iitEntryDate

    /** Reporting period bucket this client falls into. */
    fun iitPeriod(): IITPeriod = IITPeriod.of(effectiveIitEntryDate)

    /**
     * PEPFAR TX_ML IIT classification tier (based on total days since nextAppointment).
     *   < 28 days   → should not appear in IIT list (filtered at query level)
     *  28–89 days   → IIT < 3 Months
     *  90–179 days  → IIT 3–5 Months
     *  ≥ 180 days   → IIT 6+ Months (LTFU)
     */
    val iitTier: IITTier
        get() = when {
            daysOverdue >= 180 -> IITTier.LTFU
            daysOverdue >= 90  -> IITTier.IIT
            else               -> IITTier.EARLY_IIT
        }
}

enum class IITTier(val label: String, val shortLabel: String) {
    EARLY_IIT("IIT < 3 Months",      "28–89d"),
    IIT      ("IIT 3–5 Months",      "90–179d"),
    LTFU     ("IIT 6+ Months",       "180d+")
}

/**
 * Time-period bucket for IIT reporting — based on when the client crossed the
 * 28-day IIT threshold (nextAppointment + 28 days).
 * Buckets are EXCLUSIVE, evaluated from most-recent first.
 *
 * PEPFAR Financial Year: October 1 – September 30.
 */
enum class IITPeriod(val label: String) {
    TODAY("Today"),
    THIS_WEEK("This Week"),
    LAST_WEEK("Last Week"),
    THIS_MONTH("This Month"),
    THIS_FY("This FY"),
    PREVIOUS("Previous");

    companion object {
        private val watTimeZone: TimeZone = TimeZone.getTimeZone("Africa/Lagos")

        fun of(entryDate: Date?): IITPeriod {
            entryDate ?: return PREVIOUS

            fun Calendar.clearTime() = apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
            }

            val today = Calendar.getInstance(watTimeZone).clearTime()
            val entry = Calendar.getInstance(watTimeZone).also { it.time = entryDate }.clearTime()

            // Today
            if (entry.timeInMillis == today.timeInMillis) return TODAY

            // Monday of current week (ISO: Mon=start)
            val dow = today.get(Calendar.DAY_OF_WEEK) // Sun=1, Mon=2 … Sat=7
            val daysSinceMon = if (dow == Calendar.SUNDAY) 6 else dow - Calendar.MONDAY
            val weekMon = (today.clone() as Calendar).also { it.add(Calendar.DAY_OF_YEAR, -daysSinceMon) }
            val weekSun = (weekMon.clone() as Calendar).also { it.add(Calendar.DAY_OF_YEAR, 6) }
            if (!entry.before(weekMon) && !entry.after(weekSun)) return THIS_WEEK

            val lastWeekMon = (weekMon.clone() as Calendar).also { it.add(Calendar.DAY_OF_YEAR, -7) }
            val lastWeekSun = (weekMon.clone() as Calendar).also { it.add(Calendar.DAY_OF_YEAR, -1) }
            if (!entry.before(lastWeekMon) && !entry.after(lastWeekSun)) return LAST_WEEK

            val monthStart = (today.clone() as Calendar).also { it.set(Calendar.DAY_OF_MONTH, 1) }
            val nextMonthStart = (monthStart.clone() as Calendar).also { it.add(Calendar.MONTH, 1) }
            if (!entry.before(monthStart) && entry.before(nextMonthStart)) return THIS_MONTH

            // PEPFAR FY: Oct 1 – Sep 30
            val fyYear = if (today.get(Calendar.MONTH) >= Calendar.OCTOBER)
                today.get(Calendar.YEAR) else today.get(Calendar.YEAR) - 1
            val fyStart = Calendar.getInstance(watTimeZone).clearTime()
                .also { it.set(fyYear, Calendar.OCTOBER, 1) }
            val nextFYStart = (fyStart.clone() as Calendar).also { it.add(Calendar.YEAR, 1) }
            if (!entry.before(fyStart) && entry.before(nextFYStart)) return THIS_FY

            return PREVIOUS
        }
    }
}
