"""Scan publishable files and HEAD's complete history in offline, isolated Gitleaks.

Ignored deployment files are never copied. Reports contain locations only, never
matched values, source lines, author details or scanner output. Policy canaries
must succeed before a zero-finding project result can pass.
"""
import argparse
import base64
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import sys
import tempfile
import tomllib

ROOT = Path(__file__).resolve().parents[1]
GITLEAKS = "ghcr.io/gitleaks/gitleaks@sha256:c00b6bd0aeb3071cbcb79009cb16a60dd9e0a7c60e2be9ab65d25e6bc8abbb7f"
SAFE_FIELDS = ("File", "RuleID", "StartLine", "Commit", "Fingerprint")


def run(command, **kwargs):
    return subprocess.run(command, check=True, capture_output=True, timeout=600, **kwargs)


def git(directory, *args, strip=True):
    env = dict(os.environ, GIT_CONFIG_NOSYSTEM="1", GIT_CONFIG_GLOBAL=os.devnull,
               GIT_TERMINAL_PROMPT="0")
    result = run(["git", "-c", "core.hooksPath=/dev/null", "-c", "commit.gpgsign=false",
                  "-C", str(directory), *args], env=env).stdout.decode("utf-8")
    return result.strip() if strip else result


def safe_source(root, name):
    path = root / name
    if not path.resolve().is_relative_to(root.resolve()) or path.is_symlink():
        raise ValueError("Publishable symlink escapes the supported scan boundary")
    return path


def sanitize_report(data, returncode):
    if returncode not in (0, 1) or not isinstance(data, list):
        raise ValueError("Gitleaks did not complete")
    if bool(data) != (returncode == 1):
        raise ValueError("Gitleaks exit status disagrees with its report")
    sanitized = []
    for row in data:
        if not isinstance(row, dict) or not all(row.get(k) for k in ("File", "RuleID", "StartLine")):
            raise ValueError("Incomplete Gitleaks finding")
        sanitized.append({key: row.get(key, "") for key in SAFE_FIELDS})
    return sanitized


def scan(work, mode, target, name):
    report = work / (name + ".json")
    command = ["docker", "run", "--rm", "--network", "none", "-v", f"{work}:/scan",
               "-e", "GIT_CONFIG_COUNT=1", "-e", "GIT_CONFIG_KEY_0=safe.directory",
               "-e", f"GIT_CONFIG_VALUE_0={target}", GITLEAKS, mode, target,
               "--config", "/scan/policy.toml", "--gitleaks-ignore-path", "/scan/policy.ignore",
               "--redact=100", "--no-banner", "--no-color", "--log-level", "error",
               "--ignore-gitleaks-allow", "--max-decode-depth=2",
               "--report-format", "json", "--report-path", f"/scan/{name}.json"]
    if mode == "git":
        command.append("--log-opts=HEAD")
    result = subprocess.run(command, capture_output=True, timeout=600)
    if not report.is_file():
        raise ValueError("Missing Gitleaks report")
    return sanitize_report(json.loads(report.read_text(encoding="utf-8")), result.returncode)


def prove_policy(work):
    policy = tomllib.loads((work / "policy.toml").read_text(encoding="utf-8"))
    literal = policy["allowlists"][0]["regexes"][0].removeprefix("^").removesuffix("$")
    if not re.fullmatch(r"[A-Za-z0-9/=]+", literal):
        raise ValueError("Expected the reviewed literal fixture policy")
    proof = work / "proof"
    test_path = "backend/src/test/java/com/languageapp/backend/"
    allowed = test_path + "AllowedCanaryPersistenceTest.java"
    rejected = {test_path + "NewCanaryPersistenceTest.java",
                "backend/src/main/java/com/languageapp/backend/Canary.java", "frontend/ssl/cert.key"}
    expected = {(name, "private-key" if name.endswith("cert.key") else "generic-api-key")
                for name in rejected}
    contents = {
        allowed: f'jwt.secret="{literal}"\n',
        test_path + "NewCanaryPersistenceTest.java": f'api_key="{secrets.token_urlsafe(32)}" # gitleaks:allow\n',
        "backend/src/main/java/com/languageapp/backend/Canary.java": f'jwt.secret="{literal}"\n',
        "frontend/ssl/cert.key": "-----BEGIN " + "PRIVATE KEY-----\n" +
        base64.b64encode(secrets.token_bytes(128)).decode() + "\n-----END " + "PRIVATE KEY-----\n",
    }
    for name, content in contents.items():
        path = proof / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
    found = scan(work, "dir", "/scan/proof", "proof-dir")
    if {(row["File"].removeprefix("/scan/proof/"), row["RuleID"]) for row in found} != expected:
        raise ValueError("Gitleaks policy failed its positive/negative canaries")
    git(proof, "init", "--quiet")
    git(proof, "add", ".")
    git(proof, "-c", "user.name=Gate canary", "-c", "user.email=gate@example.invalid",
        "commit", "--quiet", "-m", "Synthetic secret detection canaries")
    for name in rejected:
        (proof / name).unlink()
    git(proof, "add", "-u")
    git(proof, "-c", "user.name=Gate canary", "-c", "user.email=gate@example.invalid",
        "commit", "--quiet", "-m", "Remove synthetic secrets; history must still detect them")
    historical = scan(work, "git", "/scan/proof", "proof-git")
    if {(row["File"], row["RuleID"]) for row in historical} != expected:
        raise ValueError("Deleted secrets or new private keys escaped the history scan")
    if scan(work, "dir", "/scan/proof", "proof-clean"):
        raise ValueError("Reviewed dummy fixture was not scoped correctly")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report-dir", type=Path, required=True)
    args = parser.parse_args()
    if git(ROOT, "rev-parse", "--is-shallow-repository") != "false":
        raise ValueError("Full HEAD history is required; use checkout fetch-depth: 0")
    head = git(ROOT, "rev-parse", "HEAD")
    output = args.report_dir.resolve()
    output.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="languageapp-secret-audit-") as folder:
        work = Path(folder)
        shutil.copyfile(ROOT / ".gitleaks.toml", work / "policy.toml")
        shutil.copyfile(ROOT / ".gitleaksignore", work / "policy.ignore")
        prove_policy(work)
        history = work / "repo.git"
        history.mkdir()
        git(history, "init", "--quiet", "--bare")
        git(history, "fetch", "--quiet", "--no-tags", str(ROOT), head)
        git(history, "update-ref", "refs/heads/scan", head)
        git(history, "symbolic-ref", "HEAD", "refs/heads/scan")
        snapshot = work / "snapshot"
        snapshot.mkdir()
        paths = git(ROOT, "ls-files", "-z", "--cached", "--others", "--exclude-standard", strip=False).split("\0")
        count = 0
        for name in filter(None, paths):
            source = safe_source(ROOT, name)
            if not source.exists():
                continue  # A tracked deletion is still examined in HEAD history.
            if not source.is_file():
                raise ValueError("Unsupported non-file in the publishable snapshot")
            target = snapshot / name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, target)
            count += 1
        if not count:
            raise ValueError("Empty source snapshot")
        reports = {"history": scan(work, "git", "/scan/repo.git", "history"),
                   "snapshot": scan(work, "dir", "/scan/snapshot", "snapshot")}
        if git(ROOT, "rev-parse", "HEAD") != head:
            raise ValueError("HEAD changed while scanning")
        for name, report in reports.items():
            (output / (name + ".json")).write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        summary = {"complete": True, "head": head, "scanner": GITLEAKS,
                   "checkedAt": datetime.now(timezone.utc).isoformat(), "policyCanaries": "passed",
                   "snapshotFiles": count, "findings": {key: len(value) for key, value in reports.items()}}
        (output / "summary.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
        print(f"Secret audit: policy canaries passed; {count} publishable files; "
              f"history={len(reports['history'])}, snapshot={len(reports['snapshot'])}")
        return 1 if any(reports.values()) else 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as error:
        print(f"Secret audit FAILED ({type(error).__name__}); no secret values logged", file=sys.stderr)
        sys.exit(2)
