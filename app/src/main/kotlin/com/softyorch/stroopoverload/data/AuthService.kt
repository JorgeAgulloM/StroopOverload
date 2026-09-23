package com.softyorch.stroopoverload.data

import android.util.Log
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.softyorch.stroopoverload.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

sealed interface LoginError {
    object WrongPassword : LoginError
    object InvalidEmail : LoginError
    object NetworkError : LoginError
    data class Unknown(val message: String) : LoginError

    companion object {
        fun fromException(e: Throwable): LoginError {
            val msg = e.message ?: return Unknown("Unknown error occurred")
            return when {
                msg.contains("INVALID_CREDENTIAL", ignoreCase = true) ||
                    msg.contains("wrong-password", ignoreCase = true) ||
                    msg.contains("invalid-password", ignoreCase = true) -> WrongPassword
                msg.contains("invalid-email", ignoreCase = true) ||
                    msg.contains("badly formatted", ignoreCase = true) -> InvalidEmail
                msg.contains("network", ignoreCase = true) ||
                    msg.contains("timeout", ignoreCase = true) -> NetworkError
                else -> Unknown(msg)
            }
        }
    }
}

class CooldownException(val remainingCooldownSeconds: Int) : Exception("Please wait $remainingCooldownSeconds seconds before resending verification email.")

sealed interface RegistrationError {
    object NicknameTooShort : RegistrationError
    object InvalidEmailFormat : RegistrationError
    object EmailMismatch : RegistrationError
    object PasswordTooShort : RegistrationError
    object PasswordNeedsUppercase : RegistrationError
    object PasswordNeedsLowercase : RegistrationError
    object PasswordNeedsDigit : RegistrationError
    object PasswordNeedsSymbol : RegistrationError
    object PasswordMismatch : RegistrationError
}

class RegistrationValidationException(val reason: RegistrationError) : Exception()

sealed interface ChangePasswordError {
    object WrongCurrentPassword : ChangePasswordError
    object RequiresRecentLogin : ChangePasswordError
    object NetworkError : ChangePasswordError
    object NotSignedIn : ChangePasswordError
    data class WeakNewPassword(val reason: RegistrationError) : ChangePasswordError
    data class Unknown(val message: String) : ChangePasswordError

    companion object {
        fun fromException(e: Throwable): ChangePasswordError {
            val msg = e.message ?: return Unknown("Unknown error occurred")
            return when {
                msg.contains("INVALID_CREDENTIAL", ignoreCase = true) ||
                    msg.contains("wrong-password", ignoreCase = true) ||
                    msg.contains("invalid-password", ignoreCase = true) -> WrongCurrentPassword
                msg.contains("requires-recent-login", ignoreCase = true) -> RequiresRecentLogin
                msg.contains("network", ignoreCase = true) ||
                    msg.contains("timeout", ignoreCase = true) -> NetworkError
                else -> Unknown(msg)
            }
        }
    }
}

class ChangePasswordException(val reason: ChangePasswordError) : Exception()

sealed interface DeleteAccountError {
    object WrongPassword : DeleteAccountError
    object RequiresRecentLogin : DeleteAccountError
    object NetworkError : DeleteAccountError
    object NotSignedIn : DeleteAccountError
    data class Unknown(val message: String) : DeleteAccountError

    companion object {
        fun fromException(e: Throwable): DeleteAccountError {
            val msg = e.message ?: return Unknown("Unknown error occurred")
            return when {
                msg.contains("INVALID_CREDENTIAL", ignoreCase = true) ||
                    msg.contains("wrong-password", ignoreCase = true) ||
                    msg.contains("invalid-password", ignoreCase = true) -> WrongPassword
                msg.contains("requires-recent-login", ignoreCase = true) -> RequiresRecentLogin
                msg.contains("network", ignoreCase = true) ||
                    msg.contains("timeout", ignoreCase = true) -> NetworkError
                else -> Unknown(msg)
            }
        }
    }
}

class DeleteAccountException(val reason: DeleteAccountError) : Exception()

class AuthService(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    val currentUser: FirebaseUser? get() = auth.currentUser
    val currentUid: String? get() = auth.currentUser?.uid
    val isEmailVerified: Boolean get() = auth.currentUser?.isEmailVerified ?: false

    var pendingNickname: String? = null
        private set

    fun consumePendingNickname(): String? {
        val nick = pendingNickname
        pendingNickname = null
        return nick
    }

    suspend fun signInWithEmail(email: String, pass: String): Result<FirebaseUser> = try {
        val res = auth.signInWithEmailAndPassword(email, pass).await()
        val user = res.user
        if (user != null) Result.success(user) else Result.failure(Exception("User is null"))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e("AuthService", "SignIn error: ${e.message}", e)
        Result.failure(e)
    }

    suspend fun registerWithEmail(
        email: String,
        emailConfirm: String,
        pass: String,
        passConfirm: String,
        nickname: String,
    ): Result<FirebaseUser> {
        val validationErr = validateRegistration(email, emailConfirm, pass, passConfirm, nickname)
        if (validationErr != null) return Result.failure(RegistrationValidationException(validationErr))

        return try {
            pendingNickname = nickname.trim()
            val res = auth.createUserWithEmailAndPassword(email, pass).await()
            val user = res.user
            if (user != null) {
                sendVerificationEmailFireAndForget()
                Result.success(user)
            } else {
                Result.failure(Exception("User creation returned null"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("AuthService", "Register error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun signInAnonymously(): String? {
        if (currentUid != null) return currentUid
        return try {
            val result = auth.signInAnonymously().await()
            result.user?.uid
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // No fake uid on failure: a caller that got one would report the player as
            // signed in with a local id that no Firebase session backs, so nothing they
            // played would ever reach the cloud. Null lets the caller say sign-in failed.
            Log.e("AuthService", "Anonymous sign-in failed: ${e.message}", e)
            null
        }
    }

    fun sendVerificationEmailFireAndForget() {
        try {
            auth.currentUser?.sendEmailVerification()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("AuthService", "Failed to send verification email: ${e.message}")
        }
    }

    suspend fun resendVerificationEmailWithCooldown(lastSentEpochMs: Long): Long {
        val now = System.currentTimeMillis()
        val elapsedSec = ((now - lastSentEpochMs) / 1000).toInt()
        val cooldownSec = 60
        if (elapsedSec < cooldownSec && lastSentEpochMs > 0) {
            throw CooldownException(cooldownSec - elapsedSec)
        }
        try {
            auth.currentUser?.sendEmailVerification()?.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("AuthService", "Resend verification email failed: ${e.message}")
        }
        return now
    }

    suspend fun reloadUser(): Boolean {
        return try {
            auth.currentUser?.reload()?.await()
            isEmailVerified
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    fun signOut() {
        try {
            auth.signOut()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("AuthService", "SignOut error: ${e.message}")
        }
        pendingNickname = null
    }

    suspend fun sendPasswordResetEmail(email: String): Result<Unit> = try {
        auth.sendPasswordResetEmail(email.trim()).await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("AuthService", "Password reset error: ${e.message}", e)
        Result.failure(e)
    }

    /** Re-verifies the current user's password. Required by Firebase before updatePassword/delete. */
    private suspend fun reauthenticate(currentPassword: String): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("not signed in"))
        val email = user.email ?: return Result.failure(IllegalStateException("not signed in"))
        return try {
            val credential = EmailAuthProvider.getCredential(email, currentPassword)
            user.reauthenticate(credential).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("AuthService", "Reauthenticate error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(ChangePasswordException(ChangePasswordError.NotSignedIn))

        reauthenticate(currentPassword).onFailure { e ->
            return Result.failure(ChangePasswordException(ChangePasswordError.fromException(e)))
        }

        val strengthError = validatePasswordStrength(newPassword)
        if (strengthError != null) return Result.failure(ChangePasswordException(ChangePasswordError.WeakNewPassword(strengthError)))

        return try {
            user.updatePassword(newPassword).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("AuthService", "Change password error: ${e.message}", e)
            Result.failure(ChangePasswordException(ChangePasswordError.fromException(e)))
        }
    }

    /**
     * Reauthenticates, then wipes remote/local data via [wipeUserData] (while the session is still
     * valid, since Firestore rules need request.auth.uid to match), then deletes the Firebase user.
     */
    suspend fun deleteAccount(currentPassword: String, wipeUserData: suspend () -> Unit): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(DeleteAccountException(DeleteAccountError.NotSignedIn))

        reauthenticate(currentPassword).onFailure { e ->
            return Result.failure(DeleteAccountException(DeleteAccountError.fromException(e)))
        }

        return try {
            wipeUserData()
            user.delete().await()
            pendingNickname = null
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("AuthService", "Delete account error: ${e.message}", e)
            Result.failure(DeleteAccountException(DeleteAccountError.fromException(e)))
        }
    }

    companion object {
        @androidx.annotation.StringRes
        fun registrationErrorRes(error: RegistrationError): Int = when (error) {
            RegistrationError.NicknameTooShort -> R.string.auth_validation_nickname_short
            RegistrationError.InvalidEmailFormat -> R.string.auth_validation_invalid_email
            RegistrationError.EmailMismatch -> R.string.auth_validation_email_mismatch
            RegistrationError.PasswordTooShort -> R.string.auth_validation_password_short
            RegistrationError.PasswordNeedsUppercase -> R.string.auth_validation_password_needs_upper
            RegistrationError.PasswordNeedsLowercase -> R.string.auth_validation_password_needs_lower
            RegistrationError.PasswordNeedsDigit -> R.string.auth_validation_password_needs_digit
            RegistrationError.PasswordNeedsSymbol -> R.string.auth_validation_password_needs_symbol
            RegistrationError.PasswordMismatch -> R.string.auth_validation_password_mismatch
        }

        fun validatePasswordStrength(pass: String): RegistrationError? {
            if (pass.length < 8) return RegistrationError.PasswordTooShort
            if (!pass.any { it.isUpperCase() }) return RegistrationError.PasswordNeedsUppercase
            if (!pass.any { it.isLowerCase() }) return RegistrationError.PasswordNeedsLowercase
            if (!pass.any { it.isDigit() }) return RegistrationError.PasswordNeedsDigit
            val symbolRegex = "[^A-Za-z0-9]".toRegex()
            if (!pass.contains(symbolRegex)) return RegistrationError.PasswordNeedsSymbol
            return null
        }

        fun validateRegistration(
            email: String,
            emailConfirm: String,
            pass: String,
            passConfirm: String,
            nickname: String,
        ): RegistrationError? {
            if (nickname.trim().length < 3) return RegistrationError.NicknameTooShort
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) return RegistrationError.InvalidEmailFormat
            if (email.trim() != emailConfirm.trim()) return RegistrationError.EmailMismatch
            validatePasswordStrength(pass)?.let { return it }
            if (pass != passConfirm) return RegistrationError.PasswordMismatch
            return null
        }
    }
}
