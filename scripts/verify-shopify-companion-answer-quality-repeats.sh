#!/usr/bin/env bash
set -euo pipefail

# Re-run the live Shopify Companion canonical answer-quality gate enough times
# to catch stochastic prompt/model regressions. The gate intentionally exercises
# multiple storefront surfaces and conversation modes rather than one mode:
# - max/search/internal guard paths use thinker_deep where configured
# - comparison/product-discovery depth uses navigator_deep where configured
# - cart/account action paths use executor where configured
# - product-insight, product-faq, and policy-strip rely on Bridge/runtime
#   surface and page-context mode derivation
#
# Release rule: require every repeat to PASS. For launch/release proof, keep
# ANSWER_QUALITY_REPEAT_COUNT at 3 or higher after a deploy.

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BRIDGE_BASE_URL="${SHOPIFY_BRIDGE_BASE_URL:-https://shopify-bridge-staging.46.224.145.148.sslip.io}"
SHOP_DOMAIN="${SHOP_DOMAIN:-shopping-companion-test.myshopify.com}"
QUERY_PACK="${READINESS_AUDIT_QUERY_PACK:-${REPO_ROOT}/scripts/verification/shopify-first-product-readiness/answer-quality-query-pack.json}"
REPEAT_COUNT="${ANSWER_QUALITY_REPEAT_COUNT:-3}"
TIMEOUT_SECONDS="${ANSWER_QUALITY_TIMEOUT:-75}"
OUT_ROOT="${ANSWER_QUALITY_OUT_ROOT:-/tmp}"
ACTIVE_TIER_PROFILE="${ANSWER_QUALITY_ACTIVE_TIER_PROFILE:-${READINESS_AUDIT_REQUIRED_BILLING_TIER:-}}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
RUN_ROOT="${OUT_ROOT%/}/shopify-answer-quality-${STAMP}-repeats"
SUMMARY_JSON="${RUN_ROOT}/repeat-summary.json"
SUMMARY_MD="${RUN_ROOT}/repeat-summary.md"

if ! [[ "${REPEAT_COUNT}" =~ ^[1-9][0-9]*$ ]]; then
  echo "ANSWER_QUALITY_REPEAT_COUNT must be a positive integer." >&2
  exit 2
fi

mkdir -p "${RUN_ROOT}"

echo "Shopify Companion live answer-quality repeat gate"
echo "Bridge: ${BRIDGE_BASE_URL}"
echo "Shop: ${SHOP_DOMAIN}"
echo "Query pack: ${QUERY_PACK}"
echo "Repeats: ${REPEAT_COUNT}"
echo "Active tier profile: ${ACTIVE_TIER_PROFILE:-ALL}"
echo "Output: ${RUN_ROOT}"

printf '{\n  "schemaVersion": 1,\n  "strategy": "shopify-companion-expanded-answer-quality-repeat-gate",\n  "bridgeBaseUrl": %s,\n  "shopDomain": %s,\n  "queryPack": %s,\n  "repeatCount": %s,\n  "runs": [\n' \
  "$(jq -Rn --arg value "${BRIDGE_BASE_URL}" '$value')" \
  "$(jq -Rn --arg value "${SHOP_DOMAIN}" '$value')" \
  "$(jq -Rn --arg value "${QUERY_PACK}" '$value')" \
  "${REPEAT_COUNT}" > "${SUMMARY_JSON}"

{
  echo "# Shopify Companion Answer-Quality Repeat Gate"
  echo
  echo "- Bridge: \`${BRIDGE_BASE_URL}\`"
  echo "- Shop: \`${SHOP_DOMAIN}\`"
  echo "- Query pack: \`${QUERY_PACK}\`"
  echo "- Repeats: \`${REPEAT_COUNT}\`"
  echo "- Active tier profile: \`${ACTIVE_TIER_PROFILE:-ALL}\`"
  echo
  echo "| Run | Decision | Passed | Total | Output |"
  echo "|---:|---|---:|---:|---|"
} > "${SUMMARY_MD}"

all_passed=true

for run in $(seq 1 "${REPEAT_COUNT}"); do
  OUT_DIR="${RUN_ROOT}/run-${run}"
  mkdir -p "${OUT_DIR}"
  echo "Running repeat ${run}/${REPEAT_COUNT} -> ${OUT_DIR}"
  tier_args=()
  if [[ -n "${ACTIVE_TIER_PROFILE}" ]]; then
    tier_args=(--active-tier-profile "${ACTIVE_TIER_PROFILE}")
  fi
  if python3 "${REPO_ROOT}/scripts/evaluate-shopify-companion-answers.py" \
    --bridge-base-url "${BRIDGE_BASE_URL}" \
    --shop-domain "${SHOP_DOMAIN}" \
    --query-pack "${QUERY_PACK}" \
    --out "${OUT_DIR}" \
    --timeout "${TIMEOUT_SECONDS}" \
    "${tier_args[@]}"; then
    :
  else
    all_passed=false
  fi

  decision="$(jq -r '.decision' "${OUT_DIR}/answer-quality-results.json")"
  passed="$(jq -r '.passedQueries' "${OUT_DIR}/answer-quality-results.json")"
  total="$(jq -r '.totalQueries' "${OUT_DIR}/answer-quality-results.json")"
  [[ "${decision}" == "PASS" ]] || all_passed=false

  if [[ "${run}" -gt 1 ]]; then
    printf ',\n' >> "${SUMMARY_JSON}"
  fi
  jq -n \
    --argjson run "${run}" \
    --arg decision "${decision}" \
    --argjson passed "${passed}" \
    --argjson total "${total}" \
    --arg outDir "${OUT_DIR}" \
    '{run:$run, decision:$decision, passedQueries:$passed, totalQueries:$total, outDir:$outDir}' >> "${SUMMARY_JSON}"

  echo "| ${run} | \`${decision}\` | ${passed} | ${total} | \`${OUT_DIR}\` |" >> "${SUMMARY_MD}"
done

if [[ "${all_passed}" == "true" ]]; then
  final_decision="PASS"
else
  final_decision="FAIL"
fi

printf '\n  ],\n  "decision": %s\n}\n' "$(jq -Rn --arg value "${final_decision}" '$value')" >> "${SUMMARY_JSON}"

{
  echo
  echo "Final decision: \`${final_decision}\`"
} >> "${SUMMARY_MD}"

echo "Repeat gate decision: ${final_decision}"
echo "Summary JSON: ${SUMMARY_JSON}"
echo "Summary Markdown: ${SUMMARY_MD}"

[[ "${final_decision}" == "PASS" ]]
