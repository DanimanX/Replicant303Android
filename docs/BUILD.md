# Build

## Android

Requirements:

- JDK 17 (the Gradle wrapper is 8.11.1).
- Android SDK platform 35 and build-tools 35.
- Android NDK `27.0.12077973`.
- CMake `3.22.1` from the SDK.

```bash
echo "sdk.dir=$HOME/android-sdk" > local.properties
JAVA_HOME=/path/to/jdk17 ./gradlew :app:assembleDebug
JAVA_HOME=/path/to/jdk17 ./gradlew :app:testDebugUnitTest
JAVA_HOME=/path/to/jdk17 ./gradlew :app:assembleRelease
```

The native library is built for `arm64-v8a` and `x86_64`. The release build
uses the debug key unless the four signing environment variables are present:

```bash
export REPLICANT_KEYSTORE=/absolute/path/release.jks
export REPLICANT_KEYSTORE_PASSWORD=...
export REPLICANT_KEY_ALIAS=...
export REPLICANT_KEY_PASSWORD=...
```

## Host tests

The DSP core builds without Android or Oboe:

```bash
cmake -S tools/host -B build-host -DCMAKE_BUILD_TYPE=Release
cmake --build build-host -j
./build-host/r303_tests
```

The suite covers the pattern model, 16th clock, swing, accents, slides, external
clock, MIDI clock output, CC mapping, preset generation, the preset wire format,
engine preset loading, preset switching/reset release and DSP stability. It exits
non-zero on failure.

## Soundtrack renderers

```bash
./build-host/render_demo assets/replicant303_android_demo.wav 48 132
./build-host/render_track assets/replicant303_promo_track.wav assets/replicant303_promo_track.json 140
```

`render_demo` loads factory presets, keeps the transport running and applies a slow
filter movement. `render_track` renders the six promo sections from the factory
vault and writes a JSON manifest with the section boundaries so the video edit stays
in sync with the music. Both write 48 kHz, 16-bit stereo.

## Promo video

Requires Python 3 with `numpy`, `Pillow`, `scipy` and `ffmpeg`.

```bash
python3 tools/make_promo.py
```

The edit needs `assets/replicant303_promo_track.wav` and its JSON manifest, so run
`render_track` first (see above). Optionally refresh the screenshots with
`tools/capture_screenshots.py` on a connected emulator or phone.

It writes:

- `assets/replicant303_android_promo_1080p.mp4`
- `assets/replicant303_android_poster.png`

The screenshots in `screenshots/` are real emulator captures at 1080 × 2400
(phone) and 2560 × 1600 / 1600 × 2560 (tablet). To refresh them, install the
debug APK on an emulator and capture the SYNTH, SEQUENCE, VAULT and MIDI tabs in
both skins.

## Emulator notes

The verification emulator (`phone35`, API 35) renders with `-gpu off` in this
environment; GPU emulation modes crashed before boot. Audio opened AAudio in
shared mode with a 1920-frame buffer. That is an emulator limitation, not a
device measurement. Use a real phone with AAudio exclusive mode to evaluate the
low-latency path.

## Versioning

`versionName` defaults to `1.0.0` and `versionCode` to `10000`. CI can override
them:

```bash
./gradlew :app:assembleRelease -PreplicantVersionName=1.0.1 -PreplicantVersionCode=10001
```
