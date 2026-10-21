package com.danimanx.replicant303.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danimanx.replicant303.model.BankLetters
import com.danimanx.replicant303.model.EngineUiState
import com.danimanx.replicant303.model.MidiUiState
import com.danimanx.replicant303.ui.theme.LocalSkin
import com.danimanx.replicant303.ui.theme.Skin
import java.util.Locale

@Composable
fun AppHeader(
    state: EngineUiState,
    midi: MidiUiState,
    skinName: Skin,
    compact: Boolean,
    onPlayToggle: () -> Unit,
    onTempoClick: () -> Unit,
    onSkinClick: () -> Unit,
    onOpenMidi: () -> Unit
) {
    val skin = LocalSkin.current
    val bankLetter = BankLetters.getOrElse(state.presetBank.coerceIn(0, 7)) { "A" }
    val patternLetter = BankLetters.getOrElse(state.currentPattern.coerceIn(0, 7)) { "A" }
    val presetDetail = "BANK $bankLetter · SLOT ${(state.presetSlot + 1).toString().padStart(2, '0')} · PATTERN $patternLetter"

    if (compact) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Brand(modifier = Modifier.weight(1f))
                SkinChip(skinName, onClick = onSkinClick)
                BigPlayButton(state.playing, onPlayToggle)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(skin.panelHigh.copy(alpha = 0.85f))
                        .border(1.dp, skin.line, RoundedCornerShape(9.dp))
                        .clickable { onTempoClick() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        state.presetName,
                        style = MaterialTheme.typography.titleSmall,
                        color = skin.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        presetDetail,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                        color = skin.textMuted,
                        maxLines = 1
                    )
                }
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(skin.accent.copy(alpha = 0.10f))
                        .border(1.dp, skin.accent.copy(alpha = 0.40f), RoundedCornerShape(9.dp))
                        .clickable { onTempoClick() }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        state.tempo.toInt().toString(),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        ),
                        color = skin.accent
                    )
                    Text(
                        "BPM",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                        color = skin.textSecondary
                    )
                }
            }
            StatusRow(state, midi, onOpenMidi)
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Brand(modifier = Modifier.width(230.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(skin.panelHigh.copy(alpha = 0.85f))
                    .border(1.dp, skin.line, RoundedCornerShape(10.dp))
                    .clickable { onTempoClick() }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text(
                    state.presetName,
                    style = MaterialTheme.typography.titleSmall,
                    color = skin.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    presetDetail,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    color = skin.textMuted
                )
            }
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(skin.accent.copy(alpha = 0.10f))
                    .border(1.dp, skin.accent.copy(alpha = 0.40f), RoundedCornerShape(10.dp))
                    .clickable { onTempoClick() }
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    state.tempo.toInt().toString(),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp
                    ),
                    color = skin.accent
                )
                Text(
                    "BPM",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    color = skin.textSecondary
                )
            }
            SkinChip(skinName, onClick = onSkinClick)
            BigPlayButton(state.playing, onPlayToggle)
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                StatusRow(state, midi, onOpenMidi)
            }
        }
    }
}

@Composable
private fun Brand(modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(skin.accent.copy(alpha = 0.10f))
                .border(1.dp, skin.accent.copy(alpha = 0.45f), RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "303",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                ),
                color = skin.accent
            )
        }
        Spacer(Modifier.width(9.dp))
        Column {
            Text(
                "REPLICANT",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                ),
                color = skin.textPrimary
            )
            Text(
                "ANDROID ACID ENGINE",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, letterSpacing = 1.2.sp),
                color = skin.accent
            )
        }
    }
}

@Composable
private fun SkinChip(skinName: Skin, onClick: () -> Unit) {
    val skin = LocalSkin.current
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(skin.panelHigh)
            .border(1.dp, skin.line, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "SKIN",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
            color = skin.textMuted
        )
        Text(
            if (skinName == Skin.GREEN) "TD-3 GREEN" else "OBSIDIAN",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
            color = skin.accent,
            maxLines = 1
        )
    }
}

@Composable
private fun BigPlayButton(playing: Boolean, onClick: () -> Unit) {
    val skin = LocalSkin.current
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (playing) skin.accent else skin.accent.copy(alpha = 0.12f))
            .border(1.dp, skin.accent.copy(alpha = 0.65f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
            contentDescription = if (playing) "Stop" else "Play",
            tint = if (playing) skin.backgroundDeep else skin.accent,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun StatusRow(state: EngineUiState, midi: MidiUiState, onOpenMidi: () -> Unit) {
    val skin = LocalSkin.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusChip(
            "AUDIO",
            when {
                state.audioRunning && state.audio.sampleRate > 0 ->
                    "${state.audio.sampleRate / 1000}K/${state.audio.sharingLabel}"
                state.audioRunning -> "ON"
                else -> "SILENT"
            },
            if (state.audioRunning) skin.accent else skin.warn,
            active = state.audioRunning
        )
        StatusChip(
            "LAT",
            if (state.audio.latencyMs > 0.1) {
                String.format(Locale.US, "%.1f MS", state.audio.latencyMs)
            } else "—",
            if (state.audio.latencyMs in 0.1..15.0) skin.success else skin.warn,
            active = state.audioRunning
        )
        StatusChip(
            if (state.waitingForClock) "EXT WAIT" else if (state.clockReceive) "EXT CLK" else "INT CLK",
            null,
            if (state.externalLocked) skin.success else skin.textSecondary,
            active = state.playing || state.armed
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(skin.panelHigh)
                .border(1.dp, skin.line, RoundedCornerShape(50))
                .clickable(onClick = onOpenMidi)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Usb,
                    contentDescription = null,
                    tint = if (midi.inputConnected || midi.outputConnected) skin.accentAlt else skin.textMuted,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "USB ${if (midi.inputConnected) "IN" else "—"}/${if (midi.outputConnected) "OUT" else "—"}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    color = if (midi.inputConnected || midi.outputConnected) skin.accentAlt else skin.textMuted
                )
            }
        }
    }
}
