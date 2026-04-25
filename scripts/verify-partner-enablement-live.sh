#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${PLATFORM_BASE_URL:-${PARTNER_PLATFORM_BASE_URL:-}}"
PARTNER_UI_BASE_URL="${PARTNER_UI_BASE_URL:-}"
PARTNER_SUPABASE_JWT="${PARTNER_SUPABASE_JWT:-}"
STRICT="${PARTNER_LIVE_STRICT:-false}"

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

PARTNER_SUPABASE_JWT="$(resolve_secret_value "PARTNER_SUPABASE_JWT")"
BASE_URL="${BASE_URL%/}"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

request() {
  local method="$1"
  local url="$2"
  local out="$3"
  shift 3
  curl -sS -X "${method}" -o "${out}" -w "%{http_code}" "$@" "${url}"
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
  ui_health_status="$(request GET "${PARTNER_UI_BASE_URL%/}/health" "${ui_health_body}")"
  cp "${ui_health_body}" "${TMP_DIR}/last-body"
  assert_status "${ui_health_status}" "200" "partner UI health reachable"

  ui_runtime_body="${TMP_DIR}/partner-ui-runtime-config.js"
  ui_runtime_status="$(request GET "${PARTNER_UI_BASE_URL%/}/runtime-config.js" "${ui_runtime_body}")"
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
  ui_status="$(request GET "${PARTNER_UI_BASE_URL%/}/" "${ui_body}")"
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
else
  echo "BLOCKED: PARTNER_UI_BASE_URL is not set; deployed partner UI route was not checked."
  if [[ "${STRICT}" == "true" ]]; then
    exit 20
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
session_status="$(request GET "${BASE_URL}/api/partners/session" "${session_body}" -H "Authorization: Bearer ${PARTNER_SUPABASE_JWT}")"
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

if python3 - <<'PY' "${session_body}"
import json, pathlib, sys
data = json.loads(pathlib.Path(sys.argv[1]).read_text())
sys.exit(0 if not data.get("signupRequired") else 1)
PY
then
  catalog_body="${TMP_DIR}/catalog.json"
  catalog_status="$(request GET "${BASE_URL}/api/partners/catalog" "${catalog_body}" -H "Authorization: Bearer ${PARTNER_SUPABASE_JWT}")"
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
  stores_status="$(request GET "${BASE_URL}/api/partners/stores" "${stores_body}" -H "Authorization: Bearer ${PARTNER_SUPABASE_JWT}")"
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
else
  echo "SKIP: catalog/store checks need a provisioned partner workspace."
fi

echo "PASS: partner enablement live gate completed"
