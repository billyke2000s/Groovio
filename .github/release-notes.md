# Groovio Android release

This is the **shared stable/experimental release template**, not a version-specific changelog. For exact changes, inspect the release's tag and commit history rather than assuming every release contains every experimental feature.

- **Stable:** `v0.1.N`; the specific owner-confirmed song-playback recovery point is **v0.1.71** (18 November 2026).
- **Experimental:** `experimental-v0.1.N` prereleases may contain audio and UI changes not yet tested on a phone. The in-app updater defaults to stable unless experimental is enabled.
- Install a newer signed APK **over** the existing app. Back up the library before testing updates. JSON does not contain downloaded audio or individual play history. Android 8/9 require manual backup before an update.
- Source/network/device compatibility can vary. Automated tests and APK assembly do not prove physical playback.

[README and genuine app screenshots](https://github.com/billyke2000s/Groovio#readme) · [Current feature and verification matrix](https://github.com/billyke2000s/Groovio/blob/experimental/docs/CURRENT_STATE.md) · [Experimental history](https://github.com/billyke2000s/Groovio/blob/experimental/docs/EXPERIMENTAL_STATUS.md) · [Recovery baseline](https://github.com/billyke2000s/Groovio/blob/experimental/docs/EXPERIMENTAL_BASELINE.md)

Groovio isn't affiliated with Spotify, YouTube or YouTube Music. No Groovio account is required.
