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

resolve_node_binary() {
  if [[ -n "${SHOPIFY_CLI_NODE_BINARY:-}" ]]; then
    printf '%s' "${SHOPIFY_CLI_NODE_BINARY}"
    return
  fi
  if [[ -n "${NODE:-}" && -x "${NODE}" ]]; then
    printf '%s' "${NODE}"
    return
  fi
  command -v node
}

resolve_shopify_cli() {
  if [[ -n "${SHOPIFY_CLI_RUNNER:-}" ]]; then
    printf '%s' "${SHOPIFY_CLI_RUNNER}"
    return
  fi
  command -v shopify
}

NODE_BINARY="$(resolve_node_binary)"
SHOPIFY_CLI_RUNNER="$(resolve_shopify_cli)"

if [[ ! -x "${NODE_BINARY}" ]]; then
  echo "Resolved Node binary is not executable: ${NODE_BINARY}" >&2
  exit 2
fi

if [[ ! -f "${SHOPIFY_CLI_RUNNER}" ]]; then
  echo "Resolved Shopify CLI runner is not a file: ${SHOPIFY_CLI_RUNNER}" >&2
  exit 2
fi

cd "${SERVICE_ROOT}"
exec "${NODE_BINARY}" "${SHOPIFY_CLI_RUNNER}" "$@"
