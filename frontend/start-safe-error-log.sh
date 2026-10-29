#!/bin/sh
set -eu
fifo=/var/log/nginx/diagnostic.pipe
if [ ! -p "$fifo" ]; then
    mkfifo -m 600 "$fifo"
fi
# The reader remains in the same container; all error lines are retained after redaction.
awk -f /usr/local/lib/redact-nginx-errors.awk < "$fifo" >&2 &
printf '%s\n' "$!" > /run/nginx-log-redactor.pid
