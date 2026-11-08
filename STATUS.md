# Status

Only items with evidence are marked verified. Update this file with each phase.

## Phase 1 — Foundation (CI green; device checks outstanding)

| Item | State | Evidence |
|---|---|---|
| CI builds debug APK | Verified | CI run 42502602860: `groovio-debug-apk` artifact (18 MB) |
| Unit tests (codec labels) | Verified | Same run: 6 tests pass |
| Lint | Verified | Same run: no errors (warnings remain) |
| Local file playback on a device | **Not verified** | Needs manual test (checklist below) |
| Notification / lock-screen / Bluetooth controls | **Not verified** | Needs manual test |

### Fixed in this phase
- CI: every earlier run failed during SDK setup (`setup-android` tried to install the removed `tools` package). Packages are now explicit; unit tests and lint run before the APK build; missing APK fails the job.
- Added the Gradle wrapper (8.10.2).
- Opted in to Media3 `UnstableApi` where `Format` bitrate fields are read (lint error).
- Codec label: Media3 MIME types now map to familiar names (`audio/mp4a-latm` → AAC, `audio/mpeg` → MP3); sample rate shows `48kHz` not `48.0kHz`; uses the actually selected track in the group; falls back to average bitrate.
- Playback: audio focus handling and pause on headphone/Bluetooth disconnect.
- Picker: no crash when a provider refuses a persistable grant; shows the real file name instead of a document ID.
- Next is disabled when there is no next item; Previous restarts the track.

### Manual device checklist
1. Install the CI APK, open an MP3, a FLAC and an M4A/AAC file. Label matches the file?
2. Lock the screen: playback continues, lock-screen controls work.
3. Pull down the notification: play/pause works.
4. Bluetooth headset: play/pause button works; turning the headset off pauses playback.
5. Unplug wired headphones while playing: playback pauses.
6. Start a phone call or another audio app: Groovio pauses or ducks.

## Phase 2 — Interface (CI green; device checks outstanding)

| Item | State | Evidence |
|---|---|---|
| Build, unit tests, lint | Verified | CI run 42503888268: APK artifact, tests pass, lint no errors |
| Full player, mini-player, queue on a device | **Not verified** | Manual checklist below |

### Added
- Full-screen player: square artwork (embedded art, or fallback), title with inline actual audio label scrolling together, artist, seek bar with times, shuffle / previous / play-pause / next / repeat (off → all → one), buffering ring, output switcher, queue.
- Mini-player with progress, play/pause and next; tap opens the player.
- Queue screen: tap to play, remove items.
- Pick several files at once; they become the queue.
- Original coral accent; adaptive app icon.

### Manual device checklist (Phase 2)
1. Choose 3+ files: mini-player appears; tap it, full player slides up.
2. Long title: title and audio label scroll together.
3. Seek by dragging; times update. Shuffle and repeat icons change colour and behaviour.
4. Output button: Android 14+ shows the system output picker; older shows Bluetooth settings.
5. Queue: tap a song to play it; remove one; remove all → returns home.

## Known limitations
- No persistence yet: queue, favourites, playlists and history are lost when the app is closed (Phase 3).
- Dependencies are a year behind current releases (lint warnings); upgrade in a dedicated pass.
- Local files show the file name as title. Artist and artwork should come from embedded tags when present [not yet verified on a device].
- No online sources yet. Phase 4: YouTube Music search and on-device playback via NewPipe Extractor (owner decision, see DECISIONS.md).
