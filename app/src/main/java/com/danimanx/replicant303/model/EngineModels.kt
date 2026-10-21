package com.danimanx.replicant303.model

import androidx.compose.runtime.Immutable
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/** Parameter ids shared with the native engine. */
object Param {
    const val WAVEFORM = 0
    const val TUNING = 1
    const val CUTOFF = 2
    const val RESONANCE = 3
    const val ENV_MOD = 4
    const val DECAY = 5
    const val ACCENT = 6
    const val VOLUME = 7
    const val DIST_ACTIVE = 8
    const val DIST_TYPE = 9
    const val DIST_DRIVE = 10
    const val DIST_TONE = 11
    const val DIST_MIX = 12
    const val TEMPO = 13
    const val SWING = 14
    const val COUNT = 15
}

object StatusIndex {
    const val PLAYING = 0
    const val CURRENT_STEP = 1
    const val CURRENT_PATTERN = 2
    const val MIDI_OUT = 3
    const val CLOCK_SEND = 4
    const val CLOCK_RECEIVE = 5
    const val EXTERNAL_LOCKED = 6
    const val MIDI_CHANNEL = 7
    const val LAST_NOTE = 8
    const val WAITING_FOR_CLOCK = 9
    const val ARMED = 10
    const val SLIDE = 11
    const val ACCENT = 12
    const val PRESET_BANK = 13
    const val PRESET_SLOT = 14
    const val LENGTH = 15
    const val COUNT = 16
}

val DistortionNames = listOf("POCO RAT", "TUBE DRIVE", "HARD CLIP", "GERMANIUM", "WAVEFOLDER")
val DistortionShortNames = listOf("RAT", "TUBE", "HARD", "FUZZ", "FOLD")
val BankLetters = listOf("A", "B", "C", "D", "E", "F", "G", "H")
val BankNames = listOf("AMBER", "SUB", "NEON", "ACID", "BLADE", "ROOFTOP", "OFF WORLD", "LAST")

@Immutable
data class SynthParams(
    val waveform: Int = 0,
    val tuning: Float = 0f,
    val cutoff: Float = 350f,
    val resonance: Float = 0.65f,
    val envMod: Float = 0.60f,
    val decay: Float = 0.75f,
    val accent: Float = 0.70f,
    val volume: Float = 0.80f,
    val distActive: Boolean = true,
    val distType: Int = 0,
    val distDrive: Float = 0.50f,
    val distTone: Float = 0.60f,
    val distMix: Float = 0.85f
)

@Immutable
data class PatternState(
    val notes: List<Int> = List(16) { 36 },
    val gates: List<Boolean> = List(16) { true },
    val accents: List<Boolean> = List(16) { false },
    val slides: List<Boolean> = List(16) { false },
    val length: Int = 16
) {
    fun note(step: Int): Int = notes.getOrElse(step) { 36 }
    fun gate(step: Int): Boolean = gates.getOrElse(step) { false }
    fun accent(step: Int): Boolean = accents.getOrElse(step) { false }
    fun slide(step: Int): Boolean = slides.getOrElse(step) { false }
}

@Immutable
data class PresetState(
    val name: String = "Amber Line",
    val bank: Int = 0,
    val slot: Int = 0,
    val params: SynthParams = SynthParams(),
    val pattern: PatternState = PatternState(),
    val tempo: Float = 125f,
    val swing: Float = 0f,
    val userStored: Boolean = false
)

@Immutable
data class AudioStats(
    val latencyMs: Double = 0.0,
    val cpuLoad: Float = 0.0f,
    val xRuns: Int = 0,
    val sampleRate: Int = 0,
    val framesPerBurst: Int = 0,
    val bufferFrames: Int = 0,
    val api: Int = 0,
    val sharing: Int = 0
) {
    val apiLabel: String
        get() = when (api) {
            1 -> "AAUDIO"
            2 -> "OPENSL ES"
            else -> "STARTING"
        }

    val sharingLabel: String
        get() = when (sharing) {
            1 -> "EXCLUSIVE"
            2 -> "SHARED"
            else -> "—"
        }

    val bufferMs: Double
        get() = if (sampleRate > 0) bufferFrames * 1000.0 / sampleRate else 0.0
}

@Immutable
data class EngineUiState(
    val ready: Boolean = false,
    val audioRunning: Boolean = false,
    val silentFallback: Boolean = false,
    val engineError: String? = null,
    val playing: Boolean = false,
    val currentStep: Int = 0,
    val currentPattern: Int = 0,
    val tempo: Float = 125f,
    val swing: Float = 0f,
    val presetName: String = "Amber Line",
    val presetBank: Int = 0,
    val presetSlot: Int = 0,
    val length: Int = 16,
    val params: SynthParams = SynthParams(),
    val pattern: PatternState = PatternState(),
    val midiOut: Boolean = true,
    val midiChannel: Int = -1,
    val clockSend: Boolean = true,
    val clockReceive: Boolean = false,
    val externalLocked: Boolean = false,
    val waitingForClock: Boolean = false,
    val armed: Boolean = false,
    val slideLed: Boolean = false,
    val accentLed: Boolean = false,
    val lastNote: Int = 36,
    val meter: Float = 0f,
    val audio: AudioStats = AudioStats()
)

enum class MidiDirection { IN, OUT, SYSTEM }

@Immutable
data class MidiLogEntry(
    val id: Long,
    val direction: MidiDirection,
    val bytes: String,
    val description: String,
    val timestampMs: Long
)

@Immutable
data class MidiDeviceEntry(
    val id: Int,
    val name: String,
    val manufacturer: String,
    val typeLabel: String,
    val inputPorts: Int,
    val outputPorts: Int,
    val isUmp: Boolean,
    val isInputConnected: Boolean,
    val isOutputConnected: Boolean
)

@Immutable
data class MidiUiState(
    val available: Boolean = true,
    val umpSupported: Boolean = false,
    val scanning: Boolean = false,
    val devices: List<MidiDeviceEntry> = emptyList(),
    val inputDeviceId: Int? = null,
    val outputDeviceId: Int? = null,
    val inputDeviceName: String = "",
    val outputDeviceName: String = "",
    val inputConnected: Boolean = false,
    val outputConnected: Boolean = false,
    val midiInEnabled: Boolean = true,
    val midiOutEnabled: Boolean = true,
    val clockSend: Boolean = true,
    val clockReceive: Boolean = false,
    val transportSend: Boolean = true,
    val channelFilter: Int = -1,
    val useUmp: Boolean = false,
    val error: String? = null,
    val log: List<MidiLogEntry> = emptyList(),
    val lastRxMs: Long = 0L
)

object NoteNames {
    private val names = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    fun label(midiNote: Int): String {
        val note = midiNote.coerceIn(0, 127)
        return "${names[note % 12]}${note / 12 - 1}"
    }
}

// UI knobs are normalised 0..1; these helpers map them to the physical ranges
// the engine expects.
object ParamMap {
    fun cutoffToNorm(hz: Float): Float =
        (ln(hz.coerceIn(25f, 4500f) / 25.0) / ln(180.0)).toFloat().coerceIn(0f, 1f)

    fun normToCutoff(value: Float): Float =
        (25.0 * 180.0.pow(value.coerceIn(0f, 1f).toDouble())).toFloat().coerceIn(25f, 4500f)

    fun decayToNorm(seconds: Float): Float = ((seconds - 0.20f) / 2.30f).coerceIn(0f, 1f)
    fun normToDecay(value: Float): Float = (0.20f + value.coerceIn(0f, 1f) * 2.30f)

    fun tuningToNorm(semitones: Float): Float = ((semitones + 12f) / 24f).coerceIn(0f, 1f)
    fun normToTuning(value: Float): Float = (value.coerceIn(0f, 1f) * 24f - 12f)
}

object PresetWire {
    const val SIZE = 80

    fun encode(preset: PresetState): IntArray {
        val out = IntArray(SIZE)
        val p = preset.params
        out[0] = p.waveform
        out[1] = Math.round(p.tuning * 100f)
        out[2] = Math.round(p.cutoff * 10f)
        out[3] = Math.round(p.resonance * 1000f)
        out[4] = Math.round(p.envMod * 1000f)
        out[5] = Math.round(p.decay * 1000f)
        out[6] = Math.round(p.accent * 1000f)
        out[7] = Math.round(preset.tempo * 10f)
        out[8] = Math.round(preset.swing * 1000f)
        out[9] = if (p.distActive) 1 else 0
        out[10] = p.distType
        out[11] = Math.round(p.distDrive * 1000f)
        out[12] = Math.round(p.distTone * 1000f)
        out[13] = Math.round(p.distMix * 1000f)
        out[14] = preset.pattern.length
        for (i in 0 until 16) {
            out[15 + i] = preset.pattern.note(i)
            out[31 + i] = if (preset.pattern.gate(i)) 1 else 0
            out[47 + i] = if (preset.pattern.accent(i)) 1 else 0
            out[63 + i] = if (preset.pattern.slide(i)) 1 else 0
        }
        return out
    }

    fun decode(values: IntArray, name: String, bank: Int, slot: Int): PresetState {
        require(values.size >= SIZE) { "preset wire must contain $SIZE values" }
        val notes = (0 until 16).map { values[15 + it].coerceIn(24, 48) }
        val gates = (0 until 16).map { values[31 + it] != 0 }
        val accents = (0 until 16).map { values[47 + it] != 0 }
        val slides = (0 until 16).map { values[63 + it] != 0 }
        val length = values[14].coerceIn(1, 16)
        return PresetState(
            name = name.ifBlank { "Untitled" },
            bank = bank,
            slot = slot,
            params = SynthParams(
                waveform = values[0].coerceIn(0, 1),
                tuning = (values[1] / 100f).coerceIn(-12f, 12f),
                cutoff = (values[2] / 10f).coerceIn(25f, 4500f),
                resonance = (values[3] / 1000f).coerceIn(0f, 1f),
                envMod = (values[4] / 1000f).coerceIn(0f, 1f),
                decay = (values[5] / 1000f).coerceIn(0.2f, 2.5f),
                accent = (values[6] / 1000f).coerceIn(0f, 1f),
                distActive = values[9] != 0,
                distType = values[10].coerceIn(0, 4),
                distDrive = (values[11] / 1000f).coerceIn(0f, 1f),
                distTone = (values[12] / 1000f).coerceIn(0f, 1f),
                distMix = (values[13] / 1000f).coerceIn(0f, 1f)
            ),
            pattern = PatternState(notes, gates, accents, slides, length),
            tempo = (values[7] / 10f).coerceIn(40f, 300f),
            swing = (values[8] / 1000f).coerceIn(0f, 0.66f)
        )
    }
}
