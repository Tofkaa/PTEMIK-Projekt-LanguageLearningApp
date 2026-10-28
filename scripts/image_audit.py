"""Local image/SBOM audit using pinned Trivy, exported archives and no Docker socket mount."""
import argparse
from collections import Counter
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile

TRIVY = "ghcr.io/aquasecurity/trivy@sha256:790fd66d1697395d8693d1acaa1ea702406a20f109b6fc7bac8db623eca6c06c"


def findings(report):
    if report.get("SchemaVersion") != 2 or not isinstance(report.get("Results"), list):
        raise ValueError("Incomplete Trivy vulnerability report")
    if not report["Results"] or not report.get("Metadata", {}).get("OS", {}).get("Family"):
        raise ValueError("No OS package scan: an empty scan is not success")
    if report["Metadata"]["OS"].get("EOSL"):
        raise ValueError("Unsupported end-of-life image OS")
    if not any(target.get("Class") == "os-pkgs" and isinstance(target.get("Packages"), list)
               and target["Packages"] for target in report["Results"]):
        raise ValueError("Missing OS package inventory")
    result = []
    for target in report["Results"]:
        values = target.get("Vulnerabilities", [])
        if not isinstance(values, list):
            raise ValueError("Invalid Trivy vulnerability list")
        for value in values:
            if not all(value.get(k) for k in ("VulnerabilityID", "PkgName", "InstalledVersion")):
                raise ValueError("Incomplete Trivy vulnerability")
            result.append({**value, "Target": target.get("Target")})
    return result


def run(command, **kwargs):
    return subprocess.run(command, check=True, timeout=1200, **kwargs)


def scanner_identity():
    # Bind-mounted files must remain removable by the non-root Linux CI runner.
    # Windows Docker Desktop maps host filesystem permissions separately.
    return ["--user", f"{os.getuid()}:{os.getgid()}"] if os.name == "posix" else []


def scanner_command(mounts, *arguments, offline=False):
    command = ["docker", "run", "--rm", *scanner_identity(), "--env", "HOME=/tmp"]
    if offline:
        command += ["--network", "none"]
    return [*command, *mounts, TRIVY, "--cache-dir", "/cache", *arguments]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("images", nargs="+", help="Explicit local images; never running containers")
    parser.add_argument("--report-dir", type=Path, required=True)
    parser.add_argument("--cache-dir", type=Path)
    args = parser.parse_args()
    destination = args.report_dir.resolve()
    destination.mkdir(parents=True, exist_ok=True)
    failed = False
    summary = []
    with tempfile.TemporaryDirectory(prefix="languageapp-image-audit-") as folder:
        work = Path(folder)
        cache = args.cache_dir.resolve() if args.cache_dir else work / "cache"
        cache.mkdir(parents=True, exist_ok=True)
        mounts = ["-v", f"{work}:/scan", "-v", f"{cache}:/cache"]
        for index, image in enumerate(args.images):
            identifier = run(["docker", "image", "inspect", image, "--format", "{{.Id}}"],
                             capture_output=True, text=True).stdout.strip()
            if not re.fullmatch(r"sha256:[a-f0-9]{64}", identifier):
                raise ValueError("Docker did not identify a local image")
            # Use the immutable local ID after inspect so a tag race cannot change the scan target.
            run(["docker", "image", "save", "--output", str(work / "image.tar"), identifier])
            run(scanner_command(mounts, "image", "--input", "/scan/image.tar",
                                "--scanners", "vuln", "--list-all-pkgs", "--format", "json",
                                "--output", "/scan/report.json", "--no-progress"))
            report = json.loads((work / "report.json").read_text(encoding="utf-8"))
            vulnerabilities = findings(report)
            # Converting the same complete scan provides an image inventory, not a second scan.
            run(scanner_command(mounts, "convert", "--format", "cyclonedx",
                                "--output", "/scan/sbom.json", "/scan/report.json", offline=True))
            (destination / f"image-{index}.json").write_text(
                json.dumps(report, indent=2) + "\n", encoding="utf-8")
            sbom = json.loads((work / "sbom.json").read_text(encoding="utf-8"))
            if sbom.get("bomFormat") != "CycloneDX" or not sbom.get("components"):
                raise ValueError("Missing image SBOM components")
            (destination / f"image-{index}.cdx.json").write_text(
                json.dumps(sbom, indent=2) + "\n", encoding="utf-8")
            counts = dict(Counter(v.get("Severity", "UNKNOWN") for v in vulnerabilities))
            summary.append({"image": image, "imageId": identifier, "counts": counts,
                            "report": f"image-{index}.json", "sbom": f"image-{index}.cdx.json"})
            print(f"{image}: {len(vulnerabilities)} vulnerability matches {counts}", flush=True)
            for value in vulnerabilities:
                print(f"  {value['VulnerabilityID']} {value['PkgName']}:{value['InstalledVersion']} "
                      f"fixed={value.get('FixedVersion', 'unavailable')}", flush=True)
            failed |= bool(vulnerabilities)
        (destination / "summary.json").write_text(
            json.dumps({"scanner": TRIVY, "complete": True, "images": summary}, indent=2) + "\n",
            encoding="utf-8")
    return 1 if failed else 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as error:
        print(f"Image audit FAILED ({type(error).__name__})", file=sys.stderr)
        sys.exit(2)
