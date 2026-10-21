#include "sequencer.h"

#include <algorithm>
#include <cassert>
#include <cmath>

namespace r303 {

Sequencer::Sequencer() {
    for (int i = 0; i < kNumPatterns; ++i) patterns_[static_cast<size_t>(i)] = makeDefaultPattern(i);
    publishedPattern_ = patterns_[0];
}

void Sequencer::prepare(double newSampleRate) {
    sampleRate_ = std::max(1000.0, newSampleRate);
    reset();
}

void Sequencer::reset() {
    samplesUntilStep_ = 0.0;
    nextStepIndex_ = 0;
    gateSamplesRemaining_ = 0.0;
    heldBySlide_ = false;
    activeNote_ = -1;
    pulseCount_ = 0;
    lastPulseNanos_ = 0;
    pulseIntervalNanos_ = 0;
    currentStep_.store(0, std::memory_order_relaxed);
    running_.store(false, std::memory_order_relaxed);
}

void Sequencer::setTempo(double bpm) noexcept {
    tempoBpm_.store(std::clamp(bpm, 20.0, 400.0), std::memory_order_relaxed);
}

double Sequencer::tempo() const noexcept {
    return tempoBpm_.load(std::memory_order_relaxed);
}

double Sequencer::effectiveTempo() const noexcept {
    if (externalClock_.load(std::memory_order_relaxed)) {
        const double ext = externalTempo_.load(std::memory_order_relaxed);
        if (ext > 1.0) return std::clamp(ext, 20.0, 400.0);
    }
    return std::clamp(tempoBpm_.load(std::memory_order_relaxed), 20.0, 400.0);
}

void Sequencer::setSwing(float amount01) noexcept {
    swingAmount_.store(clampf(amount01, 0.0f, 0.66f), std::memory_order_relaxed);
}

float Sequencer::swing() const noexcept {
    return swingAmount_.load(std::memory_order_relaxed);
}

void Sequencer::setExternalClock(bool enabled) noexcept {
    externalClock_.store(enabled, std::memory_order_relaxed);
    if (!enabled) {
        externalLocked_.store(false, std::memory_order_relaxed);
        externalTempo_.store(0.0, std::memory_order_relaxed);
    }
}

bool Sequencer::externalClock() const noexcept {
    return externalClock_.load(std::memory_order_relaxed);
}

bool Sequencer::externalLocked() const noexcept {
    return externalLocked_.load(std::memory_order_relaxed);
}

double Sequencer::externalTempo() const noexcept {
    return externalTempo_.load(std::memory_order_relaxed);
}

void Sequencer::start() noexcept {
    playRequested_.store(true, std::memory_order_relaxed);
}

void Sequencer::stop() noexcept {
    playRequested_.store(false, std::memory_order_relaxed);
}

bool Sequencer::isPlaying() const noexcept {
    return running_.load(std::memory_order_relaxed);
}

bool Sequencer::isArmed() const noexcept {
    return playRequested_.load(std::memory_order_relaxed);
}

bool Sequencer::isWaitingForClock() const noexcept {
    return externalClock_.load(std::memory_order_relaxed) &&
           playRequested_.load(std::memory_order_relaxed) &&
           !running_.load(std::memory_order_relaxed);
}

void Sequencer::requestReset() noexcept {
    resetRequested_.store(true, std::memory_order_release);
}

double Sequencer::stepSamples() const noexcept {
    return sampleRate_ * 60.0 / effectiveTempo() / 4.0;
}

int Sequencer::releaseActiveNote(Event* out, int offset) noexcept {
    int count = 0;
    if (activeNote_ >= 0 && out != nullptr) {
        out[offset + count++] = Event{false, activeNote_, 0};
        activeNote_ = -1;
    }
    gateSamplesRemaining_ = 0.0;
    heldBySlide_ = false;
    return count;
}

int Sequencer::triggerStep(int stepIndex, const Pattern& pattern, Event* out,
                           int offset) noexcept {
    int count = 0;
    const auto& step = pattern[stepIndex];
    const double fullStep = stepSamples();
    const double swing = static_cast<double>(swingAmount_.load(std::memory_order_relaxed));
    const bool evenStep = (stepIndex % 2) == 0;

    const double interval =
        fullStep * (evenStep ? (1.0 + swing * 0.5) : (1.0 - swing * 0.5));
    lastInterval_ = interval;

    if (activeNote_ >= 0 && !heldBySlide_)
        count += releaseActiveNote(out, offset + count);

    if (!step.gate) {
        count += releaseActiveNote(out, offset + count);
        return count;
    }

    const int note = clampi(step.note, 0, 127);
    const int velocity = step.accent ? 110 : 80;

    if (activeNote_ >= 0) {
        // Slide: the new note starts while the old one is still held. A slide
        // into the same pitch is a tie: sending its own note-off would close
        // the gate in the same block and swallow the note.
        out[offset + count++] = Event{true, note, velocity};
        if (activeNote_ != note)
            out[offset + count++] = Event{false, activeNote_, 0};
    } else {
        out[offset + count++] = Event{true, note, velocity};
    }

    activeNote_ = note;
    heldBySlide_ = step.slide;
    gateSamplesRemaining_ = step.slide ? 0.0 : interval * 0.5 + 1.0;
    return count;
}

int Sequencer::tick(Event* out) noexcept {
    int count = 0;

    if (resetRequested_.exchange(false, std::memory_order_acq_rel)) {
        count += releaseActiveNote(out, count);
        samplesUntilStep_ = 0.0;
        nextStepIndex_ = 0;
        pulseCount_ = 0;
        currentStep_.store(0, std::memory_order_relaxed);
    }

    const bool wantRunning = playRequested_.load(std::memory_order_relaxed);
    const bool external = externalClock_.load(std::memory_order_relaxed);

    if (!external) {
        if (wantRunning != running_.load(std::memory_order_relaxed)) {
            running_.store(wantRunning, std::memory_order_relaxed);
            if (wantRunning) {
                samplesUntilStep_ = 0.0;
                nextStepIndex_ = 0;
                gateSamplesRemaining_ = 0.0;
                heldBySlide_ = false;
                activeNote_ = -1;
                currentStep_.store(0, std::memory_order_relaxed);
            } else {
                count += releaseActiveNote(out, count);
            }
        }
    }

    if (!running_.load(std::memory_order_relaxed)) return count;

    if (!external && samplesUntilStep_ <= 0.0) {
        const Pattern pattern = readPublishedPattern();
        const int length = clampi(pattern.length, 1, Pattern::kMaxSteps);

        count += triggerStep(nextStepIndex_, pattern, out, count);
        currentStep_.store(nextStepIndex_, std::memory_order_relaxed);
        samplesUntilStep_ += std::max(1.0, lastInterval_ > 0.0 ? lastInterval_ : stepSamples());

        if (nextStepIndex_ + 1 >= length)
            nextStepIndex_ = 0;
        else
            ++nextStepIndex_;
    }

    if (!heldBySlide_ && gateSamplesRemaining_ > 0.0) {
        gateSamplesRemaining_ -= 1.0;
        if (gateSamplesRemaining_ <= 0.0) count += releaseActiveNote(out, count);
    }

    if (!external) samplesUntilStep_ -= 1.0;
    assert(count <= kMaxEventsPerCall);
    return count;
}

int Sequencer::clockPulse(Event* out, int64_t timestampNanos) noexcept {
    if (!externalClock_.load(std::memory_order_relaxed)) return 0;

    // Estimate the incoming tempo from consecutive pulses (24 PPQN).
    if (lastPulseNanos_ > 0 && timestampNanos > lastPulseNanos_) {
        const int64_t delta = timestampNanos - lastPulseNanos_;
        if (delta > 1'000'000LL && delta < 2'000'000'000LL) {
            pulseIntervalNanos_ = pulseIntervalNanos_ == 0
                                      ? delta
                                      : (pulseIntervalNanos_ * 7 + delta) / 8;
            const double bpm = 60.0e9 / (static_cast<double>(pulseIntervalNanos_) * 24.0);
            if (bpm >= 20.0 && bpm <= 400.0) {
                externalTempo_.store(bpm, std::memory_order_relaxed);
                externalLocked_.store(true, std::memory_order_relaxed);
            }
        }
    }
    if (timestampNanos > 0) lastPulseNanos_ = timestampNanos;

    int count = 0;
    if (!running_.load(std::memory_order_relaxed)) {
        if (!playRequested_.load(std::memory_order_relaxed)) return 0;
        running_.store(true, std::memory_order_relaxed);
        currentStep_.store(0, std::memory_order_relaxed);
        nextStepIndex_ = 0;
        pulseCount_ = 0;
        activeNote_ = -1;
        heldBySlide_ = false;
        gateSamplesRemaining_ = 0.0;

        const Pattern pattern = readPublishedPattern();
        count += triggerStep(0, pattern, out);
        currentStep_.store(0, std::memory_order_relaxed);
        nextStepIndex_ = 1;
        pulseCount_ = 0;
        assert(count <= kMaxEventsPerCall);
        return count;
    }

    ++pulseCount_;
    if (pulseCount_ % 6 == 0) {
        const Pattern pattern = readPublishedPattern();
        const int length = clampi(pattern.length, 1, Pattern::kMaxSteps);
        count += triggerStep(nextStepIndex_, pattern, out, count);
        currentStep_.store(nextStepIndex_, std::memory_order_relaxed);
        nextStepIndex_ = (nextStepIndex_ + 1 >= length) ? 0 : nextStepIndex_ + 1;
    }
    assert(count <= kMaxEventsPerCall);
    return count;
}

int Sequencer::midiStart(Event* out) noexcept {
    if (!externalClock_.load(std::memory_order_relaxed)) return 0;

    int count = releaseActiveNote(out, 0);
    running_.store(true, std::memory_order_relaxed);
    playRequested_.store(true, std::memory_order_relaxed);
    pulseCount_ = 0;
    nextStepIndex_ = 0;
    activeNote_ = -1;
    heldBySlide_ = false;
    gateSamplesRemaining_ = 0.0;

    const Pattern pattern = readPublishedPattern();
    count += triggerStep(0, pattern, out, count);
    currentStep_.store(0, std::memory_order_relaxed);
    nextStepIndex_ = 1;
    assert(count <= kMaxEventsPerCall);
    return count;
}

int Sequencer::midiStop(Event* out) noexcept {
    if (!externalClock_.load(std::memory_order_relaxed)) return 0;
    running_.store(false, std::memory_order_relaxed);
    int count = releaseActiveNote(out);
    nextStepIndex_ = 0;
    pulseCount_ = 0;
    assert(count <= kMaxEventsPerCall);
    return count;
}

void Sequencer::publishCurrentLocked() noexcept {
    publishSequence_.fetch_add(1, std::memory_order_acq_rel);
    publishedPattern_ =
        patterns_[static_cast<size_t>(clampi(currentPatternIndex_.load(std::memory_order_relaxed),
                                             0, kNumPatterns - 1))];
    publishSequence_.fetch_add(1, std::memory_order_release);
}

Pattern Sequencer::readPublishedPattern() const noexcept {
    for (;;) {
        const uint32_t before = publishSequence_.load(std::memory_order_acquire);
        if ((before & 1u) != 0u) continue;
        Pattern snapshot = publishedPattern_;
        if (publishSequence_.load(std::memory_order_acquire) == before) return snapshot;
    }
}

Pattern Sequencer::getPattern(int index) const {
    std::lock_guard<std::mutex> lock(patternMutex_);
    return patterns_[static_cast<size_t>(clampi(index, 0, kNumPatterns - 1))];
}

void Sequencer::setPattern(int index, const Pattern& pattern) {
    Pattern copy = pattern;
    copy.sanitise();
    {
        std::lock_guard<std::mutex> lock(patternMutex_);
        patterns_[static_cast<size_t>(clampi(index, 0, kNumPatterns - 1))] = copy;
        publishCurrentLocked();
    }
}

Step Sequencer::getStep(int patternIndex, int stepIndex) const {
    std::lock_guard<std::mutex> lock(patternMutex_);
    return patterns_[static_cast<size_t>(clampi(patternIndex, 0, kNumPatterns - 1))]
                    [clampi(stepIndex, 0, Pattern::kMaxSteps - 1)];
}

void Sequencer::setStep(int patternIndex, int stepIndex, bool gate, int note, bool accent,
                        bool slide) {
    {
        std::lock_guard<std::mutex> lock(patternMutex_);
        auto& step = patterns_[static_cast<size_t>(clampi(patternIndex, 0, kNumPatterns - 1))]
                              [clampi(stepIndex, 0, Pattern::kMaxSteps - 1)];
        step.gate = gate;
        step.note = clampi(note, Pattern::kLowestNote, Pattern::kHighestNote);
        step.accent = accent;
        step.slide = slide;
        if (clampi(patternIndex, 0, kNumPatterns - 1) ==
            currentPatternIndex_.load(std::memory_order_relaxed)) {
            publishCurrentLocked();
        }
    }
}

void Sequencer::clearPattern(int index) {
    Pattern pattern;
    for (auto& step : pattern.steps) {
        step.gate = false;
        step.note = 36;
        step.accent = false;
        step.slide = false;
    }
    pattern.length = Pattern::kMaxSteps;
    setPattern(index, pattern);
}

void Sequencer::initPattern(int index) {
    setPattern(index, makeDefaultPattern(index));
}

void Sequencer::copyPattern(int from, int to) {
    setPattern(to, getPattern(from));
}

void Sequencer::setLength(int index, int newLength) {
    {
        std::lock_guard<std::mutex> lock(patternMutex_);
        patterns_[static_cast<size_t>(clampi(index, 0, kNumPatterns - 1))].length =
            clampi(newLength, 1, Pattern::kMaxSteps);
        if (clampi(index, 0, kNumPatterns - 1) ==
            currentPatternIndex_.load(std::memory_order_relaxed)) {
            publishCurrentLocked();
        }
    }
}

int Sequencer::getLength(int index) const {
    std::lock_guard<std::mutex> lock(patternMutex_);
    return patterns_[static_cast<size_t>(clampi(index, 0, kNumPatterns - 1))].length;
}

void Sequencer::setCurrentPattern(int index) {
    currentPatternIndex_.store(clampi(index, 0, kNumPatterns - 1), std::memory_order_relaxed);
    std::lock_guard<std::mutex> lock(patternMutex_);
    publishCurrentLocked();
}

} // namespace r303
