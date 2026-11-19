import unittest
from pathlib import Path
from unittest.mock import patch

from prepare_release_notes import check_product_changed, previous_reachable_tag


ROOT = Path(__file__).resolve().parents[1]


class ReleasePolicyTests(unittest.TestCase):
    def test_releases_are_only_manually_started(self):
        for path in [
            ".github/workflows/release.yml",
            ".github/workflows/experimental.yml",
            ".github/workflows/experimental-baseline.yml",
        ]:
            with self.subTest(path=path):
                text = (ROOT / path).read_text(encoding="utf-8")
                self.assertIn("on:\n  workflow_dispatch:", text)
                self.assertNotIn("\n  push:", text)
                self.assertNotIn("\n  schedule:", text)

    def test_daily_extractor_has_no_publishing_path(self):
        text = (ROOT / ".github/workflows/extractor-update.yml").read_text(encoding="utf-8")
        self.assertNotIn("git push", text)
        self.assertNotIn("gh release create", text)
        self.assertNotIn("uses: ./.github/actions/publish", text)
        self.assertIn("contents: read", text)

    def test_regular_builds_still_run_on_push(self):
        text = (ROOT / ".github/workflows/android.yml").read_text(encoding="utf-8")
        self.assertIn("on:\n  push:", text)
        self.assertIn("test_release_*.py", text)

    @patch("prepare_release_notes.command", return_value="")
    def test_no_app_source_changes_block_release(self, command):
        with self.assertRaisesRegex(ValueError, "No Android app"):
            check_product_changed("experimental-v0.1.172")
        self.assertTrue(command.called)

    @patch("prepare_release_notes.command", return_value="app/src/main/java/Groovio.kt")
    def test_real_app_source_changes_are_allowed(self, command):
        check_product_changed("experimental-v0.1.172")
        self.assertTrue(command.called)

    @patch(
        "prepare_release_notes.command",
        return_value="v0.1.71\nexperimental-v0.1.172\nexperimental-v0.1.169",
    )
    def test_previous_tag_from_correct_channel(self, command):
        self.assertEqual("experimental-v0.1.172", previous_reachable_tag("experimental"))
        self.assertEqual("v0.1.71", previous_reachable_tag("stable"))


if __name__ == "__main__":
    unittest.main()
