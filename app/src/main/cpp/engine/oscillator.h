#pragma once

#include "dsp_common.h"

namespace r303 {

class Oscillator303 {
public:
    Oscillator303() { reset(); }

    void prepare(double newSampleRate) noexcept;
    void reset() noexcept;

    void setWaveform(Waveform wave) noexcept { waveform = wave; }
    void setFrequency(float freqHz) noexcept;
    void setTuningSemi(float semi) noexcept;

    float process() noexcept;

private:
    static float polyBlep(float t, float dt) noexcept;

    double sampleRate = 44100.0;
    Waveform waveform = Waveform::Sawtooth;

    float targetFreq = 110.0f;
    float actualFreq = 110.0f;
    float tuningSemi = 0.0f;

    float phase = 0.0f;
    float phaseInc = 0.0f;

    // Square-wave RC droop high-pass state.
    float squareHpState = 0.0f;
    float squareHpCoeff = 0.992f;
};

} // namespace r303
