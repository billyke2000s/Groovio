## Playback compatibility hotfix

- Restores the player construction and stream-retry behaviour from the last working version.
- Removes the network-status gate that could reject a playable online song before trying it.
- Temporarily rolls back volume processing, next-track pre-resolution and automatic failed-track skipping while phone playback is verified. The corresponding settings are hidden during this rollback.
- Keeps saved downloads, offline listening, library backups and your existing library.
- Removes the permanent Downloads counter and extra top spacing from the normal app screen. Downloads remains in Settings; the encouraging message appears only when offline.
- Shows buffering and playback errors in the mini-player so failures aren't presented as an ordinary paused song.

Android build checks do not substitute for phone playback verification.
