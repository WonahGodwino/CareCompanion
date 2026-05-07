package com.carecompanion.utils

import com.carecompanion.data.database.entities.ArtPharmacy
import com.carecompanion.data.database.entities.Patient
import com.carecompanion.presentation.viewmodels.ServiceEligibilityUI
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Local offline eligibility engine.
 * Runs entirely against synced Room data.
 */
object ServiceEligibilityEngine {

    private const val GRACE_DAYS = 28
    private const val EARLY_REFILL_WINDOW_DAYS = 7
    private const val VL_BASELINE_DAYS = 181
    private const val VL_ROUTINE_ADULT_DAYS = 365
    private const val VL_ROUTINE_CHILD_DAYS = 180

    private val ON_TREATMENT_STATUSES = setOf("ART_START", "ART_TRANSFER_IN", "ACTIVE", "ACTIVE_TX_CURR")
    private val IIT_STATUSES = setOf("INTERRUPTED_IN_TREATMENT", "IIT", "LTFU", "LOST_TO_FOLLOW_UP")
    private val TRANSFER_OUT_STATUSES = setOf("ART_TRANSFER_OUT", "TRANSFER_OUT")
    private val STOPPED_TREATMENT_STATUSES = setOf("STOPPED_TREATMENT", "TREATMENT_STOPPED", "ART_STOP")
    private val DEATH_STATUSES = setOf("DEATH", "DIED", "DEAD")

    fun calculateArtRefill(patient: Patient, artPharmacy: List<ArtPharmacy>): ServiceEligibilityUI {
        val latest = artPharmacy.maxByOrNull { it.visitDate }
        val careCategory = resolveCareCategory(patient, latest)
        val isActiveTxCurr = careCategory == "ACTIVE"

        if (latest == null) {
            return ServiceEligibilityUI(
                service = "ART_REFILL",
                eligible = false,
                reason = "No pharmacy records found",
                urgency = "routine",
                nextAction = "Verify pharmacy history in EMR",
                careCategory = careCategory,
                eligibilityGroup = artRefillEligibilityGroup(careCategory, isEligible = false, hasPharmacyHistory = false),
            )
        }

        if (!isActiveTxCurr) {
            return ServiceEligibilityUI(
                service = "ART_REFILL",
                eligible = false,
                reason = "Client not currently on active treatment",
                urgency = "routine",
                nextAction = null,
                careCategory = careCategory,
                eligibilityGroup = artRefillEligibilityGroup(careCategory, isEligible = false, hasPharmacyHistory = true),
            )
        }

        val today = Date()
        val refillDays = latest.refillPeriod ?: 0
        val medicationExpiry = Date(latest.visitDate.time + TimeUnit.DAYS.toMillis((refillDays + GRACE_DAYS).toLong()))
        val earlyRefillDate = Date(medicationExpiry.time - TimeUnit.DAYS.toMillis(EARLY_REFILL_WINDOW_DAYS.toLong()))
        val daysUntilExpiry = daysBetween(today, medicationExpiry)

        return if (!today.before(earlyRefillDate)) {
            ServiceEligibilityUI(
                service = "ART_REFILL",
                eligible = true,
                reason = "Medication supply expires in $daysUntilExpiry day${if (daysUntilExpiry == 1L) "" else "s"}",
                urgency = when {
                    daysUntilExpiry < 0 -> "critical"
                    daysUntilExpiry <= EARLY_REFILL_WINDOW_DAYS -> "due"
                    else -> "routine"
                },
                nextAction = "Schedule medication refill appointment",
                careCategory = careCategory,
                eligibilityGroup = artRefillEligibilityGroup(careCategory, isEligible = true, hasPharmacyHistory = true),
                details = mapOf(
                    "last_refill_date" to DateUtils.formatDate(latest.visitDate),
                    "refill_period_days" to refillDays,
                    "medication_expiry_date" to DateUtils.formatDate(medicationExpiry),
                    "days_until_expiry" to daysUntilExpiry,
                ),
            )
        } else {
            val daysUntilEligible = daysBetween(today, earlyRefillDate)
            ServiceEligibilityUI(
                service = "ART_REFILL",
                eligible = false,
                reason = "Next refill due in $daysUntilEligible day${if (daysUntilEligible == 1L) "" else "s"}",
                urgency = "routine",
                nextAction = null,
                careCategory = careCategory,
                eligibilityGroup = artRefillEligibilityGroup(careCategory, isEligible = false, hasPharmacyHistory = true),
                details = mapOf(
                    "last_refill_date" to DateUtils.formatDate(latest.visitDate),
                    "refill_period_days" to refillDays,
                    "medication_expiry_date" to DateUtils.formatDate(medicationExpiry),
                    "days_until_refill_eligible" to daysUntilEligible,
                ),
            )
        }
    }

    fun calculateViralLoad(patient: Patient, artPharmacy: List<ArtPharmacy>): ServiceEligibilityUI {
        val latest = artPharmacy.maxByOrNull { it.visitDate }
        val careCategory = resolveCareCategory(patient, latest)
        val isActiveTxCurr = careCategory == "ACTIVE"

        val vlDate = patient.lastViralLoadDate
        val vlResult = patient.lastViralLoadResult
        val artStartDate = patient.artStartDate

        if (!isActiveTxCurr) {
            return ServiceEligibilityUI(
                service = "VIRAL_LOAD",
                eligible = false,
                reason = "Client not currently on active treatment",
                urgency = "routine",
                nextAction = null,
                careCategory = careCategory,
                eligibilityGroup = viralLoadEligibilityGroup(careCategory, isEligible = false, vlType = "none"),
            )
        }

        if (artStartDate == null) {
            return ServiceEligibilityUI(
                service = "VIRAL_LOAD",
                eligible = false,
                reason = "ART start date not recorded in EMR",
                urgency = "routine",
                nextAction = "Verify ART start date in the patient's EMR record",
                careCategory = careCategory,
                eligibilityGroup = viralLoadEligibilityGroup(careCategory, isEligible = false, vlType = "no_art_date"),
            )
        }

        val today = Date()
        val ageYears = DateUtils.calculateAge(patient.dateOfBirth)

        if (vlDate == null) {
            val baselineDueDate = Date(artStartDate.time + TimeUnit.DAYS.toMillis(VL_BASELINE_DAYS.toLong()))
            val daysUntilBaseline = daysBetween(today, baselineDueDate)

            return if (daysUntilBaseline >= 0) {
                ServiceEligibilityUI(
                    service = "VIRAL_LOAD",
                    eligible = false,
                    reason = "Baseline VL due in $daysUntilBaseline day${if (daysUntilBaseline == 1L) "" else "s"} (${DateUtils.formatDate(baselineDueDate)})",
                    urgency = if (daysUntilBaseline <= 14) "due" else "routine",
                    nextAction = "Schedule baseline VL ${DateUtils.formatDate(baselineDueDate)}",
                    careCategory = careCategory,
                    eligibilityGroup = viralLoadEligibilityGroup(careCategory, isEligible = false, vlType = "baseline"),
                    details = mapOf(
                        "vl_type" to "baseline",
                        "art_start_date" to DateUtils.formatDate(artStartDate),
                        "baseline_due_date" to DateUtils.formatDate(baselineDueDate),
                        "days_until" to daysUntilBaseline,
                    ),
                )
            } else {
                val daysOverdue = -daysUntilBaseline
                ServiceEligibilityUI(
                    service = "VIRAL_LOAD",
                    eligible = true,
                    reason = "Baseline VL overdue by $daysOverdue day${if (daysOverdue == 1L) "" else "s"}",
                    urgency = "critical",
                    nextAction = "Collect baseline VL immediately",
                    careCategory = careCategory,
                    eligibilityGroup = viralLoadEligibilityGroup(careCategory, isEligible = true, vlType = "baseline"),
                    details = mapOf(
                        "vl_type" to "baseline",
                        "art_start_date" to DateUtils.formatDate(artStartDate),
                        "baseline_due_date" to DateUtils.formatDate(baselineDueDate),
                        "days_overdue" to daysOverdue,
                    ),
                )
            }
        }

        val routineIntervalDays = if (ageYears > 14) VL_ROUTINE_ADULT_DAYS else VL_ROUTINE_CHILD_DAYS
        val routineDueDate = Date(vlDate.time + TimeUnit.DAYS.toMillis(routineIntervalDays.toLong()))
        val daysUntilRoutine = daysBetween(today, routineDueDate)

        val lastResultText = when {
            vlResult == null -> "Result pending"
            vlResult <= 20 -> "Undetectable (<=20 cp/mL)"
            vlResult < 1000 -> "Suppressed (${vlResult} cp/mL)"
            else -> "Unsuppressed (${vlResult} cp/mL)"
        }

        val detailsBase: Map<String, Any> = mapOf(
            "vl_type" to "routine",
            "last_vl_date" to DateUtils.formatDate(vlDate),
            "last_vl_result" to lastResultText,
            "routine_interval_days" to routineIntervalDays,
            "routine_due_date" to DateUtils.formatDate(routineDueDate),
        )

        return if (daysUntilRoutine >= 0) {
            ServiceEligibilityUI(
                service = "VIRAL_LOAD",
                eligible = false,
                reason = "Routine VL due in $daysUntilRoutine day${if (daysUntilRoutine == 1L) "" else "s"} (${DateUtils.formatDate(routineDueDate)})",
                urgency = when {
                    daysUntilRoutine <= 14 -> "due"
                    daysUntilRoutine <= 30 -> "high"
                    else -> "routine"
                },
                nextAction = "Next VL due ${DateUtils.formatDate(routineDueDate)}",
                careCategory = careCategory,
                eligibilityGroup = viralLoadEligibilityGroup(careCategory, isEligible = false, vlType = "routine"),
                details = detailsBase + mapOf("days_until" to daysUntilRoutine),
            )
        } else {
            val daysOverdue = -daysUntilRoutine
            ServiceEligibilityUI(
                service = "VIRAL_LOAD",
                eligible = true,
                reason = "Routine VL overdue by $daysOverdue day${if (daysOverdue == 1L) "" else "s"}",
                urgency = if (daysOverdue >= 90) "critical" else "high",
                nextAction = "Collect VL sample immediately",
                careCategory = careCategory,
                eligibilityGroup = viralLoadEligibilityGroup(careCategory, isEligible = true, vlType = "routine"),
                details = detailsBase + mapOf("days_overdue" to daysOverdue),
            )
        }
    }

    fun calculateAll(patient: Patient, artPharmacy: List<ArtPharmacy>): Map<String, ServiceEligibilityUI> {
        val results = mutableMapOf<String, ServiceEligibilityUI>()
        results["ART_REFILL"] = calculateArtRefill(patient, artPharmacy)
        results["VIRAL_LOAD"] = calculateViralLoad(patient, artPharmacy)
        results["TPT"] = ServiceEligibilityUI(
            service = "TPT",
            eligible = false,
            reason = "TPT eligibility assessment coming soon",
            urgency = "routine",
            nextAction = null,
        )
        results["TB_AHD"] = ServiceEligibilityUI(
            service = "TB_AHD",
            eligible = false,
            reason = "TB/AHD screening assessment coming soon",
            urgency = "routine",
            nextAction = null,
        )
        return results
    }

    fun artRefillEligibilityGroupCounts(services: Map<String, ServiceEligibilityUI>): Map<String, Int> {
        val counts = linkedMapOf(
            "ACTIVE_ELIGIBLE" to 0,
            "ACTIVE_NOT_DUE" to 0,
            "ACTIVE_NO_PHARMACY_HISTORY" to 0,
            "IIT" to 0,
            "TRANSFER_OUT" to 0,
            "STOPPED_TREATMENT" to 0,
            "DEATH" to 0,
            "OTHER_INACTIVE" to 0,
            "UNKNOWN_STATUS" to 0,
        )
        val group = services["ART_REFILL"]?.eligibilityGroup
        if (!group.isNullOrBlank()) {
            if (counts.containsKey(group)) {
                counts[group] = (counts[group] ?: 0) + 1
            } else {
                counts["OTHER_INACTIVE"] = (counts["OTHER_INACTIVE"] ?: 0) + 1
            }
        }
        return counts
    }

    fun viralLoadEligibilityGroupCounts(services: Map<String, ServiceEligibilityUI>): Map<String, Int> {
        val counts = linkedMapOf(
            "ACTIVE_ELIGIBLE_BASELINE" to 0,
            "ACTIVE_ELIGIBLE_ROUTINE" to 0,
            "ACTIVE_NOT_DUE" to 0,
            "ACTIVE_NO_VL_DATA" to 0,
            "IIT" to 0,
            "TRANSFER_OUT" to 0,
            "STOPPED_TREATMENT" to 0,
            "DEATH" to 0,
            "OTHER_INACTIVE" to 0,
            "UNKNOWN_STATUS" to 0,
        )
        val group = services["VIRAL_LOAD"]?.eligibilityGroup
        if (!group.isNullOrBlank()) {
            if (counts.containsKey(group)) {
                counts[group] = (counts[group] ?: 0) + 1
            } else {
                counts["OTHER_INACTIVE"] = (counts["OTHER_INACTIVE"] ?: 0) + 1
            }
        }
        return counts
    }

    private fun daysBetween(from: Date, to: Date): Long =
        TimeUnit.MILLISECONDS.toDays(to.time - from.time)

    private fun normalizeStatus(value: String?): String? {
        val normalized = value?.trim()?.uppercase()?.replace(" ", "_")?.replace("-", "_")
        return normalized?.takeIf { it.isNotBlank() }
    }

    private fun resolveCareCategory(patient: Patient, latestPharmacy: ArtPharmacy?): String {
        val normalizedStatus = normalizeStatus(patient.currentStatus)

        if (normalizedStatus in TRANSFER_OUT_STATUSES) return "TRANSFER_OUT"
        if (normalizedStatus in STOPPED_TREATMENT_STATUSES) return "STOPPED_TREATMENT"
        if (normalizedStatus in DEATH_STATUSES) return "DEATH"
        if (normalizedStatus in IIT_STATUSES) return "IIT"

        if (normalizedStatus in ON_TREATMENT_STATUSES) {
            if (latestPharmacy == null) return "IIT"
            val refillDays = latestPharmacy.refillPeriod ?: 0
            val coverageEnd = Date(latestPharmacy.visitDate.time + TimeUnit.DAYS.toMillis((refillDays + GRACE_DAYS).toLong()))
            return if (!coverageEnd.before(Date())) "ACTIVE" else "IIT"
        }

        if (normalizedStatus == null) return "UNKNOWN_STATUS"
        return "OTHER_INACTIVE"
    }

    private fun viralLoadEligibilityGroup(careCategory: String, isEligible: Boolean, vlType: String): String {
        if (careCategory == "ACTIVE") {
            if (!isEligible) return "ACTIVE_NOT_DUE"
            if (vlType == "baseline") return "ACTIVE_ELIGIBLE_BASELINE"
            if (vlType == "routine") return "ACTIVE_ELIGIBLE_ROUTINE"
            return "ACTIVE_NO_VL_DATA"
        }
        if (careCategory == "IIT") return "IIT"
        if (careCategory == "TRANSFER_OUT") return "TRANSFER_OUT"
        if (careCategory == "STOPPED_TREATMENT") return "STOPPED_TREATMENT"
        if (careCategory == "DEATH") return "DEATH"
        if (careCategory == "UNKNOWN_STATUS") return "UNKNOWN_STATUS"
        return "OTHER_INACTIVE"
    }

    private fun artRefillEligibilityGroup(careCategory: String, isEligible: Boolean, hasPharmacyHistory: Boolean): String {
        if (careCategory == "ACTIVE") {
            if (!hasPharmacyHistory) return "ACTIVE_NO_PHARMACY_HISTORY"
            return if (isEligible) "ACTIVE_ELIGIBLE" else "ACTIVE_NOT_DUE"
        }
        if (careCategory == "IIT") return "IIT"
        if (careCategory == "TRANSFER_OUT") return "TRANSFER_OUT"
        if (careCategory == "STOPPED_TREATMENT") return "STOPPED_TREATMENT"
        if (careCategory == "DEATH") return "DEATH"
        if (careCategory == "UNKNOWN_STATUS") return "UNKNOWN_STATUS"
        return "OTHER_INACTIVE"
    }
}
