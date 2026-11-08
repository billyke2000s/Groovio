# Groovio 🎵

**Your music. Your way.**

Groovio is a free, local-first, native Android music player in development, with a dark music-streaming-inspired interface and accurate audio-format information.

## Current status
🚧 **Early development — not a released streaming service.**

This repository contains an Android application foundation with a Compose player screen and local audio playback through AndroidX Media3. It does **not** yet provide a catalogue of playable commercial songs, online streaming providers, downloads, playlists, or an APK release.

## Principles
- Free, no subscriptions, no advertising, no required accounts or hosted backend.
- Native Kotlin, Jetpack Compose, AndroidX Media3.
- Local-first. Online catalogue discovery can be added later.
- Show **real** codec/sample-rate/bitrate when known; never invent or upscale quality.
- Spotify-inspired usability with original Groovio branding.
- Use authorised music sources only.

## Try the starter
1. Open this repository in Android Studio with JDK 17.
2. Install Android SDK platform 35.
3. Sync Gradle and run the `app` configuration on Android 8.0+.
4. Tap **Open audio file** and choose a playable file from your device.

The app uses Android's document picker, so broad storage permission is unnecessary. Playback continues through a Media3 media session service.

> The repository does not yet include the Gradle wrapper binary. Use Android Studio's configured Gradle or install Gradle 8.10.2 to build from the command line.

## Development
See [AGENTS.md](AGENTS.md) for contributor/AI rules and [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for design decisions. This project is not affiliated with Spotify.

## License
Apache-2.0. See [LICENSE](LICENSE).
