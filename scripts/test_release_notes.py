import unittest

from prepare_release_notes import (
    notes_for,
    user_facing_changes,
)


class ReleaseNoteTests(unittest.TestCase):
    def test_clear_user_facing_descriptions(self):
        lines = "Live lyrics: Follow along as a song plays.\n- Tap a lyric to jump to that part."
        self.assertEqual(2, len(user_facing_changes(lines)))
        body = notes_for(user_facing_changes(lines), "experimental", "experimental-v0.1.172")
        self.assertIn("## What's new", body)
        self.assertIn("- Live lyrics: Follow along as a song plays.", body)
        self.assertIn("Experimental features", body)

    def test_rejects_missing_or_technical_content(self):
        samples = ["", "feat(lyrics): fix nanojson parser keyword",
                   "fix: Correct some code", "Kotlin Gradle compilation issues",
                   "x", "x" * 160]
        for text in samples:
            with self.subTest(text=text):
                with self.assertRaises(ValueError):
                    user_facing_changes(text)

    def test_limits_and_deduplicates(self):
        one = "Improved offline search across saved songs."
        self.assertEqual([one], user_facing_changes(one + "\n" + one))
        with self.assertRaises(ValueError):
            user_facing_changes("\n".join("Improved user feature " + str(n) for n in range(7)))


if __name__ == "__main__":
    unittest.main()
