package com.softyorch.stroopoverload.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.softyorch.stroopoverload.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * GDPR/UMP consent gate for AdMob. No ad request goes out until the SDK is
 * actually initialized, and the SDK is only initialized once the user
 * either isn't in a region requiring consent or has already responded to
 * the consent form -- required by Google for EEA/UK traffic, and simplest
 * to just always run.
 */
class AdsConsentManager(private val appContext: Context) {
    private val initialized = AtomicBoolean(false)
    private val _adsReady = MutableStateFlow(false)
    val adsReady: StateFlow<Boolean> = _adsReady.asStateFlow()

    fun requestConsentIfNeeded(activity: Activity, onComplete: () -> Unit = {}) {
        if (!BuildConfig.ADS_ENABLED) {
            onComplete()
            return
        }

        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()

        val consentInfo = UserMessagingPlatform.getConsentInformation(activity)
        consentInfo.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    initializeIfReady(activity)
                    onComplete()
                }
            },
            {
                // Consent info update failed (offline, network error, etc.) -- proceed
                // without blocking the player; initializeIfReady will simply no-op if
                // canRequestAds() still says no.
                initializeIfReady(activity)
                onComplete()
            },
        )
    }

    fun initializeIfReady(context: Context = appContext) {
        if (!BuildConfig.ADS_ENABLED) return
        if (!UserMessagingPlatform.getConsentInformation(context).canRequestAds()) return
        if (initialized.compareAndSet(false, true)) {
            MobileAds.initialize(context) { _adsReady.value = true }
        } else {
            _adsReady.value = true
        }
    }
}
