package com.seasaw.theta.core.webview

import android.content.Context
import android.os.Bundle
import android.os.Parcel
import android.webkit.WebView
import com.seasaw.theta.core.data.TabStateStore
import javax.inject.Inject
import javax.inject.Singleton

/** LRU bookkeeping with no Android types, so the eviction rule can be unit tested. */
internal class LiveTabTracker(private val maxLive: Int) {
    private val order = LinkedHashSet<String>()

    /** Marks [tabId] as most recently used and returns the tabs that must now give up their WebViews. */
    fun touch(tabId: String): List<String> {
        order.remove(tabId)
        order.add(tabId)
        val excess = order.size - maxLive
        if (excess <= 0) return emptyList()
        return order.take(excess).onEach(order::remove)
    }

    fun remove(tabId: String) {
        order.remove(tabId)
    }

    /** Everything except [keep], oldest first. */
    fun allExcept(keep: String?): List<String> = order.filter { it != keep }
}

/** Converts WebView state to bytes. Parcels are not stable across versions, so readers must tolerate failure. */
internal object WebViewStateCodec {
    fun encode(webView: WebView): ByteArray? {
        val bundle = Bundle()
        webView.saveState(bundle) ?: return null
        val parcel = Parcel.obtain()
        return try {
            parcel.writeBundle(bundle)
            parcel.marshall()
        } finally {
            parcel.recycle()
        }
    }

    /** Returns false when the state is unusable, in which case the caller loads the tab's URL instead. */
    fun restore(webView: WebView, bytes: ByteArray): Boolean {
        val parcel = Parcel.obtain()
        return try {
            parcel.unmarshall(bytes, 0, bytes.size)
            parcel.setDataPosition(0)
            val bundle = parcel.readBundle(WebViewStateCodec::class.java.classLoader)
            bundle != null && webView.restoreState(bundle) != null
        } catch (ignored: RuntimeException) {
            false
        } finally {
            parcel.recycle()
        }
    }
}

/** Identifies a tab to the pool, and carries what is needed to create or restore its WebView. */
data class TabSpec(val id: String, val url: String, val isIncognito: Boolean, val preferences: TabPreferences)

/**
 * Owns all live WebViews and keeps at most [MAX_LIVE_WEBVIEWS], the project's memory budget. Older tabs are saved and
 * destroyed, then restored on demand. Regular tab state goes to disk, while incognito state stays in memory only and
 * dies with its tab or the process.
 *
 * Not thread safe. WebViews are main-thread objects, so every call must come from the main thread.
 */
@Singleton
class WebViewPool @Inject constructor(
    private val factory: WebViewFactory,
    private val stateStore: TabStateStore,
    private val incognito: IncognitoProfiles,
) {
    private class Live(val spec: TabSpec, val webView: WebView, val events: TabEvents)

    private val live = HashMap<String, Live>()
    private val tracker = LiveTabTracker(MAX_LIVE_WEBVIEWS)
    private val incognitoState = HashMap<String, ByteArray>()
    private val incognitoTabs = HashSet<String>()
    private var host: Context? = null
    private var hostDarkMode = false

    fun view(tabId: String): WebView? = live[tabId]?.webView

    /**
     * Returns the tab's WebView, creating and restoring it if needed. [loadInitial] runs only when there was no saved
     * state to restore, and is how a fresh tab starts its first navigation.
     */
    suspend fun acquire(spec: TabSpec, events: TabEvents, loadInitial: (WebView) -> Unit): WebView {
        tracker.touch(spec.id).filter { it != spec.id }.forEach { evict(it) }
        live[spec.id]?.let { return it.webView }

        if (spec.isIncognito) incognitoTabs += spec.id
        val webView = factory.create(spec.isIncognito, spec.preferences, events) { handleRendererGone(spec.id) }
        factory.attachHost(webView, host, spec.preferences.darkMode)
        live[spec.id] = Live(spec, webView, events)

        val saved = if (spec.isIncognito) incognitoState[spec.id] else stateStore.read(spec.id)
        if (saved == null || !WebViewStateCodec.restore(webView, saved)) loadInitial(webView)
        return webView
    }

    /** The tab was closed. Its WebView is destroyed and nothing is kept. */
    suspend fun close(tabId: String) {
        destroy(tabId)
        tracker.remove(tabId)
        incognitoState.remove(tabId)
        stateStore.delete(tabId)
        val wasIncognito = incognitoTabs.remove(tabId)
        if (wasIncognito && incognitoTabs.isEmpty()) incognito.wipeAfterSession()
    }

    /** Saves and destroys every live WebView, for when the screen that owns these tabs goes away for good. */
    suspend fun releaseAll() {
        live.keys.toList().forEach { evict(it) }
    }

    /** Called from onTrimMemory. Everything but the visible tab gives up its WebView. */
    suspend fun trimToVisible(visibleTabId: String?) {
        tracker.allExcept(visibleTabId).forEach { evict(it) }
    }

    /** Saves all tabs without destroying them, for the moment the app goes to the background. */
    suspend fun persistAll() {
        live.values.forEach { saveState(it) }
    }

    /** Theme changes need fresh WebViews, because a WebView's dark setting comes from its context theme. */
    suspend fun recreateForTheme(visibleTabId: String?, darkMode: Boolean) {
        hostDarkMode = darkMode
        live.keys.toList().forEach { if (it != visibleTabId) evict(it) }
        visibleTabId?.let { id ->
            live[id]?.let { current ->
                saveState(current)
                destroy(id)
                val updated = current.spec.copy(preferences = current.spec.preferences.copy(darkMode = darkMode))
                acquire(updated, current.events) { webView -> webView.loadUrl(updated.url) }
            }
        }
    }

    fun updatePreferences(tabId: String, preferences: TabPreferences) {
        live[tabId]?.let { factory.update(it.webView, preferences) }
    }

    /** The Activity is the context for popups such as `<select>` lists. Pass null when destroyed, to avoid a leak. */
    fun attachHost(activity: Context?) {
        host = activity
        live.values.forEach { factory.attachHost(it.webView, activity, it.spec.preferences.darkMode) }
    }

    private suspend fun evict(tabId: String) {
        val tab = live[tabId] ?: return
        saveState(tab)
        destroy(tabId)
        tracker.remove(tabId)
    }

    private suspend fun saveState(tab: Live) {
        val bytes = WebViewStateCodec.encode(tab.webView) ?: return
        if (tab.spec.isIncognito) incognitoState[tab.spec.id] = bytes else stateStore.write(tab.spec.id, bytes)
    }

    private fun handleRendererGone(tabId: String) {
        destroy(tabId)
        tracker.remove(tabId)
    }

    private fun destroy(tabId: String) {
        live.remove(tabId)?.webView?.let { webView ->
            (webView.parent as? android.view.ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.destroy()
        }
    }

    companion object {
        const val MAX_LIVE_WEBVIEWS = 3
    }
}
