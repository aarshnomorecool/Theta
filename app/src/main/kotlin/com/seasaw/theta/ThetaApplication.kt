package com.seasaw.theta

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.seasaw.theta.core.analytics.CrashReporter
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/** Deliberately does almost nothing: the cold start budget is 1.2 s. Real startup work is in [DeferredStartup]. */
@HiltAndroidApp
class ThetaApplication :
    Application(),
    Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var crashReporter: CrashReporter

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // The one startup task that cannot wait for the first frame: a crash before then must not reach Crashlytics
        // with URLs in it. It only wraps a thread handler, so it costs nothing measurable.
        crashReporter.installUncaughtExceptionScrubber()
    }
}
