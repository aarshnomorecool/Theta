package com.seasaw.theta.core.ads

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface AdSlotEntryPoint {
    fun adsManager(): AdsManager
}

private val SLOT_HEIGHT = 96.dp

/**
 * The one way features show an ad (ads rule 10). [placement] can only name a native screen, so an ad next to web
 * content cannot be requested at all.
 *
 * The slot reserves its full height while an ad is on its way, so that loading never moves content (ads rule 5). It
 * only collapses when no ad is coming. Callers must not place it directly beside tappable controls.
 *
 * The ad itself is owned by [AdsManager], not by this composable, so a slot that leaves and re-enters the screen shows
 * the same ad instead of requesting a new one.
 *
 * @param visibleHosts hosts of any page previews on the same screen, for the YouTube rule
 */
@Composable
fun AdSlot(placement: AdPlacement, modifier: Modifier = Modifier, visibleHosts: Collection<String?> = emptyList()) {
    if (LocalInspectionMode.current) {
        Box(modifier.fillMaxWidth().height(SLOT_HEIGHT), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.ad_label), color = MaterialTheme.colorScheme.outline)
        }
        return
    }
    val context = LocalContext.current
    val manager = remember {
        EntryPointAccessors.fromApplication(context.applicationContext, AdSlotEntryPoint::class.java).adsManager()
    }
    val adsState by manager.state.collectAsStateWithLifecycle()
    val slotFlow = remember(placement) { manager.nativeAd(placement) }
    val slot by slotFlow.collectAsStateWithLifecycle()
    val isAllowedHere = AdPolicy.mayShowOnScreen(visibleHosts)

    LaunchedEffect(adsState, placement, isAllowedHere) {
        if (adsState == AdsState.READY && isAllowedHere) manager.requestNativeAd(placement)
    }

    val isHidden = adsState == AdsState.UNAVAILABLE || slot is NativeSlotState.Empty || !isAllowedHere
    if (isHidden) return

    val adLabel = stringResource(R.string.ad_label)
    val colors = MaterialTheme.colorScheme
    Box(modifier.fillMaxWidth().height(SLOT_HEIGHT)) {
        (slot as? NativeSlotState.Showing)?.ad?.let { current ->
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { viewContext -> NativeAdViews.create(viewContext, adLabel, colors) },
                update = { view -> NativeAdViews.bind(view, current) },
            )
        }
    }
}

/** The SDK requires a real View hierarchy for native ads, so this part cannot be Compose. */
private object NativeAdViews {
    private const val ICON_DP = 48
    private const val PADDING_DP = 12
    private const val GAP_DP = 12
    private const val LABEL_SP = 11f
    private const val TITLE_SP = 14f
    private const val BODY_SP = 12f

    fun create(context: Context, adLabel: String, colors: ColorScheme): NativeAdView {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val icon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(ICON_DP), dp(ICON_DP)).apply { marginEnd = dp(GAP_DP) }
        }
        val headline = text(context, TITLE_SP, colors.onSurface.toArgb()).apply { setTypeface(typeface, Typeface.BOLD) }
        val body = text(context, BODY_SP, colors.onSurfaceVariant.toArgb())
        val label = text(context, LABEL_SP, colors.outline.toArgb()).apply { text = adLabel }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(label)
            addView(headline)
            addView(body)
        }
        val action = Button(context).apply {
            isAllCaps = false
            setTextColor(colors.onPrimary.toArgb())
            setBackgroundColor(colors.primary.toArgb())
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(PADDING_DP), dp(PADDING_DP), dp(PADDING_DP), dp(PADDING_DP))
            setBackgroundColor(colors.surfaceContainer.toArgb())
            addView(icon)
            addView(column)
            addView(action)
        }
        return NativeAdView(context).apply {
            addView(row)
            iconView = icon
            headlineView = headline
            bodyView = body
            callToActionView = action
        }
    }

    fun bind(view: NativeAdView, ad: NativeAd) {
        (view.headlineView as TextView).text = ad.headline
        (view.bodyView as TextView).text = ad.body
        (view.callToActionView as Button).text = ad.callToAction
        (view.iconView as ImageView).setImageDrawable(ad.icon?.drawable)
        view.setNativeAd(ad)
    }

    private fun text(context: Context, sizeSp: Float, color: Int) = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(color)
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    }
}
