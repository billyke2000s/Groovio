# Groovio 🎵

**Your music. Your way.**

Groovio is a free, native Android music app in development. It searches YouTube Music and plays songs directly on your phone (no server, no account), with a dark music-streaming-inspired interface and accurate audio-format information. It can also play audio files from your phone.

## Current status
🚧 **Early development — not a released streaming service.**

See [STATUS.md](STATUS.md) for exactly what is built and verified. Debug APKs come from CI; there is no release yet.

**About online playback:** songs are found and streamed on each phone using [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor), the same open-source library the NewPipe app uses. Groovio is not affiliated with YouTube or Spotify.

## Features
- Paste or share a Spotify song, album or playlist link and play it: each song is matched to YouTube on the phone (no Spotify account needed).
- Search YouTube Music and stream on the phone, with the actual codec, bitrate and sample rate shown after the title.
- Full-screen player, mini-player, queue, shuffle/repeat, output picker, background and lock-screen playback.
- Liked songs, playlists, recently played, and the queue restored after restart, all stored only on the phone.
- Play audio files from your phone too.

## Principles
- Free, no subscriptions, no advertising, no required accounts or hosted backend.
- Native Kotlin, Jetpack Compose, AndroidX Media3.
- Your playlists and history stay on your phone.
- Show **real** codec/sample-rate/bitrate when known; never invent or upscale quality.
- Spotify-inspired usability with original Groovio branding.
- No server: search and streaming happen on the phone.

## Try the starter
1. Open this repository in Android Studio with JDK 17.
2. Install Android SDK platform 35.
3. Sync Gradle and run the `app` configuration on Android 8.0+.
4. Tap **Open audio file** and choose a playable file from your device.

The app uses Android's document picker, so broad storage permission is unnecessary. Playback continues through a Media3 media session service.

Command line: `./gradlew :app:assembleDebug` (wrapper pins Gradle 8.10.2). Run tests with `./gradlew :app:testDebugUnitTest`.

CI builds every push and uploads the debug APK as the `groovio-debug-apk` artifact. See [STATUS.md](STATUS.md) for what is verified.

## Development
See [AGENTS.md](AGENTS.md) for contributor/AI rules and [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for design decisions. This project is not affiliated with Spotify.

## License
GPL-3.0. See [LICENSE](LICENSE). (Required by NewPipe Extractor, which is GPL-3.0.)
