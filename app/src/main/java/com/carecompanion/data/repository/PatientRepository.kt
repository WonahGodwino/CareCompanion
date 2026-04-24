package com.carecompanion.data.repository

import com.carecompanion.data.database.entities.ArtPharmacy
import com.carecompanion.data.database.entities.Biometric
import com.carecompanion.data.database.entities.IITClient
import com.carecompanion.data.database.entities.Patient
import kotlinx.coroutines.flow.Flow
import java.util.Date

interface PatientRepository {
    suspend fun getAllActivePatients(): List<Patient>
    suspend fun getAllActiveByFacility(facilityId: Long): List<Patient>
    suspend fun searchPatients(query: String): List<Patient>
    suspend fun searchPatientsByFacility(query: String, facilityId: Long): List<Patient>
    suspend fun getPatientByEmrId(emrId: Long): Patient?
    suspend fun getPatientByUuid(uuid: String): Patient?
    suspend fun getBiometricsForPatient(personUuid: String): List<Biometric>
    suspend fun getAllBiometrics(): List<Biometric>
    suspend fun getArtPharmacyForPatient(personUuid: String): List<ArtPharmacy>
    suspend fun getPatientCount(): Int
    suspend fun getBiometricCount(): Int
    // Reactive flows — Room emits a new list whenever the table changes
    fun observeAllActive(): Flow<List<Patient>>
    fun observeAllActiveByFacility(facilityId: Long): Flow<List<Patient>>
    fun observeSearch(query: String): Flow<List<Patient>>
    fun observeSearchByFacility(query: String, facilityId: Long): Flow<List<Patient>>
    fun observeActiveCount(): Flow<Int>
    fun observeActiveCountByFacility(facilityId: Long): Flow<Int>
    // IIT (Interruption in Treatment) — PEPFAR: missed refill ≥ 28 days
    fun observeIITClients(cutoffDate: Date): Flow<List<IITClient>>
    fun observeIITClientsByFacility(cutoffDate: Date, facilityId: Long): Flow<List<IITClient>>
    fun observeIITSearch(q: String, cutoffDate: Date): Flow<List<IITClient>>
    fun observeIITSearchByFacility(q: String, cutoffDate: Date, facilityId: Long): Flow<List<IITClient>>
}