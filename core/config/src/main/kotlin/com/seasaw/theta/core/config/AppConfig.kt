package com.seasaw.theta.core.config

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Remotely tunable behaviour. Every value has a safe local default, so the app works offline and on first launch. */
interface AppConfig {
    /** Kill switch: stops all ad loading. */
    val adsEnabled: Boolean

    /** Kill switch: disables the ad blocker if a bad filter list breaks sites in the field. */
    val adBlockEnabled: Boolean

    val filterListUrls: List<String>

    /** The cookie notice list, kept apart from [filterListUrls] because it has its own setting. */
    val cookieListUrl: String

    /** Empty until the policy is published. Callers hide the link rather than open a dead page. */
    val privacyPolicyUrl: String

    /** Refreshes values in the background. Failure keeps what we already have. */
    suspend fun refresh()
}

object ConfigKeys {
    const val ADS_ENABLED = "ads_enabled"
    const val ADBLOCK_ENABLED = "adblock_enabled"
    const val FILTER_LIST_URLS = "filter_list_urls"
    const val COOKIE_LIST_URL = "cookie_list_url"
    const val PRIVACY_POLICY_URL = "privacy_policy_url"
}

object ConfigDefaults {
    /** EasyList and EasyPrivacy, plus IndianList for the primary market. All three share the EasyList licence. */
    private val FILTER_LISTS = listOf(
        "https://easylist.to/easylist/easylist.txt",
        "https://easylist.to/easylist/easyprivacy.txt",
        "https://easylist-downloads.adblockplus.org/indianlist.txt",
    )

    /** Published on the project website. Remote Config can point elsewhere if the site ever moves. */
    const val PRIVACY_POLICY_URL = "https://aarshnomorecool.github.io/privacy.html"

    /** EasyList Cookie List (CC BY 3.0, by the EasyList authors), which hides cookie consent pop-ups. */
    const val COOKIE_LIST_URL = "https://secure.fanboy.co.nz/fanboy-cookiemonster.txt"

    /** Remote Config only holds strings, so the default list is stored in the same JSON form a remote value uses. */
    val FILTER_LIST_URLS: String = FILTER_LISTS.joinToString(separator = ",", prefix = "[", postfix = "]") { "\"$it\"" }

    val all: Map<String, Any> = mapOf(
        ConfigKeys.ADS_ENABLED to true,
        ConfigKeys.ADBLOCK_ENABLED to true,
        ConfigKeys.FILTER_LIST_URLS to FILTER_LIST_URLS,
        ConfigKeys.COOKIE_LIST_URL to COOKIE_LIST_URL,
        ConfigKeys.PRIVACY_POLICY_URL to PRIVACY_POLICY_URL,
    )
}

/** Raw key-value access, so the validation below can be tested without Firebase. */
interface ConfigSource {
    fun boolean(key: String): Boolean
    fun long(key: String): Long
    fun string(key: String): String
    suspend fun fetchAndActivate()
}

@Singleton
internal class RemoteAppConfig @Inject constructor(private val source: ConfigSource) : AppConfig {
    override val adsEnabled: Boolean get() = source.boolean(ConfigKeys.ADS_ENABLED)
    override val adBlockEnabled: Boolean get() = source.boolean(ConfigKeys.ADBLOCK_ENABLED)

    override val filterListUrls: List<String>
        get() = parseUrlList(source.string(ConfigKeys.FILTER_LIST_URLS))
            .ifEmpty { parseUrlList(ConfigDefaults.FILTER_LIST_URLS) }

    override val privacyPolicyUrl: String
        get() = source.string(ConfigKeys.PRIVACY_POLICY_URL).takeIf { it.startsWith("https://") }
            ?: ConfigDefaults.PRIVACY_POLICY_URL

    override suspend fun refresh() = source.fetchAndActivate()

    /** Only https is accepted: filter lists decide what gets blocked, so they must not be tampered with in transit. */
    private fun parseUrlList(raw: String): List<String> = runCatching {
        (Json.parseToJsonElement(raw) as JsonArray).mapNotNull { it.jsonPrimitive.contentOrNull }
    }.getOrDefault(emptyList()).filter { it.startsWith("https://") }

    override val cookieListUrl: String
        get() = source.string(ConfigKeys.COOKIE_LIST_URL).takeIf { it.startsWith("https://") }
            ?: ConfigDefaults.COOKIE_LIST_URL
}
