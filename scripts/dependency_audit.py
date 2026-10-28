"""Audit external Maven coordinates from a CycloneDX SBOM against public OSV metadata.

Only package coordinates/versions leave the machine, never sources or configuration.
Network errors and incomplete scans fail closed; no implicit vulnerability suppressions.
"""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import sys
import urllib.request

API = "https://api.osv.dev/v1/querybatch"


def coordinates(bom):
    packages = set()
    if bom.get("bomFormat") != "CycloneDX":
        raise ValueError("Expected a CycloneDX JSON SBOM")
    for component in bom.get("components", []):
        if not component.get("purl", "").startswith("pkg:maven/"):
            continue
        group, name, version = (component.get(key) for key in ("group", "name", "version"))
        if not all(isinstance(value, str) and value.strip() for value in (group, name, version)):
            raise ValueError("Incomplete Maven component in SBOM")
        if group == "com.languageapp":
            continue  # The application's own component is not a public Maven dependency.
        packages.add((group + ":" + name, version))
    if not packages:
        raise ValueError("No external Maven components: an empty scan is not success")
    return [{"name": name, "version": version} for name, version in sorted(packages)]


def audit(packages, request_json):
    results = []
    for offset in range(0, len(packages), 100):
        batch = packages[offset:offset + 100]
        payload = {"queries": [{"package": {"ecosystem": "Maven", "name": p["name"]},
                                "version": p["version"]} for p in batch]}
        answer = request_json(payload)
        responses = answer.get("results")
        if not isinstance(responses, list) or len(responses) != len(batch):
            raise ValueError("Incomplete OSV batch response")
        for package, response in zip(batch, responses):
            if not isinstance(response, dict) or "error" in response:
                raise ValueError("OSV returned a query error")
            vulnerabilities = response.get("vulns", [])
            if not isinstance(vulnerabilities, list) or any(
                    not isinstance(v, dict) or not isinstance(v.get("id"), str) or not v["id"]
                    for v in vulnerabilities):
                raise ValueError("Invalid OSV vulnerability response")
            results.append({**package, "advisoryIds": sorted({v["id"] for v in vulnerabilities})})
    return results


def request_json(payload):
    request = urllib.request.Request(API, data=json.dumps(payload).encode("utf-8"),
                                     headers={"Content-Type": "application/json",
                                              "User-Agent": "LanguageApp-dependency-audit/1.0"})
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.load(response)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("sbom", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    packages = coordinates(json.loads(args.sbom.read_text(encoding="utf-8-sig")))
    results = audit(packages, request_json)
    affected = [p for p in results if p["advisoryIds"]]
    report = {"checkedAt": datetime.now(timezone.utc).isoformat(), "service": API,
              "complete": True, "packages": results}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(f"OSV: {len(results)} Maven components checked, {len(affected)} affected")
    for package in affected:
        print(f"{package['name']}:{package['version']}: {', '.join(package['advisoryIds'])}")
    return 1 if affected else 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as error:
        # Never print HTTP bodies, proxy settings, credentials or the SBOM itself on failure.
        print(f"Dependency audit FAILED ({type(error).__name__})", file=sys.stderr)
        sys.exit(2)
