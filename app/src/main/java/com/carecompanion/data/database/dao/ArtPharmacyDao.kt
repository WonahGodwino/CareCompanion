package com.carecompanion.data.database.dao

import androidx.room.*
import com.carecompanion.data.database.entities.ArtPharmacy
import com.carecompanion.data.database.entities.IITClient
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface ArtPharmacyDao {
    @Query("SELECT * FROM art_pharmacy WHERE personUuid = :personUuid ORDER BY visitDate DESC")
    suspend fun getByPersonUuid(personUuid: String): List<ArtPharmacy>
    @Query("SELECT * FROM art_pharmacy WHERE personUuid IN (:personUuids)")
    suspend fun getByPersonUuids(personUuids: List<String>): List<ArtPharmacy>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(artPharmacy: ArtPharmacy)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(artPharmacies: List<ArtPharmacy>)
    @Query("SELECT COUNT(*) FROM art_pharmacy") suspend fun getCount(): Int
    @Query("DELETE FROM art_pharmacy WHERE personUuid = :personUuid") suspend fun deleteByPersonUuid(personUuid: String)

    // ── IIT (Interruption in Treatment) queries ───────────────────────────────
    // Definition (PEPFAR TX_ML, consistent with WINCO _is_tx_curr_active and Patient.calculatePatientStatus):
    // an on-ART, alive patient whose pharmacy coverage has LAPSED — latest visitDate + refillPeriod + 28
    // days < today. (Was previously nextAppointment + 28, which is a different, inconsistent formula.)
    // Excludes TRANSFER_OUT / DEATH / STOPPED and non-ART rows. refillPeriod is in days.

    /**
     * All facilities — reactive Flow of IIT clients (coverage lapsed: visitDate + refillPeriod + 28 < today).
     * [todayMs] should be System.currentTimeMillis() or today as milliseconds since epoch.
     */
    @Query("""
        SELECT
            p.uuid          AS patientId,
            p.uuid,
            p.hospitalNumber,
            p.firstName,
            p.surname,
            p.fullName,
            p.sex,
            p.dateOfBirth,
            p.facilityId,
            p.currentStatus,
            p.currentStatusDate,
            ap.visitDate    AS lastVisitDate,
            ap.nextAppointment,
            ap.dsdModel,
            ap.refillPeriod
        FROM patient_person p
        INNER JOIN art_pharmacy ap
            ON p.uuid = ap.personUuid
        WHERE (p.deceased IS NULL OR p.deceased = 0)
          AND UPPER(REPLACE(REPLACE(COALESCE(p.currentStatus,''),' ','_'),'-','_'))
              IN ('ACTIVE','IIT','ART','ART_START','ART_TRANSFER_IN','ACTIVE_TX_CURR')
          AND ap.visitDate = (
              SELECT MAX(visitDate) FROM art_pharmacy WHERE personUuid = p.uuid
          )
          AND (ap.visitDate + ((COALESCE(ap.refillPeriod,0) + 28) * 86400000)) < :todayMs
        GROUP BY p.uuid
        ORDER BY ap.nextAppointment ASC
    """)
    fun observeIITClients(todayMs: Long): Flow<List<IITClient>>

    /**
     * Facility-scoped variant of [observeIITClients].
     */
    @Query("""
        SELECT
            p.uuid          AS patientId,
            p.uuid,
            p.hospitalNumber,
            p.firstName,
            p.surname,
            p.fullName,
            p.sex,
            p.dateOfBirth,
            p.facilityId,
            p.currentStatus,
            p.currentStatusDate,
            ap.visitDate    AS lastVisitDate,
            ap.nextAppointment,
            ap.dsdModel,
            ap.refillPeriod
        FROM patient_person p
        INNER JOIN art_pharmacy ap
            ON p.uuid = ap.personUuid
        WHERE p.facilityId = :facilityId
          AND (p.deceased IS NULL OR p.deceased = 0)
          AND UPPER(REPLACE(REPLACE(COALESCE(p.currentStatus,''),' ','_'),'-','_'))
              IN ('ACTIVE','IIT','ART','ART_START','ART_TRANSFER_IN','ACTIVE_TX_CURR')
          AND ap.visitDate = (
              SELECT MAX(visitDate) FROM art_pharmacy WHERE personUuid = p.uuid
          )
          AND (ap.visitDate + ((COALESCE(ap.refillPeriod,0) + 28) * 86400000)) < :todayMs
        GROUP BY p.uuid
        ORDER BY ap.nextAppointment ASC
    """)
    fun observeIITClientsByFacility(todayMs: Long, facilityId: Long): Flow<List<IITClient>>

    /**
     * Search within IIT clients (all facilities).
     */
    @Query("""
        SELECT
            p.uuid          AS patientId,
            p.uuid,
            p.hospitalNumber,
            p.firstName,
            p.surname,
            p.fullName,
            p.sex,
            p.dateOfBirth,
            p.facilityId,
            p.currentStatus,
            p.currentStatusDate,
            ap.visitDate    AS lastVisitDate,
            ap.nextAppointment,
            ap.dsdModel,
            ap.refillPeriod
        FROM patient_person p
        INNER JOIN art_pharmacy ap
            ON p.uuid = ap.personUuid
        WHERE p.isActive = 1
          AND ap.nextAppointment IS NOT NULL
          AND ap.visitDate = (
              SELECT MAX(visitDate) FROM art_pharmacy WHERE personUuid = p.uuid
          )
          AND ap.nextAppointment + (28 * 86400000) < :todayMs
          AND (
              p.hospitalNumber LIKE '%' || :q || '%'
              OR p.firstName   LIKE '%' || :q || '%'
              OR p.surname     LIKE '%' || :q || '%'
              OR p.fullName    LIKE '%' || :q || '%'
          )
        GROUP BY p.uuid
        ORDER BY ap.nextAppointment ASC
    """)
    fun observeIITSearch(q: String, todayMs: Long): Flow<List<IITClient>>

    /**
     * Search within IIT clients scoped to a facility.
     */
    @Query("""
        SELECT
            p.uuid          AS patientId,
            p.uuid,
            p.hospitalNumber,
            p.firstName,
            p.surname,
            p.fullName,
            p.sex,
            p.dateOfBirth,
            p.facilityId,
            p.currentStatus,
            p.currentStatusDate,
            ap.visitDate    AS lastVisitDate,
            ap.nextAppointment,
            ap.dsdModel,
            ap.refillPeriod
        FROM patient_person p
        INNER JOIN art_pharmacy ap
            ON p.uuid = ap.personUuid
        WHERE p.isActive = 1
          AND p.facilityId = :facilityId
          AND ap.nextAppointment IS NOT NULL
          AND ap.visitDate = (
              SELECT MAX(visitDate) FROM art_pharmacy WHERE personUuid = p.uuid
          )
          AND ap.nextAppointment + (28 * 86400000) < :todayMs
          AND (
              p.hospitalNumber LIKE '%' || :q || '%'
              OR p.firstName   LIKE '%' || :q || '%'
              OR p.surname     LIKE '%' || :q || '%'
              OR p.fullName    LIKE '%' || :q || '%'
          )
        GROUP BY p.uuid
        ORDER BY ap.nextAppointment ASC
    """)
    fun observeIITSearchByFacility(q: String, todayMs: Long, facilityId: Long): Flow<List<IITClient>>

    // ── Missed Appointments queries ────────────────────────────────────────────
    // Definition: patient whose nextAppointment date has already passed — regardless
    // of how many days ago (includes IIT-range clients as well as 1–27 day misses).
    @Query("""
        SELECT
            p.uuid          AS patientId,
            p.uuid,
            p.hospitalNumber,
            p.firstName,
            p.surname,
            p.fullName,
            p.sex,
            p.dateOfBirth,
            p.facilityId,
            p.currentStatus,
            p.currentStatusDate,
            ap.visitDate    AS lastVisitDate,
            ap.nextAppointment,
            ap.dsdModel,
            ap.refillPeriod
        FROM patient_person p
        INNER JOIN art_pharmacy ap
            ON p.uuid = ap.personUuid
        WHERE p.isActive = 1
          AND ap.nextAppointment IS NOT NULL
          AND ap.visitDate = (
              SELECT MAX(visitDate) FROM art_pharmacy WHERE personUuid = p.uuid
          )
          AND ap.nextAppointment < :todayMs
        GROUP BY p.uuid
        ORDER BY ap.nextAppointment ASC
    """)
    fun observeMissedApptClients(todayMs: Long): Flow<List<IITClient>>

    @Query("""
        SELECT
            p.uuid          AS patientId,
            p.uuid,
            p.hospitalNumber,
            p.firstName,
            p.surname,
            p.fullName,
            p.sex,
            p.dateOfBirth,
            p.facilityId,
            p.currentStatus,
            p.currentStatusDate,
            ap.visitDate    AS lastVisitDate,
            ap.nextAppointment,
            ap.dsdModel,
            ap.refillPeriod
        FROM patient_person p
        INNER JOIN art_pharmacy ap
            ON p.uuid = ap.personUuid
        WHERE p.isActive = 1
          AND p.facilityId = :facilityId
          AND ap.nextAppointment IS NOT NULL
          AND ap.visitDate = (
              SELECT MAX(visitDate) FROM art_pharmacy WHERE personUuid = p.uuid
          )
          AND ap.nextAppointment < :todayMs
        GROUP BY p.uuid
        ORDER BY ap.nextAppointment ASC
    """)
    fun observeMissedApptClientsByFacility(todayMs: Long, facilityId: Long): Flow<List<IITClient>>

    @Query("""
        SELECT
            p.uuid          AS patientId,
            p.uuid,
            p.hospitalNumber,
            p.firstName,
            p.surname,
            p.fullName,
            p.sex,
            p.dateOfBirth,
            p.facilityId,
            p.currentStatus,
            p.currentStatusDate,
            ap.visitDate    AS lastVisitDate,
            ap.nextAppointment,
            ap.dsdModel,
            ap.refillPeriod
        FROM patient_person p
        INNER JOIN art_pharmacy ap
            ON p.uuid = ap.personUuid
        WHERE p.isActive = 1
          AND ap.nextAppointment IS NOT NULL
          AND ap.visitDate = (
              SELECT MAX(visitDate) FROM art_pharmacy WHERE personUuid = p.uuid
          )
          AND ap.nextAppointment < :todayMs
          AND (
              p.hospitalNumber LIKE '%' || :q || '%'
              OR p.firstName   LIKE '%' || :q || '%'
              OR p.surname     LIKE '%' || :q || '%'
              OR p.fullName    LIKE '%' || :q || '%'
          )
        GROUP BY p.uuid
        ORDER BY ap.nextAppointment ASC
    """)
    fun observeMissedApptSearch(q: String, todayMs: Long): Flow<List<IITClient>>

    @Query("""
        SELECT
            p.uuid          AS patientId,
            p.uuid,
            p.hospitalNumber,
            p.firstName,
            p.surname,
            p.fullName,
            p.sex,
            p.dateOfBirth,
            p.facilityId,
            p.currentStatus,
            p.currentStatusDate,
            ap.visitDate    AS lastVisitDate,
            ap.nextAppointment,
            ap.dsdModel,
            ap.refillPeriod
        FROM patient_person p
        INNER JOIN art_pharmacy ap
            ON p.uuid = ap.personUuid
        WHERE p.isActive = 1
          AND p.facilityId = :facilityId
          AND ap.nextAppointment IS NOT NULL
          AND ap.visitDate = (
              SELECT MAX(visitDate) FROM art_pharmacy WHERE personUuid = p.uuid
          )
          AND ap.nextAppointment < :todayMs
          AND (
              p.hospitalNumber LIKE '%' || :q || '%'
              OR p.firstName   LIKE '%' || :q || '%'
              OR p.surname     LIKE '%' || :q || '%'
              OR p.fullName    LIKE '%' || :q || '%'
          )
        GROUP BY p.uuid
        ORDER BY ap.nextAppointment ASC
    """)
    fun observeMissedApptSearchByFacility(q: String, todayMs: Long, facilityId: Long): Flow<List<IITClient>>

    @Query("DELETE FROM art_pharmacy") suspend fun deleteAll()

    // ── Today's Clinic Worklist ────────────────────────────────────────────────
    // Patients whose nextAppointment falls within the current calendar day (WAT).
    // Carries all fields needed to compute due services (VL, TB, biometric) in one trip.

    @Query("""
        SELECT
            p.uuid,
            p.hospitalNumber,
            p.firstName,
            p.surname,
            p.fullName,
            p.sex,
            p.dateOfBirth,
            p.artStartDate,
            p.lastViralLoadDate,
            p.lastViralLoadResult,
            p.lastTbScreeningDate,
            p.lastTbScreeningStatus,
            p.ndrMatchedStatus,
            ap.visitDate    AS lastVisitDate,
            ap.nextAppointment,
            ap.refillPeriod,
            ap.regimenId,
            ap.dsdModel,
            (SELECT COUNT(*) FROM biometric b
             WHERE b.personUuid = p.uuid AND COALESCE(b.archived,0) = 0) AS biometricCount,
            (SELECT MAX(COALESCE(b.lastModifiedDate, b.enrollmentDate, b.lastSyncDate))
             FROM biometric b
             WHERE b.personUuid = p.uuid AND COALESCE(b.archived,0) = 0) AS lastBiometricDate
        FROM patient_person p
        INNER JOIN art_pharmacy ap ON p.uuid = ap.personUuid
        WHERE p.isActive = 1
          AND ap.nextAppointment IS NOT NULL
          AND ap.visitDate = (SELECT MAX(visitDate) FROM art_pharmacy WHERE personUuid = p.uuid)
          AND ap.nextAppointment >= :startOfDayMs
          AND ap.nextAppointment < :endOfDayMs
        GROUP BY p.uuid
        ORDER BY ap.nextAppointment ASC
    """)
    fun observeTodayWorklist(startOfDayMs: Long, endOfDayMs: Long): kotlinx.coroutines.flow.Flow<List<com.carecompanion.data.database.entities.WorklistEntry>>

    @Query("""
        SELECT
            p.uuid,
            p.hospitalNumber,
            p.firstName,
            p.surname,
            p.fullName,
            p.sex,
            p.dateOfBirth,
            p.artStartDate,
            p.lastViralLoadDate,
            p.lastViralLoadResult,
            p.lastTbScreeningDate,
            p.lastTbScreeningStatus,
            p.ndrMatchedStatus,
            ap.visitDate    AS lastVisitDate,
            ap.nextAppointment,
            ap.refillPeriod,
            ap.regimenId,
            ap.dsdModel,
            (SELECT COUNT(*) FROM biometric b
             WHERE b.personUuid = p.uuid AND COALESCE(b.archived,0) = 0) AS biometricCount,
            (SELECT MAX(COALESCE(b.lastModifiedDate, b.enrollmentDate, b.lastSyncDate))
             FROM biometric b
             WHERE b.personUuid = p.uuid AND COALESCE(b.archived,0) = 0) AS lastBiometricDate
        FROM patient_person p
        INNER JOIN art_pharmacy ap ON p.uuid = ap.personUuid
        WHERE p.isActive = 1
          AND p.facilityId = :facilityId
          AND ap.nextAppointment IS NOT NULL
          AND ap.visitDate = (SELECT MAX(visitDate) FROM art_pharmacy WHERE personUuid = p.uuid)
          AND ap.nextAppointment >= :startOfDayMs
          AND ap.nextAppointment < :endOfDayMs
        GROUP BY p.uuid
        ORDER BY ap.nextAppointment ASC
    """)
    fun observeTodayWorklistByFacility(startOfDayMs: Long, endOfDayMs: Long, facilityId: Long): kotlinx.coroutines.flow.Flow<List<com.carecompanion.data.database.entities.WorklistEntry>>

    // ── ART Refill queries ─────────────────────────────────────────────────────
    // Returns ALL active ART patients with their latest pharmacy record (no overdue
    // filter). Eligibility grouping is computed in the ViewModel using
    // ServiceEligibilityEngine — this query simply provides the raw data.

    @Query("""
        SELECT
            p.uuid          AS patientId,
            p.uuid,
            p.hospitalNumber,
            p.firstName,
            p.surname,
            p.fullName,
            p.sex,
            p.dateOfBirth,
            p.facilityId,
            p.currentStatus,
            p.currentStatusDate,
            ap.visitDate    AS lastVisitDate,
            ap.nextAppointment,
            ap.dsdModel,
            ap.refillPeriod
        FROM patient_person p
        INNER JOIN art_pharmacy ap
            ON p.uuid = ap.personUuid
        WHERE p.isActive = 1
          AND ap.visitDate = (
              SELECT MAX(visitDate) FROM art_pharmacy WHERE personUuid = p.uuid
          )
        GROUP BY p.uuid
        ORDER BY ap.visitDate DESC
    """)
    fun observeArtRefillClients(): Flow<List<IITClient>>

    @Query("""
        SELECT
            p.uuid          AS patientId,
            p.uuid,
            p.hospitalNumber,
            p.firstName,
            p.surname,
            p.fullName,
            p.sex,
            p.dateOfBirth,
            p.facilityId,
            p.currentStatus,
            p.currentStatusDate,
            ap.visitDate    AS lastVisitDate,
            ap.nextAppointment,
            ap.dsdModel,
            ap.refillPeriod
        FROM patient_person p
        INNER JOIN art_pharmacy ap
            ON p.uuid = ap.personUuid
        WHERE p.isActive = 1
          AND p.facilityId = :facilityId
          AND ap.visitDate = (
              SELECT MAX(visitDate) FROM art_pharmacy WHERE personUuid = p.uuid
          )
        GROUP BY p.uuid
        ORDER BY ap.visitDate DESC
    """)
    fun observeArtRefillClientsByFacility(facilityId: Long): Flow<List<IITClient>>

    @Query("""
        SELECT
            p.uuid          AS patientId,
            p.uuid,
            p.hospitalNumber,
            p.firstName,
            p.surname,
            p.fullName,
            p.sex,
            p.dateOfBirth,
            p.facilityId,
            p.currentStatus,
            p.currentStatusDate,
            ap.visitDate    AS lastVisitDate,
            ap.nextAppointment,
            ap.dsdModel,
            ap.refillPeriod
        FROM patient_person p
        INNER JOIN art_pharmacy ap
            ON p.uuid = ap.personUuid
        WHERE p.isActive = 1
          AND ap.visitDate = (
              SELECT MAX(visitDate) FROM art_pharmacy WHERE personUuid = p.uuid
          )
          AND (
              p.hospitalNumber LIKE '%' || :q || '%'
              OR p.firstName   LIKE '%' || :q || '%'
              OR p.surname     LIKE '%' || :q || '%'
              OR p.fullName    LIKE '%' || :q || '%'
          )
        GROUP BY p.uuid
        ORDER BY ap.visitDate DESC
    """)
    fun observeArtRefillSearch(q: String): Flow<List<IITClient>>

    @Query("""
        SELECT
            p.uuid          AS patientId,
            p.uuid,
            p.hospitalNumber,
            p.firstName,
            p.surname,
            p.fullName,
            p.sex,
            p.dateOfBirth,
            p.facilityId,
            p.currentStatus,
            p.currentStatusDate,
            ap.visitDate    AS lastVisitDate,
            ap.nextAppointment,
            ap.dsdModel,
            ap.refillPeriod
        FROM patient_person p
        INNER JOIN art_pharmacy ap
            ON p.uuid = ap.personUuid
        WHERE p.isActive = 1
          AND p.facilityId = :facilityId
          AND ap.visitDate = (
              SELECT MAX(visitDate) FROM art_pharmacy WHERE personUuid = p.uuid
          )
          AND (
              p.hospitalNumber LIKE '%' || :q || '%'
              OR p.firstName   LIKE '%' || :q || '%'
              OR p.surname     LIKE '%' || :q || '%'
              OR p.fullName    LIKE '%' || :q || '%'
          )
        GROUP BY p.uuid
        ORDER BY ap.visitDate DESC
    """)
    fun observeArtRefillSearchByFacility(q: String, facilityId: Long): Flow<List<IITClient>>
}