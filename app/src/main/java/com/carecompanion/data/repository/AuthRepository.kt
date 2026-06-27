package com.carecompanion.data.repository

import com.carecompanion.data.database.entities.AppUser
import com.carecompanion.data.database.entities.UserRole

interface AuthRepository {
    /** True if at least one active user account exists in the local DB. */
    suspend fun hasAnyUser(): Boolean

    /** Create the first admin account (initial device setup). */
    suspend fun createAdminAccount(username: String, fullName: String, pin: String): AppUser

    /** Create an additional user account (admin only). */
    suspend fun createUser(
        username: String,
        fullName: String,
        pin: String,
        role: UserRole
    ): Result<AppUser>

    /**
     * Validate username + PIN. Returns the [AppUser] on success or null on failure.
     * Also records the login timestamp.
     */
    suspend fun login(username: String, pin: String): AppUser?

    /** Retrieve all active users (for admin management screen). */
    suspend fun getAllUsers(): List<AppUser>

    /** Change a user's PIN. */
    suspend fun changePin(userId: Long, newPin: String)

    /** Soft-delete a user account. */
    suspend fun deactivateUser(userId: Long)

    /** Retrieve a user by id. */
    suspend fun getUserById(id: Long): AppUser?
}
