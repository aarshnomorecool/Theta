package com.seasaw.theta.feature.newtab

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.seasaw.theta.core.common.IoDispatcher
import com.seasaw.theta.core.data.BundledPhotos
import com.seasaw.theta.core.data.PhotoCredit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class Wallpaper(val image: Bitmap, val credit: PhotoCredit)

/** Picks the new-tab page's photo. Each call to [next] moves to the following one, so every new tab looks different. */
@Singleton
class Wallpapers @Inject constructor(
    @ApplicationContext private val context: Context,
    private val photos: BundledPhotos,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {
    private val preferences by lazy { context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE) }

    suspend fun next(): Wallpaper? {
        val all = photos.credits()
        if (all.isEmpty()) return null
        return withContext(dispatcher) {
            val index = (preferences.getInt(KEY_LAST, -1) + 1).mod(all.size)
            preferences.edit().putInt(KEY_LAST, index).apply()
            decode(all[index])?.let { Wallpaper(it, all[index]) }
        }
    }

    /**
     * Scaled to the screen's width and decoded in 16-bit colour: about 2 MB on a 720p phone instead of 10 MB at full
     * size, which matters on 2 GB phones.
     */
    private fun decode(photo: PhotoCredit): Bitmap? {
        val screenWidth = context.resources.displayMetrics.widthPixels
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
        if (screenWidth in 1 until SOURCE_WIDTH) {
            options.inScaled = true
            options.inDensity = SOURCE_WIDTH
            options.inTargetDensity = screenWidth
        }
        return try {
            photos.open(photo).use { BitmapFactory.decodeStream(it, null, options) }
        } catch (ignored: IOException) {
            null
        }
    }

    private companion object {
        const val PREFERENCES = "new_tab_page"
        const val KEY_LAST = "last_wallpaper"
        const val SOURCE_WIDTH = 1080
    }
}
