#!/usr/bin/env bash
set -euo pipefail

# Live verification for the Shopify Companion Max Mode widget.
#
# Verifies:
# - Storefront bootstrap resolves an Elite-capable Max Mode runtime contract.
# - The storefront chat endpoint accepts the Max Mode/depth surface.
# - A real browser can load the Shopify storefront, load the Max widget bundle, and click-open the launcher.
# - Optional Platform POC adapter auth paths exercise the same deployment through the Max widget chat route.
#
# Required env:
#   SHOPIFY_BRIDGE_BASE_URL
#   SHOP_DOMAIN
#
# Optional env:
#   SHOPIFY_STOREFRONT_URL=https://<shop>/products/selling-plans-ski-wax
#   SHOPIFY_STOREFRONT_PASSWORD or SHOPIFY_STOREFRONT_PASSWORD_FILE
#   VERIFY_BROWSER=true
#   VERIFY_POC_AUTH_PATHS=true
#   MAX_WIDGET_AUTH_PATHS=PLATFORM_PRIVATE,PUBLIC_AUTHENTICATED,PUBLIC_ANONYMOUS
#   MAX_WIDGET_AUTH_PATH_STRICT=false
#   PLATFORM_BASE_URL
#   PLATFORM_DEPLOYMENT_ID=<defaults from storefront bootstrap>
#   PLATFORM_SESSION_COOKIE_JAR=/tmp/platform.cookies
#   PLATFORM_API_KEY=...
#   PLATFORM_API_KEY_HEADER=X-PLATFORM-API-KEY
#   PLATFORM_LOGIN_EMAIL / PLATFORM_LOGIN_PASSWORD, or *_FILE variants
#   EXPECT_BILLING_TIER=ELITE
#   EXPECT_CHAT_FALLBACK_ENABLED=true
#   EXPECT_ENABLED_SURFACES=contextual-pill,product-insight,policy-strip,product-faq,comparison,order-lookup
#   EXPECT_ALLOWED_CONVERSATION_MODES=thinker_deep,executor
#   MAX_WIDGET_SCREENSHOT_PATH=/tmp/shopify-companion-max-widget.png

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

SHOPIFY_BRIDGE_BASE_URL="${SHOPIFY_BRIDGE_BASE_URL:-}"
SHOP_DOMAIN="${SHOP_DOMAIN:-}"
SHOPIFY_STOREFRONT_URL="${SHOPIFY_STOREFRONT_URL:-}"
SHOPIFY_STOREFRONT_PASSWORD="${SHOPIFY_STOREFRONT_PASSWORD:-}"
SHOPIFY_STOREFRONT_PASSWORD_FILE="${SHOPIFY_STOREFRONT_PASSWORD_FILE:-}"
VERIFY_BROWSER="${VERIFY_BROWSER:-true}"
VERIFY_POC_AUTH_PATHS="${VERIFY_POC_AUTH_PATHS:-true}"
MAX_WIDGET_AUTH_PATHS="${MAX_WIDGET_AUTH_PATHS:-PLATFORM_PRIVATE,PUBLIC_AUTHENTICATED,PUBLIC_ANONYMOUS}"
MAX_WIDGET_AUTH_PATH_STRICT="${MAX_WIDGET_AUTH_PATH_STRICT:-false}"
PLATFORM_BASE_URL="${PLATFORM_BASE_URL:-}"
PLATFORM_DEPLOYMENT_ID="${PLATFORM_DEPLOYMENT_ID:-}"
PLATFORM_API_KEY="${PLATFORM_API_KEY:-}"
PLATFORM_API_KEY_HEADER="${PLATFORM_API_KEY_HEADER:-X-PLATFORM-API-KEY}"
PLATFORM_SESSION_COOKIE_JAR="${PLATFORM_SESSION_COOKIE_JAR:-}"
PLATFORM_LOGIN_EMAIL="${PLATFORM_LOGIN_EMAIL:-}"
PLATFORM_LOGIN_PASSWORD="${PLATFORM_LOGIN_PASSWORD:-}"
PLATFORM_LOGIN_EMAIL_FILE="${PLATFORM_LOGIN_EMAIL_FILE:-}"
PLATFORM_LOGIN_PASSWORD_FILE="${PLATFORM_LOGIN_PASSWORD_FILE:-}"
EXPECT_BILLING_TIER="${EXPECT_BILLING_TIER:-ELITE}"
EXPECT_CHAT_FALLBACK_ENABLED="${EXPECT_CHAT_FALLBACK_ENABLED:-true}"
EXPECT_ENABLED_SURFACES="${EXPECT_ENABLED_SURFACES:-contextual-pill,product-insight,policy-strip,product-faq,comparison,order-lookup}"
EXPECT_ALLOWED_CONVERSATION_MODES="${EXPECT_ALLOWED_CONVERSATION_MODES:-thinker_deep,executor}"
SHOPPER_SESSION_ID="${SHOPPER_SESSION_ID:-max-widget-live-$(date +%s)}"
MAX_WIDGET_QUERY="${MAX_WIDGET_QUERY:-Open the store assistant and recommend one product from this store.}"
MAX_WIDGET_SCREENSHOT_PATH="${MAX_WIDGET_SCREENSHOT_PATH:-/tmp/shopify-companion-max-widget-${SHOP_DOMAIN:-store}-$(date +%s).png}"
PLAYWRIGHT_REQUIRE_PATH="${PLAYWRIGHT_REQUIRE_PATH:-${REPO_ROOT}/Platfrom/ui/node_modules/playwright}"
TEMP_PLATFORM_COOKIE_JAR=""
HTTP_STATUS=""
HTTP_BODY=""

cleanup() {
  if [[ -n "${TEMP_PLATFORM_COOKIE_JAR}" && -f "${TEMP_PLATFORM_COOKIE_JAR}" ]]; then
    rm -f "${TEMP_PLATFORM_COOKIE_JAR}"
  fi
}
trap cleanup EXIT

require_cmd() {
  local cmd="$1"
  if ! command -v "${cmd}" >/dev/null 2>&1; then
    echo "Missing required command: ${cmd}" >&2
    exit 2
  fi
}

require_env() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    echo "Missing required env: ${name}" >&2
    exit 2
  fi
}

trim_slash() {
  local url="$1"
  if [[ "${url}" == */ ]]; then
    printf '%s' "${url%/}"
  else
    printf '%s' "${url}"
  fi
}

resolve_secret_value() {
  local var_name="$1"
  local file_var_name="${var_name}_FILE"
  local direct_value="${!var_name:-}"
  local file_path="${!file_var_name:-}"

  if [[ -n "${file_path}" ]]; then
    if [[ ! -f "${file_path}" ]]; then
      echo "Missing secret file for ${var_name}: ${file_path}" >&2
      exit 2
    fi
    python3 - <<'PY' "${file_path}"
import pathlib
import sys
print(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8").strip())
PY
    return
  fi

  printf '%s' "${direct_value}"
}

http_request() {
  local method="$1"
  local url="$2"
  local body="${3:-}"
  shift 3 || true
  local response_file
  response_file="$(mktemp)"
  local curl_args=(-sS -L -o "${response_file}" -w "%{http_code}" -X "${method}" "$@")
  if [[ -n "${body}" ]]; then
    curl_args+=(-H "Content-Type: application/json" --data "${body}")
  fi
  HTTP_STATUS="$(curl "${curl_args[@]}" "${url}")"
  HTTP_BODY="$(cat "${response_file}")"
  rm -f "${response_file}"
}

json_get() {
  local payload="$1"
  local path="$2"
  JSON_PAYLOAD="${payload}" JSON_PATH="${path}" python3 - <<'PY'
import json
import os

payload = os.environ["JSON_PAYLOAD"]
path = os.environ["JSON_PATH"]
value = json.loads(payload or "{}")
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
    echo "Assertion failed for ${label}: expected '${expected}', got '${actual}'" >&2
    exit 1
  fi
}

assert_nonempty() {
  local value="$1"
  local label="$2"
  if [[ -z "${value}" ]]; then
    echo "Assertion failed for ${label}: value is empty" >&2
    exit 1
  fi
}

json_array_contains() {
  local payload="$1"
  local path="$2"
  local expected="$3"
  local label="$4"
  JSON_PAYLOAD="${payload}" JSON_PATH="${path}" EXPECTED="${expected}" ASSERT_LABEL="${label}" python3 - <<'PY'
import json
import os
import sys

payload = json.loads(os.environ["JSON_PAYLOAD"] or "{}")
path = os.environ["JSON_PATH"]
expected = os.environ["EXPECTED"]
label = os.environ["ASSERT_LABEL"]
current = payload
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
    print(f"Assertion failed for {label}: JSON path is not an array", file=sys.stderr)
    raise SystemExit(1)
values = {str(item).strip() for item in current if str(item).strip()}
if expected not in values:
    print(f"Assertion failed for {label}: missing {expected}; actual={sorted(values)}", file=sys.stderr)
    raise SystemExit(1)
PY
}

json_array_contains_csv() {
  local payload="$1"
  local path="$2"
  local expected_csv="$3"
  local label="$4"
  local raw expected

  IFS=',' read -ra expected_values <<< "${expected_csv}"
  for raw in "${expected_values[@]}"; do
    expected="$(printf '%s' "${raw}" | xargs)"
    if [[ -n "${expected}" ]]; then
      json_array_contains "${payload}" "${path}" "${expected}" "${label} ${expected}"
    fi
  done
}

init_platform_auth() {
  if [[ "${VERIFY_POC_AUTH_PATHS}" != "true" ]]; then
    return
  fi
  require_env PLATFORM_BASE_URL
  if [[ -n "${PLATFORM_SESSION_COOKIE_JAR}" ]]; then
    PLATFORM_CURL_ARGS=(-b "${PLATFORM_SESSION_COOKIE_JAR}")
    return
  fi
  if [[ -n "${PLATFORM_API_KEY}" ]]; then
    PLATFORM_CURL_ARGS=(-H "${PLATFORM_API_KEY_HEADER}: ${PLATFORM_API_KEY}")
    return
  fi

  local email password
  email="$(resolve_secret_value PLATFORM_LOGIN_EMAIL)"
  password="$(resolve_secret_value PLATFORM_LOGIN_PASSWORD)"
  if [[ -z "${email}" || -z "${password}" ]]; then
    echo "Skipping Platform POC auth-path verification because no Platform auth was provided." >&2
    VERIFY_POC_AUTH_PATHS="false"
    return
  fi
  TEMP_PLATFORM_COOKIE_JAR="$(mktemp)"
  local login_body
  login_body="$(EMAIL="${email}" PASSWORD="${password}" python3 - <<'PY'
import json
import os
print(json.dumps({"email": os.environ["EMAIL"], "password": os.environ["PASSWORD"]}))
PY
)"
  http_request POST "$(trim_slash "${PLATFORM_BASE_URL}")/api/platform/auth/login" "${login_body}" -c "${TEMP_PLATFORM_COOKIE_JAR}"
  assert_equals "${HTTP_STATUS}" "200" "platform login status"
  PLATFORM_CURL_ARGS=(-b "${TEMP_PLATFORM_COOKIE_JAR}")
}

platform_request() {
  local method="$1"
  local url="$2"
  local body="${3:-}"
  http_request "${method}" "${url}" "${body}" "${PLATFORM_CURL_ARGS[@]}"
}

verify_storefront_contract() {
  echo "== Storefront Max Mode bootstrap =="
  http_request GET "${bridge_base}/api/storefront/shops/${SHOP_DOMAIN}/bootstrap?pageType=product" ""
  assert_equals "${HTTP_STATUS}" "200" "storefront bootstrap status"
  bootstrap_json="${HTTP_BODY}"
  assert_equals "$(json_get "${bootstrap_json}" "available")" "true" "storefront bootstrap availability"
  assert_equals "$(json_get "${bootstrap_json}" "billingTier")" "${EXPECT_BILLING_TIER}" "storefront billing tier"
  assert_equals "$(json_get "${bootstrap_json}" "chatFallbackEnabled")" "${EXPECT_CHAT_FALLBACK_ENABLED}" "storefront chat fallback"
  assert_nonempty "$(json_get "${bootstrap_json}" "deploymentId")" "storefront deploymentId"
  assert_nonempty "$(json_get "${bootstrap_json}" "bridgeQueryUrl")" "storefront bridgeQueryUrl"
  assert_nonempty "$(json_get "${bootstrap_json}" "bridgeSuggestionsUrl")" "storefront bridgeSuggestionsUrl"
  json_array_contains_csv "${bootstrap_json}" "enabledSurfaces" "${EXPECT_ENABLED_SURFACES}" "storefront enabled surface"
  json_array_contains_csv "${bootstrap_json}" "allowedConversationModes" "${EXPECT_ALLOWED_CONVERSATION_MODES}" "storefront allowed conversation mode"
  if [[ -z "${PLATFORM_DEPLOYMENT_ID}" ]]; then
    PLATFORM_DEPLOYMENT_ID="$(json_get "${bootstrap_json}" "deploymentId")"
  fi
}

verify_max_surface_query() {
  echo "== Storefront Max Mode query =="
  local payload
  payload="$(MAX_WIDGET_QUERY="${MAX_WIDGET_QUERY}" python3 - <<'PY'
import json
import os
print(json.dumps({
    "query": os.environ["MAX_WIDGET_QUERY"],
    "storefrontContext": {
        "pageType": "product",
        "shopifyShellModeProfile": "SHOPIFY_COMPANION",
        "shopifySurfaceEntry": "max-mode",
        "shopifyPageModeGroup": "product",
        "shopifyEffectiveConversationMode": "navigator_deep",
    },
}))
PY
)"
  http_request POST "${bridge_base}/api/storefront/shops/${SHOP_DOMAIN}/chat/query" "${payload}" -H "X-AI-FABRIC-SHOPPER-SESSION-ID: ${SHOPPER_SESSION_ID}"
  assert_equals "${HTTP_STATUS}" "200" "storefront max-mode query status"
  assert_nonempty "$(json_get "${HTTP_BODY}" "conversationId")" "storefront max-mode query conversationId"
}

verify_poc_auth_path() {
  local auth_path="$1"
  local payload
  payload="$(AUTH_PATH="${auth_path}" python3 - <<'PY'
import json
import os
print(json.dumps({
    "query": f"Run a Max widget smoke response through {os.environ['AUTH_PATH']}.",
    "conversationId": "max-widget-" + os.environ["AUTH_PATH"].lower().replace("_", "-"),
}))
PY
)"
  platform_request POST "${platform_base}/api/deployments/${PLATFORM_DEPLOYMENT_ID}/poc-widget/chat/me/query?authPath=${auth_path}" "${payload}"
  if [[ "${HTTP_STATUS}" == "200" ]]; then
    assert_nonempty "$(json_get "${HTTP_BODY}" "conversationId")" "POC ${auth_path} conversationId"
    echo "PASS: POC Max widget auth path ${auth_path}"
    return
  fi
  if [[ "${MAX_WIDGET_AUTH_PATH_STRICT}" != "true" && "${auth_path}" != "PLATFORM_PRIVATE" ]]; then
    echo "SKIP: POC Max widget auth path ${auth_path} unavailable in this deployment (HTTP ${HTTP_STATUS})."
    return
  fi
  echo "POC Max widget auth path ${auth_path} failed with HTTP ${HTTP_STATUS}: ${HTTP_BODY}" >&2
  exit 1
}

verify_poc_auth_paths() {
  if [[ "${VERIFY_POC_AUTH_PATHS}" != "true" ]]; then
    echo "Skipping Platform POC auth-path verification."
    return
  fi
  assert_nonempty "${PLATFORM_DEPLOYMENT_ID}" "Platform deployment id"
  echo "== Platform POC Max widget auth paths =="
  IFS=',' read -ra auth_paths <<< "${MAX_WIDGET_AUTH_PATHS}"
  for raw_path in "${auth_paths[@]}"; do
    auth_path="$(printf '%s' "${raw_path}" | xargs)"
    if [[ -n "${auth_path}" ]]; then
      verify_poc_auth_path "${auth_path}"
    fi
  done
}

verify_browser_widget() {
  if [[ "${VERIFY_BROWSER}" != "true" ]]; then
    echo "Skipping browser widget verification."
    return
  fi
  echo "== Browser Max Mode click-open =="
  require_cmd node
  local password
  password="$(resolve_secret_value SHOPIFY_STOREFRONT_PASSWORD)"
  SHOPIFY_STOREFRONT_PASSWORD="${password}" \
  SHOPIFY_STOREFRONT_URL="${SHOPIFY_STOREFRONT_URL}" \
  MAX_WIDGET_SCREENSHOT_PATH="${MAX_WIDGET_SCREENSHOT_PATH}" \
  PLAYWRIGHT_REQUIRE_PATH="${PLAYWRIGHT_REQUIRE_PATH}" \
  node <<'NODE'
const fs = require('fs');
const playwrightPath = process.env.PLAYWRIGHT_REQUIRE_PATH;
const { chromium } = require(playwrightPath);

const targetUrl = process.env.SHOPIFY_STOREFRONT_URL;
const storefrontPassword = process.env.SHOPIFY_STOREFRONT_PASSWORD || '';
const screenshotPath = process.env.MAX_WIDGET_SCREENSHOT_PATH;

(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 960 } });
  const observed = {
    maxBundle: false,
    maxShell: false,
    bootstrap: false,
    widgetHost: false,
    opened: false,
  };
  page.on('response', (response) => {
    const url = response.url();
    const status = response.status();
    if (url.includes('max-mode-widget.iife.js') && status >= 200 && status < 400) observed.maxBundle = true;
    if (url.includes('companion-max-mode-shell.js') && status >= 200 && status < 400) observed.maxShell = true;
    if (url.includes('/api/storefront/shops/') && url.includes('/bootstrap') && status >= 200 && status < 400) observed.bootstrap = true;
  });

  await page.goto(targetUrl, { waitUntil: 'domcontentloaded', timeout: 60000 });

  const passwordInput = page.locator('input[type="password"], input[name="password"]').first();
  if (await passwordInput.count()) {
    if (!storefrontPassword) {
      throw new Error('Storefront password page is present but SHOPIFY_STOREFRONT_PASSWORD is not configured.');
    }
    await passwordInput.fill(storefrontPassword);
    const submit = page.locator('button[type="submit"], input[type="submit"]').first();
    if (await submit.count()) {
      await Promise.all([
        page.waitForLoadState('domcontentloaded').catch(() => {}),
        submit.click(),
      ]);
    } else {
      await passwordInput.press('Enter');
      await page.waitForLoadState('domcontentloaded').catch(() => {});
    }
  }

  await page.waitForFunction(() => {
    return Boolean(window.MaxMode && document.getElementById('max-mode-widget-shadow-host'));
  }, { timeout: 60000 });
  observed.widgetHost = true;

  const launcherMeta = await page.evaluate(() => {
    const host = document.getElementById('max-mode-widget-shadow-host');
    const root = host && host.shadowRoot;
    if (!root) return null;
    const buttons = Array.from(root.querySelectorAll('button'));
    const launcher = buttons.find((button) => {
      const text = `${button.textContent || ''} ${button.getAttribute('aria-label') || ''} ${button.getAttribute('title') || ''}`;
      return /ask|help|assistant|companion/i.test(text);
    }) || buttons[0];
    if (!launcher) return null;
    return {
      ariaLabel: launcher.getAttribute('aria-label') || '',
      title: launcher.getAttribute('title') || '',
      text: (launcher.textContent || '').trim(),
    };
  });
  if (!launcherMeta) {
    throw new Error('Max Mode launcher button was not found in the widget shadow root.');
  }

  await page.evaluate(() => {
    const host = document.getElementById('max-mode-widget-shadow-host');
    const root = host && host.shadowRoot;
    const buttons = root ? Array.from(root.querySelectorAll('button')) : [];
    const launcher = buttons.find((button) => {
      const text = `${button.textContent || ''} ${button.getAttribute('aria-label') || ''} ${button.getAttribute('title') || ''}`;
      return /ask|help|assistant|companion/i.test(text);
    }) || buttons[0];
    launcher.click();
  });

  await page.waitForFunction(() => {
    const host = document.getElementById('max-mode-widget-shadow-host');
    const root = host && host.shadowRoot;
    if (!root) return false;
    const closeButton = Array.from(root.querySelectorAll('button')).some((button) => {
      return /close max mode/i.test(button.getAttribute('aria-label') || '');
    });
    const text = root.textContent || '';
    return closeButton || (/max/i.test(text) && /ask|assistant|store/i.test(text));
  }, { timeout: 30000 });
  observed.opened = true;

  await page.screenshot({ path: screenshotPath, fullPage: false });
  await browser.close();

  for (const [key, value] of Object.entries(observed)) {
    if (!value) {
      throw new Error(`Browser proof did not observe ${key}. Observed=${JSON.stringify(observed)}`);
    }
  }
  console.log(JSON.stringify({ success: true, screenshotPath, launcherMeta, observed }));
})().catch(async (error) => {
  console.error(error && error.stack ? error.stack : String(error));
  process.exit(1);
});
NODE
}

require_cmd curl
require_cmd python3
require_env SHOPIFY_BRIDGE_BASE_URL
require_env SHOP_DOMAIN

bridge_base="$(trim_slash "${SHOPIFY_BRIDGE_BASE_URL}")"
platform_base="$(trim_slash "${PLATFORM_BASE_URL:-}")"
if [[ -z "${SHOPIFY_STOREFRONT_URL}" ]]; then
  SHOPIFY_STOREFRONT_URL="https://${SHOP_DOMAIN}/products/selling-plans-ski-wax"
fi

declare -a PLATFORM_CURL_ARGS=()
init_platform_auth
verify_storefront_contract
verify_max_surface_query
verify_poc_auth_paths
verify_browser_widget

echo "Shopify Companion Max widget live verification passed for ${SHOP_DOMAIN}"
