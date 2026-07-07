package com.softyorch.stroopoverload.ui.screen.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.softyorch.stroopoverload.R
import com.softyorch.stroopoverload.data.AuthService
import com.softyorch.stroopoverload.data.ChangePasswordError
import com.softyorch.stroopoverload.data.ChangePasswordException
import com.softyorch.stroopoverload.data.DeleteAccountError
import com.softyorch.stroopoverload.data.DeleteAccountException
import com.softyorch.stroopoverload.data.FirebaseGameRepository
import com.softyorch.stroopoverload.domain.Achievement
import com.softyorch.stroopoverload.domain.CareerStats
import com.softyorch.stroopoverload.domain.UserProfile
import com.softyorch.stroopoverload.domain.XpSystem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val profile: UserProfile = UserProfile(),
    val editSnapshot: UserProfile? = null,
    val careerStats: CareerStats = CareerStats(),
    val achievements: List<Achievement> = emptyList(),
    val isEditing: Boolean = false,
    val xpInCurrentLevel: Int = 0,
    val xpNeededForNextLevel: Int = 100,
    val isProcessingAccountAction: Boolean = false,
    val changePasswordError: String? = null,
    val changePasswordSuccess: Boolean = false,
    val deleteAccountError: String? = null,
) {
    val hasUnsavedChanges: Boolean get() = isEditing && editSnapshot != null && profile != editSnapshot
}

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FirebaseGameRepository.getInstance(application)
    private val authService = AuthService()

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        loadProfile()
    }

    fun loadProfile() {
        viewModelScope.launch {
            val prof = repository.getProfile()
            val stats = repository.getCareerStats()
            val achs = repository.getAllAchievements()
            val (currentXp, neededXp) = XpSystem.xpProgressInCurrentLevel(prof.experience)
            _state.value = _state.value.copy(
                profile = prof,
                careerStats = stats,
                achievements = achs,
                xpInCurrentLevel = currentXp,
                xpNeededForNextLevel = neededXp
            )
        }
    }

    fun beginEdit() {
        _state.value = _state.value.copy(isEditing = true, editSnapshot = _state.value.profile)
    }

    fun updateDraftNickname(newNick: String) {
        if (!_state.value.isEditing) return
        val updated = _state.value.profile.copy(nickname = newNick)
        _state.value = _state.value.copy(profile = updated)
    }

    fun discardEdit() {
        val snap = _state.value.editSnapshot ?: return
        _state.value = _state.value.copy(profile = snap, isEditing = false, editSnapshot = null)
    }

    fun saveEdit() {
        val current = _state.value.profile
        viewModelScope.launch {
            val unique = current.generateUniqueName()
            val finalProfile = current.copy(uniqueName = unique)
            repository.updateProfile(finalProfile)
            _state.value = _state.value.copy(
                profile = finalProfile,
                isEditing = false,
                editSnapshot = null
            )
        }
    }

    fun signOut(onSignedOut: () -> Unit) {
        authService.signOut()
        onSignedOut()
    }

    private fun string(resId: Int): String = getApplication<Application>().getString(resId)

    fun changePassword(currentPassword: String, newPassword: String, confirmNewPassword: String) {
        if (newPassword != confirmNewPassword) {
            _state.value = _state.value.copy(changePasswordError = string(R.string.profile_change_password_mismatch))
            return
        }
        _state.value = _state.value.copy(isProcessingAccountAction = true, changePasswordError = null, changePasswordSuccess = false)
        viewModelScope.launch {
            authService.changePassword(currentPassword, newPassword)
                .onSuccess {
                    _state.value = _state.value.copy(isProcessingAccountAction = false, changePasswordSuccess = true)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(isProcessingAccountAction = false, changePasswordError = resolveChangePasswordError(e))
                }
        }
    }

    fun clearChangePasswordResult() {
        _state.value = _state.value.copy(changePasswordError = null, changePasswordSuccess = false)
    }

    fun deleteAccount(password: String, onDeleted: () -> Unit) {
        val uid = authService.currentUid
        if (uid == null) {
            _state.value = _state.value.copy(deleteAccountError = string(R.string.profile_delete_account_error_generic))
            return
        }
        _state.value = _state.value.copy(isProcessingAccountAction = true, deleteAccountError = null)
        viewModelScope.launch {
            authService.deleteAccount(password, wipeUserData = { repository.deleteAllUserData(uid) })
                .onSuccess {
                    _state.value = _state.value.copy(isProcessingAccountAction = false)
                    onDeleted()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(isProcessingAccountAction = false, deleteAccountError = resolveDeleteAccountError(e))
                }
        }
    }

    fun clearDeleteAccountError() {
        _state.value = _state.value.copy(deleteAccountError = null)
    }

    private fun resolveChangePasswordError(e: Throwable): String {
        val reason = (e as? ChangePasswordException)?.reason
        return when (reason) {
            is ChangePasswordError.WrongCurrentPassword -> string(R.string.profile_change_password_error_wrong_current)
            is ChangePasswordError.RequiresRecentLogin -> string(R.string.profile_change_password_error_recent_login)
            is ChangePasswordError.NetworkError -> string(R.string.profile_change_password_error_network)
            is ChangePasswordError.WeakNewPassword -> string(AuthService.registrationErrorRes(reason.reason))
            is ChangePasswordError.NotSignedIn, is ChangePasswordError.Unknown, null -> string(R.string.profile_change_password_error_generic)
        }
    }

    private fun resolveDeleteAccountError(e: Throwable): String {
        val reason = (e as? DeleteAccountException)?.reason
        return when (reason) {
            is DeleteAccountError.WrongPassword -> string(R.string.profile_delete_account_error_wrong_password)
            is DeleteAccountError.RequiresRecentLogin -> string(R.string.profile_delete_account_error_recent_login)
            is DeleteAccountError.NetworkError -> string(R.string.profile_delete_account_error_network)
            is DeleteAccountError.NotSignedIn, is DeleteAccountError.Unknown, null -> string(R.string.profile_delete_account_error_generic)
        }
    }
}
