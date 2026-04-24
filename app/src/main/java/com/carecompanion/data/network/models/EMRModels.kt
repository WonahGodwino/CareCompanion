package com.carecompanion.data.network.models

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

// Real LAMISPlus /api/v1/hiv/patients response
data class HivPatient(
    val id: Long,
    val hospitalNumber: String?,
    val firstName: String?,
    val surname: String?,
    val otherName: String?,
    val sex: String?,
    val personUuid: String,
    val dateOfBirth: String?,
    val dateOfRegistration: String?,
    val age: Int?,
    val biometricStatus: Boolean = false,
    val isDobEstimated: Boolean = false,
    val currentStatus: String? = null  // "ART", "PrEP", "HTS", "HIVST", etc.
)
data class HivPatientsPage(
    val totalRecords: Int,
    val pageNumber: Int,
    val pageSize: Int,
    val totalPages: Int,
    val records: List<HivPatient>
)

data class ArtCommencementDto(
    val id: Long,
    val personUuid: String,
    val personId: Long,
    val dateOfCommencement: String?,
    val regimenId: Long?,
    val firstName: String? = null,
    val surname: String? = null,
    val hospitaNumber: String? = null
)

data class ArtCommencementPage(
    val totalRecords: Int = 0,
    val pageNumber: Int = 0,
    val pageSize: Int = 0,
    val totalPages: Int = 0,
    @SerializedName(value = "content", alternate = ["records", "result"]) val content: List<ArtCommencementDto> = emptyList()
)

data class PatientPageDto(
    @SerializedName(value = "content", alternate = ["records", "result"]) val content: List<PatientPersonDto> = emptyList(),
    @SerializedName(value = "totalElements", alternate = ["totalRecords"]) val totalElements: Int = 0,
    @SerializedName("totalPages") val totalPages: Int = 0,
    @SerializedName(value = "number", alternate = ["pageNumber", "pageNo"]) val pageNumber: Int = 0,
    @SerializedName(value = "size", alternate = ["pageSize"]) val pageSize: Int = 0,
    @SerializedName("last") val last: Boolean = false
)

data class PatientPersonDto(
    val id: Long,
    @SerializedName(value = "uuid", alternate = ["personUuid"]) val uuid: String,
    val createdDate: String? = null,
    val createdBy: String? = null,
    val lastModifiedDate: String? = null,
    val lastModifiedBy: String? = null,
    val active: Boolean? = null,
    val contactPoint: JsonElement? = null,
    val address: JsonElement? = null,
    val gender: JsonElement? = null,
    val identifier: JsonElement? = null,
    val deceased: Boolean? = null,
    val deceasedDateTime: String? = null,
    val maritalStatus: JsonElement? = null,
    val employmentStatus: JsonElement? = null,
    val education: JsonElement? = null,
    val organization: JsonElement? = null,
    val contact: JsonElement? = null,
    val dateOfBirth: String? = null,
    val dateOfRegistration: String? = null,
    val archived: Int? = null,
    val facilityId: Long? = null,
    val ninNumber: String? = null,
    val emrId: String? = null,
    val firstName: String? = null,
    val sex: String? = null,
    val surname: String? = null,
    val otherName: String? = null,
    val hospitalNumber: String? = null,
    @SerializedName(value = "isDateOfBirthEstimated", alternate = ["isDobEstimated"]) val isDateOfBirthEstimated: Boolean? = null,
    val fullName: String? = null,
    val caseManagerId: Long? = null,
    val reason: String? = null,
    val latitude: String? = null,
    val longitude: String? = null,
    val source: String? = null,
    val phoneNumber: String? = null,
    val currentStatus: String? = null  // "ART", "PrEP", "HTS", "HIVST", etc.
)
// Real LAMISPlus /api/v1/biometrics/patient/{id} response
data class BiometricEntry(
    val templateType: String?,
    val template: String?,
    val recapture: Int? = 0
)
data class PatientBiometricResponse(
    val personId: Long?,
    val capturedBiometricsList: List<BiometricEntry>? = null,
    val capturedBiometricsList2: List<BiometricEntry>? = null
)

data class EMRPatient(val id: Long, val uuid: String, val hospitalNumber: String, val firstName: String?, val surname: String?, val otherName: String?, val fullName: String?, val sex: String?, val dateOfBirth: String?, val isDateOfBirthEstimated: Boolean=false, val ninNumber: String?, val emrId: String?, val phoneNumber: String?, val facilityId: Long, val lastModifiedDate: String, val isActive: Boolean=true, val archived: Int=0)
data class EMRBiometric(val id: String, val personUuid: String, val template: String, val biometricType: String?, val templateType: String?, val enrollmentDate: String?, val deviceName: String?, val imageQuality: Int?, val iso: Boolean=false, val versionIso20: Boolean=false, val lastModifiedDate: String)
data class EMRArtPharmacy(val id: Long, val personUuid: String, val visitDate: String, val nextAppointment: String?, val regimenId: Long?, val mmdType: String?, val refillPeriod: Int?, val dsdModel: String?, val adherence: Boolean?, val lastModifiedDate: String)
data class ApiResponse<T>(val success: Boolean, val data: T?=null, val message: String?=null, val errorCode: String?=null)
data class HealthStatus(val status: String, val version: String, val timestamp: Long)
data class SyncStatus(val lastSyncDate: String?, val pendingRecords: Int, val facilityVersion: String)
data class AuthRequest(val username: String, val password: String)
data class AuthResponse(val id_token: String?)
data class EMRFacility(val id: Long, val name: String, val facilityCode: String? = null, val state: String? = null, val lga: String? = null)
data class UserOrganisationUnit(val id: Long, val applicationUserId: Long, val organisationUnitId: Long, val organisationUnitName: String, val datimId: String? = null, val archived: Int = 0)
data class AccountResponse(val id: Long, val userName: String, val currentOrganisationUnitId: Long?, val currentOrganisationUnitName: String?, val applicationUserOrganisationUnits: List<UserOrganisationUnit>? = null)