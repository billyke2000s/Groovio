# Contributing to Groovio

Read [AGENTS.md](AGENTS.md), [DECISIONS.md](DECISIONS.md) and the [experimental baseline](docs/EXPERIMENTAL_BASELINE.md) before editing.

Use JDK 17 and Android SDK 35, then run:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

`GROOVIO_BUILD_NUMBER` supplies the Android version code. Set `GROOVIO_EXPERIMENTAL=1` to label test builds. The publish action allocates a code above both channels' tags under shared release concurrency. Keep the existing signing identity; never print or replace its credentials.

Work on experimental in focused commits. The release workflow tests, lints and builds before publishing a signed prerelease. Stable updates exclude prereleases unless the user explicitly enables experimental updates. Do not promote audio changes to main without phone playback confirmation and owner approval.

Media3 builds and JVM audio-effect tests cannot verify real output. Follow [the device checklist](docs/EXPERIMENTAL_STATUS.md). Network smoke tests run separately: YouTube bot challenges on runner addresses are inconclusive for phones.

Do not present test fixtures, generated images or another application's screenshots as Groovio screenshots. Product screenshots must come from the real application with loaded artwork. Keep the GPL-3.0 licence and dependency attribution.
