#include "filter.h"

namespace r303 {

void DiodeLadderFilter::prepare(double newSampleRate) noexcept {
    sampleRate = std::max(1000.0, newSampleRate);
    const float rc = 1.0f / (2.0f * kPi * 40.0f);
    const float dt = 1.0f / static_cast<float>(sampleRate);
    hpAlpha = rc / (rc + dt);
    reset();
}

void DiodeLadderFilter::reset() noexcept {
    for (int i = 0; i < 4; ++i) stage[i] = 0.0f;
    hpState = 0.0f;
}

void DiodeLadderFilter::setCutoff(float cutoffHz) noexcept {
    baseCutoffHz = clampf(cutoffHz, 25.0f, 4800.0f);
}

void DiodeLadderFilter::setResonance(float res01) noexcept {
    resonance = clampf(res01, 0.0f, 1.0f);
}

float DiodeLadderFilter::process(float input, float envMod01, float envVal,
                                 float accentVal) noexcept {
    const float octavesMod = envMod01 * envVal * 5.2f + accentVal * 2.2f;
    float modCutoff = baseCutoffHz * std::pow(2.0f, octavesMod);
    modCutoff = clampf(modCutoff, 25.0f, static_cast<float>(sampleRate * 0.42));

    const float wd = 2.0f * kPi * modCutoff;
    const float t = 1.0f / static_cast<float>(sampleRate);
    const float wa = (2.0f / t) * std::tan(wd * t * 0.5f);
    float g = wa * t * 0.5f;
    g = clampf(g, 0.001f, 0.95f);

    const float k = resonance * 17.2f;
    const float drive = 1.0f + resonance * 0.4f;
    const float subG = g * 0.5f;

    float out = 0.0f;
    for (int step = 0; step < 2; ++step) {
        const float fbSignal = stage[3];
        const float hpOut = hpAlpha * (hpState + fbSignal);
        hpState = hpOut - fbSignal;

        const float saturatedFeedback = fastTanh(k * hpOut);
        const float u = fastTanh(input * drive - saturatedFeedback);

        const float du0 = subG * (u - fastTanh(stage[0]));
        stage[0] = clampf(stage[0] + du0, -4.0f, 4.0f);
        const float du1 = subG * (fastTanh(stage[0]) - fastTanh(stage[1]));
        stage[1] = clampf(stage[1] + du1, -4.0f, 4.0f);
        const float du2 = subG * (fastTanh(stage[1]) - fastTanh(stage[2]));
        stage[2] = clampf(stage[2] + du2, -4.0f, 4.0f);
        const float du3 = subG * (fastTanh(stage[2]) - fastTanh(stage[3]));
        stage[3] = clampf(stage[3] + du3, -4.0f, 4.0f);
        out = stage[3];
    }

    return fastTanh(out * 1.5f);
}

} // namespace r303
