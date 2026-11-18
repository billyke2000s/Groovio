Experimental stage 1: navigation and update channels. The v0.1.71 playback service is unchanged.

- Animated full-screen search, with artwork and replay from Last Groove before typing.
- Create playlists from bottom navigation using the existing library.
- Explicit experimental update preference; stable selection excludes prereleases.
- Signed with the existing app identity. No library schema changes.

Tests, lint and APK build run before publication. Phone playback, Bluetooth, background listening and screen behaviour still need device verification; a build does not establish these work. This is an experimental prerelease, not a replacement for the confirmed baseline.

Recovery: experimental-baseline-v0.1.71 remains available. Android normally blocks downgrading version codes; export your library before testing. Installing an older APK may require uninstalling first, which removes app data and downloads.
