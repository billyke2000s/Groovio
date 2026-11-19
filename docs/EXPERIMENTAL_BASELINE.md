# Confirmed working experimental baseline

The owner confirmed that **Groovio v0.1.71 plays songs on their phone** on 18 November 2026.

- Baseline source: `6d2c55a372b2f7d6598032c78e3dee574b5e2085`
- Baseline APK: `groovio-v0.1.71.apk`
- SHA-256: `7410f75ec1c1137e7721e01468c3bd99d8634aba0772d6ed8acf83fdbf78a654`
- Experimental prerelease: `experimental-baseline-v0.1.71`

The experimental baseline APK is a byte-for-byte copy of the confirmed working release, using the same package, version and signing identity. The filename and release label change; the app does not. Reinstalling this baseline is optional if v0.1.71 is already installed.

Continue development on `experimental` from this checkpoint. Keep the current Groovio design. The Spotify screenshots reference recommendation behaviour, not a full redesign. Make focused changes and verify phone playback before promoting playback changes to main.

The exact regression in v0.1.69 has not been isolated: v0.1.71 rolled back multiple playback changes. Do not attribute it to a particular cause without evidence. Normalisation, next-URL preparation and automatic failed-track skipping remain rolled back.

This baseline publication copies the already tested/signed artifact; it does not rebuild or alter playback. Its prerelease tag does not match the stable updater's numeric version tags.

## 19 November 2026 clarification (does not change the baseline)

The note above about normalisation, next-URL preparation and skipped-track handling describes what was **rolled back in v0.1.71**. Later **experimental** source reintroduced those paths with different safeguards. They are not thereby owner-confirmed on a phone, and the confirmed v0.1.71 APK/source/hash remain unchanged. See [CURRENT_STATE](CURRENT_STATE.md). Installing this older recovery APK over a newer version may be refused by Android; uninstalling deletes app-private downloads, which JSON backup does **not** contain.
