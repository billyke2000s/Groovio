# Groovio current state — 19 November 2026

This page covers source inspected on **experimental**. **Implemented code is not the same as a stable release or phone-tested playback.** Keep dated historical progress in [EXPERIMENTAL_STATUS](EXPERIMENTAL_STATUS.md).

## Release evidence

| Channel | What has actually been established |
| --- | --- |
| Stable recovery | Owner confirmed **v0.1.71** plays songs on their phone on 18 November 2026, source `6d2c55a372b2f7d6598032c78e3dee574b5e2085`. This is not all-feature certification. |
| Latest stable APK at inspection | **v0.1.71**. The stable publish action checks audio-related diffs against `PHONE_PLAYBACK_APPROVAL.json`. |
| Experimental source | Newer player, offline, library, spoken, update and interface features. The combined source has not received an equivalent new owner playback approval. |
| Branches | Before this docs-only commit, `main` and `experimental` were separate commits pointing to **identical Git trees**. Main-branch presence does not imply phone-tested stable release. |
| Automated testing | [18 November Android CI](https://github.com/billyke2000s/Groovio/actions/runs/44422540191) passed. It cannot verify physical output. |
| Automatic extractor update | [19 November run](https://github.com/billyke2000s/Groovio/actions/runs/44544010480) attempted NewPipe v0.27.1; compilation failed due to removed Android/iOS streaming URL and user-agent helpers. No push or release resulted. |

[Stable recovery release](https://github.com/billyke2000s/Groovio/releases/tag/v0.1.71) · [All releases](https://github.com/billyke2000s/Groovio/releases) · [Baseline](EXPERIMENTAL_BASELINE.md)

## Newer experimental implementation

- **Online:** YouTube / YouTube Music via on-device NewPipe extraction, artist/album pages, recommendations, Daily Mixes and real UK chart metadata. Spotify links are read as **metadata** and matched to available YouTube audio, not Spotify streaming.
- **Playback:** Media3 session/background controls, queue/shuffle/repeat, resume, inline selected-codec labels, artwork/mini-player swipes, sleep timer, optional five-band EQ, normalisation, skip-unavailable and next-source preparation. Later audio additions need on-phone testing.
- **Library:** Room tracks, favourites, ordered playlists, individual play history and saved queue; separate recent collection history; followed/saved podcasts and audiobooks with progress.
- **Offline:** one **Your music** UI for private completed downloads plus imported phone audio. Older Downloads and Local Files routes are aliases. The canonical offline audio is private; optional **separate** `Music/Groovio/` copies exist on Android 10+. WorkManager downloads and Spotify collection-matching jobs are durable.
- **Updates:** stable/experimental opt-in, package/version/signature verification, pre-update JSON snapshot on Android 10+, manual backup on Android 8/9, and manual library restore.

## Known gaps (documentation did not fix code)

1. Offline Home cached mixes/shortcuts may contain unsaved tracks; offline global search covers private downloads but not all imported phone audio.
2. Copying a whole collection through `PlayerViewModel` removes repeated IDs via `distinctBy` even though Room playlists permit repeated songs.
3. Historical download job reconciliation can incorrectly match a different track by title alone; completed private audio is validated by length/metadata, not a checksum.
4. Saved podcast/book Library entries navigate to the broad category instead of the exact item; Player and Queue lack full shared `SongMenu` actions.
5. Sleep timer lifecycle is tied to `PlayerViewModel` rather than the service.
6. JSON backup excludes downloaded audio, individual song-play events and durable Spotify matching manifests. Room playlists are restored transactionally; settings/spoken/collection history are separate; repeated restores create copies.
7. Optional external export and newer audio effects remain phone-unverified. Android Auto library browsing, widget, **word-by-word karaoke lyrics**, sharing, smart playlists and automatic new-episode refresh are suggestions or future work, **not currently implemented features**. Line-synchronised LRCLIB lyrics are implemented experimentally as described below.
8. The repository includes publicly accessible signing keystore/credentials. This is a security risk needing a carefully planned signing migration. Never copy credential values.
9. **Resolved in the 19 November release-workflow change:** the daily NewPipe follower is read-only and no longer pushes to `main` or publishes releases; compilation alone still does not prove phone playback.

## Documentation map

| Source | Purpose |
| --- | --- |
| [README](../README.md) | User-facing introduction, genuine Groovio screenshots and installation |
| **This page**, [STATUS](../STATUS.md) | Present scope, risks, verified versus unverified |
| [EXPERIMENTAL_STATUS](EXPERIMENTAL_STATUS.md) | Dated experimental stages and CI links |
| [EXPERIMENTAL_BASELINE](EXPERIMENTAL_BASELINE.md), [PHONE_PLAYBACK_APPROVAL.json](PHONE_PLAYBACK_APPROVAL.json) | Owner-confirmed 18 November recovery; not editable merely for documentation cleanup |
| [ARCHITECTURE](ARCHITECTURE.md), [OFFLINE_AND_PLAYBACK](OFFLINE_AND_PLAYBACK.md) | Current wiring and storage behaviour |
| [UI_ACTION_PARITY_AUDIT](UI_ACTION_PARITY_AUDIT.md) | Remaining action/navigation discrepancies |
| [AGENTS](../AGENTS.md), [CONTRIBUTING](../CONTRIBUTING.md), [DECISIONS](../DECISIONS.md) | Rules, build/release workflows and dated owner choices |

Source code establishes implementation; **only an explicit owner real-phone check establishes owner-confirmed playback**.

## Update descriptions in app (experimental, 19 November 2026)

The app now reads per-release `What's new` items from the GitHub release body and shows a bounded list in the update dialog and the first item in the Home update banner, with a link to full release notes. Previously it derived descriptions from commit titles; **now** releases are manually approved with 1–6 plain-English feature/fix descriptions. Old generic releases still use the fallback. Commit summaries are not proof of real-device functionality. This feature changes update UI and release metadata, **not playback**, and still needs Android CI/phone verification.

## Player lyrics button (experimental, 19 November 2026)

A **Lyrics** button has been added to the player utility row. It opens a full-screen Groovio-coloured lyrics view, looks up exactly matched recordings on [LRCLIB](https://lrclib.net), follows line-timed LRC lyrics against the existing Media3 position, and lets users tap a line to seek. Plain lyrics are shown un-timed; instrumental, mismatched, missing and failed lookups have explicit states. Successful results are cached privately on the device. No server or required account is added. LRCLIB offers line-timing records; **word-level karaoke highlighting is not implemented**, and availability/rights depend on the lyrics source. The media service, decoder and playback flow were not altered. On-device playback, readability and provider behaviour still require verification.

## Landscape video experiment (20 November 2026)

The experimental full-screen player now uses a dedicated landscape surface. Portrait keeps the existing album-art player; landscape attempts to find a short, official music video on YouTube with a near-matching duration and artist/title, rendering it through an independent muted Media3 ExoPlayer. Video track selection disables audio tracks; the original background MediaSession remains the only audible player and owns the queue, position, audio effects and audio focus. The landscape UI follows that position without changing it, and releases its video player on return to portrait.

Video lookup is opt-in by connectivity setting (Wi-Fi/Ethernet default, Always, Off), offline mode blocks lookup, and video failures or uncertain matches retain square artwork that fits landscape instead of cropping. A 12-second discovery deadline bounds loading. Landscape controls auto-hide and support tapping to reveal, pause/seek and track navigation. Matching tests cover official titles, incompatible edits, duration mismatch and artist mismatch. Video sync is a best-effort visual overlay, not proven frame-perfect; music video introductions/alternate edits, blocked YouTube extraction, device rotation, decoder and real phone playback remain **unverified**. A push triggers Android CI but does not itself publish an experimental APK update.

## Wi-Fi artwork quality and video opt-in (20 November 2026)

The experimental artwork loader now requests actual larger source thumbnails on Wi-Fi/Ethernet when the host supports them. YouTube thumbnails request their real `maxresdefault` variant; Google-hosted music artwork requests a 1080px version. If the higher-resolution variant returns an error, Coil retries the original; unrecognised hosts and locally embedded images are unchanged. The normal existing artwork URL remains the default on mobile data. Settings → Artwork quality has a separate **High-resolution artwork on mobile data** opt-in, default off and included in settings backups. Audio data-saving, streaming, decoder and background playback are untouched.

**Landscape music videos are now off by default.** Settings explicitly offers Off (default), Wi-Fi only or Always. A rotation with Off selected shows fitted album artwork and does no video lookup or decode. Existing explicit video preferences remain saved. Automated tests cover image URL variants, unknown-host fallbacks and settings persistence. Device image quality and data traffic have not yet been verified in person.

## Release publication policy (19 November 2026)

Pushing commits to `experimental` or `main` automatically runs Android build/test checks **without publishing an update**. Both release workflows now run only through a manual GitHub Actions `workflow_dispatch`, require an itemised user-facing change list, and refuse a release when there are no Android app/source/dependency differences from the previous reachable release tag on that channel. The existing app updater's version/signature checks and visible popup layout are unchanged. The daily NewPipe Extractor job only checks and tests a candidate inside CI; it no longer pushes or publishes. Historical releases, including v0.1.71 and the experimental v0.1.172, are unchanged.


## Spoken status, Home filters and skip policy (20 November 2026 — proposed fix)

The proposed fix on the spoken-progress review branch separates **listening history** from **explicit library bookmarks** and **completed offline audio downloads**. Starting an episode/book still creates a private resume record, but no longer automatically bookmarks it; the UI uses a bookmark icon and plain "In library" / "Downloaded" wording instead of a checkmark that resembles a completion state. Previously stored items default to bookmarked on decode to preserve users' existing library rather than silently discard saves. Playback's existing start-position logic is retained, and pause now requests an immediate saved position.

Home's Music view excludes "Show" and "Audiobook" recent collections from its quick-access and Jump back in shelves. The All view retains these mixed categories. Spoken offline discovery shows locally downloaded recordings rather than equating library bookmarks with downloads. The skip-unavailable setting is broadened to cover Media3-wrapped individual playback errors after the standard retry; blocked/disconnected networks are excluded and queue-sized consecutive failures prevent repeat-mode loops. Added regression tests cover these contracts.

**Evidence:** source changes and tests are in a proposed branch only; this note does not claim a passing Android build or verified phone playback. Podcast/audio availability still depends on the extractor and individual uploads.
