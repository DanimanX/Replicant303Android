package com.danimanx.replicant303.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PresetWireTest {

    @Test
    fun wireRoundTripPreservesPatternAndVoice() {
        val notes = listOf(24, 36, 48, 31, 43, 36, 39, 24, 48, 36, 31, 43, 36, 39, 24, 48)
        val gates = MutableList(16) { true }.also { it[6] = false }
        val accents = MutableList(16) { false }.also { it[0] = true; it[8] = true }
        val slides = MutableList(16) { false }.also { it[1] = true; it[9] = true }
        val preset = PresetState(
            name = "Wire Test",
            params = SynthParams(
                waveform = 1,
                tuning = -12f,
                cutoff = 1234f,
                resonance = 0.94f,
                envMod = 0.71f,
                decay = 1.25f,
                accent = 0.88f,
                distActive = true,
                distType = 4,
                distDrive = 0.77f,
                distTone = 0.33f,
                distMix = 0.91f
            ),
            pattern = PatternState(notes, gates, accents, slides, 12),
            tempo = 137.5f,
            swing = 0.18f
        )

        val decoded = PresetWire.decode(PresetWire.encode(preset), preset.name, 0, 0)

        assertEquals(preset.pattern.notes, decoded.pattern.notes)
        assertEquals(preset.pattern.gates, decoded.pattern.gates)
        assertEquals(preset.pattern.accents, decoded.pattern.accents)
        assertEquals(preset.pattern.slides, decoded.pattern.slides)
        assertEquals(12, decoded.pattern.length)
        assertEquals(1, decoded.params.waveform)
        assertEquals(4, decoded.params.distType)
        assertTrue(decoded.params.distActive)
        assertEquals(-12f, decoded.params.tuning, 0.01f)
        assertEquals(1234f, decoded.params.cutoff, 0.11f)
        assertEquals(0.94f, decoded.params.resonance, 0.0011f)
        assertEquals(1.25f, decoded.params.decay, 0.0011f)
        assertEquals(137.5f, decoded.tempo, 0.11f)
        assertEquals(0.18f, decoded.swing, 0.0011f)
    }

    @Test
    fun parameterMapsRoundTrip() {
        assertEquals(350f, ParamMap.normToCutoff(ParamMap.cutoffToNorm(350f)), 0.2f)
        assertEquals(4500f, ParamMap.normToCutoff(1f), 0.5f)
        assertEquals(25f, ParamMap.normToCutoff(0f), 0.1f)
        assertEquals(0.75f, ParamMap.normToDecay(ParamMap.decayToNorm(0.75f)), 0.001f)
        assertEquals(0f, ParamMap.normToTuning(ParamMap.tuningToNorm(0f)), 0.001f)
        assertEquals(12f, ParamMap.normToTuning(1f), 0.001f)
        assertEquals(-12f, ParamMap.normToTuning(0f), 0.001f)
    }

    @Test
    fun noteLabelsUseMidiOctaves() {
        assertEquals("C1", NoteNames.label(24))
        assertEquals("C2", NoteNames.label(36))
        assertEquals("C3", NoteNames.label(48))
        assertEquals("A#2", NoteNames.label(46))
    }
}
