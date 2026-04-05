#!/usr/bin/env bash
set -euo pipefail

# Resolve canonical verification rollout deployment ids from the live platform.
#
# Default usage:
#   PLATFORM_BASE_URL="https://<platform>.up.railway.app" \
#   PLATFORM_LOGIN_EMAIL="admin@example.com" \
#   PLATFORM_LOGIN_PASSWORD="<password>" \
#   ./scripts/resolve-verification-rollouts.sh
#
# Optional rollout ensure/recreate:
#   REQUIRED_ROLLOUT_KEYS="ecommerce,qdrant,pinecone,milvus,weaviate" \
#   ALLOW_ROLLOUT_MUTATION=true \
#   ./scripts/resolve-verification-rollouts.sh

PLATFORM_BASE_URL="${PLATFORM_BASE_URL:-${PLATFORM_PUBLIC_BASE_URL:-}}"
PLATFORM_API_KEY_HEADER="${PLATFORM_API_KEY_HEADER:-X-PLATFORM-API-KEY}"
PLATFORM_API_KEY="${PLATFORM_API_KEY:-}"
PLATFORM_COOKIE="${PLATFORM_COOKIE:-}"
PLATFORM_LOGIN_EMAIL="${PLATFORM_LOGIN_EMAIL:-}"
PLATFORM_LOGIN_PASSWORD="${PLATFORM_LOGIN_PASSWORD:-}"

REQUIRED_ROLLOUT_KEYS="${REQUIRED_ROLLOUT_KEYS-ecommerce,qdrant,pinecone,milvus,weaviate}"
ALLOW_ROLLOUT_MUTATION="${ALLOW_ROLLOUT_MUTATION:-false}"
WAIT_FOR_VERIFICATION_READY="${WAIT_FOR_VERIFICATION_READY:-true}"
ROLLOUT_READY_TIMEOUT_ATTEMPTS="${ROLLOUT_READY_TIMEOUT_ATTEMPTS:-120}"
ROLLOUT_READY_TIMEOUT_SLEEP_SECONDS="${ROLLOUT_READY_TIMEOUT_SLEEP_SECONDS:-15}"

HTTP_STATUS=""
HTTP_BODY=""
TMP_DIR=""
PLATFORM_COOKIE_JAR=""
RESOLVED_JSON=""

require_cmd() {
  local cmd="$1"
  if ! command -v "${cmd}" >/dev/null 2>&1; then
    echo "Missing required command: ${cmd}" >&2
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

assert_status() {
  local expected="$1"
  local label="$2"
  if [[ "${HTTP_STATUS}" != "${expected}" ]]; then
    echo "---- ${label} ----" >&2
    echo "HTTP ${HTTP_STATUS}" >&2
    echo "${HTTP_BODY}" >&2
    echo "------------------" >&2
    echo "FAIL: ${label} (expected HTTP ${expected})" >&2
    exit 1
  fi
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

  local status
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

  HTTP_STATUS="${status}"
  HTTP_BODY="$(cat "${tmp}")"
  rm -f "${tmp}"
}

platform_login() {
  if [[ -n "${PLATFORM_API_KEY}" || -n "${PLATFORM_COOKIE}" ]]; then
    return 0
  fi
  if [[ -z "${PLATFORM_LOGIN_EMAIL}" || -z "${PLATFORM_LOGIN_PASSWORD}" ]]; then
    echo "Rollout resolution requires PLATFORM_API_KEY, PLATFORM_COOKIE, or PLATFORM_LOGIN_EMAIL/PLATFORM_LOGIN_PASSWORD." >&2
    exit 2
  fi

  local tmp
  tmp="$(mktemp)"
  local payload
  payload="$(mktemp)"
  cat > "${payload}" <<EOF
{"email":"${PLATFORM_LOGIN_EMAIL}","password":"${PLATFORM_LOGIN_PASSWORD}"}
EOF
  local status
  status="$(
    curl -sS -o "${tmp}" -w "%{http_code}" -c "${PLATFORM_COOKIE_JAR}" \
      -H "Content-Type: application/json" \
      --data "@${payload}" \
      "${PLATFORM_BASE_URL}/api/platform/auth/login" || true
  )"
  rm -f "${payload}"
  if [[ "${status}" != "200" ]]; then
    echo "Platform login failed (HTTP ${status})." >&2
    cat "${tmp}" >&2
    rm -f "${tmp}"
    exit 1
  fi
  rm -f "${tmp}"
}

normalize_required_keys() {
  REQUIRED_KEYS_JSON="$(
    REQUIRED_ROLLOUT_KEYS="${REQUIRED_ROLLOUT_KEYS}" python3 - <<'PY'
import json
import os

raw = os.environ.get("REQUIRED_ROLLOUT_KEYS", "")
keys = []
for part in raw.split(","):
    value = part.strip()
    if value and value not in keys:
        keys.append(value)
print(json.dumps(keys))
PY
  )"
}

build_rollout_payload() {
  local keys_json="$1"
  TARGET_KEYS_JSON="${keys_json}" python3 - <<'PY'
import json
import os

keys = json.loads(os.environ["TARGET_KEYS_JSON"])
print(json.dumps({"rolloutKeys": keys}))
PY
}

resolve_inventory_json() {
  INVENTORY_BODY="${HTTP_BODY}" REQUIRED_KEYS_JSON="${REQUIRED_KEYS_JSON}" MUTATED_KEYS_JSON="${MUTATED_KEYS_JSON:-[]}" python3 - <<'PY'
import json
import os

inventory = json.loads(os.environ.get("INVENTORY_BODY", "{}") or "{}")
required_keys = json.loads(os.environ.get("REQUIRED_KEYS_JSON", "[]") or "[]")
mutated_keys = json.loads(os.environ.get("MUTATED_KEYS_JSON", "[]") or "[]")

items = inventory.get("items") or []
item_map = {item.get("key"): item for item in items if item.get("key")}

def item_summary(key):
    item = item_map.get(key) or {}
    return {
        "deploymentId": item.get("deploymentId"),
        "exists": bool(item.get("exists")),
        "archived": bool(item.get("archived")),
        "verificationReady": bool(item.get("verificationReady")),
        "deploymentStatus": item.get("deploymentStatus"),
        "readinessMessage": item.get("readinessMessage"),
        "verificationProfile": item.get("verificationProfile"),
    }

resolved = {
    "summaryMessage": inventory.get("summaryMessage"),
    "requiredKeys": required_keys,
    "mutatedKeys": mutated_keys,
    "allRequiredReady": True,
    "unreadyKeys": [],
    "items": {key: item_summary(key) for key in ["ecommerce", "qdrant", "pinecone", "milvus", "weaviate"]},
}

for required in required_keys:
    item = item_map.get(required) or {}
    if not item.get("verificationReady"):
        resolved["allRequiredReady"] = False
        resolved["unreadyKeys"].append(required)

resolved["ecommerceDeploymentId"] = (item_map.get("ecommerce") or {}).get("deploymentId")
resolved["qdrantDeploymentId"] = (item_map.get("qdrant") or {}).get("deploymentId")
resolved["pineconeDeploymentId"] = (item_map.get("pinecone") or {}).get("deploymentId")
resolved["milvusDeploymentId"] = (item_map.get("milvus") or {}).get("deploymentId")
resolved["weaviateDeploymentId"] = (item_map.get("weaviate") or {}).get("deploymentId")

print(json.dumps(resolved))
PY
}

resolve_unready_csv() {
  RESOLVED_JSON="${RESOLVED_JSON}" python3 - <<'PY'
import json
import os

payload = json.loads(os.environ.get("RESOLVED_JSON", "{}") or "{}")
print(",".join(payload.get("unreadyKeys") or []))
PY
}

write_github_outputs() {
  local json="$1"
  if [[ -z "${GITHUB_OUTPUT:-}" ]]; then
    return 0
  fi
  RESOLVED_JSON="${json}" python3 - <<'PY'
import json
import os

payload = json.loads(os.environ["RESOLVED_JSON"])
lines = [
    ("summary_message", payload.get("summaryMessage") or ""),
    ("all_required_ready", "true" if payload.get("allRequiredReady") else "false"),
    ("unready_keys", ",".join(payload.get("unreadyKeys") or [])),
    ("mutated_keys", ",".join(payload.get("mutatedKeys") or [])),
    ("ecommerce_deployment_id", payload.get("ecommerceDeploymentId") or ""),
    ("qdrant_deployment_id", payload.get("qdrantDeploymentId") or ""),
    ("pinecone_deployment_id", payload.get("pineconeDeploymentId") or ""),
    ("milvus_deployment_id", payload.get("milvusDeploymentId") or ""),
    ("weaviate_deployment_id", payload.get("weaviateDeploymentId") or ""),
]

with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as handle:
    for key, value in lines:
        handle.write(f"{key}<<__RESOLVE__\n{value}\n__RESOLVE__\n")
PY
}

cleanup() {
  rm -rf "${TMP_DIR}"
}

require_cmd curl
require_cmd python3

TMP_DIR="$(mktemp -d)"
trap cleanup EXIT
PLATFORM_COOKIE_JAR="${TMP_DIR}/platform-cookies.txt"
touch "${PLATFORM_COOKIE_JAR}"
PLATFORM_BASE_URL="$(trim_slash "${PLATFORM_BASE_URL}")"

if [[ -z "${PLATFORM_BASE_URL}" ]]; then
  echo "Missing PLATFORM_BASE_URL." >&2
  exit 2
fi

normalize_required_keys
platform_login

platform_http GET "${PLATFORM_BASE_URL}/api/deployments/verification-rollouts"
assert_status 200 "canonical rollout inventory"
MUTATED_KEYS_JSON='[]'
RESOLVED_JSON="$(resolve_inventory_json)"
UNREADY_KEYS_CSV="$(resolve_unready_csv)"

if [[ -n "${UNREADY_KEYS_CSV}" ]]; then
  if [[ "${ALLOW_ROLLOUT_MUTATION}" != "true" ]]; then
    echo "Required canonical rollout keys are not ready: ${UNREADY_KEYS_CSV}" >&2
    echo "${RESOLVED_JSON}" >&2
    exit 1
  fi

  MUTATED_KEYS_JSON="$(
    UNREADY_KEYS_CSV="${UNREADY_KEYS_CSV}" python3 - <<'PY'
import json
import os

keys = [part.strip() for part in os.environ.get("UNREADY_KEYS_CSV", "").split(",") if part.strip()]
print(json.dumps(keys))
PY
  )"
  platform_http POST "${PLATFORM_BASE_URL}/api/deployments/verification-rollouts/recreate" "$(build_rollout_payload "${MUTATED_KEYS_JSON}")"
  assert_status 200 "canonical rollout recreate"

  if [[ "${WAIT_FOR_VERIFICATION_READY}" == "true" ]]; then
    attempt=1
    while [[ "${attempt}" -le "${ROLLOUT_READY_TIMEOUT_ATTEMPTS}" ]]; do
      platform_http GET "${PLATFORM_BASE_URL}/api/deployments/verification-rollouts"
      assert_status 200 "canonical rollout inventory"
      RESOLVED_JSON="$(resolve_inventory_json)"
      UNREADY_KEYS_CSV="$(resolve_unready_csv)"
      if [[ -z "${UNREADY_KEYS_CSV}" ]]; then
        break
      fi
      sleep "${ROLLOUT_READY_TIMEOUT_SLEEP_SECONDS}"
      attempt=$((attempt + 1))
    done
    if [[ -n "${UNREADY_KEYS_CSV}" ]]; then
      echo "Timed out waiting for canonical rollout keys to become ready: ${UNREADY_KEYS_CSV}" >&2
      echo "${RESOLVED_JSON}" >&2
      exit 1
    fi
  else
    platform_http GET "${PLATFORM_BASE_URL}/api/deployments/verification-rollouts"
    assert_status 200 "canonical rollout inventory"
    RESOLVED_JSON="$(resolve_inventory_json)"
  fi
fi

write_github_outputs "${RESOLVED_JSON}"
printf '%s\n' "${RESOLVED_JSON}"
