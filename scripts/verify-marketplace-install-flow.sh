#!/usr/bin/env bash
set -euo pipefail

# Live marketplace install-flow verifier.
#
# Proves that:
# - a template plugin can bootstrap a deployment
# - action and data plugins can be installed onto that deployment
# - entitlement activation compiles marketplace-managed contributions into the deployment draft
# - packaged seed, SQL sync, and folder sync DATA plugins flow into deployment config
# - the draft validates, publishes, applies, and reaches APPLIED_VERIFIED
# - the live runtime returns evidence from each installed DATA plugin through the platform POC path
#
# Example:
#   PLATFORM_BASE_URL="https://<platform>.up.railway.app" \
#   PLATFORM_LOGIN_EMAIL="admin@example.com" \
#   PLATFORM_LOGIN_PASSWORD="..." \
#   ./scripts/verify-marketplace-install-flow.sh

PLATFORM_BASE_URL="${PLATFORM_BASE_URL:-${PLATFORM_PUBLIC_BASE_URL:-}}"
PLATFORM_API_KEY_HEADER="${PLATFORM_API_KEY_HEADER:-X-PLATFORM-API-KEY}"
PLATFORM_API_KEY="${PLATFORM_API_KEY:-}"
PLATFORM_COOKIE="${PLATFORM_COOKIE:-}"
PLATFORM_LOGIN_EMAIL="${PLATFORM_LOGIN_EMAIL:-}"
PLATFORM_LOGIN_PASSWORD="${PLATFORM_LOGIN_PASSWORD:-}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

TEMPLATE_PLUGIN_ID="${TEMPLATE_PLUGIN_ID:-mkp-template-support-desk-shell}"
ACTION_PLUGIN_ID="${ACTION_PLUGIN_ID:-mkp-action-notifications}"
PACKAGED_DATA_PLUGIN_ID="${PACKAGED_DATA_PLUGIN_ID:-${DATA_PLUGIN_ID:-mkp-data-help-center}}"
SQL_DATA_PLUGIN_ID="${SQL_DATA_PLUGIN_ID:-mkp-data-commerce-catalog}"
FOLDER_DATA_PLUGIN_ID="${FOLDER_DATA_PLUGIN_ID:-mkp-data-policy-folder}"
FOLDER_DATA_PLUGIN_SLUG="${FOLDER_DATA_PLUGIN_SLUG:-policy-folder-data}"
FIRST_PARTY_PUBLISHER_SLUG="${FIRST_PARTY_PUBLISHER_SLUG:-loom}"
FIRST_PARTY_PUBLISHER_DISPLAY_NAME="${FIRST_PARTY_PUBLISHER_DISPLAY_NAME:-Loom AI}"
FIRST_PARTY_PUBLISHER_CONTACT_EMAIL="${FIRST_PARTY_PUBLISHER_CONTACT_EMAIL:-support@loom.test}"
FOLDER_DATA_PLUGIN_MANIFEST_PATH="${FOLDER_DATA_PLUGIN_MANIFEST_PATH:-${SCRIPT_DIR}/fixtures/marketplace/mkp-data-policy-folder-1.0.0.json}"

VALIDATION_TEMPLATE_ID="${VALIDATION_TEMPLATE_ID:-dev-openai-qdrant}"
VALIDATION_ENVIRONMENT="${VALIDATION_ENVIRONMENT:-dev}"
VALIDATION_NAME_PREFIX="${VALIDATION_NAME_PREFIX:-Marketplace Live Validation}"
KEEP_DEPLOYMENT="${KEEP_DEPLOYMENT:-true}"
VALIDATION_VECTOR_PROVISIONING_MODE="${VALIDATION_VECTOR_PROVISIONING_MODE:-PLATFORM_MANAGED}"
VALIDATION_SHARED_VECTOR_PROVIDER="${VALIDATION_SHARED_VECTOR_PROVIDER:-aws}"
VALIDATION_SHARED_VECTOR_REGION="${VALIDATION_SHARED_VECTOR_REGION:-eu-west-1}"

ACTION_PLUGIN_VERSION="${ACTION_PLUGIN_VERSION:-1.0.0}"
PACKAGED_DATA_PLUGIN_VERSION="${PACKAGED_DATA_PLUGIN_VERSION:-${DATA_PLUGIN_VERSION:-1.0.0}}"
SQL_DATA_PLUGIN_VERSION="${SQL_DATA_PLUGIN_VERSION:-1.0.0}"
FOLDER_DATA_PLUGIN_VERSION="${FOLDER_DATA_PLUGIN_VERSION:-1.0.0}"
TEMPLATE_PLUGIN_VERSION="${TEMPLATE_PLUGIN_VERSION:-1.0.0}"

ACTION_CONFIG_JSON="${ACTION_CONFIG_JSON:-{\"provider\":\"sendgrid\",\"defaultSender\":\"support@loom.test\"}}"
ACTION_SECRET_REFS_JSON="${ACTION_SECRET_REFS_JSON:-{\"credentialSecretRef\":\"sec-sendgrid\"}}"
PACKAGED_DATA_CONFIG_JSON="${PACKAGED_DATA_CONFIG_JSON:-${DATA_CONFIG_JSON:-{\"scope\":\"all\"}}}"
PACKAGED_DATA_SECRET_REFS_JSON="${PACKAGED_DATA_SECRET_REFS_JSON:-${DATA_SECRET_REFS_JSON:-{}}}"
SQL_DATA_CONFIG_JSON="${SQL_DATA_CONFIG_JSON:-{\"scope\":\"all\"}}"
SQL_DATA_SECRET_REFS_JSON="${SQL_DATA_SECRET_REFS_JSON:-{}}"
FOLDER_DATA_CONFIG_JSON="${FOLDER_DATA_CONFIG_JSON:-{}}"
FOLDER_DATA_SECRET_REFS_JSON="${FOLDER_DATA_SECRET_REFS_JSON:-{}}"

PACKAGED_DATA_QUERY="${PACKAGED_DATA_QUERY:-How do I reset my password?}"
FOLDER_DATA_QUERY="${FOLDER_DATA_QUERY:-What is the standard shipping target?}"
SQL_DATA_QUERY="${SQL_DATA_QUERY:-Tell me about Alienware m18 R2}"
MULTI_SOURCE_QUERY="${MULTI_SOURCE_QUERY:-Summarize the refund policy.}"

TMP_DIR=""
COOKIE_JAR=""
HTTP_BODY_FILE=""
HTTP_STATUS=""
DEPLOYMENT_ID=""
DRAFT_ID=""
PUBLISHED_VERSION_ID=""
RELEASE_ID=""

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

require_cmd() {
  local cmd="$1"
  if ! command -v "${cmd}" >/dev/null 2>&1; then
    echo "Missing required command: ${cmd}" >&2
    exit 2
  fi
}

cleanup() {
  if [[ -n "${DEPLOYMENT_ID}" && "${KEEP_DEPLOYMENT}" != "true" ]]; then
    local -a cleanup_args=(
      curl -sS -X DELETE
      "$(platform_url "/api/deployments/${DEPLOYMENT_ID}")"
    )
    if [[ -n "${PLATFORM_API_KEY}" ]]; then
      cleanup_args+=(-H "${PLATFORM_API_KEY_HEADER}: ${PLATFORM_API_KEY}")
    elif [[ -n "${PLATFORM_COOKIE}" ]]; then
      cleanup_args+=(-H "Cookie: ${PLATFORM_COOKIE}")
    else
      cleanup_args+=(-b "${COOKIE_JAR}" -c "${COOKIE_JAR}")
    fi
    "${cleanup_args[@]}" >/dev/null || true
  fi
  rm -rf "${TMP_DIR}"
}

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

pass() {
  echo "PASS: $*"
}

platform_url() {
  local path="$1"
  printf '%s%s' "${PLATFORM_BASE_URL%/}" "${path}"
}

platform_request() {
  local method="$1"
  local path="$2"
  local body="${3:-}"
  HTTP_BODY_FILE="${TMP_DIR}/body-$(date +%s%N).json"

  local -a curl_args=(
    curl -sS -X "${method}"
    "$(platform_url "${path}")"
    -o "${HTTP_BODY_FILE}"
    -w "%{http_code}"
  )

  if [[ -n "${body}" ]]; then
    curl_args+=(-H "Content-Type: application/json" --data "${body}")
  fi

  if [[ -n "${PLATFORM_API_KEY}" ]]; then
    curl_args+=(-H "${PLATFORM_API_KEY_HEADER}: ${PLATFORM_API_KEY}")
  elif [[ -n "${PLATFORM_COOKIE}" ]]; then
    curl_args+=(-H "Cookie: ${PLATFORM_COOKIE}")
  else
    curl_args+=(-b "${COOKIE_JAR}" -c "${COOKIE_JAR}")
  fi

  HTTP_STATUS="$("${curl_args[@]}")"
}

public_request() {
  local url="$1"
  HTTP_BODY_FILE="${TMP_DIR}/public-$(date +%s%N).json"
  HTTP_STATUS="$(curl -sS "${url}" -o "${HTTP_BODY_FILE}" -w "%{http_code}")"
}

assert_status() {
  local expected="$1"
  local label="$2"
  if [[ "${HTTP_STATUS}" != "${expected}" ]]; then
    echo "${label} returned ${HTTP_STATUS}" >&2
    cat "${HTTP_BODY_FILE}" >&2
    exit 1
  fi
}

json_assert() {
  local label="$1"
  local program="$2"
  PARSE_BODY_FILE="${HTTP_BODY_FILE}" python3 - <<'PY' "${label}" "${program}" >/dev/null
import json
import os
import sys
from pathlib import Path

label = sys.argv[1]
program = sys.argv[2]
raw = Path(os.environ["PARSE_BODY_FILE"]).read_text(encoding="utf-8")
data = json.loads(raw) if raw.strip() else None
namespace = {"data": data}
try:
    exec(program, namespace, namespace)
except AssertionError:
    print(f"{label}: AssertionError", file=sys.stderr)
    print(raw, file=sys.stderr)
    raise
PY
}

extract_json_value() {
  local program="$1"
  PARSE_BODY_FILE="${HTTP_BODY_FILE}" python3 - <<'PY' "${program}"
import json
import os
import sys
from pathlib import Path

raw = Path(os.environ["PARSE_BODY_FILE"]).read_text(encoding="utf-8")
data = json.loads(raw) if raw.strip() else None
program = sys.argv[1]
namespace = {"data": data}
exec(program, namespace, namespace)
print(namespace["result"])
PY
}

read_json_file_compact() {
  local file_path="$1"
  python3 - <<'PY' "${file_path}"
import json
import sys
from pathlib import Path

path = Path(sys.argv[1])
print(json.dumps(json.loads(path.read_text(encoding="utf-8"))))
PY
}

build_chat_query_payload() {
  local query="$1"
  local conversation_id="$2"
  CHAT_QUERY="${query}" CHAT_CONVERSATION_ID="${conversation_id}" python3 - <<'PY'
import json
import os

payload = {"query": os.environ["CHAT_QUERY"]}
conversation_id = os.environ.get("CHAT_CONVERSATION_ID", "").strip()
if conversation_id:
    payload["conversationId"] = conversation_id
print(json.dumps(payload))
PY
}

login_if_needed() {
  if [[ -n "${PLATFORM_API_KEY}" || -n "${PLATFORM_COOKIE}" ]]; then
    return
  fi
  if [[ -z "${PLATFORM_LOGIN_EMAIL}" || -z "${PLATFORM_LOGIN_PASSWORD}" ]]; then
    fail "Set PLATFORM_API_KEY, PLATFORM_COOKIE, or PLATFORM_LOGIN_EMAIL and PLATFORM_LOGIN_PASSWORD."
  fi
  platform_request "POST" "/api/platform/auth/login" "{\"email\":\"${PLATFORM_LOGIN_EMAIL}\",\"password\":\"${PLATFORM_LOGIN_PASSWORD}\"}"
  assert_status 200 "platform login"
  json_assert "platform login" $'assert (data or {}).get("authenticated") is True\nprint("ok")'
}

ensure_verified_publisher() {
  local publisher_slug="$1"
  local display_name="$2"
  local contact_email="$3"
  local publisher_id=""

  platform_request "GET" "/api/marketplace/publishers"
  assert_status 200 "marketplace publishers list"
  publisher_id="$(extract_json_value $'items = data or []\nresult = next((((item or {}).get("id") or "") for item in items if ((item or {}).get("slug") or "").lower() == "'"${publisher_slug}"'"), "")')"

  if [[ -z "${publisher_id}" ]]; then
    platform_request "POST" "/api/marketplace/publishers" "$(python3 - <<'PY' "${publisher_slug}" "${display_name}" "${contact_email}"
import json
import sys

print(json.dumps({
    "slug": sys.argv[1],
    "displayName": sys.argv[2],
    "contactEmail": sys.argv[3],
}))
PY
)"
    assert_status 201 "marketplace publisher create"
    publisher_id="$(extract_json_value 'result = (data or {}).get("id", "")')"
    if [[ -z "${publisher_id}" ]]; then
      fail "Created marketplace publisher id was empty for slug ${publisher_slug}."
    fi
    echo "PASS: created first-party marketplace publisher ${publisher_id}" >&2
  fi

  platform_request "PUT" "/api/marketplace/publishers/${publisher_id}/verification" '{"verificationStatus":"VERIFIED","status":"ACTIVE"}'
  assert_status 200 "marketplace publisher verification"
  json_assert "marketplace publisher verification" $'assert (data or {}).get("verificationStatus") == "VERIFIED", data\nassert (data or {}).get("status") == "ACTIVE", data\nprint("ok")'
  printf '%s' "${publisher_id}"
}

ensure_published_plugin_from_manifest_file() {
  local plugin_id="$1"
  local plugin_version="$2"
  local plugin_slug="$3"
  local manifest_path="$4"
  local publisher_slug="$5"
  local publisher_display_name="$6"
  local publisher_contact_email="$7"

  if [[ ! -f "${manifest_path}" ]]; then
    fail "Marketplace manifest fixture not found: ${manifest_path}"
  fi

  platform_request "GET" "/api/marketplace/plugins/${plugin_id}/versions/${plugin_version}"
  if [[ "${HTTP_STATUS}" == "200" ]]; then
    json_assert "marketplace plugin version fetch" $'assert (data or {}).get("status") == "PUBLISHED", data\nprint("ok")'
    pass "marketplace plugin ${plugin_id}@${plugin_version} already published"
    return
  fi
  if [[ "${HTTP_STATUS}" != "404" ]]; then
    fail "marketplace plugin version fetch returned ${HTTP_STATUS} for ${plugin_id}@${plugin_version}"
  fi

  local publisher_id=""
  local plugin_version_id=""
  local manifest_json=""
  publisher_id="$(ensure_verified_publisher "${publisher_slug}" "${publisher_display_name}" "${publisher_contact_email}")"
  manifest_json="$(read_json_file_compact "${manifest_path}")"

  platform_request "POST" "/api/marketplace/publishers/${publisher_id}/submissions" "$(python3 - <<'PY' "${plugin_slug}" "${manifest_json}"
import json
import sys

print(json.dumps({
    "pluginSlug": sys.argv[1],
    "releaseChannel": "GA",
    "manifest": json.loads(sys.argv[2]),
}))
PY
)"
  assert_status 201 "marketplace publisher submission create"
  plugin_version_id="$(extract_json_value 'result = (data or {}).get("pluginVersionId", "")')"
  if [[ -z "${plugin_version_id}" ]]; then
    fail "Marketplace publisher submission returned empty pluginVersionId for ${plugin_id}@${plugin_version}."
  fi

  platform_request "POST" "/api/marketplace/submissions/${plugin_version_id}/validate" '{"reviewNotes":"Seeded first-party starter plugin for live verification."}'
  assert_status 200 "marketplace publisher submission validate"
  json_assert "marketplace publisher submission validate" $'assert (data or {}).get("status") == "VALIDATED", data\nprint("ok")'

  platform_request "POST" "/api/marketplace/submissions/${plugin_version_id}/publish" '{"reviewNotes":"Published first-party starter plugin for live verification."}'
  assert_status 200 "marketplace publisher submission publish"
  json_assert "marketplace publisher submission publish" $'assert (data or {}).get("status") == "PUBLISHED", data\nprint("ok")'
  pass "marketplace plugin ${plugin_id}@${plugin_version} published through first-party publisher workflow"
}

fetch_active_draft() {
  platform_request "GET" "/api/deployments/${DEPLOYMENT_ID}/draft"
  assert_status 200 "deployment draft fetch"
  DRAFT_ID="$(extract_json_value 'result = (data or {}).get("id", "")')"
  if [[ -z "${DRAFT_ID}" ]]; then
    fail "Active draft id was empty for deployment ${DEPLOYMENT_ID}."
  fi
}

validate_active_draft() {
  fetch_active_draft
  platform_request "POST" "/api/deployment-drafts/${DRAFT_ID}/validate"
  assert_status 200 "deployment draft validate"
  json_assert "deployment draft validate" $'issues = (data or {}).get("issues") or []\nassert (data or {}).get("publishReady") is True, data\nassert len(issues) == 0, issues\nprint("ok")'
  pass "deployment draft validated as publish-ready"
}

publish_active_draft() {
  validate_active_draft
  platform_request "POST" "/api/deployment-drafts/${DRAFT_ID}/publish"
  if [[ "${HTTP_STATUS}" != "200" && "${HTTP_STATUS}" != "201" ]]; then
    echo "deployment draft publish returned ${HTTP_STATUS}" >&2
    cat "${HTTP_BODY_FILE}" >&2
    exit 1
  fi
  PUBLISHED_VERSION_ID="$(extract_json_value 'result = (data or {}).get("id", "")')"
  if [[ -z "${PUBLISHED_VERSION_ID}" ]]; then
    fail "Published version id was empty for deployment ${DEPLOYMENT_ID}."
  fi
  json_assert "deployment draft publish" $'assert (data or {}).get("status") == "PUBLISHED", data\nprint("ok")'
  pass "deployment draft published as ${PUBLISHED_VERSION_ID}"
}

apply_published_version() {
  platform_request "POST" "/api/deployments/${DEPLOYMENT_ID}/apply/${PUBLISHED_VERSION_ID}"
  assert_status 200 "deployment version apply"
  RELEASE_ID="$(extract_json_value 'result = (data or {}).get("id", "")')"
  if [[ -z "${RELEASE_ID}" ]]; then
    fail "Release id was empty when applying version ${PUBLISHED_VERSION_ID}."
  fi
  pass "deployment version apply requested as ${RELEASE_ID}"
}

wait_for_release_verification() {
  local attempts="${1:-120}"
  local sleep_seconds="${2:-10}"
  local status=""
  local verification_status=""
  local current_step=""
  local error_message=""

  for _ in $(seq 1 "${attempts}"); do
    platform_request "GET" "/api/deployments/${DEPLOYMENT_ID}/releases"
    assert_status 200 "deployment releases poll"
    status="$(extract_json_value $'items = data or []\nrelease = next((item for item in items if (item or {}).get("id") == "'"${RELEASE_ID}"'"), None)\nresult = "" if release is None else (release.get("status") or "")')"
    verification_status="$(extract_json_value $'items = data or []\nrelease = next((item for item in items if (item or {}).get("id") == "'"${RELEASE_ID}"'"), None)\nresult = "" if release is None else (release.get("verificationStatus") or "")')"
    current_step="$(extract_json_value $'items = data or []\nrelease = next((item for item in items if (item or {}).get("id") == "'"${RELEASE_ID}"'"), None)\nresult = "" if release is None else (release.get("currentStepKey") or "")')"
    error_message="$(extract_json_value $'items = data or []\nrelease = next((item for item in items if (item or {}).get("id") == "'"${RELEASE_ID}"'"), None)\nresult = "" if release is None else (release.get("errorMessage") or "")')"

    if [[ "${status}" == "APPLIED_VERIFIED" && "${verification_status}" == "PASSED" ]]; then
      pass "release ${RELEASE_ID} reached APPLIED_VERIFIED"
      return
    fi

    if [[ "${status}" == "FAILED" || "${status}" == "VERIFY_FAILED" || "${status}" == "PRE_APPLY_BLOCKED" || "${status}" == "DELETED" ]]; then
      fail "release ${RELEASE_ID} ended in ${status}/${verification_status} at step ${current_step}: ${error_message}"
    fi

    sleep "${sleep_seconds}"
  done

  fail "Timed out waiting for release ${RELEASE_ID}; last state was ${status}/${verification_status} at step ${current_step}: ${error_message}"
}

run_platform_poc_query() {
  local query="$1"
  local conversation_id="$2"
  local label="$3"
  platform_request "POST" "/api/deployments/${DEPLOYMENT_ID}/poc-widget/chat/me/query?authPath=PLATFORM_PRIVATE" "$(build_chat_query_payload "${query}" "${conversation_id}")"
  assert_status 200 "${label}"
  json_assert "${label}" $'assert (data or {}).get("success") is True, data\nresult = (data or {}).get("result") or {}\nassert result.get("success") is True, result\nassert result.get("type") in {"INFORMATION_PROVIDED", "ACTION_EXECUTED"}, result\nprint("ok")'
}

assert_query_source() {
  local label="$1"
  local expected_source_id="$2"
  json_assert "${label}" $'result = (data or {}).get("result") or {}\ndocs = (((result.get("data") or {}).get("ragResponse") or {}).get("documents") or [])\nassert docs, result\nsource_ids = {((doc.get("metadata") or {}).get("knowledgeSourceId")) for doc in docs if isinstance(doc, dict)}\nadapter_types = {((doc.get("metadata") or {}).get("knowledgeSourceAdapterType")) for doc in docs if isinstance(doc, dict)}\nassert "'"${expected_source_id}"'" in source_ids, {"expectedSource": "'"${expected_source_id}"'", "actual": sorted([v for v in source_ids if v])}\nassert "shared-index" in adapter_types, {"expectedAdapter": "shared-index", "actual": sorted([v for v in adapter_types if v])}\nprint("ok")'
}

assert_multi_source_query() {
  local label="$1"
  json_assert "${label}" $'result = (data or {}).get("result") or {}\ndocs = (((result.get("data") or {}).get("ragResponse") or {}).get("documents") or [])\nassert docs, result\nsource_ids = {((doc.get("metadata") or {}).get("knowledgeSourceId")) for doc in docs if isinstance(doc, dict)}\nadapter_types = {((doc.get("metadata") or {}).get("knowledgeSourceAdapterType")) for doc in docs if isinstance(doc, dict)}\nexpected = {"help-center", "policy-folder", "commerce-catalog"}\nassert "shared-index" in adapter_types, {"expectedAdapter": "shared-index", "actual": sorted([v for v in adapter_types if v])}\nassert len(expected & source_ids) >= 2, {"expectedAtLeast": 2, "actual": sorted([v for v in (expected & source_ids) if v])}\nprint("ok")'
}

PLATFORM_BASE_URL="$(resolve_secret_value PLATFORM_BASE_URL)"
PLATFORM_API_KEY="$(resolve_secret_value PLATFORM_API_KEY)"
PLATFORM_COOKIE="$(resolve_secret_value PLATFORM_COOKIE)"
PLATFORM_LOGIN_EMAIL="$(resolve_secret_value PLATFORM_LOGIN_EMAIL)"
PLATFORM_LOGIN_PASSWORD="$(resolve_secret_value PLATFORM_LOGIN_PASSWORD)"

if [[ -z "${PLATFORM_BASE_URL}" ]]; then
  fail "Set PLATFORM_BASE_URL."
fi

require_cmd curl
require_cmd python3

TMP_DIR="$(mktemp -d)"
COOKIE_JAR="${TMP_DIR}/platform-cookies.txt"
trap cleanup EXIT

login_if_needed
ensure_published_plugin_from_manifest_file \
  "${FOLDER_DATA_PLUGIN_ID}" \
  "${FOLDER_DATA_PLUGIN_VERSION}" \
  "${FOLDER_DATA_PLUGIN_SLUG}" \
  "${FOLDER_DATA_PLUGIN_MANIFEST_PATH}" \
  "${FIRST_PARTY_PUBLISHER_SLUG}" \
  "${FIRST_PARTY_PUBLISHER_DISPLAY_NAME}" \
  "${FIRST_PARTY_PUBLISHER_CONTACT_EMAIL}"

VALIDATION_NAME="${VALIDATION_NAME_PREFIX} $(date +%Y%m%d-%H%M%S)"

platform_request "POST" "/api/marketplace/templates/${TEMPLATE_PLUGIN_ID}/bootstrap" "$(python3 - <<'PY' "${VALIDATION_NAME}" "${VALIDATION_ENVIRONMENT}" "${VALIDATION_TEMPLATE_ID}" "${TEMPLATE_PLUGIN_VERSION}" "${VALIDATION_VECTOR_PROVISIONING_MODE}"
import json
import sys
print(json.dumps({
    "name": sys.argv[1],
    "environment": sys.argv[2],
    "templateId": sys.argv[3],
    "pluginVersion": sys.argv[4],
    "vectorProvisioningMode": sys.argv[5],
}))
PY
)"
assert_status 201 "template bootstrap"
DEPLOYMENT_ID="$(extract_json_value 'result = (data or {}).get("id", "")')"
VALIDATION_TEMPLATE_ID_EXPECTED="${VALIDATION_TEMPLATE_ID}" \
  json_assert "template bootstrap deployment" $'import os\nassert (data or {}).get("templateId") == os.environ["VALIDATION_TEMPLATE_ID_EXPECTED"]\nprint("ok")'
pass "template plugin bootstrapped deployment ${DEPLOYMENT_ID}"

fetch_active_draft
PATCHED_PROVIDER_CONFIG_JSON="$(extract_json_value $'import json\nresult = json.dumps((data or {}).get("providerConfig") or {})')"
PATCHED_PROVIDER_CONFIG_JSON="$(python3 - <<'PY' "${PATCHED_PROVIDER_CONFIG_JSON}" "${VALIDATION_SHARED_VECTOR_PROVIDER}" "${VALIDATION_SHARED_VECTOR_REGION}"
import json
import sys

provider = json.loads(sys.argv[1])
provider["vectorProvisioningMode"] = "PLATFORM_MANAGED"
provider["vectorStoragePosture"] = "SHARED"
provider["qdrantManagedCollectionsEnabled"] = True
provider["qdrantCloudProviderId"] = sys.argv[2]
provider["qdrantCloudRegionId"] = sys.argv[3]
print(json.dumps(provider))
PY
)"
platform_request "PUT" "/api/deployment-drafts/${DRAFT_ID}" "$(python3 - <<'PY' "${PATCHED_PROVIDER_CONFIG_JSON}"
import json
import sys
print(json.dumps({
    "providerConfig": json.loads(sys.argv[1]),
}))
PY
)"
assert_status 200 "deployment draft shared vector patch"
VALIDATION_SHARED_VECTOR_PROVIDER_EXPECTED="${VALIDATION_SHARED_VECTOR_PROVIDER}" \
VALIDATION_SHARED_VECTOR_REGION_EXPECTED="${VALIDATION_SHARED_VECTOR_REGION}" \
  json_assert "deployment draft shared vector patch" $'import os\nprovider = (data or {}).get("providerConfig") or {}\nassert provider.get("vectorStoragePosture") == "SHARED", provider\nassert provider.get("vectorProvisioningMode") == "PLATFORM_MANAGED", provider\nassert provider.get("qdrantManagedCollectionsEnabled") is True, provider\nassert provider.get("qdrantCloudProviderId") == os.environ["VALIDATION_SHARED_VECTOR_PROVIDER_EXPECTED"], provider\nassert provider.get("qdrantCloudRegionId") == os.environ["VALIDATION_SHARED_VECTOR_REGION_EXPECTED"], provider\nprint("ok")'
pass "deployment draft patched to shared Qdrant vector backing"

platform_request "POST" "/api/deployments/${DEPLOYMENT_ID}/marketplace-installs" "$(python3 - <<'PY' "${ACTION_PLUGIN_ID}" "${ACTION_PLUGIN_VERSION}" "${ACTION_CONFIG_JSON}" "${ACTION_SECRET_REFS_JSON}"
import json
import sys
print(json.dumps({
    "pluginId": sys.argv[1],
    "pluginVersion": sys.argv[2],
    "config": json.loads(sys.argv[3]),
    "secretRefs": json.loads(sys.argv[4]),
}))
PY
)"
assert_status 201 "action plugin install"
ACTION_INSTALL_ID="$(extract_json_value 'result = (data or {}).get("id", "")')"
json_assert "action plugin install" $'assert (data or {}).get("pluginType") == "ACTION"\nassert (data or {}).get("readinessStatus") in {"READY", "ENTITLEMENT_REQUIRED"}\nprint("ok")'
pass "action plugin installed as ${ACTION_INSTALL_ID}"

platform_request "POST" "/api/deployments/${DEPLOYMENT_ID}/marketplace-installs" "$(python3 - <<'PY' "${PACKAGED_DATA_PLUGIN_ID}" "${PACKAGED_DATA_PLUGIN_VERSION}" "${PACKAGED_DATA_CONFIG_JSON}" "${PACKAGED_DATA_SECRET_REFS_JSON}"
import json
import sys
print(json.dumps({
    "pluginId": sys.argv[1],
    "pluginVersion": sys.argv[2],
    "config": json.loads(sys.argv[3]),
    "secretRefs": json.loads(sys.argv[4]),
}))
PY
)"
assert_status 201 "packaged data plugin install"
PACKAGED_DATA_INSTALL_ID="$(extract_json_value 'result = (data or {}).get("id", "")')"
json_assert "packaged data plugin install" $'assert (data or {}).get("pluginType") == "DATA"\nassert (data or {}).get("readinessStatus") in {"READY", "ENTITLEMENT_REQUIRED"}\nprint("ok")'
pass "packaged data plugin installed as ${PACKAGED_DATA_INSTALL_ID}"

platform_request "POST" "/api/deployments/${DEPLOYMENT_ID}/marketplace-installs" "$(python3 - <<'PY' "${SQL_DATA_PLUGIN_ID}" "${SQL_DATA_PLUGIN_VERSION}" "${SQL_DATA_CONFIG_JSON}" "${SQL_DATA_SECRET_REFS_JSON}"
import json
import sys
print(json.dumps({
    "pluginId": sys.argv[1],
    "pluginVersion": sys.argv[2],
    "config": json.loads(sys.argv[3]),
    "secretRefs": json.loads(sys.argv[4]),
}))
PY
)"
assert_status 201 "sql data plugin install"
SQL_DATA_INSTALL_ID="$(extract_json_value 'result = (data or {}).get("id", "")')"
json_assert "sql data plugin install" $'assert (data or {}).get("pluginType") == "DATA"\nassert (data or {}).get("readinessStatus") in {"READY", "ENTITLEMENT_REQUIRED"}\nprint("ok")'
pass "sql data plugin installed as ${SQL_DATA_INSTALL_ID}"

platform_request "POST" "/api/deployments/${DEPLOYMENT_ID}/marketplace-installs" "$(python3 - <<'PY' "${FOLDER_DATA_PLUGIN_ID}" "${FOLDER_DATA_PLUGIN_VERSION}" "${FOLDER_DATA_CONFIG_JSON}" "${FOLDER_DATA_SECRET_REFS_JSON}"
import json
import sys
print(json.dumps({
    "pluginId": sys.argv[1],
    "pluginVersion": sys.argv[2],
    "config": json.loads(sys.argv[3]),
    "secretRefs": json.loads(sys.argv[4]),
}))
PY
)"
assert_status 201 "folder data plugin install"
FOLDER_DATA_INSTALL_ID="$(extract_json_value 'result = (data or {}).get("id", "")')"
json_assert "folder data plugin install" $'assert (data or {}).get("pluginType") == "DATA"\nassert (data or {}).get("readinessStatus") in {"READY", "ENTITLEMENT_REQUIRED"}\nprint("ok")'
pass "folder data plugin installed as ${FOLDER_DATA_INSTALL_ID}"

platform_request "PUT" "/api/deployments/${DEPLOYMENT_ID}/marketplace-installs/${ACTION_INSTALL_ID}/entitlement" '{"status":"ACTIVE"}'
assert_status 200 "action plugin entitlement activation"
json_assert "action plugin entitlement activation" $'assert ((data or {}).get("entitlement") or {}).get("status") == "ACTIVE"\nassert (data or {}).get("readinessStatus") == "READY"\nprint("ok")'
pass "action entitlement activated"

platform_request "PUT" "/api/deployments/${DEPLOYMENT_ID}/marketplace-installs/${SQL_DATA_INSTALL_ID}/entitlement" '{"status":"ACTIVE"}'
assert_status 200 "sql data plugin entitlement activation"
json_assert "sql data plugin entitlement activation" $'assert ((data or {}).get("entitlement") or {}).get("status") == "ACTIVE"\nassert (data or {}).get("readinessStatus") == "READY"\nprint("ok")'
pass "sql data entitlement activated"

fetch_active_draft
PACKAGED_DATA_INSTALL_ID_EXPECTED="${PACKAGED_DATA_INSTALL_ID}" \
SQL_DATA_INSTALL_ID_EXPECTED="${SQL_DATA_INSTALL_ID}" \
FOLDER_DATA_INSTALL_ID_EXPECTED="${FOLDER_DATA_INSTALL_ID}" \
ACTION_INSTALL_ID_EXPECTED="${ACTION_INSTALL_ID}" \
json_assert "deployment draft marketplace compilation" $'import os\ndraft = data or {}\nactions = ((draft.get("actionsConfig") or {}).get("actions") or [])\nentities = (((draft.get("entityConfig") or {}).get("ai-entities") or {}))\nsources = ((draft.get("knowledgeSourceConfig") or {}).get("sources") or [])\nshell = draft.get("shellConfig") or {}\nmodules = shell.get("modules") or []\nstarter_prompts = shell.get("starterPrompts") or []\nprompt_config = draft.get("promptConfig") or {}\nsecurity = draft.get("securityConfig") or {}\ndatasets = ((draft.get("marketplaceDatasetConfig") or {}).get("datasets") or [])\npackaged_install_id = os.environ["PACKAGED_DATA_INSTALL_ID_EXPECTED"]\nsql_install_id = os.environ["SQL_DATA_INSTALL_ID_EXPECTED"]\nfolder_install_id = os.environ["FOLDER_DATA_INSTALL_ID_EXPECTED"]\naction_install_id = os.environ["ACTION_INSTALL_ID_EXPECTED"]\nassert any(item.get("name") == "send-email" and item.get("marketplaceInstallId") == action_install_id for item in actions), actions\nassert any(item.get("name") == "send-sms" and item.get("marketplaceInstallId") == action_install_id for item in actions), actions\nassert (entities.get("faq-article") or {}).get("marketplaceInstallId") == packaged_install_id, entities\nassert (entities.get("product") or {}).get("marketplaceInstallId") == sql_install_id, entities\nassert (entities.get("support-policy") or {}).get("marketplaceInstallId") == folder_install_id, entities\nassert any(item.get("id") == "help-center" and item.get("marketplaceInstallId") == packaged_install_id for item in sources), sources\nassert any(item.get("id") == "commerce-catalog" and item.get("marketplaceInstallId") == sql_install_id for item in sources), sources\nassert any(item.get("id") == "policy-folder" and item.get("marketplaceInstallId") == folder_install_id for item in sources), sources\nmodule_ids = {item.get("id") for item in modules}\nassert {"actions", "docs", "ai-search", "support", "products"}.issubset(module_ids), modules\nassert shell.get("defaultConversationMode") == "guided-support", shell\nassert (shell.get("greeting") or {}).get("title") == "Support Desk", shell\nstarter_ids = {item.get("id") for item in starter_prompts}\nassert {"support-capabilities", "refund-policy", "notification-troubleshooting"}.issubset(starter_ids), starter_prompts\nassert prompt_config.get("intentExtractionPrompt", "").find("must not be classified as OUT_OF_SCOPE") != -1, prompt_config\nassert security.get("authzMode") == "ALLOW_VERIFIED", security\nif datasets:\n    assert any(item.get("marketplaceInstallId") == packaged_install_id and item.get("ingestionMode") == "PACKAGED_SEED" and (item.get("seedDatasetRef") or "").endswith("help-center.jsonl") for item in datasets), datasets\n    assert any(item.get("marketplaceInstallId") == sql_install_id and item.get("ingestionMode") == "EXTERNAL_SYNC_SQL" and ((item.get("syncConnector") or {}).get("connectionRef")) == "platform-marketplace-demo-sql" for item in datasets), datasets\n    assert any(item.get("marketplaceInstallId") == folder_install_id and item.get("ingestionMode") == "EXTERNAL_SYNC_FOLDER" and "policy-pack" in (((item.get("syncConnector") or {}).get("folderRef")) or "") for item in datasets), datasets\n    for install_id in [packaged_install_id, sql_install_id, folder_install_id]:\n        matches = [item for item in datasets if item.get("marketplaceInstallId") == install_id]\n        assert matches, {"missingInstallId": install_id, "datasets": datasets}\n        assert all(item.get("handleRef") for item in matches), matches\n        assert all(item.get("datasetHash") for item in matches), matches\nprint("ok")'
pass "deployment draft contains compiled marketplace contributions"

platform_request "GET" "/api/deployments/${DEPLOYMENT_ID}/marketplace-impact"
assert_status 200 "deployment marketplace impact"
json_assert "deployment marketplace impact" $'impact = data or {}\nassert int(impact.get("totalInstalls") or 0) >= 5, impact\nassert int(impact.get("actionPluginCount") or 0) >= 1, impact\nassert int(impact.get("dataPluginCount") or 0) >= 3, impact\nassert int(impact.get("templatePluginCount") or 0) >= 1, impact\nfor plugin_id in ["mkp-template-support-desk-shell", "mkp-action-notifications", "mkp-data-help-center", "mkp-data-commerce-catalog", "mkp-data-policy-folder"]:\n  assert plugin_id in (impact.get("installedPluginIds") or []), impact\nfor action_id in ["send-email", "send-sms"]:\n  assert action_id in (impact.get("actionIds") or []), impact\nfor source_id in ["help-center", "commerce-catalog", "policy-folder"]:\n  assert source_id in (impact.get("knowledgeSourceIds") or []), impact\nshell_ids = set(impact.get("shellModuleIds") or [])\nassert {"actions", "docs", "ai-search", "support", "products"}.issubset(shell_ids), impact\nprint("ok")'
pass "impact preview reflects template, action, and data contributions"

platform_request "GET" "/api/deployments/${DEPLOYMENT_ID}/marketplace-installs"
assert_status 200 "deployment marketplace installs"
json_assert "deployment marketplace installs" $'installs = data or []\ninstall_map = {item.get("pluginId"): item for item in installs if isinstance(item, dict)}\nassert install_map["mkp-template-support-desk-shell"]["status"] == "BOOTSTRAPPED", install_map\nassert install_map["mkp-action-notifications"]["readinessStatus"] == "READY", install_map\nassert install_map["mkp-data-help-center"]["readinessStatus"] == "READY", install_map\nassert install_map["mkp-data-commerce-catalog"]["readinessStatus"] == "READY", install_map\nassert install_map["mkp-data-policy-folder"]["readinessStatus"] == "READY", install_map\nassert ((install_map["mkp-action-notifications"].get("entitlement") or {}).get("status")) == "ACTIVE", install_map\nassert ((install_map["mkp-data-commerce-catalog"].get("entitlement") or {}).get("status")) == "ACTIVE", install_map\nprint("ok")'
pass "install records reflect active entitlements and compiled readiness"

publish_active_draft

platform_request "GET" "/api/deployments/${DEPLOYMENT_ID}/versions/${PUBLISHED_VERSION_ID}/artifacts"
assert_status 200 "version artifacts bundle"
json_assert "version artifacts bundle" $'bundle = data or {}\nassert bool(bundle.get("knowledgeSourceArtifactUrl")), bundle\nassert bool(bundle.get("shellArtifactUrl")), bundle\nassert bool(bundle.get("manifestArtifactUrl") or bundle.get("manifestUrl")), bundle\nprint("ok")'
MARKETPLACE_DATASET_ARTIFACT_URL="$(extract_json_value 'result = (data or {}).get("marketplaceDatasetArtifactUrl", "")')"
KNOWLEDGE_SOURCE_ARTIFACT_URL="$(extract_json_value 'result = (data or {}).get("knowledgeSourceArtifactUrl", "")')"
SHELL_ARTIFACT_URL="$(extract_json_value 'result = (data or {}).get("shellArtifactUrl", "")')"
MANIFEST_ARTIFACT_URL="$(extract_json_value $'bundle = data or {}\nresult = bundle.get("manifestArtifactUrl") or bundle.get("manifestUrl") or ""')"
pass "version artifacts include knowledge, shell, and manifest outputs"

if [[ -n "${MARKETPLACE_DATASET_ARTIFACT_URL}" ]]; then
  public_request "${MARKETPLACE_DATASET_ARTIFACT_URL}"
  assert_status 200 "marketplace dataset artifact fetch"
  json_assert "marketplace dataset artifact fetch" $'datasets = ((data or {}).get("datasets") or [])\nby_id = {item.get("datasetId"): item for item in datasets if isinstance(item, dict)}\nassert (data or {}).get("contractVersion") == "MARKETPLACE_DATASET_CONFIG_V1", data\nassert set(by_id.keys()) == {"help-center-seed", "commerce-catalog-sql", "policy-folder-pack"}, by_id\nassert by_id["help-center-seed"]["ingestionMode"] == "PACKAGED_SEED", by_id\nassert by_id["commerce-catalog-sql"]["ingestionMode"] == "EXTERNAL_SYNC_SQL", by_id\nassert by_id["policy-folder-pack"]["ingestionMode"] == "EXTERNAL_SYNC_FOLDER", by_id\nprint("ok")'
fi

public_request "${KNOWLEDGE_SOURCE_ARTIFACT_URL}"
assert_status 200 "knowledge source artifact fetch"
json_assert "knowledge source artifact fetch" $'sources = ((data or {}).get("sources") or [])\nids = {item.get("id") for item in sources if isinstance(item, dict)}\nassert ids == {"help-center", "commerce-catalog", "policy-folder"}, ids\nfor item in sources:\n  assert item.get("sourceType") == "shared-index", item\n  assert item.get("handleRef", "").startswith("plugin/"), item\nprint("ok")'

public_request "${SHELL_ARTIFACT_URL}"
assert_status 200 "shell artifact fetch"
json_assert "shell artifact fetch" $'shell = data or {}\nmodule_ids = {item.get("id") for item in (shell.get("modules") or [])}\nassert {"actions", "docs", "ai-search", "support", "products"}.issubset(module_ids), shell\nassert (shell.get("greeting") or {}).get("title") == "Support Desk", shell\nprint("ok")'

public_request "${MANIFEST_ARTIFACT_URL}"
assert_status 200 "manifest artifact fetch"
json_assert "manifest artifact fetch" $'manifest = data or {}\nassert bool(manifest.get("marketplaceDatasetConfig")), manifest\nassert bool(manifest.get("knowledgeSourceConfig")), manifest\nassert bool(manifest.get("shellConfig")), manifest\nprint("ok")'
pass "published artifacts expose marketplace dataset outputs"

apply_published_version
wait_for_release_verification

platform_request "GET" "/api/deployments/${DEPLOYMENT_ID}/releases"
assert_status 200 "deployment releases final"
json_assert "deployment releases final" $'items = data or []\nrelease = next((item for item in items if (item or {}).get("id") == "'"${RELEASE_ID}"'"), None)\nassert release is not None, items\nassert release.get("status") == "APPLIED_VERIFIED", release\nassert release.get("verificationStatus") == "PASSED", release\nassert release.get("deploymentVersionId") == "'"${PUBLISHED_VERSION_ID}"'", release\nprint("ok")'
pass "published version is active and verified"

run_platform_poc_query "${PACKAGED_DATA_QUERY}" "marketplace-help-center-$(date +%s)" "packaged data live query"
assert_query_source "packaged data live query" "help-center"
pass "packaged seed data plugin answered live query"

run_platform_poc_query "${FOLDER_DATA_QUERY}" "marketplace-policy-folder-$(date +%s)" "folder data live query"
assert_query_source "folder data live query" "policy-folder"
pass "folder sync data plugin answered live query"

run_platform_poc_query "${SQL_DATA_QUERY}" "marketplace-sql-data-$(date +%s)" "sql data live query"
assert_query_source "sql data live query" "commerce-catalog"
pass "sql sync data plugin answered live query"

run_platform_poc_query "${MULTI_SOURCE_QUERY}" "marketplace-multi-source-$(date +%s)" "multi-source marketplace query"
assert_multi_source_query "multi-source marketplace query"
pass "multi-source retrieval returned marketplace data from multiple installed plugins"

echo ""
echo "Deployment: ${DEPLOYMENT_ID}"
echo "Published version: ${PUBLISHED_VERSION_ID}"
echo "Release: ${RELEASE_ID}"
echo "Action install: ${ACTION_INSTALL_ID}"
echo "Packaged data install: ${PACKAGED_DATA_INSTALL_ID}"
echo "SQL data install: ${SQL_DATA_INSTALL_ID}"
echo "Folder data install: ${FOLDER_DATA_INSTALL_ID}"
pass "marketplace install flow live verification"
