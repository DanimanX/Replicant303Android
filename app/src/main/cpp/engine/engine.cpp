#include "engine.h"

#include <algorithm>
#include <cmath>

namespace r303 {

// ===========================================================================
// Synth voice
// ===========================================================================

void Synth303::prepare(double sampleRate) noexcept {
    sampleRate_ = std::max(1000.0, sampleRate);
    osc_.prepare(sampleRate_);
    filter_.prepare(sampleRate_);
    env_.prepare(sampleRate_);
    distortion_.prepare(sampleRate_);
    noteStack_.reserve(128);
    updateSlideCoefficient();
    reset();
}

void Synth303::reset() noexcept {
    osc_.reset();
    filter_.reset();
    env_.reset();
    distortion_.reset();
    noteStack_.clear();
    targetPitchHz_ = 110.0f;
    currentPitchHz_ = 110.0f;
    pitchBendSemi_ = 0.0f;
    slideLedActive_ = false;
    accentLedActive_ = false;
}

void Synth303::updateSlideCoefficient() noexcept {
    const float dt = 1.0f / static_cast<float>(sampleRate_);
    pitchSlideCoeff_ = 1.0f - std::exp(-dt / 0.060f);
}

void Synth303::setWaveform(int wave) noexcept {
    osc_.setWaveform(wave == 1 ? Waveform::Square : Waveform::Sawtooth);
}

void Synth303::setTuning(float semitones) noexcept { osc_.setTuningSemi(semitones); }
void Synth303::setCutoff(float hz) noexcept { filter_.setCutoff(hz); }
void Synth303::setResonance(float resonance01) noexcept { filter_.setResonance(resonance01); }
void Synth303::setEnvMod(float envMod01) noexcept { envMod_ = clampf(envMod01, 0.0f, 1.0f); }
void Synth303::setDecay(float seconds) noexcept { env_.setDecayTime(seconds); }
void Synth303::setAccent(float accent01) noexcept { accentAmount_ = clampf(accent01, 0.0f, 1.0f); }
void Synth303::setVolume(float linear) noexcept { masterGain_ = clampf(linear, 0.0f, 1.5f); }

void Synth303::setDistortion(bool active, int type, float drive, float tone, float mix) noexcept {
    distortion_.setActive(active);
    distortion_.setType(static_cast<DistortionType>(clampi(type, 0, 4)));
    distortion_.setDrive(drive);
    distortion_.setTone(tone);
    distortion_.setMix(mix);
}

void Synth303::setPitchBend(float semitones) noexcept {
    pitchBendSemi_ = clampf(semitones, -12.0f, 12.0f);
}

void Synth303::noteOn(int noteNumber, int velocity) noexcept {
    lastNoteNumber_ = noteNumber;
    lastVelocity_ = velocity;

    const bool isAccented = velocity >= 100;
    const bool isLegato = !noteStack_.empty();

    targetPitchHz_ = midiToFreq(static_cast<float>(noteNumber));

    if (!isLegato) {
        currentPitchHz_ = targetPitchHz_;
        slideLedActive_ = false;
    } else {
        slideLedActive_ = true;
    }

    accentLedActive_ = isAccented;
    env_.noteOn(isAccented, isLegato);
    if (noteStack_.size() >= 128) noteStack_.erase(noteStack_.begin());
    noteStack_.push_back(ActiveNote{noteNumber, velocity});
}

void Synth303::noteOff(int noteNumber) noexcept {
    const auto it = std::find_if(noteStack_.begin(), noteStack_.end(),
                                 [noteNumber](const ActiveNote& note) {
                                     return note.note == noteNumber;
                                 });
    if (it != noteStack_.end()) noteStack_.erase(it);

    if (noteStack_.empty()) {
        env_.noteOff();
        slideLedActive_ = false;
        accentLedActive_ = false;
    } else {
        targetPitchHz_ = midiToFreq(static_cast<float>(noteStack_.back().note));
        slideLedActive_ = true;
    }
}

float Synth303::process() noexcept {
    const float bendFactor = std::pow(2.0f, pitchBendSemi_ / 12.0f);
    const float effectiveTarget = targetPitchHz_ * bendFactor;
    currentPitchHz_ += (effectiveTarget - currentPitchHz_) * pitchSlideCoeff_;

    osc_.setFrequency(currentPitchHz_);
    const float rawOsc = osc_.process();

    float vcfEnv = 0.0f;
    float vcaEnv = 0.0f;
    float accentEnv = 0.0f;
    env_.process(vcfEnv, vcaEnv, accentEnv);

    const float filtered =
        filter_.process(rawOsc, envMod_, vcfEnv, accentEnv * accentAmount_);
    const float vcaBoost = 1.0f + accentEnv * accentAmount_ * 0.45f;
    const float amplified = filtered * vcaEnv * vcaBoost;
    const float distorted = distortion_.processSample(amplified);
    return sanitiseSample(distorted * masterGain_);
}

// ===========================================================================
// Engine
// ===========================================================================

Engine::Engine() {
    params_[kParamWaveform].store(0.0f);
    params_[kParamTuning].store(0.0f);
    params_[kParamCutoff].store(350.0f);
    params_[kParamResonance].store(0.65f);
    params_[kParamEnvMod].store(0.60f);
    params_[kParamDecay].store(0.75f);
    params_[kParamAccent].store(0.70f);
    params_[kParamVolume].store(0.80f);
    params_[kParamDistActive].store(1.0f);
    params_[kParamDistType].store(0.0f);
    params_[kParamDistDrive].store(0.50f);
    params_[kParamDistTone].store(0.60f);
    params_[kParamDistMix].store(0.85f);
    params_[kParamTempo].store(125.0f);
    params_[kParamSwing].store(0.0f);

    silentBuffer_.assign(1024 * 2, 0.0f);
    prepare(48000.0, 512);
}

void Engine::prepare(double sampleRate, int maxBlockFrames) {
    sampleRate_ = std::max(8000.0, sampleRate);
    maxBlockFrames_ = std::max(64, maxBlockFrames);

    synth_.prepare(sampleRate_);
    sequencer_.prepare(sampleRate_);

    if (silentBuffer_.size() < static_cast<size_t>(maxBlockFrames_ * 2))
        silentBuffer_.assign(static_cast<size_t>(maxBlockFrames_ * 2), 0.0f);

    lastPlaying_ = false;
    clockAccumulator_ = 0.0;
    midiClockCount_ = 0;
    scopeCounter_ = 0;
    meter_.store(0.0f);
}

void Engine::snapshotVoice() noexcept {
    synth_.setWaveform(static_cast<int>(params_[kParamWaveform].load(std::memory_order_relaxed)));
    synth_.setTuning(params_[kParamTuning].load(std::memory_order_relaxed));
    synth_.setCutoff(params_[kParamCutoff].load(std::memory_order_relaxed));
    synth_.setResonance(params_[kParamResonance].load(std::memory_order_relaxed));
    synth_.setEnvMod(params_[kParamEnvMod].load(std::memory_order_relaxed));
    synth_.setDecay(params_[kParamDecay].load(std::memory_order_relaxed));
    synth_.setAccent(params_[kParamAccent].load(std::memory_order_relaxed));
    synth_.setVolume(params_[kParamVolume].load(std::memory_order_relaxed));
    synth_.setDistortion(
        params_[kParamDistActive].load(std::memory_order_relaxed) > 0.5f,
        static_cast<int>(params_[kParamDistType].load(std::memory_order_relaxed)),
        params_[kParamDistDrive].load(std::memory_order_relaxed),
        params_[kParamDistTone].load(std::memory_order_relaxed),
        params_[kParamDistMix].load(std::memory_order_relaxed));
}

void Engine::setParam(int id, float value) {
    if (id < 0 || id >= kParamCount) return;

    switch (id) {
        case kParamWaveform: value = value >= 0.5f ? 1.0f : 0.0f; break;
        case kParamTuning: value = clampf(value, -12.0f, 12.0f); break;
        case kParamCutoff: value = clampf(value, 25.0f, 4500.0f); break;
        case kParamResonance: value = clampf(value, 0.0f, 1.0f); break;
        case kParamEnvMod: value = clampf(value, 0.0f, 1.0f); break;
        case kParamDecay: value = clampf(value, 0.20f, 2.50f); break;
        case kParamAccent: value = clampf(value, 0.0f, 1.0f); break;
        case kParamVolume: value = clampf(value, 0.0f, 1.0f); break;
        case kParamDistActive: value = value >= 0.5f ? 1.0f : 0.0f; break;
        case kParamDistType: value = static_cast<float>(clampi(static_cast<int>(value), 0, 4)); break;
        case kParamDistDrive:
        case kParamDistTone:
        case kParamDistMix: value = clampf(value, 0.0f, 1.0f); break;
        case kParamTempo:
            value = clampf(value, 40.0f, 300.0f);
            sequencer_.setTempo(value);
            break;
        case kParamSwing:
            value = clampf(value, 0.0f, 0.66f);
            sequencer_.setSwing(value);
            break;
        default: break;
    }

    params_[id].store(value, std::memory_order_relaxed);
}

float Engine::param(int id) const {
    if (id < 0 || id >= kParamCount) return 0.0f;
    return params_[id].load(std::memory_order_relaxed);
}

void Engine::getParams(float* out, int count) const {
    if (out == nullptr) return;
    for (int i = 0; i < count && i < kParamCount; ++i)
        out[i] = params_[i].load(std::memory_order_relaxed);
}

void Engine::setPlaying(bool playing) {
    if (playing) {
        sequencer_.start();
    } else {
        sequencer_.stop();
    }
}

void Engine::togglePlaying() { setPlaying(!sequencer_.isPlaying()); }
bool Engine::isPlaying() const { return sequencer_.isPlaying(); }

void Engine::setTempo(double bpm) { setParam(kParamTempo, static_cast<float>(bpm)); }
void Engine::setSwing(float swing) { setParam(kParamSwing, swing); }

void Engine::setPatternLength(int length) {
    sequencer_.setLength(sequencer_.currentPattern(), length);
}

void Engine::setCurrentPattern(int pattern) { sequencer_.setCurrentPattern(pattern); }

void Engine::setStep(int patternIndex, int stepIndex, bool gate, int note, bool accent,
                     bool slide) {
    sequencer_.setStep(patternIndex, stepIndex, gate, note, accent, slide);
}

void Engine::clearPattern(int patternIndex) { sequencer_.clearPattern(patternIndex); }
void Engine::initPattern(int patternIndex) { sequencer_.initPattern(patternIndex); }
void Engine::copyPattern(int from, int to) { sequencer_.copyPattern(from, to); }

void Engine::getPatternRow(int patternIndex, int* out, int count) const {
    if (out == nullptr) return;
    const Pattern pattern = sequencer_.getPattern(patternIndex);
    for (int i = 0; i < count && i < Pattern::kMaxSteps; ++i) {
        const auto& step = pattern[i];
        out[i] = (step.gate ? 1 : 0) | (step.accent ? 2 : 0) | (step.slide ? 4 : 0) |
                 (step.note << 3);
    }
}

int Engine::getPatternLength(int patternIndex) const {
    return sequencer_.getLength(patternIndex);
}

void Engine::noteOn(int note, int velocity) {
    noteCommands_.push(NoteCommand{true, clampi(note, 0, 127), clampi(velocity, 1, 127)});
}

void Engine::noteOff(int note) {
    noteCommands_.push(NoteCommand{false, clampi(note, 0, 127), 0});
}

void Engine::allNotesOff() {
    for (int note = 0; note < 128; ++note)
        noteCommands_.push(NoteCommand{false, note, 0});
}

void Engine::loadFactoryPreset(int bank, int slot) {
    const int b = clampi(bank, 0, presetbank::kNumBanks - 1);
    const int s = clampi(slot, 0, presetbank::kPresetsPerBank - 1);
    const Preset preset = presetbank::makeFactoryPreset(b, s);

    applyPreset(preset, b);
    presetBank_.store(b);
    presetSlot_.store(s);
}

void Engine::generatePreset(int bank, int slot, uint32_t seed) {
    const int b = clampi(bank, 0, presetbank::kNumBanks - 1);
    const int s = clampi(slot, 0, presetbank::kPresetsPerBank - 1);
    const Preset preset = presetbank::generatePreset(b, s, seed);

    applyPreset(preset, b);
    presetBank_.store(b);
    presetSlot_.store(s);
}

void Engine::applyPreset(const Preset& source, int patternSlot) {
    Preset preset = source;
    preset.sanitise();

    setParam(kParamWaveform, static_cast<float>(preset.waveform));
    setParam(kParamTuning, preset.tuning);
    setParam(kParamCutoff, preset.cutoff);
    setParam(kParamResonance, preset.resonance);
    setParam(kParamEnvMod, preset.envMod);
    setParam(kParamDecay, preset.decay);
    setParam(kParamAccent, preset.accent);
    setParam(kParamTempo, preset.tempo);
    setParam(kParamSwing, preset.swing);
    setParam(kParamDistActive, preset.distActive ? 1.0f : 0.0f);
    setParam(kParamDistType, static_cast<float>(preset.distType));
    setParam(kParamDistDrive, preset.distDrive);
    setParam(kParamDistTone, preset.distTone);
    setParam(kParamDistMix, preset.distMix);

    const int slot = clampi(patternSlot, 0, Sequencer::kNumPatterns - 1);
    sequencer_.setCurrentPattern(slot);
    sequencer_.setPattern(slot, preset.pattern);
    sequencer_.setSwing(preset.swing);
    sequencer_.requestReset();

    std::lock_guard<std::mutex> lock(nameMutex_);
    currentPresetName_ = preset.name;
}

Preset Engine::capturePreset() const {
    Preset preset;
    {
        std::lock_guard<std::mutex> lock(nameMutex_);
        preset.name = currentPresetName_;
    }
    preset.pattern = sequencer_.getPattern(sequencer_.currentPattern());
    preset.waveform = static_cast<int>(params_[kParamWaveform].load());
    preset.tuning = params_[kParamTuning].load();
    preset.cutoff = params_[kParamCutoff].load();
    preset.resonance = params_[kParamResonance].load();
    preset.envMod = params_[kParamEnvMod].load();
    preset.decay = params_[kParamDecay].load();
    preset.accent = params_[kParamAccent].load();
    preset.tempo = params_[kParamTempo].load();
    preset.swing = params_[kParamSwing].load();
    preset.distActive = params_[kParamDistActive].load() > 0.5f;
    preset.distType = static_cast<int>(params_[kParamDistType].load());
    preset.distDrive = params_[kParamDistDrive].load();
    preset.distTone = params_[kParamDistTone].load();
    preset.distMix = params_[kParamDistMix].load();
    preset.sanitise();
    return preset;
}

std::string Engine::currentPresetName() const {
    std::lock_guard<std::mutex> lock(nameMutex_);
    return currentPresetName_;
}

std::string Engine::factoryPresetName(int bank, int slot) {
    return presetbank::presetName(bank, slot);
}

void Engine::setMidiOutEnabled(bool enabled) { midiOutEnabled_.store(enabled); }
void Engine::setClockSend(bool enabled) { clockSend_.store(enabled); }

void Engine::setClockReceive(bool enabled) {
    clockReceive_.store(enabled);
    sequencer_.setExternalClock(enabled);
}

void Engine::setTransportSend(bool enabled) { transportSend_.store(enabled); }
void Engine::setMidiChannel(int channel) { midiChannel_.store(clampi(channel, -1, 15)); }

void Engine::pushMidi(int status, int data1, int data2, int64_t timestampNanos) {
    MidiInRecord record;
    record.status = static_cast<uint8_t>(clampi(status, 0, 255));
    record.data1 = static_cast<uint8_t>(clampi(data1, 0, 127));
    record.data2 = static_cast<uint8_t>(clampi(data2, 0, 127));
    record.timestamp = timestampNanos;
    midiIn_.push(record);
}

int Engine::pollMidiOut(uint8_t* out, int maxRecords) {
    if (out == nullptr || maxRecords <= 0) return 0;

    int count = 0;
    MidiOutRecord record;
    while (count < maxRecords && midiOut_.pop(record)) {
        uint8_t* base = out + static_cast<size_t>(count) * 16;
        for (int i = 0; i < 16; ++i) base[i] = 0;
        base[0] = record.status;
        base[1] = record.data1;
        base[2] = record.data2;
        const uint64_t stamp = static_cast<uint64_t>(record.timestamp);
        for (int i = 0; i < 8; ++i)
            base[8 + i] = static_cast<uint8_t>((stamp >> (8 * i)) & 0xFFu);
        ++count;
    }
    return count;
}

void Engine::pushMidiOut(uint8_t status, uint8_t data1, uint8_t data2,
                         int64_t timestampNanos) noexcept {
    if (!midiOutEnabled_.load(std::memory_order_relaxed)) return;
    midiOut_.push(MidiOutRecord{status, data1, data2, timestampNanos});
}

void Engine::applySequencerEvents(const Sequencer::Event* events, int count,
                                  int64_t timestampNanos) noexcept {
    const int channel = midiChannel_.load(std::memory_order_relaxed) >= 0
                            ? midiChannel_.load(std::memory_order_relaxed)
                            : 0;
    for (int i = 0; i < count; ++i) {
        const auto& event = events[i];
        if (event.noteOn) {
            synth_.noteOn(event.note, event.velocity);
            pushMidiOut(static_cast<uint8_t>(0x90 | channel),
                        static_cast<uint8_t>(event.note),
                        static_cast<uint8_t>(event.velocity), timestampNanos);
        } else {
            synth_.noteOff(event.note);
            pushMidiOut(static_cast<uint8_t>(0x80 | channel),
                        static_cast<uint8_t>(event.note), 0, timestampNanos);
        }
    }
}

void Engine::drainLiveNotes() noexcept {
    NoteCommand command;
    while (noteCommands_.pop(command)) {
        if (command.on) {
            synth_.noteOn(command.note, command.velocity);
        } else {
            synth_.noteOff(command.note);
        }
    }
}

void Engine::updateParamFromCc(int controller, int value) noexcept {
    const float normalised = clampf(static_cast<float>(value) / 127.0f, 0.0f, 1.0f);

    switch (controller) {
        case 1: setParam(kParamEnvMod, normalised); break;
        case 71: setParam(kParamResonance, normalised); break;
        case 72: setParam(kParamDecay, 0.20f + normalised * 2.30f); break;
        case 73: setParam(kParamEnvMod, normalised); break;
        case 74: setParam(kParamCutoff, 25.0f * std::pow(180.0f, normalised)); break;
        case 75: setParam(kParamAccent, normalised); break;
        case 76: setParam(kParamDistDrive, normalised); break;
        case 77: setParam(kParamDistTone, normalised); break;
        case 78: setParam(kParamDistMix, normalised); break;
        case 79: setParam(kParamDistActive, value >= 64 ? 1.0f : 0.0f); break;
        case 80: setParam(kParamWaveform, value >= 64 ? 1.0f : 0.0f); break;
        case 120:
        case 123:
            for (int note = 0; note < 128; ++note) synth_.noteOff(note);
            break;
        default: break;
    }
}

void Engine::handleMidiMessage(int status, int data1, int data2,
                               int64_t timestampNanos) noexcept {
    if (status < 0) return;

    if (status >= 0xF0) {
        switch (status) {
            case 0xF8: {  // clock
                if (!clockReceive_.load(std::memory_order_relaxed)) return;
                Sequencer::Event events[4];
                const int count = sequencer_.clockPulse(events, timestampNanos);
                applySequencerEvents(events, count, timestampNanos);
                break;
            }
            case 0xFA: {  // start
                if (!clockReceive_.load(std::memory_order_relaxed)) return;
                Sequencer::Event events[4];
                const int count = sequencer_.midiStart(events);
                applySequencerEvents(events, count, timestampNanos);
                break;
            }
            case 0xFC: {  // stop
                if (!clockReceive_.load(std::memory_order_relaxed)) return;
                Sequencer::Event events[4];
                const int count = sequencer_.midiStop(events);
                applySequencerEvents(events, count, timestampNanos);
                break;
            }
            case 0xFF:  // reset
                synth_.reset();
                sequencer_.requestReset();
                break;
            default: break;
        }
        return;
    }

    const int channel = midiChannel_.load(std::memory_order_relaxed);
    if (channel >= 0 && (status & 0x0F) != channel) return;

    const int type = status & 0xF0;
    switch (type) {
        case 0x90:
            if (data2 > 0) {
                synth_.noteOn(data1, data2);
            } else {
                synth_.noteOff(data1);
            }
            break;
        case 0x80:
            synth_.noteOff(data1);
            break;
        case 0xB0:
            updateParamFromCc(data1, data2);
            break;
        case 0xE0: {
            const int wheel = (data2 << 7) | data1;
            synth_.setPitchBend((static_cast<float>(wheel - 8192) / 8192.0f) * 2.0f);
            break;
        }
        default: break;
    }
}

void Engine::drainMidiInput() noexcept {
    MidiInRecord record;
    while (midiIn_.pop(record))
        handleMidiMessage(record.status, record.data1, record.data2, record.timestamp);
}

void Engine::process(float* interleavedStereo, int frames, int64_t eventClockNanos) {
    if (interleavedStereo == nullptr || frames <= 0) return;

    drainMidiInput();
    drainLiveNotes();
    snapshotVoice();

    float peak = 0.0f;
    const double samplesPerClock =
        sampleRate_ * 60.0 / std::max(20.0, sequencer_.effectiveTempo()) / 24.0;

    for (int i = 0; i < frames; ++i) {
        const int64_t timestamp =
            eventClockNanos + static_cast<int64_t>(
                                  (static_cast<double>(i) / sampleRate_) * 1.0e9);

        Sequencer::Event events[4];
        const int eventCount = sequencer_.tick(events);
        applySequencerEvents(events, eventCount, timestamp);

        const bool playing = sequencer_.isPlaying();
        if (playing != lastPlaying_) {
            lastPlaying_ = playing;
            clockAccumulator_ = 0.0;
            if (transportSend_.load(std::memory_order_relaxed) &&
                !sequencer_.externalClock()) {
                pushMidiOut(playing ? 0xFA : 0xFC, 0, 0, timestamp);
            }
        }

        if (playing && clockSend_.load(std::memory_order_relaxed) &&
            !sequencer_.externalClock()) {
            clockAccumulator_ += 1.0;
            if (clockAccumulator_ >= samplesPerClock) {
                clockAccumulator_ -= samplesPerClock;
                pushMidiOut(0xF8, 0, 0, timestamp);
                ++midiClockCount_;
            }
        }

        float sample = synth_.process();
        sample = sanitiseSample(softClip(sample * 0.95f));
        interleavedStereo[i * 2] = sample;
        interleavedStereo[i * 2 + 1] = sample;

        peak = std::max(peak, std::fabs(sample));

        if ((scopeCounter_++ & 3u) == 0u) {
            const uint32_t index = scopeWrite_.load(std::memory_order_relaxed);
            scope_[index & 255u] = sample;
            scopeWrite_.store(index + 1, std::memory_order_release);
        }
    }

    const float previous = meter_.load(std::memory_order_relaxed) * 0.82f;
    meter_.store(std::max(peak, previous), std::memory_order_relaxed);
}

void Engine::renderSilent(int frames) {
    if (frames <= 0) return;
    if (silentBuffer_.size() < static_cast<size_t>(maxBlockFrames_ * 2))
        silentBuffer_.assign(static_cast<size_t>(maxBlockFrames_ * 2), 0.0f);

    int remaining = frames;
    while (remaining > 0) {
        const int chunk = std::min(remaining, maxBlockFrames_);
        process(silentBuffer_.data(), chunk, 0);
        remaining -= chunk;
    }
}

void Engine::getStatus(int* out, int count) const {
    if (out == nullptr) return;
    for (int i = 0; i < count; ++i) out[i] = 0;
    if (count > kStatusPlaying) out[kStatusPlaying] = sequencer_.isPlaying() ? 1 : 0;
    if (count > kStatusCurrentStep) out[kStatusCurrentStep] = sequencer_.currentStep();
    if (count > kStatusCurrentPattern) out[kStatusCurrentPattern] = sequencer_.currentPattern();
    if (count > kStatusMidiOut) out[kStatusMidiOut] = midiOutEnabled_.load() ? 1 : 0;
    if (count > kStatusClockSend) out[kStatusClockSend] = clockSend_.load() ? 1 : 0;
    if (count > kStatusClockReceive) out[kStatusClockReceive] = clockReceive_.load() ? 1 : 0;
    if (count > kStatusExternalLocked) out[kStatusExternalLocked] = sequencer_.externalLocked() ? 1 : 0;
    if (count > kStatusMidiChannel) out[kStatusMidiChannel] = midiChannel_.load();
    if (count > kStatusLastNote) out[kStatusLastNote] = synth_.lastNote();
    if (count > kStatusWaitingForClock) out[kStatusWaitingForClock] = sequencer_.isWaitingForClock() ? 1 : 0;
    if (count > kStatusArmed) out[kStatusArmed] = sequencer_.isArmed() ? 1 : 0;
    if (count > kStatusSlide) out[kStatusSlide] = synth_.slideActive() ? 1 : 0;
    if (count > kStatusAccent) out[kStatusAccent] = synth_.accentActive() ? 1 : 0;
    if (count > kStatusPresetBank) out[kStatusPresetBank] = presetBank_.load();
    if (count > kStatusPresetSlot) out[kStatusPresetSlot] = presetSlot_.load();
    if (count > kStatusLength)
        out[kStatusLength] = sequencer_.getLength(sequencer_.currentPattern());
}

void Engine::getScope(float* out, int count) const {
    if (out == nullptr) return;
    const uint32_t write = scopeWrite_.load(std::memory_order_acquire);
    for (int i = 0; i < count; ++i) {
        const uint32_t offset = static_cast<uint32_t>(i);
        const uint32_t index = (write + 256u - static_cast<uint32_t>(count) + offset) & 255u;
        out[i] = scope_[index];
    }
}

float Engine::meter() const { return meter_.load(std::memory_order_relaxed); }

} // namespace r303
