#!/bin/sh
autossh -M "${MONITORING_PORT}" -o "ServerAliveInterval 3" -4 -C -L 0.0.0.0:"$LOCAL_PORT":127.0.0.1:"$REMOTE_PORT" "$USER_AT_HOST" -f -N
