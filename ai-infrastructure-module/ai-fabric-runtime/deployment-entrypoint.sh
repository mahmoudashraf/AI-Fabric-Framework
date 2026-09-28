#!/bin/sh
set -eu

case "${PLATFORM_COOLIFY_SERVICE_ROLE:-runtime}" in
  runtime)
    exec /app/runtime-entrypoint.sh "$@"
    ;;
  connector)
    exec /app/connector-entrypoint.sh "$@"
    ;;
  *)
    echo "Unsupported PLATFORM_COOLIFY_SERVICE_ROLE: ${PLATFORM_COOLIFY_SERVICE_ROLE}" >&2
    exit 64
    ;;
esac
