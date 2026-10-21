#pragma once

#include "dsp_common.h"

namespace r303 {

class Envelope303 {
public:
    void prepare(double newSampleRate) noexcept {
        sampleRate = std::max(1000.0, newSampleRate);
        updateCoefficients();
    }

    void reset() noexcept {
        vcfEnv = 0.0f;
        vcaEnv = 0.0f;
        accentEnv = 0.0f;
        gate = false;
        accented = false;
    }

    void setDecayTime(float seconds) noexcept {
        decayTimeSec = clampf(seconds, 0.15f, 2.8f);
        updateCoefficients();
    }

    void noteOn(bool isAccented, bool isSlide) noexcept {
        gate = true;
        accented = isAccented;

        if (!isSlide) {
            vcfEnv = 1.0f;
            vcaEnv = 1.0f;
            if (isAccented) accentEnv = 1.0f;
        } else if (isAccented && accentEnv < 0.2f) {
            accentEnv = 0.8f;
        }
    }

    void noteOff() noexcept { gate = false; }

    void process(float& outVcf, float& outVca, float& outAccent) noexcept {
        const float activeDecay = accented ? accentDecayCoeff : normalDecayCoeff;
        vcfEnv *= activeDecay;
        accentEnv *= accentDecayCoeff;
        vcaEnv *= gate ? vcaHoldDecayCoeff : vcaReleaseCoeff;

        outVcf = vcfEnv;
        outVca = vcaEnv;
        outAccent = accentEnv;
    }

    bool isActive() const noexcept { return vcaEnv > 0.0001f || gate; }

private:
    void updateCoefficients() noexcept {
        const float dt = 1.0f / static_cast<float>(sampleRate);
        normalDecayCoeff = std::exp(-dt / decayTimeSec);
        accentDecayCoeff = std::exp(-dt / 0.18f);
        vcaHoldDecayCoeff = std::exp(-dt / 4.0f);
        vcaReleaseCoeff = std::exp(-dt / 0.025f);
    }

    double sampleRate = 44100.0;
    float decayTimeSec = 0.8f;

    float normalDecayCoeff = 0.999f;
    float accentDecayCoeff = 0.999f;
    float vcaHoldDecayCoeff = 0.9999f;
    float vcaReleaseCoeff = 0.99f;

    float vcfEnv = 0.0f;
    float vcaEnv = 0.0f;
    float accentEnv = 0.0f;

    bool gate = false;
    bool accented = false;
};

} // namespace r303
