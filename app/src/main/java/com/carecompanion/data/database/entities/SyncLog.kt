package com.carecompanion.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "sync_log")
data class SyncLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tableName: String,
    val lastSyncedRecordId: String,
    val syncDate: Date,
    val status: String
)