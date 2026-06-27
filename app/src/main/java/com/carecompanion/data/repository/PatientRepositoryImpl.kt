package com.carecompanion.data.repository

import com.carecompanion.data.database.dao.ArtPharmacyDao
import com.carecompanion.data.database.dao.BiometricDao
import com.carecompanion.data.database.dao.PatientDao
import com.carecompanion.data.database.dao.ViralLoadHistoryDao
import com.carecompanion.data.database.entities.ArtPharmacy
import com.carecompanion.data.database.entities.Biometric
import com.carecompanion.data.database.entities.IITClient
import com.carecompanion.data.database.entities.NoBiometricEntry
import com.carecompanion.data.database.entities.Patient
import com.carecompanion.data.database.entities.TptEntry
import com.carecompanion.data.database.entities.ViralLoadHistory
import com.carecompanion.data.database.entities.WorklistEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date
import javax.inject.Inject

class PatientRepositoryImpl @Inject constructor(
    private val patientDao: PatientDao,
    private val biometricDao: BiometricDao,
    private val artPharmacyDao: ArtPharmacyDao,
    private val viralLoadHistoryDao: ViralLoadHistoryDao
) : PatientRepository {
    override suspend fun getAllActivePatients() = patientDao.getAllActive()
    override suspend fun getAllActiveByFacility(facilityId: Long) = patientDao.getAllActiveByFacility(facilityId)
    override suspend fun searchPatients(query: String) = patientDao.searchPatients(query)
    override suspend fun searchPatientsByFacility(query: String, facilityId: Long) = patientDao.searchPatientsByFacility(query, facilityId)
    override suspend fun getPatientByEmrId(emrId: Long) = patientDao.getByEmrId(emrId.toString())
    override suspend fun getPatientByUuid(uuid: String) = patientDao.getByUuid(uuid)
    override suspend fun getBiometricsForPatient(personUuid: String) = biometricDao.getByPersonUuid(personUuid)
    override suspend fun getAllBiometrics() = biometricDao.getAll()
    override suspend fun getArtPharmacyForPatient(personUuid: String) = artPharmacyDao.getByPersonUuid(personUuid)
    override suspend fun getArtPharmacyForPatients(personUuids: List<String>) = artPharmacyDao.getByPersonUuids(personUuids)
    override suspend fun getPatientCount() = patientDao.getActiveCount()
    override suspend fun getBiometricCount() = biometricDao.getCount()
    override fun observeAllActive() = patientDao.observeAllActive()
    override fun observeAllActiveByFacility(facilityId: Long) = patientDao.observeAllActiveByFacility(facilityId)
    override fun observeSearch(query: String) = patientDao.observeSearch(query)
    override fun observeSearchByFacility(query: String, facilityId: Long) = patientDao.observeSearchByFacility(query, facilityId)
    override fun observeActiveCount() = patientDao.observeActiveCount()
    override fun observeActiveCountByFacility(facilityId: Long) = patientDao.observeActiveCountByFacility(facilityId)
    // IIT
    override fun observeIITClients(todayMs: Long) = artPharmacyDao.observeIITClients(todayMs)
    override fun observeIITClientsByFacility(todayMs: Long, facilityId: Long) = artPharmacyDao.observeIITClientsByFacility(todayMs, facilityId)
    override fun observeIITSearch(q: String, todayMs: Long) = artPharmacyDao.observeIITSearch(q, todayMs)
    override fun observeIITSearchByFacility(q: String, todayMs: Long, facilityId: Long) = artPharmacyDao.observeIITSearchByFacility(q, todayMs, facilityId)
    // Missed Appointments
    override fun observeMissedApptClients(todayMs: Long) = artPharmacyDao.observeMissedApptClients(todayMs)
    override fun observeMissedApptClientsByFacility(todayMs: Long, facilityId: Long) = artPharmacyDao.observeMissedApptClientsByFacility(todayMs, facilityId)
    override fun observeMissedApptSearch(q: String, todayMs: Long) = artPharmacyDao.observeMissedApptSearch(q, todayMs)
    override fun observeMissedApptSearchByFacility(q: String, todayMs: Long, facilityId: Long) = artPharmacyDao.observeMissedApptSearchByFacility(q, todayMs, facilityId)
    // ART Refill
    override fun observeArtRefillClients() = artPharmacyDao.observeArtRefillClients()
    override fun observeArtRefillClientsByFacility(facilityId: Long) = artPharmacyDao.observeArtRefillClientsByFacility(facilityId)
    override fun observeArtRefillSearch(q: String) = artPharmacyDao.observeArtRefillSearch(q)
    override fun observeArtRefillSearchByFacility(q: String, facilityId: Long) = artPharmacyDao.observeArtRefillSearchByFacility(q, facilityId)

    override suspend fun getViralLoadHistory(personUuid: String): List<ViralLoadHistory> = withContext(Dispatchers.IO) {
        viralLoadHistoryDao.getByPersonUuid(personUuid)
    }

    override suspend fun getViralLoadHistoryForPatients(personUuids: List<String>): List<ViralLoadHistory> = withContext(Dispatchers.IO) {
        if (personUuids.isEmpty()) emptyList() else viralLoadHistoryDao.getByPersonUuids(personUuids)
    }

    override suspend fun saveBiometric(biometric: Biometric) = withContext(Dispatchers.IO) {
        biometricDao.insert(biometric)
    }

    // ── Today's worklist ───────────────────────────────────────────────────────
    override fun observeTodayWorklist(startOfDayMs: Long, endOfDayMs: Long) =
        artPharmacyDao.observeTodayWorklist(startOfDayMs, endOfDayMs)

    override fun observeTodayWorklistByFacility(startOfDayMs: Long, endOfDayMs: Long, facilityId: Long) =
        artPharmacyDao.observeTodayWorklistByFacility(startOfDayMs, endOfDayMs, facilityId)

    // ── Patients without biometrics ────────────────────────────────────────────
    override fun observeNoBiometricPatients() = patientDao.observeNoBiometricPatients()
    override fun observeNoBiometricPatientsByFacility(facilityId: Long) = patientDao.observeNoBiometricPatientsByFacility(facilityId)
    override fun observeNoBiometricSearch(q: String) = patientDao.observeNoBiometricSearch(q)

    // ── VL Cascade counts ──────────────────────────────────────────────────────
    override fun observeTxCurrCount(todayMs: Long) = patientDao.observeTxCurrCount(todayMs)
    override fun observeTxCurrCountByFacility(todayMs: Long, facilityId: Long) = patientDao.observeTxCurrCountByFacility(todayMs, facilityId)
    override fun observeUnsuppressedTxCurr(todayMs: Long) = patientDao.observeUnsuppressedTxCurr(todayMs)
    override fun observeVlTestedCount(todayMs: Long) = patientDao.observeVlTestedCount(todayMs)
    override fun observeVlResultReceivedCount(todayMs: Long) = patientDao.observeVlResultReceivedCount(todayMs)
    override fun observeVlSuppressedCount(todayMs: Long) = patientDao.observeVlSuppressedCount(todayMs)
    override fun observeVlUnsuppressedCount(todayMs: Long) = patientDao.observeVlUnsuppressedCount(todayMs)

    // ── TPT ────────────────────────────────────────────────────────────────────
    override fun observeTptPatients() = patientDao.observeTptPatients()
    override fun observeTptPatientsByFacility(facilityId: Long) = patientDao.observeTptPatientsByFacility(facilityId)
}