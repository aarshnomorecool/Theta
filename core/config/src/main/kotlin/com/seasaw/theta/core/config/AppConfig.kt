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

    /** Kill switch: stops interstitials only. */
    val interstitialsEnabled: Boolean

    /** Kill switch: disables the ad blocker if a bad filter list breaks sites in the field. */
    val adBlockEnabled: Boolean

    /** Ads rule 4: the minimum time between interstitials. */
    val interstitialMinIntervalSeconds: Long
    val filterListUrls: List<String>

    /** Empty until the policy is published. Callers hide the link rather than open a dead page. */
    val privacyPolicyUrl: String

    /** Refreshes values in the background. Failure keeps what we already have. */
    suspend fun refresh()
}

object ConfigKeys {
    const val ADS_ENABLED = "ads_enabled"
    const val INTERSTITIALS_ENABLED = "interstitials_enabled"
    const val ADBLOCK_ENABLED = "adblock_enabled"
    const val INTERSTITIAL_MIN_INTERVAL_SECONDS = "interstitial_min_interval_seconds"
    const val FILTER_LIST_URLS = "filter_list_urls"
    const val PRIVACY_POLICY_URL = "privacy_policy_url"
}

object ConfigDefaults {
    const val INTERSTITIAL_MIN_INTERVAL_SECONDS = 240L

    /** EasyList and EasyPrivacy, plus IndianList for the primary market. All three share the EasyList licence. */
    private val FILTER_LISTS = listOf(
        "https://easylist.to/easylist/easylist.txt",
        "https://easylist.to/easylist/easyprivacy.txt",
        "https://easylist-downloads.adblockplus.org/indianlist.txt",
    )

    /** Remote Config only holds strings, so the default list is stored in the same JSON form a remote value uses. */
    val FILTER_LIST_URLS: String = FILTER_LISTS.joinToString(separator = ",", prefix = "[", postfix = "]") { "\"$it\"" }

    val all: Map<String, Any> = mapOf(
        ConfigKeys.ADS_ENABLED to true,
        ConfigKeys.INTERSTITIALS_ENABLED to true,
        ConfigKeys.ADBLOCK_ENABLED to true,
        ConfigKeys.INTERSTITIAL_MIN_INTERVAL_SECONDS to INTERSTITIAL_MIN_INTERVAL_SECONDS,
        ConfigKeys.FILTER_LIST_URLS to FILTER_LIST_URLS,
        ConfigKeys.PRIVACY_POLICY_URL to "",
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
    override val interstitialsEnabled: Boolean get() = source.boolean(ConfigKeys.INTERSTITIALS_ENABLED)
    override val adBlockEnabled: Boolean get() = source.boolean(ConfigKeys.ADBLOCK_ENABLED)

    // A remote value of 0 or a negative number would remove the frequency cap, which the ad policy does not allow.
    override val interstitialMinIntervalSeconds: Long
        get() = source.long(ConfigKeys.INTERSTITIAL_MIN_INTERVAL_SECONDS)
            .takeIf { it >= MIN_ALLOWED_INTERVAL_SECONDS }
            ?: ConfigDefaults.INTERSTITIAL_MIN_INTERVAL_SECONDS

    override val filterListUrls: List<String>
        get() = parseUrlList(source.string(ConfigKeys.FILTER_LIST_URLS))
            .ifEmpty { parseUrlList(ConfigDefaults.FILTER_LIST_URLS) }

    override val privacyPolicyUrl: String
        get() = source.string(ConfigKeys.PRIVACY_POLICY_URL).takeIf { it.startsWith("https://") }.orEmpty()

    override suspend fun refresh() = source.fetchAndActivate()

    /** Only https is accepted: filter lists decide what gets blocked, so they must not be tampered with in transit. */
    private fun parseUrlList(raw: String): List<String> = runCatching {
        (Json.parseToJsonElement(raw) as JsonArray).mapNotNull { it.jsonPrimitive.contentOrNull }
    }.getOrDefault(emptyList()).filter { it.startsWith("https://") }

    private companion object {
        const val MIN_ALLOWED_INTERVAL_SECONDS = 60L
    }
}
