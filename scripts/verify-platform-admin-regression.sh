#!/usr/bin/env bash
set -euo pipefail

# Platform-admin live regression script.
#
# Focus:
# - real platform admin auth
# - real admin-only API behavior
# - deployment assignment visibility
# - async deletion and notification flow
# - canonical rollout inventory
# - consumer-bound deployment resolution
# - inference-service admin UI and API verification
# - managed inference service restart/scale/rotate-secret/force-recreate
# - optional manual canonical rollout mutation
#
# Usage:
#   PLATFORM_BASE_URL="https://<platform>.up.railway.app" \
#   PLATFORM_UI_BASE_URL="https://<platform-ui>.up.railway.app" \
#   ADMIN_TARGET_DEPLOYMENT_ID="dep-xxxxxxxx" \
#   PLATFORM_LOGIN_EMAIL="admin@example.com" \
#   PLATFORM_LOGIN_PASSWORD="<password>" \
#   ./scripts/verify-platform-admin-regression.sh
#
# Optional destructive canonical rollout mutation:
#   VERIFY_CANONICAL_ROLLOUT_MUTATION=true \
#   CANONICAL_ROLLOUT_KEYS="ecommerce,qdrant" \
#   ./scripts/verify-platform-admin-regression.sh

PLATFORM_BASE_URL="${PLATFORM_BASE_URL:-${PLATFORM_PUBLIC_BASE_URL:-}}"
PLATFORM_UI_BASE_URL="${PLATFORM_UI_BASE_URL:-}"
ADMIN_TARGET_DEPLOYMENT_ID="${ADMIN_TARGET_DEPLOYMENT_ID:-}"

PLATFORM_API_KEY_HEADER="${PLATFORM_API_KEY_HEADER:-X-PLATFORM-API-KEY}"
PLATFORM_API_KEY="${PLATFORM_API_KEY:-}"
PLATFORM_COOKIE="${PLATFORM_COOKIE:-}"
PLATFORM_LOGIN_EMAIL="${PLATFORM_LOGIN_EMAIL:-}"
PLATFORM_LOGIN_PASSWORD="${PLATFORM_LOGIN_PASSWORD:-}"
PLATFORM_PUBLIC_API_CLIENT_ID_HEADER="${PLATFORM_PUBLIC_API_CLIENT_ID_HEADER:-X-PLATFORM-CLIENT-ID}"
PLATFORM_PUBLIC_API_KEY_HEADER="${PLATFORM_PUBLIC_API_KEY_HEADER:-X-PLATFORM-PUBLIC-API-KEY}"
PLATFORM_PUBLIC_API_CLIENT_ID="${PLATFORM_PUBLIC_API_CLIENT_ID:-}"
PLATFORM_PUBLIC_API_KEY="${PLATFORM_PUBLIC_API_KEY:-}"

VERIFY_ASYNC_DELETE_SMOKE="${VERIFY_ASYNC_DELETE_SMOKE:-true}"
VERIFY_DEPLOYMENT_OVERRIDE_SMOKE="${VERIFY_DEPLOYMENT_OVERRIDE_SMOKE:-true}"
VERIFY_CANONICAL_ROLLOUT_READONLY="${VERIFY_CANONICAL_ROLLOUT_READONLY:-true}"
VERIFY_CANONICAL_ROLLOUT_MUTATION="${VERIFY_CANONICAL_ROLLOUT_MUTATION:-false}"
CANONICAL_ROLLOUT_KEYS="${CANONICAL_ROLLOUT_KEYS:-}"
VERIFY_CONSUMER_RESOLUTION_SMOKE="${VERIFY_CONSUMER_RESOLUTION_SMOKE:-true}"
VERIFY_INFERENCE_SERVICE_UI="${VERIFY_INFERENCE_SERVICE_UI:-true}"
VERIFY_INFERENCE_SERVICE_ADMIN_READONLY="${VERIFY_INFERENCE_SERVICE_ADMIN_READONLY:-true}"
VERIFY_INFERENCE_SERVICE_ADMIN_MUTATION="${VERIFY_INFERENCE_SERVICE_ADMIN_MUTATION:-false}"
INFERENCE_SERVICE_REF="${INFERENCE_SERVICE_REF:-shared-ollama-orchestration}"
INFERENCE_SERVICE_ROTATE_SECRET_VALUE="${INFERENCE_SERVICE_ROTATE_SECRET_VALUE:-}"
INFERENCE_SERVICE_SCALE_TARGET="${INFERENCE_SERVICE_SCALE_TARGET:-}"

DELETE_SMOKE_TEMPLATE_ID="${DELETE_SMOKE_TEMPLATE_ID:-dev-openai-lucene}"
DELETE_SMOKE_ENVIRONMENT="${DELETE_SMOKE_ENVIRONMENT:-dev}"
DELETE_SMOKE_NAME_PREFIX="${DELETE_SMOKE_NAME_PREFIX:-Regression Delete Smoke}"
DELETE_SMOKE_CURATED_MODULE_ID="${DELETE_SMOKE_CURATED_MODULE_ID:-}"
DELETE_SMOKE_VECTOR_PROVISIONING_MODE="${DELETE_SMOKE_VECTOR_PROVISIONING_MODE:-}"
DELETE_SMOKE_TIMEOUT_ATTEMPTS="${DELETE_SMOKE_TIMEOUT_ATTEMPTS:-40}"
DELETE_SMOKE_TIMEOUT_SLEEP_SECONDS="${DELETE_SMOKE_TIMEOUT_SLEEP_SECONDS:-2}"
PLATFORM_HTTP_RETRY_ATTEMPTS="${PLATFORM_HTTP_RETRY_ATTEMPTS:-4}"
PLATFORM_HTTP_RETRY_SLEEP_SECONDS="${PLATFORM_HTTP_RETRY_SLEEP_SECONDS:-5}"

HTTP_STATUS=""
HTTP_BODY=""
TMP_DIR=""
PLATFORM_COOKIE_JAR=""
TEMP_DEPLOYMENT_ID=""
TEMP_DEPLOYMENT_CLEANED_UP="false"
TEMP_OVERRIDE_SECRET_NAME=""
TEMP_CONSUMER_CUSTOMER_ID=""
TEMP_CONSUMER_ID=""
TEMP_CONSUMER_DEPLOYMENT_ID=""
PLATFORM_CURRENT_ACTOR_ID=""
PLATFORM_CURRENT_USER_ID=""

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
import pathlib
import sys
print(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
PY
    return
  fi

  printf '%s' "${direct_value}"
}

trim_slash() {
  local url="$1"
  if [[ "${url}" == */ ]]; then
    printf '%s' "${url%/}"
  else
    printf '%s' "${url}"
  fi
}

require_cmd() {
  local cmd="$1"
  if ! command -v "${cmd}" >/dev/null 2>&1; then
    echo "Missing required command: ${cmd}"
    exit 2
  fi
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

json_extract() {
  local py="$1"
  JSON_EXTRACT_BODY="${HTTP_BODY}" JSON_EXTRACT_PY="${py}" python3 - <<'PY'
import json
import os

raw = os.environ.get("JSON_EXTRACT_BODY", "").strip()
data = json.loads(raw) if raw else None
namespace = {"data": data}
exec(os.environ["JSON_EXTRACT_PY"].replace("\\n", "\n"), namespace, namespace)
PY
}

public_http_get() {
  local url="$1"

  local tmp
  tmp="$(mktemp)"

  local status=""
  local attempt=1
  while true; do
    status="$(curl -sS -L -o "${tmp}" -w "%{http_code}" "${url}" || true)"
    if [[ ( "${status}" == "000" || "${status}" == "502" || "${status}" == "503" || "${status}" == "504" ) \
        && "${attempt}" -lt "${PLATFORM_HTTP_RETRY_ATTEMPTS}" ]]; then
      echo "WARN: transient public GET ${url} returned HTTP ${status}; retrying (${attempt}/${PLATFORM_HTTP_RETRY_ATTEMPTS})..." >&2
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

consumer_resolution_http_get() {
  local url="$1"

  if [[ -n "${PLATFORM_PUBLIC_API_CLIENT_ID}" && -n "${PLATFORM_PUBLIC_API_KEY}" ]]; then
    local tmp
    tmp="$(mktemp)"

    local status=""
    local attempt=1
    while true; do
      status="$(
        curl -sS -o "${tmp}" -w "%{http_code}" \
          -H "Accept: application/json" \
          -H "${PLATFORM_PUBLIC_API_CLIENT_ID_HEADER}: ${PLATFORM_PUBLIC_API_CLIENT_ID}" \
          -H "${PLATFORM_PUBLIC_API_KEY_HEADER}: ${PLATFORM_PUBLIC_API_KEY}" \
          "${url}" || true
      )"
      if [[ ( "${status}" == "000" || "${status}" == "502" || "${status}" == "503" || "${status}" == "504" ) \
          && "${attempt}" -lt "${PLATFORM_HTTP_RETRY_ATTEMPTS}" ]]; then
        echo "WARN: transient consumer resolution GET ${url} returned HTTP ${status}; retrying (${attempt}/${PLATFORM_HTTP_RETRY_ATTEMPTS})..." >&2
        sleep "${PLATFORM_HTTP_RETRY_SLEEP_SECONDS}"
        attempt=$((attempt + 1))
        continue
      fi
      break
    done

    HTTP_STATUS="${status}"
    HTTP_BODY="$(cat "${tmp}")"
    rm -f "${tmp}"
    return 0
  fi

  platform_http GET "${url}"
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
  cat > "${payload}" <<EOF
{"email":"${PLATFORM_LOGIN_EMAIL}","password":"${PLATFORM_LOGIN_PASSWORD}"}
EOF
  local status
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
    i=$((i + 1))
  done

  echo "---- ${label} ----"
  echo "Last HTTP ${HTTP_STATUS}"
  echo "${HTTP_BODY}"
  echo "------------------"
  fail "${label} (timed out after ${attempts} attempts)"
}

cleanup() {
  if [[ -n "${TEMP_DEPLOYMENT_ID}" && "${TEMP_DEPLOYMENT_CLEANED_UP}" != "true" ]]; then
    echo "WARN: attempting best-effort cleanup for temp deployment ${TEMP_DEPLOYMENT_ID}"
    platform_http POST "${PLATFORM_BASE_URL}/api/deployments/${TEMP_DEPLOYMENT_ID}/archive" || true
    local payload='{"hardDelete":false,"reason":"Best-effort cleanup from verify-platform-admin-regression.sh"}'
    platform_http DELETE "${PLATFORM_BASE_URL}/api/deployments/${TEMP_DEPLOYMENT_ID}" "${payload}" || true
  fi
  if [[ -n "${TEMP_CONSUMER_ID}" && -n "${TEMP_CONSUMER_CUSTOMER_ID}" ]]; then
    platform_http PUT "${PLATFORM_BASE_URL}/api/platform/customers/${TEMP_CONSUMER_CUSTOMER_ID}/consumers/${TEMP_CONSUMER_ID}/binding" '{"deploymentId":null,"reason":"Best-effort cleanup from verify-platform-admin-regression.sh"}' || true
    platform_http DELETE "${PLATFORM_BASE_URL}/api/platform/customers/${TEMP_CONSUMER_CUSTOMER_ID}/consumers/${TEMP_CONSUMER_ID}" || true
  fi
  if [[ -n "${TEMP_CONSUMER_DEPLOYMENT_ID}" ]]; then
    platform_http POST "${PLATFORM_BASE_URL}/api/deployments/${TEMP_CONSUMER_DEPLOYMENT_ID}/archive" || true
    local consumer_payload='{"hardDelete":false,"reason":"Best-effort cleanup from verify-platform-admin-regression.sh consumer smoke"}'
    platform_http DELETE "${PLATFORM_BASE_URL}/api/deployments/${TEMP_CONSUMER_DEPLOYMENT_ID}" "${consumer_payload}" || true
  fi
  if [[ -n "${TEMP_OVERRIDE_SECRET_NAME}" ]]; then
    platform_http DELETE "${PLATFORM_BASE_URL}/api/platform/secrets/deployment-overrides/${TEMP_OVERRIDE_SECRET_NAME}" || true
  fi
  rm -rf "${TMP_DIR}"
}

build_create_deployment_payload() {
  CREATE_NAME="$1" \
  DELETE_SMOKE_ENVIRONMENT="${DELETE_SMOKE_ENVIRONMENT}" \
  DELETE_SMOKE_TEMPLATE_ID="${DELETE_SMOKE_TEMPLATE_ID}" \
  DELETE_SMOKE_CURATED_MODULE_ID="${DELETE_SMOKE_CURATED_MODULE_ID}" \
  DELETE_SMOKE_VECTOR_PROVISIONING_MODE="${DELETE_SMOKE_VECTOR_PROVISIONING_MODE}" \
  python3 - <<'PY'
import json
import os

payload = {
    "name": os.environ["CREATE_NAME"],
    "environment": os.environ["DELETE_SMOKE_ENVIRONMENT"],
    "templateId": os.environ["DELETE_SMOKE_TEMPLATE_ID"],
}
curated = os.environ.get("DELETE_SMOKE_CURATED_MODULE_ID", "").strip()
if curated:
    payload["curatedModuleId"] = curated
vector_mode = os.environ.get("DELETE_SMOKE_VECTOR_PROVISIONING_MODE", "").strip()
if vector_mode:
    payload["vectorProvisioningMode"] = vector_mode
print(json.dumps(payload))
PY
}

build_rollout_payload() {
  ROLLOUT_KEYS_TEXT="${CANONICAL_ROLLOUT_KEYS}" python3 - <<'PY'
import json
import os

keys = []
for part in (os.environ.get("ROLLOUT_KEYS_TEXT") or "").split(","):
    value = part.strip()
    if value and value not in keys:
        keys.append(value)
print(json.dumps({"rolloutKeys": keys}))
PY
}

assert_inference_service_summary() {
  local label="$1"
  local expected_operation="${2:-}"
  local expected_replicas="${3:-}"

  INFERENCE_EXPECTED_OPERATION="${expected_operation}" \
  INFERENCE_EXPECTED_REPLICAS="${expected_replicas}" \
  json_assert "${label}" $'assert (data or {}).get("serviceRef") == "'"${INFERENCE_SERVICE_REF}"'"\nassert bool((data or {}).get("displayName"))\nassert bool((data or {}).get("serviceKind"))\nassert bool((data or {}).get("providerType"))\nassert bool((data or {}).get("status"))\nassert isinstance((data or {}).get("dependentDeploymentsCount"), int)\nassert isinstance((data or {}).get("dependentActiveDeploymentsCount"), int)\nendpoints = (data or {}).get("endpoints") or []\nassert isinstance(endpoints, list)\nassert len(endpoints) >= 1, endpoints\nexpected_operation = "'"${expected_operation}"'".strip()\nif expected_operation:\n  assert (data or {}).get("lastVerifiedOperation") == expected_operation, data\n  assert (data or {}).get("lastVerifiedStatus") == "READY", data\n  assert bool((data or {}).get("lastVerifiedAt")), data\n  assert bool((data or {}).get("lastVerifiedMessage")), data\n  assert (data or {}).get("driftStatus") == "NO_DRIFT", data\nexpected_replicas = "'"${expected_replicas}"'".strip()\nif expected_replicas:\n  expected = int(expected_replicas)\n  assert (data or {}).get("desiredReplicas") == expected, data\n  assert (data or {}).get("actualReplicas") == expected, data\nprint("ok")'
}

assert_inference_service_health() {
  local label="$1"

  json_assert "${label}" $'assert (data or {}).get("serviceRef") == "'"${INFERENCE_SERVICE_REF}"'"\nassert (data or {}).get("status") == "READY", data\nassert (data or {}).get("driftStatus") == "NO_DRIFT", data\nassert (data or {}).get("secretConfigured") is True, data\nhealth_probe = (data or {}).get("healthProbe") or {}\ninference_probe = (data or {}).get("inferenceProbe") or {}\nassert (health_probe or {}).get("status") == "READY", health_probe\nassert bool((health_probe or {}).get("endpoint")), health_probe\nassert (inference_probe or {}).get("status") == "READY", inference_probe\nassert bool((inference_probe or {}).get("endpoint")), inference_probe\nassert bool((inference_probe or {}).get("message")), inference_probe\nprint("ok")'
}

verify_inference_service_ui() {
  echo ""
  echo "== Inference Service UI =="

  public_http_get "${PLATFORM_UI_BASE_URL}/inference-services"
  assert_status 200 "inference services ui route"

  local html_file
  html_file="$(mktemp "${TMP_DIR}/inference-ui-html.XXXXXX")"
  printf '%s' "${HTTP_BODY}" > "${html_file}"

  local bundle_url
  bundle_url="$(
    python3 - <<'PY' "${html_file}" "${PLATFORM_UI_BASE_URL}"
import re
import sys
import urllib.parse

with open(sys.argv[1], "r", encoding="utf-8") as handle:
    html = handle.read()
base_url = sys.argv[2]
matches = re.findall(r'<script[^>]+src="([^"]+\.js)"', html)
if not matches:
    raise SystemExit("No JavaScript bundle found in UI HTML")
preferred = next((value for value in matches if "/assets/" in value), matches[0])
print(urllib.parse.urljoin(base_url.rstrip("/") + "/", preferred))
PY
  )"

  public_http_get "${bundle_url}"
  assert_status 200 "inference services ui bundle"

  local bundle_file
  bundle_file="$(mktemp "${TMP_DIR}/inference-ui-bundle.XXXXXX")"
  printf '%s' "${HTTP_BODY}" > "${bundle_file}"

  python3 - <<'PY' "${bundle_file}"
import pathlib
import sys

bundle = pathlib.Path(sys.argv[1]).read_text(encoding="utf-8")
required_tokens = [
    "Inference Services",
    "Run health check",
    "Restart",
    "Rotate secret",
    "Force recreate",
    "Apply scale",
    "Recent activity",
    "Dependents",
]
missing = [token for token in required_tokens if token not in bundle]
if missing:
    raise SystemExit(f"Missing UI bundle tokens: {', '.join(missing)}")
PY

  pass "public UI /inference-services bundle"
}

verify_inference_service_admin() {
  echo ""
  echo "== Inference Service Admin =="

  platform_http GET "${PLATFORM_BASE_URL}/api/marketplace/inference-services"
  assert_status 200 "list inference services"
  json_assert "list inference services" $'items = data or []\nassert isinstance(items, list)\nmatching = [item for item in items if (item or {}).get("serviceRef") == "'"${INFERENCE_SERVICE_REF}"'"]\nassert len(matching) == 1, items\nservice = matching[0]\nassert bool((service or {}).get("displayName"))\nassert bool((service or {}).get("serviceKind"))\nassert bool((service or {}).get("providerType"))\nassert bool((service or {}).get("status"))\nprint("ok")'
  pass "platform GET /api/marketplace/inference-services"

  platform_http GET "${PLATFORM_BASE_URL}/api/marketplace/inference-services/${INFERENCE_SERVICE_REF}"
  assert_status 200 "get inference service"
  assert_inference_service_summary "get inference service"
  local replica_state
  replica_state="$(
    SERVICE_BODY="${HTTP_BODY}" SCALE_TARGET_OVERRIDE="${INFERENCE_SERVICE_SCALE_TARGET}" python3 - <<'PY'
import json
import os

service = json.loads(os.environ.get("SERVICE_BODY", "") or "{}")
current = service.get("desiredReplicas")
if not isinstance(current, int) or current <= 0:
    current = service.get("actualReplicas")
if not isinstance(current, int) or current <= 0:
    current = 1
min_replicas = service.get("minReplicas")
max_replicas = service.get("maxReplicas")
lower = min_replicas if isinstance(min_replicas, int) and min_replicas > 0 else 1
upper = max_replicas if isinstance(max_replicas, int) and max_replicas > 0 else None
override = (os.environ.get("SCALE_TARGET_OVERRIDE") or "").strip()
if override:
    target = int(override)
    if target == current:
        raise SystemExit(f"Configured scale target {target} matches current replica count")
    if target < lower:
        raise SystemExit(f"Configured scale target {target} is below lower bound {lower}")
    if upper is not None and target > upper:
        raise SystemExit(f"Configured scale target {target} is above upper bound {upper}")
else:
    candidates = []
    if upper is None or current + 1 <= upper:
        candidates.append(current + 1)
    if current - 1 >= lower:
        candidates.append(current - 1)
    if 2 >= lower and (upper is None or 2 <= upper):
        candidates.append(2)
    target = next((candidate for candidate in candidates if candidate != current), None)
    if target is None:
        raise SystemExit("Unable to find an alternate replica count for scale verification")
print(f"{current}:{target}")
PY
  )"
  local original_replicas="${replica_state%%:*}"
  local target_replicas="${replica_state##*:}"
  pass "platform GET /api/marketplace/inference-services/${INFERENCE_SERVICE_REF}"

  platform_http GET "${PLATFORM_BASE_URL}/api/marketplace/inference-services/${INFERENCE_SERVICE_REF}/dependents"
  assert_status 200 "list inference service dependents"
  json_assert "list inference service dependents" $'items = data or []\nassert isinstance(items, list)\nfor item in items:\n  assert bool((item or {}).get("deploymentId")), item\n  assert bool((item or {}).get("deploymentName")), item\n  assert isinstance((item or {}).get("usages"), list), item\nprint("ok")'
  pass "platform GET /api/marketplace/inference-services/${INFERENCE_SERVICE_REF}/dependents"

  platform_http GET "${PLATFORM_BASE_URL}/api/marketplace/inference-services/${INFERENCE_SERVICE_REF}/activity"
  assert_status 200 "list inference service activity"
  json_assert "list inference service activity" $'items = data or []\nassert isinstance(items, list)\nfor item in items[:10]:\n  assert bool((item or {}).get("id")), item\n  assert bool((item or {}).get("action")), item\n  assert bool((item or {}).get("createdAt")), item\nprint("ok")'
  pass "platform GET /api/marketplace/inference-services/${INFERENCE_SERVICE_REF}/activity"

  platform_http GET "${PLATFORM_BASE_URL}/api/marketplace/inference-services/${INFERENCE_SERVICE_REF}/health"
  assert_status 200 "get inference service health"
  assert_inference_service_health "get inference service health"
  pass "platform GET /api/marketplace/inference-services/${INFERENCE_SERVICE_REF}/health"

  if [[ "${VERIFY_INFERENCE_SERVICE_ADMIN_MUTATION}" == "true" ]]; then
    platform_http POST "${PLATFORM_BASE_URL}/api/marketplace/inference-services/${INFERENCE_SERVICE_REF}/restart"
    assert_status 200 "restart inference service"
    assert_inference_service_summary "restart inference service" "RESTART"
    pass "platform POST /api/marketplace/inference-services/${INFERENCE_SERVICE_REF}/restart"

    local scale_payload
    scale_payload="$(DESIRED_REPLICAS="${target_replicas}" python3 - <<'PY'
import json
import os
print(json.dumps({"desiredReplicas": int(os.environ["DESIRED_REPLICAS"])}))
PY
)"
    platform_http PUT "${PLATFORM_BASE_URL}/api/marketplace/inference-services/${INFERENCE_SERVICE_REF}/scale" "${scale_payload}"
    assert_status 200 "scale inference service up"
    assert_inference_service_summary "scale inference service up" "SCALE" "${target_replicas}"
    pass "platform PUT /api/marketplace/inference-services/${INFERENCE_SERVICE_REF}/scale -> ${target_replicas}"

    scale_payload="$(DESIRED_REPLICAS="${original_replicas}" python3 - <<'PY'
import json
import os
print(json.dumps({"desiredReplicas": int(os.environ["DESIRED_REPLICAS"])}))
PY
)"
    platform_http PUT "${PLATFORM_BASE_URL}/api/marketplace/inference-services/${INFERENCE_SERVICE_REF}/scale" "${scale_payload}"
    assert_status 200 "scale inference service restore"
    assert_inference_service_summary "scale inference service restore" "SCALE" "${original_replicas}"
    pass "platform PUT /api/marketplace/inference-services/${INFERENCE_SERVICE_REF}/scale -> ${original_replicas}"

    local rotate_payload
    rotate_payload="$(ROTATE_VALUE="${INFERENCE_SERVICE_ROTATE_SECRET_VALUE}" python3 - <<'PY'
import json
import os
print(json.dumps({"value": os.environ["ROTATE_VALUE"]}))
PY
)"
    platform_http PUT "${PLATFORM_BASE_URL}/api/marketplace/inference-services/${INFERENCE_SERVICE_REF}/rotate-secret" "${rotate_payload}"
    assert_status 200 "rotate inference service secret"
    assert_inference_service_summary "rotate inference service secret" "ROTATE_SECRET" "${original_replicas}"
    pass "platform PUT /api/marketplace/inference-services/${INFERENCE_SERVICE_REF}/rotate-secret"

    platform_http POST "${PLATFORM_BASE_URL}/api/marketplace/inference-services/${INFERENCE_SERVICE_REF}/force-recreate"
    assert_status 200 "force recreate inference service"
    assert_inference_service_summary "force recreate inference service" "FORCE_RECREATE" "${original_replicas}"
    pass "platform POST /api/marketplace/inference-services/${INFERENCE_SERVICE_REF}/force-recreate"

    platform_http GET "${PLATFORM_BASE_URL}/api/marketplace/inference-services/${INFERENCE_SERVICE_REF}/activity"
    assert_status 200 "list inference service activity after mutations"
    json_assert "list inference service activity after mutations" $'items = data or []\nassert isinstance(items, list)\nverified = set()\nfor item in items:\n  if (item or {}).get("action") != "MANAGED_INFERENCE_OPERATION_VERIFIED":\n    continue\n  details = (item or {}).get("details") or {}\n  action = (details or {}).get("action")\n  if action:\n    verified.add(action)\nexpected = {"RESTART", "SCALE", "ROTATE_SECRET", "FORCE_RECREATE"}\nmissing = expected - verified\nassert not missing, {"missing": sorted(missing), "verified": sorted(verified)}\nprint("ok")'
    pass "inference service activity recorded verification actions"
  fi
}

verify_consumer_resolution_smoke() {
  echo ""
  echo "== Consumer Resolution Smoke =="

  platform_http GET "${PLATFORM_BASE_URL}/api/deployments/overview?includeArchived=false"
  assert_status 200 "list deployment overviews for consumer smoke"

  TEMP_CONSUMER_CUSTOMER_ID="$(
    TARGET_DEPLOYMENT_ID="${ADMIN_TARGET_DEPLOYMENT_ID}" JSON_EXTRACT_BODY="${HTTP_BODY}" python3 - <<'PY'
import json
import os

items = json.loads(os.environ.get("JSON_EXTRACT_BODY", "") or "[]")
target = os.environ["TARGET_DEPLOYMENT_ID"]
for item in items:
    if (item or {}).get("id") != target:
        continue
    binding = (item or {}).get("binding") or {}
    customer_id = binding.get("customerId")
    if not customer_id:
        raise SystemExit(f"Missing customer binding for deployment {target}")
    print(customer_id)
    break
else:
    raise SystemExit(f"Deployment not found in overview payload: {target}")
PY
  )"

  local consumer_timestamp
  consumer_timestamp="$(date -u +%Y%m%d%H%M%S)"
  local consumer_create_payload
  consumer_create_payload="$(
    CREATE_NAME="Consumer Resolution Smoke ${consumer_timestamp}" \
    CUSTOMER_ID="${TEMP_CONSUMER_CUSTOMER_ID}" \
    python3 - <<'PY'
import json
import os

payload = {
    "name": os.environ["CREATE_NAME"],
    "environment": "dev",
    "templateId": "dev-openai-lucene",
    "customerId": os.environ["CUSTOMER_ID"],
}
print(json.dumps(payload))
PY
  )"
  platform_http POST "${PLATFORM_BASE_URL}/api/deployments" "${consumer_create_payload}"
  assert_status 201 "create consumer smoke deployment"
  json_assert "create consumer smoke deployment" $'assert bool((data or {}).get("id"))\nassert (data or {}).get("binding", {}).get("customerId") == "'"${TEMP_CONSUMER_CUSTOMER_ID}"'"\nprint("ok")'
  TEMP_CONSUMER_DEPLOYMENT_ID="$(json_extract 'print((data or {}).get("id") or "")')"
  pass "platform POST /api/deployments (consumer smoke deployment)"

  TEMP_CONSUMER_ID="consumer-smoke-${consumer_timestamp}"
  local consumer_payload
  consumer_payload="$(
    CONSUMER_ID="${TEMP_CONSUMER_ID}" \
    DEPLOYMENT_ID="${ADMIN_TARGET_DEPLOYMENT_ID}" \
    python3 - <<'PY'
import json
import os

payload = {
    "consumerId": os.environ["CONSUMER_ID"],
    "displayName": "Consumer smoke",
    "description": "Live regression consumer-bound deployment resolution smoke.",
    "deploymentId": os.environ["DEPLOYMENT_ID"],
    "bindingReason": "Initial live regression binding."
}
print(json.dumps(payload))
PY
  )"
  platform_http POST "${PLATFORM_BASE_URL}/api/platform/customers/${TEMP_CONSUMER_CUSTOMER_ID}/consumers" "${consumer_payload}"
  assert_status 201 "create consumer smoke binding"
  json_assert "create consumer smoke binding" $'assert (data or {}).get("consumerId") == "'"${TEMP_CONSUMER_ID}"'"\nassert (data or {}).get("boundDeploymentId") == "'"${ADMIN_TARGET_DEPLOYMENT_ID}"'"\nprint("ok")'
  pass "platform POST /api/platform/customers/${TEMP_CONSUMER_CUSTOMER_ID}/consumers"

  public_http_get "${PLATFORM_BASE_URL}/api/public/consumers/${TEMP_CONSUMER_ID}/credentials"
  assert_status 401 "anonymous consumer credentials blocked"
  pass "anonymous GET /api/public/consumers/${TEMP_CONSUMER_ID}/credentials is blocked"

  consumer_resolution_http_get "${PLATFORM_BASE_URL}/api/public/consumers/${TEMP_CONSUMER_ID}/credentials"
  assert_status 200 "public consumer credentials"
  json_assert "public consumer credentials" $'assert (data or {}).get("consumerId") == "'"${TEMP_CONSUMER_ID}"'"\nassert (data or {}).get("deploymentId") == "'"${ADMIN_TARGET_DEPLOYMENT_ID}"'"\nassert bool(((data or {}).get("integration") or {}).get("preferredIntegrationMode"))\nprint("ok")'
  pass "public GET /api/public/consumers/${TEMP_CONSUMER_ID}/credentials"

  local rebind_payload
  rebind_payload="$(
    DEPLOYMENT_ID="${TEMP_CONSUMER_DEPLOYMENT_ID}" python3 - <<'PY'
import json
import os

payload = {
    "deploymentId": os.environ["DEPLOYMENT_ID"],
    "reason": "Rebind consumer to the replacement deployment during live regression."
}
print(json.dumps(payload))
PY
  )"
  platform_http PUT "${PLATFORM_BASE_URL}/api/platform/customers/${TEMP_CONSUMER_CUSTOMER_ID}/consumers/${TEMP_CONSUMER_ID}/binding" "${rebind_payload}"
  assert_status 200 "rebind consumer smoke"
  json_assert "rebind consumer smoke" $'assert (data or {}).get("consumerId") == "'"${TEMP_CONSUMER_ID}"'"\nassert (data or {}).get("boundDeploymentId") == "'"${TEMP_CONSUMER_DEPLOYMENT_ID}"'"\nprint("ok")'
  pass "platform PUT /api/platform/customers/${TEMP_CONSUMER_CUSTOMER_ID}/consumers/${TEMP_CONSUMER_ID}/binding"

  consumer_resolution_http_get "${PLATFORM_BASE_URL}/api/public/consumers/${TEMP_CONSUMER_ID}/status"
  assert_status 200 "public consumer status"
  json_assert "public consumer status" $'assert (data or {}).get("consumerId") == "'"${TEMP_CONSUMER_ID}"'"\nassert (data or {}).get("deploymentId") == "'"${TEMP_CONSUMER_DEPLOYMENT_ID}"'"\nassert bool((data or {}).get("status"))\nprint("ok")'
  pass "public GET /api/public/consumers/${TEMP_CONSUMER_ID}/status"

  platform_http GET "${PLATFORM_BASE_URL}/api/platform/customers/${TEMP_CONSUMER_CUSTOMER_ID}/consumers/${TEMP_CONSUMER_ID}/history"
  assert_status 200 "consumer binding history"
  json_assert "consumer binding history" $'items = data or []\nassert isinstance(items, list)\nassert len(items) >= 2, items\nassert any((item or {}).get("toDeploymentId") == "'"${ADMIN_TARGET_DEPLOYMENT_ID}"'" for item in items), items\nassert any((item or {}).get("toDeploymentId") == "'"${TEMP_CONSUMER_DEPLOYMENT_ID}"'" for item in items), items\nprint("ok")'
  pass "platform GET /api/platform/customers/${TEMP_CONSUMER_CUSTOMER_ID}/consumers/${TEMP_CONSUMER_ID}/history"

  platform_http PUT "${PLATFORM_BASE_URL}/api/platform/customers/${TEMP_CONSUMER_CUSTOMER_ID}/consumers/${TEMP_CONSUMER_ID}/binding" '{"deploymentId":null,"reason":"Unbind consumer after live regression."}'
  assert_status 200 "unbind consumer smoke"
  json_assert "unbind consumer smoke" $'assert (data or {}).get("consumerId") == "'"${TEMP_CONSUMER_ID}"'"\nassert (data or {}).get("boundDeploymentId") in (None, "")\nprint("ok")'
  pass "platform PUT /api/platform/customers/${TEMP_CONSUMER_CUSTOMER_ID}/consumers/${TEMP_CONSUMER_ID}/binding -> unbound"

  platform_http DELETE "${PLATFORM_BASE_URL}/api/platform/customers/${TEMP_CONSUMER_CUSTOMER_ID}/consumers/${TEMP_CONSUMER_ID}"
  assert_status 204 "delete consumer smoke"
  pass "platform DELETE /api/platform/customers/${TEMP_CONSUMER_CUSTOMER_ID}/consumers/${TEMP_CONSUMER_ID}"
  TEMP_CONSUMER_ID=""

  platform_http POST "${PLATFORM_BASE_URL}/api/deployments/${TEMP_CONSUMER_DEPLOYMENT_ID}/archive"
  assert_status 200 "archive consumer smoke deployment"
  platform_http DELETE "${PLATFORM_BASE_URL}/api/deployments/${TEMP_CONSUMER_DEPLOYMENT_ID}" '{"hardDelete":false,"reason":"Cleanup consumer smoke deployment"}'
  assert_status 202 "delete consumer smoke deployment"
  pass "consumer smoke deployment cleanup queued"
  TEMP_CONSUMER_DEPLOYMENT_ID=""
}

PLATFORM_API_KEY="$(resolve_secret_value PLATFORM_API_KEY)"
PLATFORM_COOKIE="$(resolve_secret_value PLATFORM_COOKIE)"
PLATFORM_LOGIN_EMAIL="$(resolve_secret_value PLATFORM_LOGIN_EMAIL)"
PLATFORM_LOGIN_PASSWORD="$(resolve_secret_value PLATFORM_LOGIN_PASSWORD)"
PLATFORM_PUBLIC_API_CLIENT_ID="$(resolve_secret_value PLATFORM_PUBLIC_API_CLIENT_ID)"
PLATFORM_PUBLIC_API_KEY="$(resolve_secret_value PLATFORM_PUBLIC_API_KEY)"
PLATFORM_UI_BASE_URL="$(resolve_secret_value PLATFORM_UI_BASE_URL)"
INFERENCE_SERVICE_ROTATE_SECRET_VALUE="$(resolve_secret_value INFERENCE_SERVICE_ROTATE_SECRET_VALUE)"

require_cmd curl
require_cmd python3

if [[ -z "${PLATFORM_BASE_URL}" ]]; then
  echo "Missing required env vars."
  echo "Set PLATFORM_BASE_URL."
  exit 2
fi

if [[ "${VERIFY_CANONICAL_ROLLOUT_MUTATION}" == "true" ]]; then
  if [[ -z "${CANONICAL_ROLLOUT_KEYS}" ]]; then
    echo "VERIFY_CANONICAL_ROLLOUT_MUTATION=true requires CANONICAL_ROLLOUT_KEYS."
    exit 2
  fi
fi
if [[ "${VERIFY_DEPLOYMENT_OVERRIDE_SMOKE}" == "true" && "${VERIFY_ASYNC_DELETE_SMOKE}" != "true" ]]; then
  echo "VERIFY_DEPLOYMENT_OVERRIDE_SMOKE=true requires VERIFY_ASYNC_DELETE_SMOKE=true because the smoke proves hard-delete cleanup."
  exit 2
fi
if [[ "${VERIFY_INFERENCE_SERVICE_ADMIN_MUTATION}" == "true" && "${VERIFY_INFERENCE_SERVICE_ADMIN_READONLY}" != "true" ]]; then
  echo "VERIFY_INFERENCE_SERVICE_ADMIN_MUTATION=true requires VERIFY_INFERENCE_SERVICE_ADMIN_READONLY=true."
  exit 2
fi
if [[ "${VERIFY_INFERENCE_SERVICE_UI}" == "true" && -z "${PLATFORM_UI_BASE_URL}" ]]; then
  echo "VERIFY_INFERENCE_SERVICE_UI=true requires PLATFORM_UI_BASE_URL."
  exit 2
fi
if [[ "${VERIFY_INFERENCE_SERVICE_ADMIN_MUTATION}" == "true" && -z "${INFERENCE_SERVICE_ROTATE_SECRET_VALUE}" ]]; then
  echo "VERIFY_INFERENCE_SERVICE_ADMIN_MUTATION=true requires INFERENCE_SERVICE_ROTATE_SECRET_VALUE."
  exit 2
fi

PLATFORM_BASE_URL="$(trim_slash "${PLATFORM_BASE_URL}")"
if [[ -n "${PLATFORM_UI_BASE_URL}" ]]; then
  PLATFORM_UI_BASE_URL="$(trim_slash "${PLATFORM_UI_BASE_URL}")"
fi
TMP_DIR="$(mktemp -d)"
PLATFORM_COOKIE_JAR="${TMP_DIR}/platform-cookie.txt"
trap cleanup EXIT

echo "Platform: ${PLATFORM_BASE_URL}"
if [[ -n "${ADMIN_TARGET_DEPLOYMENT_ID}" ]]; then
  echo "Admin target deployment: ${ADMIN_TARGET_DEPLOYMENT_ID}"
fi
echo "Verify async delete smoke: ${VERIFY_ASYNC_DELETE_SMOKE}"
echo "Verify deployment override smoke: ${VERIFY_DEPLOYMENT_OVERRIDE_SMOKE}"
echo "Verify canonical rollout inventory: ${VERIFY_CANONICAL_ROLLOUT_READONLY}"
echo "Verify canonical rollout mutation: ${VERIFY_CANONICAL_ROLLOUT_MUTATION}"
echo "Verify consumer resolution smoke: ${VERIFY_CONSUMER_RESOLUTION_SMOKE}"
echo "Verify inference service UI: ${VERIFY_INFERENCE_SERVICE_UI}"
echo "Verify inference service admin read-only: ${VERIFY_INFERENCE_SERVICE_ADMIN_READONLY}"
echo "Verify inference service admin mutation: ${VERIFY_INFERENCE_SERVICE_ADMIN_MUTATION}"
if [[ "${VERIFY_INFERENCE_SERVICE_ADMIN_READONLY}" == "true" || "${VERIFY_INFERENCE_SERVICE_ADMIN_MUTATION}" == "true" ]]; then
  echo "Inference service ref: ${INFERENCE_SERVICE_REF}"
fi

platform_login

echo ""
echo "== Platform Admin Auth =="
platform_http GET "${PLATFORM_BASE_URL}/actuator/health"
assert_status 200 "platform health"
json_assert "platform health" $'assert (data or {}).get("status") == "UP"\nprint("ok")'
pass "platform /actuator/health"

platform_http GET "${PLATFORM_BASE_URL}/api/platform/auth/session"
assert_status 200 "platform auth session"
json_assert "platform auth session" $'assert (data or {}).get("authenticated") is True\nassert (data or {}).get("canManageUsers") is True\nprint("ok")'
PLATFORM_CURRENT_ACTOR_ID="$(PARSE_BODY="${HTTP_BODY}" python3 - <<'PY'
import json
import os
d = json.loads(os.environ.get("PARSE_BODY", "") or "{}")
print((d.get("actorId")) or "")
PY
)"
pass "platform GET /api/platform/auth/session"

echo ""
echo "== Platform User Directory =="
platform_http GET "${PLATFORM_BASE_URL}/api/platform/users"
assert_status 200 "platform users"
json_assert "platform users" $'items = data or []\nassert isinstance(items, list)\nassert len(items) >= 1\nfor item in items[:5]:\n  assert (item or {}).get("id"), item\n  assert (item or {}).get("email"), item\n  assert (item or {}).get("role"), item\nprint("ok")'
PLATFORM_CURRENT_USER_ID="$(PARSE_BODY="${HTTP_BODY}" CURRENT_ACTOR_ID="${PLATFORM_CURRENT_ACTOR_ID}" python3 - <<'PY'
import json
import os

items = json.loads(os.environ.get("PARSE_BODY", "") or "[]")
actor_id = (os.environ.get("CURRENT_ACTOR_ID") or "").strip().lower()
selected = None
for item in items:
    if not isinstance(item, dict):
        continue
    email = (item.get("email") or "").strip().lower()
    if actor_id and email == actor_id:
        selected = item
        break
if selected is None:
    for item in items:
        if isinstance(item, dict) and (item.get("role") or "") == "PLATFORM_ADMIN":
            selected = item
            break
print("" if selected is None else (selected.get("id") or ""))
PY
)"
if [[ -z "${PLATFORM_CURRENT_USER_ID}" ]]; then
  fail "Unable to resolve current platform-admin user id from /api/platform/users"
fi
pass "platform GET /api/platform/users"

if [[ -n "${ADMIN_TARGET_DEPLOYMENT_ID}" ]]; then
  platform_http GET "${PLATFORM_BASE_URL}/api/platform/users/access-overview?deploymentId=${ADMIN_TARGET_DEPLOYMENT_ID}"
  assert_status 200 "platform user access overview"
  json_assert "platform user access overview" $'items = data or []\nassert isinstance(items, list)\nassert len(items) >= 1\nfor item in items[:10]:\n  assert (item or {}).get("id"), item\n  assert (item or {}).get("email"), item\nselected = [(item or {}).get("selectedDeploymentAssignment") for item in items if (item or {}).get("selectedDeploymentAssignment")]\nfor assignment in selected:\n  assert (assignment or {}).get("deploymentId") == "'"${ADMIN_TARGET_DEPLOYMENT_ID}"'", assignment\nprint("ok")'
  pass "platform GET /api/platform/users/access-overview"

  echo ""
  echo "== Existing Deployment Assignments =="
  platform_http GET "${PLATFORM_BASE_URL}/api/deployments/${ADMIN_TARGET_DEPLOYMENT_ID}/assignments"
  assert_status 200 "existing deployment assignments"
  json_assert "existing deployment assignments" $'items = data or []\nassert isinstance(items, list)\nfor item in items:\n  assert (item or {}).get("deploymentId") == "'"${ADMIN_TARGET_DEPLOYMENT_ID}"'", item\n  assert (item or {}).get("assignmentRole"), item\n  assert (item or {}).get("userId"), item\nprint("ok")'
  pass "platform GET /api/deployments/${ADMIN_TARGET_DEPLOYMENT_ID}/assignments"
fi

if [[ "${VERIFY_CONSUMER_RESOLUTION_SMOKE}" == "true" && -n "${ADMIN_TARGET_DEPLOYMENT_ID}" ]]; then
  verify_consumer_resolution_smoke
fi

echo ""
echo "== Admin Notifications =="
platform_http GET "${PLATFORM_BASE_URL}/api/platform/notifications/deployment-deletions?limit=20"
assert_status 200 "deployment deletion notifications"
json_assert "deployment deletion notifications" $'items = data or []\nassert isinstance(items, list)\nprint("ok")'
pass "platform GET /api/platform/notifications/deployment-deletions"

if [[ "${VERIFY_CANONICAL_ROLLOUT_READONLY}" == "true" ]]; then
  echo ""
  echo "== Canonical Rollout Inventory =="
  platform_http GET "${PLATFORM_BASE_URL}/api/deployments/verification-rollouts"
  assert_status 200 "canonical rollout inventory"
  json_assert "canonical rollout inventory" $'items = (data or {}).get("items") or []\nkeys = {item.get("key") for item in items}\nfor required in {"ecommerce", "qdrant", "pinecone", "milvus", "weaviate"}:\n  assert required in keys, {"required": required, "actual": sorted(keys)}\nassert bool((data or {}).get("summaryMessage"))\nprint("ok")'
  pass "platform GET /api/deployments/verification-rollouts"
fi

if [[ "${VERIFY_CANONICAL_ROLLOUT_MUTATION}" == "true" ]]; then
  echo ""
  echo "== Canonical Rollout Mutation =="
  rollout_payload="$(build_rollout_payload)"

  platform_http POST "${PLATFORM_BASE_URL}/api/deployments/verification-rollouts/recreate" "${rollout_payload}"
  assert_status 200 "canonical rollout recreate"
  json_assert "canonical rollout recreate" $'message = (data or {}).get("summaryMessage") or ""\nassert "Created or reapplied" in message, message\nitems = (data or {}).get("items") or []\nselected = {item.strip() for item in "'"${CANONICAL_ROLLOUT_KEYS}"'".split(",") if item.strip()}\nkeys = {item.get("key") for item in items}\nfor required in selected:\n  assert required in keys, {"required": required, "actual": sorted(keys)}\nprint("ok")'
  pass "platform POST /api/deployments/verification-rollouts/recreate"

  platform_http POST "${PLATFORM_BASE_URL}/api/deployments/verification-rollouts/cleanup" "${rollout_payload}"
  assert_status 200 "canonical rollout cleanup"
  json_assert "canonical rollout cleanup" $'message = (data or {}).get("summaryMessage") or ""\nassert "Queued cleanup" in message, message\nitems = (data or {}).get("items") or []\nselected = {item.strip() for item in "'"${CANONICAL_ROLLOUT_KEYS}"'".split(",") if item.strip()}\nkeys = {item.get("key") for item in items}\nfor required in selected:\n  assert required in keys, {"required": required, "actual": sorted(keys)}\nprint("ok")'
  pass "platform POST /api/deployments/verification-rollouts/cleanup"
fi

if [[ "${VERIFY_ASYNC_DELETE_SMOKE}" == "true" ]]; then
  echo ""
  echo "== Async Deletion Smoke =="
  temp_name="${DELETE_SMOKE_NAME_PREFIX} $(date +%s)"
  create_payload="$(build_create_deployment_payload "${temp_name}")"

  platform_http POST "${PLATFORM_BASE_URL}/api/deployments" "${create_payload}"
  assert_status 201 "create temp deployment"
  json_assert "create temp deployment" $'assert bool((data or {}).get("id"))\nassert (data or {}).get("name") == "'"${temp_name}"'"\nprint("ok")'
  TEMP_DEPLOYMENT_ID="$(PARSE_BODY="${HTTP_BODY}" python3 - <<'PY'
import json
import os
d = json.loads(os.environ.get("PARSE_BODY", "") or "{}")
print((d.get("id")) or "")
PY
)"
  pass "platform POST /api/deployments (temp deployment ${TEMP_DEPLOYMENT_ID})"

  assignment_payload="$(ASSIGN_USER_ID="${PLATFORM_CURRENT_USER_ID}" python3 - <<'PY'
import json
import os
print(json.dumps({
    "userId": os.environ["ASSIGN_USER_ID"],
    "assignmentRole": "DEPLOYMENT_ADMIN",
}))
PY
)"
  platform_http POST "${PLATFORM_BASE_URL}/api/deployments/${TEMP_DEPLOYMENT_ID}/assignments" "${assignment_payload}"
  assert_status 201 "assign temp deployment admin"
  json_assert "assign temp deployment admin" $'assert (data or {}).get("deploymentId") == "'"${TEMP_DEPLOYMENT_ID}"'"\nassert (data or {}).get("userId") == "'"${PLATFORM_CURRENT_USER_ID}"'"\nassert (data or {}).get("assignmentRole") == "DEPLOYMENT_ADMIN"\nprint("ok")'
  pass "platform POST /api/deployments/${TEMP_DEPLOYMENT_ID}/assignments"

  platform_http GET "${PLATFORM_BASE_URL}/api/platform/users/access-overview?deploymentId=${TEMP_DEPLOYMENT_ID}"
  assert_status 200 "temp deployment user access overview"
  json_assert "temp deployment user access overview" $'items = data or []\nassert isinstance(items, list)\nmatching = [item for item in items if (item or {}).get("id") == "'"${PLATFORM_CURRENT_USER_ID}"'"]\nassert matching, items\nselected = (matching[0] or {}).get("selectedDeploymentAssignment") or {}\nassert selected.get("deploymentId") == "'"${TEMP_DEPLOYMENT_ID}"'", selected\nassert selected.get("assignmentRole") == "DEPLOYMENT_ADMIN", selected\nprint("ok")'
  pass "platform GET /api/platform/users/access-overview?deploymentId=${TEMP_DEPLOYMENT_ID}"

  platform_http GET "${PLATFORM_BASE_URL}/api/deployments/${TEMP_DEPLOYMENT_ID}/assignments"
  assert_status 200 "temp deployment assignments"
  json_assert "temp deployment assignments" $'items = data or []\nassert isinstance(items, list)\nassert len(items) >= 1, items\nmatching = [item for item in items if (item or {}).get("userId") == "'"${PLATFORM_CURRENT_USER_ID}"'"]\nassert matching, items\nfor item in matching:\n  assert (item or {}).get("deploymentId") == "'"${TEMP_DEPLOYMENT_ID}"'", item\n  assert (item or {}).get("assignmentRole") == "DEPLOYMENT_ADMIN", item\nprint("ok")'
  pass "platform GET /api/deployments/${TEMP_DEPLOYMENT_ID}/assignments"

  if [[ "${VERIFY_DEPLOYMENT_OVERRIDE_SMOKE}" == "true" ]]; then
    echo ""
    echo "== Deployment Override Smoke =="
    TEMP_OVERRIDE_SECRET_NAME="DEPLOYMENT_OVERRIDE_OPENAI_${TEMP_DEPLOYMENT_ID//-/_}"
    TEMP_OVERRIDE_SECRET_NAME="$(printf '%s' "${TEMP_OVERRIDE_SECRET_NAME}" | tr '[:lower:]' '[:upper:]')"

    override_secret_payload="$(cat <<EOF
{"secretPurpose":"OPENAI_API_KEY","value":"override-${TEMP_DEPLOYMENT_ID}","deploymentId":"${TEMP_DEPLOYMENT_ID}","cleanupPolicy":"DELETE_ON_HARD_DELETE"}
EOF
)"
    platform_http PUT "${PLATFORM_BASE_URL}/api/platform/secrets/deployment-overrides/${TEMP_OVERRIDE_SECRET_NAME}" "${override_secret_payload}"
    assert_status 200 "create deployment override secret"
    json_assert "create deployment override secret" $'assert (data or {}).get("name") == "'"${TEMP_OVERRIDE_SECRET_NAME}"'"\nassert (data or {}).get("deploymentId") == "'"${TEMP_DEPLOYMENT_ID}"'"\nassert (data or {}).get("secretPurpose") == "OPENAI_API_KEY"\nprint("ok")'
    pass "platform PUT /api/platform/secrets/deployment-overrides/${TEMP_OVERRIDE_SECRET_NAME}"

    bind_override_payload="$(cat <<EOF
{"secretPurpose":"OPENAI_API_KEY","bindingMode":"REQUIRE_OVERRIDE","secretName":"${TEMP_OVERRIDE_SECRET_NAME}"}
EOF
)"
    platform_http PUT "${PLATFORM_BASE_URL}/api/deployments/${TEMP_DEPLOYMENT_ID}/provider-secret-bindings" "${bind_override_payload}"
    assert_status 200 "bind deployment override"
    json_assert "bind deployment override" $'assert (data or {}).get("deploymentId") == "'"${TEMP_DEPLOYMENT_ID}"'"\nassert (data or {}).get("secretPurpose") == "OPENAI_API_KEY"\nassert (data or {}).get("bindingMode") == "REQUIRE_OVERRIDE"\nresolution = (data or {}).get("effectiveResolution") or {}\nassert resolution.get("resolved") is True, resolution\nassert resolution.get("scopeType") == "DEPLOYMENT_OVERRIDE", resolution\nprint("ok")'
    pass "platform PUT /api/deployments/${TEMP_DEPLOYMENT_ID}/provider-secret-bindings"

    platform_http GET "${PLATFORM_BASE_URL}/api/deployments/${TEMP_DEPLOYMENT_ID}/provider-secret-bindings"
    assert_status 200 "list deployment override bindings"
    json_assert "list deployment override bindings" $'bindings = (data or {}).get("bindings") or []\nassert isinstance(bindings, list)\nmatching = [item for item in bindings if (item or {}).get("secretPurpose") == "OPENAI_API_KEY"]\nassert len(matching) == 1, bindings\nassert (matching[0] or {}).get("secretName") == "'"${TEMP_OVERRIDE_SECRET_NAME}"'", matching[0]\nprint("ok")'
    pass "platform GET /api/deployments/${TEMP_DEPLOYMENT_ID}/provider-secret-bindings"

    platform_http DELETE "${PLATFORM_BASE_URL}/api/deployments/${TEMP_DEPLOYMENT_ID}/provider-secret-bindings/OPENAI_API_KEY"
    assert_status 204 "clear deployment override binding"
    pass "platform DELETE /api/deployments/${TEMP_DEPLOYMENT_ID}/provider-secret-bindings/OPENAI_API_KEY"

    platform_http GET "${PLATFORM_BASE_URL}/api/deployments/${TEMP_DEPLOYMENT_ID}/provider-secret-bindings"
    assert_status 200 "list deployment override bindings after clear"
    json_assert "list deployment override bindings after clear" $'bindings = (data or {}).get("bindings") or []\nassert isinstance(bindings, list)\nmatching = [item for item in bindings if (item or {}).get("secretPurpose") == "OPENAI_API_KEY"]\nassert len(matching) == 0, bindings\nprint("ok")'
    pass "platform GET /api/deployments/${TEMP_DEPLOYMENT_ID}/provider-secret-bindings after clear"

    platform_http PUT "${PLATFORM_BASE_URL}/api/deployments/${TEMP_DEPLOYMENT_ID}/provider-secret-bindings" "${bind_override_payload}"
    assert_status 200 "rebind deployment override"
    json_assert "rebind deployment override" $'assert (data or {}).get("secretPurpose") == "OPENAI_API_KEY"\nresolution = (data or {}).get("effectiveResolution") or {}\nassert resolution.get("resolved") is True, resolution\nassert resolution.get("reasonCode") == "DEPLOYMENT_OVERRIDE_PRESENT", resolution\nprint("ok")'
    pass "platform PUT /api/deployments/${TEMP_DEPLOYMENT_ID}/provider-secret-bindings rebind"

    platform_http GET "${PLATFORM_BASE_URL}/api/deployments/${TEMP_DEPLOYMENT_ID}/secret-usage"
    assert_status 200 "deployment override secret usage"
    json_assert "deployment override secret usage" $'items = (data or {}).get("secrets") or []\nmatching = [item for item in items if (item or {}).get("secretName") == "OPENAI_API_KEY"]\nassert matching, items\nresolution = (matching[0] or {}).get("effectiveResolution") or {}\nassert resolution.get("reasonCode") == "DEPLOYMENT_OVERRIDE_PRESENT", resolution\nassert resolution.get("scopeType") == "DEPLOYMENT_OVERRIDE", resolution\nprint("ok")'
    pass "platform GET /api/deployments/${TEMP_DEPLOYMENT_ID}/secret-usage"
  fi

  platform_http POST "${PLATFORM_BASE_URL}/api/deployments/${TEMP_DEPLOYMENT_ID}/archive"
  assert_status 200 "archive temp deployment"
  json_assert "archive temp deployment" $'assert (data or {}).get("id") == "'"${TEMP_DEPLOYMENT_ID}"'"\nprint("ok")'
  pass "platform POST /api/deployments/${TEMP_DEPLOYMENT_ID}/archive"

  if [[ "${VERIFY_DEPLOYMENT_OVERRIDE_SMOKE}" == "true" ]]; then
    delete_payload='{"hardDelete":true,"reason":"Live admin regression deployment override cleanup smoke"}'
  else
    delete_payload='{"hardDelete":false,"reason":"Live admin regression async delete smoke"}'
  fi
  platform_http DELETE "${PLATFORM_BASE_URL}/api/deployments/${TEMP_DEPLOYMENT_ID}" "${delete_payload}"
  assert_status 202 "queue temp deployment delete"
  json_assert "queue temp deployment delete" $'assert (data or {}).get("deploymentId") == "'"${TEMP_DEPLOYMENT_ID}"'"\nassert (data or {}).get("status") in {"QUEUED", "RUNNING", "SUCCEEDED"}\nassert bool((data or {}).get("id"))\nprint("ok")'
  delete_operation_id="$(PARSE_BODY="${HTTP_BODY}" python3 - <<'PY'
import json
import os
d = json.loads(os.environ.get("PARSE_BODY", "") or "{}")
print((d.get("id")) or "")
PY
)"
  pass "platform DELETE /api/deployments/${TEMP_DEPLOYMENT_ID}"

  poll_until \
    "deletion notification ${delete_operation_id}" \
    "${DELETE_SMOKE_TIMEOUT_ATTEMPTS}" \
    "${DELETE_SMOKE_TIMEOUT_SLEEP_SECONDS}" \
    "platform_http GET \"${PLATFORM_BASE_URL}/api/platform/notifications/deployment-deletions/${delete_operation_id}\"" \
    $'status = (data or {}).get("status") or ""\nassert status in {"QUEUED", "RUNNING", "SUCCEEDED", "FAILED"}\nraise SystemExit(0 if status == "SUCCEEDED" else 1)'

  json_assert "deletion notification success" $'assert (data or {}).get("deploymentId") == "'"${TEMP_DEPLOYMENT_ID}"'"\nassert (data or {}).get("status") == "SUCCEEDED"\nassert bool((data or {}).get("statusMessage"))\nrequest_details = (data or {}).get("requestDetails") or {}\nresult_details = (data or {}).get("resultDetails") or {}\nassert request_details.get("deploymentId") == "'"${TEMP_DEPLOYMENT_ID}"'", request_details\nassert bool(result_details.get("completedAt")), result_details\nprint("ok")'
  pass "platform GET /api/platform/notifications/deployment-deletions/${delete_operation_id}"

  if [[ "${VERIFY_DEPLOYMENT_OVERRIDE_SMOKE}" == "true" ]]; then
    json_assert "deployment override cleanup result" $'result_details = (data or {}).get("resultDetails") or {}\ncleanup = result_details.get("providerSecretOverrideCleanup") or {}\nassert "'"${TEMP_OVERRIDE_SECRET_NAME}"'" in (cleanup.get("deletedSecretNames") or []), cleanup\nprint("ok")'
    pass "deployment override cleanup details recorded"
  fi

  platform_http GET "${PLATFORM_BASE_URL}/api/deployments?includeArchived=true"
  assert_status 200 "list deployments after delete"
  json_assert "list deployments after delete" $'items = data or []\nids = {item.get("id") for item in items}\nassert "'"${TEMP_DEPLOYMENT_ID}"'" not in ids, ids\nprint("ok")'
  pass "platform GET /api/deployments?includeArchived=true"

  if [[ "${VERIFY_DEPLOYMENT_OVERRIDE_SMOKE}" == "true" ]]; then
    platform_http GET "${PLATFORM_BASE_URL}/api/platform/secrets/deployment-overrides"
    assert_status 200 "list deployment override secrets after delete"
    json_assert "list deployment override secrets after delete" $'items = data or []\nids = {item.get("name") for item in items}\nassert "'"${TEMP_OVERRIDE_SECRET_NAME}"'" not in ids, ids\nprint("ok")'
    pass "platform GET /api/platform/secrets/deployment-overrides"
    TEMP_OVERRIDE_SECRET_NAME=""
  fi

  TEMP_DEPLOYMENT_CLEANED_UP="true"
  TEMP_DEPLOYMENT_ID=""
fi

if [[ "${VERIFY_INFERENCE_SERVICE_UI}" == "true" ]]; then
  verify_inference_service_ui
fi

if [[ "${VERIFY_INFERENCE_SERVICE_ADMIN_READONLY}" == "true" ]]; then
  verify_inference_service_admin
fi

echo ""
pass "platform admin live regression"
