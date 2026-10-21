#pragma once

#include "dsp_common.h"

namespace r303 {

class DiodeLadderFilter {
public:
    DiodeLadderFilter() { reset(); }

    void prepare(double newSampleRate) noexcept;
    void reset() noexcept;

    void setCutoff(float cutoffHz) noexcept;
    void setResonance(float res01) noexcept;

    // envMod01: envelope amount, envVal: current VCF envelope,
    // accentVal: extra squelch from the accent envelope.
    float process(float input, float envMod01, float envVal, float accentVal) noexcept;

private:
    double sampleRate = 44100.0;
    float baseCutoffHz = 400.0f;
    float resonance = 0.5f;

    float stage[4] = {0.0f, 0.0f, 0.0f, 0.0f};

    float hpState = 0.0f;
    float hpAlpha = 0.995f;

    static constexpr float kVT = 0.026f;
    static constexpr float kInvVT = 1.0f / kVT;
};

} // namespace r303
