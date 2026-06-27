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
    @Query("SELECT * FROM patient_person WHERE uuid = :uuid")
    suspend fun getByUuid(uuid: String): Patient?

    // Added: lookup by emrId (string) and personUuid
    @Query("SELECT * FROM patient_person WHERE emrId = :emrId LIMIT 1")
    suspend fun getByEmrId(emrId: String?): Patient?

    @Query("SELECT * FROM patient_person WHERE person_uuid = :personUuid LIMIT 1")
    suspend fun getByPersonUuid(personUuid: String): Patient?
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

    // ── Patients without any active biometric ──────────────────────────────────

    @Query("""
        SELECT
            p.uuid, p.hospitalNumber, p.firstName, p.surname, p.fullName,
            p.sex, p.dateOfBirth, p.currentStatus, p.artStartDate, p.facilityId
        FROM patient_person p
        WHERE p.isActive = 1
          AND NOT EXISTS (
              SELECT 1 FROM biometric b
              WHERE b.personUuid = p.uuid AND COALESCE(b.archived,0) = 0
          )
        ORDER BY p.surname, p.firstName
    """)
    fun observeNoBiometricPatients(): kotlinx.coroutines.flow.Flow<List<com.carecompanion.data.database.entities.NoBiometricEntry>>

    @Query("""
        SELECT
            p.uuid, p.hospitalNumber, p.firstName, p.surname, p.fullName,
            p.sex, p.dateOfBirth, p.currentStatus, p.artStartDate, p.facilityId
        FROM patient_person p
        WHERE p.isActive = 1
          AND p.facilityId = :facilityId
          AND NOT EXISTS (
              SELECT 1 FROM biometric b
              WHERE b.personUuid = p.uuid AND COALESCE(b.archived,0) = 0
          )
        ORDER BY p.surname, p.firstName
    """)
    fun observeNoBiometricPatientsByFacility(facilityId: Long): kotlinx.coroutines.flow.Flow<List<com.carecompanion.data.database.entities.NoBiometricEntry>>

    @Query("""
        SELECT
            p.uuid, p.hospitalNumber, p.firstName, p.surname, p.fullName,
            p.sex, p.dateOfBirth, p.currentStatus, p.artStartDate, p.facilityId
        FROM patient_person p
        WHERE p.isActive = 1
          AND NOT EXISTS (
              SELECT 1 FROM biometric b
              WHERE b.personUuid = p.uuid AND COALESCE(b.archived,0) = 0
          )
          AND (
              p.hospitalNumber LIKE '%' || :q || '%'
              OR p.firstName   LIKE '%' || :q || '%'
              OR p.surname     LIKE '%' || :q || '%'
              OR p.fullName    LIKE '%' || :q || '%'
          )
        ORDER BY p.surname, p.firstName
    """)
    fun observeNoBiometricSearch(q: String): kotlinx.coroutines.flow.Flow<List<com.carecompanion.data.database.entities.NoBiometricEntry>>

    // ── VL Cascade counts (reactive) ───────────────────────────────────────────

    // TX_CURR (Current on Treatment) and the VL cascade share ONE cohort: on-ART (currentStatus holds
    // WINCO's care_category — ACTIVE/IIT ⇔ raw ART_START/ART_TRANSFER_IN), ALIVE, and pharmacy coverage
    // NOT lapsed (latest visitDate + refillPeriod + 28 days >= today). Mirrors WINCO _is_tx_curr_active
    // and Patient.calculatePatientStatus. Excludes lapsed-coverage (IIT), TRANSFER_OUT, DEATH, STOPPED,
    // and non-ART rows. Dates are stored as epoch-millis; 86400000 = ms/day.
    @Query("""
        SELECT COUNT(DISTINCT p.uuid) FROM patient_person p
        INNER JOIN art_pharmacy ap ON p.uuid = ap.personUuid
        WHERE (p.deceased IS NULL OR p.deceased = 0)
          AND UPPER(REPLACE(REPLACE(COALESCE(p.currentStatus,''),' ','_'),'-','_'))
              IN ('ACTIVE','IIT','ART','ART_START','ART_TRANSFER_IN','ACTIVE_TX_CURR')
          AND ap.visitDate = (SELECT MAX(visitDate) FROM art_pharmacy WHERE personUuid = p.uuid)
          AND (ap.visitDate + ((COALESCE(ap.refillPeriod,0) + 28) * 86400000)) >= :todayMs
    """)
    fun observeTxCurrCount(todayMs: Long): kotlinx.coroutines.flow.Flow<Int>

    @Query("""
        SELECT COUNT(DISTINCT p.uuid) FROM patient_person p
        INNER JOIN art_pharmacy ap ON p.uuid = ap.personUuid
        WHERE p.facilityId = :facilityId
          AND (p.deceased IS NULL OR p.deceased = 0)
          AND UPPER(REPLACE(REPLACE(COALESCE(p.currentStatus,''),' ','_'),'-','_'))
              IN ('ACTIVE','IIT','ART','ART_START','ART_TRANSFER_IN','ACTIVE_TX_CURR')
          AND ap.visitDate = (SELECT MAX(visitDate) FROM art_pharmacy WHERE personUuid = p.uuid)
          AND (ap.visitDate + ((COALESCE(ap.refillPeriod,0) + 28) * 86400000)) >= :todayMs
    """)
    fun observeTxCurrCountByFacility(todayMs: Long, facilityId: Long): kotlinx.coroutines.flow.Flow<Int>

    // EAC-relevant cohort: TX_CURR clients whose latest VL is unsuppressed (>=1000). Every member is
    // active, so EacGapEngine runs with isTxCurr = true. Drives the EAC worklist.
    @Query("""
        SELECT p.* FROM patient_person p
        INNER JOIN art_pharmacy ap ON p.uuid = ap.personUuid
        WHERE (p.deceased IS NULL OR p.deceased = 0)
          AND UPPER(REPLACE(REPLACE(COALESCE(p.currentStatus,''),' ','_'),'-','_'))
              IN ('ACTIVE','IIT','ART','ART_START','ART_TRANSFER_IN','ACTIVE_TX_CURR')
          AND ap.visitDate = (SELECT MAX(visitDate) FROM art_pharmacy WHERE personUuid = p.uuid)
          AND (ap.visitDate + ((COALESCE(ap.refillPeriod,0) + 28) * 86400000)) >= :todayMs
          AND p.lastViralLoadResult IS NOT NULL AND p.lastViralLoadResult >= 1000
        ORDER BY p.lastViralLoadResult DESC
    """)
    fun observeUnsuppressedTxCurr(todayMs: Long): kotlinx.coroutines.flow.Flow<List<com.carecompanion.data.database.entities.Patient>>

    @Query("""
        SELECT COUNT(DISTINCT p.uuid) FROM patient_person p
        INNER JOIN art_pharmacy ap ON p.uuid = ap.personUuid
        WHERE (p.deceased IS NULL OR p.deceased = 0)
          AND UPPER(REPLACE(REPLACE(COALESCE(p.currentStatus,''),' ','_'),'-','_'))
              IN ('ACTIVE','IIT','ART','ART_START','ART_TRANSFER_IN','ACTIVE_TX_CURR')
          AND ap.visitDate = (SELECT MAX(visitDate) FROM art_pharmacy WHERE personUuid = p.uuid)
          AND (ap.visitDate + ((COALESCE(ap.refillPeriod,0) + 28) * 86400000)) >= :todayMs
          AND p.lastViralLoadDate IS NOT NULL
    """)
    fun observeVlTestedCount(todayMs: Long): kotlinx.coroutines.flow.Flow<Int>

    @Query("""
        SELECT COUNT(DISTINCT p.uuid) FROM patient_person p
        INNER JOIN art_pharmacy ap ON p.uuid = ap.personUuid
        WHERE (p.deceased IS NULL OR p.deceased = 0)
          AND UPPER(REPLACE(REPLACE(COALESCE(p.currentStatus,''),' ','_'),'-','_'))
              IN ('ACTIVE','IIT','ART','ART_START','ART_TRANSFER_IN','ACTIVE_TX_CURR')
          AND ap.visitDate = (SELECT MAX(visitDate) FROM art_pharmacy WHERE personUuid = p.uuid)
          AND (ap.visitDate + ((COALESCE(ap.refillPeriod,0) + 28) * 86400000)) >= :todayMs
          AND p.lastViralLoadDate IS NOT NULL
          AND (p.lastViralLoadResult IS NOT NULL OR p.lastViralLoadResultRaw IS NOT NULL)
    """)
    fun observeVlResultReceivedCount(todayMs: Long): kotlinx.coroutines.flow.Flow<Int>

    @Query("""
        SELECT COUNT(DISTINCT p.uuid) FROM patient_person p
        INNER JOIN art_pharmacy ap ON p.uuid = ap.personUuid
        WHERE (p.deceased IS NULL OR p.deceased = 0)
          AND UPPER(REPLACE(REPLACE(COALESCE(p.currentStatus,''),' ','_'),'-','_'))
              IN ('ACTIVE','IIT','ART','ART_START','ART_TRANSFER_IN','ACTIVE_TX_CURR')
          AND ap.visitDate = (SELECT MAX(visitDate) FROM art_pharmacy WHERE personUuid = p.uuid)
          AND (ap.visitDate + ((COALESCE(ap.refillPeriod,0) + 28) * 86400000)) >= :todayMs
          AND p.lastViralLoadResult IS NOT NULL AND p.lastViralLoadResult < 1000
    """)
    fun observeVlSuppressedCount(todayMs: Long): kotlinx.coroutines.flow.Flow<Int>

    @Query("""
        SELECT COUNT(DISTINCT p.uuid) FROM patient_person p
        INNER JOIN art_pharmacy ap ON p.uuid = ap.personUuid
        WHERE (p.deceased IS NULL OR p.deceased = 0)
          AND UPPER(REPLACE(REPLACE(COALESCE(p.currentStatus,''),' ','_'),'-','_'))
              IN ('ACTIVE','IIT','ART','ART_START','ART_TRANSFER_IN','ACTIVE_TX_CURR')
          AND ap.visitDate = (SELECT MAX(visitDate) FROM art_pharmacy WHERE personUuid = p.uuid)
          AND (ap.visitDate + ((COALESCE(ap.refillPeriod,0) + 28) * 86400000)) >= :todayMs
          AND p.lastViralLoadResult IS NOT NULL AND p.lastViralLoadResult >= 1000
    """)
    fun observeVlUnsuppressedCount(todayMs: Long): kotlinx.coroutines.flow.Flow<Int>

    // ── TPT screen — active ART patients with TB screening data ───────────────

    @Query("""
        SELECT
            p.uuid, p.hospitalNumber, p.firstName, p.surname, p.fullName,
            p.sex, p.dateOfBirth, p.currentStatus, p.artStartDate,
            p.lastTbScreeningDate, p.lastTbScreeningStatus,
            p.facilityId,
            (SELECT ap.iptType FROM art_pharmacy ap WHERE ap.personUuid = p.uuid
             ORDER BY ap.visitDate DESC LIMIT 1) AS iptType
        FROM patient_person p
        WHERE p.isActive = 1
        ORDER BY
            CASE WHEN p.lastTbScreeningDate IS NULL THEN 0 ELSE 1 END,
            p.lastTbScreeningDate ASC,
            p.surname, p.firstName
    """)
    fun observeTptPatients(): kotlinx.coroutines.flow.Flow<List<com.carecompanion.data.database.entities.TptEntry>>

    @Query("""
        SELECT
            p.uuid, p.hospitalNumber, p.firstName, p.surname, p.fullName,
            p.sex, p.dateOfBirth, p.currentStatus, p.artStartDate,
            p.lastTbScreeningDate, p.lastTbScreeningStatus,
            p.facilityId,
            (SELECT ap.iptType FROM art_pharmacy ap WHERE ap.personUuid = p.uuid
             ORDER BY ap.visitDate DESC LIMIT 1) AS iptType
        FROM patient_person p
        WHERE p.isActive = 1
          AND p.facilityId = :facilityId
        ORDER BY
            CASE WHEN p.lastTbScreeningDate IS NULL THEN 0 ELSE 1 END,
            p.lastTbScreeningDate ASC,
            p.surname, p.firstName
    """)
    fun observeTptPatientsByFacility(facilityId: Long): kotlinx.coroutines.flow.Flow<List<com.carecompanion.data.database.entities.TptEntry>>
}