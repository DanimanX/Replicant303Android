package com.danimanx.replicant303.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

enum class Skin(val label: String) {
    GREEN("TD-3 GREEN"),
    RED("OBSIDIAN RED")
}

@Immutable
data class SkinPalette(
    val skin: Skin,
    val background: Color,
    val backgroundDeep: Color,
    val panel: Color,
    val panelHigh: Color,
    val panelSoft: Color,
    val line: Color,
    val lineBright: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val accentAlt: Color,
    val danger: Color,
    val success: Color,
    val warn: Color,
    val chassis: Color,
    val pcb: Color,
    val silk: Color,
    val led: Color
) {
    val backgroundGradient: List<Color>
        get() = listOf(background, backgroundDeep)

    val panelGradient: Brush
        get() = Brush.verticalGradient(listOf(panelHigh, panel))
}

val GreenSkin = SkinPalette(
    skin = Skin.GREEN,
    background = Color(0xFF040B07),
    backgroundDeep = Color(0xFF020604),
    panel = Color(0xFF081A10),
    panelHigh = Color(0xFF0C2417),
    panelSoft = Color(0xFF0A1E13),
    line = Color(0xFF1B432B),
    lineBright = Color(0xFF2C6642),
    textPrimary = Color(0xFFE8FFF0),
    textSecondary = Color(0xFF9BC9AA),
    textMuted = Color(0xFF5F846D),
    accent = Color(0xFFB7FF4B),
    accentAlt = Color(0xFF2FE6A0),
    danger = Color(0xFFFF5A6E),
    success = Color(0xFF43E08A),
    warn = Color(0xFFFFC857),
    chassis = Color(0xFF0B1E12),
    pcb = Color(0xFF0E2A18),
    silk = Color(0xFFB7FF4B),
    led = Color(0xFFB7FF4B)
)

val RedSkin = SkinPalette(
    skin = Skin.RED,
    background = Color(0xFF0B0406),
    backgroundDeep = Color(0xFF050203),
    panel = Color(0xFF1B090D),
    panelHigh = Color(0xFF251016),
    panelSoft = Color(0xFF210C11),
    line = Color(0xFF4A1D27),
    lineBright = Color(0xFF6E2C3B),
    textPrimary = Color(0xFFFFE9EC),
    textSecondary = Color(0xFFD8A3AD),
    textMuted = Color(0xFF8E5F68),
    accent = Color(0xFFFF5A5E),
    accentAlt = Color(0xFFFFB45C),
    danger = Color(0xFFFF5A6E),
    success = Color(0xFF4CE0A5),
    warn = Color(0xFFF4D03F),
    chassis = Color(0xFF240B10),
    pcb = Color(0xFF3A1119),
    silk = Color(0xFFFF8C93),
    led = Color(0xFFFF5A5E)
)

fun paletteFor(skin: Skin): SkinPalette = if (skin == Skin.RED) RedSkin else GreenSkin

val LocalSkin = staticCompositionLocalOf { GreenSkin }
