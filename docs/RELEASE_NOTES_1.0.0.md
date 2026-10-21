# Replicant 303 Android 1.0.0

The first Android release of Replicant303: the TB-303 virtual-analog acid engine,
rebuilt for touch, USB MIDI and low-latency Android audio.

## Highlights

- Native C++20 303 voice: PolyBLEP saw/square, 4-pole diode ladder, interactive
  envelopes, 60 ms slide and squelch accent.
- Five distortion models: Poco Rat, Tube Drive, Hard Clip, Germanium Fuzz,
  Wavefolder.
- 8 × 16-step sequencer with gate, accent, slide, swing and 1–16 length.
- 512-preset vault (8 banks × 64), deterministic GEN, STORE, and `.midi`
  import/export with `R303PRESET/1` patch metadata.
- USB MIDI IN/OUT, MIDI 2.0 UMP translation on Android 13+, 24 PPQN clock,
  start/stop, channel filter and live monitor.
- Oboe / AAudio low-latency path, adaptive buffer, lock-free audio callback.
- Two skins: TD-3 Green and Obsidian Red.
- Adaptive phone/tablet Compose layout.
- No network permission, no accounts, no proprietary content.

## Verification

- Host DSP suite: 206 checks passing.
- Android unit tests: MIDI stream parser (4), UMP codec (6), preset wire (3),
  preset file codec (4) — 17 total.
- Debug and release APKs built for `arm64-v8a` and `x86_64`.
- GitHub Actions: host suite and Android job both green on `main` and on tag `1.0.0`.
- Emulator verification: UI, playback, pattern edits, GEN/STORE persistence,
  skin switching and MIDI screen; AAudio stream started in shared mode.
- Promo: 48 s 1080p video generated from real emulator screenshots and a
  soundtrack rendered by the native engine.

## Known limitations

- Latency measured on the verification emulator was ~110–125 ms in shared
  AAudio mode; real-device exclusive-mode latency was not measured here. The app
  reports the actual API, sharing mode, buffer and latency.
- USB MIDI IN/OUT was verified through the Android MIDI service and host-side
  queues; physical USB MIDI hardware was not available for an end-to-end test.
- MIDI clock receive performs tempo estimation and pulse-stepped transport; it is
  not a sample-accurate phase lock to an external DAW.

## Install

```bash
adb install -r Replicant303Android-1.0.0.apk
```

Requires Android 8.0 (API 26) or newer on a 64-bit device.
