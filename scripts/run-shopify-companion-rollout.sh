#!/usr/bin/env bash
set -euo pipefail

# Prepare and advance a Shopify Companion store rollout from the platform side.
#
# Required env:
#   PLATFORM_BASE_URL
#   SHOPIFY_BRIDGE_BASE_URL
#   SHOP_DOMAIN
#
# Required auth:
#   PLATFORM_API_KEY or PLATFORM_SESSION_COOKIE_JAR
#
# Optional env:
#   PLATFORM_API_KEY_HEADER=X-PLATFORM-API-KEY
#   PRODUCT_SERVICE_REF=shopify-bridge-prod
#   STORE_DISPLAY_NAME=<label>
#   AUTO_RUN_SOURCE_PREFLIGHT=true
#   AUTO_REQUEST_GO_LIVE=true

PLATFORM_BASE_URL="${PLATFORM_BASE_URL:-}"
SHOPIFY_BRIDGE_BASE_URL="${SHOPIFY_BRIDGE_BASE_URL:-}"
SHOP_DOMAIN="${SHOP_DOMAIN:-}"
PLATFORM_API_KEY="${PLATFORM_API_KEY:-}"
PLATFORM_API_KEY_HEADER="${PLATFORM_API_KEY_HEADER:-X-PLATFORM-API-KEY}"
PLATFORM_SESSION_COOKIE_JAR="${PLATFORM_SESSION_COOKIE_JAR:-}"
PRODUCT_SERVICE_REF="${PRODUCT_SERVICE_REF:-shopify-bridge-prod}"
STORE_DISPLAY_NAME="${STORE_DISPLAY_NAME:-}"
AUTO_RUN_SOURCE_PREFLIGHT="${AUTO_RUN_SOURCE_PREFLIGHT:-true}"
AUTO_REQUEST_GO_LIVE="${AUTO_REQUEST_GO_LIVE:-true}"

require_cmd() {
  local cmd="$1"
  command -v "${cmd}" >/dev/null 2>&1 || { echo "Missing required command: ${cmd}" >&2; exit 2; }
}

require_env() {
  local name="$1"
  [[ -n "${!name:-}" ]] || { echo "Missing required env: ${name}" >&2; exit 2; }
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

value = json.loads(os.environ["JSON_PAYLOAD"])
path = os.environ["JSON_PATH"]
current = value
for part in [segment for segment in path.split(".") if segment]:
    if isinstance(current, list):
        current = current[int(part)]
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

json_lines() {
  local payload="$1"
  local path="$2"
  JSON_PAYLOAD="${payload}" JSON_PATH="${path}" python3 - <<'PY'
import json
import os

value = json.loads(os.environ["JSON_PAYLOAD"])
path = os.environ["JSON_PATH"]
current = value
for part in [segment for segment in path.split(".") if segment]:
    if isinstance(current, list):
        current = current[int(part)]
    elif isinstance(current, dict):
        current = current.get(part)
    else:
        current = []
        break
if not isinstance(current, list):
    current = []
for item in current:
    if item is None:
        continue
    print(str(item))
PY
}

http_request() {
  local method="$1"
  local url="$2"
  local body="${3:-}"
  shift 3 || true
  local headers=("$@")
  local response
  response="$(python3 - "$method" "$url" "$body" "${HTTP_COOKIE_JAR:-}" "${headers[@]}" <<'PY'
import subprocess
import sys

method = sys.argv[1]
url = sys.argv[2]
body = sys.argv[3]
cookie_jar = sys.argv[4]
headers = sys.argv[5:]

cmd = ["curl", "-sS", "-X", method, "-H", "Accept: application/json", "-w", "\n%{http_code}"]
if cookie_jar:
    cmd.extend(["-b", cookie_jar])
for header in headers:
    if header:
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

platform_request() {
  local previous_cookie_jar="${HTTP_COOKIE_JAR:-}"
  HTTP_COOKIE_JAR="${PLATFORM_SESSION_COOKIE_JAR:-}"
  http_request "$@"
  HTTP_COOKIE_JAR="${previous_cookie_jar}"
}

print_store_summary() {
  local store_json="$1"
  echo "shop: $(json_get "${store_json}" "shopDomain")"
  echo "product service: $(json_get "${store_json}" "productServiceRef")"
  echo "customer: $(json_get "${store_json}" "customerId")"
  echo "deployment: $(json_get "${store_json}" "deploymentId")"
  echo "consumer: $(json_get "${store_json}" "consumerId")"
  echo "installStatus: $(json_get "${store_json}" "installStatus")"
  echo "credentials: $(json_get "${store_json}" "credentials.status")"
  echo "sourceReadinessStatus: $(json_get "${store_json}" "sourceReadinessStatus")"
  echo "syncStatus: $(json_get "${store_json}" "syncStatus")"
  echo "widgetStatus: $(json_get "${store_json}" "widgetStatus")"
  echo "onboardingStatus: $(json_get "${store_json}" "onboardingStatus")"
  echo "goLiveEligible: $(json_get "${store_json}" "readiness.goLiveEligible")"
  echo "storefrontReady: $(json_get "${store_json}" "readiness.storefrontReady")"
}

print_blockers() {
  local store_json="$1"
  local blockers
  blockers="$(json_lines "${store_json}" "readiness.goLiveBlockingReasons")"
  if [[ -n "${blockers}" ]]; then
    echo "go-live blockers:"
    while IFS= read -r line; do
      [[ -n "${line}" ]] && echo "  - ${line}"
    done <<< "${blockers}"
  fi
  local actions
  actions="$(json_lines "${store_json}" "readiness.nextActions")"
  if [[ -n "${actions}" ]]; then
    echo "next actions:"
    while IFS= read -r line; do
      [[ -n "${line}" ]] && echo "  - ${line}"
    done <<< "${actions}"
  fi
}

readiness_mentions_archived_binding() {
  local store_json="$1"
  local blockers
  blockers="$(
    {
      json_lines "${store_json}" "readiness.goLiveBlockingReasons"
      json_lines "${store_json}" "readiness.storefrontBlockingReasons"
      json_lines "${store_json}" "readiness.nextActions"
    } | tr '[:upper:]' '[:lower:]'
  )"
  [[ "${blockers}" == *"archived"* && "${blockers}" == *"bootstrap"* ]]
}

require_cmd curl
require_cmd python3
require_env PLATFORM_BASE_URL
require_env SHOPIFY_BRIDGE_BASE_URL
require_env SHOP_DOMAIN
if [[ -z "${PLATFORM_API_KEY}" && -z "${PLATFORM_SESSION_COOKIE_JAR}" ]]; then
  echo "Missing required auth: set PLATFORM_API_KEY or PLATFORM_SESSION_COOKIE_JAR" >&2
  exit 2
fi

platform_base="$(trim_slash "${PLATFORM_BASE_URL}")"
bridge_base="$(trim_slash "${SHOPIFY_BRIDGE_BASE_URL}")"
declare -a platform_headers=()
if [[ -n "${PLATFORM_API_KEY}" ]]; then
  platform_headers=("${PLATFORM_API_KEY_HEADER}: ${PLATFORM_API_KEY}")
fi

display_name="${STORE_DISPLAY_NAME:-${SHOP_DOMAIN}}"
install_url="${bridge_base}/auth/shopify/install?shop=${SHOP_DOMAIN}"

echo "== Resolve store mapping =="
platform_request GET "${platform_base}/api/shopify/stores/${SHOP_DOMAIN}" "" "${platform_headers[@]-}"
if [[ "${HTTP_STATUS}" == "404" ]]; then
  echo "Store mapping not found. Creating it."
  payload="$(python3 - <<'PY'
import json
import os
print(json.dumps({
    "shopDomain": os.environ["SHOP_DOMAIN"],
    "displayName": os.environ["STORE_DISPLAY_NAME"],
    "productServiceRef": os.environ["PRODUCT_SERVICE_REF"],
    "installStatus": "UNINSTALLED",
    "syncStatus": "NOT_SYNCED",
    "sourceReadinessStatus": "NOT_RUN",
    "widgetStatus": "NOT_ENABLED",
    "onboardingStatus": "CONNECTED",
    "productsEnabled": True,
    "collectionsEnabled": True,
    "pagesEnabled": True,
    "policiesEnabled": True,
    "articlesEnabled": True,
}))
PY
)"
  STORE_DISPLAY_NAME="${display_name}" SHOP_DOMAIN="${SHOP_DOMAIN}" PRODUCT_SERVICE_REF="${PRODUCT_SERVICE_REF}" \
    platform_request POST "${platform_base}/api/shopify/stores" "${payload}" "${platform_headers[@]-}"
  [[ "${HTTP_STATUS}" == "201" ]] || { echo "Failed to create store mapping: HTTP ${HTTP_STATUS}" >&2; echo "${HTTP_BODY}" >&2; exit 1; }
  store_json="${HTTP_BODY}"
elif [[ "${HTTP_STATUS}" == "200" ]]; then
  store_json="${HTTP_BODY}"
else
  echo "Failed to load store mapping: HTTP ${HTTP_STATUS}" >&2
  echo "${HTTP_BODY}" >&2
  exit 1
fi
print_store_summary "${store_json}"

if [[ -z "$(json_get "${store_json}" "customerId")" || -z "$(json_get "${store_json}" "deploymentId")" || -z "$(json_get "${store_json}" "consumerId")" ]]; then
  echo
  echo "== Bootstrap platform bindings =="
  platform_request POST "${platform_base}/api/shopify/stores/${SHOP_DOMAIN}/bootstrap" "{}" "${platform_headers[@]-}"
  [[ "${HTTP_STATUS}" == "200" ]] || { echo "Failed to bootstrap store: HTTP ${HTTP_STATUS}" >&2; echo "${HTTP_BODY}" >&2; exit 1; }
  echo "Bootstrapped customer/deployment/consumer for ${SHOP_DOMAIN}."
fi

echo
echo "== Refresh store summary =="
platform_request GET "${platform_base}/api/shopify/stores/${SHOP_DOMAIN}" "" "${platform_headers[@]-}"
[[ "${HTTP_STATUS}" == "200" ]] || { echo "Failed to reload store summary: HTTP ${HTTP_STATUS}" >&2; echo "${HTTP_BODY}" >&2; exit 1; }
store_json="${HTTP_BODY}"
print_store_summary "${store_json}"
echo "install URL: ${install_url}"

if readiness_mentions_archived_binding "${store_json}"; then
  echo
  echo "== Re-bootstrap archived deployment binding =="
  platform_request POST "${platform_base}/api/shopify/stores/${SHOP_DOMAIN}/bootstrap" "{}" "${platform_headers[@]-}"
  [[ "${HTTP_STATUS}" == "200" ]] || { echo "Failed to re-bootstrap archived store binding: HTTP ${HTTP_STATUS}" >&2; echo "${HTTP_BODY}" >&2; exit 1; }
  platform_request GET "${platform_base}/api/shopify/stores/${SHOP_DOMAIN}" "" "${platform_headers[@]-}"
  [[ "${HTTP_STATUS}" == "200" ]] || { echo "Failed to reload store summary after re-bootstrap: HTTP ${HTTP_STATUS}" >&2; echo "${HTTP_BODY}" >&2; exit 1; }
  store_json="${HTTP_BODY}"
  print_store_summary "${store_json}"
fi

install_status="$(json_get "${store_json}" "installStatus")"
credential_status="$(json_get "${store_json}" "credentials.status")"
if [[ "${install_status}" != "INSTALLED" || "${credential_status}" != "READY" ]]; then
  echo
  echo "Manual Shopify step still required before rollout can continue."
  echo "Open and approve: ${install_url}"
  print_blockers "${store_json}"
  exit 0
fi

if [[ "${AUTO_RUN_SOURCE_PREFLIGHT}" == "true" ]]; then
  echo
  echo "== Run live source preflight =="
  platform_request POST "${platform_base}/api/product-services/${PRODUCT_SERVICE_REF}/stores/${SHOP_DOMAIN}/run-source-preflight" "" "${platform_headers[@]-}"
  [[ "${HTTP_STATUS}" == "200" ]] || { echo "Failed to run live source preflight: HTTP ${HTTP_STATUS}" >&2; echo "${HTTP_BODY}" >&2; exit 1; }
fi

echo
echo "== Refresh store summary after preflight =="
platform_request GET "${platform_base}/api/shopify/stores/${SHOP_DOMAIN}" "" "${platform_headers[@]-}"
[[ "${HTTP_STATUS}" == "200" ]] || { echo "Failed to reload store summary after preflight: HTTP ${HTTP_STATUS}" >&2; echo "${HTTP_BODY}" >&2; exit 1; }
store_json="${HTTP_BODY}"
print_store_summary "${store_json}"

go_live_eligible="$(json_get "${store_json}" "readiness.goLiveEligible")"
if [[ "${AUTO_REQUEST_GO_LIVE}" == "true" && "${go_live_eligible}" == "true" ]]; then
  echo
  echo "== Request go-live =="
  platform_request POST "${platform_base}/api/shopify/stores/${SHOP_DOMAIN}/go-live" "" "${platform_headers[@]-}"
  if [[ "${HTTP_STATUS}" == "400" && "${HTTP_BODY}" == *"Deployment is archived and cannot"* ]]; then
    echo "Archived deployment binding detected during go-live. Re-running bootstrap once."
    platform_request POST "${platform_base}/api/shopify/stores/${SHOP_DOMAIN}/bootstrap" "{}" "${platform_headers[@]-}"
    [[ "${HTTP_STATUS}" == "200" ]] || { echo "Failed to bootstrap archived store binding after go-live rejection: HTTP ${HTTP_STATUS}" >&2; echo "${HTTP_BODY}" >&2; exit 1; }
    platform_request POST "${platform_base}/api/shopify/stores/${SHOP_DOMAIN}/go-live" "" "${platform_headers[@]-}"
  fi
  [[ "${HTTP_STATUS}" == "200" ]] || { echo "Failed to request go-live: HTTP ${HTTP_STATUS}" >&2; echo "${HTTP_BODY}" >&2; exit 1; }
  store_json="${HTTP_BODY}"
  print_store_summary "${store_json}"
else
  echo
  echo "Go-live not requested."
  print_blockers "${store_json}"
fi
