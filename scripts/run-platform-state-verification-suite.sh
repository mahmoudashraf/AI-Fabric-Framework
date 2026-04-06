#!/usr/bin/env bash
set -euo pipefail

# Sequential platform state verification runner.
#
# Uses the same underlying verification scripts as GitHub Actions, but drives
# them from one shell entrypoint in a fixed sequence.
#
# Example:
#   PLATFORM_BASE_URL="https://<platform>.up.railway.app" \
#   PLATFORM_LOGIN_EMAIL="admin@example.com" \
#   PLATFORM_LOGIN_PASSWORD="..." \
#   CONNECTOR_API_KEY="..." \
#   APP_ADMIN_API_KEY="..." \
#   ./scripts/run-platform-state-verification-suite.sh

PLATFORM_BASE_URL="${PLATFORM_BASE_URL:-${PLATFORM_PUBLIC_BASE_URL:-}}"
PLATFORM_API_KEY="${PLATFORM_API_KEY:-}"
PLATFORM_LOGIN_EMAIL="${PLATFORM_LOGIN_EMAIL:-}"
PLATFORM_LOGIN_PASSWORD="${PLATFORM_LOGIN_PASSWORD:-}"

CONNECTOR_API_KEY="${CONNECTOR_API_KEY:-}"
APP_ADMIN_API_KEY="${APP_ADMIN_API_KEY:-}"

RUN_PLATFORM_CODE_CHECKS="${RUN_PLATFORM_CODE_CHECKS:-true}"
RUN_ECOMMERCE_DEPLOYMENT_CHECKS="${RUN_ECOMMERCE_DEPLOYMENT_CHECKS:-true}"
RUN_VECTOR_DEPLOYMENT_CHECKS="${RUN_VECTOR_DEPLOYMENT_CHECKS:-true}"
RUN_MANAGED_PROVIDER_CHECKS="${RUN_MANAGED_PROVIDER_CHECKS:-true}"
ENSURE_CANONICAL_ROLLOUTS="${ENSURE_CANONICAL_ROLLOUTS:-true}"
VERIFY_WRITE="${VERIFY_WRITE:-false}"

ECOMMERCE_DEPLOYMENT_ID="${ECOMMERCE_DEPLOYMENT_ID:-}"
QDRANT_DEPLOYMENT_ID="${QDRANT_DEPLOYMENT_ID:-}"
PINECONE_DEPLOYMENT_ID="${PINECONE_DEPLOYMENT_ID:-}"
MILVUS_DEPLOYMENT_ID="${MILVUS_DEPLOYMENT_ID:-}"
WEAVIATE_DEPLOYMENT_ID="${WEAVIATE_DEPLOYMENT_ID:-}"
TMP_DIR=""

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
  rm -rf "${TMP_DIR}"
}

run_step() {
  local label="$1"
  shift
  echo ""
  echo "== ${label} =="
  "$@"
}

PLATFORM_BASE_URL="$(resolve_secret_value PLATFORM_BASE_URL)"
PLATFORM_API_KEY="$(resolve_secret_value PLATFORM_API_KEY)"
PLATFORM_LOGIN_EMAIL="$(resolve_secret_value PLATFORM_LOGIN_EMAIL)"
PLATFORM_LOGIN_PASSWORD="$(resolve_secret_value PLATFORM_LOGIN_PASSWORD)"
CONNECTOR_API_KEY="$(resolve_secret_value CONNECTOR_API_KEY)"
APP_ADMIN_API_KEY="$(resolve_secret_value APP_ADMIN_API_KEY)"
TMP_DIR="$(mktemp -d)"
trap cleanup EXIT

require_cmd bash
require_cmd python3

if [[ "${RUN_PLATFORM_CODE_CHECKS}" == "true" ]]; then
  require_cmd mvn
  require_cmd npm
fi
if [[ "${RUN_ECOMMERCE_DEPLOYMENT_CHECKS}" == "true" || "${RUN_VECTOR_DEPLOYMENT_CHECKS}" == "true" ]]; then
  if [[ -z "${PLATFORM_BASE_URL}" ]]; then
    echo "Set PLATFORM_BASE_URL when deployment checks are enabled." >&2
    exit 2
  fi
  if [[ -z "${CONNECTOR_API_KEY}" ]]; then
    echo "Set CONNECTOR_API_KEY when deployment checks are enabled." >&2
    exit 2
  fi
fi
if [[ ("${RUN_ECOMMERCE_DEPLOYMENT_CHECKS}" == "true" || "${RUN_VECTOR_DEPLOYMENT_CHECKS}" == "true") && -z "${PLATFORM_API_KEY}" && ( -z "${PLATFORM_LOGIN_EMAIL}" || -z "${PLATFORM_LOGIN_PASSWORD}" ) ]]; then
  echo "Set PLATFORM_API_KEY or PLATFORM_LOGIN_EMAIL and PLATFORM_LOGIN_PASSWORD." >&2
  exit 2
fi

resolved_json='{}'
if [[ "${RUN_ECOMMERCE_DEPLOYMENT_CHECKS}" == "true" || "${RUN_VECTOR_DEPLOYMENT_CHECKS}" == "true" ]]; then
  required_rollout_keys=""
  if [[ "${RUN_ECOMMERCE_DEPLOYMENT_CHECKS}" == "true" && -z "${ECOMMERCE_DEPLOYMENT_ID}" ]]; then
    required_rollout_keys="ecommerce"
  fi
  if [[ "${RUN_VECTOR_DEPLOYMENT_CHECKS}" == "true" ]]; then
    for vector_key in qdrant pinecone milvus weaviate; do
      override_name="$(printf '%s' "${vector_key}" | tr '[:lower:]' '[:upper:]')_DEPLOYMENT_ID"
      if [[ -n "${!override_name:-}" ]]; then
        continue
      fi
      if [[ -n "${required_rollout_keys}" ]]; then
        required_rollout_keys="${required_rollout_keys},${vector_key}"
      else
        required_rollout_keys="${vector_key}"
      fi
    done
  fi
  if [[ -n "${required_rollout_keys}" ]]; then
    run_step "Resolve rollout inventory" env \
      PLATFORM_BASE_URL="${PLATFORM_BASE_URL}" \
      PLATFORM_API_KEY="${PLATFORM_API_KEY}" \
      PLATFORM_LOGIN_EMAIL="${PLATFORM_LOGIN_EMAIL}" \
      PLATFORM_LOGIN_PASSWORD="${PLATFORM_LOGIN_PASSWORD}" \
      REQUIRED_ROLLOUT_KEYS="${required_rollout_keys}" \
      ALLOW_ROLLOUT_MUTATION="${ENSURE_CANONICAL_ROLLOUTS}" \
      WAIT_FOR_VERIFICATION_READY=true \
      ROLLOUT_OUTPUT_FILE="${TMP_DIR}/rollouts.json" \
      bash -o pipefail -c 'bash scripts/resolve-verification-rollouts.sh | tee "${ROLLOUT_OUTPUT_FILE}" >/dev/null'
    resolved_json="$(cat "${TMP_DIR}/rollouts.json")"
    if [[ -z "${resolved_json}" ]]; then
      echo "Rollout resolution returned no JSON output." >&2
      exit 1
    fi
  fi
fi

resolved_ids="$(
RESOLVED_JSON="${resolved_json}" \
ECOMMERCE_DEPLOYMENT_ID="${ECOMMERCE_DEPLOYMENT_ID}" \
QDRANT_DEPLOYMENT_ID="${QDRANT_DEPLOYMENT_ID}" \
PINECONE_DEPLOYMENT_ID="${PINECONE_DEPLOYMENT_ID}" \
MILVUS_DEPLOYMENT_ID="${MILVUS_DEPLOYMENT_ID}" \
WEAVIATE_DEPLOYMENT_ID="${WEAVIATE_DEPLOYMENT_ID}" \
python3 - <<'PY'
import json
import os

payload = json.loads(os.environ.get("RESOLVED_JSON", "{}") or "{}")

def choose(name, resolved):
    return os.environ.get(name, "") or (resolved or "")

values = {
    "ECOMMERCE_DEPLOYMENT_ID": choose("ECOMMERCE_DEPLOYMENT_ID", payload.get("ecommerceDeploymentId")),
    "QDRANT_DEPLOYMENT_ID": choose("QDRANT_DEPLOYMENT_ID", payload.get("qdrantDeploymentId")),
    "PINECONE_DEPLOYMENT_ID": choose("PINECONE_DEPLOYMENT_ID", payload.get("pineconeDeploymentId")),
    "MILVUS_DEPLOYMENT_ID": choose("MILVUS_DEPLOYMENT_ID", payload.get("milvusDeploymentId")),
    "WEAVIATE_DEPLOYMENT_ID": choose("WEAVIATE_DEPLOYMENT_ID", payload.get("weaviateDeploymentId")),
}

for key, value in values.items():
    print(f"{key}={value}")
PY
)"
while IFS='=' read -r key value; do
  case "${key}" in
    ECOMMERCE_DEPLOYMENT_ID) ECOMMERCE_DEPLOYMENT_ID="${value}" ;;
    QDRANT_DEPLOYMENT_ID) QDRANT_DEPLOYMENT_ID="${value}" ;;
    PINECONE_DEPLOYMENT_ID) PINECONE_DEPLOYMENT_ID="${value}" ;;
    MILVUS_DEPLOYMENT_ID) MILVUS_DEPLOYMENT_ID="${value}" ;;
    WEAVIATE_DEPLOYMENT_ID) WEAVIATE_DEPLOYMENT_ID="${value}" ;;
  esac
done <<< "${resolved_ids}"

if [[ "${RUN_PLATFORM_CODE_CHECKS}" == "true" ]]; then
  run_step "Platform backend tests" mvn -f Platfrom/backend/pom.xml test -DskipITs
  run_step "ai-fabric-product tests" mvn -f ai-fabric-product/pom.xml test
  run_step "Targeted infrastructure tests" mvn -f ai-infrastructure-module/pom.xml \
    -pl ai-infrastructure-data-sync,victor-databases/ai-infrastructure-vector-pinecone,victor-databases/ai-infrastructure-vector-qdrant,victor-databases/ai-infrastructure-vector-weaviate,victor-databases/ai-infrastructure-vector-milvus \
    -am test -DskipITs
  run_step "Platform UI build" bash -c 'cd Platfrom/ui && npm ci && npm run build'
  run_step "Shell syntax checks" bash -c 'bash -n scripts/verify-ecommerce-deployment.sh && bash -n scripts/verify-vector-deployment.sh && bash -n scripts/verify-managed-vector-providers.sh && bash -n scripts/verify-platform-admin-regression.sh && bash -n scripts/resolve-verification-rollouts.sh && bash -n scripts/run-platform-deployment-verification.sh && bash -n scripts/run-platform-state-verification-suite.sh'
fi

if [[ "${RUN_ECOMMERCE_DEPLOYMENT_CHECKS}" == "true" ]]; then
  if [[ -z "${ECOMMERCE_DEPLOYMENT_ID}" ]]; then
    echo "Missing ecommerce deployment id after rollout resolution." >&2
    exit 1
  fi
  run_step "Verify ecommerce deployment ${ECOMMERCE_DEPLOYMENT_ID}" env \
    PLATFORM_BASE_URL="${PLATFORM_BASE_URL}" \
    PLATFORM_API_KEY="${PLATFORM_API_KEY}" \
    PLATFORM_LOGIN_EMAIL="${PLATFORM_LOGIN_EMAIL}" \
    PLATFORM_LOGIN_PASSWORD="${PLATFORM_LOGIN_PASSWORD}" \
    PLATFORM_DEPLOYMENT_ID="${ECOMMERCE_DEPLOYMENT_ID}" \
    VERIFICATION_PROFILE="ecommerce" \
    VERIFY_WRITE="${VERIFY_WRITE}" \
    CONNECTOR_API_KEY="${CONNECTOR_API_KEY}" \
    APP_ADMIN_API_KEY="${APP_ADMIN_API_KEY}" \
    bash scripts/run-platform-deployment-verification.sh
fi

if [[ "${RUN_VECTOR_DEPLOYMENT_CHECKS}" == "true" ]]; then
  for deployment_id in "${QDRANT_DEPLOYMENT_ID}" "${PINECONE_DEPLOYMENT_ID}" "${MILVUS_DEPLOYMENT_ID}" "${WEAVIATE_DEPLOYMENT_ID}"; do
    if [[ -z "${deployment_id}" ]]; then
      echo "Missing vector deployment id after rollout resolution." >&2
      exit 1
    fi
    run_step "Verify vector deployment ${deployment_id}" env \
      PLATFORM_BASE_URL="${PLATFORM_BASE_URL}" \
      PLATFORM_API_KEY="${PLATFORM_API_KEY}" \
      PLATFORM_LOGIN_EMAIL="${PLATFORM_LOGIN_EMAIL}" \
      PLATFORM_LOGIN_PASSWORD="${PLATFORM_LOGIN_PASSWORD}" \
      PLATFORM_DEPLOYMENT_ID="${deployment_id}" \
      VERIFICATION_PROFILE="vector" \
      VERIFY_WRITE="${VERIFY_WRITE}" \
      CONNECTOR_API_KEY="${CONNECTOR_API_KEY}" \
      APP_ADMIN_API_KEY="${APP_ADMIN_API_KEY}" \
      bash scripts/run-platform-deployment-verification.sh
  done
fi

if [[ "${RUN_MANAGED_PROVIDER_CHECKS}" == "true" ]]; then
  run_step "Managed vector provider verification" bash scripts/verify-managed-vector-providers.sh
fi

echo ""
echo "Platform state verification suite completed."
