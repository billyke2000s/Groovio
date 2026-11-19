# Groovio architecture

**Current experimental source, 19 November 2026.** There is no required Groovio account, backend, VPS or Lavalink. See [CURRENT_STATE](CURRENT_STATE.md) for verification limits.

- `MainActivity` hosts `ui/GroovioApp.kt`, which handles Home, Library, Search, player, queue, Settings and detail navigation. Shared Android text can open Spotify-link import.
- `ui/home` includes real collection recents, Daily Mixes, online discovery, chart metadata and update UI; `ui/search` provides online/offline search; `ui/browse` implements **artist, album and collection pages** (not merely planned features).
- `ui/library` handles likes, playlists, menus and unified **Your music**. Legacy Downloads and Local Files routes both display `LocalFilesScreen`. `spoken` handles podcasts/books, follow/save and progress.
- `playback/PlayerViewModel` exposes MediaController state and actions, including queue and view-model-scoped sleep timer. `playback/GroovioPlaybackService` uses Media3 `MediaSessionService`/ExoPlayer with audio focus, noisy handling, session effects, saved queue and playback history.
- Online media uses `yt:<videoId>` identities and resolves through `ResolvingDataSource` on a loader thread: verified app-private download first, then a currently available online URL via `online/YouTubeMusic`/NewPipe Extractor. Local documents use `content://`. `TrackMapping` reconstructs playable MediaItems.
- `StreamSelector` selects genuine available original audio. `AudioFormatLabel` displays selected Media3 codec/bitrate/sample rate where known. Spotify public links supply metadata matched to YouTube; **Spotify protected audio is not streamed**.
- `data/LibraryDatabase` Room schema v1 stores tracks, favourites, individual play history, playlists (ordered entries can include duplicates), queue and playback state. `LibraryRepository` is its facade.
- `data/CollectionHistory` persists collection recents separately; `spoken/SpokenStore` tracks saved spoken content and progress; `data/GroovioSettings` persists preferences.
- `offline/OfflineStore` retains private originals; `AudioDownloadWorker` and `CollectionImports` manage WorkManager downloads and Spotify matching. `PublicMusicFolder` optionally copies files to `Music/Groovio/` on Android 10+ without replacing canonical private audio.
- `update/UpdateChecker` selects eligible releases; `UpdateInstaller` checks same package, signer and newer version; `UpdateSafety` writes pre-install JSON on Android 10+. JSON excludes audio and individual listening events.

**Known incomplete areas:** offline Home and global search coverage, exact spoken Library deep links, SongMenu parity in Player/Queue, repeated-song collection copying, complete cross-store backup, real-device output and exported-file tests. See [offline guide](OFFLINE_AND_PLAYBACK.md) and [action audit](UI_ACTION_PARITY_AUDIT.md).

**Lyrics (experimental):** `ui/player/LyricsOverlay.kt` opens from the full player and follows line-synchronised LRC timestamps via `lyrics/LyricsRepository.kt` and the current Media3 position; timed rows can seek, and fetched lyrics are cached privately. The provider is LRCLIB. This does not include word-by-word karaoke highlighting or guaranteed lyrics coverage.

**Not-current features:** word-by-word karaoke lyrics, smart rules-based playlists, robust account-free sharing, Android Auto browsing, home widget and automatic podcast episode refresh remain proposals.
