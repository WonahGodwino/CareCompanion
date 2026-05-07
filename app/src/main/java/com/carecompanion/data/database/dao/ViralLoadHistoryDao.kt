package com.carecompanion.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.carecompanion.data.database.entities.ViralLoadHistory

@Dao
interface ViralLoadHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ViralLoadHistory>)

    @Query("DELETE FROM viral_load_history WHERE personUuid = :personUuid")
    suspend fun deleteByPersonUuid(personUuid: String)

    @Query(
        """
        SELECT *
        FROM viral_load_history
        WHERE personUuid = :personUuid
        ORDER BY
            resultDate DESC,
            assayedDate DESC,
            sampleDate DESC,
            testId DESC
        """
    )
    suspend fun getByPersonUuid(personUuid: String): List<ViralLoadHistory>
}
