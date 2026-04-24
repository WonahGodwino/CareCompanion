package com.carecompanion.data.network

import com.carecompanion.data.network.models.PatientBiometricResponse
import com.carecompanion.data.network.models.WincoClientPage
import com.carecompanion.data.network.models.WincoFacility
import com.carecompanion.data.network.models.WincoTokenRequest
import com.carecompanion.data.network.models.WincoTokenResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for the WINCO aggregation server.
 *
 * Authentication is via X-API-KEY header (injected by the WINCO OkHttpClient
 * interceptor in NetworkModule — see [com.carecompanion.di.NetworkModule]).
 *
 * Base URL is the WINCO server address stored in SharedPreferences under
 * "winco_url" and dynamically rewritten by the same interceptor pattern used
 * for the EMR client.
 */
interface WincoApiService {

    /**
     * Returns a paginated list of ART clients from WINCO.
     *
     * Pass [txCurrOnly]=true to restrict results to TX_CURR-eligible clients
     * (latest status ART_START | ART_TRANSFER_IN with active pharmacy record).
     *
     * WINCO endpoint: GET /api/art/clients
     */
    @GET("api/art/clients")
    suspend fun getTxCurrClients(
        @Query("facility_id") facilityId: Long?,
        @Query("page")        page: Int = 1,
        @Query("per_page")    perPage: Int = 100,
        @Query("tx_curr_only") txCurrOnly: Boolean = true,
    ): WincoClientPage

    /**
     * Biometric templates for a single client.
     *
     * WINCO reads template bytes from its own local mirror of the EMR
     * biometric table (synced via direct PostgreSQL connection — no outbound
     * HTTP call to EMR is made).  The JSON shape is identical to EMR's
     * /api/v1/biometrics/patient/{id} so [PatientBiometricResponse] is reused.
     *
     * capturedBiometricsList  = original enrollment session (recapture = 0)
     * capturedBiometricsList2 = recapture session fingers   (recapture >= 1)
     *
     * WINCO endpoint: GET /api/art/biometrics/{person_uuid}/templates
     */
    @GET("api/art/biometrics/{person_uuid}/templates")
    suspend fun getBiometricTemplates(
        @Path("person_uuid") personUuid: String,
    ): PatientBiometricResponse

    /**
     * Obtain a Bearer token from WINCO by presenting WINCO username + password.
     * Call this WITHOUT the Authorization header (it is the authentication step).
     * Use a plain OkHttpClient without the global WINCO interceptor.
     *
     * WINCO endpoint: POST /api/art/token
     */
    @POST("api/art/token")
    suspend fun getToken(@Body request: WincoTokenRequest): WincoTokenResponse

    /**
     * Returns the list of facilities WINCO knows about.
     * Used by the Settings screen to populate the facility picker after login.
     *
     * WINCO endpoint: GET /api/art/facilities
     */
    @GET("api/art/facilities")
    suspend fun getFacilities(): List<WincoFacility>
}
