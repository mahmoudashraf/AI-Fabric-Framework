#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVICE_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
LOCAL_ENV_FILE="${SHOPIFY_LOCAL_ENV_FILE:-${SCRIPT_DIR}/.env.shopify}"

resolve_output_file() {
  if [[ -n "${SHOPIFY_APP_TOML_OUTPUT:-}" ]]; then
    printf '%s' "${SHOPIFY_APP_TOML_OUTPUT}"
    return
  fi

  shopt -s nullglob
  local linked_configs=("${SERVICE_ROOT}"/shopify.app.*.toml)
  shopt -u nullglob
  if [[ "${#linked_configs[@]}" -eq 1 ]]; then
    printf '%s' "${linked_configs[0]}"
    return
  fi

  printf '%s' "${SERVICE_ROOT}/shopify.app.toml"
}

OUTPUT_FILE="$(resolve_output_file)"

if [[ -f "${LOCAL_ENV_FILE}" ]]; then
  set -a
  # shellcheck source=/dev/null
  . "${LOCAL_ENV_FILE}"
  set +a
fi

require_env() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    echo "Missing required env: ${name}" >&2
    exit 2
  fi
}

trim_trailing_slash() {
  local value="$1"
  if [[ "${value}" == */ ]]; then
    printf '%s' "${value%/}"
  else
    printf '%s' "${value}"
  fi
}

escape_toml() {
  printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

require_env SHOPIFY_CLI_APP_CLIENT_ID
require_env SHOPIFY_BRIDGE_PUBLIC_BASE_URL

APP_PUBLIC_BASE_URL_INPUT="${SHOPIFY_APP_PUBLIC_BASE_URL:-${SHOPIFY_BRIDGE_PUBLIC_BASE_URL}}"
APP_NAME="${SHOPIFY_CLI_APP_NAME:-Loom Companion}"
APP_HANDLE="${SHOPIFY_CLI_APP_HANDLE:-loom-companion}"
APP_EMBEDDED="${SHOPIFY_CLI_APP_EMBEDDED:-true}"
APP_SCOPES="${SHOPIFY_CLI_APP_SCOPES:-read_products,read_content,read_legal_policies,read_metaobjects,read_metaobject_definitions,read_orders}"
APP_API_VERSION="${SHOPIFY_CLI_APP_API_VERSION:-2026-04}"
APP_PROXY_SUBPATH="${SHOPIFY_CLI_APP_PROXY_SUBPATH:-loom-companion}"
APP_PUBLIC_BASE_URL="$(trim_trailing_slash "${APP_PUBLIC_BASE_URL_INPUT}")"
BRIDGE_BASE_URL="$(trim_trailing_slash "${SHOPIFY_BRIDGE_PUBLIC_BASE_URL}")"
CALLBACK_URL="${SHOPIFY_CLI_APP_CALLBACK_URL:-${BRIDGE_BASE_URL}/auth/shopify/callback}"
CUSTOMER_AUTH_REDIRECT_URI="${SHOPIFY_CLI_CUSTOMER_AUTH_REDIRECT_URI:-${BRIDGE_BASE_URL}/api/customer-auth/callback}"
CUSTOMER_AUTH_JAVASCRIPT_ORIGIN="${SHOPIFY_CLI_CUSTOMER_AUTH_JAVASCRIPT_ORIGIN:-${BRIDGE_BASE_URL}}"
DEV_STORE_URL="${SHOPIFY_CLI_DEV_STORE_URL:-}"

mkdir -p "$(dirname "${OUTPUT_FILE}")"

cat > "${OUTPUT_FILE}" <<EOF
client_id = "$(escape_toml "${SHOPIFY_CLI_APP_CLIENT_ID}")"
name = "$(escape_toml "${APP_NAME}")"
handle = "$(escape_toml "${APP_HANDLE}")"
application_url = "$(escape_toml "${APP_PUBLIC_BASE_URL}")"
embedded = ${APP_EMBEDDED}
extension_directories = ["extensions/*"]
web_directories = [".", "ui"]

[access_scopes]
scopes = "$(escape_toml "${APP_SCOPES}")"

[auth]
redirect_urls = [
  "$(escape_toml "${CALLBACK_URL}")"
]

[customer_authentication]
redirect_uris = [
  "$(escape_toml "${CUSTOMER_AUTH_REDIRECT_URI}")"
]
javascript_origins = [
  "$(escape_toml "${CUSTOMER_AUTH_JAVASCRIPT_ORIGIN}")"
]

[build]
automatically_update_urls_on_dev = false
EOF

if [[ -n "${DEV_STORE_URL}" ]]; then
  cat >> "${OUTPUT_FILE}" <<EOF
dev_store_url = "$(escape_toml "${DEV_STORE_URL}")"
EOF
fi

cat >> "${OUTPUT_FILE}" <<EOF

[webhooks]
api_version = "$(escape_toml "${APP_API_VERSION}")"

[[webhooks.subscriptions]]
name = "loom-companion-compliance"
compliance_topics = [
  "customers/data_request",
  "customers/redact",
  "shop/redact"
]
uri = "/api/webhooks/shopify"

[app_proxy]
url = "$(escape_toml "${BRIDGE_BASE_URL}")"
subpath = "$(escape_toml "${APP_PROXY_SUBPATH}")"
prefix = "apps"
EOF

echo "Rendered Shopify app config to ${OUTPUT_FILE}"
