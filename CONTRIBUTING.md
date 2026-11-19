# Contributing to Groovio

Read [AGENTS](AGENTS.md), [DECISIONS](DECISIONS.md), [CURRENT_STATE](docs/CURRENT_STATE.md) and [EXPERIMENTAL_BASELINE](docs/EXPERIMENTAL_BASELINE.md) before changing the app.

Use **JDK 17** and **Android SDK 35**:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Automated unit/lint/build success does **not** prove phone audio. CI runs optional separate screenshot fixtures and live-network smoke checks; GitHub runner YouTube challenges are inconclusive for phone operation. Use the [device acceptance checklist](docs/OFFLINE_AND_PLAYBACK.md) and owner confirmation for audio promotion.

## Building automatically versus publishing an update

- **Every code push** still triggers the Android build workflow, running tests/lint and producing a downloadable CI artifact. CI artifacts are *not* GitHub Releases and never appear as in-app updates.
- **Neither stable nor experimental releases are automatic.** To ship an approved update, use GitHub Actions → **Publish experimental release** (on `experimental`) or **Publish stable release** (on `main`) → Run workflow. Supply 1–6 plain-English descriptions of the actual user-facing improvements (one per line).
- A release is refused if no Android app source/dependency files differ from the latest reachable tag in that channel, or if the notes are missing/technical. Builds still run unit tests, lint and APK assembly before publication; the stable playback approval guard remains in effect.
- Daily NewPipe Extractor checks are read-only compatibility tests: they **never** push to `main` or publish a release. Manually review a candidate and test audio on a phone before applying it.
- `GROOVIO_BUILD_NUMBER` sets the build version; the manual publishing action allocates above all existing stable/experimental tags. Installing new versions should happen over the existing app, not through uninstalling it.
- The last owner-confirmed playback release is v0.1.71. Do not modify `docs/PHONE_PLAYBACK_APPROVAL.json` without new explicit phone confirmation. Stable publishing still rejects unapproved audio-source changes.

## Documentation and security

- README uses **actual Groovio screenshots**, not mocks or generated promotional images. Explain which features are experimental rather than representing all of them as stable.
- [CURRENT_STATE](docs/CURRENT_STATE.md) is the current-source matrix; [STATUS](STATUS.md) and [EXPERIMENTAL_STATUS](docs/EXPERIMENTAL_STATUS.md) preserve dated history.
- In a **docs-only task**, do not edit Kotlin, XML, Gradle, CI workflows, signing material, assets, APKs or phone-approval records.
- The publicly committed signing identity and credentials are a known security risk, not permission to add more secrets or silently rotate the key. Plan a migration before any signer change; never print credential material.
- Keep GPL-3.0 compatibility, original Groovio branding and honest attribution. Spotify links supply metadata, not Spotify audio streaming. No mandatory paid API, accounts or backend.

Report paths modified, checks actually observed and device verification not performed.
