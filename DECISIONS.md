# Decisions

Owner decisions are recorded here so agents don't silently undo them.

## 2026-11-08 — Workflow: one branch and PR per phase
Each phase is built on its own branch, must pass CI (unit tests, lint, debug APK), and is opened as a PR. The owner merges; agents do not merge to `main`.

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
- `Release` workflow publishes `v0.1.<commit count>` for every app change on `main`.
- Groovio checks GitHub releases on launch and offers the newer APK.
- All builds are signed with one key committed at `signing/groovio.jks` so updates install over each other. The owner accepted that a public key lets others build look-alike updates; Groovio holds no accounts or passwords.
