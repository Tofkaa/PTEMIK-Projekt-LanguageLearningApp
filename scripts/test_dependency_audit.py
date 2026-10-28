import unittest

from dependency_audit import audit, coordinates


def component(group="org.example", name="library", version="1.0"):
    return {"group": group, "name": name, "version": version,
            "purl": f"pkg:maven/{group}/{name}@{version}"}


class DependencyAuditTest(unittest.TestCase):
    def test_extracts_unique_external_maven_versions_only(self):
        bom = {"bomFormat": "CycloneDX", "components": [component(), component(),
               component(group="com.languageapp"), {"purl": "pkg:npm/example@1"}]}
        self.assertEqual(coordinates(bom), [{"name": "org.example:library", "version": "1.0"}])

    def test_empty_invalid_or_incomplete_sbom_fails(self):
        for bom in [{}, {"bomFormat": "CycloneDX", "components": []},
                    {"bomFormat": "CycloneDX", "components": [component(version="")]}]:
            with self.assertRaises(ValueError):
                coordinates(bom)

    def test_error_or_incomplete_api_is_never_a_clean_result(self):
        packages = [{"name": "org.example:library", "version": "1.0"}]
        for result in [{}, {"results": []}, {"results": [None]},
                       {"results": [{"error": "unavailable"}]},
                       {"results": [{"vulns": [{}]}]}, {"results": [{"vulns": None}]}]:
            with self.assertRaises(ValueError):
                audit(packages, lambda payload: result)

    def test_retains_findings_and_exact_versions_without_suppression(self):
        packages = [{"name": "org.example:library", "version": "1.0"}]
        result = audit(packages, lambda payload: {"results": [{"vulns": [{"id": "GHSA-test"}]}]})
        self.assertEqual(result[0]["advisoryIds"], ["GHSA-test"])
        self.assertEqual(result[0]["version"], "1.0")

    def test_large_scan_is_batched_and_contains_only_public_coordinates(self):
        packages = [{"name": f"org.example:lib{i}", "version": "1.0"} for i in range(205)]
        calls = []
        def request(payload):
            calls.append(payload)
            return {"results": [{} for _ in payload["queries"]]}
        self.assertEqual(len(audit(packages, request)), 205)
        self.assertEqual([len(c["queries"]) for c in calls], [100, 100, 5])
        self.assertEqual(set(calls[0]["queries"][0]), {"package", "version"})


if __name__ == "__main__":
    unittest.main()
