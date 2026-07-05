package com.softyorch.stroopoverload.ui.screen.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.softyorch.stroopoverload.R
import com.softyorch.stroopoverload.data.AuthService
import com.softyorch.stroopoverload.data.CooldownException
import com.softyorch.stroopoverload.data.FirebaseGameRepository
import com.softyorch.stroopoverload.data.LoginError
import com.softyorch.stroopoverload.data.RegistrationError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val authService = AuthService()
    private val repository = FirebaseGameRepository.getInstance(application)

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    private var lastVerificationSentEpochMs: Long = 0L

    private fun string(resId: Int): String = getApplication<Application>().getString(resId)
    private fun string(resId: Int, vararg args: Any): String = getApplication<Application>().getString(resId, *args)

    init {
        checkCurrentSession()
    }

    private fun checkCurrentSession() {
        val uid = authService.currentUid
        if (uid != null) {
            viewModelScope.launch {
                repository.syncUserProfile(uid, authService.consumePendingNickname())
                val verified = authService.isEmailVerified || authService.currentUser?.isAnonymous == true
                _state.value = _state.value.copy(
                    isLoggedIn = true,
                    userUid = uid,
                    isAnonymous = authService.currentUser?.isAnonymous ?: true,
                    needsEmailVerification = !verified
                )
            }
        }
    }

    fun login(email: String, pass: String) {
        if (email.isBlank() || pass.isBlank()) {
            _state.value = _state.value.copy(errorMessage = string(R.string.auth_error_missing_credentials))
            return
        }
        _state.value = _state.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val res = authService.signInWithEmail(email, pass)
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

    fun register(email: String, pass: String, nickname: String) {
        val validationErr = AuthService.validateRegistration(email, pass, nickname)
        if (validationErr != null) {
            _state.value = _state.value.copy(errorMessage = string(R.string.auth_register_rejected, string(registrationErrorRes(validationErr))))
            return
        }
        _state.value = _state.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val res = authService.registerWithEmail(email, pass, nickname)
            res.onSuccess { user ->
                lastVerificationSentEpochMs = System.currentTimeMillis()
                repository.syncUserProfile(user.uid, nickname)
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

    private fun registrationErrorRes(error: RegistrationError): Int = when (error) {
        RegistrationError.NicknameTooShort -> R.string.auth_validation_nickname_short
        RegistrationError.InvalidEmailFormat -> R.string.auth_validation_invalid_email
        RegistrationError.PasswordTooShort -> R.string.auth_validation_password_short
        RegistrationError.PasswordNeedsUppercase -> R.string.auth_validation_password_needs_upper
        RegistrationError.PasswordNeedsLowercase -> R.string.auth_validation_password_needs_lower
        RegistrationError.PasswordNeedsDigit -> R.string.auth_validation_password_needs_digit
        RegistrationError.PasswordNeedsSymbol -> R.string.auth_validation_password_needs_symbol
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
            try {
                lastVerificationSentEpochMs = authService.resendVerificationEmailWithCooldown(lastVerificationSentEpochMs)
                _state.value = _state.value.copy(
                    successMessage = string(R.string.auth_resend_success),
                    errorMessage = null
                )
            } catch (e: CooldownException) {
                _state.value = _state.value.copy(
                    errorMessage = string(R.string.auth_resend_cooldown, e.remainingCooldownSeconds)
                )
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
        viewModelScope.launch {
            repository.clearLocalProgress()
            _state.value = AuthUiState()
        }
    }
}
