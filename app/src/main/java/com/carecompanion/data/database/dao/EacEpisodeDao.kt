package com.carecompanion.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.carecompanion.data.database.entities.EacEpisode

@Dao
interface EacEpisodeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<EacEpisode>)

    @Query("DELETE FROM eac_episode WHERE personUuid = :personUuid")
    suspend fun deleteByPersonUuid(personUuid: String)

    @Query(
        """
        SELECT * FROM eac_episode
        WHERE personUuid = :personUuid
        ORDER BY triggerDate DESC
        """
    )
    suspend fun getByPersonUuid(personUuid: String): List<EacEpisode>

    @Query("SELECT * FROM eac_episode")
    suspend fun getAll(): List<EacEpisode>

    // Clients with an EAC episode left incomplete/stopped — candidates for a follow-up worklist.
    @Query(
        """
        SELECT DISTINCT personUuid FROM eac_episode
        WHERE stage IN ('NOT_STARTED', 'IN_PROGRESS', 'STOPPED')
        """
    )
    suspend fun personsWithUnfinishedEac(): List<String>
}
