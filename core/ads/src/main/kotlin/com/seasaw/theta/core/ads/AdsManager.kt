package com.seasaw.theta.core.ads

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.SystemClock
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
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

/**
 * The only class that talks to the ads SDKs (ads rule 10), apart from the slot composable added with the UI.
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
    private val interstitialGate = InterstitialGate {
        config.interstitialMinIntervalSeconds * MILLIS_PER_SECOND
    }
    private val started = AtomicBoolean(false)
    private val sdkInitialised = AtomicBoolean(false)
    private var interstitial: InterstitialAd? = null
    private var interstitialLoading = false

    // Benchmark variants build on release code with no ad units, and the SDK throws if asked to load a blank one.
    private val adsAvailable: Boolean
        get() = config.adsEnabled && BuildConfig.NATIVE_AD_UNIT.isNotEmpty()

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
        MobileAds.initialize(context) {
            _state.value = AdsState.READY
            loadInterstitial()
        }
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
        if (_state.value != AdsState.READY || !nativeAds.shouldLoad(placement, SystemClock.elapsedRealtime())) return
        AdLoader.Builder(context, BuildConfig.NATIVE_AD_UNIT)
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

    /** Pre-loads so that showing later is instant and does not delay the transition the user just asked for. */
    private fun loadInterstitial() {
        if (interstitial != null || interstitialLoading || !config.interstitialsEnabled) return
        interstitialLoading = true
        InterstitialAd.load(
            context,
            BuildConfig.INTERSTITIAL_AD_UNIT,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitial = ad
                    interstitialLoading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialLoading = false
                }
            },
        )
    }

    /**
     * Call only at a native-to-native transition (ads rule 4), such as closing the tab switcher back to the new-tab
     * page. Returns whether an ad was shown. Either way, [onDone] runs exactly once to continue the transition.
     */
    fun maybeShowInterstitial(
        activity: Activity,
        isFirstSession: Boolean,
        isPageLoading: Boolean,
        onDone: () -> Unit,
    ): Boolean {
        val now = SystemClock.elapsedRealtime()
        val allowed = _state.value == AdsState.READY &&
            config.adsEnabled &&
            interstitialGate.mayShow(now, isFirstSession, isPageLoading, config.interstitialsEnabled)
        val ad = interstitial.takeIf { allowed }
        if (ad == null) {
            onDone()
            return false
        }
        interstitial = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                loadInterstitial()
                onDone()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                loadInterstitial()
                onDone()
            }
        }
        interstitialGate.recordShown(now)
        analytics.track(AnalyticsEvent.AdShown("interstitial"))
        ad.show(activity)
        return true
    }

    /** For the Settings entry that lets users revisit their ad consent choice, where regulations require one. */
    val isPrivacyOptionsRequired: Boolean
        get() = consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L

        /** AdMob advises against refreshing native ads more often than about once a minute. */
        const val NATIVE_REFRESH_MILLIS = 60_000L
        const val NATIVE_RETRY_MILLIS = 60_000L
    }
}
