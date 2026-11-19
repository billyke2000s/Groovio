# Groovio — Agent and Engineering Rules

These rules apply to every contributor and coding agent. Read this file before editing.

## Product contract
- Groovio is a free, standalone, native Android music application.
- No mandatory accounts, subscriptions, advertisements, paid APIs, VPS, Lavalink service, or hosted backend.
- Kotlin + Jetpack Compose for UI; AndroidX Media3 for playback; Room for local persistence.
- Local-first playlists, favourites, history, and settings. Network is optional for online catalogue discovery.
- Build Groovio's own branding; Spotify may inspire layout but do not copy protected logos or assets.
- Metadata services do not grant full-track streaming rights. Never fabricate stream URLs.
- Owner decision (see DECISIONS.md): online audio comes from YouTube / YouTube Music, extracted on-device with NewPipe Extractor. Do not add other unofficial sources without the owner's approval.
- Licence is GPL-3.0 (required by NewPipe Extractor). Keep all dependencies GPL-compatible.

## Architecture
- Keep UI, metadata, source discovery, track matching, playback, and persistence separate behind explicit interfaces.
- Reuse ideas from the existing Discord bot's track matching only after adapting and testing them for Android; no runtime dependence on the bot.
- Avoid WebView as the application shell; no unnecessary dependencies.
- Keep network operations off the main thread, support cancellation, timeouts, and bounded retries.
- Do not add secrets/credentials to Git or logs. **Historical exception:** this repository already contains publicly committed APK signing material and its credentials. This is a security exposure, not a precedent; do not print them, promise authenticity from this signer or silently rotate it without an Android update-compatibility plan.

## Audio quality contract
- Select the highest-quality *available original* stream compatible with the device and permitted source.
- Prefer genuine lossless where available; do not assume a larger bitrate is better across codecs.
- Never upscale/transcode merely to claim a higher quality. Never mislabel lossy audio as lossless.
- Display measured/declared actual codec, bitrate, and sample rate inline after the song title, e.g. `Song · OPUS · 160K · 48kHz`; omit unknown values rather than invent them.
- For long titles, scroll the title and inline audio labels together; preserve readable artist and controls.
- Adapt to weak connections only when an alternative authorised representation exists.
- Aim for gapless playback when supported by source, format, and device.

## UI contract
- Maintain the agreed dark, Spotify-inspired full-screen player: top navigation, large square cover art, title + inline codec details, artist, timeline, shuffle/previous/play-next/repeat, and output/queue controls.
- No separate codec panel in the default player. No decorative or non-functional controls.
- Smooth navigation and accessibility: semantic labels, contrast, touch targets, scalable text, and responsive layouts.
- Support background audio, notification/lock-screen media controls, and Bluetooth headset controls.

## Reliability and testing
- Never claim a feature, test, build, or playback source works without executing the relevant verification.
- Do not ship placeholder buttons or fake results as working features.
- Handle unavailable metadata, failed streams, offline mode, expired links, and decoder failures gracefully.
- Prefer root-cause fixes; avoid broad rewrites for isolated defects.
- Add focused tests for matching, codec label formatting, queue transitions, and persistence migrations.
- Run lint, unit tests, and an Android build before marking a release ready; report anything unverified.
- Preserve existing behaviour unless the task explicitly changes it.

## Documentation and verification authority
- [docs/CURRENT_STATE.md](docs/CURRENT_STATE.md) is the as-of-date inventory of implementation and evidence. [STATUS.md](STATUS.md) contains historical phases; [docs/EXPERIMENTAL_STATUS.md](docs/EXPERIMENTAL_STATUS.md) is a dated development record. Never describe a historical checkpoint as the present feature status.
- The owner confirmed song playback only for v0.1.71 at `6d2c55a372b2f7d6598032c78e3dee574b5e2085`. Newer experimental audio requires explicit phone confirmation. Do not alter `docs/PHONE_PLAYBACK_APPROVAL.json` without it.
- Main branch presence and green CI do not establish phone playback. The extractor-update workflow is now a read-only scheduled compatibility check; it must not push to main or publish. Releases require a manual workflow dispatch, meaningful app-source differences and a human-readable change list. The stable phone-playback guard still applies.
- When asked for docs-only edits, do not alter Kotlin, XML, Gradle, workflows, APKs, screenshots, signing files or approval records.

## Agent workflow
1. Read this file and inspect relevant code and docs.
2. Describe the smallest viable change and its acceptance criteria.
3. Implement in focused, reviewable commits.
4. Update tests and documentation alongside behaviour changes.
5. Report changed files, checks actually run, failures, and remaining limitations.
6. Never silently remove features, add paid dependencies, or bypass licensing/security requirements.

## Foundation milestones (implemented in source; device verification varies)
1. Android Studio project and CI tests/lint/APK build.
2. Groovio full player, local-file path and inline actual codec metadata.
3. YouTube Music discovery/source resolution and Spotify link metadata matching.
4. Room favourites, playlists, history and playback state.

These are **not blanket on-device playback claims**. See [CURRENT_STATE](docs/CURRENT_STATE.md) for remaining gaps.
