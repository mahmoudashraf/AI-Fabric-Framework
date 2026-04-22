#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVICE_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
LOCAL_ENV_FILE="${SHOPIFY_LOCAL_ENV_FILE:-${SCRIPT_DIR}/.env.shopify}"
MIN_FREE_MB="${SHOPIFY_MIN_FREE_MB:-2048}"

if [[ -f "${LOCAL_ENV_FILE}" ]]; then
  set -a
  # shellcheck source=/dev/null
  . "${LOCAL_ENV_FILE}"
  set +a
fi

require_command() {
  local command_name="$1"
  if ! command -v "${command_name}" >/dev/null 2>&1; then
    echo "Missing required command: ${command_name}" >&2
    exit 2
  fi
}

if [[ "${SHOPIFY_SKIP_CLI_CHECK:-false}" != "true" ]]; then
  require_command shopify
fi

require_command node
require_command npm
require_command mvn

for required_path in \
  "${SERVICE_ROOT}/package.json" \
  "${SERVICE_ROOT}/shopify.web.toml" \
  "${SERVICE_ROOT}/ui/package.json" \
  "${SERVICE_ROOT}/ui/shopify.web.toml" \
  "${SERVICE_ROOT}/extensions/companion-theme-app-extension/shopify.extension.toml"; do
  if [[ ! -f "${required_path}" ]]; then
    echo "Missing required workspace file: ${required_path}" >&2
    exit 2
  fi
done

if [[ "${SHOPIFY_SKIP_SPACE_CHECK:-false}" != "true" ]]; then
  available_mb="$(df -Pm "${SERVICE_ROOT}" | awk 'NR==2 {print $4}')"
  if [[ -z "${available_mb}" ]]; then
    echo "Unable to determine free disk space for ${SERVICE_ROOT}" >&2
    exit 2
  fi
  if (( available_mb < MIN_FREE_MB )); then
    echo "Insufficient disk space: ${available_mb}MB free, need at least ${MIN_FREE_MB}MB." >&2
    exit 2
  fi
fi

missing_env=()
for env_name in SHOPIFY_CLI_APP_CLIENT_ID SHOPIFY_BRIDGE_PUBLIC_BASE_URL; do
  if [[ -z "${!env_name:-}" ]]; then
    missing_env+=("${env_name}")
  fi
done
if (( ${#missing_env[@]} > 0 )); then
  printf 'Missing required environment values: %s\n' "${missing_env[*]}" >&2
  echo "Copy deploy/shopify/.env.shopify.example to deploy/shopify/.env.shopify and fill in the real values." >&2
  exit 2
fi

if [[ -z "${SHOPIFY_APP_PUBLIC_BASE_URL:-}" ]]; then
  echo "SHOPIFY_APP_PUBLIC_BASE_URL is not set; the generated app config will use SHOPIFY_BRIDGE_PUBLIC_BASE_URL for application_url."
fi

echo "Shopify CLI workspace preflight passed."
echo "Service root: ${SERVICE_ROOT}"
echo "Local env file: ${LOCAL_ENV_FILE}"
