#!/usr/bin/env bash
set -euo pipefail

# Deployment verification script for the 3-service ecommerce demo:
# - Ecommerce Store (domain API)
# - Generic REST Connector (actions + optional runtime proxy)
# - AI Fabric Runtime
#
# Usage (read-only checks + action routing smoke):
#   STORE_BASE_URL="https://<ecommerce-store>.up.railway.app" \
#   REST_CONNECTOR_BASE_URL="https://<rest-connector>.up.railway.app" \
#   RUNTIME_BASE_URL="https://<runtime>.up.railway.app" \
#   API_KEY="test-key" \
#   ./scripts/verify-ecommerce-deployment.sh
#
# Optional (write checks: create/delete product + verify indexing counts):
#   VERIFY_WRITE=true ./scripts/verify-ecommerce-deployment.sh
#
# Notes:
# - If your REST connector inbound auth is enabled, set API_KEY (default header: X-AIFABRIC-API-KEY).
# - Runtime admin endpoints allow unauthenticated access unless app.admin.api-key is configured.

STORE_BASE_URL="${STORE_BASE_URL:-${ECOMMERCE_STORE_BASE_URL:-}}"
REST_CONNECTOR_BASE_URL="${REST_CONNECTOR_BASE_URL:-}"
RUNTIME_BASE_URL="${RUNTIME_BASE_URL:-}"

API_KEY_HEADER="${API_KEY_HEADER:-X-AIFABRIC-API-KEY}"
API_KEY="${API_KEY:-}"

RUNTIME_ADMIN_API_KEY_HEADER="${RUNTIME_ADMIN_API_KEY_HEADER:-X-ADMIN-API-KEY}"
RUNTIME_ADMIN_API_KEY="${RUNTIME_ADMIN_API_KEY:-}"

VERIFY_WRITE="${VERIFY_WRITE:-false}"

if [[ -z "${STORE_BASE_URL}" || -z "${REST_CONNECTOR_BASE_URL}" ]]; then
  echo "Missing required env vars."
  echo "Set: STORE_BASE_URL and REST_CONNECTOR_BASE_URL (and optionally RUNTIME_BASE_URL)."
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

echo ""
pass "All checks completed."
