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

    // ── IIT (Interruption in Treatment) queries ───────────────────────────────
    // PEPFAR definition: patient on ART whose latest nextAppointment < (today - 28 days)

    /**
     * All facilities — reactive Flow of IIT clients whose most recent ART pharmacy
     * record has a nextAppointment older than [cutoffDate] (today minus 28 days).
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
            ap.visitDate    AS lastVisitDate,
            ap.nextAppointment,
            ap.dsdModel,
            ap.refillPeriod
        FROM patient_person p
        INNER JOIN art_pharmacy ap
            ON p.uuid = ap.personUuid
        WHERE p.isActive = 1
          AND ap.nextAppointment IS NOT NULL
          AND ap.nextAppointment < :cutoffDate
          AND ap.visitDate = (
              SELECT MAX(visitDate) FROM art_pharmacy WHERE personUuid = p.uuid
          )
        GROUP BY p.uuid
        ORDER BY ap.nextAppointment ASC
    """)
    fun observeIITClients(cutoffDate: Date): Flow<List<IITClient>>

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
          AND ap.nextAppointment < :cutoffDate
          AND ap.visitDate = (
              SELECT MAX(visitDate) FROM art_pharmacy WHERE personUuid = p.uuid
          )
        GROUP BY p.uuid
        ORDER BY ap.nextAppointment ASC
    """)
    fun observeIITClientsByFacility(cutoffDate: Date, facilityId: Long): Flow<List<IITClient>>

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
            ap.visitDate    AS lastVisitDate,
            ap.nextAppointment,
            ap.dsdModel,
            ap.refillPeriod
        FROM patient_person p
        INNER JOIN art_pharmacy ap
            ON p.uuid = ap.personUuid
        WHERE p.isActive = 1
          AND ap.nextAppointment IS NOT NULL
          AND ap.nextAppointment < :cutoffDate
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
        ORDER BY ap.nextAppointment ASC
    """)
    fun observeIITSearch(q: String, cutoffDate: Date): Flow<List<IITClient>>

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
          AND ap.nextAppointment < :cutoffDate
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
        ORDER BY ap.nextAppointment ASC
    """)
    fun observeIITSearchByFacility(q: String, cutoffDate: Date, facilityId: Long): Flow<List<IITClient>>

    @Query("DELETE FROM art_pharmacy") suspend fun deleteAll()
}