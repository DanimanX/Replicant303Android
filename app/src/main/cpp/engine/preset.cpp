#include "preset.h"

#include <algorithm>
#include <cmath>

namespace r303 {
void Preset::sanitise() noexcept {
    // Trim ASCII whitespace and clamp the name to 64 characters.
    const auto first = name.find_first_not_of(" \t\r\n");
    const auto last = name.find_last_not_of(" \t\r\n");
    name = first == std::string::npos ? "" : name.substr(first, last - first + 1);
    if (name.empty()) name = "Untitled";
    if (name.size() > 64) name.resize(64);

    pattern.sanitise();
    waveform = clampi(waveform, 0, 1);
    tuning = clampf(tuning, -12.0f, 12.0f);
    cutoff = clampf(cutoff, 25.0f, 4500.0f);
    resonance = clampf(resonance, 0.0f, 1.0f);
    envMod = clampf(envMod, 0.0f, 1.0f);
    decay = clampf(decay, 0.20f, 2.50f);
    accent = clampf(accent, 0.0f, 1.0f);
    tempo = clampf(tempo, 40.0f, 300.0f);
    swing = clampf(swing, 0.0f, 0.66f);
    distType = clampi(distType, 0, 4);
    distDrive = clampf(distDrive, 0.0f, 1.0f);
    distTone = clampf(distTone, 0.0f, 1.0f);
    distMix = clampf(distMix, 0.0f, 1.0f);
}

namespace presetbank {
namespace {

const char* const kBankLetters = "ABCDEFGH";
const char* const kBankNames[] = {"AMBER", "SUB", "NEON", "ACID",
                                 "BLADE", "ROOFTOP", "OFF WORLD", "LAST"};
const char* const kFactoryNames[] = {"Amber Line", "Sub Level", "Neon Run",
                                    "Acid Rain", "Blade Pulse", "Rooftop Chase",
                                    "Off World", "Last Replicant"};

const char* const kAdjectives[] = {
    "Amber", "Sub", "Neon", "Acid", "Blade", "Rooftop", "Off", "Last",
    "Chrome", "Static", "Velvet", "Rust", "Ghost", "Silver", "Crimson",
    "Electric", "Hollow", "Bright", "Dark", "Fever", "Glass", "Iron",
    "Lunar", "Magnetic", "Midnight", "Mirror", "Molten", "Obsidian",
    "Phantom", "Plastic", "Prism", "Quartz", "Radiant", "Reactor",
    "Rebel", "Redline", "Satin", "Scarlet", "Secret", "Shadow", "Silent",
    "Solar", "Sonic", "Spectral", "Steel", "Storm", "Subzero", "Sunken",
    "Tidal", "Toxic", "Turbo", "Twilight", "Ultraviolet", "Vacuum",
    "Vapor", "Velour", "Violet", "Voltage", "Wired", "Wraith", "Zero",
    "Zenith", "Cobalt", "Carbon"};

const char* const kNouns[] = {
    "Line", "Level", "Run", "Rain", "Pulse", "Chase", "World", "Replicant",
    "Circuit", "Signal", "Engine", "Wire", "Grid", "Vault", "Machine",
    "Protocol", "Sequence", "District", "Corridor", "Frequency", "Reactor",
    "Tempo", "Voltage", "Current", "Motion", "Shadow", "Mirror", "Ladder",
    "Filter", "Oscillator", "Groove", "Pattern", "Sector", "Tunnel",
    "Chamber", "Beacon", "Antenna", "Archive", "Bishop", "Cipher", "Column",
    "Compass", "Console", "Cortex", "Cradle", "Cursor", "Delta", "Divider",
    "Driver", "Echo", "Ember", "Envelope", "Fabric", "Factory", "Frame",
    "Horizon", "Kernel", "Lantern", "Lattice", "Matrix", "Meridian",
    "Monolith", "Neuron", "Orbit"};

static_assert(sizeof(kAdjectives) / sizeof(kAdjectives[0]) == 64, "adjective table");
static_assert(sizeof(kNouns) / sizeof(kNouns[0]) == 64, "noun table");

const int kScales[kNumBanks][8] = {
    {0, 3, 5, 7, 10, 12, 15, 19},
    {0, 2, 3, 5, 7, 8, 10, 12},
    {0, 2, 4, 7, 9, 12, 14, 16},
    {0, 1, 3, 5, 6, 8, 10, 12},
    {0, 3, 5, 6, 7, 10, 12, 15},
    {0, 2, 3, 5, 7, 9, 10, 12},
    {0, 2, 3, 5, 7, 8, 11, 12},
    {0, 1, 2, 4, 6, 7, 10, 12}};

const int kLengths[] = {16, 16, 16, 16, 16, 12, 8, 7};

// Deterministic PRNG (SplitMix32). The factory bank and checked-in .midi
// examples must be byte-for-byte reproducible on every platform.
struct FastRandom {
    explicit FastRandom(uint32_t seedValue) noexcept
        : state(seedValue != 0 ? seedValue : 0x30303030u) {}

    uint32_t next() noexcept {
        state += 0x9e3779b9u;
        uint32_t z = state;
        z = (z ^ (z >> 16)) * 0x85ebca6bu;
        z = (z ^ (z >> 13)) * 0xc2b2ae35u;
        return z ^ (z >> 16);
    }

    float nextFloat() noexcept {
        return static_cast<float>(next() >> 8) * (1.0f / 16777216.0f);
    }

    int nextInt(int low, int high) noexcept {
        const uint32_t span = static_cast<uint32_t>(high - low + 1);
        return low + static_cast<int>(next() % span);
    }

    float between(float low, float high) noexcept {
        return low + (high - low) * nextFloat();
    }

    bool chance(float probability) noexcept { return nextFloat() < probability; }

    uint32_t state;
};

uint32_t makeSeed(int bank, int slot, uint32_t seed) noexcept {
    return seed ^ (static_cast<uint32_t>(bank + 1) * 0x9e3779b9u) ^
           (static_cast<uint32_t>(slot + 1) * 0x85ebca6bu) ^ 0x52443303u;
}

float logBetween(FastRandom& rng, float low, float high) noexcept {
    return std::exp(rng.between(std::log(low), std::log(high)));
}

bool isFactoryName(const std::string& name) {
    for (const auto* factoryName : kFactoryNames)
        if (name == factoryName) return true;
    return false;
}

std::string makeGeneratedName(int bank, int slot) {
    const auto adjective =
        static_cast<int>((static_cast<uint32_t>(slot) * 7u +
                          static_cast<uint32_t>(bank) * 11u) % 64u);
    const auto noun =
        static_cast<int>((static_cast<uint32_t>(slot) * 17u +
                          static_cast<uint32_t>(bank) * 23u) % 64u);
    std::string name = std::string(kAdjectives[adjective]) + " " + kNouns[noun];
    if (isFactoryName(name)) name += " II";
    return name;
}

int scaled(float value, float factor) {
    return static_cast<int>(std::lround(value * factor));
}

float unscaled(int value, float factor) {
    return static_cast<float>(value) / factor;
}

} // namespace

std::string bankLetter(int bank) {
    return std::string(1, kBankLetters[clampi(bank, 0, kNumBanks - 1)]);
}

std::string bankName(int bank) {
    return kBankNames[clampi(bank, 0, kNumBanks - 1)];
}

std::string presetName(int bank, int slot) {
    const int b = clampi(bank, 0, kNumBanks - 1);
    const int s = clampi(slot, 0, kPresetsPerBank - 1);
    return s == 0 ? kFactoryNames[b] : makeGeneratedName(b, s);
}

Preset makeFactoryPreset(int bank, int slot) {
    const int b = clampi(bank, 0, kNumBanks - 1);
    const int s = clampi(slot, 0, kPresetsPerBank - 1);

    if (s == 0) {
        Preset preset;
        preset.name = kFactoryNames[b];
        preset.pattern = makeDefaultPattern(b);
        preset.sanitise();
        return preset;
    }

    return generatePreset(b, s, 0);
}

Preset generatePreset(int bank, int slot, uint32_t seed) {
    const int b = clampi(bank, 0, kNumBanks - 1);
    const int s = clampi(slot, 0, kPresetsPerBank - 1);

    FastRandom rng(makeSeed(b, s, seed));

    Preset preset;
    preset.name = makeGeneratedName(b, s);
    if (s == 0) preset.name = std::string(kFactoryNames[b]) + " " + std::to_string(1 + seed % 9u);

    // Acid line.
    const auto& scale = kScales[b];
    const int root = 24 + rng.nextInt(0, 12);
    const int length =
        kLengths[rng.nextInt(0, static_cast<int>(sizeof(kLengths) / sizeof(kLengths[0])) - 1)];

    preset.pattern.length = length;

    const float density = rng.between(0.62f, 0.94f);
    const float accentChance = rng.between(0.16f, 0.38f);
    const float slideChance = rng.between(0.10f, 0.32f);

    int previousNote = root + scale[0];
    for (int i = 0; i < Pattern::kMaxSteps; ++i) {
        auto& step = preset.pattern[i];
        step.gate = (i == 0) || rng.chance(density);

        const int degree = rng.nextInt(0, 7);
        int octave = 0;
        if (rng.chance(0.16f)) octave = 12;
        if (rng.chance(0.08f)) octave = -12;

        int note = root + scale[degree] + octave;
        if (rng.chance(0.12f)) note = previousNote;

        step.note = clampi(note, Pattern::kLowestNote, Pattern::kHighestNote);
        previousNote = step.note;
        step.accent = step.gate && rng.chance(accentChance);
        step.slide = step.gate && rng.chance(slideChance);
    }

    int gates = 0;
    for (int i = 0; i < length; ++i)
        if (preset.pattern[i].gate) ++gates;

    for (int guard = 0; guard < 64 && gates < 4; ++guard) {
        const int i = rng.nextInt(0, length - 1);
        if (!preset.pattern[i].gate) {
            preset.pattern[i].gate = true;
            ++gates;
        }
    }

    bool allSameNote = true;
    for (int i = 1; i < length; ++i)
        if (preset.pattern[i].note != preset.pattern[0].note) allSameNote = false;

    if (allSameNote) {
        preset.pattern[length / 2].note = clampi(preset.pattern[0].note + scale[3],
                                                 Pattern::kLowestNote, Pattern::kHighestNote);
        preset.pattern[length - 1].note = clampi(preset.pattern[0].note + scale[5],
                                                 Pattern::kLowestNote, Pattern::kHighestNote);
    }

    bool anyAccent = false;
    for (int i = 0; i < length; ++i) anyAccent = anyAccent || preset.pattern[i].accent;
    if (!anyAccent) preset.pattern[0].accent = true;

    // Voice archetypes: bank/slot walks eight distinct characters.
    const int archetype = (b + s) % 8;
    switch (archetype) {
        case 0:  // Classic acid
            preset.waveform = 0;
            preset.cutoff = logBetween(rng, 170.0f, 900.0f);
            preset.resonance = rng.between(0.55f, 0.84f);
            preset.envMod = rng.between(0.50f, 0.82f);
            preset.decay = rng.between(0.38f, 0.90f);
            preset.accent = rng.between(0.60f, 0.85f);
            preset.distActive = true;
            preset.distType = 0;
            preset.distDrive = rng.between(0.28f, 0.62f);
            preset.distTone = rng.between(0.45f, 0.75f);
            preset.distMix = rng.between(0.62f, 0.90f);
            break;
        case 1:  // Sub pressure
            preset.waveform = 0;
            preset.cutoff = logBetween(rng, 70.0f, 380.0f);
            preset.resonance = rng.between(0.30f, 0.68f);
            preset.envMod = rng.between(0.30f, 0.68f);
            preset.decay = rng.between(0.65f, 1.45f);
            preset.accent = rng.between(0.55f, 0.82f);
            preset.distActive = true;
            preset.distType = rng.chance(0.70f) ? 0 : 2;
            preset.distDrive = rng.between(0.18f, 0.48f);
            preset.distTone = rng.between(0.25f, 0.55f);
            preset.distMix = rng.between(0.55f, 0.82f);
            break;
        case 2:  // Bright / open
            preset.waveform = rng.chance(0.75f) ? 1 : 0;
            preset.cutoff = logBetween(rng, 800.0f, 2600.0f);
            preset.resonance = rng.between(0.35f, 0.78f);
            preset.envMod = rng.between(0.68f, 0.95f);
            preset.decay = rng.between(0.20f, 0.52f);
            preset.accent = rng.between(0.50f, 0.80f);
            preset.distActive = true;
            preset.distType = rng.chance(0.65f) ? 1 : 0;
            preset.distDrive = rng.between(0.15f, 0.42f);
            preset.distTone = rng.between(0.62f, 0.90f);
            preset.distMix = rng.between(0.35f, 0.68f);
            break;
        case 3:  // Screamer
            preset.waveform = 0;
            preset.cutoff = logBetween(rng, 280.0f, 1300.0f);
            preset.resonance = rng.between(0.68f, 0.94f);
            preset.envMod = rng.between(0.58f, 0.90f);
            preset.decay = rng.between(0.22f, 0.62f);
            preset.accent = rng.between(0.65f, 0.92f);
            preset.distActive = true;
            preset.distType = rng.chance(0.60f) ? 2 : 0;
            preset.distDrive = rng.between(0.58f, 0.92f);
            preset.distTone = rng.between(0.40f, 0.78f);
            preset.distMix = rng.between(0.72f, 1.0f);
            break;
        case 4:  // Germanium fuzz
            preset.waveform = rng.chance(0.70f) ? 1 : 0;
            preset.cutoff = logBetween(rng, 200.0f, 1000.0f);
            preset.resonance = rng.between(0.45f, 0.80f);
            preset.envMod = rng.between(0.48f, 0.86f);
            preset.decay = rng.between(0.30f, 0.85f);
            preset.accent = rng.between(0.55f, 0.85f);
            preset.distActive = true;
            preset.distType = 3;
            preset.distDrive = rng.between(0.48f, 0.90f);
            preset.distTone = rng.between(0.30f, 0.68f);
            preset.distMix = rng.between(0.60f, 0.95f);
            break;
        case 5:  // Wavefolder
            preset.waveform = 0;
            preset.cutoff = logBetween(rng, 380.0f, 1900.0f);
            preset.resonance = rng.between(0.42f, 0.82f);
            preset.envMod = rng.between(0.62f, 0.94f);
            preset.decay = rng.between(0.25f, 0.72f);
            preset.accent = rng.between(0.55f, 0.88f);
            preset.distActive = true;
            preset.distType = 4;
            preset.distDrive = rng.between(0.38f, 0.86f);
            preset.distTone = rng.between(0.42f, 0.82f);
            preset.distMix = rng.between(0.50f, 0.90f);
            break;
        case 6:  // Clean / dry
            preset.waveform = rng.chance(0.55f) ? 0 : 1;
            preset.cutoff = logBetween(rng, 650.0f, 2200.0f);
            preset.resonance = rng.between(0.18f, 0.55f);
            preset.envMod = rng.between(0.38f, 0.78f);
            preset.decay = rng.between(0.28f, 0.72f);
            preset.accent = rng.between(0.42f, 0.75f);
            preset.distActive = rng.chance(0.35f);
            preset.distType = rng.nextInt(0, 4);
            preset.distDrive = rng.between(0.10f, 0.38f);
            preset.distTone = rng.between(0.50f, 0.85f);
            preset.distMix = rng.between(0.20f, 0.55f);
            break;
        default:  // Hard / industrial
            preset.waveform = 0;
            preset.cutoff = logBetween(rng, 140.0f, 720.0f);
            preset.resonance = rng.between(0.60f, 0.94f);
            preset.envMod = rng.between(0.70f, 0.97f);
            preset.decay = rng.between(0.20f, 0.48f);
            preset.accent = rng.between(0.72f, 0.96f);
            preset.distActive = true;
            preset.distType = rng.chance(0.68f) ? 0 : 2;
            preset.distDrive = rng.between(0.70f, 1.0f);
            preset.distTone = rng.between(0.32f, 0.70f);
            preset.distMix = rng.between(0.78f, 1.0f);
            break;
    }

    preset.tempo = std::round(rng.between(116.0f, 150.0f) * 2.0f) * 0.5f;
    preset.swing = rng.chance(0.55f) ? 0.0f : rng.between(0.04f, 0.30f);
    preset.tuning = rng.chance(0.10f) ? (rng.chance(0.5f) ? -12.0f : 12.0f) : 0.0f;

    preset.sanitise();
    return preset;
}

void toWire(const Preset& preset, int* out, int count) {
    if (out == nullptr || count < kWireSize) return;
    for (int i = 0; i < kWireSize; ++i) out[i] = 0;

    out[0] = preset.waveform;
    out[1] = scaled(preset.tuning, 100.0f);
    out[2] = scaled(preset.cutoff, 10.0f);
    out[3] = scaled(preset.resonance, 1000.0f);
    out[4] = scaled(preset.envMod, 1000.0f);
    out[5] = scaled(preset.decay, 1000.0f);
    out[6] = scaled(preset.accent, 1000.0f);
    out[7] = scaled(preset.tempo, 10.0f);
    out[8] = scaled(preset.swing, 1000.0f);
    out[9] = preset.distActive ? 1 : 0;
    out[10] = preset.distType;
    out[11] = scaled(preset.distDrive, 1000.0f);
    out[12] = scaled(preset.distTone, 1000.0f);
    out[13] = scaled(preset.distMix, 1000.0f);
    out[14] = preset.pattern.length;

    for (int i = 0; i < Pattern::kMaxSteps; ++i) {
        out[15 + i] = preset.pattern[i].note;
        out[31 + i] = preset.pattern[i].gate ? 1 : 0;
        out[47 + i] = preset.pattern[i].accent ? 1 : 0;
        out[63 + i] = preset.pattern[i].slide ? 1 : 0;
    }
}

bool fromWire(const int* in, int count, Preset& preset) {
    if (in == nullptr || count < kWireSize) return false;

    Preset result;
    result.waveform = in[0];
    result.tuning = unscaled(in[1], 100.0f);
    result.cutoff = unscaled(in[2], 10.0f);
    result.resonance = unscaled(in[3], 1000.0f);
    result.envMod = unscaled(in[4], 1000.0f);
    result.decay = unscaled(in[5], 1000.0f);
    result.accent = unscaled(in[6], 1000.0f);
    result.tempo = unscaled(in[7], 10.0f);
    result.swing = unscaled(in[8], 1000.0f);
    result.distActive = in[9] != 0;
    result.distType = in[10];
    result.distDrive = unscaled(in[11], 1000.0f);
    result.distTone = unscaled(in[12], 1000.0f);
    result.distMix = unscaled(in[13], 1000.0f);
    result.pattern.length = in[14];

    for (int i = 0; i < Pattern::kMaxSteps; ++i) {
        result.pattern[i].note = in[15 + i];
        result.pattern[i].gate = in[31 + i] != 0;
        result.pattern[i].accent = in[47 + i] != 0;
        result.pattern[i].slide = in[63 + i] != 0;
    }

    result.sanitise();
    result.name.clear();
    preset = result;
    return true;
}

} // namespace presetbank
} // namespace r303
