# Experimental implementation log — 18 November 2026 (historical checkpoints)

This page retains the **dated implementation and CI history**. See [CURRENT_STATE](CURRENT_STATE.md) for the verified/unverified state as inspected on 19 November 2026. At the start of these checkpoints, the confirmed recovery source/tag was `6d2c55a372b2f7d6598032c78e3dee574b5e2085` / `experimental-baseline-v0.1.71`, and experimental initially pointed at `55e4d98`. The old statement that main had not changed was true **at that earlier checkpoint**, not necessarily thereafter.

## Published stages

| Stage | Source | Evidence |
|---|---|---|
| Search/Create and update channels | `6560024` | [v0.1.74](https://github.com/billyke2000s/Groovio/releases/tag/experimental-v0.1.74); [tests/lint/APK passed](https://github.com/billyke2000s/Groovio/actions/runs/44278558375). Playback service unchanged from baseline. |
| Daily mixes and spoken browsing | `a65ba33` | [release checks passed](https://github.com/billyke2000s/Groovio/actions/runs/44279210998). Playback service unchanged. |
| Durable ordered collection imports | `5911c0f` | [checks passed](https://github.com/billyke2000s/Groovio/actions/runs/44279663563). Baseline renderer, sink and service unchanged. |
| Isolated audio changes | `99dc498` | [v0.1.81](https://github.com/billyke2000s/Groovio/releases/tag/experimental-v0.1.81); [tests/lint/APK passed](https://github.com/billyke2000s/Groovio/actions/runs/44280301082). No phone audio verification. |

| Final feature/check stage | `4bde332035f9abef1c5481e23f7dd393ffe9d9dd` | [Signed experimental v0.1.87](https://github.com/billyke2000s/Groovio/releases/tag/experimental-v0.1.87); [release checks passed](https://github.com/billyke2000s/Groovio/actions/runs/44282255386), [95 automated tests, lint and APK assembly passed](https://github.com/billyke2000s/Groovio/actions/runs/44282255466). Rendered UI fixtures passed and Home/player/Settings were visually reviewed. |

Final APK: `groovio-experimental-v0.1.87.apk`, SHA-256 `80d7c14f8f3cd0d6d4d7348f11c26e2b6c45fcbdcdd2bf9d20610476fae87f05`, published as a prerelease with the existing signing identity. Genuine artwork loading and physical playback remain unverified.

This stage includes search interaction coverage, downloaded-file length markers, saved show/book collections, artwork collages, artist/genre/discovery mixes, stale update-channel result rejection and the stable publication guard.

## Implementation notes

- Existing orange design, player and mini-player retained; no permanent Home download counter.
- Search opens an animated route; Last Groove replays history through existing library playback. Artist/album/playlist and Spotify-link paths remain.
- Daily mixes persist real YouTube related tracks seeded by distinct artists from likes/history. Mixes deduplicate across shelves and can be saved as library playlists. Generic genre discovery stays labelled separately.
- Home filters open music or real YouTube spoken searches. Saved YouTube playlist collections list their uploads as episodes/chapters. Progress is stored separately from music; existing sleep timer plus playback speed apply.
- Library retains existing schema/data, sorting, grid/list presentation and playlist management. Downloads, saved spoken content and known artists are accessible there. Playlist covers use real track artwork.
- Spotify Save & play offline persists source/name/artwork and original track positions in private manifests. A durable worker matches and writes positions idempotently, queues audio and reports matched/unavailable totals. Play can stream immediately. Playback and saving can perform separate matching requests; this is extra network work.
- No Room schema change or destructive migration. Backups retain the existing format and optional spoken/settings fields. Import manifests/source provenance are not included in exported library backups; saved matched playlist tracks are included.
- Effects default to off and instantiate no native effect when disabled. The ExoPlayer renderer/sink construction remains the baseline. Equaliser uses attenuation headroom; optional platform levelling attenuates loud passages with no make-up gain. Native effect behaviour still needs listening verification.
- One transient retry; optional skipping has a three-track budget, reset only after actual playback. Bot challenges are not treated as unavailable songs. Recovery and autoplay check pause intent. No validated-network gate in stream resolution; network UI observes INTERNET capability rather than VALIDATED.
- Optional next preparation fills a separate next-ID URL cache and never writes the active queue/player. Data saver/offline mode bypass it. Media3 gapless support remains format/source/device dependent.
- Update channel consent defaults to stable and is not restored from backups. Experimental tags are prereleases; stable selection excludes all prereleases. Version codes allocate above both channels' published tags using shared workflow concurrency. Existing signing identity retained.

## Checks and remaining work

Meaningful automated coverage includes library persistence/backups, idempotent import positions, HTTP range/incomplete-transfer handling, saved spoken progress, recommendation diversity, queue order, effect bypass/session replacement/unsupported outputs, pause/retry/skip limits and update channel selection. The interaction test covers Last Groove and close navigation; truncation coverage checks saved file markers.

Local Gradle execution was attempted but cannot download Gradle (`Network is unreachable`); no Android SDK or adb/emulator is installed. GitHub Actions supplies compilation, unit tests, lint and APK assembly. Existing rendered UI fixtures have passed, but they do not establish real artwork loading or physical playback.

The separate [latest network smoke job](https://github.com/billyke2000s/Groovio/actions/runs/44282255466) failed with `SignInConfirmNotBotException` / YouTube LOGIN_REQUIRED on the runner. This is inconclusive for phone playback.

Still outstanding:

- Phone playback with effects off, then each effect separately and together; listening quality and clipping/pumping assessment.
- Seeking; next/previous; long background/lock-screen playback; Bluetooth and wired disconnect/reconnect; Wi-Fi/mobile handover; explicit pause during errors; offline replay after killing/reopening the app.
- Real device navigation/accessibility review and genuine loaded-artwork screenshots for README. Mock/test images are intentionally not presented as product screenshots.
- Personalised genre mixes now use exact, unambiguous MusicBrainz artist identities and positive genre votes, then real YouTube songs by those listening-seed artists. Requests are rate limited and cached for 30 days. Unsupported or ambiguous artists get no genre label. Live metadata coverage and real listening still need phone verification. Generic genre discovery remains separately labelled.
- Automatic new-episode refresh and timestamp chapters inside one video are not implemented. Saved playlist chapters/episodes are supported.
- Exact original shuffled order across process recreation is not persisted; the visible current queue uses Media3's shuffle order.

## Promotion and recovery

`scripts/check_playback_approval.py` compares audio-related paths with the owner-confirmed source in `PHONE_PLAYBACK_APPROVAL.json`. The stable publish action refuses changed audio without a new explicit owner confirmation record. The guard was run locally: the baseline passed and the current experimental source was correctly rejected. This guard reduces accidental publication risk; it is not proof of playback or an absolute regression guarantee.

Do not update the confirmation record or promote to main until the owner confirms phone playback for the new source and approves promotion. If playback regresses, stop additions, bisect the focused audio commits, and restore the responsible change. The original signed baseline remains downloadable. Android downgrade restrictions mean that installing it over a newer code may require uninstalling; export the library first and remember backups exclude audio files.

## Regression investigation

The `v0.1.69` to `v0.1.71` diff confirms that several changes were removed together. In v0.1.69:

- Stream requests were rejected when the validated-network observer returned false, even if an HTTP request could have succeeded.
- A custom audio sink/processor was installed even when normalisation was off. Its `onConfigure` still activated processing for PCM16; the disabled path copied samples but did not restore the default sink.
- Next-URL preparation ran while `playWhenReady` was true, including buffering periods.
- Automatic skipping covered generic playback errors, including network failures.

These are observed code properties and plausible failure risks, **not an isolated explanation of the owner's phone regression**. Only comparative device testing or a bisect can identify that cause. The new code keeps the baseline renderer/sink, avoids a validated-network stream gate, allocates no effects when disabled, makes early preparation opt-in/ready-only, and limits skipping to unavailable content with a three-song budget. All audio additions remain experimental pending phone confirmation.

## Follow-up: spoken discovery and collection history

Requested after v0.1.87: replace the sparse podcast/book lists with Spotify-style card organisation using Groovio colours, and make Jump back in/history show collections instead of songs.

The spoken feed now has cover-led tinted cards with actual available metadata, clear play/save actions, a Following row/filter, horizontal collection shelves and a separate episode/chapter view. Search and source information are behind toolbar actions. No fake previews, dates, descriptions or play counts are shown. Existing audio sources, saved content, progress and downloads remain connected.

A new independent collection history stores title/type/artwork, source identity, ordered track snapshots and timestamps. Home shortcuts, Jump back in, Last Groove and Library recents consume this history. Local playlist/liked collections, YouTube albums/playlists, Spotify collections, daily mixes and spoken collections record their context. Per-song history is retained for recommendations and is not relabelled as a playlist. Older song-only events cannot reconstruct past playlist context reliably; collections populate the new history as they are played. New backup fields are optional and the Room schema remains unchanged.

Added deterministic tests for collection ordering/deduplication/recreation, clear-history races, restored snapshot identity, Home and search collection replay, and spoken card/following controls. Optional live-catalogue screenshot tests load real YouTube entries and artwork for review. Verification results will be recorded after CI completes. Playback service, renderer/sink and audio effect implementation are unchanged in this follow-up.

## Update safety follow-up (post-v0.1.95)

A user reported their library appearing to reset after a Groovio update. The existing in-app updater uses Android's normal ACTION_VIEW APK installer, with the same application ID and signing key, and the Room database name/schema are unchanged. No updater-side delete/wipe call was found; the exact device-level cause remains unverified. Returning-user Home display had also been repaired separately.

To reduce data-loss risk, the experimental updater now checks that the downloaded APK has Groovio's installed package identity and signing certificate and a strictly greater version code. Before handing it to Android, it creates a JSON copy of the library (playlists, likes, metadata, settings, spoken items and collection history) in Downloads/Groovio via scoped storage. If APK verification or backup fails, installation is blocked with an error. Restore remains available from Settings > Restore library. The JSON does **not** include downloaded audio binaries and is not an automatic restoration. Android 8/9 users must export a backup manually before updating; their automatic update is blocked until that workflow is implemented.

Tests cover package/signature/version rejection and persistence of likes and playlists when the same Room database is reopened. This is a forward-looking safeguard, not proof that any previously deleted data can be recovered. Never uninstall Groovio to update it.

## Home, library and equaliser layout follow-up

- Removed redundant Home 'Discover podcasts' and 'Discover audiobooks' links (the filter chips already navigate there).
- Made Library recents genuinely collection-led, with pinned Liked Songs, recently used collections and saved playlists rather than arbitrary auto-generated artist rows ahead of them. Added filters for playlists, podcasts, albums from real played collection history, artists from actual listening and saved audiobooks. Library search routes to Groovio search, Downloads is in the toolbar, and list/grid plus alphabetical/recents sorting work across all categories.
- Replaced the equaliser's three tiny preset buttons with a dropdown covering 22 distinct five-band profiles, an interactive five-band adjustment panel and a live EQ response graph. "Custom" appears as soon as sliders diverge from the presets. Settings persistence and the existing headroom-aware Android session effects remain unchanged. No playback engine or Room migration changes.
- Screenshot-based visual comparison is a design reference only, not a claim the build has been reviewed on a real phone.

## Phone-friendly Local Files (post-v0.1.102)

A permanent Local Files tile appears on Home, the existing Home folder icon opens the same screen, and Your Library shows a visible Local Files entry. The redundant Library header plus was removed; the bottom Create tab remains the only playlist creation shortcut.

Android 10+ uses MediaStore's shared `Music/Groovio/` folder. Local Files can import selected audio documents without moving/deleting originals, scan files copied with external file managers after a granular music read permission, play listed local audio through the existing Media3 queue, browse the folder in Files where supported (fallback Android folder chooser), and show downloaded tracks in the same place. A file-specific overflow menu offers Downloads and Settings without another top-level gear.

Each completed online download is **copied** with its real audio container extension to public Music/Groovio; the original private cached audio is retained deliberately for playback reliability. Local Files retries exporting older private downloads. Clearing or removing offline caches does **not** delete user-accessible shared files. This temporarily uses extra storage and must be evaluated before later switching the player's canonical download location; don't remove private cached files without a verified migration and phone playback test. On Android 8–9, file picking/playback remains available but public folder publishing is unsupported.

The folder may not physically exist until an MP3 is imported or a download is exported. Files app paths and folder intents vary by device, so the path is always shown and the Add music picker always works. Android 13+ uses READ_MEDIA_AUDIO for finding tracks placed by other apps. No all-files storage permission is requested. The confirmed working 0.1.71 playback baseline is untouched.


## Playlist detail navigation and Home chart order (18 November 2026)

Home quick tiles, Jump back in, personal mixes, Library recents and Search's Last Groove now **open a collection page**, rather than starting playback as a side effect of selecting a collection. The full page displays real artwork (2x2 when four distinct track covers exist), name, kind, number of songs, playable track rows, an explicit Play button, Shuffle, and the existing optional Download & play action. The original local Library playlist editor remains available for rename/removal. Home's saved playlist cards fetch current Room playlist tracks when opened. Playback history is recorded only when the user presses Play/Shuffle or taps a track, not when the page opens.

UK top songs is displayed after personal mixes, recommendations, saved playlists and discovery shelves, not above the customised Home content.

The Android app and media data schema are unchanged. Validate the new flow on-device before promoting from experimental.

## Full-screen swipe and compact control polish (experimental)

- Artwork is now a horizontal, finger-following carousel: swipe left for the next actual Media3 queue entry; swipe right for the previous Media3 item regardless of the usual skip-to-start behaviour of the Previous button. A short swipe or queue boundary snaps back softly. The artwork view preloads the adjacent queue entries' known artwork URLs (no fabricated media), and requests playback only at swipe commit. Vertical swipe-down to dismiss is still supported, with an eased return or dismissal; the mini player also eases back after sideways swipes. Navigation slide easing is tuned.
- The playlist action formerly labelled "Download & play" is now a compact accessible download icon. Real WorkManager jobs drive a progress indicator, and fully saved playlists show a checkmark. Clicking the idle icon still enqueues and starts playback exactly as before; clicking the checkmark plays the saved collection.
- The five-band EQ was condensed into five finger-draggable vertical bars, retaining the saved -6..+6 dB settings, preset dropdown and headroom-safe audio path. Bar levels animate softly when presets are changed. No database or audio decoder changes.
- Unit tests cover swipe threshold, fling, and first/last queue boundaries. Device testing is still needed for feel, Bluetooth behaviour and Android-specific gesture competition.

## Unified Your music and download-status correction (18 November 2026)

The separately presented **Local Files** and **Downloads** pages now open one **Your music** view from Home, Library, Settings and the offline shortcut. The view reads verified private downloaded audio directly via `OfflineStore.tracks`, so it no longer relies on successfully creating a duplicate public MediaStore file. Downloaded audio is shown first; the same view supports All, Downloads and On this phone filters, Play all, Shuffle, Add music, long-press song actions, removal with confirmation, and optional manual export to `Music/Groovio/`. Public copies associated with still-downloaded originals are hidden from the combined list, but are not deleted. Both navigation routes are retained as compatibility aliases to the same view.

The owner reported **66 saved songs (221 MB)** while the old Downloads view showed **65 failed jobs** and Local Files showed only one phone-visible copy. Code inspection found that `AudioDownloadWorker` could successfully save private audio and then mark the entire download FAILED if the subsequent optional MediaStore export failed. The worker now returns SUCCESS after verified private saves and reports optional export failure separately in its output data; it does not redownload saved songs for a public-copy error. Existing historical FAILED WorkInfo records are treated as resolved when their identified audio exists in verified private storage. Truly unresolved failures remain accessible via Download activity, as do playlist-import unmatched-track details.

Opening Your music no longer attempts re-exporting every stored download. A manual export action retries each song independently and reports partial successes/failures without changing the playable originals. Neither the Media3 resolver nor the `yt:` identity, data formats, private storage location, or Room database schema were changed. Focused JUnit tests cover historical worker-state classification; compilation and physical phone playback still require verification. The Android-specific root cause for failed public exports is not yet confirmed, so do not claim the external folder copy itself is repaired on every device.

## Repository documentation and CI follow-up — 19 November 2026

- [CURRENT_STATE](CURRENT_STATE.md) now distinguishes experimental implemented behaviour, owner-confirmed stable playback and unresolved device tests. This work updated documentation, not the APK or signing source.
- The [18 November experimental Android CI run](https://github.com/billyke2000s/Groovio/actions/runs/44422540191) completed successfully. This does **not** verify newer audio, exported folder permissions or all UI interactions on a phone.
- The [19 November NewPipe follower](https://github.com/billyke2000s/Groovio/actions/runs/44544010480) attempted Extractor v0.27.1 but failed compilation on removed `isAndroidStreamingUrl`, `getAndroidUserAgent`, `isIosStreamingUrl` and `getIosUserAgent` methods; that attempt was **not pushed or published**. The release pipeline's phone-source guard had separately blocked newer unapproved stable audio.
- Before the 19 November docs reconciliation, `main` and `experimental` had identical Git trees but separate commit histories. Earlier assertions in this log that main was unchanged apply only to their dated stage.
- Normalisation, next-URL preparation and skip-unavailable were previously rolled back for the v0.1.71 recovery, but later experimental source reintroduced them; no new phone approval is implied. The public README now displays the genuine images already in `docs/screenshots/`, not generated or fixture images.
- Remaining source-level gaps include title-only failed-job matching, lost duplicate occurrences when copying playlists, partial offline Home/search playability, incomplete full-player/queue menus, saved spoken Library deep links, and incomplete backup coverage. These are tracked in [CURRENT_STATE](CURRENT_STATE.md) and [UI_ACTION_PARITY_AUDIT](UI_ACTION_PARITY_AUDIT.md); they were **not fixed in code** here.

## Player lyrics — 19 November 2026

The experimental player gained a **Lyrics** button in its bottom action row. Its full-screen view shows LRCLIB's exactly matched timestamped LRC lines highlighted using the existing player position, tapping a line seeks through the existing controller, and untimed lyrics, instrumental and missing/error cases are identified honestly. Results are cached in private app files. No new audio service, decoder or playback source was added. Android unit tests, lint, debug APK assembly and screenshot checks passed for source commit `9427b62d`; the separate live YouTube smoke job failed with the known GitHub-runner bot challenge. Device experience and LRCLIB availability still need phone testing. Word-level singing karaoke animation is future work.
