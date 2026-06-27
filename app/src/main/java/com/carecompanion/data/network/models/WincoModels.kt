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
    @SerializedName("person_uuid")                        val personUuid: String,
    @SerializedName("unique_id")                          val uniqueId: String?,
    @SerializedName("enrollment_uuid")                    val enrollmentUuid: String?,
    @SerializedName("facility_id")                        val facilityId: Long?,
    @SerializedName("facility_name")                      val facilityName: String?,
    @SerializedName("date_started")                       val dateStarted: String?,
    @SerializedName("date_of_registration")               val dateOfRegistration: String?,
    // Care classification fields (added when WINCO started returning these)
    @SerializedName("is_art_client")                      val isArtClient: Boolean = true,
    @SerializedName("is_active_tx_curr")                  val isActiveTxCurr: Boolean = false,
    @SerializedName("care_category")                      val careCategory: String?,
    // Latest clinical snapshot
    @SerializedName("latest_clinical_visit_date")         val latestClinicalVisitDate: String?,
    @SerializedName("latest_clinical_next_appointment")   val latestClinicalNextAppointment: String?,
    @SerializedName("latest_clinical_adherence_level")    val latestClinicalAdherenceLevel: String?,
    @SerializedName("latest_clinical_tb_status")          val latestClinicalTbStatus: String?,
    // Latest pharmacy snapshot
    @SerializedName("latest_pharmacy_visit_date")         val latestPharmacyVisitDate: String?,
    @SerializedName("latest_pharmacy_next_appointment")   val latestPharmacyNextAppointment: String?,
    @SerializedName("latest_pharmacy_refill_period")      val latestPharmacyRefillPeriod: Int?,
    @SerializedName("latest_pharmacy_mmd_type")           val latestPharmacyMmdType: String?,
    // Latest status / biometric snapshot
    @SerializedName("latest_hiv_status")                  val latestHivStatus: String?,
    @SerializedName("latest_hiv_status_date")             val latestHivStatusDate: String?,
    @SerializedName("latest_biometric_status")            val latestBiometricStatus: String?,
    @SerializedName("biometric_count")                    val biometricCount: Int = 0,
    @SerializedName("latest_pharmacy_source_id")          val latestPharmacySourceId: Long? = null,
    @SerializedName("archived")                           val archived: Int? = 0,
    // Patient demographics — returned since WINCO joined EmrPatient in /clients
    @SerializedName("first_name")                         val firstName: String? = null,
    @SerializedName("middle_name")                        val middleName: String? = null,
    @SerializedName("last_name")                          val lastName: String? = null,
    @SerializedName("patient_name")                       val patientName: String?,
    @SerializedName("hospital_number")                    val hospitalNumber: String?,
    @SerializedName("gender")                             val gender: String?,
    @SerializedName("date_of_birth")                      val dateOfBirth: String?,
    @SerializedName("phone_number")                       val phoneNumber: String?,
)

data class WincoClientPage(
    @SerializedName("page")                     val page: Int,
    @SerializedName("per_page")                 val perPage: Int,
    @SerializedName("total")                    val total: Int,
    @SerializedName("pages")                    val pages: Int,
    @SerializedName("care_categories_applied")  val careCategoriesApplied: List<String> = emptyList(),
    @SerializedName("items")                    val items: List<WincoClientItem>,
)

// ---------------------------------------------------------------------------
// Single ART client detail
// GET /api/art/clients/{person_uuid}  →  WincoClientDetail
// ---------------------------------------------------------------------------

data class WincoEnrollmentDetail(
    @SerializedName("uuid")                 val uuid: String?,
    @SerializedName("facility_id")          val facilityId: Long?,
    @SerializedName("date_started")         val dateStarted: String?,
    @SerializedName("date_of_registration") val dateOfRegistration: String?,
    @SerializedName("archived")             val archived: Int?,
    @SerializedName("last_modified_date")   val lastModifiedDate: String?,
)

data class WincoClinicalVisit(
    @SerializedName("visit_date")       val visitDate: String?,
    @SerializedName("next_appointment") val nextAppointment: String?,
    @SerializedName("art_status_id")    val artStatusId: String?,
    @SerializedName("regimen_id")       val regimenId: String?,
    @SerializedName("regimen_type_id")  val regimenTypeId: String?,
    @SerializedName("adherence_level")  val adherenceLevel: String?,
    @SerializedName("tb_status")        val tbStatus: String?,
)

data class WincoPharmacyVisit(
    @SerializedName("visit_date")          val visitDate: String?,
    @SerializedName("next_appointment")    val nextAppointment: String?,
    @SerializedName("refill_period")       val refillPeriod: Int?,
    @SerializedName("mmd_type")            val mmdType: String?,
    @SerializedName("dsd_model_type")      val dsdModelType: String?,
    @SerializedName("dsd_model")           val dsdModel: String?,
    @SerializedName("refill_type")         val refillType: String?,
    @SerializedName("adherence")           val adherence: Boolean?,
    @SerializedName("ard_screened")        val ardScreened: Boolean?,
    @SerializedName("prescription_error")  val prescriptionError: Boolean?,
    @SerializedName("delivery_point")      val deliveryPoint: String?,
    @SerializedName("ipt_type")            val iptType: String?,
    @SerializedName("ipt")                 val ipt: String?,
    @SerializedName("is_devolve")          val isDevolve: Boolean?,
    @SerializedName("latitude")            val latitude: String?,
    @SerializedName("longitude")           val longitude: String?,
    @SerializedName("source_id")           val sourceId: Long?,
)

data class WincoStatusHistoryItem(
    @SerializedName("hiv_status")       val hivStatus: String?,
    @SerializedName("status_date")      val statusDate: String?,
    @SerializedName("tracking_outcome") val trackingOutcome: String?,
    @SerializedName("track_date")       val trackDate: String?,
    @SerializedName("agreed_date")      val agreedDate: String?,
    @SerializedName("biometric_status") val biometricStatus: String?,
)

data class WincoBiometricItem(
    @SerializedName("id")              val id: String?,
    @SerializedName("biometric_type")  val biometricType: String?,
    @SerializedName("enrollment_date") val enrollmentDate: String?,
    @SerializedName("iso")             val iso: Boolean?,
    @SerializedName("device_name")     val deviceName: String?,
    @SerializedName("archived")        val archived: Int?,
)

data class WincoBiometricSummary(
    @SerializedName("count") val count: Int = 0,
    @SerializedName("items") val items: List<WincoBiometricItem> = emptyList(),
)

data class WincoPatientDetail(
    @SerializedName("uuid")            val uuid: String?,
    @SerializedName("person_uuid")     val personUuid: String?,
    @SerializedName("emr_id")          val emrId: String?,
    @SerializedName("hospital_number") val hospitalNumber: String?,
    @SerializedName("first_name")      val firstName: String?,
    @SerializedName("middle_name")     val middleName: String?,
    @SerializedName("last_name")       val lastName: String?,
    @SerializedName("full_name")       val fullName: String?,
    @SerializedName("sex")             val sex: String?,
    @SerializedName("gender")          val gender: String?,
    @SerializedName("date_of_birth")   val dateOfBirth: String?,
    @SerializedName("phone_number")    val phoneNumber: String?,
    @SerializedName("facility_id")     val facilityId: Long?,
)

data class WincoServiceEligibility(
    @SerializedName("service")      val service: String,          // e.g. "ART_REFILL", "MISSED_APPOINTMENT", etc.
    @SerializedName("eligible")     val eligible: Boolean,
    @SerializedName("reason")       val reason: String?,
    @SerializedName("urgency")      val urgency: String?,         // "critical", "high", "due", "routine"
    @SerializedName("next_action")  val nextAction: String?,
    // Service-specific fields
    @SerializedName("last_refill_date")              val lastRefillDate: String? = null,
    @SerializedName("medication_expiry_date")       val medicationExpiryDate: String? = null,
    @SerializedName("days_until_expiry")            val daysUntilExpiry: Int? = null,
    @SerializedName("appointment_type")             val appointmentType: String? = null,
    @SerializedName("scheduled_date")               val scheduledDate: String? = null,
    @SerializedName("days_overdue")                 val daysOverdue: Int? = null,
    @SerializedName("vl_type")                      val vlType: String? = null,  // "baseline" or "routine"
    @SerializedName("months_on_art")                val monthsOnArt: Int? = null,
    @SerializedName("months_since_last_vl")        val monthsSinceLastVl: Int? = null,
    @SerializedName("cd4_count")                    val cd4Count: Int? = null,
    @SerializedName("tb_status")                    val tbStatus: String? = null,
    @SerializedName("priority")                     val priority: String? = null,
    @SerializedName("contraindications")            val contraindications: List<String> = emptyList(),
)

data class WincoServiceEligibilitySummary(
    @SerializedName("as_of_date")           val asOfDate: String?,
    @SerializedName("services")             val services: Map<String, WincoServiceEligibility> = emptyMap(),
    @SerializedName("eligible_services")    val eligibleServices: List<String> = emptyList(),
    @SerializedName("summary")              val summary: Map<String, Int> = emptyMap(),
)

data class WincoViralLoadSummary(
    @SerializedName(value = "result_numeric", alternate = ["last_result_value"]) val lastResultValue: Long? = null,
    @SerializedName(value = "date_result_reported", alternate = ["last_result_date"]) val lastResultDate: String? = null,
    @SerializedName("test_type")            val testType: String? = null,
    @SerializedName(value = "result_raw", alternate = ["raw_result"]) val rawResult: String? = null,
)

data class WincoViralLoadHistoryItem(
    @SerializedName("test_id")              val testId: Long? = null,
    @SerializedName("sample_type_id")       val sampleTypeId: Int? = null,
    @SerializedName("sample_number")        val sampleNumber: String? = null,
    @SerializedName("result_raw")           val resultRaw: String? = null,
    @SerializedName("result_numeric")       val resultNumeric: Long? = null,
    @SerializedName("date_result_reported") val dateResultReported: String? = null,
    @SerializedName("date_assayed")         val dateAssayed: String? = null,
    @SerializedName("date_sample_collected") val dateSampleCollected: String? = null,
    @SerializedName("source_id")            val sourceId: Long? = null,
    @SerializedName("source")               val source: String? = null,
    @SerializedName("viral_load_indication") val viralLoadIndication: Int? = null,
    @SerializedName("vl_category")          val vlCategory: String? = null,
)

// EAC cascade — from GET /api/art/clients/{uuid}/eac. The app stores the episodes and recomputes gaps
// on-device (live TX_CURR + VL) via EacGapEngine; gaps/warnings here are WINCO's server-side view.
data class WincoEacResponse(
    @SerializedName("tx_curr")              val txCurr: Boolean = false,
    @SerializedName("current_vl")           val currentVl: Long? = null,
    @SerializedName("current_unsuppressed") val currentUnsuppressed: Boolean = false,
    @SerializedName("episode_count")        val episodeCount: Int = 0,
    @SerializedName("episodes")             val episodes: List<WincoEacEpisode> = emptyList(),
)

data class WincoEacEpisode(
    @SerializedName("uuid")             val uuid: String? = null,
    @SerializedName("status")           val status: String? = null,
    @SerializedName("stage")            val stage: String? = null,
    @SerializedName("sessions")         val sessions: Int = 0,
    @SerializedName("trigger_vl")       val triggerVl: Double? = null,
    @SerializedName("trigger_date")     val triggerDate: String? = null,
    @SerializedName("repeat_vl")        val repeatVl: Double? = null,
    @SerializedName("regimen_switched") val regimenSwitched: Boolean = false,
)

// PMTCT worklist — from GET /api/art/pmtct/worklist. Currently-pregnant women with GA + PMTCT VL gaps.
data class WincoPmtctWorklistResponse(
    @SerializedName("facility_id") val facilityId: Long? = null,
    @SerializedName("count")       val count: Int = 0,
    @SerializedName("with_gap")    val withGap: Int = 0,
    @SerializedName("items")       val items: List<WincoPmtctItem> = emptyList(),
)

data class WincoPmtctItem(
    @SerializedName("person_uuid")        val personUuid: String? = null,
    @SerializedName("name")               val name: String? = null,
    @SerializedName("hospital_number")    val hospitalNumber: String? = null,
    @SerializedName("anc_no")             val ancNo: String? = null,
    @SerializedName("lmp")                val lmp: String? = null,
    @SerializedName("edd")                val edd: String? = null,
    @SerializedName("ga_weeks")           val gaWeeks: Int? = null,
    @SerializedName("currently_pregnant") val currentlyPregnant: Boolean = false,
    @SerializedName("pmtct_vl_done")      val pmtctVlDone: Boolean = false,
    @SerializedName("tx_curr")            val txCurr: Boolean = false,
    @SerializedName("gaps")               val gaps: List<WincoPmtctGap> = emptyList(),
)

data class WincoPmtctGap(
    @SerializedName("type")     val type: String? = null,
    @SerializedName("severity") val severity: String? = null,
    @SerializedName("message")  val message: String? = null,
)

// EID worklist — from GET /api/art/eid/worklist. HIV-exposed infants + high-risk + intervention gaps.
data class WincoEidWorklistResponse(
    @SerializedName("facility_id") val facilityId: Long? = null,
    @SerializedName("count")       val count: Int = 0,
    @SerializedName("high_risk")   val highRisk: Int = 0,
    @SerializedName("with_gap")    val withGap: Int = 0,
    @SerializedName("items")       val items: List<WincoInfantItem> = emptyList(),
)

data class WincoInfantItem(
    @SerializedName("infant_uuid")         val infantUuid: String? = null,
    @SerializedName("name")                val name: String? = null,
    @SerializedName("hospital_number")     val hospitalNumber: String? = null,
    @SerializedName("mother_person_uuid")  val motherPersonUuid: String? = null,
    @SerializedName("anc_no")              val ancNo: String? = null,
    @SerializedName("date_of_delivery")    val dateOfDelivery: String? = null,
    @SerializedName("age_weeks")           val ageWeeks: Int? = null,
    @SerializedName("age_months")          val ageMonths: Int? = null,
    @SerializedName("high_risk")           val highRisk: Boolean = false,
    @SerializedName("high_risk_reason")    val highRiskReason: String? = null,
    @SerializedName("arv_given")           val arvGiven: Boolean = false,
    @SerializedName("ctx_given")           val ctxGiven: Boolean = false,
    @SerializedName("pcr_done")            val pcrDone: Boolean = false,
    @SerializedName("pcr_result")          val pcrResult: String? = null,
    @SerializedName("pcr_positive")        val pcrPositive: Boolean = false,
    @SerializedName("pcr_result_received") val pcrResultReceived: Boolean = false,
    @SerializedName("antibody_done")       val antibodyDone: Boolean = false,
    @SerializedName("outcome_18m")         val outcome18m: String? = null,
    @SerializedName("gaps")                val gaps: List<WincoPmtctGap> = emptyList(),
)

data class WincoTbScreeningSummary(
    @SerializedName("status")               val status: String? = null,
    @SerializedName("date")                 val date: String? = null,
    @SerializedName("note")                 val note: String? = null,
)

data class WincoNdrMatch(
    @SerializedName("facility_name")     val facilityName: String? = null,
    @SerializedName("datim_code")        val datimCode: String? = null,
    @SerializedName("patient_art_number") val patientArtNumber: String? = null,
    @SerializedName("person_uuid")       val personUuid: String? = null,
    @SerializedName("match_outcome")     val matchOutcome: String? = null,
)

data class WincoClientDetail(
    @SerializedName("person_uuid")      val personUuid: String,
    @SerializedName("unique_id")        val uniqueId: String?,
    @SerializedName("is_art_client")    val isArtClient: Boolean = true,
    @SerializedName("is_active_tx_curr") val isActiveTxCurr: Boolean = false,
    @SerializedName("care_category")    val careCategory: String?,
    @SerializedName("patient")          val patient: WincoPatientDetail?,
    @SerializedName("enrollment")       val enrollment: WincoEnrollmentDetail?,
    @SerializedName("clinical_visits")  val clinicalVisits: List<WincoClinicalVisit> = emptyList(),
    @SerializedName("pharmacy_visits")  val pharmacyVisits: List<WincoPharmacyVisit> = emptyList(),
    @SerializedName("status_history")   val statusHistory: List<WincoStatusHistoryItem> = emptyList(),
    @SerializedName("biometric")        val biometric: WincoBiometricSummary?,
    @SerializedName("service_eligibility") val serviceEligibility: WincoServiceEligibilitySummary? = null,
    @SerializedName("viral_load")       val viralLoad: WincoViralLoadSummary? = null,
    @SerializedName("viral_load_history") val viralLoadHistory: List<WincoViralLoadHistoryItem> = emptyList(),
    @SerializedName("tb_screening")     val tbScreening: WincoTbScreeningSummary? = null,
    @SerializedName("ndr_match")        val ndrMatch: WincoNdrMatch? = null,
)

// ---------------------------------------------------------------------------
// WINCO biometric templates response
// GET /api/art/biometrics/{person_uuid}/templates
// personId is a UUID string from WINCO (NOT a numeric Long like the EMR endpoint)
// ---------------------------------------------------------------------------

data class WincoBiometricEntry(
    @SerializedName("template")         val template: String?,
    @SerializedName("templateType")     val templateType: String?,
    @SerializedName("recapture")        val recapture: Int? = 0,
    @SerializedName("sourceId")         val sourceId: String?,                  // WINCO returns UUID/text source IDs
    @SerializedName("templateHash")     val templateHash: String? = null,
    @SerializedName("biometricType")    val biometricType: String?,             // e.g. "FINGERPRINT", "RIGHT_THUMB"
    @SerializedName("enrollmentDate")   val enrollmentDate: String?,            // ISO date when finger was enrolled
    @SerializedName("iso")              val iso: Boolean? = false,
    @SerializedName("imageQuality")     val imageQuality: Int?,                 // 0–100 quality score
    @SerializedName("archived")         val archived: Int? = null,              // mirrors EMR archived flag
)

data class WincoBiometricResponse(
    @SerializedName("personId")                val personId: String?,
    @SerializedName("capturedBiometricsList")  val capturedBiometricsList: List<WincoBiometricEntry>? = null,
    @SerializedName("capturedBiometricsList2") val capturedBiometricsList2: List<WincoBiometricEntry>? = null,
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

// ---------------------------------------------------------------------------
// WINCO mobile startup KPI summary
// GET /api/art/summary
// ---------------------------------------------------------------------------

data class WincoSummary(
    @SerializedName("as_of_date")                   val asOfDate: String?,
    @SerializedName("active_tx_curr")               val activeTxCurr: Int = 0,
    @SerializedName("iit_this_week")                val iitThisWeek: Int = 0,
    @SerializedName("biometric_coverage_active_pct") val biometricCoveragePct: Double = 0.0,
    @SerializedName("active_with_biometric")        val activeWithBiometric: Int = 0,
    @SerializedName("active_without_biometric")     val activeWithoutBiometric: Int = 0,
    @SerializedName("last_sync_at")                 val lastSyncAt: String? = null,
    @SerializedName("facility_id")                  val facilityId: Long? = null,
)


data class WincoViralLoadHistoryResponse(
    @SerializedName("person_uuid") val personUuid: String,
    @SerializedName("count") val count: Int = 0,
    @SerializedName("items") val items: List<WincoViralLoadHistoryItem> = emptyList(),
)

data class WincoPharmacyHistoryResponse(
    @SerializedName("person_uuid") val personUuid: String,
    @SerializedName("count") val count: Int = 0,
    @SerializedName("items") val items: List<WincoPharmacyVisit> = emptyList(),
)

// ---------------------------------------------------------------------------
// Bulk biometric templates — POST /api/art/biometrics/bulk
// Single request returns templates for up to 200 patients.
// ---------------------------------------------------------------------------

data class WincoBulkBiometricRequest(
    @SerializedName("person_uuids") val personUuids: List<String>,
)

data class WincoBulkBiometricResponse(
    @SerializedName("biometrics") val biometrics: Map<String, WincoBiometricResponse> = emptyMap(),
)
