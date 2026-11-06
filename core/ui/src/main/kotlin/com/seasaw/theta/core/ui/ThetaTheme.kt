package com.seasaw.theta.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The Neel theme: deep indigo with a marigold highlight.
 *
 * In dark mode marigold takes over as primary, because indigo has too little contrast on a dark background. No dynamic
 * colour: most target phones run Android 10 or 11 and would not get it, and the palette is the brand.
 */
private val NeelLightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF2B2F8F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE2E5F6),
    onPrimaryContainer = Color(0xFF15182B),
    secondary = Color(0xFF8A5A00),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFE6B0),
    onSecondaryContainer = Color(0xFF2A1A00),
    tertiary = Color(0xFF565C7A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE2E5F6),
    onTertiaryContainer = Color(0xFF15182B),
    background = Color(0xFFF6F7FC),
    onBackground = Color(0xFF15182B),
    surface = Color(0xFFF6F7FC),
    onSurface = Color(0xFF15182B),
    surfaceVariant = Color(0xFFE2E5F6),
    onSurfaceVariant = Color(0xFF565C7A),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBFBFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFE8EAF7),
    surfaceContainerHighest = Color(0xFFE2E5F6),
    outline = Color(0xFF7D82A0),
    outlineVariant = Color(0xFFD3D6EA),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val NeelDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFF2B53A),
    onPrimary = Color(0xFF221700),
    primaryContainer = Color(0xFF2A2E4E),
    onPrimaryContainer = Color(0xFFE7E8F5),
    secondary = Color(0xFFA9B0FF),
    onSecondary = Color(0xFF15182B),
    secondaryContainer = Color(0xFF2A2E4E),
    onSecondaryContainer = Color(0xFFDDE0FF),
    tertiary = Color(0xFFA3A8C7),
    onTertiary = Color(0xFF15182B),
    tertiaryContainer = Color(0xFF2A2E4E),
    onTertiaryContainer = Color(0xFFE7E8F5),
    background = Color(0xFF0F1120),
    onBackground = Color(0xFFE7E8F5),
    surface = Color(0xFF0F1120),
    onSurface = Color(0xFFE7E8F5),
    surfaceVariant = Color(0xFF2A2E4E),
    onSurfaceVariant = Color(0xFFA3A8C7),
    surfaceContainerLowest = Color(0xFF0B0D19),
    surfaceContainerLow = Color(0xFF151728),
    surfaceContainer = Color(0xFF191C31),
    surfaceContainerHigh = Color(0xFF20233B),
    surfaceContainerHighest = Color(0xFF2A2E4E),
    outline = Color(0xFF6E7393),
    outlineVariant = Color(0xFF2F3354),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)

private val ThetaShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
)

/** The default system font is kept on purpose: it renders Devanagari and Cyrillic well and adds nothing to the APK. */
@Composable
fun ThetaTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) NeelDarkColors else NeelLightColors,
        shapes = ThetaShapes,
        content = content,
    )
}
