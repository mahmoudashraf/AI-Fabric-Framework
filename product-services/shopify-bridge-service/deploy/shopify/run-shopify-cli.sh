#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVICE_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
LOCAL_ENV_FILE="${SHOPIFY_LOCAL_ENV_FILE:-${SCRIPT_DIR}/.env.shopify}"

if [[ -f "${LOCAL_ENV_FILE}" ]]; then
  set -a
  # shellcheck source=/dev/null
  . "${LOCAL_ENV_FILE}"
  set +a
fi

resolve_shopify_cli() {
  if [[ -n "${SHOPIFY_CLI_RUNNER:-}" ]]; then
    printf '%s' "${SHOPIFY_CLI_RUNNER}"
    return
  fi
  command -v shopify
}

resolve_node_binary() {
  if [[ -n "${SHOPIFY_CLI_NODE_BINARY:-}" ]]; then
    printf '%s' "${SHOPIFY_CLI_NODE_BINARY}"
    return
  fi
  if [[ -n "${NODE:-}" && -x "${NODE}" ]]; then
    printf '%s' "${NODE}"
    return
  fi
  if [[ -n "${SHOPIFY_CLI_RUNNER:-}" ]]; then
    local runner_dir
    runner_dir="$(cd "$(dirname "${SHOPIFY_CLI_RUNNER}")" && pwd)"
    if [[ -x "${runner_dir}/node" ]]; then
      printf '%s' "${runner_dir}/node"
      return
    fi
  fi
  command -v node
}

SHOPIFY_CLI_RUNNER="$(resolve_shopify_cli)"
NODE_BINARY="$(resolve_node_binary)"
NODE_DIR="$(cd "$(dirname "${NODE_BINARY}")" && pwd)"

if [[ ! -x "${NODE_BINARY}" ]]; then
  echo "Resolved Node binary is not executable: ${NODE_BINARY}" >&2
  exit 2
fi

if [[ ! -f "${SHOPIFY_CLI_RUNNER}" ]]; then
  echo "Resolved Shopify CLI runner is not a file: ${SHOPIFY_CLI_RUNNER}" >&2
  exit 2
fi

cd "${SERVICE_ROOT}"
export NODE="${NODE_BINARY}"
export PATH="${NODE_DIR}:${PATH}"
exec "${NODE_BINARY}" "${SHOPIFY_CLI_RUNNER}" "$@"
