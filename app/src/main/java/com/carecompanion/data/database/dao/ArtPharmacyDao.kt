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
    // Definition: patient whose expected return date (nextAppointment + refillPeriod) has passed.
    // refillPeriod is in days (90, 180, etc.); defaults to 28 if not set.

    /**
     * All facilities — reactive Flow of IIT clients whose most recent ART pharmacy
     * record's expected return date (nextAppointment + refillPeriod days) is before today.
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
        WHERE p.isActive = 1
          AND ap.nextAppointment IS NOT NULL
          AND ap.visitDate = (
              SELECT MAX(visitDate) FROM art_pharmacy WHERE personUuid = p.uuid
          )
          AND ap.nextAppointment + (28 * 86400000) < :todayMs
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
        WHERE p.isActive = 1
          AND p.facilityId = :facilityId
          AND ap.nextAppointment IS NOT NULL
          AND ap.visitDate = (
              SELECT MAX(visitDate) FROM art_pharmacy WHERE personUuid = p.uuid
          )
          AND ap.nextAppointment + (28 * 86400000) < :todayMs
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