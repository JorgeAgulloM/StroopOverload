package com.softyorch.stroopoverload.ui.screen.profile

import com.softyorch.stroopoverload.R
import com.softyorch.stroopoverload.core.StringResolver
import com.softyorch.stroopoverload.data.ChangePasswordError
import com.softyorch.stroopoverload.data.ChangePasswordException
import com.softyorch.stroopoverload.data.DeleteAccountError
import com.softyorch.stroopoverload.data.DeleteAccountException
import com.softyorch.stroopoverload.data.FakeAuthRepository
import com.softyorch.stroopoverload.data.FakeGameRepository
import com.softyorch.stroopoverload.data.RegistrationError
import com.softyorch.stroopoverload.domain.CareerStats
import com.softyorch.stroopoverload.domain.UserProfile
import com.softyorch.stroopoverload.domain.XpSystem
import com.softyorch.stroopoverload.ui.screen.multiplayer.FakeMultiplayerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val strings = StringResolver { id, args ->
        if (args.isEmpty()) "s$id" else "s$id(${args.joinToString()})"
    }
    private val auth = FakeAuthRepository(currentUid = "uid-1", isAnonymousSession = false)
    private val repository = FakeGameRepository(
        storedProfile = UserProfile(userId = "uid-1", nickname = "Neo", experience = 250L, profileCreated = true),
        storedCareerStats = CareerStats(totalGamesPlayed = 12),
    )
    private val multiplayer = FakeMultiplayerRepository()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun s(id: Int) = strings.get(id)

    private fun viewModel() =
        ProfileViewModel(repository, auth, multiplayer, strings).also { dispatcher.scheduler.advanceUntilIdle() }

    @Test
    fun `loading fills the profile, career stats and XP progress`() = runTest {
        val state = viewModel().state.value

        val (inLevel, needed) = XpSystem.xpProgressInCurrentLevel(250L)
        assertEquals("Neo", state.profile.nickname)
        assertEquals(12, state.careerStats.totalGamesPlayed)
        assertEquals(inLevel, state.xpInCurrentLevel)
        assertEquals(needed, state.xpNeededForNextLevel)
    }

    @Test
    fun `nickname edits are ignored outside edit mode`() = runTest {
        val vm = viewModel()

        vm.updateDraftNickname("Hacked")

        assertEquals("Neo", vm.state.value.profile.nickname)
    }

    @Test
    fun `discarding an edit restores the snapshot and saves nothing`() = runTest {
        val vm = viewModel()

        vm.beginEdit()
        vm.updateDraftNickname("Morpheus")
        assertTrue(vm.state.value.hasUnsavedChanges)
        vm.discardEdit()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Neo", vm.state.value.profile.nickname)
        assertFalse(vm.state.value.isEditing)
        assertEquals("Neo", repository.storedProfile.nickname)
    }

    @Test
    fun `saving an edit regenerates the unique name and persists it`() = runTest {
        val vm = viewModel()

        vm.beginEdit()
        vm.updateDraftNickname("Morpheus")
        vm.saveEdit()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Morpheus", repository.storedProfile.nickname)
        assertEquals("@morpheus-id-1", repository.storedProfile.uniqueName)
        assertFalse(vm.state.value.isEditing)
        assertFalse(vm.state.value.hasUnsavedChanges)
    }

    @Test
    fun `mismatched new passwords are rejected before reaching the backend`() = runTest {
        val vm = viewModel()

        vm.changePassword("Old1!pass", "N3w!pass", "N3w!pasz")

        assertEquals(s(R.string.profile_change_password_mismatch), vm.state.value.changePasswordError)
        assertTrue(auth.changePasswordCalls.isEmpty())
    }

    @Test
    fun `wrong current password gets its own message`() = runTest {
        auth.changePasswordResult = Result.failure(ChangePasswordException(ChangePasswordError.WrongCurrentPassword))
        val vm = viewModel()

        vm.changePassword("bad", "N3w!pass", "N3w!pass")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(s(R.string.profile_change_password_error_wrong_current), vm.state.value.changePasswordError)
        assertFalse(vm.state.value.changePasswordSuccess)
        assertFalse(vm.state.value.isProcessingAccountAction)
    }

    @Test
    fun `a weak new password reports the exact rule it breaks`() = runTest {
        auth.changePasswordResult = Result.failure(
            ChangePasswordException(ChangePasswordError.WeakNewPassword(RegistrationError.PasswordNeedsDigit))
        )
        val vm = viewModel()

        vm.changePassword("Old1!pass", "NoDigits!", "NoDigits!")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(s(R.string.auth_validation_password_needs_digit), vm.state.value.changePasswordError)
    }

    @Test
    fun `successful password change is reported and can be cleared`() = runTest {
        val vm = viewModel()

        vm.changePassword("Old1!pass", "N3w!pass", "N3w!pass")
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.state.value.changePasswordSuccess)
        assertNull(vm.state.value.changePasswordError)

        vm.clearChangePasswordResult()
        assertFalse(vm.state.value.changePasswordSuccess)
    }

    @Test
    fun `deleting with nobody signed in fails without touching any data`() = runTest {
        auth.currentUid = null
        val vm = viewModel()
        var deleted = false

        vm.deleteAccount("pw") { deleted = true }
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(deleted)
        assertEquals(s(R.string.profile_delete_account_error_generic), vm.state.value.deleteAccountError)
        assertEquals(0, auth.deleteAccountCount)
        assertTrue(repository.deletedUids.isEmpty())
    }

    @Test
    fun `deleting wipes the profile and the multiplayer data before reporting success`() = runTest {
        val vm = viewModel()
        var deleted = false

        vm.deleteAccount("Old1!pass") { deleted = true }
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(deleted)
        assertEquals(listOf("uid-1"), repository.deletedUids)
        assertEquals(1, multiplayer.deleteMyMultiplayerDataCallCount)
        assertFalse(vm.state.value.isProcessingAccountAction)
    }

    @Test
    fun `a wrong password stops deletion before any data is wiped`() = runTest {
        auth.deleteAccountReauthResult = Result.failure(DeleteAccountException(DeleteAccountError.WrongPassword))
        val vm = viewModel()
        var deleted = false

        vm.deleteAccount("bad") { deleted = true }
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(deleted)
        assertTrue(repository.deletedUids.isEmpty())
        assertEquals(0, multiplayer.deleteMyMultiplayerDataCallCount)
        assertEquals(s(R.string.profile_delete_account_error_wrong_password), vm.state.value.deleteAccountError)
    }

    @Test
    fun `a failed multiplayer wipe is reported as a failure, not a deleted account`() = runTest {
        multiplayer.deleteMyMultiplayerDataResult = Result.failure(Exception("permission-denied"))
        val vm = viewModel()
        var deleted = false

        vm.deleteAccount("Old1!pass") { deleted = true }
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(deleted)
        assertEquals(s(R.string.profile_delete_account_error_generic), vm.state.value.deleteAccountError)
        assertFalse(vm.state.value.isProcessingAccountAction)
    }

    @Test
    fun `sign out calls the backend and then the callback`() = runTest {
        val vm = viewModel()
        var signedOut = false

        vm.signOut { signedOut = true }

        assertTrue(signedOut)
        assertEquals(1, auth.signOutCount)
    }
}
