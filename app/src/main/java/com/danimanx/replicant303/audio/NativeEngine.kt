package com.danimanx.replicant303.audio

/**
 * Thin JNI surface for the Replicant 303 engine. Every call is cheap and
 * non-blocking; the audio callback owns the DSP state and the UI thread only
 * exchanges atomics, seqlock snapshots and lock-free queues.
 */
object NativeEngine {
    init {
        System.loadLibrary("replicant303_engine")
    }

    external fun nativeCreate(): Long
    external fun nativeDestroy(handle: Long)
    external fun nativeVersion(): String

    external fun nativeStartAudio(handle: Long): Boolean
    external fun nativeStopAudio(handle: Long)
    external fun nativeIsAudioRunning(handle: Long): Boolean
    external fun nativeRenderSilent(handle: Long, frames: Int)
    external fun nativeGetAudioStats(handle: Long, floats: FloatArray, ints: IntArray)

    external fun nativeGetParams(handle: Long, out: FloatArray)
    external fun nativeGetStatus(handle: Long, out: IntArray)
    external fun nativeGetScope(handle: Long, out: FloatArray)
    external fun nativeGetMeter(handle: Long): Float

    external fun nativeSetParam(handle: Long, id: Int, value: Float)
    external fun nativeSetPlaying(handle: Long, playing: Boolean)
    external fun nativeTogglePlaying(handle: Long)
    external fun nativeIsPlaying(handle: Long): Boolean
    external fun nativeSetTempo(handle: Long, bpm: Double)
    external fun nativeSetSwing(handle: Long, value: Float)
    external fun nativeSetPatternLength(handle: Long, length: Int)
    external fun nativeSetCurrentPattern(handle: Long, pattern: Int)

    external fun nativeSetStep(
        handle: Long,
        patternIndex: Int,
        stepIndex: Int,
        gate: Boolean,
        note: Int,
        accent: Boolean,
        slide: Boolean
    )

    external fun nativeClearPattern(handle: Long, patternIndex: Int)
    external fun nativeInitPattern(handle: Long, patternIndex: Int)
    external fun nativeCopyPattern(handle: Long, from: Int, to: Int)
    external fun nativeGetPatternRow(handle: Long, patternIndex: Int, out: IntArray)
    external fun nativeGetPatternLength(handle: Long, patternIndex: Int): Int

    external fun nativeNoteOn(handle: Long, note: Int, velocity: Int)
    external fun nativeNoteOff(handle: Long, note: Int)
    external fun nativeAllNotesOff(handle: Long)

    external fun nativeLoadFactoryPreset(handle: Long, bank: Int, slot: Int)
    external fun nativeGeneratePreset(handle: Long, bank: Int, slot: Int, seed: Int)
    external fun nativeGetPreset(handle: Long, out: IntArray)
    external fun nativeApplyPreset(handle: Long, values: IntArray, name: String): Boolean
    external fun nativeCurrentPresetName(handle: Long): String
    external fun nativeFactoryPresetName(handle: Long, bank: Int, slot: Int): String

    external fun nativePushMidi(handle: Long, status: Int, data1: Int, data2: Int, timestampNanos: Long)
    external fun nativePollMidiOut(handle: Long, out: ByteArray): Int
    external fun nativeSetMidiOutEnabled(handle: Long, enabled: Boolean)
    external fun nativeSetClockSend(handle: Long, enabled: Boolean)
    external fun nativeSetClockReceive(handle: Long, enabled: Boolean)
    external fun nativeSetTransportSend(handle: Long, enabled: Boolean)
    external fun nativeSetMidiChannel(handle: Long, channel: Int)
}
