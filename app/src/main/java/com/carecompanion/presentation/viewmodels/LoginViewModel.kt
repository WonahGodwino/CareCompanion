package com.carecompanion.presentation.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carecompanion.data.database.entities.AppUser
import com.carecompanion.data.database.entities.UserRole
import com.carecompanion.data.repository.AuthRepository
import com.carecompanion.utils.SharedPreferencesHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val isLoading: Boolean = true,
    val needsSetup: Boolean = false,     // no user exists yet — show first-time setup
    val isLoggedIn: Boolean = false,
    val loggedInUser: AppUser? = null,
    val errorMessage: String? = null,
    // Setup form fields
    val setupFullName: String = "",
    val setupUsername: String = "",
    val setupPin: String = "",
    val setupConfirmPin: String = "",
    val setupError: String? = null,
    // Login form fields
    val loginUsername: String = "",
    val loginPin: String = "",
    val loginError: String? = null
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        checkSessionOrUserExists()
    }

    private fun checkSessionOrUserExists() {
        viewModelScope.launch {
            // If there is a valid cached session, skip the login screen
            if (SharedPreferencesHelper.isSessionValid()) {
                val userId = SharedPreferencesHelper.getLoggedInUserId()
                val user = authRepository.getUserById(userId)
                if (user != null) {
                    _uiState.value = LoginUiState(isLoading = false, isLoggedIn = true, loggedInUser = user)
                    return@launch
                }
            }
            val hasUsers = authRepository.hasAnyUser()
            _uiState.value = LoginUiState(isLoading = false, needsSetup = !hasUsers)
        }
    }

    // ── Login form ──────────────────────────────────────────────────────────────

    fun onLoginUsernameChanged(v: String) {
        _uiState.value = _uiState.value.copy(loginUsername = v, loginError = null)
    }

    fun onLoginPinChanged(v: String) {
        if (v.length <= 6 && v.all { it.isDigit() }) {
            _uiState.value = _uiState.value.copy(loginPin = v, loginError = null)
        }
    }

    fun login() {
        val state = _uiState.value
        if (state.loginUsername.isBlank() || state.loginPin.length < 4) {
            _uiState.value = state.copy(loginError = "Enter your username and a 4–6 digit PIN")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, loginError = null)
            val user = authRepository.login(state.loginUsername, state.loginPin)
            if (user == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    loginError = "Incorrect username or PIN"
                )
            } else {
                persistSession(user)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isLoggedIn = true,
                    loggedInUser = user,
                    loginError = null
                )
            }
        }
    }

    // ── First-time setup form ────────────────────────────────────────────────────

    fun onSetupFullNameChanged(v: String) {
        _uiState.value = _uiState.value.copy(setupFullName = v, setupError = null)
    }

    fun onSetupUsernameChanged(v: String) {
        _uiState.value = _uiState.value.copy(setupUsername = v.lowercase().trim(), setupError = null)
    }

    fun onSetupPinChanged(v: String) {
        if (v.length <= 6 && v.all { it.isDigit() }) {
            _uiState.value = _uiState.value.copy(setupPin = v, setupError = null)
        }
    }

    fun onSetupConfirmPinChanged(v: String) {
        if (v.length <= 6 && v.all { it.isDigit() }) {
            _uiState.value = _uiState.value.copy(setupConfirmPin = v, setupError = null)
        }
    }

    fun createAdminAccount() {
        val state = _uiState.value
        val error = when {
            state.setupFullName.isBlank()                -> "Full name is required"
            state.setupUsername.isBlank()                -> "Username is required"
            state.setupUsername.contains(" ")            -> "Username must not contain spaces"
            state.setupPin.length < 4                    -> "PIN must be at least 4 digits"
            state.setupPin != state.setupConfirmPin      -> "PINs do not match"
            else                                         -> null
        }
        if (error != null) {
            _uiState.value = state.copy(setupError = error)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, setupError = null)
            val user = authRepository.createAdminAccount(
                username = state.setupUsername,
                fullName = state.setupFullName,
                pin = state.setupPin
            )
            persistSession(user)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isLoggedIn = true,
                needsSetup = false,
                loggedInUser = user
            )
        }
    }

    fun logout() {
        SharedPreferencesHelper.clearSession()
        _uiState.value = LoginUiState(isLoading = false, needsSetup = false)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(loginError = null, setupError = null, errorMessage = null)
    }

    private fun persistSession(user: AppUser) {
        SharedPreferencesHelper.setLoggedInUserId(user.id)
        SharedPreferencesHelper.setSessionStartedAt(System.currentTimeMillis())
        SharedPreferencesHelper.setLoggedInUsername(user.username)
        SharedPreferencesHelper.setLoggedInUserFullName(user.fullName)
        SharedPreferencesHelper.setLoggedInUserRole(user.role)
    }
}
