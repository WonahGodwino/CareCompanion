package com.carecompanion.data.database.dao

import androidx.room.*
import com.carecompanion.data.database.entities.Biometric

@Dao
interface BiometricDao {
    // For verification: get all by person and finger (template-based)
    @Query("""
        SELECT * FROM biometric
        WHERE personUuid = :personUuid
        AND (
            REPLACE(UPPER(REPLACE(REPLACE(COALESCE(biometricType, ''), ' ', '_'), '-', '_')), '_FINGER', '') = :fingerType
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

    // For 1:N identification — full database scan
    @Query("SELECT * FROM biometric WHERE COALESCE(archived, 0) = 0")
    suspend fun getAllBiometrics(): List<Biometric>

    // Facility-scoped 1:N identification — searched first for performance (PEPFAR local-first pattern)
    @Query("SELECT * FROM biometric WHERE COALESCE(archived, 0) = 0 AND facilityId = :facilityId")
    suspend fun getAllBiometricsByFacility(facilityId: Long): List<Biometric>

    // Quality-filtered 1:N — skips noisy low-quality templates to reduce false-positive risk
    @Query("SELECT * FROM biometric WHERE COALESCE(archived, 0) = 0 AND (imageQuality IS NULL OR imageQuality >= :minQuality)")
    suspend fun getAllBiometricsAboveQuality(minQuality: Int): List<Biometric>

    // Most recent biometric per patient — used for recapture staleness check
    @Query("""
        SELECT * FROM biometric
        WHERE personUuid = :personUuid AND COALESCE(archived, 0) = 0
        ORDER BY COALESCE(lastModifiedDate, enrollmentDate, lastSyncDate) DESC
        LIMIT 1
    """)
    suspend fun getLatestBiometricForPerson(personUuid: String): Biometric?

    // All templates for a patient across all fingers — used for multi-finger fallback in verification
    @Query("SELECT * FROM biometric WHERE personUuid = :personUuid AND COALESCE(archived, 0) = 0")
    suspend fun getAllByPerson(personUuid: String): List<Biometric>
}