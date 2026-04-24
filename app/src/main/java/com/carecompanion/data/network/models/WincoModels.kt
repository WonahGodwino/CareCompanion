package com.carecompanion.data.network.models

import com.google.gson.annotations.SerializedName

// ---------------------------------------------------------------------------
// WINCO API response models
// Returned by WINCO's  GET /api/art/clients          (paginated TX_CURR list)
//                  and GET /api/art/clients/{uuid}   (single client detail)
// The biometric templates endpoint returns PatientBiometricResponse (same
// shape as the EMR endpoint, defined in EMRModels.kt).
// ---------------------------------------------------------------------------

data class WincoClientItem(
    @SerializedName("person_uuid")                      val personUuid: String,
    @SerializedName("unique_id")                        val uniqueId: String?,
    @SerializedName("enrollment_uuid")                  val enrollmentUuid: String?,
    @SerializedName("facility_id")                      val facilityId: Long?,
    @SerializedName("facility_name")                    val facilityName: String?,
    @SerializedName("date_started")                     val dateStarted: String?,
    @SerializedName("date_of_registration")             val dateOfRegistration: String?,
    @SerializedName("latest_hiv_status")                val latestHivStatus: String?,
    @SerializedName("latest_hiv_status_date")           val latestHivStatusDate: String?,
    @SerializedName("latest_pharmacy_visit_date")       val latestPharmacyVisitDate: String?,
    @SerializedName("latest_pharmacy_next_appointment") val latestPharmacyNextAppointment: String?,
    @SerializedName("latest_pharmacy_refill_period")    val latestPharmacyRefillPeriod: Int?,
    @SerializedName("latest_pharmacy_mmd_type")         val latestPharmacyMmdType: String?,
    @SerializedName("latest_clinical_adherence_level")  val latestClinicalAdherenceLevel: String?,
    @SerializedName("latest_clinical_tb_status")        val latestClinicalTbStatus: String?,
    @SerializedName("biometric_count")                  val biometricCount: Int = 0,
    @SerializedName("archived")                         val archived: Int? = 0,
)

data class WincoClientPage(
    @SerializedName("page")     val page: Int,
    @SerializedName("per_page") val perPage: Int,
    @SerializedName("total")    val total: Int,
    @SerializedName("pages")    val pages: Int,
    @SerializedName("items")    val items: List<WincoClientItem>,
)

// ---------------------------------------------------------------------------
// WINCO token auth models
// POST /api/art/token  →  WincoTokenResponse
// ---------------------------------------------------------------------------

data class WincoTokenRequest(
    @SerializedName("username") val username: String,
    @SerializedName("password") val password: String,
)

data class WincoTokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type")   val tokenType: String = "Bearer",
    @SerializedName("expires_in")   val expiresIn: Int = 604800,
    @SerializedName("error")        val error: String? = null,
)

// ---------------------------------------------------------------------------
// WINCO facility list model
// GET /api/art/facilities
// ---------------------------------------------------------------------------

data class WincoFacility(
    @SerializedName("id")    val id: Long,
    @SerializedName("name")  val name: String,
    @SerializedName("lga")   val lga: String?,
    @SerializedName("state") val state: String?,
)
