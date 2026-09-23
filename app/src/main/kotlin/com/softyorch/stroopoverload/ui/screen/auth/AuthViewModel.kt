package com.softyorch.stroopoverload.ui.screen.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.softyorch.stroopoverload.R
import com.softyorch.stroopoverload.core.StringResolver
import com.softyorch.stroopoverload.data.AuthRepository
import com.softyorch.stroopoverload.data.AuthService
import com.softyorch.stroopoverload.data.CooldownException
import com.softyorch.stroopoverload.data.GameRepository
import com.softyorch.stroopoverload.data.LoginError
import com.softyorch.stroopoverload.data.RegistrationError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val isLoggedIn: Boolean = false,
    val userUid: String? = null,
    val isAnonymous: Boolean = true,
    val needsEmailVerification: Boolean = false,
    val cooldownRemainingSec: Int = 0,
)

class AuthViewModel(
    private val authService: AuthRepository,
    private val repository: GameRepository,
    private val strings: StringResolver,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    private fun string(resId: Int, vararg args: Any): String = strings.get(resId, *args)

    init {
        checkCurrentSession()
    }

    private fun checkCurrentSession() {
        val uid = authService.currentUid
        if (uid != null) {
            viewModelScope.launch {
                repository.syncUserProfile(uid, authService.consumePendingNickname())
                val isAnonymous = authService.isAnonymousSession ?: true
                val verified = authService.isEmailVerified || isAnonymous
                _state.value = _state.value.copy(
                    isLoggedIn = true,
                    userUid = uid,
                    isAnonymous = isAnonymous,
                    needsEmailVerification = !verified
                )
            }
        }
    }

    fun login(email: String, pass: String) {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank() || pass.isBlank()) {
            _state.value = _state.value.copy(errorMessage = string(R.string.auth_error_missing_credentials))
            return
        }
        _state.value = _state.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val res = authService.signInWithEmail(trimmedEmail, pass)
            res.onSuccess { user ->
                repository.syncUserProfile(user.uid, authService.consumePendingNickname())
                _state.value = _state.value.copy(
                    isLoading = false,
                    isLoggedIn = true,
                    userUid = user.uid,
                    isAnonymous = false,
                    needsEmailVerification = !user.isEmailVerified
                )
            }.onFailure { e ->
                val loginErr = LoginError.fromException(e)
                val msg = when (loginErr) {
                    is LoginError.WrongPassword -> string(R.string.auth_error_wrong_password)
                    is LoginError.InvalidEmail -> string(R.string.auth_error_invalid_email)
                    is LoginError.NetworkError -> string(R.string.auth_error_network)
                    is LoginError.Unknown -> string(R.string.auth_error_unknown, loginErr.message)
                }
                _state.value = _state.value.copy(isLoading = false, errorMessage = msg)
            }
        }
    }

    fun register(email: String, emailConfirm: String, pass: String, passConfirm: String, nickname: String) {
        val trimmedEmail = email.trim()
        val trimmedEmailConfirm = emailConfirm.trim()
        val validationErr = AuthService.validateRegistration(trimmedEmail, trimmedEmailConfirm, pass, passConfirm, nickname)
        if (validationErr != null) {
            _state.value = _state.value.copy(errorMessage = string(R.string.auth_register_rejected, string(AuthService.registrationErrorRes(validationErr))))
            return
        }
        _state.value = _state.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val res = authService.registerWithEmail(trimmedEmail, trimmedEmailConfirm, pass, passConfirm, nickname)
            res.onSuccess { user ->
                repository.syncUserProfile(user.uid, nickname)
                repository.updateProfile(repository.getProfile().copy(lastVerificationEmailSentAtEpochMs = clock()))
                _state.value = _state.value.copy(
                    isLoading = false,
                    isLoggedIn = true,
                    userUid = user.uid,
                    isAnonymous = false,
                    needsEmailVerification = !user.isEmailVerified,
                    successMessage = string(R.string.auth_register_success)
                )
            }.onFailure { e ->
                val loginErr = LoginError.fromException(e)
                val msg = when (loginErr) {
                    is LoginError.WrongPassword -> string(R.string.auth_register_error_weak_password)
                    is LoginError.InvalidEmail -> string(R.string.auth_register_error_invalid_email)
                    is LoginError.NetworkError -> string(R.string.auth_register_error_network)
                    is LoginError.Unknown -> string(R.string.auth_register_error_unknown, loginErr.message)
                }
                _state.value = _state.value.copy(isLoading = false, errorMessage = msg)
            }
        }
    }

    fun continueAsGuest() {
        _state.value = _state.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val uid = authService.signInAnonymously()
            if (uid != null) {
                repository.syncUserProfile(uid, "Guest_${uid.takeLast(4).uppercase()}")
                _state.value = _state.value.copy(
                    isLoading = false,
                    isLoggedIn = true,
                    userUid = uid,
                    isAnonymous = true,
                    needsEmailVerification = false
                )
            } else {
                _state.value = _state.value.copy(isLoading = false, errorMessage = string(R.string.auth_guest_error))
            }
        }
    }

    fun resendVerificationEmail() {
        viewModelScope.launch {
            val profile = repository.getProfile()
            try {
                val sentAt = authService.resendVerificationEmailWithCooldown(profile.lastVerificationEmailSentAtEpochMs)
                repository.updateProfile(profile.copy(lastVerificationEmailSentAtEpochMs = sentAt))
                _state.value = _state.value.copy(
                    successMessage = string(R.string.auth_resend_success),
                    errorMessage = null
                )
            } catch (e: CooldownException) {
                _state.value = _state.value.copy(
                    errorMessage = string(R.string.auth_resend_cooldown, e.remainingCooldownSeconds)
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(errorMessage = string(R.string.auth_resend_failed))
            }
        }
    }

    fun checkEmailVerified() {
        viewModelScope.launch {
            val verified = authService.reloadUser()
            if (verified) {
                _state.value = _state.value.copy(needsEmailVerification = false, successMessage = string(R.string.auth_verified_success))
            } else {
                _state.value = _state.value.copy(errorMessage = string(R.string.auth_still_pending))
            }
        }
    }

    fun clearMessages() {
        _state.value = _state.value.copy(errorMessage = null, successMessage = null)
    }

    fun signOut() {
        authService.signOut()
        _state.value = AuthUiState()
    }

    fun forgotPassword(email: String) {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank()) {
            _state.value = _state.value.copy(errorMessage = string(R.string.auth_error_missing_credentials))
            return
        }
        _state.value = _state.value.copy(isLoading = true, errorMessage = null, successMessage = null)
        viewModelScope.launch {
            authService.sendPasswordResetEmail(trimmedEmail)
            // Always the same generic confirmation regardless of outcome — never reveal
            // whether the email belongs to an existing account (user enumeration).
            _state.value = _state.value.copy(isLoading = false, successMessage = string(R.string.auth_forgot_password_sent))
        }
    }
}
