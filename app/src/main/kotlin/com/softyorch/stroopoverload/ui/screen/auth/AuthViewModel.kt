package com.softyorch.stroopoverload.ui.screen.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.softyorch.stroopoverload.data.AuthService
import com.softyorch.stroopoverload.data.CooldownException
import com.softyorch.stroopoverload.data.FirebaseGameRepository
import com.softyorch.stroopoverload.data.LoginError
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
            _state.value = _state.value.copy(errorMessage = "[ ERROR // MISSING CREDENTIALS ]")
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
                    is LoginError.WrongPassword -> "[ DENIED // INVALID PASSWORD ]"
                    is LoginError.InvalidEmail -> "[ DENIED // UNKNOWN EMAIL OR BAD FORMAT ]"
                    is LoginError.NetworkError -> "[ OFFLINE // NETWORK CONNECTION TIMEOUT ]"
                    is LoginError.Unknown -> "[ DENIED // ${loginErr.message} ]"
                }
                _state.value = _state.value.copy(isLoading = false, errorMessage = msg)
            }
        }
    }

    fun register(email: String, pass: String, nickname: String) {
        val validationErr = AuthService.validateRegistration(email, pass, nickname)
        if (validationErr != null) {
            _state.value = _state.value.copy(errorMessage = "[ REGISTRATION REJECTED // ${validationErr.uppercase()} ]")
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
                    successMessage = "[ REGISTRATION SUCCESS // VERIFICATION EMAIL DISPATCHED ]"
                )
            }.onFailure { e ->
                val loginErr = LoginError.fromException(e)
                val msg = when (loginErr) {
                    is LoginError.WrongPassword -> "[ REJECTED // WEAK PASSWORD ]"
                    is LoginError.InvalidEmail -> "[ REJECTED // INVALID EMAIL FORMAT ]"
                    is LoginError.NetworkError -> "[ OFFLINE // NETWORK ERROR DURING REGISTRATION ]"
                    is LoginError.Unknown -> "[ REJECTED // ${loginErr.message} ]"
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
                _state.value = _state.value.copy(isLoading = false, errorMessage = "[ ERROR // GUEST NEURAL LINK FAILED ]")
            }
        }
    }

    fun resendVerificationEmail() {
        viewModelScope.launch {
            try {
                lastVerificationSentEpochMs = authService.resendVerificationEmailWithCooldown(lastVerificationSentEpochMs)
                _state.value = _state.value.copy(
                    successMessage = "[ SIGNAL SENT // VERIFICATION EMAIL RE-DISPATCHED ]",
                    errorMessage = null
                )
            } catch (e: CooldownException) {
                _state.value = _state.value.copy(
                    errorMessage = "[ COOLDOWN ACTIVE // WAIT ${e.remainingCooldownSeconds}S BEFORE RESENDING ]"
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(errorMessage = "[ ERROR // DISPATCH FAILED ]")
            }
        }
    }

    fun checkEmailVerified() {
        viewModelScope.launch {
            val verified = authService.reloadUser()
            if (verified) {
                _state.value = _state.value.copy(needsEmailVerification = false, successMessage = "[ NEURAL LINK VERIFIED ]")
            } else {
                _state.value = _state.value.copy(errorMessage = "[ STATUS // EMAIL STILL PENDING VERIFICATION ]")
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
