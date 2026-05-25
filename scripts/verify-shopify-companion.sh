#!/usr/bin/env bash
set -euo pipefail

# Shopify Companion verification script.
#
# Verifies:
# - platform-managed product service operator surfaces
# - platform Shopify store mapping/control-plane state
# - Shopify Bridge shell and embedded app UI shell
# - storefront bootstrap/query/suggestions/events path
# - optional merchant session path when a Shopify session bearer token is available
#
# Required env:
#   PLATFORM_BASE_URL
#   PLATFORM_API_KEY or PLATFORM_SESSION_COOKIE_JAR
#   SHOPIFY_BRIDGE_BASE_URL
#   SHOP_DOMAIN
#
# Optional env:
#   PRODUCT_SERVICE_REF=shopify-bridge-staging
#   PLATFORM_API_KEY_HEADER=X-PLATFORM-API-KEY
#   PLATFORM_SESSION_COOKIE_JAR=/tmp/platform.cookies
#   SHOPIFY_BRIDGE_ADMIN_API_KEY=... # same secret value as deployed SHOPIFY_BRIDGE_SHARED_SECRET
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
#   EXPECT_EMBEDDED_APP_UI=true
#   EXPECT_SHELL_MODE_PROFILE=SHOPIFY_COMPANION
#   EXPECT_ENABLED_SURFACES=<optional effective surfaces override; defaults to live billing allowedSurfaces>
#   EXPECT_CONFIGURED_ENABLED_SURFACES=<optional configured widget surfaces override>
#   EXPECT_WEBHOOK_STATUS=READY
#   EXPECT_BILLING_STATUS=ACTIVE
#   EXPECT_BILLING_TIER=FREE
#   EXPECT_BILLING_LAUNCH_BLOCKED=false
#   EXPECT_CATALOG_PRODUCT_CAP=50
#   EXPECT_POWERED_BY_BADGE_REQUIRED=true
#   EXPECT_CHAT_FALLBACK_ENABLED=false
#   EXPECT_ACTION_CAPABILITY_AVAILABLE=false
#   EXPECT_ACTION_REQUIRES_CONFIRMATION=false
#   EXPECT_ACTION_AUDIT_AVAILABLE=false
#   EXPECT_MAX_WIDGET_SURFACE=<optional; defaults true when chat fallback is expected>
#   SHOPIFY_COMPANION_ENSURE_BILLING_STATE=true repairs Bridge billing state to EXPECT_BILLING_TIER/ACTIVE before assertions
#   EXPECT_ORDER_LOOKUP_STATUS=READY
#   EXPECT_ORDER_LOOKUP_SUPPORTED=<optional; defaults from live billing allowedSurfaces>
#   EXPECT_ORDER_LOOKUP_SCOPE_GRANTED=<optional expected live scope grant>
#   EXPECT_ORDER_LOOKUP_APP_SCOPES_WEBHOOK_READY=<optional expected app scopes webhook posture>
#   EXPECT_ORDER_LOOKUP_MERCHANT_HANDOFF_CONFIGURED=<optional expected merchant handoff posture>
#   EXPECT_SUPPORT_LIFECYCLE_STAGE=<optional expected support lifecycle stage>
#   EXPECT_HISTORICAL_ORDER_LOOKUP_SUPPORTED=<optional expected broader order scope grant>
#   EXPECT_OLDER_ORDERS_REQUIRE_BROADER_SCOPE=<optional storefront bootstrap expectation>
#   ORDER_LOOKUP_ORDER_NUMBER=<optional exact order name like #1001>
#   ORDER_LOOKUP_EMAIL=<optional checkout email for the order above>
#   SHOPIFY_ADMIN_ACCESS_TOKEN=<offline-access-token>
#   SHOPIFY_ADMIN_API_VERSION=2026-04
#   SHOPIFY_MERCHANT_AUTHORIZATION="Bearer <session-token>"
#   SHOPIFY_EMBEDDED_HOST=<base64-host>
#   SHOPIFY_COMPARISON_MODE=navigator_deep

PLATFORM_BASE_URL="${PLATFORM_BASE_URL:-}"
PLATFORM_API_KEY="${PLATFORM_API_KEY:-}"
PLATFORM_API_KEY_HEADER="${PLATFORM_API_KEY_HEADER:-X-PLATFORM-API-KEY}"
PLATFORM_SESSION_COOKIE_JAR="${PLATFORM_SESSION_COOKIE_JAR:-}"
PLATFORM_LOGIN_EMAIL="${PLATFORM_LOGIN_EMAIL:-}"
PLATFORM_LOGIN_PASSWORD="${PLATFORM_LOGIN_PASSWORD:-}"
SHOPIFY_BRIDGE_BASE_URL="${SHOPIFY_BRIDGE_BASE_URL:-}"
SHOP_DOMAIN="${SHOP_DOMAIN:-}"
PRODUCT_SERVICE_REF="${PRODUCT_SERVICE_REF:-shopify-bridge-staging}"
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
EXPECT_STOREFRONT_SHOPPER_TRAFFIC_READY="${EXPECT_STOREFRONT_SHOPPER_TRAFFIC_READY:-${EXPECT_STOREFRONT_READY}}"
EXPECT_GO_LIVE_ELIGIBLE="${EXPECT_GO_LIVE_ELIGIBLE:-true}"
EXPECT_EMBEDDED_APP_UI="${EXPECT_EMBEDDED_APP_UI:-true}"
EXPECT_SHELL_MODE_PROFILE="${EXPECT_SHELL_MODE_PROFILE:-SHOPIFY_COMPANION}"
EXPECT_ENABLED_SURFACES="${EXPECT_ENABLED_SURFACES:-}"
EXPECT_CONFIGURED_ENABLED_SURFACES="${EXPECT_CONFIGURED_ENABLED_SURFACES:-}"
EXPECT_WEBHOOK_STATUS="${EXPECT_WEBHOOK_STATUS:-}"
EXPECT_BILLING_STATUS="${EXPECT_BILLING_STATUS:-}"
EXPECT_BILLING_TIER="${EXPECT_BILLING_TIER:-}"
EXPECT_BILLING_LAUNCH_BLOCKED="${EXPECT_BILLING_LAUNCH_BLOCKED:-}"
EXPECT_CATALOG_PRODUCT_CAP="${EXPECT_CATALOG_PRODUCT_CAP:-}"
EXPECT_POWERED_BY_BADGE_REQUIRED="${EXPECT_POWERED_BY_BADGE_REQUIRED:-}"
EXPECT_CHAT_FALLBACK_ENABLED="${EXPECT_CHAT_FALLBACK_ENABLED:-}"
EXPECT_ACTION_CAPABILITY_AVAILABLE="${EXPECT_ACTION_CAPABILITY_AVAILABLE:-}"
EXPECT_ACTION_REQUIRES_CONFIRMATION="${EXPECT_ACTION_REQUIRES_CONFIRMATION:-}"
EXPECT_ACTION_AUDIT_AVAILABLE="${EXPECT_ACTION_AUDIT_AVAILABLE:-}"
EXPECT_MAX_WIDGET_SURFACE="${EXPECT_MAX_WIDGET_SURFACE:-}"
EXPECT_ORDER_LOOKUP_STATUS="${EXPECT_ORDER_LOOKUP_STATUS:-READY}"
EXPECT_ORDER_LOOKUP_SUPPORTED="${EXPECT_ORDER_LOOKUP_SUPPORTED:-}"
EXPECT_ORDER_LOOKUP_SCOPE_GRANTED="${EXPECT_ORDER_LOOKUP_SCOPE_GRANTED:-}"
EXPECT_ORDER_LOOKUP_APP_SCOPES_WEBHOOK_READY="${EXPECT_ORDER_LOOKUP_APP_SCOPES_WEBHOOK_READY:-}"
EXPECT_ORDER_LOOKUP_MERCHANT_HANDOFF_CONFIGURED="${EXPECT_ORDER_LOOKUP_MERCHANT_HANDOFF_CONFIGURED:-}"
EXPECT_SUPPORT_LIFECYCLE_STAGE="${EXPECT_SUPPORT_LIFECYCLE_STAGE:-}"
EXPECT_HISTORICAL_ORDER_LOOKUP_SUPPORTED="${EXPECT_HISTORICAL_ORDER_LOOKUP_SUPPORTED:-}"
EXPECT_OLDER_ORDERS_REQUIRE_BROADER_SCOPE="${EXPECT_OLDER_ORDERS_REQUIRE_BROADER_SCOPE:-}"
EXPECT_REQUIRED_ACTIONS="${EXPECT_REQUIRED_ACTIONS:-shopify_search_catalog,shopify_get_product_details,shopify_search_policies,shopify_get_cart,shopify_update_cart}"
SHOPIFY_COMPANION_ENSURE_BILLING_STATE="${SHOPIFY_COMPANION_ENSURE_BILLING_STATE:-false}"
SHOPIFY_COMPANION_BILLING_STATE_REASON="${SHOPIFY_COMPANION_BILLING_STATE_REASON:-Shopify Companion live verification requires the configured release-gate billing posture.}"
SHOPIFY_ADMIN_ACCESS_TOKEN="${SHOPIFY_ADMIN_ACCESS_TOKEN:-}"
SHOPIFY_ADMIN_ACCESS_TOKEN_SOURCE="none"
SHOPIFY_ADMIN_API_VERSION="${SHOPIFY_ADMIN_API_VERSION:-2026-04}"
SHOPIFY_MERCHANT_AUTHORIZATION="${SHOPIFY_MERCHANT_AUTHORIZATION:-}"
SHOPIFY_EMBEDDED_HOST="${SHOPIFY_EMBEDDED_HOST:-}"
SHOPIFY_COMPARISON_MODE="${SHOPIFY_COMPARISON_MODE:-navigator_deep}"
ORDER_LOOKUP_ORDER_NUMBER="${ORDER_LOOKUP_ORDER_NUMBER:-}"
ORDER_LOOKUP_EMAIL="${ORDER_LOOKUP_EMAIL:-}"
ORDER_LOOKUP_SAMPLE_SOURCE="none"
STOREFRONT_QUERY_RETRY_ATTEMPTS="${STOREFRONT_QUERY_RETRY_ATTEMPTS:-3}"
STOREFRONT_QUERY_RETRY_SLEEP_SECONDS="${STOREFRONT_QUERY_RETRY_SLEEP_SECONDS:-2}"
PRODUCT_SERVICE_LOGS_RETRY_ATTEMPTS="${PRODUCT_SERVICE_LOGS_RETRY_ATTEMPTS:-12}"
PRODUCT_SERVICE_LOGS_RETRY_SLEEP_SECONDS="${PRODUCT_SERVICE_LOGS_RETRY_SLEEP_SECONDS:-10}"
TEMP_PLATFORM_COOKIE_JAR=""

cleanup() {
  if [[ -n "${TEMP_PLATFORM_COOKIE_JAR}" && -f "${TEMP_PLATFORM_COOKIE_JAR}" ]]; then
    rm -f "${TEMP_PLATFORM_COOKIE_JAR}"
  fi
}

trap cleanup EXIT

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

json_array_to_csv() {
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

if not isinstance(current, list):
    print("")
    raise SystemExit(0)

values = [str(item).strip() for item in current if str(item).strip()]
print(",".join(values))
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

assert_contains() {
  local value="$1"
  local expected="$2"
  local label="$3"
  if [[ "${value}" != *"${expected}"* ]]; then
    echo "Assertion failed for ${label}: expected substring '${expected}'"
    exit 1
  fi
}

assert_optional_equals() {
  local actual="$1"
  local expected="$2"
  local label="$3"
  if [[ -n "${expected}" ]]; then
    assert_equals "${actual}" "${expected}" "${label}"
  fi
}

assert_json_array_contains_csv() {
  local payload="$1"
  local path="$2"
  local expected_csv="$3"
  local label="$4"
  JSON_PAYLOAD="${payload}" JSON_PATH="${path}" EXPECTED_CSV="${expected_csv}" ASSERT_LABEL="${label}" python3 - <<'PY'
import json
import os
import sys

payload = os.environ["JSON_PAYLOAD"]
path = os.environ["JSON_PATH"]
expected_csv = os.environ["EXPECTED_CSV"]
label = os.environ["ASSERT_LABEL"]

try:
    value = json.loads(payload)
except json.JSONDecodeError as exc:
    print(f"Invalid JSON for {label}: {exc}", file=sys.stderr)
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

if not isinstance(current, list):
    print(f"Assertion failed for {label}: expected JSON array at path '{path}'", file=sys.stderr)
    raise SystemExit(1)

actual = {str(item).strip() for item in current if str(item).strip()}
expected = {item.strip() for item in expected_csv.split(",") if item.strip()}
missing = sorted(expected - actual)
if missing:
    print(
        f"Assertion failed for {label}: missing values {missing}; actual={sorted(actual)}",
        file=sys.stderr,
    )
    raise SystemExit(1)
PY
}

assert_json_array_not_contains_csv() {
  local payload="$1"
  local path="$2"
  local forbidden_csv="$3"
  local label="$4"
  JSON_PAYLOAD="${payload}" JSON_PATH="${path}" FORBIDDEN_CSV="${forbidden_csv}" ASSERT_LABEL="${label}" python3 - <<'PY'
import json
import os
import sys

payload = os.environ["JSON_PAYLOAD"]
path = os.environ["JSON_PATH"]
forbidden_csv = os.environ["FORBIDDEN_CSV"]
label = os.environ["ASSERT_LABEL"]

try:
    value = json.loads(payload)
except json.JSONDecodeError as exc:
    print(f"Invalid JSON for {label}: {exc}", file=sys.stderr)
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

if not isinstance(current, list):
    print(f"Assertion failed for {label}: expected JSON array at path '{path}'", file=sys.stderr)
    raise SystemExit(1)

actual = {str(item).strip() for item in current if str(item).strip()}
forbidden = {item.strip() for item in forbidden_csv.split(",") if item.strip()}
present = sorted(forbidden & actual)
if present:
    print(
        f"Assertion failed for {label}: forbidden values present {present}; actual={sorted(actual)}",
        file=sys.stderr,
    )
    raise SystemExit(1)
PY
}

assert_storefront_resolution_contains_action() {
  local payload="$1"
  local expected_action="$2"
  local label="$3"
  JSON_PAYLOAD="${payload}" EXPECTED_ACTION="${expected_action}" ASSERT_LABEL="${label}" python3 - <<'PY'
import json
import os
import sys

payload = os.environ["JSON_PAYLOAD"]
expected_action = os.environ["EXPECTED_ACTION"]
label = os.environ["ASSERT_LABEL"]

try:
    data = json.loads(payload)
except json.JSONDecodeError as exc:
    print(f"Invalid JSON for {label}: {exc}", file=sys.stderr)
    raise SystemExit(1)

result = (data or {}).get("result") or {}
metadata = result.get("metadata") or {}
top_resolution = metadata.get("readActionResolution") or ((result.get("data") or {}).get("readActionResolution") or {})
children = result.get("children") or []

executed_actions = set()

direct_action = (result.get("data") or {}).get("action")
if isinstance(direct_action, str) and direct_action.strip():
    executed_actions.add(direct_action.strip())

for item in top_resolution.get("executedActions") or []:
    if isinstance(item, dict) and item.get("action"):
        executed_actions.add(item.get("action"))

for child in children:
    if not isinstance(child, dict):
        continue
    child_metadata = child.get("metadata") or {}
    child_data = child.get("data") or {}
    child_action = child_data.get("action")
    if isinstance(child_action, str) and child_action.strip():
        executed_actions.add(child_action.strip())
    child_resolution = child_metadata.get("readActionResolution") or child_data.get("readActionResolution") or {}
    for item in child_resolution.get("executedActions") or []:
        if isinstance(item, dict) and item.get("action"):
            executed_actions.add(item.get("action"))

if expected_action not in executed_actions:
    print(
        f"Assertion failed for {label}: expected executed action '{expected_action}', actual={sorted(executed_actions)}",
        file=sys.stderr,
    )
    raise SystemExit(1)
PY
}

resolve_shopify_admin_access_token() {
  if [[ -n "${SHOPIFY_ADMIN_ACCESS_TOKEN}" ]]; then
    SHOPIFY_ADMIN_ACCESS_TOKEN_SOURCE="explicit"
    return
  fi
  platform_request POST "${platform_base}/api/shopify/stores/${SHOP_DOMAIN}/credentials/material" "" "${platform_headers[@]-}"
  if [[ "${HTTP_STATUS}" != "200" ]]; then
    return
  fi
  local resolved_access_token
  resolved_access_token="$(json_get "${HTTP_BODY}" "accessToken")"
  if [[ -n "${resolved_access_token}" ]]; then
    SHOPIFY_ADMIN_ACCESS_TOKEN="${resolved_access_token}"
    SHOPIFY_ADMIN_ACCESS_TOKEN_SOURCE="resolved"
  fi
}

resolve_order_lookup_sample() {
  if [[ -n "${ORDER_LOOKUP_ORDER_NUMBER}" && -n "${ORDER_LOOKUP_EMAIL}" ]]; then
    ORDER_LOOKUP_SAMPLE_SOURCE="explicit"
    return
  fi
  if [[ -z "${SHOPIFY_ADMIN_ACCESS_TOKEN}" ]]; then
    return
  fi
  local sample_query='{"query":"query ShopifyBridgeVerificationOrderSample { orders(first: 10, sortKey: PROCESSED_AT, reverse: true) { nodes { name email customer { email } } } }"}'
  http_request POST "https://${SHOP_DOMAIN}/admin/api/${SHOPIFY_ADMIN_API_VERSION}/graphql.json" "${sample_query}" "X-Shopify-Access-Token: ${SHOPIFY_ADMIN_ACCESS_TOKEN}"
  if [[ "${HTTP_STATUS}" != "200" ]]; then
    return
  fi
  local resolved_sample
  resolved_sample="$(JSON_PAYLOAD="${HTTP_BODY}" python3 - <<'PY'
import json
import os

payload = json.loads(os.environ["JSON_PAYLOAD"])
nodes = (((payload.get("data") or {}).get("orders") or {}).get("nodes")) or []
for node in nodes:
    if not isinstance(node, dict):
        continue
    order_name = str(node.get("name") or "").strip()
    email = str(node.get("email") or "").strip()
    if not email:
        customer = node.get("customer") or {}
        if isinstance(customer, dict):
            email = str(customer.get("email") or "").strip()
    if order_name and email:
        print(order_name)
        print(email)
        break
PY
)"
  if [[ -z "${resolved_sample}" ]]; then
    return
  fi
  ORDER_LOOKUP_ORDER_NUMBER="$(printf '%s\n' "${resolved_sample}" | sed -n '1p')"
  ORDER_LOOKUP_EMAIL="$(printf '%s\n' "${resolved_sample}" | sed -n '2p')"
  if [[ -n "${ORDER_LOOKUP_ORDER_NUMBER}" && -n "${ORDER_LOOKUP_EMAIL}" ]]; then
    ORDER_LOOKUP_SAMPLE_SOURCE="shopify-admin"
  fi
}

ensure_companion_billing_state() {
  if [[ "${SHOPIFY_COMPANION_ENSURE_BILLING_STATE}" != "true" ]]; then
    return
  fi
  if [[ -z "${EXPECT_BILLING_TIER}" ]]; then
    echo "FAIL: EXPECT_BILLING_TIER is required when SHOPIFY_COMPANION_ENSURE_BILLING_STATE=true" >&2
    exit 2
  fi
  if [[ -z "${SHOPIFY_BRIDGE_ADMIN_API_KEY}" ]]; then
    echo "FAIL: SHOPIFY_BRIDGE_ADMIN_API_KEY is required when SHOPIFY_COMPANION_ENSURE_BILLING_STATE=true" >&2
    exit 2
  fi

  local target_status="${EXPECT_BILLING_STATUS:-ACTIVE}"
  http_request GET "${bridge_base}/api/admin/stores/${SHOP_DOMAIN}/billing-summary" "" "${SHOPIFY_BRIDGE_ADMIN_API_KEY_HEADER}: ${SHOPIFY_BRIDGE_ADMIN_API_KEY}"
  assert_equals "${HTTP_STATUS}" "200" "bridge billing state preflight status"

  local current_tier current_status
  current_tier="$(json_get "${HTTP_BODY}" "tierKey")"
  current_status="$(json_get "${HTTP_BODY}" "status")"
  if [[ "${current_tier}" == "${EXPECT_BILLING_TIER}" && "${current_status}" == "${target_status}" ]]; then
    echo "PASS: bridge billing posture already ${EXPECT_BILLING_TIER}/${target_status}"
    return
  fi

  local payload
  payload="$(python3 - <<'PY' "${EXPECT_BILLING_TIER}" "${target_status}" "${SHOPIFY_COMPANION_BILLING_STATE_REASON}"
import json
import sys
tier = sys.argv[1]
status = sys.argv[2]
reason = sys.argv[3]
print(json.dumps({
    "tierKey": tier,
    "status": status,
    "subscriptionId": f"release-gate-{tier.lower()}",
    "subscriptionName": f"Loom Companion {tier.title()}",
    "reason": reason,
}))
PY
  )"
  http_request POST "${bridge_base}/api/admin/stores/${SHOP_DOMAIN}/billing-state" "${payload}" "${SHOPIFY_BRIDGE_ADMIN_API_KEY_HEADER}: ${SHOPIFY_BRIDGE_ADMIN_API_KEY}"
  assert_equals "${HTTP_STATUS}" "200" "bridge billing state repair status"
  assert_equals "$(json_get "${HTTP_BODY}" "tierKey")" "${EXPECT_BILLING_TIER}" "bridge billing state repaired tier"
  assert_equals "$(json_get "${HTTP_BODY}" "status")" "${target_status}" "bridge billing state repaired status"
  echo "PASS: bridge billing posture set to ${EXPECT_BILLING_TIER}/${target_status}"
}

http_request() {
  local method="$1"
  local url="$2"
  local body="${3:-}"
  shift 3 || true
  local headers=("$@")
  local response
  response="$(python3 - "$method" "$url" "$body" "${HTTP_COOKIE_JAR:-}" "${headers[@]-}" <<'PY'
import json
import subprocess
import sys

method = sys.argv[1]
url = sys.argv[2]
body = sys.argv[3]
cookie_jar = sys.argv[4]
headers = sys.argv[5:]

cmd = [
    "curl",
    "-sS",
    "-X", method,
    "-H", "Accept: application/json",
    "-w", "\n%{http_code}",
]
if cookie_jar:
    cmd.extend(["-b", cookie_jar])
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

http_request_text() {
  local method="$1"
  local url="$2"
  shift 2 || true
  local headers=("$@")
  local response
  response="$(python3 - "$method" "$url" "${headers[@]-}" <<'PY'
import subprocess
import sys

method = sys.argv[1]
url = sys.argv[2]
headers = sys.argv[3:]

cmd = [
    "curl",
    "-sS",
    "-X", method,
    "-H", "Accept: text/html, text/plain, */*",
    "-w", "\n%{http_code}",
]
for header in headers:
    cmd.extend(["-H", header])
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

retry_storefront_query() {
  local url="$1"
  local body="$2"
  shift 2 || true
  local headers=("$@")
  local attempt
  for ((attempt=1; attempt<=STOREFRONT_QUERY_RETRY_ATTEMPTS; attempt+=1)); do
    http_request POST "${url}" "${body}" "${headers[@]-}"
    if [[ "${HTTP_STATUS}" == "200" ]]; then
      return 0
    fi
    if [[ ! "${HTTP_STATUS}" =~ ^(429|500|502|503|504)$ || "${attempt}" -eq "${STOREFRONT_QUERY_RETRY_ATTEMPTS}" ]]; then
      return 0
    fi
    sleep "${STOREFRONT_QUERY_RETRY_SLEEP_SECONDS}"
  done
}

extract_first_asset_path() {
  local payload="$1"
  HTML_PAYLOAD="${payload}" python3 - <<'PY'
import re
import os

payload = os.environ["HTML_PAYLOAD"]
match = re.search(r'(/assets/[^"\']+)', payload)
print(match.group(1) if match else "")
PY
}

platform_request() {
  local previous_cookie_jar="${HTTP_COOKIE_JAR:-}"
  HTTP_COOKIE_JAR="${PLATFORM_SESSION_COOKIE_JAR:-}"
  http_request "$@"
  HTTP_COOKIE_JAR="${previous_cookie_jar}"
}

platform_login() {
  if [[ -n "${PLATFORM_API_KEY}" || -n "${PLATFORM_SESSION_COOKIE_JAR}" ]]; then
    return
  fi
  if [[ -z "${PLATFORM_LOGIN_EMAIL}" || -z "${PLATFORM_LOGIN_PASSWORD}" ]]; then
    return
  fi

  TEMP_PLATFORM_COOKIE_JAR="$(mktemp)"
  local response
  response="$(python3 - "${platform_base}" "${TEMP_PLATFORM_COOKIE_JAR}" "${PLATFORM_LOGIN_EMAIL}" "${PLATFORM_LOGIN_PASSWORD}" <<'PY'
import json
import subprocess
import sys

platform_base = sys.argv[1]
cookie_jar = sys.argv[2]
email = sys.argv[3]
password = sys.argv[4]

cmd = [
    "curl",
    "-sS",
    "-X", "POST",
    "-H", "Accept: application/json",
    "-H", "Content-Type: application/json",
    "-c", cookie_jar,
    "-b", cookie_jar,
    "-w", "\n%{http_code}",
    "--data", json.dumps({"email": email, "password": password}),
    f"{platform_base}/api/platform/auth/login",
]
completed = subprocess.run(cmd, capture_output=True, text=True)
if completed.returncode != 0:
    print(completed.stderr.strip(), file=sys.stderr)
    raise SystemExit(completed.returncode)
print(completed.stdout, end="")
PY
)"
  HTTP_BODY="${response%$'\n'*}"
  HTTP_STATUS="${response##*$'\n'}"
  if [[ "${HTTP_STATUS}" != "200" ]]; then
    echo "Platform login failed (HTTP ${HTTP_STATUS})."
    echo "${HTTP_BODY}"
    exit 1
  fi
  PLATFORM_SESSION_COOKIE_JAR="${TEMP_PLATFORM_COOKIE_JAR}"
}

assert_shopify_webhook_subscriptions() {
  local payload="$1"
  local expected_uri="$2"
  JSON_PAYLOAD="${payload}" EXPECTED_URI="${expected_uri}" python3 - <<'PY'
import json
import os
import sys

required = {
    "loom-app-uninstalled": "APP_UNINSTALLED",
    "loom-app-subscriptions-update": "APP_SUBSCRIPTIONS_UPDATE",
    "loom-app-scopes-update": "APP_SCOPES_UPDATE",
    "loom-products-create": "PRODUCTS_CREATE",
    "loom-products-update": "PRODUCTS_UPDATE",
    "loom-products-delete": "PRODUCTS_DELETE",
    "loom-collections-create": "COLLECTIONS_CREATE",
    "loom-collections-update": "COLLECTIONS_UPDATE",
    "loom-collections-delete": "COLLECTIONS_DELETE",
    "loom-shop-update": "SHOP_UPDATE",
}

payload = json.loads(os.environ["JSON_PAYLOAD"])
expected_uri = os.environ["EXPECTED_URI"]
edges = (((payload.get("data") or {}).get("webhookSubscriptions") or {}).get("edges")) or []
subscriptions = {}
for edge in edges:
    node = (edge or {}).get("node") or {}
    name = (node.get("name") or "").strip()
    if not name:
        continue
    subscriptions[name] = {
        "topic": (node.get("topic") or "").strip(),
        "uri": (node.get("uri") or "").strip(),
    }

missing = []
wrong = []
for name, topic in required.items():
    current = subscriptions.get(name)
    if not current:
        missing.append(name)
        continue
    if current["topic"] != topic or current["uri"] != expected_uri:
        wrong.append(f"{name}=>topic={current['topic']} uri={current['uri']}")

if missing or wrong:
    details = []
    if missing:
        details.append("missing=" + ",".join(missing))
    if wrong:
        details.append("wrong=" + ",".join(wrong))
    print("Shopify webhook subscriptions are not fully configured: " + " ".join(details), file=sys.stderr)
    raise SystemExit(1)
PY
}

declare -a platform_headers=()
PLATFORM_API_KEY="$(resolve_secret_value PLATFORM_API_KEY)"
PLATFORM_LOGIN_EMAIL="$(resolve_secret_value PLATFORM_LOGIN_EMAIL)"
PLATFORM_LOGIN_PASSWORD="$(resolve_secret_value PLATFORM_LOGIN_PASSWORD)"
SHOPIFY_BRIDGE_ADMIN_API_KEY="$(resolve_secret_value SHOPIFY_BRIDGE_ADMIN_API_KEY)"
SHOPIFY_ADMIN_ACCESS_TOKEN="$(resolve_secret_value SHOPIFY_ADMIN_ACCESS_TOKEN)"
SHOPIFY_MERCHANT_AUTHORIZATION="$(resolve_secret_value SHOPIFY_MERCHANT_AUTHORIZATION)"
if [[ -n "${PLATFORM_API_KEY}" ]]; then
  platform_headers=("${PLATFORM_API_KEY_HEADER}: ${PLATFORM_API_KEY}")
fi
bridge_base="$(trim_slash "${SHOPIFY_BRIDGE_BASE_URL}")"
platform_base="$(trim_slash "${PLATFORM_BASE_URL}")"

require_cmd curl
require_cmd python3
require_env PLATFORM_BASE_URL
require_env SHOPIFY_BRIDGE_BASE_URL
require_env SHOP_DOMAIN
platform_login
if [[ -z "${PLATFORM_API_KEY}" && -z "${PLATFORM_SESSION_COOKIE_JAR}" ]]; then
  echo "Missing required auth: set PLATFORM_API_KEY, PLATFORM_SESSION_COOKIE_JAR, or PLATFORM_LOGIN_EMAIL/PLATFORM_LOGIN_PASSWORD"
  exit 2
fi

resolve_shopify_admin_access_token

echo "== Platform health =="
http_request GET "${platform_base}/actuator/health" ""
assert_equals "${HTTP_STATUS}" "200" "platform actuator health status"
platform_health_json="${HTTP_BODY}"
assert_equals "$(json_get "${platform_health_json}" "status")" "UP" "platform actuator health payload"

echo "== Shopify Bridge health =="
http_request GET "${bridge_base}/actuator/health" ""
assert_equals "${HTTP_STATUS}" "200" "bridge actuator health status"
bridge_health_json="${HTTP_BODY}"
assert_equals "$(json_get "${bridge_health_json}" "status")" "UP" "bridge actuator health payload"
ensure_companion_billing_state

echo "== Platform product service summary =="
platform_request GET "${platform_base}/api/product-services/${PRODUCT_SERVICE_REF}" "" "${platform_headers[@]-}"
assert_equals "${HTTP_STATUS}" "200" "product service summary status"
product_service_json="${HTTP_BODY}"
assert_equals "$(json_get "${product_service_json}" "serviceRef")" "${PRODUCT_SERVICE_REF}" "product service ref"
assert_equals "$(json_get "${product_service_json}" "status")" "${EXPECT_PRODUCT_SERVICE_STATUS}" "product service status"
assert_nonempty "$(json_get "${product_service_json}" "baseUrl")" "product service baseUrl"

echo "== Platform product service health =="
platform_request GET "${platform_base}/api/product-services/${PRODUCT_SERVICE_REF}/health" "" "${platform_headers[@]-}"
assert_equals "${HTTP_STATUS}" "200" "product service health status"
health_json="${HTTP_BODY}"
assert_nonempty "$(json_get "${health_json}" "status")" "product service health status value"
assert_nonempty "$(json_get "${health_json}" "lastProbeStatus")" "product service last probe status"

echo "== Platform product service overview =="
platform_request GET "${platform_base}/api/product-services/${PRODUCT_SERVICE_REF}/overview" "" "${platform_headers[@]-}"
assert_equals "${HTTP_STATUS}" "200" "product service overview status"
overview_json="${HTTP_BODY}"
assert_nonempty "$(json_get "${overview_json}" "stores.totalCount")" "overview store total count"
assert_nonempty "$(json_get "${overview_json}" "stores.platformAccessStatus")" "overview store platform access status"
assert_nonempty "$(json_get "${overview_json}" "usage.totalToday")" "overview usage total today"
assert_nonempty "$(json_get "${overview_json}" "usage.totalLast7Days")" "overview usage total last 7 days"
assert_nonempty "$(json_get "${overview_json}" "billing.mode")" "overview billing mode"

echo "== Platform product service Railway deployments =="
platform_request GET "${platform_base}/api/product-services/${PRODUCT_SERVICE_REF}/railway/deployments?limit=5" "" "${platform_headers[@]-}"
assert_equals "${HTTP_STATUS}" "200" "product service Railway deployments status"
product_service_deployments_json="${HTTP_BODY}"
assert_equals "$(json_get "${product_service_deployments_json}" "serviceRef")" "${PRODUCT_SERVICE_REF}" "product service deployment history serviceRef"
assert_equals "$(json_get "${product_service_deployments_json}" "available")" "true" "product service deployment history availability"
assert_nonempty "$(json_get "${product_service_deployments_json}" "railwayServiceId")" "product service Railway service id"
assert_nonempty "$(json_get "${product_service_deployments_json}" "deployments.0.id")" "product service latest Railway deployment id"

echo "== Platform product service Railway logs =="
latest_product_service_deployment_id="$(json_get "${product_service_deployments_json}" "deployments.0.id")"
for ((product_service_logs_attempt = 1; product_service_logs_attempt <= PRODUCT_SERVICE_LOGS_RETRY_ATTEMPTS; product_service_logs_attempt++)); do
  platform_request GET "${platform_base}/api/product-services/${PRODUCT_SERVICE_REF}/railway/logs?source=deployment&deploymentId=${latest_product_service_deployment_id}&limit=50" "" "${platform_headers[@]-}"
  product_service_logs_json="${HTTP_BODY}"
  product_service_logs_available="$(json_get "${product_service_logs_json}" "available")"
  product_service_logs_message="$(json_get "${product_service_logs_json}" "message")"
  if [[ "${HTTP_STATUS}" == "200" && "${product_service_logs_available}" == "true" ]]; then
    break
  fi
  if [[ "${product_service_logs_message}" == *"HTTP 429"* && "${product_service_logs_attempt}" -lt "${PRODUCT_SERVICE_LOGS_RETRY_ATTEMPTS}" ]]; then
    sleep "${PRODUCT_SERVICE_LOGS_RETRY_SLEEP_SECONDS}"
    continue
  fi
  break
done
assert_equals "${HTTP_STATUS}" "200" "product service Railway logs status"
assert_equals "$(json_get "${product_service_logs_json}" "serviceRef")" "${PRODUCT_SERVICE_REF}" "product service Railway logs serviceRef"
assert_equals "$(json_get "${product_service_logs_json}" "available")" "true" "product service Railway logs availability"
assert_equals "$(json_get "${product_service_logs_json}" "railwayDeploymentId")" "${latest_product_service_deployment_id}" "product service Railway logs deployment id"
assert_nonempty "$(json_get "${product_service_logs_json}" "queriedAt")" "product service Railway logs queriedAt"

echo "== Platform store summary =="
platform_request GET "${platform_base}/api/shopify/stores/${SHOP_DOMAIN}" "" "${platform_headers[@]-}"
assert_equals "${HTTP_STATUS}" "200" "platform store summary status"
store_json="${HTTP_BODY}"
assert_equals "$(json_get "${store_json}" "shopDomain")" "${SHOP_DOMAIN}" "platform store shopDomain"
assert_equals "$(json_get "${store_json}" "productServiceRef")" "${PRODUCT_SERVICE_REF}" "platform store serviceRef"
assert_equals "$(json_get "${store_json}" "installStatus")" "${EXPECT_INSTALL_STATUS}" "platform store installStatus"
assert_equals "$(json_get "${store_json}" "syncStatus")" "${EXPECT_SYNC_STATUS}" "platform store syncStatus"
assert_equals "$(json_get "${store_json}" "sourceReadinessStatus")" "${EXPECT_SOURCE_READINESS_STATUS}" "platform store sourceReadinessStatus"
assert_equals "$(json_get "${store_json}" "readiness.storefrontReady")" "${EXPECT_STOREFRONT_READY}" "platform storefront readiness"
assert_equals "$(json_get "${store_json}" "readiness.goLiveEligible")" "${EXPECT_GO_LIVE_ELIGIBLE}" "platform go-live eligibility"
assert_nonempty "$(json_get "${store_json}" "deploymentId")" "platform deploymentId"
assert_nonempty "$(json_get "${store_json}" "consumerId")" "platform consumerId"
assert_nonempty "$(json_get "${store_json}" "sourcePreflight.checkedAt")" "platform source preflight checkedAt"
assert_nonempty "$(json_get "${store_json}" "syncDetail.checkedAt")" "platform sync checkedAt"
assert_json_array_contains_csv "${store_json}" "capabilities.actionNames" "${EXPECT_REQUIRED_ACTIONS}" "platform store capability actionNames"

echo "== Platform store source coverage =="
JSON_PAYLOAD="${store_json}" python3 - <<'PY'
import json
import os

payload = json.loads(os.environ["JSON_PAYLOAD"])
preflight = payload.get("sourcePreflight") or {}
categories = preflight.get("categories") or []
category_map = {
    (entry.get("category") or "").strip(): entry
    for entry in categories
    if isinstance(entry, dict)
}

enabled_categories = [
    category
    for category, enabled in {
        "products": payload.get("productsEnabled"),
        "collections": payload.get("collectionsEnabled"),
        "pages": payload.get("pagesEnabled"),
        "policies": payload.get("policiesEnabled"),
        "articles": payload.get("articlesEnabled"),
        "metaobjects": payload.get("metaobjectsEnabled"),
    }.items()
    if enabled is True
]

missing = [category for category in enabled_categories if category not in category_map]
assert not missing, {"missingEnabledPreflightCategories": missing}
for category in enabled_categories:
    assert category_map[category].get("status"), {"missingEnabledPreflightStatus": category}
PY

echo "== Platform store binding inspection =="
platform_request GET "${platform_base}/api/shopify/stores/${SHOP_DOMAIN}/binding" "" "${platform_headers[@]-}"
assert_equals "${HTTP_STATUS}" "200" "platform store binding status"
store_binding_json="${HTTP_BODY}"
assert_equals "$(json_get "${store_binding_json}" "shopDomain")" "${SHOP_DOMAIN}" "platform store binding shopDomain"
assert_equals "$(json_get "${store_binding_json}" "productServiceRef")" "${PRODUCT_SERVICE_REF}" "platform store binding serviceRef"
assert_nonempty "$(json_get "${store_binding_json}" "customer.id")" "platform store binding customer id"
assert_nonempty "$(json_get "${store_binding_json}" "deployment.id")" "platform store binding deployment id"
assert_nonempty "$(json_get "${store_binding_json}" "consumer.consumerId")" "platform store binding consumer id"
assert_nonempty "$(json_get "${store_binding_json}" "latestVersion.id")" "platform store binding latest version id"
assert_nonempty "$(json_get "${store_binding_json}" "latestRelease.id")" "platform store binding latest release id"

echo "== Platform consumer runtime credentials =="
store_consumer_id="$(json_get "${store_json}" "consumerId")"
assert_nonempty "${store_consumer_id}" "platform store consumerId for credentials"
platform_request GET "${platform_base}/api/public/consumers/${store_consumer_id}/credentials" "" "${platform_headers[@]-}"
assert_equals "${HTTP_STATUS}" "200" "platform consumer credentials status"
platform_consumer_credentials_json="${HTTP_BODY}"
assert_equals "$(json_get "${platform_consumer_credentials_json}" "consumerId")" "${store_consumer_id}" "platform consumer credentials consumerId"
assert_equals "$(json_get "${platform_consumer_credentials_json}" "deploymentId")" "$(json_get "${store_json}" "deploymentId")" "platform consumer credentials deploymentId"
assert_equals "$(json_get "${platform_consumer_credentials_json}" "access.posture.runtimeAuthMode")" "PRIVATE_RUNTIME_SIGNED_ASSERTION" "platform consumer runtime auth mode"
assert_equals "$(json_get "${platform_consumer_credentials_json}" "integration.preferredIntegrationMode")" "BACKEND_MEDIATED_PRIVATE_RUNTIME" "platform consumer preferred integration mode"
assert_equals "$(json_get "${platform_consumer_credentials_json}" "access.trustedBackend.callerAuthConfigured")" "true" "platform consumer trusted backend caller auth"
assert_equals "$(json_get "${platform_consumer_credentials_json}" "access.trustedBackend.assertionValidationConfigured")" "true" "platform consumer trusted backend assertion validation"
assert_equals "$(json_get "${platform_consumer_credentials_json}" "access.trustedBackend.externalIntegrationReady")" "true" "platform consumer trusted backend externalIntegrationReady"
assert_nonempty "$(json_get "${platform_consumer_credentials_json}" "access.runtime.chatQueryUrl")" "platform consumer chat query url"
assert_nonempty "$(json_get "${platform_consumer_credentials_json}" "access.runtime.suggestionsUrl")" "platform consumer suggestions url"

echo "== Platform store binding inspection via product service =="
platform_request GET "${platform_base}/api/product-services/${PRODUCT_SERVICE_REF}/stores/${SHOP_DOMAIN}/binding" "" "${platform_headers[@]-}"
assert_equals "${HTTP_STATUS}" "200" "product service store binding status"
service_store_binding_json="${HTTP_BODY}"
assert_equals "$(json_get "${service_store_binding_json}" "shopDomain")" "${SHOP_DOMAIN}" "product service store binding shopDomain"
assert_equals "$(json_get "${service_store_binding_json}" "productServiceRef")" "${PRODUCT_SERVICE_REF}" "product service store binding serviceRef"
assert_equals "$(json_get "${service_store_binding_json}" "deployment.id")" "$(json_get "${store_binding_json}" "deployment.id")" "product service store binding deployment id"
assert_equals "$(json_get "${service_store_binding_json}" "consumer.consumerId")" "$(json_get "${store_binding_json}" "consumer.consumerId")" "product service store binding consumer id"

echo "== Platform store billing posture =="
platform_request GET "${platform_base}/api/product-services/${PRODUCT_SERVICE_REF}/stores/${SHOP_DOMAIN}/billing-summary" "" "${platform_headers[@]-}"
assert_equals "${HTTP_STATUS}" "200" "platform store billing summary status"
platform_store_billing_json="${HTTP_BODY}"
assert_equals "$(json_get "${platform_store_billing_json}" "shopDomain")" "${SHOP_DOMAIN}" "platform store billing shopDomain"
assert_nonempty "$(json_get "${platform_store_billing_json}" "mode")" "platform store billing mode"
assert_optional_equals "$(json_get "${platform_store_billing_json}" "status")" "${EXPECT_BILLING_STATUS}" "platform store billing status"
assert_optional_equals "$(json_get "${platform_store_billing_json}" "tierKey")" "${EXPECT_BILLING_TIER}" "platform store billing tier"
assert_optional_equals "$(json_get "${platform_store_billing_json}" "launchBlocked")" "${EXPECT_BILLING_LAUNCH_BLOCKED}" "platform store billing launchBlocked"
assert_optional_equals "$(json_get "${platform_store_billing_json}" "catalogProductCap")" "${EXPECT_CATALOG_PRODUCT_CAP}" "platform store billing catalogProductCap"
assert_optional_equals "$(json_get "${platform_store_billing_json}" "poweredByBadgeRequired")" "${EXPECT_POWERED_BY_BADGE_REQUIRED}" "platform store billing poweredByBadgeRequired"
assert_optional_equals "$(json_get "${platform_store_billing_json}" "chatFallbackEnabled")" "${EXPECT_CHAT_FALLBACK_ENABLED}" "platform store billing chatFallbackEnabled"
billing_allowed_surfaces_csv="$(json_array_to_csv "${platform_store_billing_json}" "allowedSurfaces")"
assert_nonempty "${billing_allowed_surfaces_csv}" "platform store billing allowedSurfaces"
assert_equals "$(json_get "${platform_store_billing_json}" "availablePlans.0.tierKey")" "FREE" "platform store billing availablePlans.0 tierKey"
assert_equals "$(json_get "${platform_store_billing_json}" "availablePlans.0.chatFallbackEnabled")" "false" "platform store billing FREE chatFallbackEnabled"
assert_json_array_contains_csv "${platform_store_billing_json}" "availablePlans.0.allowedSurfaces" "ai-search" "platform store billing FREE allowedSurfaces"
assert_json_array_not_contains_csv "${platform_store_billing_json}" "availablePlans.0.allowedSurfaces" "order-lookup" "platform store billing FREE allowedSurfaces"
assert_equals "$(json_get "${platform_store_billing_json}" "availablePlans.1.tierKey")" "STARTER" "platform store billing availablePlans.1 tierKey"
assert_equals "$(json_get "${platform_store_billing_json}" "availablePlans.1.chatFallbackEnabled")" "true" "platform store billing STARTER chatFallbackEnabled"
assert_json_array_contains_csv "${platform_store_billing_json}" "availablePlans.1.allowedSurfaces" "comparison" "platform store billing STARTER allowedSurfaces"
assert_json_array_not_contains_csv "${platform_store_billing_json}" "availablePlans.1.allowedSurfaces" "order-lookup" "platform store billing STARTER allowedSurfaces"
assert_equals "$(json_get "${platform_store_billing_json}" "availablePlans.2.tierKey")" "ELITE" "platform store billing availablePlans.2 tierKey"
assert_equals "$(json_get "${platform_store_billing_json}" "availablePlans.2.actionCapable")" "true" "platform store billing ELITE actionCapable"
assert_json_array_contains_csv "${platform_store_billing_json}" "availablePlans.2.allowedSurfaces" "comparison,order-lookup" "platform store billing ELITE allowedSurfaces"
if [[ "$(json_get "${platform_store_billing_json}" "availablePlans.2.commerciallyAvailable")" == "true" ]]; then
  assert_equals "$(json_get "${platform_store_billing_json}" "availablePlans.2.requiresExplicitConfirmation")" "true" "platform store billing ELITE requiresExplicitConfirmation"
  assert_equals "$(json_get "${platform_store_billing_json}" "availablePlans.2.auditTrailAvailable")" "true" "platform store billing ELITE auditTrailAvailable"
  assert_json_array_contains_csv "${platform_store_billing_json}" "availablePlans.2.actionPackages" "guided-commerce" "platform store billing ELITE actionPackages"
fi
effective_expected_surfaces="${EXPECT_ENABLED_SURFACES:-${billing_allowed_surfaces_csv}}"
effective_expected_order_lookup_supported="${EXPECT_ORDER_LOOKUP_SUPPORTED}"
if [[ -z "${effective_expected_order_lookup_supported}" ]]; then
  if printf '%s' "${effective_expected_surfaces}" | tr ',' '\n' | grep -Fxq "order-lookup"; then
    effective_expected_order_lookup_supported="true"
  else
    effective_expected_order_lookup_supported="false"
  fi
fi
effective_expected_order_lookup_scope_granted="${EXPECT_ORDER_LOOKUP_SCOPE_GRANTED}"
if [[ -z "${effective_expected_order_lookup_scope_granted}" && "${effective_expected_order_lookup_supported}" == "true" ]]; then
  effective_expected_order_lookup_scope_granted="true"
fi
effective_expected_order_lookup_app_scopes_webhook_ready="${EXPECT_ORDER_LOOKUP_APP_SCOPES_WEBHOOK_READY}"
effective_expected_historical_order_lookup_supported="${EXPECT_HISTORICAL_ORDER_LOOKUP_SUPPORTED}"
effective_expected_older_orders_require_broader_scope="${EXPECT_OLDER_ORDERS_REQUIRE_BROADER_SCOPE}"
if [[ -z "${effective_expected_older_orders_require_broader_scope}" ]]; then
  if [[ "${effective_expected_order_lookup_supported}" == "true" && "${effective_expected_historical_order_lookup_supported}" != "true" ]]; then
    effective_expected_older_orders_require_broader_scope="true"
  else
    effective_expected_older_orders_require_broader_scope="false"
  fi
fi
effective_bootstrap_expected_surfaces="${effective_expected_surfaces}"
if [[ "${effective_expected_order_lookup_supported}" != "true" ]]; then
  effective_bootstrap_expected_surfaces="$(printf '%s' "${effective_bootstrap_expected_surfaces}" | tr ',' '\n' | awk 'NF && $0 != "order-lookup"' | paste -sd, -)"
fi
effective_expected_billing_tier="${EXPECT_BILLING_TIER:-$(json_get "${platform_store_billing_json}" "tierKey")}"
effective_expected_catalog_product_cap="${EXPECT_CATALOG_PRODUCT_CAP:-$(json_get "${platform_store_billing_json}" "catalogProductCap")}"
effective_expected_powered_by_badge_required="${EXPECT_POWERED_BY_BADGE_REQUIRED:-$(json_get "${platform_store_billing_json}" "poweredByBadgeRequired")}"
effective_expected_chat_fallback_enabled="${EXPECT_CHAT_FALLBACK_ENABLED:-$(json_get "${platform_store_billing_json}" "chatFallbackEnabled")}"
effective_expected_action_capability_available="${EXPECT_ACTION_CAPABILITY_AVAILABLE:-$(json_get "${platform_store_billing_json}" "actionCapable")}"
effective_expected_action_requires_confirmation="${EXPECT_ACTION_REQUIRES_CONFIRMATION:-$(json_get "${platform_store_billing_json}" "requiresExplicitConfirmation")}"
effective_expected_action_audit_available="${EXPECT_ACTION_AUDIT_AVAILABLE:-$(json_get "${platform_store_billing_json}" "auditTrailAvailable")}"
effective_expected_max_widget_surface="${EXPECT_MAX_WIDGET_SURFACE:-${effective_expected_chat_fallback_enabled}}"

echo "== Platform store support readiness =="
platform_request GET "${platform_base}/api/product-services/${PRODUCT_SERVICE_REF}/stores/${SHOP_DOMAIN}/support-readiness" "" "${platform_headers[@]-}"
assert_equals "${HTTP_STATUS}" "200" "platform store support readiness status"
platform_store_support_json="${HTTP_BODY}"
assert_equals "$(json_get "${platform_store_support_json}" "shopDomain")" "${SHOP_DOMAIN}" "platform store support readiness shopDomain"
assert_equals "$(json_get "${platform_store_support_json}" "status")" "${EXPECT_ORDER_LOOKUP_STATUS}" "platform store support readiness posture"
assert_equals "$(json_get "${platform_store_support_json}" "orderLookupSupported")" "${effective_expected_order_lookup_supported}" "platform store order lookup supported"
assert_optional_equals "$(json_get "${platform_store_support_json}" "orderLookupScopeGranted")" "${effective_expected_order_lookup_scope_granted}" "platform store order lookup scope granted"
assert_optional_equals "$(json_get "${platform_store_support_json}" "appScopesUpdateWebhookReady")" "${effective_expected_order_lookup_app_scopes_webhook_ready}" "platform store scopes webhook ready"
assert_optional_equals "$(json_get "${platform_store_support_json}" "merchantHandoffConfigured")" "${EXPECT_ORDER_LOOKUP_MERCHANT_HANDOFF_CONFIGURED}" "platform store merchant handoff configured"
assert_optional_equals "$(json_get "${platform_store_support_json}" "lifecycleStage")" "${EXPECT_SUPPORT_LIFECYCLE_STAGE}" "platform store support lifecycle stage"
assert_optional_equals "$(json_get "${platform_store_support_json}" "allOrdersScopeGranted")" "${effective_expected_historical_order_lookup_supported}" "platform store historical order lookup support"
if [[ "${effective_expected_order_lookup_supported}" == "true" ]]; then
  assert_json_array_contains_csv "${platform_store_support_json}" "verificationMethods" "ORDER_NUMBER_AND_EMAIL" "platform store support verification methods"
  assert_json_array_contains_csv "${platform_store_support_json}" "supportedCapabilities" "order-status,tracking-link" "platform store support capabilities"
else
  assert_json_array_contains_csv "${platform_store_support_json}" "verificationMethods" "MERCHANT_SUPPORT_HANDOFF" "platform store support verification methods"
  if [[ "${effective_expected_order_lookup_scope_granted}" == "true" ]]; then
    assert_json_array_not_contains_csv "${platform_store_support_json}" "missingScopes" "read_orders" "platform store support missing scopes"
  elif [[ "${effective_expected_order_lookup_scope_granted}" == "false" ]]; then
    assert_json_array_contains_csv "${platform_store_support_json}" "missingScopes" "read_orders" "platform store support missing scopes"
  else
    assert_json_array_not_contains_csv "${platform_store_support_json}" "missingScopes" "read_orders" "platform store support missing scopes"
  fi
  assert_nonempty "$(json_get "${platform_store_support_json}" "nextActions.0")" "platform store support next action"
fi

echo "== Platform store webhook diagnostics =="
platform_request GET "${platform_base}/api/product-services/${PRODUCT_SERVICE_REF}/stores/${SHOP_DOMAIN}/webhook-subscriptions" "" "${platform_headers[@]-}"
assert_equals "${HTTP_STATUS}" "200" "platform store webhook diagnostics status"
platform_store_webhook_json="${HTTP_BODY}"
assert_equals "$(json_get "${platform_store_webhook_json}" "shopDomain")" "${SHOP_DOMAIN}" "platform store webhook shopDomain"
assert_nonempty "$(json_get "${platform_store_webhook_json}" "webhookUri")" "platform store webhook uri"
assert_nonempty "$(json_get "${platform_store_webhook_json}" "expectedCount")" "platform store webhook expectedCount"
assert_optional_equals "$(json_get "${platform_store_webhook_json}" "status")" "${EXPECT_WEBHOOK_STATUS}" "platform store webhook status"

echo "== Platform store vectorization overview =="
platform_request GET "${platform_base}/api/shopify/stores/${SHOP_DOMAIN}/vectorization" "" "${platform_headers[@]-}"
assert_equals "${HTTP_STATUS}" "200" "platform store vectorization summary status"
platform_store_vectorization_json="${HTTP_BODY}"
assert_equals "$(json_get "${platform_store_vectorization_json}" "shopDomain")" "${SHOP_DOMAIN}" "platform store vectorization shopDomain"
assert_nonempty "$(json_get "${platform_store_vectorization_json}" "selectedCategories.0")" "platform store vectorization selectedCategories"
assert_nonempty "$(json_get "${platform_store_vectorization_json}" "selectedEntityTypes.0")" "platform store vectorization selectedEntityTypes"
assert_nonempty "$(json_get "${platform_store_vectorization_json}" "policy.policyVersion")" "platform store vectorization policy version"

echo "== Platform store governed actions =="
platform_request GET "${platform_base}/api/shopify/stores/${SHOP_DOMAIN}/actions/recent?limit=5" "" "${platform_headers[@]-}"
if [[ "${HTTP_STATUS}" == "200" ]]; then
  platform_store_governed_actions_json="${HTTP_BODY}"
elif [[ "${effective_expected_action_capability_available}" == "true" ]]; then
  assert_equals "${HTTP_STATUS}" "200" "platform store governed actions status"
else
  echo "Skipping platform store governed actions assertion because action capability is not expected and the endpoint returned HTTP ${HTTP_STATUS}."
  platform_store_governed_actions_json="[]"
fi
assert_nonempty "$(json_get "${platform_store_vectorization_json}" "policy.sourcePolicies.0.sourceCategory")" "platform store vectorization source policy category"
assert_nonempty "$(json_get "${platform_store_vectorization_json}" "policy.sourcePolicies.0.updateTriggerMode")" "platform store vectorization update trigger mode"
assert_nonempty "$(json_get "${platform_store_vectorization_json}" "effectiveIndexedFields.0.fieldKey")" "platform store vectorization indexed field"
assert_nonempty "$(json_get "${platform_store_vectorization_json}" "automation.autoIndexingHealthy")" "platform store vectorization automation health"
assert_nonempty "$(json_get "${platform_store_vectorization_json}" "recentEvents")" "platform store vectorization recent events payload"

echo "== Platform store vectorization events =="
platform_request GET "${platform_base}/api/shopify/stores/${SHOP_DOMAIN}/vectorization/events?limit=5" "" "${platform_headers[@]-}"
assert_equals "${HTTP_STATUS}" "200" "platform store vectorization events status"
platform_store_vectorization_events_json="${HTTP_BODY}"
JSON_PAYLOAD="${platform_store_vectorization_json}" JSON_EVENTS="${platform_store_vectorization_events_json}" JSON_STORE="${store_json}" python3 - <<'PY'
import json
import os
import sys

summary = json.loads(os.environ["JSON_PAYLOAD"])
events = json.loads(os.environ["JSON_EVENTS"])
store = json.loads(os.environ["JSON_STORE"])

assert isinstance(events, list), {"eventsType": type(events).__name__}
assert isinstance(summary.get("policy"), dict), {"missing": "policy"}
assert isinstance(summary.get("automation"), dict), {"missing": "automation"}
assert isinstance(summary.get("recentEvents"), list), {"missing": "recentEvents"}
assert isinstance(summary.get("effectiveIndexedFields"), list), {"missing": "effectiveIndexedFields"}

selected_categories = summary.get("selectedCategories") or []
selected_entity_types = summary.get("selectedEntityTypes") or []
source_policies = summary["policy"].get("sourcePolicies") or []
policy_categories = {entry.get("sourceCategory") for entry in source_policies if isinstance(entry, dict)}

missing_policies = [category for category in selected_categories if category not in policy_categories]
assert not missing_policies, {"missingPolicyCategories": missing_policies, "policyCategories": sorted(policy_categories)}

indexed_fields = summary.get("effectiveIndexedFields") or []
indexed_entity_types = {entry.get("entityType") for entry in indexed_fields if isinstance(entry, dict)}
missing_indexed = [entity_type for entity_type in selected_entity_types if entity_type not in indexed_entity_types]
assert not missing_indexed, {"missingIndexedEntityTypes": missing_indexed, "indexedEntityTypes": sorted(indexed_entity_types)}

recent_events = summary.get("recentEvents") or []
assert len(recent_events) <= 20, {"recentEventsCount": len(recent_events)}
for event in events:
    assert isinstance(event, dict), {"badEvent": event}
    assert event.get("sourceCategory"), {"missingEventSourceCategory": event}
    assert event.get("entityType"), {"missingEventEntityType": event}

if store.get("articlesEnabled") is True:
    assert "articles" in selected_categories, {"missingArticlesSelectedCategory": selected_categories}
    assert "articles" in policy_categories, {"missingArticlesPolicyCategory": sorted(policy_categories)}
if store.get("metaobjectsEnabled") is True:
    assert "metaobjects" in selected_categories, {"missingMetaobjectsSelectedCategory": selected_categories}
    assert "metaobjects" in policy_categories, {"missingMetaobjectsPolicyCategory": sorted(policy_categories)}
PY

echo "== Bridge shell =="
http_request GET "${bridge_base}/api/app/shell"
assert_equals "${HTTP_STATUS}" "200" "bridge shell status"
shell_json="${HTTP_BODY}"
assert_equals "$(json_get "${shell_json}" "serviceRef")" "${PRODUCT_SERVICE_REF}" "bridge shell serviceRef"
assert_nonempty "$(json_get "${shell_json}" "appName")" "bridge shell appName"

if [[ "${EXPECT_EMBEDDED_APP_UI}" == "true" ]]; then
  echo "== Bridge embedded app shell =="
  http_request_text GET "${bridge_base}/?shop=${SHOP_DOMAIN}"
  assert_equals "${HTTP_STATUS}" "200" "bridge embedded app shell status"
  embedded_app_html="${HTTP_BODY}"
  assert_contains "${embedded_app_html}" "<div id=\"root\"></div>" "bridge embedded app root"
  embedded_asset_path="$(extract_first_asset_path "${embedded_app_html}")"
  assert_nonempty "${embedded_asset_path}" "bridge embedded app asset path"

  echo "== Bridge embedded app asset =="
  http_request_text GET "${bridge_base}${embedded_asset_path}"
  assert_equals "${HTTP_STATUS}" "200" "bridge embedded app asset status"
fi

echo "== Bridge admin overview =="
if [[ -n "${SHOPIFY_BRIDGE_ADMIN_API_KEY}" ]]; then
  http_request GET "${bridge_base}/api/admin/overview" "" "${SHOPIFY_BRIDGE_ADMIN_API_KEY_HEADER}: ${SHOPIFY_BRIDGE_ADMIN_API_KEY}"
  assert_equals "${HTTP_STATUS}" "200" "bridge admin overview status"
  admin_overview_json="${HTTP_BODY}"
  assert_nonempty "$(json_get "${admin_overview_json}" "serviceRef")" "bridge admin overview serviceRef"

  http_request GET "${bridge_base}/api/admin/stores/${SHOP_DOMAIN}/billing-summary" "" "${SHOPIFY_BRIDGE_ADMIN_API_KEY_HEADER}: ${SHOPIFY_BRIDGE_ADMIN_API_KEY}"
  assert_equals "${HTTP_STATUS}" "200" "bridge admin store billing status"
  bridge_admin_billing_json="${HTTP_BODY}"
  assert_nonempty "$(json_get "${bridge_admin_billing_json}" "mode")" "bridge admin store billing mode"
  assert_optional_equals "$(json_get "${bridge_admin_billing_json}" "status")" "${EXPECT_BILLING_STATUS}" "bridge admin store billing status"
  assert_optional_equals "$(json_get "${bridge_admin_billing_json}" "tierKey")" "${effective_expected_billing_tier}" "bridge admin store billing tier"
  assert_optional_equals "$(json_get "${bridge_admin_billing_json}" "catalogProductCap")" "${effective_expected_catalog_product_cap}" "bridge admin store billing catalogProductCap"
  assert_optional_equals "$(json_get "${bridge_admin_billing_json}" "poweredByBadgeRequired")" "${effective_expected_powered_by_badge_required}" "bridge admin store billing poweredByBadgeRequired"
  assert_optional_equals "$(json_get "${bridge_admin_billing_json}" "chatFallbackEnabled")" "${effective_expected_chat_fallback_enabled}" "bridge admin store billing chatFallbackEnabled"

  http_request GET "${bridge_base}/api/admin/stores/${SHOP_DOMAIN}/webhook-subscriptions" "" "${SHOPIFY_BRIDGE_ADMIN_API_KEY_HEADER}: ${SHOPIFY_BRIDGE_ADMIN_API_KEY}"
  assert_equals "${HTTP_STATUS}" "200" "bridge admin store webhook diagnostics status"
  bridge_admin_webhook_json="${HTTP_BODY}"
  assert_nonempty "$(json_get "${bridge_admin_webhook_json}" "expectedCount")" "bridge admin store webhook expectedCount"
  assert_optional_equals "$(json_get "${bridge_admin_webhook_json}" "status")" "${EXPECT_WEBHOOK_STATUS}" "bridge admin store webhook status"

  http_request GET "${bridge_base}/api/admin/stores/${SHOP_DOMAIN}/support-readiness" "" "${SHOPIFY_BRIDGE_ADMIN_API_KEY_HEADER}: ${SHOPIFY_BRIDGE_ADMIN_API_KEY}"
  assert_equals "${HTTP_STATUS}" "200" "bridge admin support readiness status"
  bridge_admin_support_json="${HTTP_BODY}"
  assert_equals "$(json_get "${bridge_admin_support_json}" "status")" "${EXPECT_ORDER_LOOKUP_STATUS}" "bridge admin support readiness posture"
  assert_equals "$(json_get "${bridge_admin_support_json}" "orderLookupSupported")" "${effective_expected_order_lookup_supported}" "bridge admin order lookup supported"
  assert_optional_equals "$(json_get "${bridge_admin_support_json}" "orderLookupScopeGranted")" "${effective_expected_order_lookup_scope_granted}" "bridge admin order lookup scope granted"
  assert_optional_equals "$(json_get "${bridge_admin_support_json}" "appScopesUpdateWebhookReady")" "${effective_expected_order_lookup_app_scopes_webhook_ready}" "bridge admin scopes webhook ready"
  assert_optional_equals "$(json_get "${bridge_admin_support_json}" "merchantHandoffConfigured")" "${EXPECT_ORDER_LOOKUP_MERCHANT_HANDOFF_CONFIGURED}" "bridge admin merchant handoff configured"
  assert_optional_equals "$(json_get "${bridge_admin_support_json}" "lifecycleStage")" "${EXPECT_SUPPORT_LIFECYCLE_STAGE}" "bridge admin support lifecycle stage"

  http_request GET "${bridge_base}/api/admin/stores/${SHOP_DOMAIN}/usage-summary" "" "${SHOPIFY_BRIDGE_ADMIN_API_KEY_HEADER}: ${SHOPIFY_BRIDGE_ADMIN_API_KEY}"
  assert_equals "${HTTP_STATUS}" "200" "bridge admin store usage summary status"
  bridge_admin_usage_json="${HTTP_BODY}"
  assert_nonempty "$(json_get "${bridge_admin_usage_json}" "shopDomain")" "bridge admin store usage shopDomain"
  assert_nonempty "$(json_get "${bridge_admin_usage_json}" "generatedAt")" "bridge admin store usage generatedAt"

  http_request GET "${bridge_base}/api/admin/stores/${SHOP_DOMAIN}/vectorization" "" "${SHOPIFY_BRIDGE_ADMIN_API_KEY_HEADER}: ${SHOPIFY_BRIDGE_ADMIN_API_KEY}"
  assert_equals "${HTTP_STATUS}" "200" "bridge admin vectorization status"
  bridge_admin_vectorization_json="${HTTP_BODY}"
  assert_nonempty "$(json_get "${bridge_admin_vectorization_json}" "shopDomain")" "bridge admin vectorization shopDomain"
  assert_nonempty "$(json_get "${bridge_admin_vectorization_json}" "readyToRun")" "bridge admin vectorization readyToRun"

  http_request GET "${bridge_base}/api/admin/stores/${SHOP_DOMAIN}/actions/recent?limit=5" "" "${SHOPIFY_BRIDGE_ADMIN_API_KEY_HEADER}: ${SHOPIFY_BRIDGE_ADMIN_API_KEY}"
  assert_equals "${HTTP_STATUS}" "200" "bridge admin governed actions status"
  bridge_admin_actions_json="${HTTP_BODY}"

  echo "== Bridge admin vectorization source page =="
  bridge_vector_entity_type="$(json_get "${platform_store_vectorization_json}" "selectedEntityTypes.0")"
  assert_nonempty "${bridge_vector_entity_type}" "bridge admin vectorization entity type"
  http_request GET "${bridge_base}/api/admin/stores/${SHOP_DOMAIN}/vectorization-source/${bridge_vector_entity_type}?limit=1" "" "${SHOPIFY_BRIDGE_ADMIN_API_KEY_HEADER}: ${SHOPIFY_BRIDGE_ADMIN_API_KEY}"
  assert_equals "${HTTP_STATUS}" "200" "bridge admin vectorization source page status"
  bridge_vector_page_json="${HTTP_BODY}"
  assert_equals "$(json_get "${bridge_vector_page_json}" "entityType")" "${bridge_vector_entity_type}" "bridge admin vectorization entityType"
  assert_nonempty "$(json_get "${bridge_vector_page_json}" "totalCount")" "bridge admin vectorization source totalCount"
else
  echo "Skipping bridge admin overview because SHOPIFY_BRIDGE_ADMIN_API_KEY is not configured."
fi

if [[ -n "${SHOPIFY_ADMIN_ACCESS_TOKEN}" ]]; then
  echo "== Shopify webhook subscriptions =="
  webhook_query='{"query":"query ShopifyBridgeWebhookSubscriptions { webhookSubscriptions(first: 50) { edges { node { topic uri name } } } }"}'
  http_request POST "https://${SHOP_DOMAIN}/admin/api/${SHOPIFY_ADMIN_API_VERSION}/graphql.json" "${webhook_query}" "X-Shopify-Access-Token: ${SHOPIFY_ADMIN_ACCESS_TOKEN}"
  if [[ "${HTTP_STATUS}" == "200" ]]; then
    assert_shopify_webhook_subscriptions "${HTTP_BODY}" "${bridge_base}/api/webhooks/shopify"
  elif [[ "${SHOPIFY_ADMIN_ACCESS_TOKEN_SOURCE}" == "explicit" ]]; then
    assert_equals "${HTTP_STATUS}" "200" "shopify webhook subscription query status"
  else
    echo "Skipping Shopify webhook subscription verification because resolved store credentials are not valid for Shopify Admin GraphQL (HTTP ${HTTP_STATUS})."
  fi
else
  echo "Skipping Shopify webhook subscription verification because SHOPIFY_ADMIN_ACCESS_TOKEN is not configured."
fi

echo "== Storefront bootstrap =="
http_request GET "${bridge_base}/api/storefront/shops/${SHOP_DOMAIN}/bootstrap"
assert_equals "${HTTP_STATUS}" "200" "storefront bootstrap status"
bootstrap_json="${HTTP_BODY}"
assert_equals "$(json_get "${bootstrap_json}" "shopDomain")" "${SHOP_DOMAIN}" "storefront bootstrap shopDomain"
assert_equals "$(json_get "${bootstrap_json}" "available")" "${EXPECT_STOREFRONT_SHOPPER_TRAFFIC_READY}" "storefront bootstrap availability"
assert_nonempty "$(json_get "${bootstrap_json}" "bridgeQueryUrl")" "storefront bridgeQueryUrl"
assert_nonempty "$(json_get "${bootstrap_json}" "bridgeSuggestionsUrl")" "storefront bridgeSuggestionsUrl"
assert_nonempty "$(json_get "${bootstrap_json}" "bridgeOrderLookupUrl")" "storefront bridgeOrderLookupUrl"
assert_nonempty "$(json_get "${bootstrap_json}" "defaultConversationMode")" "storefront defaultConversationMode"
assert_nonempty "$(json_get "${bootstrap_json}" "effectiveConversationMode")" "storefront effectiveConversationMode"
assert_nonempty "$(json_get "${bootstrap_json}" "allowedConversationModes.0")" "storefront allowedConversationModes.0"
assert_equals "$(json_get "${bootstrap_json}" "orderLookupEnabled")" "${effective_expected_order_lookup_supported}" "storefront bootstrap orderLookupEnabled"
assert_equals "$(json_get "${bootstrap_json}" "olderOrdersRequireBroaderScope")" "${effective_expected_older_orders_require_broader_scope}" "storefront bootstrap historical order access"
assert_nonempty "$(json_get "${bootstrap_json}" "orderLookupMessage")" "storefront bootstrap orderLookupMessage"
assert_optional_equals "$(json_get "${bootstrap_json}" "billingTier")" "${effective_expected_billing_tier}" "storefront bootstrap billingTier"
assert_optional_equals "$(json_get "${bootstrap_json}" "billingStatus")" "${EXPECT_BILLING_STATUS}" "storefront bootstrap billingStatus"
assert_optional_equals "$(json_get "${bootstrap_json}" "catalogProductCap")" "${effective_expected_catalog_product_cap}" "storefront bootstrap catalogProductCap"
assert_optional_equals "$(json_get "${bootstrap_json}" "poweredByBadgeRequired")" "${effective_expected_powered_by_badge_required}" "storefront bootstrap poweredByBadgeRequired"
assert_optional_equals "$(json_get "${bootstrap_json}" "chatFallbackEnabled")" "${effective_expected_chat_fallback_enabled}" "storefront bootstrap chatFallbackEnabled"
assert_optional_equals "$(json_get "${bootstrap_json}" "shellModeProfile")" "${EXPECT_SHELL_MODE_PROFILE}" "storefront bootstrap shellModeProfile"
assert_json_array_contains_csv "${bootstrap_json}" "groundingSignals" "Catalog product grounding,Policy grounding" "storefront bootstrap groundingSignals"
if [[ "$(json_get "${store_json}" "productsEnabled")" == "true" ]]; then
  assert_json_array_contains_csv "${bootstrap_json}" "supportedReviewProviders" "Judge.me,Okendo" "storefront bootstrap supportedReviewProviders"
fi
if [[ "$(json_get "${store_json}" "articlesEnabled")" == "true" ]]; then
  assert_json_array_contains_csv "${bootstrap_json}" "groundingSignals" "Published article grounding" "storefront bootstrap article grounding"
fi
if [[ "$(json_get "${store_json}" "metaobjectsEnabled")" == "true" ]]; then
  assert_json_array_contains_csv "${bootstrap_json}" "groundingSignals" "Metaobject grounding" "storefront bootstrap metaobject grounding"
fi
assert_optional_equals "$(json_get "${bootstrap_json}" "actionCapability.available")" "${effective_expected_action_capability_available}" "storefront bootstrap action capability available"
assert_optional_equals "$(json_get "${bootstrap_json}" "actionCapability.requiresExplicitConfirmation")" "${effective_expected_action_requires_confirmation}" "storefront bootstrap action requires confirmation"
assert_optional_equals "$(json_get "${bootstrap_json}" "actionCapability.auditTrailAvailable")" "${effective_expected_action_audit_available}" "storefront bootstrap action audit available"
assert_nonempty "$(json_get "${bootstrap_json}" "actionCapability.message")" "storefront bootstrap action capability message"
assert_json_array_contains_csv "${bootstrap_json}" "enabledSurfaces" "${effective_bootstrap_expected_surfaces}" "storefront bootstrap enabledSurfaces"
if [[ "${effective_expected_action_capability_available}" == "true" ]]; then
  assert_json_array_contains_csv "${bootstrap_json}" "actionCapability.actionPackages" "guided-commerce" "storefront bootstrap action packages"
  assert_json_array_contains_csv "${bootstrap_json}" "actionCapability.allowedActionTypes" "ADD_TO_CART,UPDATE_CART_QUANTITY" "storefront bootstrap governed action types"
  assert_nonempty "$(json_get "${bootstrap_json}" "actionCapability.grantUrl")" "storefront bootstrap action grantUrl"
  assert_nonempty "$(json_get "${bootstrap_json}" "actionCapability.completeUrl")" "storefront bootstrap action completeUrl"
fi

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
retry_storefront_query "${bridge_base}/api/storefront/shops/${SHOP_DOMAIN}/chat/query" "${query_payload}" "X-AI-FABRIC-SHOPPER-SESSION-ID: ${SHOPPER_SESSION_ID}"
assert_equals "${HTTP_STATUS}" "200" "storefront query status"
query_json="${HTTP_BODY}"
assert_nonempty "$(json_get "${query_json}" "conversationId")" "storefront query conversationId"
storefront_query_summary="$(json_get "${query_json}" "safeSummary")"
if [[ -z "${storefront_query_summary}" ]]; then
  storefront_query_summary="$(json_get "${query_json}" "answer")"
fi
if [[ -z "${storefront_query_summary}" ]]; then
  storefront_query_summary="$(json_get "${query_json}" "message")"
fi
if [[ -z "${storefront_query_summary}" ]]; then
  storefront_query_summary="$(json_get "${query_json}" "message")"
fi
assert_nonempty "${storefront_query_summary}" "storefront query summary"
if [[ "${effective_expected_chat_fallback_enabled}" == "false" && ",${effective_expected_surfaces}," == *",ai-search,"* ]]; then
  echo "PASS: storefront standalone AI search contract"
fi

if [[ "${effective_expected_max_widget_surface}" == "true" ]]; then
  echo "== Storefront Max Mode query =="
  max_widget_query_payload="$(python3 - <<'PY'
import json
print(json.dumps({
    "query": "Open the storefront assistant and recommend one product from this store.",
    "context": {
        "pageType": "product",
        "shopifyShellModeProfile": "SHOPIFY_COMPANION",
        "shopifySurfaceEntry": "max-mode",
        "shopifyPageModeGroup": "product",
        "shopifyEffectiveConversationMode": "navigator_deep",
    },
}))
PY
)"
  retry_storefront_query "${bridge_base}/api/storefront/shops/${SHOP_DOMAIN}/chat/query" "${max_widget_query_payload}" "X-AI-FABRIC-SHOPPER-SESSION-ID: ${SHOPPER_SESSION_ID}"
  assert_equals "${HTTP_STATUS}" "200" "storefront Max Mode query status"
  max_widget_query_json="${HTTP_BODY}"
  assert_nonempty "$(json_get "${max_widget_query_json}" "conversationId")" "storefront Max Mode query conversationId"
  max_widget_query_summary="$(json_get "${max_widget_query_json}" "safeSummary")"
  if [[ -z "${max_widget_query_summary}" ]]; then
    max_widget_query_summary="$(json_get "${max_widget_query_json}" "answer")"
  fi
  if [[ -z "${max_widget_query_summary}" ]]; then
    max_widget_query_summary="$(json_get "${max_widget_query_json}" "message")"
  fi
  if [[ -z "${max_widget_query_summary}" ]]; then
    max_widget_query_summary="$(json_get "${max_widget_query_json}" "message")"
  fi
  assert_nonempty "${max_widget_query_summary}" "storefront Max Mode query summary"
fi

if [[ ",${effective_expected_surfaces}," == *",comparison,"* ]]; then
  echo "== Storefront comparison query =="
  comparison_query_payload="$(python3 - <<'PY'
import json
print(json.dumps({"query": 'Compare the current product with similar options in this store and explain who should choose each one.'}))
PY
)"
  retry_storefront_query "${bridge_base}/api/storefront/shops/${SHOP_DOMAIN}/chat/query" "${comparison_query_payload}" "X-AI-FABRIC-SHOPPER-SESSION-ID: ${SHOPPER_SESSION_ID}"
  assert_equals "${HTTP_STATUS}" "200" "storefront comparison query status"
  comparison_query_json="${HTTP_BODY}"
  comparison_query_summary="$(json_get "${comparison_query_json}" "safeSummary")"
  if [[ -z "${comparison_query_summary}" ]]; then
    comparison_query_summary="$(json_get "${comparison_query_json}" "answer")"
  fi
  if [[ -z "${comparison_query_summary}" ]]; then
    comparison_query_summary="$(json_get "${comparison_query_json}" "message")"
  fi
  if [[ -z "${comparison_query_summary}" ]]; then
    comparison_query_summary="$(json_get "${comparison_query_json}" "message")"
  fi
  assert_nonempty "${comparison_query_summary}" "storefront comparison query summary"

  if [[ -n "${SHOPIFY_BRIDGE_ADMIN_API_KEY}" ]]; then
    echo "== Storefront comparison resolver query =="
    comparison_action_payload="$(python3 - <<'PY' "${SHOP_DOMAIN}"
import json
import sys

shop_domain = sys.argv[1]
print(json.dumps({
    "actionId": "shopify_search_catalog",
    "params": {
        "query": "shirt",
        "country": "US",
        "intent": "product comparison",
        "limit": 5
    },
    "trace": {
        "shopDomain": shop_domain,
        "actionConfig": {
            "adapterType": "mcp-tool",
            "execution": {
                "adapterType": "mcp-tool",
                "mcp": {
                    "serverRef": "shopify-storefront",
                    "endpointKind": "STOREFRONT_STANDARD",
                    "toolName": "search_catalog",
                    "argumentTemplate": {
                        "catalog": {
                            "query": "{{params.query}}",
                            "context": {
                                "address_country": "{{params.country}}",
                                "intent": "{{params.intent}}"
                            },
                            "pagination": {
                                "limit": "{{params.limit}}"
                            }
                        }
                    }
                }
            }
        }
    }
}))
PY
)"
    http_request POST "${bridge_base}/api/admin/stores/${SHOP_DOMAIN}/actions/execute" "${comparison_action_payload}" "${SHOPIFY_BRIDGE_ADMIN_API_KEY_HEADER}: ${SHOPIFY_BRIDGE_ADMIN_API_KEY}"
    assert_equals "${HTTP_STATUS}" "200" "bridge admin search catalog status"
    comparison_terms_csv="$(JSON_PAYLOAD="${HTTP_BODY}" python3 - <<'PY'
import json
import os

payload = json.loads(os.environ["JSON_PAYLOAD"])
terms = []

def add(value):
    value = str(value or "").strip()
    if value and value not in terms:
        terms.append(value)

for item in (((payload or {}).get("data") or {}).get("items") or []):
    if not isinstance(item, dict):
        continue
    add(item.get("primarySku"))
    add(item.get("title"))

tool_result = ((payload or {}).get("data") or {}).get("toolResult") or {}
for content_item in tool_result.get("content") or []:
    if not isinstance(content_item, dict) or content_item.get("type") != "text":
        continue
    try:
        catalog_payload = json.loads(content_item.get("text") or "{}")
    except json.JSONDecodeError:
        continue
    for product in catalog_payload.get("products") or []:
        if not isinstance(product, dict):
            continue
        product_title = str(product.get("title") or "").strip()
        add(product_title)
        for variant in product.get("variants") or []:
            if not isinstance(variant, dict):
                continue
            variant_title = str(variant.get("title") or "").strip()
            add(" ".join(part for part in [product_title, variant_title] if part))
            add(variant.get("id"))
            if len(terms) >= 2:
                break
        if len(terms) >= 2:
            break
    if len(terms) >= 2:
        break

print(",".join(terms[:2]))
PY
)"
    assert_nonempty "${comparison_terms_csv}" "bridge admin comparison product sample"
    comparison_reference_term="${comparison_terms_csv%%,*}"
    comparison_secondary_term="${comparison_terms_csv#*,}"
    assert_nonempty "${comparison_reference_term}" "comparison reference product"
    assert_nonempty "${comparison_secondary_term}" "comparison secondary product"

    comparison_resolver_payload="$(python3 - <<'PY' "${comparison_reference_term}" "${comparison_secondary_term}" "${SHOPIFY_COMPARISON_MODE}"
import json
import sys

reference_term = sys.argv[1]
comparison_term = sys.argv[2]
mode = sys.argv[3]
print(json.dumps({
    "query": f"Compare {reference_term} and {comparison_term} and explain the tradeoffs.",
    "mode": mode,
    "context": {
        "pageType": "product",
        "pageTitle": "Verification comparison page",
        "shopifySurfaceEntry": "comparison",
        "shopifyShellModeProfile": "SHOPIFY_COMPANION"
    }
}))
PY
)"
    retry_storefront_query "${bridge_base}/api/storefront/shops/${SHOP_DOMAIN}/chat/query" "${comparison_resolver_payload}" "X-AI-FABRIC-SHOPPER-SESSION-ID: ${SHOPPER_SESSION_ID}"
    assert_equals "${HTTP_STATUS}" "200" "storefront comparison resolver query status"
    comparison_resolver_json="${HTTP_BODY}"
    comparison_resolver_summary="$(json_get "${comparison_resolver_json}" "safeSummary")"
    if [[ -z "${comparison_resolver_summary}" ]]; then
      comparison_resolver_summary="$(json_get "${comparison_resolver_json}" "answer")"
    fi
    if [[ -z "${comparison_resolver_summary}" ]]; then
      comparison_resolver_summary="$(json_get "${comparison_resolver_json}" "message")"
    fi
    if [[ -z "${comparison_resolver_summary}" ]]; then
      comparison_resolver_summary="$(json_get "${comparison_resolver_json}" "message")"
    fi
    assert_nonempty "${comparison_resolver_summary}" "storefront comparison resolver summary"
  fi
fi

echo "== Storefront event =="
http_request POST "${bridge_base}/api/storefront/shops/${SHOP_DOMAIN}/events" '{"eventType":"WIDGET_OPENED","pageType":"product","pageTitle":"Verification product page"}' "X-AI-FABRIC-SHOPPER-SESSION-ID: ${SHOPPER_SESSION_ID}"
assert_equals "${HTTP_STATUS}" "202" "storefront event status"

echo "== Platform store summary after storefront bootstrap =="
platform_request GET "${platform_base}/api/shopify/stores/${SHOP_DOMAIN}" "" "${platform_headers[@]-}"
assert_equals "${HTTP_STATUS}" "200" "platform store summary after bootstrap status"
store_after_bootstrap_json="${HTTP_BODY}"
assert_equals "$(json_get "${store_after_bootstrap_json}" "widgetStatus")" "${EXPECT_WIDGET_STATUS}" "platform store widgetStatus"
assert_nonempty "$(json_get "${store_after_bootstrap_json}" "widgetDetail.message")" "platform widget message"
assert_optional_equals "$(json_get "${store_after_bootstrap_json}" "widgetDetail.settings.shellModeProfile")" "${EXPECT_SHELL_MODE_PROFILE}" "platform widget shellModeProfile"
if [[ -n "${EXPECT_CONFIGURED_ENABLED_SURFACES}" ]]; then
  assert_json_array_contains_csv "${store_after_bootstrap_json}" "widgetDetail.settings.enabledSurfaces" "${EXPECT_CONFIGURED_ENABLED_SURFACES}" "platform widget configured enabledSurfaces"
else
  assert_nonempty "$(json_get "${store_after_bootstrap_json}" "widgetDetail.settings.enabledSurfaces.0")" "platform widget enabledSurfaces.0"
fi

if [[ -n "${SHOPIFY_MERCHANT_AUTHORIZATION}" ]]; then
  echo "== Merchant session =="
  declare -a merchant_headers=("Authorization: ${SHOPIFY_MERCHANT_AUTHORIZATION}")
  if [[ -n "${SHOPIFY_EMBEDDED_HOST}" ]]; then
    merchant_headers+=("X-Shopify-Embedded-Host: ${SHOPIFY_EMBEDDED_HOST}")
  fi
  http_request GET "${bridge_base}/api/app/session" "" "${merchant_headers[@]-}"
  assert_equals "${HTTP_STATUS}" "200" "merchant session status"
  merchant_session_json="${HTTP_BODY}"
  assert_equals "$(json_get "${merchant_session_json}" "shopDomain")" "${SHOP_DOMAIN}" "merchant session shopDomain"
  assert_nonempty "$(json_get "${merchant_session_json}" "userId")" "merchant session userId"
  assert_equals "$(json_get "${merchant_session_json}" "supportReadiness.status")" "${EXPECT_ORDER_LOOKUP_STATUS}" "merchant session support readiness posture"
  assert_equals "$(json_get "${merchant_session_json}" "supportReadiness.orderLookupSupported")" "${effective_expected_order_lookup_supported}" "merchant session order lookup supported"
  assert_optional_equals "$(json_get "${merchant_session_json}" "supportReadiness.orderLookupScopeGranted")" "${effective_expected_order_lookup_scope_granted}" "merchant session order lookup scope granted"

  echo "== Merchant billing summary =="
  http_request GET "${bridge_base}/api/app/store/billing-summary" "" "${merchant_headers[@]-}"
  assert_equals "${HTTP_STATUS}" "200" "merchant billing summary status"
  merchant_billing_json="${HTTP_BODY}"
  assert_nonempty "$(json_get "${merchant_billing_json}" "mode")" "merchant billing mode"
  assert_optional_equals "$(json_get "${merchant_billing_json}" "status")" "${EXPECT_BILLING_STATUS}" "merchant billing status"
  assert_optional_equals "$(json_get "${merchant_billing_json}" "tierKey")" "${effective_expected_billing_tier}" "merchant billing tier"
  assert_optional_equals "$(json_get "${merchant_billing_json}" "launchBlocked")" "${EXPECT_BILLING_LAUNCH_BLOCKED}" "merchant billing launchBlocked"
  assert_optional_equals "$(json_get "${merchant_billing_json}" "catalogProductCap")" "${effective_expected_catalog_product_cap}" "merchant billing catalogProductCap"
  assert_optional_equals "$(json_get "${merchant_billing_json}" "poweredByBadgeRequired")" "${effective_expected_powered_by_badge_required}" "merchant billing poweredByBadgeRequired"
  assert_optional_equals "$(json_get "${merchant_billing_json}" "chatFallbackEnabled")" "${effective_expected_chat_fallback_enabled}" "merchant billing chatFallbackEnabled"
  assert_equals "$(json_get "${merchant_billing_json}" "availablePlans.0.tierKey")" "FREE" "merchant billing availablePlans.0 tierKey"
  assert_equals "$(json_get "${merchant_billing_json}" "availablePlans.0.chatFallbackEnabled")" "false" "merchant billing FREE chatFallbackEnabled"
  assert_json_array_contains_csv "${merchant_billing_json}" "availablePlans.0.allowedSurfaces" "ai-search" "merchant billing FREE allowedSurfaces"
  assert_json_array_not_contains_csv "${merchant_billing_json}" "availablePlans.0.allowedSurfaces" "order-lookup" "merchant billing FREE allowedSurfaces"
  assert_equals "$(json_get "${merchant_billing_json}" "availablePlans.1.tierKey")" "STARTER" "merchant billing availablePlans.1 tierKey"
  assert_equals "$(json_get "${merchant_billing_json}" "availablePlans.1.chatFallbackEnabled")" "true" "merchant billing STARTER chatFallbackEnabled"
  assert_json_array_contains_csv "${merchant_billing_json}" "availablePlans.1.allowedSurfaces" "comparison" "merchant billing STARTER allowedSurfaces"
  assert_json_array_not_contains_csv "${merchant_billing_json}" "availablePlans.1.allowedSurfaces" "order-lookup" "merchant billing STARTER allowedSurfaces"
  assert_equals "$(json_get "${merchant_billing_json}" "availablePlans.2.tierKey")" "ELITE" "merchant billing availablePlans.2 tierKey"
  assert_equals "$(json_get "${merchant_billing_json}" "availablePlans.2.actionCapable")" "true" "merchant billing ELITE actionCapable"
  assert_json_array_contains_csv "${merchant_billing_json}" "availablePlans.2.allowedSurfaces" "comparison,order-lookup" "merchant billing ELITE allowedSurfaces"
  if [[ "$(json_get "${merchant_billing_json}" "availablePlans.2.commerciallyAvailable")" == "true" ]]; then
    assert_equals "$(json_get "${merchant_billing_json}" "availablePlans.2.requiresExplicitConfirmation")" "true" "merchant billing ELITE requiresExplicitConfirmation"
    assert_equals "$(json_get "${merchant_billing_json}" "availablePlans.2.auditTrailAvailable")" "true" "merchant billing ELITE auditTrailAvailable"
    assert_json_array_contains_csv "${merchant_billing_json}" "availablePlans.2.actionPackages" "guided-commerce" "merchant billing ELITE actionPackages"
  fi

  echo "== Merchant storefront preview =="
  http_request GET "${bridge_base}/api/app/store/storefront-preview" "" "${merchant_headers[@]-}"
  assert_equals "${HTTP_STATUS}" "200" "merchant storefront preview status"
  merchant_preview_json="${HTTP_BODY}"
  assert_nonempty "$(json_get "${merchant_preview_json}" "extensionHandle")" "merchant storefront preview extensionHandle"
  assert_nonempty "$(json_get "${merchant_preview_json}" "surfacePlacements.0.blockHandle")" "merchant storefront preview surfacePlacements.0.blockHandle"
  assert_equals "$(json_get "${merchant_preview_json}" "surfacePlacements.0.blockHandle")" "companion-ai-search" "merchant storefront preview AI search block handle"
  assert_equals "$(json_get "${merchant_preview_json}" "surfacePlacements.0.requiredTierKey")" "FREE" "merchant storefront preview AI search requiredTierKey"
  assert_equals "$(json_get "${merchant_preview_json}" "surfacePlacements.1.blockHandle")" "companion-contextual-pill" "merchant storefront preview contextual pill block handle"
  assert_equals "$(json_get "${merchant_preview_json}" "surfacePlacements.1.requiredTierKey")" "STARTER" "merchant storefront preview contextual pill requiredTierKey"
  assert_equals "$(json_get "${merchant_preview_json}" "surfacePlacements.2.blockHandle")" "companion-product-insight" "merchant storefront preview product insight block handle"
  assert_equals "$(json_get "${merchant_preview_json}" "surfacePlacements.3.blockHandle")" "companion-policy-strip" "merchant storefront preview policy strip block handle"
  assert_equals "$(json_get "${merchant_preview_json}" "surfacePlacements.4.blockHandle")" "companion-product-faq" "merchant storefront preview product faq block handle"
  assert_equals "$(json_get "${merchant_preview_json}" "surfacePlacements.5.blockHandle")" "companion-comparison" "merchant storefront preview comparison block handle"
  assert_equals "$(json_get "${merchant_preview_json}" "surfacePlacements.5.requiredTierKey")" "STARTER" "merchant storefront preview comparison requiredTierKey"
  assert_equals "$(json_get "${merchant_preview_json}" "surfacePlacements.6.blockHandle")" "companion-order-lookup" "merchant storefront preview order lookup block handle"
  assert_equals "$(json_get "${merchant_preview_json}" "surfacePlacements.6.requiredTierKey")" "ELITE" "merchant storefront preview order lookup requiredTierKey"
  assert_nonempty "$(json_get "${merchant_preview_json}" "surfacePlacements.0.themeEditorUrl")" "merchant storefront preview AI search themeEditorUrl"
  assert_json_array_contains_csv "${merchant_preview_json}" "groundingSignals" "Catalog product grounding,Policy grounding" "merchant storefront preview groundingSignals"
  if [[ "$(json_get "${platform_store_json}" "productsEnabled")" == "true" ]]; then
    assert_json_array_contains_csv "${merchant_preview_json}" "supportedReviewProviders" "Judge.me,Okendo" "merchant storefront preview supportedReviewProviders"
  fi
  if [[ "$(json_get "${platform_store_json}" "articlesEnabled")" == "true" ]]; then
    assert_json_array_contains_csv "${merchant_preview_json}" "groundingSignals" "Published article grounding" "merchant storefront preview article grounding"
  fi
  if [[ "$(json_get "${platform_store_json}" "metaobjectsEnabled")" == "true" ]]; then
    assert_json_array_contains_csv "${merchant_preview_json}" "groundingSignals" "Metaobject grounding" "merchant storefront preview metaobject grounding"
  fi

  echo "== Merchant webhook diagnostics =="
  http_request GET "${bridge_base}/api/app/store/webhook-subscriptions" "" "${merchant_headers[@]-}"
  assert_equals "${HTTP_STATUS}" "200" "merchant webhook diagnostics status"
  merchant_webhook_json="${HTTP_BODY}"
  assert_nonempty "$(json_get "${merchant_webhook_json}" "expectedCount")" "merchant webhook expectedCount"
  assert_optional_equals "$(json_get "${merchant_webhook_json}" "status")" "${EXPECT_WEBHOOK_STATUS}" "merchant webhook status"

  echo "== Merchant support readiness =="
  http_request GET "${bridge_base}/api/app/store/support-readiness" "" "${merchant_headers[@]-}"
  assert_equals "${HTTP_STATUS}" "200" "merchant support readiness status"
  merchant_support_json="${HTTP_BODY}"
  assert_equals "$(json_get "${merchant_support_json}" "status")" "${EXPECT_ORDER_LOOKUP_STATUS}" "merchant support readiness posture"
  assert_equals "$(json_get "${merchant_support_json}" "orderLookupSupported")" "${effective_expected_order_lookup_supported}" "merchant support order lookup supported"
  assert_optional_equals "$(json_get "${merchant_support_json}" "orderLookupScopeGranted")" "${effective_expected_order_lookup_scope_granted}" "merchant support order lookup scope granted"
  assert_optional_equals "$(json_get "${merchant_support_json}" "appScopesUpdateWebhookReady")" "${effective_expected_order_lookup_app_scopes_webhook_ready}" "merchant support scopes webhook ready"
  assert_optional_equals "$(json_get "${merchant_support_json}" "merchantHandoffConfigured")" "${EXPECT_ORDER_LOOKUP_MERCHANT_HANDOFF_CONFIGURED}" "merchant support merchant handoff configured"
  assert_optional_equals "$(json_get "${merchant_support_json}" "lifecycleStage")" "${EXPECT_SUPPORT_LIFECYCLE_STAGE}" "merchant support lifecycle stage"

  echo "== Merchant usage summary =="
  http_request GET "${bridge_base}/api/app/store/usage-summary" "" "${merchant_headers[@]-}"
  assert_equals "${HTTP_STATUS}" "200" "merchant usage summary status"
  merchant_usage_json="${HTTP_BODY}"
  assert_equals "$(json_get "${merchant_usage_json}" "shopDomain")" "${SHOP_DOMAIN}" "merchant usage shopDomain"
  assert_nonempty "$(json_get "${merchant_usage_json}" "generatedAt")" "merchant usage generatedAt"
  assert_nonempty "$(json_get "${merchant_usage_json}" "totalToday")" "merchant usage totalToday"

  echo "== Merchant governed actions =="
  http_request GET "${bridge_base}/api/app/store/actions/recent?limit=5" "" "${merchant_headers[@]-}"
  assert_equals "${HTTP_STATUS}" "200" "merchant governed actions status"
fi

if [[ "${effective_expected_order_lookup_supported}" == "true" ]]; then
  resolve_order_lookup_sample
  if [[ -n "${ORDER_LOOKUP_ORDER_NUMBER}" && -n "${ORDER_LOOKUP_EMAIL}" ]]; then
    echo "== Storefront order lookup =="
    order_lookup_payload="$(python3 - <<'PY' "${ORDER_LOOKUP_ORDER_NUMBER}" "${ORDER_LOOKUP_EMAIL}"
import json
import sys
print(json.dumps({"orderNumber": sys.argv[1], "email": sys.argv[2]}))
PY
)"
    http_request POST "${bridge_base}/api/storefront/shops/${SHOP_DOMAIN}/support/order-lookup" "${order_lookup_payload}" "X-AI-FABRIC-SHOPPER-SESSION-ID: ${SHOPPER_SESSION_ID}"
    assert_equals "${HTTP_STATUS}" "200" "storefront order lookup status"
    storefront_order_lookup_json="${HTTP_BODY}"
    assert_equals "$(json_get "${storefront_order_lookup_json}" "available")" "true" "storefront order lookup available"
    assert_equals "$(json_get "${storefront_order_lookup_json}" "matched")" "true" "storefront order lookup matched"
    assert_nonempty "$(json_get "${storefront_order_lookup_json}" "order.orderName")" "storefront order lookup order name"
    assert_nonempty "$(json_get "${storefront_order_lookup_json}" "order.createdAt")" "storefront order lookup createdAt"
    assert_nonempty "$(json_get "${storefront_order_lookup_json}" "guidance")" "storefront order lookup guidance"
  else
    echo "Skipping live storefront order lookup because no sample order could be resolved from explicit env or Shopify Admin."
  fi
fi

echo "Shopify Companion verification passed for ${SHOP_DOMAIN}"
