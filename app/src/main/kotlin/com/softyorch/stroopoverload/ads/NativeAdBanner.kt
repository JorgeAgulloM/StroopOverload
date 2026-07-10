package com.softyorch.stroopoverload.ads

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.softyorch.stroopoverload.BuildConfig

private const val TAG = "NativeAdBanner"

// Matches ui/theme/Color.kt -- this file works in the legacy Android View
// system (NativeAdView has no Compose equivalent), so the theme's Compose
// Color values aren't reachable here; kept in sync by hand.
private const val COLOR_BACKGROUND = 0xFF050508.toInt()
private const val COLOR_SURFACE = 0xFF12121A.toInt()
private const val COLOR_TECH_ACCENT = 0xFF00C8FF.toInt()
private const val COLOR_ON_SURFACE = 0xFFE0E0E0.toInt()
private const val COLOR_MUTED = 0xFF8C8CA4.toInt()
private const val COLOR_NEON_YELLOW = 0xFFFFE600.toInt()

/**
 * Renders a native ad styled to match the app's cyberpunk theme instead of
 * stock AdMob widget defaults. Renders nothing (zero height, zero side
 * effects) when ads are disabled for this build (see BuildConfig.ADS_ENABLED,
 * false for the demo flavor) or the player has gone ad-free/premium -- no ad
 * request is even made in that case.
 */
@Composable
fun NativeAdBanner(
    adUnitId: String,
    isAdFree: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!BuildConfig.ADS_ENABLED || isAdFree || adUnitId.isBlank()) return

    val context = LocalContext.current
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }

    DisposableEffect(adUnitId) {
        val loader = AdLoader.Builder(context, adUnitId)
            .forNativeAd { ad -> nativeAd = ad }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.w(TAG, "load failed for $adUnitId: ${adError.code} - ${adError.message}")
                }
            })
            .build()
        loader.loadAd(AdRequest.Builder().build())
        onDispose {
            nativeAd?.destroy()
            nativeAd = null
        }
    }

    nativeAd?.let { ad ->
        AndroidView(
            factory = { ctx -> buildNativeAdView(ctx).also { populateNativeAdView(ad, it) } },
            update = { view -> populateNativeAdView(ad, view) },
            modifier = modifier
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, Color(COLOR_TECH_ACCENT).copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surface),
        )
    }
}

private fun buildNativeAdView(context: Context): NativeAdView {
    val density = context.resources.displayMetrics.density
    fun Int.dp() = (this * density).toInt()

    val icon = ImageView(context).apply {
        layoutParams = LinearLayout.LayoutParams(40.dp(), 40.dp()).apply { marginEnd = 10.dp() }
        scaleType = ImageView.ScaleType.CENTER_CROP
    }
    val headline = TextView(context).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
        setTextColor(COLOR_ON_SURFACE)
        textSize = 13f
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }
    val body = TextView(context).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        setTypeface(Typeface.MONOSPACE, Typeface.NORMAL)
        setTextColor(COLOR_MUTED)
        textSize = 11f
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }
    val adBadge = TextView(context).apply {
        layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
        text = "AD"
        setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
        setTextColor(COLOR_BACKGROUND)
        setBackgroundColor(COLOR_TECH_ACCENT)
        textSize = 9f
        setPadding(6.dp(), 2.dp(), 6.dp(), 2.dp())
    }
    val textBlock = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        addView(adBadge)
        addView(headline)
        addView(body)
    }
    val cta = Button(context).apply {
        layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = 10.dp() }
        setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
        setTextColor(COLOR_BACKGROUND)
        setBackgroundColor(COLOR_NEON_YELLOW)
        textSize = 11f
        isAllCaps = true
    }
    val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        setBackgroundColor(COLOR_SURFACE)
        setPadding(10.dp(), 8.dp(), 10.dp(), 8.dp())
        addView(icon)
        addView(textBlock)
        addView(cta)
    }
    return NativeAdView(context).apply {
        layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        addView(row)
        headlineView = headline
        bodyView = body
        iconView = icon
        callToActionView = cta
    }
}

private fun populateNativeAdView(ad: NativeAd, view: NativeAdView) {
    (view.headlineView as? TextView)?.text = ad.headline
    (view.bodyView as? TextView)?.apply {
        ad.body?.let { text = it; visibility = View.VISIBLE } ?: run { visibility = View.GONE }
    }
    (view.iconView as? ImageView)?.apply {
        ad.icon?.let { setImageDrawable(it.drawable); visibility = View.VISIBLE } ?: run { visibility = View.GONE }
    }
    (view.callToActionView as? Button)?.apply {
        ad.callToAction?.let { text = it; visibility = View.VISIBLE } ?: run { visibility = View.GONE }
    }
    view.setNativeAd(ad)
}
