package com.seasaw.theta.core.webview

enum class ResourceKind {
    DOCUMENT,
    SUBDOCUMENT,
    SCRIPT,
    STYLESHEET,
    IMAGE,
    FONT,
    MEDIA,
    XHR,
    PING,
    OBJECT,
    OTHER,
}

/** A request seen by a WebView. [pageUrl] is the top-level page, and is null when unknown (service workers). */
data class InterceptedRequest(val url: String, val pageUrl: String?, val kind: ResourceKind)

/**
 * Implemented by feature/adblock and bound through Hilt, because core must not depend on a feature. The scope is
 * deliberately only requests from our own browsing WebViews, so it can never touch the app's AdMob ads (ads rule 3).
 *
 * Called on WebView IO threads for every subresource, so implementations must be fast and non-blocking.
 */
fun interface RequestBlocker {
    fun shouldBlock(request: InterceptedRequest): Boolean
}

/**
 * Infers what a request is for, because WebView does not report it. A wrong guess of [ResourceKind.OTHER] is the safe
 * direction: it means a type-specific blocking rule will not apply.
 */
object ResourceClassifier {
    private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp", "svg", "ico", "bmp", "avif")
    private val FONT_EXTENSIONS = setOf("woff", "woff2", "ttf", "otf", "eot")
    private val MEDIA_EXTENSIONS = setOf("mp4", "webm", "m3u8", "mpd", "ts", "m4s", "mp3", "aac", "m4a", "ogg", "opus")
    private val DATA_EXTENSIONS = setOf("json", "xml")

    fun classify(url: String, isMainFrame: Boolean, acceptHeader: String?, fetchDest: String?): ResourceKind {
        if (isMainFrame) return ResourceKind.DOCUMENT
        return fromFetchDest(fetchDest) ?: fromAccept(acceptHeader) ?: fromExtension(url)
    }

    private fun fromFetchDest(dest: String?): ResourceKind? = when (dest) {
        "iframe", "frame", "document" -> ResourceKind.SUBDOCUMENT
        "script", "worker", "sharedworker", "serviceworker" -> ResourceKind.SCRIPT
        "style" -> ResourceKind.STYLESHEET
        "image" -> ResourceKind.IMAGE
        "font" -> ResourceKind.FONT
        "audio", "video", "track" -> ResourceKind.MEDIA
        "object", "embed" -> ResourceKind.OBJECT
        "report" -> ResourceKind.PING
        else -> null
    }

    private fun fromAccept(accept: String?): ResourceKind? {
        val value = accept?.lowercase() ?: return null
        return when {
            value.startsWith("text/css") -> ResourceKind.STYLESHEET
            value.startsWith("image/") -> ResourceKind.IMAGE
            value.startsWith("video/") || value.startsWith("audio/") -> ResourceKind.MEDIA
            value.startsWith("text/html") -> ResourceKind.SUBDOCUMENT
            value.startsWith("application/json") -> ResourceKind.XHR
            else -> null
        }
    }

    private fun fromExtension(url: String): ResourceKind {
        val path = url.substringBefore('#').substringBefore('?')
        val fileName = path.substringAfterLast('/')
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "js", "mjs" -> ResourceKind.SCRIPT
            "css" -> ResourceKind.STYLESHEET
            in IMAGE_EXTENSIONS -> ResourceKind.IMAGE
            in FONT_EXTENSIONS -> ResourceKind.FONT
            in MEDIA_EXTENSIONS -> ResourceKind.MEDIA
            in DATA_EXTENSIONS -> ResourceKind.XHR
            else -> ResourceKind.OTHER
        }
    }
}
