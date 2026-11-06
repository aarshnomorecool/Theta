package com.seasaw.theta.core.ads

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
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

    private val _adsReady = MutableStateFlow(false)

    /** True once consent is settled and the SDK is up. Ad slots stay empty and take no space until then. */
    val adsReady: StateFlow<Boolean> = _adsReady.asStateFlow()

    /** Runs the consent flow, then initialises ads. Safe to call again: only the first call does anything. */
    fun start(activity: Activity) {
        if (!config.adsEnabled || !started.compareAndSet(false, true)) return
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
        if (!consentInformation.canRequestAds() || !sdkInitialised.compareAndSet(false, true)) return
        MobileAds.initialize(context) {
            _adsReady.value = true
            loadInterstitial()
        }
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
        val allowed = _adsReady.value &&
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
    }
}
