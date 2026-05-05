package com.carecompanion.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "art_pharmacy",
    foreignKeys = [
        ForeignKey(
            entity = Patient::class,
            parentColumns = ["uuid"],
            childColumns = ["personUuid"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["personUuid"])]
)
data class ArtPharmacy(
    @PrimaryKey val id: Long,
    val personUuid: String,
    val visitDate: Date,
    val nextAppointment: Date?,
    val regimenId: Long?,
    val mmdType: String?,
    val refillPeriod: Int?,
    val dsdModel: String?,
    val adherence: Boolean?,
    val lastSyncDate: Date,
    // --- columns aligned with hiv_art_pharmacy server schema ---
    val uuid: String? = null,
    val visitId: String? = null,
    val visitType: String? = null,
    val createdDate: Date? = null,
    val createdBy: String? = null,
    val lastModifiedDate: Date? = null,
    val lastModifiedBy: String? = null,
    val facilityId: Long? = null,
    val archived: Int? = null,
    val deliveryPoint: String? = null,
    val dsdModelType: String? = null,
    val ipt: String? = null,          // jsonb stored as JSON string
    val iptType: String? = null,
    val isDevolve: Boolean? = null,
    val ardScreened: Boolean? = null,
    val adverseDrugReactions: String? = null, // jsonb stored as JSON string
    val prescriptionError: Boolean? = null,
    val refill: String? = null,
    val refillType: String? = null,
    val source: String? = null,
    val sourceId: Long? = null,
    val rawPayload: String? = null,
    val latitude: String? = null,
    val longitude: String? = null,
    val extra: String? = null         // jsonb stored as JSON string
)