#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${PLATFORM_BASE_URL:-${PARTNER_PLATFORM_BASE_URL:-}}"
PLATFORM_UI_BASE_URL="${PLATFORM_UI_BASE_URL:-}"
PARTNER_UI_BASE_URL="${PARTNER_UI_BASE_URL:-}"
PARTNER_SUPABASE_JWT="${PARTNER_SUPABASE_JWT:-}"
PLATFORM_API_KEY_HEADER="${PLATFORM_API_KEY_HEADER:-X-PLATFORM-API-KEY}"
PLATFORM_API_KEY="${PLATFORM_API_KEY:-}"
PLATFORM_COOKIE="${PLATFORM_COOKIE:-}"
PLATFORM_LOGIN_EMAIL="${PLATFORM_LOGIN_EMAIL:-}"
PLATFORM_LOGIN_PASSWORD="${PLATFORM_LOGIN_PASSWORD:-}"
STRICT="${PARTNER_LIVE_STRICT:-false}"
TARGET_STORE_ID="${PARTNER_LIVE_STORE_ID:-}"
TARGET_SHOP_DOMAIN="${PARTNER_LIVE_SHOP_DOMAIN:-${SHOP_DOMAIN:-}}"
PRODUCTION_PROMOTION_PROOF="${PARTNER_LIVE_PRODUCTION_PROMOTION_PROOF:-false}"
RUN_TAG="${PARTNER_LIVE_RUN_TAG:-release-gate-$(date -u +%Y%m%dT%H%M%SZ)}"
REQUIRED_STORE_SURFACES="${PARTNER_LIVE_REQUIRED_STORE_SURFACES:-}"
PACKAGE_TRIAL_PRIVILEGE="PACKAGE_TRIAL_ACTIVATE"
TEMP_ACCESS_REQUEST_ID=""
TEMP_ACCESS_SHOP_DOMAIN=""
TEMP_ACCESS_APPROVAL_CODE=""
PARTNER_TRIAL_MEMBER_ID=""
PARTNER_TRIAL_MEMBER_ORIGINAL_PRIVILEGES=""
PARTNER_TRIAL_MEMBER_PRIVILEGE_TOUCHED="false"
TMP_DIR=""
PLATFORM_COOKIE_JAR=""

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
print(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
PY
    return
  fi

  printf '%s' "${direct_value}"
}

if [[ -z "${BASE_URL}" ]]; then
  echo "ERROR: set PLATFORM_BASE_URL or PARTNER_PLATFORM_BASE_URL." >&2
  exit 2
fi

request() {
  local method="$1"
  local url="$2"
  local out="$3"
  shift 3
  curl -sS -X "${method}" -o "${out}" -w "%{http_code}" "$@" "${url}"
}

partner_request() {
  local method="$1"
  local url="$2"
  local out="$3"
  shift 3
  request "${method}" "${url}" "${out}" -H "Authorization: Bearer ${PARTNER_SUPABASE_JWT}" "$@"
}

platform_request() {
  local method="$1"
  local url="$2"
  local out="$3"
  shift 3
  local headers=()
  local curl_args=(-sS -X "${method}" -o "${out}" -w "%{http_code}")
  if [[ -n "${PLATFORM_API_KEY}" ]]; then
    headers+=("-H" "${PLATFORM_API_KEY_HEADER}: ${PLATFORM_API_KEY}")
  fi
  if [[ -n "${PLATFORM_COOKIE}" ]]; then
    headers+=("-H" "Cookie: ${PLATFORM_COOKIE}")
  fi
  if ((${#headers[@]})); then
    curl_args+=("${headers[@]}")
  fi
  if [[ -n "${PLATFORM_COOKIE_JAR}" && -s "${PLATFORM_COOKIE_JAR}" ]]; then
    curl "${curl_args[@]}" -b "${PLATFORM_COOKIE_JAR}" -c "${PLATFORM_COOKIE_JAR}" "$@" "${url}"
  else
    curl "${curl_args[@]}" "$@" "${url}"
  fi
}

platform_auth_available() {
  [[ -n "${PLATFORM_API_KEY}" || -n "${PLATFORM_COOKIE}" || ( -n "${PLATFORM_COOKIE_JAR}" && -s "${PLATFORM_COOKIE_JAR}" ) ]]
}

urlencode() {
  python3 - <<'PY' "$1"
import sys
import urllib.parse
print(urllib.parse.quote(sys.argv[1], safe=""))
PY
}

assert_status() {
  local actual="$1"
  local expected="$2"
  local label="$3"
  if [[ "${actual}" != "${expected}" ]]; then
    echo "FAIL: ${label}: expected HTTP ${expected}, got ${actual}" >&2
    if [[ -s "${TMP_DIR}/last-body" ]]; then
      python3 - <<'PY' "${TMP_DIR}/last-body" >&2
import pathlib, sys
body = pathlib.Path(sys.argv[1]).read_text(errors="replace")
print(body[:800])
PY
    fi
    exit 1
  fi
  echo "PASS: ${label} (${actual})"
}

cleanup() {
  local exit_code=$?
  if [[ "${PARTNER_TRIAL_MEMBER_PRIVILEGE_TOUCHED}" == "true" && -n "${PARTNER_TRIAL_MEMBER_ID}" && -n "${PARTNER_TRIAL_MEMBER_ORIGINAL_PRIVILEGES}" && -n "${TMP_DIR}" ]] && platform_auth_available; then
    local restore_payload="${TMP_DIR}/cleanup-partner-trial-privileges.json"
    local restore_body="${TMP_DIR}/cleanup-partner-trial-privileges-response.json"
    python3 - <<'PY' "${PARTNER_TRIAL_MEMBER_ORIGINAL_PRIVILEGES}" "${restore_payload}"
import json
import pathlib
import sys

privileges = json.loads(sys.argv[1])
pathlib.Path(sys.argv[2]).write_text(json.dumps({"privileges": privileges}), encoding="utf-8")
PY
    local restore_status
    restore_status="$(platform_request PATCH "${BASE_URL}/api/platform/partners/members/${PARTNER_TRIAL_MEMBER_ID}" "${restore_body}" \
      -H "Content-Type: application/json" \
      --data "@${restore_payload}" || true)"
    if [[ "${restore_status}" == "200" ]]; then
      echo "PASS: partner package-trial privilege restored during cleanup"
      PARTNER_TRIAL_MEMBER_PRIVILEGE_TOUCHED="false"
    else
      echo "WARN: partner package-trial privilege cleanup returned HTTP ${restore_status:-unknown}" >&2
    fi
  fi
  if [[ -n "${TEMP_ACCESS_APPROVAL_CODE}" && -n "${TMP_DIR}" ]]; then
    local cleanup_body="${TMP_DIR}/cleanup-revoke.json"
    local cleanup_status
    cleanup_status="$(request POST "${BASE_URL}/api/merchant/partner-access/${TEMP_ACCESS_APPROVAL_CODE}/revoke" "${cleanup_body}" \
      -H "Content-Type: application/json" \
      -d '{"approverName":"Release Gate Cleanup","approverEmail":"release-gate@loom.test","decisionReason":"Release gate temporary access cleanup."}' || true)"
    if [[ "${cleanup_status}" == "200" ]]; then
      echo "PASS: temporary partner access revoked during cleanup"
    else
      echo "WARN: temporary partner access cleanup returned HTTP ${cleanup_status:-unknown}" >&2
    fi
  elif [[ -n "${TEMP_ACCESS_REQUEST_ID}" && -n "${TEMP_ACCESS_SHOP_DOMAIN}" && -n "${TMP_DIR}" ]] && platform_auth_available; then
    local cleanup_body="${TMP_DIR}/cleanup-revoke.json"
    local encoded_shop
    encoded_shop="$(urlencode "${TEMP_ACCESS_SHOP_DOMAIN}")"
    local cleanup_status
    cleanup_status="$(platform_request POST "${BASE_URL}/api/merchant/partner-access/requests/${TEMP_ACCESS_REQUEST_ID}/revoke?shopDomain=${encoded_shop}" "${cleanup_body}" \
      -H "Content-Type: application/json" \
      -d '{"approverName":"Release Gate Cleanup","approverEmail":"release-gate@loom.test","decisionReason":"Release gate temporary access cleanup."}' || true)"
    if [[ "${cleanup_status}" == "200" ]]; then
      echo "PASS: temporary partner access revoked during cleanup"
    else
      echo "WARN: temporary partner access cleanup returned HTTP ${cleanup_status:-unknown}" >&2
    fi
  fi
  if [[ -n "${TMP_DIR}" ]]; then
    rm -rf "${TMP_DIR}"
  fi
  exit "${exit_code}"
}

platform_login_if_needed() {
  if [[ -n "${PLATFORM_API_KEY}" || -n "${PLATFORM_COOKIE}" ]]; then
    return
  fi
  if [[ -z "${PLATFORM_LOGIN_EMAIL}" || -z "${PLATFORM_LOGIN_PASSWORD}" ]]; then
    echo "FAIL: strict Partner Enablement verification requires PLATFORM_API_KEY, PLATFORM_COOKIE, or PLATFORM_LOGIN_EMAIL/PLATFORM_LOGIN_PASSWORD for merchant approval proof." >&2
    exit 24
  fi
  local login_body="${TMP_DIR}/platform-login.json"
  local login_payload="${TMP_DIR}/platform-login-payload.json"
  python3 - <<'PY' "${PLATFORM_LOGIN_EMAIL}" "${PLATFORM_LOGIN_PASSWORD}" "${login_payload}"
import json
import pathlib
import sys

pathlib.Path(sys.argv[3]).write_text(json.dumps({"email": sys.argv[1], "password": sys.argv[2]}), encoding="utf-8")
PY
  local login_status
  login_status="$(curl -sS -X POST -o "${login_body}" -w "%{http_code}" -c "${PLATFORM_COOKIE_JAR}" \
    -H "Content-Type: application/json" \
    --data "@${login_payload}" \
    "${BASE_URL}/api/platform/auth/login")"
  rm -f "${login_payload}"
  cp "${login_body}" "${TMP_DIR}/last-body"
  assert_status "${login_status}" "200" "platform admin session login"
  python3 - <<'PY' "${login_body}"
import json
import pathlib
import sys

data = json.loads(pathlib.Path(sys.argv[1]).read_text())
assert data.get("authenticated") is True, data
print("PASS: platform admin session cookie established")
PY
}

PARTNER_SUPABASE_JWT="$(resolve_secret_value "PARTNER_SUPABASE_JWT")"
PLATFORM_API_KEY="$(resolve_secret_value "PLATFORM_API_KEY")"
PLATFORM_COOKIE="$(resolve_secret_value "PLATFORM_COOKIE")"
PLATFORM_LOGIN_EMAIL="$(resolve_secret_value "PLATFORM_LOGIN_EMAIL")"
PLATFORM_LOGIN_PASSWORD="$(resolve_secret_value "PLATFORM_LOGIN_PASSWORD")"
BASE_URL="${BASE_URL%/}"
PLATFORM_UI_BASE_URL="${PLATFORM_UI_BASE_URL%/}"
PARTNER_UI_BASE_URL="${PARTNER_UI_BASE_URL%/}"
TMP_DIR="$(mktemp -d)"
PLATFORM_COOKIE_JAR="${TMP_DIR}/platform-cookie.jar"
trap cleanup EXIT

health_body="${TMP_DIR}/health.json"
cp /dev/null "${TMP_DIR}/last-body"
health_status="$(request GET "${BASE_URL}/actuator/health" "${health_body}")"
cp "${health_body}" "${TMP_DIR}/last-body"
if [[ "${health_status}" != "200" ]]; then
  echo "FAIL: backend health expected HTTP 200, got ${health_status}" >&2
  exit 1
fi
echo "PASS: backend health reachable"

session_unauth_body="${TMP_DIR}/session-unauth.json"
session_unauth_status="$(request GET "${BASE_URL}/api/partners/session" "${session_unauth_body}")"
cp "${session_unauth_body}" "${TMP_DIR}/last-body"
assert_status "${session_unauth_status}" "401" "unauthenticated partner session is rejected"

session_invalid_body="${TMP_DIR}/session-invalid.json"
session_invalid_status="$(request GET "${BASE_URL}/api/partners/session" "${session_invalid_body}" -H "Authorization: Bearer invalid.partner.jwt")"
cp "${session_invalid_body}" "${TMP_DIR}/last-body"
assert_status "${session_invalid_status}" "401" "invalid partner JWT is rejected"

if [[ -n "${PARTNER_UI_BASE_URL}" ]]; then
  ui_health_body="${TMP_DIR}/partner-ui-health.json"
  ui_health_status="$(request GET "${PARTNER_UI_BASE_URL}/health" "${ui_health_body}")"
  cp "${ui_health_body}" "${TMP_DIR}/last-body"
  assert_status "${ui_health_status}" "200" "partner UI health reachable"

  ui_runtime_body="${TMP_DIR}/partner-ui-runtime-config.js"
  ui_runtime_status="$(request GET "${PARTNER_UI_BASE_URL}/runtime-config.js" "${ui_runtime_body}")"
  cp "${ui_runtime_body}" "${TMP_DIR}/last-body"
  assert_status "${ui_runtime_status}" "200" "partner UI runtime config reachable"
python3 - <<'PY' "${ui_runtime_body}" "${BASE_URL}"
import json
import pathlib
import re
import sys

body = pathlib.Path(sys.argv[1]).read_text(errors="replace")
expected_platform_base = sys.argv[2].rstrip("/")
try:
    match = re.search(r"Object\.freeze\((\{.*\})\)", body, re.DOTALL)
    if not match:
        raise AssertionError("runtime config did not expose Object.freeze({...})")
    config = json.loads(match.group(1))
    platform_base = str(config.get("platformApiBaseUrl") or "").rstrip("/")
    supabase_url = str(config.get("supabaseUrl") or "")
    supabase_anon_key = str(config.get("supabaseAnonKey") or "")
    if platform_base != expected_platform_base:
        raise AssertionError("runtime platform API base URL is missing or points at the wrong backend")
    if not supabase_url.startswith("https://"):
        raise AssertionError("runtime Supabase URL is missing")
    if not supabase_anon_key:
        raise AssertionError("runtime Supabase anon key is missing")
except Exception as exc:
    print(f"FAIL: partner UI runtime config is not deploy-ready: {exc}", file=sys.stderr)
    sys.exit(1)
print("PASS: partner UI runtime config is populated")
PY

  ui_body="${TMP_DIR}/partner-ui.html"
  ui_status="$(request GET "${PARTNER_UI_BASE_URL}/" "${ui_body}")"
  cp "${ui_body}" "${TMP_DIR}/last-body"
  if [[ "${ui_status}" != "200" ]]; then
    echo "FAIL: partner UI route expected HTTP 200, got ${ui_status}" >&2
    exit 1
  fi
  if ! grep -qi "<script" "${ui_body}"; then
    echo "FAIL: partner UI response did not include an application script." >&2
    exit 1
  fi
  echo "PASS: partner UI route reachable"
  python3 - <<'PY' "${ui_body}" "${PARTNER_UI_BASE_URL}"
from html.parser import HTMLParser
from urllib.parse import urljoin
from urllib.request import Request, urlopen
import pathlib
import sys

html = pathlib.Path(sys.argv[1]).read_text(errors="replace")
base_url = sys.argv[2].rstrip("/") + "/"

class ScriptParser(HTMLParser):
    def __init__(self):
        super().__init__()
        self.sources = []

    def handle_starttag(self, tag, attrs):
        if tag.lower() != "script":
            return
        attrs = dict(attrs)
        src = attrs.get("src")
        if src:
            self.sources.append(urljoin(base_url, src))

parser = ScriptParser()
parser.feed(html)
if not parser.sources:
    print("FAIL: partner UI route did not expose script assets", file=sys.stderr)
    raise SystemExit(1)

combined = ""
for source in parser.sources:
    request = Request(source, headers={"User-Agent": "partner-release-gate/1.0"})
    with urlopen(request, timeout=20) as response:
        if response.status != 200:
            print(f"FAIL: partner UI asset {source} returned HTTP {response.status}", file=sys.stderr)
            raise SystemExit(1)
        combined += response.read().decode("utf-8", errors="replace")

required = [
    "/api/partners/activity",
    "/api/partners/verification-packs",
    "/api/partners/evidence-bundles",
    "/api/partners/templates",
    "/api/partners/template-applications",
    "/api/partners/stores/",
    "/launch-readiness",
    "/production-promotions",
    "/max-widget",
    "Live Max widget test",
    "Partner-secured routes",
    "Production readiness",
    "Request production promotion",
    "/notes",
    "/escalations",
    "/package-trials",
    "Package trial",
    "Recent trial activity",
    "PACKAGE_TRIAL_ACTIVATE",
    "010.1 launch package",
    "Design-partner package",
    "Weekly value review",
    "App Store/private listing",
]
missing = [item for item in required if item not in combined]
if missing:
    print(f"FAIL: partner UI deployed assets are missing workflow routes: {missing}", file=sys.stderr)
    raise SystemExit(1)
print("PASS: partner UI deployed assets include workflow surfaces")
PY

  ui_widget_bundle_body="${TMP_DIR}/partner-ui-max-widget-bundle.js"
  ui_widget_bundle_status="$(request GET "${PARTNER_UI_BASE_URL}/max-mode-widget.iife.js" "${ui_widget_bundle_body}")"
  cp "${ui_widget_bundle_body}" "${TMP_DIR}/last-body"
  assert_status "${ui_widget_bundle_status}" "200" "partner UI Max widget bundle reachable"
  python3 - <<'PY' "${ui_widget_bundle_body}"
import pathlib
import sys

body = pathlib.Path(sys.argv[1]).read_text(errors="replace")
if "MaxMode" not in body:
    print("FAIL: deployed Max widget bundle does not expose MaxMode", file=sys.stderr)
    raise SystemExit(1)
print("PASS: partner UI Max widget bundle exposes MaxMode")
PY
else
  echo "BLOCKED: PARTNER_UI_BASE_URL is not set; deployed partner UI route was not checked."
  if [[ "${STRICT}" == "true" ]]; then
    exit 20
  fi
fi

if [[ -n "${PLATFORM_UI_BASE_URL}" ]]; then
  platform_ui_body="${TMP_DIR}/platform-ui-partner-privileges.html"
  platform_ui_status="$(request GET "${PLATFORM_UI_BASE_URL}/partner-privileges" "${platform_ui_body}")"
  cp "${platform_ui_body}" "${TMP_DIR}/last-body"
  assert_status "${platform_ui_status}" "200" "Platform UI partner privileges route reachable"
  python3 - <<'PY' "${platform_ui_body}" "${PLATFORM_UI_BASE_URL}"
from html.parser import HTMLParser
from urllib.parse import urljoin
from urllib.request import Request, urlopen
import pathlib
import sys

html = pathlib.Path(sys.argv[1]).read_text(errors="replace")
base_url = sys.argv[2].rstrip("/") + "/"

class ScriptParser(HTMLParser):
    def __init__(self):
        super().__init__()
        self.sources = []

    def handle_starttag(self, tag, attrs):
        if tag.lower() != "script":
            return
        attrs = dict(attrs)
        src = attrs.get("src")
        if src:
            self.sources.append(urljoin(base_url, src))

parser = ScriptParser()
parser.feed(html)
if not parser.sources:
    print("FAIL: Platform UI partner privileges route did not expose script assets", file=sys.stderr)
    raise SystemExit(1)

combined = ""
for source in parser.sources:
    request = Request(source, headers={"User-Agent": "partner-release-gate/1.0"})
    with urlopen(request, timeout=20) as response:
        if response.status != 200:
            print(f"FAIL: Platform UI asset {source} returned HTTP {response.status}", file=sys.stderr)
            raise SystemExit(1)
        combined += response.read().decode("utf-8", errors="replace")

required = [
    "Partner Privileges",
    "PACKAGE_TRIAL_ACTIVATE",
    "/api/platform/partners/members",
]
missing = [item for item in required if item not in combined]
if missing:
    print(f"FAIL: Platform UI deployed assets are missing partner privilege controls: {missing}", file=sys.stderr)
    raise SystemExit(1)
print("PASS: Platform UI deployed assets include partner privilege controls")
PY

  platform_launch_body="${TMP_DIR}/platform-ui-shopify-launch.html"
  platform_launch_status="$(request GET "${PLATFORM_UI_BASE_URL}/shopify-launch-readiness" "${platform_launch_body}")"
  cp "${platform_launch_body}" "${TMP_DIR}/last-body"
  assert_status "${platform_launch_status}" "200" "Platform UI Shopify launch readiness route reachable"
  python3 - <<'PY' "${platform_launch_body}" "${PLATFORM_UI_BASE_URL}"
from html.parser import HTMLParser
from urllib.parse import urljoin
from urllib.request import Request, urlopen
import pathlib
import sys

html = pathlib.Path(sys.argv[1]).read_text(errors="replace")
base_url = sys.argv[2].rstrip("/") + "/"

class ScriptParser(HTMLParser):
    def __init__(self):
        super().__init__()
        self.sources = []

    def handle_starttag(self, tag, attrs):
        if tag.lower() != "script":
            return
        attrs = dict(attrs)
        src = attrs.get("src")
        if src:
            self.sources.append(urljoin(base_url, src))

parser = ScriptParser()
parser.feed(html)
if not parser.sources:
    print("FAIL: Platform UI Shopify launch route did not expose script assets", file=sys.stderr)
    raise SystemExit(1)

combined = ""
for source in parser.sources:
    request = Request(source, headers={"User-Agent": "partner-release-gate/1.0"})
    with urlopen(request, timeout=20) as response:
        if response.status != 200:
            print(f"FAIL: Platform UI asset {source} returned HTTP {response.status}", file=sys.stderr)
            raise SystemExit(1)
        combined += response.read().decode("utf-8", errors="replace")

required = [
    "Shopify Launch Readiness",
    "App Store/private listing readiness",
    "Controlled production proof",
    "Customer Account MCP",
    "Checkout MCP",
    "010_SELF_SERVICE_PRODUCTION_READY",
    "staging remains untouched",
]
missing = [item for item in required if item not in combined]
if missing:
    print(f"FAIL: Platform UI deployed assets are missing Shopify launch readiness surfaces: {missing}", file=sys.stderr)
    raise SystemExit(1)
print("PASS: Platform UI deployed assets include Shopify launch readiness surfaces")
PY
else
  echo "BLOCKED: PLATFORM_UI_BASE_URL is not set; deployed Platform UI partner privileges route was not checked."
  if [[ "${STRICT}" == "true" ]]; then
    exit 22
  fi
fi

if [[ -z "${PARTNER_SUPABASE_JWT}" ]]; then
  echo "BLOCKED: PARTNER_SUPABASE_JWT is not set; authenticated partner workspace checks were not run."
  if [[ "${STRICT}" == "true" ]]; then
    exit 21
  fi
  exit 0
fi

session_body="${TMP_DIR}/session-auth.json"
session_status="$(partner_request GET "${BASE_URL}/api/partners/session" "${session_body}")"
cp "${session_body}" "${TMP_DIR}/last-body"
assert_status "${session_status}" "200" "valid partner JWT is accepted"

python3 - <<'PY' "${session_body}"
import json, pathlib, sys
data = json.loads(pathlib.Path(sys.argv[1]).read_text())
assert data["authenticated"] is True, data
assert "permissions" in data and isinstance(data["permissions"], list), data
print("PASS: partner session payload is shaped correctly")
if data.get("signupRequired"):
    assert data.get("assignedStoreCount") == 0, data
    print("PASS: new partner sees empty workspace state")
PY

set +e
session_state="$(python3 - <<'PY' "${session_body}" "${STRICT}"
import json
import pathlib
import sys

data = json.loads(pathlib.Path(sys.argv[1]).read_text())
strict = sys.argv[2].lower() == "true"
if data.get("signupRequired"):
    print("SIGNUP")
else:
    print("PROVISIONED")
PY
)"
session_state_rc=$?
set -e
if [[ "${session_state_rc}" != "0" ]]; then
  exit "${session_state_rc}"
fi

if [[ "${session_state}" == "SIGNUP" && "${STRICT}" == "true" ]]; then
  signup_body="${TMP_DIR}/signup-complete.json"
  signup_status="$(partner_request POST "${BASE_URL}/api/partners/signup/complete" "${signup_body}" \
    -H "Content-Type: application/json" \
    -d "{\"workspaceName\":\"Release Gate Partner ${RUN_TAG}\"}")"
  cp "${signup_body}" "${TMP_DIR}/last-body"
  assert_status "${signup_status}" "200" "partner workspace signup completed"
  python3 - <<'PY' "${signup_body}"
import json
import pathlib
import sys

data = json.loads(pathlib.Path(sys.argv[1]).read_text())
assert data.get("authenticated") is True, data
assert data.get("signupRequired") is False, data
assert data.get("account"), data
assert data.get("member", {}).get("role") == "PARTNER_ADMIN", data
print("PASS: strict gate provisioned partner workspace")
PY
  session_state="PROVISIONED"
fi

if [[ "${session_state}" == "PROVISIONED" ]]; then
  catalog_body="${TMP_DIR}/catalog.json"
  catalog_status="$(partner_request GET "${BASE_URL}/api/partners/catalog" "${catalog_body}")"
  cp "${catalog_body}" "${TMP_DIR}/last-body"
  assert_status "${catalog_status}" "200" "partner catalog reachable"
  python3 - <<'PY' "${catalog_body}"
import json, pathlib, sys
catalog = json.loads(pathlib.Path(sys.argv[1]).read_text())
surface_ids = {item["surfaceId"] for item in catalog}
assert "ai-search" in surface_ids, surface_ids
assert "order-lookup" not in surface_ids, surface_ids
assert any(item["surfaceId"] == "ai-search" and item["tier"] == "Free" for item in catalog), catalog
assert all(not (item["tier"] == "Starter" and item["surfaceId"] == "order-lookup") for item in catalog), catalog
print("PASS: catalog enforces Free AI search and no Starter order lookup surface")
PY

  stores_body="${TMP_DIR}/stores.json"
  stores_status="$(partner_request GET "${BASE_URL}/api/partners/stores" "${stores_body}")"
  cp "${stores_body}" "${TMP_DIR}/last-body"
  assert_status "${stores_status}" "200" "partner stores endpoint reachable"
  python3 - <<'PY' "${stores_body}"
import json, pathlib, sys
stores = json.loads(pathlib.Path(sys.argv[1]).read_text())
assert isinstance(stores, list), stores
for store in stores:
    forbidden = {"deployment", "provider", "secret", "vectorization", "runtime"}
    raw = json.dumps(store).lower()
    assert not any(term in raw for term in forbidden), store
print("PASS: partner store summaries are partner-safe")
PY

  if [[ "${STRICT}" == "true" ]]; then
    if [[ -z "${TARGET_SHOP_DOMAIN}" ]]; then
      echo "FAIL: strict Partner Enablement verification requires PARTNER_LIVE_SHOP_DOMAIN or SHOP_DOMAIN for the installed test store." >&2
      exit 25
    fi
    platform_login_if_needed
  fi

  set +e
  existing_target_store_id="$(python3 - <<'PY' "${stores_body}" "${TARGET_STORE_ID}" "${TARGET_SHOP_DOMAIN}" "${STRICT}"
import json
import pathlib
import sys

stores = json.loads(pathlib.Path(sys.argv[1]).read_text())
target_id = sys.argv[2].strip()
target_shop = sys.argv[3].strip().lower()
strict = sys.argv[4].lower() == "true"

def active(store):
    return str(store.get("assignmentStatus") or "").upper() == "ACTIVE" and str(store.get("status") or "").upper() == "READY"

matches = []
for store in stores:
    if target_id and store.get("id") != target_id:
        continue
    if target_shop and str(store.get("shopDomain") or "").lower() != target_shop:
        continue
    if not target_id and not target_shop:
        if active(store):
            matches.append(store)
            continue
    elif active(store):
        matches.append(store)

if matches:
    print(matches[0]["id"])
elif strict and (target_id or target_shop):
    print("NONE")
else:
    print("NONE")
PY
)"
  existing_target_rc=$?
  set -e
  if [[ "${existing_target_rc}" != "0" ]]; then
    exit "${existing_target_rc}"
  fi

  workflow_store_id="${existing_target_store_id}"
  created_temp_access="false"
  implementation_id=""
  evidence_bundle_id=""

  if [[ "${STRICT}" == "true" ]]; then
    if [[ "${workflow_store_id}" != "NONE" ]]; then
      echo "FAIL: strict Partner Enablement approval proof requires a clean test partner with no active assignment for ${TARGET_SHOP_DOMAIN}; found ${workflow_store_id}." >&2
      exit 26
    fi

    encoded_target_shop="$(urlencode "${TARGET_SHOP_DOMAIN}")"
    eligible_body="${TMP_DIR}/eligible-stores.json"
    eligible_status="$(partner_request GET "${BASE_URL}/api/partners/eligible-stores?query=${encoded_target_shop}" "${eligible_body}")"
    cp "${eligible_body}" "${TMP_DIR}/last-body"
    assert_status "${eligible_status}" "200" "installed store eligibility reachable"
    store_connection_id="$(python3 - <<'PY' "${eligible_body}" "${TARGET_SHOP_DOMAIN}" "${REQUIRED_STORE_SURFACES}"
import json
import pathlib
import sys

stores = json.loads(pathlib.Path(sys.argv[1]).read_text())
target_shop = sys.argv[2].lower()
required_surfaces = {item.strip() for item in sys.argv[3].split(",") if item.strip()}
matches = [store for store in stores if str(store.get("shopDomain") or "").lower() == target_shop]
if not matches:
    print(f"FAIL: no installed eligible store found for {target_shop}", file=sys.stderr)
    raise SystemExit(1)
store = matches[0]
if str(store.get("installStatus") or "").upper() != "INSTALLED":
    print(f"FAIL: eligible store {target_shop} is not installed: {store.get('installStatus')}", file=sys.stderr)
    raise SystemExit(1)
enabled_surfaces = set(store.get("enabledSurfaces") or [])
if not enabled_surfaces:
    print(f"FAIL: eligible store {target_shop} has no store-configured surfaces", file=sys.stderr)
    raise SystemExit(1)
missing_surfaces = sorted(required_surfaces - enabled_surfaces)
if missing_surfaces:
    print(f"FAIL: eligible store {target_shop} is missing required store-configured surfaces: {missing_surfaces}", file=sys.stderr)
    raise SystemExit(1)
print(store["storeConnectionId"])
PY
)"
    echo "PASS: installed eligible store selected (${TARGET_SHOP_DOMAIN})"

    implementation_body="${TMP_DIR}/implementation-create.json"
    implementation_status="$(partner_request POST "${BASE_URL}/api/partners/client-implementations" "${implementation_body}" \
      -H "Content-Type: application/json" \
      -d "{\"clientName\":\"Release Gate ${RUN_TAG}\",\"contactEmail\":\"release-gate@loom.test\",\"storeConnectionId\":\"${store_connection_id}\",\"vertical\":\"release-gate\",\"knownIntegrations\":[\"release-gate\"],\"notes\":\"Automated release gate approval proof for ${RUN_TAG}.\"}")"
    cp "${implementation_body}" "${TMP_DIR}/last-body"
    assert_status "${implementation_status}" "201" "client implementation request created"
    implementation_id="$(python3 - <<'PY' "${implementation_body}" "${TARGET_SHOP_DOMAIN}" "${REQUIRED_STORE_SURFACES}"
import json
import pathlib
import sys

data = json.loads(pathlib.Path(sys.argv[1]).read_text())
target_shop = sys.argv[2].lower()
required_surfaces = {item.strip() for item in sys.argv[3].split(",") if item.strip()}
assert data["status"] == "WAITING_ON_MERCHANT", data
assert str(data.get("shopDomain") or "").lower() == target_shop, data
assert data["requestedTier"] == "MERCHANT_CONFIGURED", data
requested_surfaces = set(data.get("requestedSurfaces") or [])
assert requested_surfaces, data
assert required_surfaces.issubset(requested_surfaces), data
assert data.get("approvalUrl"), data
print(data["id"])
PY
)"
    TEMP_ACCESS_APPROVAL_CODE="$(python3 - <<'PY' "${implementation_body}"
import json
import pathlib
import sys

data = json.loads(pathlib.Path(sys.argv[1]).read_text())
approval_url = str(data.get("approvalUrl") or "").rstrip("/")
approval_code = approval_url.rsplit("/", 1)[-1]
assert approval_code, data
print(approval_code)
PY
)"
    echo "PASS: partner implementation status is waiting on merchant (${implementation_id})"

    implementation_detail_body="${TMP_DIR}/implementation-detail-waiting.json"
    implementation_detail_status="$(partner_request GET "${BASE_URL}/api/partners/client-implementations/${implementation_id}" "${implementation_detail_body}")"
    cp "${implementation_detail_body}" "${TMP_DIR}/last-body"
    assert_status "${implementation_detail_status}" "200" "client implementation detail reachable"

    access_requests_body="${TMP_DIR}/merchant-access-requests.json"
    access_requests_status="$(platform_request GET "${BASE_URL}/api/merchant/partner-access/requests?shopDomain=${encoded_target_shop}" "${access_requests_body}")"
    cp "${access_requests_body}" "${TMP_DIR}/last-body"
    assert_status "${access_requests_status}" "200" "merchant access request list reachable"
    TEMP_ACCESS_REQUEST_ID="$(python3 - <<'PY' "${access_requests_body}" "${implementation_id}"
import json
import pathlib
import sys

requests = json.loads(pathlib.Path(sys.argv[1]).read_text())
implementation_id = sys.argv[2]
matches = [request for request in requests if request.get("implementationRequestId") == implementation_id]
if not matches:
    print("FAIL: merchant access request for implementation was not listed", file=sys.stderr)
    raise SystemExit(1)
request = matches[0]
assert request["status"] == "WAITING_ON_MERCHANT", request
assert request["requestedScope"] == "FULL_STORE_ACCESS", request
assert request["requestedTier"] == "MERCHANT_CONFIGURED", request
print(request["requestId"])
PY
)"
    TEMP_ACCESS_SHOP_DOMAIN="${TARGET_SHOP_DOMAIN}"
    echo "PASS: merchant access request is pending (${TEMP_ACCESS_REQUEST_ID})"

    merchant_invite_body="${TMP_DIR}/merchant-approval-invite.json"
    merchant_invite_status="$(partner_request POST "${BASE_URL}/api/partners/client-implementations/${implementation_id}/merchant-invites" "${merchant_invite_body}" \
      -H "Content-Type: application/json" \
      -d "{}")"
    cp "${merchant_invite_body}" "${TMP_DIR}/last-body"
    assert_status "${merchant_invite_status}" "200" "merchant approval invite requested"
    python3 - <<'PY' "${merchant_invite_body}" "${TEMP_ACCESS_REQUEST_ID}" "${TARGET_SHOP_DOMAIN}"
import json
import pathlib
import sys

invite = json.loads(pathlib.Path(sys.argv[1]).read_text())
assert invite["requestId"] == sys.argv[2], invite
assert str(invite.get("shopDomain") or "").lower() == sys.argv[3].lower(), invite
assert invite.get("status") in {"SENT", "RECORDED"}, invite
assert invite.get("channel") in {"SMTP", "SMTP_DRY_RUN", "EMAIL_DISABLED"}, invite
assert invite.get("approvalUrl"), invite
assert invite.get("inviteCount", 0) >= 1, invite
print("PASS: merchant approval invite has durable delivery evidence")
PY

    merchant_workspace_waiting_body="${TMP_DIR}/merchant-workspace-waiting.json"
    merchant_workspace_waiting_status="$(request GET "${BASE_URL}/api/merchant/partner-access/${TEMP_ACCESS_APPROVAL_CODE}/workspace" "${merchant_workspace_waiting_body}")"
    cp "${merchant_workspace_waiting_body}" "${TMP_DIR}/last-body"
    assert_status "${merchant_workspace_waiting_status}" "200" "merchant approval deep-link workspace reachable"
    python3 - <<'PY' "${merchant_workspace_waiting_body}" "${TEMP_ACCESS_REQUEST_ID}" "${TARGET_SHOP_DOMAIN}"
import json
import pathlib
import sys

workspace = json.loads(pathlib.Path(sys.argv[1]).read_text())
request = workspace["accessRequest"]
assert request["requestId"] == sys.argv[2], workspace
assert str(request.get("shopDomain") or "").lower() == sys.argv[3].lower(), workspace
assert request["status"] == "WAITING_ON_MERCHANT", workspace
assert "APPROVE_PARTNER_ACCESS" in workspace.get("availableActions", []), workspace
assert "DENY_PARTNER_ACCESS" in workspace.get("availableActions", []), workspace
raw = json.dumps(workspace).lower()
for forbidden in ("secret", "token", "password", "credential", "provider_resource", "coolify"):
    assert forbidden not in raw, workspace
print("PASS: merchant approval workspace is available without platform/provider internals")
PY

    approval_body="${TMP_DIR}/merchant-access-approve.json"
    approval_status="$(request POST "${BASE_URL}/api/merchant/partner-access/${TEMP_ACCESS_APPROVAL_CODE}/approve" "${approval_body}" \
      -H "Content-Type: application/json" \
      -d '{"approverName":"Release Gate Merchant","approverEmail":"release-gate-merchant@loom.test","approvedScope":"FULL_STORE_ACCESS","decisionReason":"Automated release gate approval proof."}')"
    cp "${approval_body}" "${TMP_DIR}/last-body"
    assert_status "${approval_status}" "200" "merchant access request approved through approval deep-link"
    workflow_store_id="$(python3 - <<'PY' "${approval_body}" "${TARGET_SHOP_DOMAIN}"
import json
import pathlib
import sys

data = json.loads(pathlib.Path(sys.argv[1]).read_text())
target_shop = sys.argv[2].lower()
assert data["status"] == "ACTIVE", data
assert str(data.get("shopDomain") or "").lower() == target_shop, data
print(data["assignmentId"])
PY
)"
    created_temp_access="true"
    echo "PASS: merchant approval created active partner assignment (${workflow_store_id})"

    merchant_workspace_approved_body="${TMP_DIR}/merchant-workspace-approved.json"
    merchant_workspace_approved_status="$(request GET "${BASE_URL}/api/merchant/partner-access/${TEMP_ACCESS_APPROVAL_CODE}/workspace" "${merchant_workspace_approved_body}")"
    cp "${merchant_workspace_approved_body}" "${TMP_DIR}/last-body"
    assert_status "${merchant_workspace_approved_status}" "200" "merchant launch workspace reachable after approval"
    python3 - <<'PY' "${merchant_workspace_approved_body}" "${workflow_store_id}" "${TARGET_SHOP_DOMAIN}"
import json
import pathlib
import sys

workspace = json.loads(pathlib.Path(sys.argv[1]).read_text())
assert workspace["accessRequest"]["status"] == "APPROVED", workspace
assert workspace.get("store", {}).get("id") == sys.argv[2], workspace
assert str(workspace.get("store", {}).get("shopDomain") or "").lower() == sys.argv[3].lower(), workspace
assert workspace.get("launchReadiness", {}).get("stagingReady") is True, workspace
assert "REVOKE_PARTNER_ACCESS" in workspace.get("availableActions", []), workspace
assert "REQUEST_ROLLBACK" in workspace.get("availableActions", []), workspace
print("PASS: merchant launch workspace exposes approved-store readiness and safe actions")
PY

    implementation_approved_body="${TMP_DIR}/implementation-detail-approved.json"
    implementation_approved_status="$(partner_request GET "${BASE_URL}/api/partners/client-implementations/${implementation_id}" "${implementation_approved_body}")"
    cp "${implementation_approved_body}" "${TMP_DIR}/last-body"
    assert_status "${implementation_approved_status}" "200" "approved implementation detail reachable"
    python3 - <<'PY' "${implementation_approved_body}" "${implementation_id}"
import json
import pathlib
import sys

data = json.loads(pathlib.Path(sys.argv[1]).read_text())
assert data["id"] == sys.argv[2], data
assert data["status"] == "APPROVED", data
print("PASS: partner implementation status reflects merchant approval")
PY
  elif [[ "${workflow_store_id}" == "NONE" ]]; then
    echo "SKIP: no active partner store assignment available for workflow checks."
    exit 0
  fi

  store_detail_body="${TMP_DIR}/store-detail.json"
  store_detail_status="$(partner_request GET "${BASE_URL}/api/partners/stores/${workflow_store_id}" "${store_detail_body}")"
  cp "${store_detail_body}" "${TMP_DIR}/last-body"
  assert_status "${store_detail_status}" "200" "active partner store detail reachable"
  workflow_shop_domain="$(python3 - <<'PY' "${store_detail_body}" "${REQUIRED_STORE_SURFACES}"
import json
import pathlib
import sys

store = json.loads(pathlib.Path(sys.argv[1]).read_text())
required_surfaces = {item.strip() for item in sys.argv[2].split(",") if item.strip()}
assert store["assignmentStatus"] == "ACTIVE", store
assert store["status"] == "READY", store
assert store.get("storeConnectionId"), store
assert store.get("installStatus") == "INSTALLED", store
assert store.get("widgetStatus") == "ENABLED", store
assert store.get("approvedAt"), store
assert "VERIFICATION_READ" in (store.get("permissions") or []), store
assert "products" in (store.get("enabledSourceCategories") or []), store
enabled_surfaces = set(store.get("enabledSurfaces") or [])
assert enabled_surfaces, store
assert required_surfaces.issubset(enabled_surfaces), store
raw = json.dumps(store).lower()
for forbidden in ("deployment", "provider", "secret", "vectorization", "runtime"):
    assert forbidden not in raw, store
print(store["shopDomain"])
PY
)"
  echo "PASS: selected active partner store is workflow-ready (${workflow_shop_domain})"

  product_controls_body="${TMP_DIR}/product-controls.json"
  product_controls_status="$(partner_request GET "${BASE_URL}/api/partners/stores/${workflow_store_id}/product-controls" "${product_controls_body}")"
  cp "${product_controls_body}" "${TMP_DIR}/last-body"
  assert_status "${product_controls_status}" "200" "partner product controls reachable"
  product_support_restore_payload="${TMP_DIR}/product-support-restore.json"
  product_support_update_payload="${TMP_DIR}/product-support-update.json"
  python3 - <<'PY' "${product_controls_body}" "${workflow_store_id}" "${workflow_shop_domain}" "${RUN_TAG}" "${product_support_restore_payload}" "${product_support_update_payload}" "${REQUIRED_STORE_SURFACES}"
import json
import pathlib
import sys

controls = json.loads(pathlib.Path(sys.argv[1]).read_text())
store_id = sys.argv[2]
shop = sys.argv[3].lower()
run_tag = sys.argv[4]
restore_path = pathlib.Path(sys.argv[5])
update_path = pathlib.Path(sys.argv[6])
required_surfaces = {item.strip() for item in sys.argv[7].split(",") if item.strip()}
assert controls["storeId"] == store_id, controls
assert str(controls.get("shopDomain") or "").lower() == shop, controls
assert controls["assignmentStatus"] == "ACTIVE", controls
assert controls["installStatus"] == "INSTALLED", controls
assert "PRODUCT_CONFIG_READ" in (controls.get("capabilities") or []), controls
assert "STOREFRONT_SURFACE_CONTROL" in (controls.get("capabilities") or []), controls
assert "KNOWLEDGE_SOURCE_CONTROL" in (controls.get("capabilities") or []), controls
assert "SUPPORT_MANAGE" in (controls.get("capabilities") or []), controls
enabled_surfaces = set(controls.get("enabledSurfaces") or [])
assert enabled_surfaces, controls
assert required_surfaces.issubset(enabled_surfaces), controls
assert controls.get("sourceSettings", {}).get("productsEnabled") is True, controls
raw = json.dumps(controls).lower()
for forbidden in ("secret", "token", "password", "credential", "runtimebaseurl", "deploymentstatus"):
    assert forbidden not in raw, controls
support = controls.get("supportProfile") or {}
restore = {
    "contactEmail": support.get("contactEmail"),
    "contactUrl": support.get("contactUrl"),
    "helpCenterUrl": support.get("helpCenterUrl"),
    "orderLookupPageUrl": support.get("orderLookupPageUrl"),
    "supportPolicyNote": support.get("supportPolicyNote"),
}
update = dict(restore)
update["contactEmail"] = update.get("contactEmail") or "release-gate@loom.test"
update["contactUrl"] = update.get("contactUrl") or "https://loom.test/release-gate"
update["helpCenterUrl"] = update.get("helpCenterUrl") or "https://loom.test/help"
update["supportPolicyNote"] = f"Release gate partner product-control proof {run_tag}."
restore_path.write_text(json.dumps(restore), encoding="utf-8")
update_path.write_text(json.dumps(update), encoding="utf-8")
print("PASS: partner product controls are scoped and secret-safe")
PY

  if [[ "${STRICT}" == "true" ]]; then
    trial_session_before_body="${TMP_DIR}/trial-session-before.json"
    trial_session_before_status="$(partner_request GET "${BASE_URL}/api/partners/session" "${trial_session_before_body}")"
    cp "${trial_session_before_body}" "${TMP_DIR}/last-body"
    assert_status "${trial_session_before_status}" "200" "partner session reachable before package-trial privilege proof"

    platform_members_body="${TMP_DIR}/platform-partner-members.json"
    platform_members_status="$(platform_request GET "${BASE_URL}/api/platform/partners/members" "${platform_members_body}")"
    cp "${platform_members_body}" "${TMP_DIR}/last-body"
    assert_status "${platform_members_status}" "200" "Platform partner member privilege list reachable"

    original_privileges_file="${TMP_DIR}/partner-trial-original-privileges.json"
    remove_privilege_payload="${TMP_DIR}/partner-trial-remove-privilege.json"
    add_privilege_payload="${TMP_DIR}/partner-trial-add-privilege.json"
    PARTNER_TRIAL_MEMBER_ID="$(python3 - <<'PY' "${trial_session_before_body}" "${platform_members_body}" "${PACKAGE_TRIAL_PRIVILEGE}" "${original_privileges_file}" "${remove_privilege_payload}" "${add_privilege_payload}"
import json
import pathlib
import sys

session = json.loads(pathlib.Path(sys.argv[1]).read_text())
members = json.loads(pathlib.Path(sys.argv[2]).read_text())
privilege = sys.argv[3]
original_path = pathlib.Path(sys.argv[4])
remove_path = pathlib.Path(sys.argv[5])
add_path = pathlib.Path(sys.argv[6])

member_id = ((session.get("member") or {}).get("id") or "").strip()
if not member_id:
    print("FAIL: partner session did not expose member id for trial privilege proof", file=sys.stderr)
    raise SystemExit(1)
matches = [member for member in members if member.get("id") == member_id]
if not matches:
    print(f"FAIL: Platform partner member list did not include current partner member {member_id}", file=sys.stderr)
    raise SystemExit(1)
member = matches[0]
original = [value for value in (member.get("privileges") or []) if isinstance(value, str)]
without = [value for value in original if value != privilege]
with_privilege = list(without)
if privilege not in with_privilege:
    with_privilege.append(privilege)
original_path.write_text(json.dumps(original), encoding="utf-8")
remove_path.write_text(json.dumps({"privileges": without}), encoding="utf-8")
add_path.write_text(json.dumps({"privileges": with_privilege}), encoding="utf-8")
print(member_id)
PY
)"
    PARTNER_TRIAL_MEMBER_ORIGINAL_PRIVILEGES="$(cat "${original_privileges_file}")"

    remove_privilege_body="${TMP_DIR}/partner-trial-remove-privilege-response.json"
    remove_privilege_status="$(platform_request PATCH "${BASE_URL}/api/platform/partners/members/${PARTNER_TRIAL_MEMBER_ID}" "${remove_privilege_body}" \
      -H "Content-Type: application/json" \
      --data "@${remove_privilege_payload}")"
    cp "${remove_privilege_body}" "${TMP_DIR}/last-body"
    assert_status "${remove_privilege_status}" "200" "Platform can remove partner package-trial privilege"
    PARTNER_TRIAL_MEMBER_PRIVILEGE_TOUCHED="true"

    trial_session_denied_body="${TMP_DIR}/trial-session-denied.json"
    trial_session_denied_status="$(partner_request GET "${BASE_URL}/api/partners/session" "${trial_session_denied_body}")"
    cp "${trial_session_denied_body}" "${TMP_DIR}/last-body"
    assert_status "${trial_session_denied_status}" "200" "partner session reachable after package-trial privilege removal"
    python3 - <<'PY' "${trial_session_denied_body}" "${PACKAGE_TRIAL_PRIVILEGE}"
import json
import pathlib
import sys

session = json.loads(pathlib.Path(sys.argv[1]).read_text())
privilege = sys.argv[2]
member = session.get("member") or {}
assert privilege not in (member.get("privileges") or []), session
assert privilege not in (member.get("effectivePermissions") or []), session
assert privilege not in (session.get("permissions") or []), session
print("PASS: partner session excludes package-trial privilege after Platform removal")
PY

    trial_controls_denied_body="${TMP_DIR}/trial-product-controls-denied.json"
    trial_controls_denied_status="$(partner_request GET "${BASE_URL}/api/partners/stores/${workflow_store_id}/product-controls" "${trial_controls_denied_body}")"
    cp "${trial_controls_denied_body}" "${TMP_DIR}/last-body"
    assert_status "${trial_controls_denied_status}" "200" "partner product controls reachable without package-trial privilege"
    python3 - <<'PY' "${trial_controls_denied_body}" "${PACKAGE_TRIAL_PRIVILEGE}"
import json
import pathlib
import sys

controls = json.loads(pathlib.Path(sys.argv[1]).read_text())
privilege = sys.argv[2]
assert privilege not in (controls.get("capabilities") or []), controls
assert isinstance(controls.get("packageTrialHistory"), list), controls
assert {"STARTER", "ELITE"}.issubset(set(controls.get("trialActivationTiers") or [])), controls
assert isinstance(controls.get("maxTrialDays"), int) and controls["maxTrialDays"] >= 1, controls
print("PASS: product controls expose trial schema but hide activation capability without Platform privilege")
PY

    trial_activation_denied_body="${TMP_DIR}/trial-activation-denied.json"
    trial_activation_denied_status="$(partner_request POST "${BASE_URL}/api/partners/stores/${workflow_store_id}/package-trials" "${trial_activation_denied_body}" \
      -H "Content-Type: application/json" \
      -d "{\"tierKey\":\"STARTER\",\"trialDays\":1,\"reason\":\"Release gate negative package-trial privilege proof ${RUN_TAG}\"}")"
    cp "${trial_activation_denied_body}" "${TMP_DIR}/last-body"
    assert_status "${trial_activation_denied_status}" "403" "partner package-trial activation is denied without Platform privilege"

    add_privilege_body="${TMP_DIR}/partner-trial-add-privilege-response.json"
    add_privilege_status="$(platform_request PATCH "${BASE_URL}/api/platform/partners/members/${PARTNER_TRIAL_MEMBER_ID}" "${add_privilege_body}" \
      -H "Content-Type: application/json" \
      --data "@${add_privilege_payload}")"
    cp "${add_privilege_body}" "${TMP_DIR}/last-body"
    assert_status "${add_privilege_status}" "200" "Platform can grant partner package-trial privilege"

    trial_session_allowed_body="${TMP_DIR}/trial-session-allowed.json"
    trial_session_allowed_status="$(partner_request GET "${BASE_URL}/api/partners/session" "${trial_session_allowed_body}")"
    cp "${trial_session_allowed_body}" "${TMP_DIR}/last-body"
    assert_status "${trial_session_allowed_status}" "200" "partner session reachable after package-trial privilege grant"
    python3 - <<'PY' "${trial_session_allowed_body}" "${PACKAGE_TRIAL_PRIVILEGE}"
import json
import pathlib
import sys

session = json.loads(pathlib.Path(sys.argv[1]).read_text())
privilege = sys.argv[2]
member = session.get("member") or {}
assert privilege in (member.get("privileges") or []), session
assert privilege in (member.get("effectivePermissions") or []), session
assert privilege in (session.get("permissions") or []), session
print("PASS: partner session reflects Platform-granted package-trial privilege")
PY

    trial_controls_allowed_body="${TMP_DIR}/trial-product-controls-allowed.json"
    trial_controls_allowed_status="$(partner_request GET "${BASE_URL}/api/partners/stores/${workflow_store_id}/product-controls" "${trial_controls_allowed_body}")"
    cp "${trial_controls_allowed_body}" "${TMP_DIR}/last-body"
    assert_status "${trial_controls_allowed_status}" "200" "partner product controls expose granted package-trial capability"
    python3 - <<'PY' "${trial_controls_allowed_body}" "${PACKAGE_TRIAL_PRIVILEGE}"
import json
import pathlib
import sys

controls = json.loads(pathlib.Path(sys.argv[1]).read_text())
privilege = sys.argv[2]
assert privilege in (controls.get("capabilities") or []), controls
assert isinstance(controls.get("packageTrialHistory"), list), controls
assert {"STARTER", "ELITE"}.issubset(set(controls.get("trialActivationTiers") or [])), controls
assert isinstance(controls.get("maxTrialDays"), int) and controls["maxTrialDays"] >= 1, controls
print("PASS: product controls expose package-trial capability and tracking fields after Platform grant")
PY

    restore_privilege_payload="${TMP_DIR}/partner-trial-restore-privilege.json"
    python3 - <<'PY' "${PARTNER_TRIAL_MEMBER_ORIGINAL_PRIVILEGES}" "${restore_privilege_payload}"
import json
import pathlib
import sys

pathlib.Path(sys.argv[2]).write_text(json.dumps({"privileges": json.loads(sys.argv[1])}), encoding="utf-8")
PY
    restore_privilege_body="${TMP_DIR}/partner-trial-restore-privilege-response.json"
    restore_privilege_status="$(platform_request PATCH "${BASE_URL}/api/platform/partners/members/${PARTNER_TRIAL_MEMBER_ID}" "${restore_privilege_body}" \
      -H "Content-Type: application/json" \
      --data "@${restore_privilege_payload}")"
    cp "${restore_privilege_body}" "${TMP_DIR}/last-body"
    assert_status "${restore_privilege_status}" "200" "Platform restores original partner package-trial privileges"
    PARTNER_TRIAL_MEMBER_PRIVILEGE_TOUCHED="false"
  fi

  max_widget_auth_body="${TMP_DIR}/max-widget-auth-context.json"
  max_widget_auth_status="$(partner_request GET "${BASE_URL}/api/partners/stores/${workflow_store_id}/max-widget/chat/me/auth-context?authPath=PLATFORM_PRIVATE" "${max_widget_auth_body}")"
  cp "${max_widget_auth_body}" "${TMP_DIR}/last-body"
  assert_status "${max_widget_auth_status}" "200" "partner Max widget auth context reachable"
  python3 - <<'PY' "${max_widget_auth_body}"
import json
import pathlib
import sys

context = json.loads(pathlib.Path(sys.argv[1]).read_text())
assert context.get("authMode") == "PLATFORM_PROXY_SESSION", context
assert context.get("callerType") == "PLATFORM_PROXY", context
assert context.get("deploymentId"), context
raw = json.dumps(context).lower()
for forbidden in ("secret", "token", "password", "credential"):
    assert forbidden not in raw, context
print("PASS: partner Max widget auth context is live and secret-safe")
PY

  max_widget_query_body="${TMP_DIR}/max-widget-query.json"
  max_widget_query_status="$(partner_request POST "${BASE_URL}/api/partners/stores/${workflow_store_id}/max-widget/chat/me/query?authPath=PLATFORM_PRIVATE" "${max_widget_query_body}" \
    -H "Content-Type: application/json" \
    -d "{\"query\":\"Run a release-gate live Max widget smoke test for ${workflow_shop_domain}. Confirm product discovery and storefront-safe boundaries.\"}")"
  cp "${max_widget_query_body}" "${TMP_DIR}/last-body"
  assert_status "${max_widget_query_status}" "200" "partner Max widget smoke query reachable"
  python3 - <<'PY' "${max_widget_query_body}"
import json
import pathlib
import sys

response = json.loads(pathlib.Path(sys.argv[1]).read_text())
assert response.get("success", True) is not False, response
message = response.get("message")
result = response.get("result") if isinstance(response.get("result"), dict) else {}
sanitized = result.get("sanitizedPayload") if isinstance(result.get("sanitizedPayload"), dict) else {}
answer = (
    response.get("answer")
    or response.get("safeSummary")
    or response.get("response")
    or response.get("text")
    or response.get("content")
    or result.get("answer")
    or result.get("response")
    or result.get("text")
    or result.get("content")
    or result.get("message")
    or sanitized.get("safeSummary")
    or sanitized.get("message")
    or message
)
if not isinstance(answer, str) or not answer.strip():
    print(f"FAIL: Max widget smoke query returned no displayable answer: {response}", file=sys.stderr)
    raise SystemExit(1)
raw = json.dumps(response).lower()
for forbidden in ("secret", "token", "password", "credential"):
    assert forbidden not in raw, response
print("PASS: partner Max widget smoke query returned a live answer")
PY

  product_support_update_body="${TMP_DIR}/product-support-update-response.json"
  product_support_update_status="$(partner_request POST "${BASE_URL}/api/partners/stores/${workflow_store_id}/product-controls/support-profile" "${product_support_update_body}" \
    -H "Content-Type: application/json" \
    --data "@${product_support_update_payload}")"
  cp "${product_support_update_body}" "${TMP_DIR}/last-body"
  assert_status "${product_support_update_status}" "200" "partner product support profile update persisted"
  python3 - <<'PY' "${product_support_update_body}" "${RUN_TAG}"
import json
import pathlib
import sys

controls = json.loads(pathlib.Path(sys.argv[1]).read_text())
profile = controls.get("supportProfile") or {}
assert profile.get("merchantHandoffConfigured") is True, controls
assert profile.get("supportPolicyNote") == f"Release gate partner product-control proof {sys.argv[2]}.", profile
print("PASS: partner product support profile write returned canonical state")
PY

  if [[ "${STRICT}" == "true" ]]; then
    encoded_product_shop="$(urlencode "${workflow_shop_domain}")"
    product_admin_support_body="${TMP_DIR}/product-admin-support-profile.json"
    product_admin_support_status="$(platform_request GET "${BASE_URL}/api/shopify/stores/${encoded_product_shop}/support-profile" "${product_admin_support_body}")"
    cp "${product_admin_support_body}" "${TMP_DIR}/last-body"
    assert_status "${product_admin_support_status}" "200" "platform support profile sees partner product-control write"
    python3 - <<'PY' "${product_admin_support_body}" "${RUN_TAG}"
import json
import pathlib
import sys

profile = json.loads(pathlib.Path(sys.argv[1]).read_text())
assert profile.get("supportPolicyNote") == f"Release gate partner product-control proof {sys.argv[2]}.", profile
assert profile.get("merchantHandoffConfigured") is True, profile
print("PASS: partner write landed in canonical Shopify support profile")
PY
  fi

  product_support_restore_body="${TMP_DIR}/product-support-restore-response.json"
  product_support_restore_status="$(partner_request POST "${BASE_URL}/api/partners/stores/${workflow_store_id}/product-controls/support-profile" "${product_support_restore_body}" \
    -H "Content-Type: application/json" \
    --data "@${product_support_restore_payload}")"
  cp "${product_support_restore_body}" "${TMP_DIR}/last-body"
  assert_status "${product_support_restore_status}" "200" "partner product support profile restored"
  echo "PASS: partner product-control proof state restored"

  product_activity_body="${TMP_DIR}/product-activity.json"
  product_activity_status="$(partner_request GET "${BASE_URL}/api/partners/activity" "${product_activity_body}")"
  cp "${product_activity_body}" "${TMP_DIR}/last-body"
  assert_status "${product_activity_status}" "200" "partner product-control activity visible"
  python3 - <<'PY' "${product_activity_body}"
import json
import pathlib
import sys

activity = json.loads(pathlib.Path(sys.argv[1]).read_text())
actions = {item.get("action") for item in activity}
assert "PRODUCT_SUPPORT_PROFILE_UPDATED" in actions, activity
assert "PARTNER_MAX_WIDGET_QUERY_RAN" in actions, activity
raw = json.dumps(activity).lower()
for forbidden in ("secret", "token", "password", "credential"):
    assert forbidden not in raw, activity
print("PASS: product-control and Max widget audit actions are partner-visible and secret-safe")
PY

  implementations_body="${TMP_DIR}/implementations.json"
  implementations_status="$(partner_request GET "${BASE_URL}/api/partners/client-implementations" "${implementations_body}")"
  cp "${implementations_body}" "${TMP_DIR}/last-body"
  assert_status "${implementations_status}" "200" "partner implementation statuses reachable"
  python3 - <<'PY' "${implementations_body}" "${workflow_shop_domain}"
import json
import pathlib
import sys

items = json.loads(pathlib.Path(sys.argv[1]).read_text())
shop = sys.argv[2].lower()
matching = [item for item in items if str(item.get("shopDomain") or "").lower() == shop]
assert matching, items
assert any(item.get("status") == "APPROVED" for item in matching), matching
print("PASS: partner implementation list exposes approved request status")
PY

  packs_body="${TMP_DIR}/verification-packs.json"
  packs_status="$(partner_request GET "${BASE_URL}/api/partners/verification-packs" "${packs_body}")"
  cp "${packs_body}" "${TMP_DIR}/last-body"
  assert_status "${packs_status}" "200" "verification packs reachable"
  python3 - <<'PY' "${packs_body}"
import json
import pathlib
import sys

packs = json.loads(pathlib.Path(sys.argv[1]).read_text())
starter = [pack for pack in packs if pack.get("id") == "starter-launch-readiness"]
elite = [pack for pack in packs if pack.get("id") == "shopify-companion-elite-readiness"]
assert starter, packs
assert len(starter[0].get("steps") or []) >= 7, starter[0]
assert elite, packs
assert len(elite[0].get("steps") or []) >= 8, elite[0]
print("PASS: Starter and Elite verification packs are available")
PY

  verification_pack_id="$(python3 - <<'PY' "${store_detail_body}" "${packs_body}"
import json
import pathlib
import sys

store = json.loads(pathlib.Path(sys.argv[1]).read_text())
packs = {pack.get("id") for pack in json.loads(pathlib.Path(sys.argv[2]).read_text())}
profile = store.get("packageProfile") or {}
configured = (profile.get("verificationPackId") or "").strip()
surfaces = set(store.get("enabledSurfaces") or [])
elite_surfaces = {
    "order-lookup",
    "governed-add-to-cart",
    "cart-update",
    "checkout-completion",
    "refund-initiation",
}
if configured and configured in packs:
    print(configured)
elif surfaces.intersection(elite_surfaces) and "shopify-companion-elite-readiness" in packs:
    print("shopify-companion-elite-readiness")
else:
    print("starter-launch-readiness")
PY
)"
  echo "PASS: selected package-aware verification pack (${verification_pack_id})"

  store_pack_body="${TMP_DIR}/store-verification-pack.json"
  store_pack_status="$(partner_request GET "${BASE_URL}/api/partners/stores/${workflow_store_id}/verification-pack" "${store_pack_body}")"
  cp "${store_pack_body}" "${TMP_DIR}/last-body"
  assert_status "${store_pack_status}" "200" "store verification pack reachable"
  python3 - <<'PY' "${store_pack_body}" "${verification_pack_id}"
import json
import pathlib
import sys

pack = json.loads(pathlib.Path(sys.argv[1]).read_text())
assert pack["id"] == sys.argv[2], pack
statuses = {step.get("status") for step in pack.get("steps") or []}
assert "PASSED" in statuses, statuses
assert "FAILED" not in statuses, pack
print("PASS: store verification pack evaluates live package state")
PY

  verification_run_body="${TMP_DIR}/verification-run-create.json"
  verification_run_status="$(partner_request POST "${BASE_URL}/api/partners/stores/${workflow_store_id}/verification-runs" "${verification_run_body}" \
    -H "Content-Type: application/json" \
    -d "{\"packId\":\"${verification_pack_id}\"}")"
  cp "${verification_run_body}" "${TMP_DIR}/last-body"
  assert_status "${verification_run_status}" "201" "store verification run created"
  run_ref="$(python3 - <<'PY' "${verification_run_body}" "${verification_pack_id}"
import json
import pathlib
import sys

run = json.loads(pathlib.Path(sys.argv[1]).read_text())
pack_id = sys.argv[2]
assert run["packId"] == pack_id, run
if run["status"] != "PASSED":
    print(f"FAIL: expected {pack_id} to pass, got {run['status']}: {run}", file=sys.stderr)
    raise SystemExit(1)
assert run.get("evidenceBundleId"), run
assert run["totalSteps"] >= 7, run
assert run["failedSteps"] == 0, run
assert run["blockedSteps"] == 0, run
assert all(step.get("status") == "PASSED" for step in run.get("steps") or []), run
summary = run.get("summary") or {}
assert summary.get("redaction") == "merchant-safe", summary
print(run["id"] + "|" + run["evidenceBundleId"])
PY
)"
  IFS='|' read -r verification_run_id evidence_bundle_id <<< "${run_ref}"
  echo "PASS: package-aware verification run persisted and passed (${verification_run_id})"

  verification_detail_body="${TMP_DIR}/verification-run-detail.json"
  verification_detail_status="$(partner_request GET "${BASE_URL}/api/partners/verification-runs/${verification_run_id}" "${verification_detail_body}")"
  cp "${verification_detail_body}" "${TMP_DIR}/last-body"
  assert_status "${verification_detail_status}" "200" "verification run detail reachable"
  python3 - <<'PY' "${verification_detail_body}" "${verification_run_id}" "${evidence_bundle_id}"
import json
import pathlib
import sys

run = json.loads(pathlib.Path(sys.argv[1]).read_text())
assert run["id"] == sys.argv[2], run
assert run["evidenceBundleId"] == sys.argv[3], run
assert run["summary"]["redaction"] == "merchant-safe", run
print("PASS: verification run detail preserves merchant-safe summary")
PY

  store_runs_body="${TMP_DIR}/store-verification-runs.json"
  store_runs_status="$(partner_request GET "${BASE_URL}/api/partners/stores/${workflow_store_id}/verification-runs" "${store_runs_body}")"
  cp "${store_runs_body}" "${TMP_DIR}/last-body"
  assert_status "${store_runs_status}" "200" "store verification run history reachable"
  python3 - <<'PY' "${store_runs_body}" "${verification_run_id}"
import json
import pathlib
import sys

runs = json.loads(pathlib.Path(sys.argv[1]).read_text())
assert any(run.get("id") == sys.argv[2] for run in runs), runs
print("PASS: store verification run history includes new run")
PY

  manual_step_body="${TMP_DIR}/manual-step.json"
  manual_step_status="$(partner_request POST "${BASE_URL}/api/partners/stores/${workflow_store_id}/verification-steps/release-gate-manual-proof/complete" "${manual_step_body}" \
    -H "Content-Type: application/json" \
    -d "{\"runId\":\"${verification_run_id}\",\"status\":\"PASSED\",\"evidenceNote\":\"Release gate manual evidence proof ${RUN_TAG}.\",\"partnerSafeMessage\":\"Release gate manual proof captured.\"}")"
  cp "${manual_step_body}" "${TMP_DIR}/last-body"
  assert_status "${manual_step_status}" "200" "manual verification step persisted"
  python3 - <<'PY' "${manual_step_body}"
import json
import pathlib
import sys

run = json.loads(pathlib.Path(sys.argv[1]).read_text())
steps = run.get("steps") or []
matching = [step for step in steps if step.get("stepId") == "release-gate-manual-proof"]
assert matching, run
assert matching[0]["status"] == "PASSED", matching[0]
assert matching[0].get("evidence"), matching[0]
print("PASS: manual verification step evidence is persisted")
PY

  evidence_body="${TMP_DIR}/evidence-bundle.json"
  evidence_status="$(partner_request GET "${BASE_URL}/api/partners/evidence-bundles/${evidence_bundle_id}" "${evidence_body}")"
  cp "${evidence_body}" "${TMP_DIR}/last-body"
  assert_status "${evidence_status}" "200" "verification evidence bundle reachable"
  python3 - <<'PY' "${evidence_body}" "${verification_run_id}"
import json
import pathlib
import sys

bundle = json.loads(pathlib.Path(sys.argv[1]).read_text())
assert bundle["verificationRunId"] == sys.argv[2], bundle
assert bundle["status"] == "READY", bundle
assert bundle["summary"]["redaction"] == "merchant-safe", bundle
attachments = set(bundle.get("attachments") or [])
assert {"manifest.json", "verification-run.json", "merchant-safe-summary.md"}.issubset(attachments), attachments
print("PASS: verification evidence bundle is merchant-safe")
PY

  evidence_zip="${TMP_DIR}/evidence-bundle.zip"
  evidence_export_status="$(partner_request GET "${BASE_URL}/api/partners/evidence-bundles/${evidence_bundle_id}/export" "${evidence_zip}")"
  cp "${evidence_zip}" "${TMP_DIR}/last-body"
  assert_status "${evidence_export_status}" "200" "evidence bundle export reachable"
  python3 - <<'PY' "${evidence_zip}" "${evidence_bundle_id}"
import json
import pathlib
import sys
import zipfile

zip_path = pathlib.Path(sys.argv[1])
bundle_id = sys.argv[2]
with zipfile.ZipFile(zip_path) as archive:
    names = set(archive.namelist())
    required = {"manifest.json", "summary.json", "attachments.json", "merchant-safe-summary.md"}
    assert required.issubset(names), names
    manifest = json.loads(archive.read("manifest.json"))
    assert manifest["bundleId"] == bundle_id, manifest
    assert manifest["redaction"] == "merchant-safe", manifest
    summary = json.loads(archive.read("summary.json"))
    assert summary["redaction"] == "merchant-safe", summary
print("PASS: evidence export ZIP contains required merchant-safe files")
PY

  launch_bundle_body="${TMP_DIR}/launch-evidence-bundle.json"
  launch_bundle_status="$(partner_request POST "${BASE_URL}/api/partners/stores/${workflow_store_id}/evidence-bundles" "${launch_bundle_body}" \
    -H "Content-Type: application/json" \
    -d '{"bundleKind":"LAUNCH_PACKET"}')"
  cp "${launch_bundle_body}" "${TMP_DIR}/last-body"
  assert_status "${launch_bundle_status}" "201" "launch evidence bundle created"
  launch_bundle_id="$(python3 - <<'PY' "${launch_bundle_body}"
import json
import pathlib
import sys

bundle = json.loads(pathlib.Path(sys.argv[1]).read_text())
assert bundle["bundleKind"] == "LAUNCH_PACKET", bundle
assert bundle["status"] == "READY", bundle
assert bundle["summary"]["redaction"] == "merchant-safe", bundle
print(bundle["id"])
PY
)"
  echo "PASS: launch evidence bundle persisted (${launch_bundle_id})"

  launch_readiness_body="${TMP_DIR}/launch-readiness.json"
  launch_readiness_status="$(partner_request GET "${BASE_URL}/api/partners/stores/${workflow_store_id}/launch-readiness" "${launch_readiness_body}")"
  cp "${launch_readiness_body}" "${TMP_DIR}/last-body"
  assert_status "${launch_readiness_status}" "200" "partner launch readiness reachable"
  production_promotion_ready="$(python3 - <<'PY' "${launch_readiness_body}" "${workflow_store_id}" "${workflow_shop_domain}"
import json
import pathlib
import sys

readiness = json.loads(pathlib.Path(sys.argv[1]).read_text())
store_id = sys.argv[2]
shop = sys.argv[3].lower()
assert readiness["storeId"] == store_id, readiness
assert str(readiness.get("shopDomain") or "").lower() == shop, readiness
assert readiness.get("stagingReady") is True, readiness
assert readiness.get("latestVerificationStatus") == "PASSED", readiness
assert readiness.get("evidenceReady") is True, readiness
assert readiness.get("latestEvidenceStatus") == "READY", readiness
raw = json.dumps(readiness).lower()
for forbidden in ("secret", "token", "password", "credential", "provider_resource"):
    assert forbidden not in raw, readiness
print("true" if readiness.get("productionPromotionReady") is True else "false")
PY
  )"
  echo "PASS: launch readiness reports staging proof and merchant-safe evidence"

  if [[ -n "${TEMP_ACCESS_APPROVAL_CODE}" ]]; then
    merchant_workspace_release_body="${TMP_DIR}/merchant-workspace-release.json"
    merchant_workspace_release_status="$(request GET "${BASE_URL}/api/merchant/partner-access/${TEMP_ACCESS_APPROVAL_CODE}/workspace" "${merchant_workspace_release_body}")"
    cp "${merchant_workspace_release_body}" "${TMP_DIR}/last-body"
    assert_status "${merchant_workspace_release_status}" "200" "merchant launch workspace shows release evidence"
    python3 - <<'PY' "${merchant_workspace_release_body}" "${workflow_store_id}" "${workflow_shop_domain}" "${launch_bundle_id}"
import json
import pathlib
import sys

workspace = json.loads(pathlib.Path(sys.argv[1]).read_text())
store_id = sys.argv[2]
shop = sys.argv[3].lower()
bundle_id = sys.argv[4]
assert workspace.get("store", {}).get("id") == store_id, workspace
assert str(workspace.get("store", {}).get("shopDomain") or "").lower() == shop, workspace
readiness = workspace.get("launchReadiness") or {}
assert readiness.get("stagingReady") is True, workspace
assert readiness.get("latestVerificationStatus") == "PASSED", workspace
assert readiness.get("evidenceReady") is True, workspace
assert any(bundle.get("id") == bundle_id for bundle in workspace.get("evidenceBundles") or []), workspace
raw = json.dumps(workspace).lower()
for forbidden in ("secret", "token", "password", "credential", "provider_resource", "coolify"):
    assert forbidden not in raw, workspace
print("PASS: merchant launch workspace exposes merchant-safe evidence and no provider internals")
PY
  fi

  if [[ "${PRODUCTION_PROMOTION_PROOF}" == "true" ]]; then
    if [[ "${production_promotion_ready}" != "true" ]]; then
      echo "FAIL: PARTNER_LIVE_PRODUCTION_PROMOTION_PROOF=true but launch readiness is not production-ready." >&2
      exit 27
    fi
    production_promotion_body="${TMP_DIR}/production-promotion.json"
    if [[ -n "${TEMP_ACCESS_APPROVAL_CODE}" ]]; then
      production_promotion_status="$(request POST "${BASE_URL}/api/merchant/partner-access/${TEMP_ACCESS_APPROVAL_CODE}/production-promotions" "${production_promotion_body}")"
    else
      production_promotion_status="$(partner_request POST "${BASE_URL}/api/partners/stores/${workflow_store_id}/production-promotions" "${production_promotion_body}")"
    fi
    cp "${production_promotion_body}" "${TMP_DIR}/last-body"
    assert_status "${production_promotion_status}" "200" "production promotion requested"
    python3 - <<'PY' "${production_promotion_body}" "${workflow_store_id}" "${workflow_shop_domain}"
import json
import pathlib
import sys

promotion = json.loads(pathlib.Path(sys.argv[1]).read_text())
store_id = sys.argv[2]
shop = sys.argv[3].lower()
assert promotion["storeId"] == store_id, promotion
assert str(promotion.get("shopDomain") or "").lower() == shop, promotion
assert promotion["status"] == "REQUESTED", promotion
assert promotion.get("latestReleaseStatus"), promotion
raw = json.dumps(promotion).lower()
for forbidden in ("secret", "token", "password", "credential", "provider_resource"):
    assert forbidden not in raw, promotion
print("PASS: production promotion response is merchant-safe")
PY
  else
    echo "SKIP: production promotion mutation not run; set PARTNER_LIVE_PRODUCTION_PROMOTION_PROOF=true for an intentional Go production proof."
  fi

  if [[ -n "${TEMP_ACCESS_APPROVAL_CODE}" ]]; then
    merchant_rollback_body="${TMP_DIR}/merchant-rollback-request.json"
    merchant_rollback_status="$(request POST "${BASE_URL}/api/merchant/partner-access/${TEMP_ACCESS_APPROVAL_CODE}/rollback-requests" "${merchant_rollback_body}" \
      -H "Content-Type: application/json" \
      -d "{\"requesterName\":\"Release Gate Merchant\",\"requesterEmail\":\"release-gate-merchant@loom.test\",\"reason\":\"Release gate proof that merchant rollback/deactivation requests are recorded without direct production mutation ${RUN_TAG}.\"}")"
    cp "${merchant_rollback_body}" "${TMP_DIR}/last-body"
    assert_status "${merchant_rollback_status}" "200" "merchant rollback/deactivation request recorded"
    python3 - <<'PY' "${merchant_rollback_body}" "${workflow_store_id}" "${workflow_shop_domain}"
import json
import pathlib
import sys

request = json.loads(pathlib.Path(sys.argv[1]).read_text())
assert request["storeId"] == sys.argv[2], request
assert str(request.get("shopDomain") or "").lower() == sys.argv[3].lower(), request
assert request["status"] == "REQUESTED", request
assert request.get("escalationId"), request
raw = json.dumps(request).lower()
for forbidden in ("secret", "token", "password", "credential", "provider_resource", "coolify"):
    assert forbidden not in raw, request
print("PASS: merchant rollback/deactivation request is merchant-safe and recorded")
PY
  fi

  evidence_list_body="${TMP_DIR}/evidence-bundles-list.json"
  evidence_list_status="$(partner_request GET "${BASE_URL}/api/partners/stores/${workflow_store_id}/evidence-bundles" "${evidence_list_body}")"
  cp "${evidence_list_body}" "${TMP_DIR}/last-body"
  assert_status "${evidence_list_status}" "200" "store evidence bundle list reachable"
  python3 - <<'PY' "${evidence_list_body}" "${launch_bundle_id}"
import json
import pathlib
import sys

bundles = json.loads(pathlib.Path(sys.argv[1]).read_text())
assert any(bundle.get("id") == sys.argv[2] for bundle in bundles), bundles
print("PASS: store evidence bundle list includes launch bundle")
PY

  templates_body="${TMP_DIR}/templates.json"
  templates_status="$(partner_request GET "${BASE_URL}/api/partners/templates" "${templates_body}")"
  cp "${templates_body}" "${TMP_DIR}/last-body"
  assert_status "${templates_status}" "200" "partner templates reachable"
  python3 - <<'PY' "${templates_body}"
import json
import pathlib
import sys

templates = json.loads(pathlib.Path(sys.argv[1]).read_text())
assert any(template.get("id") == "fashion-apparel-starter" for template in templates), templates
print("PASS: partner templates include fashion/apparel starter playbook")
PY

  template_detail_body="${TMP_DIR}/template-detail.json"
  template_detail_status="$(partner_request GET "${BASE_URL}/api/partners/templates/fashion-apparel-starter" "${template_detail_body}")"
  cp "${template_detail_body}" "${TMP_DIR}/last-body"
  assert_status "${template_detail_status}" "200" "partner template detail reachable"

  template_application_body="${TMP_DIR}/template-application.json"
  template_application_status="$(partner_request POST "${BASE_URL}/api/partners/templates/fashion-apparel-starter/applications" "${template_application_body}" \
    -H "Content-Type: application/json" \
    -d "{\"storeId\":\"${workflow_store_id}\"}")"
  cp "${template_application_body}" "${TMP_DIR}/last-body"
  assert_status "${template_application_status}" "201" "partner template application persisted"
  template_application_id="$(python3 - <<'PY' "${template_application_body}" "${workflow_store_id}"
import json
import pathlib
import sys

application = json.loads(pathlib.Path(sys.argv[1]).read_text())
assert application["templateId"] == "fashion-apparel-starter", application
assert application["storeAssignmentId"] == sys.argv[2], application
assert application["status"] == "ACTIVE", application
print(application["id"])
PY
)"

  template_application_reuse_body="${TMP_DIR}/template-application-reuse.json"
  template_application_reuse_status="$(partner_request POST "${BASE_URL}/api/partners/templates/fashion-apparel-starter/applications" "${template_application_reuse_body}" \
    -H "Content-Type: application/json" \
    -d "{\"storeId\":\"${workflow_store_id}\"}")"
  cp "${template_application_reuse_body}" "${TMP_DIR}/last-body"
  assert_status "${template_application_reuse_status}" "201" "partner template application is idempotent"
  python3 - <<'PY' "${template_application_reuse_body}" "${template_application_id}"
import json
import pathlib
import sys

application = json.loads(pathlib.Path(sys.argv[1]).read_text())
assert application["id"] == sys.argv[2], application
assert application["status"] == "ACTIVE", application
print("PASS: repeated template application reuses persisted active playbook")
PY

  template_applications_body="${TMP_DIR}/template-applications.json"
  template_applications_status="$(partner_request GET "${BASE_URL}/api/partners/template-applications" "${template_applications_body}")"
  cp "${template_applications_body}" "${TMP_DIR}/last-body"
  assert_status "${template_applications_status}" "200" "partner template applications reachable"
  python3 - <<'PY' "${template_applications_body}" "${template_application_id}"
import json
import pathlib
import sys

applications = json.loads(pathlib.Path(sys.argv[1]).read_text())
assert any(application.get("id") == sys.argv[2] for application in applications), applications
matching = [application for application in applications if application.get("id") == sys.argv[2]]
assert len(matching) == 1, applications
print("PASS: partner template application list includes persisted application")
PY

  note_body="${TMP_DIR}/store-note.json"
  note_status="$(partner_request POST "${BASE_URL}/api/partners/stores/${workflow_store_id}/notes" "${note_body}" \
    -H "Content-Type: application/json" \
    -d "{\"bodyMarkdown\":\"Release gate store note proof ${RUN_TAG}.\"}")"
  cp "${note_body}" "${TMP_DIR}/last-body"
  assert_status "${note_status}" "201" "store note persisted"
  note_id="$(python3 - <<'PY' "${note_body}"
import json
import pathlib
import sys

note = json.loads(pathlib.Path(sys.argv[1]).read_text())
assert note["status"] == "ACTIVE", note
assert "Release gate store note proof" in note["bodyMarkdown"], note
print(note["id"])
PY
)"

  notes_body="${TMP_DIR}/store-notes.json"
  notes_status="$(partner_request GET "${BASE_URL}/api/partners/stores/${workflow_store_id}/notes" "${notes_body}")"
  cp "${notes_body}" "${TMP_DIR}/last-body"
  assert_status "${notes_status}" "200" "store notes reachable"
  python3 - <<'PY' "${notes_body}" "${note_id}"
import json
import pathlib
import sys

notes = json.loads(pathlib.Path(sys.argv[1]).read_text())
assert any(note.get("id") == sys.argv[2] for note in notes), notes
print("PASS: store note list includes persisted note")
PY

  members_body="${TMP_DIR}/members.json"
  members_status="$(partner_request GET "${BASE_URL}/api/partners/members" "${members_body}")"
  cp "${members_body}" "${TMP_DIR}/last-body"
  assert_status "${members_status}" "200" "partner members reachable"
  python3 - <<'PY' "${members_body}"
import json
import pathlib
import sys

members = json.loads(pathlib.Path(sys.argv[1]).read_text())
assert members, members
assert any(member.get("role") == "PARTNER_ADMIN" and member.get("status") == "ACTIVE" for member in members), members
print("PASS: partner members include an active admin")
PY

  profile_body="${TMP_DIR}/profile.json"
  profile_status="$(partner_request PATCH "${BASE_URL}/api/partners/profile" "${profile_body}" \
    -H "Content-Type: application/json" \
    -d "{\"displayName\":\"Release Gate Partner Admin\"}")"
  cp "${profile_body}" "${TMP_DIR}/last-body"
  assert_status "${profile_status}" "200" "partner profile update persisted"
  python3 - <<'PY' "${profile_body}"
import json
import pathlib
import sys

profile = json.loads(pathlib.Path(sys.argv[1]).read_text())
assert profile["displayName"] == "Release Gate Partner Admin", profile
print("PASS: partner profile update returned persisted member state")
PY

  escalation_body="${TMP_DIR}/escalation.json"
  escalation_status="$(partner_request POST "${BASE_URL}/api/partners/stores/${workflow_store_id}/escalations" "${escalation_body}" \
    -H "Content-Type: application/json" \
    -d "{\"title\":\"Release gate support proof ${RUN_TAG}\",\"severity\":\"LOW\",\"description\":\"Automated release gate proof that support escalation persists and links merchant-safe evidence.\",\"reproductionSteps\":\"Run Partner Enablement release gate.\",\"expectedBehavior\":\"Escalation persists with evidence bundle link.\",\"actualBehavior\":\"Escalation persisted during release gate.\",\"impact\":\"Release gate evidence only.\",\"nextAction\":\"No production action; evidence proof.\",\"evidenceBundleIds\":[\"${launch_bundle_id}\"]}")"
  cp "${escalation_body}" "${TMP_DIR}/last-body"
  assert_status "${escalation_status}" "201" "support escalation with evidence persisted"
  escalation_id="$(python3 - <<'PY' "${escalation_body}" "${launch_bundle_id}"
import json
import pathlib
import sys

escalation = json.loads(pathlib.Path(sys.argv[1]).read_text())
assert escalation["status"] == "OPEN", escalation
assert sys.argv[2] in (escalation.get("evidenceBundleIds") or []), escalation
print(escalation["id"])
PY
)"

  reply_body="${TMP_DIR}/support-reply.json"
  reply_status="$(partner_request POST "${BASE_URL}/api/partners/escalations/${escalation_id}/replies" "${reply_body}" \
    -H "Content-Type: application/json" \
    -d "{\"bodyMarkdown\":\"Release gate partner-visible reply ${RUN_TAG}.\",\"evidenceBundleIds\":[\"${launch_bundle_id}\"]}")"
  cp "${reply_body}" "${TMP_DIR}/last-body"
  assert_status "${reply_status}" "201" "support reply with evidence persisted"
  python3 - <<'PY' "${reply_body}" "${launch_bundle_id}"
import json
import pathlib
import sys

reply = json.loads(pathlib.Path(sys.argv[1]).read_text())
assert reply["visibility"] == "PARTNER_VISIBLE", reply
assert sys.argv[2] in (reply.get("attachments") or []), reply
print("PASS: support reply persisted as partner-visible with evidence")
PY

  thread_body="${TMP_DIR}/support-thread.json"
  thread_status="$(partner_request GET "${BASE_URL}/api/partners/escalations/${escalation_id}/thread" "${thread_body}")"
  cp "${thread_body}" "${TMP_DIR}/last-body"
  assert_status "${thread_status}" "200" "support thread reachable"
  python3 - <<'PY' "${thread_body}" "${escalation_id}" "${launch_bundle_id}"
import json
import pathlib
import sys

thread = json.loads(pathlib.Path(sys.argv[1]).read_text())
assert thread["escalation"]["id"] == sys.argv[2], thread
replies = thread.get("replies") or []
assert replies, thread
assert all(reply.get("visibility") == "PARTNER_VISIBLE" for reply in replies), replies
assert any(sys.argv[3] in (reply.get("attachments") or []) for reply in replies), replies
print("PASS: support thread returns partner-visible evidence-linked replies")
PY

  escalations_body="${TMP_DIR}/escalations-list.json"
  escalations_status="$(partner_request GET "${BASE_URL}/api/partners/support/escalations" "${escalations_body}")"
  cp "${escalations_body}" "${TMP_DIR}/last-body"
  assert_status "${escalations_status}" "200" "support escalation list reachable"
  python3 - <<'PY' "${escalations_body}" "${escalation_id}"
import json
import pathlib
import sys

escalations = json.loads(pathlib.Path(sys.argv[1]).read_text())
assert any(escalation.get("id") == sys.argv[2] for escalation in escalations), escalations
print("PASS: support escalation list includes persisted escalation")
PY

  activity_body="${TMP_DIR}/partner-activity.json"
  activity_status="$(partner_request GET "${BASE_URL}/api/partners/activity" "${activity_body}")"
  cp "${activity_body}" "${TMP_DIR}/last-body"
  assert_status "${activity_status}" "200" "partner activity feed reachable"
  python3 - <<'PY' "${activity_body}"
import json
import pathlib
import sys

activity = json.loads(pathlib.Path(sys.argv[1]).read_text())
actions = {item.get("action") for item in activity}
required = {
    "VERIFICATION_RUN_CREATED",
    "EVIDENCE_BUNDLE_CREATED",
    "TEMPLATE_APPLICATION_REUSED",
    "SUPPORT_REPLY_CREATED",
}
missing = required - actions
assert not missing, {"missing": sorted(missing), "activity": activity}
raw = json.dumps(activity).lower()
for forbidden in ("secret", "token", "password", "credential"):
    assert forbidden not in raw, activity
print("PASS: partner activity feed includes workflow events without secret material")
PY

  if [[ "${created_temp_access}" == "true" ]]; then
    revoke_body="${TMP_DIR}/merchant-access-revoke.json"
    if [[ -n "${TEMP_ACCESS_APPROVAL_CODE}" ]]; then
      revoke_status="$(request POST "${BASE_URL}/api/merchant/partner-access/${TEMP_ACCESS_APPROVAL_CODE}/revoke" "${revoke_body}" \
        -H "Content-Type: application/json" \
        -d '{"approverName":"Release Gate Merchant","approverEmail":"release-gate-merchant@loom.test","decisionReason":"Release gate temporary access cleanup."}')"
    else
      encoded_revoke_shop="$(urlencode "${TEMP_ACCESS_SHOP_DOMAIN}")"
      revoke_status="$(platform_request POST "${BASE_URL}/api/merchant/partner-access/requests/${TEMP_ACCESS_REQUEST_ID}/revoke?shopDomain=${encoded_revoke_shop}" "${revoke_body}" \
        -H "Content-Type: application/json" \
        -d '{"approverName":"Release Gate Merchant","approverEmail":"release-gate-merchant@loom.test","decisionReason":"Release gate temporary access cleanup."}')"
    fi
    cp "${revoke_body}" "${TMP_DIR}/last-body"
    assert_status "${revoke_status}" "200" "temporary merchant access revoked"
    python3 - <<'PY' "${revoke_body}" "${workflow_store_id}" "${workflow_shop_domain}"
import json
import pathlib
import sys

decision = json.loads(pathlib.Path(sys.argv[1]).read_text())
assert decision["assignmentId"] == sys.argv[2], decision
assert str(decision.get("shopDomain") or "").lower() == sys.argv[3].lower(), decision
assert decision["status"] == "REVOKED", decision
print("PASS: temporary partner assignment revoked")
PY
    implementation_revoked_body="${TMP_DIR}/implementation-detail-revoked.json"
    implementation_revoked_status="$(partner_request GET "${BASE_URL}/api/partners/client-implementations/${implementation_id}" "${implementation_revoked_body}")"
    cp "${implementation_revoked_body}" "${TMP_DIR}/last-body"
    assert_status "${implementation_revoked_status}" "200" "revoked implementation detail reachable"
    python3 - <<'PY' "${implementation_revoked_body}" "${implementation_id}"
import json
import pathlib
import sys

implementation = json.loads(pathlib.Path(sys.argv[1]).read_text())
assert implementation["id"] == sys.argv[2], implementation
assert implementation["status"] == "REVOKED", implementation
print("PASS: partner implementation status reflects access revoke")
PY
    revoked_store_body="${TMP_DIR}/revoked-store-detail.json"
    revoked_store_status="$(partner_request GET "${BASE_URL}/api/partners/stores/${workflow_store_id}" "${revoked_store_body}")"
    cp "${revoked_store_body}" "${TMP_DIR}/last-body"
    assert_status "${revoked_store_status}" "403" "revoked partner store detail is forbidden"
    revoked_product_controls_body="${TMP_DIR}/revoked-product-controls.json"
    revoked_product_controls_status="$(partner_request GET "${BASE_URL}/api/partners/stores/${workflow_store_id}/product-controls" "${revoked_product_controls_body}")"
    cp "${revoked_product_controls_body}" "${TMP_DIR}/last-body"
    assert_status "${revoked_product_controls_status}" "403" "revoked partner product controls are forbidden"
    TEMP_ACCESS_REQUEST_ID=""
    TEMP_ACCESS_SHOP_DOMAIN=""
    TEMP_ACCESS_APPROVAL_CODE=""
  fi
else
  echo "SKIP: catalog/store checks need a provisioned partner workspace."
fi

echo "PASS: partner enablement live gate completed"
