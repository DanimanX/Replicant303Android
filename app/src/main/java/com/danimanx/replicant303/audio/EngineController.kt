package com.danimanx.replicant303.audio

import android.content.Context
import com.danimanx.replicant303.model.AudioStats
import com.danimanx.replicant303.model.EngineUiState
import com.danimanx.replicant303.model.Param
import com.danimanx.replicant303.model.ParamMap
import com.danimanx.replicant303.model.PatternState
import com.danimanx.replicant303.model.PresetState
import com.danimanx.replicant303.model.PresetWire
import com.danimanx.replicant303.model.StatusIndex
import com.danimanx.replicant303.model.SynthParams
import com.danimanx.replicant303.preset.PresetFileIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Owns the native engine handle, the low-latency audio stream and the polling
 * loop that turns native atomics into immutable UI state.
 */
class EngineController(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("engine", Context.MODE_PRIVATE)
    private val workScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val handle: Long = NativeEngine.nativeCreate()

    private val _state = kotlinx.coroutines.flow.MutableStateFlow(EngineUiState())
    val state: kotlinx.coroutines.flow.StateFlow<EngineUiState> = _state

    private val _scope = kotlinx.coroutines.flow.MutableStateFlow(List(128) { 0f })
    val scope: kotlinx.coroutines.flow.StateFlow<List<Float>> = _scope

    private val params = FloatArray(Param.COUNT)
    private val status = IntArray(StatusIndex.COUNT)
    private val patternRow = IntArray(16)
    private val scopeBuffer = FloatArray(128)
    private val statsFloats = FloatArray(2)
    private val statsInts = IntArray(6)
    private val presetWire = IntArray(PresetWire.SIZE)

    private val overrides = LinkedHashMap<Int, PresetState>()
    private val nameCache = HashMap<Int, String>()

    @Volatile private var selectedBank = 0
    @Volatile private var selectedSlot = 0
    @Volatile private var started = false
    @Volatile private var bootstrapped = false

    private var pollJob: Job? = null
    private var silentJob: Job? = null

    init {
        loadOverrides()
    }

    fun start() {
        synchronized(this) {
            if (started) return
            started = true
        }
        pollJob = workScope.launch { pollLoop() }
        workScope.launch(Dispatchers.IO) {
            restoreSession()
            startAudioInternal()
            refreshPattern()
            bootstrapped = true
        }
    }

    fun ensureAudio(): Boolean = startAudioInternal()

    private fun startAudioInternal(): Boolean {
        val running = runCatching { NativeEngine.nativeStartAudio(handle) }.getOrDefault(false)
        if (running) {
            stopSilentTicker()
        } else {
            startSilentTicker()
            _state.value = _state.value.copy(
                engineError = "No low-latency audio device available. The sequencer is running in silent clock mode."
            )
        }
        return running
    }

    private fun startSilentTicker() {
        if (silentJob?.isActive == true) return
        silentJob = workScope.launch {
            while (isActive) {
                NativeEngine.nativeRenderSilent(handle, 512)
                delay(10)
            }
        }
    }

    private fun stopSilentTicker() {
        silentJob?.cancel()
        silentJob = null
    }

    fun stopAudio() {
        stopSilentTicker()
        NativeEngine.nativeStopAudio(handle)
    }

    fun destroy() {
        pollJob?.cancel()
        silentJob?.cancel()
        NativeEngine.nativeStopAudio(handle)
        NativeEngine.nativeDestroy(handle)
        workScope.cancel()
    }

    private suspend fun pollLoop() {
        while (workScope.isActive) {
            try {
                val newState = readState()
                if (newState != _state.value) _state.value = newState
                _scope.value = scopeBuffer.toList()
                if (newState.audioRunning) stopSilentTicker() else startSilentTicker()
            } catch (_: Throwable) {
                // Never let a transient JNI issue kill the UI loop.
            }
            delay(33)
        }
    }

    private fun readState(): EngineUiState {
        NativeEngine.nativeGetParams(handle, params)
        NativeEngine.nativeGetStatus(handle, status)
        NativeEngine.nativeGetScope(handle, scopeBuffer)
        NativeEngine.nativeGetAudioStats(handle, statsFloats, statsInts)
        NativeEngine.nativeGetPatternRow(handle, status[StatusIndex.CURRENT_PATTERN], patternRow)

        val pattern = PatternState(
            notes = (0 until 16).map { patternRow[it] shr 3 and 0x3F },
            gates = (0 until 16).map { patternRow[it] and 1 != 0 },
            accents = (0 until 16).map { patternRow[it] and 2 != 0 },
            slides = (0 until 16).map { patternRow[it] and 4 != 0 },
            length = status[StatusIndex.LENGTH].coerceIn(1, 16)
        )

        val synth = SynthParams(
            waveform = params[Param.WAVEFORM].toInt().coerceIn(0, 1),
            tuning = params[Param.TUNING],
            cutoff = params[Param.CUTOFF],
            resonance = params[Param.RESONANCE],
            envMod = params[Param.ENV_MOD],
            decay = params[Param.DECAY],
            accent = params[Param.ACCENT],
            volume = params[Param.VOLUME],
            distActive = params[Param.DIST_ACTIVE] > 0.5f,
            distType = params[Param.DIST_TYPE].toInt().coerceIn(0, 4),
            distDrive = params[Param.DIST_DRIVE],
            distTone = params[Param.DIST_TONE],
            distMix = params[Param.DIST_MIX]
        )

        val audio = AudioStats(
            latencyMs = statsFloats[0].toDouble(),
            cpuLoad = statsFloats[1],
            xRuns = statsInts[0],
            sampleRate = statsInts[1],
            framesPerBurst = statsInts[2],
            bufferFrames = statsInts[3],
            api = statsInts[4],
            sharing = statsInts[5]
        )

        val audioRunning = NativeEngine.nativeIsAudioRunning(handle)
        val staleError = _state.value.engineError
        return EngineUiState(
            ready = bootstrapped,
            audioRunning = audioRunning,
            silentFallback = !audioRunning,
            engineError = if (audioRunning && staleError?.startsWith("No low-latency") == true) {
                null
            } else {
                staleError
            },
            playing = status[StatusIndex.PLAYING] != 0,
            currentStep = status[StatusIndex.CURRENT_STEP].coerceIn(0, 15),
            currentPattern = status[StatusIndex.CURRENT_PATTERN].coerceIn(0, 7),
            tempo = params[Param.TEMPO],
            swing = params[Param.SWING],
            presetName = NativeEngine.nativeCurrentPresetName(handle),
            presetBank = selectedBank,
            presetSlot = selectedSlot,
            length = status[StatusIndex.LENGTH].coerceIn(1, 16),
            params = synth,
            pattern = pattern,
            midiOut = status[StatusIndex.MIDI_OUT] != 0,
            midiChannel = status[StatusIndex.MIDI_CHANNEL],
            clockSend = status[StatusIndex.CLOCK_SEND] != 0,
            clockReceive = status[StatusIndex.CLOCK_RECEIVE] != 0,
            externalLocked = status[StatusIndex.EXTERNAL_LOCKED] != 0,
            waitingForClock = status[StatusIndex.WAITING_FOR_CLOCK] != 0,
            armed = status[StatusIndex.ARMED] != 0,
            slideLed = status[StatusIndex.SLIDE] != 0,
            accentLed = status[StatusIndex.ACCENT] != 0,
            lastNote = status[StatusIndex.LAST_NOTE],
            meter = NativeEngine.nativeGetMeter(handle),
            audio = audio
        )
    }

    private fun refreshPattern() {
        NativeEngine.nativeGetStatus(handle, status)
        NativeEngine.nativeGetPatternRow(handle, status[StatusIndex.CURRENT_PATTERN], patternRow)
    }

    // ---------------------------------------------------------------------
    // Synth parameters
    // ---------------------------------------------------------------------

    fun setParam(id: Int, value: Float) = NativeEngine.nativeSetParam(handle, id, value)

    fun setCutoffNormalized(value: Float) =
        setParam(Param.CUTOFF, ParamMap.normToCutoff(value))

    fun setDecayNormalized(value: Float) =
        setParam(Param.DECAY, ParamMap.normToDecay(value))

    fun setTuningNormalized(value: Float) =
        setParam(Param.TUNING, ParamMap.normToTuning(value))

    fun setWaveform(waveform: Int) = setParam(Param.WAVEFORM, waveform.toFloat())

    fun setDistortionActive(active: Boolean) =
        setParam(Param.DIST_ACTIVE, if (active) 1f else 0f)

    fun setDistortionType(type: Int) =
        setParam(Param.DIST_TYPE, type.coerceIn(0, 4).toFloat())

    fun toggleDistortion() =
        setDistortionActive(!_state.value.params.distActive)

    fun setTempo(bpm: Float) = NativeEngine.nativeSetTempo(handle, bpm.coerceIn(40f, 300f).toDouble())

    fun setSwing(value: Float) = NativeEngine.nativeSetSwing(handle, value.coerceIn(0f, 0.66f))

    fun setLength(length: Int) {
        NativeEngine.nativeSetPatternLength(handle, length.coerceIn(1, 16))
        _state.value = _state.value.copy(
            length = length.coerceIn(1, 16),
            pattern = _state.value.pattern.copy(length = length.coerceIn(1, 16))
        )
    }

    // ---------------------------------------------------------------------
    // Transport and pattern editing
    // ---------------------------------------------------------------------

    fun setPlaying(playing: Boolean) = NativeEngine.nativeSetPlaying(handle, playing)
    fun togglePlaying() {
        if (_state.value.playing) NativeEngine.nativeSetPlaying(handle, false)
        else NativeEngine.nativeSetPlaying(handle, true)
    }

    fun selectPattern(index: Int) {
        val safe = index.coerceIn(0, 7)
        NativeEngine.nativeSetCurrentPattern(handle, safe)
        refreshPattern()
        _state.value = _state.value.copy(currentPattern = safe)
    }

    fun setStep(step: Int, note: Int, gate: Boolean, accent: Boolean, slide: Boolean) {
        val patternIndex = _state.value.currentPattern
        NativeEngine.nativeSetStep(
            handle, patternIndex, step.coerceIn(0, 15),
            gate, note.coerceIn(24, 48), accent, slide
        )
        val current = _state.value.pattern
        val notes = current.notes.toMutableList()
        val gates = current.gates.toMutableList()
        val accents = current.accents.toMutableList()
        val slides = current.slides.toMutableList()
        notes[step] = note.coerceIn(24, 48)
        gates[step] = gate
        accents[step] = accent
        slides[step] = slide
        _state.value = _state.value.copy(
            pattern = current.copy(notes = notes, gates = gates, accents = accents, slides = slides)
        )
    }

    fun toggleGate(step: Int) {
        val pattern = _state.value.pattern
        setStep(step, pattern.note(step), !pattern.gate(step), pattern.accent(step), pattern.slide(step))
    }

    fun adjustNote(step: Int, delta: Int) {
        val pattern = _state.value.pattern
        val note = (pattern.note(step) + delta).coerceIn(24, 48)
        setStep(step, note, true, pattern.accent(step), pattern.slide(step))
    }

    fun toggleAccent(step: Int) {
        val pattern = _state.value.pattern
        setStep(step, pattern.note(step), pattern.gate(step), !pattern.accent(step), pattern.slide(step))
    }

    fun toggleSlide(step: Int) {
        val pattern = _state.value.pattern
        setStep(step, pattern.note(step), pattern.gate(step), pattern.accent(step), !pattern.slide(step))
    }

    fun clearPattern() {
        NativeEngine.nativeClearPattern(handle, _state.value.currentPattern)
        refreshPattern()
    }

    fun initPattern() {
        NativeEngine.nativeInitPattern(handle, _state.value.currentPattern)
        refreshPattern()
    }

    fun copyPatternToNext() {
        val from = _state.value.currentPattern
        val to = (from + 1) % 8
        NativeEngine.nativeCopyPattern(handle, from, to)
        selectPattern(to)
    }

    fun noteOn(note: Int, velocity: Int = 100) =
        NativeEngine.nativeNoteOn(handle, note.coerceIn(0, 127), velocity.coerceIn(1, 127))

    fun noteOff(note: Int) = NativeEngine.nativeNoteOff(handle, note.coerceIn(0, 127))

    // ---------------------------------------------------------------------
    // Preset vault
    // ---------------------------------------------------------------------

    fun isOverridden(bank: Int, slot: Int): Boolean = overrides.containsKey(key(bank, slot))

    fun presetNameFor(bank: Int, slot: Int): String {
        overrides[key(bank, slot)]?.let { return it.name }
        val cacheKey = key(bank, slot)
        return nameCache.getOrPut(cacheKey) {
            NativeEngine.nativeFactoryPresetName(handle, bank, slot)
        }
    }

    fun loadFactoryPreset(bank: Int, slot: Int) {
        val b = bank.coerceIn(0, 7)
        val s = slot.coerceIn(0, 63)
        selectedBank = b
        selectedSlot = s
        val override = overrides[key(b, s)]
        if (override != null) {
            NativeEngine.nativeSetCurrentPattern(handle, b)
            applyPresetState(override.copy(bank = b, slot = s), applyPatternSlot = b)
        } else {
            NativeEngine.nativeLoadFactoryPreset(handle, b, s)
        }
        _state.value = _state.value.copy(
            presetBank = b,
            presetSlot = s,
            presetName = presetNameFor(b, s),
            currentPattern = b
        )
        saveSession()
    }

    fun generatePreset(bank: Int, slot: Int, seed: Int) {
        val b = bank.coerceIn(0, 7)
        val s = slot.coerceIn(0, 63)
        selectedBank = b
        selectedSlot = s
        NativeEngine.nativeGeneratePreset(handle, b, s, seed)
        val generated = capturePreset().copy(bank = b, slot = s, userStored = true)
        overrides[key(b, s)] = generated
        saveOverrides()
        _state.value = _state.value.copy(
            presetBank = b,
            presetSlot = s,
            presetName = generated.name,
            currentPattern = b
        )
        saveSession()
    }

    fun storeCurrentPreset(bank: Int, slot: Int) {
        val b = bank.coerceIn(0, 7)
        val s = slot.coerceIn(0, 63)
        val captured = capturePreset().copy(
            bank = b,
            slot = s,
            name = presetNameFor(b, s),
            userStored = true
        )
        overrides[key(b, s)] = captured
        saveOverrides()
        selectedBank = b
        selectedSlot = s
        _state.value = _state.value.copy(
            presetBank = b,
            presetSlot = s,
            presetName = captured.name
        )
        saveSession()
    }

    fun capturePreset(): PresetState {
        NativeEngine.nativeGetPreset(handle, presetWire)
        val name = NativeEngine.nativeCurrentPresetName(handle)
        return PresetWire.decode(presetWire, name, selectedBank, selectedSlot)
    }

    fun applyPresetState(
        preset: PresetState,
        applyPatternSlot: Int? = null,
        persist: Boolean = true
    ) {
        val slot = applyPatternSlot ?: preset.bank.coerceIn(0, 7)
        NativeEngine.nativeSetCurrentPattern(handle, slot)
        NativeEngine.nativeApplyPreset(handle, PresetWire.encode(preset), preset.name)
        selectedBank = preset.bank.coerceIn(0, 7)
        selectedSlot = preset.slot.coerceIn(0, 63)
        _state.value = _state.value.copy(
            presetBank = selectedBank,
            presetSlot = selectedSlot,
            presetName = preset.name,
            currentPattern = slot
        )
        if (persist) saveSession()
    }

    fun importPreset(preset: PresetState) {
        applyPresetState(preset, applyPatternSlot = preset.bank.coerceIn(0, 7))
        saveSession()
    }

    // ---------------------------------------------------------------------
    // Session persistence
    // ---------------------------------------------------------------------

    fun saveSession() {
        runCatching {
            val preset = capturePreset()
            prefs.edit()
                .putString(KEY_SESSION, PresetFileIO.toJson(preset))
                .putInt(KEY_SESSION_BANK, selectedBank)
                .putInt(KEY_SESSION_SLOT, selectedSlot)
                .putInt(KEY_SESSION_PATTERN, _state.value.currentPattern)
                .apply()
        }
    }

    private fun restoreSession() {
        val saved = prefs.getString(KEY_SESSION, null)
        val preset = saved?.let { PresetFileIO.fromJson(it) }
        if (preset != null) {
            selectedBank = prefs.getInt(KEY_SESSION_BANK, preset.bank).coerceIn(0, 7)
            selectedSlot = prefs.getInt(KEY_SESSION_SLOT, preset.slot).coerceIn(0, 63)
            val patternSlot = prefs.getInt(KEY_SESSION_PATTERN, selectedBank).coerceIn(0, 7)
            applyPresetState(
                preset.copy(bank = selectedBank, slot = selectedSlot),
                patternSlot,
                persist = false
            )
        } else {
            loadFactoryPreset(0, 0)
            setTempo(128f)
        }
    }

    private fun loadOverrides() {
        val raw = prefs.getString(KEY_OVERRIDES, null) ?: return
        runCatching {
            val json = JSONObject(raw)
            for (key in json.keys()) {
                val preset = PresetFileIO.fromJson(json.getString(key)) ?: continue
                val parts = key.split(':')
                if (parts.size != 2) continue
                val bank = parts[0].toIntOrNull() ?: continue
                val slot = parts[1].toIntOrNull() ?: continue
                overrides[key(bank, slot)] = preset.copy(bank = bank, slot = slot, userStored = true)
            }
        }
    }

    private fun saveOverrides() {
        runCatching {
            val json = JSONObject()
            for ((key, preset) in overrides) {
                json.put(keyText(key), PresetFileIO.toJson(preset))
            }
            prefs.edit().putString(KEY_OVERRIDES, json.toString()).apply()
        }
    }

    private fun key(bank: Int, slot: Int): Int =
        bank.coerceIn(0, 7) * 64 + slot.coerceIn(0, 63)

    private fun keyText(value: Int): String = "${value / 64}:${value % 64}"

    // ---------------------------------------------------------------------
    // MIDI plumbing
    // ---------------------------------------------------------------------

    fun pushMidi(status: Int, data1: Int, data2: Int, timestampNanos: Long) =
        NativeEngine.nativePushMidi(handle, status, data1, data2, timestampNanos)

    fun pollMidiOut(maxRecords: Int = 128): List<MidiOutRecord> {
        if (maxRecords <= 0) return emptyList()
        val bytes = ByteArray(maxRecords * 16)
        val count = NativeEngine.nativePollMidiOut(handle, bytes)
        if (count <= 0) return emptyList()
        return (0 until count).map { i ->
            val base = i * 16
            var timestamp = 0L
            for (b in 0 until 8) {
                timestamp = timestamp or ((bytes[base + 8 + b].toLong() and 0xFF) shl (8 * b))
            }
            MidiOutRecord(
                status = bytes[base].toInt() and 0xFF,
                data1 = bytes[base + 1].toInt() and 0xFF,
                data2 = bytes[base + 2].toInt() and 0xFF,
                timestampNanos = timestamp
            )
        }
    }

    fun setMidiOutEnabled(enabled: Boolean) = NativeEngine.nativeSetMidiOutEnabled(handle, enabled)
    fun setClockSend(enabled: Boolean) = NativeEngine.nativeSetClockSend(handle, enabled)
    fun setClockReceive(enabled: Boolean) = NativeEngine.nativeSetClockReceive(handle, enabled)
    fun setTransportSend(enabled: Boolean) = NativeEngine.nativeSetTransportSend(handle, enabled)
    fun setMidiChannel(channel: Int) = NativeEngine.nativeSetMidiChannel(handle, channel)

    fun bpmRounded(): Int = _state.value.tempo.roundToInt()

    companion object {
        private const val KEY_OVERRIDES = "vault_overrides"
        private const val KEY_SESSION = "session_preset"
        private const val KEY_SESSION_BANK = "session_bank"
        private const val KEY_SESSION_SLOT = "session_slot"
        private const val KEY_SESSION_PATTERN = "session_pattern"
    }
}

data class MidiOutRecord(
    val status: Int,
    val data1: Int,
    val data2: Int,
    val timestampNanos: Long
)

object EngineHolder {
    @Volatile
    private var controller: EngineController? = null

    @Volatile
    private var midi: com.danimanx.replicant303.midi.MidiController? = null

    fun get(context: Context): EngineController {
        val existing = controller
        if (existing != null) return existing
        return synchronized(this) {
            controller ?: EngineController(context.applicationContext).also {
                controller = it
                it.start()
            }
        }
    }

    fun getMidi(context: Context): com.danimanx.replicant303.midi.MidiController {
        val existing = midi
        if (existing != null) return existing
        return synchronized(this) {
            midi ?: com.danimanx.replicant303.midi.MidiController(
                context.applicationContext,
                get(context)
            ).also {
                midi = it
                it.start()
            }
        }
    }

    fun stopEngine() {
        midi?.disconnectAll()
        controller?.saveSession()
        controller?.stopAudio()
    }

    fun saveSession() {
        controller?.saveSession()
    }

    fun peek(): EngineController? = controller
}
