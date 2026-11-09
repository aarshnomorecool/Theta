package com.seasaw.theta.core.webview

import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.webkit.URLUtil
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.seasaw.theta.core.common.AppError
import com.seasaw.theta.core.common.AppResult
import com.seasaw.theta.core.common.resultOf
import com.seasaw.theta.core.common.url.UrlUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class EnqueuedDownload(val downloadId: Long, val fileName: String)

/**
 * Hands downloads to the system DownloadManager, which keeps running if we are killed and shows its own notification.
 * Phase 1 only downloads what the page explicitly offers: no media sniffing or stream extraction belongs here.
 */
@Singleton
class DownloadEnqueuer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val incognito: IncognitoProfiles,
) {
    /** True on Android 8 and 9, where saving to public Downloads still needs a runtime permission. */
    fun needsStoragePermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
        ContextCompat.checkSelfPermission(context, WRITE_STORAGE) != PackageManager.PERMISSION_GRANTED

    /**
     * @param pageUrl the page that offered the download. Cookies are only attached when the download is on the same
     * site: DownloadManager re-sends custom headers on every redirect, so a cross-site offer that bounces through an
     * open redirect would otherwise hand that site's session cookies to wherever it lands.
     */
    fun enqueue(offer: DownloadOffer, isIncognito: Boolean, pageUrl: String? = null): AppResult<EnqueuedDownload> {
        // DownloadManager only speaks http(s). blob: and data: downloads belong to the page and cannot be routed here.
        if (!URLUtil.isHttpUrl(offer.url) && !URLUtil.isHttpsUrl(offer.url)) {
            return AppResult.Failure(AppError.NotSupported)
        }
        if (needsStoragePermission()) return AppResult.Failure(AppError.Storage)

        return resultOf {
            val fileName = fileNameFor(offer)
            val request = DownloadManager.Request(offer.url.toUri())
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                .setTitle(fileName)
            offer.mimeType?.let(request::setMimeType)
            offer.userAgent?.let { request.addRequestHeader("User-Agent", it) }
            // Without the session's cookies, a download behind a login returns the login page instead of the file.
            if (DownloadNames.isSameSite(offer.url, pageUrl)) {
                incognito.cookiesFor(isIncognito).getCookie(offer.url)?.let { request.addRequestHeader("Cookie", it) }
            }

            val manager = context.getSystemService(DownloadManager::class.java)
            EnqueuedDownload(manager.enqueue(request), fileName)
        }
    }

    /** The name the file is saved under. The download prompt must show this same name, not a separate guess. */
    fun fileNameFor(offer: DownloadOffer): String =
        DownloadNames.sanitize(URLUtil.guessFileName(offer.url, offer.contentDisposition, offer.mimeType))

    private companion object {
        const val WRITE_STORAGE = android.Manifest.permission.WRITE_EXTERNAL_STORAGE
    }
}

/** Pure rules for download names and cookies, kept free of Android types so they can be unit tested. */
internal object DownloadNames {
    private const val FALLBACK_FILE_NAME = "download"
    private const val MAX_LENGTH = 120
    private const val MAX_EXTENSION = 10

    // Bidirectional overrides and isolates let "invoice<U+202E>fdp.apk" display as "invoicekpa.pdf".
    private val INVISIBLE_OR_BIDI = Regex("[\\p{Cc}\\p{Cf}\\u2028\\u2029]")

    /** The server chooses the name, so anything that could hide its real extension or leave Downloads goes. */
    fun sanitize(guessed: String?): String {
        val lastSegment = File(guessed.orEmpty().replace('\\', '/')).name
        val visible = lastSegment.replace(INVISIBLE_OR_BIDI, "").trim().trimStart('.')
        if (visible.isEmpty()) return FALLBACK_FILE_NAME
        if (visible.length <= MAX_LENGTH) return visible
        // Keeps the extension, so a long name cannot push ".apk" out of sight.
        val dot = visible.lastIndexOf('.')
        val extension = if (dot > 0 && visible.length - dot <= MAX_EXTENSION) visible.substring(dot) else ""
        return visible.take(MAX_LENGTH - extension.length) + extension
    }

    fun isSameSite(downloadUrl: String, pageUrl: String?): Boolean {
        val download = UrlUtils.siteOf(downloadUrl) ?: return false
        return pageUrl != null && UrlUtils.siteOf(pageUrl) == download
    }
}
