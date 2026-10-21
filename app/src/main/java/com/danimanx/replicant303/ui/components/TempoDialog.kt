package com.danimanx.replicant303.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danimanx.replicant303.ui.theme.LocalSkin

@Composable
fun TempoDialog(
    tempo: Float,
    onChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val skin = LocalSkin.current
    var value by remember { mutableFloatStateOf(tempo.coerceIn(40f, 300f)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = skin.panel,
        titleContentColor = skin.textPrimary,
        textContentColor = skin.textSecondary,
        title = { Text("TEMPO", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                Text(
                    "${value.toInt()} BPM",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 40.sp
                    ),
                    color = skin.accent
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = value,
                    onValueChange = {
                        value = it
                        onChange(it)
                    },
                    valueRange = 40f..300f,
                    colors = SliderDefaults.colors(
                        thumbColor = skin.accent,
                        activeTrackColor = skin.accent,
                        inactiveTrackColor = skin.line
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(120f, 125f, 128f, 132f, 140f, 150f).forEach { preset ->
                        ActionButton(
                            text = preset.toInt().toString(),
                            modifier = Modifier.weight(1f),
                            accent = skin.accent,
                            selected = value.toInt() == preset.toInt(),
                            height = 34.dp
                        ) {
                            value = preset
                            onChange(preset)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Stepper(
                        value = value.toInt().toString(),
                        label = "BPM",
                        accent = skin.accent,
                        onMinus = {
                            value = (value - 1f).coerceAtLeast(40f)
                            onChange(value)
                        },
                        onPlus = {
                            value = (value + 1f).coerceAtMost(300f)
                            onChange(value)
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("DONE", color = skin.accent)
            }
        }
    )
}
