package com.carecompanion.data.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "app_user",
    indices = [Index(value = ["username"], unique = true)]
)
data class AppUser(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    val fullName: String,
    val role: String,               // ADMIN | CLINICIAN | DATA_OFFICER | VIEWER
    val hashedPin: String,          // hex(SHA-256(salt + pin))
    val salt: String,               // 16-byte random hex
    val facilityId: Long = 0L,
    val isActive: Boolean = true,
    val createdAt: Date = Date(),
    val lastLoginAt: Date? = null,
    val syncedFromWinco: Boolean = false
) {
    val roleLabel: String
        get() = when (role) {
            "ADMIN"        -> "Administrator"
            "CLINICIAN"    -> "Clinician"
            "DATA_OFFICER" -> "Data Officer"
            "VIEWER"       -> "Viewer"
            else           -> role
        }

    val canManageUsers: Boolean get() = role == "ADMIN"
    val canSync: Boolean get() = role == "ADMIN" || role == "DATA_OFFICER"
    val canViewBiometrics: Boolean get() = role != "VIEWER"
}

enum class UserRole(val code: String, val label: String) {
    ADMIN("ADMIN", "Administrator"),
    CLINICIAN("CLINICIAN", "Clinician"),
    DATA_OFFICER("DATA_OFFICER", "Data Officer"),
    VIEWER("VIEWER", "Viewer");

    companion object {
        fun fromCode(code: String): UserRole =
            values().firstOrNull { it.code == code } ?: VIEWER
    }
}
