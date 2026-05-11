# Thinker Resolver Developer Guide

Status: developer guide for the current 006 Thinker/Resolver implementation (2026-05-01)

This guide documents the implemented full-stack Thinker/Resolver architecture, code locations, route contracts, verification path, and current boundaries.

Related user guides:

- [Thinker Resolver User Guide](../User_Guides/THINKER_RESOLVER_USER_GUIDE.md)
- [Thinker Resolver Operator Guide](../User_Guides/THINKER_RESOLVER_OPERATOR_GUIDE.md)
- [Thinker Resolver Partner Guide](../User_Guides/THINKER_RESOLVER_PARTNER_GUIDE.md)

Related roadmap files:

- `doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/006_THINKER_RESOLVER_GOVERNED_ISSUE_RESOLUTION_BLUEPRINT.md`
- `doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/006_1_THINKER_PHASE_1_READ_ONLY_ISSUE_RESOLUTION_PRODUCTIZATION.md`
- `doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/006_2_RESOLVER_DRY_RUN_AND_POLICY_SIMULATION.md`
- `doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/006_3_GOVERNED_LOW_RISK_WRITE_EXECUTION.md`
- `doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/006_4_PRODUCTIZED_RESOLUTION_ASSISTANT_READINESS_AND_ROLLOUT.md`

---

## 1) Architecture Boundary

Thinker/Resolver is built on the existing runtime, action, RAG, auth, and Platform control-plane architecture.

Key ownership rules:

- Runtime/Thinker generates final answers from RAG and eligible read-action evidence.
- Bridge transports shopper requests, storefront context, session state, and diagnostics.
- Bridge must not replace successful runtime evidence with canned semantic fallback answers.
- Framework/core modules must not contain Shopify or product-domain text matching.
- Shopify-specific wording belongs in Shopify deployment configuration and product-specific prompt settings.
- Commerce-curated modules may contain generic commerce concepts only when they can work across commerce platforms.
- Resolver writes are governed Platform workflows, not direct storefront chat writes.

If runtime returns only `Action executed.` while evidence exists, treat that as a runtime answer-generation contract issue. Do not hide it in Bridge with a canned answer.

---

## 2) Data Model

Migration:

- `Platfrom/backend/src/main/resources/db/migration/V70__thinker_resolver_governed_issue_resolution.sql`

Main persisted records:

- deployment controls
- issue sessions
- evidence items
- resolution plans
- audit events
- Resolver intent proposals
- policy decisions
- dry-runs
- executions

The Platform database is the source of truth. Partner support records created by execution use the existing Partner Enablement tables; no duplicate partner-owned shadow store is created.

---

## 3) Backend Code

Main package:

- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/thinker`

Key components:

- `ThinkerResolverService`
- `ThinkerResolverOperatorController`
- `ThinkerResolverPartnerController`
- `ThinkerResolverShopifyController`
- entity and repository classes under the same package

Runtime hook:

- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/PublicConsumerBridgeChatService.java`

The public consumer bridge chat service records Thinker sessions from live public chat responses when Thinker mode is enabled and allowed.

Security rules:

- operator endpoints use Platform operator/admin auth
- partner endpoints use Supabase partner auth plus active assignment checks
- partner Thinker read requires `PRODUCT_CONFIG_READ`
- partner support handoff requires `SUPPORT_MANAGE`
- Shopify health endpoint is merchant/product-service safe and does not expose secrets

---

## 4) API Contracts

Operator routes:

- `GET /api/operator/thinker/readiness`
- `GET /api/operator/thinker/sessions`
- `POST /api/operator/thinker/sessions`
- `GET /api/operator/thinker/sessions/{sessionId}`
- `GET /api/operator/thinker/sessions/{sessionId}/evidence`
- `GET /api/operator/thinker/sessions/{sessionId}/export`
- `GET /api/operator/thinker/deployments/{deploymentId}/control`
- `PUT /api/operator/thinker/deployments/{deploymentId}/control`
- `GET /api/operator/resolver/proposals`
- `POST /api/operator/resolver/proposals`
- `GET /api/operator/resolver/policy-decisions`
- `POST /api/operator/resolver/proposals/{proposalId}/dry-run`
- `POST /api/operator/resolver/proposals/{proposalId}/execute`
- `GET /api/operator/resolver/executions`

Partner routes:

- `GET /api/partners/stores/{storeId}/thinker-sessions`
- `GET /api/partners/thinker-sessions/{sessionId}`
- `POST /api/partners/thinker-sessions/{sessionId}/escalations`

Shopify health route:

- `GET /api/shopify/stores/{shopDomain}/thinker-health`

Storefront bridge chat route:

- `POST /api/storefront/shops/{shopDomain}/chat/query`

---

## 5) UI Surfaces

Platform UI:

- route: `/thinker-resolver`
- file: `Platfrom/ui/src/pages/ThinkerResolverPage.tsx`
- API client: `Platfrom/ui/src/api/platformApi.ts`

Partner UI:

- route: `/thinker`
- file: `Platfrom/partner-ui/src/pages/ThinkerSessionsPage.tsx`
- store workspace Thinker tab: `Platfrom/partner-ui/src/pages/StoreWorkspacePage.tsx`
- API client: `Platfrom/partner-ui/src/api/thinker.ts`

Shopify Bridge merchant UI:

- merchant session includes `thinkerHealth`
- embedded merchant app shows a Thinker deep diagnosis health card
- relevant UI file: `product-services/shopify-bridge-service/ui/src/App.tsx`

Storefront widget:

- sends mode, position, attachments, and storefront context through Bridge
- selected widget mode should be respected in runtime metadata
- structured action evidence should render as cards when product data is present

---

## 6) Resolver Execution Contract

Current executable action family:

- `SUPPORT_ESCALATION`

Current action id:

- `create_support_escalation`

Execution requires:

- deployment governed execution enabled
- Resolver preview enabled where the proposal is created
- action family not disabled
- policy decision `ALLOWED`
- completed dry-run
- confirmation text exactly `CREATE SUPPORT ESCALATION`
- idempotency key

Execution result:

- creates a real `PartnerEvidenceBundleEntity`
- creates a real `PartnerSupportEscalationEntity`
- records execution status and external references

Execution does not:

- mutate Shopify product data
- mutate orders, refunds, fulfillment, billing, or theme settings
- bypass partner assignment checks
- create placeholder support records

No other write action family should be added without a new policy path, dry-run contract, post-action verification, tests, UI, documentation, and readiness proof.

---

## 7) Dry-Run Contract

Dry-run is non-mutating.

The current dry-run record includes:

- target action
- validated/redacted parameters
- expected state transition
- expected side effects
- warnings
- unsupported fields
- idempotency posture
- rollback posture
- evidence freshness
- product boundary
- status

For `SUPPORT_ESCALATION`, dry-run must explain that execution creates one partner-visible support escalation and one evidence bundle, and that no Shopify customer, order, billing, or catalog state will be mutated.

---

## 8) Storefront Answer Contract

Storefront answer quality depends on cooperation between RAG, read actions, attachments, and generation.

Rules:

- Read-action planning must be LLM/tool-contract driven.
- Action parameters should be generated by the planner from the user question and context.
- Attachments are request context and should be respected for vague prompts.
- RAG can run with read actions and can be used again when action evidence is insufficient.
- If read actions return structured evidence, runtime should generate the final user answer from that evidence.
- Bridge should surface diagnostics or pass through generated answers; it should not replace them with product-domain fallback text.
- Shopify Companion uses the commerce-curated runtime pack with information retrieval configured to generate a final answer, so retrieval-only placeholders such as `Search completed.` are runtime configuration defects, not Bridge UI copy problems.
- System context parameters such as shopper session identifiers must be injected by trusted runtime context or hidden from public clarification payloads; never ask shoppers to provide internal parameter names.

Known disabled path:

- `relationship_query` is disabled from the shopper path until it cooperates cleanly with RAG and attachments and no longer bypasses the intended retrieval flow.

---

## 9) Local Verification

Run focused verification before committing code changes:

```bash
bash -n scripts/verify-thinker-resolver-readiness.sh
bash -n scripts/verify-platform-code-regression.sh
mvn -f Platfrom/backend/pom.xml -q -Dtest=ThinkerResolverIntegrationTest,PublicConsumerBridgeChatServiceTest test
mvn -f product-services/shopify-bridge-service/pom.xml -q test
npm --prefix Platfrom/ui run build
npm --prefix Platfrom/partner-ui run build
npm --prefix product-services/shopify-bridge-service/ui run build
git diff --check
```

For docs-only changes, at minimum run:

```bash
git diff --check
```

---

## 10) Live Verification

Live verification after deployment:

```bash
PLATFORM_BASE_URL=https://ai-fabric-framework-production-324f.up.railway.app \
PLATFORM_UI_BASE_URL=https://platform-ui-production-00e3.up.railway.app \
PARTNER_UI_BASE_URL=https://ai-fabric-framework-production-158d.up.railway.app \
THINKER_SHOP_DOMAIN=shopping-companion-test.myshopify.com \
THINKER_EXECUTE_LOW_RISK=false \
THINKER_REQUIRE_PARTNER_PROOF=true \
scripts/verify-thinker-resolver-readiness.sh
```

Required secrets must come from local/Railway/GitHub secret storage, never from committed docs:

- Platform operator/admin auth
- `PARTNER_SUPABASE_JWT` for partner proof

The live script keeps low-risk execution off by default. Set `THINKER_EXECUTE_LOW_RISK=true` only when creating a real support escalation is approved for that sandbox/design-partner proof.

---

## 11) Storefront Query Verification

Use the storefront widget or Bridge storefront chat endpoint to test answer behavior.

Recommended queries:

- `Show me products related to student laptops.`
- `Need to see more details about high performance laptops for gaming.`
- `Compare AtlasBook 14 Laptop, Harbor Student 15 Laptop, and Aurora 2-in-1 14 Laptop based on the product details you have.`
- `Which laptop options are best for a student who needs portability and value?`
- `Compare available snowboards under $800 and explain what evidence is missing before claiming one is safest.`
- `Create support escalation for this unresolved shopper issue.`

Expected diagnostics:

- HTTP response should be 2xx
- response `success` should be true for normal product questions
- `result.metadata.orchestrationPolicy.mode` should match the selected mode or documented default
- product answers should include generated text, not only raw action result JSON
- support escalation requests should not directly create support records from storefront chat
- unregistered actions should not be selected by the planner

---

## 12) Release Gate

The full Platform release readiness suite includes:

- standalone suite: `thinker-resolver-readiness`
- blocking stage inside `full-platform-release-readiness`

The suite context requires:

- Platform UI base URL
- Partner UI base URL
- Shopify shop domain
- `PARTNER_SUPABASE_JWT` Platform secret when partner proof is required

The script enables Thinker/Resolver preview for the target deployment as part of verification and leaves governed execution disabled unless explicitly requested.

---

## 13) Change Rules

When changing Thinker/Resolver:

- keep changes aligned with the current source-of-truth rule: Platform owns records; Bridge transports and reports
- do not introduce mocks, stubs, fake success, or placeholder execution paths
- do not add product-specific text matching to framework/core modules
- do not weaken partner assignment checks
- do not log secrets, bearer tokens, or API keys
- include tests for policy, dry-run, idempotency, redaction, and route auth
- update this guide and the user guide when behavior changes
