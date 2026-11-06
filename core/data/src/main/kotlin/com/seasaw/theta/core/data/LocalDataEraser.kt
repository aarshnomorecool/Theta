package com.seasaw.theta.core.data

import com.seasaw.theta.core.data.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

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
 * Removes app-side local data. Cookies, cache and web storage belong to the WebView and are cleared by core/webview, so
 * callers offering "clear everything" must invoke both. Files already in the Downloads folder are the user's and stay.
 */
@Singleton
class LocalDataEraser @Inject constructor(
    private val tabs: TabsRepository,
    private val tabState: TabStateStore,
    private val history: HistoryRepository,
    private val bookmarks: BookmarksRepository,
    private val speedDial: SpeedDialRepository,
    private val downloads: DownloadsRepository,
    private val sitePermissions: SitePermissionsRepository,
    private val adBlockExemptions: AdBlockExemptionsRepository,
    private val settings: SettingsRepository,
) {
    suspend fun erase(request: EraseRequest) {
        if (request.history) history.clear()
        if (request.tabs) {
            tabs.clear()
            tabState.clear()
        }
        if (request.bookmarks) bookmarks.clear()
        if (request.speedDial) speedDial.clear()
        if (request.downloads) downloads.clear()
        if (request.sitePreferences) {
            sitePermissions.clear()
            adBlockExemptions.clear()
        }
        if (request.settings) settings.resetToDefaults()
    }
}
