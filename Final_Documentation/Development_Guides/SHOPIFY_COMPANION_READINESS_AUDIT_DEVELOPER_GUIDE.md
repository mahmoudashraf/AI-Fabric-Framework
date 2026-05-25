# Shopify Companion Readiness Audit Developer Guide

Status: developer and LLM maintenance guide for the live Shopify Companion first-product readiness gate (2026-04-29)

This guide is for:

- engineers maintaining the readiness audit
- LLM implementation sessions
- platform operators debugging readiness evidence
- release-gate maintainers

Related guides and plans:

- [Shopify Companion Readiness Audit Operator Guide](../User_Guides/SHOPIFY_COMPANION_READINESS_AUDIT_OPERATOR_GUIDE.md)
- [Shopify Companion Developer And Store Admin Guide](./SHOPIFY_COMPANION_DEVELOPER_AND_STORE_ADMIN_GUIDE.md)
- [Shopify Internal Development And Full Deployment Guide](./SHOPIFY_INTERNAL_DEVELOPMENT_AND_FULL_DEPLOYMENT_GUIDE.md)
- [Platform Credentials And Secret Boundaries Guide](./PLATFORM_CREDENTIALS_AND_SECRET_BOUNDARIES_GUIDE.md)
- [005 Shopify Companion First Product Readiness Audit](../../doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/005_SHOPIFY_COMPANION_FIRST_PRODUCT_READINESS_AUDIT.md)

---

## 1) System Purpose

The readiness audit is the first concrete Product Generation Audit gate. It verifies that Shopify Companion Starter is ready for controlled design-partner activity.

It is intentionally more than a UI smoke test. It checks:

- canonical product truth
- entitlement boundaries
- storefront and merchant-admin behavior
- live bridge verification
- query-to-answer quality
- operator evidence visibility
- support and App Review collateral
- design-partner readiness

The gate can produce `TECHNICAL_READY`, `DESIGN_PARTNER_READY`, `PARTIAL`, or `NOT_READY`.

It must not produce `MARKET_PROVEN`.

---

## 2) Code Ownership Map

Verification suite catalog and release-gate wiring:

```text
Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/PlatformVerificationSuiteCatalog.java
Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/PlatformVerificationSuiteScriptContextService.java
```

Readiness backend API:

```text
Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/shopify/web/ShopifyCompanionReadinessAuditController.java
Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/shopify/service/ShopifyCompanionReadinessAuditService.java
Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/shopify/model/
```

Operator UI:

```text
Platfrom/ui/src/pages/ShopifyReadinessAuditPage.tsx
Platfrom/ui/src/api/platformApi.ts
Platfrom/ui/src/App.tsx
Platfrom/ui/src/layout/AppShell.tsx
```

Shell and answer-quality verification:

```text
scripts/verify-shopify-first-product-readiness-audit.sh
scripts/evaluate-shopify-companion-answers.py
scripts/verify-shopify-companion-answer-quality-repeats.sh
scripts/verification/shopify-first-product-readiness/answer-quality-query-pack.json
```

Regression tests:

```text
Platfrom/backend/src/test/java/com/ai/fabric/platform/backend/shopify/service/ShopifyCompanionReadinessAuditServiceTest.java
Platfrom/backend/src/test/java/com/ai/fabric/platform/backend/deployment/service/PlatformVerificationSuiteScriptContextServiceTest.java
Platfrom/backend/src/test/java/com/ai/fabric/platform/backend/deployment/service/PlatformVerificationSuiteServiceTest.java
```

---

## 3) Runtime Evidence Flow

The suite key is:

```text
shopify-first-product-readiness-audit
```

The full release-gate suite key is:

```text
full-platform-release-readiness
```

Evidence can come from either:

- the latest standalone `shopify-first-product-readiness-audit` run
- the latest `full-platform-release-readiness` run that contains the `shopify-first-product-readiness-audit` stage

This fallback is required. In production, the most authoritative proof is often the full release gate, not a standalone readiness run.

The backend service parses:

- suite status
- stage status
- stage summary message
- `QUERY_RESULT ...` log lines emitted by the answer-quality evaluator
- `Readiness decision: ...` emitted by the readiness script

The operator UI consumes the normalized state from:

```text
GET /api/shopify/readiness-audit/latest
GET /api/shopify/readiness-audit/definition
```

---

## 4) API Contract

`GET /api/shopify/readiness-audit/definition`

Returns stable audit metadata:

- suite key
- product ref
- target store
- tier profile
- artifact root
- decision options
- rubric dimensions
- forbidden internal terms
- checklist definition
- query pack definition

`GET /api/shopify/readiness-audit/latest`

Returns current evidence state:

- definition
- latest evidence-producing suite run
- latest Shopify readiness stage
- decision
- freshness status
- completed timestamp
- blockers
- next handoff
- checklist results
- parsed answer results
- evidence artifact references

Expected passing live shape:

```json
{
  "decision": "DESIGN_PARTNER_READY",
  "freshnessStatus": "FRESH",
  "blockers": [],
  "latestRun": {
    "suiteKey": "full-platform-release-readiness",
    "status": "PASSED"
  },
  "latestStage": {
    "stageKey": "shopify-first-product-readiness-audit",
    "status": "PASSED"
  }
}
```

---

## 5) Script Contract

Primary runner:

```bash
scripts/verify-shopify-first-product-readiness-audit.sh
```

Required environment:

```text
PLATFORM_BASE_URL
SHOPIFY_BRIDGE_BASE_URL
SHOP_DOMAIN
```

Required for the live Shopify Companion verifier stage:

```text
PLATFORM_API_KEY or PLATFORM_API_KEY_FILE
```

Required when the audit must prepare non-Free billing posture:

```text
SHOPIFY_BRIDGE_ADMIN_API_KEY or SHOPIFY_BRIDGE_ADMIN_API_KEY_FILE
```

Optional:

```text
PRODUCT_SERVICE_REF
READINESS_AUDIT_OUT
READINESS_AUDIT_QUERY_PACK
READINESS_AUDIT_LOCAL_GATES
READINESS_AUDIT_REQUIRED_BILLING_TIER
READINESS_AUDIT_ENSURE_BILLING_STATE
```

Secret handling rules:

- Prefer `*_FILE` env vars for secrets.
- Do not print secret values.
- Do not commit generated `/tmp` packets.
- Do not paste cookies, tokens, API keys, or private headers into docs.

---

## 6) Answer-Quality Evaluator

Command shape:

```bash
python3 scripts/evaluate-shopify-companion-answers.py \
  --bridge-base-url "${SHOPIFY_BRIDGE_BASE_URL}" \
  --shop-domain "${SHOP_DOMAIN}" \
  --query-pack scripts/verification/shopify-first-product-readiness/answer-quality-query-pack.json \
  --out /tmp/shopify-first-product-readiness-audit
```

The evaluator posts each query to:

```text
/api/storefront/shops/{shopDomain}/chat/query
```

It checks:

- HTTP 200
- `conversationId` present
- answer non-empty
- forbidden claims absent
- forbidden internal terms absent
- required deterministic concepts present
- no governed-action execution language

It writes:

- `answer-quality-query-pack.json`
- `answer-quality-results.json`
- `answer-quality-audit.md`

The script emits one `QUERY_RESULT` JSON line per query. The backend parses those lines for the operator UI.

---

## 7) Query Pack Maintenance

Canonical query pack:

```text
scripts/verification/shopify-first-product-readiness/answer-quality-query-pack.json
```

Required P0 categories:

- AI search and product discovery
- product page context
- product FAQ
- comparison
- policy
- source gap
- out-of-scope redirect
- Starter order-lookup guard
- governed-action guard
- internal-language guard

When adding a query:

1. Give it a stable `queryId`.
2. Set `productRef` behavior through the pack, not hardcoded evaluator logic.
3. Include tier profile, surface, expected behavior, required concepts, and forbidden claims.
4. Use deterministic concepts only when they are stable enough for a live store.
5. Keep global forbidden internal terms product-safe and merchant-safe.
6. Update backend definition if the operator UI should expose the new canonical query.
7. Add or update tests when parsing or decision behavior changes.

Do not add broad free-text assertions that make the gate flaky.

### Live Repeat Strategy

The canonical answer-quality gate must be treated as stochastic live product evidence, not a one-shot unit test. After changing prompts, runtime orchestration, Bridge storefront context handling, Marketplace action metadata, MCP gateway behavior, billing/package posture, or Shopify store data, run the canonical pack repeatedly against live staging.

Use:

```bash
ANSWER_QUALITY_REPEAT_COUNT=3 \
SHOPIFY_BRIDGE_BASE_URL="https://shopify-bridge-staging.46.224.145.148.sslip.io" \
SHOP_DOMAIN="shopping-companion-test.myshopify.com" \
bash scripts/verify-shopify-companion-answer-quality-repeats.sh
```

Pass criteria:

- every repeat must return `PASS`
- every repeat must pass all queries in the canonical pack
- outputs must be kept under `/tmp` or another non-committed evidence directory
- any single failure blocks release evidence until explained and fixed

The repeat script writes:

```text
/tmp/shopify-answer-quality-<timestamp>-repeats/repeat-summary.json
/tmp/shopify-answer-quality-<timestamp>-repeats/repeat-summary.md
/tmp/shopify-answer-quality-<timestamp>-repeats/run-N/answer-quality-results.json
/tmp/shopify-answer-quality-<timestamp>-repeats/run-N/answer-quality-audit.md
```

Mode coverage is defined by the query pack, not by a single script flag. The current canonical pack intentionally exercises:

- `thinker_deep` for Max/search/internal tool guard paths
- `navigator_deep` for comparison and product-discovery depth paths
- `executor` for cart and account/order action paths
- surface-derived behavior for `product-insight`, `product-faq`, and `policy-strip`

Do not call the gate “verified” from one mode only. The release evidence should state the repeat count, the result of each run, and the output root.

---

## 8) Checklist Maintenance

Checklist definition lives in:

```text
ShopifyCompanionReadinessAuditService#checklist
```

When adding a checklist item:

1. Decide whether it is blocking.
2. Add concise pass criteria.
3. Map it to an evidence artifact.
4. Make sure the runner actually creates or references that evidence.
5. Add UI-safe wording. No raw provider, token, or secret details.
6. Add tests if decision, blocker, or evidence parsing changes.

Do not create a checklist row that is only aspirational. If it is listed as a gate, the script, suite, or UI must produce real evidence for it.

---

## 9) Local Verification

Run focused checks after readiness code changes:

```bash
bash -n scripts/verify-shopify-first-product-readiness-audit.sh
python3 -m py_compile scripts/evaluate-shopify-companion-answers.py
mvn -f Platfrom/backend/pom.xml -q -Dtest=ShopifyCompanionReadinessAuditServiceTest,PlatformVerificationSuiteScriptContextServiceTest,PlatformVerificationSuiteServiceTest test
npm --prefix Platfrom/ui run build
git diff --check
```

Run broader checks when entitlement, bridge, storefront, or suite execution behavior changes:

```bash
mvn -f Platfrom/backend/pom.xml -q test
mvn -f product-services/shopify-bridge-service/pom.xml -q test
npm --prefix product-services/shopify-bridge-service/ui run build
```

---

## 10) Live Verification

Health check:

```bash
curl -sS -m 30 "${PLATFORM_BASE_URL}/actuator/health"
```

Readiness endpoint check with an authenticated Platform session:

```bash
curl -sS -m 30 -b /tmp/platform-secret-update-cookies.txt \
  "${PLATFORM_BASE_URL}/api/shopify/readiness-audit/latest" |
  jq '{decision,freshnessStatus,blockerCount:(.blockers|length),checklistTotal:(.checklistResults|length),answerTotal:(.answerResults|length),latestRun:{id:.latestRun.id,suiteKey:.latestRun.suiteKey,status:.latestRun.status},latestStage:{stageKey:.latestStage.stageKey,status:.latestStage.status}}'
```

Passing live result should show:

- `decision`: `DESIGN_PARTNER_READY`
- `freshnessStatus`: `FRESH`
- blocker count: `0`
- checklist count: `10`
- answer count: `10`
- latest stage key: `shopify-first-product-readiness-audit`
- latest stage status: `PASSED`

Full release gate proof can be checked with:

```bash
curl -sS -m 30 -b /tmp/platform-secret-update-cookies.txt \
  "${PLATFORM_BASE_URL}/api/verification-suites/runs/${RUN_ID}" |
  jq '{id,status,summaryMessage,completedAt,stageCount:(.stages|length),passedStages:([.stages[]|select(.status=="PASSED")]|length)}'
```

---

## 11) Troubleshooting

`/api/shopify/readiness-audit/latest` returns `NOT_READY` and `MISSING`:

- No recent standalone readiness run or full release-gate run with a Shopify readiness stage is available.
- Run the standalone suite or full release gate.

Full gate passed but readiness UI says `NOT_READY`:

- Confirm deployed backend includes the full-gate evidence fallback.
- Check that the full gate stage key is exactly `shopify-first-product-readiness-audit`.
- Check the latest deployed backend commit.

Answer results are empty:

- Confirm the stage log includes `QUERY_RESULT` lines.
- Confirm the answer evaluator ran inside the readiness script.
- Confirm the query pack path is valid.

Answer-quality failure:

- Inspect `answer-quality-audit.md`.
- Classify the failure as backend, grounding/helpfulness, entitlement/governed-action, forbidden claim, or internal language.
- Fix product behavior before weakening the query pack.

Bridge admin failure:

- Confirm `SHOPIFY_BRIDGE_ADMIN_API_KEY` is present through secret file or platform secret propagation.
- Confirm it matches the deployed bridge shared secret.
- Do not paste the key into chat, docs, or logs.

Transient Railway 502 immediately after deploy:

- Wait for app warmup.
- Recheck `/actuator/health`.
- If it persists, inspect Railway deployment logs and recent startup errors.

---

## 12) Production Rules

- No dummy implementation.
- No stubbed readiness rows.
- No fake pass states.
- No secret leakage in artifacts or docs.
- No merchant-facing internal provider/runtime/vector terminology.
- Do not mark `DESIGN_PARTNER_READY` unless the operator UI or equivalent review surface can show evidence without chat history.
- Do not mark `MARKET_PROVEN` from this audit.
- Treat the Platform control plane as the source of truth for readiness runs and release-gate evidence.
