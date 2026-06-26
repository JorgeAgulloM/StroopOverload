package com.softyorch.stroopoverload.data

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

class AuthService(private val auth: FirebaseAuth = FirebaseAuth.getInstance()) {

    val currentUid: String? get() = auth.currentUser?.uid

    suspend fun signInAnonymously(): String {
        val result = auth.signInAnonymously().await()
        return result.user!!.uid
    }
}
