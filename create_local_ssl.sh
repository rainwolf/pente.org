#!/usr/bin/env bash

clear; printf '\e[3J';

cd "$(dirname "${BASH_SOURCE[0]}")" || exit 1

[ ! -f .env ] || export $(grep -v '^#' .env | xargs)

openssl req -x509 -out localhost.crt -keyout localhost.key \
  -newkey rsa:2048 -nodes -sha256 -days 3650 \
  -subj '/CN=localhost' -extensions EXT -config <( \
   printf "[dn]\nCN=localhost\n[req]\ndistinguished_name = dn\n[EXT]\nsubjectAltName=DNS:localhost,IP:10.0.2.2\nkeyUsage=digitalSignature\nextendedKeyUsage=serverAuth")

mkdir -p dockerMain/ssl
openssl pkcs12 -export -in localhost.crt -inkey localhost.key -out dockerMain/ssl/keystore.p12.tmp -password pass:"$SSL_PWD" -name tomcat

#rm localhost.crt
rm localhost.key

if ! mv dockerMain/ssl/keystore.p12.tmp dockerMain/ssl/keystore.p12; then
  echo "ERROR: keystore not built, dev cert not copied to the app" >&2
  exit 1
fi

# Pin this dev cert in the Android debug build (debug-overrides trust anchor).
APP_RAW_DIR="$PWD/../penteLive-Android/app/src/debug/res/raw"
if ! mkdir -p "$APP_RAW_DIR" || ! cp localhost.crt "$APP_RAW_DIR/dev_localhost.crt"; then
  echo "ERROR: failed to copy localhost.crt to $APP_RAW_DIR/dev_localhost.crt" >&2
  exit 1
fi
