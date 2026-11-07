package com.seasaw.theta.core.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.LruCache
import com.seasaw.theta.core.common.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

/**
 * Small images kept as files, with a memory cache in front. Both stores below are caches: a failed write only means
 * a letter tile or a blank preview, so IO errors are swallowed.
 */
abstract class ImageFileStore(
    private val directory: File,
    private val dispatcher: CoroutineDispatcher,
    cacheEntries: Int,
) {
    private val memory = LruCache<String, Bitmap>(cacheEntries)
    private val _updates = MutableSharedFlow<String>(extraBufferCapacity = UPDATE_BUFFER)

    /** Emits a key whenever its image changes, so lists showing it can reload. */
    val updates: SharedFlow<String> = _updates.asSharedFlow()

    protected abstract fun fileName(key: String): String

    protected suspend fun store(key: String, bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int) {
        memory.put(key, bitmap)
        withContext(dispatcher) {
            try {
                directory.mkdirs()
                val target = File(directory, fileName(key))
                val temporary = File(directory, "${target.name}.tmp")
                temporary.outputStream().use { bitmap.compress(format, quality, it) }
                temporary.renameTo(target)
            } catch (ignored: IOException) {
                // A cache miss later is the only consequence.
            }
        }
        _updates.tryEmit(key)
    }

    /** Memory only. Use in composition, then fall back to [load]. */
    fun cached(key: String): Bitmap? = memory.get(key)

    suspend fun load(key: String): Bitmap? = cached(key) ?: withContext(dispatcher) {
        val file = File(directory, fileName(key))
        if (!file.exists()) return@withContext null
        try {
            BitmapFactory.decodeFile(file.path)?.also { memory.put(key, it) }
        } catch (ignored: IOException) {
            null
        }
    }

    suspend fun remove(key: String) {
        memory.remove(key)
        withContext(dispatcher) { File(directory, fileName(key)).delete() }
        _updates.tryEmit(key)
    }

    suspend fun clear() {
        memory.evictAll()
        withContext(dispatcher) { directory.deleteRecursively() }
        _updates.tryEmit(CLEARED)
    }

    companion object {
        /** Emitted on [updates] when everything was removed. */
        const val CLEARED = ""
        private const val UPDATE_BUFFER = 16
    }
}

/**
 * Site icons, keyed by host without "www.". Stored because they make the new-tab page and history recognisable at a
 * glance; wiped together with history, since a folder of icons is itself a record of where the user has been.
 * Incognito tabs must never save here.
 */
@Singleton
class FaviconStore @Inject constructor(
    @ApplicationContext context: Context,
    @IoDispatcher dispatcher: CoroutineDispatcher,
) : ImageFileStore(File(context.filesDir, "favicons"), dispatcher, cacheEntries = 64) {
    suspend fun save(host: String, icon: Bitmap) {
        val key = keyFor(host)
        if (key.isEmpty() || icon.width < MIN_SIZE) return
        // Sites send anything from 16 to 512 pixels. 64 is plenty for a 40dp tile and keeps the folder small.
        val scaled = if (icon.width > MAX_SIZE) Bitmap.createScaledBitmap(icon, MAX_SIZE, MAX_SIZE, true) else icon
        store(key, scaled, Bitmap.CompressFormat.PNG, PNG_QUALITY)
    }

    fun cachedFor(host: String): Bitmap? = cached(keyFor(host))

    suspend fun loadFor(host: String): Bitmap? = keyFor(host).takeIf { it.isNotEmpty() }?.let { load(it) }

    /** Hashed, so neither the file names nor the folder listing reveal the sites. */
    override fun fileName(key: String): String = sha256(key) + ".png"

    companion object {
        fun keyFor(host: String): String = host.lowercase().removePrefix("www.")
        private const val MIN_SIZE = 16
        private const val MAX_SIZE = 64

        /** PNG ignores quality; the value only satisfies the API. */
        private const val PNG_QUALITY = 100
    }
}

/**
 * Pictures of tabs for the tab switcher, keyed by tab id. Wiped with tabs. Incognito previews are kept in memory by
 * the browser and never reach this store.
 */
@Singleton
class TabPreviewStore @Inject constructor(
    @ApplicationContext context: Context,
    @IoDispatcher dispatcher: CoroutineDispatcher,
) : ImageFileStore(File(context.cacheDir, "tab_previews"), dispatcher, cacheEntries = 24) {
    suspend fun save(tabId: String, preview: Bitmap) = store(tabId, preview, webp(), PREVIEW_QUALITY)

    override fun fileName(key: String): String = File(key).name + ".webp"

    @Suppress("DEPRECATION")
    private fun webp(): Bitmap.CompressFormat = if (Build.VERSION.SDK_INT >=
        Build.VERSION_CODES.R
    ) {
        Bitmap.CompressFormat.WEBP_LOSSY
    } else {
        Bitmap.CompressFormat.WEBP
    }

    private companion object {
        const val PREVIEW_QUALITY = 70
    }
}

private fun sha256(text: String): String =
    MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") {
        "%02x".format(it)
    }.take(HASH_LENGTH)

/** 128 bits of the hash: plenty to tell sites apart, and shorter file names. */
private const val HASH_LENGTH = 32
