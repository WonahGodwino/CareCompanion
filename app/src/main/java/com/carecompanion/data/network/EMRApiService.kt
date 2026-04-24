package com.carecompanion.data.network

import retrofit2.http.*
import com.carecompanion.data.network.models.*

interface EMRApiService {
    @GET("api/v1/hiv/patients")
    suspend fun getHivPatients(
        @Query("facilityId") facilityId: Long,
        @Query("pageNo") pageNo: Int = 0,
        @Query("pageSize") pageSize: Int = 100,
        @Query("searchValue") searchValue: String? = null
    ): HivPatientsPage

    @GET("api/v1/hiv/art/commencement")
    suspend fun getArtCommencementPatients(
        @Query("pageNo") pageNo: Int = 0,
        @Query("pageSize") pageSize: Int = 100
    ): ArtCommencementPage

    @GET("api/v1/patient/get-all-patient-pageable")
    suspend fun getPatientsPageable(
        @Query("searchParam") searchParam: String = "*",
        @Query("pageNo") pageNo: Int = 0,
        @Query("pageSize") pageSize: Int = 200
    ): PatientPageDto

    @GET("api/v1/biometrics/patient/{id}")
    suspend fun getPatientBiometrics(@Path("id") patientId: Long): PatientBiometricResponse
    @GET("api/v1/health") suspend fun healthCheck(): ApiResponse<HealthStatus>
    @GET("api/v1/sync/status") suspend fun getSyncStatus(@Query("facilityId") facilityId: Long): ApiResponse<SyncStatus>
    @POST("api/v1/authenticate") suspend fun authenticate(@Body request: AuthRequest): AuthResponse
    @GET("api/v1/account") suspend fun getAccount(): AccountResponse
    @GET("api/v1/organisation-units") suspend fun getFacilities(): List<EMRFacility>
}