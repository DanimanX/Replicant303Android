// Renders the promo soundtrack from the factory preset vault.
//
// Every section loads a real factory preset by bank/slot, so the names shown in
// the promo video are the same names the app shows in the vault. The renderer
// also writes a JSON manifest with the section boundaries so the video edit can
// stay in sync with the music.

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

bool writeWav(const std::string& path, const std::vector<float>& interleaved, int sampleRate) {
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

std::string escapeJson(const std::string& text) {
    std::string out;
    out.reserve(text.size() + 8);
    for (char c : text) {
        switch (c) {
            case '"': out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\n': out += "\\n"; break;
            default: out += c; break;
        }
    }
    return out;
}

struct SectionSpec {
    int bank;
    int slot;
    float swing;
    float cutoffScale;  // multiplies the preset cutoff for the whole section
    float driveScale;
};

} // namespace

int main(int argc, char** argv) {
    const std::string wavPath = argc > 1 ? argv[1] : "assets/replicant303_promo_track.wav";
    const std::string jsonPath = argc > 2 ? argv[2] : "assets/replicant303_promo_track.json";
    double bpm = argc > 3 ? std::atof(argv[3]) : 142.0;
    if (!(bpm >= 40.0 && bpm <= 300.0)) {
        std::printf("tempo %.3f is outside the engine range 40-300 BPM\n", bpm);
        return 2;
    }

    // Six vault patches chosen for contrast: dark, bright, classic acid,
    // fuzz, obsidian and a hard finale. The names come from the generator.
    const SectionSpec specs[] = {
        {0, 23, 0.00f, 0.85f, 1.00f},  // A/24 Reactor Replicant
        {1, 17, 0.10f, 1.00f, 1.05f},  // B/18 Neon Kernel
        {3, 0, 0.14f, 0.95f, 1.00f},   // D/01 Acid Rain
        {5, 16, 0.08f, 1.00f, 1.10f},  // F/17 Shadow Rain
        {0, 13, 0.00f, 0.90f, 1.00f},  // A/14 Obsidian Oscillator
        {7, 20, 0.12f, 0.95f, 1.15f},  // H/21 Mirror Factory
    };
    constexpr int kSectionCount = static_cast<int>(sizeof(specs) / sizeof(specs[0]));

    constexpr int kIntroBars = 2;
    constexpr int kSectionBars = 5;
    constexpr int kOutroBars = 2;

    constexpr double sr = 48000.0;
    const double beat = 60.0 / bpm;
    const double bar = beat * 4.0;
    const int barFrames = static_cast<int>(std::lround(bar * sr));
    const int stepFrames = barFrames / 16;
    const int introFrames = barFrames * kIntroBars;
    const int sectionFrames = barFrames * kSectionBars;
    const int outroFrames = barFrames * kOutroBars;
    const int totalFrames = introFrames + sectionFrames * kSectionCount + outroFrames;

    r303::Engine engine;
    engine.prepare(sr, 512);
    engine.setMidiOutEnabled(false);
    engine.setClockSend(false);
    engine.setTransportSend(false);

    struct RenderedSection {
        int bank;
        int slot;
        std::string name;
        double start;
        double end;
    };
    std::vector<RenderedSection> rendered;
    rendered.reserve(kSectionCount);

    std::vector<float> mix;
    mix.reserve(static_cast<size_t>(totalFrames) * 2);

    std::vector<float> block(512 * 2, 0.0f);
    double positionSeconds = 0.0;

    auto renderFrames = [&](int frames, float cutoff, float volume) {
        int remaining = frames;
        while (remaining > 0) {
            const int chunk = std::min<int>(512, remaining);
            engine.setParam(r303::kParamCutoff, cutoff);
            engine.setParam(r303::kParamVolume, volume);
            engine.process(block.data(), chunk,
                           static_cast<int64_t>(positionSeconds * 1.0e9));
            for (int i = 0; i < chunk; ++i) {
                mix.push_back(block[static_cast<size_t>(i) * 2]);
                mix.push_back(block[static_cast<size_t>(i) * 2 + 1]);
            }
            remaining -= chunk;
            positionSeconds += static_cast<double>(chunk) / sr;
        }
    };

    // Intro: the first patch with the filter opening over two bars.
    engine.loadFactoryPreset(specs[0].bank, specs[0].slot);
    engine.setTempo(bpm);
    engine.setSwing(specs[0].swing);
    engine.setPlaying(true);

    {
        const float baseCutoff = engine.param(r303::kParamCutoff);
        int done = 0;
        while (done < introFrames) {
            const int chunk = std::min<int>(512, introFrames - done);
            const float t = static_cast<float>(done) / static_cast<float>(introFrames);
            const float cutoff = std::clamp(baseCutoff * (0.35f + 0.85f * t), 60.0f, 4200.0f);
            renderFrames(chunk, cutoff, 0.55f + 0.30f * t);
            done += chunk;
        }
    }

    for (int s = 0; s < kSectionCount; ++s) {
        const auto& spec = specs[s];
        engine.loadFactoryPreset(spec.bank, spec.slot);
        engine.setTempo(bpm);
        engine.setSwing(spec.swing);

        const auto preset = engine.capturePreset();
        const int startFrame = introFrames + sectionFrames * s;
        const double start = static_cast<double>(startFrame) / sr;

        int done = 0;
        const float baseCutoff = preset.cutoff;
        const float baseVolume = 0.85f;

        while (done < sectionFrames) {
            const int chunk = std::min<int>(512, sectionFrames - done);
            const float local = static_cast<float>(done) / static_cast<float>(sectionFrames);

            // Slow filter arc: open through the first half, settle, then lift
            // again into the transition. Two different LFO rates per section
            // keep repeated presets from sounding static.
            const float phase = static_cast<float>(s) * 0.7f;
            const float arc = 0.72f + 0.30f * std::sin(local * 3.14159265f + phase * 0.3f);
            const float lift = local > 0.82f ? (local - 0.82f) / 0.18f * 0.35f : 0.0f;
            const float cutoff = std::clamp(baseCutoff * spec.cutoffScale * (arc + lift),
                                            50.0f, 4400.0f);

            // Short duck + ramp at the section boundary so preset changes land
            // as an edit instead of a click.
            float gain = baseVolume;
            const int fromStart = done;
            const int toEnd = sectionFrames - done;
            if (fromStart < 320) gain *= 0.35f + 0.65f * (fromStart / 320.0f);
            if (toEnd < 320) gain *= 0.35f + 0.65f * (toEnd / 320.0f);

            renderFrames(chunk, cutoff, gain);
            done += chunk;
        }

        const int endFrame = startFrame + sectionFrames;
        rendered.push_back(RenderedSection{
            spec.bank, spec.slot, preset.name, start,
            static_cast<double>(endFrame) / sr});
    }

    // Outro: keep the last patch but close the filter and fade.
    {
        const float baseCutoff = engine.param(r303::kParamCutoff);
        int done = 0;
        while (done < outroFrames) {
            const int chunk = std::min<int>(512, outroFrames - done);
            const float t = static_cast<float>(done) / static_cast<float>(outroFrames);
            const float cutoff = std::clamp(baseCutoff * (1.0f - 0.75f * t), 45.0f, 4200.0f);
            renderFrames(chunk, cutoff, 0.85f * (1.0f - 0.92f * t));
            done += chunk;
        }
    }

    // Fade the very start and end.
    const int fadeIn = static_cast<int>(sr * 0.35);
    const int fadeOut = static_cast<int>(sr * 1.4);
    const int frames = static_cast<int>(mix.size() / 2);
    for (int i = 0; i < frames; ++i) {
        float gain = 1.0f;
        if (i < fadeIn) gain *= static_cast<float>(i) / fadeIn;
        if (i >= frames - fadeOut) gain *= static_cast<float>(frames - i) / fadeOut;
        mix[static_cast<size_t>(i) * 2] *= gain;
        mix[static_cast<size_t>(i) * 2 + 1] *= gain;
    }

    float peak = 0.0f;
    for (float value : mix) peak = std::max(peak, std::fabs(value));
    if (peak > 0.0f) {
        const float gain = 0.62f / peak;
        for (float& value : mix) value *= gain;
    }

    if (!writeWav(wavPath, mix, static_cast<int>(sr))) {
        std::printf("could not write %s\n", wavPath.c_str());
        return 1;
    }

    std::FILE* json = std::fopen(jsonPath.c_str(), "wb");
    if (json != nullptr) {
        std::fprintf(json, "{\n  \"bpm\": %.3f,\n  \"sampleRate\": %.0f,\n  \"duration\": %.4f,\n",
                     bpm, sr, static_cast<double>(frames) / sr);
        std::fprintf(json, "  \"introEnd\": %.4f,\n  \"barSeconds\": %.4f,\n",
                     static_cast<double>(introFrames) / sr, bar);
        std::fprintf(json, "  \"sections\": [\n");
        for (size_t i = 0; i < rendered.size(); ++i) {
            const auto& section = rendered[i];
            std::fprintf(json,
                         "    {\"bank\": %d, \"slot\": %d, \"name\": \"%s\", "
                         "\"start\": %.4f, \"end\": %.4f}%s\n",
                         section.bank, section.slot, escapeJson(section.name).c_str(),
                         section.start, section.end,
                         i + 1 == rendered.size() ? "" : ",");
        }
        std::fprintf(json, "  ]\n}\n");
        std::fclose(json);
    } else {
        std::printf("could not write %s\n", jsonPath.c_str());
        return 1;
    }

    std::printf("wrote %s (%.2f s, %d sections, peak %.2f)\n", wavPath.c_str(),
                static_cast<double>(frames) / sr, kSectionCount, peak);
    for (const auto& section : rendered) {
        std::printf("  %c/%02d  %-22s  %6.2f - %6.2f\n",
                    'A' + section.bank, section.slot + 1, section.name.c_str(),
                    section.start, section.end);
    }
    return 0;
}
