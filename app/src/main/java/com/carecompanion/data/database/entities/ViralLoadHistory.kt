package com.carecompanion.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import java.util.Date

@Entity(
    tableName = "viral_load_history",
    primaryKeys = ["personUuid", "testId"],
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
        Index(value = ["resultDate"]),
        Index(value = ["sampleDate"])
    ]
)
data class ViralLoadHistory(
    val personUuid: String,
    val testId: Long,
    val sampleTypeId: Int? = null,
    val sampleNumber: String? = null,
    val resultRaw: String? = null,
    val resultNumeric: Long? = null,
    val resultDate: Date? = null,
    val assayedDate: Date? = null,
    val sampleDate: Date? = null,
    val sourceId: Long? = null,
    val source: String? = null,
    val lastSyncDate: Date,
)
