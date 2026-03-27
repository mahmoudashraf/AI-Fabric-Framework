#!/bin/sh
set -eu

APP_USER="spring"
APP_GROUP="spring"

# Ensure mounted volumes are writable by the non-root runtime user.
if [ "$(id -u)" = "0" ]; then
  mkdir -p /app/data
  chown -R "${APP_USER}:${APP_GROUP}" /app/data || true
  exec su-exec "${APP_USER}:${APP_GROUP}" sh -c "exec java ${JAVA_OPTS:-} -jar /app/app.jar"
fi

exec sh -c "exec java ${JAVA_OPTS:-} -jar /app/app.jar"

