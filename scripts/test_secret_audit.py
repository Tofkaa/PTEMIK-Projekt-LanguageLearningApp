from pathlib import Path
import tempfile
import unittest
from types import SimpleNamespace
from unittest.mock import patch

from secret_audit import git, safe_source, sanitize_report


class SecretAuditTest(unittest.TestCase):
    def test_reports_never_retain_values_source_lines_or_personal_git_metadata(self):
        row = {"File": "source.txt", "RuleID": "example", "StartLine": 1, "Commit": "abc",
               "Fingerprint": "abc:source.txt:example:1", "Secret": "not-to-be-retained",
               "Match": "sensitive line", "Author": "person", "Email": "person@example.invalid",
               "Message": "sensitive commit message"}
        result = sanitize_report([row], 1)[0]
        self.assertEqual(set(result), {"File", "RuleID", "StartLine", "Commit", "Fingerprint"})

    def test_empty_missing_invalid_or_disagreeing_reports_fail_closed(self):
        for data, code in [(None, 0), ({}, 0), ([], 2), ([], 1), ([{}], 1),
                           ([{"File": "x", "RuleID": "x", "StartLine": 1}], 0)]:
            with self.assertRaises(ValueError):
                sanitize_report(data, code)
        self.assertEqual(sanitize_report([], 0), [])

    def test_snapshot_paths_cannot_escape_repository(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            with self.assertRaises(ValueError):
                safe_source(root, "../outside")
            self.assertEqual(safe_source(root, "source.txt"), root / "source.txt")

    def test_nul_delimited_file_names_keep_leading_whitespace(self):
        with patch("secret_audit.run", return_value=SimpleNamespace(stdout=b" leading.txt\0")):
            self.assertEqual(git(Path("."), "ls-files", "-z", strip=False), " leading.txt\0")


if __name__ == "__main__":
    unittest.main()
