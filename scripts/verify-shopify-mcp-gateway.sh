#!/usr/bin/env bash
set -euo pipefail

# Verifies the Plan 009 / 009.1 / 009.2 MCP-first execution path:
# Marketplace MCP discovery -> managed MCP Gateway -> Shopify MCP tools/list/tools/call
# and Shopify Bridge -> plugin MCP config -> managed MCP Gateway.
#
# Required:
#   PLATFORM_BASE_URL
#   SHOPIFY_BRIDGE_BASE_URL
#   SHOP_DOMAIN
#   MCP_GATEWAY_API_KEY or MCP_GATEWAY_API_KEY_FILE
#   SHOPIFY_BRIDGE_ADMIN_API_KEY or SHOPIFY_BRIDGE_ADMIN_API_KEY_FILE
#   PLATFORM_API_KEY or PLATFORM_LOGIN_EMAIL/PLATFORM_LOGIN_PASSWORD
#
# Optional:
#   PRODUCT_SERVICE_REF=shopify-bridge-staging
#   MCP_GATEWAY_PRODUCT_SERVICE_REF=mcp-execution-gateway
#   PLATFORM_API_KEY_HEADER=X-PLATFORM-API-KEY
#   MCP_GATEWAY_API_KEY_HEADER=X-MCP-GATEWAY-API-KEY
#   SHOPIFY_BRIDGE_ADMIN_API_KEY_HEADER=X-BRIDGE-API-KEY

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

pass() {
  echo "PASS: $*"
}

trim_trailing_slash() {
  local value="${1:-}"
  value="${value%/}"
  printf '%s' "${value}"
}

resolve_secret_value() {
  local name="$1"
  local file_var="${name}_FILE"
  local file_path="${!file_var:-}"
  local value="${!name:-}"
  if [[ -n "${file_path}" ]]; then
    [[ -f "${file_path}" ]] || fail "${file_var} points to a missing file."
    tr -d '\r\n' < "${file_path}"
    return
  fi
  if [[ -n "${value}" ]]; then
    printf '%s' "${value}"
  fi
}

require_env() {
  local name="$1"
  local value="${!name:-}"
  [[ -n "${value}" ]] || fail "${name} is required."
}

require_secret() {
  local name="$1"
  local value
  value="$(resolve_secret_value "${name}")"
  [[ -n "${value}" ]] || fail "${name} or ${name}_FILE is required."
  printf '%s' "${value}"
}

HTTP_STATUS=""
HTTP_BODY=""

http_request() {
  local method="$1"
  local url="$2"
  local body="${3:-}"
  shift 3 || true
  local output_file
  output_file="$(mktemp)"
  local args=(-sS -o "${output_file}" -w "%{http_code}" -X "${method}")
  if [[ -n "${body}" ]]; then
    args+=(-H "Content-Type: application/json" --data "${body}")
  fi
  for header in "$@"; do
    if [[ -n "${header}" ]]; then
      args+=(-H "${header}")
    fi
  done
  if ! HTTP_STATUS="$(curl "${args[@]}" "${url}")"; then
    rm -f "${output_file}"
    fail "HTTP request failed: ${method} ${url}"
  fi
  HTTP_BODY="$(cat "${output_file}")"
  rm -f "${output_file}"
}

json_value() {
  local payload="$1"
  local path="$2"
  JSON_PAYLOAD="${payload}" python3 - "${path}" <<'PY'
import json
import os
import sys

path = sys.argv[1]
data = json.loads(os.environ.get("JSON_PAYLOAD") or "{}")
current = data
for part in [p for p in path.split(".") if p]:
    if isinstance(current, list):
        try:
            current = current[int(part)]
        except Exception:
            print("")
            raise SystemExit(0)
    elif isinstance(current, dict):
        current = current.get(part)
    else:
        current = None
    if current is None:
        print("")
        raise SystemExit(0)
if isinstance(current, bool):
    print("true" if current else "false")
elif isinstance(current, (dict, list)):
    print(json.dumps(current, separators=(",", ":")))
else:
    print(current)
PY
}

json_check() {
  local payload="$1"
  local label="$2"
  local code="$3"
  JSON_PAYLOAD="${payload}" python3 - "${label}" "${code}" <<'PY'
import json
import os
import sys
import traceback

label = sys.argv[1]
code = sys.argv[2]
try:
    data = json.loads(os.environ.get("JSON_PAYLOAD") or "{}")
    exec(code, {"data": data})
except Exception as exc:
    print(f"FAIL: {label}: {exc}", file=sys.stderr)
    traceback.print_exc()
    raise SystemExit(1)
PY
  pass "${label}"
}

json_body() {
  python3 - "$@" <<'PY'
import json
import sys

kind = sys.argv[1]
if kind == "server":
    shop = sys.argv[2]
    print(json.dumps({
        "serverRef": "shopify-storefront",
        "server": {
            "transport": "STREAMABLE_HTTP",
            "endpointUrl": f"https://{shop}/api/mcp",
            "auth": {"mode": "NONE"}
        },
        "trace": {"shopDomain": shop}
    }, separators=(",", ":")))
elif kind == "marketplace-discovery":
    shop = sys.argv[2]
    gateway_ref = sys.argv[3]
    print(json.dumps({
        "serverRef": "shopify-storefront-release-gate",
        "server": {
            "transport": "STREAMABLE_HTTP",
            "endpointUrl": f"https://{shop}/api/mcp",
            "auth": {"mode": "NONE"}
        },
        "trace": {"shopDomain": shop, "releaseGate": "shopify-mcp-gateway"},
        "allowedTools": ["search_catalog"],
        "gatewayServiceRef": gateway_ref
    }, separators=(",", ":")))
elif kind == "action":
    shop = sys.argv[2]
    print(json.dumps({
        "actionId": "shopify_search_catalog",
        "params": {
            "query": "release gate catalog search",
            "country": "US",
            "intent": "product discovery",
            "limit": 3
        },
        "trace": {
            "shopDomain": shop,
            "actionConfig": {
                "adapterType": "mcp-tool",
                "execution": {
                    "adapterType": "mcp-tool",
                    "mcp": {
                        "serverRef": "shopify-storefront",
                        "endpointKind": "STOREFRONT_STANDARD",
                        "toolName": "search_catalog",
                        "argumentTemplate": {
                            "catalog": {
                                "query": "{{params.query}}",
                                "context": {
                                    "address_country": "{{params.country}}",
                                    "intent": "{{params.intent}}"
                                },
                                "pagination": {
                                    "limit": "{{params.limit}}"
                                }
                            }
                        }
                    }
                }
            }
        }
    }, separators=(",", ":")))
else:
    raise SystemExit(f"Unknown json body kind: {kind}")
PY
}

PLATFORM_BASE_URL="$(trim_trailing_slash "${PLATFORM_BASE_URL:-}")"
SHOPIFY_BRIDGE_BASE_URL="$(trim_trailing_slash "${SHOPIFY_BRIDGE_BASE_URL:-}")"
SHOP_DOMAIN="${SHOP_DOMAIN:-}"
PRODUCT_SERVICE_REF="${PRODUCT_SERVICE_REF:-shopify-bridge-staging}"
MCP_GATEWAY_PRODUCT_SERVICE_REF="${MCP_GATEWAY_PRODUCT_SERVICE_REF:-mcp-execution-gateway}"
PLATFORM_API_KEY_HEADER="${PLATFORM_API_KEY_HEADER:-X-PLATFORM-API-KEY}"
MCP_GATEWAY_API_KEY_HEADER="${MCP_GATEWAY_API_KEY_HEADER:-X-MCP-GATEWAY-API-KEY}"
SHOPIFY_BRIDGE_ADMIN_API_KEY_HEADER="${SHOPIFY_BRIDGE_ADMIN_API_KEY_HEADER:-X-BRIDGE-API-KEY}"

require_env PLATFORM_BASE_URL
require_env SHOPIFY_BRIDGE_BASE_URL
require_env SHOP_DOMAIN

MCP_GATEWAY_API_KEY="$(require_secret MCP_GATEWAY_API_KEY)"
SHOPIFY_BRIDGE_ADMIN_API_KEY="$(require_secret SHOPIFY_BRIDGE_ADMIN_API_KEY)"

PLATFORM_HEADERS=()
PLATFORM_API_KEY="$(resolve_secret_value PLATFORM_API_KEY)"
if [[ -n "${PLATFORM_API_KEY}" ]]; then
  PLATFORM_HEADERS+=("${PLATFORM_API_KEY_HEADER}: ${PLATFORM_API_KEY}")
else
  PLATFORM_LOGIN_EMAIL="$(resolve_secret_value PLATFORM_LOGIN_EMAIL)"
  PLATFORM_LOGIN_PASSWORD="$(resolve_secret_value PLATFORM_LOGIN_PASSWORD)"
  [[ -n "${PLATFORM_LOGIN_EMAIL}" && -n "${PLATFORM_LOGIN_PASSWORD}" ]] \
    || fail "Set PLATFORM_API_KEY or PLATFORM_LOGIN_EMAIL/PLATFORM_LOGIN_PASSWORD."
  login_payload="$(python3 - <<'PY' "${PLATFORM_LOGIN_EMAIL}" "${PLATFORM_LOGIN_PASSWORD}"
import json
import sys
print(json.dumps({"email": sys.argv[1], "password": sys.argv[2]}, separators=(",", ":")))
PY
)"
  cookie_jar="$(mktemp)"
  http_request POST "${PLATFORM_BASE_URL}/api/platform/auth/login" "${login_payload}"
  [[ "${HTTP_STATUS}" == "200" ]] || fail "Platform login failed with HTTP ${HTTP_STATUS}."
  curl -sS -c "${cookie_jar}" -o /dev/null -H "Content-Type: application/json" \
    -X POST "${PLATFORM_BASE_URL}/api/platform/auth/login" --data "${login_payload}" >/dev/null
  session_cookie="$(awk 'NF >= 7 && $0 !~ /^#/ {print $6 "=" $7}' "${cookie_jar}" | tail -n 1)"
  rm -f "${cookie_jar}"
  [[ -n "${session_cookie}" ]] || fail "Platform login did not return a session cookie."
  PLATFORM_HEADERS+=("Cookie: ${session_cookie}")
fi

platform_request() {
  local method="$1"
  local path="$2"
  local body="${3:-}"
  http_request "${method}" "${PLATFORM_BASE_URL}${path}" "${body}" "${PLATFORM_HEADERS[@]}"
}

echo "== Platform health =="
http_request GET "${PLATFORM_BASE_URL}/actuator/health" ""
[[ "${HTTP_STATUS}" == "200" ]] || fail "Platform health returned HTTP ${HTTP_STATUS}."
json_check "${HTTP_BODY}" "platform health is UP" 'assert data.get("status") == "UP", data'

echo "== MCP Gateway product service =="
platform_request GET "/api/product-services/${MCP_GATEWAY_PRODUCT_SERVICE_REF}" ""
[[ "${HTTP_STATUS}" == "200" ]] || fail "MCP Gateway product service summary returned HTTP ${HTTP_STATUS}."
mcp_gateway_summary="${HTTP_BODY}"
json_check "${mcp_gateway_summary}" "mcp gateway product service summary" '
assert data.get("serviceRef"), data
assert data.get("serviceKind") == "MCP_EXECUTION_GATEWAY_SERVICE", data
assert data.get("secretConfigured") is True, data
assert data.get("status") == "ACTIVE", data
assert data.get("baseUrl"), data
if data.get("lastReconcileStatus"):
    assert data.get("lastReconcileStatus") == "SUCCESS", data
if data.get("driftStatus"):
    assert data.get("driftStatus") == "NO_DRIFT", data
'
MCP_GATEWAY_BASE_URL="$(trim_trailing_slash "$(json_value "${mcp_gateway_summary}" "baseUrl")")"
[[ -n "${MCP_GATEWAY_BASE_URL}" ]] || fail "MCP Gateway product service has no baseUrl."

platform_request GET "/api/product-services/${MCP_GATEWAY_PRODUCT_SERVICE_REF}/health" ""
[[ "${HTTP_STATUS}" == "200" ]] || fail "MCP Gateway product service health returned HTTP ${HTTP_STATUS}."
json_check "${HTTP_BODY}" "mcp gateway product service is READY" 'assert data.get("status") == "READY", data'

echo "== Shopify Bridge product service =="
platform_request GET "/api/product-services/${PRODUCT_SERVICE_REF}" ""
[[ "${HTTP_STATUS}" == "200" ]] || fail "Shopify Bridge product service summary returned HTTP ${HTTP_STATUS}."
json_check "${HTTP_BODY}" "shopify bridge product service summary" '
assert data.get("serviceKind") == "SHOPIFY_BRIDGE_SERVICE", data
assert data.get("secretConfigured") is True, data
assert data.get("status") == "ACTIVE", data
if data.get("driftStatus"):
    assert data.get("driftStatus") == "NO_DRIFT", data
'

platform_request GET "/api/product-services/${PRODUCT_SERVICE_REF}/health" ""
[[ "${HTTP_STATUS}" == "200" ]] || fail "Shopify Bridge product service health returned HTTP ${HTTP_STATUS}."
json_check "${HTTP_BODY}" "shopify bridge product service is READY" 'assert data.get("status") == "READY", data'

echo "== Direct service health =="
http_request GET "${MCP_GATEWAY_BASE_URL}/actuator/health" ""
[[ "${HTTP_STATUS}" == "200" ]] || fail "MCP Gateway actuator returned HTTP ${HTTP_STATUS}."
json_check "${HTTP_BODY}" "mcp gateway actuator is UP" 'assert data.get("status") == "UP", data'

http_request GET "${SHOPIFY_BRIDGE_BASE_URL}/actuator/health" ""
[[ "${HTTP_STATUS}" == "200" ]] || fail "Shopify Bridge actuator returned HTTP ${HTTP_STATUS}."
json_check "${HTTP_BODY}" "shopify bridge actuator is UP" 'assert data.get("status") == "UP", data'

echo "== MCP Gateway admin auth =="
http_request GET "${MCP_GATEWAY_BASE_URL}/api/admin/overview" ""
[[ "${HTTP_STATUS}" == "401" ]] || fail "MCP Gateway admin overview without key returned HTTP ${HTTP_STATUS}; expected 401."
pass "mcp gateway admin rejects missing key"

http_request GET "${MCP_GATEWAY_BASE_URL}/api/admin/overview" "" "${MCP_GATEWAY_API_KEY_HEADER}: ${MCP_GATEWAY_API_KEY}"
[[ "${HTTP_STATUS}" == "200" ]] || fail "MCP Gateway admin overview with key returned HTTP ${HTTP_STATUS}."
json_check "${HTTP_BODY}" "mcp gateway admin overview" '
assert data.get("status") == "READY", data
assert data.get("serviceKind") == "MCP_EXECUTION_GATEWAY_SERVICE", data
capabilities = set(data.get("capabilities") or [])
for expected in ["mcp.initialize", "mcp.tools.list", "mcp.tools.call", "marketplace.mcp.discovery", "actions.adapterType.mcp-tool"]:
    assert expected in capabilities, capabilities
'

echo "== Marketplace MCP discovery through gateway =="
discovery_payload="$(json_body marketplace-discovery "${SHOP_DOMAIN}" "${MCP_GATEWAY_PRODUCT_SERVICE_REF}")"
platform_request POST "/api/marketplace/mcp/discover" "${discovery_payload}"
[[ "${HTTP_STATUS}" == "200" ]] || fail "Marketplace MCP discovery returned HTTP ${HTTP_STATUS}."
json_check "${HTTP_BODY}" "marketplace mcp discovery" '
assert data.get("ready") is True, data
tools = data.get("tools") or []
tool = next((item for item in tools if item.get("name") == "search_catalog"), None)
assert tool is not None, tools
assert str(tool.get("schemaHash") or "").startswith("sha256:"), tool
'

echo "== MCP Gateway tools/list =="
server_payload="$(json_body server "${SHOP_DOMAIN}")"
http_request POST "${MCP_GATEWAY_BASE_URL}/api/internal/mcp/servers/tools/list" "${server_payload}" "${MCP_GATEWAY_API_KEY_HEADER}: ${MCP_GATEWAY_API_KEY}"
[[ "${HTTP_STATUS}" == "200" ]] || fail "MCP Gateway tools/list returned HTTP ${HTTP_STATUS}."
json_check "${HTTP_BODY}" "mcp gateway tools/list" '
assert data.get("success") is True, data
tools = data.get("result") or []
names = {item.get("name") for item in tools if isinstance(item, dict)}
assert "search_catalog" in names, names
'

echo "== MCP Gateway action execution =="
action_payload="$(json_body action "${SHOP_DOMAIN}")"
http_request POST "${MCP_GATEWAY_BASE_URL}/api/internal/mcp/actions/execute" "${action_payload}" "${MCP_GATEWAY_API_KEY_HEADER}: ${MCP_GATEWAY_API_KEY}"
[[ "${HTTP_STATUS}" == "200" ]] || fail "MCP Gateway action execution returned HTTP ${HTTP_STATUS}."
json_check "${HTTP_BODY}" "mcp gateway action evidence" '
assert data.get("success") is True, data
payload = data.get("data") or {}
assert payload.get("adapterType") == "mcp-tool", payload
assert payload.get("evidenceType") == "MCP_TOOL_RESULT", payload
assert payload.get("mcpToolName") == "search_catalog", payload
assert payload.get("toolResult") is not None, payload
'

echo "== Shopify Bridge MCP readiness =="
http_request GET "${SHOPIFY_BRIDGE_BASE_URL}/api/admin/stores/${SHOP_DOMAIN}/mcp/readiness" "" "${SHOPIFY_BRIDGE_ADMIN_API_KEY_HEADER}: ${SHOPIFY_BRIDGE_ADMIN_API_KEY}"
[[ "${HTTP_STATUS}" == "200" ]] || fail "Shopify Bridge MCP readiness returned HTTP ${HTTP_STATUS}."
json_check "${HTTP_BODY}" "shopify bridge mcp readiness" '
assert data.get("ready") is True, data
servers = data.get("servers") or []
assert len(servers) >= 1, data
by_ref = {server.get("serverRef"): server for server in servers if isinstance(server, dict)}
server = by_ref.get("shopify-storefront")
assert server is not None, by_ref
assert server.get("ready") is True, server
missing = server.get("missingTools") or []
assert not missing, missing
tools = set(server.get("presentTools") or [])
assert "search_catalog" in tools, tools
'

echo "== Shopify Bridge delegated MCP action =="
http_request POST "${SHOPIFY_BRIDGE_BASE_URL}/api/admin/stores/${SHOP_DOMAIN}/actions/execute" "${action_payload}" "${SHOPIFY_BRIDGE_ADMIN_API_KEY_HEADER}: ${SHOPIFY_BRIDGE_ADMIN_API_KEY}"
[[ "${HTTP_STATUS}" == "200" ]] || fail "Shopify Bridge delegated MCP action returned HTTP ${HTTP_STATUS}."
json_check "${HTTP_BODY}" "shopify bridge delegated mcp evidence" '
assert data.get("success") is True, data
payload = data.get("data") or {}
assert payload.get("adapterType") == "mcp-tool", payload
assert payload.get("evidenceType") == "MCP_TOOL_RESULT", payload
assert payload.get("mcpToolName") == "search_catalog", payload
assert payload.get("toolResult") is not None, payload
'

pass "shopify mcp gateway release gate completed"
