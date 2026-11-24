#!/usr/bin/env bash
# Rebuild the PKCS12 keystore the container reads, from the cert getssl just renewed.
# Installed at /home/debian/letsencrypt/build_keystore.sh; run by getssl via RELOAD_CMD.
# Usage: build_keystore.sh [ssl_dir]   (default /home/debian/dockerMain/ssl)
set -euo pipefail
umask 077

SSL_DIR="${1:-/home/debian/dockerMain/ssl}"
ENV_FILE="${ENV_FILE:-/home/debian/.env}"

# Take SSL_PWD from the environment, else from the one matching line of .env.
# The .env is never sourced as shell.
if [[ -z "${SSL_PWD:-}" && -r "$ENV_FILE" ]]; then
  SSL_PWD="$(grep -m1 '^SSL_PWD=' "$ENV_FILE" | sed 's/^SSL_PWD=//' || true)"
  # Mimic docker compose: drop a trailing CR, then one pair of matching quotes.
  SSL_PWD="${SSL_PWD%$'\r'}"
  if [[ "$SSL_PWD" =~ ^\"(.*)\"$ || "$SSL_PWD" =~ ^\'(.*)\'$ ]]; then
    SSL_PWD="${BASH_REMATCH[1]}"
  fi
fi
if [[ -z "${SSL_PWD:-}" ]]; then
  echo "build_keystore: SSL_PWD not set in environment or $ENV_FILE" >&2
  exit 1
fi
export SSL_PWD

for f in fullchain.crt pente.org.key; do
  if [[ ! -r "$SSL_DIR/$f" ]]; then
    echo "build_keystore: missing $SSL_DIR/$f" >&2
    exit 1
  fi
done

# Write to a temp file in the same dir, then mv over the live keystore.
# The password goes via env:, so it never shows up in the process list.
TMP="$SSL_DIR/keystore.p12.tmp"
trap 'rm -f "$TMP"' EXIT

openssl pkcs12 -export \
  -in "$SSL_DIR/fullchain.crt" \
  -inkey "$SSL_DIR/pente.org.key" \
  -name tomcat \
  -passout env:SSL_PWD \
  -out "$TMP"
chmod 600 "$TMP"
mv "$TMP" "$SSL_DIR/keystore.p12"
echo "build_keystore: wrote $SSL_DIR/keystore.p12"
