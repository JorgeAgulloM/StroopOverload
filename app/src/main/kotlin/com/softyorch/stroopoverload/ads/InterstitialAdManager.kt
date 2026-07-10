package com.softyorch.stroopoverload.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.softyorch.stroopoverload.BuildConfig

private const val TAG = "InterstitialAdManager"

/**
 * Loads one interstitial ahead of time and shows it on demand, always
 * followed by [onComplete] -- whether the ad played, failed to show, wasn't
 * loaded yet, or ads are disabled entirely. Callers never get stuck waiting
 * on an ad: this is a monetization gate, not a hard blocker, so any ad
 * failure just falls through to the real action.
 */
class InterstitialAdManager(private val adUnitId: String) {
    private var interstitialAd: InterstitialAd? = null
    private var isLoading = false

    fun preload(context: Context) {
        if (!BuildConfig.ADS_ENABLED || adUnitId.isBlank() || interstitialAd != null || isLoading) return
        isLoading = true
        InterstitialAd.load(
            context,
            adUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    isLoading = false
                    interstitialAd = ad
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    isLoading = false
                    interstitialAd = null
                    Log.w(TAG, "load failed for $adUnitId: ${adError.code} - ${adError.message}")
                }
            },
        )
    }

    /**
     * Shows the preloaded interstitial (if ready and ads are enabled for this
     * build/profile) then always invokes [onComplete] -- once, exactly when
     * it's safe for the caller to proceed with the gated action (room
     * create/join). A fresh ad is preloaded for next time regardless of
     * outcome.
     */
    fun showAndThen(activity: Activity, isAdFree: Boolean, onComplete: () -> Unit) {
        if (!BuildConfig.ADS_ENABLED || isAdFree) {
            onComplete()
            return
        }

        val ad = interstitialAd
        if (ad == null) {
            preload(activity)
            onComplete()
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                preload(activity)
                onComplete()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.w(TAG, "show failed for $adUnitId: ${adError.code} - ${adError.message}")
                interstitialAd = null
                preload(activity)
                onComplete()
            }
        }
        ad.show(activity)
    }
}
