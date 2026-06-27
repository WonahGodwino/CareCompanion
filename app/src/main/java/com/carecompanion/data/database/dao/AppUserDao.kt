package com.carecompanion.data.database.dao

import androidx.room.*
import com.carecompanion.data.database.entities.AppUser
import java.util.Date

@Dao
interface AppUserDao {

    @Query("SELECT COUNT(*) FROM app_user WHERE isActive = 1")
    suspend fun countActiveUsers(): Int

    @Query("SELECT * FROM app_user WHERE username = :username AND isActive = 1 LIMIT 1")
    suspend fun findByUsername(username: String): AppUser?

    @Query("SELECT * FROM app_user WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): AppUser?

    @Query("SELECT * FROM app_user WHERE isActive = 1 ORDER BY fullName ASC")
    suspend fun getAllActive(): List<AppUser>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: AppUser): Long

    @Query("UPDATE app_user SET lastLoginAt = :timestamp WHERE id = :id")
    suspend fun updateLastLogin(id: Long, timestamp: Date)

    @Query("UPDATE app_user SET isActive = 0 WHERE id = :id")
    suspend fun deactivate(id: Long)

    @Query("UPDATE app_user SET hashedPin = :hashedPin WHERE id = :id")
    suspend fun updatePin(id: Long, hashedPin: String)

    @Query("UPDATE app_user SET fullName = :fullName, role = :role WHERE id = :id")
    suspend fun updateProfile(id: Long, fullName: String, role: String)

    @Query("DELETE FROM app_user")
    suspend fun deleteAll()
}
