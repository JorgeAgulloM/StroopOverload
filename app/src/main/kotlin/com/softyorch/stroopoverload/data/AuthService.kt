package com.softyorch.stroopoverload.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
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

class AuthService(
    private val auth: FirebaseAuth = try { FirebaseAuth.getInstance() } catch (e: Exception) { null } ?: FirebaseAuth.getInstance()
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
    } catch (e: Exception) {
        Log.e("AuthService", "SignIn error: ${e.message}", e)
        Result.failure(e)
    }

    suspend fun registerWithEmail(email: String, pass: String, nickname: String): Result<FirebaseUser> {
        val validationErr = validateRegistration(email, pass, nickname)
        if (validationErr != null) return Result.failure(IllegalArgumentException(validationErr))

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
        } catch (e: Exception) {
            Log.e("AuthService", "Firebase Auth error (offline/unconfigured): ${e.message}", e)
            "guest_local_0001"
        }
    }

    fun sendVerificationEmailFireAndForget() {
        try {
            auth.currentUser?.sendEmailVerification()
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
        } catch (e: Exception) {
            Log.w("AuthService", "Resend verification email failed: ${e.message}")
        }
        return now
    }

    suspend fun reloadUser(): Boolean {
        return try {
            auth.currentUser?.reload()?.await()
            isEmailVerified
        } catch (e: Exception) {
            false
        }
    }

    fun signOut() {
        try {
            auth.signOut()
        } catch (e: Exception) {
            Log.w("AuthService", "SignOut error: ${e.message}")
        }
    }

    companion object {
        fun validateRegistration(email: String, pass: String, nickname: String): String? {
            if (nickname.trim().length < 3) return "Nickname must be at least 3 characters."
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) return "Invalid email format."
            if (pass.length < 8) return "Password must be at least 8 characters."
            if (!pass.any { it.isUpperCase() }) return "Password must contain at least one uppercase letter."
            if (!pass.any { it.isLowerCase() }) return "Password must contain at least one lowercase letter."
            if (!pass.any { it.isDigit() }) return "Password must contain at least one digit."
            val symbolRegex = "[^A-Za-z0-9]".toRegex()
            if (!pass.contains(symbolRegex)) return "Password must contain at least one symbol."
            return null
        }
    }
}
