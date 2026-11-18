#!/usr/bin/env python3
"""Block stable APK publication when playback code differs from the phone-tested source."""
import argparse
import json
import subprocess
import sys
from pathlib import Path

AUDIO_PATHS = [
    "app/src/main/java/com/groovio/app/playback",
    "app/src/main/java/com/groovio/app/online",
    "app/src/main/java/com/groovio/app/offline",
    "app/src/main/java/com/groovio/app/data/GroovioSettings.kt",
    "app/src/main/java/com/groovio/app/data/TrackMapping.kt",
    "app/build.gradle.kts",
]

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--candidate", default="HEAD")
    args = parser.parse_args()
    approval = json.loads(Path("docs/PHONE_PLAYBACK_APPROVAL.json").read_text())
    source = approval["source_commit"]
    if approval.get("owner_confirmed") is not True:
        sys.exit("Stable publication blocked: owner phone confirmation is missing.")
    subprocess.run(["git", "cat-file", "-e", f"{source}^{{commit}}"], check=True)
    changed = subprocess.check_output(["git", "diff", "--name-only", source, args.candidate, "--", *AUDIO_PATHS], text=True)
    if changed.strip():
        print("Stable publication blocked: playback differs from the owner-confirmed source.")
        print(changed)
        sys.exit(1)
    print("Playback matches the recorded phone-confirmed source.")
