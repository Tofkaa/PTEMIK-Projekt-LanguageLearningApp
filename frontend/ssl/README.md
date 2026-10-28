# Local HTTPS files

Generate your own `cert.crt` and `cert.key` with the instructions in
[the local HTTPS guide](../../docs/local-https.md).

Only this README belongs in Git. Certificates, keys and backups in this folder
are ignored. Docker Compose mounts this folder read-only into Nginx; it is
excluded from the Docker build context.

Each reviewer or developer generates a separate certificate on their machine.
Never copy an mkcert root CA private key into this repository or send it to others.

An old local leaf key was committed in early history and has been replaced.
Treat historical certificate keys as public; generate your own instead of reusing them.
