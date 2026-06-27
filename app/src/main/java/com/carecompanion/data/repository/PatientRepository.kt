package com.carecompanion.data.repository

import com.carecompanion.data.database.entities.ArtPharmacy
import com.carecompanion.data.database.entities.Biometric
import com.carecompanion.data.database.entities.IITClient
import com.carecompanion.data.database.entities.NoBiometricEntry
import com.carecompanion.data.database.entities.Patient
import com.carecompanion.data.database.entities.TptEntry
import com.carecompanion.data.database.entities.ViralLoadHistory
import com.carecompanion.data.database.entities.WorklistEntry
import kotlinx.coroutines.flow.Flow
import java.util.Date

interface PatientRepository {
        suspend fun saveBiometric(biometric: com.carecompanion.data.database.entities.Biometric)
    suspend fun getAllActivePatients(): List<Patient>
    suspend fun getAllActiveByFacility(facilityId: Long): List<Patient>
    suspend fun searchPatients(query: String): List<Patient>
    suspend fun searchPatientsByFacility(query: String, facilityId: Long): List<Patient>
    suspend fun getPatientByEmrId(emrId: Long): Patient?
    suspend fun getPatientByUuid(uuid: String): Patient?
    suspend fun getBiometricsForPatient(personUuid: String): List<Biometric>
    suspend fun getAllBiometrics(): List<Biometric>
    suspend fun getArtPharmacyForPatient(personUuid: String): List<ArtPharmacy>
    suspend fun getArtPharmacyForPatients(personUuids: List<String>): List<ArtPharmacy>
    suspend fun getPatientCount(): Int
    suspend fun getBiometricCount(): Int
    // Reactive flows — Room emits a new list whenever the table changes
    fun observeAllActive(): Flow<List<Patient>>
    fun observeAllActiveByFacility(facilityId: Long): Flow<List<Patient>>
    fun observeSearch(query: String): Flow<List<Patient>>
    fun observeSearchByFacility(query: String, facilityId: Long): Flow<List<Patient>>
    fun observeActiveCount(): Flow<Int>
    fun observeActiveCountByFacility(facilityId: Long): Flow<Int>
    // IIT (Interruption in Treatment) — patient whose expected return (nextAppointment + refillPeriod days) has passed
    fun observeIITClients(todayMs: Long): Flow<List<IITClient>>
    fun observeIITClientsByFacility(todayMs: Long, facilityId: Long): Flow<List<IITClient>>
    fun observeIITSearch(q: String, todayMs: Long): Flow<List<IITClient>>
    fun observeIITSearchByFacility(q: String, todayMs: Long, facilityId: Long): Flow<List<IITClient>>
    // Missed Appointments — patient whose nextAppointment date has passed (1+ days overdue)
    fun observeMissedApptClients(todayMs: Long): Flow<List<IITClient>>
    fun observeMissedApptClientsByFacility(todayMs: Long, facilityId: Long): Flow<List<IITClient>>
    fun observeMissedApptSearch(q: String, todayMs: Long): Flow<List<IITClient>>
    fun observeMissedApptSearchByFacility(q: String, todayMs: Long, facilityId: Long): Flow<List<IITClient>>
    fun observeArtRefillClients(): Flow<List<IITClient>>
    fun observeArtRefillClientsByFacility(facilityId: Long): Flow<List<IITClient>>
    fun observeArtRefillSearch(q: String): Flow<List<IITClient>>
    fun observeArtRefillSearchByFacility(q: String, facilityId: Long): Flow<List<IITClient>>

    suspend fun getViralLoadHistory(personUuid: String): List<ViralLoadHistory>
    suspend fun getViralLoadHistoryForPatients(personUuids: List<String>): List<ViralLoadHistory>

    // ── Today's worklist ───────────────────────────────────────────────────────
    fun observeTodayWorklist(startOfDayMs: Long, endOfDayMs: Long): Flow<List<WorklistEntry>>
    fun observeTodayWorklistByFacility(startOfDayMs: Long, endOfDayMs: Long, facilityId: Long): Flow<List<WorklistEntry>>

    // ── Patients without biometrics ────────────────────────────────────────────
    fun observeNoBiometricPatients(): Flow<List<NoBiometricEntry>>
    fun observeNoBiometricPatientsByFacility(facilityId: Long): Flow<List<NoBiometricEntry>>
    fun observeNoBiometricSearch(q: String): Flow<List<NoBiometricEntry>>

    // ── VL Cascade counts ──────────────────────────────────────────────────────
    fun observeTxCurrCount(): Flow<Int>
    fun observeVlTestedCount(): Flow<Int>
    fun observeVlResultReceivedCount(): Flow<Int>
    fun observeVlSuppressedCount(): Flow<Int>
    fun observeVlUnsuppressedCount(): Flow<Int>

    // ── TPT ────────────────────────────────────────────────────────────────────
    fun observeTptPatients(): Flow<List<TptEntry>>
    fun observeTptPatientsByFacility(facilityId: Long): Flow<List<TptEntry>>
}