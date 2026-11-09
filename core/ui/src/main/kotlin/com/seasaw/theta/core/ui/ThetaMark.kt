package com.seasaw.theta.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Theta's mark: the launcher icon's marigold theta on indigo, drawn from the same path so the two always match.
 * Decorative only, so it has no content description.
 */
@Composable
fun ThetaMark(modifier: Modifier = Modifier, size: Dp = 56.dp) {
    Image(
        imageVector = ThetaMarkVector,
        contentDescription = null,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * CORNER_RATIO)),
    )
}

private val Indigo = Color(0xFF2B2F8F)
private val Marigold = Color(0xFFF2B53A)

/** The theta from res/drawable/ic_launcher_foreground.xml in the app module, on its 108-unit grid. */
private const val THETA_PATH = "M54,28c-14,0 -22,11.6 -22,26s8,26 22,26 22,-11.6 22,-26 -8,-26 -22,-26z" +
    "M54,35c8,0 13.6,6.6 14.8,15L39.2,50C40.4,41.6 46,35 54,35z" +
    "M54,73c-8,0 -13.6,-6.6 -14.8,-15l29.6,0C67.6,66.4 62,73 54,73z"

/** The launcher grid is 108 units with generous safe-zone padding; cropping to 76 makes the theta fill the mark. */
private const val VIEWPORT = 76f
private const val CROP_OFFSET = -16f
private const val CORNER_RATIO = 0.3f

private val ThetaMarkVector: ImageVector by lazy {
    ImageVector.Builder(
        name = "theta_mark",
        defaultWidth = VIEWPORT.dp,
        defaultHeight = VIEWPORT.dp,
        viewportWidth = VIEWPORT,
        viewportHeight = VIEWPORT,
    ).apply {
        addPath(pathData = addPathNodes("M0,0h${VIEWPORT}v${VIEWPORT}h-${VIEWPORT}z"), fill = SolidColor(Indigo))
        addGroup(translationX = CROP_OFFSET, translationY = CROP_OFFSET)
        addPath(pathData = addPathNodes(THETA_PATH), fill = SolidColor(Marigold))
        clearGroup()
    }.build()
}
