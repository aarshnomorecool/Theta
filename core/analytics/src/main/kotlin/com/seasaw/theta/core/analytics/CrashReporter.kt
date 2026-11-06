package com.seasaw.theta.core.analytics

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Crash reporting that scrubs URLs before anything reaches the backend. */
interface CrashReporter {
    fun log(message: String)
    fun recordHandled(throwable: Throwable)
    fun setCollectionEnabled(enabled: Boolean)

    /** Must run after Crashlytics has installed its own handler, because this one wraps it. */
    fun installUncaughtExceptionScrubber()
}

/** Removes anything that could reveal browsing activity from text bound for crash reports. */
internal object LogScrubber {
    private const val URL_MARKER = "[url]"
    private const val HOST_MARKER = "[host]"

    private val URL_WITH_SCHEME = Regex("\\b[a-zA-Z][a-zA-Z0-9+.-]*:(//)?[^\\s\"'<>]+")
    private val QUERY_STRING = Regex("\\?[^\\s\"'<>]*=[^\\s\"'<>]*")
    private const val COMMON_TLDS = "com|net|org|in|ru|io|co|app|dev|info|xyz|me|tv|edu|gov|uk|bharat"
    private val BARE_HOST = Regex("\\b(?:[a-zA-Z0-9-]+\\.)+(?:$COMMON_TLDS)\\b(?:/[^\\s\"'<>]*)?")

    fun scrub(text: String): String = text
        .replace(URL_WITH_SCHEME, URL_MARKER)
        .replace(QUERY_STRING, URL_MARKER)
        .replace(BARE_HOST, HOST_MARKER)

    /**
     * Returns a throwable safe to report, keeping the stack trace so crash grouping still works.
     * The original is returned when there was nothing to scrub, which preserves its real type in the console.
     */
    fun scrub(throwable: Throwable, seen: MutableSet<Throwable> = HashSet()): Throwable {
        if (!seen.add(throwable)) return throwable
        val cause = throwable.cause?.let { scrub(it, seen) }
        val message = throwable.message
        val clean = message?.let(::scrub)
        if (clean == message && cause === throwable.cause) return throwable
        return ScrubbedThrowable("${throwable.javaClass.name}: $clean", cause).also {
            it.stackTrace = throwable.stackTrace
        }
    }
}

internal class ScrubbedThrowable(message: String, cause: Throwable?) : Exception(message, cause)

@Singleton
internal class FirebaseCrashReporter @Inject constructor(@ApplicationContext private val context: Context) :
    CrashReporter {
    private val crashlytics: FirebaseCrashlytics? by lazy {
        if (FirebaseApp.getApps(context).isEmpty()) null else FirebaseCrashlytics.getInstance()
    }

    override fun log(message: String) {
        crashlytics?.log(LogScrubber.scrub(message))
    }

    override fun recordHandled(throwable: Throwable) {
        crashlytics?.recordException(LogScrubber.scrub(throwable))
    }

    override fun setCollectionEnabled(enabled: Boolean) {
        crashlytics?.isCrashlyticsCollectionEnabled = enabled
    }

    override fun installUncaughtExceptionScrubber() {
        val delegate = Thread.getDefaultUncaughtExceptionHandler() ?: return
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            delegate.uncaughtException(thread, LogScrubber.scrub(throwable))
        }
    }
}
