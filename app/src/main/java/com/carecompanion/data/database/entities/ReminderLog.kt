package com.carecompanion.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * One record per reminder the app actually attempted to send. This closes the
 * measurement loop: it lets the clinic prove what the AI sent and, by joining to
 * subsequent pharmacy visits, whether the reminder was followed by attendance.
 */
@Entity(tableName = "reminder_log")
data class ReminderLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personUuid: String,
    val hospitalNumber: String,
    val displayName: String,
    val reminderType: String,        // ReminderType.name
    val channels: String,            // "SMS", "Email", "SMS & Email"
    val success: Boolean,
    val detail: String? = null,      // failure reason when not successful
    val riskScore: Int,
    val riskBand: String,
    val appointmentDate: Date? = null,
    val sentAt: Date,
    val auto: Boolean,               // true = background worker, false = manual tap
)
