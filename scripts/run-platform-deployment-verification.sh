#!/usr/bin/env bash
set -euo pipefail

# Resolve hosted deployment verification context from the platform and run the
# returned verification script locally.
#
# Usage:
#   PLATFORM_BASE_URL="https://<platform>.up.railway.app" \
#   PLATFORM_DEPLOYMENT_ID="dep-12345678" \
#   VERIFICATION_PROFILE="ecommerce" \
#   VERIFY_WRITE="true" \
#   CONNECTOR_API_KEY="..." \
#   APP_ADMIN_API_KEY="..." \
#   PLATFORM_LOGIN_EMAIL="admin@example.com" \
#   PLATFORM_LOGIN_PASSWORD="..." \
#   ./scripts/run-platform-deployment-verification.sh

PLATFORM_BASE_URL="${PLATFORM_BASE_URL:-${PLATFORM_PUBLIC_BASE_URL:-}}"
PLATFORM_DEPLOYMENT_ID="${PLATFORM_DEPLOYMENT_ID:-}"
VERIFICATION_PROFILE="${VERIFICATION_PROFILE:-ecommerce}"
VERIFY_WRITE="${VERIFY_WRITE:-false}"
VECTORIZATION_COUNTERPART_DEPLOYMENT_ID="${VECTORIZATION_COUNTERPART_DEPLOYMENT_ID:-}"

PLATFORM_API_KEY_HEADER="${PLATFORM_API_KEY_HEADER:-X-PLATFORM-API-KEY}"
PLATFORM_API_KEY="${PLATFORM_API_KEY:-}"
PLATFORM_COOKIE="${PLATFORM_COOKIE:-}"
PLATFORM_LOGIN_EMAIL="${PLATFORM_LOGIN_EMAIL:-}"
PLATFORM_LOGIN_PASSWORD="${PLATFORM_LOGIN_PASSWORD:-}"
PLATFORM_HTTP_RETRY_ATTEMPTS="${PLATFORM_HTTP_RETRY_ATTEMPTS:-4}"
PLATFORM_HTTP_RETRY_SLEEP_SECONDS="${PLATFORM_HTTP_RETRY_SLEEP_SECONDS:-5}"

CONNECTOR_API_KEY="${CONNECTOR_API_KEY:-}"
APP_ADMIN_API_KEY="${APP_ADMIN_API_KEY:-}"
RUNTIME_TRUSTED_BACKEND_API_KEY="${RUNTIME_TRUSTED_BACKEND_API_KEY:-}"
RUNTIME_TRUSTED_BACKEND_API_KEY_HEADER="${RUNTIME_TRUSTED_BACKEND_API_KEY_HEADER:-X-AIFABRIC-RUNTIME-API-KEY}"

TMP_DIR=""
PLATFORM_COOKIE_JAR=""
HTTP_STATUS=""
HTTP_BODY=""

resolve_secret_value() {
  local var_name="$1"
  local file_var_name="${var_name}_FILE"
  local direct_value="${!var_name:-}"
  local file_path="${!file_var_name:-}"

  if [[ -n "${file_path}" ]]; then
    if [[ ! -f "${file_path}" ]]; then
      echo "Missing secret file for ${var_name}: ${file_path}" >&2
      exit 2
    fi
    python3 - <<'PY' "${file_path}"
import pathlib
import sys
print(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
PY
    return
  fi

  printf '%s' "${direct_value}"
}

require_cmd() {
  local cmd="$1"
  if ! command -v "${cmd}" >/dev/null 2>&1; then
    echo "Missing required command: ${cmd}" >&2
    exit 2
  fi
}

trim_slash() {
  local value="$1"
  if [[ "${value}" == */ ]]; then
    printf '%s' "${value%/}"
  else
    printf '%s' "${value}"
  fi
}

assert_status() {
  local expected="$1"
  local label="$2"
  if [[ "${HTTP_STATUS}" != "${expected}" ]]; then
    echo "---- ${label} ----" >&2
    echo "HTTP ${HTTP_STATUS}" >&2
    echo "${HTTP_BODY}" >&2
    echo "------------------" >&2
    echo "FAIL: ${label} (expected HTTP ${expected})" >&2
    exit 1
  fi
}

platform_http() {
  local method="$1"
  local url="$2"
  local body="${3:-}"
  if [[ "$#" -ge 3 ]]; then
    shift 3
  else
    shift "$#"
  fi

  local tmp
  tmp="$(mktemp)"
  local headers=()
  headers+=("-H" "Accept: application/json")
  if [[ "${method}" != "GET" ]]; then
    headers+=("-H" "Content-Type: application/json")
  fi
  if [[ -n "${PLATFORM_API_KEY}" ]]; then
    headers+=("-H" "${PLATFORM_API_KEY_HEADER}: ${PLATFORM_API_KEY}")
  fi
  if [[ -n "${PLATFORM_COOKIE}" ]]; then
    headers+=("-H" "Cookie: ${PLATFORM_COOKIE}")
  fi

  local status=""
  local attempt=1
  while true; do
    if [[ -n "${PLATFORM_COOKIE_JAR}" ]]; then
      if [[ -n "${body}" ]]; then
        status="$(curl -sS -o "${tmp}" -w "%{http_code}" -X "${method}" "${headers[@]}" -b "${PLATFORM_COOKIE_JAR}" -c "${PLATFORM_COOKIE_JAR}" "$@" --data "${body}" "${url}" || true)"
      else
        status="$(curl -sS -o "${tmp}" -w "%{http_code}" -X "${method}" "${headers[@]}" -b "${PLATFORM_COOKIE_JAR}" -c "${PLATFORM_COOKIE_JAR}" "$@" "${url}" || true)"
      fi
    else
      if [[ -n "${body}" ]]; then
        status="$(curl -sS -o "${tmp}" -w "%{http_code}" -X "${method}" "${headers[@]}" "$@" --data "${body}" "${url}" || true)"
      else
        status="$(curl -sS -o "${tmp}" -w "%{http_code}" -X "${method}" "${headers[@]}" "$@" "${url}" || true)"
      fi
    fi
    if [[ "${method}" == "GET" \
        && ( "${status}" == "000" || "${status}" == "502" || "${status}" == "503" || "${status}" == "504" ) \
        && "${attempt}" -lt "${PLATFORM_HTTP_RETRY_ATTEMPTS}" ]]; then
      echo "WARN: transient platform ${method} ${url} returned HTTP ${status}; retrying (${attempt}/${PLATFORM_HTTP_RETRY_ATTEMPTS})..." >&2
      sleep "${PLATFORM_HTTP_RETRY_SLEEP_SECONDS}"
      attempt=$((attempt + 1))
      continue
    fi
    break
  done

  HTTP_STATUS="${status}"
  HTTP_BODY="$(cat "${tmp}")"
  rm -f "${tmp}"
}

platform_login() {
  if [[ -n "${PLATFORM_API_KEY}" || -n "${PLATFORM_COOKIE}" ]]; then
    return 0
  fi
  if [[ -z "${PLATFORM_LOGIN_EMAIL}" || -z "${PLATFORM_LOGIN_PASSWORD}" ]]; then
    echo "Deployment verification requires PLATFORM_API_KEY, PLATFORM_COOKIE, or PLATFORM_LOGIN_EMAIL/PLATFORM_LOGIN_PASSWORD." >&2
    exit 2
  fi

  local payload
  payload="$(mktemp)"
  cat > "${payload}" <<EOF
{"email":"${PLATFORM_LOGIN_EMAIL}","password":"${PLATFORM_LOGIN_PASSWORD}"}
EOF
  platform_http POST "${PLATFORM_BASE_URL}/api/platform/auth/login" "$(cat "${payload}")"
  rm -f "${payload}"
  assert_status 200 "platform login"
}

cleanup() {
  rm -rf "${TMP_DIR}"
}

require_cmd curl
require_cmd python3

PLATFORM_BASE_URL="$(trim_slash "${PLATFORM_BASE_URL}")"
PLATFORM_API_KEY="$(resolve_secret_value PLATFORM_API_KEY)"
PLATFORM_COOKIE="$(resolve_secret_value PLATFORM_COOKIE)"
PLATFORM_LOGIN_EMAIL="$(resolve_secret_value PLATFORM_LOGIN_EMAIL)"
PLATFORM_LOGIN_PASSWORD="$(resolve_secret_value PLATFORM_LOGIN_PASSWORD)"
CONNECTOR_API_KEY="$(resolve_secret_value CONNECTOR_API_KEY)"
APP_ADMIN_API_KEY="$(resolve_secret_value APP_ADMIN_API_KEY)"
RUNTIME_TRUSTED_BACKEND_API_KEY="$(resolve_secret_value RUNTIME_TRUSTED_BACKEND_API_KEY)"

if [[ -z "${PLATFORM_BASE_URL}" || -z "${PLATFORM_DEPLOYMENT_ID}" ]]; then
  echo "Set PLATFORM_BASE_URL and PLATFORM_DEPLOYMENT_ID." >&2
  exit 2
fi
TMP_DIR="$(mktemp -d)"
trap cleanup EXIT
PLATFORM_COOKIE_JAR="${TMP_DIR}/platform-cookies.txt"
touch "${PLATFORM_COOKIE_JAR}"

CONTEXT_URL="${PLATFORM_BASE_URL}/api/deployments/${PLATFORM_DEPLOYMENT_ID}/hosted-verification-context?profile=${VERIFICATION_PROFILE}&verifyWrite=${VERIFY_WRITE}"
AUTH_MODE=""

if [[ -n "${PLATFORM_API_KEY}" ]]; then
  platform_http GET "${CONTEXT_URL}"
  if [[ "${HTTP_STATUS}" =~ ^2 ]]; then
    AUTH_MODE="api_key"
  elif [[ "${HTTP_STATUS}" != "401" && "${HTTP_STATUS}" != "403" ]]; then
    echo "Platform API key fetch failed for deployment context." >&2
    echo "${HTTP_BODY}" >&2
    exit 1
  fi
fi

if [[ -z "${AUTH_MODE}" ]]; then
  PLATFORM_API_KEY=""
  platform_login
  platform_http GET "${CONTEXT_URL}"
  assert_status 200 "deployment verification context"
  AUTH_MODE="session"
fi

CONTEXT_FILE="${TMP_DIR}/deployment-verification-context.json"
printf '%s' "${HTTP_BODY}" > "${CONTEXT_FILE}"

CONTEXT_RUNTIME_BASE_URL="$(CONTEXT_FILE="${CONTEXT_FILE}" python3 - <<'PY'
import json
import os

with open(os.environ["CONTEXT_FILE"], "r", encoding="utf-8") as handle:
    payload = json.load(handle)

print(((payload.get("env") or {}).get("RUNTIME_BASE_URL")) or "")
PY
)"
CONTEXT_REST_CONNECTOR_BASE_URL="$(CONTEXT_FILE="${CONTEXT_FILE}" python3 - <<'PY'
import json
import os

with open(os.environ["CONTEXT_FILE"], "r", encoding="utf-8") as handle:
    payload = json.load(handle)

print(((payload.get("env") or {}).get("REST_CONNECTOR_BASE_URL")) or "")
PY
)"

if [[ -n "${CONTEXT_RUNTIME_BASE_URL}" && -z "${CONTEXT_REST_CONNECTOR_BASE_URL}" && -z "${RUNTIME_TRUSTED_BACKEND_API_KEY}" ]]; then
  echo "Hosted deployment verification requires RUNTIME_TRUSTED_BACKEND_API_KEY for runtime-backed operational checks." >&2
  exit 2
fi

if [[ -n "${CONTEXT_REST_CONNECTOR_BASE_URL}" && -z "${CONNECTOR_API_KEY}" ]]; then
  echo "Hosted deployment verification requires CONNECTOR_API_KEY when direct connector compatibility checks are enabled." >&2
  exit 2
fi

AUTH_MODE="${AUTH_MODE}" \
CONTEXT_FILE="${CONTEXT_FILE}" \
PLATFORM_BASE_URL="${PLATFORM_BASE_URL}" \
PLATFORM_DEPLOYMENT_ID="${PLATFORM_DEPLOYMENT_ID}" \
VERIFICATION_PROFILE="${VERIFICATION_PROFILE}" \
VERIFY_WRITE="${VERIFY_WRITE}" \
PLATFORM_API_KEY="${PLATFORM_API_KEY}" \
PLATFORM_LOGIN_EMAIL="${PLATFORM_LOGIN_EMAIL}" \
PLATFORM_LOGIN_PASSWORD="${PLATFORM_LOGIN_PASSWORD}" \
PLATFORM_COOKIE="${PLATFORM_COOKIE}" \
CONNECTOR_API_KEY="${CONNECTOR_API_KEY}" \
APP_ADMIN_API_KEY="${APP_ADMIN_API_KEY}" \
RUNTIME_TRUSTED_BACKEND_API_KEY="${RUNTIME_TRUSTED_BACKEND_API_KEY}" \
RUNTIME_TRUSTED_BACKEND_API_KEY_HEADER="${RUNTIME_TRUSTED_BACKEND_API_KEY_HEADER}" \
VECTORIZATION_COUNTERPART_DEPLOYMENT_ID="${VECTORIZATION_COUNTERPART_DEPLOYMENT_ID}" \
python3 - <<'PY'
import json
import os
import subprocess
import sys

with open(os.environ["CONTEXT_FILE"], "r", encoding="utf-8") as handle:
    payload = json.load(handle)

script = payload.get("script")
if not script:
    print("Missing verification script in hosted verification context.", file=sys.stderr)
    sys.exit(1)

env = os.environ.copy()
for key, value in (payload.get("env") or {}).items():
    if value is None:
        continue
    env[key] = str(value)

env["PLATFORM_BASE_URL"] = os.environ["PLATFORM_BASE_URL"]
env["PLATFORM_DEPLOYMENT_ID"] = os.environ["PLATFORM_DEPLOYMENT_ID"]
env["API_KEY"] = os.environ["CONNECTOR_API_KEY"]
if os.environ.get("APP_ADMIN_API_KEY"):
    env["RUNTIME_ADMIN_API_KEY"] = os.environ["APP_ADMIN_API_KEY"]
    env["CONNECTOR_ADMIN_API_KEY"] = os.environ["APP_ADMIN_API_KEY"]
if os.environ.get("RUNTIME_TRUSTED_BACKEND_API_KEY"):
    env["RUNTIME_TRUSTED_BACKEND_API_KEY"] = os.environ["RUNTIME_TRUSTED_BACKEND_API_KEY"]
if os.environ.get("RUNTIME_TRUSTED_BACKEND_API_KEY_HEADER"):
    env["RUNTIME_TRUSTED_BACKEND_API_KEY_HEADER"] = os.environ["RUNTIME_TRUSTED_BACKEND_API_KEY_HEADER"]

if os.environ.get("VECTORIZATION_COUNTERPART_DEPLOYMENT_ID"):
    env["VECTORIZATION_COUNTERPART_DEPLOYMENT_ID"] = os.environ["VECTORIZATION_COUNTERPART_DEPLOYMENT_ID"]

if os.environ.get("AUTH_MODE") == "api_key":
    env["PLATFORM_API_KEY"] = os.environ.get("PLATFORM_API_KEY", "")
    env.pop("PLATFORM_LOGIN_EMAIL", None)
    env.pop("PLATFORM_LOGIN_PASSWORD", None)
elif os.environ.get("AUTH_MODE") == "session":
    env.pop("PLATFORM_API_KEY", None)
    if os.environ.get("PLATFORM_COOKIE"):
        env["PLATFORM_COOKIE"] = os.environ["PLATFORM_COOKIE"]
    else:
        env["PLATFORM_LOGIN_EMAIL"] = os.environ.get("PLATFORM_LOGIN_EMAIL", "")
        env["PLATFORM_LOGIN_PASSWORD"] = os.environ.get("PLATFORM_LOGIN_PASSWORD", "")

print(f"Deployment: {payload.get('deploymentId')}")
print(f"Release: {payload.get('releaseId')}")
print(f"Version: {payload.get('deploymentVersionId')}")
print(f"Profile: {payload.get('profile')}")

subprocess.run(["bash", script], env=env, check=True)
PY
