"""Verify retained/redacted Nginx diagnostics in the built image, using synthetic credentials only."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import shutil
import tempfile
import time
import uuid


def check(image):
    if not re.fullmatch(r"[a-z0-9][a-z0-9./:_-]{0,180}", image):
        raise ValueError("Invalid image reference")
    private = "SYNTHETIC_PRIVATE_CANARY"
    fixtures = [
        f'2026/10/29 [error] 1#1: connect() failed (111: Connection refused), client: 192.0.2.1, request: "GET /api/x?token={private} HTTP/1.1", upstream: "http://backend/x?token={private}", host: "{private}"',
        f'2026/10/29 [warn] 1#1: invalid header: "Authorization: Bearer {private}", client: 192.0.2.1',
        f'2026/10/29 [error] 1#1: open() "/usr/share/nginx/html/{private}@example.test" failed (2: No such file)',
        f'2026/10/29 [error] 1#1: invalid input "{private}',
        f'2026/10/29 [warn] 1#1: GET /verify?token={private}',
        '2026/10/29 [error] 1#1: invalid input "escaped\\\"' + private + '"',
        '2026/10/29 [crit] 1#1: worker process exited with code 1',
    ]
    result = subprocess.run(["docker", "run", "--rm", "--read-only", "--network", "none", "-i", "--entrypoint", "awk", image,
                             "-f", "/usr/local/lib/redact-nginx-errors.awk"],
                            input="\n".join(fixtures) + "\n", text=True, capture_output=True, timeout=60, check=True)
    retained = all(value in result.stdout for value in ["[error]", "[warn]", "[crit]", "111: Connection refused", "worker process exited"])
    passed = len(result.stdout.splitlines()) == len(fixtures) and private not in result.stdout and "192.0.2.1" not in result.stdout and retained
    if not passed:
        raise ValueError("Nginx log retention/redaction policy failed")
    return {"passed": True, "cases": len(fixtures), "errorsRetained": True, "sensitiveValuesAbsent": True}


def runtime_check(image):
    """No application network, ports, credentials or volumes: a dead upstream produces a real retained 502 error."""
    openssl = shutil.which("openssl")
    if not openssl and Path("C:/Program Files/Git/usr/bin/openssl.exe").is_file():
        openssl = "C:/Program Files/Git/usr/bin/openssl.exe"
    if not openssl:
        raise RuntimeError("OpenSSL is required to generate the disposable TLS fixture")
    name = "r28-log-check-" + uuid.uuid4().hex[:12]
    upstream, edge = name + "-backend", name + "-edge"
    created = []
    network = False
    private = "SYNTHETIC_RUNTIME_PRIVATE_CANARY"
    def run(*args, **options):
        return subprocess.run(list(args), check=True, capture_output=True, text=True, timeout=60, **options)
    with tempfile.TemporaryDirectory(prefix="languageapp-r28-tls-") as directory:
        work = Path(directory).resolve()
        if work.parent != Path(tempfile.gettempdir()).resolve() or not work.name.startswith("languageapp-r28-tls-"):
            raise RuntimeError("Unexpected temporary fixture directory")
        run(openssl, "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-days", "1", "-subj", "/CN=localhost",
            "-keyout", str(work / "key.pem"), "-out", str(work / "cert.pem"))
        # The app's TLS filenames, containing only this ephemeral fixture.
        shutil.copyfile(work / "key.pem", work / "cert.key")
        shutil.copyfile(work / "cert.pem", work / "cert.crt")
        try:
            run("docker", "network", "create", "--internal", name)
            network = True
            run("docker", "run", "-d", "--name", upstream, "--network", name, "--network-alias", "backend",
                "--no-healthcheck", "--entrypoint", "/bin/sh", image, "-c", "sleep 120")
            created.append(upstream)
            run("docker", "run", "-d", "--name", edge, "--network", name,
                "--mount", "type=bind,src=" + str(work) + ",dst=/etc/nginx/ssl,readonly", image)
            created.append(edge)
            healthy = False
            for _ in range(20):
                probe = subprocess.run(["docker", "exec", edge, "/bin/sh", "-c",
                    'test -p /var/log/nginx/diagnostic.pipe && kill -0 "$(cat /run/nginx-log-redactor.pid)" && nginx -t'],
                    capture_output=True, text=True, timeout=10)
                if probe.returncode == 0:
                    healthy = True
                    break
                time.sleep(0.25)
            if not healthy:
                raise RuntimeError("Disposable Nginx/redactor did not become healthy")
            # Use the frontend build's pinned Node runtime as an internal-network HTTPS test client.
            # Internal Docker networks do not publish a host port; no production listener is used.
            client_image = "node:22.23.4-alpine@sha256:fe4f6d74e20370acb04ad889077d14d0f64a9c8ea6a42bc60cddf1e844699914"
            javascript = """const https=require('node:https');
                const request=https.get(process.argv[1],{rejectUnauthorized:false},response=>{
                    let data='';response.on('data',chunk=>data+=chunk);
                    response.on('end',()=>{try{console.log(JSON.stringify({status:response.statusCode,
                        correlation:response.headers['x-request-id'],body:JSON.parse(data)}));}
                        catch(error){process.exitCode=2;}});
                });request.on('error',()=>{process.exitCode=3;});
                request.setTimeout(10000,()=>request.destroy());"""
            client = name + "-client"
            created.append(client)
            result = run("docker", "run", "--rm", "--read-only", "--name", client, "--network", name,
                         "--entrypoint", "node", client_image, "-e", javascript,
                         "https://" + edge + "/api/diagnostic-check?token=" + private)
            received = json.loads(result.stdout)
            status,body,correlation = received['status'],received['body'],received['correlation']
            logs = run("docker", "logs", edge)
            output = logs.stdout + logs.stderr
            retained = "[error]" in output and "Connection refused" in output
            report = {"runtimeHealthy": True, "runtime502": status == 502,
                      "runtimeErrorRetained": retained, "runtimeSensitiveAbsent": private not in output,
                      "structuredStatus": '\"status\":502' in output,
                      "errorSchemaCorrelated": body.get("status") == 502 and body.get("requestId") == correlation and bool(correlation)}
            if not all(report.values()):
                raise ValueError("Real proxy log retention/redaction check failed")
            return report
        finally:
            for container in reversed(created):
                subprocess.run(["docker", "rm", "--force", "--volumes", container],capture_output=True,timeout=30)
            if network:
                subprocess.run(["docker", "network", "rm", name],capture_output=True,timeout=30)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("image")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    report = check(args.image)
    report.update(runtime_check(args.image))
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print("Nginx diagnostics: 7 synthetic cases and isolated real-proxy 502 passed; errors retained and sensitive values redacted")


if __name__ == "__main__":
    main()
