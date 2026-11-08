# Architecture

## Current implementation
- `MainActivity`: Compose player screen and Android document picker.
- `GroovioPlaybackService`: AndroidX Media3 `MediaSessionService` and ExoPlayer for local audio playback.
- Android document picker supplies a persistable read URI for a user-selected local file.

## Planned modules (not implemented)
- `metadata`: MusicBrainz/Cover Art Archive with caching and respectful rate limits.
- `sources`: provider adapters for legally authorised playable media; no fabricated streams.
- `matching`: Android-native, tested track matching inspired by existing Discord bot.
- `database`: Room entities for favourites, playlists, and listening history.
- `ui/home`, `ui/search`, `ui/library`: Compose navigation and screens.

No backend, Lavalink server, or account is required. Online discovery will still require an internet connection.
