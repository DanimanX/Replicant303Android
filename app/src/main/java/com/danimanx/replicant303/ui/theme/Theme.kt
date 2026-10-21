package com.danimanx.replicant303.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp

private fun schemeFor(palette: SkinPalette) = darkColorScheme(
    primary = palette.accent,
    onPrimary = palette.backgroundDeep,
    secondary = palette.accentAlt,
    onSecondary = palette.backgroundDeep,
    tertiary = palette.warn,
    onTertiary = palette.backgroundDeep,
    background = palette.background,
    onBackground = palette.textPrimary,
    surface = palette.panel,
    onSurface = palette.textPrimary,
    surfaceVariant = palette.panelHigh,
    onSurfaceVariant = palette.textSecondary,
    outline = palette.line,
    outlineVariant = palette.lineBright,
    error = palette.danger,
    onError = palette.backgroundDeep
)

private val ReplicantShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(7.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun ReplicantTheme(skin: Skin, content: @Composable () -> Unit) {
    val palette = paletteFor(skin)
    CompositionLocalProvider(LocalSkin provides palette) {
        MaterialTheme(
            colorScheme = schemeFor(palette),
            typography = ReplicantTypography,
            shapes = ReplicantShapes,
            content = content
        )
    }
}
