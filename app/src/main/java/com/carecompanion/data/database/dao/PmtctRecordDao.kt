package com.carecompanion.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.carecompanion.data.database.entities.PmtctRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface PmtctRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PmtctRecord>)

    @Query("DELETE FROM pmtct_record")
    suspend fun clear()

    // Worklist: currently-pregnant women, those with a PMTCT VL gap first, then by gestational age.
    @Query(
        """
        SELECT * FROM pmtct_record
        WHERE currentlyPregnant = 1
        ORDER BY (gapType IS NOT NULL) DESC, gaWeeks DESC
        """
    )
    fun observeWorklist(): Flow<List<PmtctRecord>>

    @Query("SELECT * FROM pmtct_record")
    suspend fun getAll(): List<PmtctRecord>
}
