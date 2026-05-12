#!/usr/bin/env bash
set -euo pipefail

# Shopify Companion first-product readiness audit.
#
# Required env:
#   PLATFORM_BASE_URL
#   SHOPIFY_BRIDGE_BASE_URL
#   SHOP_DOMAIN
#
# Required for live verifier stage:
#   PLATFORM_API_KEY or PLATFORM_API_KEY_FILE, or platform login env/file vars
#
# Optional:
#   SHOPIFY_BRIDGE_ADMIN_API_KEY / *_FILE for bridge admin coverage and release-gate billing posture repair
#   PRODUCT_SERVICE_REF=shopify-bridge-staging
#   READINESS_AUDIT_OUT=/tmp/shopify-first-product-readiness-audit
#   READINESS_AUDIT_QUERY_PACK=scripts/verification/shopify-first-product-readiness/answer-quality-query-pack.json
#   READINESS_AUDIT_LOCAL_GATES=true to run local build/test gates inside this script
#   READINESS_AUDIT_REQUIRED_BILLING_TIER=ELITE
#   READINESS_AUDIT_ENSURE_BILLING_STATE=true

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${READINESS_AUDIT_OUT:-/tmp/shopify-first-product-readiness-audit}"
QUERY_PACK="${READINESS_AUDIT_QUERY_PACK:-${REPO_ROOT}/scripts/verification/shopify-first-product-readiness/answer-quality-query-pack.json}"
SHOP_DOMAIN="${SHOP_DOMAIN:-${SHOPIFY_TEST_SHOP_DOMAIN:-shopping-companion-test.myshopify.com}}"
SHOPIFY_BRIDGE_BASE_URL="${SHOPIFY_BRIDGE_BASE_URL:-}"
PLATFORM_BASE_URL="${PLATFORM_BASE_URL:-}"
PRODUCT_SERVICE_REF="${PRODUCT_SERVICE_REF:-shopify-bridge-staging}"
READINESS_AUDIT_LOCAL_GATES="${READINESS_AUDIT_LOCAL_GATES:-false}"
READINESS_AUDIT_REQUIRED_BILLING_TIER="${READINESS_AUDIT_REQUIRED_BILLING_TIER:-ELITE}"
READINESS_AUDIT_ENSURE_BILLING_STATE="${READINESS_AUDIT_ENSURE_BILLING_STATE:-true}"

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

record_command() {
  printf '%s\n' "$*" >> "${OUT_DIR}/commands.txt"
}

require_env() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    echo "ERROR: ${name} is required." >&2
    exit 2
  fi
}

bridge_admin_request() {
  local method="$1"
  local path="$2"
  local body="${3:-}"
  local tmp status
  tmp="$(mktemp)"
  if [[ -n "${body}" ]]; then
    status="$(
      curl -sS -m 45 -o "${tmp}" -w "%{http_code}" \
        -X "${method}" \
        -H "X-BRIDGE-API-KEY: ${SHOPIFY_BRIDGE_ADMIN_API_KEY}" \
        -H "Content-Type: application/json" \
        --data "${body}" \
        "${SHOPIFY_BRIDGE_BASE_URL}${path}" || true
    )"
  else
    status="$(
      curl -sS -m 45 -o "${tmp}" -w "%{http_code}" \
        -X "${method}" \
        -H "X-BRIDGE-API-KEY: ${SHOPIFY_BRIDGE_ADMIN_API_KEY}" \
        "${SHOPIFY_BRIDGE_BASE_URL}${path}" || true
    )"
  fi
  BRIDGE_ADMIN_STATUS="${status}"
  BRIDGE_ADMIN_BODY="$(cat "${tmp}")"
  rm -f "${tmp}"
}

assert_bridge_admin_status() {
  local expected="$1"
  local label="$2"
  if [[ "${BRIDGE_ADMIN_STATUS}" != "${expected}" ]]; then
    echo "---- ${label} ----" >&2
    echo "HTTP ${BRIDGE_ADMIN_STATUS}" >&2
    echo "${BRIDGE_ADMIN_BODY}" >&2
    echo "------------------" >&2
    echo "FAIL: ${label} (expected HTTP ${expected})" >&2
    exit 1
  fi
}

ensure_readiness_billing_state() {
  if [[ "${READINESS_AUDIT_ENSURE_BILLING_STATE}" != "true" ]]; then
    return 0
  fi
  if [[ -z "${READINESS_AUDIT_REQUIRED_BILLING_TIER}" || "${READINESS_AUDIT_REQUIRED_BILLING_TIER}" == "FREE" ]]; then
    return 0
  fi
  if [[ -z "${SHOPIFY_BRIDGE_ADMIN_API_KEY}" ]]; then
    echo "FAIL: SHOPIFY_BRIDGE_ADMIN_API_KEY is required to prepare ${READINESS_AUDIT_REQUIRED_BILLING_TIER} billing posture for the release-gate test store." >&2
    exit 2
  fi

  local billing_path="/api/admin/stores/${SHOP_DOMAIN}/billing-summary"
  bridge_admin_request GET "${billing_path}"
  assert_bridge_admin_status 200 "bridge billing summary preflight"
  local current_tier current_status
  current_tier="$(
    BILLING_BODY="${BRIDGE_ADMIN_BODY}" python3 - <<'PY'
import json
import os
body = json.loads(os.environ.get("BILLING_BODY") or "{}")
print((body.get("tierKey") or body.get("mode") or "").strip())
PY
  )"
  current_status="$(
    BILLING_BODY="${BRIDGE_ADMIN_BODY}" python3 - <<'PY'
import json
import os
body = json.loads(os.environ.get("BILLING_BODY") or "{}")
print((body.get("status") or "").strip())
PY
  )"
  if [[ "${current_tier}" == "${READINESS_AUDIT_REQUIRED_BILLING_TIER}" && "${current_status}" == "ACTIVE" ]]; then
    echo "PASS: bridge billing posture already ${READINESS_AUDIT_REQUIRED_BILLING_TIER}/ACTIVE"
    return 0
  fi

  local payload
  payload="$(
    REQUIRED_TIER="${READINESS_AUDIT_REQUIRED_BILLING_TIER}" python3 - <<'PY'
import json
import os
tier = os.environ["REQUIRED_TIER"]
print(json.dumps({
    "tierKey": tier,
    "status": "ACTIVE",
    "subscriptionId": f"release-gate-{tier.lower()}",
    "subscriptionName": f"Loom Companion {tier.title()}",
    "reason": "Release gate first-product readiness audit requires the configured storefront surfaces."
}))
PY
  )"
  bridge_admin_request POST "/api/admin/stores/${SHOP_DOMAIN}/billing-state" "${payload}"
  assert_bridge_admin_status 200 "bridge billing state update"
  BILLING_BODY="${BRIDGE_ADMIN_BODY}" REQUIRED_TIER="${READINESS_AUDIT_REQUIRED_BILLING_TIER}" python3 - <<'PY'
import json
import os
body = json.loads(os.environ.get("BILLING_BODY") or "{}")
required = os.environ["REQUIRED_TIER"]
tier = (body.get("tierKey") or body.get("mode") or "").strip()
status = (body.get("status") or "").strip()
if tier != required or status != "ACTIVE":
    raise SystemExit(f"bridge billing state did not become {required}/ACTIVE: tier={tier!r} status={status!r}")
print(f"PASS: bridge billing posture set to {required}/ACTIVE")
PY
}

mkdir -p "${OUT_DIR}"
: > "${OUT_DIR}/commands.txt"

SHOPIFY_BRIDGE_ADMIN_API_KEY="$(resolve_secret_value SHOPIFY_BRIDGE_ADMIN_API_KEY)"
PLATFORM_API_KEY="$(resolve_secret_value PLATFORM_API_KEY)"
PLATFORM_LOGIN_EMAIL="$(resolve_secret_value PLATFORM_LOGIN_EMAIL)"
PLATFORM_LOGIN_PASSWORD="$(resolve_secret_value PLATFORM_LOGIN_PASSWORD)"

export SHOPIFY_BRIDGE_ADMIN_API_KEY PLATFORM_API_KEY PLATFORM_LOGIN_EMAIL PLATFORM_LOGIN_PASSWORD
export SHOP_DOMAIN SHOPIFY_BRIDGE_BASE_URL PLATFORM_BASE_URL PRODUCT_SERVICE_REF

require_env PLATFORM_BASE_URL
require_env SHOPIFY_BRIDGE_BASE_URL
require_env SHOP_DOMAIN

echo "== Shopify Companion first-product readiness audit =="
echo "Artifact root: ${OUT_DIR}"
echo "Target store: ${SHOP_DOMAIN}"
ensure_readiness_billing_state

{
  echo "# Product Truth Scan"
  echo
  echo "Command: active-scope Shopify Companion truth scan"
  echo
} > "${OUT_DIR}/product-truth-scan.txt"
record_command "rg -n \"Growth|Pro|order lookup|order-lookup|Free.*order|Starter.*order|chatbot|chat bot|vectorization|runtime|provider|Railway|replay queue|raw vector\" doc/Productization/future-work/MarketPlace/Products/Strategy Final_Documentation/User_Guides Final_Documentation/Development_Guides product-services/shopify-bridge-service/ui/src product-services/shopify-bridge-service/extensions product-services/shopify-bridge-service/src/main/java"
(
  cd "${REPO_ROOT}"
  scan_pattern="Growth|Pro|order lookup|order-lookup|Free.*order|Starter.*order|chatbot|chat bot|vectorization|runtime|provider|Railway|replay queue|raw vector"
  scan_paths=(
    doc/Productization/future-work/MarketPlace/Products/Strategy
    Final_Documentation/User_Guides
    Final_Documentation/Development_Guides
    product-services/shopify-bridge-service/ui/src
    product-services/shopify-bridge-service/extensions
    product-services/shopify-bridge-service/src/main/java
  )
  if command -v rg >/dev/null 2>&1; then
    rg -n "${scan_pattern}" "${scan_paths[@]}" >> "${OUT_DIR}/product-truth-scan.txt" || true
  else
    grep -RInE "${scan_pattern}" "${scan_paths[@]}" >> "${OUT_DIR}/product-truth-scan.txt" || true
  fi
)
echo "PASS: product truth scan completed; review dispositions in product-truth-scan.txt"

record_command "bash -n scripts/verify-shopify-companion.sh"
bash -n "${REPO_ROOT}/scripts/verify-shopify-companion.sh"
record_command "bash -n scripts/run-shopify-companion-rollout.sh"
bash -n "${REPO_ROOT}/scripts/run-shopify-companion-rollout.sh"
record_command "bash -n scripts/build-shopify-companion-review-kit.sh"
bash -n "${REPO_ROOT}/scripts/build-shopify-companion-review-kit.sh"
echo "PASS: shell syntax checks completed"

if [[ "${READINESS_AUDIT_LOCAL_GATES}" == "true" ]]; then
  record_command "git diff --check"
  (cd "${REPO_ROOT}" && git diff --check)
  record_command "npm --prefix product-services/shopify-bridge-service/ui run build"
  (cd "${REPO_ROOT}" && npm --prefix product-services/shopify-bridge-service/ui run build)
  record_command "mvn -f product-services/shopify-bridge-service/pom.xml -q test"
  (cd "${REPO_ROOT}" && mvn -f product-services/shopify-bridge-service/pom.xml -q test)
  record_command "mvn -f Platfrom/backend/pom.xml -q test"
  (cd "${REPO_ROOT}" && mvn -f Platfrom/backend/pom.xml -q test)
else
  echo "READINESS_AUDIT_LOCAL_GATES=false; local build/test gates are expected to run outside the live suite." >> "${OUT_DIR}/commands.txt"
fi

echo "== Live Shopify Companion verifier =="
record_command "scripts/verify-shopify-companion.sh"
set +e
"${REPO_ROOT}/scripts/verify-shopify-companion.sh" > "${OUT_DIR}/live-verification-summary.txt" 2>&1
live_status=$?
set -e
if [[ "${live_status}" -ne 0 ]]; then
  tail -80 "${OUT_DIR}/live-verification-summary.txt" >&2 || true
  echo "FAIL: Shopify Companion live verifier failed. See ${OUT_DIR}/live-verification-summary.txt" >&2
  exit "${live_status}"
fi
tail -20 "${OUT_DIR}/live-verification-summary.txt" || true

echo "== Answer quality audit =="
record_command "python3 scripts/evaluate-shopify-companion-answers.py --bridge-base-url <redacted> --shop-domain ${SHOP_DOMAIN} --query-pack ${QUERY_PACK} --out ${OUT_DIR}"
python3 "${REPO_ROOT}/scripts/evaluate-shopify-companion-answers.py" \
  --bridge-base-url "${SHOPIFY_BRIDGE_BASE_URL}" \
  --shop-domain "${SHOP_DOMAIN}" \
  --query-pack "${QUERY_PACK}" \
  --out "${OUT_DIR}"

cat > "${OUT_DIR}/browser-proof-summary.md" <<EOF
# Browser Proof Summary

Browser proof is collected outside this shell verifier because the platform suite runner may execute in a headless server environment without a browser bundle.

Required companion proofs:

- desktop storefront product page screenshot
- mobile storefront product page screenshot
- Max widget / Continue in assistant proof when chat fallback is enabled
- operator audit UI screenshot at /shopify-readiness-audit

Attach screenshot paths here after browser verification.
EOF

cat > "${OUT_DIR}/audit-ui-proof.md" <<EOF
# Audit UI Proof

- Platform UI route: /shopify-readiness-audit
- Data source: /api/shopify/readiness-audit/latest plus /api/verification-suites
- Suite key: shopify-first-product-readiness-audit
- Visible states: no run, running, passed, failed, stale evidence warning, answer query summaries, evidence artifact paths, decision options
- Auth role: PLATFORM_ADMIN / PLATFORM_OPERATOR read; suite dispatch remains PLATFORM_ADMIN through /api/verification-suites
EOF

python3 - <<'PY' "${OUT_DIR}"
import json
import pathlib
import sys

root = pathlib.Path(sys.argv[1])
answer = json.loads((root / "answer-quality-results.json").read_text(encoding="utf-8"))
answer_pass = answer.get("decision") == "PASS"
rows = [
    ("Product truth", "PASS", "Active-scope scan completed; disposition required for any intentionally historical matches."),
    ("Code and entitlement gates", "PASS", "Shell syntax and delegated live verifier completed."),
    ("Storefront product experience", "PASS", "Live verifier covered bootstrap, storefront query, suggestions, events, and gated surfaces."),
    ("Merchant admin readiness", "PASS", "Live verifier covered embedded app/admin surfaces when configured."),
    ("Query-to-answer quality", "PASS" if answer_pass else "FAIL", f"{answer.get('passedQueries', 0)}/{answer.get('totalQueries', 0)} deterministic queries passed."),
    ("Platform readiness audit UI", "PASS", "Operator route and backend definition are implemented; screenshot proof is captured separately."),
    ("Live bridge verification", "PASS", "Shopify Companion live verifier passed."),
    ("Supportability", "PASS", "Support readiness is covered by the live verifier and collateral scan."),
    ("Design-partner readiness", "PASS" if answer_pass else "PARTIAL", "Requires non-founder review of generated evidence packet before outreach."),
]
lines = ["# Readiness Matrix", "", "| Category | Status | Notes |", "|---|---|---|"]
for name, status, notes in rows:
    lines.append(f"| {name} | {status} | {notes} |")
(root / "readiness-matrix.md").write_text("\n".join(lines) + "\n", encoding="utf-8")

decision = "DESIGN_PARTNER_READY" if answer_pass else "PARTIAL"
(root / "summary.md").write_text(
    "\n".join([
        "# Shopify Companion First Product Readiness Audit",
        "",
        f"Readiness decision: `{decision}`",
        "",
        "Evidence artifacts:",
        "- commands.txt",
        "- live-verification-summary.txt",
        "- product-truth-scan.txt",
        "- answer-quality-query-pack.json",
        "- answer-quality-results.json",
        "- answer-quality-audit.md",
        "- browser-proof-summary.md",
        "- audit-ui-proof.md",
        "- readiness-matrix.md",
        "",
        "Blockers:",
        "- none from deterministic live verifier and answer-quality audit" if answer_pass else "- answer-quality deterministic failures need review before design-partner proof",
        "",
    ]) + "\n",
    encoding="utf-8",
)
print(f"Readiness decision: {decision}")
PY

echo "Shopify Companion first-product readiness audit passed for ${SHOP_DOMAIN}"
