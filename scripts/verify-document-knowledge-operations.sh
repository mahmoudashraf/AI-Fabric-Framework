#!/usr/bin/env bash
set -euo pipefail

# Live, deployment-local Document Knowledge Operations verification.
# The script uses Platform administration APIs only. It never receives source
# credentials or file bytes and never asks the source connector to delete data.

PLATFORM_BASE_URL="${PLATFORM_BASE_URL:-${PLATFORM_PUBLIC_BASE_URL:-}}"
PLATFORM_API_KEY_HEADER="${PLATFORM_API_KEY_HEADER:-X-PLATFORM-API-KEY}"
PLATFORM_API_KEY="${PLATFORM_API_KEY:-}"
PLATFORM_COOKIE="${PLATFORM_COOKIE:-}"
PLATFORM_LOGIN_EMAIL="${PLATFORM_LOGIN_EMAIL:-}"
PLATFORM_LOGIN_PASSWORD="${PLATFORM_LOGIN_PASSWORD:-}"

DOCUMENT_DEPLOYMENT_ID="${DOCUMENT_DEPLOYMENT_ID:-}"
DOCUMENT_DATASET_ID="${DOCUMENT_DATASET_ID:-document-knowledge}"
DOCUMENT_TEXT_OBJECT_REFERENCE="${DOCUMENT_TEXT_OBJECT_REFERENCE:-}"
DOCUMENT_TEXT_RETRIEVAL_QUERY="${DOCUMENT_TEXT_RETRIEVAL_QUERY:-}"
DOCUMENT_JSON_OBJECT_REFERENCE="${DOCUMENT_JSON_OBJECT_REFERENCE:-}"
DOCUMENT_JSON_RETRIEVAL_QUERY="${DOCUMENT_JSON_RETRIEVAL_QUERY:-}"
DOCUMENT_EXPECTED_CONNECTOR_TYPE="${DOCUMENT_EXPECTED_CONNECTOR_TYPE:-}"
DOCUMENT_CLEANUP_INDEX="${DOCUMENT_CLEANUP_INDEX:-true}"
DOCUMENT_DISCOVERY_MAX_PAGES="${DOCUMENT_DISCOVERY_MAX_PAGES:-20}"
DOCUMENT_RECONCILE_ATTEMPTS="${DOCUMENT_RECONCILE_ATTEMPTS:-60}"
DOCUMENT_RECONCILE_SLEEP_SECONDS="${DOCUMENT_RECONCILE_SLEEP_SECONDS:-2}"
DOCUMENT_RETRIEVAL_ATTEMPTS="${DOCUMENT_RETRIEVAL_ATTEMPTS:-15}"
DOCUMENT_RETRIEVAL_SLEEP_SECONDS="${DOCUMENT_RETRIEVAL_SLEEP_SECONDS:-2}"
PLATFORM_HTTP_RETRY_ATTEMPTS="${PLATFORM_HTTP_RETRY_ATTEMPTS:-4}"
PLATFORM_HTTP_RETRY_SLEEP_SECONDS="${PLATFORM_HTTP_RETRY_SLEEP_SECONDS:-3}"
PLATFORM_CURL_CONNECT_TIMEOUT_SECONDS="${PLATFORM_CURL_CONNECT_TIMEOUT_SECONDS:-15}"
PLATFORM_CURL_MAX_TIME_SECONDS="${PLATFORM_CURL_MAX_TIME_SECONDS:-90}"

HTTP_STATUS=""
HTTP_BODY=""
TMP_DIR=""
PLATFORM_COOKIE_JAR=""
DISCOVERED_FINGERPRINT=""

pass() { printf 'PASS: %s\n' "$*"; }
fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "Missing required command: $1"
}

require_value() {
  local name="$1"
  [[ -n "${!name:-}" ]] || fail "Missing required environment value: ${name}"
}

resolve_secret_value() {
  local name="$1"
  local file_name="${name}_FILE"
  local file_path="${!file_name:-}"
  if [[ -n "${file_path}" ]]; then
    [[ -f "${file_path}" ]] || fail "Missing secret file for ${name}"
    python3 - "${file_path}" <<'PY'
import pathlib
import sys
print(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"), end="")
PY
    return
  fi
  printf '%s' "${!name:-}"
}

trim_slash() {
  printf '%s' "${1%/}"
}

is_true() {
  case "${1,,}" in
    true|1|yes|on) return 0 ;;
    *) return 1 ;;
  esac
}

assert_status_any() {
  local label="$1"
  shift
  local expected
  for expected in "$@"; do
    if [[ "${HTTP_STATUS}" == "${expected}" ]]; then
      return 0
    fi
  done
  fail "${label} returned HTTP ${HTTP_STATUS}; expected one of: $*"
}

json_assert() {
  local label="$1"
  local assertion="$2"
  ASSERT_LABEL="${label}" ASSERT_BODY="${HTTP_BODY}" ASSERT_CODE="${assertion}" python3 - <<'PY'
import json
import os

label = os.environ["ASSERT_LABEL"]
try:
    data = json.loads(os.environ.get("ASSERT_BODY", ""))
    namespace = {"data": data}
    exec(os.environ["ASSERT_CODE"].replace("\\n", "\n"), namespace, namespace)
except Exception as exc:
    detail = str(exc).strip() or exc.__class__.__name__
    raise SystemExit(f"FAIL: {label}: {detail}")
PY
}

json_extract() {
  local expression="$1"
  JSON_BODY="${HTTP_BODY}" JSON_EXPRESSION="${expression}" python3 - <<'PY'
import json
import os

data = json.loads(os.environ.get("JSON_BODY", ""))
value = eval(os.environ["JSON_EXPRESSION"], {"data": data})
if value is not None:
    print(value, end="")
PY
}

json_payload() {
  PAYLOAD_DATASET="${DOCUMENT_DATASET_ID}" \
  PAYLOAD_OBJECT="${1:-}" \
  PAYLOAD_CURSOR="${2:-}" \
  PAYLOAD_QUERY="${3:-}" \
  PAYLOAD_KIND="${4:-register}" \
  python3 - <<'PY'
import json
import os

kind = os.environ["PAYLOAD_KIND"]
if kind == "discover":
    payload = {"datasetId": os.environ["PAYLOAD_DATASET"], "limit": 200}
    if os.environ.get("PAYLOAD_CURSOR"):
        payload["cursor"] = os.environ["PAYLOAD_CURSOR"]
elif kind == "retrieval":
    payload = {"query": os.environ["PAYLOAD_QUERY"], "limit": 10}
else:
    payload = {
        "datasetId": os.environ["PAYLOAD_DATASET"],
        "objectReference": os.environ["PAYLOAD_OBJECT"],
        "visibility": "internal",
        "metadata": {"sourceCategory": "verification"},
    }
print(json.dumps(payload, separators=(",", ":")))
PY
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

  local output_file="${TMP_DIR}/response.json"
  local headers=(-H "Accept: application/json")
  if [[ "${method}" != "GET" && "${method}" != "DELETE" ]]; then
    headers+=(-H "Content-Type: application/json")
  fi
  if [[ -n "${PLATFORM_API_KEY}" ]]; then
    headers+=(-H "${PLATFORM_API_KEY_HEADER}: ${PLATFORM_API_KEY}")
  fi
  if [[ -n "${PLATFORM_COOKIE}" ]]; then
    headers+=(-H "Cookie: ${PLATFORM_COOKIE}")
  fi

  local attempt=1
  local status=""
  while true; do
    local cookie_args=()
    if [[ -s "${PLATFORM_COOKIE_JAR}" ]]; then
      cookie_args=(-b "${PLATFORM_COOKIE_JAR}" -c "${PLATFORM_COOKIE_JAR}")
    fi
    local body_args=()
    if [[ -n "${body}" ]]; then
      body_args=(--data "${body}")
    fi
    status="$(curl -sS \
      --connect-timeout "${PLATFORM_CURL_CONNECT_TIMEOUT_SECONDS}" \
      --max-time "${PLATFORM_CURL_MAX_TIME_SECONDS}" \
      -o "${output_file}" -w '%{http_code}' -X "${method}" \
      "${headers[@]}" "${cookie_args[@]}" "${body_args[@]}" "$@" "${url}" || true)"
    if [[ ( "${status}" == "000" || "${status}" == "502" || "${status}" == "503" || "${status}" == "504" ) \
        && "${attempt}" -lt "${PLATFORM_HTTP_RETRY_ATTEMPTS}" ]]; then
      sleep "${PLATFORM_HTTP_RETRY_SLEEP_SECONDS}"
      attempt=$((attempt + 1))
      continue
    fi
    break
  done
  HTTP_STATUS="${status}"
  HTTP_BODY="$(cat "${output_file}")"
}

platform_login() {
  if [[ -n "${PLATFORM_API_KEY}" || -n "${PLATFORM_COOKIE}" ]]; then
    return
  fi
  require_value PLATFORM_LOGIN_EMAIL
  require_value PLATFORM_LOGIN_PASSWORD
  local payload
  payload="$(LOGIN_EMAIL="${PLATFORM_LOGIN_EMAIL}" LOGIN_PASSWORD="${PLATFORM_LOGIN_PASSWORD}" python3 - <<'PY'
import json
import os
print(json.dumps({"email": os.environ["LOGIN_EMAIL"], "password": os.environ["LOGIN_PASSWORD"]}))
PY
)"
  platform_http POST "${PLATFORM_BASE_URL}/api/platform/auth/login" "${payload}"
  assert_status_any "Platform login" 200
}

new_key() {
  local label="$1"
  printf 'document-verification-%s-%s-%s' "${RUN_ID}" "${label}" "$RANDOM"
}

discover_object() {
  local object_reference="$1"
  local cursor=""
  local page=1
  DISCOVERED_FINGERPRINT=""
  while [[ "${page}" -le "${DOCUMENT_DISCOVERY_MAX_PAGES}" ]]; do
    platform_http POST "${DOCUMENT_API_BASE}/discover" "$(json_payload "" "${cursor}" "" discover)"
    assert_status_any "Document discovery" 200
    DISCOVERED_FINGERPRINT="$(EXPECTED_OBJECT="${object_reference}" JSON_BODY="${HTTP_BODY}" python3 - <<'PY'
import json
import os

data = json.loads(os.environ["JSON_BODY"])
expected = os.environ["EXPECTED_OBJECT"]
for source in data.get("sources", []):
    if source.get("objectReference") == expected:
        print(source.get("providerRevisionFingerprint", ""), end="")
        break
PY
)"
    if [[ -n "${DISCOVERED_FINGERPRINT}" ]]; then
      return
    fi
    cursor="$(json_extract 'data.get("nextCursor") or ""')"
    [[ -n "${cursor}" ]] || break
    page=$((page + 1))
  done
  fail "The pre-seeded verification object was not found inside the configured connector scope: ${object_reference}"
}

assert_terminal_active_detail() {
  DETAIL_SOURCE_ID="${1}" JSON_BODY="${HTTP_BODY}" python3 - <<'PY'
import json
import os

data = json.loads(os.environ["JSON_BODY"])
source = data.get("source") or {}
if source.get("sourceId") != os.environ["DETAIL_SOURCE_ID"]:
    raise SystemExit(1)
if source.get("status") != "ACTIVE" or source.get("activeVersion") is None:
    raise SystemExit(1)
active = source["activeVersion"]
manifests = data.get("manifests") or []
manifest = next((item for item in manifests if item.get("sourceVersion") == active and item.get("state") == "ACTIVE"), None)
if manifest is None:
    raise SystemExit(1)
index_work = [item for item in (manifest.get("work") or []) if item.get("operation") == "INDEX"]
if not index_work or any(not item.get("terminal") or not item.get("successful") for item in index_work):
    raise SystemExit(1)
PY
}

reconcile_until_active() {
  local source_id="$1"
  local attempt=1
  while [[ "${attempt}" -le "${DOCUMENT_RECONCILE_ATTEMPTS}" ]]; do
    platform_http POST "${DOCUMENT_API_BASE}/sources/${source_id}/reconcile" '{}' \
      -H "Idempotency-Key: $(new_key "reconcile-${attempt}")"
    assert_status_any "Document reconcile" 200
    platform_http GET "${DOCUMENT_API_BASE}/sources/${source_id}" ""
    assert_status_any "Document detail during reconciliation" 200
    if assert_terminal_active_detail "${source_id}"; then
      return
    fi
    local state
    state="$(json_extract '(data.get("source") or {}).get("status", "UNKNOWN")')"
    if [[ "${state}" == "FAILED" || "${state}" == "DELETED" ]]; then
      fail "Document indexing reached unexpected terminal source state: ${state}"
    fi
    sleep "${DOCUMENT_RECONCILE_SLEEP_SECONDS}"
    attempt=$((attempt + 1))
  done
  fail "Document indexing did not reach an active successful state in time"
}

retrieval_until_present() {
  local source_id="$1"
  local query="$2"
  local attempt=1
  while [[ "${attempt}" -le "${DOCUMENT_RETRIEVAL_ATTEMPTS}" ]]; do
    platform_http POST "${DOCUMENT_API_BASE}/retrieval-proof" "$(json_payload "" "" "${query}" retrieval)"
    assert_status_any "Document retrieval proof" 200
    if EXPECTED_SOURCE_ID="${source_id}" JSON_BODY="${HTTP_BODY}" python3 - <<'PY'
import json
import os

data = json.loads(os.environ["JSON_BODY"])
source_id = os.environ["EXPECTED_SOURCE_ID"]
evidence = [item for item in (data.get("evidence") or []) if item.get("sourceId") == source_id]
if data.get("evidenceCount", 0) <= 0 or not evidence:
    raise SystemExit(1)
for item in evidence:
    if item.get("sourceVersion") is None or not item.get("chunkId") or not item.get("content"):
        raise SystemExit(1)
PY
    then
      return
    fi
    sleep "${DOCUMENT_RETRIEVAL_SLEEP_SECONDS}"
    attempt=$((attempt + 1))
  done
  fail "Active document evidence was not returned by retrieval proof"
}

reconcile_until_deleted() {
  local source_id="$1"
  local attempt=1
  while [[ "${attempt}" -le "${DOCUMENT_RECONCILE_ATTEMPTS}" ]]; do
    platform_http POST "${DOCUMENT_API_BASE}/sources/${source_id}/reconcile" '{}' \
      -H "Idempotency-Key: $(new_key "delete-reconcile-${attempt}")"
    assert_status_any "Document delete reconciliation" 200
    platform_http GET "${DOCUMENT_API_BASE}/sources/${source_id}" ""
    assert_status_any "Document detail during delete reconciliation" 200
    if JSON_BODY="${HTTP_BODY}" python3 - <<'PY'
import json
import os

data = json.loads(os.environ["JSON_BODY"])
source = data.get("source") or {}
manifests = data.get("manifests") or []
if source.get("status") != "DELETED" or source.get("activeVersion") is not None:
    raise SystemExit(1)
if any(item.get("state") != "DELETED" for item in manifests):
    raise SystemExit(1)
for manifest in manifests:
    delete_work = [item for item in (manifest.get("work") or []) if item.get("operation") == "DELETE"]
    if not delete_work or any(not item.get("terminal") or not item.get("successful") for item in delete_work):
        raise SystemExit(1)
PY
    then
      return
    fi
    sleep "${DOCUMENT_RECONCILE_SLEEP_SECONDS}"
    attempt=$((attempt + 1))
  done
  fail "Document index deletion did not reach a successful terminal state in time"
}

retrieval_until_absent() {
  local source_id="$1"
  local query="$2"
  local attempt=1
  while [[ "${attempt}" -le "${DOCUMENT_RETRIEVAL_ATTEMPTS}" ]]; do
    platform_http POST "${DOCUMENT_API_BASE}/retrieval-proof" "$(json_payload "" "" "${query}" retrieval)"
    assert_status_any "Post-delete retrieval proof" 200
    if EXPECTED_SOURCE_ID="${source_id}" JSON_BODY="${HTTP_BODY}" python3 - <<'PY'
import json
import os

data = json.loads(os.environ["JSON_BODY"])
source_id = os.environ["EXPECTED_SOURCE_ID"]
if any(item.get("sourceId") == source_id for item in (data.get("evidence") or [])):
    raise SystemExit(1)
PY
    then
      return
    fi
    sleep "${DOCUMENT_RETRIEVAL_SLEEP_SECONDS}"
    attempt=$((attempt + 1))
  done
  fail "Deleted document evidence remained retrievable"
}

verify_object_lifecycle() {
  local contract="$1"
  local object_reference="$2"
  local retrieval_query="$3"

  discover_object "${object_reference}"
  local original_fingerprint="${DISCOVERED_FINGERPRINT}"
  pass "${contract} object is discoverable inside the bounded connector scope"

  local register_body
  register_body="$(json_payload "${object_reference}" "" "" register)"
  platform_http POST "${DOCUMENT_API_BASE}/sources" "${register_body}" \
    -H "Idempotency-Key: $(new_key "${contract}-register")"
  assert_status_any "${contract} source registration" 200 201
  local source_id
  source_id="$(json_extract 'data.get("sourceId")')"
  [[ -n "${source_id}" ]] || fail "${contract} registration did not return a sourceId"

  local refresh_key
  refresh_key="$(new_key "${contract}-refresh")"
  platform_http POST "${DOCUMENT_API_BASE}/sources/${source_id}/refresh" '{}' -H "Idempotency-Key: ${refresh_key}"
  assert_status_any "${contract} source refresh" 200
  json_assert "${contract} refresh outcome" 'assert data.get("outcome") in {"CHANGED", "UNCHANGED"}'
  platform_http POST "${DOCUMENT_API_BASE}/sources/${source_id}/refresh" '{}' -H "Idempotency-Key: ${refresh_key}"
  assert_status_any "${contract} source refresh replay" 200
  json_assert "${contract} refresh idempotency" 'assert data.get("outcome") == "IDEMPOTENT_REPLAY"'

  platform_http GET "${DOCUMENT_API_BASE}/sources/${source_id}" ""
  assert_status_any "${contract} source detail before preview" 200
  local manifests_before
  manifests_before="$(json_extract 'len(data.get("manifests") or [])')"

  platform_http GET "${DOCUMENT_API_BASE}/sources/${source_id}/preview" ""
  assert_status_any "${contract} bounded preview" 200
  EXPECTED_SOURCE_ID="${source_id}" JSON_BODY="${HTTP_BODY}" python3 - <<'PY'
import json
import os

data = json.loads(os.environ["JSON_BODY"])
assert (data.get("source") or {}).get("sourceId") == os.environ["EXPECTED_SOURCE_ID"]
assert data.get("documentCount", 0) > 0
assert data.get("chunkCount", 0) > 0
chunks = data.get("chunks") or []
assert 0 < len(chunks) <= 100
for chunk in chunks:
    assert chunk.get("chunkId")
    assert chunk.get("contentFingerprint")
    assert len(chunk.get("contentPreview") or "") <= 2000
PY
  platform_http GET "${DOCUMENT_API_BASE}/sources/${source_id}" ""
  assert_status_any "${contract} source detail after preview" 200
  local manifests_after
  manifests_after="$(json_extract 'len(data.get("manifests") or [])')"
  [[ "${manifests_before}" == "${manifests_after}" ]] \
    || fail "${contract} preview created durable manifest/queue state"
  pass "${contract} preview is bounded and side-effect free"

  local index_key
  index_key="$(new_key "${contract}-index")"
  platform_http POST "${DOCUMENT_API_BASE}/sources/${source_id}/index" '{}' -H "Idempotency-Key: ${index_key}"
  assert_status_any "${contract} index submission" 202
  json_assert "${contract} index submission outcome" 'assert data.get("outcome") in {"ACCEPTED", "UNCHANGED"}'
  platform_http POST "${DOCUMENT_API_BASE}/sources/${source_id}/index" '{}' -H "Idempotency-Key: ${index_key}"
  assert_status_any "${contract} index replay" 202
  json_assert "${contract} index idempotency" 'assert data.get("outcome") == "IDEMPOTENT_REPLAY"'

  reconcile_until_active "${source_id}"
  retrieval_until_present "${source_id}" "${retrieval_query}"
  pass "${contract} index reached terminal success and returned active source/version/chunk evidence"

  if is_true "${DOCUMENT_CLEANUP_INDEX}"; then
    local delete_key
    delete_key="$(new_key "${contract}-delete-index")"
    platform_http DELETE "${DOCUMENT_API_BASE}/sources/${source_id}" '' -H "Idempotency-Key: ${delete_key}"
    assert_status_any "${contract} index removal" 202
    json_assert "${contract} delete outcome" 'assert data.get("outcome") in {"ACCEPTED", "DELETE_PENDING", "UNCHANGED"}'
    platform_http DELETE "${DOCUMENT_API_BASE}/sources/${source_id}" '' -H "Idempotency-Key: ${delete_key}"
    assert_status_any "${contract} index removal replay" 202
    json_assert "${contract} delete idempotency" 'assert data.get("outcome") == "IDEMPOTENT_REPLAY"'

    reconcile_until_deleted "${source_id}"
    retrieval_until_absent "${source_id}" "${retrieval_query}"
    discover_object "${object_reference}"
    [[ "${DISCOVERED_FINGERPRINT}" == "${original_fingerprint}" ]] \
      || fail "${contract} customer source revision changed during derived-index removal"
    pass "${contract} exact index removal preserved the customer source object"
  fi
}

require_cmd curl
require_cmd python3
PLATFORM_API_KEY="$(resolve_secret_value PLATFORM_API_KEY)"
require_value PLATFORM_BASE_URL
require_value DOCUMENT_DEPLOYMENT_ID
require_value DOCUMENT_DATASET_ID
require_value DOCUMENT_TEXT_OBJECT_REFERENCE
require_value DOCUMENT_TEXT_RETRIEVAL_QUERY
require_value DOCUMENT_JSON_OBJECT_REFERENCE
require_value DOCUMENT_JSON_RETRIEVAL_QUERY

[[ "${DOCUMENT_TEXT_OBJECT_REFERENCE,,}" == *.txt ]] || fail "Text verification object must end in .txt"
[[ "${DOCUMENT_JSON_OBJECT_REFERENCE,,}" == *.json ]] || fail "JSON verification object must end in .json"

PLATFORM_BASE_URL="$(trim_slash "${PLATFORM_BASE_URL}")"
TMP_DIR="$(mktemp -d)"
PLATFORM_COOKIE_JAR="${TMP_DIR}/platform-cookie.txt"
RUN_ID="$(date -u +%Y%m%d%H%M%S)-$$"
trap 'rm -rf "${TMP_DIR}"' EXIT

platform_login
DOCUMENT_API_BASE="${PLATFORM_BASE_URL}/api/deployments/${DOCUMENT_DEPLOYMENT_ID}/document-knowledge"

platform_http GET "${PLATFORM_BASE_URL}/api/deployments/${DOCUMENT_DEPLOYMENT_ID}/workspace" ""
assert_status_any "Document deployment workspace" 200
json_assert "Document capability workspace projection" \
  'assert data.get("documentKnowledgeConfigured") is True\nassert data.get("documentKnowledgeLive") is True'

platform_http GET "${DOCUMENT_API_BASE}/connector" ""
assert_status_any "Document source connector status" 200
EXPECTED_CONNECTOR_TYPE="${DOCUMENT_EXPECTED_CONNECTOR_TYPE}" JSON_BODY="${HTTP_BODY}" python3 - <<'PY'
import json
import os

data = json.loads(os.environ["JSON_BODY"])
assert data.get("ready") is True
assert data.get("bindingRef")
assert data.get("scopeDigest")
expected = os.environ.get("EXPECTED_CONNECTOR_TYPE", "").strip()
if expected:
    assert data.get("connectorType") == expected
PY
pass "Deployment-local source connector is ready"

platform_http GET "${DOCUMENT_API_BASE}/retention" ""
assert_status_any "Document retention status" 200
json_assert "Document retention policy" \
  'assert data.get("evidenceRetention")\nassert data.get("commandRetention")\nassert int(data.get("batchSize", 0)) > 0\nlast = data.get("lastCleanup")\nassert last is None or last.get("customerSourceObjectsDeleted") is False'
pass "Deployment-local retention policy is visible and source-preserving"

# The Platform must reject raw file-content relay before a runtime call is made.
raw_content_payload="$(RAW_OBJECT="${DOCUMENT_TEXT_OBJECT_REFERENCE}" DATASET="${DOCUMENT_DATASET_ID}" python3 - <<'PY'
import json
import os
print(json.dumps({
    "datasetId": os.environ["DATASET"],
    "objectReference": os.environ["RAW_OBJECT"],
    "visibility": "internal",
    "content": "raw bytes must never cross the Platform control plane",
}))
PY
)"
platform_http POST "${DOCUMENT_API_BASE}/sources" "${raw_content_payload}" \
  -H "Idempotency-Key: $(new_key raw-content-rejection)"
assert_status_any "Raw document-content relay rejection" 400
pass "Platform control plane rejects raw document content"

verify_object_lifecycle "text" "${DOCUMENT_TEXT_OBJECT_REFERENCE}" "${DOCUMENT_TEXT_RETRIEVAL_QUERY}"
verify_object_lifecycle "json" "${DOCUMENT_JSON_OBJECT_REFERENCE}" "${DOCUMENT_JSON_RETRIEVAL_QUERY}"

pass "Document Knowledge Operations verification completed"
