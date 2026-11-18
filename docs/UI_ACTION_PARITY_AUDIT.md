# UI action parity audit — experimental (18 November 2026)

Scope: read-only examination of the Kotlin/Compose navigation, Home, Search, Library, Local Files, Downloads, collection/album/artist details, player/queue, spoken content, and offline download handling. This report separates code-verified wiring from phone behaviour, which still requires device testing.

## Implemented on experimental

| Surface | Change | Existing code reused |
| --- | --- | --- |
| Home collection long press (quick shortcuts, Jump back in, mixes, Your playlists) | Added **Download playlist** for eligible online tracks without auto-playing them; shows **Downloading** or **Downloaded** with a route to Downloads | `CollectionMenu`, `OfflineStore.enqueue`, `PlayerViewModel.downloadTracks(play=false)` |
| Library list/grid (saved playlists, Liked songs, recent collections) | Added long-press actions consistent with Home | `CollectionMenu` |
| Search > Last Groove recent collections | Added long-press collection actions | `CollectionMenu` |
| Collection detail header | Added a compact overflow button exposing existing collection actions | `CollectionMenu` |
| Song overflow / song long press | Changed song action to **Download song** without auto-playing; reflects saved/downloading state and links to Downloads | `SongMenu`, `PlayerViewModel.downloadTracks(play=false)` |
| Downloads list and Local Files list | Added long-press song actions, keeping existing click-to-play and remove/download-management controls | `SongMenu` |

**Removal safety:** `Remove from this section` is still available on the Home section where it has a meaningful section, but is hidden when a collection menu is opened from Library, Search, or the collection detail screen. It does not delete a playlist or audio.

**Download safety:** Only distinct `yt:` tracks are queued; Android local `content://` music does not need another download. `OfflineStore.enqueue` already skips completed tracks and uses WorkManager unique jobs to prevent duplicate work. Menu download is *download-only*, whereas the pre-existing collection-header download button retains its original *download-and-play* semantics. No audio decoder, resolver, storage schema, or backup code was changed in this pass.

## Remaining cross-surface gaps / observations

1. **Full-screen Player:** Like and Add to playlist are direct controls, but the full song action menu (Download, Play next, Add to queue) is not exposed by a single overflow here. Deliberately left unchanged to preserve the current player layout. Related file: `ui/player/PlayerScreen.kt`.
2. **Queue:** Queue tracks can be selected, removed and reordered but cannot open the shared `SongMenu`. A future change should avoid stealing the drag gesture. Related file: `ui/player/QueueScreen.kt`.
3. **Podcast/audiobook Library entries:** Tapping saved episodes/books or followed shows routes to the general spoken feed for their category instead of directly opening the selected item/collection. This is a navigation mismatch, not a cosmetic menu change. Related files: `ui/library/LibraryScreen.kt`, `spoken/SpokenScreen.kt`.
4. **Spoken-content actions:** Episode/book cards use a separate DropdownMenu (Save/remove, Download & play) rather than `SongMenu`; their progress and follow state are domain-specific. A parity review should preserve these spoken-specific actions. Related file: `spoken/SpokenDiscovery.kt`.
5. **Home while offline:** The Home shortcuts and Jump back in can still reference collections whose tracks are not downloaded. Local Files is a separate shared-folder view, so its count may differ from downloaded private audio. The large offline announcement remains. This was discussed separately and was **not** redesigned in this menu pass. Related files: `ui/GroovioApp.kt`, `ui/home/HomeScreen.kt`, `ui/library/LocalFilesScreen.kt`, `offline/OfflineStore.kt`.
6. **Copy consistency:** The Settings description still says “Show Download & play for songs and playlists”, although new context menus use separate “Download” actions. The Downloads empty/help text also primarily describes Download & play. Wording should be aligned in a small follow-up; functionality already exists. Related files: `ui/settings/SettingsScreen.kt`, `ui/library/DownloadsScreen.kt`.
7. **Album vs collection actions:** Online album details have a download/progress icon and shuffle/play buttons, but do not use `CollectionMenu`. Saved collection details do. Reusing it here requires a deliberate decision about source discovery and collection persistence, rather than treating an online album as a saved local playlist.
8. **Download status granularity:** The context menus show a downloading state and provide access to Downloads, but do not display per-playlist numeric progress. The dedicated Downloads view remains the place to inspect jobs.
9. **Disabled downloads:** Context menu download actions are hidden when downloads are switched off in Settings; if discoverability matters, a future UI could offer a clear enabled/disabled explanation rather than a no-op button.
10. **Player buffering/error screen:** The reported “Loading audio…” while offline is not established as caused by a particular menu or download path. It needs a reproducible phone test and log evidence before changing playback.

## Verification and non-goals

- This audit is based on inspected **experimental** source code and GitHub writes, not an APK running on a device.
- GitHub status/PR-run queries returned no result for the most recent write at inspection time. The local environment has neither an Android SDK nor a Gradle build environment; therefore **compilation, tests, installation and phone playback are not claimed as verified**.
- Existing `AGENTS.md` rules and `docs/EXPERIMENTAL_BASELINE.md` apply. The proven v0.1.71 baseline (commit `6d2c55a372b2f7d6598032c78e3dee574b5e2085`) remains the recovery point. No commits were made to `main` in this pass.
- No Share button, new music source, account requirement, schema migration, redesigned Home, or audio engine change was introduced.
