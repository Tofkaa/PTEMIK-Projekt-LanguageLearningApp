"""Reproducible backend gates. Integration owns a new Docker PostgreSQL, never an app DB.

Python 3.12+, Java 21 and Docker (integration only). No third-party Python packages.
"""
import argparse
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SOURCES = ROOT / "backend/src/test/java"
REPORTS = ROOT / "backend/target/surefire-reports"
MANIFEST = Path(__file__).with_name("database-tests.json")
POSTGRES_BUILD = ROOT / "database"
GUARD = re.compile(
    r'@EnabledIfEnvironmentVariable\(named\s*=\s*"(\w+)"\s*,\s*matches\s*=\s*'
    r'"jdbc:postgresql://\[\^/\]\+/([a-z_]+)\[a-zA-Z0-9_\]\+"\)'
)


def inventory():
    """Require review of every new/changed Spring DB guard before it can pass CI."""
    expected = json.loads(MANIFEST.read_text(encoding="utf-8"))
    actual = {}
    for source in SOURCES.rglob("*.java"):
        content = source.read_text(encoding="utf-8")
        match = GUARD.search(content)
        if "@SpringBootTest" in content and not match:
            raise RuntimeError(f"Unguarded Spring context: {source.name}")
        if match:
            actual[source.stem] = {"environment": match[1], "prefix": match[2]}
    if actual != expected:
        raise RuntimeError("DB test inventory changed: review scripts/database-tests.json")
    return expected


def child_environment():
    # Do not inherit deployment secrets, opt-in URLs, Spring overrides or Maven options.
    allowed = {"PATH", "JAVA_HOME", "HOME", "USERPROFILE", "SYSTEMROOT", "WINDIR",
               "COMSPEC", "PATHEXT", "TEMP", "TMP", "TMPDIR", "LOCALAPPDATA",
               "APPDATA", "PROGRAMFILES", "PROGRAMFILES(X86)", "SYSTEMDRIVE"}
    env = {key: value for key, value in os.environ.items() if key.upper() in allowed}
    env.update({"SPRING_DATASOURCE_URL": "jdbc:postgresql://127.0.0.1:1/must_not_be_used",
                "SPRING_DATASOURCE_USERNAME": "app_user",
                "SPRING_DATASOURCE_PASSWORD": "unused-test-password"})
    return env


def verify_reports(directory, guarded, integration, started, required=None):
    files = list(directory.glob("TEST-*.xml"))
    if not files:
        raise RuntimeError("No Surefire XML reports: tests did not run")
    seen, executed, skipped = set(), 0, 0
    for path in files:
        if path.stat().st_mtime < started - 2:  # filesystem timestamp resolution
            raise RuntimeError(f"Stale Surefire report: {path.name}")
        suite = ET.parse(path).getroot()
        if int(suite.attrib.get("failures", 0)) or int(suite.attrib.get("errors", 0)):
            raise RuntimeError(f"Failed suite: {path.name}")
        for case in suite.iter("testcase"):
            name = case.attrib.get("classname", "").rsplit(".", 1)[-1].split("$")[0]
            if case.find("failure") is not None or case.find("error") is not None:
                raise RuntimeError(f"Failed test: {name}.{case.attrib.get('name')}")
            is_skipped = case.find("skipped") is not None
            if is_skipped and (integration or name not in guarded):
                raise RuntimeError(f"Unexpected skipped test: {name}")
            if not integration and name in guarded and not is_skipped:
                raise RuntimeError(f"DB test executed in unit mode: {name}")
            seen.add(name)
            skipped += is_skipped
            executed += not is_skipped
    missing = set(required if required is not None else guarded) - seen
    if missing or not executed:
        raise RuntimeError(f"Incomplete test run: missing={sorted(missing)}, executed={executed}")
    print(f"Verified Surefire: {executed} executed, {skipped} expected skips", flush=True)


def run(command, **kwargs):
    return subprocess.run(command, check=True, cwd=ROOT, timeout=1200, **kwargs)


def maven(env, *goals, release=None):
    wrapper = ROOT / "backend" / ("mvnw.cmd" if os.name == "nt" else "mvnw")
    command = ([str(wrapper)] if os.name == "nt" else ["sh", str(wrapper)])
    command += ["-B", "-q", "-f", str(ROOT / "backend/pom.xml")]
    if release:
        command.append(f"-Dmaven.compiler.release={release}")
    run(command + list(goals), env=env)


class DisposablePostgres:
    """Only operates on its own new container ID, with no bind mounts or app volumes."""
    def __init__(self):
        self.identifier = None
        self.password = secrets.token_hex(24)

    def __enter__(self):
        env = dict(os.environ, POSTGRES_PASSWORD=self.password)
        built = run(["docker", "build", "--quiet", str(POSTGRES_BUILD)],
                    capture_output=True, text=True).stdout.strip()
        if not re.fullmatch(r"sha256:[a-f0-9]{64}", built):
            raise RuntimeError("Docker did not return the isolated PostgreSQL image ID")
        result = run(["docker", "create", "--label", "languageapp.quality=disposable",
                      "-e", "POSTGRES_PASSWORD", "-e", "POSTGRES_USER=app_user",
                      "-p", "127.0.0.1::5432", built],
                     env=env, capture_output=True, text=True)
        identifier = result.stdout.strip()
        if not re.fullmatch(r"[a-f0-9]{64}", identifier):
            raise RuntimeError("Docker did not return a container ID")
        self.identifier = identifier
        try:
            run(["docker", "start", identifier], stdout=subprocess.DEVNULL)
            for _ in range(90):
                ready = subprocess.run(["docker", "exec", identifier, "pg_isready", "-h", "127.0.0.1",
                                        "-U", "app_user", "-d", "postgres"],
                                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                if ready.returncode == 0:
                    # pg_isready may see the image's temporary initialization server.
                    port = run(["docker", "port", identifier, "5432/tcp"],
                               capture_output=True, text=True).stdout.strip()
                    self.port = int(port.rsplit(":", 1)[1])
                    return self
                time.sleep(1)
            raise RuntimeError("Disposable PostgreSQL did not become ready")
        except BaseException:
            self.__exit__(None, None, None)
            raise

    def sql(self, statement):
        run(["docker", "exec", "-i", self.identifier, "psql", "-U", "app_user",
             "-d", "postgres", "-v", "ON_ERROR_STOP=1"],
            input=statement, text=True, stdout=subprocess.DEVNULL)

    def __exit__(self, *args):
        if self.identifier:
            # Remove anonymous PG volume too. Never resolve a user-provided container name.
            run(["docker", "rm", "--force", "--volumes", self.identifier],
                stdout=subprocess.DEVNULL)


def database_name(prefix, suffix):
    if not re.fullmatch(r"(?:languageapp_[a-z_]+_check_|language_learning_utc_check_)", prefix):
        raise ValueError("Unsafe test database prefix")
    name = prefix + suffix
    if len(name) > 63 or not re.fullmatch(r"[a-z_]+[a-f0-9]{12}", name):
        raise ValueError("Unsafe test database name")
    return name


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=["unit", "integration"])
    parser.add_argument("--java-release", help="Optional local compiler release override")
    args = parser.parse_args()
    guarded = inventory()
    required = {p.stem for p in SOURCES.rglob("*.java")
                if re.search(r"@(Test|ParameterizedTest|RepeatedTest|TestFactory|TestTemplate)\b",
                             p.read_text(encoding="utf-8"))}
    env = child_environment()
    started = time.time()
    if args.mode == "unit":
        maven(env, "clean", "test", release=args.java_release)
        verify_reports(REPORTS, guarded, False, started, required)
        return
    with DisposablePostgres() as postgres:
        suffix = secrets.token_hex(6)
        env["SPRING_DATASOURCE_PASSWORD"] = postgres.password
        databases = {entry["environment"]: database_name(entry["prefix"], suffix)
                     for entry in guarded.values()}
        smoke = databases["APPLICATION_SMOKE_TEST_DATABASE_URL"]
        utc = databases["UTC_TEST_DATABASE_URL"]
        for variable, name in databases.items():
            if name != utc:
                postgres.sql(f'CREATE DATABASE "{name}";')
            env[variable] = f"jdbc:postgresql://127.0.0.1:{postgres.port}/{name}"
        # Preserve legacy ddl-auto=validate coverage against a fresh, data-free schema.
        maven(env, "clean", "test", "-Dtest=BackendApplicationTests", release=args.java_release)
        postgres.sql(f'CREATE DATABASE "{utc}" TEMPLATE "{smoke}";')
        started = time.time()
        maven(env, "clean", "package", release=args.java_release)
        verify_reports(REPORTS, guarded, True, started, required)


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, ValueError, subprocess.SubprocessError) as error:
        print(f"Quality gate FAILED: {error}", file=sys.stderr)
        sys.exit(1)
