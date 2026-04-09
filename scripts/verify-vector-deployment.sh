#!/usr/bin/env bash
set -euo pipefail

# End-to-end verification script for vector-backed AI Fabric deployments.
#
# Verifies:
# - runtime-backed connector and runtime health
# - runtime-backed connector admin and runtime admin endpoints
# - data-sync vector spaces
# - runtime indexing overview for the configured vector backend
# - live upsert/delete roundtrip through the runtime-backed operational surface
# - optional platform release/workspace/verification alignment
#
# Minimal usage:
#   RUNTIME_BASE_URL="https://<runtime>.up.railway.app" \
#   RUNTIME_TRUSTED_BACKEND_API_KEY="test" \
#   RUNTIME_PRIVATE_AUTHORIZATION="Bearer rpa1..." \
#   EXPECTED_VECTOR_SPACES="product" \
#   ./scripts/verify-vector-deployment.sh
#
# With platform verification using session login:
#   PLATFORM_BASE_URL="https://<platform>.up.railway.app" \
#   PLATFORM_DEPLOYMENT_ID="dep-12345678" \
#   PLATFORM_LOGIN_EMAIL="admin@example.com" \
#   PLATFORM_LOGIN_PASSWORD="admin" \
#   ./scripts/verify-vector-deployment.sh
#
# Verified profiles:
# - Qdrant Cloud platform-managed using existing-cluster reuse
# - Weaviate Cloud external-existing
# - Pinecone platform-managed
# - Milvus platform-managed through Zilliz Cloud

# Qdrant example:
#   RUNTIME_BASE_URL="https://runtime-dep-xxxxxxxx-dev.up.railway.app" \
#   RUNTIME_TRUSTED_BACKEND_API_KEY="test" \
#   RUNTIME_PRIVATE_AUTHORIZATION="Bearer rpa1..." \
#   EXPECTED_VECTOR_SPACES="product" \
#   EXPECTED_VECTOR_DB="QdrantVectorDatabaseService" \
#   PLATFORM_BASE_URL="https://<platform>.up.railway.app" \
#   PLATFORM_DEPLOYMENT_ID="dep-xxxxxxxx" \
#   PLATFORM_LOGIN_EMAIL="admin@example.com" \
#   PLATFORM_LOGIN_PASSWORD="<password>" \
#   ./scripts/verify-vector-deployment.sh
#
# Pinecone example:
#   RUNTIME_BASE_URL="https://runtime-dep-xxxxxxxx-dev.up.railway.app" \
#   RUNTIME_TRUSTED_BACKEND_API_KEY="test" \
#   RUNTIME_PRIVATE_AUTHORIZATION="Bearer rpa1..." \
#   EXPECTED_VECTOR_SPACES="product" \
#   EXPECTED_VECTOR_DB="PineconeVectorDatabaseService" \
#   PLATFORM_BASE_URL="https://<platform>.up.railway.app" \
#   PLATFORM_DEPLOYMENT_ID="dep-xxxxxxxx" \
#   PLATFORM_LOGIN_EMAIL="admin@example.com" \
#   PLATFORM_LOGIN_PASSWORD="<password>" \
#   ./scripts/verify-vector-deployment.sh
#
# Milvus/Zilliz example:
#   RUNTIME_BASE_URL="https://runtime-dep-xxxxxxxx-dev.up.railway.app" \
#   RUNTIME_TRUSTED_BACKEND_API_KEY="test" \
#   RUNTIME_PRIVATE_AUTHORIZATION="Bearer rpa1..." \
#   EXPECTED_VECTOR_SPACES="product" \
#   EXPECTED_VECTOR_DB="MilvusVectorDatabaseService" \
#   PLATFORM_BASE_URL="https://<platform>.up.railway.app" \
#   PLATFORM_DEPLOYMENT_ID="dep-xxxxxxxx" \
#   PLATFORM_LOGIN_EMAIL="admin@example.com" \
#   PLATFORM_LOGIN_PASSWORD="<password>" \
#   ./scripts/verify-vector-deployment.sh

RUNTIME_BASE_URL="${RUNTIME_BASE_URL:-}"

RUNTIME_TRUSTED_BACKEND_API_KEY_HEADER="${RUNTIME_TRUSTED_BACKEND_API_KEY_HEADER:-X-AIFABRIC-RUNTIME-API-KEY}"
RUNTIME_TRUSTED_BACKEND_API_KEY="${RUNTIME_TRUSTED_BACKEND_API_KEY:-}"
RUNTIME_PRIVATE_AUTHORIZATION_HEADER="${RUNTIME_PRIVATE_AUTHORIZATION_HEADER:-X-AIFABRIC-RUNTIME-AUTHORIZATION}"
RUNTIME_PRIVATE_AUTHORIZATION="${RUNTIME_PRIVATE_AUTHORIZATION:-}"
RUNTIME_CONNECTOR_HEALTH_URL="${RUNTIME_CONNECTOR_HEALTH_URL:-}"
RUNTIME_CONNECTOR_OVERVIEW_URL="${RUNTIME_CONNECTOR_OVERVIEW_URL:-}"
RUNTIME_CONNECTOR_ACTIONS_OVERVIEW_URL="${RUNTIME_CONNECTOR_ACTIONS_OVERVIEW_URL:-}"
RUNTIME_CONNECTOR_CONFIG_URL="${RUNTIME_CONNECTOR_CONFIG_URL:-}"
RUNTIME_CONNECTOR_LOGS_URL="${RUNTIME_CONNECTOR_LOGS_URL:-}"
RUNTIME_AUTH_OVERVIEW_URL="${RUNTIME_AUTH_OVERVIEW_URL:-}"

PLATFORM_BASE_URL="${PLATFORM_BASE_URL:-${PLATFORM_PUBLIC_BASE_URL:-}}"
PLATFORM_DEPLOYMENT_ID="${PLATFORM_DEPLOYMENT_ID:-}"
PLATFORM_API_KEY_HEADER="${PLATFORM_API_KEY_HEADER:-X-PLATFORM-API-KEY}"
PLATFORM_API_KEY="${PLATFORM_API_KEY:-}"
PLATFORM_COOKIE="${PLATFORM_COOKIE:-}"
PLATFORM_LOGIN_EMAIL="${PLATFORM_LOGIN_EMAIL:-}"
PLATFORM_LOGIN_PASSWORD="${PLATFORM_LOGIN_PASSWORD:-}"
PLATFORM_HTTP_RETRY_ATTEMPTS="${PLATFORM_HTTP_RETRY_ATTEMPTS:-4}"
PLATFORM_HTTP_RETRY_SLEEP_SECONDS="${PLATFORM_HTTP_RETRY_SLEEP_SECONDS:-5}"
PLATFORM_EXPECT_RELEASE_STATUS="${PLATFORM_EXPECT_RELEASE_STATUS:-APPLIED_VERIFIED}"
PLATFORM_EXPECT_VERIFICATION_STATUS="${PLATFORM_EXPECT_VERIFICATION_STATUS:-PASSED}"
PLATFORM_EXPECT_RELEASE_ID="${PLATFORM_EXPECT_RELEASE_ID:-}"
PLATFORM_EXPECT_VERSION_ID="${PLATFORM_EXPECT_VERSION_ID:-}"

EXPECTED_VECTOR_SPACES="${EXPECTED_VECTOR_SPACES:-product}"
TEST_VECTOR_SPACE="${TEST_VECTOR_SPACE:-${EXPECTED_VECTOR_SPACES%%,*}}"
TEST_RECORD_ID="${TEST_RECORD_ID:-VECTOR-VERIFY-$(date +%s)}"
TEST_CONTENT="${TEST_CONTENT:-Created by verify-vector-deployment.sh for vector database wiring checks.}"
EXPECTED_VECTOR_DB="${EXPECTED_VECTOR_DB:-}"
EXPECT_TENANT_SCOPED_SHARED="${EXPECT_TENANT_SCOPED_SHARED:-}"
EXPECT_TENANT_SCOPED_STATUS="${EXPECT_TENANT_SCOPED_STATUS:-}"
EXPECT_TENANT_SCOPED_CUSTOMER_ID="${EXPECT_TENANT_SCOPED_CUSTOMER_ID:-}"
EXPECT_TENANT_SCOPED_TENANT_ID="${EXPECT_TENANT_SCOPED_TENANT_ID:-}"
EXPECT_TENANT_SCOPED_SCOPE_TYPE="${EXPECT_TENANT_SCOPED_SCOPE_TYPE:-}"
EXPECT_TENANT_SCOPED_ROOT_RESOURCE_VALUE="${EXPECT_TENANT_SCOPED_ROOT_RESOURCE_VALUE:-}"
EXPECT_TENANT_SCOPED_SCOPE_PREFIX="${EXPECT_TENANT_SCOPED_SCOPE_PREFIX:-}"
EXPECT_TENANT_SCOPED_TENANT_HANDLE="${EXPECT_TENANT_SCOPED_TENANT_HANDLE:-}"
EXPECT_TENANT_SCOPED_SCOPE_PATTERN="${EXPECT_TENANT_SCOPED_SCOPE_PATTERN:-}"
EXPECT_TENANT_SCOPED_REGISTRY_STATUS="${EXPECT_TENANT_SCOPED_REGISTRY_STATUS:-}"
EXPECT_TENANT_SCOPED_READINESS_STATUS="${EXPECT_TENANT_SCOPED_READINESS_STATUS:-}"
EXPECT_TENANT_SCOPED_MIGRATION_LOCKED="${EXPECT_TENANT_SCOPED_MIGRATION_LOCKED:-}"
EXPECT_VECTORIZATION_PLAN_PRESENT="${EXPECT_VECTORIZATION_PLAN_PRESENT:-}"
EXPECT_VECTORIZATION_SOURCE_CONNECTION_PRESENT="${EXPECT_VECTORIZATION_SOURCE_CONNECTION_PRESENT:-}"
EXPECT_VECTORIZATION_RUNNER_PRESENT="${EXPECT_VECTORIZATION_RUNNER_PRESENT:-}"
EXPECT_VECTORIZATION_PLAN_STATUS="${EXPECT_VECTORIZATION_PLAN_STATUS:-}"
EXPECT_VECTORIZATION_RUNNER_MODE="${EXPECT_VECTORIZATION_RUNNER_MODE:-}"
EXPECT_VECTORIZATION_SYNC_STATE="${EXPECT_VECTORIZATION_SYNC_STATE:-}"
EXPECT_VECTORIZATION_SOURCE_ADAPTER="${EXPECT_VECTORIZATION_SOURCE_ADAPTER:-}"
EXPECT_VECTORIZATION_SOURCE_AUTH_MODE="${EXPECT_VECTORIZATION_SOURCE_AUTH_MODE:-}"
EXPECT_VECTORIZATION_SOURCE_STATUS="${EXPECT_VECTORIZATION_SOURCE_STATUS:-}"
EXPECT_VECTORIZATION_RUNNER_STATUS="${EXPECT_VECTORIZATION_RUNNER_STATUS:-}"
EXPECT_VECTORIZATION_RUNNER_COMPATIBILITY_STATUS="${EXPECT_VECTORIZATION_RUNNER_COMPATIBILITY_STATUS:-}"
EXPECT_VECTORIZATION_AVAILABLE_ENTITIES="${EXPECT_VECTORIZATION_AVAILABLE_ENTITIES:-}"
EXPECT_VECTORIZATION_ENTITY_SCOPE="${EXPECT_VECTORIZATION_ENTITY_SCOPE:-}"
EXPECT_VECTORIZATION_RUNNER_REQUIRED="${EXPECT_VECTORIZATION_RUNNER_REQUIRED:-}"
EXPECT_VECTORIZATION_PLATFORM_MANAGED_RUNNER="${EXPECT_VECTORIZATION_PLATFORM_MANAGED_RUNNER:-}"
VERIFY_VECTORIZATION_ADMIN="${VERIFY_VECTORIZATION_ADMIN:-false}"
VERIFY_VECTORIZATION_CONTROL_PLANE="${VERIFY_VECTORIZATION_CONTROL_PLANE:-}"
VERIFY_PLATFORM_USER_DIRECTORY_ADMIN="${VERIFY_PLATFORM_USER_DIRECTORY_ADMIN:-false}"
VERIFY_VECTORIZATION_RUNNER_ACTIVE="${VERIFY_VECTORIZATION_RUNNER_ACTIVE:-false}"
VERIFY_VECTORIZATION_SAMPLE="${VERIFY_VECTORIZATION_SAMPLE:-false}"
VERIFY_TENANT_SHARED_ISOLATION="${VERIFY_TENANT_SHARED_ISOLATION:-false}"
VECTORIZATION_COUNTERPART_DEPLOYMENT_ID="${VECTORIZATION_COUNTERPART_DEPLOYMENT_ID:-}"
VERIFY_WRITE="${VERIFY_WRITE:-false}"

if [[ -z "${VERIFY_VECTORIZATION_CONTROL_PLANE}" ]]; then
  if [[ "${EXPECT_VECTORIZATION_PLAN_PRESENT}" == "true" || "${EXPECT_VECTORIZATION_SOURCE_CONNECTION_PRESENT}" == "true" || "${EXPECT_VECTORIZATION_RUNNER_PRESENT}" == "true" || "${VERIFY_VECTORIZATION_RUNNER_ACTIVE}" == "true" || "${VERIFY_VECTORIZATION_SAMPLE}" == "true" ]]; then
    VERIFY_VECTORIZATION_CONTROL_PLANE="true"
  else
    VERIFY_VECTORIZATION_CONTROL_PLANE="false"
  fi
fi

resolve_secret_value() {
  local var_name="$1"
  local file_var_name="${var_name}_FILE"
  local direct_value="${!var_name:-}"
  local file_path="${!file_var_name:-}"

  if [[ -n "${file_path}" ]]; then
    if [[ ! -f "${file_path}" ]]; then
      echo "Missing secret file for ${var_name}: ${file_path}"
      exit 2
    fi
    python3 - <<'PY' "${file_path}"
import pathlib, sys
print(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
PY
    return
  fi

  printf '%s' "${direct_value}"
}

RUNTIME_TRUSTED_BACKEND_API_KEY="$(resolve_secret_value RUNTIME_TRUSTED_BACKEND_API_KEY)"
RUNTIME_PRIVATE_AUTHORIZATION="$(resolve_secret_value RUNTIME_PRIVATE_AUTHORIZATION)"
PLATFORM_API_KEY="$(resolve_secret_value PLATFORM_API_KEY)"
PLATFORM_COOKIE="$(resolve_secret_value PLATFORM_COOKIE)"
PLATFORM_LOGIN_EMAIL="$(resolve_secret_value PLATFORM_LOGIN_EMAIL)"
PLATFORM_LOGIN_PASSWORD="$(resolve_secret_value PLATFORM_LOGIN_PASSWORD)"

RUN_PLATFORM_CHECKS="false"
if [[ -n "${PLATFORM_BASE_URL}" || -n "${PLATFORM_DEPLOYMENT_ID}" ]]; then
  if [[ -z "${PLATFORM_BASE_URL}" || -z "${PLATFORM_DEPLOYMENT_ID}" ]]; then
    echo "Invalid platform verification configuration."
    echo "Set both PLATFORM_BASE_URL and PLATFORM_DEPLOYMENT_ID together."
    exit 2
  fi
  RUN_PLATFORM_CHECKS="true"
fi

USE_RUNTIME_OPERATIONAL_SURFACE="false"
if [[ -n "${RUNTIME_TRUSTED_BACKEND_API_KEY}" ]]; then
  USE_RUNTIME_OPERATIONAL_SURFACE="true"
fi

if [[ -z "${RUNTIME_BASE_URL}" ]]; then
  echo "Missing required env vars."
  echo "Set RUNTIME_BASE_URL."
  exit 2
fi

if [[ "${USE_RUNTIME_OPERATIONAL_SURFACE}" != "true" ]]; then
  echo "Missing required env vars."
  echo "Configure RUNTIME_TRUSTED_BACKEND_API_KEY for runtime-backed operational verification."
  exit 2
fi

require_cmd() {
  local cmd="$1"
  if ! command -v "${cmd}" >/dev/null 2>&1; then
    echo "Missing required command: ${cmd}"
    exit 2
  fi
}

require_cmd curl
require_cmd jq
require_cmd python3

trim_slash() {
  local url="$1"
  if [[ "${url}" == */ ]]; then
    echo "${url%/}"
  else
    echo "${url}"
  fi
}

RUNTIME_BASE_URL="$(trim_slash "${RUNTIME_BASE_URL}")"
if [[ -z "${RUNTIME_CONNECTOR_HEALTH_URL}" && -n "${RUNTIME_BASE_URL}" ]]; then
  RUNTIME_CONNECTOR_HEALTH_URL="${RUNTIME_BASE_URL}/api/admin/connector/health"
fi
if [[ -z "${RUNTIME_CONNECTOR_OVERVIEW_URL}" && -n "${RUNTIME_BASE_URL}" ]]; then
  RUNTIME_CONNECTOR_OVERVIEW_URL="${RUNTIME_BASE_URL}/api/admin/connector/overview"
fi
if [[ -z "${RUNTIME_CONNECTOR_ACTIONS_OVERVIEW_URL}" && -n "${RUNTIME_BASE_URL}" ]]; then
  RUNTIME_CONNECTOR_ACTIONS_OVERVIEW_URL="${RUNTIME_BASE_URL}/api/admin/connector/actions/overview"
fi
if [[ -z "${RUNTIME_CONNECTOR_CONFIG_URL}" && -n "${RUNTIME_BASE_URL}" ]]; then
  RUNTIME_CONNECTOR_CONFIG_URL="${RUNTIME_BASE_URL}/api/admin/connector/config"
fi
if [[ -z "${RUNTIME_CONNECTOR_LOGS_URL}" && -n "${RUNTIME_BASE_URL}" ]]; then
  RUNTIME_CONNECTOR_LOGS_URL="${RUNTIME_BASE_URL}/api/admin/connector/logs"
fi
if [[ -z "${RUNTIME_AUTH_OVERVIEW_URL}" && -n "${RUNTIME_BASE_URL}" ]]; then
  RUNTIME_AUTH_OVERVIEW_URL="${RUNTIME_BASE_URL}/api/admin/auth/overview"
fi
if [[ -n "${PLATFORM_BASE_URL}" ]]; then
  PLATFORM_BASE_URL="$(trim_slash "${PLATFORM_BASE_URL}")"
fi

TMP_DIR="$(mktemp -d)"
PLATFORM_COOKIE_JAR="${TMP_DIR}/platform-cookie.txt"
cleanup() {
  rm -rf "${TMP_DIR}"
}
trap cleanup EXIT

HTTP_STATUS=""
HTTP_BODY=""

runtime_operational_http() {
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
  if [[ -n "${RUNTIME_TRUSTED_BACKEND_API_KEY}" ]]; then
    headers+=("-H" "${RUNTIME_TRUSTED_BACKEND_API_KEY_HEADER}: ${RUNTIME_TRUSTED_BACKEND_API_KEY}")
  fi
  if [[ -n "${RUNTIME_PRIVATE_AUTHORIZATION}" ]]; then
    headers+=("-H" "${RUNTIME_PRIVATE_AUTHORIZATION_HEADER}: ${RUNTIME_PRIVATE_AUTHORIZATION}")
  fi

  local status
  if [[ -n "${body}" ]]; then
    status="$(curl -sS -o "${tmp}" -w "%{http_code}" -X "${method}" "${headers[@]}" "$@" --data "${body}" "${url}" || true)"
  else
    status="$(curl -sS -o "${tmp}" -w "%{http_code}" -X "${method}" "${headers[@]}" "$@" "${url}" || true)"
  fi

  HTTP_STATUS="${status}"
  HTTP_BODY="$(cat "${tmp}")"
  rm -f "${tmp}"
}

runtime_http() {
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
  if [[ -n "${RUNTIME_TRUSTED_BACKEND_API_KEY}" ]]; then
    headers+=("-H" "${RUNTIME_TRUSTED_BACKEND_API_KEY_HEADER}: ${RUNTIME_TRUSTED_BACKEND_API_KEY}")
  fi
  if [[ -n "${RUNTIME_PRIVATE_AUTHORIZATION}" ]]; then
    headers+=("-H" "${RUNTIME_PRIVATE_AUTHORIZATION_HEADER}: ${RUNTIME_PRIVATE_AUTHORIZATION}")
  fi

  local status
  if [[ -n "${body}" ]]; then
    status="$(curl -sS -o "${tmp}" -w "%{http_code}" -X "${method}" "${headers[@]}" "$@" --data "${body}" "${url}" || true)"
  else
    status="$(curl -sS -o "${tmp}" -w "%{http_code}" -X "${method}" "${headers[@]}" "$@" "${url}" || true)"
  fi

  HTTP_STATUS="${status}"
  HTTP_BODY="$(cat "${tmp}")"
  rm -f "${tmp}"
}

operational_http() {
  local method="$1"
  local path="$2"
  local body="${3:-}"
  local target="${RUNTIME_BASE_URL}${path}"
  runtime_operational_http "${method}" "${target}" "${body}"
}

operational_surface_name() {
  printf '%s' "runtime"
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
    if [[ -s "${PLATFORM_COOKIE_JAR}" ]]; then
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
  if [[ "${RUN_PLATFORM_CHECKS}" != "true" ]]; then
    return 0
  fi
  if [[ -n "${PLATFORM_API_KEY}" || -n "${PLATFORM_COOKIE}" ]]; then
    return 0
  fi
  if [[ -z "${PLATFORM_LOGIN_EMAIL}" || -z "${PLATFORM_LOGIN_PASSWORD}" ]]; then
    echo "Platform verification requires PLATFORM_API_KEY, PLATFORM_COOKIE, or PLATFORM_LOGIN_EMAIL/PLATFORM_LOGIN_PASSWORD."
    exit 2
  fi

  local tmp
  tmp="$(mktemp)"
  local payload
  payload="$(mktemp)"
  local status
  cat > "${payload}" <<EOF
{"email":"${PLATFORM_LOGIN_EMAIL}","password":"${PLATFORM_LOGIN_PASSWORD}"}
EOF
  status="$(
    curl -sS -o "${tmp}" -w "%{http_code}" -c "${PLATFORM_COOKIE_JAR}" \
      -H "Content-Type: application/json" \
      --data "@${payload}" \
      "${PLATFORM_BASE_URL}/api/platform/auth/login" || true
  )"
  rm -f "${payload}"
  if [[ "${status}" != "200" ]]; then
    echo "Platform login failed (HTTP ${status})."
    cat "${tmp}"
    rm -f "${tmp}"
    exit 1
  fi
  rm -f "${tmp}"
}

pass() { echo "PASS: $*"; }
fail() { echo "FAIL: $*"; exit 1; }

assert_status() {
  local expected="$1"
  local label="$2"
  if [[ "${HTTP_STATUS}" != "${expected}" ]]; then
    echo "---- ${label} ----"
    echo "HTTP ${HTTP_STATUS}"
    echo "${HTTP_BODY}"
    echo "------------------"
    fail "${label} (expected HTTP ${expected})"
  fi
}

json_assert() {
  local label="$1"
  local py="$2"
  ASSERT_LABEL="${label}" ASSERT_BODY="${HTTP_BODY}" ASSERT_PY="${py}" python3 - <<'PY'
import json
import os

label = os.environ["ASSERT_LABEL"]
raw = os.environ.get("ASSERT_BODY", "").strip()
try:
    data = json.loads(raw) if raw else None
except Exception as exc:
    print(f"FAIL: {label}: invalid JSON: {exc}")
    print(f"{label}: invalid JSON: {exc}")
    print(raw)
    raise SystemExit(2)

namespace = {"data": data}
try:
    exec(os.environ["ASSERT_PY"].replace("\\n", "\n"), namespace, namespace)
except Exception as exc:
    detail = str(exc).strip() or exc.__class__.__name__
    print(f"FAIL: {label}: {detail}")
    if raw:
        print(raw)
    raise
PY
}

body_assert_contains() {
  local label="$1"
  local needle="$2"
  if [[ "${HTTP_BODY}" != *"${needle}"* ]]; then
    echo "---- ${label} ----"
    echo "${HTTP_BODY}"
    echo "------------------"
    fail "${label} (missing '${needle}')"
  fi
}

poll_until() {
  local label="$1"
  local attempts="$2"
  local sleep_s="$3"
  local cmd="$4"
  local condition_py="$5"

  local i=1
  while [[ "${i}" -le "${attempts}" ]]; do
    eval "${cmd}"
    if [[ "${HTTP_STATUS}" == "200" ]]; then
      if POLL_LABEL="${label}" POLL_BODY="${HTTP_BODY}" POLL_PY="${condition_py}" python3 - <<'PY'
import json
import os

label = os.environ["POLL_LABEL"]
raw = os.environ.get("POLL_BODY", "").strip()
data = json.loads(raw) if raw else {}
namespace = {"data": data}
exec(os.environ["POLL_PY"].replace("\\n", "\n"), namespace, namespace)
PY
      then
        return 0
      fi
    fi
    sleep "${sleep_s}"
    i=$((i+1))
  done

  echo "---- ${label} ----"
  echo "Last HTTP ${HTTP_STATUS}"
  echo "${HTTP_BODY}"
  echo "------------------"
  fail "${label} (timed out after ${attempts} attempts)"
}

vectorization_scope_json() {
  SCOPE_TEXT="${EXPECT_VECTORIZATION_ENTITY_SCOPE:-${EXPECT_VECTORIZATION_AVAILABLE_ENTITIES:-}}" python3 - <<'PY'
import json
import os

raw = os.environ.get("SCOPE_TEXT", "")
items = []
for part in raw.split(","):
    value = part.strip()
    if value and value not in items:
        items.append(value)
print(json.dumps(items))
PY
}

create_platform_vectorization_verification() {
  local verification_type="$1"
  local note="$2"
  local counterpart="${3:-}"
  local scope_json
  scope_json="$(vectorization_scope_json)"
  local payload
  payload="$(VERIFY_TYPE="${verification_type}" VERIFY_NOTE="${note}" VERIFY_COUNTERPART="${counterpart}" VERIFY_SCOPE_JSON="${scope_json}" python3 - <<'PY'
import json
import os

payload = {
    "verificationType": os.environ["VERIFY_TYPE"],
    "note": os.environ["VERIFY_NOTE"],
}
scope = json.loads(os.environ.get("VERIFY_SCOPE_JSON") or "[]")
if scope:
    payload["entityTypes"] = scope
counterpart = (os.environ.get("VERIFY_COUNTERPART") or "").strip()
if counterpart:
    payload["counterpartDeploymentId"] = counterpart
print(json.dumps(payload))
PY
)"
  platform_http POST "${PLATFORM_BASE_URL}/api/deployments/${PLATFORM_DEPLOYMENT_ID}/vectorization/verifications" "${payload}"
  assert_status 201 "platform vectorization verification create ${verification_type}"
  json_assert "platform vectorization verification create ${verification_type}" $'assert (data or {}).get("verificationType") == "'"${verification_type}"'"\nassert bool((data or {}).get("id"))\nprint("ok")' >/dev/null
  PARSE_BODY="${HTTP_BODY}" python3 - <<'PY'
import json
import os
d = json.loads(os.environ.get("PARSE_BODY", "") or "{}")
print((d.get("id")) or "")
PY
}

wait_for_platform_vectorization_verification() {
  local verification_run_id="$1"
  local label="$2"
  local attempts="${3:-80}"
  local sleep_s="${4:-3}"

  local i=1
  while [[ "${i}" -le "${attempts}" ]]; do
    platform_http GET "${PLATFORM_BASE_URL}/api/deployments/${PLATFORM_DEPLOYMENT_ID}/vectorization/verifications/${verification_run_id}"
    assert_status 200 "${label} status"
    local status
    status="$(PARSE_BODY="${HTTP_BODY}" python3 - <<'PY'
import json
import os
d = json.loads(os.environ.get("PARSE_BODY", "") or "{}")
run = (d.get("verificationRun") or {})
print((run.get("status")) or "")
PY
)"
    if [[ "${status}" == "PASSED" ]]; then
      return 0
    fi
    if [[ "${status}" == "FAILED" || "${status}" == "CANCELLED" ]]; then
      echo "---- ${label} ----"
      echo "${HTTP_BODY}"
      echo "------------------"
      fail "${label} (${status})"
    fi
    sleep "${sleep_s}"
    i=$((i+1))
  done

  echo "---- ${label} ----"
  echo "${HTTP_BODY}"
  echo "------------------"
  fail "${label} (timed out waiting for PASS)"
}

run_platform_vectorization_verification() {
  local verification_type="$1"
  local label="$2"
  local counterpart="${3:-}"
  local run_id
  run_id="$(create_platform_vectorization_verification "${verification_type}" "Triggered by verify-vector-deployment.sh." "${counterpart}")"
  wait_for_platform_vectorization_verification "${run_id}" "${label}"
  pass "${label}"
}

echo "Runtime: ${RUNTIME_BASE_URL}"
echo "Operational data-sync surface: $(operational_surface_name)"
echo "Expected vector spaces: ${EXPECTED_VECTOR_SPACES}"
echo "Test vector space: ${TEST_VECTOR_SPACE}"
if [[ -n "${EXPECTED_VECTOR_DB}" ]]; then
  echo "Expected vector DB: ${EXPECTED_VECTOR_DB}"
fi
if [[ -n "${EXPECT_TENANT_SCOPED_SHARED}" ]]; then
  echo "Expected tenant-scoped shared storage: ${EXPECT_TENANT_SCOPED_SHARED}"
fi
echo "Verify write: ${VERIFY_WRITE}"
if [[ "${RUN_PLATFORM_CHECKS}" == "true" ]]; then
  echo "Platform: ${PLATFORM_BASE_URL}"
  echo "Platform deployment: ${PLATFORM_DEPLOYMENT_ID}"
fi

platform_login

echo ""
echo "== Health =="
runtime_http GET "${RUNTIME_CONNECTOR_HEALTH_URL}"
assert_status 200 "runtime connector health proxy"
json_assert "runtime connector health proxy" $'assert (data or {}).get("status") == "UP"\nprint("ok")'
pass "runtime GET /api/admin/connector/health"

runtime_http GET "${RUNTIME_BASE_URL}/actuator/health"
assert_status 200 "runtime health"
json_assert "runtime health" $'assert (data or {}).get("status") == "UP"\nprint("ok")'
pass "runtime /actuator/health"

echo ""
echo "== Connector and Runtime Admin =="
runtime_http GET "${RUNTIME_CONNECTOR_OVERVIEW_URL}"
assert_status 200 "runtime connector admin overview"
json_assert "runtime connector admin overview" $'assert (data or {}).get("success") is True\nprint("ok")'
pass "runtime GET /api/admin/connector/overview"

runtime_http GET "${RUNTIME_CONNECTOR_CONFIG_URL}"
assert_status 200 "runtime connector config"
json_assert "runtime connector config" $'assert (data or {}).get("success") is True\nprint("ok")'
pass "runtime GET /api/admin/connector/config"

operational_http GET "/api/ai/data-sync/vector-spaces"
assert_status 200 "$(operational_surface_name) vector spaces"
json_assert "$(operational_surface_name) vector spaces" $'spaces = set((data or {}).get("vectorSpaces") or [])\nfor req in [item.strip() for item in "'"${EXPECTED_VECTOR_SPACES}"'".split(",") if item.strip()]:\n  assert req in spaces, spaces\nprint("ok")'
pass "$(operational_surface_name) GET /api/ai/data-sync/vector-spaces"

runtime_http GET "${RUNTIME_BASE_URL}/api/admin/overview"
assert_status 200 "runtime admin overview"
json_assert "runtime admin overview" $'assert (data or {}).get("success") is True\nentity_types = set((data or {}).get("supportedEntityTypes") or [])\nfor req in [item.strip() for item in "'"${EXPECTED_VECTOR_SPACES}"'".split(",") if item.strip()]:\n  assert req in entity_types, entity_types\nassert bool((data or {}).get("entityConfigLocation"))\nassert bool((data or {}).get("promptConfigLocation"))\nprint("ok")'
RUNTIME_ADMIN_OVERVIEW_BODY="${HTTP_BODY}"
pass "runtime GET /api/admin/overview"

runtime_http GET "${RUNTIME_AUTH_OVERVIEW_URL}"
assert_status 200 "runtime auth overview"
json_assert "runtime auth overview" $'assert (data or {}).get("success") is True\nauth = (data or {}).get("auth") or {}\nassert (auth.get("ingressMode") or "") == "VERIFIED_CONTEXT_REQUIRED"\nassert auth.get("verifiedContextRequired") is True\nassert "warnings" in (data or {})\nprint("ok")'
pass "runtime GET /api/admin/auth/overview"

if [[ -n "${EXPECT_TENANT_SCOPED_SHARED}" ]]; then
  HTTP_BODY="${RUNTIME_ADMIN_OVERVIEW_BODY}"
  json_assert "runtime admin tenant-scoped vector scope" $'scope = (data or {}).get("vectorScope") or {}\nexpected_shared = "'"${EXPECT_TENANT_SCOPED_SHARED}"'".lower() == "true"\nif expected_shared:\n  assert bool(scope.get("sharedStorage")) is True, scope\n  if "'"${EXPECT_TENANT_SCOPED_SCOPE_TYPE}"'":\n    assert (scope.get("scopeType") or "") == "'"${EXPECT_TENANT_SCOPED_SCOPE_TYPE}"'", scope\n  if "'"${EXPECT_TENANT_SCOPED_ROOT_RESOURCE_VALUE}"'":\n    assert (scope.get("rootResourceValue") or "") == "'"${EXPECT_TENANT_SCOPED_ROOT_RESOURCE_VALUE}"'", scope\n  if "'"${EXPECT_TENANT_SCOPED_SCOPE_PREFIX}"'":\n    assert (scope.get("scopePrefix") or "") == "'"${EXPECT_TENANT_SCOPED_SCOPE_PREFIX}"'", scope\n  if "'"${EXPECT_TENANT_SCOPED_TENANT_HANDLE}"'":\n    assert (scope.get("tenantHandle") or "") == "'"${EXPECT_TENANT_SCOPED_TENANT_HANDLE}"'", scope\n  if "'"${EXPECT_TENANT_SCOPED_SCOPE_PATTERN}"'":\n    assert (scope.get("scopePattern") or "") == "'"${EXPECT_TENANT_SCOPED_SCOPE_PATTERN}"'", scope\nelse:\n  assert not scope or bool(scope.get("sharedStorage")) is False, scope\nprint("ok")'
  pass "runtime admin tenant-scoped vector scope alignment"
fi

runtime_http GET "${RUNTIME_BASE_URL}/api/admin/indexing/overview"
assert_status 200 "runtime indexing overview"
json_assert "runtime indexing overview" $'assert (data or {}).get("success") is True\ncounts = (data or {}).get("countsByEntityType") or {}\nassert "'"${TEST_VECTOR_SPACE}"'" in counts, counts\nif "'"${EXPECTED_VECTOR_DB}"'":\n  assert (data or {}).get("vectorDb") == "'"${EXPECTED_VECTOR_DB}"'", data\nprint("ok")'
INITIAL_COUNT="$(PARSE_BODY="${HTTP_BODY}" TEST_VECTOR_SPACE="${TEST_VECTOR_SPACE}" python3 - <<'PY'
import json
import os
d = json.loads(os.environ.get("PARSE_BODY", "") or "{}")
counts = d.get("countsByEntityType") or {}
print(int(counts.get(os.environ["TEST_VECTOR_SPACE"]) or 0))
PY
)"
pass "runtime GET /api/admin/indexing/overview"

PLATFORM_RELEASE_BODY=""
PLATFORM_SOURCE_OF_TRUTH_BODY=""
PLATFORM_LIVE_ENTITY_ARTIFACT_URL=""
PLATFORM_LIVE_PROMPT_ARTIFACT_URL=""
PLATFORM_LIVE_RUNTIME_URL=""
PLATFORM_GENERATED_PROVISIONING_MODE=""

if [[ "${RUN_PLATFORM_CHECKS}" == "true" ]]; then
  echo ""
  echo "== Platform Deployment State =="
  platform_http GET "${PLATFORM_BASE_URL}/api/deployments/${PLATFORM_DEPLOYMENT_ID}/workspace"
  assert_status 200 "platform workspace"
  json_assert "platform workspace" $'assert (data or {}).get("deployment", {}).get("id") == "'"${PLATFORM_DEPLOYMENT_ID}"'"\nassert (data or {}).get("access", {}).get("canOperate") is True\nlifecycle = (data or {}).get("lifecycle") or {}\nassert lifecycle.get("hasPublishedVersion") is True\nprint("ok")'
  pass "platform GET /api/deployments/${PLATFORM_DEPLOYMENT_ID}/workspace"

  if [[ "${VERIFY_PLATFORM_USER_DIRECTORY_ADMIN}" == "true" ]]; then
    echo ""
    echo "== Platform User Directory Admin Access =="
    platform_http GET "${PLATFORM_BASE_URL}/api/platform/auth/session"
    assert_status 200 "platform auth session"
    json_assert "platform auth session" $'assert (data or {}).get("authenticated") is True\nassert (data or {}).get("canManageUsers") is True\nprint("ok")'
    pass "platform GET /api/platform/auth/session"

    platform_http GET "${PLATFORM_BASE_URL}/api/platform/users/access-overview?deploymentId=${PLATFORM_DEPLOYMENT_ID}"
    assert_status 200 "platform user access overview"
    json_assert "platform user access overview" $'items = data or []\nassert isinstance(items, list)\nassert len(items) >= 1\nfor item in items[:5]:\n  assert (item or {}).get("id"), item\n  assert (item or {}).get("email"), item\nselected = [(item or {}).get("selectedDeploymentAssignment") for item in items if (item or {}).get("selectedDeploymentAssignment")]\nfor assignment in selected:\n  assert (assignment or {}).get("deploymentId") == "'"${PLATFORM_DEPLOYMENT_ID}"'", assignment\nprint("ok")'
    pass "platform GET /api/platform/users/access-overview"
  fi

  platform_http GET "${PLATFORM_BASE_URL}/api/deployments/${PLATFORM_DEPLOYMENT_ID}/source-of-truth"
  assert_status 200 "platform source of truth"
  json_assert "platform source of truth" $'assert (data or {}).get("deploymentId") == "'"${PLATFORM_DEPLOYMENT_ID}"'"\nlive = (data or {}).get("live") or {}\nassert "available" in live\nreadback = (data or {}).get("liveRailwayReadback") or {}\nassert "available" in readback\nassert "status" in readback\nprint("ok")'
  PLATFORM_SOURCE_OF_TRUTH_BODY="${HTTP_BODY}"
  PLATFORM_GENERATED_PROVISIONING_MODE="$(PARSE_BODY="${PLATFORM_SOURCE_OF_TRUTH_BODY}" python3 - <<'PY'
import json, os
d = json.loads(os.environ.get("PARSE_BODY", "") or "{}")
generated = d.get("generated") or {}
print((generated.get("provisioningMode")) or "")
PY
)"
  pass "platform GET /api/deployments/${PLATFORM_DEPLOYMENT_ID}/source-of-truth"

  if [[ -n "${EXPECT_TENANT_SCOPED_SHARED}" ]]; then
    HTTP_BODY="${PLATFORM_SOURCE_OF_TRUTH_BODY}"
    json_assert "platform tenant-scoped vector source of truth" $'tenant = (data or {}).get("tenantScopedVector") or {}\nassert tenant, data\nexpected_shared = "'"${EXPECT_TENANT_SCOPED_SHARED}"'".lower() == "true"\nassert bool(tenant.get("sharedStorage")) == expected_shared, tenant\nif "'"${EXPECT_TENANT_SCOPED_STATUS}"'":\n  assert (tenant.get("status") or "") == "'"${EXPECT_TENANT_SCOPED_STATUS}"'", tenant\nif "'"${EXPECT_TENANT_SCOPED_CUSTOMER_ID}"'":\n  assert (tenant.get("customerId") or "") == "'"${EXPECT_TENANT_SCOPED_CUSTOMER_ID}"'", tenant\nif "'"${EXPECT_TENANT_SCOPED_TENANT_ID}"'":\n  assert (tenant.get("tenantId") or "") == "'"${EXPECT_TENANT_SCOPED_TENANT_ID}"'", tenant\nif "'"${EXPECT_TENANT_SCOPED_SCOPE_TYPE}"'":\n  assert (tenant.get("scopeType") or "") == "'"${EXPECT_TENANT_SCOPED_SCOPE_TYPE}"'", tenant\nif "'"${EXPECT_TENANT_SCOPED_ROOT_RESOURCE_VALUE}"'":\n  assert (tenant.get("rootResourceValue") or "") == "'"${EXPECT_TENANT_SCOPED_ROOT_RESOURCE_VALUE}"'", tenant\nif "'"${EXPECT_TENANT_SCOPED_SCOPE_PREFIX}"'":\n  assert (tenant.get("scopePrefix") or "") == "'"${EXPECT_TENANT_SCOPED_SCOPE_PREFIX}"'", tenant\nif "'"${EXPECT_TENANT_SCOPED_TENANT_HANDLE}"'":\n  assert (tenant.get("tenantHandle") or "") == "'"${EXPECT_TENANT_SCOPED_TENANT_HANDLE}"'", tenant\nif "'"${EXPECT_TENANT_SCOPED_SCOPE_PATTERN}"'":\n  assert (tenant.get("scopePattern") or "") == "'"${EXPECT_TENANT_SCOPED_SCOPE_PATTERN}"'", tenant\nif "'"${EXPECT_TENANT_SCOPED_MIGRATION_LOCKED}"'":\n  assert bool(tenant.get("migrationLocked")) == ("'"${EXPECT_TENANT_SCOPED_MIGRATION_LOCKED}"'".lower() == "true"), tenant\nif "'"${EXPECT_TENANT_SCOPED_REGISTRY_STATUS}"'":\n  registry = tenant.get("registry") or {}\n  assert (registry.get("status") or "") == "'"${EXPECT_TENANT_SCOPED_REGISTRY_STATUS}"'", registry\nprint("ok")'
    pass "platform tenant-scoped vector source-of-truth alignment"
  fi

  echo ""
  echo "== Platform Vectorization =="
  platform_http GET "${PLATFORM_BASE_URL}/api/deployments/${PLATFORM_DEPLOYMENT_ID}/vectorization"
  assert_status 200 "platform vectorization overview"
  json_assert "platform vectorization overview" $'assert (data or {}).get("deploymentId") == "'"${PLATFORM_DEPLOYMENT_ID}"'"\nplan = (data or {}).get("plan")\nsource = (data or {}).get("sourceConnection")\nrunner = (data or {}).get("runner")\nassert bool(plan) == ("'"${EXPECT_VECTORIZATION_PLAN_PRESENT}"'".lower() == "true"), data\nassert bool(source) == ("'"${EXPECT_VECTORIZATION_SOURCE_CONNECTION_PRESENT}"'".lower() == "true"), data\nassert bool(runner) == ("'"${EXPECT_VECTORIZATION_RUNNER_PRESENT}"'".lower() == "true"), data\nif plan and "'"${EXPECT_VECTORIZATION_PLAN_STATUS}"'":\n  assert (plan.get("status") or "") == "'"${EXPECT_VECTORIZATION_PLAN_STATUS}"'", plan\nif plan and "'"${EXPECT_VECTORIZATION_RUNNER_MODE}"'":\n  assert (plan.get("runnerMode") or "") == "'"${EXPECT_VECTORIZATION_RUNNER_MODE}"'", plan\nif plan and "'"${EXPECT_VECTORIZATION_SYNC_STATE}"'":\n  assert (plan.get("syncState") or "") == "'"${EXPECT_VECTORIZATION_SYNC_STATE}"'", plan\nif source and "'"${EXPECT_VECTORIZATION_SOURCE_ADAPTER}"'":\n  assert (source.get("adapterType") or "") == "'"${EXPECT_VECTORIZATION_SOURCE_ADAPTER}"'", source\nif source and "'"${EXPECT_VECTORIZATION_SOURCE_AUTH_MODE}"'":\n  assert (source.get("authMode") or "") == "'"${EXPECT_VECTORIZATION_SOURCE_AUTH_MODE}"'", source\nif source and "'"${EXPECT_VECTORIZATION_SOURCE_STATUS}"'":\n  assert (source.get("status") or "") == "'"${EXPECT_VECTORIZATION_SOURCE_STATUS}"'", source\nif runner and "'"${EXPECT_VECTORIZATION_RUNNER_STATUS}"'":\n  assert (runner.get("registrationStatus") or "") == "'"${EXPECT_VECTORIZATION_RUNNER_STATUS}"'", runner\nif runner and "'"${EXPECT_VECTORIZATION_RUNNER_COMPATIBILITY_STATUS}"'":\n  assert (runner.get("compatibilityStatus") or "") == "'"${EXPECT_VECTORIZATION_RUNNER_COMPATIBILITY_STATUS}"'", runner\nprint("ok")'
  pass "platform GET /api/deployments/${PLATFORM_DEPLOYMENT_ID}/vectorization"

  platform_http GET "${PLATFORM_BASE_URL}/api/deployments/${PLATFORM_DEPLOYMENT_ID}/vectorization/preview"
  assert_status 200 "platform vectorization preview"
  json_assert "platform vectorization preview" $'assert (data or {}).get("deploymentId") == "'"${PLATFORM_DEPLOYMENT_ID}"'"\nreindex = (data or {}).get("reindexOptions") or {}\nassert reindex.get("supportsSelectedEntities") is True, reindex\nassert reindex.get("supportsFullDeployment") is True, reindex\nassert reindex.get("supportsDefer") is True, reindex\nif "'"${EXPECT_VECTORIZATION_SYNC_STATE}"'":\n  assert (data or {}).get("syncState") == "'"${EXPECT_VECTORIZATION_SYNC_STATE}"'", data\nif "'"${EXPECT_VECTORIZATION_AVAILABLE_ENTITIES}"'":\n  expected = {item for item in "'"${EXPECT_VECTORIZATION_AVAILABLE_ENTITIES}"'".split(",") if item}\n  actual = set(reindex.get("availableEntities") or [])\n  assert actual == expected, {"expected": sorted(expected), "actual": sorted(actual)}\nif "'"${EXPECT_VECTORIZATION_ENTITY_SCOPE}"'":\n  expected_scope = {item for item in "'"${EXPECT_VECTORIZATION_ENTITY_SCOPE}"'".split(",") if item}\n  actual_scope = set((data or {}).get("entityScope") or [])\n  assert actual_scope == expected_scope, {"expected": sorted(expected_scope), "actual": sorted(actual_scope)}\nprint("ok")'
  pass "platform GET /api/deployments/${PLATFORM_DEPLOYMENT_ID}/vectorization/preview"

  if [[ "${VERIFY_VECTORIZATION_ADMIN}" == "true" ]]; then
    echo ""
    echo "== Platform Vectorization Admin Verifications =="
    if [[ "${VERIFY_VECTORIZATION_CONTROL_PLANE}" == "true" ]]; then
      run_platform_vectorization_verification "CONTROL_PLANE_READINESS" "platform vectorization control-plane readiness"
    else
      echo "Control-plane readiness verification is disabled for this deployment."
    fi
    if [[ "${VERIFY_VECTORIZATION_RUNNER_ACTIVE}" == "true" ]]; then
      run_platform_vectorization_verification "RUNNER_PROVISIONING_SMOKE" "platform vectorization runner provisioning smoke"
      run_platform_vectorization_verification "SOURCE_DISCOVERY_SMOKE" "platform vectorization discovery smoke"
      run_platform_vectorization_verification "RUNNER_COMPATIBILITY_SMOKE" "platform vectorization runner compatibility smoke"
    else
      echo "Runner-active vectorization verifications are disabled for this deployment."
    fi
    if [[ "${VERIFY_VECTORIZATION_SAMPLE}" == "true" ]]; then
      run_platform_vectorization_verification "SAMPLE_VECTORIZATION_SMOKE" "platform vectorization sample smoke"
      run_platform_vectorization_verification "SYNC_STATE_AND_REINDEX_SMOKE" "platform vectorization sync-state smoke"
    fi
    if [[ "${VERIFY_TENANT_SHARED_ISOLATION}" == "true" ]]; then
      run_platform_vectorization_verification "TENANT_SHARED_ISOLATION_SMOKE" "platform tenant shared isolation smoke" "${VECTORIZATION_COUNTERPART_DEPLOYMENT_ID}"
    fi
  fi

  platform_http GET "${PLATFORM_BASE_URL}/api/deployments/${PLATFORM_DEPLOYMENT_ID}/releases"
  assert_status 200 "platform releases"
  json_assert "platform releases" $'items = data or []\nassert len(items) > 0\nwant = "'"${PLATFORM_EXPECT_RELEASE_ID}"'"\nrelease = next((item for item in items if not want or (item or {}).get("id") == want), None)\nassert release is not None, items\nassert release.get("status") == "'"${PLATFORM_EXPECT_RELEASE_STATUS}"'", release\nassert release.get("verificationStatus") == "'"${PLATFORM_EXPECT_VERIFICATION_STATUS}"'", release\nif "'"${PLATFORM_EXPECT_VERSION_ID}"'":\n  assert release.get("deploymentVersionId") == "'"${PLATFORM_EXPECT_VERSION_ID}"'", release\nprint("ok")'
  PLATFORM_RELEASE_BODY="${HTTP_BODY}"
  PLATFORM_LIVE_ENTITY_ARTIFACT_URL="$(PARSE_BODY="${PLATFORM_RELEASE_BODY}" EXPECT_RELEASE_ID="${PLATFORM_EXPECT_RELEASE_ID}" python3 - <<'PY'
import json
import os
items = json.loads(os.environ.get("PARSE_BODY", "") or "[]")
want = os.environ.get("EXPECT_RELEASE_ID") or ""
release = next((item for item in items if not want or item.get("id") == want), items[0] if items else {})
print((((release.get("provisioningDetails") or {}).get("artifactUrls") or {}).get("entities")) or "")
PY
)"
  PLATFORM_LIVE_PROMPT_ARTIFACT_URL="$(PARSE_BODY="${PLATFORM_RELEASE_BODY}" EXPECT_RELEASE_ID="${PLATFORM_EXPECT_RELEASE_ID}" python3 - <<'PY'
import json
import os
items = json.loads(os.environ.get("PARSE_BODY", "") or "[]")
want = os.environ.get("EXPECT_RELEASE_ID") or ""
release = next((item for item in items if not want or item.get("id") == want), items[0] if items else {})
print((((release.get("provisioningDetails") or {}).get("artifactUrls") or {}).get("prompts")) or "")
PY
)"
  PLATFORM_LIVE_RUNTIME_URL="$(PARSE_BODY="${PLATFORM_RELEASE_BODY}" EXPECT_RELEASE_ID="${PLATFORM_EXPECT_RELEASE_ID}" python3 - <<'PY'
import json
import os
items = json.loads(os.environ.get("PARSE_BODY", "") or "[]")
want = os.environ.get("EXPECT_RELEASE_ID") or ""
release = next((item for item in items if not want or item.get("id") == want), items[0] if items else {})
services = (((release.get("provisioningDetails") or {}).get("railway") or {}).get("services") or {})
print(((services.get("runtime") or {}).get("baseUrl")) or "")
PY
)"
  pass "platform GET /api/deployments/${PLATFORM_DEPLOYMENT_ID}/releases"

  if [[ -n "${PLATFORM_LIVE_RUNTIME_URL}" ]]; then
    [[ "${PLATFORM_LIVE_RUNTIME_URL}" == "${RUNTIME_BASE_URL}" ]] || fail "platform runtime base URL does not match supplied RUNTIME_BASE_URL"
    pass "platform runtime base URL matches runtime input"
  fi
  platform_http GET "${PLATFORM_BASE_URL}/api/deployments/${PLATFORM_DEPLOYMENT_ID}/verification-runs"
  assert_status 200 "platform verification runs"
  json_assert "platform verification runs" $'items = data or []\nassert len(items) > 0\nwant_release = "'"${PLATFORM_EXPECT_RELEASE_ID}"'"\nwant_version = "'"${PLATFORM_EXPECT_VERSION_ID}"'"\nrun = next((item for item in items if (not want_release or (item or {}).get("releaseId") == want_release) and (not want_version or (item or {}).get("deploymentVersionId") == want_version)), None)\nassert run is not None, items\nassert run.get("status") == "'"${PLATFORM_EXPECT_VERIFICATION_STATUS}"'", run\nprint("ok")'
  PLATFORM_LATEST_VERIFICATION_RUN_ID="$(PARSE_BODY="${HTTP_BODY}" LATEST_RELEASE_ID="${PLATFORM_EXPECT_RELEASE_ID}" EXPECT_VERSION_ID="${PLATFORM_EXPECT_VERSION_ID}" python3 - <<'PY'
import json, os
items = json.loads(os.environ.get("PARSE_BODY", "") or "[]")
target = ""
for item in items:
    if item.get("releaseId") == os.environ.get("LATEST_RELEASE_ID") and (not os.environ.get("EXPECT_VERSION_ID") or item.get("deploymentVersionId") == os.environ.get("EXPECT_VERSION_ID")):
        target = item.get("id") or ""
        break
if not target and items:
    target = items[0].get("id") or ""
print(target)
PY
)"
  json_assert "platform verification run checks" $'items = data or []\nrun_id = "'"${PLATFORM_LATEST_VERIFICATION_RUN_ID}"'"\nassert run_id\nrun = next((item for item in items if (item or {}).get("id") == run_id), None)\nassert run is not None\nchecks = {((check or {}).get("name") or (check or {}).get("key")): (check or {}).get("status") for check in ((run or {}).get("checks") or [])}\nrequired = ["runtime_config_matches_expected","connector_config_matches_expected","runtime_actions_match_expected","connector_actions_match_expected"]\nfor req in required:\n  assert req in checks, checks\nexpected = "SKIPPED" if "'"${PLATFORM_GENERATED_PROVISIONING_MODE:-}"'" == "RAILWAY_STUB" else "PASSED"\nfor req in required:\n  assert checks.get(req) == expected, checks\nif "'"${EXPECT_VECTORIZATION_PLAN_PRESENT}"'".lower() == "true":\n  assert checks.get("vectorization_control_plane_ready") == expected, checks\nelse:\n  if "vectorization_control_plane_ready" in checks:\n    assert checks.get("vectorization_control_plane_ready") == "SKIPPED", checks\nif "'"${EXPECT_VECTORIZATION_RUNNER_REQUIRED}"'".lower() == "true":\n  assert checks.get("vectorization_runner_registration_ready") == expected, checks\nelse:\n  if "vectorization_runner_registration_ready" in checks:\n    assert checks.get("vectorization_runner_registration_ready") == "SKIPPED", checks\nif "'"${EXPECT_VECTORIZATION_PLATFORM_MANAGED_RUNNER}"'".lower() == "true":\n  assert checks.get("vectorization_runner_service_provisioned") == expected, checks\nelse:\n  if "vectorization_runner_service_provisioned" in checks:\n    assert checks.get("vectorization_runner_service_provisioned") == "SKIPPED", checks\nprint("ok")'
  pass "platform GET /api/deployments/${PLATFORM_DEPLOYMENT_ID}/verification-runs"

  platform_http GET "${PLATFORM_BASE_URL}/api/deployments/${PLATFORM_DEPLOYMENT_ID}/production-readiness"
  assert_status 200 "platform production readiness"
  json_assert "platform production readiness" $'assert (data or {}).get("deploymentId") == "'"${PLATFORM_DEPLOYMENT_ID}"'"\nareas = {item.get("key"): item for item in ((data or {}).get("areas") or [])}\nassert "tenantScopedVector" in areas, areas\nassert "vectorization" in areas, areas\nif "'"${EXPECT_TENANT_SCOPED_READINESS_STATUS}"'":\n  assert (areas["tenantScopedVector"].get("status") or "") == "'"${EXPECT_TENANT_SCOPED_READINESS_STATUS}"'", areas["tenantScopedVector"]\nprint("ok")'
  pass "platform GET /api/deployments/${PLATFORM_DEPLOYMENT_ID}/production-readiness"

  if [[ "${EXPECT_TENANT_SCOPED_SHARED}" == "true" && -n "${EXPECT_TENANT_SCOPED_CUSTOMER_ID}" && -n "${EXPECT_TENANT_SCOPED_TENANT_ID}" ]]; then
    platform_http GET "${PLATFORM_BASE_URL}/api/platform/customers"
    assert_status 200 "platform customers"
    json_assert "platform customers tenant shared summary" $'customers = data or []\ncustomer = next((item for item in customers if (item or {}).get("id") == "'"${EXPECT_TENANT_SCOPED_CUSTOMER_ID}"'"), None)\nassert customer is not None, customers\ntenant = next((item for item in (customer.get("tenants") or []) if (item or {}).get("id") == "'"${EXPECT_TENANT_SCOPED_TENANT_ID}"'"), None)\nassert tenant is not None, customer\nshared = tenant.get("sharedVector") or {}\nassert int(shared.get("activeHandleCount") or 0) >= 1, shared\nif "'"${EXPECT_TENANT_SCOPED_SCOPE_PATTERN}"'":\n  assert (shared.get("latestScopePattern") or "") == "'"${EXPECT_TENANT_SCOPED_SCOPE_PATTERN}"'", shared\nprint("ok")'
    pass "platform customer tenant shared-vector summary"
  fi

  if [[ -n "${PLATFORM_LIVE_PROMPT_ARTIFACT_URL}" ]]; then
    platform_http GET "${PLATFORM_LIVE_PROMPT_ARTIFACT_URL}"
    assert_status 200 "live prompt artifact fetch"
    json_assert "live prompt artifact fetch" $'assert isinstance(data, dict)\nprint("ok")'
    pass "platform prompt artifact URL fetch"
  fi

  if [[ -n "${PLATFORM_LIVE_ENTITY_ARTIFACT_URL}" ]]; then
    platform_http GET "${PLATFORM_LIVE_ENTITY_ARTIFACT_URL}"
    assert_status 200 "live entity artifact fetch"
    body_assert_contains "live entity artifact fetch" "ai-entities:"
    pass "platform entity artifact URL fetch"
  fi

  HTTP_BODY="${RUNTIME_ADMIN_OVERVIEW_BODY}"
  if [[ -n "${PLATFORM_LIVE_ENTITY_ARTIFACT_URL}" ]]; then
    json_assert "runtime admin entity artifact alignment" $'assert (data or {}).get("entityConfigLocation") == "'"${PLATFORM_LIVE_ENTITY_ARTIFACT_URL}"'"\nprint("ok")'
    pass "runtime entity config location matches platform live entity artifact"
  fi
  if [[ -n "${PLATFORM_LIVE_PROMPT_ARTIFACT_URL}" ]]; then
    json_assert "runtime admin prompt artifact alignment" $'assert (data or {}).get("promptConfigLocation") == "'"${PLATFORM_LIVE_PROMPT_ARTIFACT_URL}"'"\nprint("ok")'
    pass "runtime prompt config location matches platform live prompt artifact"
  fi
fi

echo ""
echo "== Vector Write Roundtrip =="
if [[ "${VERIFY_WRITE}" == "true" ]]; then
VECTOR_UPSERT_BODY="$(cat <<JSON
{
  "vectorSpace": "${TEST_VECTOR_SPACE}",
  "id": "${TEST_RECORD_ID}",
  "content": "${TEST_CONTENT}",
  "metadata": {
    "source": "verify-vector-deployment.sh",
    "kind": "verification"
  },
  "trace": {
    "requestId": "verify-upsert-${TEST_RECORD_ID}",
    "authContext": {
      "subjectId": "vector-verification-smoke",
      "subjectType": "SYSTEM_PROCESS",
      "authMode": "PRIVATE_RUNTIME_BACKEND_MEDIATED",
      "callerType": "SYSTEM_PROCESS",
      "sessionId": "vector-verify",
      "issuer": "verify-vector-deployment.sh",
      "grantedScopes": [
        "data-sync:upsert",
        "data-sync:delete",
        "vectorization:verification"
      ]
    },
    "metadata": {
      "origin": "codex"
    }
  }
}
JSON
)"

vector_upsert_ok="false"
for attempt in $(seq 1 10); do
  operational_http POST "/api/ai/data-sync/upsert" "${VECTOR_UPSERT_BODY}"
  if [[ "${HTTP_STATUS}" == "200" ]]; then
    if ASSERT_LABEL="vector upsert" ASSERT_BODY="${HTTP_BODY}" ASSERT_PY=$'assert (data or {}).get("success") is True\nassert (data or {}).get("vectorSpace") == "'"${TEST_VECTOR_SPACE}"'"\nassert (data or {}).get("id") == "'"${TEST_RECORD_ID}"'"\nprint("ok")' python3 - <<'PY'
import json
import os

raw = os.environ.get("ASSERT_BODY", "").strip()
data = json.loads(raw) if raw else None
namespace = {"data": data}
exec(os.environ["ASSERT_PY"].replace("\\n", "\n"), namespace, namespace)
PY
    then
      vector_upsert_ok="true"
      break
    fi
  fi
  sleep 2
done

if [[ "${vector_upsert_ok}" != "true" ]]; then
  echo "---- vector upsert ----"
  echo "HTTP ${HTTP_STATUS}"
  echo "${HTTP_BODY}"
  echo "------------------"
  fail "vector upsert (timed out waiting for a successful write)"
fi
pass "$(operational_surface_name) POST /api/ai/data-sync/upsert"

poll_until "vector indexed" 20 2 \
  "runtime_http GET \"${RUNTIME_BASE_URL}/api/admin/indexing/overview\"" \
  $'counts = (data or {}).get("countsByEntityType") or {}\ncur = int(counts.get("'"${TEST_VECTOR_SPACE}"'") or 0)\nwant = int('"${INITIAL_COUNT}"') + 1\nraise SystemExit(0 if cur >= want else 1)\n'
pass "runtime indexing count increased"

operational_http POST "/api/ai/data-sync/delete" "$(cat <<JSON
{
  "vectorSpace": "${TEST_VECTOR_SPACE}",
  "id": "${TEST_RECORD_ID}",
  "trace": {
    "requestId": "verify-delete-${TEST_RECORD_ID}",
    "authContext": {
      "subjectId": "vector-verification-smoke",
      "subjectType": "SYSTEM_PROCESS",
      "authMode": "PRIVATE_RUNTIME_BACKEND_MEDIATED",
      "callerType": "SYSTEM_PROCESS",
      "sessionId": "vector-verify",
      "issuer": "verify-vector-deployment.sh",
      "grantedScopes": [
        "data-sync:upsert",
        "data-sync:delete",
        "vectorization:verification"
      ]
    },
    "metadata": {
      "origin": "codex"
    }
  }
}
JSON
)"
assert_status 200 "vector delete"
json_assert "vector delete" $'assert (data or {}).get("success") is True\nassert (data or {}).get("vectorSpace") == "'"${TEST_VECTOR_SPACE}"'"\nassert (data or {}).get("id") == "'"${TEST_RECORD_ID}"'"\nprint("ok")'
pass "$(operational_surface_name) POST /api/ai/data-sync/delete"

poll_until "vector deleted" 20 2 \
  "runtime_http GET \"${RUNTIME_BASE_URL}/api/admin/indexing/overview\"" \
  $'counts = (data or {}).get("countsByEntityType") or {}\ncur = int(counts.get("'"${TEST_VECTOR_SPACE}"'") or 0)\nwant = int('"${INITIAL_COUNT}"')\nraise SystemExit(0 if cur == want else 1)\n'
pass "runtime indexing count returned to initial value"
else
  echo "Read-only mode enabled. Skipping vector upsert/delete checks."
fi

if [[ "${RUN_PLATFORM_CHECKS}" == "true" ]]; then
  platform_http GET "${PLATFORM_BASE_URL}/api/deployments/${PLATFORM_DEPLOYMENT_ID}/releases"
  assert_status 200 "platform releases (final)"
  json_assert "platform releases (final)" $'items = data or []\nassert len(items) > 0\nwant = "'"${PLATFORM_EXPECT_RELEASE_ID}"'"\nrelease = next((item for item in items if not want or (item or {}).get("id") == want), None)\nassert release is not None, items\nassert release.get("status") == "'"${PLATFORM_EXPECT_RELEASE_STATUS}"'", release\nassert release.get("verificationStatus") == "'"${PLATFORM_EXPECT_VERIFICATION_STATUS}"'", release\nif "'"${PLATFORM_EXPECT_VERSION_ID}"'":\n  assert release.get("deploymentVersionId") == "'"${PLATFORM_EXPECT_VERSION_ID}"'", release\nprint("ok")'
  pass "platform release remains applied and verified"
fi

echo ""
pass "All checks completed."
