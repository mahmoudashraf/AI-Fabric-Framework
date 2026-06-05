# 010.18 Shopify Companion Production Release And App Listing Readiness Plan

Status: controlled production smoke green, public listing still gated, created 2026-06-05, last updated 2026-06-05

Related plans:

- [010.8 Shopify Companion Next Urgent Steps Readiness Plan](010_8_SHOPIFY_COMPANION_NEXT_URGENT_STEPS_READINESS_PLAN.md)
- [010.12 Production Deployment Execution Plan](010_12_PRODUCTION_DEPLOYMENT_EXECUTION_PLAN.md)
- [010.14 Consumer-Bound Runtime Assignment And Direct Private Auth Plan](010_14_CONSUMER_BOUND_RUNTIME_ASSIGNMENT_AND_DIRECT_PRIVATE_AUTH_PLAN.md)
- [010.16 Practical Dev, Staging, Production Deployment Model](010_16_PRACTICAL_DEV_STAGING_PRODUCTION_DEPLOYMENT_MODEL.md)
- [010.17 Grounding-Eligible Read Action Post-Action Generation And LLM Facts Plan](010_17_GROUNDING_ELIGIBLE_READ_ACTION_POST_ACTION_GENERATION_AND_LLM_FACTS_PLAN.md)

## Purpose

Prepare Loom Companion for the release posture requested on 2026-06-05:

- release what is already achieved for Loom Companion;
- keep the claim set narrow and truthful;
- finish the minimum production-readiness gates for a controlled/private design-partner release;
- keep public Shopify App Store/self-service launch blocked until Shopify listing, billing, protected-data, and support evidence are complete.

This plan is intentionally practical. It tracks the current production domain switch, the live storefront blocker, Shopify listing requirements, and the next work items that can be executed without inventing placeholder behavior.

## Current Release Decision

Current status: controlled/private production release is technically viable for the `shopping-companion-test.myshopify.com` proof store after the 2026-06-05 fixes and live smoke.

Allowed target after the P0 gates pass:

- controlled private/design-partner release;
- read-first Loom Companion V1;
- production domains;
- no public self-service claims;
- no autonomous checkout, broad Customer Account MCP, Checkout MCP, refund, return, or order-write claims.

Not allowed yet:

- public Shopify App Store submission;
- broad self-service install claims;
- paid public billing claims until billing is configured and tested with Shopify Billing API production posture;
- protected customer/order data claims until Partner Dashboard approval and live evidence are current.

Remaining public-launch blockers:

- `SHOPIFY_BRIDGE_BILLING_TEST=true` in production Bridge env means public paid launch/listing must stay blocked until production billing charges are configured and tested with `test=false`.
- Public App Store reviewer package, screenshots, screencast, reviewer credentials, support links, privacy/terms evidence, and install/reinstall proof still need final packaging.
- Protected customer/order data posture must be finalized for the public V1 claim set, especially because the production OAuth scope set currently includes `read_orders`.
- Public claims must stay read-first and must not imply checkout automation, autonomous purchasing, broad order/customer writes, Customer Account MCP, or Checkout MCP.

## 2026-06-05 Current Evidence

Completed checks:

- `https://shopify-bridge.loomai.pro/actuator/health` returned `200` / `UP`.
- `https://shopify-bridge.loomai.pro/` returned `200`.
- Shopify OAuth install flow for `shopping-companion-test.myshopify.com` redirects with production callback `https://shopify-bridge.loomai.pro/auth/shopify/callback`.
- Shopify OAuth install flow advertises the released production scope set: `read_products`, `read_content`, `read_legal_policies`, `read_metaobjects`, `read_metaobject_definitions`, and `read_orders`.
- Tracked Shopify app TOML files point at `https://shopify-bridge.loomai.pro`.
- Companion theme-extension block defaults point at `https://shopify-bridge.loomai.pro`.
- Invalid Shopify webhook HMAC returns `401`, so webhook verification fails closed.
- `https://api.loomai.pro/actuator/health` returned `200` / `UP`.
- Console runtime config points at `https://api.loomai.pro`.
- Partner runtime config points at `https://api.loomai.pro`.
- CORS preflight from `https://console.loomai.pro` to `https://api.loomai.pro/api/platform/auth/session` returned `200` with the expected allowed origin.
- Known production Shopify runtime `http://dep-8c3e7259.46.225.162.106.sslip.io/actuator/health` returned `200` / `UP`.
- Shopify CLI released app/theme version `loom-companion-55` with production URLs.
- Production Coolify access works from the local shell through `/tmp/coolify_production_api_token.secret`, without printing token values.
- Production Bridge Coolify env readback confirms `SHOPIFY_BRIDGE_PUBLIC_BASE_URL=https://shopify-bridge.loomai.pro` and `SHOPIFY_BRIDGE_PLATFORM_BASE_URL=https://api.loomai.pro`.
- Production Bridge Coolify env readback confirms `SHOPIFY_BRIDGE_PLATFORM_ADMIN_API_KEY` is configured.
- Production Bridge Coolify env readback shows `SHOPIFY_BRIDGE_BILLING_MODE=SHOPIFY_APP_SUBSCRIPTION` and `SHOPIFY_BRIDGE_BILLING_TEST=true`.
- Platform store mapping for `shopping-companion-test.myshopify.com` is present and live: `consumerId=shopify-shopping-companion-test`, `deploymentId=dep-8c3e7259`, `deploymentStatus=ACTIVE`, `syncStatus=SYNCED`, `sourceReadinessStatus=READY`, `widgetStatus=ENABLED`, `storefrontReady=true`, and `goLiveEligible=true`.
- Corrected Shopify runtime security/defaults were published and applied through the supported go-live flow. Latest green release is `rel-4286bee2`, version `ver-d6dd23c3`, verification run `vrf-143e2e82`, status `APPLIED_VERIFIED`, verification `PASSED`, provisioning `ACTIVE`.
- Production Bridge request-shape fix commit `00cf72c98` was pushed and deployed as Coolify deployment `wglbp9vr48rvor6okhhfk32f`.
- Platform action-catalog pruning fix commit `d6f47fda3` was pushed and Platform backend was deployed as Coolify deployment `zosvv1hbwmlfdy4psr09ul1w`.
- Commerce read-action generation fix commit `3bc8dbfcd` was pushed; version `ver-d6dd23c3` was applied to runtime so storefront READ action results generate shopper-facing answers.
- New version artifacts expose the supported Shopify MCP actions `shopify_search_catalog`, `shopify_search_policies`, and `shopify_get_product_details`; standalone legacy aliases such as `list_products`, `search_products`, and `get_product_details` are not exposed as runtime actions or routes.
- Live production storefront smoke after the final release passed:
  - Bridge health `200` / `UP`;
  - bootstrap `200`, `available=true`, `consumerId=shopify-shopping-companion-test`, `deploymentId=dep-8c3e7259`, billing `ACTIVE`;
  - suggestions `200`, `success=true`, `5` suggestions;
  - query `200`, `success=true`, `type=ACTION_EXECUTED`, generated shopper-facing snowboard answer, `answerStartsWithJson=false`.
- Final evidence directories:
  - `/tmp/shopify_companion_golive_after_commerce_generation_20260605T170536Z`;
  - `/tmp/shopify_companion_artifact_check_after_commerce_generation_20260605T171641Z`;
  - `/tmp/shopify_companion_live_after_commerce_generation_20260605T171656Z`.

Blocking checks:

- No controlled-release runtime/bootstrap/chat blocker is currently known after the 2026-06-05 final smoke.
- The Coolify token copied in the private handoff was stale and returned `401`; the temp local production token works. Refresh the private handoff copy before relying on it as the durable operator source.
- Public listing remains blocked by billing test mode, listing/reviewer package, and protected-data/scope posture.

## Current Shopify App Store Requirement Check

Checked against current Shopify public docs on 2026-06-05:

- Shopify App Store requirements: `https://shopify.dev/docs/apps/launch/shopify-app-store/app-store-requirements`
- Pass app review: `https://shopify.dev/docs/apps/launch/app-store-review/pass-app-review`
- Protected customer data: `https://shopify.dev/docs/apps/launch/protected-customer-data`
- Billing: `https://shopify.dev/docs/apps/launch/billing`

Implications for Loom Companion:

- The controlled/private release can proceed before public App Store submission because the final P0 runtime/bootstrap smoke is clean for the proof store.
- Public App Store submission is not currently blocked by bootstrap, but must stay blocked until billing, listing/reviewer package, protected-data/scope posture, and support/onboarding evidence are complete.
- If the public app charges merchants, production charge requests must use Shopify Billing API / Shopify App Pricing and must not remain in test-charge mode.
- Current production Bridge has billing mode set to `SHOPIFY_APP_SUBSCRIPTION`, but `SHOPIFY_BRIDGE_BILLING_TEST=true`; this is acceptable for testing, not for public paid launch.
- Public review needs complete OAuth install/reinstall proof, no fatal UI/web errors, testing instructions, reviewer credentials if needed, and a short screencast matching the actual released feature set.
- Online-store functionality must use theme app extensions and show widgets without storefront errors.
- Public apps using customer/order data must request the required protected customer data access in Partner Dashboard and provide data-minimization/security evidence. The current `read_orders` scope should be treated as a review-sensitive claim boundary until the public V1 scope story is finalized.
- New public app Admin API usage should be reviewed against Shopify's GraphQL Admin API requirement; avoid adding new REST Admin API dependencies for public V1 unless already justified by compatibility.

## P0 Controlled Release Gates

### P0.1 Restore Production Admin Access

Status: partially complete.

Goal:

- regain read-only and mutation-capable production administration through the approved private operator path;
- do not print or commit any token;
- confirm the current Coolify production token can read version and non-secret application env rows.

Current diagnosis:

- `/tmp/coolify_production_api_token.secret` works for read-only production Coolify checks.
- The private handoff's direct token copy appears stale and returned `401`.
- Non-secret env readback for the production Bridge app is available through the temp local token.

Acceptance:

- `GET /api/v1/version` succeeds against production Coolify from the local operator shell: completed with temp local token.
- Production Bridge app env readback is possible without exposing secrets: completed with temp local token.
- `SHOPIFY_BRIDGE_PUBLIC_BASE_URL` and `SHOPIFY_BRIDGE_PLATFORM_BASE_URL` read back as production domains: completed.
- Billing and advanced MCP gates can be read without guessing: partially complete; billing env was read, MCP readiness is still a Platform/store readiness blocker.
- Private handoff token is refreshed and rechecked: pending.

### P0.2 Fix Storefront Runtime Assignment Readiness

Status: completed for the production proof store.

Goal:

- make the `shopify-shopping-companion-test` consumer assignment return an externally ready backend-mediated private runtime assignment;
- keep runtime assignment discovery backend-only;
- preserve fail-closed behavior.

Current diagnosis:

- production Bridge is healthy;
- production runtime `dep-8c3e7259` is healthy;
- production Platform store mapping points `shopify-shopping-companion-test` at `consumerId=shopify-shopping-companion-test` and `deploymentId=dep-8c3e7259`;
- the old blocker was an assignment/published-security-config readiness problem, not a dead runtime;
- publishing/applying the corrected Shopify runtime defaults restored externally ready backend-mediated runtime assignment.

Completed remediation:

1. Refreshed/published Shopify Companion runtime security/default config for `dep-8c3e7259` / `shopify-shopping-companion-test`.
2. Applied corrected versions through supported go-live releases:
   - `rel-70270f95` / `ver-623292c1` after legacy action pruning;
   - `rel-4286bee2` / `ver-d6dd23c3` after commerce read-action generation config.
3. Confirmed the runtime auth contract for the assigned runtime:
   - `runtimeAuthMode=PRIVATE_RUNTIME_SIGNED_ASSERTION`;
   - `preferredIntegrationMode=BACKEND_MEDIATED_PRIVATE_RUNTIME`;
   - Bridge-mediated bootstrap, suggestions, and query succeed.
4. Preserved fail-closed assignment discovery; anonymous runtime-assignment access still returns `401`.

Acceptance:

- storefront bootstrap returns `200` and `available=true`: completed.
- bootstrap includes expected consumer id, deployment id, billing state, enabled surfaces, conversation modes, and Bridge-backed chat URLs: completed.
- one storefront chat query succeeds through the production Bridge: completed.
- one suggestions request succeeds through the production Bridge: completed.
- failure cases still fail closed when assignment or runtime auth is wrong: still required as part of broader release-gate regression, but no current fail-open evidence was found.

### P0.3 Repair Store Go-Live Readiness Evidence

Status: completed for controlled production proof.

Goal:

- make Platform store readiness agree with the controlled release target;
- avoid public launch until webhook/MCP readiness is current.

Current diagnosis:

- Platform store readiness reports `storefrontReady=true`.
- Platform store readiness reports `goLiveEligible=true`.
- Final store readback after release `rel-4286bee2` reports no go-live or storefront blocking reasons.
- Public App Store/self-service release claims are still blocked by billing/listing/protected-data posture, not by current controlled-store runtime readiness.

Acceptance:

- Store readiness no longer contains stale blocker text for the controlled proof store: completed.
- Shopify MCP endpoint/tool readiness is verified for the three exposed read actions in the deployed artifacts and final smoke: completed for controlled V1 read-first proof.
- Public App Store scope/webhook posture still needs final reviewer-package validation before public launch.

### P0.4 Add Secret-Safe Bridge Runtime Auth Diagnostics

Status: completed locally.

Goal:

- make production Bridge diagnostics show whether private runtime auth material is configured without exposing secret values.

Completed changes:

- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/diagnostics/model/ShopifyBridgeOverviewResponse.java` now includes `runtimeTrustedBackendApiKeyConfigured` and `runtimePrivateAssertionSigningKeyConfigured`.
- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/diagnostics/service/ShopifyBridgeDiagnosticsService.java` populates those booleans and advertises `runtime-private-auth-readiness`.
- Tests cover both configured and missing runtime private-auth material.

Verification:

- `mvn -f product-services/shopify-bridge-service/pom.xml -Dtest=ShopifyBridgeDiagnosticsServiceTest,ShopifyBridgeAdminControllerTest test` passed.

### P0.5 Refresh Live Shopify Companion Verification

Status: completed for targeted controlled-production smoke; full public release package still pending.

Goal:

- rerun the full Shopify Companion live gate after assignment readiness and current store readiness are restored.

Acceptance:

- targeted live smoke passed against `https://shopify-bridge.loomai.pro`.
- final query answer is generated text from Shopify MCP catalog data and does not start with raw JSON.
- full `scripts/verify-shopify-companion.sh`, browser proof, support-readiness parity, and uninstall/cleanup should still be rerun for the public/listing package.

### P0.6 Freeze Claim-Safe Controlled Release Package

Status: pending product/release-owner packaging.

Allowed claims:

- AI search;
- product insights;
- policy answers;
- product FAQ;
- comparison guidance;
- grounded answers from store data;
- embedded storefront intelligence;
- merchant freshness and support visibility.

Disallowed claims:

- autonomous purchasing;
- checkout automation;
- automatic order/customer writes;
- full support desk replacement;
- universal review-provider support;
- all themes with zero setup;
- Customer Account MCP or Checkout MCP unless their live production evidence is current and explicitly in scope.

Acceptance:

- listing copy, screenshots, support guide, review script, and merchant setup notes all use the same read-first claim set;
- no public listing material implies unsupported action automation.

## P1 Public App Store Gates

Status: blocked until public listing gates pass.

Required before public App Store submission:

- Shopify billing posture is unambiguous.
- If paid, all charges use Shopify Billing API and production charge requests use `test=false`.
- Requested scopes are minimized to the actual public V1 claim set, or optional/protected scopes are justified with evidence.
- Protected customer/order data usage is approved in the Partner Dashboard if any public flow uses it.
- App UI is stable after install and reinstall.
- Theme extension setup instructions and deep links are complete.
- Screenshots show the actual live app UI and unique surfaces.
- Review screencast matches the live claim set.
- Privacy, terms, support, and reviewer credentials are ready.
- Rollback/deactivation proof is current.

Reference:

- Shopify App Store requirements: `https://shopify.dev/docs/apps/launch/shopify-app-store/app-store-requirements`
- Shopify protected customer data requirements: `https://shopify.dev/docs/apps/launch/protected-customer-data`
- Shopify pass app review checklist: `https://shopify.dev/docs/apps/launch/app-store-review/pass-app-review`

## Execution Log

### 2026-06-05 Plan Created

Status: completed.

Notes:

- Created after the production domain switch to separate controlled/private release readiness from public App Store readiness.
- Initial live evidence shows production domains are aligned, but storefront bootstrap is blocked by runtime assignment readiness.

### 2026-06-05 Safe Live Checks And Assignment Diagnosis

Status: completed for read-only checks.

Notes:

- Production Bridge, Platform API, console CORS, Shopify OAuth redirect, and known runtime health are green.
- Storefront bootstrap is still blocked by assignment `externalIntegrationReady=false`.
- Platform store mapping and credentials prove the deployment/runtime are present and trusted-backend access is configured.
- Assignment-level readiness requires a published security config that explicitly accepts `platform-consumer-bridge` and consumer audience `shopify-shopping-companion-test`.
- Production Bridge billing remains in test mode and should not be presented as public paid launch-ready.
- Production Bridge runtime private-auth env rows were not visible in Coolify readback and need explicit operator confirmation/configuration before Bridge-to-runtime chat proof.

### 2026-06-05 Bridge Runtime Auth Diagnostics Added

Status: completed locally.

Notes:

- Added secret-safe Bridge overview booleans for trusted backend API key and private assertion signing key configuration.
- Added focused tests and verified the diagnostics slice.

### 2026-06-05 Storefront Bootstrap And Runtime Assignment Restored

Status: completed and deployed.

Notes:

- Fixed Bridge storefront runtime request shape in commit `00cf72c98` (`Normalize Shopify storefront runtime chat requests`), pushed to `origin/Platform-V10`, and deployed production Bridge as Coolify deployment `wglbp9vr48rvor6okhhfk32f`.
- Fixed the Platform Shopify Companion action catalog in commit `d6f47fda3` (`Prune unsupported Shopify companion legacy actions`), pushed to `origin/Platform-V10`, and deployed production Platform backend as Coolify deployment `zosvv1hbwmlfdy4psr09ul1w`.
- Re-ran supported Shopify go-live after the action-catalog fix: release `rel-70270f95`, version `ver-623292c1`, verification run `vrf-d9b16146`, final status `APPLIED_VERIFIED`, verification `PASSED`.
- Artifact proof for `ver-623292c1` showed only supported Shopify MCP action ids as standalone runtime actions and routes; legacy generic aliases were no longer exposed.
- Live smoke then reached `shopify_search_catalog` successfully, but the storefront query still returned raw MCP catalog JSON as the answer. This created the final answer-quality blocker.

### 2026-06-05 Commerce Read-Action Answer Generation Fixed

Status: completed, deployed, and live verified.

Notes:

- Fixed the commerce curated pack in commit `3bc8dbfcd` (`Force commerce read action answer generation`), pushed to `origin/Platform-V10`.
- Commerce storefront modes now set `force-grounding-eligible-read-action-post-generation: true`; stale legacy read-action aliases were removed from commerce mode allowlists.
- Verification passed:
  - `mvn -f ai-infrastructure-module/ai-infrastructure-core/pom.xml -Dtest=IntentHandlingStepPostActionGenerationTest,OrchestrationPolicyResolutionStepTest test`;
  - `mvn -f ai-infrastructure-module/pom.xml -pl curated/ai-curated-commerce -am -Dtest=CommerceCuratedPackTest -Dsurefire.failIfNoSpecifiedTests=false test`;
  - `git diff --check` for the commerce pack files.
- Re-ran supported Shopify go-live: release `rel-4286bee2`, version `ver-d6dd23c3`, verification run `vrf-143e2e82`, final status `APPLIED_VERIFIED`, verification `PASSED`, provisioning `ACTIVE`.
- Store readiness after the release: `deploymentStatus=ACTIVE`, `goLiveEligible=true`, `storefrontReady=true`, no go-live or storefront blockers.
- Final artifact proof for `ver-d6dd23c3`:
  - no standalone runtime actions/routes for `list_products`, `search_products`, `get_product_details`, `check_availability`, `get_policy`, `view_cart`, `add_product_to_cart`, `add_to_cart`, or `update_cart_quantity`;
  - supported MCP action ids present: `shopify_search_catalog`, `shopify_search_policies`, `shopify_get_product_details`.
- Final live smoke against `https://shopify-bridge.loomai.pro` passed:
  - health `200` / `UP`;
  - bootstrap `200`, `available=true`, consumer `shopify-shopping-companion-test`, deployment `dep-8c3e7259`, billing `ACTIVE`;
  - suggestions `200`, `success=true`, `5` suggestions;
  - query `200`, `success=true`, `type=ACTION_EXECUTED`, generated shopper-facing snowboard answer, `answerStartsWithJson=false`.
- Evidence directories:
  - `/tmp/shopify_companion_golive_after_commerce_generation_20260605T170536Z`;
  - `/tmp/shopify_companion_artifact_check_after_commerce_generation_20260605T171641Z`;
  - `/tmp/shopify_companion_live_after_commerce_generation_20260605T171656Z`.

## Work Queue

| Item | Status | Owner | Notes |
| --- | --- | --- | --- |
| Create this plan | completed | Codex | Added as 010.18. |
| Verify production public endpoints | completed | Codex | Safe checks only; no secrets printed. |
| Verify production administration token material | partially completed | Codex/operator | Temp local Coolify token works; private handoff token is stale. |
| Diagnose Shopify consumer assignment readiness | completed | Codex | Assignment fails because published security allowlist is not consumer-audience ready. |
| Add Bridge runtime auth diagnostics | completed locally | Codex | Secret-safe booleans added and tested. |
| Restore assignment readiness | completed | Codex | Supported go-live releases restored bootstrap and `goLiveEligible=true`. |
| Confirm Bridge runtime private-auth path | completed for smoke | Codex | Bootstrap/suggestions/query passed through Bridge-mediated private runtime path. |
| Repair APP_SCOPES_UPDATE webhook readiness | no current controlled-proof blocker | Codex/operator | Store readiness now has no go-live blockers; still review public scope/webhook posture for listing. |
| Verify Shopify MCP readiness or remove from V1 claims | completed for read-first V1 proof | Codex | Three supported MCP read actions remain in artifacts; legacy aliases removed. |
| Rerun Shopify Companion live verification | completed targeted smoke | Codex | Final health/bootstrap/suggestions/query smoke passed; full public package verifier still pending. |
| Decide billing/scope posture for controlled release | pending | Product/operator | Billing test mode and protected/order data scope posture must be explicit before public listing. |
| Prepare public listing package | pending | Product/operator | Controlled P0 evidence is clean; public package still needs billing/listing/protected-data/support proof. |
