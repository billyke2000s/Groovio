#!/usr/bin/env python3
"""Prepare user-facing release notes and reject releases without product changes.

Called only by manual GitHub Actions workflow_dispatch. Android build/test jobs
run independently on every push and never publish to GitHub Releases.
"""
from __future__ import annotations

import argparse
import os
from pathlib import Path
import re
import subprocess

TAG = re.compile(r"^(experimental-)?v0\.1\.(\d+)$")
SOURCE_PATHS = (
    "app/src/main", "app/build.gradle.kts", "build.gradle.kts",
    "settings.gradle.kts", "gradle",
)
DEVELOPER_PREFIX = re.compile(
    r"^(?:feat|fix|chore|docs|test|ci|refactor|build|perf)(?:\([^)]*\))?!?:\s*",
    re.IGNORECASE,
)
TECHNICAL_MARKER = re.compile(
    r"\b(?:nanojson|gradle|kotlin|workflow|commit\s+[0-9a-f]{7,}|\.kt\b|\.java\b|stacktrace)\b",
    re.IGNORECASE,
)


def command(*args: str) -> str:
    return subprocess.check_output(["git", *args], text=True).strip()


def user_facing_changes(raw: str) -> list[str]:
    if len(raw) > 1800:
        raise ValueError("Release notes are too long; use up to six short user-facing lines.")
    items = []
    for line in raw.splitlines():
        line = line.strip().removeprefix("- ").strip()
        if not line:
            continue
        if len(line) < 10 or len(line) > 155:
            raise ValueError("Each change needs 10–155 characters of clear, useful text.")
        if DEVELOPER_PREFIX.match(line) or TECHNICAL_MARKER.search(line):
            raise ValueError("Describe what changed in the app, not Kotlin/Gradle/commit internals.")
        if line.startswith(("#", "*", ">")):
            raise ValueError("Provide plain text lines, not Markdown headings.")
        if line.casefold() in {item.casefold() for item in items}:
            continue
        items.append(line)
    if not items or len(items) > 6:
        raise ValueError("Provide 1–6 distinct user-facing improvements (one per line).")
    return items


def previous_reachable_tag(channel: str) -> str:
    pattern = "experimental-v0.1.*" if channel == "experimental" else "v0.1.*"
    candidates = []
    for tag in command("tag", "--merged", "HEAD", "--list", pattern).splitlines():
        match = TAG.fullmatch(tag)
        if match and bool(match.group(1)) == (channel == "experimental"):
            candidates.append((int(match.group(2)), tag))
    if not candidates:
        raise ValueError("No previous reachable release tag: refusing a possible duplicate release.")
    return max(candidates)[1]


def check_product_changed(previous: str) -> None:
    changed = command("diff", "--name-only", previous, "HEAD", "--", *SOURCE_PATHS)
    if not changed:
        raise ValueError(
            "No Android app/source/dependency changes since " + previous +
            ". No release will be published for docs, tests or workflow changes."
        )


def notes_for(items: list[str], channel: str, previous: str) -> str:
    notes = "## What's new\n\n" + "\n".join("- " + item for item in items)
    notes += "\n\n## Update information\n\n"
    if channel == "experimental":
        notes += "Experimental features may still need real-device testing.\n\n"
    notes += (
        "Install over the existing Groovio app, not by uninstalling it. "
        "Library JSON backups exclude downloaded audio files.\n\n"
        "Changes since " + previous + " were reviewed before this release.\n"
    )
    return notes


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--channel", choices=("stable", "experimental"), required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    items = user_facing_changes(os.environ.get("GROOVIO_RELEASE_CHANGES", ""))
    previous = previous_reachable_tag(args.channel)
    check_product_changed(previous)
    args.output.write_text(notes_for(items, args.channel, previous), encoding="utf-8")
    print("Release approved for manual build; compared Android source against " + previous)


if __name__ == "__main__":
    try:
        main()
    except ValueError as error:
        raise SystemExit("Release not published: " + str(error))
