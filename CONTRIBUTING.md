# Contributing to Groovio

Read [AGENTS](AGENTS.md), [DECISIONS](DECISIONS.md), [CURRENT_STATE](docs/CURRENT_STATE.md) and [EXPERIMENTAL_BASELINE](docs/EXPERIMENTAL_BASELINE.md) before changing the app.

Use **JDK 17** and **Android SDK 35**:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Automated unit/lint/build success does **not** prove phone audio. CI runs optional separate screenshot fixtures and live-network smoke checks; GitHub runner YouTube challenges are inconclusive for phone operation. Use the [device acceptance checklist](docs/OFFLINE_AND_PLAYBACK.md) and owner confirmation for audio promotion.

## Actual release workflows

- Work in focused commits on `experimental` and preserve newer code. Owner-confirmed recovery is **v0.1.71**, source `6d2c55a372b2f7d6598032c78e3dee574b5e2085`, not the newer experimental playback path.
- `.github/workflows/android.yml` tests and builds on pushes/PRs. `experimental.yml` publishes experimental prerelease APKs for app changes. `release.yml` attempts stable releases on selected `main` app/build changes with an audio-source approval guard. Documentation-only commits normally trigger neither release workflow.
- The daily `extractor-update.yml` **currently can push** a passing dependency update to `main` without phone verification. Its 19 November v0.27.1 attempt failed compilation and was not published. Do not describe this as having an enforced real-phone gate.
- `GROOVIO_BUILD_NUMBER` sets version code; the shared publish action assigns codes above stable/experimental published tags. `GROOVIO_EXPERIMENTAL=1` is used for prerelease publishing. Stable updater excludes prereleases unless the user opts in.
- `scripts/check_playback_approval.py` uses `docs/PHONE_PLAYBACK_APPROVAL.json`. Do not modify that record without explicit owner phone-test approval. A successful source-diff guard is not a universal security or behaviour guarantee.
- Updating should install a newer signed APK **over** an existing one; uninstall removes private downloads. Downgrades may be blocked.

## Documentation and security

- README uses **actual Groovio screenshots**, not mocks or generated promotional images. Explain which features are experimental rather than representing all of them as stable.
- [CURRENT_STATE](docs/CURRENT_STATE.md) is the current-source matrix; [STATUS](STATUS.md) and [EXPERIMENTAL_STATUS](docs/EXPERIMENTAL_STATUS.md) preserve dated history.
- In a **docs-only task**, do not edit Kotlin, XML, Gradle, CI workflows, signing material, assets, APKs or phone-approval records.
- The publicly committed signing identity and credentials are a known security risk, not permission to add more secrets or silently rotate the key. Plan a migration before any signer change; never print credential material.
- Keep GPL-3.0 compatibility, original Groovio branding and honest attribution. Spotify links supply metadata, not Spotify audio streaming. No mandatory paid API, accounts or backend.

Report paths modified, checks actually observed and device verification not performed.
