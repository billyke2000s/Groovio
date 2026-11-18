<div align="center">

# Groovio

**Discover music. Build your playlists. Listen anywhere.**

A free, open-source Android music player with a dark interface, coral accents and a library that's yours.

[**Download APK**](https://github.com/billyke2000s/Groovio/releases) · [**Latest stable**](https://github.com/billyke2000s/Groovio/releases/latest) · [**Source code**](https://github.com/billyke2000s/Groovio)

</div>

<p align="center">
  <a href="docs/screenshots/home.webp"><img src="docs/screenshots/home.webp" width="330" alt="Groovio Home screen with mixes, playlists, Jump back in and music controls"></a>
</p>

## Your home for music

Pick up where you left off with **Jump back in**, find a favourite playlist in seconds, or explore your **Daily Mixes**. The mini-player stays close while you browse.

### Search and discover

Look for songs, artists and albums, explore recommendations, and open collections to browse their tracks. **Last Groove** helps you return to music you've recently enjoyed.

### Playlists and your library

Create playlists, save favourites and organise your collections in **Your Library**. Long-press a song or playlist for quick actions like adding to a playlist, playing next or downloading music.

<p align="center">
  <a href="docs/screenshots/song-menu.webp"><img src="docs/screenshots/song-menu.webp" width="250" alt="Groovio song actions menu"></a>
  &nbsp;
  <a href="docs/screenshots/playlist-menu.webp"><img src="docs/screenshots/playlist-menu.webp" width="250" alt="Groovio playlist actions menu"></a>
</p>

### Your music, even offline

Keep your downloads and imported audio together in **Your music**. Browse saved songs, play everything, shuffle or add music from your phone. Groovio uses your verified downloaded audio directly for offline playback.

### A player that stays out of your way

Enjoy large album artwork, a mini-player and full playback controls. Swipe to skip tracks, manage the queue, shuffle, repeat and set a sleep timer.

### Make it sound right

Use the five-band equaliser, choose a preset or enable optional volume normalisation. Audio-effect availability depends on your Android device.

### Podcasts and audiobooks

Discover available public episodes and recordings, save them to your library and resume where you left off.

---

## Get Groovio

**Android 8.0 or newer** is required.

1. Open the [releases page](https://github.com/billyke2000s/Groovio/releases) and choose an APK.
2. Open the downloaded file and allow installation from that source if Android asks.
3. Launch Groovio and start listening.

**Updating?** Install a newer signed APK over your existing app rather than uninstalling first. Use **Settings → Back up library** before testing new builds. Backups save playlists and library information, but not downloaded audio files.

[**Releases**](https://github.com/billyke2000s/Groovio/releases) · [**Experimental builds**](https://github.com/billyke2000s/Groovio/releases) · [**Build from source**](CONTRIBUTING.md)

## How it works

Groovio plays available YouTube / YouTube Music audio through on-device extraction. Spotify links are used to match track and playlist **metadata** to available audio; they don't stream Spotify's protected catalogue. Not every track is guaranteed to match or play in every region.

Your playlists, favourites and settings are stored locally. Groovio doesn't require a Groovio account, subscription or hosted music server.

<details>
<summary><strong>Technical details and development</strong></summary>

Groovio is written in **Kotlin** with **Jetpack Compose**. Playback uses **AndroidX Media3**, the local library uses **Room**, background downloads use **WorkManager**, and artwork uses **Coil**. Audio extraction uses [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor), with optional artist metadata from [MusicBrainz](https://musicbrainz.org/).

The project is actively developed. Some features still need testing across different Android devices, audio outputs and network conditions. See [development status](docs/EXPERIMENTAL_STATUS.md) and [contributing](CONTRIBUTING.md).

Groovio isn't affiliated with YouTube, YouTube Music or Spotify.

</details>

---

<div align="center">

**Made for the music you love.**

[Contribute](CONTRIBUTING.md) · [GPL-3.0 licence](LICENSE)

</div>
