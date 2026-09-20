#!/usr/bin/env bash
set -euo pipefail

# Certifies one exact behavior-template composition through the supported
# Marketplace bootstrap, V04 publish/apply, live behavior, and readiness APIs.

PLATFORM_BASE_URL="${PLATFORM_BASE_URL:-${PLATFORM_PUBLIC_BASE_URL:-}}"
PLATFORM_API_KEY_HEADER="${PLATFORM_API_KEY_HEADER:-X-PLATFORM-API-KEY}"
PLATFORM_API_KEY="${PLATFORM_API_KEY:-}"
PLATFORM_COOKIE="${PLATFORM_COOKIE:-}"
PLATFORM_LOGIN_EMAIL="${PLATFORM_LOGIN_EMAIL:-}"
PLATFORM_LOGIN_PASSWORD="${PLATFORM_LOGIN_PASSWORD:-}"

BEHAVIOR_TYPE="${BEHAVIOR_TYPE:-}"
TEMPLATE_PLUGIN_ID="${TEMPLATE_PLUGIN_ID:-}"
TEMPLATE_PLUGIN_VERSION="${TEMPLATE_PLUGIN_VERSION:-}"
TARGET_PROFILE_ID="${TARGET_PROFILE_ID:-}"
SOURCE_ARTIFACT_ID="${SOURCE_ARTIFACT_ID:-}"
VALIDATION_ENVIRONMENT="${VALIDATION_ENVIRONMENT:-}"
VALIDATION_TEMPLATE_ID="${VALIDATION_TEMPLATE_ID:-custom-start-from-scratch}"
VALIDATION_NAME_PREFIX="${VALIDATION_NAME_PREFIX:-Behavior Readiness Proof}"
VALIDATION_VECTOR_PROVISIONING_MODE="${VALIDATION_VECTOR_PROVISIONING_MODE:-}"
BEHAVIOR_EVIDENCE_REF="${BEHAVIOR_EVIDENCE_REF:-}"

KEEP_DEPLOYMENT="${KEEP_DEPLOYMENT:-false}"
CLEANUP_ON_FAILURE="${CLEANUP_ON_FAILURE:-false}"
HTTP_CONNECT_TIMEOUT_SECONDS="${HTTP_CONNECT_TIMEOUT_SECONDS:-15}"
HTTP_MAX_TIME_SECONDS="${HTTP_MAX_TIME_SECONDS:-90}"
HTTP_RETRY_ATTEMPTS="${HTTP_RETRY_ATTEMPTS:-4}"
HTTP_RETRY_SLEEP_SECONDS="${HTTP_RETRY_SLEEP_SECONDS:-5}"
RELEASE_POLL_SLEEP_SECONDS="${RELEASE_POLL_SLEEP_SECONDS:-10}"
RELEASE_WAIT_MAX_TOTAL_SECONDS="${RELEASE_WAIT_MAX_TOTAL_SECONDS:-3600}"
OPERATION_POLL_SLEEP_SECONDS="${OPERATION_POLL_SLEEP_SECONDS:-2}"
OPERATION_WAIT_MAX_TOTAL_SECONDS="${OPERATION_WAIT_MAX_TOTAL_SECONDS:-300}"
BEHAVIOR_REQUEST_RETRY_ATTEMPTS="${BEHAVIOR_REQUEST_RETRY_ATTEMPTS:-6}"
BEHAVIOR_REQUEST_RETRY_SLEEP_SECONDS="${BEHAVIOR_REQUEST_RETRY_SLEEP_SECONDS:-8}"

TMP_DIR=""
COOKIE_JAR=""
HTTP_BODY_FILE=""
HTTP_STATUS=""
DEPLOYMENT_ID=""
DRAFT_ID=""
VERSION_ID=""
RELEASE_ID=""
READINESS_CANDIDATE_ID=""
SCRIPT_SUCCEEDED="false"

resolve_secret_value() {
  local var_name="$1"
  local file_var_name="${var_name}_FILE"
  local direct_value="${!var_name:-}"
  local file_path="${!file_var_name:-}"
  if [[ -n "${file_path}" ]]; then
    [[ -f "${file_path}" ]] || { echo "Missing secret file for ${var_name}: ${file_path}" >&2; exit 2; }
    python3 - <<'PY' "${file_path}"
from pathlib import Path
import sys
print(Path(sys.argv[1]).read_text(encoding="utf-8"))
PY
    return
  fi
  printf '%s' "${direct_value}"
}

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

pass() {
  echo "PASS: $*"
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "Missing required command: $1"
}

require_value() {
  [[ -n "${!1:-}" ]] || fail "Set $1."
}

platform_url() {
  printf '%s%s' "${PLATFORM_BASE_URL%/}" "$1"
}

platform_request() {
  local method="$1"
  local path="$2"
  local body="${3:-}"
  HTTP_BODY_FILE="${TMP_DIR}/body-$(date +%s%N).json"
  local -a args=(
    curl -sS -X "${method}"
    --connect-timeout "${HTTP_CONNECT_TIMEOUT_SECONDS}"
    --max-time "${HTTP_MAX_TIME_SECONDS}"
    "$(platform_url "${path}")"
    -o "${HTTP_BODY_FILE}"
    -w "%{http_code}"
  )
  if [[ -n "${body}" ]]; then
    args+=(-H "Content-Type: application/json" --data "${body}")
  fi
  if [[ -n "${PLATFORM_API_KEY}" ]]; then
    args+=(-H "${PLATFORM_API_KEY_HEADER}: ${PLATFORM_API_KEY}")
  elif [[ -n "${PLATFORM_COOKIE}" ]]; then
    args+=(-H "Cookie: ${PLATFORM_COOKIE}")
  else
    args+=(-b "${COOKIE_JAR}" -c "${COOKIE_JAR}")
  fi

  local attempt=1
  while true; do
    HTTP_STATUS="$("${args[@]}" || true)"
    [[ -n "${HTTP_STATUS}" ]] || HTTP_STATUS="000"
    if [[ "${method}" == "GET" \
      && "${HTTP_STATUS}" =~ ^(000|502|503|504)$ \
      && "${attempt}" -lt "${HTTP_RETRY_ATTEMPTS}" ]]; then
      echo "WARN: transient ${method} ${path} returned ${HTTP_STATUS}; retrying." >&2
      sleep "${HTTP_RETRY_SLEEP_SECONDS}"
      attempt=$((attempt + 1))
      continue
    fi
    break
  done
}

assert_status() {
  local expected="$1"
  local label="$2"
  if [[ " ${expected} " != *" ${HTTP_STATUS} "* ]]; then
    echo "${label} returned HTTP ${HTTP_STATUS}" >&2
    [[ -f "${HTTP_BODY_FILE}" ]] && cat "${HTTP_BODY_FILE}" >&2
    exit 1
  fi
}

json_value() {
  local program="$1"
  BODY_FILE="${HTTP_BODY_FILE}" python3 - <<'PY' "${program}"
import json
import os
import sys
from pathlib import Path

raw = Path(os.environ["BODY_FILE"]).read_text(encoding="utf-8")
data = json.loads(raw) if raw.strip() else None
namespace = {"data": data}
exec(sys.argv[1], namespace, namespace)
value = namespace.get("result", "")
if isinstance(value, bool):
    print("true" if value else "false")
elif isinstance(value, (dict, list)):
    print(json.dumps(value, separators=(",", ":")))
elif value is not None:
    print(value)
PY
}

json_assert() {
  local label="$1"
  local program="$2"
  BODY_FILE="${HTTP_BODY_FILE}" python3 - <<'PY' "${label}" "${program}" >/dev/null
import json
import os
import sys
from pathlib import Path

raw = Path(os.environ["BODY_FILE"]).read_text(encoding="utf-8")
data = json.loads(raw) if raw.strip() else None
namespace = {"data": data}
try:
    exec(sys.argv[2], namespace, namespace)
except Exception:
    print(f"{sys.argv[1]} failed", file=sys.stderr)
    print(raw, file=sys.stderr)
    raise
PY
}

urlencode() {
  python3 - <<'PY' "$1"
from urllib.parse import quote
import sys
print(quote(sys.argv[1], safe=""))
PY
}

login_if_needed() {
  if [[ -n "${PLATFORM_API_KEY}" || -n "${PLATFORM_COOKIE}" ]]; then
    return
  fi
  require_value PLATFORM_LOGIN_EMAIL
  require_value PLATFORM_LOGIN_PASSWORD
  local payload
  payload="$(python3 - <<'PY' "${PLATFORM_LOGIN_EMAIL}" "${PLATFORM_LOGIN_PASSWORD}"
import json
import sys
print(json.dumps({"email": sys.argv[1], "password": sys.argv[2]}))
PY
)"
  platform_request "POST" "/api/platform/auth/login" "${payload}"
  assert_status "200" "platform login"
  json_assert "platform login" 'assert (data or {}).get("authenticated") is True'
  pass "platform authentication"
}

cleanup_deployment() {
  [[ -n "${DEPLOYMENT_ID}" ]] || return
  if [[ "${KEEP_DEPLOYMENT}" == "true" ]]; then
    echo "INFO: keeping deployment ${DEPLOYMENT_ID}."
    return
  fi
  if [[ "${SCRIPT_SUCCEEDED}" != "true" && "${CLEANUP_ON_FAILURE}" != "true" ]]; then
    echo "WARN: keeping failed verification deployment ${DEPLOYMENT_ID} for diagnosis." >&2
    return
  fi

  platform_request "POST" "/api/deployments/${DEPLOYMENT_ID}/archive" || true
  if [[ "${HTTP_STATUS}" != "200" && "${HTTP_STATUS}" != "409" && "${HTTP_STATUS}" != "404" ]]; then
    echo "WARN: archive returned HTTP ${HTTP_STATUS} for ${DEPLOYMENT_ID}." >&2
  fi
  local attempt
  for attempt in $(seq 1 18); do
    platform_request "GET" "/api/deployments?includeArchived=true" || true
    [[ "${HTTP_STATUS}" == "200" ]] || break
    local archived
    archived="$(DEPLOYMENT_ID_TARGET="${DEPLOYMENT_ID}" json_value $'import os\nitems = data or []\ntarget = os.environ["DEPLOYMENT_ID_TARGET"]\nitem = next((x for x in items if (x or {}).get("id") == target), None)\nresult = item is None or (item.get("status") or "").upper() == "ARCHIVED"')"
    [[ "${archived}" == "true" ]] && break
    sleep 5
  done
  platform_request "DELETE" "/api/deployments/${DEPLOYMENT_ID}" || true
  if [[ ! "${HTTP_STATUS}" =~ ^(200|202|204|404)$ ]]; then
    echo "WARN: delete returned HTTP ${HTTP_STATUS} for ${DEPLOYMENT_ID}." >&2
  else
    pass "cleanup requested for ${DEPLOYMENT_ID}"
  fi
}

cleanup() {
  cleanup_deployment || true
  [[ -n "${TMP_DIR}" ]] && rm -rf "${TMP_DIR}"
}

fetch_active_draft() {
  platform_request "GET" "/api/deployments/${DEPLOYMENT_ID}/draft"
  assert_status "200" "deployment draft fetch"
  DRAFT_ID="$(json_value 'result = (data or {}).get("id", "")')"
  [[ -n "${DRAFT_ID}" ]] || fail "Deployment draft id is empty."
}

wait_for_release() {
  local started now status verification provisioning current_step error failure_streak=0
  started="$(date +%s)"
  while true; do
    platform_request "GET" "/api/deployments/${DEPLOYMENT_ID}/releases"
    assert_status "200" "release status"
    status="$(RELEASE_ID_TARGET="${RELEASE_ID}" json_value $'import os\nrelease = next((x for x in (data or []) if (x or {}).get("id") == os.environ["RELEASE_ID_TARGET"]), {})\nresult = release.get("status", "")')"
    verification="$(RELEASE_ID_TARGET="${RELEASE_ID}" json_value $'import os\nrelease = next((x for x in (data or []) if (x or {}).get("id") == os.environ["RELEASE_ID_TARGET"]), {})\nresult = release.get("verificationStatus", "")')"
    provisioning="$(RELEASE_ID_TARGET="${RELEASE_ID}" json_value $'import os\nrelease = next((x for x in (data or []) if (x or {}).get("id") == os.environ["RELEASE_ID_TARGET"]), {})\nresult = release.get("provisioningStatus", "")')"
    current_step="$(RELEASE_ID_TARGET="${RELEASE_ID}" json_value $'import os\nrelease = next((x for x in (data or []) if (x or {}).get("id") == os.environ["RELEASE_ID_TARGET"]), {})\nresult = release.get("currentStepKey", "")')"
    error="$(RELEASE_ID_TARGET="${RELEASE_ID}" json_value $'import os\nrelease = next((x for x in (data or []) if (x or {}).get("id") == os.environ["RELEASE_ID_TARGET"]), {})\nresult = release.get("errorMessage", "")')"
    echo "INFO: release=${RELEASE_ID} status=${status:-UNKNOWN} verification=${verification:-UNKNOWN} provisioning=${provisioning:-UNKNOWN} step=${current_step:-none}"
    if [[ "${status}" == "APPLIED_VERIFIED" && "${verification}" == "PASSED" && "${provisioning}" == "ACTIVE" ]]; then
      pass "release ${RELEASE_ID} is APPLIED_VERIFIED/PASSED/ACTIVE"
      return
    fi
    if [[ "${status}" =~ ^(FAILED|PRE_APPLY_BLOCKED|APPLIED_VERIFICATION_FAILED|CANCELLED|CANCELED)$ \
      || "${provisioning}" =~ ^(FAILED|BLOCKED|CANCELLED|CANCELED)$ \
      || "${verification}" == "FAILED" ]]; then
      failure_streak=$((failure_streak + 1))
      if [[ "${failure_streak}" -ge 3 ]]; then
        fail "Release failed: status=${status}, verification=${verification}, provisioning=${provisioning}, error=${error}"
      fi
    else
      failure_streak=0
    fi
    now="$(date +%s)"
    (( now - started < RELEASE_WAIT_MAX_TOTAL_SECONDS )) || fail "Timed out waiting for release ${RELEASE_ID}."
    sleep "${RELEASE_POLL_SLEEP_SECONDS}"
  done
}

behavior_request_with_retry() {
  local method="$1"
  local path="$2"
  local body="${3:-}"
  local label="$4"
  local attempt
  for attempt in $(seq 1 "${BEHAVIOR_REQUEST_RETRY_ATTEMPTS}"); do
    platform_request "${method}" "${path}" "${body}"
    if [[ "${HTTP_STATUS}" =~ ^(200|201|202)$ ]]; then
      return
    fi
    if [[ "${attempt}" -eq "${BEHAVIOR_REQUEST_RETRY_ATTEMPTS}" ]]; then
      assert_status "200 201 202" "${label}"
    fi
    echo "WARN: ${label} returned ${HTTP_STATUS}; waiting for runtime convergence." >&2
    sleep "${BEHAVIOR_REQUEST_RETRY_SLEEP_SECONDS}"
  done
}

wait_for_agentic_execution() {
  local execution_id="$1"
  local started status
  started="$(date +%s)"
  while true; do
    platform_request "GET" "/api/deployments/${DEPLOYMENT_ID}/behavior-operations/agentic/executions/${execution_id}"
    assert_status "200" "agentic execution status"
    status="$(json_value 'result = (data or {}).get("status", "")')"
    if [[ "${status}" =~ ^(COMPLETED|SUCCEEDED)$ ]]; then
      json_assert "agentic durable result" $'assert (data or {}).get("durable") is True\nresults = (data or {}).get("results") or []\nspecialists = {item.get("specialist") for item in results if isinstance(item, dict) and item.get("specialist")}\nassert len(specialists) >= 2, data\nsteps = (data or {}).get("steps") or []\nworkers = [worker for step in steps if isinstance(step, dict) for worker in (step.get("workers") or []) if isinstance(worker, dict)]\nassert len({worker.get("specialist") for worker in workers if worker.get("specialist")}) >= 2, data'
      return
    fi
    [[ ! "${status}" =~ ^(FAILED|INVALID|CANCELLED|CANCELED|EXPIRED)$ ]] || fail "Agentic execution ${execution_id} ended ${status}."
    (( $(date +%s) - started < OPERATION_WAIT_MAX_TOTAL_SECONDS )) || fail "Timed out waiting for agentic execution ${execution_id}."
    sleep "${OPERATION_POLL_SLEEP_SECONDS}"
  done
}

seed_agentic_verification_evidence() {
  local body
  body="$(python3 - <<'PY' "${DEPLOYMENT_ID}"
import json, sys
deployment_id = sys.argv[1]
print(json.dumps({
    "datasetLabel": "Agentic behavior readiness evidence",
    "vectorSpace": "document",
    "records": [
        {
            "id": f"behavior-readiness-{deployment_id}",
            "content": (
                "Verified deployment knowledge: this agentic deployment coordinates a deployment "
                "knowledge specialist and a deployment runtime-state specialist. The knowledge "
                "specialist grounds answers in approved indexed evidence, while the runtime-state "
                "specialist reports the current bounded runtime snapshot. The application remains "
                "the authority and this evidence grants no write action."
            ),
            "metadata": {
                "title": "Agentic deployment readiness evidence",
                "source": "deployment-behavior-market-readiness",
                "kind": "verification"
            }
        }
    ]
}))
PY
)"
  platform_request "POST" "/api/deployments/${DEPLOYMENT_ID}/poc/import-runs" "${body}"
  assert_status "201" "agentic verification evidence import"
  json_assert "agentic verification evidence import" $'assert (data or {}).get("status") == "SUCCEEDED", data\nassert (data or {}).get("vectorSpace") == "document", data\nassert int((data or {}).get("importedCount") or 0) == 1, data\nassert int((data or {}).get("failedCount") or 0) == 0, data'
  pass "seeded deployment-scoped grounded evidence through the secured import API"
}

verify_agentic() {
  local key="behavior-agentic-$(date +%s)-${RANDOM}"
  local question="Analyze this deployment using both verified knowledge and runtime-state specialists, then summarize their distinct evidence."
  local body execution_id replayed_id replay_flag
  body="$(python3 - <<'PY' "${question}" "${key}"
import json, sys
print(json.dumps({"question": sys.argv[1], "idempotencyKey": sys.argv[2]}))
PY
)"
  behavior_request_with_retry "POST" "/api/deployments/${DEPLOYMENT_ID}/behavior-operations/agentic/executions" "${body}" "agentic submit"
  execution_id="$(json_value 'result = (data or {}).get("executionId", "")')"
  [[ -n "${execution_id}" ]] || fail "Agentic submit did not return executionId."
  wait_for_agentic_execution "${execution_id}"
  pass "agentic durable execution used distinct specialists"

  platform_request "POST" "/api/deployments/${DEPLOYMENT_ID}/behavior-operations/agentic/executions" "${body}"
  assert_status "200 202" "agentic idempotent resubmit"
  replayed_id="$(json_value 'result = (data or {}).get("executionId", "")')"
  replay_flag="$(json_value 'result = bool((data or {}).get("replayed"))')"
  [[ "${replayed_id}" == "${execution_id}" && "${replay_flag}" == "true" ]] \
    || fail "Agentic idempotent resubmit did not replay the original execution."

  platform_request "POST" "/api/deployments/${DEPLOYMENT_ID}/behavior-operations/agentic/executions/${execution_id}/replay" "${body}"
  assert_status "200 202" "agentic explicit replay"
  EXECUTION_ID_EXPECTED="${execution_id}" json_assert "agentic explicit replay" $'import os\nassert (data or {}).get("executionId") == os.environ["EXECUTION_ID_EXPECTED"]\nassert (data or {}).get("replayed") is True'
  pass "agentic idempotency and replay"
}

wait_for_smart_brain_operation() {
  local operation_id="$1"
  local started status
  started="$(date +%s)"
  while true; do
    platform_request "GET" "/api/deployments/${DEPLOYMENT_ID}/behavior-operations/smart-brain/operations/${operation_id}"
    assert_status "200" "Smart Brain operation status"
    status="$(json_value 'result = (data or {}).get("status", "")')"
    if [[ "${status}" == "SUCCEEDED" ]]; then
      json_assert "Smart Brain durable result" $'assert (data or {}).get("result") is not None\nassert not (data or {}).get("failure")'
      return
    fi
    [[ ! "${status}" =~ ^(FAILED|CANCELLED|CANCELED|EXPIRED)$ ]] || fail "Smart Brain operation ${operation_id} ended ${status}."
    (( $(date +%s) - started < OPERATION_WAIT_MAX_TOTAL_SECONDS )) || fail "Timed out waiting for Smart Brain operation ${operation_id}."
    sleep "${OPERATION_POLL_SLEEP_SECONDS}"
  done
}

verify_smart_brain() {
  local suffix="$(date +%s)-${RANDOM}"
  local key="behavior-smart-brain-${suffix}"
  local event_id="behavior-event-${suffix}"
  local body operation_id replayed_id replay_flag
  body="$(python3 - <<'PY' "${key}" "${event_id}"
import json, sys
event = {
    "specversion": "1.0",
    "id": sys.argv[2],
    "source": "urn:loomai:behavior-readiness",
    "type": "com.loomai.smart-brain.analysis.requested",
    "datacontenttype": "application/json",
    "data": {"subject": "market-readiness", "instruction": "Return a typed bounded analysis."},
}
print(json.dumps({"idempotencyKey": sys.argv[1], "cloudEvent": event}))
PY
)"
  behavior_request_with_retry "POST" "/api/deployments/${DEPLOYMENT_ID}/behavior-operations/smart-brain/triggers/event-analysis" "${body}" "Smart Brain trigger"
  operation_id="$(json_value 'result = (data or {}).get("operationId", "")')"
  [[ -n "${operation_id}" ]] || fail "Smart Brain trigger did not return operationId."
  wait_for_smart_brain_operation "${operation_id}"
  pass "Smart Brain CloudEvent produced a durable typed result"

  platform_request "POST" "/api/deployments/${DEPLOYMENT_ID}/behavior-operations/smart-brain/triggers/event-analysis" "${body}"
  assert_status "200 202" "Smart Brain idempotent resubmit"
  replayed_id="$(json_value 'result = (data or {}).get("operationId", "")')"
  replay_flag="$(json_value 'result = bool((data or {}).get("replayed"))')"
  [[ "${replayed_id}" == "${operation_id}" && "${replay_flag}" == "true" ]] \
    || fail "Smart Brain idempotent resubmit did not replay the original operation."

  platform_request "POST" "/api/deployments/${DEPLOYMENT_ID}/behavior-operations/smart-brain/operations/${operation_id}/replay" '{}'
  assert_status "200" "Smart Brain explicit replay"
  OPERATION_ID_EXPECTED="${operation_id}" json_assert "Smart Brain explicit replay" $'import os\nassert (data or {}).get("operationId") == os.environ["OPERATION_ID_EXPECTED"]\nassert (data or {}).get("replayed") is True'
  pass "Smart Brain idempotency and replay"
}

verify_conversational() {
  local first_body second_body conversation_id
  first_body='{"query":"Write a concise welcome message for a new user.","authPath":"PLATFORM_PRIVATE"}'
  behavior_request_with_retry "POST" "/api/deployments/${DEPLOYMENT_ID}/poc-chat/query" "${first_body}" "conversational first query"
  json_assert "conversational first query" $'assert (data or {}).get("success") is True\nresult = (data or {}).get("result")\nassert isinstance(result, dict)\nassert ((result.get("metadata") or {}).get("mode") or result.get("mode") or "").lower() == "conversational", result\nassert (data or {}).get("conversationId")'
  conversation_id="$(json_value 'result = (data or {}).get("conversationId", "")')"
  second_body="$(python3 - <<'PY' "${conversation_id}"
import json, sys
print(json.dumps({
    "query": "Continue the same welcome message with one concrete next step.",
    "conversationId": sys.argv[1],
    "authPath": "PLATFORM_PRIVATE",
}))
PY
)"
  behavior_request_with_retry "POST" "/api/deployments/${DEPLOYMENT_ID}/poc-chat/query" "${second_body}" "conversational continuation"
  CONVERSATION_ID_EXPECTED="${conversation_id}" json_assert "conversational continuation" $'import os\nassert (data or {}).get("success") is True\nassert (data or {}).get("conversationId") == os.environ["CONVERSATION_ID_EXPECTED"]\nresult = (data or {}).get("result")\nassert isinstance(result, dict)\nassert ((result.get("metadata") or {}).get("mode") or result.get("mode") or "").lower() == "conversational", result'
  pass "authenticated conversational query, structured result, and continuation"
}

build_readiness_payload() {
  local checks_json="$1"
  python3 - <<'PY' "${RELEASE_ID}" "${VERIFICATION_PACKS_JSON}" "${BEHAVIOR_EVIDENCE_REF}" "${checks_json}"
import json, sys
release_id, packs_raw, evidence_ref, checks_raw = sys.argv[1:]
packs = json.loads(packs_raw)
checks = json.loads(checks_raw)
print(json.dumps({
    "releaseId": release_id,
    "behaviorProofs": [
        {
            "verificationPackId": pack,
            "status": "PASSED",
            "evidenceRef": evidence_ref,
            "passedChecks": checks,
        }
        for pack in packs
    ],
}))
PY
}

PLATFORM_BASE_URL="$(resolve_secret_value PLATFORM_BASE_URL)"
PLATFORM_API_KEY="$(resolve_secret_value PLATFORM_API_KEY)"
PLATFORM_COOKIE="$(resolve_secret_value PLATFORM_COOKIE)"
PLATFORM_LOGIN_EMAIL="$(resolve_secret_value PLATFORM_LOGIN_EMAIL)"
PLATFORM_LOGIN_PASSWORD="$(resolve_secret_value PLATFORM_LOGIN_PASSWORD)"

require_cmd curl
require_cmd python3
require_value PLATFORM_BASE_URL
require_value BEHAVIOR_TYPE
require_value TEMPLATE_PLUGIN_ID
require_value TEMPLATE_PLUGIN_VERSION
require_value TARGET_PROFILE_ID
require_value SOURCE_ARTIFACT_ID
require_value VALIDATION_ENVIRONMENT

BEHAVIOR_TYPE="$(printf '%s' "${BEHAVIOR_TYPE}" | tr '[:lower:]-' '[:upper:]_')"
VALIDATION_ENVIRONMENT="$(printf '%s' "${VALIDATION_ENVIRONMENT}" | tr '[:upper:]' '[:lower:]')"
case "${BEHAVIOR_TYPE}" in
  CONVERSATIONAL)
    PASSED_CHECKS_JSON='["AUTHENTICATED_QUERY","CONTINUATION","STRUCTURED_RESULT"]'
    ;;
  AGENTIC_SPECIALIST_TEAM)
    PASSED_CHECKS_JSON='["DURABLE_EXECUTION","DISTINCT_SPECIALISTS","IDEMPOTENT_REPLAY"]'
    ;;
  SMART_BRAIN)
    PASSED_CHECKS_JSON='["CLOUD_EVENT_INGRESS","DURABLE_RESULT","IDEMPOTENT_REPLAY"]'
    ;;
  *) fail "Unsupported BEHAVIOR_TYPE: ${BEHAVIOR_TYPE}" ;;
esac
[[ "${VALIDATION_ENVIRONMENT}" == "staging" || "${VALIDATION_ENVIRONMENT}" == "production" ]] \
  || fail "VALIDATION_ENVIRONMENT must be staging or production."

TMP_DIR="$(mktemp -d)"
COOKIE_JAR="${TMP_DIR}/cookies.txt"
trap cleanup EXIT
login_if_needed

VALIDATION_NAME="${VALIDATION_NAME_PREFIX} ${BEHAVIOR_TYPE} ${VALIDATION_ENVIRONMENT} $(date +%Y%m%d-%H%M%S)"
BOOTSTRAP_BODY="$(python3 - <<'PY' "${VALIDATION_NAME}" "${VALIDATION_ENVIRONMENT}" "${VALIDATION_TEMPLATE_ID}" "${TEMPLATE_PLUGIN_VERSION}" "${VALIDATION_VECTOR_PROVISIONING_MODE}"
import json, sys
payload = {
    "name": sys.argv[1],
    "environment": sys.argv[2],
    "templateId": sys.argv[3],
    "pluginVersion": sys.argv[4],
}
if sys.argv[5]:
    payload["vectorProvisioningMode"] = sys.argv[5]
print(json.dumps(payload))
PY
)"
platform_request "POST" "/api/marketplace/templates/${TEMPLATE_PLUGIN_ID}/bootstrap" "${BOOTSTRAP_BODY}"
assert_status "201" "Marketplace behavior template bootstrap"
DEPLOYMENT_ID="$(json_value 'result = (data or {}).get("id", "")')"
[[ -n "${DEPLOYMENT_ID}" ]] || fail "Template bootstrap returned no deployment id."
BEHAVIOR_EXPECTED="${BEHAVIOR_TYPE}" json_assert "template behavior" $'import os\nassert (data or {}).get("behaviorType") == os.environ["BEHAVIOR_EXPECTED"], data'
pass "bootstrapped ${TEMPLATE_PLUGIN_ID}@${TEMPLATE_PLUGIN_VERSION} as ${DEPLOYMENT_ID}"

platform_request "GET" "/api/deployments/${DEPLOYMENT_ID}/marketplace-installs"
assert_status "200" "Marketplace install provenance"
TEMPLATE_PLUGIN_ID_EXPECTED="${TEMPLATE_PLUGIN_ID}" TEMPLATE_PLUGIN_VERSION_EXPECTED="${TEMPLATE_PLUGIN_VERSION}" \
  json_assert "Marketplace template provenance" $'import os\nmatching = [item for item in (data or []) if (item or {}).get("pluginId") == os.environ["TEMPLATE_PLUGIN_ID_EXPECTED"]]\nassert len(matching) == 1, data\nassert matching[0].get("status") == "BOOTSTRAPPED", matching[0]\nassert matching[0].get("pluginVersion") == os.environ["TEMPLATE_PLUGIN_VERSION_EXPECTED"], matching[0]'
pass "exact BOOTSTRAPPED template provenance"

fetch_active_draft
platform_request "POST" "/api/deployment-drafts/${DRAFT_ID}/validate"
assert_status "200" "deployment draft validation"
json_assert "deployment draft validation" $'assert (data or {}).get("publishReady") is True\nassert (data or {}).get("errorCount") == 0, data\nassert all((issue or {}).get("severity") == "WARNING" for issue in ((data or {}).get("issues") or [])), data'
pass "draft is publish-ready"

platform_request "GET" "/api/deployments/${DEPLOYMENT_ID}/draft"
assert_status "200" "deployment draft verification-pack fetch"
VERIFICATION_PACKS_JSON="$(json_value 'result = (((data or {}).get("behaviorConfig") or {}).get("runtimeRequirements") or {}).get("verificationPackIds") or []')"
[[ "${VERIFICATION_PACKS_JSON}" != "[]" ]] || fail "Behavior template did not compile verification packs into the draft."

platform_request "POST" "/api/deployment-drafts/${DRAFT_ID}/publish"
assert_status "200 201" "deployment draft publish"
VERSION_ID="$(json_value 'result = (data or {}).get("id", "")')"
[[ -n "${VERSION_ID}" ]] || fail "Draft publish returned no version id."
json_assert "published V04 version" 'assert (data or {}).get("status") == "PUBLISHED"'
pass "published immutable V04 version ${VERSION_ID}"

platform_request "GET" "/api/deployments/${DEPLOYMENT_ID}/versions/${VERSION_ID}/compatible-source-artifacts"
assert_status "200" "compatible source artifacts"
SOURCE_ARTIFACT_ID_EXPECTED="${SOURCE_ARTIFACT_ID}" json_assert "selected source artifact compatibility" $'import os\nassert any((item or {}).get("id") == os.environ["SOURCE_ARTIFACT_ID_EXPECTED"] for item in (data or [])), data'
pass "source artifact ${SOURCE_ARTIFACT_ID} is compatible"

APPLY_PATH="/api/deployments/${DEPLOYMENT_ID}/apply/${VERSION_ID}?targetProfileId=$(urlencode "${TARGET_PROFILE_ID}")&sourceArtifactId=$(urlencode "${SOURCE_ARTIFACT_ID}")"
platform_request "POST" "${APPLY_PATH}"
assert_status "200 201" "deployment version apply"
RELEASE_ID="$(json_value 'result = (data or {}).get("id", "")')"
[[ -n "${RELEASE_ID}" ]] || fail "Apply returned no release id."
pass "release ${RELEASE_ID} requested"
wait_for_release

case "${BEHAVIOR_TYPE}" in
  CONVERSATIONAL) verify_conversational ;;
  AGENTIC_SPECIALIST_TEAM) seed_agentic_verification_evidence; verify_agentic ;;
  SMART_BRAIN) verify_smart_brain ;;
esac

if [[ -z "${BEHAVIOR_EVIDENCE_REF}" ]]; then
  BEHAVIOR_EVIDENCE_REF="live-verifier:${DEPLOYMENT_ID}:${RELEASE_ID}:$(date -u +%Y%m%dT%H%M%SZ)"
fi
READINESS_BODY="$(build_readiness_payload "${PASSED_CHECKS_JSON}")"
platform_request "POST" "/api/deployments/${DEPLOYMENT_ID}/behavior-readiness/evaluate" "${READINESS_BODY}"
assert_status "200" "behavior readiness evaluation"
READINESS_CANDIDATE_ID="$(json_value 'result = (data or {}).get("id", "")')"
MATERIAL_HASH="$(json_value 'result = (data or {}).get("materialHash", "")')"
ENVIRONMENT_EXPECTED="${VALIDATION_ENVIRONMENT}" json_assert "behavior readiness evidence" $'import os\nroot = data or {}\nassert root.get("effectiveMaturity") == "HOSTED_PROVEN", root\nassert root.get("status") == "ACTIVE", root\nassert root.get("materialHash", "").startswith("sha256:"), root\nproofs = root.get("hostedProofs") or []\nassert any((proof or {}).get("environment") == os.environ["ENVIRONMENT_EXPECTED"] and (proof or {}).get("verificationStatus") == "PASSED" for proof in proofs), proofs\nassert all(all((item or {}).get("status") == "PASSED" for item in ((proof or {}).get("behaviorProofs") or [])) for proof in proofs), proofs'
pass "recorded HOSTED_PROVEN readiness candidate ${READINESS_CANDIDATE_ID}"

SCRIPT_SUCCEEDED="true"
python3 - <<'PY' "${BEHAVIOR_TYPE}" "${TEMPLATE_PLUGIN_ID}" "${TEMPLATE_PLUGIN_VERSION}" "${VALIDATION_ENVIRONMENT}" "${DEPLOYMENT_ID}" "${VERSION_ID}" "${RELEASE_ID}" "${READINESS_CANDIDATE_ID}" "${MATERIAL_HASH}"
import json, sys
keys = ["behaviorType", "templatePluginId", "templatePluginVersion", "environment", "deploymentId", "versionId", "releaseId", "readinessCandidateId", "materialHash"]
print("BEHAVIOR_READINESS_RESULT=" + json.dumps(dict(zip(keys, sys.argv[1:])), separators=(",", ":")))
PY
pass "exact deployment behavior certification completed"
