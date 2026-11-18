<div align="center">

# 🎧 Groovio

### Find your next favourite. Keep your groove.

**A native Android music player for discovering, organising and listening to music — online or offline.**

Dark by design. Coral accents. Your library stays on your phone.

[**Get the APK**](https://github.com/billyke2000s/Groovio/releases/latest) · [**All releases**](https://github.com/billyke2000s/Groovio/releases) · [**Build from source**](CONTRIBUTING.md)

</div>

---

## A look inside Groovio

<p align="center">
  <a href="docs/screenshots/home.webp"><img src="docs/screenshots/home.webp" width="30%" alt="Actual Groovio Android Home screen showing playlist shortcuts, Jump back in, personalised mixes and the mini-player"></a>
  &nbsp;
  <a href="docs/screenshots/song-menu.webp"><img src="docs/screenshots/song-menu.webp" width="30%" alt="Actual Groovio Android song options sheet with artwork, playback and playlist actions"></a>
  &nbsp;
  <a href="docs/screenshots/playlist-menu.webp"><img src="docs/screenshots/playlist-menu.webp" width="30%" alt="Actual Groovio Android playlist options sheet over Home"></a>
</p>

<p align="center"><sub>Real screenshots captured from Groovio on Android and supplied by the app owner — no generated mockups. The menu captures are from an earlier experimental build; labels and actions may have changed since.</sub></p>

## What you can do

| | |
| --- | --- |
| **Discover music** | Search songs and artists, explore albums and mixes, and return to recent playlists with **Jump back in**. |
| **Build your library** | Like songs, make and edit playlists, add songs to the queue, and organise collections in list or grid view. |
| **Listen offline** | **Your music** brings Groovio downloads and imported phone audio together. Play, shuffle and browse saved tracks without an internet connection. |
| **Take control** | Full-screen player, swipe-to-skip artwork, mini-player, queue ordering, shuffle, repeat, seeking and sleep timer. |
| **Tune your sound** | Built-in five-band equaliser and presets, plus optional volume normalisation. Effect support depends on the device. |
| **Explore more** | Browse available public podcasts and audiobooks, save supported content and resume listening. |

### Made for listening

Groovio uses a dark interface with coral-orange accents, album artwork and quick access to your favourite collections. Tap a collection to inspect its tracks; long-press songs or playlists for their available actions. The mini-player keeps playback in reach while you browse.

**Your music** is the home for audio already saved on your device: verified Groovio downloads appear alongside files you import from Android storage. The optional public copy in \`Music/Groovio\` is not required for offline playback inside Groovio.

## Install on Android

**Requires Android 8.0 or newer.** Download an APK from the [releases page](https://github.com/billyke2000s/Groovio/releases), open it on your phone and allow installs from that source if Android prompts you.

- **Stable:** [Latest stable release](https://github.com/billyke2000s/Groovio/releases/latest).
- **Experimental:** [Find experimental prereleases](https://github.com/billyke2000s/Groovio/releases) for newer features that may still need testing.
- **Recovery build:** [v0.1.71](https://github.com/billyke2000s/Groovio/releases/tag/experimental-baseline-v0.1.71), an older build whose music playback was confirmed on the owner's phone.

The \`main\` source may be newer than the latest published stable APK. Stable publication has a phone-playback approval check. Check [GitHub Actions](https://github.com/billyke2000s/Groovio/actions) for build results rather than assuming every source change is already available in an APK.

**Keep your library when updating:** install a newer signed APK over the existing app — **do not uninstall** just to update. Use **Settings → Back up library** before experimental updates. Library backups include playlists, likes and settings, **not downloaded audio files**. Android may prevent downgrading to an older version without uninstalling.

## How music works

Groovio plays available YouTube / YouTube Music audio using on-device extraction. A song can be streamed or saved for offline playback when available. Downloading a playlist queues its eligible tracks; unavailable matches remain unavailable rather than being replaced with invented audio.

Spotify links are used for **track and collection metadata only**. Groovio attempts to match those entries to available YouTube audio; it does **not** play Spotify's protected streams or guarantee every track in a playlist can be matched.

There are **no required accounts, subscriptions or hosted Groovio servers**. Your playlists, favourites, listening history and settings are stored locally. Music availability still depends on the source, region and network, and some videos may be unavailable or blocked.

## Under the hood

Built with **Kotlin**, **Jetpack Compose**, **AndroidX Media3**, **Room** and **WorkManager**. Artwork uses **Coil**; online extraction uses [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor). Optional genre metadata comes from [MusicBrainz](https://musicbrainz.org/).

Groovio is in active development. Some features (including podcast/audiobook discovery, audio effects and newer offline workflows) still need real-device verification. For implementation details and the checks actually completed, see [experimental status](docs/EXPERIMENTAL_STATUS.md).

**Groovio is independent and is not affiliated with YouTube, YouTube Music or Spotify.**

---

<div align="center">

[**Releases**](https://github.com/billyke2000s/Groovio/releases) · [**Contributing**](CONTRIBUTING.md) · [**GPL-3.0 licence**](LICENSE)

</div>
