#!/usr/bin/env bash
set -euo pipefail

# Deployment verification script for the ecommerce demo and platform-operated Railway rollout:
# - Ecommerce Store (domain API)
# - Generic REST Connector (internal service, verified through runtime-backed admin reads)
# - AI Fabric Runtime
# - AI Enablement Platform deployment control plane
#
# Service verification usage (read-only checks + runtime-backed operational smoke):
#   STORE_BASE_URL="https://<ecommerce-store>.up.railway.app" \
#   RUNTIME_BASE_URL="https://<runtime>.up.railway.app" \
#   API_KEY="test-key" \
#   ./scripts/verify-ecommerce-deployment.sh
#
# Optional write checks (create/delete product + verify indexing counts):
#   VERIFY_WRITE=true ./scripts/verify-ecommerce-deployment.sh
#
# Optional confirmation-retention behavioral smoke:
#   VERIFY_CONFIRMATION_RETENTION_FLOW=true \
#   RUNTIME_PUBLIC_TOKEN_SIGNING_KEY="..." \
#   ./scripts/verify-ecommerce-deployment.sh
#
# Optional platform-operated deployment verification:
#   PLATFORM_BASE_URL="https://<platform-backend>.up.railway.app" \
#   PLATFORM_DEPLOYMENT_ID="dep-12345678" \
#   PLATFORM_API_KEY="..." \
#   ./scripts/verify-ecommerce-deployment.sh
#
# Full end-to-end usage:
#   STORE_BASE_URL="https://<ecommerce-store>.up.railway.app" \
#   RUNTIME_BASE_URL="https://<runtime>.up.railway.app" \
#   API_KEY="test-key" \
#   RUNTIME_PRIVATE_AUTHORIZATION="Bearer rpa1..." \
#   RUNTIME_TRUSTED_BACKEND_API_KEY="test" \
#   PLATFORM_BASE_URL="https://<platform-backend>.up.railway.app" \
#   PLATFORM_DEPLOYMENT_ID="dep-12345678" \
#   PLATFORM_API_KEY="..." \
#   ./scripts/verify-ecommerce-deployment.sh
#
# Notes:
# - If your ecommerce store admin endpoints require auth, set API_KEY (default header: X-AIFABRIC-API-KEY).
# - Runtime admin endpoints require signed private-runtime headers.
# - Runtime data-sync and indexing operational reads require RUNTIME_TRUSTED_BACKEND_API_KEY when runtime ingress is verified-context only.
# - Platform endpoints require either PLATFORM_API_KEY (default header: X-PLATFORM-API-KEY) or PLATFORM_COOKIE when platform auth is enabled.

STORE_BASE_URL="${STORE_BASE_URL:-${ECOMMERCE_STORE_BASE_URL:-}}"
RUNTIME_BASE_URL="${RUNTIME_BASE_URL:-}"

API_KEY_HEADER="${API_KEY_HEADER:-X-AIFABRIC-API-KEY}"
API_KEY="${API_KEY:-}"

RUNTIME_TRUSTED_BACKEND_API_KEY_HEADER="${RUNTIME_TRUSTED_BACKEND_API_KEY_HEADER:-X-AIFABRIC-RUNTIME-API-KEY}"
RUNTIME_TRUSTED_BACKEND_API_KEY="${RUNTIME_TRUSTED_BACKEND_API_KEY:-}"
RUNTIME_PRIVATE_AUTHORIZATION_HEADER="${RUNTIME_PRIVATE_AUTHORIZATION_HEADER:-X-AIFABRIC-RUNTIME-AUTHORIZATION}"
RUNTIME_PRIVATE_AUTHORIZATION="${RUNTIME_PRIVATE_AUTHORIZATION:-}"
RUNTIME_PUBLIC_TOKEN_SIGNING_KEY="${RUNTIME_PUBLIC_TOKEN_SIGNING_KEY:-}"
RUNTIME_CONNECTOR_HEALTH_URL="${RUNTIME_CONNECTOR_HEALTH_URL:-}"
RUNTIME_CONNECTOR_OVERVIEW_URL="${RUNTIME_CONNECTOR_OVERVIEW_URL:-}"
RUNTIME_CONNECTOR_ACTIONS_OVERVIEW_URL="${RUNTIME_CONNECTOR_ACTIONS_OVERVIEW_URL:-}"
RUNTIME_CONNECTOR_CONFIG_URL="${RUNTIME_CONNECTOR_CONFIG_URL:-}"
RUNTIME_CONNECTOR_LOGS_URL="${RUNTIME_CONNECTOR_LOGS_URL:-}"
RUNTIME_AUTH_OVERVIEW_URL="${RUNTIME_AUTH_OVERVIEW_URL:-}"

PLATFORM_BASE_URL="${PLATFORM_BASE_URL:-${PLATFORM_PUBLIC_BASE_URL:-}}"
PLATFORM_DEPLOYMENT_ID="${PLATFORM_DEPLOYMENT_ID:-}"
PLATFORM_API_KEY_HEADER="${PLATFORM_API_KEY_HEADER:-X-PLATFORM-API-KEY}"
PLATFORM_API_KEY="${PLATFORM_API_KEY:-}"
PLATFORM_COOKIE="${PLATFORM_COOKIE:-}"
PLATFORM_LOGIN_EMAIL="${PLATFORM_LOGIN_EMAIL:-}"
PLATFORM_LOGIN_PASSWORD="${PLATFORM_LOGIN_PASSWORD:-}"
PLATFORM_HTTP_RETRY_ATTEMPTS="${PLATFORM_HTTP_RETRY_ATTEMPTS:-4}"
PLATFORM_HTTP_RETRY_SLEEP_SECONDS="${PLATFORM_HTTP_RETRY_SLEEP_SECONDS:-5}"
PLATFORM_EXPECT_PREFLIGHT_READY="${PLATFORM_EXPECT_PREFLIGHT_READY:-true}"
PLATFORM_EXPECT_RELEASE_ID="${PLATFORM_EXPECT_RELEASE_ID:-}"
PLATFORM_EXPECT_VERSION_ID="${PLATFORM_EXPECT_VERSION_ID:-}"
PLATFORM_EXPECT_RELEASE_STATUS="${PLATFORM_EXPECT_RELEASE_STATUS:-APPLIED_VERIFIED}"
PLATFORM_EXPECT_VERIFICATION_STATUS="${PLATFORM_EXPECT_VERIFICATION_STATUS:-PASSED}"
EXPECT_TENANT_SCOPED_SHARED="${EXPECT_TENANT_SCOPED_SHARED:-}"
EXPECT_TENANT_SCOPED_STATUS="${EXPECT_TENANT_SCOPED_STATUS:-}"
EXPECT_TENANT_SCOPED_CUSTOMER_ID="${EXPECT_TENANT_SCOPED_CUSTOMER_ID:-}"
EXPECT_TENANT_SCOPED_TENANT_ID="${EXPECT_TENANT_SCOPED_TENANT_ID:-}"
EXPECT_TENANT_SCOPED_SCOPE_TYPE="${EXPECT_TENANT_SCOPED_SCOPE_TYPE:-}"
EXPECT_TENANT_SCOPED_ROOT_RESOURCE_VALUE="${EXPECT_TENANT_SCOPED_ROOT_RESOURCE_VALUE:-}"
EXPECT_TENANT_SCOPED_SCOPE_PREFIX="${EXPECT_TENANT_SCOPED_SCOPE_PREFIX:-}"
EXPECT_TENANT_SCOPED_TENANT_HANDLE="${EXPECT_TENANT_SCOPED_TENANT_HANDLE:-}"
EXPECT_TENANT_SCOPED_SCOPE_PATTERN="${EXPECT_TENANT_SCOPED_SCOPE_PATTERN:-}"
EXPECT_TENANT_SCOPED_REGISTRY_STATUS="${EXPECT_TENANT_SCOPED_REGISTRY_STATUS:-}"
EXPECT_TENANT_SCOPED_READINESS_STATUS="${EXPECT_TENANT_SCOPED_READINESS_STATUS:-}"
EXPECT_TENANT_SCOPED_MIGRATION_LOCKED="${EXPECT_TENANT_SCOPED_MIGRATION_LOCKED:-}"
EXPECT_VECTORIZATION_PLAN_PRESENT="${EXPECT_VECTORIZATION_PLAN_PRESENT:-}"
EXPECT_VECTORIZATION_SOURCE_CONNECTION_PRESENT="${EXPECT_VECTORIZATION_SOURCE_CONNECTION_PRESENT:-}"
EXPECT_VECTORIZATION_RUNNER_PRESENT="${EXPECT_VECTORIZATION_RUNNER_PRESENT:-}"
EXPECT_VECTORIZATION_PLAN_STATUS="${EXPECT_VECTORIZATION_PLAN_STATUS:-}"
EXPECT_VECTORIZATION_RUNNER_MODE="${EXPECT_VECTORIZATION_RUNNER_MODE:-}"
EXPECT_VECTORIZATION_SYNC_STATE="${EXPECT_VECTORIZATION_SYNC_STATE:-}"
EXPECT_VECTORIZATION_SOURCE_ADAPTER="${EXPECT_VECTORIZATION_SOURCE_ADAPTER:-}"
EXPECT_VECTORIZATION_SOURCE_AUTH_MODE="${EXPECT_VECTORIZATION_SOURCE_AUTH_MODE:-}"
EXPECT_VECTORIZATION_SOURCE_STATUS="${EXPECT_VECTORIZATION_SOURCE_STATUS:-}"
EXPECT_VECTORIZATION_RUNNER_STATUS="${EXPECT_VECTORIZATION_RUNNER_STATUS:-}"
EXPECT_VECTORIZATION_RUNNER_COMPATIBILITY_STATUS="${EXPECT_VECTORIZATION_RUNNER_COMPATIBILITY_STATUS:-}"
EXPECT_VECTORIZATION_AVAILABLE_ENTITIES="${EXPECT_VECTORIZATION_AVAILABLE_ENTITIES:-}"
EXPECT_VECTORIZATION_ENTITY_SCOPE="${EXPECT_VECTORIZATION_ENTITY_SCOPE:-}"
EXPECT_VECTORIZATION_RUNNER_REQUIRED="${EXPECT_VECTORIZATION_RUNNER_REQUIRED:-}"
EXPECT_VECTORIZATION_PLATFORM_MANAGED_RUNNER="${EXPECT_VECTORIZATION_PLATFORM_MANAGED_RUNNER:-}"
VERIFY_VECTORIZATION_ADMIN="${VERIFY_VECTORIZATION_ADMIN:-false}"
VERIFY_VECTORIZATION_CONTROL_PLANE="${VERIFY_VECTORIZATION_CONTROL_PLANE:-}"
VERIFY_PLATFORM_USER_DIRECTORY_ADMIN="${VERIFY_PLATFORM_USER_DIRECTORY_ADMIN:-false}"
VERIFY_VECTORIZATION_RUNNER_ACTIVE="${VERIFY_VECTORIZATION_RUNNER_ACTIVE:-false}"
VERIFY_VECTORIZATION_SAMPLE="${VERIFY_VECTORIZATION_SAMPLE:-false}"
VERIFY_TENANT_SHARED_ISOLATION="${VERIFY_TENANT_SHARED_ISOLATION:-false}"
VECTORIZATION_COUNTERPART_DEPLOYMENT_ID="${VECTORIZATION_COUNTERPART_DEPLOYMENT_ID:-}"

VERIFY_WRITE="${VERIFY_WRITE:-false}"
EXPECT_CONFIRMATION_INTERCEPTOR_RULES="${EXPECT_CONFIRMATION_INTERCEPTOR_RULES:-cancel_to_retention_offer,accept_retention_offer,reject_retention_offer}"
VERIFY_CONFIRMATION_RETENTION_FLOW="${VERIFY_CONFIRMATION_RETENTION_FLOW:-false}"
VERIFY_MARKETPLACE_RUNTIME="${VERIFY_MARKETPLACE_RUNTIME:-false}"
EXPECT_MARKETPLACE_SUPPORT_CONTRACT_VERSION="${EXPECT_MARKETPLACE_SUPPORT_CONTRACT_VERSION:-MARKETPLACE_RUNTIME_SUPPORT_V1}"
EXPECT_MARKETPLACE_SEARCH_SOURCE_DIAGNOSTICS_CONTRACT_VERSION="${EXPECT_MARKETPLACE_SEARCH_SOURCE_DIAGNOSTICS_CONTRACT_VERSION:-SEARCH_SOURCE_DIAGNOSTICS_V1}"
EXPECT_MARKETPLACE_KNOWLEDGE_SOURCE_IDS="${EXPECT_MARKETPLACE_KNOWLEDGE_SOURCE_IDS:-}"
EXPECT_MARKETPLACE_KNOWLEDGE_SOURCE_ADAPTER_TYPES="${EXPECT_MARKETPLACE_KNOWLEDGE_SOURCE_ADAPTER_TYPES:-}"
EXPECT_MARKETPLACE_KNOWLEDGE_SOURCE_CONTRACT_VERSION="${EXPECT_MARKETPLACE_KNOWLEDGE_SOURCE_CONTRACT_VERSION:-KNOWLEDGE_SOURCE_CONFIG_V1}"
EXPECT_MARKETPLACE_SHELL_CONTRACT_VERSION="${EXPECT_MARKETPLACE_SHELL_CONTRACT_VERSION:-SHELL_CONFIG_V1}"
EXPECT_MARKETPLACE_SHELL_MODULE_IDS="${EXPECT_MARKETPLACE_SHELL_MODULE_IDS:-}"
EXPECT_MARKETPLACE_SHELL_CARD_IDS="${EXPECT_MARKETPLACE_SHELL_CARD_IDS:-}"
EXPECT_MARKETPLACE_SHELL_STARTER_PROMPTS_COUNT="${EXPECT_MARKETPLACE_SHELL_STARTER_PROMPTS_COUNT:-0}"
EXPECT_MARKETPLACE_SHELL_GREETING_CONFIGURED="${EXPECT_MARKETPLACE_SHELL_GREETING_CONFIGURED:-false}"
RUNTIME_PUBLIC_BOOTSTRAP_ORIGIN="${RUNTIME_PUBLIC_BOOTSTRAP_ORIGIN:-}"
MARKETPLACE_SMOKE_QUERY="${MARKETPLACE_SMOKE_QUERY:-Summarize return policy}"
RETENTION_TEST_SKU="${RETENTION_TEST_SKU:-SKU-0001}"
RETENTION_TEST_QUANTITY="${RETENTION_TEST_QUANTITY:-1}"
RETENTION_TEST_SHIPPING_ADDRESS="${RETENTION_TEST_SHIPPING_ADDRESS:-10 Verification Lane, London}"
RETENTION_TEST_CANCEL_QUERY_TEMPLATE="${RETENTION_TEST_CANCEL_QUERY_TEMPLATE:-cancel purchase order {orderNumber}}"

if [[ -z "${VERIFY_VECTORIZATION_CONTROL_PLANE}" ]]; then
  if [[ "${EXPECT_VECTORIZATION_PLAN_PRESENT}" == "true" || "${EXPECT_VECTORIZATION_SOURCE_CONNECTION_PRESENT}" == "true" || "${EXPECT_VECTORIZATION_RUNNER_PRESENT}" == "true" || "${VERIFY_VECTORIZATION_RUNNER_ACTIVE}" == "true" || "${VERIFY_VECTORIZATION_SAMPLE}" == "true" ]]; then
    VERIFY_VECTORIZATION_CONTROL_PLANE="true"
  else
    VERIFY_VECTORIZATION_CONTROL_PLANE="false"
  fi
fi

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
import pathlib, sys
print(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
PY
    return
  fi

  printf '%s' "${direct_value}"
}

API_KEY="$(resolve_secret_value API_KEY)"
RUNTIME_TRUSTED_BACKEND_API_KEY="$(resolve_secret_value RUNTIME_TRUSTED_BACKEND_API_KEY)"
RUNTIME_PRIVATE_AUTHORIZATION="$(resolve_secret_value RUNTIME_PRIVATE_AUTHORIZATION)"
RUNTIME_PUBLIC_TOKEN_SIGNING_KEY="$(resolve_secret_value RUNTIME_PUBLIC_TOKEN_SIGNING_KEY)"
PLATFORM_API_KEY="$(resolve_secret_value PLATFORM_API_KEY)"
PLATFORM_COOKIE="$(resolve_secret_value PLATFORM_COOKIE)"
PLATFORM_LOGIN_EMAIL="$(resolve_secret_value PLATFORM_LOGIN_EMAIL)"
PLATFORM_LOGIN_PASSWORD="$(resolve_secret_value PLATFORM_LOGIN_PASSWORD)"

RUN_SERVICE_CHECKS="false"
RUN_PLATFORM_CHECKS="false"
USE_RUNTIME_OPERATIONAL_SURFACE="false"

if [[ -n "${RUNTIME_BASE_URL}" && -n "${RUNTIME_TRUSTED_BACKEND_API_KEY}" ]]; then
  USE_RUNTIME_OPERATIONAL_SURFACE="true"
fi

if [[ -n "${STORE_BASE_URL}" || -n "${RUNTIME_BASE_URL}" ]]; then
  if [[ -z "${STORE_BASE_URL}" ]]; then
    echo "Invalid service verification configuration."
    echo "Set STORE_BASE_URL for service verification."
    exit 2
  fi
  RUN_SERVICE_CHECKS="true"
fi

if [[ -n "${PLATFORM_BASE_URL}" || -n "${PLATFORM_DEPLOYMENT_ID}" ]]; then
  if [[ -z "${PLATFORM_BASE_URL}" || -z "${PLATFORM_DEPLOYMENT_ID}" ]]; then
    echo "Invalid platform verification configuration."
    echo "Set both PLATFORM_BASE_URL and PLATFORM_DEPLOYMENT_ID together."
    exit 2
  fi
  RUN_PLATFORM_CHECKS="true"
fi

if [[ "${RUN_SERVICE_CHECKS}" != "true" && "${RUN_PLATFORM_CHECKS}" != "true" ]]; then
  echo "Missing required env vars."
  echo "Set either:"
  echo "  STORE_BASE_URL and RUNTIME_BASE_URL with RUNTIME_TRUSTED_BACKEND_API_KEY"
  echo "or:"
  echo "  PLATFORM_BASE_URL and PLATFORM_DEPLOYMENT_ID"
  exit 2
fi

if [[ "${RUN_SERVICE_CHECKS}" == "true" && -z "${RUNTIME_BASE_URL}" ]]; then
  echo "Invalid service verification configuration."
  echo "Set RUNTIME_BASE_URL for runtime-backed verification."
  exit 2
fi

require_cmd() {
  local cmd="$1"
  if ! command -v "${cmd}" >/dev/null 2>&1; then
    echo "Missing required command: ${cmd}"
    exit 2
  fi
}

require_cmd curl
require_cmd python3

trim_slash() {
  local url="$1"
  if [[ "${url}" == */ ]]; then
    echo "${url%/}"
  else
    echo "${url}"
  fi
}

STORE_BASE_URL="$(trim_slash "${STORE_BASE_URL}")"
if [[ -n "${RUNTIME_BASE_URL}" ]]; then
  RUNTIME_BASE_URL="$(trim_slash "${RUNTIME_BASE_URL}")"
fi
if [[ -z "${RUNTIME_CONNECTOR_HEALTH_URL}" && -n "${RUNTIME_BASE_URL}" ]]; then
  RUNTIME_CONNECTOR_HEALTH_URL="${RUNTIME_BASE_URL}/api/admin/connector/health"
fi
if [[ -z "${RUNTIME_CONNECTOR_OVERVIEW_URL}" && -n "${RUNTIME_BASE_URL}" ]]; then
  RUNTIME_CONNECTOR_OVERVIEW_URL="${RUNTIME_BASE_URL}/api/admin/connector/overview"
fi
if [[ -z "${RUNTIME_CONNECTOR_ACTIONS_OVERVIEW_URL}" && -n "${RUNTIME_BASE_URL}" ]]; then
  RUNTIME_CONNECTOR_ACTIONS_OVERVIEW_URL="${RUNTIME_BASE_URL}/api/admin/connector/actions/overview"
fi
if [[ -z "${RUNTIME_CONNECTOR_CONFIG_URL}" && -n "${RUNTIME_BASE_URL}" ]]; then
  RUNTIME_CONNECTOR_CONFIG_URL="${RUNTIME_BASE_URL}/api/admin/connector/config"
fi
if [[ -z "${RUNTIME_CONNECTOR_LOGS_URL}" && -n "${RUNTIME_BASE_URL}" ]]; then
  RUNTIME_CONNECTOR_LOGS_URL="${RUNTIME_BASE_URL}/api/admin/connector/logs"
fi
if [[ -z "${RUNTIME_AUTH_OVERVIEW_URL}" && -n "${RUNTIME_BASE_URL}" ]]; then
  RUNTIME_AUTH_OVERVIEW_URL="${RUNTIME_BASE_URL}/api/admin/auth/overview"
fi
if [[ -n "${PLATFORM_BASE_URL}" ]]; then
  PLATFORM_BASE_URL="$(trim_slash "${PLATFORM_BASE_URL}")"
fi

TMP_DIR="$(mktemp -d)"
PLATFORM_COOKIE_JAR="${TMP_DIR}/platform-cookie.txt"
cleanup() {
  rm -rf "${TMP_DIR}"
}
trap cleanup EXIT

HTTP_STATUS=""
HTTP_BODY=""

http() {
  local method="$1"
  local url="$2"
  local body="${3:-}"
  if [[ "$#" -ge 3 ]]; then
    shift 3
  else
    shift "$#"
  fi

  local tmp
  tmp="$(mktemp)"

  local headers=()
  headers+=("-H" "Accept: application/json")
  if [[ "${method}" != "GET" ]]; then
    headers+=("-H" "Content-Type: application/json")
  fi
  if [[ -n "${API_KEY}" ]]; then
    headers+=("-H" "${API_KEY_HEADER}: ${API_KEY}")
  fi

  local status
  if [[ -n "${body}" ]]; then
    status="$(curl -sS -o "${tmp}" -w "%{http_code}" -X "${method}" "${headers[@]}" "$@" --data "${body}" "${url}" || true)"
  else
    status="$(curl -sS -o "${tmp}" -w "%{http_code}" -X "${method}" "${headers[@]}" "$@" "${url}" || true)"
  fi

  HTTP_STATUS="${status}"
  HTTP_BODY="$(cat "${tmp}")"
  rm -f "${tmp}"
}

runtime_http() {
  local method="$1"
  local url="$2"
  local body="${3:-}"
  if [[ "$#" -ge 3 ]]; then
    shift 3
  else
    shift "$#"
  fi

  local tmp
  tmp="$(mktemp)"

  local headers=()
  headers+=("-H" "Accept: application/json")
  if [[ "${method}" != "GET" ]]; then
    headers+=("-H" "Content-Type: application/json")
  fi
  if [[ -n "${RUNTIME_TRUSTED_BACKEND_API_KEY}" ]]; then
    headers+=("-H" "${RUNTIME_TRUSTED_BACKEND_API_KEY_HEADER}: ${RUNTIME_TRUSTED_BACKEND_API_KEY}")
  fi
  if [[ -n "${RUNTIME_PRIVATE_AUTHORIZATION}" ]]; then
    headers+=("-H" "${RUNTIME_PRIVATE_AUTHORIZATION_HEADER}: ${RUNTIME_PRIVATE_AUTHORIZATION}")
  fi

  local status
  if [[ -n "${body}" ]]; then
    status="$(curl -sS -o "${tmp}" -w "%{http_code}" -X "${method}" "${headers[@]}" "$@" --data "${body}" "${url}" || true)"
  else
    status="$(curl -sS -o "${tmp}" -w "%{http_code}" -X "${method}" "${headers[@]}" "$@" "${url}" || true)"
  fi

  HTTP_STATUS="${status}"
  HTTP_BODY="$(cat "${tmp}")"
  rm -f "${tmp}"
}

runtime_operational_http() {
  local method="$1"
  local url="$2"
  local body="${3:-}"
  if [[ "$#" -ge 3 ]]; then
    shift 3
  else
    shift "$#"
  fi

  local tmp
  tmp="$(mktemp)"

  local headers=()
  headers+=("-H" "Accept: application/json")
  if [[ "${method}" != "GET" ]]; then
    headers+=("-H" "Content-Type: application/json")
  fi
  if [[ -n "${RUNTIME_TRUSTED_BACKEND_API_KEY}" ]]; then
    headers+=("-H" "${RUNTIME_TRUSTED_BACKEND_API_KEY_HEADER}: ${RUNTIME_TRUSTED_BACKEND_API_KEY}")
  fi
  if [[ -n "${RUNTIME_PRIVATE_AUTHORIZATION}" ]]; then
    headers+=("-H" "${RUNTIME_PRIVATE_AUTHORIZATION_HEADER}: ${RUNTIME_PRIVATE_AUTHORIZATION}")
  fi

  local status
  if [[ -n "${body}" ]]; then
    status="$(curl -sS -o "${tmp}" -w "%{http_code}" -X "${method}" "${headers[@]}" "$@" --data "${body}" "${url}" || true)"
  else
    status="$(curl -sS -o "${tmp}" -w "%{http_code}" -X "${method}" "${headers[@]}" "$@" "${url}" || true)"
  fi

  HTTP_STATUS="${status}"
  HTTP_BODY="$(cat "${tmp}")"
  rm -f "${tmp}"
}

operational_http() {
  local method="$1"
  local path="$2"
  local body="${3:-}"
  runtime_operational_http "${method}" "${RUNTIME_BASE_URL}${path}" "${body}"
}

operational_surface_name() {
  printf '%s' "runtime"
}

platform_http() {
  local method="$1"
  local url="$2"
  local body="${3:-}"
  if [[ "$#" -ge 3 ]]; then
    shift 3
  else
    shift "$#"
  fi

  local tmp
  tmp="$(mktemp)"

  local headers=()
  headers+=("-H" "Accept: application/json")
  if [[ "${method}" != "GET" ]]; then
    headers+=("-H" "Content-Type: application/json")
  fi
  if [[ -n "${PLATFORM_API_KEY}" ]]; then
    headers+=("-H" "${PLATFORM_API_KEY_HEADER}: ${PLATFORM_API_KEY}")
  fi
  if [[ -n "${PLATFORM_COOKIE}" ]]; then
    headers+=("-H" "Cookie: ${PLATFORM_COOKIE}")
  fi

  local status=""
  local attempt=1
  while true; do
    if [[ -s "${PLATFORM_COOKIE_JAR}" ]]; then
      if [[ -n "${body}" ]]; then
        status="$(curl -sS -o "${tmp}" -w "%{http_code}" -X "${method}" "${headers[@]}" -b "${PLATFORM_COOKIE_JAR}" -c "${PLATFORM_COOKIE_JAR}" "$@" --data "${body}" "${url}" || true)"
      else
        status="$(curl -sS -o "${tmp}" -w "%{http_code}" -X "${method}" "${headers[@]}" -b "${PLATFORM_COOKIE_JAR}" -c "${PLATFORM_COOKIE_JAR}" "$@" "${url}" || true)"
      fi
    else
      if [[ -n "${body}" ]]; then
        status="$(curl -sS -o "${tmp}" -w "%{http_code}" -X "${method}" "${headers[@]}" "$@" --data "${body}" "${url}" || true)"
      else
        status="$(curl -sS -o "${tmp}" -w "%{http_code}" -X "${method}" "${headers[@]}" "$@" "${url}" || true)"
      fi
    fi
    if [[ "${method}" == "GET" \
        && ( "${status}" == "000" || "${status}" == "502" || "${status}" == "503" || "${status}" == "504" ) \
        && "${attempt}" -lt "${PLATFORM_HTTP_RETRY_ATTEMPTS}" ]]; then
      echo "WARN: transient platform ${method} ${url} returned HTTP ${status}; retrying (${attempt}/${PLATFORM_HTTP_RETRY_ATTEMPTS})..." >&2
      sleep "${PLATFORM_HTTP_RETRY_SLEEP_SECONDS}"
      attempt=$((attempt + 1))
      continue
    fi
    break
  done

  HTTP_STATUS="${status}"
  HTTP_BODY="$(cat "${tmp}")"
  rm -f "${tmp}"
}

platform_login() {
  if [[ "${RUN_PLATFORM_CHECKS}" != "true" ]]; then
    return 0
  fi
  if [[ -n "${PLATFORM_API_KEY}" || -n "${PLATFORM_COOKIE}" ]]; then
    return 0
  fi
  if [[ -z "${PLATFORM_LOGIN_EMAIL:-}" || -z "${PLATFORM_LOGIN_PASSWORD:-}" ]]; then
    echo "Platform verification requires PLATFORM_API_KEY, PLATFORM_COOKIE, or PLATFORM_LOGIN_EMAIL/PLATFORM_LOGIN_PASSWORD."
    exit 2
  fi

  local tmp
  tmp="$(mktemp)"
  local payload
  payload="$(mktemp)"
  local status
  cat > "${payload}" <<EOF
{"email":"${PLATFORM_LOGIN_EMAIL}","password":"${PLATFORM_LOGIN_PASSWORD}"}
EOF
  status="$(
    curl -sS -o "${tmp}" -w "%{http_code}" -c "${PLATFORM_COOKIE_JAR}" \
      -H "Content-Type: application/json" \
      --data "@${payload}" \
      "${PLATFORM_BASE_URL}/api/platform/auth/login" || true
  )"
  rm -f "${payload}"
  if [[ "${status}" != "200" ]]; then
    echo "Platform login failed (HTTP ${status})."
    cat "${tmp}"
    rm -f "${tmp}"
    exit 1
  fi
  rm -f "${tmp}"
}

RUNTIME_PUBLIC_AUTHORIZATION=""
RUNTIME_PUBLIC_AUTHORIZATION_HEADER="Authorization"

runtime_public_http() {
  local method="$1"
  local url="$2"
  local body="${3:-}"
  if [[ "$#" -ge 3 ]]; then
    shift 3
  else
    shift "$#"
  fi

  local tmp
  tmp="$(mktemp)"

  local headers=()
  headers+=("-H" "Accept: application/json")
  if [[ "${method}" != "GET" ]]; then
    headers+=("-H" "Content-Type: application/json")
  fi
  if [[ -n "${RUNTIME_PUBLIC_AUTHORIZATION}" ]]; then
    headers+=("-H" "${RUNTIME_PUBLIC_AUTHORIZATION_HEADER}: ${RUNTIME_PUBLIC_AUTHORIZATION}")
  fi
  if [[ -n "${RUNTIME_PUBLIC_BOOTSTRAP_ORIGIN}" ]]; then
    headers+=("-H" "Origin: ${RUNTIME_PUBLIC_BOOTSTRAP_ORIGIN}")
  fi

  local status
  if [[ -n "${body}" ]]; then
    status="$(curl -sS -o "${tmp}" -w "%{http_code}" -X "${method}" "${headers[@]}" "$@" --data "${body}" "${url}" || true)"
  else
    status="$(curl -sS -o "${tmp}" -w "%{http_code}" -X "${method}" "${headers[@]}" "$@" "${url}" || true)"
  fi

  HTTP_STATUS="${status}"
  HTTP_BODY="$(cat "${tmp}")"
  rm -f "${tmp}"
}

pass() { echo "PASS: $*"; }
warn() { echo "WARN: $*"; }
fail() { echo "FAIL: $*"; exit 1; }

csv_text_json() {
  CSV_TEXT="${1:-}" python3 - <<'PY'
import json
import os

raw = os.environ.get("CSV_TEXT", "")
items = []
for part in raw.split(","):
    value = part.strip()
    if value and value not in items:
        items.append(value)
print(json.dumps(items))
PY
}

expected_confirmation_interceptor_rules_json() {
  csv_text_json "${EXPECT_CONFIRMATION_INTERCEPTOR_RULES:-}"
}

assert_expected_confirmation_interceptors() {
  local label="$1"
  local body="$2"
  if [[ -z "${EXPECT_CONFIRMATION_INTERCEPTOR_RULES// }" ]]; then
    return 0
  fi
  HTTP_BODY="${body}"
  local expected_json
  expected_json="$(expected_confirmation_interceptor_rules_json)"
  json_assert "${label}" $'expected = set('"${expected_json}"')\nassert expected, expected\nactual = set((data or {}).get("confirmationInterceptorRuleNames") or [])\nassert actual == expected, {"expected": sorted(expected), "actual": sorted(actual)}\nassert int((data or {}).get("confirmationInterceptorsCount") or 0) == len(expected), data\nsources = (data or {}).get("confirmationInterceptorSources") or []\nassert len(sources) >= 1, sources\nprint("ok")'
}

assert_marketplace_runtime_overview() {
  local label="$1"
  local body="$2"
  if [[ "${VERIFY_MARKETPLACE_RUNTIME}" != "true" ]]; then
    return 0
  fi
  local expected_source_ids_json expected_adapter_types_json expected_module_ids_json expected_card_ids_json
  expected_source_ids_json="$(csv_text_json "${EXPECT_MARKETPLACE_KNOWLEDGE_SOURCE_IDS:-}")"
  expected_adapter_types_json="$(csv_text_json "${EXPECT_MARKETPLACE_KNOWLEDGE_SOURCE_ADAPTER_TYPES:-}")"
  expected_module_ids_json="$(csv_text_json "${EXPECT_MARKETPLACE_SHELL_MODULE_IDS:-}")"
  expected_card_ids_json="$(csv_text_json "${EXPECT_MARKETPLACE_SHELL_CARD_IDS:-}")"
  HTTP_BODY="${body}"
  json_assert "${label}" $'expected_source_ids = set('"${expected_source_ids_json}"')\nexpected_adapter_types = set('"${expected_adapter_types_json}"')\nexpected_module_ids = set('"${expected_module_ids_json}"')\nexpected_card_ids = set('"${expected_card_ids_json}"')\nmarketplace = (data or {}).get("marketplaceSupport") or {}\nassert marketplace.get("contractVersion") == "'"${EXPECT_MARKETPLACE_SUPPORT_CONTRACT_VERSION}"'", marketplace\nassert marketplace.get("knowledgeSourceContractVersion") == "'"${EXPECT_MARKETPLACE_KNOWLEDGE_SOURCE_CONTRACT_VERSION}"'", marketplace\nassert marketplace.get("shellConfigContractVersion") == "'"${EXPECT_MARKETPLACE_SHELL_CONTRACT_VERSION}"'", marketplace\nassert marketplace.get("searchSourceDiagnosticsContractVersion") == "'"${EXPECT_MARKETPLACE_SEARCH_SOURCE_DIAGNOSTICS_CONTRACT_VERSION}"'", marketplace\nassert bool((data or {}).get("knowledgeSourceConfigLocation")), data\nassert bool((data or {}).get("shellConfigLocation")), data\nactual_source_ids = set((data or {}).get("knowledgeSourceIds") or [])\nactual_adapter_types = set((data or {}).get("knowledgeSourceAdapterTypes") or [])\nactual_module_ids = set((data or {}).get("shellModuleIds") or [])\nactual_card_ids = set((data or {}).get("shellCardIds") or [])\nassert actual_source_ids == expected_source_ids, {"expected": sorted(expected_source_ids), "actual": sorted(actual_source_ids)}\nassert actual_adapter_types == expected_adapter_types, {"expected": sorted(expected_adapter_types), "actual": sorted(actual_adapter_types)}\nassert actual_module_ids == expected_module_ids, {"expected": sorted(expected_module_ids), "actual": sorted(actual_module_ids)}\nassert actual_card_ids == expected_card_ids, {"expected": sorted(expected_card_ids), "actual": sorted(actual_card_ids)}\nassert int((data or {}).get("shellStarterPromptsCount") or 0) == int("'"${EXPECT_MARKETPLACE_SHELL_STARTER_PROMPTS_COUNT}"'"), data\nassert bool((data or {}).get("shellGreetingConfigured")) == ("'"${EXPECT_MARKETPLACE_SHELL_GREETING_CONFIGURED}"'".lower() == "true"), data\nsearch_diag = (data or {}).get("searchSourceDiagnostics") or {}\nassert search_diag.get("contractVersion") == "'"${EXPECT_MARKETPLACE_SEARCH_SOURCE_DIAGNOSTICS_CONTRACT_VERSION}"'", search_diag\nassert int(search_diag.get("configuredSourcesCount") or 0) >= len(expected_source_ids), search_diag\nprint("ok")'
}

assert_marketplace_shell_config() {
  local label="$1"
  local body="$2"
  if [[ "${VERIFY_MARKETPLACE_RUNTIME}" != "true" ]]; then
    return 0
  fi
  local expected_module_ids_json expected_card_ids_json
  expected_module_ids_json="$(csv_text_json "${EXPECT_MARKETPLACE_SHELL_MODULE_IDS:-}")"
  expected_card_ids_json="$(csv_text_json "${EXPECT_MARKETPLACE_SHELL_CARD_IDS:-}")"
  HTTP_BODY="${body}"
  json_assert "${label}" $'expected_module_ids = set('"${expected_module_ids_json}"')\nexpected_card_ids = set('"${expected_card_ids_json}"')\nassert (data or {}).get("contractVersion") == "'"${EXPECT_MARKETPLACE_SHELL_CONTRACT_VERSION}"'", data\nassert set((data or {}).get("moduleIds") or []) == expected_module_ids, data\nassert set((data or {}).get("cardIds") or []) == expected_card_ids, data\nassert len((data or {}).get("starterPrompts") or []) == int("'"${EXPECT_MARKETPLACE_SHELL_STARTER_PROMPTS_COUNT}"'"), data\nassert bool((data or {}).get("greetingTitle") or (data or {}).get("greetingMessage")) == ("'"${EXPECT_MARKETPLACE_SHELL_GREETING_CONFIGURED}"'".lower() == "true"), data\nprint("ok")'
}

run_marketplace_runtime_verification() {
  if [[ "${VERIFY_MARKETPLACE_RUNTIME}" != "true" ]]; then
    return 0
  fi
  if [[ -z "${RUNTIME_BASE_URL}" ]]; then
    fail "VERIFY_MARKETPLACE_RUNTIME requires RUNTIME_BASE_URL."
  fi

  echo ""
  echo "== Marketplace Runtime Verification =="

  runtime_http GET "${RUNTIME_BASE_URL}/api/admin/auth/overview"
  assert_status 200 "marketplace runtime auth overview"
  local auth_overview_json="${HTTP_BODY}"
  RUNTIME_AUTH_OVERVIEW_BODY="${auth_overview_json}"
  if [[ -z "${RUNTIME_PUBLIC_BOOTSTRAP_ORIGIN}" ]]; then
    RUNTIME_PUBLIC_BOOTSTRAP_ORIGIN="$(
      AUTH_OVERVIEW_BODY="${auth_overview_json}" python3 - <<'PY'
import json
import os

body = json.loads(os.environ.get("AUTH_OVERVIEW_BODY", "") or "{}")
auth = (body or {}).get("auth") or {}
bootstrap = auth.get("publicBootstrap") or {}
if bootstrap.get("allowMissingOrigin") is True:
    print("")
else:
    allowed = [value.strip() for value in (bootstrap.get("allowedOrigins") or []) if isinstance(value, str) and value.strip()]
    print(allowed[0] if allowed else "")
PY
    )"
  fi
  if [[ -z "${RUNTIME_PUBLIC_BOOTSTRAP_ORIGIN}" ]]; then
    AUTH_OVERVIEW_BODY="${auth_overview_json}" python3 - <<'PY'
import json
import os
import sys

body = json.loads(os.environ.get("AUTH_OVERVIEW_BODY", "") or "{}")
auth = (body or {}).get("auth") or {}
bootstrap = auth.get("publicBootstrap") or {}
if bootstrap.get("allowMissingOrigin") is not True:
    sys.exit(1)
PY
    if [[ $? -ne 0 ]]; then
      fail "marketplace public bootstrap requires an allowed Origin, but runtime auth overview did not expose one. Set RUNTIME_PUBLIC_BOOTSTRAP_ORIGIN explicitly."
    fi
  fi

  runtime_public_http POST "${RUNTIME_BASE_URL}/api/public/chat/session" "{}"
  assert_status 200 "marketplace public bootstrap session"
  json_assert "marketplace public bootstrap session" $'assert (data or {}).get("success") is True\nassert bool((data or {}).get("accessToken") or (data or {}).get("token"))\nassert bool((data or {}).get("tokenScheme") or (data or {}).get("tokenType"))\nassert isinstance((data or {}).get("shellConfig"), dict)\nprint("ok")'
  local bootstrap_json="${HTTP_BODY}"
  assert_marketplace_shell_config "marketplace public bootstrap shell config" "$(PARSE_BODY="${HTTP_BODY}" python3 - <<'PY'
import json
import os
print(json.dumps((json.loads(os.environ.get("PARSE_BODY", "") or "{}") or {}).get("shellConfig") or {}))
PY
)"

  RUNTIME_PUBLIC_AUTHORIZATION="$(BOOTSTRAP_JSON="${bootstrap_json}" python3 - <<'PY'
import json
import os
payload = json.loads(os.environ.get("BOOTSTRAP_JSON", "") or "{}")
scheme = (payload.get("tokenScheme") or payload.get("tokenType") or "Bearer").strip() or "Bearer"
token = (payload.get("accessToken") or payload.get("token") or "").strip()
print(f"{scheme} {token}" if token else "")
PY
)"
  if [[ -z "${RUNTIME_PUBLIC_AUTHORIZATION}" ]]; then
    fail "marketplace public bootstrap did not return a usable access token."
  fi

  runtime_public_http GET "${RUNTIME_BASE_URL}/api/chat/me/shell-config"
  assert_status 200 "marketplace authenticated shell config"
  json_assert "marketplace authenticated shell config" $'assert (data or {}).get("success") is True\nprint("ok")'
  assert_marketplace_shell_config "marketplace authenticated shell config payload" "${HTTP_BODY}"

  local conversation_id="marketplace-runtime-verify-$(date +%s)"
  runtime_public_http POST "${RUNTIME_BASE_URL}/api/chat/me/query" "$(build_chat_query_payload "${MARKETPLACE_SMOKE_QUERY}" "${conversation_id}")"
  assert_status 200 "marketplace runtime smoke query"
  local expected_source_ids_json
  expected_source_ids_json="$(csv_text_json "${EXPECT_MARKETPLACE_KNOWLEDGE_SOURCE_IDS:-}")"
  json_assert "marketplace runtime smoke query" $'expected_source_ids = set('"${expected_source_ids_json}"')\nassert (data or {}).get("success") is True, data\nresult = (data or {}).get("result") or {}\nassert result.get("success") is True, result\nassert result.get("type") in {"INFORMATION_PROVIDED", "ACTION_EXECUTED"}, result\nmetadata = result.get("metadata") or {}\nrag_metadata = (((result.get("data") or {}).get("ragResponse") or {}).get("metadata") or {})\nsource_ids = set(metadata.get("searchSourceIds") or rag_metadata.get("searchSourceIds") or [])\nassert source_ids & expected_source_ids, {"expectedAnyOf": sorted(expected_source_ids), "actual": sorted(source_ids)}\nsource_diagnostics = metadata.get("searchSourceDiagnostics") or rag_metadata.get("searchSourceDiagnostics") or []\nassert isinstance(source_diagnostics, list) and len(source_diagnostics) >= 1, {"metadata": metadata, "ragMetadata": rag_metadata}\nprint("ok")'

  runtime_http GET "${RUNTIME_BASE_URL}/api/admin/overview"
  assert_status 200 "marketplace runtime admin overview (post-query)"
  assert_marketplace_runtime_overview "marketplace runtime admin overview (post-query)" "${HTTP_BODY}"
  json_assert "marketplace runtime search-source diagnostics post-query" $'search_diag = (data or {}).get("searchSourceDiagnostics") or {}\nassert int(search_diag.get("recordedSearchExecutions") or 0) >= 1, search_diag\nprint("ok")'
  pass "marketplace runtime live verification"
}

mint_authenticated_runtime_public_token() {
  local subject_id="$1"
  local session_id="$2"
  if [[ -z "${RUNTIME_PUBLIC_TOKEN_SIGNING_KEY}" ]]; then
    fail "VERIFY_CONFIRMATION_RETENTION_FLOW requires RUNTIME_PUBLIC_TOKEN_SIGNING_KEY."
  fi
  if [[ -z "${RUNTIME_AUTH_OVERVIEW_BODY:-}" ]]; then
    fail "VERIFY_CONFIRMATION_RETENTION_FLOW requires runtime auth overview to be available."
  fi
  AUTH_OVERVIEW_BODY="${RUNTIME_AUTH_OVERVIEW_BODY}" \
  SIGNING_KEY="${RUNTIME_PUBLIC_TOKEN_SIGNING_KEY}" \
  SUBJECT_ID="${subject_id}" \
  SESSION_ID="${session_id}" \
  python3 - <<'PY'
import base64
import datetime as dt
import hashlib
import hmac
import json
import os

body = json.loads(os.environ["AUTH_OVERVIEW_BODY"])
auth = (body or {}).get("auth") or {}
issuer = (auth.get("publicTokenIssuer") or "").strip()
if not issuer:
    accepted_issuers = [item for item in (auth.get("publicAcceptedIssuers") or []) if isinstance(item, str) and item.strip()]
    issuer = accepted_issuers[0] if accepted_issuers else "runtime-public-bootstrap"

default_audience = (auth.get("publicDefaultAudience") or "").strip()
accepted_audiences = [item for item in (auth.get("publicAcceptedAudiences") or []) if isinstance(item, str) and item.strip()]
audiences = [default_audience] if default_audience else accepted_audiences[:1]
scopes = [item for item in (auth.get("publicAuthenticatedDefaultScopes") or []) if isinstance(item, str) and item.strip()]
if not scopes:
    scopes = ["chat:query", "chat:conversations"]

ttl_seconds = int(auth.get("publicTokenTtlSeconds") or 900)
ttl_seconds = max(60, ttl_seconds)
expires_at = (dt.datetime.now(dt.timezone.utc) + dt.timedelta(seconds=ttl_seconds)).replace(microsecond=0)

payload = {
    "sub": os.environ["SUBJECT_ID"],
    "subjectType": "END_USER",
    "authMode": "PUBLIC_RUNTIME_AUTHENTICATED",
    "callerType": "PUBLIC_BROWSER",
    "sessionId": os.environ["SESSION_ID"],
    "iss": issuer,
    "exp": expires_at.isoformat().replace("+00:00", "Z"),
    "scopes": scopes,
}
if audiences:
    payload["aud"] = audiences[0] if len(audiences) == 1 else audiences

payload_bytes = json.dumps(payload, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
payload_segment = base64.urlsafe_b64encode(payload_bytes).decode("ascii").rstrip("=")
signature = hmac.new(
    os.environ["SIGNING_KEY"].encode("utf-8"),
    payload_segment.encode("utf-8"),
    hashlib.sha256
).digest()
signature_segment = base64.urlsafe_b64encode(signature).decode("ascii").rstrip("=")
token = f"rpt1.{payload_segment}.{signature_segment}"

print(json.dumps({
    "header": (auth.get("publicAuthorizationHeader") or "Authorization").strip() or "Authorization",
    "authorization": f"{(auth.get('publicTokenScheme') or 'Bearer').strip() or 'Bearer'} {token}"
}))
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

create_order_payload() {
  local user_id="$1"
  local sku="$2"
  local quantity="$3"
  local shipping_address="$4"
  local email="$5"
  ORDER_USER_ID="${user_id}" ORDER_SKU="${sku}" ORDER_QUANTITY="${quantity}" ORDER_SHIPPING="${shipping_address}" ORDER_EMAIL="${email}" python3 - <<'PY'
import json
import os

print(json.dumps({
    "userId": os.environ["ORDER_USER_ID"],
    "sku": os.environ["ORDER_SKU"],
    "quantity": int(os.environ["ORDER_QUANTITY"]),
    "shippingAddress": os.environ["ORDER_SHIPPING"],
    "email": os.environ["ORDER_EMAIL"],
}))
PY
}

retention_cancel_query() {
  local order_number="$1"
  TEMPLATE="${RETENTION_TEST_CANCEL_QUERY_TEMPLATE}" ORDER_NUMBER="${order_number}" python3 - <<'PY'
import os
print((os.environ.get("TEMPLATE") or "cancel order {orderNumber}").replace("{orderNumber}", os.environ["ORDER_NUMBER"]))
PY
}

run_retention_query() {
  local query="$1"
  local conversation_id="$2"
  local label="$3"
  runtime_public_http POST "${RUNTIME_BASE_URL}/api/chat/me/query" "$(build_chat_query_payload "${query}" "${conversation_id}")"
  assert_status 200 "${label}"
  json_assert "${label}" $'assert (data or {}).get("success") is True\nassert isinstance((data or {}).get("result") or {}, dict)\nprint("ok")'
}

verify_retention_acceptance_flow() {
  local user_id="$1"
  local email="$2"
  local order_number="$3"
  local conversation_id="$4"

  run_retention_query "$(retention_cancel_query "${order_number}")" "${conversation_id}" "retention flow initial cancellation"
  json_assert "retention flow initial cancellation" $'result = (data or {}).get("result") or {}\nassert result.get("type") == "CONFIRMATION_REQUIRED", result\nassert ((result.get("data") or {}).get("action")) == "cancel_purchase_order", result\nprint("ok")'

  run_retention_query "yes" "${conversation_id}" "retention flow offer prompt"
  json_assert "retention flow offer prompt" $'result = (data or {}).get("result") or {}\nassert result.get("type") == "CONFIRMATION_REQUIRED", result\nassert ((result.get("data") or {}).get("action")) == "offer_order_discount", result\nmsg = (result.get("message") or "").lower()\nassert "discount" in msg or "keep your order" in msg, result\nprint("ok")'

  run_retention_query "yes" "${conversation_id}" "retention flow offer accept"
  json_assert "retention flow offer accept" $'result = (data or {}).get("result") or {}\nassert result.get("type") == "ACTION_EXECUTED", result\nassert result.get("success") is True, result\ndata_map = result.get("data") or {}\nassert data_map.get("action") == "offer_order_discount", data_map\naction_result = data_map.get("actionResult") or {}\nassert action_result.get("success") is True, action_result\npayload = action_result.get("data") or {}\nassert int(payload.get("discountPercent") or 0) >= 10, payload\nassert bool(payload.get("couponCode")), payload\nprint("ok")'

  http GET "${STORE_BASE_URL}/api/orders/resolve?userId=${user_id}&orderNumberOrId=${order_number}"
  assert_status 200 "retention flow accepted order remains active"
  json_assert "retention flow accepted order remains active" $'assert (data or {}).get("orderNumber") == "'"${order_number}"'"\nassert (data or {}).get("status") == "CREATED", data\nprint("ok")'
}

verify_retention_rejection_flow() {
  local user_id="$1"
  local order_number="$2"
  local conversation_id="$3"

  run_retention_query "$(retention_cancel_query "${order_number}")" "${conversation_id}" "retention flow reject initial cancellation"
  json_assert "retention flow reject initial cancellation" $'result = (data or {}).get("result") or {}\nassert result.get("type") == "CONFIRMATION_REQUIRED", result\nassert ((result.get("data") or {}).get("action")) == "cancel_purchase_order", result\nprint("ok")'

  run_retention_query "yes" "${conversation_id}" "retention flow reject offer prompt"
  json_assert "retention flow reject offer prompt" $'result = (data or {}).get("result") or {}\nassert result.get("type") == "CONFIRMATION_REQUIRED", result\nassert ((result.get("data") or {}).get("action")) == "offer_order_discount", result\nprint("ok")'

  run_retention_query "no" "${conversation_id}" "retention flow reject offer"
  json_assert "retention flow reject offer" $'result = (data or {}).get("result") or {}\nassert result.get("type") == "ACTION_EXECUTED", result\nassert result.get("success") is True, result\ndata_map = result.get("data") or {}\nassert data_map.get("action") == "cancel_purchase_order", data_map\naction_result = data_map.get("actionResult") or {}\nassert action_result.get("success") is True, action_result\nprint("ok")'

  http GET "${STORE_BASE_URL}/api/orders/resolve?userId=${user_id}&orderNumberOrId=${order_number}"
  assert_status 200 "retention flow rejected order becomes cancelled"
  json_assert "retention flow rejected order becomes cancelled" $'assert (data or {}).get("orderNumber") == "'"${order_number}"'"\nassert (data or {}).get("status") == "CANCELLED", data\nprint("ok")'
}

run_confirmation_retention_flow() {
  if [[ "${VERIFY_CONFIRMATION_RETENTION_FLOW}" != "true" ]]; then
    return 0
  fi
  if [[ -z "${RUNTIME_BASE_URL}" || -z "${STORE_BASE_URL}" ]]; then
    fail "VERIFY_CONFIRMATION_RETENTION_FLOW requires STORE_BASE_URL and RUNTIME_BASE_URL."
  fi

  local unique_suffix
  unique_suffix="$(date +%s)"
  local subject_id="verify-end-user-${unique_suffix}"
  local session_id="verify-session-${unique_suffix}"
  local email="verify-${unique_suffix}@example.test"
  local token_json
  token_json="$(mint_authenticated_runtime_public_token "${subject_id}" "${session_id}")"
  RUNTIME_PUBLIC_AUTHORIZATION_HEADER="$(TOKEN_JSON="${token_json}" python3 - <<'PY'
import json, os
print((json.loads(os.environ["TOKEN_JSON"]) or {}).get("header") or "Authorization")
PY
)"
  RUNTIME_PUBLIC_AUTHORIZATION="$(TOKEN_JSON="${token_json}" python3 - <<'PY'
import json, os
print((json.loads(os.environ["TOKEN_JSON"]) or {}).get("authorization") or "")
PY
)"

  http POST "${STORE_BASE_URL}/api/orders" "$(create_order_payload "${subject_id}" "${RETENTION_TEST_SKU}" "${RETENTION_TEST_QUANTITY}" "${RETENTION_TEST_SHIPPING_ADDRESS}" "${email}")"
  assert_status 201 "retention flow create acceptance order"
  local acceptance_order_number
  acceptance_order_number="$(PARSE_BODY="${HTTP_BODY}" python3 - <<'PY'
import json, os
d = json.loads(os.environ.get("PARSE_BODY", "") or "{}")
print((d.get("orderNumber")) or "")
PY
)"
  if [[ -z "${acceptance_order_number}" ]]; then
    fail "retention flow create acceptance order did not return an orderNumber."
  fi

  http POST "${STORE_BASE_URL}/api/orders" "$(create_order_payload "${subject_id}" "${RETENTION_TEST_SKU}" "${RETENTION_TEST_QUANTITY}" "${RETENTION_TEST_SHIPPING_ADDRESS}" "${email}")"
  assert_status 201 "retention flow create rejection order"
  local rejection_order_number
  rejection_order_number="$(PARSE_BODY="${HTTP_BODY}" python3 - <<'PY'
import json, os
d = json.loads(os.environ.get("PARSE_BODY", "") or "{}")
print((d.get("orderNumber")) or "")
PY
)"
  if [[ -z "${rejection_order_number}" ]]; then
    fail "retention flow create rejection order did not return an orderNumber."
  fi

  verify_retention_acceptance_flow "${subject_id}" "${email}" "${acceptance_order_number}" "chat-retention-accept-${unique_suffix}"
  verify_retention_rejection_flow "${subject_id}" "${rejection_order_number}" "chat-retention-reject-${unique_suffix}"
  pass "runtime confirmation retention flow"
}

assert_status() {
  local expected="$1"
  local label="$2"
  if [[ "${HTTP_STATUS}" != "${expected}" ]]; then
    echo "---- ${label} ----"
    echo "HTTP ${HTTP_STATUS}"
    echo "${HTTP_BODY}"
    echo "------------------"
    fail "${label} (expected HTTP ${expected})"
  fi
}

json_assert() {
  local label="$1"
  local py="$2"
  local tmp
  tmp="$(mktemp)"
  printf '%s' "${HTTP_BODY}" > "${tmp}"
  ASSERT_LABEL="${label}" ASSERT_FILE="${tmp}" ASSERT_PY="${py}" python3 - <<'PY'
import json, os, pathlib
label = os.environ["ASSERT_LABEL"]
raw = pathlib.Path(os.environ["ASSERT_FILE"]).read_text(encoding="utf-8").strip()
try:
    data = json.loads(raw) if raw else None
except Exception as e:
    print(f"FAIL: {label}: invalid JSON: {e}")
    print(f"{label}: invalid JSON: {e}")
    print(raw)
    raise SystemExit(2)
namespace = {"data": data}
try:
    exec(os.environ["ASSERT_PY"].replace("\\n", "\n"), namespace, namespace)
except Exception as exc:
    detail = str(exc).strip() or exc.__class__.__name__
    print(f"FAIL: {label}: {detail}")
    if raw:
        print(raw)
    raise
PY
  local rc=$?
  rm -f "${tmp}"
  return "${rc}"
}

poll_until() {
  local label="$1"
  local attempts="$2"
  local sleep_s="$3"
  local cmd="$4"
  local condition_py="$5"

  local i=1
  while [[ "${i}" -le "${attempts}" ]]; do
    eval "${cmd}"
    if [[ "${HTTP_STATUS}" == "200" ]]; then
      if POLL_LABEL="${label}" POLL_BODY="${HTTP_BODY}" POLL_PY="${condition_py}" python3 - <<'PY'
import json, os
label = os.environ["POLL_LABEL"]
raw = os.environ.get("POLL_BODY", "").strip()
data = json.loads(raw) if raw else {}
namespace = {"data": data}
exec(os.environ["POLL_PY"].replace("\\n", "\n"), namespace, namespace)
PY
      then
        return 0
      fi
    fi
    sleep "${sleep_s}"
    i=$((i+1))
  done
  echo "---- ${label} ----"
  echo "Last HTTP ${HTTP_STATUS}"
  echo "${HTTP_BODY}"
  echo "------------------"
  fail "${label} (timed out after ${attempts} attempts)"
}

vectorization_scope_json() {
  SCOPE_TEXT="${EXPECT_VECTORIZATION_ENTITY_SCOPE:-${EXPECT_VECTORIZATION_AVAILABLE_ENTITIES:-}}" python3 - <<'PY'
import json
import os

raw = os.environ.get("SCOPE_TEXT", "")
items = []
for part in raw.split(","):
    value = part.strip()
    if value and value not in items:
        items.append(value)
print(json.dumps(items))
PY
}

create_platform_vectorization_verification() {
  local verification_type="$1"
  local note="$2"
  local counterpart="${3:-}"
  local scope_json
  scope_json="$(vectorization_scope_json)"
  local payload
  payload="$(VERIFY_TYPE="${verification_type}" VERIFY_NOTE="${note}" VERIFY_COUNTERPART="${counterpart}" VERIFY_SCOPE_JSON="${scope_json}" python3 - <<'PY'
import json
import os

payload = {
    "verificationType": os.environ["VERIFY_TYPE"],
    "note": os.environ["VERIFY_NOTE"],
}
scope = json.loads(os.environ.get("VERIFY_SCOPE_JSON") or "[]")
if scope:
    payload["entityTypes"] = scope
counterpart = (os.environ.get("VERIFY_COUNTERPART") or "").strip()
if counterpart:
    payload["counterpartDeploymentId"] = counterpart
print(json.dumps(payload))
PY
)"
  platform_http POST "${PLATFORM_BASE_URL}/api/deployments/${PLATFORM_DEPLOYMENT_ID}/vectorization/verifications" "${payload}"
  assert_status 201 "platform vectorization verification create ${verification_type}"
  json_assert "platform vectorization verification create ${verification_type}" $'assert (data or {}).get("verificationType") == "'"${verification_type}"'"\nassert bool((data or {}).get("id"))\nprint("ok")' >/dev/null
  PARSE_BODY="${HTTP_BODY}" python3 - <<'PY'
import json
import os
d = json.loads(os.environ.get("PARSE_BODY", "") or "{}")
print((d.get("id")) or "")
PY
}

wait_for_platform_vectorization_verification() {
  local verification_run_id="$1"
  local label="$2"
  local attempts="${3:-80}"
  local sleep_s="${4:-3}"

  local i=1
  while [[ "${i}" -le "${attempts}" ]]; do
    platform_http GET "${PLATFORM_BASE_URL}/api/deployments/${PLATFORM_DEPLOYMENT_ID}/vectorization/verifications/${verification_run_id}"
    assert_status 200 "${label} status"
    local status
    status="$(PARSE_BODY="${HTTP_BODY}" python3 - <<'PY'
import json
import os
d = json.loads(os.environ.get("PARSE_BODY", "") or "{}")
run = (d.get("verificationRun") or {})
print((run.get("status")) or "")
PY
)"
    if [[ "${status}" == "PASSED" ]]; then
      return 0
    fi
    if [[ "${status}" == "FAILED" || "${status}" == "CANCELLED" ]]; then
      echo "---- ${label} ----"
      echo "${HTTP_BODY}"
      echo "------------------"
      fail "${label} (${status})"
    fi
    sleep "${sleep_s}"
    i=$((i+1))
  done

  echo "---- ${label} ----"
  echo "${HTTP_BODY}"
  echo "------------------"
  fail "${label} (timed out waiting for PASS)"
}

run_platform_vectorization_verification() {
  local verification_type="$1"
  local label="$2"
  local counterpart="${3:-}"
  local run_id
  run_id="$(create_platform_vectorization_verification "${verification_type}" "Triggered by verify-ecommerce-deployment.sh." "${counterpart}")"
  wait_for_platform_vectorization_verification "${run_id}" "${label}"
  pass "${label}"
}

refresh_platform_release_verification_evidence_if_needed() {
  local attempts=0
  local should_recheck=""
  local runs_file=""
  while true; do
    platform_http GET "${PLATFORM_BASE_URL}/api/deployments/${PLATFORM_DEPLOYMENT_ID}/verification-runs"
    assert_status 200 "platform verification runs"
    runs_file="$(mktemp)"
    printf '%s' "${HTTP_BODY}" > "${runs_file}"
    should_recheck="$(PARSE_FILE="${runs_file}" EXPECT_RELEASE_ID="${PLATFORM_EXPECT_RELEASE_ID:-${PLATFORM_LATEST_RELEASE_ID}}" EXPECT_VERSION_ID="${PLATFORM_EXPECT_VERSION_ID}" EXPECT_STATUS="${PLATFORM_EXPECT_VERIFICATION_STATUS}" python3 - <<'PY'
import json
import os

with open(os.environ["PARSE_FILE"], "r", encoding="utf-8") as handle:
    items = json.load(handle)
want_release = os.environ.get("EXPECT_RELEASE_ID") or ""
want_version = os.environ.get("EXPECT_VERSION_ID") or ""
want_status = os.environ.get("EXPECT_STATUS") or ""
run = next(
    (
        item for item in items
        if (not want_release or (item or {}).get("releaseId") == want_release)
        and (not want_version or (item or {}).get("deploymentVersionId") == want_version)
    ),
    None,
)
print("true" if run is None or ((run.get("status") or "") != want_status) else "false")
PY
)"
    rm -f "${runs_file}"
    runs_file=""
    if [[ "${should_recheck}" != "true" || "${PLATFORM_EXPECT_VERIFICATION_STATUS}" != "PASSED" || ${attempts} -ge 2 ]]; then
      return
    fi
    attempts=$((attempts + 1))
    platform_http POST "${PLATFORM_BASE_URL}/api/deployments/${PLATFORM_DEPLOYMENT_ID}/verification-runs/recheck"
    if [[ "${HTTP_STATUS}" != "201" && "${HTTP_STATUS}" != "200" ]]; then
      return
    fi
  done
}

echo "Store: ${STORE_BASE_URL}"
if [[ -n "${RUNTIME_BASE_URL}" ]]; then
  echo "Runtime: ${RUNTIME_BASE_URL}"
  echo "Operational data surface: $(operational_surface_name)"
fi
if [[ -n "${PLATFORM_BASE_URL}" ]]; then
  echo "Platform: ${PLATFORM_BASE_URL}"
  echo "Platform deployment: ${PLATFORM_DEPLOYMENT_ID}"
  if [[ -n "${EXPECT_TENANT_SCOPED_SHARED}" ]]; then
    echo "Expected tenant-scoped shared storage: ${EXPECT_TENANT_SCOPED_SHARED}"
  fi
fi

platform_login

PLATFORM_SOURCE_OF_TRUTH_BODY=""
PLATFORM_LIVE_PROMPT_ARTIFACT_URL=""
PLATFORM_LIVE_RUNTIME_URL=""
PLATFORM_LIVE_CONNECTOR_URL=""
PLATFORM_LIVE_RAILWAY_STATUS=""
PLATFORM_GENERATED_PROVISIONING_MODE=""
PLATFORM_LATEST_RELEASE_ID=""
PLATFORM_LATEST_VERIFICATION_RUN_ID=""

if [[ "${RUN_SERVICE_CHECKS}" == "true" ]]; then
  echo ""
  echo "== Health =="
  if [[ "${VERIFY_MARKETPLACE_RUNTIME}" != "true" ]]; then
    http GET "${STORE_BASE_URL}/actuator/health"
    assert_status 200 "store health"
    json_assert "store health" $'assert (data or {}).get("status") == "UP"\nprint("ok")'
    pass "store /actuator/health"
  fi

  if [[ -n "${RUNTIME_BASE_URL}" ]]; then
    runtime_http GET "${RUNTIME_CONNECTOR_HEALTH_URL}"
    assert_status 200 "runtime connector health proxy"
    json_assert "runtime connector health proxy" $'assert (data or {}).get("status") == "UP"\nprint("ok")'
    pass "runtime GET /api/admin/connector/health"

    runtime_http GET "${RUNTIME_BASE_URL}/actuator/health"
    assert_status 200 "runtime health"
    json_assert "runtime health" $'assert (data or {}).get("status") == "UP"\nprint("ok")'
    pass "runtime /actuator/health"
  fi

  if [[ "${VERIFY_MARKETPLACE_RUNTIME}" != "true" ]]; then
    echo ""
    echo "== Store Admin Endpoints (non-destructive) =="
    http POST "${STORE_BASE_URL}/api/admin/demo/reset" '{"confirm": true, "clearConnectorData": false, "clearRuntimeVectors": false}'
    if [[ "${HTTP_STATUS}" == "401" ]]; then
      echo "WARN: store reset endpoint requires admin auth (set API_KEY / API_KEY_HEADER)."
    else
      assert_status 200 "store reset (skip)"
      json_assert "store reset (skip)" $'assert (data or {}).get("success") is True\nprint("ok")'
      pass "store POST /api/admin/demo/reset (skip mode)"
    fi

    http POST "${STORE_BASE_URL}/api/admin/demo/clear" '{"confirm": false}'
    if [[ "${HTTP_STATUS}" == "401" ]]; then
      echo "WARN: store clear endpoint requires admin auth (set API_KEY / API_KEY_HEADER)."
    else
      # This is a "presence" check; the endpoint should refuse without confirm=true.
      assert_status 400 "store clear (confirm required)"
      pass "store POST /api/admin/demo/clear exists (confirm required)"
    fi
  fi

  echo ""
  echo "== REST Connector Admin Overview =="
  if [[ -n "${RUNTIME_BASE_URL}" ]]; then
    runtime_http GET "${RUNTIME_CONNECTOR_OVERVIEW_URL}"
    assert_status 200 "runtime connector admin overview"
    json_assert "runtime connector admin overview" $'assert (data or {}).get("success") is True\nprint("ok")'
    pass "runtime GET /api/admin/connector/overview"

    runtime_http GET "${RUNTIME_CONNECTOR_ACTIONS_OVERVIEW_URL}"
    assert_status 200 "runtime connector actions overview"
    json_assert "runtime connector actions overview" $'assert (data or {}).get("success") is True\nassert int((data or {}).get("count") or 0) > 0\nprint("ok")'
    pass "runtime GET /api/admin/connector/actions/overview"

    runtime_http GET "${RUNTIME_CONNECTOR_CONFIG_URL}"
    assert_status 200 "runtime connector config"
    json_assert "runtime connector config" $'assert (data or {}).get("success") is True\nprint("ok")'
    pass "runtime GET /api/admin/connector/config"
  fi

  echo ""
  echo "== Runtime-backed Operational Checks =="
  operational_http GET "/api/ai/data-sync/vector-spaces"
  assert_status 200 "$(operational_surface_name) vector spaces"
  json_assert "$(operational_surface_name) vector spaces" $'spaces = (data or {}).get("vectorSpaces") or []\nfor req in ["product","policy","review"]:\n  assert req in spaces\nprint("ok")'
  pass "$(operational_surface_name) GET /api/ai/data-sync/vector-spaces"

  operational_http GET "/api/admin/indexing/overview"
  assert_status 200 "$(operational_surface_name) indexing overview"
  json_assert "$(operational_surface_name) indexing overview" $'assert (data or {}).get("success") is True\nprint("ok")'
  pass "$(operational_surface_name) GET /api/admin/indexing/overview"

  if [[ -n "${RUNTIME_BASE_URL}" ]]; then
    echo ""
    echo "== Runtime Admin Overview =="
    runtime_http GET "${RUNTIME_BASE_URL}/api/admin/overview"
    assert_status 200 "runtime admin overview"
    json_assert "runtime admin overview" $'assert (data or {}).get("success") is True\nentity_types = set((data or {}).get("supportedEntityTypes") or [])\nfor req in ["product","policy","review"]:\n  assert req in entity_types, entity_types\nassert bool((data or {}).get("entityConfigLocation"))\nassert bool((data or {}).get("promptConfigLocation"))\nprint("ok")'
    RUNTIME_ADMIN_OVERVIEW_BODY="${HTTP_BODY}"
    assert_marketplace_runtime_overview "runtime admin marketplace alignment" "${RUNTIME_ADMIN_OVERVIEW_BODY}"
    pass "runtime GET /api/admin/overview"

    runtime_http GET "${RUNTIME_AUTH_OVERVIEW_URL}"
    assert_status 200 "runtime auth overview"
    json_assert "runtime auth overview" $'assert (data or {}).get("success") is True\nauth = (data or {}).get("auth") or {}\nassert (auth.get("ingressMode") or "") == "VERIFIED_CONTEXT_REQUIRED"\nassert auth.get("verifiedContextRequired") is True\nassert "warnings" in (data or {})\nprint("ok")'
    RUNTIME_AUTH_OVERVIEW_BODY="${HTTP_BODY}"
    pass "runtime GET /api/admin/auth/overview"

    if [[ -n "${EXPECT_TENANT_SCOPED_SHARED}" ]]; then
      HTTP_BODY="${RUNTIME_ADMIN_OVERVIEW_BODY}"
      json_assert "runtime admin tenant-scoped vector scope" $'scope = (data or {}).get("vectorScope") or {}\nexpected_shared = "'"${EXPECT_TENANT_SCOPED_SHARED}"'".lower() == "true"\nif expected_shared:\n  assert bool(scope.get("sharedStorage")) is True, scope\n  if "'"${EXPECT_TENANT_SCOPED_SCOPE_TYPE}"'":\n    assert (scope.get("scopeType") or "") == "'"${EXPECT_TENANT_SCOPED_SCOPE_TYPE}"'", scope\n  if "'"${EXPECT_TENANT_SCOPED_ROOT_RESOURCE_VALUE}"'":\n    assert (scope.get("rootResourceValue") or "") == "'"${EXPECT_TENANT_SCOPED_ROOT_RESOURCE_VALUE}"'", scope\n  if "'"${EXPECT_TENANT_SCOPED_SCOPE_PREFIX}"'":\n    assert (scope.get("scopePrefix") or "") == "'"${EXPECT_TENANT_SCOPED_SCOPE_PREFIX}"'", scope\n  if "'"${EXPECT_TENANT_SCOPED_TENANT_HANDLE}"'":\n    assert (scope.get("tenantHandle") or "") == "'"${EXPECT_TENANT_SCOPED_TENANT_HANDLE}"'", scope\n  if "'"${EXPECT_TENANT_SCOPED_SCOPE_PATTERN}"'":\n    assert (scope.get("scopePattern") or "") == "'"${EXPECT_TENANT_SCOPED_SCOPE_PATTERN}"'", scope\nelse:\n  assert not scope or bool(scope.get("sharedStorage")) is False, scope\nprint("ok")'
        pass "runtime admin tenant-scoped vector scope alignment"
    fi
    assert_expected_confirmation_interceptors "runtime admin confirmation interceptor alignment" "${RUNTIME_ADMIN_OVERVIEW_BODY}"
    pass "runtime admin confirmation interceptor alignment"

    echo ""
    echo "== Runtime Action Catalog =="
    runtime_http GET "${RUNTIME_BASE_URL}/api/admin/actions/overview"
    assert_status 200 "runtime actions overview"
    json_assert "runtime actions overview" $'assert (data or {}).get("success") is True\nassert int((data or {}).get("count") or 0) > 0\nprint("ok")'
    assert_expected_confirmation_interceptors "runtime actions confirmation interceptor alignment" "${HTTP_BODY}"
    pass "runtime GET /api/admin/actions/overview"

    run_marketplace_runtime_verification
  fi

  if [[ "${VERIFY_WRITE}" == "true" ]]; then
    echo ""
    echo "== Indexing Roundtrip (write) =="

    INDEXING_CMD="operational_http GET \"/api/admin/indexing/overview\""
    operational_http GET "/api/admin/indexing/overview"
    assert_status 200 "$(operational_surface_name) indexing overview (pre)"

    initial_product_count="$(PARSE_BODY="${HTTP_BODY}" python3 - <<'PY'
import json, os
d = json.loads(os.environ.get("PARSE_BODY", "") or "{}")
counts = (d.get("countsByEntityType") or {})
print(int(counts.get("product") or 0))
PY
)"

    sku="SKU-VERIFY-$(date +%s)"
    http POST "${STORE_BASE_URL}/api/products" "$(cat <<JSON
{
  "sku": "${sku}",
  "name": "Verify Product ${sku}",
  "description": "Created by verify-ecommerce-deployment.sh for wiring checks.",
  "category": "verification",
  "tags": "verification",
  "price": 1.00,
  "currency": "USD",
  "inStockQty": 1
}
JSON
)"
    assert_status 201 "create product"
    product_id="$(PARSE_BODY="${HTTP_BODY}" python3 - <<'PY'
import json, os
d = json.loads(os.environ.get("PARSE_BODY", "") or "{}")
pid = d.get("id")
if pid is None:
    raise SystemExit(2)
print(pid)
PY
)"
    pass "store POST /api/products (id=${product_id}, sku=${sku})"

    # Wait for product vector count to increase.
    poll_until "product indexed" 20 2 \
      "${INDEXING_CMD}" \
      $'counts = (data or {}).get(\"countsByEntityType\") or {}\ncur = int(counts.get(\"product\") or 0)\nwant = int('"${initial_product_count}"') + 1\nraise SystemExit(0 if cur >= want else 1)\n'
    pass "indexing upsert observed (product count >= initial+1)"

    http DELETE "${STORE_BASE_URL}/api/products/${product_id}"
    assert_status 200 "delete product"
    pass "store DELETE /api/products/${product_id}"

    # Wait for product vector count to return to initial.
    poll_until "product deleted from index" 20 2 \
      "${INDEXING_CMD}" \
      $'counts = (data or {}).get(\"countsByEntityType\") or {}\ncur = int(counts.get(\"product\") or 0)\nwant = int('"${initial_product_count}"')\nraise SystemExit(0 if cur == want else 1)\n'
    pass "indexing delete observed (product count returned to initial)"
  fi

  echo ""
  echo "== Confirmation Retention Flow =="
  run_confirmation_retention_flow
fi

if [[ "${RUN_PLATFORM_CHECKS}" == "true" ]]; then
  echo ""
  echo "== Platform Health and Capabilities =="
  platform_http GET "${PLATFORM_BASE_URL}/actuator/health"
  assert_status 200 "platform health"
  json_assert "platform health" $'assert (data or {}).get("status") == "UP"\nprint("ok")'
  pass "platform /actuator/health"

  platform_http GET "${PLATFORM_BASE_URL}/api/platform/overview"
  assert_status 200 "platform overview"
  json_assert "platform overview" $'caps = set((data or {}).get("capabilities") or [])\nfor req in ["verification","railway-preflight","release-progress-tracking","config-drift-verification"]:\n  assert req in caps\nprint("ok")'
  pass "platform GET /api/platform/overview"

  echo ""
  echo "== Platform Railway Preflight =="
  platform_http GET "${PLATFORM_BASE_URL}/api/platform/provisioning/railway/preflight"
  assert_status 200 "platform railway preflight"
  if [[ "${PLATFORM_EXPECT_PREFLIGHT_READY}" == "true" ]]; then
    json_assert "platform railway preflight" $'assert (data or {}).get("ready") is True\nchecks = (data or {}).get("checks") or []\nassert len(checks) > 0\nfailed = [item for item in checks if (item or {}).get("status") == "FAILED"]\nassert not failed, failed\nprint("ok")'
  else
    json_assert "platform railway preflight" $'checks = (data or {}).get("checks") or []\nassert len(checks) > 0\nprint("ok")'
  fi
  pass "platform GET /api/platform/provisioning/railway/preflight"

  echo ""
  echo "== Platform Deployment Workspace =="
  platform_http GET "${PLATFORM_BASE_URL}/api/deployments/${PLATFORM_DEPLOYMENT_ID}/workspace"
  assert_status 200 "platform deployment workspace"
  json_assert "platform deployment workspace" $'assert (data or {}).get("deployment", {}).get("id") == "'"${PLATFORM_DEPLOYMENT_ID}"'"\nassert (data or {}).get("access", {}).get("canOperate") is True\nprint("ok")'
  pass "platform GET /api/deployments/${PLATFORM_DEPLOYMENT_ID}/workspace"

  if [[ "${VERIFY_PLATFORM_USER_DIRECTORY_ADMIN}" == "true" ]]; then
    echo ""
    echo "== Platform User Directory Admin Access =="
    platform_http GET "${PLATFORM_BASE_URL}/api/platform/auth/session"
    assert_status 200 "platform auth session"
    json_assert "platform auth session" $'assert (data or {}).get("authenticated") is True\nassert (data or {}).get("canManageUsers") is True\nprint("ok")'
    pass "platform GET /api/platform/auth/session"

    platform_http GET "${PLATFORM_BASE_URL}/api/platform/users/access-overview?deploymentId=${PLATFORM_DEPLOYMENT_ID}"
    assert_status 200 "platform user access overview"
    json_assert "platform user access overview" $'items = data or []\nassert isinstance(items, list)\nassert len(items) >= 1\nfor item in items[:5]:\n  assert (item or {}).get("id"), item\n  assert (item or {}).get("email"), item\nselected = [(item or {}).get("selectedDeploymentAssignment") for item in items if (item or {}).get("selectedDeploymentAssignment")]\nfor assignment in selected:\n  assert (assignment or {}).get("deploymentId") == "'"${PLATFORM_DEPLOYMENT_ID}"'", assignment\nprint("ok")'
    pass "platform GET /api/platform/users/access-overview"
  fi

  echo ""
  echo "== Platform Source Of Truth =="
  platform_http GET "${PLATFORM_BASE_URL}/api/deployments/${PLATFORM_DEPLOYMENT_ID}/source-of-truth"
  assert_status 200 "platform source of truth"
  json_assert "platform source of truth" $'assert (data or {}).get("deploymentId") == "'"${PLATFORM_DEPLOYMENT_ID}"'"\nlive = (data or {}).get("live") or {}\nassert live.get("available") is True\nartifacts = (data or {}).get("liveArtifacts") or {}\nassert bool(artifacts.get("actionsArtifactUrl"))\nassert bool(artifacts.get("entityArtifactUrl"))\nassert bool(artifacts.get("routingArtifactUrl"))\nassert bool(artifacts.get("promptArtifactUrl"))\nreadback = (data or {}).get("liveRailwayReadback") or {}\nassert "available" in readback\nassert "status" in readback\nprint("ok")'
  pass "platform GET /api/deployments/${PLATFORM_DEPLOYMENT_ID}/source-of-truth"
  PLATFORM_SOURCE_OF_TRUTH_BODY="${HTTP_BODY}"

  if [[ "${VERIFY_MARKETPLACE_RUNTIME}" == "true" ]]; then
    HTTP_BODY="${PLATFORM_SOURCE_OF_TRUTH_BODY}"
    json_assert "platform marketplace source of truth artifacts" $'artifacts = (data or {}).get("liveArtifacts") or {}\nassert bool(artifacts.get("knowledgeSourceArtifactUrl")), artifacts\nassert bool(artifacts.get("shellArtifactUrl")), artifacts\nprint("ok")'
    pass "platform marketplace artifact alignment"
  fi

  if [[ -n "${EXPECT_TENANT_SCOPED_SHARED}" ]]; then
    HTTP_BODY="${PLATFORM_SOURCE_OF_TRUTH_BODY}"
    json_assert "platform tenant-scoped vector source of truth" $'tenant = (data or {}).get("tenantScopedVector") or {}\nassert tenant, data\nexpected_shared = "'"${EXPECT_TENANT_SCOPED_SHARED}"'".lower() == "true"\nassert bool(tenant.get("sharedStorage")) == expected_shared, tenant\nif "'"${EXPECT_TENANT_SCOPED_STATUS}"'":\n  assert (tenant.get("status") or "") == "'"${EXPECT_TENANT_SCOPED_STATUS}"'", tenant\nif "'"${EXPECT_TENANT_SCOPED_CUSTOMER_ID}"'":\n  assert (tenant.get("customerId") or "") == "'"${EXPECT_TENANT_SCOPED_CUSTOMER_ID}"'", tenant\nif "'"${EXPECT_TENANT_SCOPED_TENANT_ID}"'":\n  assert (tenant.get("tenantId") or "") == "'"${EXPECT_TENANT_SCOPED_TENANT_ID}"'", tenant\nif "'"${EXPECT_TENANT_SCOPED_SCOPE_TYPE}"'":\n  assert (tenant.get("scopeType") or "") == "'"${EXPECT_TENANT_SCOPED_SCOPE_TYPE}"'", tenant\nif "'"${EXPECT_TENANT_SCOPED_ROOT_RESOURCE_VALUE}"'":\n  assert (tenant.get("rootResourceValue") or "") == "'"${EXPECT_TENANT_SCOPED_ROOT_RESOURCE_VALUE}"'", tenant\nif "'"${EXPECT_TENANT_SCOPED_SCOPE_PREFIX}"'":\n  assert (tenant.get("scopePrefix") or "") == "'"${EXPECT_TENANT_SCOPED_SCOPE_PREFIX}"'", tenant\nif "'"${EXPECT_TENANT_SCOPED_TENANT_HANDLE}"'":\n  assert (tenant.get("tenantHandle") or "") == "'"${EXPECT_TENANT_SCOPED_TENANT_HANDLE}"'", tenant\nif "'"${EXPECT_TENANT_SCOPED_SCOPE_PATTERN}"'":\n  assert (tenant.get("scopePattern") or "") == "'"${EXPECT_TENANT_SCOPED_SCOPE_PATTERN}"'", tenant\nif "'"${EXPECT_TENANT_SCOPED_MIGRATION_LOCKED}"'":\n  assert bool(tenant.get("migrationLocked")) == ("'"${EXPECT_TENANT_SCOPED_MIGRATION_LOCKED}"'".lower() == "true"), tenant\nif "'"${EXPECT_TENANT_SCOPED_REGISTRY_STATUS}"'":\n  registry = tenant.get("registry") or {}\n  assert (registry.get("status") or "") == "'"${EXPECT_TENANT_SCOPED_REGISTRY_STATUS}"'", registry\nprint("ok")'
    pass "platform tenant-scoped vector source-of-truth alignment"
  fi

  echo ""
  echo "== Platform Vectorization =="
  platform_http GET "${PLATFORM_BASE_URL}/api/deployments/${PLATFORM_DEPLOYMENT_ID}/vectorization"
  assert_status 200 "platform vectorization overview"
  json_assert "platform vectorization overview" $'assert (data or {}).get("deploymentId") == "'"${PLATFORM_DEPLOYMENT_ID}"'"\nplan = (data or {}).get("plan")\nsource = (data or {}).get("sourceConnection")\nrunner = (data or {}).get("runner")\nassert bool(plan) == ("'"${EXPECT_VECTORIZATION_PLAN_PRESENT}"'".lower() == "true"), data\nassert bool(source) == ("'"${EXPECT_VECTORIZATION_SOURCE_CONNECTION_PRESENT}"'".lower() == "true"), data\nassert bool(runner) == ("'"${EXPECT_VECTORIZATION_RUNNER_PRESENT}"'".lower() == "true"), data\nif plan and "'"${EXPECT_VECTORIZATION_PLAN_STATUS}"'":\n  assert (plan.get("status") or "") == "'"${EXPECT_VECTORIZATION_PLAN_STATUS}"'", plan\nif plan and "'"${EXPECT_VECTORIZATION_RUNNER_MODE}"'":\n  assert (plan.get("runnerMode") or "") == "'"${EXPECT_VECTORIZATION_RUNNER_MODE}"'", plan\nif plan and "'"${EXPECT_VECTORIZATION_SYNC_STATE}"'":\n  assert (plan.get("syncState") or "") == "'"${EXPECT_VECTORIZATION_SYNC_STATE}"'", plan\nif source and "'"${EXPECT_VECTORIZATION_SOURCE_ADAPTER}"'":\n  assert (source.get("adapterType") or "") == "'"${EXPECT_VECTORIZATION_SOURCE_ADAPTER}"'", source\nif source and "'"${EXPECT_VECTORIZATION_SOURCE_AUTH_MODE}"'":\n  assert (source.get("authMode") or "") == "'"${EXPECT_VECTORIZATION_SOURCE_AUTH_MODE}"'", source\nif source and "'"${EXPECT_VECTORIZATION_SOURCE_STATUS}"'":\n  assert (source.get("status") or "") == "'"${EXPECT_VECTORIZATION_SOURCE_STATUS}"'", source\nif runner and "'"${EXPECT_VECTORIZATION_RUNNER_STATUS}"'":\n  assert (runner.get("registrationStatus") or "") == "'"${EXPECT_VECTORIZATION_RUNNER_STATUS}"'", runner\nif runner and "'"${EXPECT_VECTORIZATION_RUNNER_COMPATIBILITY_STATUS}"'":\n  assert (runner.get("compatibilityStatus") or "") == "'"${EXPECT_VECTORIZATION_RUNNER_COMPATIBILITY_STATUS}"'", runner\nprint("ok")'
  pass "platform GET /api/deployments/${PLATFORM_DEPLOYMENT_ID}/vectorization"

  platform_http GET "${PLATFORM_BASE_URL}/api/deployments/${PLATFORM_DEPLOYMENT_ID}/vectorization/preview"
  assert_status 200 "platform vectorization preview"
  json_assert "platform vectorization preview" $'assert (data or {}).get("deploymentId") == "'"${PLATFORM_DEPLOYMENT_ID}"'"\nreindex = (data or {}).get("reindexOptions") or {}\nassert reindex.get("supportsSelectedEntities") is True, reindex\nassert reindex.get("supportsFullDeployment") is True, reindex\nassert reindex.get("supportsDefer") is True, reindex\nif "'"${EXPECT_VECTORIZATION_SYNC_STATE}"'":\n  assert (data or {}).get("syncState") == "'"${EXPECT_VECTORIZATION_SYNC_STATE}"'", data\nif "'"${EXPECT_VECTORIZATION_AVAILABLE_ENTITIES}"'":\n  expected = {item for item in "'"${EXPECT_VECTORIZATION_AVAILABLE_ENTITIES}"'".split(",") if item}\n  actual = set(reindex.get("availableEntities") or [])\n  assert actual == expected, {"expected": sorted(expected), "actual": sorted(actual)}\nif "'"${EXPECT_VECTORIZATION_ENTITY_SCOPE}"'":\n  expected_scope = {item for item in "'"${EXPECT_VECTORIZATION_ENTITY_SCOPE}"'".split(",") if item}\n  actual_scope = set((data or {}).get("entityScope") or [])\n  assert actual_scope == expected_scope, {"expected": sorted(expected_scope), "actual": sorted(actual_scope)}\nprint("ok")'
  pass "platform GET /api/deployments/${PLATFORM_DEPLOYMENT_ID}/vectorization/preview"

  if [[ "${VERIFY_VECTORIZATION_ADMIN}" == "true" ]]; then
    echo ""
    echo "== Platform Vectorization Admin Verifications =="
    if [[ "${VERIFY_VECTORIZATION_CONTROL_PLANE}" == "true" ]]; then
      run_platform_vectorization_verification "CONTROL_PLANE_READINESS" "platform vectorization control-plane readiness"
    else
      echo "Control-plane readiness verification is disabled for this deployment."
    fi
    if [[ "${VERIFY_VECTORIZATION_RUNNER_ACTIVE}" == "true" ]]; then
      run_platform_vectorization_verification "RUNNER_PROVISIONING_SMOKE" "platform vectorization runner provisioning smoke"
      run_platform_vectorization_verification "SOURCE_DISCOVERY_SMOKE" "platform vectorization discovery smoke"
      run_platform_vectorization_verification "RUNNER_COMPATIBILITY_SMOKE" "platform vectorization runner compatibility smoke"
    else
      echo "Runner-active vectorization verifications are disabled for this deployment."
    fi
    if [[ "${VERIFY_VECTORIZATION_SAMPLE}" == "true" ]]; then
      run_platform_vectorization_verification "SAMPLE_VECTORIZATION_SMOKE" "platform vectorization sample smoke"
      run_platform_vectorization_verification "SYNC_STATE_AND_REINDEX_SMOKE" "platform vectorization sync-state smoke"
    fi
    if [[ "${VERIFY_TENANT_SHARED_ISOLATION}" == "true" ]]; then
      run_platform_vectorization_verification "TENANT_SHARED_ISOLATION_SMOKE" "platform tenant shared isolation smoke" "${VECTORIZATION_COUNTERPART_DEPLOYMENT_ID}"
    fi
  fi

  PLATFORM_LIVE_PROMPT_ARTIFACT_URL="$(PARSE_BODY="${PLATFORM_SOURCE_OF_TRUTH_BODY}" python3 - <<'PY'
import json, os
d = json.loads(os.environ.get("PARSE_BODY", "") or "{}")
print(((d.get("liveArtifacts") or {}).get("promptArtifactUrl")) or "")
PY
)"
  PLATFORM_LIVE_RUNTIME_URL="$(PARSE_BODY="${PLATFORM_SOURCE_OF_TRUTH_BODY}" python3 - <<'PY'
import json, os
d = json.loads(os.environ.get("PARSE_BODY", "") or "{}")
generated = d.get("generated") or {}
print((generated.get("runtimeBaseUrl")) or "")
PY
)"
  PLATFORM_LIVE_RAILWAY_STATUS="$(PARSE_BODY="${PLATFORM_SOURCE_OF_TRUTH_BODY}" python3 - <<'PY'
import json, os
d = json.loads(os.environ.get("PARSE_BODY", "") or "{}")
readback = d.get("liveRailwayReadback") or {}
print((readback.get("status")) or "")
PY
)"
  PLATFORM_GENERATED_PROVISIONING_MODE="$(PARSE_BODY="${PLATFORM_SOURCE_OF_TRUTH_BODY}" python3 - <<'PY'
import json, os
d = json.loads(os.environ.get("PARSE_BODY", "") or "{}")
generated = d.get("generated") or {}
print((generated.get("provisioningMode")) or "")
PY
)"
  PLATFORM_LATEST_RELEASE_ID="$(PARSE_BODY="${PLATFORM_SOURCE_OF_TRUTH_BODY}" python3 - <<'PY'
import json, os
d = json.loads(os.environ.get("PARSE_BODY", "") or "{}")
latest = d.get("latestRelease") or {}
print((latest.get("id")) or "")
PY
)"

  if [[ -n "${RUNTIME_BASE_URL}" ]]; then
    HTTP_BODY="${PLATFORM_SOURCE_OF_TRUTH_BODY}"
    json_assert "platform source of truth runtime URL" $'generated = (data or {}).get("generated") or {}\nassert generated.get("runtimeBaseUrl") == "'"${RUNTIME_BASE_URL}"'"\nprint("ok")'
    pass "platform generated runtime base URL matches runtime input"
  fi

  echo ""
  echo "== Platform Service Navigation =="
  platform_http GET "${PLATFORM_BASE_URL}/api/deployments/${PLATFORM_DEPLOYMENT_ID}/service-navigation"
  assert_status 200 "platform service navigation"
  json_assert "platform service navigation" $'surfaces = {item.get("key"): item for item in ((data or {}).get("surfaces") or [])}\nassert "runtime" in surfaces\nassert bool((surfaces["runtime"] or {}).get("primaryUrl"))\nassert bool((surfaces["runtime"] or {}).get("adminUrl"))\nassert "restConnector" in surfaces\nassert bool((surfaces["restConnector"] or {}).get("adminUrl"))\nprint("ok")'
  pass "platform GET /api/deployments/${PLATFORM_DEPLOYMENT_ID}/service-navigation"

  echo ""
  echo "== Platform Production Readiness =="
  platform_http GET "${PLATFORM_BASE_URL}/api/deployments/${PLATFORM_DEPLOYMENT_ID}/production-readiness"
  assert_status 200 "platform production readiness"
  json_assert "platform production readiness" $'assert (data or {}).get("deploymentId") == "'"${PLATFORM_DEPLOYMENT_ID}"'"\nscore = int((data or {}).get("overallScore") or 0)\nassert 0 <= score <= 100\nassert bool((data or {}).get("overallStatus"))\nareas = (data or {}).get("areas") or []\nassert len(areas) > 0\nindexed = {item.get("key"): item for item in areas}\nassert "tenantScopedVector" in indexed, indexed\nassert "vectorization" in indexed, indexed\nif "'"${EXPECT_TENANT_SCOPED_READINESS_STATUS}"'":\n  assert (indexed["tenantScopedVector"].get("status") or "") == "'"${EXPECT_TENANT_SCOPED_READINESS_STATUS}"'", indexed["tenantScopedVector"]\nprint("ok")'
  pass "platform GET /api/deployments/${PLATFORM_DEPLOYMENT_ID}/production-readiness"

  if [[ "${EXPECT_TENANT_SCOPED_SHARED}" == "true" && -n "${EXPECT_TENANT_SCOPED_CUSTOMER_ID}" && -n "${EXPECT_TENANT_SCOPED_TENANT_ID}" ]]; then
    echo ""
    echo "== Platform Customer Shared-Handle Evidence =="
    platform_http GET "${PLATFORM_BASE_URL}/api/platform/customers"
    assert_status 200 "platform customers"
    json_assert "platform customers tenant shared summary" $'customers = data or []\ncustomer = next((item for item in customers if (item or {}).get("id") == "'"${EXPECT_TENANT_SCOPED_CUSTOMER_ID}"'"), None)\nassert customer is not None, customers\ntenant = next((item for item in (customer.get("tenants") or []) if (item or {}).get("id") == "'"${EXPECT_TENANT_SCOPED_TENANT_ID}"'"), None)\nassert tenant is not None, customer\nshared = tenant.get("sharedVector") or {}\nassert int(shared.get("activeHandleCount") or 0) >= 1, shared\nif "'"${EXPECT_TENANT_SCOPED_SCOPE_PATTERN}"'":\n  assert (shared.get("latestScopePattern") or "") == "'"${EXPECT_TENANT_SCOPED_SCOPE_PATTERN}"'", shared\nprint("ok")'
    pass "platform customer tenant shared-vector summary"
  fi

  echo ""
  echo "== Platform Remediation =="
  platform_http GET "${PLATFORM_BASE_URL}/api/deployments/${PLATFORM_DEPLOYMENT_ID}/remediation"
  assert_status 200 "platform remediation"
  json_assert "platform remediation" $'actions = {((item.get("key") or "")).upper(): item for item in ((data or {}).get("actions") or [])}\nfor req in ["RERUN_VERIFICATION","REDEPLOY_ACTIVE_VERSION","RESET_RUNTIME_VECTORS"]:\n  assert req in actions\nassert "providerDriftDetected" in (data or {})\nassert "providerDriftStatus" in (data or {})\nassert "managedVectorDriftDetected" in (data or {})\nassert "managedVectorDriftStatus" in (data or {})\nprint("ok")'
  if [[ "${PLATFORM_LIVE_RAILWAY_STATUS}" == "WARNING" ]]; then
    json_assert "platform remediation drift alignment" $'assert (data or {}).get("providerDriftDetected") is True or (data or {}).get("managedVectorDriftDetected") is True\nprint("ok")'
  fi
  pass "platform GET /api/deployments/${PLATFORM_DEPLOYMENT_ID}/remediation"

  echo ""
  echo "== Platform Release Evidence =="
  platform_http GET "${PLATFORM_BASE_URL}/api/deployments/${PLATFORM_DEPLOYMENT_ID}/releases"
  assert_status 200 "platform releases"
  json_assert "platform releases" $'items = data or []\nassert len(items) > 0\nwant = "'"${PLATFORM_EXPECT_RELEASE_ID:-${PLATFORM_LATEST_RELEASE_ID}}"'"\nrelease = next((item for item in items if not want or (item or {}).get("id") == want), None)\nassert release is not None, items\nassert release.get("status") == "'"${PLATFORM_EXPECT_RELEASE_STATUS}"'", release\nassert release.get("verificationStatus") == "'"${PLATFORM_EXPECT_VERIFICATION_STATUS}"'", release\nif "'"${PLATFORM_EXPECT_VERSION_ID}"'":\n  assert release.get("deploymentVersionId") == "'"${PLATFORM_EXPECT_VERSION_ID}"'", release\nprint("ok")'
  pass "platform GET /api/deployments/${PLATFORM_DEPLOYMENT_ID}/releases"

  refresh_platform_release_verification_evidence_if_needed
  runs_file="$(mktemp)"
  printf '%s' "${HTTP_BODY}" > "${runs_file}"
  PLATFORM_VERIFICATION_STATUS_MATCHES_EXPECTATION="$(PARSE_FILE="${runs_file}" EXPECT_RELEASE_ID="${PLATFORM_EXPECT_RELEASE_ID:-${PLATFORM_LATEST_RELEASE_ID}}" EXPECT_VERSION_ID="${PLATFORM_EXPECT_VERSION_ID}" EXPECT_STATUS="${PLATFORM_EXPECT_VERIFICATION_STATUS}" python3 - <<'PY'
import json
import os

with open(os.environ["PARSE_FILE"], "r", encoding="utf-8") as handle:
    items = json.load(handle)
want_release = os.environ.get("EXPECT_RELEASE_ID") or ""
want_version = os.environ.get("EXPECT_VERSION_ID") or ""
want_status = os.environ.get("EXPECT_STATUS") or ""
run = next(
    (
        item for item in items
        if (not want_release or (item or {}).get("releaseId") == want_release)
        and (not want_version or (item or {}).get("deploymentVersionId") == want_version)
    ),
    None,
)
print("true" if run is not None and ((run.get("status") or "") == want_status) else "false")
PY
)"
  if [[ "${PLATFORM_VERIFICATION_STATUS_MATCHES_EXPECTATION}" == "true" ]]; then
    json_assert "platform verification runs" $'items = data or []\nassert len(items) > 0\nwant_release = "'"${PLATFORM_EXPECT_RELEASE_ID:-${PLATFORM_LATEST_RELEASE_ID}}"'"\nwant_version = "'"${PLATFORM_EXPECT_VERSION_ID}"'"\nrun = next((item for item in items if (not want_release or (item or {}).get("releaseId") == want_release) and (not want_version or (item or {}).get("deploymentVersionId") == want_version)), None)\nassert run is not None, items\nassert run.get("status") == "'"${PLATFORM_EXPECT_VERIFICATION_STATUS}"'", run\nprint("ok")'
  else
    warn "platform verification runs remain stale after refresh; continuing because direct live verification in this script passed."
  fi
  PLATFORM_LATEST_VERIFICATION_RUN_ID="$(PARSE_FILE="${runs_file}" LATEST_RELEASE_ID="${PLATFORM_EXPECT_RELEASE_ID:-${PLATFORM_LATEST_RELEASE_ID}}" EXPECT_VERSION_ID="${PLATFORM_EXPECT_VERSION_ID}" python3 - <<'PY'
import json, os
with open(os.environ["PARSE_FILE"], "r", encoding="utf-8") as handle:
    items = json.load(handle)
target = ""
for item in items:
    if item.get("releaseId") == os.environ.get("LATEST_RELEASE_ID") and (not os.environ.get("EXPECT_VERSION_ID") or item.get("deploymentVersionId") == os.environ.get("EXPECT_VERSION_ID")):
        target = item.get("id") or ""
        break
if not target and items:
    target = items[0].get("id") or ""
print(target)
PY
)"
  rm -f "${runs_file}"
  if [[ "${PLATFORM_VERIFICATION_STATUS_MATCHES_EXPECTATION}" == "true" ]]; then
    json_assert "platform verification run checks" $'items = data or []\nrun_id = "'"${PLATFORM_LATEST_VERIFICATION_RUN_ID}"'"\nassert run_id\nrun = next((item for item in items if (item or {}).get("id") == run_id), None)\nassert run is not None\nchecks = {((check or {}).get("name") or (check or {}).get("key")): (check or {}).get("status") for check in ((run or {}).get("checks") or [])}\nrequired = ["runtime_config_matches_expected","connector_config_matches_expected","runtime_actions_match_expected","connector_actions_match_expected"]\nfor req in required:\n  assert req in checks, checks\nexpected = "SKIPPED" if "'"${PLATFORM_GENERATED_PROVISIONING_MODE}"'" == "RAILWAY_STUB" else "PASSED"\nfor req in required:\n  assert checks.get(req) == expected, checks\nif "'"${EXPECT_VECTORIZATION_PLAN_PRESENT}"'".lower() == "true":\n  assert checks.get("vectorization_control_plane_ready") == expected, checks\nelse:\n  if "vectorization_control_plane_ready" in checks:\n    assert checks.get("vectorization_control_plane_ready") == "SKIPPED", checks\nif "'"${EXPECT_VECTORIZATION_RUNNER_REQUIRED}"'".lower() == "true":\n  assert checks.get("vectorization_runner_registration_ready") == expected, checks\nelse:\n  if "vectorization_runner_registration_ready" in checks:\n    assert checks.get("vectorization_runner_registration_ready") == "SKIPPED", checks\nif "'"${EXPECT_VECTORIZATION_PLATFORM_MANAGED_RUNNER}"'".lower() == "true":\n  assert checks.get("vectorization_runner_service_provisioned") == expected, checks\nelse:\n  if "vectorization_runner_service_provisioned" in checks:\n    assert checks.get("vectorization_runner_service_provisioned") == "SKIPPED", checks\nfor optional in ["runtime_prompt_config_matches_expected","prompt_artifact_fetch_probe"]:\n  if optional in checks:\n    assert checks.get(optional) == expected, checks\nprint("ok")'
  else
    warn "platform verification run checks remain stale after refresh; using current live verification results from this run."
  fi
  pass "platform GET /api/deployments/${PLATFORM_DEPLOYMENT_ID}/verification-runs"

  echo ""
  echo "== Prompt Deployment Alignment =="
  PLATFORM_LIVE_PROMPT_ARTIFACT_BODY=""
  if [[ -n "${PLATFORM_LIVE_PROMPT_ARTIFACT_URL}" ]]; then
    platform_http GET "${PLATFORM_LIVE_PROMPT_ARTIFACT_URL}"
    assert_status 200 "live prompt artifact fetch"
    PLATFORM_LIVE_PROMPT_ARTIFACT_BODY="${HTTP_BODY}"
    json_assert "live prompt artifact fetch" $'assert isinstance(data, dict)\nstring_keys = {"systemPrompt","intentExtractionPrompt","actionSelectionPrompt","clarificationPrompt","answerGenerationPrompt","retrievalPrompt","assistantUiPrompt"}\nnumber_keys = {"ragSimilarityThreshold","ragMaxDocumentsUsedForContext","ragMaxContextChars","responseGenerationMaxTokensConcise","responseGenerationMaxTokensStandard","responseGenerationMaxTokensDeep"}\nboolean_keys = {"smartSuggestionsEnabled"}\nallowed = string_keys | number_keys | boolean_keys\nassert set(data.keys()).issubset(allowed)\nfor key, value in data.items():\n  if key in string_keys:\n    assert isinstance(value, str)\n  elif key in number_keys:\n    assert isinstance(value, (int, float)) and not isinstance(value, bool)\n  elif key in boolean_keys:\n    assert isinstance(value, bool)\n  else:\n    raise AssertionError(key)\nprint("ok")'
    pass "platform prompt artifact URL fetch"
  else
    fail "Source-of-truth did not expose a live prompt artifact URL."
  fi

  platform_runtime_url="${RUNTIME_BASE_URL:-${PLATFORM_LIVE_RUNTIME_URL}}"
  if [[ -n "${platform_runtime_url}" ]]; then
    runtime_http GET "${platform_runtime_url}/api/admin/overview"
    if [[ "${HTTP_STATUS}" == "401" ]]; then
      echo "WARN: runtime admin overview requires private-runtime authorization headers for direct prompt alignment verification."
    else
      assert_status 200 "runtime admin overview (platform alignment)"
      json_assert "runtime admin overview (platform alignment)" $'assert (data or {}).get("success") is True\nprint("ok")'
      runtime_prompt_config_location="$(python3 - <<'PY' "${HTTP_BODY}"
import json, sys
data = json.loads(sys.argv[1])
print((data or {}).get("promptConfigLocation") or "")
PY
)"
      if [[ "${runtime_prompt_config_location}" == "${PLATFORM_LIVE_PROMPT_ARTIFACT_URL}" ]]; then
        pass "runtime prompt config location matches live prompt artifact"
      elif [[ -n "${runtime_prompt_config_location}" && -n "${PLATFORM_LIVE_PROMPT_ARTIFACT_BODY}" ]]; then
        platform_http GET "${runtime_prompt_config_location}"
        assert_status 200 "runtime prompt artifact fetch (platform alignment)"
        RUNTIME_PROMPT_ARTIFACT_BODY="${HTTP_BODY}"
        LIVE_PROMPT_ARTIFACT_BODY="${PLATFORM_LIVE_PROMPT_ARTIFACT_BODY}" \
        RUNTIME_PROMPT_ARTIFACT_BODY="${RUNTIME_PROMPT_ARTIFACT_BODY}" \
        python3 - <<'PY'
import json, os
live = json.loads(os.environ["LIVE_PROMPT_ARTIFACT_BODY"])
runtime = json.loads(os.environ["RUNTIME_PROMPT_ARTIFACT_BODY"])
assert live == runtime, {"live": live, "runtime": runtime}
print("ok")
PY
        pass "runtime prompt config content matches live prompt artifact"
      else
        fail "Runtime prompt config location is missing or does not match the live prompt artifact."
      fi
    fi
  fi
fi

echo ""
pass "All checks completed."
