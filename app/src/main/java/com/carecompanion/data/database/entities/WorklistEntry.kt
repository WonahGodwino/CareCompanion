package com.carecompanion.data.database.entities

import androidx.room.ColumnInfo
import java.util.Date

/**
 * POJO — populated by the today's-clinic JOIN query in ArtPharmacyDao.
 * Carries patient demographics, the day's ART pharmacy record, and
 * biometric presence so the worklist can compute due services in one trip.
 */
data class WorklistEntry(
    @ColumnInfo(name = "uuid")                  val uuid: String,
    @ColumnInfo(name = "hospitalNumber")        val hospitalNumber: String,
    @ColumnInfo(name = "firstName")             val firstName: String?,
    @ColumnInfo(name = "surname")               val surname: String?,
    @ColumnInfo(name = "fullName")              val fullName: String?,
    @ColumnInfo(name = "sex")                   val sex: String?,
    @ColumnInfo(name = "dateOfBirth")           val dateOfBirth: Date?,
    @ColumnInfo(name = "artStartDate")          val artStartDate: Date?,
    @ColumnInfo(name = "lastViralLoadDate")     val lastViralLoadDate: Date?,
    @ColumnInfo(name = "lastViralLoadResult")   val lastViralLoadResult: Long?,
    @ColumnInfo(name = "lastTbScreeningDate")   val lastTbScreeningDate: Date?,
    @ColumnInfo(name = "lastTbScreeningStatus") val lastTbScreeningStatus: String?,
    @ColumnInfo(name = "ndrMatchedStatus")      val ndrMatchedStatus: String?,
    @ColumnInfo(name = "lastVisitDate")         val lastVisitDate: Date?,
    @ColumnInfo(name = "nextAppointment")       val nextAppointment: Date,
    @ColumnInfo(name = "refillPeriod")          val refillPeriod: Int?,
    @ColumnInfo(name = "regimenId")             val regimenId: Long?,
    @ColumnInfo(name = "dsdModel")              val dsdModel: String?,
    @ColumnInfo(name = "biometricCount")        val biometricCount: Int,
    @ColumnInfo(name = "lastBiometricDate")     val lastBiometricDate: Date?
) {
    val displayName: String
        get() = fullName?.takeIf { it.isNotBlank() }
            ?: listOfNotNull(surname?.trim(), firstName?.trim())
                .filter { it.isNotEmpty() }.joinToString(", ").takeIf { it.isNotBlank() }
            ?: hospitalNumber.takeIf { it.isNotBlank() } ?: "Unknown"

    val initial: Char
        get() = (surname?.firstOrNull() ?: firstName?.firstOrNull() ?: '?').uppercaseChar()
}

/**
 * POJO — populated by the no-biometric LEFT JOIN query in PatientDao.
 */
data class NoBiometricEntry(
    @ColumnInfo(name = "uuid")           val uuid: String,
    @ColumnInfo(name = "hospitalNumber") val hospitalNumber: String,
    @ColumnInfo(name = "firstName")      val firstName: String?,
    @ColumnInfo(name = "surname")        val surname: String?,
    @ColumnInfo(name = "fullName")       val fullName: String?,
    @ColumnInfo(name = "sex")            val sex: String?,
    @ColumnInfo(name = "dateOfBirth")    val dateOfBirth: Date?,
    @ColumnInfo(name = "currentStatus")  val currentStatus: String?,
    @ColumnInfo(name = "artStartDate")   val artStartDate: Date?,
    @ColumnInfo(name = "facilityId")     val facilityId: Long
) {
    val displayName: String
        get() = fullName?.takeIf { it.isNotBlank() }
            ?: listOfNotNull(surname?.trim(), firstName?.trim())
                .filter { it.isNotEmpty() }.joinToString(", ").takeIf { it.isNotBlank() }
            ?: hospitalNumber.takeIf { it.isNotBlank() } ?: "Unknown"

    val initial: Char
        get() = (surname?.firstOrNull() ?: firstName?.firstOrNull() ?: '?').uppercaseChar()
}

/**
 * POJO — populated by the TPT query in PatientDao. Includes TB screening
 * fields and latest IPT type from ART pharmacy for TPT status derivation.
 */
data class TptEntry(
    @ColumnInfo(name = "uuid")                  val uuid: String,
    @ColumnInfo(name = "hospitalNumber")        val hospitalNumber: String,
    @ColumnInfo(name = "firstName")             val firstName: String?,
    @ColumnInfo(name = "surname")               val surname: String?,
    @ColumnInfo(name = "fullName")              val fullName: String?,
    @ColumnInfo(name = "sex")                   val sex: String?,
    @ColumnInfo(name = "dateOfBirth")           val dateOfBirth: Date?,
    @ColumnInfo(name = "currentStatus")         val currentStatus: String?,
    @ColumnInfo(name = "artStartDate")          val artStartDate: Date?,
    @ColumnInfo(name = "lastTbScreeningDate")   val lastTbScreeningDate: Date?,
    @ColumnInfo(name = "lastTbScreeningStatus") val lastTbScreeningStatus: String?,
    @ColumnInfo(name = "iptType")               val iptType: String?,
    @ColumnInfo(name = "facilityId")            val facilityId: Long
) {
    val displayName: String
        get() = fullName?.takeIf { it.isNotBlank() }
            ?: listOfNotNull(surname?.trim(), firstName?.trim())
                .filter { it.isNotEmpty() }.joinToString(", ").takeIf { it.isNotBlank() }
            ?: hospitalNumber.takeIf { it.isNotBlank() } ?: "Unknown"

    val initial: Char
        get() = (surname?.firstOrNull() ?: firstName?.firstOrNull() ?: '?').uppercaseChar()

    /** TB screening status normalised for comparison. */
    private val normTbStatus: String?
        get() = lastTbScreeningStatus?.trim()?.uppercase()?.replace(" ", "_")

    val tptStatus: TptStatus
        get() = when {
            lastTbScreeningDate == null -> TptStatus.NOT_SCREENED
            normTbStatus?.contains("POSITIVE") == true ||
            normTbStatus?.contains("PRESUMPTIVE") == true ||
            normTbStatus?.contains("ACTIVE") == true -> TptStatus.TB_POSITIVE
            !iptType.isNullOrBlank() -> TptStatus.ON_IPT
            normTbStatus?.contains("NEGATIVE") == true -> TptStatus.ELIGIBLE
            else -> TptStatus.SCREENED_OTHER
        }
}

enum class TptStatus(val label: String, val description: String) {
    NOT_SCREENED("Not Screened",       "No TB screening on record — screen today"),
    ELIGIBLE     ("TPT Eligible",      "TB-negative; eligible to initiate preventive therapy"),
    ON_IPT       ("On IPT",            "Currently receiving TB preventive therapy"),
    TB_POSITIVE  ("TB Positive / ATB", "Active TB or presumptive TB — refer for treatment"),
    SCREENED_OTHER("Other",            "Screened; review status manually")
}
