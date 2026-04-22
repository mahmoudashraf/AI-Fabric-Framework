#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVICE_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
PORT_VALUE="${PORT:-${SERVER_PORT:-${BACKEND_PORT:-8080}}}"

export PORT="${PORT_VALUE}"
export SERVER_PORT="${PORT_VALUE}"

cd "${SERVICE_ROOT}"
exec mvn -DskipTests spring-boot:run
