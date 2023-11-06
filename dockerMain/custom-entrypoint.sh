#!/bin/bash

set -eo pipefail

source /old-docker-entrypoint.sh

mysql_note "Custom entrypoint script for MySQL Server ${MYSQL_VERSION} started."

mysql_check_config "$@"
# Load various environment variables
docker_setup_env "$@"

if [ -z "$DATABASE_ALREADY_EXISTS" ]; then
  echo "Database does not exist; not running always-run hooks"
elif test -n "$(shopt -s nullglob; echo /update-root-initdb.d/*)"; then
  # Database exists; run always-run hooks if they exist
  mysql_note "Starting temporary server"
  docker_temp_server_start "$@" --skip-grant-tables --skip-networking
  mysql_note "Temporary server started."

  docker_process_init_files /update-root-initdb.d/*

  mysql_note "Stopping temporary server"
  docker_temp_server_stop
  mysql_note "Temporary server stopped"

  echo
  mysql_note "MySQL init process done. Ready for start up."
  echo
fi

if [ -z "$DATABASE_ALREADY_EXISTS" ]; then
  echo "Database does not exist; not running always-run hooks"
elif test -n "$(shopt -s nullglob; echo /always-initdb.d/*)"; then
  # Database exists; run always-run hooks if they exist
  mysql_note "Starting temporary server"
  docker_temp_server_start "$@"
  mysql_note "Temporary server started."

  docker_process_init_files /always-initdb.d/*

  mysql_note "Stopping temporary server"
  docker_temp_server_stop
  mysql_note "Temporary server stopped"

  echo
  mysql_note "MySQL init process done. Ready for start up."
  echo
fi

./old-docker-entrypoint.sh "$@"
