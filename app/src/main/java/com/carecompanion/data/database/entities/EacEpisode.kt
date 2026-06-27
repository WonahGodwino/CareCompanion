package com.carecompanion.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import java.util.Date

/**
 * One Enhanced Adherence Counselling EPISODE (synced from WINCO /api/art/clients/{uuid}/eac).
 * The on-device [com.carecompanion.utils.EacGapEngine] derives live gaps from these episodes plus the
 * patient's current VL + TX_CURR status. `stage` is NOT_STARTED / IN_PROGRESS / COMPLETE / STOPPED.
 */
@Entity(
    tableName = "eac_episode",
    primaryKeys = ["personUuid", "episodeUuid"],
    foreignKeys = [
        ForeignKey(
            entity = Patient::class,
            parentColumns = ["uuid"],
            childColumns = ["personUuid"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        )
    ],
    indices = [Index(value = ["personUuid"]), Index(value = ["triggerDate"])]
)
data class EacEpisode(
    val personUuid: String,
    val episodeUuid: String,
    val status: String? = null,        // raw EMR status (e.g. "FIRST EAC COMPLETED")
    val stage: String? = null,         // NOT_STARTED | IN_PROGRESS | COMPLETE | STOPPED
    val sessions: Int = 0,
    val triggerVl: Double? = null,     // VL that triggered the episode
    val triggerDate: Date? = null,
    val repeatVl: Double? = null,      // Post-EAC repeat VL (null until done)
    val regimenSwitched: Boolean = false,
    val lastSyncDate: Date,
)
