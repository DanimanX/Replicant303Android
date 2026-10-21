package com.danimanx.replicant303.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danimanx.replicant303.audio.EngineController
import com.danimanx.replicant303.model.BankLetters
import com.danimanx.replicant303.model.EngineUiState
import com.danimanx.replicant303.model.NoteNames
import com.danimanx.replicant303.model.PatternState
import com.danimanx.replicant303.ui.components.ActionButton
import com.danimanx.replicant303.ui.components.LedDot
import com.danimanx.replicant303.ui.components.MiniKnob
import com.danimanx.replicant303.ui.components.Panel
import com.danimanx.replicant303.ui.components.SectionLabel
import com.danimanx.replicant303.ui.components.StatusChip
import com.danimanx.replicant303.ui.components.Stepper
import com.danimanx.replicant303.ui.components.TinyTag
import com.danimanx.replicant303.ui.theme.LocalSkin
import kotlin.math.abs

@Composable
fun SequenceScreen(
    state: EngineUiState,
    engine: EngineController,
    expanded: Boolean,
    onOpenVault: () -> Unit,
    onTempoClick: () -> Unit
) {
    val skin = LocalSkin.current
    var selectedStep by remember { mutableIntStateOf(0) }
    val pattern = state.pattern

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        TransportPanel(state, engine, onTempoClick)

        Panel(accent = skin.accentAlt) {
            SectionLabel("PATTERN BANK", accent = skin.accentAlt) {
                TinyTag("${state.pattern.length} STEPS", skin.textMuted)
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                BankLetters.forEachIndexed { index, letter ->
                    val active = state.currentPattern == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(if (active) skin.accentAlt else skin.panelHigh)
                            .border(1.dp, if (active) skin.accentAlt else skin.line, RoundedCornerShape(7.dp))
                            .pointerInput(index) {
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    down.consume()
                                    engine.selectPattern(index)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                letter,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (active) skin.backgroundDeep else skin.textPrimary
                            )
                            LedDot(
                                active = active && state.playing,
                                accent = if (active) skin.backgroundDeep else skin.accent,
                                size = 4.dp
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                ActionButton(
                    "CLEAR",
                    modifier = Modifier.weight(1f),
                    accent = skin.warn,
                    height = 34.dp
                ) { engine.clearPattern() }
                ActionButton(
                    "INIT",
                    modifier = Modifier.weight(1f),
                    accent = skin.accentAlt,
                    height = 34.dp
                ) { engine.initPattern() }
                ActionButton(
                    "COPY → ${BankLetters[(state.currentPattern + 1) % 8]}",
                    modifier = Modifier.weight(1.4f),
                    accent = skin.accentAlt,
                    height = 34.dp
                ) { engine.copyPatternToNext() }
                ActionButton(
                    "VAULT",
                    modifier = Modifier.weight(1f),
                    accent = skin.accent,
                    height = 34.dp,
                    onClick = onOpenVault
                )
            }
        }

        Panel(accent = skin.accent) {
            SectionLabel("STEP EDITOR // ${BankLetters[state.currentPattern]}", accent = skin.accent) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TinyTag(if (state.playing) "RUN ${state.currentStep + 1}" else "STOPPED", if (state.playing) skin.success else skin.textMuted)
                    TinyTag("${pattern.length} / 16", skin.textMuted)
                }
            }
            Spacer(Modifier.height(5.dp))
            Text(
                "TAP NOTE CELL = GATE · DRAG UP/DOWN = PITCH",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp, letterSpacing = 0.6.sp),
                color = skin.textMuted
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                for (step in 0 until 16) {
                    StepColumn(
                        step = step,
                        pattern = pattern,
                        selected = selectedStep == step,
                        playhead = state.playing && state.currentStep == step,
                        enabled = step < pattern.length,
                        onSelect = { selectedStep = step },
                        onToggleGate = {
                            selectedStep = step
                            engine.toggleGate(step)
                        },
                        onAdjustNote = { delta ->
                            selectedStep = step
                            engine.adjustNote(step, delta)
                        },
                        onToggleAccent = {
                            selectedStep = step
                            engine.toggleAccent(step)
                        },
                        onToggleSlide = {
                            selectedStep = step
                            engine.toggleSlide(step)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        StepInspector(
            step = selectedStep,
            pattern = pattern,
            engine = engine,
            modifier = Modifier.fillMaxWidth()
        )

        if (expanded) {
            Panel(accent = skin.accentAlt) {
                SectionLabel("PERFORMANCE NOTES", accent = skin.accentAlt)
                Spacer(Modifier.height(5.dp))
                Text(
                    "Accent steps fire at velocity 110 and open the filter harder. " +
                        "Slide steps hold through the next step, producing the classic 303 glide. " +
                        "Swing lengthens every on-beat 16th and shortens the off-beat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = skin.textSecondary
                )
            }
        }
    }
}

@Composable
private fun TransportPanel(
    state: EngineUiState,
    engine: EngineController,
    onTempoClick: () -> Unit
) {
    val skin = LocalSkin.current
    Panel(accent = skin.accent) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(
                if (state.playing) "STOP" else "PLAY",
                modifier = Modifier.height(48.dp),
                accent = skin.accent,
                selected = state.playing,
                height = 48.dp
            ) { engine.togglePlaying() }
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(skin.panelHigh)
                    .border(1.dp, skin.line, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text(
                    state.tempo.toInt().toString(),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = skin.accent
                )
                Text("BPM", style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp), color = skin.textMuted)
            }
            Spacer(Modifier.weight(1f))
            StatusChip(
                if (state.clockReceive) "EXT" else "INT",
                if (state.waitingForClock) "WAIT" else null,
                if (state.externalLocked) skin.success else skin.textSecondary,
                active = state.playing || state.armed
            )
        }
        Spacer(Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniKnob(
                value = state.swing / 0.66f,
                onValueChange = { engine.setSwing(it * 0.66f) },
                label = "Swing",
                valueText = "${(state.swing * 100).toInt()}%",
                accent = skin.accentAlt,
                defaultValue = 0f
            )
            Stepper(
                value = state.length.toString(),
                label = "Steps",
                accent = skin.accent,
                onMinus = { engine.setLength(state.length - 1) },
                onPlus = { engine.setLength(state.length + 1) }
            )
            Spacer(Modifier.weight(1f))
            ActionButton("TEMPO", modifier = Modifier.weight(1f), accent = skin.accentAlt, height = 34.dp, onClick = onTempoClick)
        }
    }
}

@Composable
private fun StepColumn(
    step: Int,
    pattern: PatternState,
    selected: Boolean,
    playhead: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    onToggleGate: () -> Unit,
    onAdjustNote: (Int) -> Unit,
    onToggleAccent: () -> Unit,
    onToggleSlide: () -> Unit,
    modifier: Modifier = Modifier
) {
    val skin = LocalSkin.current
    val patternState = rememberUpdatedState(pattern)
    val toggleGate = rememberUpdatedState(onToggleGate)
    val adjustNote = rememberUpdatedState(onAdjustNote)
    val select = rememberUpdatedState(onSelect)

    val gate = pattern.gate(step)
    val accent = pattern.accent(step)
    val slide = pattern.slide(step)
    val note = pattern.note(step)

    val cellColor = when {
        playhead -> skin.accent.copy(alpha = 0.92f)
        selected && gate -> skin.accentAlt.copy(alpha = 0.20f)
        gate -> skin.accent.copy(alpha = 0.10f)
        else -> skin.panelHigh.copy(alpha = 0.65f)
    }
    val noteColor = when {
        playhead -> skin.backgroundDeep
        gate -> skin.textPrimary
        else -> skin.textMuted
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            (step + 1).toString().padStart(2, '0'),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
            color = if (playhead) skin.accent else if (enabled) skin.textMuted else skin.textMuted.copy(alpha = 0.35f)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(if (enabled) cellColor else skin.panelHigh.copy(alpha = 0.25f))
                .border(
                    1.dp,
                    if (selected) skin.accent else if (playhead) skin.accent else skin.line,
                    RoundedCornerShape(5.dp)
                )
                .pointerInput(step) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        select.value()
                        var accumulated = 0f
                        var emitted = 0
                        var dragged = false
                        val threshold = 16.dp.toPx()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            accumulated += change.position.y - down.position.y
                            if (abs(accumulated) > threshold) dragged = true
                            if (dragged) {
                                val semitones = (-accumulated / threshold).toInt()
                                if (semitones != emitted) {
                                    adjustNote.value(semitones - emitted)
                                    emitted = semitones
                                }
                            }
                            change.consume()
                        }
                        if (!dragged) toggleGate.value()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                NoteNames.label(note),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                color = if (enabled) noteColor else skin.textMuted.copy(alpha = 0.4f),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
        StepToggle("A", enabled && accent, skin.danger, onToggleAccent)
        StepToggle("S", enabled && slide, skin.warn, onToggleSlide)
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(50))
                .background(if (gate && enabled) skin.accent else skin.line.copy(alpha = 0.5f))
        )
    }
}

@Composable
private fun StepToggle(
    letter: String,
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    val skin = LocalSkin.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (active) activeColor.copy(alpha = 0.20f) else skin.panelHigh.copy(alpha = 0.6f))
            .border(1.dp, if (active) activeColor else skin.line.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
            .pointerInput(letter) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    onClick()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            letter,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
            color = if (active) activeColor else skin.textMuted
        )
    }
}

@Composable
private fun StepInspector(
    step: Int,
    pattern: PatternState,
    engine: EngineController,
    modifier: Modifier = Modifier
) {
    val skin = LocalSkin.current
    Panel(modifier = modifier, accent = skin.accentAlt) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(
                    "STEP ${(step + 1).toString().padStart(2, '0')}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, letterSpacing = 1.sp),
                    color = skin.textMuted
                )
                Text(
                    NoteNames.label(pattern.note(step)),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = skin.accent
                )
            }
            TinyTag(if (pattern.gate(step)) "GATE ON" else "GATE OFF", if (pattern.gate(step)) skin.success else skin.textMuted)
            TinyTag(if (pattern.accent(step)) "ACCENT" else "ACC —", if (pattern.accent(step)) skin.danger else skin.textMuted)
            TinyTag(if (pattern.slide(step)) "SLIDE" else "SLD —", if (pattern.slide(step)) skin.warn else skin.textMuted)
        }
        Spacer(Modifier.height(7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            ActionButton("− SEMI", Modifier.weight(1f), skin.accentAlt, height = 32.dp) {
                engine.adjustNote(step, -1)
            }
            ActionButton("+ SEMI", Modifier.weight(1f), skin.accentAlt, height = 32.dp) {
                engine.adjustNote(step, 1)
            }
            ActionButton("− OCT", Modifier.weight(1f), skin.accentAlt, height = 32.dp) {
                engine.adjustNote(step, -12)
            }
            ActionButton("+ OCT", Modifier.weight(1f), skin.accentAlt, height = 32.dp) {
                engine.adjustNote(step, 12)
            }
            ActionButton(
                if (pattern.gate(step)) "MUTE" else "GATE",
                Modifier.weight(1f),
                skin.accent,
                height = 32.dp
            ) { engine.toggleGate(step) }
        }
    }
}
