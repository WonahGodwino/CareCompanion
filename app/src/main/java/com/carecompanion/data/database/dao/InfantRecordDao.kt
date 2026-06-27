package com.carecompanion.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.carecompanion.data.database.entities.InfantRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface InfantRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<InfantRecord>)

    @Query("DELETE FROM infant_record")
    suspend fun clear()

    // EID worklist: critical gaps first, then high-risk, then youngest (most time-sensitive).
    @Query(
        """
        SELECT * FROM infant_record
        ORDER BY (gapSeverity = 'critical') DESC, (gapType IS NOT NULL) DESC, highRisk DESC, ageWeeks ASC
        """
    )
    fun observeWorklist(): Flow<List<InfantRecord>>
}
