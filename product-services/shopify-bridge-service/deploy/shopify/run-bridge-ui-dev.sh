#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVICE_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
UI_ROOT="${SERVICE_ROOT}/ui"
FRONTEND_PORT_VALUE="${PORT:-${FRONTEND_PORT:-4175}}"
BACKEND_PORT_VALUE="${BACKEND_PORT:-8080}"

export PORT="${FRONTEND_PORT_VALUE}"
export FRONTEND_PORT="${FRONTEND_PORT_VALUE}"
export BACKEND_PORT="${BACKEND_PORT_VALUE}"

cd "${UI_ROOT}"
exec npm run dev -- --host 0.0.0.0 --port "${FRONTEND_PORT_VALUE}"
