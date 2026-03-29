#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../../.." && pwd)"
ENV_FILE="${ENV_FILE:-${SCRIPT_DIR}/prod-local.env}"
JAR_PATH="${REPO_ROOT}/Platfrom/backend/target/ai-enablement-platform-backend-0.1.0-SNAPSHOT.jar"
TUNNEL_PID=""
TUNNEL_LOG_FILE=""

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "Missing ${ENV_FILE}."
  echo "Copy ${SCRIPT_DIR}/prod-local.env.example to ${SCRIPT_DIR}/prod-local.env and fill in real values."
  exit 1
fi

set -a
# shellcheck source=/dev/null
source "${ENV_FILE}"
set +a

PLATFORM_SERVER_PORT_VALUE="${PLATFORM_SERVER_PORT:-8088}"
PLATFORM_TUNNEL_MODE="${PLATFORM_TUNNEL_MODE:-manual}"

cleanup() {
  if [[ -n "${TUNNEL_PID}" ]] && kill -0 "${TUNNEL_PID}" >/dev/null 2>&1; then
    kill "${TUNNEL_PID}" >/dev/null 2>&1 || true
  fi
  if [[ -n "${TUNNEL_LOG_FILE}" && -f "${TUNNEL_LOG_FILE}" ]]; then
    rm -f "${TUNNEL_LOG_FILE}"
  fi
}

trap cleanup EXIT

require_var() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    echo "Missing required variable: ${name}"
    exit 1
  fi
}

require_command() {
  local name="$1"
  if ! command -v "${name}" >/dev/null 2>&1; then
    echo "Missing required command: ${name}"
    exit 1
  fi
}

require_public_url() {
  local url="$1"
  if [[ -z "${url}" ]]; then
    echo "PLATFORM_PUBLIC_BASE_URL must not be empty."
    exit 1
  fi
  case "${url}" in
    http://localhost*|https://localhost*|http://127.0.0.1*|https://127.0.0.1*)
      echo "PLATFORM_PUBLIC_BASE_URL must be publicly reachable in RAILWAY_API mode."
      exit 1
      ;;
  esac
}

extract_cloudflared_url() {
  python3 - "$1" <<'PY'
import re
import sys
from pathlib import Path

text = Path(sys.argv[1]).read_text(encoding="utf-8", errors="ignore")
match = re.search(r"https://[a-zA-Z0-9-]+\.trycloudflare\.com", text)
if match:
    print(match.group(0), end="")
PY
}

extract_ngrok_url() {
  python3 - <<'PY'
import json
import sys

payload = json.load(sys.stdin)
for tunnel in payload.get("tunnels", []):
    public_url = tunnel.get("public_url", "")
    if public_url.startswith("https://"):
        print(public_url, end="")
        break
PY
}

start_cloudflared_tunnel() {
  require_command cloudflared
  TUNNEL_LOG_FILE="$(mktemp -t platform-cloudflared.XXXXXX.log)"
  cloudflared tunnel --no-autoupdate --url "http://127.0.0.1:${PLATFORM_SERVER_PORT_VALUE}" >"${TUNNEL_LOG_FILE}" 2>&1 &
  TUNNEL_PID=$!

  local public_url=""
  local attempt
  for attempt in {1..60}; do
    if ! kill -0 "${TUNNEL_PID}" >/dev/null 2>&1; then
      echo "cloudflared exited before a public URL was discovered."
      cat "${TUNNEL_LOG_FILE}" || true
      exit 1
    fi
    public_url="$(extract_cloudflared_url "${TUNNEL_LOG_FILE}")"
    if [[ -n "${public_url}" ]]; then
      PLATFORM_PUBLIC_BASE_URL="${public_url}"
      export PLATFORM_PUBLIC_BASE_URL
      return
    fi
    sleep 1
  done

  echo "Timed out waiting for cloudflared to publish a tunnel URL."
  cat "${TUNNEL_LOG_FILE}" || true
  exit 1
}

start_ngrok_tunnel() {
  require_command ngrok
  require_command curl
  TUNNEL_LOG_FILE="$(mktemp -t platform-ngrok.XXXXXX.log)"
  ngrok http "http://127.0.0.1:${PLATFORM_SERVER_PORT_VALUE}" --log=stdout >"${TUNNEL_LOG_FILE}" 2>&1 &
  TUNNEL_PID=$!

  local public_url=""
  local api_base="${PLATFORM_NGROK_API_URL:-http://127.0.0.1:4040}"
  local attempt
  for attempt in {1..60}; do
    if ! kill -0 "${TUNNEL_PID}" >/dev/null 2>&1; then
      echo "ngrok exited before a public URL was discovered."
      cat "${TUNNEL_LOG_FILE}" || true
      exit 1
    fi
    if public_url="$(curl -fsS "${api_base}/api/tunnels" 2>/dev/null | extract_ngrok_url)"; then
      if [[ -n "${public_url}" ]]; then
        PLATFORM_PUBLIC_BASE_URL="${public_url}"
        export PLATFORM_PUBLIC_BASE_URL
        return
      fi
    fi
    sleep 1
  done

  echo "Timed out waiting for ngrok to publish a tunnel URL."
  cat "${TUNNEL_LOG_FILE}" || true
  exit 1
}

require_var RAILWAY_API_TOKEN
require_var RAILWAY_WORKSPACE_ID
require_var PLATFORM_DEPLOY_REPOSITORY
require_var OPENAI_API_KEY
require_var CONNECTOR_API_KEY
require_var ACTIONS_CONNECTOR_API_KEY
require_var PLATFORM_ARTIFACT_SIGNING_KEY

case "${PLATFORM_TUNNEL_MODE}" in
  manual)
    require_var PLATFORM_PUBLIC_BASE_URL
    require_public_url "${PLATFORM_PUBLIC_BASE_URL}"
    ;;
  cloudflared)
    start_cloudflared_tunnel
    require_public_url "${PLATFORM_PUBLIC_BASE_URL}"
    ;;
  ngrok)
    start_ngrok_tunnel
    require_public_url "${PLATFORM_PUBLIC_BASE_URL}"
    ;;
  *)
    echo "Unsupported PLATFORM_TUNNEL_MODE: ${PLATFORM_TUNNEL_MODE}"
    echo "Supported values: manual, cloudflared, ngrok"
    exit 1
    ;;
esac

if [[ ! -f "${JAR_PATH}" || "${PLATFORM_REBUILD_JAR:-true}" == "true" ]]; then
  mvn -f "${REPO_ROOT}/Platfrom/backend/pom.xml" -DskipTests package
fi

declare -a java_args=(
  "--server.port=${PLATFORM_SERVER_PORT_VALUE}"
  "--spring.datasource.url=${PLATFORM_DB_URL:-jdbc:postgresql://localhost:15432/ai_enablement_platform}"
  "--spring.datasource.driver-class-name=org.postgresql.Driver"
  "--spring.datasource.username=${PLATFORM_DB_USERNAME:-platform}"
  "--spring.datasource.password=${PLATFORM_DB_PASSWORD:-platform}"
  "--platform.auth.enabled=true"
  "--platform.auth.api-key-enabled=${PLATFORM_AUTH_API_KEY_ENABLED:-false}"
  "--platform.auth.session-enabled=${PLATFORM_AUTH_SESSION_ENABLED:-true}"
  "--platform.auth.bootstrap-admin-enabled=${PLATFORM_BOOTSTRAP_ADMIN_ENABLED:-true}"
  "--platform.auth.bootstrap-admin-email=${PLATFORM_BOOTSTRAP_ADMIN_EMAIL:-admin@example.com}"
  "--platform.auth.bootstrap-admin-password=${PLATFORM_BOOTSTRAP_ADMIN_PASSWORD:-AdminPass123!}"
  "--platform.auth.bootstrap-admin-display-name=${PLATFORM_BOOTSTRAP_ADMIN_DISPLAY_NAME:-Platform Admin}"
  "--platform.bootstrap.sample-enabled=${PLATFORM_BOOTSTRAP_SAMPLE_ENABLED:-true}"
  "--platform.delivery.public-base-url=${PLATFORM_PUBLIC_BASE_URL}"
  "--platform.provisioning.mode=RAILWAY_API"
  "--platform.provisioning.repository=${PLATFORM_DEPLOY_REPOSITORY}"
  "--platform.provisioning.branch=${PLATFORM_DEPLOY_BRANCH:-main}"
  "--platform.provisioning.environment-name=${PLATFORM_PROVISIONING_ENVIRONMENT:-dev}"
  "--platform.provisioning.workspace-id=${RAILWAY_WORKSPACE_ID}"
  "--platform.cors.allowed-origins=${PLATFORM_CORS_ALLOWED_ORIGINS:-http://localhost:5173}"
  "--platform.cors.allow-credentials=${PLATFORM_CORS_ALLOW_CREDENTIALS:-true}"
  "--platform.public-api.enabled=${PLATFORM_PUBLIC_API_ENABLED:-false}"
)

if [[ -n "${PUBLIC_API_CLIENT_ID:-}" && -n "${PUBLIC_API_CLIENT_SECRET:-}" ]]; then
  java_args+=("--platform.public-api.clients.${PUBLIC_API_CLIENT_ID}=${PUBLIC_API_CLIENT_SECRET}")
fi

echo "Starting platform backend in live Railway provisioning mode."
echo "Tunnel mode: ${PLATFORM_TUNNEL_MODE}"
echo "Public base URL: ${PLATFORM_PUBLIC_BASE_URL}"
echo "Workspace: ${RAILWAY_WORKSPACE_ID}"
echo "Repository: ${PLATFORM_DEPLOY_REPOSITORY}@${PLATFORM_DEPLOY_BRANCH:-main}"
echo "Port: ${PLATFORM_SERVER_PORT_VALUE}"

exec java -jar "${JAR_PATH}" "${java_args[@]}"
