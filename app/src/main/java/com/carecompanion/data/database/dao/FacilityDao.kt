package com.carecompanion.data.database.dao

import androidx.room.*
import com.carecompanion.data.database.entities.Facility

@Dao
interface FacilityDao {
    @Query("SELECT * FROM facility ORDER BY name ASC")
    suspend fun getAll(): List<Facility>

    @Query("SELECT * FROM facility WHERE id = :id")
    suspend fun getById(id: Long): Facility?

    @Query("SELECT * FROM facility WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): Facility?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(facilities: List<Facility>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(facility: Facility)

    @Query("UPDATE facility SET isActive = 0")
    suspend fun clearActive()

    @Query("UPDATE facility SET isActive = 1 WHERE id = :id")
    suspend fun setActive(id: Long)

    @Query("SELECT COUNT(*) FROM facility")
    suspend fun getCount(): Int
}
