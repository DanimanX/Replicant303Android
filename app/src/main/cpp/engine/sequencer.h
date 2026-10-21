#pragma once

#include "pattern.h"

#include <array>
#include <atomic>
#include <cstdint>
#include <mutex>

namespace r303 {

// Pattern clock + pattern bank. The sequencer owns eight 16-step patterns and
// plays the selected one as 16th notes, emitting note events for the voice and
// for MIDI out.
//
// Threading:
//   - the audio thread calls prepare()/tick()/clockPulse()/midiStart()/stop();
//   - the UI thread owns the editing calls. Pattern edits are published to the
//     audio thread through a seqlock, so the callback never takes a lock.
class Sequencer {
public:
    static constexpr int kNumPatterns = 8;

    struct Event {
        bool noteOn = false;
        int note = 36;
        int velocity = 80;
    };

    Sequencer();

    void prepare(double newSampleRate);
    void reset();

    void setTempo(double bpm) noexcept;
    double tempo() const noexcept;
    double effectiveTempo() const noexcept;

    void setSwing(float amount01) noexcept;
    float swing() const noexcept;

    void setExternalClock(bool enabled) noexcept;
    bool externalClock() const noexcept;
    bool externalLocked() const noexcept;
    double externalTempo() const noexcept;

    void start() noexcept;
    void stop() noexcept;
    bool isPlaying() const noexcept;
    bool isArmed() const noexcept;
    bool isWaitingForClock() const noexcept;
    void requestReset() noexcept;

    // Advances one sample on the internal clock and handles gate release.
    // In external-clock mode it only advances the gate countdown; steps are
    // driven by clockPulse().
    int tick(Event* out) noexcept;

    // External-clock transport. Returns the number of emitted events.
    int clockPulse(Event* out, int64_t timestampNanos) noexcept;
    int midiStart(Event* out) noexcept;
    int midiStop(Event* out) noexcept;

    int currentStep() const noexcept { return currentStep_.load(std::memory_order_relaxed); }
    int currentPattern() const noexcept { return currentPatternIndex_.load(std::memory_order_relaxed); }

    // Pattern editing (UI thread).
    Pattern getPattern(int index) const;
    void setPattern(int index, const Pattern& pattern);
    Step getStep(int patternIndex, int stepIndex) const;
    void setStep(int patternIndex, int stepIndex, bool gate, int note, bool accent, bool slide);
    void clearPattern(int index);
    void initPattern(int index);
    void copyPattern(int from, int to);
    void setLength(int index, int newLength);
    int getLength(int index) const;
    void setCurrentPattern(int index);

private:
    int triggerStep(int stepIndex, const Pattern& pattern, Event* out) noexcept;
    int releaseActiveNote(Event* out) noexcept;
    double stepSamples() const noexcept;

    void publishCurrentLocked() noexcept;
    Pattern readPublishedPattern() const noexcept;

    double sampleRate_ = 48000.0;

    std::atomic<bool> playRequested_{false};
    std::atomic<bool> running_{false};
    std::atomic<bool> externalClock_{false};
    std::atomic<bool> externalLocked_{false};
    std::atomic<double> externalTempo_{0.0};
    std::atomic<double> tempoBpm_{125.0};
    std::atomic<float> swingAmount_{0.0f};

    // Audio-thread clock state.
    double samplesUntilStep_ = 0.0;
    int nextStepIndex_ = 0;
    double gateSamplesRemaining_ = 0.0;
    double lastInterval_ = 0.0;
    bool heldBySlide_ = false;
    int activeNote_ = -1;

    int pulseCount_ = 0;
    int64_t lastPulseNanos_ = 0;
    int64_t pulseIntervalNanos_ = 0;

    mutable std::mutex patternMutex_;
    std::array<Pattern, kNumPatterns> patterns_{};
    std::atomic<int> currentPatternIndex_{0};

    std::atomic<uint32_t> publishSequence_{0};
    Pattern publishedPattern_{};
    std::atomic<bool> resetRequested_{false};
    std::atomic<int> currentStep_{0};
};

} // namespace r303
