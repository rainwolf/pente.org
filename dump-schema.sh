#!/usr/bin/env bash
#
# Regenerate dsg_src/sql/schema.sql from the running main DB container.
#
# Schema only -- NO data. A full data dump (used to bootstrap a fresh docker DB)
# lives separately in dockerMain/dbInit/*.sql.gz. AUTO_INCREMENT counters are
# stripped so the file diffs cleanly between runs.
#
# Credentials come from ./.env (MYSQL_USER / MYSQL_PASSWORD / MYSQL_DATABASE) or
# the environment; override the container with DB_CONTAINER.
#
# Usage: ./dump-schema.sh
#
set -eu
cd "$(dirname "$0")"

[ -f .env ] && { set -a; . ./.env; set +a; }

CONTAINER="${DB_CONTAINER:-penteorg-main_db-1}"
OUT="dsg_src/sql/schema.sql"
tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT

# --routines emits a harmless warning (and a non-zero exit) on a DB whose
# mysql.proc predates the server version; tables are still dumped, so tolerate
# it and validate the output instead of trusting the exit code.
docker exec -e MYSQL_PWD="${MYSQL_PASSWORD:?set MYSQL_PASSWORD or provide a .env}" "$CONTAINER" \
    mariadb-dump -u"${MYSQL_USER:-dsg_rw}" \
        --no-data --skip-comments --routines --events --triggers \
        "${MYSQL_DATABASE:-dsg}" > "$tmp" || true

if ! grep -q 'CREATE TABLE' "$tmp"; then
    echo "dump failed: no tables in output (is the container up?)" >&2
    exit 1
fi

sed -E 's/ AUTO_INCREMENT=[0-9]+//' "$tmp" > "$OUT"
echo "wrote $OUT ($(grep -c 'CREATE TABLE' "$OUT") tables)"
