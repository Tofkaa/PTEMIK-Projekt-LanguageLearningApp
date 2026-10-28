# Same PostgreSQL major and Alpine UID as the existing named volume.
FROM postgres:15.19-alpine@sha256:54e0dd8e8cddcdbb9156c2486eed5a43eccb7214d7c6828097d41ce049fb3984

# The upstream entrypoint uses gosu only when started as root. Run directly as
# postgres instead, removing the unused Go executable and its vulnerable runtime.
RUN apk upgrade --no-cache && rm /usr/local/bin/gosu
USER postgres
