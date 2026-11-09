package com.seasaw.theta.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The Neel theme, taken from the launcher icon: a marigold theta on indigo. Designed dark first.
 *
 * Dark mode is indigo-tinted surfaces with marigold as the one accent. In light mode marigold is too pale to carry
 * text or icons on white, so deep indigo takes the accent role and marigold moves into the containers, which keeps
 * the same pairing the icon has. The tertiary mint is kept for the one number people look for, the count of blocked
 * ads and trackers.
 */
private val NeelDark: ColorScheme = darkColorScheme(
    primary = Color(0xFFF2B53A),
    onPrimary = Color(0xFF2B1D00),
    primaryContainer = Color(0xFF2E3270),
    onPrimaryContainer = Color(0xFFFFD98A),
    secondary = Color(0xFFC3C5E0),
    onSecondary = Color(0xFF1B1D33),
    secondaryContainer = Color(0xFF2C2F4B),
    onSecondaryContainer = Color(0xFFE2E3F7),
    tertiary = Color(0xFF7FD8C0),
    onTertiary = Color(0xFF00382D),
    tertiaryContainer = Color(0xFF0F4E41),
    onTertiaryContainer = Color(0xFFB3F2E0),
    background = Color(0xFF0F1120),
    onBackground = Color(0xFFE6E6F2),
    surface = Color(0xFF0F1120),
    onSurface = Color(0xFFE6E6F2),
    surfaceVariant = Color(0xFF2A2D44),
    onSurfaceVariant = Color(0xFFA9ABC3),
    surfaceContainerLowest = Color(0xFF0A0B17),
    surfaceContainerLow = Color(0xFF151729),
    surfaceContainer = Color(0xFF1A1D31),
    surfaceContainerHigh = Color(0xFF22253B),
    surfaceContainerHighest = Color(0xFF2B2E46),
    outline = Color(0xFF5F6280),
    outlineVariant = Color(0xFF32354D),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFE6E6F2),
    inverseOnSurface = Color(0xFF1A1D31),
    inversePrimary = Color(0xFF7A5900),
    scrim = Color(0xFF000000),
)

private val NeelLight: ColorScheme = lightColorScheme(
    primary = Color(0xFF3236A0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE2A3),
    onPrimaryContainer = Color(0xFF2B1D00),
    secondary = Color(0xFF5B5D78),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE2E3F3),
    onSecondaryContainer = Color(0xFF181A33),
    tertiary = Color(0xFF00695A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFB3F2E0),
    onTertiaryContainer = Color(0xFF00201A),
    background = Color(0xFFF6F7FC),
    onBackground = Color(0xFF181A2B),
    surface = Color(0xFFF6F7FC),
    onSurface = Color(0xFF181A2B),
    surfaceVariant = Color(0xFFE2E3EF),
    onSurfaceVariant = Color(0xFF565874),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F1F8),
    surfaceContainer = Color(0xFFEAEBF4),
    surfaceContainerHigh = Color(0xFFE4E5F0),
    surfaceContainerHighest = Color(0xFFDDDFEB),
    outline = Color(0xFF85879F),
    outlineVariant = Color(0xFFD2D3E2),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    inverseSurface = Color(0xFF2D3047),
    inverseOnSurface = Color(0xFFF0F1F8),
    inversePrimary = Color(0xFFBFC2FF),
    scrim = Color(0xFF000000),
)

/** Accessibility: secondary text and borders move much closer to full contrast. */
private fun ColorScheme.withHighContrast(dark: Boolean): ColorScheme = copy(
    onSurfaceVariant = if (dark) Color(0xFFDCDDEE) else Color(0xFF2A2C42),
    outline = if (dark) Color(0xFFB2B4CC) else Color(0xFF4A4C66),
    outlineVariant = if (dark) Color(0xFF6B6E8A) else Color(0xFF8A8CA6),
    primary = if (dark) Color(0xFFFFD27A) else Color(0xFF1E2178),
)

/** A little tighter than Material's defaults: calmer, and more room for content on a small screen. */
private val ThetaShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val ThetaTypography = Typography().let { base ->
    base.copy(
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium),
        labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
    )
}

/** The default system font is kept on purpose: it renders Devanagari and Cyrillic well and adds nothing to the APK. */
@Composable
fun ThetaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    highContrast: Boolean = false,
    content: @Composable () -> Unit,
) {
    val base = if (darkTheme) NeelDark else NeelLight
    MaterialTheme(
        colorScheme = if (highContrast) base.withHighContrast(darkTheme) else base,
        shapes = ThetaShapes,
        typography = ThetaTypography,
        content = content,
    )
}
