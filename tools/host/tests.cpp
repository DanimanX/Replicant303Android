// Host-side regression suite for the Replicant 303 core. It exercises the
// sequencer clock, pattern editing, the preset vault, the wire format, MIDI
// clock I/O and the DSP signal path without Android or Oboe.

#include "engine/engine.h"
#include "engine/sequencer.h"

#include <cmath>
#include <cstdio>
#include <cstring>
#include <cstdint>
#include <string>
#include <vector>

namespace {

int g_failures = 0;
int g_checks = 0;

void check(bool condition, const std::string& message) {
    ++g_checks;
    if (!condition) {
        ++g_failures;
        std::printf("FAIL  %s\n", message.c_str());
    }
}

struct NoteEvent {
    int sample = 0;
    bool on = false;
    int note = 0;
    int velocity = 0;
};

std::vector<NoteEvent> runSequencer(r303::Sequencer& sequencer, int samples) {
    std::vector<NoteEvent> events;
    for (int i = 0; i < samples; ++i) {
        r303::Sequencer::Event buffer[4];
        const int count = sequencer.tick(buffer);
        for (int e = 0; e < count; ++e) {
            events.push_back(NoteEvent{i, buffer[e].noteOn, buffer[e].note, buffer[e].velocity});
        }
    }
    return events;
}

void testFactoryPatterns() {
    for (int i = 0; i < 8; ++i) {
        const auto pattern = r303::makeDefaultPattern(i);
        check(pattern.length >= 1 && pattern.length <= 16, "factory pattern length");
        bool accent = false, slide = false;
        for (int step = 0; step < pattern.length; ++step) {
            accent = accent || pattern[step].accent;
            slide = slide || pattern[step].slide;
            check(pattern[step].note >= r303::Pattern::kLowestNote &&
                      pattern[step].note <= r303::Pattern::kHighestNote,
                  "factory note in range");
        }
        check(accent, "factory pattern has an accent");
        check(slide, "factory pattern has a slide");
    }
}

void testPresetVault() {
    using namespace r303::presetbank;
    check(makeFactoryPreset(0, 0).name == "Amber Line", "factory slot 0 keeps legacy name");
    check(makeFactoryPreset(7, 0).name == "Last Replicant", "last factory name");
    check(bankLetter(3) == "D", "bank letter");
    check(bankName(6) == "OFF WORLD", "bank name");

    const r303::Preset a = generatePreset(3, 9, 0);
    const r303::Preset b = generatePreset(3, 9, 0);
    check(a.name == b.name, "generator name deterministic");
    check(a.pattern == b.pattern, "generator pattern deterministic");
    check(std::fabs(a.cutoff - b.cutoff) < 1e-6f, "generator cutoff deterministic");

    const r303::Preset c = generatePreset(3, 10, 0);
    check(a.name != c.name || a.pattern != c.pattern, "adjacent slots differ");

    int wire[kWireSize] = {};
    toWire(a, wire, kWireSize);
    r303::Preset roundTrip;
    check(fromWire(wire, kWireSize, roundTrip), "wire decode");
    check(roundTrip.name.empty(), "wire carries no name");
    check(roundTrip.pattern == a.pattern, "wire pattern round-trip");
    check(std::fabs(roundTrip.cutoff - a.cutoff) < 0.11f, "wire cutoff round-trip");

    const r303::Preset generated = generatePreset(1, 17, 1234);
    check(!generated.name.empty() && generated.name.size() <= 64, "generated name sane");
    check(generated.pattern.length >= 1 && generated.pattern.length <= 16,
          "generated length sane");
    check(generated.distType >= 0 && generated.distType <= 4, "generated distortion type");
}

void testSequencerClock() {
    r303::Pattern pattern;
    for (auto& step : pattern.steps) {
        step.gate = true;
        step.note = 36;
        step.accent = false;
        step.slide = false;
    }
    pattern.length = 16;

    r303::Sequencer sequencer;
    sequencer.prepare(48000.0);
    sequencer.setTempo(120.0);
    sequencer.setPattern(0, pattern);
    sequencer.setCurrentPattern(0);
    sequencer.start();
    const auto events = runSequencer(sequencer, 12000);

    int noteOns = 0, noteOffs = 0;
    int firstOn = -1, firstOff = -1, secondOn = -1;
    for (const auto& event : events) {
        if (event.on) {
            ++noteOns;
            if (firstOn < 0) firstOn = event.sample;
            else if (secondOn < 0) secondOn = event.sample;
        } else {
            ++noteOffs;
            if (firstOff < 0) firstOff = event.sample;
        }
    }

    check(noteOns == 2, "two 16ths in 12000 samples at 120 BPM");
    check(firstOn == 0, "first note on beat one");
    check(secondOn == 6000, "second note on the next 16th (6000 samples)");
    check(firstOff >= 2999 && firstOff <= 3002, "50% gate releases at 3000 samples");
    check(noteOffs >= 2, "notes are released");
}

void testAccentAndSlide() {
    r303::Pattern pattern;
    for (auto& step : pattern.steps) {
        step.gate = true;
        step.note = 36;
        step.accent = false;
        step.slide = false;
    }
    pattern[0].accent = true;
    pattern[0].slide = true;
    pattern[1].note = 43;

    r303::Sequencer sequencer;
    sequencer.prepare(48000.0);
    sequencer.setTempo(120.0);
    sequencer.setPattern(0, pattern);
    sequencer.setCurrentPattern(0);
    sequencer.start();
    const auto events = runSequencer(sequencer, 6100);

    bool sawAccent = false;
    for (const auto& event : events)
        if (event.on && event.velocity >= 100) sawAccent = true;
    check(sawAccent, "accent step sends velocity >= 100");

    // The slide step starts a new note before releasing the previous one, so
    // the voice glides instead of retriggering.
    bool slideOn = false;
    for (size_t i = 0; i + 1 < events.size(); ++i) {
        if (events[i].on && events[i].note == 43 && i > 0 &&
            events[i + 1].sample == events[i].sample && !events[i + 1].on) {
            slideOn = true;
        }
    }
    check(slideOn, "slide overlaps the outgoing note");
}

void testSwing() {
    r303::Sequencer sequencer;
    sequencer.prepare(48000.0);
    sequencer.setTempo(120.0);
    r303::Pattern pattern;
    sequencer.setPattern(0, pattern);
    sequencer.setCurrentPattern(0);
    sequencer.setSwing(0.40f);
    sequencer.start();
    const auto events = runSequencer(sequencer, 13000);

    int secondOn = -1;
    int count = 0;
    for (const auto& event : events) {
        if (event.on && ++count == 2) secondOn = event.sample;
    }
    // First step is lengthened by 20% (6000 -> 7200), the second shortened.
    check(secondOn >= 7100 && secondOn <= 7300, "swing delays the off-beat 16th");
}

void testDspAndPresets() {
    r303::Engine engine;
    engine.prepare(48000.0, 512);
    engine.setMidiOutEnabled(false);
    engine.setClockSend(false);
    engine.setTransportSend(false);
    engine.loadFactoryPreset(3, 9);
    engine.setPlaying(true);

    std::vector<float> buffer(512 * 2, 0.0f);
    float peak = 0.0f;
    bool finite = true;
    for (int block = 0; block < 94; ++block) {
        engine.process(buffer.data(), 512, 0);
        for (float value : buffer) {
            finite = finite && std::isfinite(value);
            peak = std::max(peak, std::fabs(value));
        }
    }
    check(finite, "DSP output is finite for one second");
    check(peak > 0.001f, "sequencer produces audible output");
    check(peak <= 1.001f, "output stays inside the safety clip");

    for (int type = 0; type <= 4; ++type) {
        engine.setParam(r303::kParamDistType, static_cast<float>(type));
        engine.setParam(r303::kParamDistDrive, 1.0f);
        engine.setParam(r303::kParamResonance, 1.0f);
        engine.setParam(r303::kParamCutoff, 25.0f);
        engine.setParam(r303::kParamEnvMod, 1.0f);
        finite = true;
        for (int block = 0; block < 24; ++block) {
            engine.process(buffer.data(), 512, 0);
            for (float value : buffer) finite = finite && std::isfinite(value);
        }
        check(finite, "distortion type " + std::to_string(type) + " stays finite at extremes");
    }

    const r303::Preset captured = engine.capturePreset();
    check(!captured.name.empty(), "capture carries the preset name");
    check(captured.pattern.length >= 1, "capture carries the pattern");
}

void testEnginePresets() {
    r303::Engine engine;
    engine.prepare(48000.0, 512);

    const r303::Preset factory = r303::presetbank::generatePreset(3, 9, 0);
    engine.loadFactoryPreset(3, 9);
    const r303::Preset loaded = engine.capturePreset();
    check(loaded.name == factory.name, "engine loads the requested preset name");
    check(loaded.pattern == factory.pattern, "engine loads the requested preset pattern");
    check(std::fabs(loaded.cutoff - factory.cutoff) < 0.11f, "engine loads the preset voice");
    check(engine.currentPattern() == 3, "bank letter selects the pattern slot");
    check(engine.currentPresetName() == factory.name, "engine reports the loaded preset name");

    r303::Preset custom;
    custom.name = "Host Test Patch";
    custom.pattern = r303::makeDefaultPattern(5);
    custom.pattern[0].note = 48;
    custom.pattern[0].accent = true;
    custom.pattern[0].slide = true;
    custom.cutoff = 1234.0f;
    custom.distType = 4;
    engine.applyPreset(custom, 6);

    const r303::Preset captured = engine.capturePreset();
    check(captured.name == "Host Test Patch", "applied preset name round-trips");
    check(captured.pattern[0].note == 48, "applied preset note round-trips");
    check(captured.pattern[0].accent && captured.pattern[0].slide, "applied articulation round-trips");
    check(std::fabs(captured.cutoff - 1234.0f) < 0.1f, "applied cutoff round-trips");
    check(captured.distType == 4, "applied distortion model round-trips");
    check(engine.currentPattern() == 6, "applyPreset targets the requested pattern slot");

    engine.generatePreset(0, 17, 4242);
    const r303::Preset generated = r303::presetbank::generatePreset(0, 17, 4242);
    check(engine.currentPresetName() == generated.name, "GEN loads the generated name");
    check(engine.capturePreset().pattern == generated.pattern, "GEN loads the generated pattern");
}

void testPresetSwitchKeepsTheVoiceAlive() {
    // Regression: the step trigger used to write its events at the start of the
    // shared event buffer, overwriting the note-off emitted by the reset that a
    // preset load requests. The voice then kept a stuck note, every later
    // note-on was treated as legato and the section faded out.
    const int banks[] = {0, 1, 3, 5, 0, 7};
    const int slots[] = {23, 17, 0, 16, 13, 20};
    const char* names[] = {"A/24", "B/18", "D/01", "F/17", "A/14", "H/21"};

    r303::Engine engine;
    engine.prepare(48000.0, 512);
    engine.setMidiOutEnabled(false);
    engine.setClockSend(false);
    engine.setTransportSend(false);

    const int barFrames = static_cast<int>(std::lround(60.0 / 142.0 * 4.0 * 48000.0));
    std::vector<float> buffer(512 * 2, 0.0f);

    for (int section = 0; section < 6; ++section) {
        engine.loadFactoryPreset(banks[section], slots[section]);
        engine.setTempo(142.0);
        engine.setPlaying(true);

        double firstEnergy = 0.0;
        double lastEnergy = 0.0;
        int rendered = 0;
        for (int bar = 0; bar < 4; ++bar) {
            double energy = 0.0;
            long count = 0;
            for (int chunk = 0; chunk < barFrames; chunk += 512) {
                const int frames = std::min(512, barFrames - chunk);
                engine.process(buffer.data(), frames, 0);
                for (int i = 0; i < frames; ++i) {
                    energy += static_cast<double>(buffer[static_cast<size_t>(i) * 2]) *
                              buffer[static_cast<size_t>(i) * 2];
                    ++count;
                }
            }
            const double rms = std::sqrt(energy / static_cast<double>(count));
            if (bar == 0) firstEnergy = rms;
            lastEnergy = rms;
            rendered += barFrames;
        }
        check(rendered > 0, "section renders");
        check(lastEnergy > 0.005, std::string("voice stays audible after switching to ") +
                                      names[section]);
        const double ratio = lastEnergy / std::max(1.0e-9, firstEnergy);
        check(ratio > 0.45, std::string("level does not collapse in ") + names[section]);
        check(engine.heldNotes() <= 1, std::string("no stuck notes in ") + names[section]);
    }
}

void testRepeatedNoteOnReleasesTheVoice() {
    // Live "double tap" on one pitch: the second note-on replaces the first
    // stack entry, so the single note-off that follows still releases the
    // voice. With duplicate entries the release only pops one of them and the
    // tone sustains forever.
    r303::Engine engine;
    engine.prepare(48000.0, 512);
    engine.setPlaying(false);
    engine.setParam(r303::kParamDistActive, 0.0f);
    engine.setParam(r303::kParamDecay, 0.30f);
    engine.setParam(r303::kParamCutoff, 1400.0f);

    std::vector<float> buffer(512 * 2, 0.0f);
    const auto render = [&engine, &buffer](int blocks) {
        double energy = 0.0;
        for (int b = 0; b < blocks; ++b) {
            engine.process(buffer.data(), 512, 0);
            for (float value : buffer) energy += static_cast<double>(value) * value;
        }
        return std::sqrt(energy / (static_cast<double>(blocks) * 512.0 * 2.0));
    };

    engine.noteOn(36, 90);
    const double held = render(40);
    engine.noteOn(36, 90);  // repeated press on the same pitch
    engine.noteOff(36);     // the one and only release
    render(80);             // let the release finish
    const double tail = render(80);

    check(held > 0.01, "repeated note is audible while held");
    check(tail < held * 0.05, "one note-off releases a doubled note-on");
}

void testResetReleasesHeldNote() {
    r303::Sequencer sequencer;
    sequencer.prepare(48000.0);
    sequencer.setTempo(142.0);

    r303::Pattern pattern;
    for (auto& step : pattern.steps) {
        step.gate = true;
        step.note = 36;
        step.accent = false;
        step.slide = false;
    }
    sequencer.setPattern(0, pattern);
    sequencer.setCurrentPattern(0);
    sequencer.start();

    bool started = false;
    for (int i = 0; i < 16 && !started; ++i) {
        r303::Sequencer::Event events[4]{};
        const int count = sequencer.tick(events);
        for (int e = 0; e < count; ++e) started = started || events[e].noteOn;
    }
    check(started, "sequence starts");

    // While a step is sounding, a preset load asks for a reset. The very next
    // tick must emit its note-off, even though it also triggers step 0 again.
    // The buffer is zeroed so an unwritten slot can never be read as an event:
    // that is exactly how the overwrite bug used to hide.
    sequencer.requestReset();
    r303::Sequencer::Event resetEvents[4]{};
    const int resetCount = sequencer.tick(resetEvents);
    check(resetCount == 2, "reset release and restart both survive in the buffer");
    check(!resetEvents[0].noteOn && resetEvents[0].note == 36,
          "reset emits the release for the held note first");
    check(resetEvents[1].noteOn && resetEvents[1].note == 36,
          "reset then restarts the pattern");
}

void testMidiOutClock() {
    r303::Engine engine;
    engine.prepare(48000.0, 512);
    engine.setMidiOutEnabled(true);
    engine.setClockSend(true);
    engine.setTransportSend(false);
    engine.loadFactoryPreset(0, 0);
    engine.setTempo(120.0);
    engine.setPlaying(true);

    std::vector<float> buffer(256 * 2, 0.0f);
    std::vector<uint8_t> records(2048 * 16, 0);
    int clocks = 0, noteOns = 0, noteOffs = 0;
    for (int block = 0; block < 188; ++block) {
        engine.process(buffer.data(), 256, 0);
        const int count = engine.pollMidiOut(records.data(), 2048);
        for (int i = 0; i < count; ++i) {
            const uint8_t status = records[static_cast<size_t>(i) * 16];
            if (status == 0xF8) ++clocks;
            if ((status & 0xF0) == 0x90) ++noteOns;
            if ((status & 0xF0) == 0x80) ++noteOffs;
        }
    }
    check(clocks >= 46 && clocks <= 50, "24 PPQN clock at 120 BPM is ~48 pulses/s");
    check(noteOns >= 7, "sequencer sends note-ons to MIDI out");
    check(noteOffs >= 7, "sequencer sends note-offs to MIDI out");
}

void testExternalClock() {
    r303::Engine engine;
    engine.prepare(48000.0, 512);
    engine.setMidiOutEnabled(true);
    engine.setClockSend(false);
    engine.setTransportSend(false);
    engine.setClockReceive(true);
    engine.loadFactoryPreset(2, 0);  // Neon Run: every step is gated
    engine.setParam(r303::kParamTempo, 125.0f);
    engine.setPlaying(true);

    // Consume the pending preset reset before the clock arrives.
    std::vector<float> warmup(2, 0.0f);
    engine.process(warmup.data(), 1, 0);

    for (int pulse = 0; pulse < 24; ++pulse) {
        engine.pushMidi(0xF8, 0, 0, static_cast<int64_t>(pulse) * 20'000'000LL);
    }

    std::vector<float> buffer(512 * 2, 0.0f);
    engine.process(buffer.data(), 512, 0);

    std::vector<uint8_t> records(512 * 16, 0);
    const int count = engine.pollMidiOut(records.data(), 512);
    int noteOns = 0;
    for (int i = 0; i < count; ++i) {
        if ((records[static_cast<size_t>(i) * 16] & 0xF0) == 0x90) ++noteOns;
    }
    check(noteOns == 4, "24 external clock pulses advance four 16th steps");

    int status[r303::kStatusCount] = {};
    engine.getStatus(status, r303::kStatusCount);
    check(status[r303::kStatusExternalLocked] == 1, "external tempo locks");
    check(status[r303::kStatusCurrentStep] == 3, "external clock advances the step index");
}

void testCcControl() {
    r303::Engine engine;
    engine.prepare(48000.0, 512);
    const float before = engine.param(r303::kParamCutoff);
    engine.pushMidi(0xB0, 74, 127, 0);
    std::vector<float> buffer(128 * 2, 0.0f);
    engine.process(buffer.data(), 128, 0);
    const float after = engine.param(r303::kParamCutoff);
    check(after > before, "CC74 opens the filter");
    check(after > 4000.0f, "CC74 maps to the top of the cutoff range");
}

} // namespace

int main() {
    testFactoryPatterns();
    testPresetVault();
    testSequencerClock();
    testAccentAndSlide();
    testSwing();
    testDspAndPresets();
    testEnginePresets();
    testPresetSwitchKeepsTheVoiceAlive();
    testRepeatedNoteOnReleasesTheVoice();
    testResetReleasesHeldNote();
    testMidiOutClock();
    testExternalClock();
    testCcControl();

    std::printf("\n%d checks, %d failure%s\n", g_checks, g_failures,
                g_failures == 1 ? "" : "s");
    return g_failures == 0 ? 0 : 1;
}
