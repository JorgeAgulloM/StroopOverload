package com.softyorch.stroopoverload

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck

class StroopApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        // App Check attests that a call to the multiplayer backend comes from a genuine,
        // unmodified build of this app. The backend does NOT enforce it yet
        // (ENFORCE_APP_CHECK in functions/src/index.ts): enforcement can only be turned
        // on once the installed base actually sends tokens, otherwise every older client
        // is rejected mid-match. The provider is per build type -- see appCheckProviderFactory().
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(appCheckProviderFactory())
    }
}
