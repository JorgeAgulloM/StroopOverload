package com.softyorch.stroopoverload

import android.app.Application
import com.google.firebase.FirebaseApp

class StroopApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}
