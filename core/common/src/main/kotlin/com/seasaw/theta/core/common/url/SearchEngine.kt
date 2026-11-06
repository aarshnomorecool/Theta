package com.seasaw.theta.core.common.url

import java.net.URLEncoder

/** Engines offered in the picker. [id] is the persisted value, so it must never change. */
enum class SearchEngine(val id: String, val displayName: String, private val queryPrefix: String) {
    GOOGLE("google", "Google", "https://www.google.com/search?q="),
    BING("bing", "Bing", "https://www.bing.com/search?q="),
    DUCKDUCKGO("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q="),
    YANDEX("yandex", "Yandex", "https://yandex.com/search/?text="),
    ;

    fun searchUrl(query: String): String = queryPrefix + URLEncoder.encode(query, Charsets.UTF_8.name())

    companion object {
        val Default = GOOGLE

        fun fromId(id: String?): SearchEngine = entries.firstOrNull { it.id == id } ?: Default
    }
}
