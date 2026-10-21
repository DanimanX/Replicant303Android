// Renders the promotional soundtrack straight from the native 303 engine.
// The acid line, filter movement, slides and distortion you hear in the promo
// are produced by this file, not by a sample pack.

#include "engine/engine.h"

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <string>
#include <vector>

namespace {

void writeU16(std::FILE* file, uint16_t value) {
    std::fputc(value & 0xFF, file);
    std::fputc((value >> 8) & 0xFF, file);
}

void writeU32(std::FILE* file, uint32_t value) {
    for (int i = 0; i < 4; ++i) std::fputc((value >> (8 * i)) & 0xFF, file);
}

bool writeWav(const std::string& path, const std::vector<float>& interleaved,
              int sampleRate) {
    std::FILE* file = std::fopen(path.c_str(), "wb");
    if (file == nullptr) return false;

    const uint32_t frames = static_cast<uint32_t>(interleaved.size() / 2);
    const uint32_t dataBytes = frames * 2 * 2;

    std::fwrite("RIFF", 1, 4, file);
    writeU32(file, 36 + dataBytes);
    std::fwrite("WAVEfmt ", 1, 8, file);
    writeU32(file, 16);
    writeU16(file, 1);
    writeU16(file, 2);
    writeU32(file, static_cast<uint32_t>(sampleRate));
    writeU32(file, static_cast<uint32_t>(sampleRate * 2 * 2));
    writeU16(file, 4);
    writeU16(file, 16);
    std::fwrite("data", 1, 4, file);
    writeU32(file, dataBytes);

    for (float value : interleaved) {
        const float clamped = std::clamp(value, -1.0f, 1.0f);
        const int16_t pcm = static_cast<int16_t>(std::lround(clamped * 32767.0f));
        writeU16(file, static_cast<uint16_t>(pcm));
    }

    std::fclose(file);
    return true;
}

} // namespace

int main(int argc, char** argv) {
    const std::string path = argc > 1 ? argv[1] : "assets/replicant303_android_demo.wav";
    const double seconds = argc > 2 ? std::atof(argv[2]) : 48.0;
    double bpm = argc > 3 ? std::atof(argv[3]) : 132.0;
    if (seconds < 4.0) {
        std::printf("usage: render_demo [out.wav] [seconds] [bpm]\n");
        return 1;
    }

    r303::Engine engine;
    engine.prepare(48000.0, 512);
    engine.setMidiOutEnabled(false);
    engine.setClockSend(false);
    engine.setTransportSend(false);

    struct Section {
        int bank;
        int slot;
        float swing;
    };
    // Four contrasting factory patches, each rendered for a quarter of the cut.
    const Section sections[] = {
        {3, 9, 0.0f},   // D / Acid Rain
        {0, 3, 0.12f},  // A / Chrome Ladder
        {5, 17, 0.0f},  // F / Rooftop...
        {7, 33, 0.10f}, // H / hard industrial
        {2, 5, 0.18f},  // C / bright
        {6, 49, 0.0f},  // G / off world
    };
    constexpr int kSectionCount = static_cast<int>(sizeof(sections) / sizeof(sections[0]));

    const int totalFrames = static_cast<int>(seconds * 48000.0);
    const double stepSeconds = 60.0 / bpm / 4.0;
    const int barFrames = static_cast<int>(std::lround(stepSeconds * 16.0));
    const int sectionBars = std::max(2, static_cast<int>(std::lround(seconds / 2.4 / kSectionCount)));
    const int sectionFrames = barFrames * sectionBars;

    std::vector<float> mix;
    mix.reserve(static_cast<size_t>(totalFrames) * 2);

    engine.loadFactoryPreset(sections[0].bank, sections[0].slot);
    engine.setTempo(bpm);
    engine.setSwing(sections[0].swing);
    engine.setPlaying(true);

    std::vector<float> block(512 * 2, 0.0f);
    int rendered = 0;
    int section = 0;
    int frameInSection = 0;
    const float baseCutoff = engine.param(r303::kParamCutoff);
    const float baseVolume = engine.param(r303::kParamVolume);

    while (rendered < totalFrames) {
        if (section < kSectionCount && frameInSection >= sectionFrames) {
            ++section;
            frameInSection = 0;
            if (section < kSectionCount) {
                engine.loadFactoryPreset(sections[section].bank, sections[section].slot);
                engine.setTempo(bpm);
                engine.setSwing(sections[section].swing);
                engine.setParam(r303::kParamVolume, baseVolume);
            }
        }

        const double t = static_cast<double>(rendered) / 48000.0;
        // Slow filter breathing so a static preset still feels performed.
        const float lfo = 0.5f + 0.5f * std::sin(static_cast<float>(t) * 0.55f);
        const float cutoff = std::min(4200.0f, baseCutoff * (0.72f + 0.55f * lfo));
        engine.setParam(r303::kParamCutoff, cutoff);

        const int frames = std::min<int>(512, totalFrames - rendered);
        engine.process(block.data(), frames, static_cast<int64_t>(t * 1.0e9));

        // Section-boundary fades keep preset changes click-free.
        for (int i = 0; i < frames; ++i) {
            float gain = 1.0f;
            const int fromBoundary = frameInSection + i;
            if (section > 0 && fromBoundary < 480) gain *= fromBoundary / 480.0f;
            const int toBoundary = sectionFrames - fromBoundary;
            if (toBoundary >= 0 && toBoundary < 480) gain *= toBoundary / 480.0f;

            mix.push_back(block[static_cast<size_t>(i) * 2] * gain);
            mix.push_back(block[static_cast<size_t>(i) * 2 + 1] * gain);
        }

        rendered += frames;
        frameInSection += frames;
    }

    // Fade the whole cut in and out.
    const int fadeIn = 4800;
    const int fadeOut = 19200;
    for (int i = 0; i < totalFrames; ++i) {
        float gain = 1.0f;
        if (i < fadeIn) gain *= static_cast<float>(i) / fadeIn;
        if (i >= totalFrames - fadeOut)
            gain *= static_cast<float>(totalFrames - i) / fadeOut;
        mix[static_cast<size_t>(i) * 2] *= gain;
        mix[static_cast<size_t>(i) * 2 + 1] *= gain;
    }

    float peak = 0.0f;
    for (float value : mix) peak = std::max(peak, std::fabs(value));
    if (peak > 0.0f) {
        // Leave headroom for the drum bed and the promo mix.
        const float gain = 0.58f / peak;
        for (float& value : mix) value *= gain;
    }

    if (!writeWav(path, mix, 48000)) {
        std::printf("could not write %s\n", path.c_str());
        return 1;
    }

    std::printf("wrote %s (%.1f s, %.1f BPM, %d sections, peak %.2f)\n", path.c_str(),
                seconds, bpm, kSectionCount, peak);
    return 0;
}
