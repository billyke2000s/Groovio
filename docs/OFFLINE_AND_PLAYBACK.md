# Offline library and playback

## Behaviour
- Settings keeps the existing quality selector, equalizer, autoplay, history/cache clearing and updates. Adds optional live volume normalisation (off initially), skip-unavailable songs (off initially), sleep timer, downloads toggle, unmetered-only downloads (on initially), offline mode, download management, library export and merge restore.
- Search song taps offer Play or Download & play when downloads are enabled and online. The song menu exposes the same actions. Spotify collections, albums, liked songs and local playlists have batch Download & play actions.
- Download & play starts streaming immediately and schedules saving in the background; leaving the screen doesn't cancel jobs. WorkManager persists pending work and Spotify metadata matching across process restarts; interrupted transfers restart from the beginning. Force-stop pauses Android work until the app is reopened.
- Only fully saved audio with committed metadata appears in Downloads or is selected for offline playback. Files are in app-private persistent storage; clearing artwork cache doesn't delete audio. Uninstalling clears these files.
- Unmetered-only means Android's unmetered network classification (usually Wi-Fi); metered Wi-Fi waits too. Changing the option cancels pending work; request downloads again to apply the new policy. Downloads toggle/offline mode also cancel pending jobs. Existing saved tracks remain playable.
- Downloads use validated HTTP byte ranges so a partial server response cannot be mistaken for a whole song.
- Per-song progress, waiting state, failures, cancellation and removal are in Downloads. Retry failed songs with Download & play. A per-song 512 MB / 8 minute transfer bound prevents unbounded jobs. Individual interrupted transfers retry at most twice automatically.
- Stored song IDs remain unchanged, so favourites/playlists automatically prefer local audio, even after restart. Search uses download metadata when offline. The offline banner is honest about whether any saved songs exist and links to Downloads.
- Backups include playlist ordering and duplicates, likes, track metadata and settings. They omit audio and history. Restore validates before a transaction and merges; repeated restores create playlist copies. Local file permissions do not transfer between devices.
- Next URLs are resolved in advance; Media3 handles gapless transitions for compatible sources. This is not a guarantee of sample-perfect gapless YouTube streams. Live normalisation attenuates loud PCM16 audio only; it is conservative RMS levelling, not ReplayGain/LUFS analysis or quiet-song boosting.
- Queue Next up follows Media3's actual shuffle traversal. Drag reordering is available with shuffle off. Background position is saved every five seconds and on state changes.
- Actual UK song chart metadata comes from Apple's published feed and is matched to YouTube only when played. Search shelves are labelled as searches; favourites seed a separate mix. No account/backend is added.

## Phone acceptance checks (pending)
1. Search song: Play streams; Download & play saves; Downloads shows completion. Repeat for a Spotify playlist, album and saved playlist.
2. Enable airplane mode, force-stop and reopen: Downloads lists saved songs; play all, seek, next and lock-screen controls work without network. Unsaved songs fail clearly. Offline message with zero songs does not claim music was saved.
3. Unmetered-only: mobile-data job waits; Wi-Fi starts it. Cancel an in-flight job; incomplete files don't appear. Kill process during transfer; reopening eventually retries. Simulate full storage and HTTP failure; failure is visible and retry works.
4. Remove one/all downloads; likes and playlists stay. Clear image cache; audio stays. Switching download settings cancels pending jobs.
5. Export and restore likes/playlists/settings, including duplicate playlist tracks. Malformed backup leaves the library untouched. Restore on another phone; repick local files if needed.
6. Shuffle queue order matches actual next songs. Disable shuffle; drag reorder works.
7. With normalisation on, compare loud/quiet songs and enable/disable during playback. Check headset, Bluetooth, calls/audio focus, and 30+ minute locked-screen playback with Wi-Fi/mobile handover.
8. Check actual chart card loads or is omitted when unavailable; no fabricated chart results. Verify favourites mix and clearing history.

## Verification
Focused unit tests cover validated backup merging, setting persistence, normaliser passthrough/attenuation and shuffle traversal. Android tests, lint and APK build must pass in CI. Device playback and live chart/download verification require the checks above; no device result is claimed by compilation.
