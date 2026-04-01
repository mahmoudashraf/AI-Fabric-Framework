#!/usr/bin/env bash
set -euo pipefail

# End-to-end verification script for vector-backed AI Fabric deployments.
#
# Verifies:
# - REST connector and runtime health
# - connector admin and runtime admin endpoints
# - data-sync vector spaces
# - runtime indexing overview for the configured vector backend
# - live upsert/delete roundtrip through the deployed REST connector
# - optional platform release/workspace/verification alignment
#
# Minimal usage:
#   REST_CONNECTOR_BASE_URL="https://<rest>.up.railway.app" \
#   RUNTIME_BASE_URL="https://<runtime>.up.railway.app" \
#   API_KEY="test" \
#   RUNTIME_ADMIN_API_KEY="test" \
#   EXPECTED_VECTOR_SPACES="product" \
#   ./scripts/verify-vector-deployment.sh
#
# With platform verification using session login:
#   PLATFORM_BASE_URL="https://<platform>.up.railway.app" \
#   PLATFORM_DEPLOYMENT_ID="dep-12345678" \
#   PLATFORM_LOGIN_EMAIL="admin@example.com" \
#   PLATFORM_LOGIN_PASSWORD="admin" \
#   ./scripts/verify-vector-deployment.sh

REST_CONNECTOR_BASE_URL="${REST_CONNECTOR_BASE_URL:-}"
RUNTIME_BASE_URL="${RUNTIME_BASE_URL:-}"

API_KEY_HEADER="${API_KEY_HEADER:-X-AIFABRIC-API-KEY}"
API_KEY="${API_KEY:-}"

RUNTIME_ADMIN_API_KEY_HEADER="${RUNTIME_ADMIN_API_KEY_HEADER:-X-ADMIN-API-KEY}"
RUNTIME_ADMIN_API_KEY="${RUNTIME_ADMIN_API_KEY:-}"

CONNECTOR_ADMIN_API_KEY_HEADER="${CONNECTOR_ADMIN_API_KEY_HEADER:-${RUNTIME_ADMIN_API_KEY_HEADER}}"
CONNECTOR_ADMIN_API_KEY="${CONNECTOR_ADMIN_API_KEY:-${RUNTIME_ADMIN_API_KEY:-}}"

PLATFORM_BASE_URL="${PLATFORM_BASE_URL:-${PLATFORM_PUBLIC_BASE_URL:-}}"
PLATFORM_DEPLOYMENT_ID="${PLATFORM_DEPLOYMENT_ID:-}"
PLATFORM_API_KEY_HEADER="${PLATFORM_API_KEY_HEADER:-X-PLATFORM-API-KEY}"
PLATFORM_API_KEY="${PLATFORM_API_KEY:-}"
PLATFORM_COOKIE="${PLATFORM_COOKIE:-}"
PLATFORM_LOGIN_EMAIL="${PLATFORM_LOGIN_EMAIL:-}"
PLATFORM_LOGIN_PASSWORD="${PLATFORM_LOGIN_PASSWORD:-}"
PLATFORM_EXPECT_RELEASE_STATUS="${PLATFORM_EXPECT_RELEASE_STATUS:-APPLIED_VERIFIED}"
PLATFORM_EXPECT_VERIFICATION_STATUS="${PLATFORM_EXPECT_VERIFICATION_STATUS:-PASSED}"

EXPECTED_VECTOR_SPACES="${EXPECTED_VECTOR_SPACES:-product}"
TEST_VECTOR_SPACE="${TEST_VECTOR_SPACE:-${EXPECTED_VECTOR_SPACES%%,*}}"
TEST_RECORD_ID="${TEST_RECORD_ID:-VECTOR-VERIFY-$(date +%s)}"
TEST_CONTENT="${TEST_CONTENT:-Created by verify-vector-deployment.sh for vector database wiring checks.}"
EXPECTED_VECTOR_DB="${EXPECTED_VECTOR_DB:-}"

RUN_PLATFORM_CHECKS="false"
if [[ -n "${PLATFORM_BASE_URL}" || -n "${PLATFORM_DEPLOYMENT_ID}" ]]; then
  if [[ -z "${PLATFORM_BASE_URL}" || -z "${PLATFORM_DEPLOYMENT_ID}" ]]; then
    echo "Invalid platform verification configuration."
    echo "Set both PLATFORM_BASE_URL and PLATFORM_DEPLOYMENT_ID together."
    exit 2
  fi
  RUN_PLATFORM_CHECKS="true"
fi

if [[ -z "${REST_CONNECTOR_BASE_URL}" || -z "${RUNTIME_BASE_URL}" ]]; then
  echo "Missing required env vars."
  echo "Set REST_CONNECTOR_BASE_URL and RUNTIME_BASE_URL."
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

REST_CONNECTOR_BASE_URL="$(trim_slash "${REST_CONNECTOR_BASE_URL}")"
RUNTIME_BASE_URL="$(trim_slash "${RUNTIME_BASE_URL}")"
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

connector_http() {
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
  if [[ -n "${API_KEY}" ]]; then
    headers+=("-H" "${API_KEY_HEADER}: ${API_KEY}")
  fi
  if [[ -n "${CONNECTOR_ADMIN_API_KEY}" ]]; then
    headers+=("-H" "${CONNECTOR_ADMIN_API_KEY_HEADER}: ${CONNECTOR_ADMIN_API_KEY}")
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
  if [[ -n "${RUNTIME_ADMIN_API_KEY}" ]]; then
    headers+=("-H" "${RUNTIME_ADMIN_API_KEY_HEADER}: ${RUNTIME_ADMIN_API_KEY}")
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

  local curl_args=()
  if [[ -s "${PLATFORM_COOKIE_JAR}" ]]; then
    curl_args+=("-b" "${PLATFORM_COOKIE_JAR}" "-c" "${PLATFORM_COOKIE_JAR}")
  fi

  local status
  if [[ -n "${body}" ]]; then
    status="$(curl -sS -o "${tmp}" -w "%{http_code}" -X "${method}" "${headers[@]}" "${curl_args[@]}" "$@" --data "${body}" "${url}" || true)"
  else
    status="$(curl -sS -o "${tmp}" -w "%{http_code}" -X "${method}" "${headers[@]}" "${curl_args[@]}" "$@" "${url}" || true)"
  fi

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
  local status
  status="$(
    curl -sS -o "${tmp}" -w "%{http_code}" -c "${PLATFORM_COOKIE_JAR}" \
      -H "Content-Type: application/json" \
      -d "{\"email\":\"${PLATFORM_LOGIN_EMAIL}\",\"password\":\"${PLATFORM_LOGIN_PASSWORD}\"}" \
      "${PLATFORM_BASE_URL}/api/platform/auth/login" || true
  )"
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
    print(f"{label}: invalid JSON: {exc}")
    print(raw)
    raise SystemExit(2)

namespace = {"data": data}
exec(os.environ["ASSERT_PY"].replace("\\n", "\n"), namespace, namespace)
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

echo "REST connector: ${REST_CONNECTOR_BASE_URL}"
echo "Runtime: ${RUNTIME_BASE_URL}"
echo "Expected vector spaces: ${EXPECTED_VECTOR_SPACES}"
echo "Test vector space: ${TEST_VECTOR_SPACE}"
if [[ -n "${EXPECTED_VECTOR_DB}" ]]; then
  echo "Expected vector DB: ${EXPECTED_VECTOR_DB}"
fi
if [[ "${RUN_PLATFORM_CHECKS}" == "true" ]]; then
  echo "Platform: ${PLATFORM_BASE_URL}"
  echo "Platform deployment: ${PLATFORM_DEPLOYMENT_ID}"
fi

platform_login

echo ""
echo "== Health =="
connector_http GET "${REST_CONNECTOR_BASE_URL}/actuator/health"
assert_status 200 "rest connector health"
json_assert "rest connector health" $'assert (data or {}).get("status") == "UP"\nprint("ok")'
pass "rest connector /actuator/health"

runtime_http GET "${RUNTIME_BASE_URL}/actuator/health"
assert_status 200 "runtime health"
json_assert "runtime health" $'assert (data or {}).get("status") == "UP"\nprint("ok")'
pass "runtime /actuator/health"

echo ""
echo "== Connector and Runtime Admin =="
connector_http GET "${REST_CONNECTOR_BASE_URL}/api/admin/overview"
assert_status 200 "rest connector admin overview"
json_assert "rest connector admin overview" $'assert (data or {}).get("success") is True\nprint("ok")'
pass "rest connector GET /api/admin/overview"

connector_http GET "${REST_CONNECTOR_BASE_URL}/api/ai/data-sync/vector-spaces"
assert_status 200 "rest connector vector spaces"
json_assert "rest connector vector spaces" $'spaces = set((data or {}).get("vectorSpaces") or [])\nfor req in [item.strip() for item in "'"${EXPECTED_VECTOR_SPACES}"'".split(",") if item.strip()]:\n  assert req in spaces, spaces\nprint("ok")'
pass "rest connector GET /api/ai/data-sync/vector-spaces"

runtime_http GET "${RUNTIME_BASE_URL}/api/admin/overview"
assert_status 200 "runtime admin overview"
json_assert "runtime admin overview" $'assert (data or {}).get("success") is True\nentity_types = set((data or {}).get("supportedEntityTypes") or [])\nfor req in [item.strip() for item in "'"${EXPECTED_VECTOR_SPACES}"'".split(",") if item.strip()]:\n  assert req in entity_types, entity_types\nassert bool((data or {}).get("entityConfigLocation"))\nassert bool((data or {}).get("promptConfigLocation"))\nprint("ok")'
RUNTIME_ADMIN_OVERVIEW_BODY="${HTTP_BODY}"
pass "runtime GET /api/admin/overview"

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
PLATFORM_LIVE_ENTITY_ARTIFACT_URL=""
PLATFORM_LIVE_PROMPT_ARTIFACT_URL=""
PLATFORM_LIVE_RUNTIME_URL=""
PLATFORM_LIVE_CONNECTOR_URL=""

if [[ "${RUN_PLATFORM_CHECKS}" == "true" ]]; then
  echo ""
  echo "== Platform Deployment State =="
  platform_http GET "${PLATFORM_BASE_URL}/api/deployments/${PLATFORM_DEPLOYMENT_ID}/workspace"
  assert_status 200 "platform workspace"
  json_assert "platform workspace" $'assert (data or {}).get("deployment", {}).get("id") == "'"${PLATFORM_DEPLOYMENT_ID}"'"\nassert (data or {}).get("access", {}).get("canOperate") is True\nlifecycle = (data or {}).get("lifecycle") or {}\nassert lifecycle.get("hasPublishedVersion") is True\nprint("ok")'
  pass "platform GET /api/deployments/${PLATFORM_DEPLOYMENT_ID}/workspace"

  platform_http GET "${PLATFORM_BASE_URL}/api/deployments/${PLATFORM_DEPLOYMENT_ID}/releases"
  assert_status 200 "platform releases"
  json_assert "platform releases" $'items = data or []\nassert len(items) > 0\nlatest = items[0]\nassert latest.get("status") == "'"${PLATFORM_EXPECT_RELEASE_STATUS}"'", latest\nassert latest.get("verificationStatus") == "'"${PLATFORM_EXPECT_VERIFICATION_STATUS}"'", latest\nprint("ok")'
  PLATFORM_RELEASE_BODY="${HTTP_BODY}"
  PLATFORM_LIVE_ENTITY_ARTIFACT_URL="$(PARSE_BODY="${PLATFORM_RELEASE_BODY}" python3 - <<'PY'
import json
import os
items = json.loads(os.environ.get("PARSE_BODY", "") or "[]")
latest = items[0] if items else {}
print((((latest.get("provisioningDetails") or {}).get("artifactUrls") or {}).get("entities")) or "")
PY
)"
  PLATFORM_LIVE_PROMPT_ARTIFACT_URL="$(PARSE_BODY="${PLATFORM_RELEASE_BODY}" python3 - <<'PY'
import json
import os
items = json.loads(os.environ.get("PARSE_BODY", "") or "[]")
latest = items[0] if items else {}
print((((latest.get("provisioningDetails") or {}).get("artifactUrls") or {}).get("prompts")) or "")
PY
)"
  PLATFORM_LIVE_RUNTIME_URL="$(PARSE_BODY="${PLATFORM_RELEASE_BODY}" python3 - <<'PY'
import json
import os
items = json.loads(os.environ.get("PARSE_BODY", "") or "[]")
latest = items[0] if items else {}
services = (((latest.get("provisioningDetails") or {}).get("railway") or {}).get("services") or {})
print(((services.get("runtime") or {}).get("baseUrl")) or "")
PY
)"
  PLATFORM_LIVE_CONNECTOR_URL="$(PARSE_BODY="${PLATFORM_RELEASE_BODY}" python3 - <<'PY'
import json
import os
items = json.loads(os.environ.get("PARSE_BODY", "") or "[]")
latest = items[0] if items else {}
services = (((latest.get("provisioningDetails") or {}).get("railway") or {}).get("services") or {})
print(((services.get("restConnector") or {}).get("baseUrl")) or "")
PY
)"
  pass "platform GET /api/deployments/${PLATFORM_DEPLOYMENT_ID}/releases"

  if [[ -n "${PLATFORM_LIVE_RUNTIME_URL}" ]]; then
    [[ "${PLATFORM_LIVE_RUNTIME_URL}" == "${RUNTIME_BASE_URL}" ]] || fail "platform runtime base URL does not match supplied RUNTIME_BASE_URL"
    pass "platform runtime base URL matches runtime input"
  fi
  if [[ -n "${PLATFORM_LIVE_CONNECTOR_URL}" ]]; then
    [[ "${PLATFORM_LIVE_CONNECTOR_URL}" == "${REST_CONNECTOR_BASE_URL}" ]] || fail "platform connector base URL does not match supplied REST_CONNECTOR_BASE_URL"
    pass "platform connector base URL matches connector input"
  fi

  platform_http GET "${PLATFORM_BASE_URL}/api/deployments/${PLATFORM_DEPLOYMENT_ID}/verification-runs"
  assert_status 200 "platform verification runs"
  json_assert "platform verification runs" $'items = data or []\nassert len(items) > 0\nlatest = items[0]\nassert latest.get("status") == "'"${PLATFORM_EXPECT_VERIFICATION_STATUS}"'", latest\nprint("ok")'
  pass "platform GET /api/deployments/${PLATFORM_DEPLOYMENT_ID}/verification-runs"

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
    "userId": "user-123",
    "sessionId": "vector-verify",
    "requestId": "verify-upsert-${TEST_RECORD_ID}",
    "metadata": {
      "origin": "codex"
    }
  }
}
JSON
)"

vector_upsert_ok="false"
for attempt in $(seq 1 10); do
  connector_http POST "${REST_CONNECTOR_BASE_URL}/api/ai/data-sync/upsert" "${VECTOR_UPSERT_BODY}"
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
pass "rest connector POST /api/ai/data-sync/upsert"

poll_until "vector indexed" 20 2 \
  "runtime_http GET \"${RUNTIME_BASE_URL}/api/admin/indexing/overview\"" \
  $'counts = (data or {}).get("countsByEntityType") or {}\ncur = int(counts.get("'"${TEST_VECTOR_SPACE}"'") or 0)\nwant = int('"${INITIAL_COUNT}"') + 1\nraise SystemExit(0 if cur >= want else 1)\n'
pass "runtime indexing count increased"

connector_http POST "${REST_CONNECTOR_BASE_URL}/api/ai/data-sync/delete" "$(cat <<JSON
{
  "vectorSpace": "${TEST_VECTOR_SPACE}",
  "id": "${TEST_RECORD_ID}",
  "trace": {
    "userId": "user-123",
    "sessionId": "vector-verify",
    "requestId": "verify-delete-${TEST_RECORD_ID}",
    "metadata": {
      "origin": "codex"
    }
  }
}
JSON
)"
assert_status 200 "vector delete"
json_assert "vector delete" $'assert (data or {}).get("success") is True\nassert (data or {}).get("vectorSpace") == "'"${TEST_VECTOR_SPACE}"'"\nassert (data or {}).get("id") == "'"${TEST_RECORD_ID}"'"\nprint("ok")'
pass "rest connector POST /api/ai/data-sync/delete"

poll_until "vector deleted" 20 2 \
  "runtime_http GET \"${RUNTIME_BASE_URL}/api/admin/indexing/overview\"" \
  $'counts = (data or {}).get("countsByEntityType") or {}\ncur = int(counts.get("'"${TEST_VECTOR_SPACE}"'") or 0)\nwant = int('"${INITIAL_COUNT}"')\nraise SystemExit(0 if cur == want else 1)\n'
pass "runtime indexing count returned to initial value"

if [[ "${RUN_PLATFORM_CHECKS}" == "true" ]]; then
  platform_http GET "${PLATFORM_BASE_URL}/api/deployments/${PLATFORM_DEPLOYMENT_ID}/releases"
  assert_status 200 "platform releases (final)"
  json_assert "platform releases (final)" $'items = data or []\nassert len(items) > 0\nlatest = items[0]\nassert latest.get("status") == "'"${PLATFORM_EXPECT_RELEASE_STATUS}"'", latest\nassert latest.get("verificationStatus") == "'"${PLATFORM_EXPECT_VERIFICATION_STATUS}"'", latest\nprint("ok")'
  pass "platform release remains applied and verified"
fi

echo ""
pass "All checks completed."
