# Architecture

Replicant303 Android is split into a JUCE-free C++20 core and a Kotlin/Compose
application. The split is deliberate: the DSP can be built and tested on a
desktop, while Android owns the lifecycle, audio device and MIDI transport.

## Modules

### Android application

| Path | Responsibility |
|---|---|
| `MainActivity` | Immersive full-screen host, orientation, notification permission, SAF import/export launchers |
| `MainViewModel` | Engine/MIDI holders, skin state, preset import/export |
| `audio/EngineController` | JNI lifetime, polling, preset vault, session persistence, MIDI queue wrappers |
| `audio/AudioEngineService` | Foreground service, audio focus, wake lock, notification |
| `audio/NativeEngine` | Thin JNI declarations |
| `midi/MidiController` | USB MIDI enumeration, input/output ports, UMP, monitor, auto-reconnect |
| `midi/MidiStreamParser` | MIDI 1.0 byte-stream parser with running status |
| `midi/UmpCodec` | MIDI 1.0 ↔ UMP translation |
| `preset/PresetFileIO` | Preset JSON, Standard MIDI File writer/reader |
| `ui/*` | Compose panel, sequencer, vault, MIDI screen and shared controls |

### Native core

| Path | Responsibility |
|---|---|
| `engine/engine.*` | Audio callback entry point, parameter atomics, MIDI queues, clock, preset application |
| `engine/sequencer.*` | 8-pattern bank, 16th clock, swing, gate/accent/slide, external clock |
| `engine/oscillator.*` | PolyBLEP saw and RC-droop square |
| `engine/filter.*` | 4-pole diode ladder with envelope/accent modulation |
| `engine/envelope.*` | VCF, VCA and accent envelopes |
| `engine/distortion.*` | Five distortion models and tone filter |
| `engine/preset.*` | Deterministic 8 × 64 generator, wire format |
| `engine/audio_driver.*` | Oboe/AAudio stream, buffer adaptation, metrics, watchdog |
| `jni_bridge.cpp` | JNI surface |

## Threading contracts

There are three threads of interest:

1. **Audio callback** (Oboe). Owns the synth, the sequencer clock, the MIDI
   output ring consumer and the scope. It never allocates, locks or queries the
   HAL.
2. **UI thread.** Sends parameter atomics and pattern edits. Reads snapshots
   for rendering.
3. **MIDI delivery thread** (Android `MidiReceiver`). Produces MIDI input
   records and consumes MIDI output records.

### Parameters

Every synth parameter is an `std::atomic<float>`. The audio callback snapshots
them once per block and applies them to the voice. UI edits are therefore
lock-free and always audible on the next buffer.

### Patterns

Pattern edits happen under `Sequencer::patternMutex_` on the UI thread. After
an edit, the currently playing pattern is published to a seqlock:

```
publishSequence++        // odd: writer active
publishedPattern = ...   // copy
publishSequence++        // even: stable
```

The audio thread reads `publishedPattern` with a retry loop and never takes the
mutex. Pattern edits therefore cannot stall the callback.

### MIDI

- **Input:** a single-producer/single-consumer ring of 512 records. The MIDI
  thread pushes; the audio thread drains at block start.
- **Output:** a 2048-record SPSC ring. The audio thread pushes timestamped
  note/clock/transport events; Kotlin polls on its output pump.
- **Live keyboard:** a 128-command SPSC ring, so touch input never touches the
  voice directly.

### Preset apply

Preset application sets parameter atomics, sets the target pattern slot,
publishes the pattern and requests a sequencer reset. The audio thread releases
any held note and restarts the pattern on the next block.

## Audio driver

`AudioDriver::openLocked()` tries, in order:

1. AAudio, exclusive, device native rate.
2. AAudio, exclusive, 48 kHz.
3. AAudio, shared, device native rate.
4. AAudio, shared, 48 kHz.
5. Unspecified API, shared, native rate.
6. OpenSL ES, shared, 48 kHz.

It requests one burst in exclusive mode and two bursts in shared mode. If the
device reports underruns, `refreshMetrics()` grows the buffer one burst at a
time up to four. A watchdog restarts a stream that is disconnected while the
host still wants audio. Every requested and granted value is surfaced in the UI
rather than hidden.

## Signal path

```
Sequencer event
  → Synth303::noteOn/noteOff
  → Oscillator303 (saw/square)
  → DiodeLadderFilter (VCF env + accent)
  → Envelope303 (VCA)
  → DistortionUnit (5 models + tone)
  → master gain
  → soft safety clip
  → stereo float output
```

The sequencer encodes accents as velocity 110 and slides as a legato overlap,
exactly as the original engine expects.

## Preset wire format

JNI uses a fixed 80-int array for a complete preset:

| Index | Content |
|---|---|
| 0–13 | waveform, tuning×100, cutoff×10, resonance×1000, env mod×1000, decay×1000, accent×1000, tempo×10, swing×1000, dist active, dist type, drive×1000, tone×1000, mix×1000 |
| 14 | pattern length |
| 15–30 | notes |
| 31–46 | gates |
| 47–62 | accents |
| 63–78 | slides |
| 79 | reserved |

The Kotlin side converts this to JSON for storage and to a Standard MIDI File
for export. JSON adds the preset name and bank/slot metadata; the wire format
itself stays name-free so a preset can be renamed without touching the DSP.

## Persistence

- `engine` preferences: current session preset JSON, bank/slot/pattern, user
  vault overrides.
- `ui` preferences: selected skin.
- `midi` preferences: last input/output device, clock/transport toggles,
  channel filter, UMP preference.

No database, no network, no external files unless the user picks one.

## UI notes

The panel is procedurally drawn: PCB traces, silkscreen, knobs, LEDs and the
13-key keyboard are Compose primitives. The two skins come from
`SkinPalette`; no bitmap assets are required. The sequencer uses a compact
16-column editor with per-step note, accent, slide and gate state; the vault
shows 8 pages × 8 slots for the selected bank.
