package com.carecompanion.data.database.dao

import androidx.room.*
import com.carecompanion.data.database.entities.Biometric

@Dao
interface BiometricDao {
        // For verification: hash match for specific person and finger
        @Query("""
            SELECT * FROM biometric 
            WHERE hashed = :hash 
            AND personUuid = :personUuid
            AND (
                UPPER(REPLACE(REPLACE(COALESCE(biometricType, ''), ' ', '_'), '-', '_')) = :fingerType
                OR REPLACE(UPPER(REPLACE(REPLACE(COALESCE(templateType, ''), ' ', '_'), '-', '_')), '_FINGER', '') = :fingerType
            )
            AND (archived IS NULL OR archived = 0)
            AND (:facilityId IS NULL OR facilityId = :facilityId)
            LIMIT 1
        """)
        suspend fun findByTemplateHashForPersonAndFinger(hash: String, personUuid: String, fingerType: String, facilityId: Long?): Biometric?

        // For verification: get all by person and finger (for fallback)
        @Query("""
            SELECT * FROM biometric 
            WHERE personUuid = :personUuid
            AND (
                UPPER(REPLACE(REPLACE(COALESCE(biometricType, ''), ' ', '_'), '-', '_')) = :fingerType
                OR REPLACE(UPPER(REPLACE(REPLACE(COALESCE(templateType, ''), ' ', '_'), '-', '_')), '_FINGER', '') = :fingerType
            )
            AND (archived IS NULL OR archived = 0)
            AND (:facilityId IS NULL OR facilityId = :facilityId)
        """)
        suspend fun getAllByPersonAndFinger(personUuid: String, fingerType: String, facilityId: Long?): List<Biometric>
    @Query("SELECT * FROM biometric WHERE personUuid = :personUuid AND COALESCE(archived, 0) = 0")
    suspend fun getByPersonUuid(personUuid: String): List<Biometric>

    @Query("SELECT COUNT(*) FROM biometric WHERE personUuid = :personUuid AND COALESCE(archived, 0) = 0")
    suspend fun countByPersonUuid(personUuid: String): Int

    @Query("SELECT * FROM biometric WHERE personUuid = :personUuid AND biometricType = :fingerType AND COALESCE(archived, 0) = 0")
    suspend fun getByPersonUuidAndType(personUuid: String, fingerType: String): Biometric?

    @Query("SELECT * FROM biometric WHERE COALESCE(archived, 0) = 0")
    suspend fun getAll(): List<Biometric>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(biometric: Biometric)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(biometrics: List<Biometric>)

    @Query("DELETE FROM biometric WHERE personUuid = :personUuid")
    suspend fun deleteByPersonUuid(personUuid: String)

    @Query("SELECT COUNT(*) FROM biometric WHERE COALESCE(archived, 0) = 0")
    suspend fun getCount(): Int

    @Query("DELETE FROM biometric")
    suspend fun deleteAll()

    // New: Find by template hash (fast match)
    @Query("""
        SELECT * FROM biometric 
        WHERE hashed = :hash 
        AND (archived IS NULL OR archived = 0)
        AND (:facilityId IS NULL OR facilityId = :facilityId)
        LIMIT 1
    """)
    suspend fun findByTemplateHash(hash: String, facilityId: Long?): Biometric?

    @Query("SELECT * FROM biometric WHERE hashed = :hash AND COALESCE(archived, 0) = 0 LIMIT 1")
    suspend fun findByTemplateHashAcrossAllFacilities(hash: String): Biometric?

    @Query("SELECT * FROM biometric WHERE (hashed IS NULL OR hashed = '') AND COALESCE(archived, 0) = 0 LIMIT :limit")
    suspend fun getBiometricsMissingHash(limit: Int): List<Biometric>

    @Query("UPDATE biometric SET hashed = :hash WHERE id = :id")
    suspend fun updateHashById(id: String, hash: String)

    // New: Get all by facility (for fallback matching)
    @Query("""
        SELECT * FROM biometric 
        WHERE (archived IS NULL OR archived = 0)
        AND (:facilityId IS NULL OR facilityId = :facilityId)
    """)
    suspend fun getAllByFacility(facilityId: Long?): List<Biometric>

    @Query("SELECT * FROM biometric WHERE COALESCE(archived, 0) = 0")
    suspend fun getAllBiometrics(): List<Biometric>
}