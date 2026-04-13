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
# - optional manual canonical rollout mutation
#
# Usage:
#   PLATFORM_BASE_URL="https://<platform>.up.railway.app" \
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
ADMIN_TARGET_DEPLOYMENT_ID="${ADMIN_TARGET_DEPLOYMENT_ID:-}"

PLATFORM_API_KEY_HEADER="${PLATFORM_API_KEY_HEADER:-X-PLATFORM-API-KEY}"
PLATFORM_API_KEY="${PLATFORM_API_KEY:-}"
PLATFORM_COOKIE="${PLATFORM_COOKIE:-}"
PLATFORM_LOGIN_EMAIL="${PLATFORM_LOGIN_EMAIL:-}"
PLATFORM_LOGIN_PASSWORD="${PLATFORM_LOGIN_PASSWORD:-}"

VERIFY_ASYNC_DELETE_SMOKE="${VERIFY_ASYNC_DELETE_SMOKE:-true}"
VERIFY_DEPLOYMENT_OVERRIDE_SMOKE="${VERIFY_DEPLOYMENT_OVERRIDE_SMOKE:-true}"
VERIFY_CANONICAL_ROLLOUT_READONLY="${VERIFY_CANONICAL_ROLLOUT_READONLY:-true}"
VERIFY_CANONICAL_ROLLOUT_MUTATION="${VERIFY_CANONICAL_ROLLOUT_MUTATION:-false}"
CANONICAL_ROLLOUT_KEYS="${CANONICAL_ROLLOUT_KEYS:-}"

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

PLATFORM_API_KEY="$(resolve_secret_value PLATFORM_API_KEY)"
PLATFORM_COOKIE="$(resolve_secret_value PLATFORM_COOKIE)"
PLATFORM_LOGIN_EMAIL="$(resolve_secret_value PLATFORM_LOGIN_EMAIL)"
PLATFORM_LOGIN_PASSWORD="$(resolve_secret_value PLATFORM_LOGIN_PASSWORD)"

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

PLATFORM_BASE_URL="$(trim_slash "${PLATFORM_BASE_URL}")"
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

echo ""
pass "platform admin live regression"
