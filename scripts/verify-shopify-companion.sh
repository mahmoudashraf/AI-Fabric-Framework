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
#   PRODUCT_SERVICE_REF=shopify-bridge-prod
#   PLATFORM_API_KEY_HEADER=X-PLATFORM-API-KEY
#   PLATFORM_SESSION_COOKIE_JAR=/tmp/platform.cookies
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
#   SHOPIFY_ADMIN_ACCESS_TOKEN=<offline-access-token>
#   SHOPIFY_ADMIN_API_VERSION=2026-04
#   SHOPIFY_MERCHANT_AUTHORIZATION="Bearer <session-token>"
#   SHOPIFY_EMBEDDED_HOST=<base64-host>

PLATFORM_BASE_URL="${PLATFORM_BASE_URL:-}"
PLATFORM_API_KEY="${PLATFORM_API_KEY:-}"
PLATFORM_API_KEY_HEADER="${PLATFORM_API_KEY_HEADER:-X-PLATFORM-API-KEY}"
PLATFORM_SESSION_COOKIE_JAR="${PLATFORM_SESSION_COOKIE_JAR:-}"
PLATFORM_LOGIN_EMAIL="${PLATFORM_LOGIN_EMAIL:-}"
PLATFORM_LOGIN_PASSWORD="${PLATFORM_LOGIN_PASSWORD:-}"
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
EXPECT_REQUIRED_ACTIONS="${EXPECT_REQUIRED_ACTIONS:-list_products,search_products,get_product_details,find_similar_products,compare_products,check_availability,get_policy}"
SHOPIFY_ADMIN_ACCESS_TOKEN="${SHOPIFY_ADMIN_ACCESS_TOKEN:-}"
SHOPIFY_ADMIN_ACCESS_TOKEN_SOURCE="none"
SHOPIFY_ADMIN_API_VERSION="${SHOPIFY_ADMIN_API_VERSION:-2026-04}"
SHOPIFY_MERCHANT_AUTHORIZATION="${SHOPIFY_MERCHANT_AUTHORIZATION:-}"
SHOPIFY_EMBEDDED_HOST="${SHOPIFY_EMBEDDED_HOST:-}"
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
platform_request GET "${platform_base}/api/product-services/${PRODUCT_SERVICE_REF}/railway/logs?source=deployment&deploymentId=${latest_product_service_deployment_id}&limit=50" "" "${platform_headers[@]-}"
assert_equals "${HTTP_STATUS}" "200" "product service Railway logs status"
product_service_logs_json="${HTTP_BODY}"
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
effective_expected_surfaces="${EXPECT_ENABLED_SURFACES:-${billing_allowed_surfaces_csv}}"
effective_expected_billing_tier="${EXPECT_BILLING_TIER:-$(json_get "${platform_store_billing_json}" "tierKey")}"
effective_expected_catalog_product_cap="${EXPECT_CATALOG_PRODUCT_CAP:-$(json_get "${platform_store_billing_json}" "catalogProductCap")}"
effective_expected_powered_by_badge_required="${EXPECT_POWERED_BY_BADGE_REQUIRED:-$(json_get "${platform_store_billing_json}" "poweredByBadgeRequired")}"
effective_expected_chat_fallback_enabled="${EXPECT_CHAT_FALLBACK_ENABLED:-$(json_get "${platform_store_billing_json}" "chatFallbackEnabled")}"

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
assert_nonempty "$(json_get "${platform_store_vectorization_json}" "policy.sourcePolicies.0.sourceCategory")" "platform store vectorization source policy category"
assert_nonempty "$(json_get "${platform_store_vectorization_json}" "policy.sourcePolicies.0.updateTriggerMode")" "platform store vectorization update trigger mode"
assert_nonempty "$(json_get "${platform_store_vectorization_json}" "effectiveIndexedFields.0.fieldKey")" "platform store vectorization indexed field"
assert_nonempty "$(json_get "${platform_store_vectorization_json}" "automation.autoIndexingHealthy")" "platform store vectorization automation health"
assert_nonempty "$(json_get "${platform_store_vectorization_json}" "recentEvents")" "platform store vectorization recent events payload"

echo "== Platform store vectorization events =="
platform_request GET "${platform_base}/api/shopify/stores/${SHOP_DOMAIN}/vectorization/events?limit=5" "" "${platform_headers[@]-}"
assert_equals "${HTTP_STATUS}" "200" "platform store vectorization events status"
platform_store_vectorization_events_json="${HTTP_BODY}"
JSON_PAYLOAD="${platform_store_vectorization_json}" JSON_EVENTS="${platform_store_vectorization_events_json}" python3 - <<'PY'
import json
import os
import sys

summary = json.loads(os.environ["JSON_PAYLOAD"])
events = json.loads(os.environ["JSON_EVENTS"])

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
assert_equals "$(json_get "${bootstrap_json}" "available")" "${EXPECT_STOREFRONT_READY}" "storefront bootstrap availability"
assert_nonempty "$(json_get "${bootstrap_json}" "bridgeQueryUrl")" "storefront bridgeQueryUrl"
assert_nonempty "$(json_get "${bootstrap_json}" "bridgeSuggestionsUrl")" "storefront bridgeSuggestionsUrl"
assert_optional_equals "$(json_get "${bootstrap_json}" "billingTier")" "${effective_expected_billing_tier}" "storefront bootstrap billingTier"
assert_optional_equals "$(json_get "${bootstrap_json}" "billingStatus")" "${EXPECT_BILLING_STATUS}" "storefront bootstrap billingStatus"
assert_optional_equals "$(json_get "${bootstrap_json}" "catalogProductCap")" "${effective_expected_catalog_product_cap}" "storefront bootstrap catalogProductCap"
assert_optional_equals "$(json_get "${bootstrap_json}" "poweredByBadgeRequired")" "${effective_expected_powered_by_badge_required}" "storefront bootstrap poweredByBadgeRequired"
assert_optional_equals "$(json_get "${bootstrap_json}" "chatFallbackEnabled")" "${effective_expected_chat_fallback_enabled}" "storefront bootstrap chatFallbackEnabled"
assert_optional_equals "$(json_get "${bootstrap_json}" "shellModeProfile")" "${EXPECT_SHELL_MODE_PROFILE}" "storefront bootstrap shellModeProfile"
assert_json_array_contains_csv "${bootstrap_json}" "enabledSurfaces" "${effective_expected_surfaces}" "storefront bootstrap enabledSurfaces"

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

  echo "== Merchant webhook diagnostics =="
  http_request GET "${bridge_base}/api/app/store/webhook-subscriptions" "" "${merchant_headers[@]-}"
  assert_equals "${HTTP_STATUS}" "200" "merchant webhook diagnostics status"
  merchant_webhook_json="${HTTP_BODY}"
  assert_nonempty "$(json_get "${merchant_webhook_json}" "expectedCount")" "merchant webhook expectedCount"
  assert_optional_equals "$(json_get "${merchant_webhook_json}" "status")" "${EXPECT_WEBHOOK_STATUS}" "merchant webhook status"

  echo "== Merchant usage summary =="
  http_request GET "${bridge_base}/api/app/store/usage-summary" "" "${merchant_headers[@]-}"
  assert_equals "${HTTP_STATUS}" "200" "merchant usage summary status"
  merchant_usage_json="${HTTP_BODY}"
  assert_equals "$(json_get "${merchant_usage_json}" "shopDomain")" "${SHOP_DOMAIN}" "merchant usage shopDomain"
  assert_nonempty "$(json_get "${merchant_usage_json}" "generatedAt")" "merchant usage generatedAt"
  assert_nonempty "$(json_get "${merchant_usage_json}" "totalToday")" "merchant usage totalToday"
fi

echo "Shopify Companion verification passed for ${SHOP_DOMAIN}"
