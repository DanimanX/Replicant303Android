#pragma once

// Shared math helpers for the Replicant 303 DSP core.
// This file is deliberately free of JUCE and of any Android dependency so the
// same code can be compiled by the host-side test/renderer and by the app.

#include <algorithm>
#include <cmath>
#include <cstdint>

namespace r303 {

enum class Waveform : int {
    Sawtooth = 0,
    Square = 1,
};

enum class DistortionType : int {
    PocoRat = 0,
    TubeDrive = 1,
    HardClip = 2,
    GermaniumFuzz = 3,
    Wavefolder = 4,
};

constexpr float kPi = 3.14159265358979323846f;

inline float clampf(float value, float low, float high) noexcept {
    return std::max(low, std::min(high, value));
}

inline int clampi(int value, int low, int high) noexcept {
    return std::max(low, std::min(high, value));
}

inline float midiToFreq(float midiNote) noexcept {
    return 440.0f * std::pow(2.0f, (midiNote - 69.0f) / 12.0f);
}

// Fast tanh approximation with smooth saturation.
inline float fastTanh(float x) noexcept {
    const float x2 = x * x;
    return x * (27.0f + x2) / (27.0f + 9.0f * x2);
}

// Diode non-linearity approximation: v / sqrt(1 + v^2).
inline float diodeClip(float x) noexcept {
    return x / std::sqrt(1.0f + x * x);
}

inline float hardClip(float x, float threshold = 1.0f) noexcept {
    return std::clamp(x, -threshold, threshold);
}

// Cubic soft saturation.
inline float softClip(float x) noexcept {
    if (x <= -1.5f) return -1.0f;
    if (x >= 1.5f) return 1.0f;
    return x - (4.0f / 27.0f) * (x * x * x);
}

// West-coast wavefolder.
inline float wavefold(float x, float foldAmount) noexcept {
    const float gain = 1.0f + foldAmount * 5.0f;
    const float scaled = x * gain;
    const float folded =
        4.0f * std::fabs(0.25f * scaled - std::floor(0.25f * scaled + 0.75f) + 0.25f) - 1.0f;
    return softClip(folded);
}

inline float sanitiseSample(float x) noexcept {
    if (!std::isfinite(x)) return 0.0f;
    return std::fabs(x) < 1.0e-20f ? 0.0f : x;
}

} // namespace r303
