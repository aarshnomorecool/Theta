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
 * The Slate theme: neutral greys with one soft blue accent, designed dark first.
 *
 * Greys carry a faint cool tint so they read as chosen rather than default. The warm tertiary colour is kept for the
 * one number people look for, the count of blocked ads and trackers.
 */
private val SlateDark: ColorScheme = darkColorScheme(
    primary = Color(0xFF8AB4FF),
    onPrimary = Color(0xFF0B1C3D),
    primaryContainer = Color(0xFF263552),
    onPrimaryContainer = Color(0xFFD8E3FF),
    secondary = Color(0xFFC4C6CE),
    onSecondary = Color(0xFF1A1C20),
    secondaryContainer = Color(0xFF2E3036),
    onSecondaryContainer = Color(0xFFE4E5EA),
    tertiary = Color(0xFFFF9E66),
    onTertiary = Color(0xFF3A1800),
    tertiaryContainer = Color(0xFF4A2A14),
    onTertiaryContainer = Color(0xFFFFDBC8),
    background = Color(0xFF111215),
    onBackground = Color(0xFFE4E5EA),
    surface = Color(0xFF111215),
    onSurface = Color(0xFFE4E5EA),
    surfaceVariant = Color(0xFF2B2D33),
    onSurfaceVariant = Color(0xFFA5A8B0),
    surfaceContainerLowest = Color(0xFF0B0C0E),
    surfaceContainerLow = Color(0xFF17181C),
    surfaceContainer = Color(0xFF1C1E22),
    surfaceContainerHigh = Color(0xFF24262B),
    surfaceContainerHighest = Color(0xFF2E3036),
    outline = Color(0xFF5E616A),
    outlineVariant = Color(0xFF33363C),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFE4E5EA),
    inverseOnSurface = Color(0xFF1C1E22),
    inversePrimary = Color(0xFF2F5BD3),
    scrim = Color(0xFF000000),
)

private val SlateLight: ColorScheme = lightColorScheme(
    primary = Color(0xFF2F5BD3),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE4FF),
    onPrimaryContainer = Color(0xFF0A1A45),
    secondary = Color(0xFF5A5D66),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE3E4E9),
    onSecondaryContainer = Color(0xFF1A1C20),
    tertiary = Color(0xFFB4501A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDBC8),
    onTertiaryContainer = Color(0xFF351000),
    background = Color(0xFFF6F6F8),
    onBackground = Color(0xFF17181B),
    surface = Color(0xFFF6F6F8),
    onSurface = Color(0xFF17181B),
    surfaceVariant = Color(0xFFE3E4E9),
    onSurfaceVariant = Color(0xFF5A5D66),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F1F4),
    surfaceContainer = Color(0xFFEBEBEF),
    surfaceContainerHigh = Color(0xFFE5E5EA),
    surfaceContainerHighest = Color(0xFFDEDFE4),
    outline = Color(0xFF878A93),
    outlineVariant = Color(0xFFD3D4DA),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    inverseSurface = Color(0xFF2E3036),
    inverseOnSurface = Color(0xFFF1F1F4),
    inversePrimary = Color(0xFF8AB4FF),
    scrim = Color(0xFF000000),
)

/** Accessibility: secondary text and borders move much closer to full contrast. */
private fun ColorScheme.withHighContrast(dark: Boolean): ColorScheme = copy(
    onSurfaceVariant = if (dark) Color(0xFFDADCE2) else Color(0xFF2A2C31),
    outline = if (dark) Color(0xFFB0B3BB) else Color(0xFF4A4D55),
    outlineVariant = if (dark) Color(0xFF6A6D76) else Color(0xFF8A8D96),
    primary = if (dark) Color(0xFFB5CEFF) else Color(0xFF1C3FA8),
)

private val ThetaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
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
    val base = if (darkTheme) SlateDark else SlateLight
    MaterialTheme(
        colorScheme = if (highContrast) base.withHighContrast(darkTheme) else base,
        shapes = ThetaShapes,
        typography = ThetaTypography,
        content = content,
    )
}
