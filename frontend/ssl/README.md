# Local HTTPS files

Generate a separate certificate on each machine using the commands in the
[main setup guide](../../README.md#4-helyi-https).

Docker Compose mounts this directory read-only into Nginx. The required local
files are `cert.crt` and `cert.key`; only this README is version-controlled.
Certificates, private keys and backups are ignored and excluded from the Docker
build context. Never share the mkcert root CA private key or reuse certificate
keys from repository history.
