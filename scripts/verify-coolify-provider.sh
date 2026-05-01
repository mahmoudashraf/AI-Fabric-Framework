#!/usr/bin/env bash
set -euo pipefail

TOKEN_FILE="${COOLIFY_TOKEN_FILE:-/tmp/coolify_api_tokens.env}"
STAGING_BASE_URL="${COOLIFY_STAGING_BASE_URL:-http://46.224.145.148:8000}"
PRODUCTION_BASE_URL="${COOLIFY_PRODUCTION_BASE_URL:-http://46.225.162.106:8000}"
STRICT_APPLICATION_SMOKE="${COOLIFY_STRICT_APPLICATION_SMOKE:-false}"

STAGING_PROJECT_UUID="${COOLIFY_STAGING_PROJECT_UUID:-id069t43frp519u5i3dg2jpr}"
STAGING_ENVIRONMENT_NAME="${COOLIFY_STAGING_ENVIRONMENT_NAME:-staging}"
STAGING_ENVIRONMENT_UUID="${COOLIFY_STAGING_ENVIRONMENT_UUID:-h1433m09ezg882q7xmf3ae0x}"
STAGING_SERVER_UUID="${COOLIFY_STAGING_SERVER_UUID:-zf25hgk9694bt7q0zwb98ado}"
STAGING_DESTINATION_UUID="${COOLIFY_STAGING_DESTINATION_UUID:-xjhfu65nacrr30xax5cp0ry7}"
SMOKE_IMAGE="${COOLIFY_SMOKE_IMAGE:-nginx}"
SMOKE_IMAGE_TAG="${COOLIFY_SMOKE_IMAGE_TAG:-latest}"
SMOKE_PORT="${COOLIFY_SMOKE_PORT:-80}"

if [[ -f "${TOKEN_FILE}" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "${TOKEN_FILE}"
  set +a
fi

require_secret() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    echo "FAIL: missing required secret environment variable ${name}. Load ${TOKEN_FILE} or export it." >&2
    exit 1
  fi
}

api_url() {
  local base_url="$1"
  local path="$2"
  base_url="${base_url%/}"
  if [[ "${base_url}" == */api/v1 ]]; then
    printf '%s%s' "${base_url}" "${path}"
  else
    printf '%s/api/v1%s' "${base_url}" "${path}"
  fi
}

curl_json() {
  local method="$1"
  local base_url="$2"
  local token="$3"
  local path="$4"
  local data="${5:-}"
  local url
  url="$(api_url "${base_url}" "${path}")"
  if [[ -n "${data}" ]]; then
    curl -fsS \
      -X "${method}" \
      -H "Authorization: Bearer ${token}" \
      -H "Accept: application/json" \
      -H "Content-Type: application/json" \
      --data "${data}" \
      "${url}"
  else
    curl -fsS \
      -X "${method}" \
      -H "Authorization: Bearer ${token}" \
      -H "Accept: application/json" \
      "${url}"
  fi
}

json_field() {
  local field="$1"
  python3 -c 'import json,sys
raw = sys.stdin.read().strip()
try:
    data = json.loads(raw)
except Exception:
    print(raw)
    raise SystemExit(0)
if isinstance(data, dict):
    print(data.get(sys.argv[1], ""))
else:
    print(data)
' "${field}"
}

json_len() {
  python3 -c 'import json,sys; data=json.load(sys.stdin); print(len(data) if isinstance(data, list) else 0)'
}

verify_instance() {
  local label="$1"
  local base_url="$2"
  local token="$3"
  local version
  local health
  local app_count
  version="$(curl_json GET "${base_url}" "${token}" "/version" | json_field version)"
  health="$(curl_json GET "${base_url}" "${token}" "/health")"
  app_count="$(curl_json GET "${base_url}" "${token}" "/applications" | json_len)"
  echo "[coolify] ${label}: version=${version:-unknown} health=${health} applications=${app_count}"
}

create_smoke_body() {
  local name="$1"
  python3 - "$name" \
    "${STAGING_PROJECT_UUID}" \
    "${STAGING_SERVER_UUID}" \
    "${STAGING_ENVIRONMENT_NAME}" \
    "${STAGING_ENVIRONMENT_UUID}" \
    "${STAGING_DESTINATION_UUID}" \
    "${SMOKE_IMAGE}" \
    "${SMOKE_IMAGE_TAG}" \
    "${SMOKE_PORT}" <<'PY'
import json
import sys

name, project_uuid, server_uuid, environment_name, environment_uuid, destination_uuid, image, tag, port = sys.argv[1:]
print(json.dumps({
    "project_uuid": project_uuid,
    "server_uuid": server_uuid,
    "environment_name": environment_name,
    "environment_uuid": environment_uuid,
    "destination_uuid": destination_uuid,
    "docker_registry_image_name": image,
    "docker_registry_image_tag": tag,
    "ports_exposes": port,
    "name": name,
    "description": "Disposable Coolify provider verification smoke application.",
    "health_check_enabled": False,
    "instant_deploy": False,
    "autogenerate_domain": False,
    "is_force_https_enabled": False
}))
PY
}

run_staging_smoke() {
  local token="$1"
  local name="codex-coolify-smoke-$(date +%s)"
  local body
  local uuid
  body="$(create_smoke_body "${name}")"
  uuid="$(curl_json POST "${STAGING_BASE_URL}" "${token}" "/applications/dockerimage" "${body}" | json_field uuid)"
  if [[ -z "${uuid}" ]]; then
    echo "FAIL: Coolify smoke application create did not return uuid." >&2
    exit 1
  fi

  cleanup_smoke() {
    curl_json DELETE "${STAGING_BASE_URL}" "${token}" "/applications/${uuid}?delete_configurations=true&delete_volumes=true&docker_cleanup=true&delete_connected_networks=true" >/dev/null 2>&1 || true
  }
  trap cleanup_smoke EXIT

  curl_json PATCH "${STAGING_BASE_URL}" "${token}" "/applications/${uuid}/envs/bulk" \
    '{"data":[{"key":"COOLIFY_PROVIDER_SMOKE","value":"true","is_preview":false,"is_literal":true,"is_multiline":false,"is_shown_once":false}]}' >/dev/null
  curl_json GET "${STAGING_BASE_URL}" "${token}" "/applications/${uuid}/start?force=true&instant_deploy=true" >/dev/null

  local status=""
  for _ in $(seq 1 24); do
    status="$(curl_json GET "${STAGING_BASE_URL}" "${token}" "/applications/${uuid}" | json_field status)"
    if [[ "${status}" == *running* || ( "${status}" == *healthy* && "${status}" != *unhealthy* ) ]]; then
      echo "[coolify] staging smoke: uuid=${uuid} status=${status}"
      curl_json GET "${STAGING_BASE_URL}" "${token}" "/applications/${uuid}/logs?lines=20" >/dev/null 2>&1 || true
      cleanup_smoke
      trap - EXIT
      return
    fi
    sleep 5
  done

  echo "FAIL: Coolify staging smoke application did not report running/healthy status before timeout. Last status=${status:-unknown}." >&2
  exit 1
}

require_secret COOLIFY_STAGING_API_TOKEN
require_secret COOLIFY_PRODUCTION_API_TOKEN

verify_instance staging "${STAGING_BASE_URL}" "${COOLIFY_STAGING_API_TOKEN}"
verify_instance production "${PRODUCTION_BASE_URL}" "${COOLIFY_PRODUCTION_API_TOKEN}"

if [[ "${STRICT_APPLICATION_SMOKE}" == "true" ]]; then
  run_staging_smoke "${COOLIFY_STAGING_API_TOKEN}"
else
  echo "[coolify] strict staging application smoke skipped; set COOLIFY_STRICT_APPLICATION_SMOKE=true to create and delete a disposable staging app."
fi

echo "[coolify] provider verification completed."
