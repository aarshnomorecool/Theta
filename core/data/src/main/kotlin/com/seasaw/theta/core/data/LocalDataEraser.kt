package com.seasaw.theta.core.data

import com.seasaw.theta.core.data.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** What the user asked to remove. */
data class EraseRequest(
    val history: Boolean = false,
    val tabs: Boolean = false,
    val bookmarks: Boolean = false,
    val speedDial: Boolean = false,
    val downloads: Boolean = false,
    val sitePreferences: Boolean = false,
    val settings: Boolean = false,
) {
    companion object {
        /** DPDP "delete all my local data in one tap". */
        val Everything = EraseRequest(
            history = true,
            tabs = true,
            bookmarks = true,
            speedDial = true,
            downloads = true,
            sitePreferences = true,
            settings = true,
        )

        /** Clear-on-exit keeps what the user deliberately saved and removes only browsing traces. */
        val BrowsingTraces = EraseRequest(history = true, tabs = true)
    }
}

/**
 * Removes app-side local data. Privacy stats are deliberately not touched: they hold totals, not browsing, and survive
 * even "delete everything" (they have their own reset in settings).
 *
 * Cookies, cache and web storage belong to the WebView and are cleared by core/webview, so
 * callers offering "clear everything" must invoke both. Files already in the Downloads folder are the user's and stay.
 */
@Singleton
class LocalDataEraser @Inject constructor(
    private val tabs: TabsRepository,
    private val tabState: TabStateStore,
    private val tabPreviews: TabPreviewStore,
    private val favicons: FaviconStore,
    private val history: HistoryRepository,
    private val bookmarks: BookmarksRepository,
    private val speedDial: SpeedDialRepository,
    private val downloads: DownloadsRepository,
    private val sitePermissions: SitePermissionsRepository,
    private val adBlockExemptions: AdBlockExemptionsRepository,
    private val settings: SettingsRepository,
) {
    private val _erasures = MutableSharedFlow<EraseRequest>(extraBufferCapacity = 1)

    /** Emits after each erase, so holders of live copies of the data, such as open tabs, can drop them too. */
    val erasures: SharedFlow<EraseRequest> = _erasures.asSharedFlow()

    suspend fun erase(request: EraseRequest) {
        if (request.history) {
            history.clear()
            favicons.clear()
        }
        if (request.tabs) {
            tabs.clear()
            tabState.clear()
            tabPreviews.clear()
        }
        if (request.bookmarks) bookmarks.clear()
        if (request.speedDial) speedDial.clear()
        if (request.downloads) downloads.clear()
        if (request.sitePreferences) {
            sitePermissions.clear()
            adBlockExemptions.clear()
        }
        if (request.settings) settings.resetToDefaults()
        _erasures.emit(request)
    }
}
