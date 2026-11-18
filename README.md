# Groovio 🎵

**Find your next favourite. Keep your groove.**

Groovio is a free Android music player with an orange-accented dark design. Search for music, play audio from your phone, build playlists and save available audio for offline listening. No account, subscription or hosted music server is required.

## Install and update

Groovio supports Android 8 and newer. Download the APK from a release, open it on your phone, and allow installation from that download location when Android asks.

- **Stable:** [Latest stable release](https://github.com/billyke2000s/Groovio/releases/latest).
- **Experimental:** [Browse releases](https://github.com/billyke2000s/Groovio/releases) and choose a release labelled `experimental-v…`. These builds contain new work that still needs phone testing.
- **Confirmed playback baseline:** [v0.1.71 recovery release](https://github.com/billyke2000s/Groovio/releases/tag/experimental-baseline-v0.1.71), personally confirmed to play songs by the owner.

Updates use the same app identity and signing key, so a newer APK can install over your existing Groovio. Check for updates in Settings. Experimental updates require an explicit opt-in under **Settings → Updates**; stable users are not automatically moved to test builds.

Before testing experimental builds, use **Settings → Back up library**. Android normally blocks installing an older version over a newer one. Returning to an older APK may require uninstalling first, which removes app data and downloaded audio. A library backup contains playlists, likes and settings, not the downloaded audio files.

## Make yourself at home

Play songs and like your favourites to shape recommendations. Experimental builds add daily related-song mixes, which stay consistent for the day and can be saved as playlists. The recommendations use real listening seeds; generic discovery searches are labelled separately.

Tap Search to open the full-screen search view. **Last Groove** brings recent listening back within reach before you type. Search songs, artists, albums and playlists, or paste a Spotify link.

Open **Your Library** for liked songs, playlists, recent listening and Downloads. Use **Create** to name a playlist, then add songs from their menu. Rename playlists, remove songs and switch between list and grid views in the library.

## Play online or take it with you

**Play** starts streaming without requiring a download. **Download & play** also queues the audio for offline use. Spotify collections offer **Save & play offline**, which creates a reusable library playlist while supported tracks are matched and downloaded in the background.

Downloads survive navigation and app restarts. Open Downloads from Your Library or Settings to see progress, matching failures and saved songs. Wi-Fi-only transfers wait for an unmetered connection. Saved audio is preferred automatically, and offline search searches downloaded songs.

Spotify links supply metadata. Groovio matches that metadata to available YouTube audio; it does not directly stream Spotify's protected catalogue. Some collections expose only part of their track list, and some tracks cannot be matched. Matching progress describes the tracks actually available to Groovio, not a guarantee that every Spotify track was imported.

## Listening controls

The player includes a queue, shuffle, repeat, seeking and a sleep timer, plus notification and lock-screen controls. Actual available audio-format details appear beside the title when known.

Experimental audio controls include a built-in five-band equaliser, presets and optional gentle volume levelling. Both effects default to off. Equaliser boosts use relative attenuation to leave headroom; levelling reduces loud passages rather than boosting quiet recordings. Device effect support varies, and listening tests remain necessary. Optional next-song preparation can reduce the wait between tracks; gapless playback depends on the source and device.

## Podcasts and audiobooks

Experimental Home filters browse public YouTube uploads for podcasts and audiobooks. Search by show, episode, author or book title, save individual episodes/books, adjust playback speed and download available audio. Saved spoken content keeps its progress separately from music history.

This is an early implementation: show subscriptions, automatic new-episode discovery and structured chapter navigation are not finished. It does not provide paid books or Spotify-exclusive programmes.

## What to expect

Groovio is still in development. Online availability depends on YouTube and the network; bot challenges, missing uploads and regional restrictions can prevent playback. Experimental builds require device verification, including Bluetooth, offline playback and background listening. See [experimental status](docs/EXPERIMENTAL_STATUS.md) for the checks actually performed and outstanding work.

Genuine finished-app screenshots with loaded artwork are still pending device capture. Screenshots from other music apps and generated mock-ups are not used here.

## Open source

Groovio uses [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor) for on-device source extraction, AndroidX Media3 for playback, Room for the local library, WorkManager for background downloads and Coil for artwork. It is not affiliated with YouTube or Spotify.

Licensed under [GPL-3.0](LICENSE), including the requirements of NewPipe Extractor. Build and contributor information lives in [CONTRIBUTING.md](CONTRIBUTING.md).
