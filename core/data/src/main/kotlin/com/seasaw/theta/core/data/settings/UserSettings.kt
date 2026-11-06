package com.seasaw.theta.core.data.settings

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
) {
    companion object {
        const val DEFAULT_TEXT_ZOOM = 100
        const val MIN_TEXT_ZOOM = 50
        const val MAX_TEXT_ZOOM = 200
    }
}
