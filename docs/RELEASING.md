# Releasing

## Version scheme

- `versionName`: semantic version (`1.0.0`, `1.1.0`, `1.1.1`).
- `versionCode`: `major * 10000 + minor * 100 + patch` (`1.0.0` → `10000`).
- Git tags match the version name without a prefix: `1.0.0`.

## First release: 1.0.0

```bash
git tag -a 1.0.0 -m "Replicant 303 Android 1.0.0"
git push origin 1.0.0
```

The Android workflow builds the host suite, unit tests, debug APK and signed
release APK, then attaches the APK to the GitHub release for the tag. If no
signing secrets are configured, the release APK is signed with the debug key so
it still installs.

## Manual release

```bash
JAVA_HOME=/path/to/jdk17 ./gradlew :app:assembleRelease
gh release create 1.0.0 \
  app/build/outputs/apk/release/app-release.apk \
  --title "Replicant 303 Android 1.0.0" \
  --notes-file docs/RELEASE_NOTES_1.0.0.md
```

## Release checklist

- [ ] Host suite passes (`./build-host/r303_tests`).
- [ ] Android unit tests pass.
- [ ] `assembleRelease` produces an APK for both ABIs.
- [ ] Install on a phone; verify the audio stream, keyboard and play/stop.
- [ ] Verify a preset loads, GEN/STORE persists, and a `.midi` export/import round-trips.
- [ ] Verify USB MIDI IN and OUT if hardware is available.
- [ ] Refresh screenshots if the UI changed.
- [ ] Refresh the promo if the feature set changed.
- [ ] Tag `1.0.0` and push the tag.
- [ ] Check the release asset and release notes.
