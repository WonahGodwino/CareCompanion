package com.carecompanion.data.repository

import com.carecompanion.data.database.dao.ArtPharmacyDao
import com.carecompanion.data.database.dao.BiometricDao
import com.carecompanion.data.database.dao.PatientDao
import com.carecompanion.data.database.entities.ArtPharmacy
import com.carecompanion.data.database.entities.Biometric
import com.carecompanion.data.database.entities.IITClient
import com.carecompanion.data.database.entities.Patient
import java.util.Date
import javax.inject.Inject

class PatientRepositoryImpl @Inject constructor(
    private val patientDao: PatientDao,
    private val biometricDao: BiometricDao,
    private val artPharmacyDao: ArtPharmacyDao
) : PatientRepository {
    override suspend fun getAllActivePatients() = patientDao.getAllActive()
    override suspend fun getAllActiveByFacility(facilityId: Long) = patientDao.getAllActiveByFacility(facilityId)
    override suspend fun searchPatients(query: String) = patientDao.searchPatients(query)
    override suspend fun searchPatientsByFacility(query: String, facilityId: Long) = patientDao.searchPatientsByFacility(query, facilityId)
    override suspend fun getPatientByEmrId(emrId: Long) = patientDao.getByEmrPatientId(emrId)
    override suspend fun getPatientByUuid(uuid: String) = patientDao.getByUuid(uuid)
    override suspend fun getBiometricsForPatient(personUuid: String) = biometricDao.getByPersonUuid(personUuid)
    override suspend fun getAllBiometrics() = biometricDao.getAll()
    override suspend fun getArtPharmacyForPatient(personUuid: String) = artPharmacyDao.getByPersonUuid(personUuid)
    override suspend fun getPatientCount() = patientDao.getActiveCount()
    override suspend fun getBiometricCount() = biometricDao.getCount()
    override fun observeAllActive() = patientDao.observeAllActive()
    override fun observeAllActiveByFacility(facilityId: Long) = patientDao.observeAllActiveByFacility(facilityId)
    override fun observeSearch(query: String) = patientDao.observeSearch(query)
    override fun observeSearchByFacility(query: String, facilityId: Long) = patientDao.observeSearchByFacility(query, facilityId)
    override fun observeActiveCount() = patientDao.observeActiveCount()
    override fun observeActiveCountByFacility(facilityId: Long) = patientDao.observeActiveCountByFacility(facilityId)
    // IIT
    override fun observeIITClients(cutoffDate: Date) = artPharmacyDao.observeIITClients(cutoffDate)
    override fun observeIITClientsByFacility(cutoffDate: Date, facilityId: Long) = artPharmacyDao.observeIITClientsByFacility(cutoffDate, facilityId)
    override fun observeIITSearch(q: String, cutoffDate: Date) = artPharmacyDao.observeIITSearch(q, cutoffDate)
    override fun observeIITSearchByFacility(q: String, cutoffDate: Date, facilityId: Long) = artPharmacyDao.observeIITSearchByFacility(q, cutoffDate, facilityId)
}