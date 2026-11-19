# Contributing to Groovio

Read [AGENTS.md](AGENTS.md), [DECISIONS.md](DECISIONS.md) and the [experimental baseline](docs/EXPERIMENTAL_BASELINE.md) before editing.

Use JDK 17 and Android SDK 35, then run:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

`GROOVIO_BUILD_NUMBER` supplies the Android version code. Set `GROOVIO_EXPERIMENTAL=1` to label test builds. The publish action allocates a code above both channels' tags under shared release concurrency. Keep the existing signing identity; never print or replace its credentials.

Work on experimental in focused commits. **Commits automatically run Android CI and create build artifacts, not in-app updates.** To publish a user-facing APK, manually run the **Publish experimental release** workflow on `experimental` or **Publish stable release** on `main` and provide 1–6 plain-English changes (one per line). This deliberately approved release must include changes to Android app source/dependencies compared with the previous release tag; docs-only, test-only and workflow-only changes cannot publish. Stable releases retain the owner-approved playback source guard. Experimental APKs remain opt-in in the app updater. Do not promote audio changes to main without phone playback confirmation and owner approval.

The scheduled NewPipe Extractor workflow only tests a prospective upgrade inside CI; it no longer pushes or creates a release. Source code is not changed on `main` without a separate explicitly approved change.

Media3 builds and JVM audio-effect tests cannot verify real output. Follow [the device checklist](docs/EXPERIMENTAL_STATUS.md). Network smoke tests run separately: YouTube bot challenges on runner addresses are inconclusive for phones.

Do not present test fixtures, generated images or another application's screenshots as Groovio screenshots. Product screenshots must come from the real application with loaded artwork. Keep the GPL-3.0 licence and dependency attribution.
