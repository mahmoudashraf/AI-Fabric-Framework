# Thinker Resolver Developer Guide

Status: developer guide for the 006 Thinker/Resolver implementation (2026-04-29)

This guide documents the current full-stack implementation and verification path.

Related roadmap files:

- `doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/006_THINKER_RESOLVER_GOVERNED_ISSUE_RESOLUTION_BLUEPRINT.md`
- `doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/006_1_THINKER_PHASE_1_READ_ONLY_ISSUE_RESOLUTION_PRODUCTIZATION.md`
- `doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/006_2_RESOLVER_DRY_RUN_AND_POLICY_SIMULATION.md`
- `doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/006_3_GOVERNED_LOW_RISK_WRITE_EXECUTION.md`
- `doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/006_4_PRODUCTIZED_RESOLUTION_ASSISTANT_READINESS_AND_ROLLOUT.md`

---

## 1) Data Model

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

## 2) Backend Package

Main package:

- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/thinker`

Key components:

- `ThinkerResolverService`
- `ThinkerResolverOperatorController`
- `ThinkerResolverPartnerController`
- `ThinkerResolverShopifyController`
- entity and repository classes under the same package

Existing runtime hook:

- `PublicConsumerBridgeChatService` records Thinker sessions from live public chat responses when Thinker mode is enabled and allowed.

Security rules:

- operator endpoints use Platform operator/admin auth
- partner endpoints use Supabase partner auth plus active assignment checks
- partner Thinker read requires `PRODUCT_CONFIG_READ`
- partner support handoff requires `SUPPORT_MANAGE`
- Shopify health endpoint is merchant/product-service safe and does not expose secrets

---

## 3) UI Surfaces

Platform UI:

- route: `/thinker-resolver`
- file: `Platfrom/ui/src/pages/ThinkerResolverPage.tsx`

Partner UI:

- route: `/thinker`
- file: `Platfrom/partner-ui/src/pages/ThinkerSessionsPage.tsx`
- store workspace Thinker tab: `Platfrom/partner-ui/src/pages/StoreWorkspacePage.tsx`

Shopify Bridge merchant UI:

- session includes `thinkerHealth`
- embedded merchant app shows a Thinker deep diagnosis health card

---

## 4) Resolver Execution Contract

Current executable action family:

- `SUPPORT_ESCALATION`

Execution requires:

- deployment governed execution enabled
- action family not disabled
- policy decision `ALLOWED`
- completed dry-run
- confirmation text exactly `CREATE SUPPORT ESCALATION`
- idempotency key

Execution result:

- creates a real `PartnerEvidenceBundleEntity`
- creates a real `PartnerSupportEscalationEntity`
- records execution status and external references

No other write action family should be added without a new policy path, dry-run contract, post-action verification, tests, and readiness proof.

---

## 5) Verification

Local focused verification:

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

The live script intentionally keeps low-risk execution off by default. Set `THINKER_EXECUTE_LOW_RISK=true` only when creating a real support escalation is approved for that sandbox/design-partner proof.

---

## 6) Release Gate

The full Platform release readiness suite includes:

- standalone suite: `thinker-resolver-readiness`
- blocking stage inside `full-platform-release-readiness`

The suite context requires:

- Platform UI base URL
- Partner UI base URL
- Shopify shop domain
- `PARTNER_SUPABASE_JWT` Platform secret

The script enables Thinker/Resolver preview for the target deployment as part of verification and leaves governed execution disabled unless explicitly requested.
