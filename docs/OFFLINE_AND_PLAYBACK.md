# Offline library and playback

**Source status: 19 November 2026 (`experimental`).** The v0.1.71 confirmed recovery build is older; the later experimental audio paths still require on-phone testing. See [CURRENT_STATE](CURRENT_STATE.md) and [EXPERIMENTAL_BASELINE](EXPERIMENTAL_BASELINE.md).

## Unified Your music

- Home, Library, Settings and offline shortcut link to **Your music**. The former Downloads and Local Files routes are retained as navigation aliases to that **same UI**. Filters include All / Downloads / On this phone, with playback, shuffle, Add music, song actions, removal confirmation and download activity.
- `OfflineStore` keeps complete private downloaded originals in persistent app files, not an evictable artwork cache. `localFile` checks for nonzero bytes, matching completion metadata and expected size; this is **not a cryptographic integrity checksum**.
- Android **10+** can optionally create a **separate** user-accessible `Music/Groovio/` copy with `PublicMusicFolder`. Public export is not required for Groovio to play a private download. An export failure no longer makes an existing verified private original into a failed download job.
- Existing associated public copies are hidden from the merged list when the original is downloaded, but can still consume physical storage. Removing private originals does not automatically delete exported files. Uninstalling the app removes private downloads.
- Imported phone audio uses Android's file picker or shared-folder scanning where permissions and OS support allow it. On Android 8–9, selected-file playback remains, but MediaStore public publishing is unavailable.

## Downloads and online playback

- WorkManager keeps bounded downloads and Spotify collection matching in background jobs. Wi-Fi-only means **Android unmetered** and is enabled by default; changes to download/connection/offline settings can cancel pending jobs without deleting already saved originals.
- The downloader saves private audio before attempting optional extra export. A failed public copy is reported separately, not retried as a missing original. Download activity shows unresolved jobs and unmatched Spotify-import tracks.
- **Known code risk:** historical failed jobs may be considered resolved using title-only matching. Two unrelated songs with the same name can make the activity status misleading.
- `ResolvingDataSource` checks an app-private `yt:` download first, then resolves an actual currently playable YouTube URL online. No fabricated or Spotify-protected streams are added.
- Offline-only mode blocks online source resolution. Home online discovery shelves are hidden, but cached personal mixes and collection shortcuts can still contain unsaved tracks. Offline global search searches private downloads, **not all imported phone music**; Your music is the dependable entry point for locally accessible files.
- Media3 manages queue, selected shuffle traversal, play controls, audio focus and saved-position checkpoints. Exact shuffle order may not survive recreation. Sleep timer currently lives in `PlayerViewModel` rather than playback service.

## Backup, restore and update safeguards

- Library JSON contains track metadata, likes and ordered playlist entries (including duplicates), plus optional settings, spoken progress/followed collections and recent **collection** history.
- **Excluded:** downloaded audio binaries, individual song-play history, durable Spotify matching manifests, and transferable local file permissions.
- Room playlists restore in a transaction; restoring settings/spoken/collection preferences happens separately afterwards, so the full operation isn't atomic. Restore merges additional playlist copies and can duplicate playlists if repeated.
- On Android 10+, the experimental in-app updater checks APK package, signing identity and strictly higher version, then writes JSON into `Downloads/Groovio/` before opening Android's installer. Backup failure blocks installation. Android 8/9 users are instructed to export manually; automatic update preparation is blocked.
- Install a newer compatible signed APK **over** the installed app, not by uninstalling first. Android may prevent a version downgrade; uninstalling would lose private downloaded audio.

## Historical playback regression (v0.1.69 to v0.1.71)

The owner reported that v0.1.69 stopped playing reliably; v0.1.71 was confirmed to play songs. Several changes were rolled back together, so the **exact root cause was never isolated**. At that checkpoint, normalisation, next-URL preparation and failed-track auto-skip were temporarily removed/hidden. They were later reintroduced under different limitations in **experimental** source, not approved as stable phone audio. Older notes referring to a separate Downloads screen or a permanent failed-job banner reflect a superseded UI stage.

For dated details and CI evidence see [EXPERIMENTAL_STATUS](EXPERIMENTAL_STATUS.md), not this guide's current behaviour section.

## Phone acceptance checklist (unverified for newer experimental source)

1. Play online, private downloaded and imported phone tracks. Repeat after airplane mode, process restart and locked-screen playback; check seeking, next/previous, Bluetooth and audio focus.
2. Download a song, an album and a Spotify-matched playlist. Test metered/unmetered constraints, cancellation, failed transfers, storage-full and retry, then check Download activity.
3. Try public export and permissions on Android 10+; verify failed export never hides private playable audio and private deletion does not delete public copies.
4. Export/restore likes, duplicate playlist tracks, settings and spoken progress. Check repeated restore and update-over-existing-data; confirm audio and individual play history remain excluded from backup.
5. Listen with EQ, normalisation and prep/skip modes off/on; check long sessions, disconnections, calls, headphone safety and buffering.
6. Navigate Home, Your music, Search and spoken Library offline; test large/small layouts and TalkBack.

**Verification policy:** CI compilation, unit tests, lint and test-fixture screenshots do not certify physical phone behaviour.
