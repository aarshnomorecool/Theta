package com.seasaw.theta.core.analytics

/**
 * Every event the app can send. This closed type is half of the privacy guarantee: a call site cannot pass a free-form
 * string, so a URL, title or typed query has no way in. [EventAllowlist] is the other half and checks what is left.
 */
sealed class AnalyticsEvent(val name: String, val params: Map<String, Any> = emptyMap()) {
    data class TabOpened(val incognito: Boolean) : AnalyticsEvent("tab_opened", mapOf("incognito" to incognito))
    data object TabClosed : AnalyticsEvent("tab_closed")
    data object BookmarkAdded : AnalyticsEvent("bookmark_added")
    data object DownloadStarted : AnalyticsEvent("download_started")
    data object FindInPageUsed : AnalyticsEvent("find_in_page_used")
    data object LocalDataCleared : AnalyticsEvent("local_data_cleared")
    data object DefaultBrowserPromptShown : AnalyticsEvent("default_browser_prompt_shown")

    data class AdBlockToggled(val enabled: Boolean) : AnalyticsEvent("adblock_toggled", mapOf("enabled" to enabled))
    data class LiteModeToggled(val enabled: Boolean) : AnalyticsEvent("lite_mode_toggled", mapOf("enabled" to enabled))
    data class DesktopModeToggled(val enabled: Boolean) :
        AnalyticsEvent("desktop_mode_toggled", mapOf("enabled" to enabled))

    data class ThemeChanged(val mode: String) : AnalyticsEvent("theme_changed", mapOf("mode" to mode))
    data class LanguageChanged(val language: String) : AnalyticsEvent("language_changed", mapOf("language" to language))
    data class SearchEngineChanged(val engine: String) :
        AnalyticsEvent("search_engine_changed", mapOf("engine" to engine))

    data class FilterListUpdated(val result: String) :
        AnalyticsEvent("filter_list_updated", mapOf("result" to result))

    data class AdShown(val placement: String) : AnalyticsEvent("ad_shown", mapOf("placement" to placement))
}

/** Constrains what a parameter may carry. Strings are only ever accepted from a closed set of values. */
internal sealed interface ParamRule {
    data object Flag : ParamRule
    data class OneOf(val values: Set<String>) : ParamRule
}

/**
 * The single source of truth for what may leave the device. Anything not listed is dropped, so adding an event requires
 * an explicit entry here and an update to docs/data-safety.md.
 */
internal object EventAllowlist {
    private val flagEnabled = mapOf("enabled" to ParamRule.Flag)

    private val rules: Map<String, Map<String, ParamRule>> = mapOf(
        "tab_opened" to mapOf("incognito" to ParamRule.Flag),
        "tab_closed" to emptyMap(),
        "bookmark_added" to emptyMap(),
        "download_started" to emptyMap(),
        "find_in_page_used" to emptyMap(),
        "local_data_cleared" to emptyMap(),
        "default_browser_prompt_shown" to emptyMap(),
        "adblock_toggled" to flagEnabled,
        "lite_mode_toggled" to flagEnabled,
        "desktop_mode_toggled" to flagEnabled,
        "theme_changed" to mapOf("mode" to ParamRule.OneOf(setOf("system", "light", "dark"))),
        "language_changed" to mapOf("language" to ParamRule.OneOf(setOf("system", "en", "hi", "mr", "ru"))),
        "search_engine_changed" to mapOf(
            "engine" to ParamRule.OneOf(setOf("google", "bing", "duckduckgo", "yandex")),
        ),
        "filter_list_updated" to mapOf("result" to ParamRule.OneOf(setOf("success", "failure", "rejected"))),
        "ad_shown" to mapOf(
            "placement" to
                ParamRule.OneOf(setOf("new_tab", "downloads", "bookmarks", "history")),
        ),
    )

    /** True only when the event and all its parameters are on the list with permitted values. */
    fun permits(name: String, params: Map<String, Any>): Boolean {
        val allowedParams = rules[name] ?: return false
        return params.all { (key, value) ->
            when (val rule = allowedParams[key]) {
                null -> false
                ParamRule.Flag -> value is Boolean
                is ParamRule.OneOf -> value is String && value in rule.values
            }
        }
    }
}
