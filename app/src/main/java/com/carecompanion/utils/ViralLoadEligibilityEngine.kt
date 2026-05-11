package com.carecompanion.utils

import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import java.util.concurrent.TimeUnit

enum class ViralLoadDueType {
    BASELINE,
    ROUTINE
}

data class ViralLoadEligibilityResult(
    val isEligibleToday: Boolean,
    val dueType: ViralLoadDueType,
    val dueDate: Date,
    val daysUntilDue: Int,
    val daysOverdue: Int,
    val ageYears: Int,
    val message: String,
    val sampleCollectionDate: Date? = null,  // Date sample was collected (for tracking pending results)
)

object ViralLoadEligibilityEngine {
    private const val ADULT_AGE_YEARS = 15
    private const val ADULT_BASELINE_DAYS = 181
    private const val ADULT_FIRST_ROUTINE_DAYS = 365
    private const val ADULT_ROUTINE_INTERVAL_DAYS = 365
    private const val CHILD_BASELINE_DAYS = 90
    private const val CHILD_FIRST_ROUTINE_DAYS = 180
    private const val CHILD_ROUTINE_INTERVAL_DAYS = 180

    private val watTimeZone: TimeZone = TimeZone.getTimeZone("Africa/Lagos")

    fun evaluate(
        dateOfBirth: Date?,
        artRegistrationDate: Date?,
        dateSampleCollected: Date?,  // PEPFAR/NACA standard: use sample collection date for eligibility
        dateResultReported: Date? = null,  // Result date (used only for displaying to user, not for eligibility)
        referenceDate: Date = Date(),
    ): ViralLoadEligibilityResult? {
        artRegistrationDate ?: return null

        val normalizedReference = atStartOfDay(referenceDate)
        val normalizedArtDate = atStartOfDay(artRegistrationDate)
        val ageYears = DateUtils.calculateAge(dateOfBirth)
        val isChild = ageYears in 0..14

        val baselineOffset = if (isChild) CHILD_BASELINE_DAYS else ADULT_BASELINE_DAYS
        val routineInterval = if (isChild) CHILD_ROUTINE_INTERVAL_DAYS else ADULT_ROUTINE_INTERVAL_DAYS

        val dueType: ViralLoadDueType
        val dueDate: Date

        if (dateSampleCollected == null) {
            // No sample collected yet: patient is tracked against baseline schedule.
            // Use ART registration date as reference point.
            dueType = ViralLoadDueType.BASELINE
            dueDate = addDays(normalizedArtDate, baselineOffset)
        } else {
            // Sample has been collected: patient follows routine cadence from sample collection date.
            // Per PEPFAR/NACA standards, sample collection date (not result date) drives eligibility.
            val normalizedSampleDate = atStartOfDay(dateSampleCollected)
            dueType = ViralLoadDueType.ROUTINE
            dueDate = addDays(normalizedSampleDate, routineInterval)
        }

        val dayDelta = TimeUnit.MILLISECONDS.toDays(dueDate.time - normalizedReference.time).toInt()
        val isEligibleToday = dayDelta <= 0
        val daysUntilDue = if (dayDelta > 0) dayDelta else 0
        val daysOverdue = if (dayDelta < 0) -dayDelta else 0

        val profile = if (isChild) "Child (<=14y)" else "Adult (>=15y)"
        val cadenceText = if (dueType == ViralLoadDueType.BASELINE) "baseline" else "routine"
        val timingText = when {
            daysOverdue > 0 -> "overdue by $daysOverdue day(s)"
            daysUntilDue > 0 -> "due in $daysUntilDue day(s)"
            else -> "due today"
        }

        return ViralLoadEligibilityResult(
            isEligibleToday = isEligibleToday,
            dueType = dueType,
            dueDate = dueDate,
            daysUntilDue = daysUntilDue,
            daysOverdue = daysOverdue,
            ageYears = ageYears,
            message = "$profile $cadenceText viral load is $timingText.",
            sampleCollectionDate = dateSampleCollected
        )
    }

    private fun atStartOfDay(date: Date): Date {
        val cal = Calendar.getInstance(watTimeZone)
        cal.time = date
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.time
    }

    private fun addDays(base: Date, days: Int): Date {
        val cal = Calendar.getInstance(watTimeZone)
        cal.time = base
        cal.add(Calendar.DAY_OF_YEAR, days)
        return cal.time
    }
}
