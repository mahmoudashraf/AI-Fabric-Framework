#!/usr/bin/env bash
set -euo pipefail

# Shopify Companion uninstall verification script.
#
# Verifies:
# - platform uninstall path marks the store UNINSTALLED
# - platform credentials are cleared
# - synced Shopify documents are cleaned up or explicitly reported
# - storefront bootstrap becomes unavailable
# - optional merchant session reports install recovery is required
#
# Required env:
#   PLATFORM_BASE_URL
#   PLATFORM_API_KEY
#   SHOPIFY_BRIDGE_BASE_URL
#   SHOP_DOMAIN
#
# Optional env:
#   PLATFORM_API_KEY_HEADER=X-PLATFORM-API-KEY
#   SHOPIFY_MERCHANT_AUTHORIZATION="Bearer <session-token>"
#   SHOPIFY_EMBEDDED_HOST=<base64-host>
#   EXPECT_INSTALL_STATUS=UNINSTALLED
#   EXPECT_SYNC_STATUS=NOT_SYNCED
#   EXPECT_SOURCE_READINESS_STATUS=NOT_RUN
#   EXPECT_WIDGET_STATUS=NOT_ENABLED
#   EXPECT_CREDENTIAL_STATUS=MISSING
#   EXPECT_DOCUMENT_CLEANUP_STATUS=CLEARED
#   EXPECT_STOREFRONT_AVAILABLE=false
#   EXPECT_INSTALL_RECOVERY_REQUIRED=true

PLATFORM_BASE_URL="${PLATFORM_BASE_URL:-}"
PLATFORM_API_KEY="${PLATFORM_API_KEY:-}"
PLATFORM_API_KEY_HEADER="${PLATFORM_API_KEY_HEADER:-X-PLATFORM-API-KEY}"
SHOPIFY_BRIDGE_BASE_URL="${SHOPIFY_BRIDGE_BASE_URL:-}"
SHOP_DOMAIN="${SHOP_DOMAIN:-}"
SHOPIFY_MERCHANT_AUTHORIZATION="${SHOPIFY_MERCHANT_AUTHORIZATION:-}"
SHOPIFY_EMBEDDED_HOST="${SHOPIFY_EMBEDDED_HOST:-}"
EXPECT_INSTALL_STATUS="${EXPECT_INSTALL_STATUS:-UNINSTALLED}"
EXPECT_SYNC_STATUS="${EXPECT_SYNC_STATUS:-NOT_SYNCED}"
EXPECT_SOURCE_READINESS_STATUS="${EXPECT_SOURCE_READINESS_STATUS:-NOT_RUN}"
EXPECT_WIDGET_STATUS="${EXPECT_WIDGET_STATUS:-NOT_ENABLED}"
EXPECT_CREDENTIAL_STATUS="${EXPECT_CREDENTIAL_STATUS:-MISSING}"
EXPECT_DOCUMENT_CLEANUP_STATUS="${EXPECT_DOCUMENT_CLEANUP_STATUS:-CLEARED}"
EXPECT_STOREFRONT_AVAILABLE="${EXPECT_STOREFRONT_AVAILABLE:-false}"
EXPECT_INSTALL_RECOVERY_REQUIRED="${EXPECT_INSTALL_RECOVERY_REQUIRED:-true}"

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

platform_base="$(trim_slash "${PLATFORM_BASE_URL}")"
bridge_base="$(trim_slash "${SHOPIFY_BRIDGE_BASE_URL}")"
platform_headers=("${PLATFORM_API_KEY_HEADER}: ${PLATFORM_API_KEY}")

require_cmd curl
require_cmd python3
require_env PLATFORM_BASE_URL
require_env PLATFORM_API_KEY
require_env SHOPIFY_BRIDGE_BASE_URL
require_env SHOP_DOMAIN

echo "== Platform uninstall =="
http_request POST "${platform_base}/api/shopify/stores/${SHOP_DOMAIN}/uninstall" "" "${platform_headers[@]}"
assert_equals "${HTTP_STATUS}" "200" "platform uninstall status"
uninstall_json="${HTTP_BODY}"
assert_equals "$(json_get "${uninstall_json}" "shopDomain")" "${SHOP_DOMAIN}" "platform uninstall shopDomain"
assert_equals "$(json_get "${uninstall_json}" "installStatus")" "${EXPECT_INSTALL_STATUS}" "platform uninstall installStatus"
assert_equals "$(json_get "${uninstall_json}" "syncStatus")" "${EXPECT_SYNC_STATUS}" "platform uninstall syncStatus"
assert_equals "$(json_get "${uninstall_json}" "sourceReadinessStatus")" "${EXPECT_SOURCE_READINESS_STATUS}" "platform uninstall sourceReadinessStatus"
assert_equals "$(json_get "${uninstall_json}" "widgetStatus")" "${EXPECT_WIDGET_STATUS}" "platform uninstall widgetStatus"
assert_equals "$(json_get "${uninstall_json}" "credentials.status")" "${EXPECT_CREDENTIAL_STATUS}" "platform uninstall credential status"
assert_equals "$(json_get "${uninstall_json}" "readiness.storefrontReady")" "false" "platform uninstall storefront readiness"
assert_equals "$(json_get "${uninstall_json}" "readiness.goLiveEligible")" "false" "platform uninstall go-live eligibility"
assert_equals "$(json_get "${uninstall_json}" "syncDetail.status")" "${EXPECT_DOCUMENT_CLEANUP_STATUS}" "platform uninstall document cleanup status"
assert_nonempty "$(json_get "${uninstall_json}" "widgetDetail.message")" "platform uninstall widget message"

echo "== Platform store summary after uninstall =="
http_request GET "${platform_base}/api/shopify/stores/${SHOP_DOMAIN}" "" "${platform_headers[@]}"
assert_equals "${HTTP_STATUS}" "200" "platform store summary after uninstall status"
store_json="${HTTP_BODY}"
assert_equals "$(json_get "${store_json}" "installStatus")" "${EXPECT_INSTALL_STATUS}" "platform store installStatus"
assert_equals "$(json_get "${store_json}" "credentials.status")" "${EXPECT_CREDENTIAL_STATUS}" "platform store credential status"
assert_equals "$(json_get "${store_json}" "syncDetail.status")" "${EXPECT_DOCUMENT_CLEANUP_STATUS}" "platform store sync detail status"

echo "== Storefront bootstrap after uninstall =="
http_request GET "${bridge_base}/api/storefront/shops/${SHOP_DOMAIN}/bootstrap"
assert_equals "${HTTP_STATUS}" "200" "storefront bootstrap after uninstall status"
bootstrap_json="${HTTP_BODY}"
assert_equals "$(json_get "${bootstrap_json}" "available")" "${EXPECT_STOREFRONT_AVAILABLE}" "storefront bootstrap availability"
assert_nonempty "$(json_get "${bootstrap_json}" "message")" "storefront bootstrap blocking message"

if [[ -n "${SHOPIFY_MERCHANT_AUTHORIZATION}" ]]; then
  echo "== Merchant session after uninstall =="
  merchant_headers=("Authorization: ${SHOPIFY_MERCHANT_AUTHORIZATION}")
  if [[ -n "${SHOPIFY_EMBEDDED_HOST}" ]]; then
    merchant_headers+=("X-Shopify-Embedded-Host: ${SHOPIFY_EMBEDDED_HOST}")
  fi
  http_request GET "${bridge_base}/api/app/session" "" "${merchant_headers[@]}"
  assert_equals "${HTTP_STATUS}" "200" "merchant session after uninstall status"
  merchant_session_json="${HTTP_BODY}"
  assert_equals "$(json_get "${merchant_session_json}" "installRecoveryRequired")" "${EXPECT_INSTALL_RECOVERY_REQUIRED}" "merchant install recovery required"
  assert_nonempty "$(json_get "${merchant_session_json}" "reinstallUrl")" "merchant reinstall url"
fi

echo "Shopify Companion uninstall verification passed for ${SHOP_DOMAIN}"
