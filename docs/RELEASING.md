# Releasing Replicant303

The Android executable and Play Store App Bundle (`.aab`) can be built locally or via GitHub Actions.

## Version scheme

- `versionName`: semantic version (`1.0.1`, `1.1.0`).
- `versionCode`: `major * 10000 + minor * 100 + patch` (`1.0.1` → `10001`).
- Git tags match the version name: `1.0.1` or `v1.0.1`.

## Signing Keystore

A dedicated production upload key was created in `/home/danimanx/keystores/`:

- Keystore file: `/home/danimanx/keystores/replicant303-upload.jks`
- Key alias: `replicant303-upload`
- Password file: `/home/danimanx/keystores/replicant303-upload.password`
- Base64 string for CI: `/home/danimanx/keystores/replicant303-upload.base64.txt`

## Local release build

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
export ANDROID_HOME=/tmp/playbuild/sdk
export ANDROID_SDK_ROOT=/tmp/playbuild/sdk
export REPLICANT_KEYSTORE=/home/danimanx/keystores/replicant303-upload.jks
export REPLICANT_KEYSTORE_PASSWORD="$(cat /home/danimanx/keystores/replicant303-upload.password)"
export REPLICANT_KEY_ALIAS=replicant303-upload
export REPLICANT_KEY_PASSWORD="$(cat /home/danimanx/keystores/replicant303-upload.password)"

./gradlew :app:assembleRelease :app:bundleRelease \
  -PreplicantVersionName="1.0.1" \
  -PreplicantVersionCode="10001"
```

Artifacts are copied to `dist/`:
- `Replicant303-Android-1.0.1.aab` (Upload this bundle to Google Play Console)
- `Replicant303-Android-1.0.1.apk` (Install directly on phone or tablet)
- `SHA256SUMS.txt`
- `badging.txt`

## GitHub Actions Release

Push a version tag to GitHub:

```bash
git tag 1.0.1
git push origin 1.0.1
```

Or trigger manual dispatch from GitHub Actions (`release.yml`).

For GitHub Actions to sign with the production key, set these repository secrets in GitHub (`Settings -> Secrets and variables -> Actions`):
- `REPLICANT_KEYSTORE_BASE64`: content of `/home/danimanx/keystores/replicant303-upload.base64.txt`
- `REPLICANT_KEYSTORE_PASSWORD`: content of `/home/danimanx/keystores/replicant303-upload.password`
- `REPLICANT_KEY_ALIAS`: `replicant303-upload`
- `REPLICANT_KEY_PASSWORD`: content of `/home/danimanx/keystores/replicant303-upload.password`

## Google Play Console Checklist

1. **Create App**:
   - App name: `Replicant303`
   - Default language: Spanish (Latin America) or English (United States)
   - App or game: `App`
   - Free or paid: `Free`
2. **Main store listing (Ficha de Play Store principal)**:
   - App name: `Replicant303`
   - Short description: copy from `store/listing/es-419.md` (or `en-US.md`)
   - Full description: copy from `store/listing/es-419.md` (or `en-US.md`)
   - App icon: upload `store/icon-512.png`
   - Feature graphic: upload `store/feature-graphic-1024x500.png`
   - Phone screenshots: upload PNGs from `store/screenshots/phone_*.png`
   - Tablet screenshots: upload `store/screenshots/tablet_landscape.png` and `store/screenshots/tablet_portrait.png`
3. **App content & Policy declarations**:
   - Follow answers in `store/data-safety.md`.
   - Privacy Policy URL: `https://github.com/DanimanX/Replicant303Android/blob/main/docs/PRIVACY.md`
4. **Production / Testing Release**:
   - Upload `dist/Replicant303-Android-1.0.1.aab`.
   - Google Play App Signing will automatically register the upload certificate.
   - Review and roll out release.
