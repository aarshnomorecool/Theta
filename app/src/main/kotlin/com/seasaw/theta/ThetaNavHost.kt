package com.seasaw.theta

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.seasaw.theta.core.ads.AdsManager
import com.seasaw.theta.feature.bookmarks.BookmarksRoute
import com.seasaw.theta.feature.browser.BrowserNavigation
import com.seasaw.theta.feature.browser.BrowserRoute
import com.seasaw.theta.feature.browser.BrowserUiEvent
import com.seasaw.theta.feature.browser.BrowserViewModel
import com.seasaw.theta.feature.downloads.DownloadsRoute
import com.seasaw.theta.feature.history.HistoryRoute
import com.seasaw.theta.feature.newtab.NewTabRoute
import com.seasaw.theta.feature.settings.AdPrivacyOptions
import com.seasaw.theta.feature.settings.SettingsRoute

private object Routes {
    const val BROWSER = "browser"
    const val HISTORY = "history"
    const val BOOKMARKS = "bookmarks"
    const val DOWNLOADS = "downloads"
    const val SETTINGS = "settings"
    const val CLEAR_DATA = "settings/clear_data"
}

/** Features cannot depend on each other, so this is where they meet. */
@Composable
internal fun ThetaNavHost(
    isDarkTheme: Boolean,
    incomingUrl: String?,
    onIncomingUrlHandled: () -> Unit,
    adsManager: AdsManager,
    browserViewModel: BrowserViewModel,
) {
    val navController = rememberNavController()

    /** History and bookmarks hand a URL back to the browser and then get out of the way. */
    fun openInBrowser(url: String) {
        browserViewModel.onEvent(BrowserUiEvent.OpenUrl(url))
        navController.popBackStack(Routes.BROWSER, inclusive = false)
    }

    /** Links from settings, such as licences and photo sources, open in a tab of their own. */
    fun openInNewTab(url: String) {
        browserViewModel.openFromIntent(url)
        navController.popBackStack(Routes.BROWSER, inclusive = false)
    }

    NavHost(navController, startDestination = Routes.BROWSER) {
        composable(Routes.BROWSER) {
            BrowserRoute(
                isDarkTheme = isDarkTheme,
                navigation = navController.toBrowserNavigation(),
                incomingUrl = incomingUrl,
                onIncomingUrlHandled = onIncomingUrlHandled,
                newTabContent = { onOpenUrl -> NewTabRoute(onOpenUrl) },
                viewModel = browserViewModel,
            )
        }
        composable(Routes.HISTORY) {
            HistoryRoute(onOpenUrl = ::openInBrowser, onBack = { navController.popBackStack() })
        }
        composable(Routes.BOOKMARKS) {
            BookmarksRoute(onOpenUrl = ::openInBrowser, onBack = { navController.popBackStack() })
        }
        composable(Routes.DOWNLOADS) {
            DownloadsRoute(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsRoute(
                onBack = { navController.popBackStack() },
                versionName = BuildConfig.VERSION_NAME,
                adPrivacy = AdPrivacyOptions(adsManager.isPrivacyOptionsRequired, adsManager::showPrivacyOptions),
                onOpenLink = ::openInNewTab,
            )
        }
        composable(Routes.CLEAR_DATA) {
            SettingsRoute(
                onBack = { navController.popBackStack() },
                versionName = BuildConfig.VERSION_NAME,
                adPrivacy = AdPrivacyOptions(adsManager.isPrivacyOptionsRequired, adsManager::showPrivacyOptions),
                onOpenLink = ::openInNewTab,
                startOnClearData = true,
            )
        }
    }
}

private fun NavHostController.toBrowserNavigation() = BrowserNavigation(
    onBookmarks = { navigate(Routes.BOOKMARKS) },
    onHistory = { navigate(Routes.HISTORY) },
    onDownloads = { navigate(Routes.DOWNLOADS) },
    onSettings = { navigate(Routes.SETTINGS) },
    onClearBrowsingData = { navigate(Routes.CLEAR_DATA) },
)
