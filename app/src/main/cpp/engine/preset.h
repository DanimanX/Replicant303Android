#pragma once

#include "pattern.h"

#include <string>

namespace r303 {

// A complete Replicant 303 preset: the acid line and the voice that plays it.
// Master volume is deliberately absent so loading a preset never jumps the
// live level.
struct Preset {
    std::string name = "Init";
    Pattern pattern;

    int waveform = 0;
    float tuning = 0.0f;
    float cutoff = 350.0f;
    float resonance = 0.65f;
    float envMod = 0.60f;
    float decay = 0.75f;
    float accent = 0.70f;

    float tempo = 125.0f;
    float swing = 0.0f;

    bool distActive = true;
    int distType = 0;
    float distDrive = 0.50f;
    float distTone = 0.60f;
    float distMix = 0.85f;

    void sanitise() noexcept;
};

namespace presetbank {

constexpr int kNumBanks = 8;
constexpr int kPresetsPerBank = 64;
constexpr int kNumPresets = kNumBanks * kPresetsPerBank;

std::string bankLetter(int bank);
std::string bankName(int bank);

// Deterministic factory content. Slot 0 is the legacy pattern for that letter;
// slots 1-63 are produced by the procedural generator.
Preset makeFactoryPreset(int bank, int slot);
Preset generatePreset(int bank, int slot, uint32_t seed);
std::string presetName(int bank, int slot);

// Fixed-width integer wire format shared with the Android UI. Values are
// scaled to keep the patch compact in SharedPreferences and .midi metadata.
constexpr int kWireSize = 80;
void toWire(const Preset& preset, int* out, int count);
bool fromWire(const int* in, int count, Preset& preset);

} // namespace presetbank
} // namespace r303
