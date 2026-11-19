# Groovio design rules

The current product identity is a **dark, coral/orange `#FF7A45` interface** with original Groovio branding, not a one-to-one Spotify copy. Preserve its look; improve consistency without inventing screens or buttons.

## Existing surfaces

- Home, Search, Library, full player, mini-player, queue, artist/album/collection details, spoken discovery and **Your music** are implemented—not merely future navigation milestones.
- The full player has square artwork, readable title/artist, actual selected codec details inline when known, timeline, transport, shuffle/repeat, device output and queue.
- **Your music** is the unified screen for completed private downloads and imported phone audio. Optional `Music/Groovio/` exports are separate files, not a second app library view.
- Collection tiles open details rather than starting playback as a navigation side effect; explicit Play or track selection begins music.
- Reuse `SongMenu` and `CollectionMenu` where appropriate while retaining spoken-only progress/follow actions. Existing gaps are tracked in [UI_ACTION_PARITY_AUDIT](UI_ACTION_PARITY_AUDIT.md).
- Maintain contrast, clear hierarchy, adequate touch targets, accessibility labels, font scaling, screen-safe padding and responsiveness at small and large sizes.

## Motion

- Use `groovioClickable` and `groovioCombinedClickable` for genuine press/long-press feedback, keeping click bounds, semantics and ripples correct.
- Keep navigation fades/slides short and swipes directly finger-following. No decorative perpetual movement, new unrequested UI controls or fake progress.
- Respect system animation-scale/reduced-motion settings. Test rapid taps, TalkBack, back navigation, rotation, large libraries and disconnected networks on a physical device.
- A documentation cleanup must never alter code, colours, player layout, gestures or image assets.

## Evidence and screenshots

- Public screenshots in `docs/screenshots/` are **real Groovio captures**, not generated/mocked images or another app. Preserve the genuine Home, song-menu and playlist-menu screenshots in README.
- Product captures and CI-rendered sample fixture screenshots are not equivalent. Neither proves real phone playback.
- The [current-state matrix](CURRENT_STATE.md) distinguishes stable, experimental, implemented and proposed features. Do not call the last group shipped.
