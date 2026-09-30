package com.softyorch.stroopoverload.data

import kotlinx.coroutines.CancellationException

/**
 * In-memory [AuthRepository]. Every call's outcome is configurable per test, and the
 * calls are recorded so a test can assert what the ViewModel asked for -- and, as
 * often, that it asked for nothing (a rejected form must never reach the backend).
 */
class FakeAuthRepository(
    override var currentUid: String? = null,
    override var isAnonymousSession: Boolean? = null,
    override var isEmailVerified: Boolean = false,
    var pendingNickname: String? = null,
) : AuthRepository {

    var signInResult: Result<AuthUser> = Result.success(AuthUser("uid-1", isEmailVerified = true))
    var registerResult: Result<AuthUser> = Result.success(AuthUser("uid-1", isEmailVerified = false))
    var anonymousUid: String? = "anon-uid-abcd"
    var resendResult: () -> Long = { 0L }
    var reloadVerified = false
    var passwordResetResult: Result<Unit> = Result.success(Unit)
    var changePasswordResult: Result<Unit> = Result.success(Unit)

    /** Stands in for reauthentication: a failure here means the wipe never runs, as in [AuthService]. */
    var deleteAccountReauthResult: Result<Unit> = Result.success(Unit)

    val signInCalls = mutableListOf<Pair<String, String>>()
    val registerCalls = mutableListOf<String>()
    val passwordResetCalls = mutableListOf<String>()
    val changePasswordCalls = mutableListOf<Pair<String, String>>()
    var signOutCount = 0
        private set
    var deleteAccountCount = 0
        private set

    override fun consumePendingNickname(): String? = pendingNickname.also { pendingNickname = null }

    override suspend fun signInWithEmail(email: String, pass: String): Result<AuthUser> {
        signInCalls += email to pass
        return signInResult
    }

    override suspend fun registerWithEmail(
        email: String,
        emailConfirm: String,
        pass: String,
        passConfirm: String,
        nickname: String,
    ): Result<AuthUser> {
        registerCalls += email
        return registerResult
    }

    override suspend fun signInAnonymously(): String? = anonymousUid

    override suspend fun resendVerificationEmailWithCooldown(lastSentEpochMs: Long): Long = resendResult()

    override suspend fun reloadUser(): Boolean = reloadVerified

    override fun signOut() {
        signOutCount++
        currentUid = null
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        passwordResetCalls += email
        return passwordResetResult
    }

    override suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> {
        changePasswordCalls += currentPassword to newPassword
        return changePasswordResult
    }

    override suspend fun deleteAccount(currentPassword: String, wipeUserData: suspend () -> Unit): Result<Unit> {
        deleteAccountCount++
        deleteAccountReauthResult.onFailure { return Result.failure(it) }
        return try {
            wipeUserData()
            currentUid = null
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(DeleteAccountException(DeleteAccountError.fromException(e)))
        }
    }
}
