package com.softyorch.stroopoverload.ui.screen.auth

import com.softyorch.stroopoverload.R
import com.softyorch.stroopoverload.core.StringResolver
import com.softyorch.stroopoverload.data.AuthUser
import com.softyorch.stroopoverload.data.CooldownException
import com.softyorch.stroopoverload.data.FakeAuthRepository
import com.softyorch.stroopoverload.data.FakeGameRepository
import com.softyorch.stroopoverload.domain.UserProfile
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
class AuthViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val strings = StringResolver { id, args ->
        if (args.isEmpty()) "s$id" else "s$id(${args.joinToString()})"
    }
    private val auth = FakeAuthRepository()
    private val repository = FakeGameRepository()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun s(id: Int, vararg args: Any) = strings.get(id, *args)

    private fun viewModel(nowMs: Long = 1_000L) =
        AuthViewModel(auth, repository, strings, clock = { nowMs }).also { dispatcher.scheduler.advanceUntilIdle() }

    @Test
    fun `no session at start leaves the user logged out and syncs nothing`() = runTest {
        val vm = viewModel()

        assertFalse(vm.state.value.isLoggedIn)
        assertTrue(repository.syncCalls.isEmpty())
    }

    @Test
    fun `an existing unverified email session restores login and still asks for verification`() = runTest {
        auth.currentUid = "uid-7"
        auth.isAnonymousSession = false
        auth.isEmailVerified = false
        auth.pendingNickname = "Trinity"

        val state = viewModel().state.value

        assertTrue(state.isLoggedIn)
        assertEquals("uid-7", state.userUid)
        assertFalse(state.isAnonymous)
        assertTrue(state.needsEmailVerification)
        assertEquals(listOf("uid-7" to "Trinity"), repository.syncCalls)
    }

    @Test
    fun `an existing guest session never asks for email verification`() = runTest {
        auth.currentUid = "anon-1"
        auth.isAnonymousSession = true

        val state = viewModel().state.value

        assertTrue(state.isAnonymous)
        assertFalse(state.needsEmailVerification)
    }

    @Test
    fun `login with a blank field is rejected before reaching the backend`() = runTest {
        val vm = viewModel()

        vm.login("   ", "secret")

        assertEquals(s(R.string.auth_error_missing_credentials), vm.state.value.errorMessage)
        assertTrue(auth.signInCalls.isEmpty())
    }

    @Test
    fun `successful login trims the email, syncs the profile and logs in`() = runTest {
        auth.signInResult = Result.success(AuthUser("uid-9", isEmailVerified = true))
        val vm = viewModel()

        vm.login("  neo@example.com ", "pw")
        assertTrue(vm.state.value.isLoading)
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.state.value
        assertEquals(listOf("neo@example.com" to "pw"), auth.signInCalls)
        assertEquals("uid-9", repository.syncCalls.single().first)
        assertTrue(state.isLoggedIn)
        assertFalse(state.isLoading)
        assertFalse(state.needsEmailVerification)
    }

    @Test
    fun `wrong password maps to its own message and does not log in`() = runTest {
        auth.signInResult = Result.failure(Exception("ERROR_WRONG_PASSWORD: wrong-password"))
        val vm = viewModel()

        vm.login("neo@example.com", "bad")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(s(R.string.auth_error_wrong_password), vm.state.value.errorMessage)
        assertFalse(vm.state.value.isLoggedIn)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `network failure on login gets the network message`() = runTest {
        auth.signInResult = Result.failure(Exception("A network error (such as timeout) occurred"))
        val vm = viewModel()

        vm.login("neo@example.com", "pw")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(s(R.string.auth_error_network), vm.state.value.errorMessage)
    }

    @Test
    fun `invalid registration form is rejected locally with the specific reason`() = runTest {
        val vm = viewModel()

        vm.register("neo@example.com", "neo@example.com", "weak", "weak", "Neo")

        val expected = s(R.string.auth_register_rejected, s(R.string.auth_validation_password_short))
        assertEquals(expected, vm.state.value.errorMessage)
        assertTrue(auth.registerCalls.isEmpty())
    }

    @Test
    fun `malformed email is rejected locally`() = runTest {
        val vm = viewModel()

        vm.register("not-an-email", "not-an-email", "Str0ng!Pass", "Str0ng!Pass", "Neo")

        val expected = s(R.string.auth_register_rejected, s(R.string.auth_validation_invalid_email))
        assertEquals(expected, vm.state.value.errorMessage)
        assertTrue(auth.registerCalls.isEmpty())
    }

    @Test
    fun `successful registration syncs the nickname and starts the verification cooldown`() = runTest {
        auth.registerResult = Result.success(AuthUser("uid-new", isEmailVerified = false))
        val vm = viewModel(nowMs = 42_000L)

        vm.register(" neo@example.com", "neo@example.com ", "Str0ng!Pass", "Str0ng!Pass", "Neo")
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.state.value
        assertEquals(listOf("neo@example.com"), auth.registerCalls)
        assertEquals(listOf("uid-new" to "Neo"), repository.syncCalls)
        assertEquals(42_000L, repository.storedProfile.lastVerificationEmailSentAtEpochMs)
        assertTrue(state.isLoggedIn)
        assertTrue(state.needsEmailVerification)
        assertEquals(s(R.string.auth_register_success), state.successMessage)
    }

    @Test
    fun `guest sign-in failure reports an error instead of a fake session`() = runTest {
        auth.anonymousUid = null
        val vm = viewModel()

        vm.continueAsGuest()
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.state.value.isLoggedIn)
        assertEquals(s(R.string.auth_guest_error), vm.state.value.errorMessage)
        assertTrue(repository.syncCalls.isEmpty())
    }

    @Test
    fun `guest sign-in names the profile after the uid`() = runTest {
        auth.anonymousUid = "anon-uid-abcd"
        val vm = viewModel()

        vm.continueAsGuest()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.state.value.isLoggedIn)
        assertTrue(vm.state.value.isAnonymous)
        assertEquals(listOf("anon-uid-abcd" to "Guest_ABCD"), repository.syncCalls)
    }

    @Test
    fun `resend inside the cooldown reports the seconds left and keeps the old timestamp`() = runTest {
        repository.storedProfile = UserProfile(lastVerificationEmailSentAtEpochMs = 500L)
        auth.resendResult = { throw CooldownException(37) }
        val vm = viewModel()

        vm.resendVerificationEmail()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(s(R.string.auth_resend_cooldown, 37), vm.state.value.errorMessage)
        assertEquals(500L, repository.storedProfile.lastVerificationEmailSentAtEpochMs)
    }

    @Test
    fun `resend outside the cooldown stores when it was sent`() = runTest {
        auth.resendResult = { 90_000L }
        val vm = viewModel()

        vm.resendVerificationEmail()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(90_000L, repository.storedProfile.lastVerificationEmailSentAtEpochMs)
        assertEquals(s(R.string.auth_resend_success), vm.state.value.successMessage)
        assertNull(vm.state.value.errorMessage)
    }

    @Test
    fun `forgot password answers the same whether or not the reset succeeded`() = runTest {
        val vm = viewModel()

        auth.passwordResetResult = Result.success(Unit)
        vm.forgotPassword("known@example.com")
        dispatcher.scheduler.advanceUntilIdle()
        val whenKnown = vm.state.value.successMessage

        vm.clearMessages()
        auth.passwordResetResult = Result.failure(Exception("user-not-found"))
        vm.forgotPassword("unknown@example.com")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(s(R.string.auth_forgot_password_sent), whenKnown)
        assertEquals(whenKnown, vm.state.value.successMessage)
        assertNull(vm.state.value.errorMessage)
    }

    @Test
    fun `sign out resets the whole state`() = runTest {
        auth.currentUid = "uid-7"
        auth.isAnonymousSession = false
        val vm = viewModel()

        vm.signOut()

        assertEquals(AuthUiState(), vm.state.value)
        assertEquals(1, auth.signOutCount)
    }
}
