package com.carecompanion.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "biometric",
    foreignKeys = [
        ForeignKey(
            entity = Patient::class,
            parentColumns = ["uuid"],
            childColumns = ["personUuid"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["personUuid"]),
        Index(value = ["hashed"]),
        Index(value = ["sourceId"]),
        Index(value = ["lastSyncDate"]),
        Index(value = ["archived"])
    ]
)
data class Biometric(
    @PrimaryKey val id: String,
    val personUuid: String,
    val template: ByteArray,
    val biometricType: String?,
    val templateType: String?,
    val recapture: Int = 0,
    val enrollmentDate: Date?,
    val deviceName: String?,
    val imageQuality: Int?,
    val iso: Boolean = false,
    val versionIso20: Boolean = false,
    val lastSyncDate: Date,
    val archived: Int? = null,
    val count: Int? = null,
    val createdBy: String? = null,
    val createdDate: Date? = null,
    val extra: String? = null,
    val facilityId: Long? = null,
    val hashed: String? = null,
    val lastModifiedBy: String? = null,
    val lastModifiedDate: Date? = null,
    val matchBiometricId: String? = null,
    val matchPersonUuid: String? = null,
    val matchType: String? = null,
    val rawPayload: String? = null,
    val reason: String? = null,
    val recaptureMessage: String? = null,
    val replaceDate: Date? = null,
    val sourceId: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Biometric

        if (id != other.id) return false
        if (personUuid != other.personUuid) return false
        if (!template.contentEquals(other.template)) return false
        if (biometricType != other.biometricType) return false
        if (templateType != other.templateType) return false
        if (recapture != other.recapture) return false
        if (enrollmentDate != other.enrollmentDate) return false
        if (deviceName != other.deviceName) return false
        if (imageQuality != other.imageQuality) return false
        if (iso != other.iso) return false
        if (versionIso20 != other.versionIso20) return false
        if (lastSyncDate != other.lastSyncDate) return false
        if (archived != other.archived) return false
        if (count != other.count) return false
        if (createdBy != other.createdBy) return false
        if (createdDate != other.createdDate) return false
        if (extra != other.extra) return false
        if (facilityId != other.facilityId) return false
        if (hashed != other.hashed) return false
        if (lastModifiedBy != other.lastModifiedBy) return false
        if (lastModifiedDate != other.lastModifiedDate) return false
        if (matchBiometricId != other.matchBiometricId) return false
        if (matchPersonUuid != other.matchPersonUuid) return false
        if (matchType != other.matchType) return false
        if (rawPayload != other.rawPayload) return false
        if (reason != other.reason) return false
        if (recaptureMessage != other.recaptureMessage) return false
        if (replaceDate != other.replaceDate) return false
        if (sourceId != other.sourceId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + personUuid.hashCode()
        result = 31 * result + template.contentHashCode()
        result = 31 * result + (biometricType?.hashCode() ?: 0)
        result = 31 * result + (templateType?.hashCode() ?: 0)
        result = 31 * result + recapture
        result = 31 * result + (enrollmentDate?.hashCode() ?: 0)
        result = 31 * result + (deviceName?.hashCode() ?: 0)
        result = 31 * result + (imageQuality ?: 0)
        result = 31 * result + iso.hashCode()
        result = 31 * result + versionIso20.hashCode()
        result = 31 * result + lastSyncDate.hashCode()
        result = 31 * result + (archived ?: 0)
        result = 31 * result + (count ?: 0)
        result = 31 * result + (createdBy?.hashCode() ?: 0)
        result = 31 * result + (createdDate?.hashCode() ?: 0)
        result = 31 * result + (extra?.hashCode() ?: 0)
        result = 31 * result + (facilityId?.hashCode() ?: 0)
        result = 31 * result + (hashed?.hashCode() ?: 0)
        result = 31 * result + (lastModifiedBy?.hashCode() ?: 0)
        result = 31 * result + (lastModifiedDate?.hashCode() ?: 0)
        result = 31 * result + (matchBiometricId?.hashCode() ?: 0)
        result = 31 * result + (matchPersonUuid?.hashCode() ?: 0)
        result = 31 * result + (matchType?.hashCode() ?: 0)
        result = 31 * result + (rawPayload?.hashCode() ?: 0)
        result = 31 * result + (reason?.hashCode() ?: 0)
        result = 31 * result + (recaptureMessage?.hashCode() ?: 0)
        result = 31 * result + (replaceDate?.hashCode() ?: 0)
        result = 31 * result + (sourceId?.hashCode() ?: 0)
        return result
    }
}