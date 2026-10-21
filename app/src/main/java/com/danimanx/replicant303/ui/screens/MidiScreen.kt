package com.danimanx.replicant303.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danimanx.replicant303.audio.EngineController
import com.danimanx.replicant303.midi.MidiController
import com.danimanx.replicant303.model.EngineUiState
import com.danimanx.replicant303.model.MidiDeviceEntry
import com.danimanx.replicant303.model.MidiDirection
import com.danimanx.replicant303.model.MidiLogEntry
import com.danimanx.replicant303.model.MidiUiState
import com.danimanx.replicant303.ui.components.ActionButton
import com.danimanx.replicant303.ui.components.Panel
import com.danimanx.replicant303.ui.components.SectionLabel
import com.danimanx.replicant303.ui.components.StatusChip
import com.danimanx.replicant303.ui.components.TinyTag
import com.danimanx.replicant303.ui.components.ToggleChip
import com.danimanx.replicant303.ui.theme.LocalSkin
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MidiScreen(
    state: EngineUiState,
    midi: MidiUiState,
    engine: EngineController,
    controller: MidiController
) {
    val skin = LocalSkin.current
    var showChannelMenu by remember { mutableStateOf(false) }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }

    LaunchedEffect(Unit) { controller.refreshDevices() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        item {
            Panel(accent = skin.accentAlt) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(skin.accentAlt.copy(alpha = 0.10f))
                            .border(1.dp, skin.accentAlt.copy(alpha = 0.45f), RoundedCornerShape(9.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Usb, contentDescription = null, tint = skin.accentAlt)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "USB MIDI",
                            style = MaterialTheme.typography.titleMedium,
                            color = skin.textPrimary
                        )
                        Text(
                            if (midi.available) "MIDI 1.0 byte stream + MIDI 2.0 UMP" else "MIDI service unavailable",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                            color = skin.textMuted
                        )
                    }
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "Refresh MIDI devices",
                        tint = skin.accentAlt,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { controller.refreshDevices() }
                    )
                }
                Spacer(Modifier.height(7.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    StatusChip("IN", if (midi.inputConnected) "CONNECTED" else "IDLE", skin.accentAlt, midi.inputConnected)
                    StatusChip("OUT", if (midi.outputConnected) "CONNECTED" else "IDLE", skin.accent, midi.outputConnected)
                    StatusChip("CLK", when {
                        state.clockReceive && state.externalLocked -> "LOCKED"
                        state.clockReceive -> "WAIT"
                        else -> "INTERNAL"
                    }, if (state.externalLocked) skin.success else skin.textSecondary, state.clockReceive)
                    if (Build.VERSION.SDK_INT >= 33) {
                        StatusChip("UMP", if (midi.useUmp) "ON" else "OFF", skin.accentAlt, midi.useUmp)
                    }
                }
            }
        }

        if (!midi.error.isNullOrBlank()) {
            item {
                Panel(accent = skin.danger) {
                    Text(midi.error ?: "", style = MaterialTheme.typography.bodySmall, color = skin.danger)
                }
            }
        }

        item {
            Panel(accent = skin.accentAlt) {
                SectionLabel("MIDI IN // FROM CONTROLLER", accent = skin.accentAlt)
                Spacer(Modifier.height(6.dp))
                if (midi.inputConnected) {
                    ConnectedRow(
                        name = midi.inputDeviceName.ifBlank { "MIDI input" },
                        detail = "NOTES · CC · CLOCK · TRANSPORT",
                        accent = skin.success
                    ) { controller.disconnectInput() }
                }
                val inputs = midi.devices.filter { it.inputPorts > 0 }
                if (inputs.isEmpty()) {
                    Text(
                        "Connect a class-compliant USB MIDI controller or interface. " +
                            "Android's MIDI service owns the USB permission prompt.",
                        style = MaterialTheme.typography.bodySmall,
                        color = skin.textMuted,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    inputs.forEach { device ->
                        DeviceRow(
                            device = device,
                            accent = skin.accentAlt,
                            connected = device.isInputConnected,
                            portLabel = "${device.inputPorts} OUT",
                            onConnect = { controller.connectInput(device.id) },
                            onDisconnect = { controller.disconnectInput() }
                        )
                    }
                }
            }
        }

        item {
            Panel(accent = skin.accent) {
                SectionLabel("MIDI OUT // TO DEVICE", accent = skin.accent) {
                    TinyTag(if (midi.outputConnected) "ROUTED" else "IDLE", if (midi.outputConnected) skin.success else skin.textMuted)
                }
                Spacer(Modifier.height(6.dp))
                if (midi.outputConnected) {
                    ConnectedRow(
                        name = midi.outputDeviceName.ifBlank { "MIDI output" },
                        detail = "SEQUENCER · CLOCK · TRANSPORT",
                        accent = skin.success
                    ) { controller.disconnectOutput() }
                }
                val outputs = midi.devices.filter { it.outputPorts > 0 }
                if (outputs.isEmpty()) {
                    Text(
                        "Connect a USB MIDI synth, drum machine or interface to send notes, " +
                            "24 PPQN clock and start/stop.",
                        style = MaterialTheme.typography.bodySmall,
                        color = skin.textMuted,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    outputs.forEach { device ->
                        DeviceRow(
                            device = device,
                            accent = skin.accent,
                            connected = device.isOutputConnected,
                            portLabel = "${device.outputPorts} IN",
                            onConnect = { controller.connectOutput(device.id) },
                            onDisconnect = { controller.disconnectOutput() }
                        )
                    }
                }
            }
        }

        item {
            Panel(accent = skin.accentAlt) {
                SectionLabel("SYNC & ROUTING", accent = skin.accentAlt)
                Spacer(Modifier.height(4.dp))
                SettingSwitch(
                    title = "MIDI IN",
                    detail = "Receive notes, pitch bend, filter CCs, clock and transport",
                    checked = midi.midiInEnabled,
                    accent = skin.accentAlt
                ) { controller.setMidiInEnabled(it) }
                SettingSwitch(
                    title = "MIDI OUT",
                    detail = "Send the sequencer line to downstream gear",
                    checked = midi.midiOutEnabled,
                    accent = skin.accentAlt
                ) { controller.setMidiOutEnabled(it) }
                SettingSwitch(
                    title = "CLOCK OUT // 24 PPQN",
                    detail = "Clock and start/stop follow the transport",
                    checked = midi.clockSend,
                    accent = skin.accentAlt
                ) { controller.setClockSend(it) }
                SettingSwitch(
                    title = "CLOCK IN // EXTERNAL SYNC",
                    detail = "Step from incoming 24 PPQN clock; Play waits for the first pulse",
                    checked = midi.clockReceive,
                    accent = skin.accentAlt
                ) { controller.setClockReceive(it) }
                SettingSwitch(
                    title = "TRANSPORT OUT",
                    detail = "Send start/stop when the performance transport changes",
                    checked = midi.transportSend,
                    accent = skin.accentAlt
                ) { controller.setTransportSend(it) }

                Spacer(Modifier.height(7.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("MIDI CHANNEL FILTER", style = MaterialTheme.typography.titleSmall, color = skin.textPrimary)
                        Text(
                            if (midi.channelFilter < 0) "OMNI // all channels" else "CH ${midi.channelFilter + 1} only",
                            style = MaterialTheme.typography.labelSmall,
                            color = skin.textMuted
                        )
                    }
                    Box {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(skin.panelHigh)
                                .border(1.dp, skin.line, RoundedCornerShape(7.dp))
                                .clickable { showChannelMenu = true }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (midi.channelFilter < 0) "OMNI" else "${midi.channelFilter + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = skin.accent
                            )
                            Spacer(Modifier.width(5.dp))
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = skin.accent, modifier = Modifier.size(16.dp))
                        }
                        DropdownMenu(
                            expanded = showChannelMenu,
                            onDismissRequest = { showChannelMenu = false },
                            containerColor = skin.panelHigh
                        ) {
                            DropdownMenuItem(
                                text = { Text("OMNI", color = skin.textPrimary) },
                                onClick = {
                                    controller.setChannelFilter(-1)
                                    showChannelMenu = false
                                }
                            )
                            (1..16).forEach { ch ->
                                DropdownMenuItem(
                                    text = { Text("CH $ch", color = skin.textPrimary) },
                                    onClick = {
                                        controller.setChannelFilter(ch - 1)
                                        showChannelMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(7.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("MIDI 2.0 // UMP", style = MaterialTheme.typography.titleSmall, color = skin.textPrimary)
                        Text(
                            when {
                                !midi.umpSupported -> "Requires Android 13 (API 33) or newer"
                                midi.useUmp -> "UMP translation active for UMP-capable devices"
                                else -> "Opt in to Universal MIDI Packet translation"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = skin.textMuted
                        )
                    }
                    ToggleChip(
                        text = if (midi.useUmp) "UMP ON" else "UMP OFF",
                        selected = midi.useUmp,
                        accent = skin.accentAlt,
                        enabled = midi.umpSupported
                    ) { controller.setUseUmp(!midi.useUmp) }
                }
                Spacer(Modifier.height(7.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    TinyTag("CC74 CUTOFF", skin.textMuted)
                    TinyTag("CC71 RES", skin.textMuted)
                    TinyTag("CC72 DECAY", skin.textMuted)
                    TinyTag("CC76-79 DIST", skin.textMuted)
                }
            }
        }

        item {
            Panel(accent = skin.textMuted) {
                SectionLabel("MIDI MONITOR", accent = skin.textMuted) {
                    ActionButton("CLEAR", height = 27.dp, accent = skin.textMuted) { controller.clearLog() }
                }
                Spacer(Modifier.height(5.dp))
                if (midi.log.isEmpty()) {
                    Text(
                        "Incoming and outgoing events appear here. Clock is throttled to one entry per second.",
                        style = MaterialTheme.typography.bodySmall,
                        color = skin.textMuted
                    )
                } else {
                    midi.log.takeLast(18).reversed().forEach { entry ->
                        MidiLogRow(entry, timeFormat)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: MidiDeviceEntry,
    accent: androidx.compose.ui.graphics.Color,
    connected: Boolean,
    portLabel: String,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    val skin = LocalSkin.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(skin.panelHigh.copy(alpha = 0.7f))
            .border(1.dp, if (connected) skin.success else skin.line, RoundedCornerShape(7.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                device.name,
                style = MaterialTheme.typography.titleSmall,
                color = skin.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${device.typeLabel} · $portLabel · ${device.manufacturer.ifBlank { "MIDI" }}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                color = skin.textMuted,
                maxLines = 1
            )
        }
        if (device.isUmp) TinyTag("UMP", skin.accentAlt)
        Spacer(Modifier.width(5.dp))
        ActionButton(
            if (connected) "DISCONNECT" else "CONNECT",
            accent = if (connected) skin.warn else accent,
            selected = connected,
            height = 30.dp
        ) { if (connected) onDisconnect() else onConnect() }
    }
}

@Composable
private fun ConnectedRow(
    name: String,
    detail: String,
    accent: androidx.compose.ui.graphics.Color,
    onDisconnect: () -> Unit
) {
    val skin = LocalSkin.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(accent.copy(alpha = 0.08f))
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(7.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall, color = skin.textPrimary)
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                color = accent
            )
        }
        ActionButton("DISCONNECT", accent = skin.warn, height = 30.dp, onClick = onDisconnect)
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    detail: String,
    checked: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    onChange: (Boolean) -> Unit
) {
    val skin = LocalSkin.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = skin.textPrimary)
            Text(detail, style = MaterialTheme.typography.labelSmall, color = skin.textMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = skin.backgroundDeep,
                checkedTrackColor = accent,
                uncheckedThumbColor = skin.textMuted,
                uncheckedTrackColor = skin.panelHigh
            )
        )
    }
}

@Composable
private fun MidiLogRow(entry: MidiLogEntry, timeFormat: SimpleDateFormat) {
    val skin = LocalSkin.current
    val color = when (entry.direction) {
        MidiDirection.IN -> skin.accentAlt
        MidiDirection.OUT -> skin.accent
        MidiDirection.SYSTEM -> skin.textMuted
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            when (entry.direction) {
                MidiDirection.IN -> "IN"
                MidiDirection.OUT -> "OUT"
                MidiDirection.SYSTEM -> "SYS"
            },
            modifier = Modifier.width(28.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
            color = color
        )
        Text(
            entry.bytes,
            modifier = Modifier.width(74.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
            color = skin.textSecondary,
            maxLines = 1
        )
        Text(
            entry.description,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
            color = skin.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            timeFormat.format(Date(entry.timestampMs)),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
            color = skin.textMuted
        )
    }
}
