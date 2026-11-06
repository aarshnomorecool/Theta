package com.seasaw.theta.core.analytics

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/** Features depend on this. It is the only route to the analytics backend. */
interface Analytics {
    fun track(event: AnalyticsEvent)

    /** Reflects the first-launch consent choice. Nothing is collected until this is called with true. */
    fun setCollectionEnabled(enabled: Boolean)
}

internal interface AnalyticsSink {
    fun send(name: String, params: Map<String, Any>)
    fun setEnabled(enabled: Boolean)
}

@Singleton
internal class AllowlistedAnalytics @Inject constructor(private val sink: AnalyticsSink) : Analytics {
    override fun track(event: AnalyticsEvent) {
        if (EventAllowlist.permits(event.name, event.params)) sink.send(event.name, event.params)
    }

    override fun setCollectionEnabled(enabled: Boolean) = sink.setEnabled(enabled)
}

/**
 * Does nothing when Firebase is unconfigured, as it is on any build without a google-services.json.
 * The permissions lint asks for are merged into the app manifest by the Firebase Analytics AAR.
 */
@SuppressLint("MissingPermission")
@Singleton
internal class FirebaseAnalyticsSink @Inject constructor(@ApplicationContext private val context: Context) :
    AnalyticsSink {
    private val firebase: FirebaseAnalytics? by lazy {
        if (FirebaseApp.getApps(context).isEmpty()) null else FirebaseAnalytics.getInstance(context)
    }

    override fun send(name: String, params: Map<String, Any>) {
        val bundle = Bundle(params.size)
        params.forEach { (key, value) ->
            when (value) {
                is Boolean -> bundle.putString(key, value.toString())
                is String -> bundle.putString(key, value)
            }
        }
        firebase?.logEvent(name, bundle)
    }

    override fun setEnabled(enabled: Boolean) {
        firebase?.setAnalyticsCollectionEnabled(enabled)
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AnalyticsModule {
    @Binds
    abstract fun analytics(implementation: AllowlistedAnalytics): Analytics

    @Binds
    abstract fun analyticsSink(implementation: FirebaseAnalyticsSink): AnalyticsSink

    @Binds
    abstract fun crashReporter(implementation: FirebaseCrashReporter): CrashReporter
}
