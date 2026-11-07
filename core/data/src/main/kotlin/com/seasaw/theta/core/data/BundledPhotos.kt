package com.seasaw.theta.core.data

import android.content.Context
import com.seasaw.theta.core.common.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

/** Who made a photo and on what terms. Every bundled photo is CC0, public domain or CC BY, and each is credited. */
data class PhotoCredit(
    val file: String,
    val title: String,
    val author: String,
    val licence: String,
    val sourceUrl: String,
)

/**
 * The new-tab page's background photos, shipped inside the app so showing one costs no data and tells no server that
 * a tab was opened. The list and its credits come from `assets/wallpapers/credits.json`.
 */
@Singleton
class BundledPhotos @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {
    @Volatile
    private var cached: List<PhotoCredit>? = null

    suspend fun credits(): List<PhotoCredit> = cached ?: withContext(dispatcher) { read().also { cached = it } }

    /** The photo file itself. The caller closes it. */
    fun open(photo: PhotoCredit): InputStream = context.assets.open("$FOLDER/${photo.file}")

    private fun read(): List<PhotoCredit> = try {
        val json = context.assets.open("$FOLDER/credits.json").bufferedReader().use { it.readText() }
        val array = JSONObject(json).getJSONArray("photos")
        List(array.length()) { i ->
            val photo = array.getJSONObject(i)
            PhotoCredit(
                file = photo.getString("file"),
                title = photo.getString("title"),
                author = photo.getString("author"),
                licence = photo.getString("licence"),
                sourceUrl = photo.getString("sourceUrl"),
            )
        }
    } catch (ignored: IOException) {
        emptyList()
    } catch (ignored: JSONException) {
        emptyList()
    }

    private companion object {
        const val FOLDER = "wallpapers"
    }
}
