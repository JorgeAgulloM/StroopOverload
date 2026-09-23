package com.softyorch.stroopoverload.data

/** The signed-in account as the app needs it, without leaking Firebase's [com.google.firebase.auth.FirebaseUser]. */
data class AuthUser(val uid: String, val isEmailVerified: Boolean)

/**
 * Everything the auth and profile screens ask of the authentication backend. It exists
 * so those ViewModels can be tested: [AuthService] talks to FirebaseAuth directly, and
 * without this seam login, registration, password change and account deletion -- the
 * flows that touch credentials and personal data -- could not be unit tested at all.
 */
interface AuthRepository {
    val currentUid: String?
    /** Null when nobody is signed in. */
    val isAnonymousSession: Boolean?
    val isEmailVerified: Boolean

    fun consumePendingNickname(): String?

    suspend fun signInWithEmail(email: String, pass: String): Result<AuthUser>

    suspend fun registerWithEmail(
        email: String,
        emailConfirm: String,
        pass: String,
        passConfirm: String,
        nickname: String,
    ): Result<AuthUser>

    /** @return the uid, or null if sign-in failed. */
    suspend fun signInAnonymously(): String?

    /**
     * @return when the email was sent, to be stored as the next cooldown's start.
     * @throws CooldownException if the previous one was sent too recently.
     */
    suspend fun resendVerificationEmailWithCooldown(lastSentEpochMs: Long): Long

    /** @return whether the email is verified after reloading the account. */
    suspend fun reloadUser(): Boolean

    fun signOut()

    suspend fun sendPasswordResetEmail(email: String): Result<Unit>

    /** Fails with a [ChangePasswordException]. */
    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit>

    /**
     * Runs [wipeUserData] while the session is still valid, then deletes the account.
     * Fails with a [DeleteAccountException].
     */
    suspend fun deleteAccount(currentPassword: String, wipeUserData: suspend () -> Unit): Result<Unit>
}
