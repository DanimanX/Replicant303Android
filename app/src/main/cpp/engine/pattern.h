#pragma once

#include "dsp_common.h"

#include <array>
#include <string>

namespace r303 {

// One step of a 303 pattern: the four fields mirror the hardware memory of a
// TB-303 step (gate, note, accent, slide).
struct Step {
    bool gate = true;
    int note = 36;  // C2 in this panel's labelling
    bool accent = false;
    bool slide = false;

    bool operator==(const Step& other) const noexcept {
        return gate == other.gate && note == other.note &&
               accent == other.accent && slide == other.slide;
    }
    bool operator!=(const Step& other) const noexcept { return !(*this == other); }
};

// A 16-step pattern: the unit the sequencer edits and the clock plays.
struct Pattern {
    static constexpr int kMaxSteps = 16;
    static constexpr int kLowestNote = 24;   // C1
    static constexpr int kHighestNote = 48;  // C3

    std::array<Step, kMaxSteps> steps{};
    int length = kMaxSteps;

    Step& operator[](int index) noexcept {
        return steps[static_cast<size_t>(clampi(index, 0, kMaxSteps - 1))];
    }
    const Step& operator[](int index) const noexcept {
        return steps[static_cast<size_t>(clampi(index, 0, kMaxSteps - 1))];
    }

    bool operator==(const Pattern& other) const noexcept {
        if (length != other.length) return false;
        for (int i = 0; i < kMaxSteps; ++i)
            if (steps[static_cast<size_t>(i)] != other.steps[static_cast<size_t>(i)])
                return false;
        return true;
    }
    bool operator!=(const Pattern& other) const noexcept { return !(*this == other); }

    void sanitise() noexcept {
        length = clampi(length, 1, kMaxSteps);
        for (auto& step : steps)
            step.note = clampi(step.note, kLowestNote, kHighestNote);
    }
};

inline std::string noteName(int midiNote) {
    static const char* names[] = {"C",  "C#", "D",  "D#", "E",  "F",
                                  "F#", "G",  "G#", "A",  "A#", "B"};
    const int clamped = clampi(midiNote, 0, 127);
    return std::string(names[clamped % 12]) + std::to_string(clamped / 12 - 1);
}

// Factory content: the eight acid lines that also serve as the audible
// self-test of the sequencer. Index 0 is the classic 303 line, 7 the hardest.
inline Pattern makeDefaultPattern(int index) {
    struct DefaultLine {
        const char* name;
        std::array<int, 16> notes;
        std::array<int, 16> accents;
        int numAccents;
        std::array<int, 16> slides;
        int numSlides;
        std::array<int, 16> muted;
        int numMuted;
        int length;
    };

    static const DefaultLine lines[] = {
        {"Amber Line",
         {36, 36, 48, 36, 39, 36, 43, 36, 36, 48, 36, 39, 36, 43, 36, 36},
         {1, 6, 10, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1}, 3,
         {4, 8, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1}, 2,
         {3, 11, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1}, 2, 16},
        {"Sub Level",
         {24, 24, 36, 24, 27, 24, 31, 24, 24, 36, 24, 27, 31, 24, 24, 24},
         {1, 5, 9, 13, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1}, 4,
         {2, 10, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1}, 2,
         {7, 15, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1}, 2, 16},
        {"Neon Run",
         {36, 48, 36, 43, 36, 48, 39, 36, 36, 43, 48, 36, 39, 36, 43, 36},
         {1, 3, 5, 7, 9, 11, 13, 15, -1, -1, -1, -1, -1, -1, -1, -1}, 8,
         {2, 6, 10, 14, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1}, 4,
         {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1}, 0, 16},
        {"Acid Rain",
         {24, 36, 24, 31, 24, 36, 27, 24, 31, 36, 24, 27, 24, 31, 36, 24},
         {0, 4, 8, 12, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1}, 4,
         {1, 5, 9, 13, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1}, 4,
         {6, 14, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1}, 2, 16},
        {"Blade Pulse",
         {36, 36, 39, 36, 43, 36, 46, 43, 36, 36, 39, 43, 36, 36, 31, 36},
         {3, 7, 11, 15, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1}, 4,
         {4, 12, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1}, 2,
         {1, 9, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1}, 2, 16},
        {"Rooftop Chase",
         {48, 36, 48, 39, 48, 43, 48, 36, 46, 36, 46, 39, 43, 39, 36, 36},
         {0, 2, 6, 8, 12, 14, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1}, 6,
         {2, 6, 10, 14, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1}, 4,
         {4, 12, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1}, 2, 16},
        {"Off World",
         {24, 27, 24, 36, 24, 31, 27, 24, 36, 24, 27, 31, 24, 27, 24, 24},
         {1, 5, 9, 13, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1}, 4,
         {3, 7, 11, 15, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1}, 4,
         {2, 10, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1}, 2, 16},
        {"Last Replicant",
         {36, 31, 36, 43, 36, 31, 39, 36, 36, 31, 36, 43, 46, 43, 39, 36},
         {0, 3, 6, 9, 12, 15, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1}, 6,
         {1, 5, 9, 13, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1}, 4,
         {7, 14, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1}, 2, 16},
    };

    constexpr int numLines = static_cast<int>(sizeof(lines) / sizeof(lines[0]));
    const auto& line = lines[clampi(index, 0, numLines - 1)];

    Pattern pattern;
    pattern.length = clampi(line.length, 1, Pattern::kMaxSteps);

    for (int i = 0; i < Pattern::kMaxSteps; ++i) {
        auto& step = pattern.steps[static_cast<size_t>(i)];
        step.gate = true;
        step.note = line.notes[static_cast<size_t>(i)];
        step.accent = false;
        step.slide = false;
    }

    for (int i = 0; i < line.numAccents; ++i)
        if (line.accents[static_cast<size_t>(i)] >= 0)
            pattern[line.accents[static_cast<size_t>(i)]].accent = true;

    for (int i = 0; i < line.numSlides; ++i)
        if (line.slides[static_cast<size_t>(i)] >= 0)
            pattern[line.slides[static_cast<size_t>(i)]].slide = true;

    for (int i = 0; i < line.numMuted; ++i)
        if (line.muted[static_cast<size_t>(i)] >= 0)
            pattern[line.muted[static_cast<size_t>(i)]].gate = false;

    pattern.sanitise();
    return pattern;
}

inline const char* defaultPatternName(int index) {
    static const char* names[] = {"Amber Line", "Sub Level", "Neon Run", "Acid Rain",
                                  "Blade Pulse", "Rooftop Chase", "Off World",
                                  "Last Replicant"};
    return names[clampi(index, 0, 7)];
}

} // namespace r303
