package com.carecompanion.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.carecompanion.data.database.entities.ReminderLog
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderLogDao {
    @Insert suspend fun insert(log: ReminderLog)

    @Query("SELECT * FROM reminder_log ORDER BY sentAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 300): Flow<List<ReminderLog>>

    @Query("SELECT COUNT(*) FROM reminder_log") suspend fun count(): Int
    @Query("SELECT COUNT(*) FROM reminder_log WHERE success = 1") suspend fun countSuccess(): Int
    @Query("DELETE FROM reminder_log") suspend fun deleteAll()
}
