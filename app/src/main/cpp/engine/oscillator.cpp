#include "oscillator.h"

namespace r303 {

void Oscillator303::prepare(double newSampleRate) noexcept {
    sampleRate = std::max(1000.0, newSampleRate);
    const float rc = 1.0f / (2.0f * kPi * 25.0f);
    const float dt = 1.0f / static_cast<float>(sampleRate);
    squareHpCoeff = rc / (rc + dt);
    setFrequency(targetFreq);
}

void Oscillator303::reset() noexcept {
    phase = 0.0f;
    squareHpState = 0.0f;
}

void Oscillator303::setFrequency(float freqHz) noexcept {
    targetFreq = freqHz;
    const float tuned = targetFreq * std::pow(2.0f, tuningSemi / 12.0f);
    actualFreq = clampf(tuned, 10.0f, static_cast<float>(sampleRate * 0.45));
    phaseInc = actualFreq / static_cast<float>(sampleRate);
}

void Oscillator303::setTuningSemi(float semi) noexcept {
    tuningSemi = semi;
    setFrequency(targetFreq);
}

float Oscillator303::polyBlep(float t, float dt) noexcept {
    if (dt <= 0.0f) return 0.0f;
    if (t < dt) {
        t /= dt;
        return t + t - t * t - 1.0f;
    }
    if (t > 1.0f - dt) {
        t = (t - 1.0f) / dt;
        return t * t + t + t + 1.0f;
    }
    return 0.0f;
}

float Oscillator303::process() noexcept {
    phase += phaseInc;
    if (phase >= 1.0f) phase -= 1.0f;

    const float dt = phaseInc;

    if (waveform == Waveform::Sawtooth) {
        const float naiveSaw = 2.0f * phase - 1.0f;
        const float curvedSaw = naiveSaw - 0.15f * (naiveSaw * naiveSaw - 1.0f);
        return (curvedSaw - polyBlep(phase, dt)) * 0.95f;
    }

    float naiveSquare = (phase < 0.485f) ? 1.0f : -1.0f;
    naiveSquare += polyBlep(phase, dt);
    float shifted = phase - 0.485f;
    if (shifted < 0.0f) shifted += 1.0f;
    naiveSquare -= polyBlep(shifted, dt);

    const float hpOut = squareHpCoeff * (squareHpState + naiveSquare);
    squareHpState = hpOut - naiveSquare;
    return hpOut * 0.90f;
}

} // namespace r303
