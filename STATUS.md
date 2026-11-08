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

## Known limitations
- Single-file playback only; no queue, library or persistence yet (Phases 2–3).
- Codec label is still on its own line, not inline after the title (Phase 2).
- Title is the file name; embedded tags are not read yet (Phase 3, via MediaStore).
- No online sources. The audio-source policy for Phase 4 is undecided.
