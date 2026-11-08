# Architecture

## Current implementation
- `MainActivity`: edge-to-edge host for `GroovioApp`.
- `ui/GroovioApp`: navigation (home, full player, queue) and the mini-player.
- `playback/PlayerViewModel`: wraps a Media3 `MediaController`; exposes `PlayerUiState` as a `StateFlow` and position as a lifecycle-collected `Flow`.
- `playback/GroovioPlaybackService`: Media3 `MediaSessionService` + ExoPlayer, audio focus, pause on becoming noisy.
- `playback/AudioFormatLabel`: inline codec/bitrate/sample-rate label from the selected track.
- Android document picker supplies read URIs for user-selected files (multiple selection forms the queue).

- `online/YouTubeMusic`: YouTube Music search and stream resolution via NewPipe Extractor (`NewPipeDownloader` over OkHttp). `StreamSelector` picks the best original audio; `TrackMatcher` is the Discord bot's matcher, ported.
- Online songs are queued as `groovio://youtube/<id>`; `GroovioPlaybackService` resolves them to real URLs in a `ResolvingDataSource` on the loader thread, caches them (4h) and re-resolves once on HTTP errors.
- `data/LibraryDatabase` (Room): tracks, favourites, history, playlists, saved queue and playback state. `LibraryRepository` is the only entry point. A track's identity is its media id (`yt:<id>` or a local `content://` URI); `TrackMapping` derives the playable URI from it.
- The service records history once a song actually plays, saves the queue (debounced), restores it on start, and rebuilds URIs for items from external controllers.
- `ui/home`, `ui/search`, `ui/library`, `ui/player`: Compose screens; `GroovioApp` hosts tabs (Home, Search, Library), the mini-player, full player and queue.

- `online/SpotifyLinks` parses Spotify links and the public embed page (no account or key); `online/SpotifyImporter` fetches it and matches each song to YouTube with `TrackMatcher` (YouTube Music songs, then videos). `PlayerViewModel.playSpotify` plays the first match immediately and appends the rest.
- `MainActivity` accepts `ACTION_SEND` text (Share → Groovio) and opens it in Search.

## Planned
- Artist and album pages.

No backend, Lavalink server, or account is required. Online features need an internet connection.
