#!/usr/bin/env bash
set -euo pipefail

# Deployment verification script for the ecommerce demo and platform-operated Railway rollout:
# - Ecommerce Store (domain API)
# - Generic REST Connector (actions + optional runtime proxy)
# - AI Fabric Runtime
# - AI Enablement Platform deployment control plane
#
# Service verification usage (read-only checks + action routing smoke):
#   STORE_BASE_URL="https://<ecommerce-store>.up.railway.app" \
#   REST_CONNECTOR_BASE_URL="https://<rest-connector>.up.railway.app" \
#   RUNTIME_BASE_URL="https://<runtime>.up.railway.app" \
#   API_KEY="test-key" \
#   ./scripts/verify-ecommerce-deployment.sh
#
# Optional write checks (create/delete product + verify indexing counts):
#   VERIFY_WRITE=true ./scripts/verify-ecommerce-deployment.sh
#
# Optional platform-operated deployment verification:
#   PLATFORM_BASE_URL="https://<platform-backend>.up.railway.app" \
#   PLATFORM_DEPLOYMENT_ID="dep-12345678" \
#   PLATFORM_API_KEY="..." \
#   ./scripts/verify-ecommerce-deployment.sh
#
# Full end-to-end usage:
#   STORE_BASE_URL="https://<ecommerce-store>.up.railway.app" \
#   REST_CONNECTOR_BASE_URL="https://<rest-connector>.up.railway.app" \
#   RUNTIME_BASE_URL="https://<runtime>.up.railway.app" \
#   API_KEY="test-key" \
#   RUNTIME_ADMIN_API_KEY="test" \
#   PLATFORM_BASE_URL="https://<platform-backend>.up.railway.app" \
#   PLATFORM_DEPLOYMENT_ID="dep-12345678" \
#   PLATFORM_API_KEY="..." \
#   ./scripts/verify-ecommerce-deployment.sh
#
# Notes:
# - If your REST connector inbound auth is enabled, set API_KEY (default header: X-AIFABRIC-API-KEY).
# - Runtime admin endpoints require RUNTIME_ADMIN_API_KEY when app.admin.api-key is configured.
# - Platform endpoints require either PLATFORM_API_KEY (default header: X-PLATFORM-API-KEY) or PLATFORM_COOKIE when platform auth is enabled.

STORE_BASE_URL="${STORE_BASE_URL:-${ECOMMERCE_STORE_BASE_URL:-}}"
REST_CONNECTOR_BASE_URL="${REST_CONNECTOR_BASE_URL:-}"
RUNTIME_BASE_URL="${RUNTIME_BASE_URL:-}"

API_KEY_HEADER="${API_KEY_HEADER:-X-AIFABRIC-API-KEY}"
API_KEY="${API_KEY:-}"

RUNTIME_ADMIN_API_KEY_HEADER="${RUNTIME_ADMIN_API_KEY_HEADER:-X-ADMIN-API-KEY}"
RUNTIME_ADMIN_API_KEY="${RUNTIME_ADMIN_API_KEY:-}"

PLATFORM_BASE_URL="${PLATFORM_BASE_URL:-${PLATFORM_PUBLIC_BASE_URL:-}}"
PLATFORM_DEPLOYMENT_ID="${PLATFORM_DEPLOYMENT_ID:-}"
PLATFORM_API_KEY_HEADER="${PLATFORM_API_KEY_HEADER:-X-PLATFORM-API-KEY}"
PLATFORM_API_KEY="${PLATFORM_API_KEY:-}"
PLATFORM_COOKIE="${PLATFORM_COOKIE:-}"
PLATFORM_LOGIN_EMAIL="${PLATFORM_LOGIN_EMAIL:-}"
PLATFORM_LOGIN_PASSWORD="${PLATFORM_LOGIN_PASSWORD:-}"
PLATFORM_EXPECT_PREFLIGHT_READY="${PLATFORM_EXPECT_PREFLIGHT_READY:-true}"
PLATFORM_EXPECT_RELEASE_ID="${PLATFORM_EXPECT_RELEASE_ID:-}"
PLATFORM_EXPECT_VERSION_ID="${PLATFORM_EXPECT_VERSION_ID:-}"
PLATFORM_EXPECT_RELEASE_STATUS="${PLATFORM_EXPECT_RELEASE_STATUS:-APPLIED_VERIFIED}"
PLATFORM_EXPECT_VERIFICATION_STATUS="${PLATFORM_EXPECT_VERIFICATION_STATUS:-PASSED}"

CONNECTOR_ADMIN_API_KEY_HEADER="${CONNECTOR_ADMIN_API_KEY_HEADER:-${RUNTIME_ADMIN_API_KEY_HEADER:-X-ADMIN-API-KEY}}"
CONNECTOR_ADMIN_API_KEY="${CONNECTOR_ADMIN_API_KEY:-${RUNTIME_ADMIN_API_KEY:-}}"

VERIFY_WRITE="${VERIFY_WRITE:-false}"

RUN_SERVICE_CHECKS="false"
RUN_PLATFORM_CHECKS="false"

if [[ -n "${STORE_BASE_URL}" || -n "${REST_CONNECTOR_BASE_URL}" ]]; then
  if [[ -z "${STORE_BASE_URL}" || -z "${REST_CONNECTOR_BASE_URL}" ]]; then
    echo "Invalid service verification configuration."
    echo "Set both STORE_BASE_URL and REST_CONNECTOR_BASE_URL together."
    exit 2
  fi
  RUN_SERVICE_CHECKS="true"
fi

if [[ -n "${PLATFORM_BASE_URL}" || -n "${PLATFORM_DEPLOYMENT_ID}" ]]; then
  if [[ -z "${PLATFORM_BASE_URL}" || -z "${PLATFORM_DEPLOYMENT_ID}" ]]; then
    echo "Invalid platform verification configuration."
    echo "Set both PLATFORM_BASE_URL and PLATFORM_DEPLOYMENT_ID together."
    exit 2
  fi
  RUN_PLATFORM_CHECKS="true"
fi

if [[ "${RUN_SERVICE_CHECKS}" != "true" && "${RUN_PLATFORM_CHECKS}" != "true" ]]; then
  echo "Missing required env vars."
  echo "Set either:"
  echo "  STORE_BASE_URL and REST_CONNECTOR_BASE_URL"
  echo "or:"
  echo "  PLATFORM_BASE_URL and PLATFORM_DEPLOYMENT_ID"
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
require_cmd python3

trim_slash() {
  local url="$1"
  if [[ "${url}" == */ ]]; then
    echo "${url%/}"
  else
    echo "${url}"
  fi
}

STORE_BASE_URL="$(trim_slash "${STORE_BASE_URL}")"
REST_CONNECTOR_BASE_URL="$(trim_slash "${REST_CONNECTOR_BASE_URL}")"
if [[ -n "${RUNTIME_BASE_URL}" ]]; then
  RUNTIME_BASE_URL="$(trim_slash "${RUNTIME_BASE_URL}")"
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

http() {
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

  local status
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
  if [[ -z "${PLATFORM_LOGIN_EMAIL:-}" || -z "${PLATFORM_LOGIN_PASSWORD:-}" ]]; then
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
import json, os
label = os.environ["ASSERT_LABEL"]
raw = os.environ.get("ASSERT_BODY", "").strip()
try:
    data = json.loads(raw) if raw else None
except Exception as e:
    print(f"{label}: invalid JSON: {e}")
    print(raw)
    raise SystemExit(2)
namespace = {"data": data}
exec(os.environ["ASSERT_PY"].replace("\\n", "\n"), namespace, namespace)
PY
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
import json, os
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

echo "Store: ${STORE_BASE_URL}"
echo "REST connector: ${REST_CONNECTOR_BASE_URL}"
if [[ -n "${RUNTIME_BASE_URL}" ]]; then
  echo "Runtime: ${RUNTIME_BASE_URL}"
fi
if [[ -n "${PLATFORM_BASE_URL}" ]]; then
  echo "Platform: ${PLATFORM_BASE_URL}"
  echo "Platform deployment: ${PLATFORM_DEPLOYMENT_ID}"
fi

platform_login

PLATFORM_SOURCE_OF_TRUTH_BODY=""
PLATFORM_LIVE_PROMPT_ARTIFACT_URL=""
PLATFORM_LIVE_RUNTIME_URL=""
PLATFORM_LIVE_CONNECTOR_URL=""
PLATFORM_LIVE_RAILWAY_STATUS=""
PLATFORM_GENERATED_PROVISIONING_MODE=""
PLATFORM_LATEST_RELEASE_ID=""
PLATFORM_LATEST_VERIFICATION_RUN_ID=""

if [[ "${RUN_SERVICE_CHECKS}" == "true" ]]; then
  echo ""
  echo "== Health =="
  http GET "${STORE_BASE_URL}/actuator/health"
  assert_status 200 "store health"
  json_assert "store health" $'assert (data or {}).get("status") == "UP"\nprint("ok")'
  pass "store /actuator/health"

  http GET "${REST_CONNECTOR_BASE_URL}/actuator/health"
  assert_status 200 "rest connector health"
  json_assert "rest connector health" $'assert (data or {}).get("status") == "UP"\nprint("ok")'
  pass "rest connector /actuator/health"

  if [[ -n "${RUNTIME_BASE_URL}" ]]; then
    runtime_http GET "${RUNTIME_BASE_URL}/actuator/health"
    assert_status 200 "runtime health"
    json_assert "runtime health" $'assert (data or {}).get("status") == "UP"\nprint("ok")'
    pass "runtime /actuator/health"
  fi

  echo ""
  echo "== Store Admin Endpoints (non-destructive) =="
  http POST "${STORE_BASE_URL}/api/admin/demo/reset" '{"confirm": true, "clearConnectorData": false, "clearRuntimeVectors": false}'
  if [[ "${HTTP_STATUS}" == "401" ]]; then
    echo "WARN: store reset endpoint requires admin auth (set API_KEY / API_KEY_HEADER)."
  else
    assert_status 200 "store reset (skip)"
    json_assert "store reset (skip)" $'assert (data or {}).get("success") is True\nprint("ok")'
    pass "store POST /api/admin/demo/reset (skip mode)"
  fi

  http POST "${STORE_BASE_URL}/api/admin/demo/clear" '{"confirm": false}'
  if [[ "${HTTP_STATUS}" == "401" ]]; then
    echo "WARN: store clear endpoint requires admin auth (set API_KEY / API_KEY_HEADER)."
  else
    # This is a "presence" check; the endpoint should refuse without confirm=true.
    assert_status 400 "store clear (confirm required)"
    pass "store POST /api/admin/demo/clear exists (confirm required)"
  fi

  echo ""
  echo "== REST Connector Admin Overview =="
  http GET "${REST_CONNECTOR_BASE_URL}/api/admin/overview"
  assert_status 200 "rest connector admin overview"
  json_assert "rest connector admin overview" $'assert (data or {}).get("success") is True\nprint("ok")'
  pass "rest connector GET /api/admin/overview"

  http GET "${REST_CONNECTOR_BASE_URL}/api/admin/actions/overview"
  assert_status 200 "rest connector actions overview"
  json_assert "rest connector actions overview" $'assert (data or {}).get("success") is True\nassert int((data or {}).get("count") or 0) > 0\nprint("ok")'
  pass "rest connector GET /api/admin/actions/overview"

  echo ""
  echo "== Action Routing Smoke (read-only) =="
  http POST "${REST_CONNECTOR_BASE_URL}/actions/execute" "$(cat <<JSON
{
  "actionId": "list_products",
  "params": { "query": "laptop" },
  "idempotencyKey": "verify-list-products",
  "trace": {
    "requestId": "verify-list-products",
    "conversationId": "verify",
    "userId": "verify-user",
    "sessionId": "verify-session"
  }
}
JSON
)"
  assert_status 200 "actions execute list_products"
  json_assert "actions execute list_products" $'assert (data or {}).get("success") is True\nprint("ok")'
  pass "rest connector POST /actions/execute (list_products)"

  echo ""
  echo "== Runtime Proxy Checks (via REST connector if enabled) =="
  http GET "${REST_CONNECTOR_BASE_URL}/api/ai/data-sync/vector-spaces"
  if [[ "${HTTP_STATUS}" != "200" ]]; then
    if [[ -n "${RUNTIME_BASE_URL}" ]]; then
      echo "WARN: /api/ai/data-sync/vector-spaces via REST connector failed (HTTP ${HTTP_STATUS}); trying runtime directly."
      runtime_http GET "${RUNTIME_BASE_URL}/api/ai/data-sync/vector-spaces"
      assert_status 200 "runtime vector spaces"
      json_assert "runtime vector spaces" $'spaces = (data or {}).get("vectorSpaces") or []\nfor req in ["product","policy","review"]:\n  assert req in spaces\nprint("ok")'
      pass "runtime GET /api/ai/data-sync/vector-spaces"
    else
      echo "${HTTP_BODY}"
      fail "REST connector runtime proxy for data-sync appears disabled/unavailable (and RUNTIME_BASE_URL not set)"
    fi
  else
    json_assert "vector spaces (via rest connector)" $'spaces = (data or {}).get("vectorSpaces") or []\nfor req in ["product","policy","review"]:\n  assert req in spaces\nprint("ok")'
    pass "rest connector GET /api/ai/data-sync/vector-spaces"
  fi

  http GET "${REST_CONNECTOR_BASE_URL}/api/admin/indexing/overview"
  if [[ "${HTTP_STATUS}" != "200" ]]; then
    if [[ -n "${RUNTIME_BASE_URL}" ]]; then
      echo "WARN: /api/admin/indexing/overview via REST connector failed (HTTP ${HTTP_STATUS}); trying runtime directly."
      runtime_http GET "${RUNTIME_BASE_URL}/api/admin/indexing/overview"
      assert_status 200 "runtime indexing overview"
      json_assert "runtime indexing overview" $'assert (data or {}).get("success") is True\nprint("ok")'
      pass "runtime GET /api/admin/indexing/overview"
    else
      echo "${HTTP_BODY}"
      fail "REST connector runtime proxy for admin/indexing appears disabled/unavailable (and RUNTIME_BASE_URL not set)"
    fi
  else
    json_assert "indexing overview (via rest connector)" $'assert (data or {}).get("success") is True\nprint("ok")'
    pass "rest connector GET /api/admin/indexing/overview"
  fi

  if [[ -n "${RUNTIME_BASE_URL}" ]]; then
    echo ""
    echo "== Runtime Action Catalog =="
    runtime_http GET "${RUNTIME_BASE_URL}/api/admin/actions/overview"
    assert_status 200 "runtime actions overview"
    json_assert "runtime actions overview" $'assert (data or {}).get("success") is True\nassert int((data or {}).get("count") or 0) > 0\nprint("ok")'
    pass "runtime GET /api/admin/actions/overview"
  fi

  if [[ "${VERIFY_WRITE}" == "true" ]]; then
    echo ""
    echo "== Indexing Roundtrip (write) =="

    INDEXING_CMD="http GET \"${REST_CONNECTOR_BASE_URL}/api/admin/indexing/overview\""
    http GET "${REST_CONNECTOR_BASE_URL}/api/admin/indexing/overview"
    if [[ "${HTTP_STATUS}" != "200" ]]; then
      if [[ -n "${RUNTIME_BASE_URL}" ]]; then
        echo "WARN: /api/admin/indexing/overview via REST connector failed in write mode (HTTP ${HTTP_STATUS}); using runtime directly."
        runtime_http GET "${RUNTIME_BASE_URL}/api/admin/indexing/overview"
        assert_status 200 "runtime indexing overview (pre)"
        INDEXING_CMD="runtime_http GET \"${RUNTIME_BASE_URL}/api/admin/indexing/overview\""
      else
        echo "${HTTP_BODY}"
        fail "REST connector runtime proxy for admin/indexing appears disabled/unavailable in write mode (and RUNTIME_BASE_URL not set)"
      fi
    fi

    initial_product_count="$(PARSE_BODY="${HTTP_BODY}" python3 - <<'PY'
import json, os
d = json.loads(os.environ.get("PARSE_BODY", "") or "{}")
counts = (d.get("countsByEntityType") or {})
print(int(counts.get("product") or 0))
PY
)"

    sku="SKU-VERIFY-$(date +%s)"
    http POST "${STORE_BASE_URL}/api/products" "$(cat <<JSON
{
  "sku": "${sku}",
  "name": "Verify Product ${sku}",
  "description": "Created by verify-ecommerce-deployment.sh for wiring checks.",
  "category": "verification",
  "tags": "verification",
  "price": 1.00,
  "currency": "USD",
  "inStockQty": 1
}
JSON
)"
    assert_status 201 "create product"
    product_id="$(PARSE_BODY="${HTTP_BODY}" python3 - <<'PY'
import json, os
d = json.loads(os.environ.get("PARSE_BODY", "") or "{}")
pid = d.get("id")
if pid is None:
    raise SystemExit(2)
print(pid)
PY
)"
    pass "store POST /api/products (id=${product_id}, sku=${sku})"

    # Wait for product vector count to increase.
    poll_until "product indexed" 20 2 \
      "${INDEXING_CMD}" \
      $'counts = (data or {}).get(\"countsByEntityType\") or {}\ncur = int(counts.get(\"product\") or 0)\nwant = int('"${initial_product_count}"') + 1\nraise SystemExit(0 if cur >= want else 1)\n'
    pass "indexing upsert observed (product count >= initial+1)"

    # Action smoke (write): add_to_cart using both sku + productId.
    http POST "${REST_CONNECTOR_BASE_URL}/actions/execute" "$(cat <<JSON
{
  "actionId": "add_to_cart",
  "params": {
    "sku": "${sku}",
    "productId": ${product_id},
    "quantity": 1
  },
  "idempotencyKey": "verify-add-to-cart-${sku}",
  "trace": {
    "requestId": "verify-add-to-cart-${sku}",
    "conversationId": "verify",
    "userId": "verify-user",
    "sessionId": "verify-session"
  }
}
JSON
)"
    assert_status 200 "actions execute add_to_cart"
    json_assert "actions execute add_to_cart" $'assert (data or {}).get("success") is True\nprint("ok")'
    pass "rest connector POST /actions/execute (add_to_cart)"

    http DELETE "${STORE_BASE_URL}/api/products/${product_id}"
    assert_status 200 "delete product"
    pass "store DELETE /api/products/${product_id}"

    # Wait for product vector count to return to initial.
    poll_until "product deleted from index" 20 2 \
      "${INDEXING_CMD}" \
      $'counts = (data or {}).get(\"countsByEntityType\") or {}\ncur = int(counts.get(\"product\") or 0)\nwant = int('"${initial_product_count}"')\nraise SystemExit(0 if cur == want else 1)\n'
    pass "indexing delete observed (product count returned to initial)"
  fi
fi

if [[ "${RUN_PLATFORM_CHECKS}" == "true" ]]; then
  echo ""
  echo "== Platform Health and Capabilities =="
  platform_http GET "${PLATFORM_BASE_URL}/actuator/health"
  assert_status 200 "platform health"
  json_assert "platform health" $'assert (data or {}).get("status") == "UP"\nprint("ok")'
  pass "platform /actuator/health"

  platform_http GET "${PLATFORM_BASE_URL}/api/platform/overview"
  assert_status 200 "platform overview"
  json_assert "platform overview" $'caps = set((data or {}).get("capabilities") or [])\nfor req in ["verification","railway-preflight","release-progress-tracking","config-drift-verification"]:\n  assert req in caps\nprint("ok")'
  pass "platform GET /api/platform/overview"

  echo ""
  echo "== Platform Railway Preflight =="
  platform_http GET "${PLATFORM_BASE_URL}/api/platform/provisioning/railway/preflight"
  assert_status 200 "platform railway preflight"
  if [[ "${PLATFORM_EXPECT_PREFLIGHT_READY}" == "true" ]]; then
    json_assert "platform railway preflight" $'assert (data or {}).get("ready") is True\nchecks = (data or {}).get("checks") or []\nassert len(checks) > 0\nfailed = [item for item in checks if (item or {}).get("status") == "FAILED"]\nassert not failed, failed\nprint("ok")'
  else
    json_assert "platform railway preflight" $'checks = (data or {}).get("checks") or []\nassert len(checks) > 0\nprint("ok")'
  fi
  pass "platform GET /api/platform/provisioning/railway/preflight"

  echo ""
  echo "== Platform Deployment Workspace =="
  platform_http GET "${PLATFORM_BASE_URL}/api/deployments/${PLATFORM_DEPLOYMENT_ID}/workspace"
  assert_status 200 "platform deployment workspace"
  json_assert "platform deployment workspace" $'assert (data or {}).get("deployment", {}).get("id") == "'"${PLATFORM_DEPLOYMENT_ID}"'"\nassert (data or {}).get("access", {}).get("canOperate") is True\nprint("ok")'
  pass "platform GET /api/deployments/${PLATFORM_DEPLOYMENT_ID}/workspace"

  echo ""
  echo "== Platform Source Of Truth =="
  platform_http GET "${PLATFORM_BASE_URL}/api/deployments/${PLATFORM_DEPLOYMENT_ID}/source-of-truth"
  assert_status 200 "platform source of truth"
  json_assert "platform source of truth" $'assert (data or {}).get("deploymentId") == "'"${PLATFORM_DEPLOYMENT_ID}"'"\nlive = (data or {}).get("live") or {}\nassert live.get("available") is True\nartifacts = (data or {}).get("liveArtifacts") or {}\nassert bool(artifacts.get("actionsArtifactUrl"))\nassert bool(artifacts.get("entityArtifactUrl"))\nassert bool(artifacts.get("routingArtifactUrl"))\nassert bool(artifacts.get("promptArtifactUrl"))\nreadback = (data or {}).get("liveRailwayReadback") or {}\nassert "available" in readback\nassert "status" in readback\nprint("ok")'
  pass "platform GET /api/deployments/${PLATFORM_DEPLOYMENT_ID}/source-of-truth"
  PLATFORM_SOURCE_OF_TRUTH_BODY="${HTTP_BODY}"

  PLATFORM_LIVE_PROMPT_ARTIFACT_URL="$(PARSE_BODY="${PLATFORM_SOURCE_OF_TRUTH_BODY}" python3 - <<'PY'
import json, os
d = json.loads(os.environ.get("PARSE_BODY", "") or "{}")
print(((d.get("liveArtifacts") or {}).get("promptArtifactUrl")) or "")
PY
)"
  PLATFORM_LIVE_RUNTIME_URL="$(PARSE_BODY="${PLATFORM_SOURCE_OF_TRUTH_BODY}" python3 - <<'PY'
import json, os
d = json.loads(os.environ.get("PARSE_BODY", "") or "{}")
generated = d.get("generated") or {}
print((generated.get("runtimeBaseUrl")) or "")
PY
)"
  PLATFORM_LIVE_CONNECTOR_URL="$(PARSE_BODY="${PLATFORM_SOURCE_OF_TRUTH_BODY}" python3 - <<'PY'
import json, os
d = json.loads(os.environ.get("PARSE_BODY", "") or "{}")
generated = d.get("generated") or {}
print((generated.get("connectorBaseUrl")) or "")
PY
)"
  PLATFORM_LIVE_RAILWAY_STATUS="$(PARSE_BODY="${PLATFORM_SOURCE_OF_TRUTH_BODY}" python3 - <<'PY'
import json, os
d = json.loads(os.environ.get("PARSE_BODY", "") or "{}")
readback = d.get("liveRailwayReadback") or {}
print((readback.get("status")) or "")
PY
)"
  PLATFORM_GENERATED_PROVISIONING_MODE="$(PARSE_BODY="${PLATFORM_SOURCE_OF_TRUTH_BODY}" python3 - <<'PY'
import json, os
d = json.loads(os.environ.get("PARSE_BODY", "") or "{}")
generated = d.get("generated") or {}
print((generated.get("provisioningMode")) or "")
PY
)"
  PLATFORM_LATEST_RELEASE_ID="$(PARSE_BODY="${PLATFORM_SOURCE_OF_TRUTH_BODY}" python3 - <<'PY'
import json, os
d = json.loads(os.environ.get("PARSE_BODY", "") or "{}")
latest = d.get("latestRelease") or {}
print((latest.get("id")) or "")
PY
)"

  if [[ -n "${RUNTIME_BASE_URL}" ]]; then
    json_assert "platform source of truth runtime URL" $'generated = (data or {}).get("generated") or {}\nassert generated.get("runtimeBaseUrl") == "'"${RUNTIME_BASE_URL}"'"\nprint("ok")'
    pass "platform generated runtime base URL matches runtime input"
  fi
  if [[ -n "${REST_CONNECTOR_BASE_URL}" ]]; then
    json_assert "platform source of truth connector URL" $'generated = (data or {}).get("generated") or {}\nassert generated.get("connectorBaseUrl") == "'"${REST_CONNECTOR_BASE_URL}"'"\nprint("ok")'
    pass "platform generated connector base URL matches REST connector input"
  fi

  echo ""
  echo "== Platform Service Navigation =="
  platform_http GET "${PLATFORM_BASE_URL}/api/deployments/${PLATFORM_DEPLOYMENT_ID}/service-navigation"
  assert_status 200 "platform service navigation"
  json_assert "platform service navigation" $'surfaces = {item.get("key"): item for item in ((data or {}).get("surfaces") or [])}\nfor req in ["runtime","restConnector"]:\n  assert req in surfaces\n  assert bool((surfaces[req] or {}).get("primaryUrl"))\n  assert bool((surfaces[req] or {}).get("adminUrl"))\nprint("ok")'
  pass "platform GET /api/deployments/${PLATFORM_DEPLOYMENT_ID}/service-navigation"

  echo ""
  echo "== Platform Production Readiness =="
  platform_http GET "${PLATFORM_BASE_URL}/api/deployments/${PLATFORM_DEPLOYMENT_ID}/production-readiness"
  assert_status 200 "platform production readiness"
  json_assert "platform production readiness" $'assert (data or {}).get("deploymentId") == "'"${PLATFORM_DEPLOYMENT_ID}"'"\nscore = int((data or {}).get("overallScore") or 0)\nassert 0 <= score <= 100\nassert bool((data or {}).get("overallStatus"))\nareas = (data or {}).get("areas") or []\nassert len(areas) > 0\nprint("ok")'
  pass "platform GET /api/deployments/${PLATFORM_DEPLOYMENT_ID}/production-readiness"

  echo ""
  echo "== Platform Remediation =="
  platform_http GET "${PLATFORM_BASE_URL}/api/deployments/${PLATFORM_DEPLOYMENT_ID}/remediation"
  assert_status 200 "platform remediation"
  json_assert "platform remediation" $'actions = {((item.get("key") or "")).upper(): item for item in ((data or {}).get("actions") or [])}\nfor req in ["RERUN_VERIFICATION","REDEPLOY_ACTIVE_VERSION","RESET_RUNTIME_VECTORS"]:\n  assert req in actions\nassert "providerDriftDetected" in (data or {})\nassert "providerDriftStatus" in (data or {})\nprint("ok")'
  if [[ "${PLATFORM_LIVE_RAILWAY_STATUS}" == "WARNING" ]]; then
    json_assert "platform remediation drift alignment" $'assert (data or {}).get("providerDriftDetected") is True\nprint("ok")'
  fi
  pass "platform GET /api/deployments/${PLATFORM_DEPLOYMENT_ID}/remediation"

  echo ""
  echo "== Platform Release Evidence =="
  platform_http GET "${PLATFORM_BASE_URL}/api/deployments/${PLATFORM_DEPLOYMENT_ID}/releases"
  assert_status 200 "platform releases"
  json_assert "platform releases" $'items = data or []\nassert len(items) > 0\nwant = "'"${PLATFORM_EXPECT_RELEASE_ID:-${PLATFORM_LATEST_RELEASE_ID}}"'"\nrelease = next((item for item in items if not want or (item or {}).get("id") == want), None)\nassert release is not None, items\nassert release.get("status") == "'"${PLATFORM_EXPECT_RELEASE_STATUS}"'", release\nassert release.get("verificationStatus") == "'"${PLATFORM_EXPECT_VERIFICATION_STATUS}"'", release\nif "'"${PLATFORM_EXPECT_VERSION_ID}"'":\n  assert release.get("deploymentVersionId") == "'"${PLATFORM_EXPECT_VERSION_ID}"'", release\nprint("ok")'
  pass "platform GET /api/deployments/${PLATFORM_DEPLOYMENT_ID}/releases"

  platform_http GET "${PLATFORM_BASE_URL}/api/deployments/${PLATFORM_DEPLOYMENT_ID}/verification-runs"
  assert_status 200 "platform verification runs"
  json_assert "platform verification runs" $'items = data or []\nassert len(items) > 0\nwant_release = "'"${PLATFORM_EXPECT_RELEASE_ID:-${PLATFORM_LATEST_RELEASE_ID}}"'"\nwant_version = "'"${PLATFORM_EXPECT_VERSION_ID}"'"\nrun = next((item for item in items if (not want_release or (item or {}).get("releaseId") == want_release) and (not want_version or (item or {}).get("deploymentVersionId") == want_version)), None)\nassert run is not None, items\nassert run.get("status") == "'"${PLATFORM_EXPECT_VERIFICATION_STATUS}"'", run\nprint("ok")'
  PLATFORM_LATEST_VERIFICATION_RUN_ID="$(PARSE_BODY="${HTTP_BODY}" LATEST_RELEASE_ID="${PLATFORM_EXPECT_RELEASE_ID:-${PLATFORM_LATEST_RELEASE_ID}}" EXPECT_VERSION_ID="${PLATFORM_EXPECT_VERSION_ID}" python3 - <<'PY'
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
  json_assert "platform verification run checks" $'items = data or []\nrun_id = "'"${PLATFORM_LATEST_VERIFICATION_RUN_ID}"'"\nassert run_id\nrun = next((item for item in items if (item or {}).get("id") == run_id), None)\nassert run is not None\nchecks = {((check or {}).get("name") or (check or {}).get("key")): (check or {}).get("status") for check in ((run or {}).get("checks") or [])}\nrequired = ["runtime_config_matches_expected","connector_config_matches_expected","runtime_actions_match_expected","connector_actions_match_expected"]\nfor req in required:\n  assert req in checks, checks\nexpected = "SKIPPED" if "'"${PLATFORM_GENERATED_PROVISIONING_MODE}"'" == "RAILWAY_STUB" else "PASSED"\nfor req in required:\n  assert checks.get(req) == expected, checks\nfor optional in ["runtime_prompt_config_matches_expected","prompt_artifact_fetch_probe"]:\n  if optional in checks:\n    assert checks.get(optional) == expected, checks\nprint("ok")'
  pass "platform GET /api/deployments/${PLATFORM_DEPLOYMENT_ID}/verification-runs"

  echo ""
  echo "== Prompt Deployment Alignment =="
  if [[ -n "${PLATFORM_LIVE_PROMPT_ARTIFACT_URL}" ]]; then
    platform_http GET "${PLATFORM_LIVE_PROMPT_ARTIFACT_URL}"
    assert_status 200 "live prompt artifact fetch"
    json_assert "live prompt artifact fetch" $'assert isinstance(data, dict)\nallowed = {"systemPrompt","intentExtractionPrompt","actionSelectionPrompt","clarificationPrompt","answerGenerationPrompt","retrievalPrompt","assistantUiPrompt"}\nassert set(data.keys()).issubset(allowed)\nfor value in data.values():\n  assert isinstance(value, str)\nprint("ok")'
    pass "platform prompt artifact URL fetch"
  else
    fail "Source-of-truth did not expose a live prompt artifact URL."
  fi

  platform_runtime_url="${RUNTIME_BASE_URL:-${PLATFORM_LIVE_RUNTIME_URL}}"
  if [[ -n "${platform_runtime_url}" ]]; then
    runtime_http GET "${platform_runtime_url}/api/admin/overview"
    if [[ "${HTTP_STATUS}" == "401" ]]; then
      echo "WARN: runtime admin overview requires RUNTIME_ADMIN_API_KEY for direct prompt alignment verification."
    else
      assert_status 200 "runtime admin overview (platform alignment)"
      json_assert "runtime admin overview (platform alignment)" $'assert (data or {}).get("success") is True\nassert (data or {}).get("promptConfigLocation") == "'"${PLATFORM_LIVE_PROMPT_ARTIFACT_URL}"'"\nprint("ok")'
      pass "runtime prompt config location matches live prompt artifact"
    fi
  fi
fi

echo ""
pass "All checks completed."
