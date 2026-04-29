#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${PLATFORM_BASE_URL:-}"
PLATFORM_UI_BASE_URL="${PLATFORM_UI_BASE_URL:-}"
PARTNER_UI_BASE_URL="${PARTNER_UI_BASE_URL:-}"
PLATFORM_API_KEY_HEADER="${PLATFORM_API_KEY_HEADER:-X-PLATFORM-API-KEY}"
PLATFORM_API_KEY="${PLATFORM_API_KEY:-}"
PLATFORM_COOKIE="${PLATFORM_COOKIE:-}"
PLATFORM_LOGIN_EMAIL="${PLATFORM_LOGIN_EMAIL:-}"
PLATFORM_LOGIN_PASSWORD="${PLATFORM_LOGIN_PASSWORD:-}"
PARTNER_SUPABASE_JWT="${PARTNER_SUPABASE_JWT:-}"
SHOP_DOMAIN="${THINKER_SHOP_DOMAIN:-${SHOP_DOMAIN:-}}"
DEPLOYMENT_ID="${THINKER_DEPLOYMENT_ID:-}"
EXECUTE_LOW_RISK="${THINKER_EXECUTE_LOW_RISK:-false}"
REQUIRE_PARTNER_PROOF="${THINKER_REQUIRE_PARTNER_PROOF:-false}"
RUN_TAG="${THINKER_RUN_TAG:-thinker-gate-$(date -u +%Y%m%dT%H%M%SZ)}"
TMP_DIR=""
PLATFORM_COOKIE_JAR=""

resolve_secret_value() {
  local var_name="$1"
  local file_var_name="${var_name}_FILE"
  local direct_value="${!var_name:-}"
  local file_path="${!file_var_name:-}"
  if [[ -n "${file_path}" ]]; then
    if [[ ! -f "${file_path}" ]]; then
      echo "ERROR: missing secret file for ${var_name}: ${file_path}" >&2
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

urlencode() {
  python3 - <<'PY' "$1"
import sys
import urllib.parse
print(urllib.parse.quote(sys.argv[1], safe=""))
PY
}

request() {
  local method="$1"
  local url="$2"
  local out="$3"
  shift 3
  curl -sS -X "${method}" -o "${out}" -w "%{http_code}" "$@" "${url}"
}

platform_request() {
  local method="$1"
  local url="$2"
  local out="$3"
  shift 3
  local headers=()
  local curl_args=(-sS -X "${method}" -o "${out}" -w "%{http_code}")
  if [[ -n "${PLATFORM_API_KEY}" ]]; then
    headers+=("-H" "${PLATFORM_API_KEY_HEADER}: ${PLATFORM_API_KEY}")
  fi
  if [[ -n "${PLATFORM_COOKIE}" ]]; then
    headers+=("-H" "Cookie: ${PLATFORM_COOKIE}")
  fi
  if ((${#headers[@]})); then
    curl_args+=("${headers[@]}")
  fi
  if [[ -n "${PLATFORM_COOKIE_JAR}" && -s "${PLATFORM_COOKIE_JAR}" ]]; then
    curl "${curl_args[@]}" -b "${PLATFORM_COOKIE_JAR}" -c "${PLATFORM_COOKIE_JAR}" "$@" "${url}"
  else
    curl "${curl_args[@]}" "$@" "${url}"
  fi
}

partner_request() {
  local method="$1"
  local url="$2"
  local out="$3"
  shift 3
  request "${method}" "${url}" "${out}" -H "Authorization: Bearer ${PARTNER_SUPABASE_JWT}" "$@"
}

assert_status() {
  local actual="$1"
  local expected="$2"
  local label="$3"
  local body="${4:-}"
  if [[ "${actual}" != "${expected}" ]]; then
    echo "FAIL: ${label}: expected HTTP ${expected}, got ${actual}" >&2
    if [[ -n "${body}" && -s "${body}" ]]; then
      python3 - <<'PY' "${body}" >&2
import pathlib
import sys
print(pathlib.Path(sys.argv[1]).read_text(errors="replace")[:1200])
PY
    fi
    exit 1
  fi
  echo "PASS: ${label} (${actual})"
}

platform_login_if_needed() {
  if [[ -n "${PLATFORM_API_KEY}" || -n "${PLATFORM_COOKIE}" ]]; then
    return
  fi
  if [[ -z "${PLATFORM_LOGIN_EMAIL}" || -z "${PLATFORM_LOGIN_PASSWORD}" ]]; then
    echo "ERROR: set PLATFORM_API_KEY, PLATFORM_COOKIE, or PLATFORM_LOGIN_EMAIL/PLATFORM_LOGIN_PASSWORD." >&2
    exit 2
  fi
  local login_body="${TMP_DIR}/platform-login.json"
  local payload="${TMP_DIR}/platform-login-payload.json"
  python3 - <<'PY' "${PLATFORM_LOGIN_EMAIL}" "${PLATFORM_LOGIN_PASSWORD}" "${payload}"
import json
import pathlib
import sys
pathlib.Path(sys.argv[3]).write_text(json.dumps({"email": sys.argv[1], "password": sys.argv[2]}), encoding="utf-8")
PY
  local status
  status="$(curl -sS -X POST -o "${login_body}" -w "%{http_code}" -c "${PLATFORM_COOKIE_JAR}" \
    -H "Content-Type: application/json" \
    --data "@${payload}" \
    "${BASE_URL}/api/platform/auth/login")"
  rm -f "${payload}"
  assert_status "${status}" "200" "platform login" "${login_body}"
}

json_field() {
  local path="$1"
  local file="$2"
  python3 - <<'PY' "${path}" "${file}"
import json
import pathlib
import sys
data = json.loads(pathlib.Path(sys.argv[2]).read_text())
value = data
for part in sys.argv[1].split("."):
    if part:
        value = value[part]
if isinstance(value, bool):
    print("true" if value else "false")
elif value is None:
    print("")
else:
    print(value)
PY
}

resolve_deployment_id() {
  if [[ -n "${DEPLOYMENT_ID}" ]]; then
    echo "${DEPLOYMENT_ID}"
    return
  fi
  if [[ -n "${SHOP_DOMAIN}" ]]; then
    local encoded_shop health_body health_status
    encoded_shop="$(urlencode "${SHOP_DOMAIN}")"
    health_body="${TMP_DIR}/thinker-health-bootstrap.json"
    health_status="$(platform_request GET "${BASE_URL}/api/shopify/stores/${encoded_shop}/thinker-health" "${health_body}")"
    if [[ "${health_status}" == "200" ]]; then
      local linked
      linked="$(json_field "deploymentId" "${health_body}")"
      if [[ -n "${linked}" ]]; then
        echo "${linked}"
        return
      fi
    fi
  fi
  local deployments_body deployments_status
  deployments_body="${TMP_DIR}/deployments.json"
  deployments_status="$(platform_request GET "${BASE_URL}/api/deployments" "${deployments_body}")"
  assert_status "${deployments_status}" "200" "deployment list reachable" "${deployments_body}"
  python3 - <<'PY' "${deployments_body}"
import json
import pathlib
import sys
items = json.loads(pathlib.Path(sys.argv[1]).read_text())
def score(item):
    text = " ".join(str(item.get(key) or "") for key in ("name", "templateId", "environment"))
    return (0 if "shopify" in text.lower() else 1, 0 if item.get("runtimeBaseUrl") else 1, text)
selected = sorted(items, key=score)[0] if items else None
if not selected:
    raise SystemExit("No deployment was available for Thinker verification.")
print(selected["id"])
PY
}

cleanup() {
  local exit_code=$?
  if [[ -n "${TMP_DIR}" ]]; then
    rm -rf "${TMP_DIR}"
  fi
  exit "${exit_code}"
}

if [[ -z "${BASE_URL}" ]]; then
  echo "ERROR: set PLATFORM_BASE_URL." >&2
  exit 2
fi
BASE_URL="${BASE_URL%/}"
PLATFORM_UI_BASE_URL="${PLATFORM_UI_BASE_URL%/}"
PARTNER_UI_BASE_URL="${PARTNER_UI_BASE_URL%/}"
PLATFORM_API_KEY="$(resolve_secret_value PLATFORM_API_KEY)"
PLATFORM_COOKIE="$(resolve_secret_value PLATFORM_COOKIE)"
PLATFORM_LOGIN_EMAIL="$(resolve_secret_value PLATFORM_LOGIN_EMAIL)"
PLATFORM_LOGIN_PASSWORD="$(resolve_secret_value PLATFORM_LOGIN_PASSWORD)"
PARTNER_SUPABASE_JWT="$(resolve_secret_value PARTNER_SUPABASE_JWT)"
TMP_DIR="$(mktemp -d)"
PLATFORM_COOKIE_JAR="${TMP_DIR}/platform-cookie.jar"
trap cleanup EXIT

platform_login_if_needed

if [[ "${REQUIRE_PARTNER_PROOF}" == "true" && -z "${PARTNER_SUPABASE_JWT}" ]]; then
  echo "ERROR: THINKER_REQUIRE_PARTNER_PROOF=true requires PARTNER_SUPABASE_JWT." >&2
  exit 2
fi

if [[ -n "${PLATFORM_UI_BASE_URL}" ]]; then
  operator_ui_body="${TMP_DIR}/operator-ui.html"
  operator_ui_status="$(request GET "${PLATFORM_UI_BASE_URL}/thinker-resolver" "${operator_ui_body}")"
  assert_status "${operator_ui_status}" "200" "operator Thinker UI route reachable" "${operator_ui_body}"
  if ! grep -qi "<script" "${operator_ui_body}"; then
    echo "FAIL: operator Thinker UI route did not return app shell HTML." >&2
    exit 1
  fi
  echo "PASS: operator Thinker UI route exposes app shell"
fi

if [[ -n "${PARTNER_UI_BASE_URL}" ]]; then
  partner_ui_body="${TMP_DIR}/partner-ui.html"
  partner_ui_status="$(request GET "${PARTNER_UI_BASE_URL}/thinker" "${partner_ui_body}")"
  assert_status "${partner_ui_status}" "200" "partner Thinker UI route reachable" "${partner_ui_body}"
  if ! grep -qi "<script" "${partner_ui_body}"; then
    echo "FAIL: partner Thinker UI route did not return app shell HTML." >&2
    exit 1
  fi
  echo "PASS: partner Thinker UI route exposes app shell"
fi

readiness_body="${TMP_DIR}/readiness.json"
readiness_status="$(platform_request GET "${BASE_URL}/api/operator/thinker/readiness" "${readiness_body}")"
assert_status "${readiness_status}" "200" "Thinker readiness endpoint reachable" "${readiness_body}"
python3 - <<'PY' "${readiness_body}"
import json
import pathlib
import sys
data = json.loads(pathlib.Path(sys.argv[1]).read_text())
assert data.get("status") == "READY", data
assert data.get("failed") == 0, data
assert len(data.get("scenarios") or []) >= 7, data
print("PASS: Thinker readiness scenarios are green")
PY

DEPLOYMENT_ID="$(resolve_deployment_id)"
echo "INFO: using deployment ${DEPLOYMENT_ID}"

control_before_body="${TMP_DIR}/control-before.json"
control_before_status="$(platform_request GET "${BASE_URL}/api/operator/thinker/deployments/${DEPLOYMENT_ID}/control" "${control_before_body}")"
assert_status "${control_before_status}" "200" "Thinker deployment control readable" "${control_before_body}"

control_payload="${TMP_DIR}/control-payload.json"
python3 - <<'PY' "${EXECUTE_LOW_RISK}" "${control_payload}"
import json
import pathlib
import sys
execute = sys.argv[1].lower() == "true"
pathlib.Path(sys.argv[2]).write_text(json.dumps({
    "thinkerEnabled": True,
    "resolverPreviewEnabled": True,
    "governedExecutionEnabled": execute,
    "disabledActionFamilies": []
}), encoding="utf-8")
PY
control_after_body="${TMP_DIR}/control-after.json"
control_after_status="$(platform_request PUT "${BASE_URL}/api/operator/thinker/deployments/${DEPLOYMENT_ID}/control" "${control_after_body}" \
  -H "Content-Type: application/json" \
  --data "@${control_payload}")"
assert_status "${control_after_status}" "200" "Thinker deployment control updated" "${control_after_body}"

session_payload="${TMP_DIR}/session-payload.json"
python3 - <<'PY' "${DEPLOYMENT_ID}" "${SHOP_DOMAIN}" "${RUN_TAG}" "${session_payload}"
import json
import pathlib
import sys
deployment_id, shop_domain, run_tag, out = sys.argv[1:5]
payload = {
    "deploymentId": deployment_id,
    "shopDomain": shop_domain or None,
    "channel": "OPERATOR_VERIFICATION",
    "mode": "THINKER_DEEP",
    "userQuestion": f"Verification {run_tag}: shopper asks why a product recommendation is not grounded in current store data.",
    "userSafeAnswer": "I need to verify current store evidence before making a claim.",
    "evidence": [{
        "kind": "READ_ACTION_RESULT",
        "sourceKind": "VERIFICATION_PACK",
        "sourceIdentifier": run_tag,
        "freshness": "FRESH",
        "confidence": 0.92,
        "summary": "Verification pack captured live operator evidence for a read-only diagnosis path.",
        "safeSummary": "Live verification evidence is present for the read-only diagnosis path.",
        "operatorRawReference": "/verification/thinker/read-action"
    }]
}
if not shop_domain:
    payload.pop("shopDomain")
pathlib.Path(out).write_text(json.dumps(payload), encoding="utf-8")
PY
session_body="${TMP_DIR}/session.json"
session_status="$(platform_request POST "${BASE_URL}/api/operator/thinker/sessions" "${session_body}" \
  -H "Content-Type: application/json" \
  --data "@${session_payload}")"
assert_status "${session_status}" "200" "Thinker session persisted" "${session_body}"
SESSION_ID="$(json_field "id" "${session_body}")"

session_detail_body="${TMP_DIR}/session-detail.json"
session_detail_status="$(platform_request GET "${BASE_URL}/api/operator/thinker/sessions/${SESSION_ID}" "${session_detail_body}")"
assert_status "${session_detail_status}" "200" "Thinker session detail reachable" "${session_detail_body}"
python3 - <<'PY' "${session_detail_body}"
import json
import pathlib
import sys
data = json.loads(pathlib.Path(sys.argv[1]).read_text())
assert data["session"]["evidenceCount"] >= 1, data
assert data.get("plan"), data
assert data["evidence"][0].get("operatorRawReference"), data
print("PASS: Thinker session contains evidence, plan, and operator audit detail")
PY

evidence_body="${TMP_DIR}/evidence.json"
evidence_status="$(platform_request GET "${BASE_URL}/api/operator/thinker/sessions/${SESSION_ID}/evidence" "${evidence_body}")"
assert_status "${evidence_status}" "200" "Thinker evidence list reachable" "${evidence_body}"
EVIDENCE_ID="$(python3 - <<'PY' "${evidence_body}"
import json
import pathlib
import sys
items = json.loads(pathlib.Path(sys.argv[1]).read_text())
assert items, items
print(items[0]["id"])
PY
)"

export_body="${TMP_DIR}/session-export.md"
export_status="$(platform_request GET "${BASE_URL}/api/operator/thinker/sessions/${SESSION_ID}/export" "${export_body}")"
assert_status "${export_status}" "200" "Thinker markdown export reachable" "${export_body}"
if ! grep -q "${SESSION_ID}" "${export_body}"; then
  echo "FAIL: Thinker export did not include the session ID." >&2
  exit 1
fi
echo "PASS: Thinker markdown export includes session evidence"

proposal_payload="${TMP_DIR}/proposal-payload.json"
python3 - <<'PY' "${SESSION_ID}" "${EVIDENCE_ID}" "${RUN_TAG}" "${proposal_payload}"
import json
import pathlib
import sys
session_id, evidence_id, run_tag, out = sys.argv[1:5]
pathlib.Path(out).write_text(json.dumps({
    "sessionId": session_id,
    "actionId": "create_support_escalation",
    "actionFamily": "SUPPORT_ESCALATION",
    "targetDomain": "PARTNER_ENABLEMENT",
    "productBoundary": "SHOPIFY_COMPANION",
    "parameters": {
        "title": f"Thinker Resolver verification handoff {run_tag}",
        "severity": "LOW",
        "nextAction": "Review read-only diagnostic evidence and confirm no storefront claim is made without live evidence."
    },
    "sourceEvidenceItemIds": [evidence_id],
    "proposalText": "Create a partner-visible support escalation backed by the redacted Thinker evidence bundle."
}), encoding="utf-8")
PY
proposal_body="${TMP_DIR}/proposal.json"
proposal_status="$(platform_request POST "${BASE_URL}/api/operator/resolver/proposals" "${proposal_body}" \
  -H "Content-Type: application/json" \
  --data "@${proposal_payload}")"
assert_status "${proposal_status}" "200" "Resolver proposal persisted" "${proposal_body}"
PROPOSAL_ID="$(json_field "id" "${proposal_body}")"
POLICY_OUTCOME="$(json_field "latestPolicyDecision.outcome" "${proposal_body}")"
if [[ "${POLICY_OUTCOME}" != "ALLOWED" ]]; then
  echo "FAIL: Resolver policy did not allow support escalation: ${POLICY_OUTCOME}" >&2
  cat "${proposal_body}" >&2
  exit 1
fi
echo "PASS: Resolver policy allowed low-risk support escalation"

dry_run_body="${TMP_DIR}/dry-run.json"
dry_run_status="$(platform_request POST "${BASE_URL}/api/operator/resolver/proposals/${PROPOSAL_ID}/dry-run" "${dry_run_body}")"
assert_status "${dry_run_status}" "200" "Resolver dry-run completed" "${dry_run_body}"
DRY_RUN_ID="$(json_field "id" "${dry_run_body}")"
python3 - <<'PY' "${dry_run_body}"
import json
import pathlib
import sys
data = json.loads(pathlib.Path(sys.argv[1]).read_text())
assert data.get("status") == "COMPLETED", data
assert "Create one partner-visible support escalation" in data.get("expectedStateTransition", ""), data
print("PASS: Resolver dry-run reports non-mutating state transition")
PY

if [[ "${EXECUTE_LOW_RISK}" == "true" ]]; then
  execute_payload="${TMP_DIR}/execute-payload.json"
  python3 - <<'PY' "${DRY_RUN_ID}" "${RUN_TAG}" "${execute_payload}"
import json
import pathlib
import sys
dry_run_id, run_tag, out = sys.argv[1:4]
pathlib.Path(out).write_text(json.dumps({
    "dryRunId": dry_run_id,
    "idempotencyKey": f"thinker-resolver-{run_tag}",
    "confirmationText": "CREATE SUPPORT ESCALATION"
}), encoding="utf-8")
PY
  execute_body="${TMP_DIR}/execution.json"
  execute_status="$(platform_request POST "${BASE_URL}/api/operator/resolver/proposals/${PROPOSAL_ID}/execute" "${execute_body}" \
    -H "Content-Type: application/json" \
    --data "@${execute_payload}")"
  assert_status "${execute_status}" "200" "Resolver governed execution completed" "${execute_body}"
  python3 - <<'PY' "${execute_body}"
import json
import pathlib
import sys
data = json.loads(pathlib.Path(sys.argv[1]).read_text())
assert data.get("status") == "COMPLETED", data
result = data.get("executionResult") or {}
assert result.get("supportEscalationId"), data
assert result.get("evidenceBundleId"), data
print("PASS: Resolver execution created support escalation and evidence bundle")
PY
else
  echo "PASS: governed execution skipped by configuration; dry-run proof is complete"
fi

policy_body="${TMP_DIR}/policies.json"
policy_status="$(platform_request GET "${BASE_URL}/api/operator/resolver/policy-decisions" "${policy_body}")"
assert_status "${policy_status}" "200" "Resolver policy ledger reachable" "${policy_body}"
execution_ledger_body="${TMP_DIR}/executions.json"
execution_ledger_status="$(platform_request GET "${BASE_URL}/api/operator/resolver/executions" "${execution_ledger_body}")"
assert_status "${execution_ledger_status}" "200" "Resolver execution ledger reachable" "${execution_ledger_body}"

if [[ -n "${SHOP_DOMAIN}" ]]; then
  encoded_shop="$(urlencode "${SHOP_DOMAIN}")"
  health_body="${TMP_DIR}/thinker-health.json"
  health_status="$(platform_request GET "${BASE_URL}/api/shopify/stores/${encoded_shop}/thinker-health" "${health_body}")"
  assert_status "${health_status}" "200" "Shopify Thinker health reachable" "${health_body}"
  python3 - <<'PY' "${health_body}"
import json
import pathlib
import sys
data = json.loads(pathlib.Path(sys.argv[1]).read_text())
assert data.get("deploymentId"), data
assert data.get("status") in {"READY", "ATTENTION", "DISABLED"}, data
print("PASS: Shopify Thinker health returns deployment and status")
PY
fi

if [[ -n "${PARTNER_SUPABASE_JWT}" ]]; then
  stores_body="${TMP_DIR}/partner-stores.json"
  stores_status="$(partner_request GET "${BASE_URL}/api/partners/stores" "${stores_body}")"
  assert_status "${stores_status}" "200" "partner stores reachable" "${stores_body}"
  partner_store_id="$(python3 - <<'PY' "${stores_body}" "${SHOP_DOMAIN}"
import json
import pathlib
import sys
items = json.loads(pathlib.Path(sys.argv[1]).read_text())
shop = sys.argv[2].lower()
selected = None
for item in items:
    if shop and (item.get("shopDomain") or "").lower() == shop:
        selected = item
        break
selected = selected or (items[0] if items else None)
if selected:
    print(selected["id"])
PY
)"
  if [[ -z "${partner_store_id}" ]]; then
    if [[ "${REQUIRE_PARTNER_PROOF}" == "true" ]]; then
      echo "FAIL: no assigned partner store is available for partner Thinker proof." >&2
      exit 1
    fi
    echo "SKIP: no assigned partner store is available for partner Thinker proof."
  else
    partner_sessions_body="${TMP_DIR}/partner-thinker-sessions.json"
    partner_sessions_status="$(partner_request GET "${BASE_URL}/api/partners/stores/${partner_store_id}/thinker-sessions" "${partner_sessions_body}")"
    assert_status "${partner_sessions_status}" "200" "partner Thinker sessions reachable" "${partner_sessions_body}"
    partner_session_id="$(python3 - <<'PY' "${partner_sessions_body}" "${SESSION_ID}"
import json
import pathlib
import sys
items = json.loads(pathlib.Path(sys.argv[1]).read_text())
target = sys.argv[2]
assert any(item.get("id") == target for item in items), items
print(target)
PY
)"
    partner_detail_body="${TMP_DIR}/partner-thinker-detail.json"
    partner_detail_status="$(partner_request GET "${BASE_URL}/api/partners/thinker-sessions/${partner_session_id}" "${partner_detail_body}")"
    assert_status "${partner_detail_status}" "200" "partner Thinker session detail reachable" "${partner_detail_body}"
    python3 - <<'PY' "${partner_detail_body}"
import json
import pathlib
import sys
data = json.loads(pathlib.Path(sys.argv[1]).read_text())
session = data.get("session") or {}
assert session.get("tenantId") in (None, ""), session
assert session.get("customerId") in (None, ""), session
for item in data.get("evidence") or []:
    assert item.get("operatorRawReference") in (None, ""), item
for proposal in data.get("resolverProposals") or []:
    assert proposal.get("operatorParameters") in (None, ""), proposal
print("PASS: partner Thinker detail is scoped and redacted")
PY
  fi
elif [[ "${REQUIRE_PARTNER_PROOF}" == "true" ]]; then
  echo "FAIL: partner proof is required but PARTNER_SUPABASE_JWT is not configured." >&2
  exit 1
else
  echo "SKIP: PARTNER_SUPABASE_JWT not configured; partner Thinker proof skipped."
fi

echo "PASS: Thinker Resolver readiness verification completed"
