#pragma once

#include "dsp_common.h"

namespace r303 {

class DistortionUnit {
public:
    DistortionUnit() { reset(); }

    void prepare(double newSampleRate) noexcept;
    void reset() noexcept;

    void setActive(bool isActive) noexcept { active = isActive; }
    void setType(DistortionType type) noexcept { currentType = type; }
    void setDrive(float drive01) noexcept { drive = clampf(drive01, 0.0f, 1.0f); }
    void setTone(float tone01) noexcept;
    void setMix(float mix01) noexcept { mix = clampf(mix01, 0.0f, 1.0f); }

    float processSample(float input) noexcept;

private:
    float processPocoRat(float in) noexcept;
    float processTubeDrive(float in) noexcept;
    float processHardClip(float in) noexcept;
    float processGermaniumFuzz(float in) noexcept;
    float processWavefolder(float in) noexcept;

    double sampleRate = 44100.0;
    bool active = true;
    DistortionType currentType = DistortionType::PocoRat;

    float drive = 0.5f;
    float tone = 0.5f;
    float mix = 1.0f;

    float preHpState = 0.0f;
    float preHpAlpha = 0.98f;

    float postLpState = 0.0f;
    float postLpAlpha = 0.85f;

    float slewState = 0.0f;
};

} // namespace r303
