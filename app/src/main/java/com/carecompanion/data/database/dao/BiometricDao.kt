package com.carecompanion.data.database.dao

import androidx.room.*
import com.carecompanion.data.database.entities.Biometric

@Dao
interface BiometricDao {
    @Query("SELECT * FROM biometric WHERE personUuid = :personUuid") suspend fun getByPersonUuid(personUuid: String): List<Biometric>
    @Query("SELECT COUNT(*) FROM biometric WHERE personUuid = :personUuid") suspend fun countByPersonUuid(personUuid: String): Int
    @Query("SELECT * FROM biometric WHERE personUuid = :personUuid AND biometricType = :fingerType")
    suspend fun getByPersonUuidAndType(personUuid: String, fingerType: String): Biometric?
    @Query("SELECT * FROM biometric") suspend fun getAll(): List<Biometric>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(biometric: Biometric)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(biometrics: List<Biometric>)
    @Query("DELETE FROM biometric WHERE personUuid = :personUuid") suspend fun deleteByPersonUuid(personUuid: String)
    @Query("SELECT COUNT(*) FROM biometric") suspend fun getCount(): Int
    @Query("DELETE FROM biometric") suspend fun deleteAll()
}