package com.seasaw.theta

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.seasaw.theta.core.ads.AdsManager
import com.seasaw.theta.core.data.settings.SettingsRepository
import com.seasaw.theta.core.data.settings.ThemeMode
import com.seasaw.theta.core.data.settings.UserSettings
import com.seasaw.theta.core.ui.AppLocales
import com.seasaw.theta.core.ui.ThetaTheme
import com.seasaw.theta.feature.browser.BrowserUiEvent
import com.seasaw.theta.feature.browser.BrowserViewModel
import com.seasaw.theta.feature.settings.ConsentRoute
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** The only Activity. Declares its config changes in the manifest, so that rotating never reloads a user's pages. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var settings: SettingsRepository

    @Inject
    lateinit var deferredStartup: DeferredStartup

    @Inject
    lateinit var ads: AdsManager

    private val browserViewModel: BrowserViewModel by viewModels()

    /** A URL handed over by another app, until the browser has picked it up. */
    private var incomingUrl by mutableStateOf<String?>(null)

    /** Snapshot taken at launch: ads rule 4 is about this whole session, even once the flag flips underneath it. */
    private var isFirstSession = true

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocales.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        incomingUrl = intent.browsableUrl()
        setContent { Root() }
        window.decorView.post { onFirstFrameDrawn() }
    }

    @androidx.compose.runtime.Composable
    private fun Root() {
        // Null while settings load, which takes milliseconds and is covered by the launcher window's background.
        val current by settings.settings.collectAsStateWithLifecycle<UserSettings?>(initialValue = null)
        val loaded = current
        val isDark = when (loaded?.themeMode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            else -> isSystemInDarkTheme()
        }
        // Follows the app's own theme, which the user can set apart from the system's, or bar icons become unreadable.
        // Also keyed on the configuration: a config change, such as switching language, resets the bars' appearance.
        LaunchedEffect(isDark, LocalConfiguration.current) {
            val barStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { isDark }
            enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
        }
        ThetaTheme(darkTheme = isDark, highContrast = loaded?.highContrast == true) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                when {
                    loaded == null -> Unit

                    !loaded.privacyNoticeAccepted -> ConsentRoute()

                    else -> ThetaNavHost(
                        isDarkTheme = isDark,
                        incomingUrl = incomingUrl,
                        onIncomingUrlHandled = { incomingUrl = null },
                        adsManager = ads,
                        isFirstSession = { isFirstSession },
                        browserViewModel = browserViewModel,
                    )
                }
            }
        }
    }

    /** Everything heavy waits for here, which is what keeps cold start inside the 1.2 s budget. */
    private fun onFirstFrameDrawn() {
        reportFullyDrawn()
        deferredStartup.run()
        lifecycleScope.launch {
            isFirstSession = !settings.settings.first().firstSessionCompleted
            // Ads rule 8: UMP runs before any ad, and the DPDP notice comes before both.
            settings.settings.filter { it.privacyNoticeAccepted }.first()
            ads.start(this@MainActivity)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.browsableUrl()?.let { incomingUrl = it }
    }

    override fun onStop() {
        super.onStop()
        browserViewModel.onEvent(BrowserUiEvent.AppBackgrounded)
        lifecycleScope.launch { settings.markFirstSessionCompleted() }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            browserViewModel.onEvent(BrowserUiEvent.TrimMemory)
        }
    }

    /** Only http(s). Other apps can send us anything, and this must not become a way to load local files. */
    private fun Intent?.browsableUrl(): String? {
        if (this == null) return null
        val url = when (action) {
            Intent.ACTION_VIEW -> dataString
            Intent.ACTION_WEB_SEARCH -> null
            else -> null
        }
        return url?.takeIf {
            it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
        }
    }
}
