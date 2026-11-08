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

## Phase 3 — Online streaming (CI green; playback unverified)

| Item | State | Evidence |
|---|---|---|
| Build, unit tests, lint | Verified | CI on `phase3/streaming` (see PR) |
| YouTube Music search works on the real network | Verified | CI online smoke test: 20 real results with title, artist, duration, artwork |
| Stream resolution and playback | **Not verified** | From GitHub's servers YouTube answered "Sign in to confirm you're not a bot" (datacenter IP block). Must be tested on a phone. |
| Matcher matches the Discord bot | Verified | Kotlin port gives the same scores as the bot's JS on 7 cases |

### Added
- Search tab (YouTube Music "songs"), debounced, with retry and clear error messages.
- Tap a result: the whole result list becomes the queue, starting at that song.
- Stream URLs resolved on the loader thread at play time, cached for 4 hours, re-resolved once if YouTube rejects an expired URL.
- Best-quality selection: lossless > Opus > AAC > MP3, highest bitrate within a codec, original audio track over dubs. The player's label shows what was actually selected.
- Larger artwork (544px) for YouTube Music results.
- `TrackMatcher`: port of the bot's matching (artist/channel, title words, length, album, alt-version penalties).

### Manual device checklist (Phase 3)
1. Search "Get Lucky", tap the first result: it plays within a few seconds; label shows e.g. `OPUS · 160K · 48kHz`.
2. Next/previous move through the search results.
3. Lock screen and notification show the song's title, artist and artwork.
4. Pause for a long time (or leave overnight) and resume: it should recover by re-resolving the link.
5. Turn on airplane mode, search: you get the "Couldn't connect" message, and Try again works once back online.

## Phase 4 — Library (CI green; device checks outstanding)

| Item | State | Evidence |
|---|---|---|
| Build, lint, 36 unit tests | Verified | CI run 42506423472 |
| Database logic (likes, history, playlists, saved queue) | Verified | 9 Robolectric tests on a real in-memory Room database |
| Library UI, restore after restart, resume card, Bluetooth | **Not verified** | Manual checklist below |

### Added
- **Liked songs**: heart in the full player; list in Library with unlike.
- **Playlists**: create, rename, delete; add the current song from the player; remove songs; duplicates allowed.
- **Recently played**: each song once, newest first; history older than 180 days is trimmed.
- **Saved queue**: queue, position, shuffle and repeat are saved and restored (paused) when Groovio restarts. Android's "resume playback" card offers the same queue.
- Items from Bluetooth, car and system controllers get their URI rebuilt, so they play.
- Room schema v1 committed for future migration tests.

### Manual device checklist (Phase 4)
1. Play a song, tap the heart: it appears in Library → Liked songs. Tap again: removed.
2. Player → playlist button → New playlist: song is added. Rename and delete it from Library.
3. Play a few songs: they show in Recently played, newest first.
4. Force-stop Groovio and reopen: the mini-player shows the last song at the same position; play resumes there.
5. After force-stopping, use the media "resume" card (Android 11+ quick settings): it should play the saved queue.

## Phase 5 — Spotify links (CI green; on-device playback unverified)

| Item | State | Evidence |
|---|---|---|
| Build, lint, 42 unit tests | Verified | CI run 42507824115 |
| Reading a real Spotify album without an account or key | Verified | Online smoke test: "Random Access Memories", 13 tracks with titles, artists, durations |
| Matching Spotify songs to the right YouTube recordings | Verified (sample) | 3 of 3 sampled songs matched the correct Daft Punk tracks |
| Playing the matched songs | **Not verified** | Same YouTube datacenter block as Phase 3; test on a phone |

### Added
- Paste a Spotify song, album or playlist link in Search (or Share → Groovio from the Spotify app).
- A card shows the album/playlist and its songs; **Play** matches each song to YouTube in order: the first plays immediately, the rest are appended to the queue as they're found, with progress and a count of songs that couldn't be found.
- Matching is the Discord bot's method: YouTube Music songs first, then YouTube videos, scored on artist, title, length and alt-version words.

### Manual device checklist (Phase 5)
1. In Spotify, Share → Groovio on an album: Groovio opens Search with the album card.
2. Tap Play: music starts after the first match; the queue grows as songs are found.
3. Try a playlist with unusual songs: unmatched songs are counted, not played wrongly.

## Phase 6 — Spotify look and feel (CI green; device checks outstanding)

| Item | State | Evidence |
|---|---|---|
| Build, lint, unit tests | Verified | CI run 42511029999 |
| Screens render correctly (Home, Player, Mini-player, Queue) | Verified (sample data) | Roborazzi screenshots rendered in CI and reviewed; found and fixed black-on-dark text |
| Artist page and album tracks from YouTube Music | Verified | Online smoke test: Daft Punk → 10 songs, 5 albums; "Random Access Memories" → 13 tracks |
| Gestures, song menu, queue drag, tinted backgrounds on a phone | **Not verified** | Manual checklist below |

### Added
- Player and mini-player backgrounds tinted from the artwork; thin seek bar with a small thumb.
- Swipe down to close the player; swipe the mini-player to skip.
- Home: greeting, quick-access tiles, Recently played, "More like <song>" (YouTube related songs), Your playlists.
- Artist pages with photo header, subscriber count, popular songs and albums; album pages with play and shuffle; albums in search.
- Song menu (⋮ or long press) everywhere: like, add to playlist, play next, add to queue, go to artist.
- Queue: Now playing / Next up, drag to reorder.
- Library: filter chips, list/grid toggle, Recents/Alphabetical.
- Figtree font (free, close to Spotify's Circular), downloaded via Google Play services.

### Manual device checklist (Phase 6)
1. Player background takes the album's colour; swipe down closes it.
2. Swipe the mini-player left/right to skip.
3. Tap the artist name in the player: artist page with photo, songs, albums; open an album and play it.
4. Long-press a song: menu works (Play next shows in Queue → Next up).
5. Queue: drag a song by its handle to reorder.
6. Library: switch to grid; sort alphabetically.

## Known limitations
- No persistence yet: queue, favourites, playlists and history are lost when the app is closed (Phase 3).
- Dependencies are a year behind current releases (lint warnings); upgrade in a dedicated pass.
- Local files show the file name as title. Artist and artwork should come from embedded tags when present [not yet verified on a device].
- YouTube can block a network ("not a bot" check) or change its site; playback then fails until it lifts or NewPipe Extractor is updated.
- No lyrics (would need a licensed source; owner decision), no canvas videos.
- Queue "Next up" assumes normal order; with shuffle on, the real play order differs.
- Spotify playlists: the embed page may list only the first part of very long playlists [Unverified: not yet tested with a 100+ song playlist].
- Streams are fetched without chunked range requests; if YouTube throttles long songs on some networks, that is the next fix.
