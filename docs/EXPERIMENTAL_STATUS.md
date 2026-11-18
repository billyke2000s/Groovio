# Experimental status — 18 November 2026

The confirmed recovery source/tag remains unchanged: `6d2c55a372b2f7d6598032c78e3dee574b5e2085`, `experimental-baseline-v0.1.71`. Main has not been changed by this work. Experimental initially pointed at `55e4d98`, a documentation checkpoint over that source, with no newer commits to preserve.

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
