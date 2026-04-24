package com.carecompanion.data.database.dao

import androidx.room.*
import com.carecompanion.data.database.entities.SyncLog
import java.util.Date

@Dao
interface SyncLogDao {
    @Insert suspend fun insert(syncLog: SyncLog)
    @Query("SELECT * FROM sync_log ORDER BY syncDate DESC LIMIT 1") suspend fun getLastSyncLog(): SyncLog?
    @Query("SELECT MAX(syncDate) FROM sync_log WHERE status = 'SUCCESS'") suspend fun getLastSuccessfulSyncDate(): Date?
    @Query("DELETE FROM sync_log") suspend fun deleteAll()
}