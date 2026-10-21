#pragma once

#include "distortion.h"
#include "envelope.h"
#include "filter.h"
#include "oscillator.h"
#include "pattern.h"
#include "preset.h"
#include "sequencer.h"

#include <array>
#include <atomic>
#include <cstdint>
#include <mutex>
#include <string>
#include <vector>

namespace r303 {

enum ParamId : int {
    kParamWaveform = 0,
    kParamTuning = 1,
    kParamCutoff = 2,
    kParamResonance = 3,
    kParamEnvMod = 4,
    kParamDecay = 5,
    kParamAccent = 6,
    kParamVolume = 7,
    kParamDistActive = 8,
    kParamDistType = 9,
    kParamDistDrive = 10,
    kParamDistTone = 11,
    kParamDistMix = 12,
    kParamTempo = 13,
    kParamSwing = 14,
    kParamCount = 15,
};

enum StatusIndex : int {
    kStatusPlaying = 0,
    kStatusCurrentStep = 1,
    kStatusCurrentPattern = 2,
    kStatusMidiOut = 3,
    kStatusClockSend = 4,
    kStatusClockReceive = 5,
    kStatusExternalLocked = 6,
    kStatusMidiChannel = 7,
    kStatusLastNote = 8,
    kStatusWaitingForClock = 9,
    kStatusArmed = 10,
    kStatusSlide = 11,
    kStatusAccent = 12,
    kStatusPresetBank = 13,
    kStatusPresetSlot = 14,
    kStatusLength = 15,
    kStatusCount = 16,
};

// Monophonic 303 voice with a note stack, capacitor slide and the five-model
// distortion unit. Owned by the audio thread only.
class Synth303 {
public:
    void prepare(double sampleRate) noexcept;
    void reset() noexcept;

    void setWaveform(int wave) noexcept;
    void setTuning(float semitones) noexcept;
    void setCutoff(float hz) noexcept;
    void setResonance(float resonance01) noexcept;
    void setEnvMod(float envMod01) noexcept;
    void setDecay(float seconds) noexcept;
    void setAccent(float accent01) noexcept;
    void setVolume(float linear) noexcept;
    void setDistortion(bool active, int type, float drive, float tone, float mix) noexcept;
    void setPitchBend(float semitones) noexcept;

    void noteOn(int noteNumber, int velocity) noexcept;
    void noteOff(int noteNumber) noexcept;

    float process() noexcept;

    bool slideActive() const noexcept { return slideLedActive_; }
    bool accentActive() const noexcept { return accentLedActive_; }
    int lastNote() const noexcept { return lastNoteNumber_; }
    int heldNotes() const noexcept { return static_cast<int>(noteStack_.size()); }

private:
    struct ActiveNote {
        int note = -1;
        int velocity = 0;
    };

    void updateSlideCoefficient() noexcept;

    double sampleRate_ = 48000.0;

    Oscillator303 osc_;
    DiodeLadderFilter filter_;
    Envelope303 env_;
    DistortionUnit distortion_;

    float envMod_ = 0.5f;
    float accentAmount_ = 0.7f;
    float masterGain_ = 0.8f;

    std::vector<ActiveNote> noteStack_;
    float targetPitchHz_ = 110.0f;
    float currentPitchHz_ = 110.0f;
    float pitchSlideCoeff_ = 0.01f;
    float pitchBendSemi_ = 0.0f;

    bool slideLedActive_ = false;
    bool accentLedActive_ = false;
    int lastNoteNumber_ = 36;
    int lastVelocity_ = 100;
};

class Engine {
public:
    Engine();

    void prepare(double sampleRate, int maxBlockFrames);
    void process(float* interleavedStereo, int frames, int64_t eventClockNanos);
    void renderSilent(int frames);

    // Parameters (UI thread).
    void setParam(int id, float value);
    float param(int id) const;
    void getParams(float* out, int count) const;

    // Transport and pattern editing (UI thread).
    void setPlaying(bool playing);
    void togglePlaying();
    bool isPlaying() const;
    void setTempo(double bpm);
    void setSwing(float swing);
    void setPatternLength(int length);
    void setCurrentPattern(int pattern);
    void setStep(int patternIndex, int stepIndex, bool gate, int note, bool accent, bool slide);
    void clearPattern(int patternIndex);
    void initPattern(int patternIndex);
    void copyPattern(int from, int to);
    void getPatternRow(int patternIndex, int* out, int count) const;
    int getPatternLength(int patternIndex) const;
    int currentPattern() const { return sequencer_.currentPattern(); }

    // Live performance (UI thread, lock-free queues).
    void noteOn(int note, int velocity);
    void noteOff(int note);
    void allNotesOff();

    // Preset vault.
    void loadFactoryPreset(int bank, int slot);
    void generatePreset(int bank, int slot, uint32_t seed);
    void applyPreset(const Preset& preset, int patternSlot);
    Preset capturePreset() const;
    std::string currentPresetName() const;
    static std::string factoryPresetName(int bank, int slot);

    // MIDI plumbing.
    void pushMidi(int status, int data1, int data2, int64_t timestampNanos);
    int pollMidiOut(uint8_t* out, int maxRecords);
    void setMidiOutEnabled(bool enabled);
    void setClockSend(bool enabled);
    void setClockReceive(bool enabled);
    void setTransportSend(bool enabled);
    void setMidiChannel(int channel);

    // Telemetry.
    void getStatus(int* out, int count) const;
    void getScope(float* out, int count) const;
    float meter() const;
    int sampleRate() const { return static_cast<int>(sampleRate_); }
    // Test-only view of the voice note stack. It reads state owned by the
    // audio thread, so it is only valid between process() calls, never from
    // another thread: the UI uses getStatus()/meter() instead.
    int heldNotes() const { return synth_.heldNotes(); }

private:
    struct MidiInRecord {
        uint8_t status = 0;
        uint8_t data1 = 0;
        uint8_t data2 = 0;
        int64_t timestamp = 0;
    };

    struct MidiOutRecord {
        uint8_t status = 0;
        uint8_t data1 = 0;
        uint8_t data2 = 0;
        int64_t timestamp = 0;
    };

    struct NoteCommand {
        bool on = false;
        int note = 36;
        int velocity = 80;
    };

    // Single-producer / single-consumer ring, used between JNI and the audio
    // callback. Producers never block; a full queue drops the newest event.
    template <typename T, uint32_t Size>
    class SpscRing {
    public:
        bool push(const T& value) noexcept {
            const uint32_t head = head_.load(std::memory_order_relaxed);
            const uint32_t next = (head + 1) % Size;
            if (next == tail_.load(std::memory_order_acquire)) return false;
            items_[head] = value;
            head_.store(next, std::memory_order_release);
            return true;
        }

        bool pop(T& value) noexcept {
            const uint32_t tail = tail_.load(std::memory_order_relaxed);
            if (tail == head_.load(std::memory_order_acquire)) return false;
            value = items_[tail];
            tail_.store((tail + 1) % Size, std::memory_order_release);
            return true;
        }

    private:
        std::array<T, Size> items_{};
        std::atomic<uint32_t> head_{0};
        std::atomic<uint32_t> tail_{0};
    };

    void drainMidiInput() noexcept;
    void drainLiveNotes() noexcept;
    void handleMidiMessage(int status, int data1, int data2, int64_t timestamp) noexcept;
    void applySequencerEvents(const Sequencer::Event* events, int count,
                              int64_t timestampNanos) noexcept;
    void pushMidiOut(uint8_t status, uint8_t data1, uint8_t data2,
                     int64_t timestampNanos) noexcept;
    void updateParamFromCc(int controller, int value) noexcept;
    void snapshotVoice() noexcept;
    void resetTransportOutputs() noexcept;

    double sampleRate_ = 48000.0;
    int maxBlockFrames_ = 512;

    std::atomic<float> params_[kParamCount];

    Synth303 synth_;
    Sequencer sequencer_;

    std::atomic<bool> midiOutEnabled_{true};
    std::atomic<bool> clockSend_{true};
    std::atomic<bool> clockReceive_{false};
    std::atomic<bool> transportSend_{true};
    std::atomic<int> midiChannel_{-1};

    SpscRing<MidiInRecord, 512> midiIn_;
    SpscRing<MidiOutRecord, 2048> midiOut_;
    SpscRing<NoteCommand, 128> noteCommands_;

    // Audio-thread transport state.
    bool lastPlaying_ = false;
    double clockAccumulator_ = 0.0;
    int64_t midiClockCount_ = 0;
    int64_t playStartSentAt_ = 0;

    std::atomic<float> meter_{0.0f};
    std::array<float, 256> scope_{};
    std::atomic<uint32_t> scopeWrite_{0};
    uint32_t scopeCounter_ = 0;
    std::vector<float> silentBuffer_;

    std::atomic<int> presetBank_{0};
    std::atomic<int> presetSlot_{0};
    mutable std::mutex nameMutex_;
    std::string currentPresetName_{"Amber Line"};
};

} // namespace r303
