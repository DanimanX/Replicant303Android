package com.danimanx.replicant303.preset

import com.danimanx.replicant303.model.PatternState
import com.danimanx.replicant303.model.PresetState
import com.danimanx.replicant303.model.SynthParams
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/**
 * Portable preset files.
 *
 * A `.midi` preset is a valid Standard MIDI File (16th grid, tempo, track name)
 * that any DAW can play. A text meta event carries `R303PRESET/1:` plus the
 * complete patch as JSON, so the file round-trips every parameter. Plain MIDI
 * files without that event are imported as patterns: notes map to the 16-step
 * grid, velocity >= 100 becomes an accent and full-length notes become slides.
 */
object PresetFileIO {
    const val METADATA_PREFIX = "R303PRESET/1:"
    private const val TICKS_PER_QUARTER = 960
    private const val STEP_TICKS = TICKS_PER_QUARTER / 4

    fun suggestedFileName(preset: PresetState, extension: String = "mid"): String {
        val safe = preset.name.lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "replicant303_preset" }
        return "$safe.$extension"
    }

    // ---------------------------------------------------------------------
    // JSON
    // ---------------------------------------------------------------------

    fun toJson(preset: PresetState): String {
        val p = preset.params
        val json = JSONObject()
        json.put("format", "replicant303-preset")
        json.put("version", 1)
        json.put("name", preset.name)
        json.put("bank", preset.bank)
        json.put("slot", preset.slot)
        json.put("waveform", p.waveform)
        json.put("tuning", p.tuning.toDouble())
        json.put("cutoff", p.cutoff.toDouble())
        json.put("resonance", p.resonance.toDouble())
        json.put("envMod", p.envMod.toDouble())
        json.put("decay", p.decay.toDouble())
        json.put("accent", p.accent.toDouble())
        json.put("tempo", preset.tempo.toDouble())
        json.put("swing", preset.swing.toDouble())
        json.put("distActive", p.distActive)
        json.put("distType", p.distType)
        json.put("distDrive", p.distDrive.toDouble())
        json.put("distTone", p.distTone.toDouble())
        json.put("distMix", p.distMix.toDouble())
        json.put("length", preset.pattern.length)
        json.put("notes", JSONArray(preset.pattern.notes))
        json.put("gates", JSONArray(preset.pattern.gates.map { if (it) 1 else 0 }))
        json.put("accents", JSONArray(preset.pattern.accents.map { if (it) 1 else 0 }))
        json.put("slides", JSONArray(preset.pattern.slides.map { if (it) 1 else 0 }))
        return json.toString()
    }

    fun fromJson(text: String): PresetState? {
        return runCatching {
            val json = JSONObject(text)
            if (json.optString("format") != "replicant303-preset") return null

            val notes = intList(json.optJSONArray("notes"), 16, 36).map { it.coerceIn(24, 48) }
            val gates = intList(json.optJSONArray("gates"), 16, 1).map { it != 0 }
            val accents = intList(json.optJSONArray("accents"), 16, 0).map { it != 0 }
            val slides = intList(json.optJSONArray("slides"), 16, 0).map { it != 0 }

            PresetState(
                name = json.optString("name", "Untitled").ifBlank { "Untitled" }.take(64),
                bank = json.optInt("bank", 0).coerceIn(0, 7),
                slot = json.optInt("slot", 0).coerceIn(0, 63),
                params = SynthParams(
                    waveform = json.optInt("waveform", 0).coerceIn(0, 1),
                    tuning = json.optDouble("tuning", 0.0).toFloat().coerceIn(-12f, 12f),
                    cutoff = json.optDouble("cutoff", 350.0).toFloat().coerceIn(25f, 4500f),
                    resonance = json.optDouble("resonance", 0.65).toFloat().coerceIn(0f, 1f),
                    envMod = json.optDouble("envMod", 0.60).toFloat().coerceIn(0f, 1f),
                    decay = json.optDouble("decay", 0.75).toFloat().coerceIn(0.2f, 2.5f),
                    accent = json.optDouble("accent", 0.70).toFloat().coerceIn(0f, 1f),
                    distActive = json.optBoolean("distActive", true),
                    distType = json.optInt("distType", 0).coerceIn(0, 4),
                    distDrive = json.optDouble("distDrive", 0.5).toFloat().coerceIn(0f, 1f),
                    distTone = json.optDouble("distTone", 0.6).toFloat().coerceIn(0f, 1f),
                    distMix = json.optDouble("distMix", 0.85).toFloat().coerceIn(0f, 1f)
                ),
                pattern = PatternState(
                    notes = notes,
                    gates = gates,
                    accents = accents,
                    slides = slides,
                    length = json.optInt("length", 16).coerceIn(1, 16)
                ),
                tempo = json.optDouble("tempo", 125.0).toFloat().coerceIn(40f, 300f),
                swing = json.optDouble("swing", 0.0).toFloat().coerceIn(0f, 0.66f)
            )
        }.getOrNull()
    }

    private fun intList(array: JSONArray?, count: Int, fallback: Int): List<Int> {
        val result = MutableList(count) { fallback }
        if (array == null) return result
        for (i in 0 until minOf(count, array.length())) result[i] = array.optInt(i, fallback)
        return result
    }

    // ---------------------------------------------------------------------
    // Standard MIDI File writing
    // ---------------------------------------------------------------------

    fun writeMidi(preset: PresetState): ByteArray {
        val output = ByteArrayOutputStream()
        output.write("MThd".toByteArray(Charsets.US_ASCII))
        writeInt(output, 6)
        writeShort(output, 0)  // format 0
        writeShort(output, 1)  // one track
        writeShort(output, TICKS_PER_QUARTER)

        val track = ByteArrayOutputStream()
        var lastTick = 0

        fun event(tick: Int, bytes: ByteArray) {
            writeVlq(track, (tick - lastTick).coerceAtLeast(0))
            track.write(bytes)
            lastTick = tick
        }

        event(0, metaText(0x03, preset.name))
        event(0, metaText(0x01, METADATA_PREFIX + toJson(preset)))
        val microseconds = (60_000_000.0 / preset.tempo.coerceIn(40f, 300f)).roundToInt()
        event(0, byteArrayOf(0xFF.toByte(), 0x51, 0x03,
            ((microseconds shr 16) and 0xFF).toByte(),
            ((microseconds shr 8) and 0xFF).toByte(),
            (microseconds and 0xFF).toByte()))

        data class Note(val tick: Int, val on: Boolean, val note: Int, val velocity: Int)

        val notes = mutableListOf<Note>()
        for (step in 0 until preset.pattern.length.coerceIn(1, 16)) {
            if (!preset.pattern.gate(step)) continue
            val onTick = step * STEP_TICKS
            val length = if (preset.pattern.slide(step)) STEP_TICKS else STEP_TICKS / 2
            val velocity = if (preset.pattern.accent(step)) 110 else 80
            notes += Note(onTick, true, preset.pattern.note(step), velocity)
            notes += Note(onTick + length, false, preset.pattern.note(step), 0)
        }
        notes.sortedWith(compareBy<Note> { it.tick }.thenBy { it.on }).forEach { note ->
            val status = if (note.on) 0x90 else 0x80
            event(note.tick, byteArrayOf(status.toByte(), note.note.toByte(), note.velocity.toByte()))
        }

        event(lastTick + STEP_TICKS, byteArrayOf(0xFF.toByte(), 0x2F, 0x00))
        output.write("MTrk".toByteArray(Charsets.US_ASCII))
        writeInt(output, track.size())
        track.writeTo(output)
        return output.toByteArray()
    }

    private fun metaText(type: Int, text: String): ByteArray {
        val payload = text.toByteArray(Charsets.UTF_8)
        val out = ByteArrayOutputStream()
        out.write(0xFF)
        out.write(type)
        writeVlq(out, payload.size)
        out.write(payload)
        return out.toByteArray()
    }

    private fun writeShort(out: ByteArrayOutputStream, value: Int) {
        out.write((value shr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    private fun writeInt(out: ByteArrayOutputStream, value: Int) {
        out.write((value shr 24) and 0xFF)
        out.write((value shr 16) and 0xFF)
        out.write((value shr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    private fun writeVlq(out: ByteArrayOutputStream, value: Int) {
        var v = value.coerceAtLeast(0)
        var buffer = v and 0x7F
        v = v shr 7
        while (v > 0) {
            buffer = (buffer shl 8) or ((v and 0x7F) or 0x80)
            v = v shr 7
        }
        while (true) {
            out.write(buffer and 0xFF)
            if (buffer and 0x80 != 0) buffer = buffer shr 8 else break
        }
    }

    // ---------------------------------------------------------------------
    // Standard MIDI File reading
    // ---------------------------------------------------------------------

    fun readMidi(bytes: ByteArray): PresetState? {
        if (bytes.size < 14) return null
        if (String(bytes, 0, 4, Charsets.US_ASCII) != "MThd") return null

        val headerLength = readInt(bytes, 4)
        if (headerLength < 6) return null
        val division = readShort(bytes, 12)
        if (division <= 0) return null

        var metadata: String? = null
        var trackName: String? = null
        var tempoBpm = 125.0
        val rawNotes = mutableListOf<RawNote>()

        var position = 8 + headerLength
        while (position + 8 <= bytes.size) {
            val chunkId = String(bytes, position, 4, Charsets.US_ASCII)
            val chunkLength = readInt(bytes, position + 4)
            position += 8
            if (chunkLength < 0 || position + chunkLength > bytes.size) break
            if (chunkId == "MTrk") {
                parseTrack(bytes, position, chunkLength, division,
                    onText = { text ->
                        if (text.startsWith(METADATA_PREFIX)) {
                            metadata = text.removePrefix(METADATA_PREFIX)
                        } else if (trackName == null) {
                            trackName = text
                        }
                    },
                    onTempo = { usPerQuarter ->
                        if (usPerQuarter > 0) tempoBpm = 60_000_000.0 / usPerQuarter
                    },
                    notes = rawNotes
                )
            }
            position += chunkLength
        }

        metadata?.let { json ->
            fromJson(json)?.let { preset ->
                return preset.copy(
                    name = preset.name.ifBlank {
                        trackName ?: "Imported MIDI"
                    }
                )
            }
        }

        if (rawNotes.isEmpty()) return null

        val stepTicks = division / 4.0
        val notes = MutableList(16) { 36 }
        val gates = MutableList(16) { false }
        val accents = MutableList(16) { false }
        val slides = MutableList(16) { false }
        var lastStep = -1

        for (note in rawNotes) {
            var step = (note.onTick / stepTicks).roundToInt()
            if (step !in 0..15) continue
            var midiNote = note.note
            while (midiNote < 24) midiNote += 12
            while (midiNote > 48) midiNote -= 12
            notes[step] = midiNote.coerceIn(24, 48)
            gates[step] = true
            accents[step] = note.velocity >= 100
            slides[step] = note.offTick >= 0 &&
                (note.offTick - note.onTick) >= stepTicks * 0.75
            if (step > lastStep) lastStep = step
        }

        if (lastStep < 0) return null

        return PresetState(
            name = (trackName ?: "Imported MIDI").take(64),
            params = SynthParams(),
            pattern = PatternState(
                notes = notes,
                gates = gates,
                accents = accents,
                slides = slides,
                length = if (lastStep >= 12) 16 else (lastStep + 1).coerceAtLeast(1)
            ),
            tempo = tempoBpm.toFloat().coerceIn(40f, 300f)
        )
    }

    private data class RawNote(
        val onTick: Int,
        val note: Int,
        val velocity: Int,
        var offTick: Int = -1
    )

    private fun parseTrack(
        bytes: ByteArray,
        start: Int,
        length: Int,
        division: Int,
        onText: (String) -> Unit,
        onTempo: (Int) -> Unit,
        notes: MutableList<RawNote>
    ) {
        var position = start
        val end = (start + length).coerceAtMost(bytes.size)
        var tick = 0
        var runningStatus = 0

        while (position < end) {
            val delta = readVlq(bytes, position) ?: break
            position = delta.second
            tick += delta.first
            if (position >= end) break

            var status = bytes[position].toInt() and 0xFF
            if (status and 0x80 == 0) {
                status = runningStatus
            } else {
                position++
                if (status < 0xF0) runningStatus = status
            }
            if (status == 0) break

            when {
                status == 0xFF -> {
                    if (position >= end) break
                    val type = bytes[position].toInt() and 0xFF
                    position++
                    val metaLength = readVlq(bytes, position) ?: break
                    position = metaLength.second
                    val payloadEnd = (position + metaLength.first).coerceAtMost(end)
                    val payload = bytes.copyOfRange(position, payloadEnd)
                    position = payloadEnd

                    when (type) {
                        0x01, 0x03 -> onText(String(payload, Charsets.UTF_8))
                        0x51 -> {
                            if (payload.size >= 3) {
                                val microseconds = ((payload[0].toInt() and 0xFF) shl 16) or
                                    ((payload[1].toInt() and 0xFF) shl 8) or
                                    (payload[2].toInt() and 0xFF)
                                onTempo(microseconds)
                            }
                        }
                        0x2F -> return
                        else -> Unit
                    }
                }

                status == 0xF0 || status == 0xF7 -> {
                    val sysexLength = readVlq(bytes, position) ?: break
                    position = (sysexLength.second + sysexLength.first).coerceAtMost(end)
                }

                else -> {
                    val high = status and 0xF0
                    val dataBytes = if (high == 0xC0 || high == 0xD0) 1 else 2
                    if (position + dataBytes > end) break
                    val data1 = bytes[position].toInt() and 0xFF
                    val data2 = if (dataBytes == 2) bytes[position + 1].toInt() and 0xFF else 0
                    position += dataBytes

                    if (high == 0x90 && data2 > 0) {
                        notes += RawNote(tick, data1, data2)
                    } else if (high == 0x80 || (high == 0x90 && data2 == 0)) {
                        for (i in notes.indices.reversed()) {
                            val candidate = notes[i]
                            if (candidate.note == data1 && candidate.offTick < 0) {
                                candidate.offTick = tick
                                break
                            }
                        }
                    }
                }
            }
        }
    }

    private fun readVlq(bytes: ByteArray, start: Int): Pair<Int, Int>? {
        var value = 0
        var position = start
        for (i in 0 until 4) {
            if (position >= bytes.size) return null
            val byte = bytes[position].toInt() and 0xFF
            position++
            value = (value shl 7) or (byte and 0x7F)
            if (byte and 0x80 == 0) return value to position
        }
        return null
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int {
        if (offset + 4 > bytes.size) return -1
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
    }

    private fun readShort(bytes: ByteArray, offset: Int): Int {
        if (offset + 2 > bytes.size) return -1
        return ((bytes[offset].toInt() and 0xFF) shl 8) or
            (bytes[offset + 1].toInt() and 0xFF)
    }
}
