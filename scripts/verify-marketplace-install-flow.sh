#!/usr/bin/env bash
set -euo pipefail

# Live marketplace install-flow verifier.
#
# Proves that:
# - a template plugin can bootstrap a deployment
# - action, data, and automation plugins can be installed onto that deployment
# - entitlement activation compiles marketplace-managed contributions into the deployment draft
# - the resulting draft contains action, knowledge-source, automation, and shell contributions
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

TEMPLATE_PLUGIN_ID="${TEMPLATE_PLUGIN_ID:-mkp-template-support-desk-shell}"
ACTION_PLUGIN_ID="${ACTION_PLUGIN_ID:-mkp-action-notifications}"
DATA_PLUGIN_ID="${DATA_PLUGIN_ID:-mkp-data-help-center}"
AUTOMATION_PLUGIN_ID="${AUTOMATION_PLUGIN_ID:-mkp-automation-order-retention}"
VALIDATION_TEMPLATE_ID="${VALIDATION_TEMPLATE_ID:-dev-openai-qdrant}"
VALIDATION_ENVIRONMENT="${VALIDATION_ENVIRONMENT:-dev}"
VALIDATION_NAME_PREFIX="${VALIDATION_NAME_PREFIX:-Marketplace Live Validation}"
KEEP_DEPLOYMENT="${KEEP_DEPLOYMENT:-true}"
VALIDATION_VECTOR_PROVISIONING_MODE="${VALIDATION_VECTOR_PROVISIONING_MODE:-PLATFORM_MANAGED}"
VALIDATION_SHARED_VECTOR_PROVIDER="${VALIDATION_SHARED_VECTOR_PROVIDER:-aws}"
VALIDATION_SHARED_VECTOR_REGION="${VALIDATION_SHARED_VECTOR_REGION:-eu-west-1}"

ACTION_PLUGIN_VERSION="${ACTION_PLUGIN_VERSION:-1.0.0}"
DATA_PLUGIN_VERSION="${DATA_PLUGIN_VERSION:-1.0.0}"
TEMPLATE_PLUGIN_VERSION="${TEMPLATE_PLUGIN_VERSION:-1.0.0}"
AUTOMATION_PLUGIN_VERSION="${AUTOMATION_PLUGIN_VERSION:-1.0.0}"

ACTION_CONFIG_JSON="${ACTION_CONFIG_JSON:-{\"provider\":\"sendgrid\",\"defaultSender\":\"support@loom.test\"}}"
ACTION_SECRET_REFS_JSON="${ACTION_SECRET_REFS_JSON:-{\"credentialSecretRef\":\"sec-sendgrid\"}}"
DATA_CONFIG_JSON="${DATA_CONFIG_JSON:-{\"scope\":\"all\"}}"
DATA_SECRET_REFS_JSON="${DATA_SECRET_REFS_JSON:-{}}"
AUTOMATION_CONFIG_JSON="${AUTOMATION_CONFIG_JSON:-{\"discountPercent\":10,\"cooldownDays\":7}}"
AUTOMATION_SECRET_REFS_JSON="${AUTOMATION_SECRET_REFS_JSON:-{}}"

TMP_DIR=""
COOKIE_JAR=""
HTTP_BODY_FILE=""
HTTP_STATUS=""
DEPLOYMENT_ID=""

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
  PARSE_BODY_FILE="${HTTP_BODY_FILE}" python3 - <<'PY' "${label}" "${program}"
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
except AssertionError as exc:
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

platform_request "GET" "/api/deployments/${DEPLOYMENT_ID}/draft"
assert_status 200 "deployment draft fetch before shared vector patch"
DRAFT_ID="$(extract_json_value 'result = (data or {}).get("id", "")')"
PATCHED_PROVIDER_CONFIG_JSON="$(extract_json_value 'import json
result = json.dumps((data or {}).get("providerConfig") or {})')"
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

platform_request "POST" "/api/deployments/${DEPLOYMENT_ID}/marketplace-installs" "$(python3 - <<'PY' "${DATA_PLUGIN_ID}" "${DATA_PLUGIN_VERSION}" "${DATA_CONFIG_JSON}" "${DATA_SECRET_REFS_JSON}"
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
assert_status 201 "data plugin install"
DATA_INSTALL_ID="$(extract_json_value 'result = (data or {}).get("id", "")')"
json_assert "data plugin install" $'assert (data or {}).get("pluginType") == "DATA"\nassert (data or {}).get("readinessStatus") in {"READY", "ENTITLEMENT_REQUIRED"}\nprint("ok")'
pass "data plugin installed as ${DATA_INSTALL_ID}"

platform_request "POST" "/api/deployments/${DEPLOYMENT_ID}/marketplace-installs" "$(python3 - <<'PY' "${AUTOMATION_PLUGIN_ID}" "${AUTOMATION_PLUGIN_VERSION}" "${AUTOMATION_CONFIG_JSON}" "${AUTOMATION_SECRET_REFS_JSON}"
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
assert_status 201 "automation plugin install"
AUTOMATION_INSTALL_ID="$(extract_json_value 'result = (data or {}).get("id", "")')"
json_assert "automation plugin install" $'assert (data or {}).get("pluginType") == "AUTOMATION"\nassert (data or {}).get("readinessStatus") in {"READY", "ENTITLEMENT_REQUIRED"}\nprint("ok")'
pass "automation plugin installed as ${AUTOMATION_INSTALL_ID}"

platform_request "PUT" "/api/deployments/${DEPLOYMENT_ID}/marketplace-installs/${ACTION_INSTALL_ID}/entitlement" '{"status":"ACTIVE"}'
assert_status 200 "action plugin entitlement activation"
json_assert "action plugin entitlement activation" $'assert ((data or {}).get("entitlement") or {}).get("status") == "ACTIVE"\nassert (data or {}).get("readinessStatus") == "READY"\nprint("ok")'
pass "action entitlement activated"

platform_request "GET" "/api/deployments/${DEPLOYMENT_ID}/draft"
assert_status 200 "deployment draft fetch"
ACTION_INSTALL_ID_EXPECTED="${ACTION_INSTALL_ID}" DATA_INSTALL_ID_EXPECTED="${DATA_INSTALL_ID}" AUTOMATION_INSTALL_ID_EXPECTED="${AUTOMATION_INSTALL_ID}" \
  json_assert "deployment draft marketplace compilation" $'import os\ndraft = data or {}\nactions = ((draft.get("actionsConfig") or {}).get("actions") or [])\nentities = (((draft.get("entityConfig") or {}).get("ai-entities") or {}))\nsources = ((draft.get("knowledgeSourceConfig") or {}).get("sources") or [])\nautomation = (draft.get("automationConfig") or {})\ntriggers = automation.get("triggers") or []\nautomation_actions = automation.get("actions") or []\nworkflows = automation.get("workflows") or []\nschedules = automation.get("schedules") or []\nmodules = ((draft.get("shellConfig") or {}).get("modules") or [])\naction_install_id = os.environ["ACTION_INSTALL_ID_EXPECTED"]\ndata_install_id = os.environ["DATA_INSTALL_ID_EXPECTED"]\nautomation_install_id = os.environ["AUTOMATION_INSTALL_ID_EXPECTED"]\nassert any(item.get("name") == "send-email" and item.get("marketplaceInstallId") == action_install_id for item in actions), actions\nassert any(item.get("name") == "send-sms" and item.get("marketplaceInstallId") == action_install_id for item in actions), actions\nassert (entities.get("faq-article") or {}).get("marketplaceInstallId") == data_install_id, entities\nassert any(item.get("id") == "help-center" and item.get("marketplaceInstallId") == data_install_id for item in sources), sources\nassert any(item.get("id") == "order-cancel-requested" and item.get("marketplaceInstallId") == automation_install_id for item in triggers), triggers\nassert any(item.get("id") == "offer-retention-discount" and item.get("actionRef") == "offer_order_discount" and item.get("marketplaceInstallId") == automation_install_id for item in automation_actions), automation_actions\nassert any(item.get("id") == "order-cancel-retention" and item.get("marketplaceInstallId") == automation_install_id for item in workflows), workflows\nassert any(item.get("id") == "retention-follow-up" and item.get("workflowRef") == "order-cancel-retention" and item.get("marketplaceInstallId") == automation_install_id for item in schedules), schedules\nmodule_ids = {item.get("id") for item in modules}\nassert {"actions", "docs", "ai-search", "support"}.issubset(module_ids), modules\nassert (draft.get("shellConfig") or {}).get("defaultConversationMode") == "guided-support", draft.get("shellConfig")\nprint("ok")'
pass "deployment draft contains compiled marketplace contributions"

platform_request "GET" "/api/deployments/${DEPLOYMENT_ID}/marketplace-impact"
assert_status 200 "deployment marketplace impact"
json_assert "deployment marketplace impact" $'impact = data or {}\nassert int(impact.get("totalInstalls") or 0) >= 4, impact\nassert int(impact.get("actionPluginCount") or 0) >= 1, impact\nassert int(impact.get("dataPluginCount") or 0) >= 1, impact\nassert int(impact.get("templatePluginCount") or 0) >= 1, impact\nassert int(impact.get("automationPluginCount") or 0) >= 1, impact\nassert "mkp-template-support-desk-shell" in (impact.get("installedPluginIds") or []), impact\nassert "mkp-action-notifications" in (impact.get("installedPluginIds") or []), impact\nassert "mkp-data-help-center" in (impact.get("installedPluginIds") or []), impact\nassert "mkp-automation-order-retention" in (impact.get("installedPluginIds") or []), impact\nassert "send-email" in (impact.get("actionIds") or []), impact\nassert "help-center" in (impact.get("knowledgeSourceIds") or []), impact\nassert "order-cancel-retention" in (impact.get("automationIds") or []), impact\nshell_ids = set(impact.get("shellModuleIds") or [])\nassert {"actions", "docs", "ai-search", "support"}.issubset(shell_ids), impact\nprint("ok")'
pass "impact preview reflects template, action, and data contributions"

platform_request "GET" "/api/deployments/${DEPLOYMENT_ID}/marketplace-installs"
assert_status 200 "deployment marketplace installs"
json_assert "deployment marketplace installs" $'installs = data or []\ninstall_map = {item.get("pluginId"): item for item in installs if isinstance(item, dict)}\nassert install_map["mkp-template-support-desk-shell"]["status"] == "BOOTSTRAPPED", install_map\nassert install_map["mkp-action-notifications"]["readinessStatus"] == "READY", install_map\nassert install_map["mkp-data-help-center"]["readinessStatus"] == "READY", install_map\nassert install_map["mkp-automation-order-retention"]["readinessStatus"] == "READY", install_map\nassert ((install_map["mkp-action-notifications"].get("entitlement") or {}).get("status")) == "ACTIVE", install_map\nprint("ok")'
pass "install records reflect active entitlements and compiled readiness"

echo ""
echo "Deployment: ${DEPLOYMENT_ID}"
echo "Action install: ${ACTION_INSTALL_ID}"
echo "Data install: ${DATA_INSTALL_ID}"
echo "Automation install: ${AUTOMATION_INSTALL_ID}"
pass "marketplace install flow live verification"
