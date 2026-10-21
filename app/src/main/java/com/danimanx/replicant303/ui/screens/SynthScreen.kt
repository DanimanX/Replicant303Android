package com.danimanx.replicant303.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danimanx.replicant303.audio.EngineController
import com.danimanx.replicant303.model.DistortionNames
import com.danimanx.replicant303.model.DistortionShortNames
import com.danimanx.replicant303.model.EngineUiState
import com.danimanx.replicant303.model.NoteNames
import com.danimanx.replicant303.model.ParamMap
import com.danimanx.replicant303.ui.components.ActionButton
import com.danimanx.replicant303.ui.components.Keyboard303
import com.danimanx.replicant303.ui.components.MiniKnob
import com.danimanx.replicant303.ui.components.Panel
import com.danimanx.replicant303.ui.components.RotaryKnob
import com.danimanx.replicant303.ui.components.ScopeCanvas
import com.danimanx.replicant303.ui.components.SectionLabel
import com.danimanx.replicant303.ui.components.StatusChip
import com.danimanx.replicant303.ui.components.Stepper
import com.danimanx.replicant303.ui.components.TinyTag
import com.danimanx.replicant303.ui.theme.LocalSkin
import kotlin.math.roundToInt

@Composable
fun SynthScreen(
    state: EngineUiState,
    scope: List<Float>,
    engine: EngineController,
    expanded: Boolean,
    onOpenMidi: () -> Unit
) {
    val skin = LocalSkin.current
    val params = state.params
    var octaveBase by remember { mutableIntStateOf(36) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        ScopePanel(state, scope, onOpenMidi)

        if (expanded) {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    VoicePanel(state, engine)
                    AmplifierPanel(state, engine)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    DistortionPanel(state, engine)
                    KeyboardPanel(
                        state = state,
                        engine = engine,
                        octaveBase = octaveBase,
                        onOctaveChange = { octaveBase = it.coerceIn(24, 36) }
                    )
                }
            }
            PerformanceStrip(state, engine)
            SystemPanel(state)
        } else {
            VoicePanel(state, engine)
            AmplifierPanel(state, engine)
            DistortionPanel(state, engine)
            KeyboardPanel(
                state = state,
                engine = engine,
                octaveBase = octaveBase,
                onOctaveChange = { octaveBase = it.coerceIn(24, 36) }
            )
        }
    }
}

@Composable
private fun ScopePanel(state: EngineUiState, scope: List<Float>, onOpenMidi: () -> Unit) {
    val skin = LocalSkin.current
    Panel(accent = skin.accentAlt) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("SIGNAL", modifier = Modifier.weight(1f), accent = skin.accentAlt) {
                TinyTag(
                    if (state.audioRunning) state.audio.apiLabel else "SILENT CLOCK",
                    if (state.audioRunning) skin.success else skin.warn
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(skin.backgroundDeep.copy(alpha = 0.75f))
                .border(1.dp, skin.line, RoundedCornerShape(7.dp))
        ) {
            ScopeCanvas(scope, skin.accentAlt, Modifier.fillMaxSize())
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(7.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                TinyTag(NoteNames.label(state.lastNote), skin.accent)
                TinyTag(if (state.slideLed) "SLIDE" else "GATE", if (state.slideLed) skin.warn else skin.textMuted)
                TinyTag(if (state.accentLed) "ACCENT" else "—", if (state.accentLed) skin.danger else skin.textMuted)
            }
        }
        Spacer(Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(
                Modifier
                    .weight(1f)
                    .height(5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(skin.line)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(state.meter.coerceIn(0f, 1f))
                        .height(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (state.meter > 0.95f) skin.danger else skin.accent)
                )
            }
            StatusChip(
                "LAT",
                if (state.audio.latencyMs > 0.1) "${state.audio.latencyMs.roundToInt()} MS" else "—",
                if (state.audio.latencyMs in 0.1..15.0) skin.success else skin.warn,
                active = state.audioRunning
            )
            ActionButton("MIDI", accent = skin.accentAlt, height = 28.dp, onClick = onOpenMidi)
        }
    }
}

@Composable
private fun CircuitBackdrop() {
    val skin = LocalSkin.current
    Canvas(Modifier.fillMaxSize()) {
        val trace = skin.silk.copy(alpha = 0.055f)
        var y = 18.dp.toPx()
        while (y < size.height) {
            drawLine(trace, Offset(0f, y), Offset(size.width, y), 1f)
            y += 26.dp.toPx()
        }
        var x = 14.dp.toPx()
        while (x < size.width) {
            drawLine(trace.copy(alpha = 0.04f), Offset(x, 0f), Offset(x, size.height), 1f)
            x += 34.dp.toPx()
        }
        for (i in 0..5) {
            val px = 20.dp.toPx() + i * 63.dp.toPx()
            drawCircle(trace.copy(alpha = 0.11f), 3.dp.toPx(), Offset(px, 15.dp.toPx()))
        }
    }
}

@Composable
private fun VoicePanel(state: EngineUiState, engine: EngineController) {
    val skin = LocalSkin.current
    val params = state.params
    Panel(accent = skin.accent) {
        Box {
            CircuitBackdrop()
            Column {
                SectionLabel("OSCILLATOR // FILTER", accent = skin.accent) {
                    TinyTag(if (params.waveform == 0) "SAW" else "SQUARE", skin.accent)
                }
                Spacer(Modifier.height(7.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    ActionButton(
                        "SAW",
                        modifier = Modifier.weight(1f),
                        accent = skin.accent,
                        selected = params.waveform == 0,
                        height = 32.dp
                    ) { engine.setWaveform(0) }
                    ActionButton(
                        "SQUARE",
                        modifier = Modifier.weight(1f),
                        accent = skin.accent,
                        selected = params.waveform == 1,
                        height = 32.dp
                    ) { engine.setWaveform(1) }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    RotaryKnob(
                        value = ParamMap.cutoffToNorm(params.cutoff),
                        onValueChange = engine::setCutoffNormalized,
                        label = "Cutoff",
                        valueText = "${params.cutoff.roundToInt()} Hz",
                        modifier = Modifier.weight(1f)
                    )
                    RotaryKnob(
                        value = params.resonance,
                        onValueChange = { engine.setParam(com.danimanx.replicant303.model.Param.RESONANCE, it) },
                        label = "Resonance",
                        valueText = "${(params.resonance * 100).roundToInt()}%",
                        modifier = Modifier.weight(1f)
                    )
                    RotaryKnob(
                        value = params.envMod,
                        onValueChange = { engine.setParam(com.danimanx.replicant303.model.Param.ENV_MOD, it) },
                        label = "Env Mod",
                        valueText = "${(params.envMod * 100).roundToInt()}%",
                        modifier = Modifier.weight(1f)
                    )
                    RotaryKnob(
                        value = ParamMap.decayToNorm(params.decay),
                        onValueChange = engine::setDecayNormalized,
                        label = "Decay",
                        valueText = String.format("%.2fs", params.decay),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun AmplifierPanel(state: EngineUiState, engine: EngineController) {
    val skin = LocalSkin.current
    val params = state.params
    Panel(accent = skin.accentAlt) {
        SectionLabel("AMPLIFIER // ACCENT", accent = skin.accentAlt) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TinyTag("SLIDE", if (state.slideLed) skin.warn else skin.textMuted)
                TinyTag("ACC", if (state.accentLed) skin.danger else skin.textMuted)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            RotaryKnob(
                value = params.accent,
                onValueChange = { engine.setParam(com.danimanx.replicant303.model.Param.ACCENT, it) },
                label = "Accent",
                valueText = "${(params.accent * 100).roundToInt()}%",
                accent = skin.accentAlt,
                modifier = Modifier.weight(1f)
            )
            RotaryKnob(
                value = ParamMap.tuningToNorm(params.tuning),
                onValueChange = engine::setTuningNormalized,
                label = "Tuning",
                valueText = if (params.tuning == 0f) "0 st" else String.format("%+.0f st", params.tuning),
                accent = skin.accentAlt,
                defaultValue = 0.5f,
                modifier = Modifier.weight(1f)
            )
            RotaryKnob(
                value = params.volume,
                onValueChange = { engine.setParam(com.danimanx.replicant303.model.Param.VOLUME, it) },
                label = "Volume",
                valueText = "${(params.volume * 100).roundToInt()}%",
                accent = skin.accentAlt,
                modifier = Modifier.weight(1f)
            )
            Column(
                modifier = Modifier.weight(1f).padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TinyTag("MONO", skin.accentAlt)
                TinyTag("24-48", skin.textMuted)
                TinyTag("MIDI CC", skin.textMuted)
            }
        }
    }
}

@Composable
private fun DistortionPanel(state: EngineUiState, engine: EngineController) {
    val skin = LocalSkin.current
    val params = state.params
    Panel(accent = skin.danger) {
        SectionLabel("DISTORTION UNIT", accent = skin.danger) {
            ActionButton(
                if (params.distActive) "ON" else "OFF",
                accent = skin.danger,
                selected = params.distActive,
                height = 27.dp
            ) { engine.toggleDistortion() }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DistortionShortNames.forEachIndexed { index, name ->
                ActionButton(
                    name,
                    modifier = Modifier.weight(1f),
                    accent = skin.danger,
                    selected = params.distActive && params.distType == index,
                    height = 30.dp
                ) { engine.setDistortionType(index) }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            DistortionNames.getOrElse(params.distType) { "POCO RAT" },
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, letterSpacing = 0.8.sp),
            color = skin.textMuted
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            MiniKnob(
                value = params.distDrive,
                onValueChange = { engine.setParam(com.danimanx.replicant303.model.Param.DIST_DRIVE, it) },
                label = "Drive",
                valueText = "${(params.distDrive * 100).roundToInt()}%",
                accent = skin.danger,
                modifier = Modifier.weight(1f)
            )
            MiniKnob(
                value = params.distTone,
                onValueChange = { engine.setParam(com.danimanx.replicant303.model.Param.DIST_TONE, it) },
                label = "Tone",
                valueText = "${(params.distTone * 100).roundToInt()}%",
                accent = skin.danger,
                modifier = Modifier.weight(1f)
            )
            MiniKnob(
                value = params.distMix,
                onValueChange = { engine.setParam(com.danimanx.replicant303.model.Param.DIST_MIX, it) },
                label = "Mix",
                valueText = "${(params.distMix * 100).roundToInt()}%",
                accent = skin.danger,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PerformanceStrip(state: EngineUiState, engine: EngineController) {
    val skin = LocalSkin.current
    Panel(accent = skin.accent) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1.2f)) {
                Text(
                    state.presetName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = skin.textPrimary,
                    maxLines = 1
                )
                Text(
                    "PERFORMANCE STRIP // PATTERN " + com.danimanx.replicant303.model.BankLetters[state.currentPattern],
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, letterSpacing = 0.8.sp),
                    color = skin.textMuted
                )
            }
            TinyTag(if (state.slideLed) "SLIDE" else "SLIDE —", if (state.slideLed) skin.warn else skin.textMuted)
            TinyTag(if (state.accentLed) "ACCENT" else "ACCENT —", if (state.accentLed) skin.danger else skin.textMuted)
            TinyTag(NoteNames.label(state.lastNote), skin.accent)
            Spacer(Modifier.width(4.dp))
            com.danimanx.replicant303.model.BankLetters.forEachIndexed { index, letter ->
                ActionButton(
                    letter,
                    modifier = Modifier.width(36.dp),
                    accent = skin.accent,
                    selected = state.currentPattern == index,
                    height = 34.dp
                ) { engine.selectPattern(index) }
            }
        }
    }
}

@Composable
private fun SystemPanel(state: EngineUiState) {
    val skin = LocalSkin.current
    Panel(accent = skin.textSecondary) {
        SectionLabel("SYSTEM // LIVE TELEMETRY", accent = skin.textSecondary)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            SystemColumn(
                "ENGINE",
                listOf(
                    state.audio.apiLabel,
                    "${state.audio.sampleRate / 1000} KHZ",
                    "BUF ${state.audio.bufferFrames}",
                    "${state.audio.sharingLabel}"
                ),
                Modifier.weight(1f)
            )
            SystemColumn(
                "TIMING",
                listOf(
                    if (state.audio.latencyMs > 0.1) "${state.audio.latencyMs.roundToInt()} MS" else "—",
                    "CPU ${(state.audio.cpuLoad * 100).roundToInt()}%",
                    "XRUN ${state.audio.xRuns}",
                    "${state.tempo.roundToInt()} BPM"
                ),
                Modifier.weight(1f)
            )
            SystemColumn(
                "MIDI",
                listOf(
                    if (state.midiOut) "OUT ON" else "OUT OFF",
                    if (state.clockSend) "CLOCK OUT" else "CLOCK OFF",
                    if (state.clockReceive) "CLOCK IN" else "INT CLOCK",
                    "CH ${if (state.midiChannel < 0) "OMNI" else (state.midiChannel + 1).toString()}"
                ),
                Modifier.weight(1f)
            )
            SystemColumn(
                "PATCH",
                listOf(
                    "BANK ${com.danimanx.replicant303.model.BankLetters[state.presetBank]}",
                    "SLOT ${(state.presetSlot + 1).toString().padStart(2, '0')}",
                    "PATTERN ${com.danimanx.replicant303.model.BankLetters[state.currentPattern]}",
                    "${state.length} STEPS"
                ),
                Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "REPLICANT 303 // ACID PROTOCOL",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            ),
            color = skin.silk.copy(alpha = 0.35f)
        )
    }
}

@Composable
private fun SystemColumn(title: String, values: List<String>, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    Column(modifier) {
        Text(
            title,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp, letterSpacing = 1.sp),
            color = skin.accent
        )
        Spacer(Modifier.height(3.dp))
        values.forEach {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                color = skin.textSecondary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun KeyboardPanel(
    state: EngineUiState,
    engine: EngineController,
    octaveBase: Int,
    onOctaveChange: (Int) -> Unit
) {
    val skin = LocalSkin.current
    Panel(accent = skin.accent) {
        SectionLabel("KEYBOARD", accent = skin.accent) {
            Stepper(
                value = "C${octaveBase / 12 - 1}",
                label = "Octave",
                accent = skin.accent,
                onMinus = { onOctaveChange(octaveBase - 12) },
                onPlus = { onOctaveChange(octaveBase + 12) }
            )
        }
        Spacer(Modifier.height(5.dp))
        Keyboard303(
            baseNote = octaveBase,
            onNoteOn = { note, velocity -> engine.noteOn(note, velocity) },
            onNoteOff = { note -> engine.noteOff(note) }
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "13-KEY TACTILE // VELOCITY BY POSITION",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                color = skin.textMuted
            )
            Text(
                NoteNames.label(state.lastNote),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                color = skin.accent
            )
        }
    }
}
