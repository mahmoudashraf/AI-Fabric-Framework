#!/usr/bin/env bash
set -euo pipefail

# Shopify Companion verification script.
#
# Verifies:
# - platform-managed product service operator surfaces
# - platform Shopify store mapping/control-plane state
# - Shopify Bridge shell
# - storefront bootstrap/query/suggestions/events path
# - optional merchant session path when a Shopify session bearer token is available
#
# Required env:
#   PLATFORM_BASE_URL
#   PLATFORM_API_KEY
#   SHOPIFY_BRIDGE_BASE_URL
#   SHOP_DOMAIN
#
# Optional env:
#   PRODUCT_SERVICE_REF=shopify-bridge-prod
#   PLATFORM_API_KEY_HEADER=X-PLATFORM-API-KEY
#   SHOPIFY_BRIDGE_ADMIN_API_KEY=...
#   SHOPIFY_BRIDGE_ADMIN_API_KEY_HEADER=X-BRIDGE-API-KEY
#   SHOPPER_SESSION_ID=...
#   SHOPPER_QUERY="Show me your best sellers"
#   EXPECT_PRODUCT_SERVICE_STATUS=ACTIVE
#   EXPECT_INSTALL_STATUS=INSTALLED
#   EXPECT_SYNC_STATUS=SYNCED
#   EXPECT_SOURCE_READINESS_STATUS=READY
#   EXPECT_WIDGET_STATUS=ENABLED
#   EXPECT_STOREFRONT_READY=true
#   EXPECT_GO_LIVE_ELIGIBLE=true
#   SHOPIFY_MERCHANT_AUTHORIZATION="Bearer <session-token>"
#   SHOPIFY_EMBEDDED_HOST=<base64-host>

PLATFORM_BASE_URL="${PLATFORM_BASE_URL:-}"
PLATFORM_API_KEY="${PLATFORM_API_KEY:-}"
PLATFORM_API_KEY_HEADER="${PLATFORM_API_KEY_HEADER:-X-PLATFORM-API-KEY}"
SHOPIFY_BRIDGE_BASE_URL="${SHOPIFY_BRIDGE_BASE_URL:-}"
SHOP_DOMAIN="${SHOP_DOMAIN:-}"
PRODUCT_SERVICE_REF="${PRODUCT_SERVICE_REF:-shopify-bridge-prod}"
SHOPIFY_BRIDGE_ADMIN_API_KEY="${SHOPIFY_BRIDGE_ADMIN_API_KEY:-}"
SHOPIFY_BRIDGE_ADMIN_API_KEY_HEADER="${SHOPIFY_BRIDGE_ADMIN_API_KEY_HEADER:-X-BRIDGE-API-KEY}"
SHOPPER_SESSION_ID="${SHOPPER_SESSION_ID:-shopper-verification-$(date +%s)}"
SHOPPER_QUERY="${SHOPPER_QUERY:-Show me your best sellers}"
EXPECT_PRODUCT_SERVICE_STATUS="${EXPECT_PRODUCT_SERVICE_STATUS:-ACTIVE}"
EXPECT_INSTALL_STATUS="${EXPECT_INSTALL_STATUS:-INSTALLED}"
EXPECT_SYNC_STATUS="${EXPECT_SYNC_STATUS:-SYNCED}"
EXPECT_SOURCE_READINESS_STATUS="${EXPECT_SOURCE_READINESS_STATUS:-READY}"
EXPECT_WIDGET_STATUS="${EXPECT_WIDGET_STATUS:-ENABLED}"
EXPECT_STOREFRONT_READY="${EXPECT_STOREFRONT_READY:-true}"
EXPECT_GO_LIVE_ELIGIBLE="${EXPECT_GO_LIVE_ELIGIBLE:-true}"
SHOPIFY_MERCHANT_AUTHORIZATION="${SHOPIFY_MERCHANT_AUTHORIZATION:-}"
SHOPIFY_EMBEDDED_HOST="${SHOPIFY_EMBEDDED_HOST:-}"

require_cmd() {
  local cmd="$1"
  if ! command -v "${cmd}" >/dev/null 2>&1; then
    echo "Missing required command: ${cmd}"
    exit 2
  fi
}

require_env() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    echo "Missing required env: ${name}"
    exit 2
  fi
}

trim_slash() {
  local url="$1"
  if [[ "${url}" == */ ]]; then
    echo "${url%/}"
  else
    echo "${url}"
  fi
}

json_get() {
  local payload="$1"
  local path="$2"
  JSON_PAYLOAD="${payload}" JSON_PATH="${path}" python3 - <<'PY'
import json
import os
import sys

payload = os.environ["JSON_PAYLOAD"]
path = os.environ["JSON_PATH"]

try:
    value = json.loads(payload)
except json.JSONDecodeError as exc:
    print(f"Invalid JSON: {exc}", file=sys.stderr)
    raise SystemExit(1)

current = value
for part in [segment for segment in path.split(".") if segment]:
    if isinstance(current, list):
        try:
            current = current[int(part)]
        except Exception:
            current = None
            break
    elif isinstance(current, dict):
        current = current.get(part)
    else:
        current = None
        break

if current is None:
    print("")
elif isinstance(current, bool):
    print("true" if current else "false")
elif isinstance(current, (dict, list)):
    print(json.dumps(current, separators=(",", ":")))
else:
    print(str(current))
PY
}

assert_equals() {
  local actual="$1"
  local expected="$2"
  local label="$3"
  if [[ "${actual}" != "${expected}" ]]; then
    echo "Assertion failed for ${label}: expected '${expected}', got '${actual}'"
    exit 1
  fi
}

assert_nonempty() {
  local value="$1"
  local label="$2"
  if [[ -z "${value}" ]]; then
    echo "Assertion failed for ${label}: value is empty"
    exit 1
  fi
}

http_request() {
  local method="$1"
  local url="$2"
  local body="${3:-}"
  shift 3 || true
  local headers=("$@")
  local response
  response="$(python3 - "$method" "$url" "$body" "${headers[@]}" <<'PY'
import json
import subprocess
import sys

method = sys.argv[1]
url = sys.argv[2]
body = sys.argv[3]
headers = sys.argv[4:]

cmd = [
    "curl",
    "-sS",
    "-X", method,
    "-H", "Accept: application/json",
    "-w", "\n%{http_code}",
]
for header in headers:
    cmd.extend(["-H", header])
if body:
    cmd.extend(["-H", "Content-Type: application/json", "--data", body])
cmd.append(url)

completed = subprocess.run(cmd, capture_output=True, text=True)
if completed.returncode != 0:
    print(completed.stderr.strip(), file=sys.stderr)
    raise SystemExit(completed.returncode)
print(completed.stdout, end="")
PY
)"
  HTTP_BODY="${response%$'\n'*}"
  HTTP_STATUS="${response##*$'\n'}"
}

platform_headers=("${PLATFORM_API_KEY_HEADER}: ${PLATFORM_API_KEY}")
bridge_base="$(trim_slash "${SHOPIFY_BRIDGE_BASE_URL}")"
platform_base="$(trim_slash "${PLATFORM_BASE_URL}")"

require_cmd curl
require_cmd python3
require_env PLATFORM_BASE_URL
require_env PLATFORM_API_KEY
require_env SHOPIFY_BRIDGE_BASE_URL
require_env SHOP_DOMAIN

echo "== Platform product service summary =="
http_request GET "${platform_base}/api/product-services/${PRODUCT_SERVICE_REF}" "" "${platform_headers[@]}"
assert_equals "${HTTP_STATUS}" "200" "product service summary status"
product_service_json="${HTTP_BODY}"
assert_equals "$(json_get "${product_service_json}" "serviceRef")" "${PRODUCT_SERVICE_REF}" "product service ref"
assert_equals "$(json_get "${product_service_json}" "status")" "${EXPECT_PRODUCT_SERVICE_STATUS}" "product service status"
assert_nonempty "$(json_get "${product_service_json}" "baseUrl")" "product service baseUrl"

echo "== Platform product service health =="
http_request GET "${platform_base}/api/product-services/${PRODUCT_SERVICE_REF}/health" "" "${platform_headers[@]}"
assert_equals "${HTTP_STATUS}" "200" "product service health status"
health_json="${HTTP_BODY}"
assert_nonempty "$(json_get "${health_json}" "overallStatus")" "product service overallStatus"

echo "== Platform product service overview =="
http_request GET "${platform_base}/api/product-services/${PRODUCT_SERVICE_REF}/overview" "" "${platform_headers[@]}"
assert_equals "${HTTP_STATUS}" "200" "product service overview status"
overview_json="${HTTP_BODY}"
assert_nonempty "$(json_get "${overview_json}" "storeOverview.totalCount")" "overview store total count"

echo "== Platform store summary =="
http_request GET "${platform_base}/api/shopify/stores/${SHOP_DOMAIN}" "" "${platform_headers[@]}"
assert_equals "${HTTP_STATUS}" "200" "platform store summary status"
store_json="${HTTP_BODY}"
assert_equals "$(json_get "${store_json}" "shopDomain")" "${SHOP_DOMAIN}" "platform store shopDomain"
assert_equals "$(json_get "${store_json}" "productServiceRef")" "${PRODUCT_SERVICE_REF}" "platform store serviceRef"
assert_equals "$(json_get "${store_json}" "installStatus")" "${EXPECT_INSTALL_STATUS}" "platform store installStatus"
assert_equals "$(json_get "${store_json}" "syncStatus")" "${EXPECT_SYNC_STATUS}" "platform store syncStatus"
assert_equals "$(json_get "${store_json}" "sourceReadinessStatus")" "${EXPECT_SOURCE_READINESS_STATUS}" "platform store sourceReadinessStatus"
assert_equals "$(json_get "${store_json}" "widgetStatus")" "${EXPECT_WIDGET_STATUS}" "platform store widgetStatus"
assert_equals "$(json_get "${store_json}" "readiness.storefrontReady")" "${EXPECT_STOREFRONT_READY}" "platform storefront readiness"
assert_equals "$(json_get "${store_json}" "readiness.goLiveEligible")" "${EXPECT_GO_LIVE_ELIGIBLE}" "platform go-live eligibility"
assert_nonempty "$(json_get "${store_json}" "deploymentId")" "platform deploymentId"
assert_nonempty "$(json_get "${store_json}" "consumerId")" "platform consumerId"

echo "== Bridge shell =="
http_request GET "${bridge_base}/api/app/shell"
assert_equals "${HTTP_STATUS}" "200" "bridge shell status"
shell_json="${HTTP_BODY}"
assert_equals "$(json_get "${shell_json}" "serviceRef")" "${PRODUCT_SERVICE_REF}" "bridge shell serviceRef"
assert_nonempty "$(json_get "${shell_json}" "appName")" "bridge shell appName"

echo "== Bridge admin overview =="
if [[ -n "${SHOPIFY_BRIDGE_ADMIN_API_KEY}" ]]; then
  http_request GET "${bridge_base}/api/admin/overview" "" "${SHOPIFY_BRIDGE_ADMIN_API_KEY_HEADER}: ${SHOPIFY_BRIDGE_ADMIN_API_KEY}"
  assert_equals "${HTTP_STATUS}" "200" "bridge admin overview status"
  admin_overview_json="${HTTP_BODY}"
  assert_nonempty "$(json_get "${admin_overview_json}" "serviceRef")" "bridge admin overview serviceRef"
else
  echo "Skipping bridge admin overview because SHOPIFY_BRIDGE_ADMIN_API_KEY is not configured."
fi

echo "== Storefront bootstrap =="
http_request GET "${bridge_base}/api/storefront/shops/${SHOP_DOMAIN}/bootstrap"
assert_equals "${HTTP_STATUS}" "200" "storefront bootstrap status"
bootstrap_json="${HTTP_BODY}"
assert_equals "$(json_get "${bootstrap_json}" "shopDomain")" "${SHOP_DOMAIN}" "storefront bootstrap shopDomain"
assert_equals "$(json_get "${bootstrap_json}" "available")" "${EXPECT_STOREFRONT_READY}" "storefront bootstrap availability"
assert_nonempty "$(json_get "${bootstrap_json}" "bridgeQueryUrl")" "storefront bridgeQueryUrl"
assert_nonempty "$(json_get "${bootstrap_json}" "bridgeSuggestionsUrl")" "storefront bridgeSuggestionsUrl"

echo "== Storefront suggestions =="
http_request POST "${bridge_base}/api/storefront/shops/${SHOP_DOMAIN}/chat/suggestions" '{"content":"","maxSuggestions":4}' "X-AI-FABRIC-SHOPPER-SESSION-ID: ${SHOPPER_SESSION_ID}"
assert_equals "${HTTP_STATUS}" "200" "storefront suggestions status"

echo "== Storefront query =="
query_payload="$(python3 - <<'PY' "${SHOPPER_QUERY}"
import json
import sys
print(json.dumps({"query": sys.argv[1]}))
PY
)"
http_request POST "${bridge_base}/api/storefront/shops/${SHOP_DOMAIN}/chat/query" "${query_payload}" "X-AI-FABRIC-SHOPPER-SESSION-ID: ${SHOPPER_SESSION_ID}"
assert_equals "${HTTP_STATUS}" "200" "storefront query status"
query_json="${HTTP_BODY}"
assert_nonempty "$(json_get "${query_json}" "conversationId")" "storefront query conversationId"

echo "== Storefront event =="
http_request POST "${bridge_base}/api/storefront/shops/${SHOP_DOMAIN}/events" '{"eventType":"VERIFICATION_SMOKE","pageType":"verification"}' "X-AI-FABRIC-SHOPPER-SESSION-ID: ${SHOPPER_SESSION_ID}"
assert_equals "${HTTP_STATUS}" "202" "storefront event status"

if [[ -n "${SHOPIFY_MERCHANT_AUTHORIZATION}" ]]; then
  echo "== Merchant session =="
  merchant_headers=("Authorization: ${SHOPIFY_MERCHANT_AUTHORIZATION}")
  if [[ -n "${SHOPIFY_EMBEDDED_HOST}" ]]; then
    merchant_headers+=("X-Shopify-Embedded-Host: ${SHOPIFY_EMBEDDED_HOST}")
  fi
  http_request GET "${bridge_base}/api/app/session" "" "${merchant_headers[@]}"
  assert_equals "${HTTP_STATUS}" "200" "merchant session status"
  merchant_session_json="${HTTP_BODY}"
  assert_equals "$(json_get "${merchant_session_json}" "shopDomain")" "${SHOP_DOMAIN}" "merchant session shopDomain"
  assert_nonempty "$(json_get "${merchant_session_json}" "userId")" "merchant session userId"
fi

echo "Shopify Companion verification passed for ${SHOP_DOMAIN}"
