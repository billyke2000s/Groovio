# Manual release notes

The in-app update dialog already reads the `## What's new` section of GitHub Releases. New releases now use **human-written lines**, required by the manual release workflow and validated by `scripts/prepare_release_notes.py`. They are **not generated from Git commit titles**.

Example input when deliberately publishing an APK:

```
Live lyrics: Follow along with the song in real time.
Tap a lyric to jump to that part of the song.
Previously viewed lyrics stay available offline.
```

Automatic Android CI builds still run on pushes but never publish APK releases. The scheduled NewPipe compatibility check only tests a temporary candidate in the runner and cannot push or publish.
