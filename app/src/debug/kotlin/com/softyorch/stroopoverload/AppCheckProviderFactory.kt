package com.softyorch.stroopoverload

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * This build type isn't Play-signed, so Play Integrity can't attest it. The debug
 * provider prints a token to logcat on first run that has to be registered in the
 * Firebase console for each device/emulator before App Check calls succeed.
 */
fun appCheckProviderFactory(): AppCheckProviderFactory = DebugAppCheckProviderFactory.getInstance()
