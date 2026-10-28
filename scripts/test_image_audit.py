import unittest

from image_audit import findings


class ImageAuditTest(unittest.TestCase):
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
