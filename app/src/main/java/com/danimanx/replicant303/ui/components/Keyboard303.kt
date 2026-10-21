package com.danimanx.replicant303.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.danimanx.replicant303.ui.theme.LocalSkin
import kotlin.math.roundToInt

private val blackKeyDegrees = setOf(1, 3, 6, 8, 10)

/**
 * The 13-key tactile keyboard of the 303. Touch or drag across it; the LED above
 * each key lights while held. Lower touches produce higher velocity.
 */
@Composable
fun Keyboard303(
    baseNote: Int,
    onNoteOn: (Int, Int) -> Unit,
    onNoteOff: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val skin = LocalSkin.current
    var pressed by remember { mutableIntStateOf(-1) }
    val labelPaint = remember {
        Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.MONOSPACE,
                android.graphics.Typeface.BOLD
            )
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
            .pointerInput(baseNote) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    fun indexAt(x: Float): Int {
                        if (size.width <= 0f) return -1
                        return (x / (size.width / 13f)).toInt().coerceIn(0, 12)
                    }
                    fun velocityAt(y: Float): Int {
                        val t = (y / size.height).coerceIn(0f, 1f)
                        return (122 - t * 50).roundToInt().coerceIn(48, 127)
                    }

                    var current = indexAt(down.position.x)
                    pressed = current
                    onNoteOn(baseNote + current, velocityAt(down.position.y))
                    down.consume()

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val next = indexAt(change.position.x)
                        if (next != current) {
                            onNoteOff(baseNote + current)
                            current = next
                            pressed = next
                            onNoteOn(baseNote + next, velocityAt(change.position.y))
                        }
                        change.consume()
                    }

                    onNoteOff(baseNote + current)
                    pressed = -1
                }
            }
    ) {
        val keyWidth = size.width / 13f
        val gap = 2.dp.toPx()
        val ledRadius = 3.dp.toPx()
        labelPaint.textSize = 8.dp.toPx()
        labelPaint.color = skin.textSecondary.copy(alpha = 0.75f).toArgb()

        for (i in 0..12) {
            val left = i * keyWidth + gap / 2f
            val top = 16.dp.toPx()
            val height = size.height - top - 8.dp.toPx()
            val degree = (baseNote + i) % 12
            val isBlack = degree in blackKeyDegrees
            val isPressed = pressed == i

            val keyColor = when {
                isPressed -> skin.accent
                isBlack -> skin.backgroundDeep
                else -> skin.textPrimary.copy(alpha = 0.88f)
            }
            drawRoundRect(
                color = keyColor,
                topLeft = Offset(left, top),
                size = Size(keyWidth - gap, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
            )
            drawRoundRect(
                color = if (isPressed) skin.accent else if (isBlack) skin.lineBright.copy(alpha = 0.7f) else skin.line,
                topLeft = Offset(left, top),
                size = Size(keyWidth - gap, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                style = Stroke(width = 1.dp.toPx())
            )
            drawCircle(
                color = if (isPressed) skin.accent else skin.line,
                radius = ledRadius,
                center = Offset(left + (keyWidth - gap) / 2f, 8.dp.toPx())
            )
            if (degree == 0) {
                labelPaint.color = (if (isBlack) skin.textSecondary else skin.backgroundDeep.copy(alpha = 0.72f)).toArgb()
                drawContext.canvas.nativeCanvas.drawText(
                    "C${(baseNote + i) / 12 - 1}",
                    left + (keyWidth - gap) / 2f,
                    size.height - 1.dp.toPx(),
                    labelPaint
                )
            }
        }
    }
}
