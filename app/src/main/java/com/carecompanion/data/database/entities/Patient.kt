package com.carecompanion.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import java.util.Date

@Entity(tableName = "patient_person")
data class Patient(
    @PrimaryKey val uuid: String,
    @ColumnInfo(name = "person_uuid")
    val personUuid: String? = null, // WINCO/EMR sync field
    val emrPatientId: Long,
    val createdDate: Date? = null,
    val createdBy: String? = null,
    val lastModifiedDate: Date? = null,
    val lastModifiedBy: String? = null,
    val active: Boolean? = true,
    val contactPoint: String? = null,
    val address: String? = null,
    val gender: String? = null,
    val identifier: String? = null,
    val deceased: Boolean? = null,
    val deceasedDateTime: Date? = null,
    val maritalStatus: String? = null,
    val employmentStatus: String? = null,
    val education: String? = null,
    val organization: String? = null,
    val contact: String? = null,
    val hospitalNumber: String,
    val firstName: String?,
    val surname: String?,
    val otherName: String?,
    val fullName: String?,
    val sex: String?,
    val dateOfBirth: Date?,
    val dateOfRegistration: Date? = null,
    val archived: Int = 0,
    val isDateOfBirthEstimated: Boolean = false,
    val ninNumber: String?,
    val emrId: String?,
    val phoneNumber: String?,
    val caseManagerId: Long? = null,
    val reason: String? = null,
    val latitude: String? = null,
    val longitude: String? = null,
    val source: String? = null,
    val currentStatus: String? = null, // "ART", "PrEP", "HTS", "HIVST", etc. 
    val currentStatusDate: Date? = null,
    val artStartDate: Date? = null,      // ART initiation date (date_started from enrollment)
    val lastViralLoadDate: Date? = null,        // From WINCO viral_load.last_result_date
    val lastViralLoadResult: Long? = null,      // From WINCO viral_load.last_result_value (numeric)
    val lastViralLoadResultRaw: String? = null, // Raw string like "Not Detected", ">=200"
    val ndrMatchedStatus: String? = null,       // From WINCO NDR line list match_outcome / NDR_Matched_status
    val lastTbScreeningDate: Date? = null,      // From WINCO tb_screening.date
    val lastTbScreeningStatus: String? = null,  // From WINCO tb_screening.status
    val facilityId: Long,
    val lastSyncDate: Date,
    val isActive: Boolean = true
) {
    // Only sync biometrics for ART-enrolled patients (TX_CURR-eligible statuses).
    // currentStatus holds the WINCO care category (ACTIVE, IIT, etc.) or the raw
    // EMR status (ART_START / ART_TRANSFER_IN) depending on the sync path.
    fun isArtEnrolled(): Boolean {
        val normalizedStatus = currentStatus?.trim()?.uppercase()?.replace(" ", "_")?.replace("-", "_") ?: return false
        return normalizedStatus == "ART"
            || normalizedStatus == "ART_START"
            || normalizedStatus == "ART_TRANSFER_IN"
            || normalizedStatus == "ACTIVE"
            || normalizedStatus == "ACTIVE_TX_CURR"  // legacy: kept for local DB rows synced before the API update
    }

    /**
     * Calculates the patient's care category based on latest pharmacy visit, refill period, and HIV status.
     * Returns one of: ACTIVE, IIT, TRANSFER_OUT, STOPPED_TREATMENT, DEATH, OTHER_INACTIVE
     */
    fun calculatePatientStatus(
        latestPharmacyVisitDate: Date?,
        latestPharmacyRefillPeriod: Int?,
        latestHivStatus: String?
    ): String {
        if (latestPharmacyVisitDate == null || latestHivStatus == null) return "OTHER_INACTIVE"
        val refillDays = latestPharmacyRefillPeriod ?: 0
        val today = Date()
        val calendar = java.util.Calendar.getInstance()
        calendar.time = latestPharmacyVisitDate
        calendar.add(java.util.Calendar.DAY_OF_YEAR, refillDays + 28)
        val coverageEndDate = calendar.time

        val normalizedHivStatus = latestHivStatus.trim().uppercase().replace(" ", "_").replace("-", "_")
        return when {
            // Active on treatment
            normalizedHivStatus in setOf("ART_START", "ART_TRANSFER_IN") && coverageEndDate >= today -> "ACTIVE"
            // IIT/LTFU — pharmacy-lapsed on-treatment (PEPFAR TX_ML definition)
            normalizedHivStatus in setOf("ART_START", "ART_TRANSFER_IN") && coverageEndDate < today -> "IIT"
            // Explicit IIT status
            normalizedHivStatus in setOf("INTERRUPTED_IN_TREATMENT", "IIT", "LTFU", "LOST_TO_FOLLOW_UP") -> "IIT"
            // Transfer out
            normalizedHivStatus in setOf("ART_TRANSFER_OUT", "TRANSFER_OUT") -> "TRANSFER_OUT"
            // Stopped treatment
            normalizedHivStatus in setOf("STOPPED_TREATMENT", "TREATMENT_STOPPED", "ART_STOP") -> "STOPPED_TREATMENT"
            // Death
            normalizedHivStatus in setOf("DEATH", "DIED", "DEAD") -> "DEATH"
            else -> "OTHER_INACTIVE"
        }
    }
}