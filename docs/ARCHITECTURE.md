# Architecture

## Current implementation
- `MainActivity`: edge-to-edge host for `GroovioApp`.
- `ui/GroovioApp`: navigation (home, full player, queue) and the mini-player.
- `playback/PlayerViewModel`: wraps a Media3 `MediaController`; exposes `PlayerUiState` as a `StateFlow` and position as a lifecycle-collected `Flow`.
- `playback/GroovioPlaybackService`: Media3 `MediaSessionService` + ExoPlayer, audio focus, pause on becoming noisy.
- `playback/AudioFormatLabel`: inline codec/bitrate/sample-rate label from the selected track.
- Android document picker supplies read URIs for user-selected files (multiple selection forms the queue).

## Planned modules (not implemented)
- `metadata`: MusicBrainz/Cover Art Archive with caching and respectful rate limits.
- `sources`: provider adapters for legally authorised playable media; no fabricated streams.
- `matching`: Android-native, tested track matching inspired by existing Discord bot.
- `database`: Room entities for favourites, playlists, and listening history.
- `ui/home`, `ui/search`, `ui/library`: Compose navigation and screens.

No backend, Lavalink server, or account is required. Online discovery will still require an internet connection.
