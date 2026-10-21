package com.danimanx.replicant303.preset

import com.danimanx.replicant303.model.PatternState
import com.danimanx.replicant303.model.PresetState
import com.danimanx.replicant303.model.SynthParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PresetFileIOTest {

    private fun samplePreset(): PresetState {
        val notes = listOf(36, 36, 48, 36, 39, 36, 43, 36, 36, 48, 36, 39, 36, 43, 36, 36)
        val gates = MutableList(16) { true }.also { it[3] = false; it[11] = false }
        val accents = MutableList(16) { false }.also { it[1] = true; it[6] = true; it[10] = true }
        val slides = MutableList(16) { false }.also { it[4] = true; it[8] = true }
        return PresetState(
            name = "Amber Line",
            bank = 0,
            slot = 0,
            params = SynthParams(
                waveform = 0,
                tuning = 0f,
                cutoff = 350f,
                resonance = 0.65f,
                envMod = 0.6f,
                decay = 0.75f,
                accent = 0.7f,
                distActive = true,
                distType = 1,
                distDrive = 0.42f,
                distTone = 0.61f,
                distMix = 0.85f
            ),
            pattern = PatternState(notes, gates, accents, slides, 16),
            tempo = 132f,
            swing = 0.12f
        )
    }

    @Test
    fun jsonRoundTripCarriesTheWholePatch() {
        val original = samplePreset()
        val decoded = PresetFileIO.fromJson(PresetFileIO.toJson(original))
        assertNotNull(decoded)
        decoded!!
        assertEquals(original.name, decoded.name)
        assertEquals(original.pattern, decoded.pattern)
        assertEquals(original.params.waveform, decoded.params.waveform)
        assertEquals(original.params.distActive, decoded.params.distActive)
        assertEquals(original.params.distType, decoded.params.distType)
        assertTrue(abs(original.params.cutoff - decoded.params.cutoff) < 0.01f)
        assertTrue(abs(original.params.resonance - decoded.params.resonance) < 0.001f)
        assertTrue(abs(original.tempo - decoded.tempo) < 0.01f)
        assertTrue(abs(original.swing - decoded.swing) < 0.001f)
    }

    @Test
    fun midiExportIsAStandardMidiFileAndRoundTrips() {
        val original = samplePreset()
        val bytes = PresetFileIO.writeMidi(original)
        assertEquals("MThd", String(bytes, 0, 4, Charsets.US_ASCII))
        val trackIndex = String(bytes, 0, bytes.size, Charsets.ISO_8859_1).indexOf("MTrk")
        assertTrue(trackIndex > 0)

        val decoded = PresetFileIO.readMidi(bytes)
        assertNotNull(decoded)
        decoded!!
        assertEquals(original.name, decoded.name)
        assertEquals(original.pattern, decoded.pattern)
        assertEquals(original.params.distType, decoded.params.distType)
        assertTrue(abs(original.tempo - decoded.tempo) < 0.01f)
        assertTrue(abs(original.params.cutoff - decoded.params.cutoff) < 0.01f)
    }

    @Test
    fun plainMidiWithoutMetadataImportsAsAPattern() {
        val division = 960
        val track = byteArrayOf(
            0x00, 0xFF.toByte(), 0x51, 0x03, 0x07, 0xA1.toByte(), 0x20, // 120 BPM
            0x00, 0x90.toByte(), 36, 110,                                // step 0, accent
            0x00, 0x80.toByte(), 36, 0,
            0x83.toByte(), 0x60, 0x90.toByte(), 38, 80,                  // step 2
            0x81.toByte(), 0x70, 0x80.toByte(), 38, 0,
            0x00, 0xFF.toByte(), 0x2F, 0x00
        )
        val header = byteArrayOf(
            'M'.code.toByte(), 'T'.code.toByte(), 'h'.code.toByte(), 'd'.code.toByte(),
            0, 0, 0, 6, 0, 0, 0, 1,
            ((division shr 8) and 0xFF).toByte(), (division and 0xFF).toByte()
        )
        val trackHeader = byteArrayOf(
            'M'.code.toByte(), 'T'.code.toByte(), 'r'.code.toByte(), 'k'.code.toByte(),
            ((track.size shr 24) and 0xFF).toByte(),
            ((track.size shr 16) and 0xFF).toByte(),
            ((track.size shr 8) and 0xFF).toByte(),
            (track.size and 0xFF).toByte()
        )
        val bytes = header + trackHeader + track

        val preset = PresetFileIO.readMidi(bytes)
        assertNotNull(preset)
        preset!!
        assertEquals(120f, preset.tempo, 0.01f)
        assertTrue(preset.pattern.gate(0))
        assertTrue(preset.pattern.accent(0))
        assertTrue(preset.pattern.gate(2))
        assertEquals(36, preset.pattern.note(0))
        assertEquals(38, preset.pattern.note(2))
        assertEquals(3, preset.pattern.length)
    }

    @Test
    fun suggestedFileNameIsSafe() {
        val preset = samplePreset().copy(name = "Acid Rain / 09")
        assertEquals("acid_rain_09.mid", PresetFileIO.suggestedFileName(preset))
    }
}
