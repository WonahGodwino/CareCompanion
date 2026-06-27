package com.carecompanion.data.repository

import com.carecompanion.data.database.dao.AppUserDao
import com.carecompanion.data.database.entities.AppUser
import com.carecompanion.data.database.entities.UserRole
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Date
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val appUserDao: AppUserDao
) : AuthRepository {

    override suspend fun hasAnyUser(): Boolean =
        appUserDao.countActiveUsers() > 0

    override suspend fun createAdminAccount(
        username: String,
        fullName: String,
        pin: String
    ): AppUser {
        val salt = generateSalt()
        val user = AppUser(
            username = username.trim().lowercase(),
            fullName = fullName.trim(),
            role = UserRole.ADMIN.code,
            hashedPin = hashPin(salt, pin),
            salt = salt,
            createdAt = Date()
        )
        val id = appUserDao.insert(user)
        return user.copy(id = id)
    }

    override suspend fun createUser(
        username: String,
        fullName: String,
        pin: String,
        role: UserRole
    ): Result<AppUser> = runCatching {
        val normalizedUsername = username.trim().lowercase()
        if (appUserDao.findByUsername(normalizedUsername) != null) {
            error("Username '$normalizedUsername' is already taken")
        }
        val salt = generateSalt()
        val user = AppUser(
            username = normalizedUsername,
            fullName = fullName.trim(),
            role = role.code,
            hashedPin = hashPin(salt, pin),
            salt = salt,
            createdAt = Date()
        )
        val id = appUserDao.insert(user)
        user.copy(id = id)
    }

    override suspend fun login(username: String, pin: String): AppUser? {
        val user = appUserDao.findByUsername(username.trim().lowercase()) ?: return null
        val expectedHash = hashPin(user.salt, pin)
        if (!expectedHash.equals(user.hashedPin, ignoreCase = true)) return null
        appUserDao.updateLastLogin(user.id, Date())
        return user
    }

    override suspend fun getAllUsers(): List<AppUser> = appUserDao.getAllActive()

    override suspend fun changePin(userId: Long, newPin: String) {
        val user = appUserDao.findById(userId) ?: return
        appUserDao.updatePin(userId, hashPin(user.salt, newPin))
    }

    override suspend fun deactivateUser(userId: Long) = appUserDao.deactivate(userId)

    override suspend fun getUserById(id: Long): AppUser? = appUserDao.findById(id)

    // ── Crypto helpers ──────────────────────────────────────────────────────────

    private fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hashPin(salt: String, pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = "$salt$pin".toByteArray(Charsets.UTF_8)
        return digest.digest(input).joinToString("") { "%02x".format(it) }
    }
}
