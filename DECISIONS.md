# Decisions

Owner decisions are recorded here so agents don't silently undo them.

## 2026-11-08 — Original phase/PR workflow (historical)
Initial plan: separate phase branches, CI and owner-merged PRs. This is no longer a literal description of current repository state: recent work is on `experimental` and source has also reached `main`. See [CONTRIBUTING](CONTRIBUTING.md) for the workflows actually configured.

## 2026-11-08 — Original accent colour
Replaced Spotify's brand green `#1ED760` with Groovio coral `#FF7A45` so the app is inspired by, not a copy of, Spotify.

## 2026-11-08 — Online audio from YouTube, extracted on-device (owner decision)
The owner chose NewPipe-style on-device extraction from YouTube / YouTube Music for online playback, mirroring the Discord bot but without a server.
- Runs entirely on each phone; no server, Lavalink or VPS.
- Known trade-offs, accepted by the owner: distributed outside Google Play; YouTube changes can break extraction until the library is updated.
- Supersedes the "authorised sources only" rule in AGENTS.md for YouTube specifically. Other rules (no fabricated streams, honest quality labels) still apply.

## 2026-11-08 — Licence: GPL-3.0 (owner decision, applied with Phase 4)
NewPipe Extractor is GPL-3.0, so Groovio switches from Apache-2.0 to GPL-3.0 when it is added. All Groovio source must remain open.

## 2026-11-08 — Metadata from YouTube Music, not the Spotify API
Spotify's Web API needs a client secret, which cannot be shipped safely inside an APK. Search and browse use YouTube Music via the extractor. Spotify *links* can still be imported the way the Discord bot does it (public embed page, no key), then matched to YouTube.

## 2026-11-08 — Local file playback stays as an extra
Groovio is primarily a streaming app; "Choose audio files" remains as a secondary option.

## 2026-11-08 — Phase order: streaming before library
Because Groovio is primarily a streaming app, online search/playback (originally Phase 4) is built before persistence (originally Phase 3), so favourites, playlists and history are designed around streamed tracks from the start.

## 2026-11-08 — NewPipe Extractor pinned to v0.26.5; v0.27.0 blocklisted
On a phone (mobile data) every song failed with "Sign in to confirm you're not a bot". NewPipe 0.29.2, which moved to extractor v0.27.0, shows the same failure (NewPipe issue #14005), while 0.29.1 on v0.26.5 worked. v0.27.0 dropped the Android/iOS fallback clients. Groovio uses v0.26.5 until a newer extractor release appears.

## 2026-11-08 — Automatic updates (owner decision)
- `Follow NewPipe Extractor` workflow checks daily for a newer extractor release, skips versions in `.github/extractor-blocklist`, runs tests, commits to `main` and publishes a release.
- `Release` workflow attempts `v0.1.N` releases for selected main-branch paths and now has a stable audio-source approval guard; version codes are allocated above published tags, not simply taken from commit count.
- Groovio checks GitHub releases on launch and offers the newer APK.
- The existing signing keystore **and credentials** are publicly committed, historically accepted for continuity. This is an app update-security risk: others could potentially sign an APK with Groovio's identity. No accounts does not eliminate that risk. Do not reproduce credentials in docs or rotate the signer without an update-compatibility plan.

## 2026-11-18 — Owner-confirmed playback recovery

The owner confirmed that v0.1.71 at source `6d2c55a372b2f7d6598032c78e3dee574b5e2085` plays songs on the phone. Newer experimental audio is not thereby approved. Preserve the baseline and `docs/PHONE_PLAYBACK_APPROVAL.json` without new owner evidence.

## 2026-11-19 — Documentation reconciliation (observation, not new product approval)

Current experimental source has unified **Your music** for completed private downloads and imported audio, with old Downloads/Local Files routes as aliases. Public `Music/Groovio/` copies remain optional separate files. Current verification and documentation roles are tracked in [docs/CURRENT_STATE.md](docs/CURRENT_STATE.md); this docs-only change authorises no new stable audio.
