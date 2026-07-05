package com.softyorch.stroopoverload.ui.screen.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.softyorch.stroopoverload.data.AuthService
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
}
