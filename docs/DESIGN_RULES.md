# Groovio design rules

- Dark UI, clear hierarchy, original Groovio branding.
- Full-screen player with large square artwork, title, artist, progress, transport controls, device and queue controls.
- Final design: real codec/bitrate/sample-rate labels directly after song title; horizontally scroll together for long names.
- Preserve accessibility labels, touch targets, contrast, and font scaling.
- Do not present placeholders as finished functionality.
- Accent colour is Groovio coral `#FF7A45` (not Spotify green). Palette lives in `ui/theme/Theme.kt`.
- Only show navigation tabs and controls that work; Search and Library tabs arrive with their features.

## Motion guidelines

- Keep the current dark/orange layout. Motion helps indicate navigation, intent and playback state; it must not insert new controls, reflow the UI or delay a tap.
- Use the shared `groovioClickable` / `groovioCombinedClickable` press response for prominent tappable tiles, cards and rows. Preserve native ripple, long-press callbacks and hit targets. No decorative perpetual card movement.
- Use short fades/slides for navigation and the mini player, and compact crossfades/scales for play/pause and favourites. Keep swipe-to-skip/dismiss directly responsive to the finger.
- Follow Android's system animation scale/reduced-motion behaviour. Avoid animating audio processing, queue mutations, scrolling positions, search text or downloading state as fake progress.
- Check transitions with large libraries, offline mode, screen rotation, TalkBack and rapid repeated taps on a real device before promoting experimental.
