#include "distortion.h"

namespace r303 {

void DistortionUnit::prepare(double newSampleRate) noexcept {
    sampleRate = std::max(1000.0, newSampleRate);
    const float rc = 1.0f / (2.0f * kPi * 450.0f);
    const float dt = 1.0f / static_cast<float>(sampleRate);
    preHpAlpha = rc / (rc + dt);
    setTone(tone);
    reset();
}

void DistortionUnit::reset() noexcept {
    preHpState = 0.0f;
    postLpState = 0.0f;
    slewState = 0.0f;
}

void DistortionUnit::setTone(float tone01) noexcept {
    tone = clampf(tone01, 0.0f, 1.0f);
    const float cutoffHz = 800.0f * std::pow(20.0f, tone);
    const float rc = 1.0f / (2.0f * kPi * cutoffHz);
    const float dt = 1.0f / static_cast<float>(sampleRate);
    postLpAlpha = dt / (rc + dt);
}

float DistortionUnit::processPocoRat(float in) noexcept {
    const float hpOut = preHpAlpha * (preHpState + in);
    preHpState = hpOut - in;

    const float gain = 1.0f + drive * drive * 40.0f;
    const float amplified = hpOut * gain;

    const float maxSlew = 0.18f + (1.0f - drive) * 0.40f;
    const float diff = amplified - slewState;
    slewState += std::clamp(diff, -maxSlew, maxSlew);

    return fastTanh(slewState * 1.6f) * 0.85f;
}

float DistortionUnit::processTubeDrive(float in) noexcept {
    const float gain = 1.0f + drive * 24.0f;
    float sat = softClip(in * gain);
    if (sat > 0.0f) sat = std::pow(sat, 0.94f);
    return sat * 0.90f;
}

float DistortionUnit::processHardClip(float in) noexcept {
    const float gain = 1.0f + drive * 35.0f;
    const float clipped = hardClip(in * gain * 0.8f, 0.95f);
    return fastTanh(clipped * 1.2f) * 0.85f;
}

float DistortionUnit::processGermaniumFuzz(float in) noexcept {
    const float gain = 1.0f + drive * 45.0f;
    const float biased = in * gain + 0.18f * (drive * drive);
    const float clipped = biased > 0.0f ? diodeClip(biased * 1.4f)
                                        : fastTanh(biased * 2.1f);
    return (clipped - 0.12f * drive) * 0.80f;
}

float DistortionUnit::processWavefolder(float in) noexcept {
    return wavefold(in, drive) * 0.80f;
}

float DistortionUnit::processSample(float input) noexcept {
    if (!active) return input;

    float wet = 0.0f;
    switch (currentType) {
        case DistortionType::PocoRat: wet = processPocoRat(input); break;
        case DistortionType::TubeDrive: wet = processTubeDrive(input); break;
        case DistortionType::HardClip: wet = processHardClip(input); break;
        case DistortionType::GermaniumFuzz: wet = processGermaniumFuzz(input); break;
        case DistortionType::Wavefolder: wet = processWavefolder(input); break;
    }

    postLpState += postLpAlpha * (wet - postLpState);
    wet = postLpState;
    return (1.0f - mix) * input + mix * wet;
}

} // namespace r303
