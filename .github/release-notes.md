Experimental stage 1: navigation and update channels. The v0.1.71 playback service is unchanged.

- Animated full-screen search, with artwork and replay from Last Groove before typing.
- Create playlists from bottom navigation using the existing library.
- Explicit experimental update preference; stable selection excludes prereleases.
- Signed with the existing app identity. No library schema changes.

Tests, lint and APK build run before publication. Phone playback, Bluetooth, background listening and screen behaviour still need device verification; a build does not establish these work. This is an experimental prerelease, not a replacement for the confirmed baseline.

Recovery: experimental-baseline-v0.1.71 remains available. Android normally blocks downgrading version codes; export your library before testing. Installing an older APK may require uninstalling first, which removes app data and downloads.

Stage 2 additions: daily related-song mixes cached for the day and savable to the library; functional orange Home filters; YouTube podcast/book browsing and saved episodes/books; Library shortcuts; update popup; durable Spotify Save & play offline imports using idempotent original track positions. No change to ExoPlayer creation or streaming resolution in this stage.

Known gaps: saved shows/subscriptions and structured chapters are not finished. Spoken progress/speed controls and built-in audio effects are next. Recommendations currently use artist-diverse related-song seeds; genre metadata and collage artwork are not finished. README device screenshots remain outstanding.

Stage 3 audio changes (phone verification required): session equaliser with headroom and no effect allocation when disabled; opt-in gentle platform loudness levelling (Android 9+ support varies); one transient retry preserving pause intent; at most three unavailable-song skips; optional next-URL preparation isolated from the active player; separate spoken progress and speed. The Media3 renderer, sink and stream-resolution request path remain the baseline implementation.
