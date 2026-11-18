Experimental feature and audio verification build. Main and the confirmed v0.1.71 recovery tag remain unchanged.

- Animated search with Last Groove and immediate replay; Create playlist navigation.
- Persisted daily/artist/genre related-song mixes (optional MusicBrainz genre metadata), library saving and real artwork collages.
- Functional Home filters and YouTube podcast/book searches; saved playlist episode/chapter collections.
- Durable, ordered Spotify Save & play offline imports, honest matching progress, library/download artwork and truncated-file detection.
- Built-in equaliser, off by default; optional gentle platform loudness levelling (supported Android 9+ outputs).
- Bounded recovery preserving pause intent; optional unavailable-track skipping; isolated optional next preparation; separate spoken progress/speed.
- Explicit experimental update consent, popup/progress/retry and stable-publication phone confirmation guard.
- User-facing README and separate contributor guide.

Unit tests, lint and signed APK assembly run before publication. Successful builds do not prove audible playback. This build requires phone tests with effects disabled/enabled, seeking, background/lock-screen controls, Bluetooth, network handover and offline replay. YouTube blocks runner watch requests with a bot challenge; that is inconclusive for phones.

Known gaps: automatic new-episode refresh, timestamp chapters inside single uploads, exact shuffle-order restoration across process death, source/import provenance in exported backups and real device screenshots. See docs/EXPERIMENTAL_STATUS.md for evidence and limitations.

Recovery: experimental-baseline-v0.1.71 remains available. Android normally prevents APK downgrades; export the library before testing. Uninstalling removes local app data and downloaded audio. No promotion to main without phone playback confirmation and owner approval.
