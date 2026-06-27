package com.carecompanion.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.carecompanion.data.database.entities.ViralLoadHistory
import kotlinx.coroutines.flow.Flow

/** One row of the VL-type breakdown (Baseline/Routine/Post-EAC/PMTCT/…). */
data class VlCategoryCount(val category: String, val count: Int)

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

    @Query(
        """
        SELECT *
        FROM viral_load_history
        WHERE personUuid IN (:personUuids)
        ORDER BY personUuid, resultDate, assayedDate, sampleDate
        """
    )
    suspend fun getByPersonUuids(personUuids: List<String>): List<ViralLoadHistory>

    // VL-type breakdown across stored history (e.g. how many Routine vs Post-EAC vs PMTCT samples).
    @Query(
        """
        SELECT COALESCE(vlCategory, 'Unspecified') AS category, COUNT(*) AS count
        FROM viral_load_history
        GROUP BY COALESCE(vlCategory, 'Unspecified')
        ORDER BY count DESC
        """
    )
    fun observeVlCategoryCounts(): Flow<List<VlCategoryCount>>

    // Most recent VL of a specific type for one patient — e.g. has a Post-EAC confirmation VL been
    // done after an unsuppressed result? Used by the AI to detect VL-cascade gaps.
    @Query(
        """
        SELECT * FROM viral_load_history
        WHERE personUuid = :personUuid AND vlCategory = :category
        ORDER BY resultDate DESC, assayedDate DESC, sampleDate DESC
        LIMIT 1
        """
    )
    suspend fun latestByCategory(personUuid: String, category: String): ViralLoadHistory?
}
