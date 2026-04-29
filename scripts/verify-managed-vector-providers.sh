#!/usr/bin/env bash
set -euo pipefail

# Manual verification script for managed/external vector provider control planes.
#
# Covers the providers currently verified through the platform:
# - Pinecone: control plane access, existing managed index lookup, optional temp index create/delete
# - Qdrant Cloud: control plane access, account/region/package resolution, existing managed cluster lookup,
#   optional temp database API key create/delete, optional temp cluster create/delete
# - Zilliz Cloud: control plane access, existing managed cluster lookup, optional temp cluster create/delete
# - Weaviate Cloud: readiness and metadata probes against the current external cluster
#
# This script does not delete live Railway services or platform deployments.
# Cleanup only applies to ephemeral provider resources created by this script.

require_cmd() {
  local cmd="$1"
  if ! command -v "${cmd}" >/dev/null 2>&1; then
    echo "Missing required command: ${cmd}"
    exit 2
  fi
}

require_cmd curl
require_cmd jq
require_cmd python3

RUN_ID_SUFFIX="${GITHUB_RUN_ID:-$(date +%s)}"

RUN_PINECONE="${RUN_PINECONE:-true}"
RUN_QDRANT="${RUN_QDRANT:-true}"
RUN_ZILLIZ="${RUN_ZILLIZ:-true}"
RUN_WEAVIATE="${RUN_WEAVIATE:-true}"

CLEANUP_EPHEMERAL_RESOURCES="${CLEANUP_EPHEMERAL_RESOURCES:-true}"

PINECONE_API_KEY="${PINECONE_API_KEY:-}"
PINECONE_CLOUD="${PINECONE_CLOUD:-aws}"
PINECONE_REGION="${PINECONE_REGION:-us-east-1}"
PINECONE_DIMENSIONS="${PINECONE_DIMENSIONS:-1536}"
PINECONE_METRIC="${PINECONE_METRIC:-cosine}"
PINECONE_EXISTING_INDEX_NAME="${PINECONE_EXISTING_INDEX_NAME:-ai-fabric}"
PINECONE_CREATE_EPHEMERAL_INDEX="${PINECONE_CREATE_EPHEMERAL_INDEX:-true}"
PINECONE_EPHEMERAL_INDEX_NAME="${PINECONE_EPHEMERAL_INDEX_NAME:-gha-verify-pc-${RUN_ID_SUFFIX}}"

QDRANT_CLOUD_MANAGEMENT_API_KEY="${QDRANT_CLOUD_MANAGEMENT_API_KEY:-}"
QDRANT_API_KEY="${QDRANT_API_KEY:-}"
QDRANT_HOST="${QDRANT_HOST:-}"
QDRANT_CLOUD_ACCOUNT_ID="${QDRANT_CLOUD_ACCOUNT_ID:-}"
QDRANT_CLOUD_PROVIDER_ID="${QDRANT_CLOUD_PROVIDER_ID:-aws}"
QDRANT_CLOUD_REGION_ID="${QDRANT_CLOUD_REGION_ID:-eu-west-1}"
QDRANT_CLOUD_PACKAGE_ID="${QDRANT_CLOUD_PACKAGE_ID:-}"
QDRANT_EXISTING_CLUSTER_NAME="${QDRANT_EXISTING_CLUSTER_NAME:-}"
QDRANT_CREATE_EPHEMERAL_DB_KEY="${QDRANT_CREATE_EPHEMERAL_DB_KEY:-true}"
QDRANT_CREATE_EPHEMERAL_CLUSTER="${QDRANT_CREATE_EPHEMERAL_CLUSTER:-true}"
QDRANT_EPHEMERAL_DB_KEY_NAME="${QDRANT_EPHEMERAL_DB_KEY_NAME:-gha-verify-qdrant-key-${RUN_ID_SUFFIX}}"
QDRANT_EPHEMERAL_CLUSTER_NAME="${QDRANT_EPHEMERAL_CLUSTER_NAME:-gha-verify-qdrant-${RUN_ID_SUFFIX}}"

ZILLIZ_CLOUD_API_KEY="${ZILLIZ_CLOUD_API_KEY:-}"
ZILLIZ_PROJECT_ID="${ZILLIZ_PROJECT_ID:-proj-a58a34b87ccfe2c80d6ec2}"
ZILLIZ_REGION_ID="${ZILLIZ_REGION_ID:-aws-eu-central-1}"
ZILLIZ_CLUSTER_PLAN="${ZILLIZ_CLUSTER_PLAN:-Serverless}"
ZILLIZ_EXISTING_CLUSTER_NAME="${ZILLIZ_EXISTING_CLUSTER_NAME:-milvus-e2e-49d428ec}"
ZILLIZ_CREATE_EPHEMERAL_CLUSTER="${ZILLIZ_CREATE_EPHEMERAL_CLUSTER:-false}"
ZILLIZ_EPHEMERAL_CLUSTER_NAME="${ZILLIZ_EPHEMERAL_CLUSTER_NAME:-gha-verify-zilliz-${RUN_ID_SUFFIX}}"

WEAVIATE_SCHEME="${WEAVIATE_SCHEME:-https}"
WEAVIATE_HOST="${WEAVIATE_HOST:-${PLATFORM_VERIFICATION_WEAVIATE_HOST:-}}"
WEAVIATE_PORT="${WEAVIATE_PORT:-443}"
WEAVIATE_API_KEY="${WEAVIATE_API_KEY:-}"

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
import pathlib
import sys
print(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
PY
    return
  fi

  printf '%s' "${direct_value}"
}

PINECONE_API_KEY="$(resolve_secret_value PINECONE_API_KEY)"
QDRANT_CLOUD_MANAGEMENT_API_KEY="$(resolve_secret_value QDRANT_CLOUD_MANAGEMENT_API_KEY)"
QDRANT_API_KEY="$(resolve_secret_value QDRANT_API_KEY)"
ZILLIZ_CLOUD_API_KEY="$(resolve_secret_value ZILLIZ_CLOUD_API_KEY)"
WEAVIATE_API_KEY="$(resolve_secret_value WEAVIATE_API_KEY)"

TMP_DIR="$(mktemp -d)"
HTTP_STATUS=""
HTTP_BODY=""
FAILURES=0
CLEANUP_FAILURES=0

CREATED_PINECONE_INDEX=""
CREATED_QDRANT_ACCOUNT_ID=""
CREATED_QDRANT_CLUSTER_ID=""
CREATED_QDRANT_DB_KEY_ID=""
CREATED_ZILLIZ_CLUSTER_ID=""

trim_slash() {
  local value="$1"
  if [[ "${value}" == */ ]]; then
    echo "${value%/}"
  else
    echo "${value}"
  fi
}

QDRANT_HOST="$(trim_slash "${QDRANT_HOST}")"

http_request() {
  local method="$1"
  local url="$2"
  local body="${3:-}"
  shift 3 || true

  local tmp
  tmp="$(mktemp "${TMP_DIR}/http.XXXXXX")"
  local status
  if [[ -n "${body}" ]]; then
    status="$(curl -sS -o "${tmp}" -w "%{http_code}" -X "${method}" "$@" --data "${body}" "${url}" || true)"
  else
    status="$(curl -sS -o "${tmp}" -w "%{http_code}" -X "${method}" "$@" "${url}" || true)"
  fi
  HTTP_STATUS="${status}"
  HTTP_BODY="$(cat "${tmp}")"
  rm -f "${tmp}"
}

pinecone_http() {
  http_request "$1" "$2" "${3:-}" \
    -H "Accept: application/json" \
    -H "Content-Type: application/json" \
    -H "Api-Key: ${PINECONE_API_KEY}" \
    -H "X-Pinecone-Api-Version: 2025-10"
}

qdrant_mgmt_http() {
  http_request "$1" "$2" "${3:-}" \
    -H "Accept: application/json" \
    -H "Content-Type: application/json" \
    -H "Authorization: apikey ${QDRANT_CLOUD_MANAGEMENT_API_KEY}"
}

qdrant_data_http() {
  http_request "$1" "$2" "${3:-}" \
    -H "Accept: application/json" \
    -H "Content-Type: application/json" \
    -H "api-key: ${QDRANT_API_KEY}"
}

zilliz_http() {
  http_request "$1" "$2" "${3:-}" \
    -H "Accept: application/json" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer ${ZILLIZ_CLOUD_API_KEY}"
}

weaviate_http() {
  if [[ -n "${WEAVIATE_API_KEY}" ]]; then
    http_request "$1" "$2" "${3:-}" \
      -H "Accept: application/json" \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer ${WEAVIATE_API_KEY}"
    return
  fi
  http_request "$1" "$2" "${3:-}" \
    -H "Accept: application/json" \
    -H "Content-Type: application/json"
}

require_2xx() {
  local context="$1"
  if [[ ! "${HTTP_STATUS}" =~ ^2 ]]; then
    echo "FAIL: ${context} -> HTTP ${HTTP_STATUS}"
    if [[ -n "${HTTP_BODY}" ]]; then
      echo "${HTTP_BODY}"
    fi
    return 1
  fi
}

wait_until() {
  local attempts="$1"
  local sleep_seconds="$2"
  shift 2

  local i
  for ((i = 1; i <= attempts; i += 1)); do
    if "$@"; then
      return 0
    fi
    sleep "${sleep_seconds}"
  done
  return 1
}

normalized_name() {
  local prefix="$1"
  local suffix="$2"
  python3 - "$prefix" "$suffix" <<'PY'
import re
import sys

prefix = sys.argv[1]
suffix = sys.argv[2]
value = f"{prefix}-{suffix}".lower()
value = re.sub(r"[^a-z0-9-]+", "-", value)
value = re.sub(r"-+", "-", value).strip("-")
print(value[:45] if len(value) > 45 else value)
PY
}

PINECONE_EPHEMERAL_INDEX_NAME="$(normalized_name "gha-pc" "${RUN_ID_SUFFIX}")"
QDRANT_EPHEMERAL_CLUSTER_NAME="$(normalized_name "gha-qdrant" "${RUN_ID_SUFFIX}")"
ZILLIZ_EPHEMERAL_CLUSTER_NAME="$(normalized_name "gha-zilliz" "${RUN_ID_SUFFIX}")"

cleanup_ephemeral_resources() {
  if [[ "${CLEANUP_EPHEMERAL_RESOURCES}" != "true" ]]; then
    rm -rf "${TMP_DIR}"
    return
  fi

  if [[ -n "${CREATED_QDRANT_DB_KEY_ID}" && -n "${CREATED_QDRANT_ACCOUNT_ID}" && -n "${CREATED_QDRANT_CLUSTER_ID}" ]]; then
    echo "Cleanup: deleting temporary Qdrant database API key ${CREATED_QDRANT_DB_KEY_ID}"
    qdrant_mgmt_http DELETE \
      "https://api.cloud.qdrant.io/api/cluster/auth/v2/accounts/${CREATED_QDRANT_ACCOUNT_ID}/database-api-keys/${CREATED_QDRANT_DB_KEY_ID}?cluster_id=${CREATED_QDRANT_CLUSTER_ID}" \
      ""
    if [[ ! "${HTTP_STATUS}" =~ ^2|404$ ]]; then
      CLEANUP_FAILURES=$((CLEANUP_FAILURES + 1))
      echo "WARN: failed to delete temporary Qdrant database API key -> HTTP ${HTTP_STATUS}"
    fi
  fi

  if [[ -n "${CREATED_QDRANT_CLUSTER_ID}" && "${QDRANT_CREATE_EPHEMERAL_CLUSTER}" == "true" && -n "${CREATED_QDRANT_ACCOUNT_ID}" ]]; then
    echo "Cleanup: deleting temporary Qdrant cluster ${CREATED_QDRANT_CLUSTER_ID}"
    qdrant_mgmt_http DELETE \
      "https://api.cloud.qdrant.io/api/cluster/v1/accounts/${CREATED_QDRANT_ACCOUNT_ID}/clusters/${CREATED_QDRANT_CLUSTER_ID}" \
      ""
    if [[ ! "${HTTP_STATUS}" =~ ^2|404$ ]]; then
      CLEANUP_FAILURES=$((CLEANUP_FAILURES + 1))
      echo "WARN: failed to delete temporary Qdrant cluster -> HTTP ${HTTP_STATUS}"
    else
      wait_until 60 5 bash -c '
        curl -sS -o /tmp/qdrant-cleanup.out -w "%{http_code}" \
          -H "Accept: application/json" \
          -H "Content-Type: application/json" \
          -H "Authorization: apikey '"${QDRANT_CLOUD_MANAGEMENT_API_KEY}"'" \
          "https://api.cloud.qdrant.io/api/cluster/v1/accounts/'"${CREATED_QDRANT_ACCOUNT_ID}"'/clusters/'"${CREATED_QDRANT_CLUSTER_ID}"'" \
          | grep -q "^404$"
      ' || true
    fi
  fi

  if [[ -n "${CREATED_PINECONE_INDEX}" ]]; then
    echo "Cleanup: deleting temporary Pinecone index ${CREATED_PINECONE_INDEX}"
    pinecone_http PATCH "https://api.pinecone.io/indexes/${CREATED_PINECONE_INDEX}" '{"deletion_protection":"disabled"}'
    pinecone_http DELETE "https://api.pinecone.io/indexes/${CREATED_PINECONE_INDEX}" ""
    if [[ ! "${HTTP_STATUS}" =~ ^2|404$ ]]; then
      CLEANUP_FAILURES=$((CLEANUP_FAILURES + 1))
      echo "WARN: failed to delete temporary Pinecone index -> HTTP ${HTTP_STATUS}"
    fi
  fi

  if [[ -n "${CREATED_ZILLIZ_CLUSTER_ID}" ]]; then
    echo "Cleanup: deleting temporary Zilliz cluster ${CREATED_ZILLIZ_CLUSTER_ID}"
    zilliz_http DELETE "https://api.cloud.zilliz.com/v2/clusters/${CREATED_ZILLIZ_CLUSTER_ID}" ""
    if [[ ! "${HTTP_STATUS}" =~ ^2|404$ ]]; then
      CLEANUP_FAILURES=$((CLEANUP_FAILURES + 1))
      echo "WARN: failed to delete temporary Zilliz cluster -> HTTP ${HTTP_STATUS}"
    fi
  fi

  rm -rf "${TMP_DIR}"
}

trap cleanup_ephemeral_resources EXIT

run_section() {
  local title="$1"
  shift
  echo
  echo "== ${title} =="
  set +e
  "$@"
  local code=$?
  set -e
  if [[ ${code} -ne 0 ]]; then
    FAILURES=$((FAILURES + 1))
  else
    echo "PASS: ${title}"
  fi
}

verify_pinecone() {
  if [[ -z "${PINECONE_API_KEY}" ]]; then
    echo "Missing PINECONE_API_KEY"
    return 1
  fi

  pinecone_http GET "https://api.pinecone.io/indexes" ""
  require_2xx "Pinecone control plane access" || return 1

  pinecone_http GET "https://api.pinecone.io/indexes/${PINECONE_EXISTING_INDEX_NAME}" ""
  require_2xx "Pinecone existing managed index lookup" || return 1
  if [[ "$(jq -r '.status.ready // .ready // false' <<<"${HTTP_BODY}")" != "true" ]]; then
    echo "FAIL: Pinecone existing managed index is not ready"
    return 1
  fi

  if [[ "${PINECONE_CREATE_EPHEMERAL_INDEX}" == "true" ]]; then
    local payload
    payload="$(jq -nc \
      --arg name "${PINECONE_EPHEMERAL_INDEX_NAME}" \
      --arg metric "${PINECONE_METRIC}" \
      --arg cloud "${PINECONE_CLOUD}" \
      --arg region "${PINECONE_REGION}" \
      --argjson dimension "${PINECONE_DIMENSIONS}" \
      '{name:$name, dimension:$dimension, metric:$metric, deletion_protection:"disabled", spec:{serverless:{cloud:$cloud, region:$region}}}')"
    pinecone_http POST "https://api.pinecone.io/indexes" "${payload}"
    require_2xx "Pinecone temporary index creation" || return 1
    CREATED_PINECONE_INDEX="${PINECONE_EPHEMERAL_INDEX_NAME}"

    wait_until 60 5 bash -c '
      status="$(curl -sS -o /tmp/pinecone-ready.out -w "%{http_code}" \
        -H "Accept: application/json" \
        -H "Content-Type: application/json" \
        -H "Api-Key: '"${PINECONE_API_KEY}"'" \
        -H "X-Pinecone-Api-Version: 2025-10" \
        "https://api.pinecone.io/indexes/'"${PINECONE_EPHEMERAL_INDEX_NAME}"'")"
      [[ "${status}" =~ ^2 ]] && jq -e ".status.ready == true or .ready == true" /tmp/pinecone-ready.out >/dev/null 2>&1
    ' || {
      echo "FAIL: Pinecone temporary index did not become ready in time"
      return 1
    }
  fi
}

resolve_qdrant_account_id() {
  qdrant_mgmt_http GET "https://api.cloud.qdrant.io/api/account/v1/accounts" ""
  require_2xx "Qdrant Cloud account lookup" || return 1
  if [[ -n "${QDRANT_CLOUD_ACCOUNT_ID}" ]]; then
    jq -e --arg id "${QDRANT_CLOUD_ACCOUNT_ID}" '.items[] | select(.id == $id)' <<<"${HTTP_BODY}" >/dev/null 2>&1 || {
      echo "FAIL: configured Qdrant account ${QDRANT_CLOUD_ACCOUNT_ID} not visible to the management key"
      return 1
    }
    printf '%s' "${QDRANT_CLOUD_ACCOUNT_ID}"
    return 0
  fi
  local count
  count="$(jq '.items | length' <<<"${HTTP_BODY}")"
  if [[ "${count}" != "1" ]]; then
    echo "FAIL: qdrant account auto-resolution is ambiguous; set QDRANT_CLOUD_ACCOUNT_ID"
    return 1
  fi
  jq -r '.items[0].id' <<<"${HTTP_BODY}"
}

qdrant_wait_cluster_ready() {
  local account_id="$1"
  local cluster_id="$2"
  local attempts="${3:-90}"
  local sleep_seconds="${4:-5}"

  local i phase endpoint
  for ((i = 1; i <= attempts; i += 1)); do
    qdrant_mgmt_http GET "https://api.cloud.qdrant.io/api/cluster/v1/accounts/${account_id}/clusters/${cluster_id}" ""
    require_2xx "Qdrant cluster readiness polling" || return 1
    phase="$(jq -r '.cluster.state.phase // ""' <<<"${HTTP_BODY}")"
    endpoint="$(jq -r '.cluster.state.endpoint.url // ""' <<<"${HTTP_BODY}")"
    if [[ -n "${endpoint}" && ( "${phase}" == "CLUSTER_PHASE_HEALTHY" || "${phase}" == "CLUSTER_PHASE_NOT_READY" ) ]]; then
      return 0
    fi
    sleep "${sleep_seconds}"
  done

  echo "FAIL: Qdrant cluster ${cluster_id} did not reach a usable state in time"
  if [[ -n "${HTTP_BODY}" ]]; then
    echo "${HTTP_BODY}"
  fi
  return 1
}

verify_qdrant() {
  if [[ -z "${QDRANT_CLOUD_MANAGEMENT_API_KEY}" ]]; then
    echo "Missing QDRANT_CLOUD_MANAGEMENT_API_KEY"
    return 1
  fi

  local account_id
  account_id="$(resolve_qdrant_account_id)" || return 1
  CREATED_QDRANT_ACCOUNT_ID="${account_id}"

  qdrant_mgmt_http GET "https://api.cloud.qdrant.io/api/platform/v1/cloud-providers/${QDRANT_CLOUD_PROVIDER_ID}/regions/${QDRANT_CLOUD_REGION_ID}" ""
  require_2xx "Qdrant Cloud region lookup" || return 1
  jq -e '.region.available == true' <<<"${HTTP_BODY}" >/dev/null 2>&1 || {
    echo "FAIL: Qdrant Cloud region ${QDRANT_CLOUD_PROVIDER_ID}/${QDRANT_CLOUD_REGION_ID} is not available"
    return 1
  }

  qdrant_mgmt_http GET \
    "https://api.cloud.qdrant.io/api/booking/v1/accounts/${account_id}/packages?cloud_provider_id=${QDRANT_CLOUD_PROVIDER_ID}&cloud_provider_region_id=${QDRANT_CLOUD_REGION_ID}" \
    ""
  require_2xx "Qdrant Cloud package lookup" || return 1
  local effective_package_id
  if [[ -n "${QDRANT_CLOUD_PACKAGE_ID}" ]]; then
    jq -e --arg id "${QDRANT_CLOUD_PACKAGE_ID}" '.items[] | select(.id == $id and .status == "PACKAGE_STATUS_ACTIVE")' <<<"${HTTP_BODY}" >/dev/null 2>&1 || {
      echo "FAIL: configured Qdrant package ${QDRANT_CLOUD_PACKAGE_ID} is not available in the selected region"
      return 1
    }
    effective_package_id="${QDRANT_CLOUD_PACKAGE_ID}"
  else
    jq -e '.items[] | select(.status == "PACKAGE_STATUS_ACTIVE")' <<<"${HTTP_BODY}" >/dev/null 2>&1 || {
      echo "FAIL: no active Qdrant packages are available in the selected region"
      return 1
    }
    effective_package_id="$(
      jq -r '[.items[] | select(.status == "PACKAGE_STATUS_ACTIVE")] | sort_by(.unitIntPricePerHour // 0, .name) | .[0].id // empty' <<<"${HTTP_BODY}"
    )"
    if [[ -z "${effective_package_id}" ]]; then
      echo "FAIL: Qdrant package auto-resolution returned no active package id"
      return 1
    fi
  fi

  local cluster_id
  local cluster_host
  cluster_id=""
  cluster_host="${QDRANT_HOST}"

  if [[ -n "${QDRANT_EXISTING_CLUSTER_NAME}" ]]; then
    qdrant_mgmt_http GET "https://api.cloud.qdrant.io/api/cluster/v1/accounts/${account_id}/clusters" ""
    require_2xx "Qdrant Cloud cluster listing" || return 1
    cluster_id="$(jq -r --arg name "${QDRANT_EXISTING_CLUSTER_NAME}" '(.items // [])[] | select(.name == $name) | .id' <<<"${HTTP_BODY}" | head -n1)"
    if [[ -z "${cluster_id}" ]]; then
      echo "FAIL: Qdrant existing cluster ${QDRANT_EXISTING_CLUSTER_NAME} was not found"
      return 1
    fi
    qdrant_mgmt_http GET "https://api.cloud.qdrant.io/api/cluster/v1/accounts/${account_id}/clusters/${cluster_id}" ""
    require_2xx "Qdrant existing cluster detail" || return 1
    if [[ -z "${cluster_host}" ]]; then
      cluster_host="$(jq -r '.cluster.state.endpoint.url // ""' <<<"${HTTP_BODY}")"
    fi
  fi

  if [[ -n "${QDRANT_API_KEY}" && -n "${cluster_host}" ]]; then
    qdrant_data_http GET "${cluster_host}/collections" ""
    require_2xx "Qdrant data plane collection listing" || return 1
  fi

  if [[ -z "${cluster_id}" && "${QDRANT_CREATE_EPHEMERAL_CLUSTER}" == "true" ]]; then
    local cluster_payload
    cluster_payload="$(jq -nc \
      --arg account_id "${account_id}" \
      --arg deployment_id "gha-${RUN_ID_SUFFIX}" \
      --arg name "${QDRANT_EPHEMERAL_CLUSTER_NAME}" \
      --arg provider_id "${QDRANT_CLOUD_PROVIDER_ID}" \
      --arg region_id "${QDRANT_CLOUD_REGION_ID}" \
      --arg package_id "${effective_package_id}" \
      '{cluster:{accountId:$account_id, name:$name, cloudProviderId:$provider_id, cloudProviderRegionId:$region_id, labels:[{key:"managed-by",value:"github-actions"},{key:"verification-run",value:$deployment_id}], configuration:{numberOfNodes:1, packageId:$package_id, databaseConfiguration:{collection:{vectors:{onDisk:true}}}}}}')"
    qdrant_mgmt_http POST "https://api.cloud.qdrant.io/api/cluster/v1/accounts/${account_id}/clusters" "${cluster_payload}"
    require_2xx "Qdrant temporary cluster creation" || return 1
    cluster_id="$(jq -r '.cluster.id // empty' <<<"${HTTP_BODY}")"
    [[ -n "${cluster_id}" ]] || {
      echo "FAIL: Qdrant temporary cluster creation returned no id"
      return 1
    }
    CREATED_QDRANT_CLUSTER_ID="${cluster_id}"
    qdrant_wait_cluster_ready "${account_id}" "${cluster_id}" || return 1
    cluster_host="$(jq -r '.cluster.state.endpoint.url // ""' <<<"${HTTP_BODY}")"
  fi

  if [[ -z "${cluster_id}" ]]; then
    echo "FAIL: no Qdrant cluster is available. Set QDRANT_EXISTING_CLUSTER_NAME or allow QDRANT_CREATE_EPHEMERAL_CLUSTER=true."
    return 1
  fi

  if [[ "${QDRANT_CREATE_EPHEMERAL_DB_KEY}" == "true" ]]; then
    local payload
    payload="$(jq -nc \
      --arg account_id "${account_id}" \
      --arg cluster_id "${cluster_id}" \
      --arg name "${QDRANT_EPHEMERAL_DB_KEY_NAME}" \
      '{databaseApiKey:{accountId:$account_id, clusterId:$cluster_id, name:$name}}')"
    qdrant_mgmt_http POST \
      "https://api.cloud.qdrant.io/api/cluster/auth/v2/accounts/${account_id}/database-api-keys" \
      "${payload}"
    require_2xx "Qdrant temporary database API key creation" || return 1
    CREATED_QDRANT_DB_KEY_ID="$(jq -r '.databaseApiKey.id // empty' <<<"${HTTP_BODY}")"
    [[ -n "${CREATED_QDRANT_DB_KEY_ID}" ]] || {
      echo "FAIL: Qdrant temporary database API key creation returned no id"
      return 1
    }
  fi
}

verify_zilliz() {
  if [[ -z "${ZILLIZ_CLOUD_API_KEY}" ]]; then
    echo "Missing ZILLIZ_CLOUD_API_KEY"
    return 1
  fi

  zilliz_http GET "https://api.cloud.zilliz.com/v2/projects?pageSize=100&currentPage=1" ""
  require_2xx "Zilliz Cloud project listing" || return 1
  jq -e --arg project_id "${ZILLIZ_PROJECT_ID}" '.data[] | select(.projectId == $project_id)' <<<"${HTTP_BODY}" >/dev/null 2>&1 || {
    echo "FAIL: Zilliz project ${ZILLIZ_PROJECT_ID} was not found"
    return 1
  }

  zilliz_http GET "https://api.cloud.zilliz.com/v2/clusters?pageSize=100&currentPage=1" ""
  require_2xx "Zilliz Cloud cluster listing" || return 1
  local cluster_id
  cluster_id="$(jq -r --arg name "${ZILLIZ_EXISTING_CLUSTER_NAME}" '.data.clusters[] | select(.clusterName == $name) | .clusterId' <<<"${HTTP_BODY}" | head -n1)"
  if [[ -z "${cluster_id}" ]]; then
    echo "FAIL: Zilliz existing cluster ${ZILLIZ_EXISTING_CLUSTER_NAME} was not found"
    return 1
  fi
  zilliz_http GET "https://api.cloud.zilliz.com/v2/clusters/${cluster_id}" ""
  require_2xx "Zilliz existing cluster detail" || return 1

  if [[ "${ZILLIZ_CREATE_EPHEMERAL_CLUSTER}" == "true" ]]; then
    local endpoint payload create_path
    create_path="/v2/clusters/createServerless"
    payload="$(jq -nc \
      --arg cluster_name "${ZILLIZ_EPHEMERAL_CLUSTER_NAME}" \
      --arg project_id "${ZILLIZ_PROJECT_ID}" \
      --arg region_id "${ZILLIZ_REGION_ID}" \
      '{clusterName:$cluster_name, projectId:$project_id, regionId:$region_id}')"
    case "${ZILLIZ_CLUSTER_PLAN}" in
      Free)
        create_path="/v2/clusters/createFree"
        ;;
      Serverless)
        create_path="/v2/clusters/createServerless"
        ;;
      *)
        echo "FAIL: Zilliz temporary cluster creation only supports Free or Serverless in the GitHub verification suite"
        return 1
        ;;
    esac
    zilliz_http POST "https://api.cloud.zilliz.com${create_path}" "${payload}"
    require_2xx "Zilliz temporary cluster creation" || return 1
    CREATED_ZILLIZ_CLUSTER_ID="$(jq -r '.data.clusterId // empty' <<<"${HTTP_BODY}")"
    [[ -n "${CREATED_ZILLIZ_CLUSTER_ID}" ]] || {
      echo "FAIL: Zilliz temporary cluster creation returned no clusterId"
      return 1
    }
  fi
}

verify_weaviate() {
  if [[ -z "${WEAVIATE_HOST}" ]]; then
    echo "FAIL: Weaviate verification requires WEAVIATE_HOST or PLATFORM_VERIFICATION_WEAVIATE_HOST"
    return 1
  fi
  local base_url
  base_url="${WEAVIATE_SCHEME}://${WEAVIATE_HOST}"
  if [[ -n "${WEAVIATE_PORT}" && "${WEAVIATE_PORT}" != "443" && "${WEAVIATE_PORT}" != "80" ]]; then
    base_url="${base_url}:${WEAVIATE_PORT}"
  fi

  weaviate_http GET "${base_url}/v1/.well-known/ready" ""
  if [[ ! "${HTTP_STATUS}" =~ ^2 ]]; then
    if [[ "${HTTP_STATUS}" != "404" ]]; then
      echo "FAIL: Weaviate readiness probe -> HTTP ${HTTP_STATUS}"
      [[ -n "${HTTP_BODY}" ]] && echo "${HTTP_BODY}"
      return 1
    fi
    echo "INFO: Weaviate readiness probe returned HTTP 404, falling back to metadata probe."
  fi

  weaviate_http GET "${base_url}/v1/meta" ""
  require_2xx "Weaviate metadata probe" || return 1
}

echo "Managed vector provider verification"
echo "Cleanup ephemeral resources: ${CLEANUP_EPHEMERAL_RESOURCES}"
echo "Run Pinecone: ${RUN_PINECONE}"
echo "Run Qdrant: ${RUN_QDRANT}"
echo "Run Zilliz: ${RUN_ZILLIZ}"
echo "Run Weaviate: ${RUN_WEAVIATE}"

if [[ "${RUN_PINECONE}" == "true" ]]; then
  run_section "Pinecone control plane" verify_pinecone
fi

if [[ "${RUN_QDRANT}" == "true" ]]; then
  run_section "Qdrant Cloud control plane" verify_qdrant
fi

if [[ "${RUN_ZILLIZ}" == "true" ]]; then
  run_section "Zilliz Cloud control plane" verify_zilliz
fi

if [[ "${RUN_WEAVIATE}" == "true" ]]; then
  run_section "Weaviate Cloud external service" verify_weaviate
fi

if [[ ${FAILURES} -ne 0 ]]; then
  echo
  echo "FAIL: ${FAILURES} provider verification section(s) failed."
  exit 1
fi

if [[ ${CLEANUP_FAILURES} -ne 0 ]]; then
  echo
  echo "FAIL: provider verification passed but cleanup reported ${CLEANUP_FAILURES} failure(s)."
  exit 1
fi

echo
echo "PASS: All managed vector provider checks completed."
