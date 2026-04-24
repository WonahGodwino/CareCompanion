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
    val lastSyncDate: Date
)