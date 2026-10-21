package com.danimanx.replicant303.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danimanx.replicant303.ui.theme.LocalSkin

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val skin = LocalSkin.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(skin.panelGradient)
            .border(1.dp, skin.line.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
            .drawBehind {
                if (accent != null) {
                    drawLine(
                        color = accent.copy(alpha = 0.9f),
                        start = Offset(14.dp.toPx(), 0f),
                        end = Offset(size.width - 14.dp.toPx(), 0f),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }
            .padding(contentPadding),
        content = content
    )
}

@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    accent: Color = LocalSkin.current.textSecondary,
    trailing: (@Composable () -> Unit)? = null
) {
    val skin = LocalSkin.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            maxLines = 1
        )
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(1.dp).background(skin.line))
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
    }
}

@Composable
fun StatusChip(
    label: String,
    value: String? = null,
    accent: Color = LocalSkin.current.accent,
    active: Boolean = true,
    modifier: Modifier = Modifier
) {
    val skin = LocalSkin.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) accent.copy(alpha = 0.10f) else skin.panelHigh)
            .border(1.dp, if (active) accent.copy(alpha = 0.35f) else skin.line, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            Modifier
                .size(5.dp)
                .clip(RoundedCornerShape(50))
                .background(if (active) accent else skin.textMuted.copy(alpha = 0.5f))
        )
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
            color = if (active) accent else skin.textMuted,
            maxLines = 1
        )
        if (!value.isNullOrBlank()) {
            Text(
                value.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                color = skin.textPrimary,
                maxLines = 1
            )
        }
    }
}

@Composable
fun TinyTag(text: String, color: Color = LocalSkin.current.textSecondary, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(3.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
        color = color,
        maxLines = 1
    )
}

@Composable
fun LedDot(active: Boolean, accent: Color, size: Dp = 6.dp, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(50))
            .background(if (active) accent else skin.line)
    )
}

@Composable
fun ActionButton(
    text: String,
    modifier: Modifier = Modifier,
    accent: Color = LocalSkin.current.accent,
    selected: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    height: Dp = 40.dp,
    onClick: () -> Unit
) {
    val skin = LocalSkin.current
    val bg = if (selected) accent else skin.panelHigh
    val fg = if (selected) skin.backgroundDeep else if (enabled) skin.textPrimary else skin.textMuted
    Row(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) bg else skin.panelHigh.copy(alpha = 0.5f))
            .border(1.dp, if (selected) accent else skin.line, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ToggleChip(
    text: String,
    selected: Boolean,
    accent: Color = LocalSkin.current.accent,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    ActionButton(
        text = text,
        modifier = modifier,
        accent = accent,
        selected = selected,
        enabled = enabled,
        height = 30.dp,
        onClick = onClick
    )
}

@Composable
fun Stepper(
    value: String,
    label: String,
    accent: Color = LocalSkin.current.accent,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier
) {
    val skin = LocalSkin.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(skin.panelHigh)
            .border(1.dp, skin.line, RoundedCornerShape(8.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "−",
            modifier = Modifier
                .clickable(onClick = onMinus)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.titleMedium,
            color = accent
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(58.dp)
        ) {
            Text(value, style = MaterialTheme.typography.labelMedium, color = skin.textPrimary)
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                color = skin.textMuted
            )
        }
        Text(
            "+",
            modifier = Modifier
                .clickable(onClick = onPlus)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.titleMedium,
            color = accent
        )
    }
}
