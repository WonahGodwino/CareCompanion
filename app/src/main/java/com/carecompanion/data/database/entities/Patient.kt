package com.carecompanion.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "patient_person")
data class Patient(
    @PrimaryKey val uuid: String,
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
    val facilityId: Long,
    val lastSyncDate: Date,
    val isActive: Boolean = true
) {
    // Only sync biometrics for ART-enrolled patients (TX_CURR-eligible statuses)
    fun isArtEnrolled(): Boolean {
        val normalizedStatus = currentStatus?.trim()?.uppercase()?.replace(" ", "_")?.replace("-", "_") ?: return false
        return normalizedStatus == "ART" || normalizedStatus == "ART_START" || normalizedStatus == "ART_TRANSFER_IN"
    }
}