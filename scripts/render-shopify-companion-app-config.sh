#!/usr/bin/env bash
set -euo pipefail

OUTPUT_PATH="${1:-product-services/shopify-bridge-service/deploy/shopify/shopify.app.toml}"
SHOPIFY_API_KEY="${SHOPIFY_API_KEY:-}"
SHOPIFY_BRIDGE_BASE_URL="${SHOPIFY_BRIDGE_BASE_URL:-}"
SHOPIFY_APP_NAME="${SHOPIFY_APP_NAME:-Loom Companion}"
SHOPIFY_APP_HANDLE="${SHOPIFY_APP_HANDLE:-loom-companion}"
SHOPIFY_WEBHOOK_API_VERSION="${SHOPIFY_WEBHOOK_API_VERSION:-2026-04}"
SHOPIFY_ACCESS_SCOPES="${SHOPIFY_ACCESS_SCOPES:-read_products,read_content,read_legal_policies,read_metaobjects,read_metaobject_definitions,read_orders,customer_read_customers,customer_read_orders,customer_write_orders,customer_read_store_credit_accounts}"
SHOPIFY_APP_PROXY_SUBPATH="${SHOPIFY_APP_PROXY_SUBPATH:-loom-companion}"
SHOPIFY_CUSTOMER_AUTH_REDIRECT_URI="${SHOPIFY_CUSTOMER_AUTH_REDIRECT_URI:-}"
SHOPIFY_CUSTOMER_AUTH_JAVASCRIPT_ORIGIN="${SHOPIFY_CUSTOMER_AUTH_JAVASCRIPT_ORIGIN:-}"

require_env() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    echo "Missing required env: ${name}" >&2
    exit 2
  fi
}

trim_slash() {
  local value="$1"
  if [[ "${value}" == */ ]]; then
    printf '%s' "${value%/}"
  else
    printf '%s' "${value}"
  fi
}

require_env SHOPIFY_API_KEY
require_env SHOPIFY_BRIDGE_BASE_URL

bridge_base_url="$(trim_slash "${SHOPIFY_BRIDGE_BASE_URL}")"
mkdir -p "$(dirname "${OUTPUT_PATH}")"

cat > "${OUTPUT_PATH}" <<EOF
client_id = "${SHOPIFY_API_KEY}"
name = "${SHOPIFY_APP_NAME}"
handle = "${SHOPIFY_APP_HANDLE}"
application_url = "${bridge_base_url}"
embedded = true

[access_scopes]
scopes = "${SHOPIFY_ACCESS_SCOPES}"

[auth]
redirect_urls = [
  "${bridge_base_url}/auth/shopify/callback"
]

[customer_authentication]
redirect_uris = [
  "${SHOPIFY_CUSTOMER_AUTH_REDIRECT_URI:-${bridge_base_url}/api/customer-auth/callback}"
]
javascript_origins = [
  "${SHOPIFY_CUSTOMER_AUTH_JAVASCRIPT_ORIGIN:-${bridge_base_url}}"
]

[webhooks]
api_version = "${SHOPIFY_WEBHOOK_API_VERSION}"

[[webhooks.subscriptions]]
name = "loom-companion-compliance"
compliance_topics = [
  "customers/data_request",
  "customers/redact",
  "shop/redact"
]
uri = "/api/webhooks/shopify"

# Non-compliance topics are reconciled programmatically by the Shopify Bridge
# service per installed shop to avoid duplicate deliveries between app-wide and
# shop-specific subscriptions.

[app_proxy]
url = "${bridge_base_url}"
subpath = "${SHOPIFY_APP_PROXY_SUBPATH}"
prefix = "apps"
EOF

printf 'Rendered %s\n' "${OUTPUT_PATH}"
