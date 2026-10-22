# Data safety answers (Play Console → App content → Data safety)

These answers are derived directly from the shipped manifest and source code of Replicant303.

Evidence in this repository:

- Merged release manifest declares only `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS`, and `WAKE_LOCK`.
- `android.permission.INTERNET` is **NOT** declared.
- No analytics, ad networks, crash-reporting, or attribution SDKs in `app/build.gradle.kts`.
- The app operates completely offline. No network requests are made.
- Preset import/export uses standard Android file pickers, staying entirely on the local device storage.

## Section 1 — Data collection and sharing

- **Does your app collect or share any of the required user data types?** No.
- **Is all of the user data collected by your app encrypted in transit?** Not applicable (nothing leaves the device).
- **Do you provide a way for users to request that their data is deleted?** Not applicable (no account, no server-side data). Presets and settings can be cleared locally or by uninstalling the app.

## Section 2 — Data types

Leave every category unchecked:
- Location: No
- Personal info: No
- Financial info: No
- Health and fitness: No
- Messages: No
- Photos and videos: No
- Audio: No (the app *generates* synthesizer audio, it does not record user audio via microphone)
- Files and docs: No (Play defines collection as sending off-device; reading/saving local files locally is not collection)
- Calendar: No
- Contacts: No
- App activity: No
- Web browsing: No
- App info and performance: No
- Device or other IDs: No

## Section 3 — Security practices

- Data is not transferred off-device: nothing to encrypt in transit.
- No independent security review.
- No families policy applies (target audience is 13+).

## Other App content declarations in Play Console

- **Privacy policy URL**:
  `https://github.com/DanimanX/Replicant303Android/blob/main/docs/PRIVACY.md`
- **Ads**: No ads.
- **App access**: All functionality is available without credentials or login. Select "All functionality is available without special access".
- **Content rating (IARC)**: Category: Utility, Productivity, Communication or Other (Audio / Music). Answer "No" to violence, sexuality, profanity, drugs, gambling, user communication, location sharing. Resulting rating: PEGI 3 / Everyone / 3+.
- **Target audience**: 13+. Do not opt into the Designed for Families programme.
- **News app**: No.
- **COVID-19 app**: No.
- **Financial features**: No.
- **Government app**: No.
- **Health app**: No.
