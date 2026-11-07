package com.seasaw.theta.feature.browser

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.Window
import kotlin.math.min

/**
 * Takes the small picture of a page that the tab switcher shows.
 *
 * It copies what is already on screen rather than asking the WebView to draw again, which on a slow phone would
 * re-render the page in software. Only the top of the page is kept, at a quarter of screen width in 16-bit colour:
 * about 200 KB per tab.
 */
internal object PreviewCapture {
    private const val WIDTH = 270
    private const val MAX_ASPECT = 1.25f

    /** Calls [onDone] exactly once, with the picture or with null when there was nothing to copy. */
    fun capture(view: View?, window: Window?, onDone: (Bitmap?) -> Unit) {
        val isOnScreen = view != null && view.isAttachedToWindow && view.width > 0 && view.height > 0
        if (!isOnScreen || window == null) {
            onDone(null)
            return
        }
        val origin = IntArray(2)
        view!!.getLocationInWindow(origin)
        val height = min(view.height, (view.width * MAX_ASPECT).toInt())
        val source = Rect(origin[0], origin[1], origin[0] + view.width, origin[1] + height)
        val target = Bitmap.createBitmap(
            WIDTH,
            (WIDTH * height.toFloat() / view.width).toInt(),
            Bitmap.Config.ARGB_8888,
        )
        try {
            PixelCopy.request(window, source, target, { result ->
                if (result == PixelCopy.SUCCESS) {
                    val compact = target.copy(Bitmap.Config.RGB_565, false)
                    target.recycle()
                    onDone(compact)
                } else {
                    target.recycle()
                    onDone(null)
                }
            }, Handler(Looper.getMainLooper()))
        } catch (ignored: IllegalArgumentException) {
            // The window has no surface yet, or is going away. A placeholder card is fine.
            target.recycle()
            onDone(null)
        }
    }
}
