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

    fun enqueue(offer: DownloadOffer, isIncognito: Boolean): AppResult<EnqueuedDownload> {
        // DownloadManager only speaks http(s). blob: and data: downloads belong to the page and cannot be routed here.
        if (!URLUtil.isHttpUrl(offer.url) && !URLUtil.isHttpsUrl(offer.url)) {
            return AppResult.Failure(AppError.NotSupported)
        }
        if (needsStoragePermission()) return AppResult.Failure(AppError.Storage)

        return resultOf {
            val fileName = safeFileName(offer)
            val request = DownloadManager.Request(offer.url.toUri())
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                .setTitle(fileName)
            offer.mimeType?.let(request::setMimeType)
            offer.userAgent?.let { request.addRequestHeader("User-Agent", it) }
            // Without the session's cookies, a download behind a login returns the login page instead of the file.
            incognito.cookiesFor(isIncognito).getCookie(offer.url)?.let { request.addRequestHeader("Cookie", it) }

            val manager = context.getSystemService(DownloadManager::class.java)
            EnqueuedDownload(manager.enqueue(request), fileName)
        }
    }

    /** Filename comes from the server. Taking only the last path segment prevents writing outside Downloads. */
    private fun safeFileName(offer: DownloadOffer): String {
        val guessed = URLUtil.guessFileName(offer.url, offer.contentDisposition, offer.mimeType)
        return File(guessed.replace('\\', '/')).name.ifEmpty { FALLBACK_FILE_NAME }
    }

    private companion object {
        const val WRITE_STORAGE = android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        const val FALLBACK_FILE_NAME = "download"
    }
}
