package com.seasaw.theta.core.ads

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.SystemClock
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.seasaw.theta.core.analytics.Analytics
import com.seasaw.theta.core.analytics.AnalyticsEvent
import com.seasaw.theta.core.config.AppConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AdsState { PENDING, READY, UNAVAILABLE }

/** Each screen has its own ad unit, so AdMob reports show which one earns. */
internal fun AdPlacement.unitId(): String = when (this) {
    AdPlacement.NEW_TAB -> BuildConfig.NATIVE_NEW_TAB_UNIT
    AdPlacement.DOWNLOADS -> BuildConfig.NATIVE_DOWNLOADS_UNIT
    AdPlacement.BOOKMARKS -> BuildConfig.NATIVE_BOOKMARKS_UNIT
    AdPlacement.HISTORY -> BuildConfig.NATIVE_HISTORY_UNIT
}

/**
 * The only class that talks to the ads SDKs (ads rule 10), apart from the slot composable added with the UI.
 *
 * Native ads only. Full-screen (interstitial) ads were removed in 0.2.0 and stay out until the VPN feature exists.
 *
 * Nothing here runs on launch. [start] is called after the first frame, which protects the startup budget, and the SDK
 * is only initialised once UMP says ads may be requested (ads rule 8).
 */
@Singleton
class AdsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: AppConfig,
    private val analytics: Analytics,
) {
    private val consentInformation: ConsentInformation by lazy {
        UserMessagingPlatform.getConsentInformation(context)
    }
    private val started = AtomicBoolean(false)
    private val sdkInitialised = AtomicBoolean(false)

    // Benchmark variants build on release code with no ad units, and the SDK throws if asked to load a blank one.
    private val adsAvailable: Boolean
        get() = config.adsEnabled && AdPlacement.entries.any { it.unitId().isNotEmpty() }

    private val _state = MutableStateFlow(if (adsAvailable) AdsState.PENDING else AdsState.UNAVAILABLE)

    /** Slots reserve their space while [AdsState.PENDING], so that an ad arriving later never moves content. */
    val state: StateFlow<AdsState> = _state.asStateFlow()

    /** Runs the consent flow, then initialises ads. Safe to call again: only the first call does anything. */
    fun start(activity: Activity) {
        if (!adsAvailable || !started.compareAndSet(false, true)) return
        consentInformation.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    initialiseIfAllowed()
                }
            },
            {
                // No network or a UMP outage. A previous session's consent may still allow ads.
                initialiseIfAllowed()
            },
        )
    }

    private fun initialiseIfAllowed() {
        if (!consentInformation.canRequestAds()) {
            _state.value = AdsState.UNAVAILABLE
            return
        }
        if (!sdkInitialised.compareAndSet(false, true)) return
        MobileAds.initialize(context) { _state.value = AdsState.READY }
    }

    private val nativeAds = NativeAdCache<AdPlacement, NativeAd>(
        refreshMillis = NATIVE_REFRESH_MILLIS,
        retryMillis = NATIVE_RETRY_MILLIS,
        destroy = NativeAd::destroy,
    )

    /** The ad a slot on [placement] should show. Call [requestNativeAd] when the slot appears. */
    fun nativeAd(placement: AdPlacement): StateFlow<NativeSlotState<NativeAd>> = nativeAds.state(placement)

    /** Asks for an ad only when [placement] has none, or its current one is old enough to replace. */
    @SuppressLint("MissingPermission") // INTERNET is declared in the app manifest and merged by the Ads SDK.
    fun requestNativeAd(placement: AdPlacement) {
        val unit = placement.unitId()
        if (unit.isEmpty() || _state.value != AdsState.READY) return
        if (!nativeAds.shouldLoad(placement, SystemClock.elapsedRealtime())) return
        AdLoader.Builder(context, unit)
            .forNativeAd { ad ->
                nativeAds.onLoaded(placement, ad, SystemClock.elapsedRealtime())
                analytics.track(AnalyticsEvent.AdShown(placement.analyticsId))
            }
            .withAdListener(
                object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        nativeAds.onFailed(placement, SystemClock.elapsedRealtime())
                    }
                },
            )
            .build()
            .loadAd(AdRequest.Builder().build())
    }

    /** For the Settings entry that lets users revisit their ad consent choice, where regulations require one. */
    val isPrivacyOptionsRequired: Boolean
        get() = consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { }
    }

    private companion object {
        /** AdMob advises against refreshing native ads more often than about once a minute. */
        const val NATIVE_REFRESH_MILLIS = 60_000L
        const val NATIVE_RETRY_MILLIS = 60_000L
    }
}
