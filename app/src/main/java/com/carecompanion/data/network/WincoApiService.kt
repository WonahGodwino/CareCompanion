package com.carecompanion.data.network

import com.carecompanion.data.network.models.WincoBiometricResponse
import com.carecompanion.data.network.models.WincoBulkBiometricRequest
import com.carecompanion.data.network.models.WincoBulkBiometricResponse
import com.carecompanion.data.network.models.WincoClientDetail
import com.carecompanion.data.network.models.WincoEacResponse
import com.carecompanion.data.network.models.WincoPmtctWorklistResponse
import com.carecompanion.data.network.models.WincoEidWorklistResponse
import com.carecompanion.data.network.models.WincoClientPage
import com.carecompanion.data.network.models.WincoFacility
import com.carecompanion.data.network.models.WincoSummary
import com.carecompanion.data.network.models.WincoTokenRequest
import com.carecompanion.data.network.models.WincoTokenResponse
import com.carecompanion.data.network.models.WincoViralLoadHistoryResponse
import com.carecompanion.data.network.models.WincoPharmacyHistoryResponse
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
        * Default scope: all enrolled non-archived ART clients.
     * Pass [txCurrOnly]=true to restrict to TX_CURR-eligible clients only
     * (legacy switch — server still honours it for backward compatibility).
        *
        * TX_ML options:
        * - includeTxMl=true enables IIT/TRANSFER_OUT/DEATH inclusion.
        * - txMlStartDate / txMlEndDate constrain those TX_ML statuses by
        *   hiv_status_tracker.status_date (inclusive, yyyy-MM-dd).
     *
     * WINCO endpoint: GET /api/art/clients
     */
    @GET("api/art/clients")
    suspend fun getTxCurrClients(
        @Query("facility_id")      facilityId: Long?,
        @Query("page")             page: Int = 1,
        @Query("per_page")         perPage: Int = 100,
        @Query("tx_curr_only")     txCurrOnly: Boolean = false,
        @Query("apply_care_filter") applyCareFilter: Boolean = false,
        @Query("care_categories")  careCategories: String? = null,
        @Query("q")                search: String? = null,
        @Query("as_of_date")       asOfDate: String? = null,
        @Query("include_tx_ml")    includeTxMl: Boolean = false,
        @Query("tx_ml_start_date") txMlStartDate: String? = null,
        @Query("tx_ml_end_date")   txMlEndDate: String? = null,
        @Query("include_archived") includeArchived: Boolean = false,
        @Query("updated_since")    updatedSince: String? = null,
    ): WincoClientPage

    /**
     * Single ART client detail — enrollment, clinical visits, pharmacy visits,
     * status history and a biometric summary (no template bytes).
     *
     * Returns HTTP 404 if no enrollment record exists for the given person_uuid.
     * Available for all care categories (ACTIVE, IIT, TRANSFER_OUT, DEATH, etc.).
     *
     * WINCO endpoint: GET /api/art/clients/{person_uuid}
     */
    @GET("api/art/clients/{person_uuid}")
    suspend fun getClientDetail(
        @Path("person_uuid") personUuid: String,
        @Query("as_of_date") asOfDate: String? = null,
    ): WincoClientDetail

    /**
     * Optional dedicated endpoint for full viral load history.
     * Primary sync path uses getClientDetail() which now includes viral_load_history.
     */
    @GET("api/art/clients/{person_uuid}/viral-load-history")
    suspend fun getClientViralLoadHistory(
        @Path("person_uuid") personUuid: String,
    ): Map<String, @JvmSuppressWildcards Any?>

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
    ): WincoBiometricResponse

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
     * Pulls the current federated risk model for a facility (Phase 2).
     * WINCO endpoint: GET /api/knowledge/model/current?facility_id=
     */
    @GET("api/knowledge/model/current")
    suspend fun getRiskModel(@Query("facility_id") facilityId: Long): com.carecompanion.data.network.models.WincoModelPacket

    /**
     * Returns the list of facilities WINCO knows about.
     * Used by the Settings screen to populate the facility picker after login.
     *
     * WINCO endpoint: GET /api/art/facilities
     */
    @GET("api/art/facilities")
    suspend fun getFacilities(): List<WincoFacility>

    /**
     * Home-screen KPI summary for a single facility.
     * Returns TX_CURR, IIT-this-week, biometric coverage, and last-sync timestamp.
     *
     * WINCO endpoint: GET /api/art/summary
     */
    @GET("api/art/summary")
    suspend fun getDashboardSummary(
        @Query("facility_id") facilityId: Long? = null,
        @Query("as_of_date")  asOfDate: String? = null,
    ): WincoSummary

    @GET("api/art/clients/{person_uuid}/viral-load-history")
    suspend fun getViralLoadHistory(
        @Path("person_uuid") personUuid: String,
        @Query("facility_id") facilityId: Long? = null,
    ): WincoViralLoadHistoryResponse

    @GET("api/art/clients/{person_uuid}/eac")
    suspend fun getEac(
        @Path("person_uuid") personUuid: String,
        @Query("facility_id") facilityId: Long? = null,
    ): WincoEacResponse

    @GET("api/art/pmtct/worklist")
    suspend fun getPmtctWorklist(
        @Query("facility_id") facilityId: Long? = null,
    ): WincoPmtctWorklistResponse

    @GET("api/art/eid/worklist")
    suspend fun getEidWorklist(
        @Query("facility_id") facilityId: Long? = null,
    ): WincoEidWorklistResponse

    @GET("api/art/clients/{person_uuid}/pharmacy-history")
    suspend fun getPharmacyHistory(
        @Path("person_uuid") personUuid: String,
        @Query("facility_id") facilityId: Long? = null,
    ): WincoPharmacyHistoryResponse

    /**
     * Bulk biometric templates — fetch templates for up to 200 patients in a single request.
     * Replaces N individual [getBiometricTemplates] calls during the biometric sync phase.
     *
     * WINCO endpoint: POST /api/art/biometrics/bulk
     */
    @POST("api/art/biometrics/bulk")
    suspend fun getBiometricsBulk(
        @Body request: WincoBulkBiometricRequest,
    ): WincoBulkBiometricResponse
}
