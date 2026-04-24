package com.carecompanion.data.database.dao

import androidx.room.*
import com.carecompanion.data.database.entities.Patient
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface PatientDao {
    // ── One-shot queries (used by sync / profile lookups) ──────────────────
    @Query("SELECT * FROM patient_person WHERE isActive = 1") suspend fun getAllActive(): List<Patient>
    @Query("SELECT * FROM patient_person WHERE isActive = 1 AND facilityId = :facilityId") suspend fun getAllActiveByFacility(facilityId: Long): List<Patient>
    @Query("SELECT * FROM patient_person WHERE uuid = :uuid") suspend fun getByUuid(uuid: String): Patient?
    @Query("SELECT * FROM patient_person WHERE emrPatientId = :emrId LIMIT 1") suspend fun getByEmrPatientId(emrId: Long): Patient?
    @Query("SELECT * FROM patient_person WHERE hospitalNumber LIKE '%'||:q||'%' OR firstName LIKE '%'||:q||'%' OR surname LIKE '%'||:q||'%'")
    suspend fun searchPatients(q: String): List<Patient>
    @Query("SELECT * FROM patient_person WHERE (hospitalNumber LIKE '%'||:q||'%' OR firstName LIKE '%'||:q||'%' OR surname LIKE '%'||:q||'%') AND facilityId = :facilityId")
    suspend fun searchPatientsByFacility(q: String, facilityId: Long): List<Patient>
    @Query("SELECT COUNT(*) FROM patient_person WHERE isActive = 1") suspend fun getActiveCount(): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(patient: Patient)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(patients: List<Patient>)
    @Query("UPDATE patient_person SET isActive = 0 WHERE uuid = :uuid") suspend fun softDelete(uuid: String)
    @Query("SELECT MAX(lastSyncDate) FROM patient_person") suspend fun getLastSyncDate(): Date?

    // ── Reactive Flow queries (Room pushes updates when data changes) ───────
    @Query("SELECT * FROM patient_person WHERE isActive = 1 ORDER BY surname, firstName")
    fun observeAllActive(): Flow<List<Patient>>

    @Query("SELECT * FROM patient_person WHERE isActive = 1 AND facilityId = :facilityId ORDER BY surname, firstName")
    fun observeAllActiveByFacility(facilityId: Long): Flow<List<Patient>>

    @Query("SELECT * FROM patient_person WHERE isActive = 1 AND (hospitalNumber LIKE '%'||:q||'%' OR firstName LIKE '%'||:q||'%' OR surname LIKE '%'||:q||'%' OR fullName LIKE '%'||:q||'%') ORDER BY surname, firstName")
    fun observeSearch(q: String): Flow<List<Patient>>

    @Query("SELECT * FROM patient_person WHERE isActive = 1 AND facilityId = :facilityId AND (hospitalNumber LIKE '%'||:q||'%' OR firstName LIKE '%'||:q||'%' OR surname LIKE '%'||:q||'%' OR fullName LIKE '%'||:q||'%') ORDER BY surname, firstName")
    fun observeSearchByFacility(q: String, facilityId: Long): Flow<List<Patient>>

    @Query("SELECT COUNT(*) FROM patient_person WHERE isActive = 1") fun observeActiveCount(): Flow<Int>
    @Query("SELECT COUNT(*) FROM patient_person WHERE isActive = 1 AND facilityId = :facilityId") fun observeActiveCountByFacility(facilityId: Long): Flow<Int>

    @Query("DELETE FROM patient_person") suspend fun deleteAll()
}