package com.seasaw.theta.core.data.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import com.seasaw.theta.core.common.url.SearchEngine

enum class ThemeMode(val id: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    ;

    companion object {
        fun fromId(id: String?): ThemeMode = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

enum class AppLanguage(val id: String, val languageTag: String?) {
    SYSTEM("system", null),
    ENGLISH("en", "en"),
    HINDI("hi", "hi"),
    MARATHI("mr", "mr"),
    RUSSIAN("ru", "ru"),
    ;

    companion object {
        fun fromId(id: String?): AppLanguage = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

/** The on/off settings that need nothing more than a switch, and their defaults. */
enum class SettingFlag(name: String, internal val default: Boolean) {
    NTP_PHOTOS("ntp_background_photos", true),
    NTP_STATS("ntp_show_privacy_stats", true),
    NTP_RECENT("ntp_show_recent_sites", true),
    TAB_PREVIEWS("tab_previews", true),
    BLOCK_AUTOPLAY("block_autoplay", true),
    HIGH_CONTRAST("high_contrast", false),
    ZOOM_BUTTONS("show_zoom_buttons", false),
    ;

    internal val key = booleanPreferencesKey(name)
}

data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val searchEngine: SearchEngine = SearchEngine.Default,
    val adBlockEnabled: Boolean = true,
    val liteMode: Boolean = false,
    val clearOnExit: Boolean = false,
    val textZoomPercent: Int = DEFAULT_TEXT_ZOOM,

    /** DPDP: the app shows the consent screen until this is true. */
    val privacyNoticeAccepted: Boolean = false,

    /** Crash reports and analytics stay off unless the user opts in on the consent screen. */
    val diagnosticsConsent: Boolean = false,

    /** Ads rule 4: no interstitial on launch in the first session. */
    val firstSessionCompleted: Boolean = false,

    /** History shows one row per site, which keeps runs of short videos or articles from burying everything else. */
    val historyGroupedBySite: Boolean = true,

    /** New-tab page: a rotating background photo, bundled with the app so it costs no data. */
    val ntpBackgroundPhotos: Boolean = SettingFlag.NTP_PHOTOS.default,
    val ntpShowPrivacyStats: Boolean = SettingFlag.NTP_STATS.default,
    val ntpShowRecentSites: Boolean = SettingFlag.NTP_RECENT.default,

    /** Tab switcher shows a small picture of each page rather than only its title. */
    val tabPreviews: Boolean = SettingFlag.TAB_PREVIEWS.default,

    /** Pages may not start playing sound or video until the user taps. */
    val blockAutoplay: Boolean = SettingFlag.BLOCK_AUTOPLAY.default,

    // Accessibility.
    val highContrast: Boolean = SettingFlag.HIGH_CONTRAST.default,
    val showZoomButtons: Boolean = SettingFlag.ZOOM_BUTTONS.default,
) {
    companion object {
        const val DEFAULT_TEXT_ZOOM = 100
        const val MIN_TEXT_ZOOM = 50
        const val MAX_TEXT_ZOOM = 200
    }
}
