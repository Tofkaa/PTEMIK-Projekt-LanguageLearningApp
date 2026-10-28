import unittest
from unittest.mock import patch

from image_audit import findings, scanner_command, TRIVY


class ImageAuditTest(unittest.TestCase):
    def test_linux_scan_and_conversion_use_runner_identity_and_a_writable_cache(self):
        mounts = ["-v", "/tmp/audit:/scan", "-v", "/tmp/audit/cache:/cache"]
        with patch("image_audit.os.name", "posix"), \
                patch("image_audit.os.getuid", return_value=1001, create=True), \
                patch("image_audit.os.getgid", return_value=1002, create=True):
            scan = scanner_command(mounts, "image", "--scanners", "vuln")
            conversion = scanner_command(mounts, "convert", offline=True)
        for command in [scan, conversion]:
            self.assertEqual(command[command.index("--user") + 1], "1001:1002")
            self.assertIn("HOME=/tmp", command)
            self.assertEqual(command[command.index("--cache-dir") + 1], "/cache")
            self.assertIn(TRIVY, command)
            self.assertNotIn("/var/run/docker.sock", " ".join(command))
        self.assertNotIn("--network", scan)
        self.assertEqual(conversion[conversion.index("--network") + 1], "none")
        self.assertIn("vuln", scan)

    def test_windows_does_not_invent_a_posix_uid(self):
        with patch("image_audit.os.name", "nt"):
            command = scanner_command([], "image")
        self.assertNotIn("--user", command)
        self.assertIn("HOME=/tmp", command)
        self.assertEqual(command[command.index("--cache-dir") + 1], "/cache")

    def test_empty_or_unsupported_scans_fail_closed(self):
        for report in [{}, {"SchemaVersion": 2, "Results": []},
                       {"SchemaVersion": 2, "Metadata": {"OS": {"Family": "alpine", "EOSL": True}},
                        "Results": [{"Target": "image"}]}]:
            with self.assertRaises(ValueError):
                findings(report)

    def test_unknown_and_unfixed_findings_are_not_suppressed(self):
        vulnerability = {"VulnerabilityID": "CVE-test", "PkgName": "library",
                         "InstalledVersion": "1", "Severity": "UNKNOWN"}
        report = {"SchemaVersion": 2, "Metadata": {"OS": {"Family": "alpine"}},
                  "Results": [{"Target": "alpine", "Class": "os-pkgs", "Packages": [{"Name": "libc"}]},
                              {"Target": "binary", "Vulnerabilities": [vulnerability]}]}
        self.assertEqual(len(findings(report)), 1)

    def test_complete_zero_finding_scan_is_accepted(self):
        self.assertEqual(findings({"SchemaVersion": 2,
                                  "Metadata": {"OS": {"Family": "alpine", "EOSL": False}},
                                  "Results": [{"Target": "alpine", "Class": "os-pkgs",
                                               "Packages": [{"Name": "libc"}]}]}), [])

    def test_incomplete_vulnerability_is_an_error(self):
        with self.assertRaises(ValueError):
            findings({"SchemaVersion": 2, "Metadata": {"OS": {"Family": "alpine"}},
                      "Results": [{"Vulnerabilities": [{}]}]})


if __name__ == "__main__":
    unittest.main()
