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
    indices = [Index(value = ["personUuid"])]
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
    val lastSyncDate: Date
)