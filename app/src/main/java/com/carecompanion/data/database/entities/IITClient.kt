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
            ?: "Unknown"

    /** Initial letter for the avatar circle. */
    val initial: Char
        get() = (surname?.firstOrNull() ?: firstName?.firstOrNull() ?: '?').uppercaseChar()

    /**
     * Days since the missed appointment (positive = overdue).
     * Returns 0 if nextAppointment is null.
     */
    val daysOverdue: Int
        get() = nextAppointment?.let {
            TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - it.time).toInt().coerceAtLeast(0)
        } ?: 0

    /**
     * The exact date this client crossed into IIT status.
     * = nextAppointment + 28 days.
     */
    val iitEntryDate: Date?
        get() = nextAppointment?.let { Date(it.time + TimeUnit.DAYS.toMillis(28)) }

    /** Reporting period bucket this client falls into. */
    fun iitPeriod(): IITPeriod = IITPeriod.of(iitEntryDate)

    /**
     * PEPFAR IIT classification tier.
     *   < 28 days  → should not appear in IIT list (filtered at query level)
     *  28–59 days  → Early IIT
     *  60–179 days → IIT
     *  ≥ 180 days  → LTFU (Lost to Follow-Up)
     */
    val iitTier: IITTier
        get() = when {
            daysOverdue >= 180 -> IITTier.LTFU
            daysOverdue >= 60  -> IITTier.IIT
            else               -> IITTier.EARLY_IIT
        }
}

enum class IITTier(val label: String, val shortLabel: String) {
    EARLY_IIT("Early IIT",             "28–59d"),
    IIT      ("IIT",                   "60–179d"),
    LTFU     ("Lost to Follow-Up",     "180d+")
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
    LAST_WEEK("Last IIT"),
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
