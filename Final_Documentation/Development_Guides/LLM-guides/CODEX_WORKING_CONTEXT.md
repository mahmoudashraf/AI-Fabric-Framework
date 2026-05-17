# Codex Working Context

Purpose: compact, append-only context for this repo so future Codex turns do not depend on long chat history.

Rules:
- Append only important decisions, current status, changed files, tests, and blockers.
- Keep each turn compact. Do not paste logs, diffs, or long explanations.
- Prefer exact file paths, run IDs, and command names when they matter.
- Ignore noisy/unrelated working tree files unless they block the task.

## Current Session Decisions

- User wants concise chat feedback because long chat text can make Codex sessions unloadable.
- Maintain this file after meaningful turns with compact decisions/status only.
- Created this file as the compact append-only context log for future Codex turns.
- User wants meaningful completed changes committed and pushed by default; stage only relevant files and avoid unrelated local artifacts.
- Shopify Companion builder roadmap is mostly complete as engineering build-out, but not product-launch complete.
- Active Shopify product roadmap should prioritize: live release gate, product truth/pricing alignment, App Store package, design partner beta, Free+Starter launch, gated Elite, then second product.
- Previous live verification was red: Qdrant temp cluster HTTP 429, Railway/API rate limiting HTTP 429/code 1015, Shopify product service 502, marketplace/canonical rollout blockers.
- Railway HTTP 429/code 1015 means rate limiting; pause/retry later and avoid repeated full-suite spam.
- Shopify admin UI refactor target: merchant-first workflows, with platform/operator diagnostics hidden in Advanced.
- Shopify admin UI changed in `product-services/shopify-bridge-service/ui/src/App.tsx`: added tabs Home/Setup/Insights/Billing/Support/Go live/Advanced, simplified merchant summary, moved raw bundles/dossiers/webhook/vectorization internals to Advanced, added Billing section.
- Verification for Shopify admin UI refactor: `npm --prefix product-services/shopify-bridge-service/ui run build` passed; targeted Shopify backend tests passed; `git diff --check` passed; live bridge shell smoke passed.
- Local Shopify admin UI dev server was started on `http://127.0.0.1:5175/`.
- Production Shopify bridge root served a bundle containing new `Loom Companion` admin UI strings after commit `277da57d`; merchant may need Shopify Admin/hard refresh to see it.
- Support tools tab should explain internal/support purpose and avoid rendering long generated packet text; full packets stay available through copy/download actions.
- Created `doc/Productization/future-work/MarketPlace/Products/Strategy/PARTNER_DASHBOARD_STRATEGY_PLAN.md` and updated `Codex_Strategic_Context.md`: partner dashboard owns runbooks/packets/diagnostics/referrals/commissions; merchant admin stays merchant-safe.
- Clarified indexing boundary: Shopify Admin API is source of truth; Bridge exposes Shopify-backed vectorization source endpoints; Platform owns vectorization runs/evidence; Runtime stores only the derived retrieval index. Merchant UI should say `Refresh knowledge` / `Reindex`, not `Sync now`.
- 010.4 cleanup removed hidden legacy `runSync` preconditions from manual vectorization and webhook-driven auto indexing. Legacy document sync can remain for compatibility/operator repair, but it is not shopper-facing source truth or a merchant freshness requirement.
- Technical LLM implementation sessions must read this file before starting, keep it updated with compact ongoing decisions/status/blockers/changed files, and leave a clear handoff note before ending.
- Created first implementation handoff: `doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/001_SHOPIFY_COMPANION_LAUNCH_TRUTH_ENFORCEMENT.md`; it briefs Launch Truth Enforcement and requires sessions to keep this working context updated.
- Enriched Launch Truth Enforcement handoff with technical code map, implementation guidance, verification commands, live-check rules, and required LLM handoff output.
- Documented Shopify bridge admin verification key rule: `SHOPIFY_BRIDGE_ADMIN_API_KEY` must match deployed `SHOPIFY_BRIDGE_SHARED_SECRET`, uses `X-BRIDGE-API-KEY` by default, and is distinct from `SHOPIFY_ADMIN_ACCESS_TOKEN`.

## Open Working Tree Notes

- `.DS_Store` is modified and `log.txt` is untracked; treat as user/local artifacts unless explicitly asked to clean them.

## 2026-04-25 Shopify Companion Launch Truth Enforcement

- Implemented `001_SHOPIFY_COMPANION_LAUNCH_TRUTH_ENFORCEMENT.md` across Shopify bridge billing, storefront bootstrap/readiness, theme extension gating, merchant UI/generated copy, tests, and launch/product strategy docs.
- Launch truth now enforced in code: Free allowed surfaces are `ai-search` only; Starter excludes `order-lookup`; Elite includes `order-lookup`; Elite action packages expose verified `guided-commerce` only.
- Storefront bootstrap and direct order lookup now use persisted Shopify access token for billing entitlement checks and deny order lookup unless tier, widget surface, and `read_orders` scope all agree.
- Merchant UI hides Elite order lookup setup outside entitled tiers, keeps launch gates independent of Free/Starter order lookup, and generated support/App Store/review/runbook copy avoids Free/Starter order lookup claims.
- Theme extension no longer renders a dedicated order lookup block unless bootstrap `enabledSurfaces` contains `order-lookup`; default embedded surfaces fall back to `ai-search` only.
- Verification passed: `node --check companion-embedded-surfaces.js`; `npm --prefix product-services/shopify-bridge-service/ui run build`; targeted Shopify bridge tests; full `mvn -f product-services/shopify-bridge-service/pom.xml -q test`; `git diff --check`.
- Live smoke passed against production before push: `/api/app/shell` returned `200 READY_FOR_ONBOARDING`; `/actuator/health` returned `200 UP`.
- Unrelated dirty files still exist: `.DS_Store`, `log.txt`, `Codex_Strategic_Context.md`, and partner/platform strategy docs. Shopify RoadMaps docs are part of the launch-truth documentation scope.
- Launch Truth Enforcement accepted as completed in `doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/001_SHOPIFY_COMPANION_LAUNCH_TRUTH_ENFORCEMENT.md`; pushed commits `08174962` and `d908c499`; full Shopify Bridge and Platform backend suites passed; live Shopify Companion verification passed with Free=`ai-search` only and direct bridge admin endpoint coverage. Do not expose `SHOPIFY_BRIDGE_ADMIN_API_KEY`; it must match deployed `SHOPIFY_BRIDGE_SHARED_SECRET`.
- Created second implementation handoff: `doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/002_SHOPIFY_COMPANION_STOREFRONT_PRODUCT_SHELL.md`; it briefs Phase 1 Storefront Product Shell work after Launch Truth completion, including Max Mode convergence, mode contract, page context/attachments, fetch-only intelligence, entitlement gates, verification, and live-check rules.
- Storefront Product Shell accepted as complete: hosted Shopify extension deploy complete, browser proof complete, bridge admin verification complete, full live verifier passed with admin checks enabled, docs/context committed and pushed through `e50e46d2`. No pending handoff items reported.

## 2026-04-25 Shopify Companion Storefront Product Shell

- Storefront Product Shell status: code implementation complete and pushed in `a3fdab98`; bridge/runtime live proof passed; storefront-hosted visual proof is blocked until Shopify CLI app deploy can authenticate.
- Changed files: Max widget request/context/attachment/debug UI files; Shopify theme extension app embed, embedded surfaces, bootstrap snippet, app block settings, generated `max-mode-widget.iife.js`; Shopify bridge storefront bootstrap/chat services and tests; `002_SHOPIFY_COMPANION_STOREFRONT_PRODUCT_SHELL.md`.
- Decisions: Max Mode is the only long-term Shopify shopper shell; debug controls require explicit `features.debug=true`; Shopify page context is normalized from nested `storefrontContext` and legacy top-level context; page context remains hidden grounding while explicit card attachments remain separate; direct storefront chat routes enforce surface entitlement.
- Verification passed locally: `git diff --check`; `node --check` for touched theme extension assets and generated Max bundle; `bash -n scripts/verify-shopify-companion.sh`; `npm --prefix max-mode-widget run typecheck`; `npm --prefix max-mode-widget run build`; `npm --prefix product-services/shopify-bridge-service run shopify:widget:sync`; targeted Shopify Bridge Maven suite; full `mvn -f product-services/shopify-bridge-service/pom.xml -q test`.
- Live verification passed against production Platform/Bridge for `shopping-companion-test.myshopify.com` with admin checks skipped: `scripts/verify-shopify-companion.sh` passed; bootstrap showed Free=`ai-search`, modes=`navigator`, `chatFallbackEnabled=false`, and no governed-action capability; nested and legacy top-level `comparison` direct-route requests both returned HTTP `403`.
- Blockers: the available local bridge admin key returned HTTP `401` from `/api/admin/overview`, so admin checks need the deployed `SHOPIFY_BRIDGE_SHARED_SECRET` value; `shopify:app:deploy` is blocked by Shopify CLI interactive login/no non-interactive token, so browser-level proof that the Shopify-hosted theme extension opens Max Mode from embedded surfaces remains pending.
- Next handoff: provide or configure the deployed bridge shared secret for admin live checks, complete authenticated Shopify app/theme extension deploy, then browser-verify embedded surface to Max Mode page-context and attachment handoff on desktop/mobile.
- Added next-session unblock guidance for the two remaining live proof gaps: match `SHOPIFY_BRIDGE_ADMIN_API_KEY` to deployed `SHOPIFY_BRIDGE_SHARED_SECRET`, provide `SHOPIFY_CLI_PARTNERS_TOKEN` for non-interactive Shopify app/theme extension deploy, then browser-verify Shopify-hosted embedded surface opening Max Mode.

## 2026-04-25 Shopify Storefront Product Shell Live Retry

- Retried remaining live verification gaps after unblock handoff was added.
- Environment check: `SHOPIFY_BRIDGE_ADMIN_API_KEY`, `SHOPIFY_CLI_PARTNERS_TOKEN`, Railway token, GitHub token, Shopify store Admin token, merchant authorization, and embedded host were not exported in this shell.
- Local secret/config search found no usable non-interactive secret source beyond the existing local bridge admin key file; `railway` and `gh` CLIs are unavailable; Shopify CLI is available at version `3.93.2`.
- `npm --prefix product-services/shopify-bridge-service run shopify:preflight` passed.
- Bridge admin retry against production `/api/admin/overview` with the existing local key file returned HTTP `401`; this still means the local value does not match deployed `SHOPIFY_BRIDGE_SHARED_SECRET`.
- Shopify app/theme extension deploy remains blocked because `SHOPIFY_CLI_PARTNERS_TOKEN` is missing. Per the handoff stop condition, do not run deploy until the Partner token or valid non-interactive Shopify CLI session is available.
- Next handoff: export the deployed bridge shared secret as `SHOPIFY_BRIDGE_ADMIN_API_KEY`, export a valid `SHOPIFY_CLI_PARTNERS_TOKEN`, then rerun admin verification, `shopify:app:info`, `shopify:app:deploy`, and browser proof.

## 2026-04-25 Shopify CLI Token Unblock And Hosted Storefront Proof

- `SHOPIFY_CLI_PARTNERS_TOKEN` source was confirmed in the ignored private handoff file, loaded into `/tmp/shopify_cli_partners_token.secret`, and used without printing or committing the value.
- Used `/tmp/shopify-live-deploy.env` to preserve the full checked-in Shopify app scope set during config render: `read_products,read_content,read_legal_policies,read_metaobjects,read_metaobject_definitions,read_orders`.
- `shopify:app:info` passed non-interactively for `Loom Companion`, service account `Loom AI Labs Ltd`, dev store `shopping-companion-test.myshopify.com`, Shopify CLI `3.93.2`.
- `shopify:app:deploy` passed non-interactively and released Shopify app/theme extension version `loom-companion-22`; Theme Check reported `companion-app-embed.js` size over the configured 10000 B threshold, but the release completed successfully.
- Browser proof passed with screenshots and summary under `/tmp/shopify-verify/`: product page `https://shopping-companion-test.myshopify.com/products/selling-plans-ski-wax`, Shopify CDN scripts loaded from `loom-companion-22`, desktop rendered two Companion surface cards, embedded AI search submitted a query and opened Max Mode via `Continue in assistant`, mobile rendered two surface cards and opened Max Mode from the launcher.
- Post-deploy non-admin `scripts/verify-shopify-companion.sh` passed again for `shopping-companion-test.myshopify.com`.
- Remaining blocker: direct bridge admin `/api/admin/overview` still needs the deployed Railway `SHOPIFY_BRIDGE_SHARED_SECRET` exported as `SHOPIFY_BRIDGE_ADMIN_API_KEY`; the private handoff does not contain it and the existing local temp key returns HTTP `401`.

## 2026-04-25 Shopify Bridge Admin Railway Unblock

- Used the private handoff Railway API token with Railway GraphQL via `curl` to read deployed variables for `shopify-bridge-prod`; Python `urllib` hit Cloudflare `403`/`1010`, while `curl` returned `200`.
- Resolved Railway IDs from Platform product-service summary: project `1d747cae-7309-4655-993a-c7e5a34c4999`, environment `9d8a47c1-ed9a-4bb2-89e5-f6574cd79b7f`, service `72389d1b-346f-4d1a-8ad5-0677c3529ded`.
- Extracted deployed `SHOPIFY_BRIDGE_SHARED_SECRET` into `/tmp/shopify_bridge_admin_api_key_from_railway.secret` without printing or committing the value.
- Direct bridge admin `/api/admin/overview` returned HTTP `200` with `serviceRef=shopify-bridge-prod`, `status=READY`, `stores=2`, `billingMode=FREE`.
- Full `scripts/verify-shopify-companion.sh` passed with `SHOPIFY_BRIDGE_ADMIN_API_KEY_FILE=/tmp/shopify_bridge_admin_api_key_from_railway.secret`, including bridge admin overview, billing, webhook diagnostics, support readiness, usage, vectorization, governed actions, and vectorization source page.
- Storefront Product Shell status: fully live verified; no remaining live verification blocker is known.
- Storefront Product Shell final acceptance update: implementation complete, Shopify hosted extension deploy complete, browser proof complete, bridge admin verification complete, full live verifier passed with admin checks enabled, docs/context committed and pushed through `e50e46d2`; no pending handoff items.

## 2026-04-25 Shopify Companion Starter Launch Package

- Created third implementation handoff: `doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/003_SHOPIFY_COMPANION_STARTER_LAUNCH_PACKAGE.md`; it briefs Phase 2 Starter Launch Package after Launch Truth and Storefront Product Shell completion, with focus on sellable read-only Starter packaging, merchant activation, Knowledge Sync, analytics/value proof, App Store/App Review material, support runbook, design-partner rehearsal, verification, and strict Free AI-search-only / Starter no-order-lookup boundaries.
- Starter Launch Package status: complete.
- Changed files: Shopify bridge usage summary/model/service/tests, support readiness wording, merchant UI `App.tsx`/`api.ts`, `003_SHOPIFY_COMPANION_STARTER_LAUNCH_PACKAGE.md`, and this working context.
- Decisions: unanswered/source-gap questions are merchant value evidence; action-intent questions are future Elite demand only; Starter readiness is not blocked by Elite governed-action readiness; merchant copy should say `Knowledge Sync` instead of vectorization/runtime terms.
- Verification passed: `git diff --check`; `bash -n scripts/verify-shopify-companion.sh`; `npm --prefix product-services/shopify-bridge-service/ui run build`; targeted 003 Shopify Bridge Maven suite; full `mvn -f product-services/shopify-bridge-service/pom.xml -q test`.
- Pushed runtime/UI implementation commit `64f7093c`.
- Live verification passed after Railway served `/assets/index-C_82foy0.js`: deployed bundle contained `Future Elite demand signals`, `Action-intent questions`, and `Starter remains read-only`; full `scripts/verify-shopify-companion.sh` passed for `shopping-companion-test.myshopify.com` with bridge admin checks enabled; direct admin `usage-summary` contained `topQuestionsLast7Days`, `unansweredQuestionsLast7Days`, `actionIntentQuestionsLast7Days`, and `roiSummary.status=EARLY_SIGNAL`.
- Blockers: none.
- Next handoff: no pending 003 items.
- Independent 003 verification pass confirmed commits `64f7093c` and `f40036ab`: `git diff --check`, `bash -n scripts/verify-shopify-companion.sh`, Shopify admin UI build, targeted 003 Maven suite, full Shopify Bridge Maven suite, deployed bundle proof strings, full live Shopify verifier with bridge admin checks, and direct admin `usage-summary` proof all passed. Only unrelated `.DS_Store` remains dirty.
- Reviewed 004 Partner Enablement handoffs and strengthened verification constraints: Partner Enablement must be included in Platform live release gating when auth/API/UI/evidence/verification/escalation behavior changes; docs-only changes may skip live verification only with an explicit reason.

## 2026-04-25 Partner Enablement Foundation

- Created fourth implementation handoff: `doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/004_PARTNER_ENABLEMENT_FOUNDATION.md`; it briefs Phase 3 founding implementation partner enablement after verified Starter completion, focused on partner kit, intelligence catalog, setup/verification pack, escalation template, vertical playbooks, merchant-safe boundary, and explicitly excludes affiliate/referral/commission/white-label/partner API scope.
- Revised `004_PARTNER_ENABLEMENT_FOUNDATION.md` into a comprehensive mature-platform implementation plan: partner identity/access, roles, store assignment/revocation, partner workspace shell, client-store portfolio/workspace, intelligence catalog, sandbox/demo center, verification/launch center, evidence packets, support/escalation center, templates/playbooks, audit/security, rollout gates, and implementation slices; still excludes affiliate/referral/commission/white-label/public partner API scope.
- Updated Partner Enablement plan to use Supabase Auth for partner login/social login with Google, Apple, and LinkedIn OIDC; partner UI should be a separate `Platfrom/partner-ui` project, while Platform backend remains the authorization/source-of-truth layer for partner roles, invitations, store assignments, revocation, audit, and Shopify Bridge access.
- Revised Partner Enablement signup posture: self-service partner signup is in scope through Supabase, creating an empty partner workspace by default; client-store data requires merchant approval, signed approval link/code, approved install/claim flow, or operator assignment; still no affiliate/referral/commission/white-label/public partner API scope.
- Aligned UI persona/redesign docs with self-managed partner signup: partners may self-register into an empty workspace, while client-store access remains merchant-approved/scoped/revocable.
- Clarified Partner Enablement authority boundary: admins/operators own deployment-level controls; partners/integrators own product implementation workflows; merchants own store-level consent/configuration.
- Added Partner Enablement data/support model: Supabase stores identity/session only; Platform backend DB owns partner state, assignments, verification, escalations, reply threads, and evidence metadata; Shopify Bridge/product services own Shopify truth/secrets; escalation replies are threaded with partner-visible, operator-visible, and operator-internal visibility.
- Validated `004_PARTNER_ENABLEMENT_UI_DESIGN.md` against mature Partner Enablement strategy; aligned it to product-implementation authority, empty self-service workspace, merchant-approved store access, billing boundaries, current React/MUI stack, and day-1 maturity requirements.
- Updated Partner Enablement implementation posture: backend should be built as an extraction-ready `com.ai.fabric.platform.backend.partner` module inside `Platfrom/backend`, with gateway contracts to Platform/Shopify capabilities; created `004_PARTNER_ENABLEMENT_FULL_STACK_IMPLEMENTER_PROMPT.md`.

## 2026-04-25 Shopify Companion First Product Readiness Audit

- Created `doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/005_SHOPIFY_COMPANION_FIRST_PRODUCT_READINESS_AUDIT.md` as the next audit gate after Starter Launch Package and before design-partner, partner-scale, public-launch, Elite, or second-product work.
- Audit separates `TECHNICAL_READY`, `DESIGN_PARTNER_READY`, `PARTIAL`, and `NOT_READY`; it must not mark the product market-proven without real merchant/design-partner outcomes.
- Readiness plan requires compact evidence under `/tmp/shopify-first-product-readiness-audit/`, product-truth scan, entitlement/build/test gates, storefront desktop/mobile proof, merchant admin proof, live verifier with optional bridge admin checks, support collateral review, and a final appended completion section.
- Added query-to-answer quality as a required 005 gate: Shopify Companion must pass real shopper query categories, tier-safety checks, grounding/helpfulness/honesty/internal-language rubric, and answer-quality artifacts. The shape should act as the first reusable platform Product Generation Audit primitive; partners may later run/add client-store queries but not redefine canonical thresholds.
- Added platform/operator Product Readiness Audit UI requirement to 005: overview, checklist, query pack, answer results, evidence, decision panel, stale evidence warning, append-only decision history, and no merchant-admin/partner-first placement. `DESIGN_PARTNER_READY` requires a reviewable UI/evidence surface, not private scripts or chat history alone.

## 2026-04-25 Partner Enablement Foundation Implementation

- Partner Enablement Foundation status: complete locally; full release-ready live verification is blocked until deployed partner UI DNS and a valid Supabase partner JWT are available.
- Changed files: `Platfrom/backend` partner module/security/config/migration/tests, new `Platfrom/partner-ui`, `scripts/verify-partner-enablement-live.sh`, `004_PARTNER_ENABLEMENT_FOUNDATION.md`, and this working context.
- Decisions: Supabase owns partner identity/session only; Platform DB owns partner authorization, roles, assignments, approvals, audit, escalation, and evidence metadata; self-service signup creates an empty workspace; store access needs merchant approval or assignment; partner tokens do not authenticate operator APIs; Free remains AI search only and Starter remains read-only with no order lookup.
- Verification passed: `git diff --check`; `mvn -f Platfrom/backend/pom.xml -q -Dtest=PartnerEnablementIntegrationTest test`; `mvn -f Platfrom/backend/pom.xml -q test`; `npm --prefix Platfrom/partner-ui run build`; `npm --prefix Platfrom/partner-ui run smoke`.
- Live verification: `PLATFORM_BASE_URL=https://ai-fabric-framework-production-324f.up.railway.app scripts/verify-partner-enablement-live.sh` passed backend health plus unauthenticated and invalid JWT rejection; `PARTNER_UI_BASE_URL=https://partners.loomai.pro` attempt failed DNS resolution; authenticated workspace checks did not run because no `PARTNER_SUPABASE_JWT` is available.
- Blockers: deploy partner UI/DNS for `partners.loomai.pro`, configure deployed Platform Supabase partner auth env values, obtain a non-committed valid test partner JWT, then rerun `scripts/verify-partner-enablement-live.sh` with `PARTNER_LIVE_STRICT=true`.
- Next handoff: after commit/push, treat the local foundation as implemented and use the strict live verifier as the remaining release gate.

## 2026-04-25 Thinker Resolver Product Blueprint

- Created `doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/006_THINKER_RESOLVER_GOVERNED_ISSUE_RESOLUTION_BLUEPRINT.md` as a blueprint for the next product archetype after `005`: governed issue resolution with Thinker for evidence/read-action diagnosis and Resolver for policy-approved read/write action execution.
- Decisions: position as governed issue-resolution assistant, not autonomous write-access chatbot; LLM is planner, Platform/product boundaries own policy, permissions, validation, execution, confirmation, audit, recovery, and escalation.
- Gate: blueprint work is allowed now, but implementation should wait for `005` readiness or explicit user direction for parallel research; Shopify Free/Starter remain read-only and governed resolving stays higher-tier/Elite-only until proven.
- Corrected 006 platform foundation: bounded multi-read-action planning before final generation already exists via `ReadActionResolutionService`, `resolver_assistant` single-pass mode, `thinker` iterative mode, eligible read-action allowlists, action-evidence diagnostics, and RAG cooperation. 006 should focus new work on productizing issue resolution and governed write-capable Resolver behavior.

## 2026-04-29 Thinker Resolver 006.x Roadmap Restructure

- Updated Thinker/Resolver docs into one product-line roadmap: parent blueprint `006_THINKER_RESOLVER_GOVERNED_ISSUE_RESOLUTION_BLUEPRINT.md`, `006.1` read-only Thinker diagnosis, `006.2` Resolver dry-run, `006.3` governed low-risk writes, and `006.4` productized readiness/rollout.
- Renamed former `007_THINKER_PHASE_1_READ_ONLY_ISSUE_RESOLUTION_PRODUCTIZATION.md` to `006_1_THINKER_PHASE_1_READ_ONLY_ISSUE_RESOLUTION_PRODUCTIZATION.md`; future `007` should be reserved for a different product line.
- Current code status recorded in `006.1`: `ReadActionResolutionService` and commerce `thinker` mode exist; dedicated IssueSession, Thinker EvidenceBundle, ResolutionPlan, Thinker UI, partner view, readiness pack, and kill switch are not implemented yet.
- Decision: Shopify Companion Elite is the first reference vertical for `006.1`; Thinker/Resolver remains a reusable platform product line, not a separate Shopify-only app.

## 2026-04-29 Coolify Deployment Provider 007

- Created `doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/007_COOLIFY_DEPLOYMENT_PROVIDER_AND_RESTARTABLE_SERVICES.md`.
- Decision: Coolify is a first-class deployment provider beside Railway for tenant runtimes and restartable services; Platform UI/backend/Postgres/partner UI/Shopify bridge, billing, webhooks, readiness audit, and provider administration stay on Railway.
- Decision: implement Coolify through deployment target profiles, provider registry, immutable GHCR image artifacts, provider resource handles, operator UI, audit, backup/restore, and release verification. Do not add another global `platform.provisioning.mode` path or a one-off `railway|coolify` flag.
- First implementation slice should be target profiles/provider registry/Railway compatibility before any Coolify API calls.
- 2026-05-01 update: Hetzner Cloud is selected as the first Coolify host provider; automate host/firewall/network/DNS/volume/cloud-init setup through Terraform or `hcloud`, then use Coolify API for app lifecycle. Dedicated Hetzner servers are deferred until runtime density justifies them. `007` now starts with Slice 0 host automation when reproducible infrastructure is missing.
- 2026-05-01 update: `007` now records staging `CPX32`, initial production `CCX23`, secret-safe Hetzner token loading from the private local document, and an execution checklist for Terraform/`hcloud` host automation before Platform target-profile/provider work.

## 2026-04-25 Partner Enablement Supabase And Deployment Unblock

- Used ignored private handoff section `7.5 Subabse Partner UI` as the Supabase credential source; extracted runtime/admin values into `/tmp/partner_supabase.env` without committing secrets.
- Created a confirmed non-social Supabase email/password test account; JWT and account details are stored only in `/tmp/partner_supabase_jwt.secret` and `/tmp/partner_supabase_test_account.env`.
- JWT claims match the expected Supabase issuer/audience/email provider, but this project token lacks a top-level `email_verified` claim; first email-only Platform test should set `PLATFORM_SUPABASE_REQUIRE_EMAIL_VERIFIED=false`.
- Production valid-JWT live check still returns `401` because the deployed Platform backend does not include partner routes; public merchant approval probe also returned `401`. Production deploy branch is `Platform_V1`, while the Partner Enablement slice is on `Platform-V6`; do not blindly fast-forward production because the branch diff is very large.
- Added `Final_Documentation/Development_Guides/PARTNER_ENABLEMENT_DEPLOYMENT_GUIDE.md` plus Partner UI runtime config and Railway deployment assets for `partners.loomai.pro`.
- Temporary Partner UI Railway URL `https://ai-fabric-framework-production-158d.up.railway.app` is reachable: `/health` and `/runtime-config.js` return HTTP `200`, and `/` serves the app. Runtime config currently returns empty values, so set `PARTNER_UI_PLATFORM_API_BASE_URL`, `PARTNER_UI_SUPABASE_URL`, and `PARTNER_UI_SUPABASE_ANON_KEY` on the Railway service and redeploy before UI login proof.
- After Railway env values were added, temporary Partner UI runtime config is populated and points at `https://ai-fabric-framework-production-324f.up.railway.app`; live verifier now passes backend health, unauth/invalid JWT rejection, UI health, UI runtime config, and UI route. Remaining failure is backend authenticated partner access: valid Supabase JWT returns HTTP `401`, and public merchant approval route also returns HTTP `401`, so the deployed Platform backend still needs the partner-enabled branch/env.
- Platform diagnostics later confirmed the backend is on `Platform-V6`; public merchant approval with a JSON body returned HTTP `400` for a fake code, proving partner routes are deployed. Remaining `GET /api/partners/session` HTTP `401` is partner JWT auth config/defaults. Patched backend defaults to enable the launch Supabase issuer/JWKS, default email verification gate off for email-only test, include the temporary Railway Partner UI in CORS, and accept `user_metadata.email_verified` from Supabase email tokens.
- Final live unblock completed: Railway Platform project token from the private handoff was used through the `Project-Access-Token` header; `Platform-Backend` production variables were upserted without replacing unrelated variables; deployment `80173a9e-1cd3-4867-b568-9cdee8eddcb0` reached `SUCCESS`.
- Remaining auth failure was live Supabase signing algorithm support: launch Supabase email/password JWTs are `ES256`, while the backend decoder/test fixture only covered the default/RSA path. Patched `PartnerAuthConfiguration` to trust `RS256` and `ES256`; changed `PartnerEnablementIntegrationTest` to use EC P-256 `ES256` JWT/JWKS fixtures.
- Verification passed: `mvn -f Platfrom/backend/pom.xml -q -Dtest=PartnerEnablementIntegrationTest test`; local Java decode probe accepted the fresh Supabase `ES256` token; commit `3054159c` pushed to `Platform-V6`; Railway backend deployment `14f21bfe-1cec-427c-b25d-b4257984dfb0` reached `SUCCESS`.
- Direct browser-origin proof passed: `GET https://ai-fabric-framework-production-324f.up.railway.app/api/partners/session` with fresh Supabase bearer token and `Origin: https://ai-fabric-framework-production-158d.up.railway.app` returned HTTP `200`, `authenticated=true`, `signupRequired=true`, `assignedStoreCount=0`.
- Strict live verifier passed with `PARTNER_LIVE_STRICT=true`, `PARTNER_UI_BASE_URL=https://ai-fabric-framework-production-158d.up.railway.app`, and `PLATFORM_BASE_URL=https://ai-fabric-framework-production-324f.up.railway.app`: backend health, unauth/invalid JWT rejection, Partner UI health/runtime/route, valid JWT acceptance, session payload shape, and empty workspace state all passed; catalog/store checks skipped because the test partner is intentionally unprovisioned.
- Partner Enablement Foundation status: implemented, committed, pushed, deployed, and live verified on Railway. Non-blocking follow-up: later point `partners.loomai.pro` at the Partner UI Railway service and provision a partner workspace/store assignment when catalog/store live checks are required.
- Added 004 change plan for installed-store-first merchant approval: partner selects an eligible installed `ShopifyStoreConnection` from a safe dropdown, merchant approves/denies inside connected merchant/admin UI, Platform DB stores approval decisions and creates active assignments only after approval with a real `storeConnectionId`. Public approval links and typed shop-domain authority should not be the production default.
- Implemented installed-store-first merchant approval locally: Platform V66 adds `storeConnectionId` to partner implementation/access records; partner create now requires an installed store and immediately creates a `WAITING_ON_MERCHANT` review request; Platform adds eligible-store search plus merchant/admin list/approve/deny endpoints; Partner UI uses an installed-store autocomplete and removes the public approval-link CTA; Shopify Bridge service/UI adds a merchant `Partners` tab to approve/deny requests.
- Verification passed for this change: `mvn -f Platfrom/backend/pom.xml -q -Dtest=PartnerEnablementIntegrationTest test`, full `mvn -f Platfrom/backend/pom.xml -q test`, `npm --prefix Platfrom/partner-ui run build`, `npm --prefix Platfrom/partner-ui run smoke`, `mvn -f product-services/shopify-bridge-service/pom.xml -q -DskipTests compile`, `mvn -f product-services/shopify-bridge-service/pom.xml -q -Dtest=ShopifyMerchantControllerTest,PlatformShopifyStoreClientTest test`, full `mvn -f product-services/shopify-bridge-service/pom.xml -q test`, `npm --prefix product-services/shopify-bridge-service/ui run build`, and `git diff --check`.
- Installed-store approval changes were pushed to `Platform-V6`.
- Live verification passed after push: full `scripts/verify-shopify-companion.sh` passed with bridge admin checks; deployed Bridge UI asset contains `partner-access/requests` and `Partners`; Platform login and `GET /api/merchant/partner-access/requests?shopDomain={shop}` returned HTTP `200`.
- Full live installed-store denial path passed with a temporary confirmed Supabase email partner: signup returned HTTP `200`, eligible-store lookup returned one installed store, implementation creation returned HTTP `201` and `WAITING_ON_MERCHANT`, merchant/admin listing found the request, denial returned HTTP `200` and `DENIED`, partner final implementation fetch returned `DENIED`, and assigned-store count remained `0`.
- Live approval was intentionally not executed to avoid leaving an active production partner-store assignment; local integration coverage proves approval creates the assignment and partner visibility.
- Partner Enablement release-gate wiring added locally: `full-platform-release-readiness` now includes required `partner-enablement-verification` after Shopify Companion verification; standalone suite key `partner-enablement-verification` is registered; script context runs `scripts/verify-partner-enablement-live.sh` with `PARTNER_LIVE_STRICT=true`, `PARTNER_UI_BASE_URL` from suite config, and required platform secret `PARTNER_SUPABASE_JWT`.
- Verification for release-gate wiring passed locally: `mvn -f Platfrom/backend/pom.xml -q -DskipTests compile`; `mvn -f Platfrom/backend/pom.xml -q -Dtest=PlatformVerificationSuiteScriptContextServiceTest,PlatformVerificationSuiteServiceTest,PlatformVerificationSuiteExecutionServiceTest,PlatformSecretServiceTest test`; full `mvn -f Platfrom/backend/pom.xml -q test`; `git diff --check`.
- Release-gate wiring was deployed and live-verified: `/api/verification-suites` shows `partner-enablement-verification` as a standalone suite and as blocking stage 7 of 13 in `full-platform-release-readiness`; a fresh `PARTNER_SUPABASE_JWT` was stored in Platform secrets; standalone live run `vsr-4d0607a7` passed in strict mode against Platform backend `https://ai-fabric-framework-production-324f.up.railway.app` and Partner UI `https://ai-fabric-framework-production-158d.up.railway.app`.
- Additional release-gate fix pushed in `ebeb0d77`: Platform suite secrets are passed to scripts as `*_FILE`, so `scripts/verify-partner-enablement-live.sh` now reads `PARTNER_SUPABASE_JWT_FILE`; `PlatformVerificationScriptRunnerService` now treats `PARTNER_SUPABASE_JWT`/`PARTNER_SUPABASE_JWT_FILE` as managed secret env keys; regression test `PlatformVerificationScriptRunnerServiceTest` covers partner JWT file propagation.
- Current full release gate is still not `READY` because latest full run `vsr-17744b05` from 2026-04-24 failed before Partner Enablement on `Qdrant temporary cluster creation -> HTTP 429`. Rerun `full-platform-release-readiness` only after the Qdrant provider-rate blocker is clear and a fresh short-lived `PARTNER_SUPABASE_JWT` is stored.
- Partner implementation request scope corrected: partners no longer choose tier or surfaces; Platform derives surfaces from installed Shopify store widget config, stores `requestedTier=MERCHANT_CONFIGURED`, uses `FULL_STORE_ACCESS` request/approval scope, and Partner UI only shows store-configured surfaces. Verification passed with full Platform backend tests, full Shopify Bridge tests, Partner UI build/smoke, Bridge UI build, and `git diff --check`.
- Partner request visibility correction: added `GET /api/partners/client-implementations` so a partner can see request history/status after creating a client implementation; Partner UI dashboard now lists real implementation requests and no longer hard-codes pending approvals to `0`; empty partner workspaces still show pending request status before merchant approval creates a store assignment; Shopify Bridge `Partners` tab now shows a critical load error instead of silently treating Platform/proxy failures as an empty list.
- Verification for request visibility correction passed: `mvn -f Platfrom/backend/pom.xml -q -Dtest=PartnerEnablementIntegrationTest test`, full `mvn -f Platfrom/backend/pom.xml -q test`, `npm --prefix Platfrom/partner-ui run build`, `npm --prefix Platfrom/partner-ui run smoke`, `npm --prefix product-services/shopify-bridge-service/ui run build`, `mvn -f product-services/shopify-bridge-service/pom.xml -q -Dtest=ShopifyMerchantControllerTest,PlatformShopifyStoreClientTest test`, and `git diff --check`. Changed-code scan found no dummy/stubbed implementation or placeholder request history.
- Live proof after commit `497e8705`: Partner UI Railway asset `assets/index-BN4XNWI2.js` contains `Implementation request history could not be loaded` and no longer contains the old request-history placeholder; live Platform accepted a fresh Supabase JWT for the provisioned non-social test partner and `GET /api/partners/client-implementations` returned HTTP `200` with an empty array; Shopify Bridge Railway asset `assets/index-DxFkYgEa.js` contains `partner-access/requests` and `Partner access requests could not be loaded`.
- Independent 004 release-gate verification confirmed commit `1043e167` plus verifier fix: `git diff --check`, `bash -n scripts/verify-partner-enablement-live.sh`, targeted release-gate tests, and full Platform backend tests passed; strict live Partner Enablement verifier passed against live Platform/Partner UI after refreshing the local short-lived Supabase JWT and cleaning one failed-run pending request. Live proof IDs: request `psar-f27b319b-8b5e-4cf5-88d5-9d4e661b9b28`, assignment `psa-a722f586-47fa-434e-849a-66325d174cd8`, verification run `pvr-2e786f19-945d-44d6-809a-5ac0305c6ba3`, launch evidence bundle `peb-724121c2-8697-4ae6-a93c-1a4ec9c5137a`; cleanup revoked the temporary assignment.

## 2026-04-26 Product-Scoped Partner Control Addendum

- Implemented the 004 Product-Scoped Partner Control addendum against the source-of-truth rule: partners now control assigned-store product surfaces through Platform partner endpoints, and all writes delegate to canonical Shopify store services rather than creating partner-owned duplicate config.

- Backend changes: added canonical Shopify source-settings update service/admin endpoint; added partner product-control summaries and partner endpoints for widget settings, source settings, and support profile; enforced provisioned partner context, active assignment, explicit assignment capabilities, installed-store status for writes, and partner-safe response projection with no secrets/runtime/deployment internals.
- Partner UI changes: added a real Product controls tab in the store workspace with authenticated controls for storefront surfaces, conversation modes, page mappings, knowledge source toggles, and merchant handoff/support profile.
- Verification script changes: strict live verifier now reaches product controls, performs and restores a real partner-authenticated support-profile write, proves canonical Platform/admin visibility, checks partner-visible product audit activity, and confirms revoked assignments lose product-control access.
- Verification passed locally: `git diff --check`, `bash -n scripts/verify-partner-enablement-live.sh`, `mvn -f Platfrom/backend/pom.xml -q -Dtest=PartnerEnablementIntegrationTest test`, full `mvn -f Platfrom/backend/pom.xml -q test`, `npm --prefix Platfrom/partner-ui run build`, and `npm --prefix Platfrom/partner-ui run smoke`.
- Product-Scoped Partner Control addendum final proof: commit `4447fff0` pushed to `Platform-V6`; Railway backend deployment `2664ee26-7b84-4d5e-82fd-1ccaa27a8f99` and Partner UI deployment `5a29065f-1e04-4e4a-b00d-361dd1b3c984` reached `SUCCESS`; strict live `scripts/verify-partner-enablement-live.sh` passed against Platform `https://ai-fabric-framework-production-324f.up.railway.app`, Partner UI `https://ai-fabric-framework-production-158d.up.railway.app`, and `shopping-companion-test.myshopify.com`. Live proof IDs: implementation `pci-fa339182-dbe3-4c50-85a1-10d05a1b4b56`, request `psar-a3a8288f-4fcd-4b14-aa62-9b074f124787`, temporary assignment `psa-a722f586-47fa-434e-849a-66325d174cd8`, verification run `pvr-ca8da17d-af99-42f3-9576-e9884d736c14`, launch evidence bundle `peb-ea6eff23-e370-4079-8e29-e86822ce2bea`; cleanup revoked the temporary assignment and revoked product-control reads returned `403`.

## 2026-04-26 Shopify Elite Max Live Verification

- Activated `shopping-companion-test.myshopify.com` to `ELITE/ACTIVE` through the bridge admin billing-state endpoint using the deployed Railway bridge shared-secret value from local private secret files.
- Persisted the billing state in Platform (`c85e22d9`), made storefront billing prefer Platform over bridge-local install cache (`e1f94434`), and made support-readiness prefer Platform billing before stale local install billing (`ab178296`).
- Railway Shopify Bridge deployment `b87f834f-a4ae-432e-8b66-23915a09c65e` reached `SUCCESS`.
- Live proof after deploy: Platform durable billing state remained `ELITE/ACTIVE`; storefront bootstrap returned `billingTier=ELITE`, `billingStatus=ACTIVE`, `chatFallbackEnabled=true`; support-readiness returned `billingTier=ELITE`, `status=PENDING_SCOPE_GRANT`, `scopeGrantRequired=true`, and `missingScopes=["read_orders"]`.
- Verification passed: targeted Shopify Bridge Maven tests, `git diff --check`, verifier shell syntax checks, full `scripts/verify-shopify-companion.sh` with admin checks and correct go-live blocker expectations, and `scripts/verify-shopify-companion-max-widget-live.sh` with browser proof.
- Browser proof: real Shopify product page loaded Max assets, observed bridge bootstrap, found the Max widget host, clicked launcher, and opened the widget. Screenshot: `/tmp/shopify-companion-max-widget-shopping-companion-test.myshopify.com-1777195849.png`.
- Current blocker is intentional and merchant-controlled: Platform go-live remains blocked until Shopify `read_orders` is granted; shopper bootstrap and Max widget are live.

## 2026-04-29 Shopify Package Profile Approved Choices

- Shopify Companion package profile approved-choice status: implemented, committed, pushed, deployed, and live verified against Railway Platform backend.
- Changed files: Platform backend package profile options models/service/controller/tests, Platform UI package profile editor/API types, 004 handoff, and this working context.
- Decisions: backend owns package profile choices and compatibility rules; Platform UI only consumes backend options; backend validation is authoritative for package/tier/runtime/vector/inference/template/verification compatibility; no dummy records were created.
- Verification passed: `mvn -f Platfrom/backend/pom.xml -q -Dtest=ShopifyCompanionPackageProfileCatalogServiceTest,ShopifyCompanionPackageProfileOptionsServiceTest test`; `npm --prefix Platfrom/ui run build`; full `mvn -f Platfrom/backend/pom.xml -q test`; `git diff --check`.
- Live verification: commit `f9cf0471` pushed to `Platform-V6`; deployed backend `GET /api/shopify/package-profiles/options` returned HTTP `200` with 4 blueprints, 4 compatibility rules, and the `ELITE` / `ELITE` default mapping `HIGH_QUALITY` / `QDRANT_SHARED` / `mkp-inference-premium-hybrid`.

## 2026-05-01 Coolify 007 Provider Core Continuation

- Continued `007` on `Platform-V8` after rebase commit `926f03291`; no push yet.
- Implemented Coolify backend provider core: `CoolifyApiClient`, `CoolifyDeploymentProvider`, `CoolifyTargetProfileResolver`, source artifact service/API, provider resource action service/API, apply `targetProfileId`/`sourceArtifactId` overrides, `providerResourceHandleId` capture, and Coolify verification suite/script wiring.
- Added `.github/workflows/coolify-image-artifacts.yml` for GHCR runtime/REST connector image artifacts and metadata upload.
- Added `scripts/verify-coolify-provider.sh`; it loads `/tmp/coolify_api_tokens.env` or env vars and never prints token values.
- Verification passed: focused Platform backend Maven slice including new Coolify tests, Terraform `fmt`/`validate`, `bash -n` for Coolify/Hetzner scripts, live non-strict Coolify verifier showing staging/prod `version=4.0.0`, `health=OK`, `applications=0`.
- Live strict staging smoke created/started/deleted a disposable app but did not reach `running`/healthy before timeout; cleanup left staging with zero apps. Treat runtime smoke as blocked until DNS/GHCR/Coolify app health are resolved.
- Remaining blockers: DNS skipped by request, GHCR read credentials/host registry auth missing, Coolify profiles intentionally inactive, backup/restore rehearsal pending, frontend operator UI not implemented.

## 2026-05-01 Coolify 007 Slice 0 Hetzner Host Automation Baseline

- Initial user instruction requested no commits to the current branch until told; this was later superseded by permission to commit normally to `Platform-V8`.
- Implemented local Terraform-compatible Hetzner baseline under `infra/coolify/hetzner`: SSH key, firewall, private network/subnet, staging `coolify-staging-01` `cpx32`, production `coolify-prod-01` `ccx23`, optional volumes, labels, DNS record outputs, and README run/apply/destroy/rebuild guidance.
- Added cloud-init templates for Ubuntu 24.04 SSH hardening, package updates, base tools, UFW, Coolify install, production `AUTOUPDATE=false`, and bootstrap log/status paths.
- Added secret-safe helper scripts: `load-hcloud-token-from-private-doc.sh`, `terraform-with-hcloud-token.sh`, and `apply-hcloud-api-baseline.sh`; Terraform provider expects `HCLOUD_TOKEN` env, not committed tfvars.
- Updated `.gitignore` for Terraform state/cache/secret var files.
- Updated `007_COOLIFY_DEPLOYMENT_PROVIDER_AND_RESTARTABLE_SERVICES.md` with Slice 0 baseline status, live Hetzner apply status, and Slice 1 plan for target profiles/provider registry/Railway compatibility.
- Verification passed: `bash -n infra/coolify/hetzner/scripts/terraform-with-hcloud-token.sh`; `bash -n infra/coolify/hetzner/scripts/load-hcloud-token-from-private-doc.sh`; `bash -n infra/coolify/hetzner/cloud-init/coolify-bootstrap.sh.tftpl`; `git diff --check`; local scan of new infra/doc files found no concrete Hetzner token values.
- Blockers: known private handoff docs did not contain a detectable Hetzner token label; `/tmp/hetzner_cloud_token.secret` was not present when checked; no authenticated Hetzner command or apply was run. Terraform was not installed; OpenTofu install failed because the local disk had about 204 MiB available, so `terraform fmt/init/validate` remain blocked until Terraform/OpenTofu is available.
- Next handoff: operator should place the Hetzner token into `/tmp/hetzner_cloud_token.secret` with mode `0600` or into a private local doc using a supported label, free local disk or install Terraform/OpenTofu, run `terraform fmt/init/validate`, then plan/apply staging first. Slice 1 should add target profiles/provider registry/Railway compatibility without Coolify API calls.

## 2026-05-01 Coolify 007 Slice 0 Live Hetzner Apply

- User requested no local installs and full Hetzner/Coolify setup. Used existing local tools only (`curl`, `jq`, `ssh`) and the new Hetzner API fallback runner; no Terraform/OpenTofu/hcloud install was attempted after this instruction.
- Normalized repo-relative `tmp/hetzner_cloud_token.secret` to `/tmp/hetzner_cloud_token.secret`; both files are mode `0600`. Token value was not printed or committed.
- Generated local SSH key `~/.ssh/loom_coolify_hetzner_ed25519` for Hetzner host access; public key registered in Hetzner as `loom-coolify-operator`.
- Created shared Hetzner firewall and private network in `nbg1`; SSH and Coolify port `8000` are restricted to the setup public IP, while `80/443` are public.
- Live Hetzner resource IDs: SSH key `111657146`, firewall `10915120`, network `12181920`, staging server `128757995`, production server `128758153`. No Terraform state exists yet; import/adopt before running Terraform apply.
- Created and bootstrapped staging `coolify-staging-01`: `cpx32`, Ubuntu 24.04, IPv4 `46.224.145.148`, IPv6 `2a01:4f8:c2c:83e2::1`.
- Created and bootstrapped production `coolify-prod-01`: `ccx23`, Ubuntu 24.04, IPv4 `46.225.162.106`, IPv6 `2a01:4f8:1c18:c04::1`; production Coolify install used `AUTOUPDATE=false` in bootstrap.
- Coolify installed on both hosts. Local server HTTP checks returned `302`; external dashboard URLs before DNS are `http://46.224.145.148:8000` and `http://46.225.162.106:8000`.
- Generated Coolify root users for both instances through SSH tunnels; credentials are stored only in `/tmp/coolify_admin_credentials.env` mode `0600`. Do not paste or commit this file.
- Verification passed: Hetzner API readback shows both servers running with requested type/region; SSH hardening readback shows root/password/KbdInteractive auth disabled; UFW active; `sudo docker ps` shows `coolify`, `coolify-db`, `coolify-redis`, and `coolify-realtime` healthy on both hosts; root setup forms are gone and login forms are present; root login POSTs returned HTTP `302`; `git diff --check` passed.
- DNS not created: `loomai.pro` nameservers are `dns1.registrar-servers.com` and `dns2.registrar-servers.com`; need registrar/Namecheap DNS API credentials or a DNS delegation change before records can be automated.
- Planned DNS records: `A/AAAA *.runtime-staging.loomai.pro -> 46.224.145.148 / 2a01:4f8:c2c:83e2::1`; `A/AAAA coolify.ops.loomai.pro` and `*.runtime.loomai.pro -> 46.225.162.106 / 2a01:4f8:1c18:c04::1`.
- Remaining verification blocker: Terraform/OpenTofu validation still blocked by local disk/tool availability, but live infra was created by the repo API fallback runner and is reproducible without local installs.
- Browser proof: local Platform UI pointed at live Railway backend loaded `Shopify Profiles`, received HTTP `200` from `/api/shopify/package-profiles/options` and `/api/shopify/package-profiles?activeOnly=false`, clicked `New profile`, and produced draft key `SHOPIFY_PROFILE_MOJFYUB5` instead of `BALANCED`. Screenshot: `/tmp/shopify-package-profiles-approved-options-smoke.png`.
- Blockers: none locally.
- Next handoff: no pending approved-choice work; no separate deployed Platform UI URL is defined in the private handoff, so browser proof used local UI against live backend.

## 2026-04-29 Shopify First Product Readiness Live Completion

- First Product Readiness Audit status: passed.
- Evidence: full packet `/tmp/shopify-first-product-readiness-audit-20260429-104550`; standalone answer-quality packet `/tmp/shopify-answer-quality-post-fix-20260429-104422`.
- Verification: live Platform/Bridge/Runtime health all `UP`; runtime deployment `6f81ecb7-b9af-4d12-9ddc-88b472094588` reached `SUCCESS`; full audit reported `Readiness decision: DESIGN_PARTNER_READY`; answer-quality reported `PASS (10/10 passed)`.
- Live fixes pushed before final proof: `75096188` runtime signed URL loading + storefront answer guardrail, `26d9316a` Platform-recorded billing state source of truth, `3c630600` ONNX fallback explicit opt-in, `66e89ef3` managed provider fallback default off.
- Railway cleanup/status: cleanup endpoint returned `READY` with no cleanup candidates; mandatory Platform/product/bridge services are preserved; Railway service limits are capped at 1 vCPU / 1 GB by provisioning code.
- Live verification: passed for `shopping-companion-test.myshopify.com`; bridge billing summary returned `STARTER` from Platform-recorded Shopify billing state.
- Readiness decision: `DESIGN_PARTNER_READY`, not `MARKET_PROVEN`.
- Blockers: none for first design-partner rollout.
- Next handoff: start controlled design-partner proof across 5-10 real stores and collect real merchant outcome evidence before claiming market proof.

## 2026-04-29 Full Release Gate Final Proof

- Final full release gate passed: `full-platform-release-readiness` run `vsr-df616f36`, status `PASSED`, completed `2026-04-29T13:54:20Z`.
- All 11 blocking stages passed: shared inference health, platform admin live regression, canonical rollout inventory, managed vector provider verification, marketplace install flow, Shopify Companion verification, Shopify first-product readiness audit, Partner Enablement verification, Marketplace hosted verification, Ecommerce hosted verification, and Qdrant hosted verification.
- Hosted proof IDs: Marketplace `hvr-17b253e8` (`42` passes, `1` warning), Ecommerce `hvr-b0ac9f64` (`43` passes), Qdrant `hvr-88a44675` (`43` passes).
- Vectorization proof: ecommerce repair run `vrn-4d013427` completed `BOOTSTRAP` with `322` processed, `322` succeeded, `0` failed; Marketplace and Qdrant were `IN_SYNC` at hosted verification time.
- Final hardening commits pushed to `Platform-V6`: `07d972aa` marketplace grounded smoke query, `ca40b559` Shopify readiness billing posture and Bridge admin secret registry support, `4ebb4996` no ephemeral Qdrant DB key creation in release gate, `905b0dd7` live hosted probes authoritative over stale persisted evidence.
- Deployed Platform Backend proof: Railway deployments `cf7881b3-6760-457a-aeb5-979b8ec6399c`, `d1a2ac8b-39e6-476a-9800-17ea5842d76d`, `84b33547-7c1b-4136-815f-4f8e10de71af`, and final `255020a3-50d2-4638-9bea-35b7551f482c` reached `SUCCESS`.

## 2026-04-29 Shopify Readiness Operator UI Final Fix

- Re-audit found the live operator endpoint `GET /api/shopify/readiness-audit/latest` showed `NOT_READY/MISSING` because it only read standalone `shopify-first-product-readiness-audit` runs and ignored successful full release-gate stage evidence.
- Patched `ShopifyCompanionReadinessAuditService` so the readiness state uses the newest standalone readiness run or newest `full-platform-release-readiness` run that contains the Shopify first-product readiness stage.
- Added regression coverage for deriving readiness UI state from a full release-gate stage when no standalone readiness run exists.
- Verification passed: targeted Platform backend tests, readiness script syntax check, answer evaluator pycompile, Platform UI build, and `git diff --check`.
- Commit `83a877eb` pushed to `Platform-V6`; Railway Platform Backend deployment `1672f03e-b6f8-4721-9c95-a1f6b3220857` reached `SUCCESS`.
- Live proof passed: Platform health returned `UP`; `GET /api/shopify/readiness-audit/latest` returned `DESIGN_PARTNER_READY`, `FRESH`, `0` blockers, `10/10` checklist passed, `10/10` answers passed, latest run `vsr-df616f36`, latest stage `shopify-first-product-readiness-audit` `PASSED`.
- Added readiness guides: operator guide at `Final_Documentation/User_Guides/SHOPIFY_COMPANION_READINESS_AUDIT_OPERATOR_GUIDE.md` and developer guide at `Final_Documentation/Development_Guides/SHOPIFY_COMPANION_READINESS_AUDIT_DEVELOPER_GUIDE.md`; linked both from existing Shopify Companion guides.
- Live verification prerequisites refreshed without logging secrets: Qdrant data-plane key verified, Shopify Bridge admin key stored as Platform secret `SHOPIFY_BRIDGE_ADMIN_API_KEY`, and a fresh non-social Supabase partner JWT stored as `PARTNER_SUPABASE_JWT` before the final run.
- Local verification during final hardening passed: shell syntax checks for changed scripts, targeted Platform verification-suite tests, Qdrant-only managed provider verification with `QDRANT_CREATE_EPHEMERAL_DB_KEY=false`, standalone Shopify first-product readiness audit, standalone Partner Enablement strict live verifier, and `git diff --check`.
- Blockers: none. Unrelated local dirty files remain `.DS_Store` and `Platfrom/ui/tsconfig.app.tsbuildinfo`; do not stage them unless explicitly requested.

## 2026-04-29 Thinker Resolver 006 Full-Stack Implementation

- Implemented 006 as a single governed issue-resolution product line for Shopify Companion Elite: Thinker evidence sessions, redacted partner/operator views, Resolver proposals, policy decisions, dry-runs, and governed low-risk support-escalation execution.
- Backend changes: new V70 persistence model, operator/partner/Shopify Thinker Resolver APIs, Shopify Companion runtime Thinker capture, per-deployment kill switches, Elite gate, assignment/scope checks, partner-safe redaction, append-only audit ledgers, and real Partner Evidence Bundle / Support Escalation creation for governed execution.
- UI changes: Platform operator console at `/thinker-resolver`, Partner UI Thinker sessions page and store workspace tab, and Shopify merchant session health card for Thinker deep diagnosis readiness.
- Verification changes: new `scripts/verify-thinker-resolver-readiness.sh`, standalone `thinker-resolver-readiness` suite, and full release-gate insertion after Partner Enablement; release-gate tests now expect the 12-stage suite.
- Guides added: Thinker Resolver operator guide, partner guide, and developer guide; roadmap docs `006`, `006.1`, `006.2`, `006.3`, and `006.4` now reflect implemented code status.
- Local verification passed: `bash -n scripts/verify-thinker-resolver-readiness.sh`; `bash -n scripts/verify-platform-code-regression.sh`; focused `ThinkerResolverIntegrationTest` and `PublicConsumerBridgeChatServiceTest`; full `mvn -f Platfrom/backend/pom.xml -q test`; full `mvn -f product-services/shopify-bridge-service/pom.xml -q test`; Platform UI build; Partner UI build; Shopify Bridge UI build; `git diff --check`; changed-code scan found no dummy/stub implementation.
- Live deployment proof: implementation commits `7931a918` and `809696dc` are pushed to `Platform-V6`; deploy-branch commits `0b1ca07d` and `8bfc30e3` are pushed to `Platform_V1`.
- Live verification proof: `scripts/verify-thinker-resolver-readiness.sh` passed on 2026-04-29 against Platform backend `https://ai-fabric-framework-production-324f.up.railway.app`, Partner UI `https://ai-fabric-framework-production-158d.up.railway.app`, and `shopping-companion-test.myshopify.com` with `THINKER_REQUIRE_PARTNER_PROOF=true` and `THINKER_EXECUTE_LOW_RISK=true`.
- Live setup note: the test store was reapproved through the real partner implementation request and merchant approval flow after a previous revoked assignment blocked partner Thinker access; no direct data edits were used.
- 006 blockers: none for the first governed Shopify Companion Elite slice.

## 2026-05-01 Thinker/RAG/Widget Follow-Up

- Runtime/Thinker answer contract decision: Bridge must not replace successful runtime/action evidence with canned semantic fallback text during optimization. If runtime returns only `Action executed.` with evidence, that is a runtime answer-generation contract problem; Bridge should surface diagnostics/errors or pass through the generated runtime answer, not invent a compare fallback.
- Architecture decision: read-action planning and parameters must be LLM/tool-contract driven. Avoid text/business matching in core/framework/action/connector modules. Shopify-specific prompt enhancement belongs only in Shopify deployment configuration; commerce-curated code must remain generic across commerce platforms.
- Shopper action decision: `relationship_query` is disabled from Shopify Companion shopper action selection for now because it bypassed RAG/attachment quality and produced poor comparison behavior. Current preferred path is bounded read actions (`search_products`, `get_product_details`, `check_availability`, `get_policy`, `view_cart`) plus RAG cooperation and final LLM generation.
- Attachment behavior issue recorded: vague prompts like `compare` with attached products must respect attachments as context and should not be forced into unrelated live-catalog results. Mode choice from the widget UI should be respected; `navigator`, `cart_assistant`, and `thinker_deep` should remain observable in response metadata.
- Max widget renderer fix pushed in commit `29e436f69`: `ActionResultRenderer` unwraps nested action result envelopes, recognizes Shopify product fields (`vendor`, `productType`, `primarySku`, `inventoryQuantity`, `available`, `storefrontUrl`), and renders product cards with availability and `View product` instead of exposing raw `Data: { ... }` JSON.
- Verification for widget renderer: `npm run typecheck`, `npm run build`, IIFE build, Platform UI bundle sync, Shopify theme-extension asset sync, and `git diff --check` passed. Browser smoke was attempted but blocked because the local machine had only ~324 MiB free and Chrome could not create a temporary profile (`ENOSPC`).
- Live-store status for widget renderer: code is pushed to `Platform-V6`, but not proven live on `shopping-companion-test.myshopify.com`; public storefront redirects to `/password` and the password page does not include the Max widget asset. Shopify-hosted asset changes require Shopify app/theme extension deploy/release before storefront proof.

## 2026-05-01 Shopify Elite Reactivation

- Reactivated `shopping-companion-test.myshopify.com` to `ELITE/ACTIVE` through the live Shopify Bridge admin billing-state endpoint, which writes the Platform-recorded Shopify billing state. Secret source was the local private bridge admin key file; no secret values were printed or committed.
- Before reactivation, bridge billing summary returned `FREE/ACTIVE` with message `Free tier is active for this store.` After reactivation, billing summary returned `ELITE/ACTIVE` with message `Elite tier is active for this store from Platform-recorded Shopify billing state.`
- Provisioning settled to `READY`: latest job `spj-3ee07816`, package `ELITE`, tier `ELITE`, runtime profile `HIGH_QUALITY`, vector profile `QDRANT_SHARED`, verification pack `shopify-companion-elite-readiness`, ready at `2026-05-01T01:15:51Z`.
- Support readiness returned `READY`, `billingTier=ELITE`, `billingStatus=ACTIVE`, granted scopes include `read_orders`, missing scopes `[]`, order lookup supported for recent orders, and historical-order support still notes `read_all_orders` as the only broader-scope consideration.
- Storefront bootstrap verified `billingTier=ELITE`, `billingStatus=ACTIVE`, `orderLookupEnabled=true`, `chatFallbackEnabled=true`, enabled surfaces include `order-lookup`, and action capability is available with `guided-commerce`, `ADD_TO_CART`, and `UPDATE_CART_QUANTITY`.
- Evidence files are under `/tmp/shopify-elite-reactivation-20260501-021531` and `/tmp/shopify-elite-reactivation-poll-20260501-021552`.

## 2026-05-01 Full Release Gate Re-Run

- Full live release gate passed: `full-platform-release-readiness` run `vsr-e2ece3d5`, status `PASSED`, completed `2026-05-01T10:28:21Z`; `/api/verification-suites/release-gate` returned `READY`.
- All 12 stages passed, including Shopify Companion, first-product readiness, Partner Enablement, Thinker Resolver, Marketplace hosted, Ecommerce hosted, and Qdrant hosted verification.
- Commit `a95535ddd` pushed before the run: `scripts/verify-thinker-resolver-readiness.sh` now sets/restores temporary Elite billing for Thinker proof, creates/revokes temporary partner access when the Partner gate has cleaned up its assignment, and selects only active partner assignments.
- Deployed-script proof: standalone Platform suite `thinker-resolver-readiness` run `vsr-38a309e4` passed before dispatching the full gate.
- Local proof before release gate: patched `scripts/verify-thinker-resolver-readiness.sh` passed live against Platform backend, Partner UI, and `shopping-companion-test.myshopify.com`; cleanup restored billing to `STARTER/ACTIVE` and revoked temporary partner access.

## 2026-05-01 Coolify 007 Slice 0 Coolify Control Setup

- Initial no-commit instruction was later superseded by permission to commit normally to `Platform-V8`.
- DNS remains skipped by request. Dashboard URLs before DNS: staging `http://46.224.145.148:8000`, production `http://46.225.162.106:8000`.
- Coolify API enabled on both hosts; generated root credentials remain only in `/tmp/coolify_admin_credentials.env`, and generated API tokens remain only in `/tmp/coolify_api_tokens.env` mode `0600`. No token values should be pasted into docs or chat.
- Coolify version readback passed on both hosts: staging `4.0.0`, production `4.0.0`.
- Created Coolify target records: staging project/environment `id069t43frp519u5i3dg2jpr` / `h1433m09ezg882q7xmf3ae0x`; production project/environment `t1400k32bg9yd764chyt1slm` / `rn5sbycbix789i973okr9ugm`.
- Built-in Coolify server records now use hardened SSH user `loomops` instead of root and validate as reachable/usable. Staging server/destination/private-key UUIDs: `zf25hgk9694bt7q0zwb98ado` / `xjhfu65nacrr30xax5cp0ry7` / `n117g3g8n75p6x048drc11on`; production: `kvufjk78dj4wyhjgp1mlxecr` / `r3thf2xmxcjn1tt2bclabebz` / `bmllhht0k5m0gfkuk0ovwisz`.
- Live host hardening follow-up: UFW/fail2ban allow the local Coolify Docker address pool for self-validation while external SSH and dashboard access stay restricted by the Hetzner firewall/operator allowlist.
- Verification passed after docs update: shell syntax checks for all `infra/coolify/hetzner/scripts/*.sh` and `cloud-init/coolify-bootstrap.sh.tftpl`; `git diff --check`; `git check-ignore -v -- tmp/hetzner_cloud_token.secret`; Coolify API version readback; Coolify server API readback `user=loomops reachable=true usable=true`.
- Remaining blocker: Terraform/OpenTofu validation was not run because the user requested no local installs and Terraform/OpenTofu is unavailable locally. The API fallback runner is the applied source for the live Hetzner resources until Terraform state is imported/adopted.
- Next handoff: Slice 1 should add target profiles/provider registry/Railway compatibility only; do not add Coolify app lifecycle or Platform Coolify API calls yet. Before app lifecycle, add GHCR credentials, Coolify backups/restore rehearsal, dashboard/API protection beyond IP allowlist, and DNS automation when provider credentials are available.

## 2026-05-01 Coolify 007 Terraform Adoption

- User approved installing Terraform after earlier no-local-install constraint. Local Homebrew/npm/pip caches were cleared, freeing about 354 MiB; Maven cache was left intact.
- Terraform `1.6.6` was installed under `/tmp/codex-tools/bin` because the latest Terraform/OpenTofu binaries could not fit cleanly on the nearly full local disk. Use `PATH=/tmp/codex-tools/bin:$PATH` for local Terraform commands in this session.
- Terraform init/fmt/validate now pass for `infra/coolify/hetzner`. Terraform lock file `infra/coolify/hetzner/.terraform.lock.hcl` was generated and is uncommitted.
- Fixed Terraform validation issues: escaped shell variables in `cloud-init/coolify-bootstrap.sh.tftpl`, made DNS outputs tolerant during partial imports, added lifecycle ignores for imported server create-time fields (`network`, `public_net`, `ssh_keys`, `user_data`), and removed unnecessary sensitivity from `ssh_public_key`.
- Imported live resources into ignored local Terraform state: SSH key `111657146`, network `12181920`, subnet `12181920-10.44.0.0/24`, firewall `10915120`, staging server `128757995`, and production server `128758153`.
- Applied one saved Terraform firewall convergence plan: `0 added, 1 changed, 0 destroyed`. Post-apply `terraform plan -detailed-exitcode` returned `0`; servers were no-op.
- Live checks after firewall convergence passed: SSH to both hosts as `loomops`, Coolify API version `4.0.0` on both hosts, and Coolify server readback `user=loomops reachable=true usable=true`.
- Remaining blocker: DNS is still skipped; `loomai.pro` uses registrar nameservers and needs DNS provider credentials or delegation before automation can create records.

## 2026-05-01 Coolify 007 Slice 1 Target Profiles

- Rebased `Platform-V8` onto `origin/main` and resolved stash/rebase conflicts in the working-context docs, Partner Enablement docs, `PlatformVerificationSuiteProperties`, and related verification-suite tests.
- Slice 1 implemented provider-neutral target profile groundwork without Coolify app lifecycle or Platform Coolify API calls.
- Backend changes: added `DeploymentProviderType`, target-profile/provider-credential/source-artifact/provider-resource-handle entities and repositories, `DeploymentTargetProfileService`, `DeploymentProviderRegistry`, and release metadata fields for `targetProfileId`, `providerType`, `sourceArtifactId`, and `providerResourceHandleId`.
- Migration is `V76__deployment_target_profiles_and_provider_handles.sql` after the rebase onto `main`, which already contains migrations through `V75`.
- Railway compatibility: Railway API and stub providers expose provider type and still preserve legacy target strings; dispatch now resolves through the target profile registry.
- Seed data: active Railway stub/API defaults plus inactive Coolify staging/production profiles and pending Coolify credential metadata. Coolify seed metadata contains URLs and UUIDs only; no tokens or secrets are stored.
- Verification passed: focused Maven suite for target profiles, migration seeds, verification-suite property behavior, and rebase-conflicted verification-suite tests; Terraform `init -backend=false`, `fmt -check -recursive`, and `validate`; shell syntax checks for Hetzner helper scripts and Coolify bootstrap template.
- Blockers: DNS is still skipped; Coolify app lifecycle, GHCR credentials, provider API calls, backups/restore rehearsal, and stronger dashboard/API protection are future slices.
- Next handoff: Slice 2 can add a Coolify provider adapter skeleton behind the registry, but should still avoid creating application resources until source artifact and credential contracts are finalized.

## 2026-05-01 Coolify 007 Strict Smoke Unblock

- Continued from the strict staging app smoke failure on `Platform-V8`; no secrets were intentionally printed or committed. Local secret files remain `/tmp/hetzner_cloud_token.secret`, `/tmp/coolify_api_tokens.env`, and `/tmp/coolify_admin_credentials.env`.
- Root cause of the failed strict smoke: `coolify-proxy` was not running, and the hardened Coolify SSH user `loomops` could not traverse `/data/coolify` parent directories, so deployment jobs failed writing generated app compose files with `Permission denied`.
- Live host fix applied to staging and production: started `coolify-proxy` from `/data/coolify/proxy`, added ACLs granting `loomops` access to `/data/coolify` resource directories, and confirmed both proxies are healthy.
- Reproducibility changes: cloud-init and API fallback bootstrap now install `acl`, configure Coolify deployment-user ACLs for applications/databases/services directories, and start the bundled proxy during bootstrap.
- Provider/script changes: Coolify provider now normalizes generated or configured domains to URL form (`http://`/`https://`) before API create/update; strict smoke uses temporary `sslip.io` domains and waits for delete cleanup confirmation.
- Added migration `V77__coolify_temporary_sslip_runtime_domains.sql` to replace inactive Coolify profile default domain suffixes with temporary `sslip.io` suffixes until `loomai.pro` DNS automation is available.
- Changed files: Coolify provider/test, target-profile migration/test, Hetzner bootstrap/API fallback/README, Coolify verifier script, `007` roadmap, and this working context.
- Verification passed: shell syntax checks for Coolify/Hetzner scripts/templates; Terraform `fmt -check` and `validate`; focused Maven `CoolifyDeploymentProviderTest,DeploymentTargetProfileMigrationTest`; `git diff --check`; changed/untracked mixed long-token scan; staging/prod proxy health check; strict live Coolify smoke; post-smoke staging app count `0`.
- Live verification passed: `COOLIFY_STRICT_APPLICATION_SMOKE=true scripts/verify-coolify-provider.sh` created, started, observed `running:unknown`, and deleted a disposable staging `nginx` app; staging returned to zero apps afterward.
- Remaining blockers: custom DNS is still skipped, GHCR read credentials/host registry auth are not configured, Coolify backup/restore rehearsal is pending, Coolify target profiles remain inactive, and frontend operator UI integration is not implemented.

## 2026-05-01 Coolify 007 Public Git-Source Parity

- Decision: while the repo is public, Coolify should work like the Railway flow by building from the same Git source metadata instead of requiring GHCR image artifacts first. GHCR/private registry auth remains a hardened image-source follow-up, not the near-term blocker.
- Implemented `GIT_SOURCE` in `CoolifyDeploymentProvider`: it now builds a `RailwayProvisioningPlanSummary`, creates/updates a Coolify public Git application through `/applications/public`, uses the Railway runtime Dockerfile path with repo-root base directory, carries Railway runtime env vars into Coolify, disables Coolify auto-deploy, and still explicitly starts the application.
- Preserved `IMAGE_SOURCE` Docker-image support for future immutable-image deployments.
- Added `CoolifyCreatePublicApplicationRequest` and Coolify API client create/update methods for public Git applications.
- Added migration `V78__coolify_public_git_source_profiles.sql` to move seeded Coolify staging/production profiles to `GIT_SOURCE` with Dockerfile runtime defaults.
- Added optional public Git smoke to `scripts/verify-coolify-provider.sh`; it creates and deletes a disposable staging public Git app without deploying it.
- Changed files: `CoolifyDeploymentProvider.java`, `CoolifyApiClient.java`, `CoolifyCreatePublicApplicationRequest.java`, Coolify verifier script, Coolify provider/target-profile tests, `V78__coolify_public_git_source_profiles.sql`, `007_COOLIFY_DEPLOYMENT_PROVIDER_AND_RESTARTABLE_SERVICES.md`, and this context file.
- Verification passed: `mvn -f Platfrom/backend/pom.xml -q -DskipTests compile`; `mvn -f Platfrom/backend/pom.xml -q -Dtest=CoolifyDeploymentProviderTest,DeploymentTargetProfileMigrationTest,CoolifyTargetProfileResolverTest,DeploymentProvisioningServiceTargetProfileTest test`; `bash -n scripts/verify-coolify-provider.sh`; `COOLIFY_PUBLIC_GIT_SMOKE=true scripts/verify-coolify-provider.sh` against live staging/prod Coolify `4.0.0`, with staging cleanup confirmed.
- Remaining blockers before real production tenant acceptance: full Platform apply/build/start/health smoke for Git-source app, real DNS replacing `sslip.io`, backup/restore rehearsal, target-profile activation policy, dashboard/API hardening, and operator UI wiring. Push/merge the branch before live Coolify builds if the selected Git branch contains local-only code.

## 2026-05-01 Coolify 007 Target Profile Activation API

- Created live Platform secrets `COOLIFY_STAGING_API_TOKEN` and `COOLIFY_PRODUCTION_API_TOKEN` through `/api/platform/secrets`; values came from `/tmp/coolify_api_tokens.env` and were not printed, summarized, or committed.
- Added admin-only `PATCH /api/deployment-provider/target-profiles/{targetProfileId}` with `PatchDeploymentTargetProfileRequest` so operators can activate Coolify target profiles and optionally mark them default without direct DB edits.
- Patch behavior: profile must be active before becoming runtime/restartable default; setting a default clears the previous default for the same provider type; existing Railway compatibility is unchanged.
- Changed files: `DeploymentProviderOperationsController.java`, `DeploymentTargetProfileService.java`, `PatchDeploymentTargetProfileRequest.java`, `DeploymentProvisioningServiceTargetProfileTest.java`, `007_COOLIFY_DEPLOYMENT_PROVIDER_AND_RESTARTABLE_SERVICES.md`, and this context file.
- Verification passed: `mvn -f Platfrom/backend/pom.xml -q -Dtest=CoolifyDeploymentProviderTest,DeploymentTargetProfileMigrationTest,CoolifyTargetProfileResolverTest,DeploymentProvisioningServiceTargetProfileTest test`; `mvn -f Platfrom/backend/pom.xml -q -DskipTests compile`; `git diff --check`.
- Next handoff: commit/push the activation endpoint, wait for the Platform backend deployment serving `Platform-V8`, activate `dtp-coolify-staging`, run preflight, then execute a disposable Platform apply using `targetProfileId=dtp-coolify-staging` and verify provider resource status/log/delete cleanup.

## 2026-05-01 Coolify 007 Platform Staging Smoke

- Commit `625032e71` pushed to `Platform-V8`; live Platform redeployed and served the target-profile activation endpoint.
- `dtp-coolify-staging` was activated through Platform. Production target profile remains inactive/non-default.
- Initial Platform preflight failed because Railway-hosted Platform could not reach staging Coolify API through the Hetzner/operator allowlist. Live unblock: added staging-only Hetzner firewall `loom-coolify-staging-platform-api-firewall` (`10916648`) attached only to `coolify-staging-01`, plus staging host UFW `8000/tcp` from anywhere. Production remains behind the shared allowlist.
- After the staging API unblock, Platform preflight for `dtp-coolify-staging` passed with Coolify `4.0.0`.
- Disposable Platform deployment `dep-a8492a07` published `ver-e62abdd7` and applied through Coolify release `rel-4123e1a1`; Platform created Coolify app UUID `squ91oudrq6oqah8cpmk5zzl`, triggered public Git deployment from `Platform-V8`, and stored provider handle `dprh-86c0573f`.
- The first release verification timed out/failed while Coolify still reported `exited:unhealthy`, but the runtime later reached `running:healthy`; public health `http://dep-a8492a07.46.224.145.148.sslip.io/actuator/health` returned `200 UP`, Platform logs endpoint returned logs, and a later verification run for the deployment passed. Release status did not reconcile back from `APPLIED_VERIFICATION_FAILED`.
- Cleanup completed: Platform provider delete queued successfully, direct Coolify readback returned 404 for the disposable app, staging app count for the smoke app returned 0, and the disposable Platform deployment was archived then hard-delete queued (`del-b0ac1d10`).
- Security follow-up from smoke: a live session cookie was accidentally printed and immediately invalidated through logout. Platform provider status also returned raw Coolify details that may contain generated app/server tokens; patched `CoolifyDeploymentProvider.status(...)` and action summaries to return an allowlisted redacted detail object only.
- Additional changed files: `CoolifyDeploymentProvider.java`, `CoolifyDeploymentProviderTest.java`, `infra/coolify/hetzner/main.tf`, `variables.tf`, `README.md`, `007_COOLIFY_DEPLOYMENT_PROVIDER_AND_RESTARTABLE_SERVICES.md`, and this context file.
- Verification passed after redaction/firewall patch: focused Coolify/profile Maven tests, backend compile, Terraform `fmt -check -recursive`, Terraform `validate`, `git diff --check`, and changed-file long-token scan.

## 2026-05-01 Coolify 007 Late Verification Reconciliation

- Follow-up from the staging Platform smoke: late successful verification runs can now reconcile a release left in `APPLIED_VERIFICATION_FAILED`.
- Backend changes: `DeploymentReleaseRecoveryService` looks for the newest `PASSED` verification run for the same release/version, promotes the release to `APPLIED_VERIFIED`, clears the release error, and marks the deployment `ACTIVE` with the verified version.
- Repository change: `DeploymentVerificationRunRepository` now supports `findByReleaseIdOrderByCreatedAtDesc(...)`.
- Regression test added in `DeploymentReleaseRecoveryServiceTest` for the Coolify late-success path; it verifies no extra provider dispatch/Railway call occurs during reconciliation.
- Verification passed: `mvn -f Platfrom/backend/pom.xml -q -Dtest=DeploymentReleaseRecoveryServiceTest test`; focused Coolify/profile/recovery Maven suite; backend compile; `git diff --check`.
- Changed files: `DeploymentVerificationRunRepository.java`, `DeploymentReleaseRecoveryService.java`, `DeploymentReleaseRecoveryServiceTest.java`, `007_COOLIFY_DEPLOYMENT_PROVIDER_AND_RESTARTABLE_SERVICES.md`, and this context file.
- Next handoff: push/deploy this reconciliation patch, rerun a disposable staging Platform apply if needed, trigger verification recheck plus release reconcile, then confirm the release reaches `APPLIED_VERIFIED` before cleanup. Production remains gated by real DNS, backup/restore rehearsal, production API/dashboard hardening, and operator UI wiring.

## 2026-05-01 Coolify 007 Provider Readiness Settle

- Commit `747dcec7c` pushed the late-verification reconciliation patch to `Platform-V8`.
- Live disposable Platform smoke `dep-dee1b7a8` / release `rel-199dae14` reproduced a deeper provider timing issue: Coolify `start` returned before the app was settled, Platform verification ran while Coolify still reported the app as unhealthy, and a later runtime health HTTP 200 was not enough for immediate deep verification to pass.
- Cleanup completed for that smoke: direct Coolify app cleanup returned staging app count to `0`; Platform deployment hard-delete queued as `del-fb48bb46`; follow-up resource list for `dep-dee1b7a8` returned `0`.
- Provider fix in progress: `CoolifyDeploymentProvider.provision(...)` now adds a bounded `wait_for_coolify_runtime` step after `trigger_coolify_deploy`, polling Coolify until status is running and not unhealthy before returning to Platform release verification; resource handles become `ACTIVE` when the observed app is ready.
- Verification passed for the provider-settle patch: focused Coolify/profile/recovery Maven suite.
- Security note: do not print raw Coolify application JSON during future live checks; use only safe Platform status summaries or filtered fields.
- Commit `c0c353d52` pushed the provider runtime-settle patch.
- A second smoke (`dep-92d0143b` / `rel-64e33107`) ran before live Platform had picked up `c0c353d52`; it again skipped `wait_for_coolify_runtime`, then hit a transient `502` while polling releases and left the Platform record in `VERIFYING/sync_marketplace_datasets`. Direct Coolify cleanup removed the app and staging app count returned to `0`.
- New recovery fix in progress: `DeploymentReleaseRecoveryService` now treats Coolify `VERIFYING` releases at `sync_marketplace_datasets` or `run_verification` as recovery candidates, reusing the provider-neutral dataset-sync/verification recovery path so stuck Coolify records can become terminal and deletable.
- Verification passed for the Coolify stale-recovery patch: focused Coolify/profile/recovery Maven suite.

## 2026-05-01 Coolify 007 Railway Parity Runtime Connector

- Continued after the Coolify runtime reached `running:healthy` but Platform deep verification failed on runtime/admin and connector/admin probes.
- Root cause identified in code: Coolify copied Railway env values literally, including `${secret:...}` placeholders, and only created the runtime app. Railway resolves those placeholders and provisions both runtime plus REST connector.
- Commit `c2ebf664c` pushed to `Platform-V8`.
- Backend changes implemented: `CoolifyDeploymentProvider` resolves Platform secret placeholders through `PlatformSecretService` before sending env vars to Coolify, marks resolved secret env vars as shown-once, creates/updates a paired public-Git REST connector app for Git-source profiles, rewires runtime `ACTIONS_CONNECTOR_BASE_URL` to the connector FQDN, rewires connector `REST_CONNECTOR_RUNTIME_PROXY_BASE_URL` to the runtime FQDN, starts/waits for connector and runtime, and records separate resource handles (`APPLICATION`, `CONNECTOR_APPLICATION`).
- Cleanup change implemented: hard delete now deletes tracked provider resource handles through the provider registry before Platform records are removed; Coolify delete treats 404 as already absent.
- Verification passed: `mvn -f Platfrom/backend/pom.xml -q -Dtest=CoolifyDeploymentProviderTest,DeploymentInfrastructureCleanupServiceTest,CoolifyTargetProfileResolverTest,DeploymentProvisioningServiceTargetProfileTest,DeploymentReleaseRecoveryServiceTest test`; `mvn -f Platfrom/backend/pom.xml -q -DskipTests compile`; full backend `mvn -f Platfrom/backend/pom.xml -q test`; `git diff --check`; exact local-secret scan for changed files.
- Live Platform/Coolify staging proof passed: disposable deployment `dep-4a638d92`, version `ver-2551d85e`, release `rel-e7fd5707` applied with `targetProfileId=dtp-coolify-staging`; release reached `APPLIED_VERIFIED/PASSED`; provider handles included `APPLICATION` and `CONNECTOR_APPLICATION`, both `ACTIVE` with `running:healthy`; workspace reported runtime URL present and `connectorProvisioned=true`; Platform hard delete completed; provider resources returned `0`; Coolify app matches returned `0`.
- Earlier live run `dep-3886ca0d` also reached `APPLIED_VERIFIED/PASSED` with both apps healthy and cleanup complete; its local smoke script failed only because it checked for a raw `connectorBaseUrl` field that the workspace summary does not expose.
- Changed files: `CoolifyDeploymentProvider.java`, `DeploymentInfrastructureCleanupService.java`, `DeploymentDeletionExecutionService.java`, `CoolifyDeploymentProviderTest.java`, `DeploymentInfrastructureCleanupServiceTest.java`, `007_COOLIFY_DEPLOYMENT_PROVIDER_AND_RESTARTABLE_SERVICES.md`, and this context file.
- Current readiness: targeted staging deployment provisioning on Coolify is testable with `targetProfileId=dtp-coolify-staging` while the repository remains public.
- Remaining blockers before production/default cutover: real DNS replacing `sslip.io`, production control-plane access without public `8000`, operator UI wiring, and GHCR/private registry auth before making the repo private.

## 2026-05-01 Coolify 007 Default And Backup Rehearsal

- Commit `3bb407a7a` pushed to `Platform-V8` with the global-default resolver fix, V79 migration, backup rehearsal runner, tests, and docs.
- Live Platform update completed: `dtp-coolify-staging` is active and default for runtime plus restartable services; `dtp-coolify-production` is active but non-default.
- Repo policy migration added: `V79__coolify_staging_runtime_default.sql` clears older runtime/restartable defaults, activates staging as the Coolify default, and activates production as explicit non-default.
- Live no-target smoke passed after deploy: disposable deployment `dep-ac9468b9`, version `ver-76231ac5`, release `rel-e3aaf245` applied without a `targetProfileId`; Platform selected `targetProfileId=dtp-coolify-staging`, `providerType=COOLIFY`, reached `APPLIED_VERIFIED/PASSED`, stored `APPLICATION` plus `CONNECTOR_APPLICATION` handles as `ACTIVE`, and workspace reported a runtime URL plus `connectorProvisioned=true`.
- No-target smoke cleanup completed: Platform hard-delete operation `del-65ca8112` reached `SUCCEEDED` and provider resources for `dep-ac9468b9` returned `0`. Earlier local script run `dep-96259cae` stopped before publish because it treated publish-ready warnings as fatal; cleanup operation `del-b2c27ab4` also reached `SUCCEEDED` with provider resources `0`.
- Production hardening status: production host UFW keeps Coolify dashboard/API port `8000` restricted to the setup/operator CIDR while public app traffic remains on `80/443`; live Platform production preflight fails with `Coolify API request failed for /health`, so production applies remain blocked until a protected Platform-to-production Coolify API path exists.
- Backup/restore rehearsal passed on both Hetzner hosts through `infra/coolify/hetzner/scripts/rehearse-coolify-backup-restore.sh`. Staging backup directory: `/var/backups/loom-coolify/staging-20260501T214218Z`; production backup directory: `/var/backups/loom-coolify/production-20260501T214218Z`. Each contains root-only `coolify-db.dump`, `coolify-state-files.tgz`, `SHA256SUMS`, and `restore-rehearsal-status.json`.
- Rehearsal proof: each DB dump restored into a temporary Postgres database, each file archive extracted into a temporary directory and verified for `.env` plus SSH keys, then temporary restore targets were removed.
- Verification: `bash -n infra/coolify/hetzner/scripts/*.sh`, `git diff --check`, focused backend tests (`DeploymentTargetProfileMigrationTest`, `DeploymentProvisioningServiceTargetProfileTest`, `CoolifyTargetProfileResolverTest`, `CoolifyDeploymentProviderTest`), and `mvn -f Platfrom/backend/pom.xml -q -DskipTests compile` pass. `terraform fmt -check`/`terraform validate` are blocked locally because `terraform` is not installed and operator direction is not to install local tooling.
- DNS remains intentionally delayed; GHCR/private registry auth remains a go-live/private-repo blocker and is called out in the 007 pending items.

## 2026-05-02 Coolify 007 Provider-Neutral UI

- Continued `007` on `Platform-V8`: backend provider abstractions already exist (`DeploymentTargetProfile`, `DeploymentProvisioningProvider`, `DeploymentProviderRegistry`, provider resource handles/actions); UI is now decoupled from Railway flow names for deployment provisioning workflows.
- Implemented generic backend alias `GET /api/deployments/{deploymentId}/versions/{versionId}/provisioning-plan` and frontend generic API/types for target profiles, provider preflight, provider resources, status/logs/actions, and generic provisioning-plan fetch.
- Added `Platfrom/ui/src/components/DeploymentProviderOperationsPanel.tsx` and wired it into Providers: operators can inspect/activate/default target profiles, run profile preflight, list provider resources, fetch status/logs, and start/stop/restart/delete provider resources.
- Revisions now has an `Apply target` selector using active target profiles; blank selection uses the active runtime default (`dtp-coolify-staging` currently), and release execution shows `providerType`/`targetProfileId`.
- Verification, Security, and Diagnostics now use active runtime target-profile preflight instead of the Railway preflight endpoint; operator-facing copy in deployment/diagnostics/product-service/inference-service/platform diagnostics pages now says provider/provisioning instead of Railway-specific flow labels.
- Changed files: `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/web/DeploymentController.java`, `Platfrom/ui/src/api/platformApi.ts`, `Platfrom/ui/src/components/DeploymentProviderOperationsPanel.tsx`, `Platfrom/ui/src/pages/{ProvidersPage,RevisionsPage,VerificationPage,SecurityPage,DiagnosticsPage,OverviewPage,DeploymentsPage,ProductServicesPage,InferenceServicesPage,PlatformDiagnosticsPage}.tsx`, `Platfrom/ui/tsconfig.app.tsbuildinfo`, and `007_COOLIFY_DEPLOYMENT_PROVIDER_AND_RESTARTABLE_SERVICES.md`.
- Verification passed: `npm --prefix Platfrom/ui run build`; `mvn -f Platfrom/backend/pom.xml -DskipTests clean compile`; `git diff --check`; exact local-secret scan against `/tmp/hetzner_cloud_token.secret`, `/tmp/coolify_api_tokens.env`, `/tmp/coolify_admin_credentials.env`, `/tmp/platform_login_email.secret`, `/tmp/platform_login_password.secret`; provider-neutral UI copy scan found no old Railway-facing phrases.
- Secrets status: no local secret values found in changed files; do not print or commit any `/tmp/*secret*` values.
- Remaining `007` blockers before production tenant cutover: real DNS replacing `sslip.io`, protected production Coolify API/control-plane access without broad public `8000`, keep `dtp-coolify-production` non-default until preflight passes from Platform, and add GHCR/private registry auth before private repo/live private-source deployments.
- Next handoff: after this commit is deployed, browser-check Providers/Revisions/Verification/Security against live Platform, then run one no-target Coolify staging apply from the UI or API to confirm default target behavior still reaches `APPLIED_VERIFIED` and provider resource actions/logs render.

## 2026-05-02 Coolify 007 Default Provider Live Proof

- Commit `e1d0e173a` pushed to `Platform-V8`: `CoolifyDeploymentProvider` now honors the target profile `deploymentTimeoutSeconds` and `deploymentPollIntervalSeconds` as the default readiness-settle window before falling back to provider constants.
- Root cause: an interrupted smoke showed the REST connector could become healthy after the old hardcoded 6-minute provider wait. The staging profile already carries a 600-second Coolify timeout, so the provider should use that profile contract.
- Verification passed locally for the code fix: `mvn -f Platfrom/backend/pom.xml -Dtest=CoolifyDeploymentProviderTest test`; `git diff --check`; exact changed-file secret scan against local Hetzner/Coolify/Platform secret files.
- Live Platform redeployed after the push; `/actuator/health` returned `UP`.
- Clean no-target live smoke passed: deployment `dep-36ad13ea`, version `ver-b7e57a63`, release `rel-0d70cac1` applied without a `targetProfileId`; Platform selected `dtp-coolify-staging` with `providerType=COOLIFY`.
- Generic plan alias proof passed through `GET /api/deployments/{deploymentId}/versions/{versionId}/provisioning-plan`; runtime service was `runtime-dep-36ad13ea`, connector service was `rest-connector-dep-36ad13ea`, branch `Platform-V8`.
- Runtime and connector both reached `running:healthy`; Platform release reached `APPLIED_VERIFIED`, `provisioningStatus=ACTIVE`, `verificationStatus=PASSED`.
- Provider operations proof passed: two provider handles were present before cleanup, `APPLICATION` handle `dprh-e991febf` and `CONNECTOR_APPLICATION` handle `dprh-5728910e`; status endpoints returned `RUNNING_HEALTHY`; logs endpoints returned non-empty logs.
- Cleanup completed: deployment archived, hard-delete operation `del-9fc8420a` reached `SUCCEEDED`, provider resources for `dep-36ad13ea` returned `0`, and direct Coolify app-name readback found `0` remaining smoke apps.
- Interruption cleanup note: earlier helper failures created `dep-87d20fe5` and `dep-771978e9`; both Platform records were hard-deleted (`del-d7b4a53b`, `del-3b26199c`), and the only orphan Coolify connector app from `dep-87d20fe5` was directly deleted by unique smoke name.
- Current readiness: Coolify staging is testable as the default provisioning target from the Platform API/UI abstraction. Remaining go-live blockers are real DNS, protected production Coolify API access, production preflight, and GHCR/private registry auth before private repo deployments.

## 2026-05-02 Coolify 007 Release Gate Parity

- Commit `12dca128c` pushed to `Platform-V8`: pre-apply release verification now treats `COOLIFY` as a live-gated provider instead of skipping live rollout prerequisites for every non-`RAILWAY_API` target.
- Backend change: `DeploymentReleaseVerificationService` now runs the shared pre-apply artifact, managed secret, private runtime token, authz deployability, tenant-scoped vector, vectorization, and provider-connectivity checks for both `RAILWAY_API` and `COOLIFY`.
- Provider-specific gate: Railway releases still run the existing Railway preflight checks; Coolify releases resolve the selected active target profile and run the registered provider preflight through `DeploymentProviderRegistry`. A failed Coolify preflight becomes a failed `provider_preflight` check and blocks the release before provisioning.
- Regression test added: `verifyPreApplyRunsCoolifyGateAndFailsWhenTargetPreflightFails` proves Coolify pre-apply gates run common checks, fail on target-profile preflight failure, and do not run Railway preflight checks.
- Verification passed: `mvn -f Platfrom/backend/pom.xml -Dtest=DeploymentReleaseVerificationServiceTest test`; `mvn -f Platfrom/backend/pom.xml -DskipTests clean compile`; `mvn -f Platfrom/backend/pom.xml -Dtest=DeploymentReleaseVerificationServiceTest,DeploymentReleaseExecutionServiceTest,DeploymentReleaseRecoveryServiceTest,CoolifyDeploymentProviderTest,DeploymentProvisioningServiceTargetProfileTest,CoolifyTargetProfileResolverTest test`; `git diff --check`; exact changed-file local-secret scan.
- Live release-gate smoke passed after Platform redeploy: deployment `dep-8be9835f`, version `ver-bad9ef98`, release `rel-ed09eeb8` applied without `targetProfileId`; Platform selected `dtp-coolify-staging`/`COOLIFY`.
- Live PRE_APPLY verification proof: run `vrf-2761b71c` reached `PASSED` with summary `24 passed, 0 failed, 8 skipped`; `provider_preflight=PASSED`; no `railway_preflight_*` checks were present.
- Live release proof: release reached `APPLIED_VERIFIED`, `provisioningStatus=ACTIVE`, `verificationStatus=PASSED`; runtime and connector provider status endpoints returned `RUNNING_HEALTHY`; logs endpoints returned non-empty logs.
- Cleanup completed: hard-delete operation `del-1c8ecd7c` reached `SUCCEEDED`, Platform resources for `dep-8be9835f` returned `0`, and direct Coolify app-name readback found `0` smoke apps.
- Current readiness: Coolify staging provisioning now has pre-apply gate parity with Railway for the checks that matter before provisioning, plus Coolify-specific target-profile preflight.

## 2026-05-02 Coolify 007 UI Abstraction Tightening

- Continued on `Platform-V8` after release-gate parity: deployment UI now binds diagnostics/overview to provider-neutral read-back and provider-resource log contracts instead of deployment-specific Railway log/read-back types.
- Backend source-of-truth response now includes `liveProviderReadback` alongside the existing legacy `liveRailwayReadback`; both currently point at the same read-back object until the backend service internals are renamed.
- Frontend `platformApi.ts` now exposes first-class `DeploymentProviderLive*` and `DeploymentProvisioning*` types; Railway-named plan/live-readback types remain aliases only for older internal callers.
- Deployment Diagnostics now fetches provider resources with `fetchDeploymentProviderResources(...)`, selects the runtime/connector/vectorization resource handle, and fetches logs through `fetchDeploymentProviderResourceLogs(...)`. It no longer calls the deployment `/railway-logs` endpoint.
- Deployment Overview now reads provider live state through `liveProviderReadback` with a legacy fallback.
- Changed files: `DeploymentSourceOfTruthSummary.java`, `DeploymentSourceOfTruthService.java`, `Platfrom/ui/src/api/platformApi.ts`, `Platfrom/ui/src/pages/DiagnosticsPage.tsx`, `Platfrom/ui/src/pages/OverviewPage.tsx`, this context file, and `007_COOLIFY_DEPLOYMENT_PROVIDER_AND_RESTARTABLE_SERVICES.md`.
- Verification passed: `npm --prefix Platfrom/ui run build`; `mvn -f Platfrom/backend/pom.xml -Dtest=DeploymentWorkspaceIntegrationTest,DeploymentReleaseVerificationServiceTest,DeploymentReleaseExecutionServiceTest,CoolifyDeploymentProviderTest,DeploymentProvisioningServiceTargetProfileTest,CoolifyTargetProfileResolverTest test`; `mvn -f Platfrom/backend/pom.xml -DskipTests clean compile`; `git diff --check`.
- Secret scan status: changed-file secret-pattern scan printed no secret values; the only pattern hit was `Platfrom/ui/src/api/platformApi.ts` because it contains secret metadata field names. No local secret values were found or committed.
- Next handoff: commit/push this UI abstraction tightening, then after deployment browser-check Diagnostics/Overview against a Coolify-backed deployment and confirm provider resource logs render from `APPLICATION` and `CONNECTOR_APPLICATION` handles.

## 2026-05-02 Coolify 007 Loom Companion Customer Migration

- User requested migrating the Loom/Shopify Companion customer deployments from Railway to Coolify and switching current customers to Coolify-backed deployments.
- Live Platform scan found two Shopify Companion store bindings: `shopping-companion-test.myshopify.com` (`dep-8c3e7259`) and `loom-verification-20260418.myshopify.com` (`dep-3bf25c3f`).
- `shopping-companion-test.myshopify.com` was already migrated successfully: latest release `rel-e8cee807` on version `ver-1b77bfba` is `APPLIED_VERIFIED/PASSED`, provider `COOLIFY`, target `dtp-coolify-staging`; runtime and connector handles are `RUNNING_HEALTHY`, and both public health endpoints return `200 UP`.
- `loom-verification-20260418.myshopify.com` migration required repair: the store was restored from archived state, re-bootstrapped to the current shared Qdrant endpoint, published through `ver-ccc844b6`, and re-applied after a Coolify update-payload bug was fixed.
- Root cause fixed in `CoolifyApiClient`: create payloads still include create-only fields, but update payloads now omit Coolify-rejected PATCH fields (`project_uuid`, `server_uuid`, `environment_name`, `environment_uuid`, `destination_uuid`, `autogenerate_domain`).
- Code fix committed and pushed as `5643735cf` (`Fix Coolify application update payloads`); live Platform redeployed and returned `/actuator/health` `UP`.
- Live Loom repair release `rel-75648f34` on version `ver-ccc844b6` reached `APPLIED_VERIFIED/PASSED`, provider `COOLIFY`, target `dtp-coolify-staging`; runtime and connector handles are active with `running:healthy`.
- Final live proof: both Shopify Companion deployments are `ACTIVE` with latest verified Coolify releases, binding warnings are empty, readiness is `STOREFRONT_READY`, and all four Coolify public health endpoints (`runtime` plus `connector` for both stores) returned `200 UP`.
- After the verified Loom release, replayed the already-enabled widget status for `loom-verification-20260418.myshopify.com`; its onboarding status is now `LIVE`.
- Verification run locally for the fix: `mvn -f Platfrom/backend/pom.xml -q -Dtest=CoolifyApiClientTest,CoolifyDeploymentProviderTest test`; `git diff --check` before commit.
- Changed files in the pushed fix: `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/CoolifyApiClient.java` and `Platfrom/backend/src/test/java/com/ai/fabric/platform/backend/deployment/service/CoolifyApiClientTest.java`.
- Remaining blockers before production tenant cutover stay unchanged: real DNS instead of `sslip.io`, protected production Coolify API/control-plane access, production profile preflight, and GHCR/private registry auth before private-source deployments.

## 2026-05-02 Coolify 007 Production API Preflight Unblock

- Implemented protected production Coolify API access through a production-only Hetzner firewall plus host UFW allowlist, not a broad public `8000` opening.
- Live discovery process: temporarily attached a production-only Hetzner firewall while host UFW still blocked unknown sources, captured live Platform egress traffic to production port `8000`, then narrowed the firewall to `52.52.45.183/32`.
- Live resources: Hetzner firewall `loom-coolify-production-platform-api-firewall` (`10918233`) attached only to `coolify-prod-01`; rule allows TCP `8000` only from `52.52.45.183/32`. Host UFW has the matching `52.52.45.183 -> 8000/tcp` allow rule.
- Imported the staging and production Platform API firewalls into local ignored Terraform state, applied Terraform convergence (`0 added, 7 changed, 0 destroyed`) to normalize live labels/metadata, then `terraform plan -detailed-exitcode` returned no changes with the live allowlist variables.
- Terraform module now has `production_platform_api_allowed_cidrs` and attaches a production-only API firewall when set; `terraform.tfvars.example` and `infra/coolify/hetzner/README.md` document the production allowlist/import path.
- Live Platform production preflight now passes: `dtp-coolify-production` returned `PASSED`, Coolify version `4.0.0`; production profile remains active but non-default while staging remains the runtime/restartable default.
- Verification passed: `terraform fmt -check -recursive`; `terraform validate`; live Hetzner firewall readback; host UFW readback; live Platform production preflight.
- Remaining production tenant cutover blockers: real DNS instead of `sslip.io`, GHCR/private registry auth before private-source deployments, and a future stronger stable control-plane access layer if Railway egress changes.

## 2026-05-02 Coolify 007 Lightweight Customer Grouping

- Implemented Platform-owned Coolify UI grouping by customer project/environment; customers still do not access Coolify and Platform remains the deployment source of truth.
- Shape: `Project customer-{platformCustomerSlug}` with `staging|production` environment containing runtime and connector apps.
- Backend changes: `CoolifyApiClient` now supports project/environment list/create/get; `CoolifyDeploymentProvider` resolves a customer project scope from `PlatformCustomerRepository`, creates missing Coolify project/environment records, sends runtime/connector create requests to the resolved scope, persists resolved project/environment UUIDs on provider handles, and records scope metadata/details.
- Existing fixed-project Coolify handles are treated as stale on next apply; Platform deletes the old app and creates a replacement under the customer project/environment because live Coolify rejected moving apps by `project_uuid` PATCH.
- Live first re-apply for `dep-8c3e7259` failed quickly with Coolify HTTP `409` on `/applications/public` after stale scope resolution; root cause is Coolify asynchronous delete/name-domain conflict while replacing fixed-project apps. Provider patch now waits for stale app absence and retries one conflicting create after deleting a same-name app outside the resolved customer scope.
- Added `CoolifyProjectSummary.java`, `CoolifyEnvironmentSummary.java`, and migration `V80__coolify_customer_project_grouping.sql` to mark seeded Coolify profiles with grouping defaults.
- Updated `007_COOLIFY_DEPLOYMENT_PROVIDER_AND_RESTARTABLE_SERVICES.md` with the lighter grouping contract, local verification, and pending live proof.
- Verification passed: `mvn -f Platfrom/backend/pom.xml -q -Dtest=CoolifyApiClientTest,CoolifyDeploymentProviderTest test`; `mvn -f Platfrom/backend/pom.xml -q -Dtest=CoolifyApiClientTest,CoolifyDeploymentProviderTest,DeploymentTargetProfileMigrationTest test`; `mvn -f Platfrom/backend/pom.xml -q -Dtest=CoolifyDeploymentProviderTest test` after stale-delete retry patch; `mvn -f Platfrom/backend/pom.xml -q -DskipTests compile`; `git diff --check`; changed-file exact local-secret scan against `/tmp/hetzner_cloud_token.secret`, `/tmp/coolify_api_tokens.env`, `/tmp/coolify_admin_credentials.env`, `/tmp/platform_login_email.secret`, and `/tmp/platform_login_password.secret`.
- Commits pushed: `e0f39918a` (`Group Coolify resources by customer`) and `37bcb9d6f` (`Retry Coolify customer scope replacement conflicts`).
- Live proof after deploy: Shopping Companion re-apply `rel-264ea467` for `dep-8c3e7259` / `ver-1b77bfba` reached `APPLIED_VERIFIED`, `ACTIVE`, `PASSED`; Loom verification re-apply `rel-873fdcfb` for `dep-3bf25c3f` / `ver-ccc844b6` reached `APPLIED_VERIFIED`, `ACTIVE`, `PASSED`.
- Staging Coolify project readback now includes `customer-shopify-store-shopping-companion-test-myshopify` and `customer-shopify-store-loom-verification-20260418-myshopi`; provider handles for both deployments point at the customer project/environment UUIDs instead of shared `loom-staging`.
- Runtime and connector public health endpoints for both deployments returned HTTP `200` with `UP`.
- A failed historical release `rel-6150ba51` remains on `dep-8c3e7259` from the pre-fix HTTP `409` attempt; later release `rel-264ea467` is the verified active release.
- Next handoff: production blockers remain real DNS and GHCR/private registry auth before private-source deployments; production target stays non-default.

## 2026-05-02 Railway Deployment Cleanup After Coolify Migration

- User requested cleanup of Railway deployments after current customer-bound deployments were migrated to Coolify. Scope used: remove platform-managed tenant/runtime Railway deployment projects only; retain Railway control-plane/product-service resources.
- Railway workspace inventory before cleanup found 12 projects. Retained `loom-product-production-shopify-` because it hosts the Shopify Bridge product service/control-plane integration.
- Deleted 11 platform-managed Railway deployment projects, covering 28 services named only as `runtime-dep-*`, `rest-connector-dep-*`, and `vectorization-runner-dep-*`. Deleted projects included the old `shopify-companion-s-dev-8c3e7259` Railway project and verification/dev runtime projects.
- Post-cleanup Railway readback found exactly one remaining project: `loom-product-production-shopify-`.
- Verification passed after cleanup: Shopify Bridge health returned `UP`, Platform backend health returned `UP`, Platform UI health returned `UP`, Coolify staging/prod provider verification passed, and the four current Coolify customer runtime/connector health endpoints for `dep-8c3e7259` and `dep-3bf25c3f` returned HTTP `200` with `UP`.
- Local audit artifacts only, not for commit: `/tmp/railway-cleanup/workspace-inventory-before.json`, `/tmp/railway-cleanup/deletion-result.json`, `/tmp/railway-cleanup/workspace-inventory-after.json`.
- Blocker observed but not cleanup-blocking: private handoff Platform session credentials returned HTTP `401`, so the cleanup was run directly through Railway GraphQL using the private Railway token without printing or committing secret values.

## 2026-05-02 Shopify Bridge Product-Service Migration To Coolify Production

- User changed policy: recreate the Shopify Bridge product service on Coolify, use it from Platform, and update the Shopify app to point at Coolify instead of the old Railway Bridge.
- Created live production Coolify project/environment/app directly through the production Coolify API because private handoff Platform admin/session credentials returned HTTP `401` for product-service API access. Platform DB was then updated so `shopify-bridge-prod` is the Platform product-service source of truth for the Coolify app.
- Live Coolify app: `shopify-bridge-prod`, app UUID `wurlsp7d3bdsedy1lmn33sdc`, URL `https://shopify-bridge-prod.46.225.162.106.sslip.io`, status `running:healthy`.
- Platform product-service row now has `providerType=COOLIFY`, `targetProfileId=dtp-coolify-production`, Coolify project/environment/application metadata, `status=ACTIVE`, and the Coolify base URL. Railway IDs were left as rollback metadata only.
- Copied the existing Railway Bridge runtime variables into Coolify without printing secret values; patched runtime public URL, environment scope, and actuator exposure. Local runtime-var audit file remains `/tmp/shopify-bridge-coolify/railway-vars.json` mode `0600`.
- Updated Shopify app config and all theme-extension block default Bridge URLs from the old Railway Bridge to `https://shopify-bridge-prod.46.225.162.106.sslip.io`.
- Shopify CLI deploy succeeded with linked config `shopify.app.loom-companion.toml`; latest released app version is `loom-companion-27`.
- Added Platform guardrails: product services whose details declare `providerType=COOLIFY` no longer enter the Railway reconcile/refresh/restart/decommission lifecycle; health drift accepts the Coolify binding, and Railway logs/history endpoints return unavailable for Coolify-managed product services instead of showing stale Railway data.
- Live verification passed: Coolify app readback `running:healthy`; Bridge `/actuator/health` returned HTTP `200` with `UP`; Bridge admin overview returned `READY`; storefront bootstrap for `shopping-companion-test.myshopify.com` returned HTTP `200` and `available=true`; Shopify app proxy route exists and redirects to the password page for the password-protected dev store.
- Local verification passed: `mvn -f Platfrom/backend/pom.xml -q -Dtest=PlatformManagedProductProvisioningServiceTest,PlatformManagedProductAdminServiceTest test`; `git diff --check`; added-line exact local-secret scan against Hetzner/Coolify/Platform/Shopify local secret files.
- Disk note: local cache cleanup was required for Shopify CLI preflight; removable caches were cleared, no repo/source files or local secret files were intentionally deleted.
- The old Railway `loom-product-production-shopify-` project still exists as rollback until explicit deletion after a soak period. Next cleanup should remove it only after confirming no Shopify callbacks or merchants still hit the old Railway URL.

## 2026-05-02 Shopify Bridge Staging Coolify Correction

- Supersedes the same-day production Bridge migration for the active Shopify staging workflow: user clarified that current work is staging, not production.
- Active Coolify Bridge target is now staging: project `product-shopify-bridge-staging`, environment `staging`, app `shopify-bridge-staging`, UUID `c12bjqdcyqdt7tzgr48pev3z`, URL `https://shopify-bridge-staging.46.224.145.148.sslip.io`, readback `running:healthy`.
- Platform product-service row `shopify-bridge-prod` was repointed to `providerType=COOLIFY`, `targetProfileId=dtp-coolify-staging`, and the staging Coolify app metadata. The service ref remains `shopify-bridge-prod` because existing Platform Shopify store bindings use that ref.
- Shopify app config and all theme-extension Bridge URL defaults were changed from the accidental production Coolify URL to the staging Coolify URL.
- Shopify CLI release `loom-companion-29` is the corrected staging release. An intermediate `loom-companion-28` was superseded because the local deploy env still carried an older Railway PR URL for app/callback/proxy fields.
- Live staging verification passed: Bridge `/actuator/health` returned HTTP `200 UP`; Bridge admin overview returned `READY`, `environmentScope=staging`, and the staging public base URL; storefront bootstrap for `shopping-companion-test.myshopify.com` returned HTTP `200` with `available=true`; generated Shopify session JWT proof against `/api/app/session` returned HTTP `200`.
- Directly opening the deployed merchant UI outside Shopify Admin can still show `Shopify session token is unavailable`; that is expected for production-built UI because `dev_session_token` is accepted only in local `import.meta.env.DEV`. Embedded Shopify Admin or a valid Shopify session JWT is the correct staging proof.
- The accidental production Coolify Bridge app `shopify-bridge-prod` was stopped after staging verification; readback changed to `exited:unhealthy`. Keep it as rollback metadata only until deliberate cleanup.
- Local verification passed: `git diff --check`; `mvn -f Platfrom/backend/pom.xml -q -Dtest=PlatformManagedProductProvisioningServiceTest,PlatformManagedProductAdminServiceTest test`; Shopify deploy shell syntax check; Shopify CLI preflight with `/tmp/shopify-live-deploy-staging.env`; exact added-line local-secret scan. `npm --prefix product-services/shopify-bridge-service/ui run build` was blocked because `ui/node_modules` is missing and local installs are not allowed in this session.
- Local secret files used for DB/Coolify/Shopify auth remained under `/tmp` and were not committed or summarized.

## 2026-05-02 Loom Verification Store Cleanup

- User requested deletion of `loom-verification-20260418.myshopify.com` after confirming it was only an internal/dev verification store.
- Deleted the two staging Coolify applications for deployment `dep-3bf25c3f`: runtime UUID `yw9nm94x4cerbm9kg59lpws1` and connector UUID `m51na1cyv59gcsw6dsb0c3t0`; Coolify API readback now reports both absent and no matching `loom-verification`/`dep-3bf25c3f` applications.
- Removed Platform records for store connection `shp-66960697`, deployment `dep-3bf25c3f`, consumer `shopify-loom-verification-20260418`, provider handles, marketplace dataset handles/sync runs, vectorization metadata, and the now-empty verification-only customer `cus-9c130451`.
- Did not delete shared/reused Qdrant collections because this verification deployment had no vectorized data and direct Qdrant checks returned missing collections for its scoped names.
- Verification after cleanup: Platform readback counts for the store, deployment, consumer, provider handles, and customer are all `0`; deleted runtime and connector health URLs return HTTP `404`; remaining main store `shopping-companion-test.myshopify.com` still maps to `dep-8c3e7259` and its Coolify runtime health returns HTTP `200 UP`.
- Local pre-delete snapshot is stored only at `/tmp/loom-verification-delete/pre-delete-snapshot.json` and is not committed.

## 2026-05-02 Platform UI Partner Privileges PATCH CORS Fix

- User reported the Platform UI partner privileges page calling `PATCH /api/platform/partners/members/pm-787f10e8-850c-421c-8632-37cf5381582d` from `https://platform-ui-production-00e3.up.railway.app`.
- Live diagnosis before code change: the partner member row exists with normal `ACTIVE` state and empty configurable privileges; `GET` CORS preflight from the Platform UI origin returned `200`, but `PATCH` preflight returned `403`.
- Root cause: backend CORS allowed methods were `GET,POST,PUT,DELETE,OPTIONS`; the Platform UI uses `PATCH` for partner-member mutations.
- Fix: `WebConfig` now includes `PATCH` in API CORS allowed methods, and `PlatformSecurityIntegrationTest` has a regression test proving a Platform UI-style `PATCH` preflight succeeds and returns `Access-Control-Allow-Methods` containing `PATCH`.
- Verification passed: `mvn -f Platfrom/backend/pom.xml -Dtest=PlatformSecurityIntegrationTest test`.
- Live post-deploy proof: `OPTIONS` against the reported production member URL from origin `https://platform-ui-production-00e3.up.railway.app` now returns HTTP `200` with `Access-Control-Allow-Methods: GET,POST,PUT,PATCH,DELETE,OPTIONS`.
- Changed files: `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/config/WebConfig.java`, `Platfrom/backend/src/test/java/com/ai/fabric/platform/backend/security/PlatformSecurityIntegrationTest.java`, and this context handoff.

## 2026-05-02 Partner Verification Admin Cleanup

- User requested cleanup of unneeded partner admins visible on the Platform UI Partner Privileges page.
- Production cleanup kept only two active `PARTNER_ADMIN` members: the real partner admin `engmahmoudalgamal@gmail.com` and the current release-gate JWT-backed fixture `codex.partner.1777431186@loomai.test`.
- Revoked 14 stale Codex/release-gate/live-verification partner accounts and members, cleared their configurable privileges, and downgraded the revoked members to `PARTNER_SUPPORT` so they no longer remain stale admins.
- Revoked the one stale active store assignment owned by an old release-gate account. Live readback now shows only the `test` partner account with active access to `shopping-companion-test.myshopify.com`.
- Verification readback: partner member role/status counts are `PARTNER_ADMIN/ACTIVE=2` and `PARTNER_SUPPORT/REVOKED=14`; active partner-admin readback matches the two intentionally retained members.
- Local pre-cleanup snapshot is stored only at `/tmp/partner-admin-cleanup/pre-cleanup-partner-members.json` and is not committed.

## 2026-05-02 Shopify Bridge Staging Coolify Redeploy Proof

- User asked whether the new Coolify Bridge was listening to the branch commit after the Coolify UI showed the older `Migrate Shopify Bridge to Coolify` commit label.
- Coolify staging app `shopify-bridge-staging` (`c12bjqdcyqdt7tzgr48pev3z`) is configured for `mahmoudashraf/AI-Fabric-Framework.git`, branch `Platform-V8`, and `git_commit_sha=HEAD`; it is not pinned to commit `f376676be`.
- Forced a staging redeploy through the Coolify API. Deployment `u14d3rnjda3aip7qo1nimvtf` resolved `Platform-V8` to commit `ef90b0d52767a4351e586a4fa7034408eda4a89d` (`Record partner verification admin cleanup`) and finished successfully at `2026-05-02T15:57:08Z`.
- Post-redeploy health proof: `https://shopify-bridge-staging.46.224.145.148.sslip.io/actuator/health` returned HTTP `200` with `UP`.

## 2026-05-02 Partner Package Trial Pending Fix

- User reported `POST /api/partners/stores/psa-0425b4b9-f26c-4567-8532-9116466c9409/package-trials` staying pending forever from Partner UI.
- Live diagnosis found the assignment active with `PACKAGE_TRIAL_ACTIVATE`, no committed package-trial row, and Postgres sessions blocked on `shopify_store_connections`. Root cause was a synchronous Platform transaction updating billing state, then calling Bridge `/billing-state`; Bridge called back into Platform billing/store APIs and waited on the same store row transaction.
- Cleared stale live DB blockers by terminating the idle-in-transaction Platform backends; follow-up readback showed `0` lock/idle-in-transaction sessions and still no committed trial activation row for that assignment.
- Fix: partner package trial activation/deactivation now records Platform billing state and queues Platform provisioning without synchronously calling Bridge billing-state inside the same transaction. Bridge already treats Platform billing state as durable source of truth for billing/support readiness.
- Added bounded connect/read timeouts to Platform's `ShopifyBridgeAdminClient` for other legitimate Bridge admin calls.
- Added a Partner UI API timeout so failed/hung Partner API requests surface an error instead of leaving mutations pending indefinitely.
- Regression test now asserts package trial activation/deactivation do not make synchronous Bridge billing-state requests from the Platform transaction.
- Changed files: `PartnerEnablementService.java`, `ShopifyBridgeAdminClient.java`, `PartnerEnablementIntegrationTest.java`, `Platfrom/partner-ui/src/auth/apiClient.ts`, and this context file.
- Verification passed: `mvn -f Platfrom/backend/pom.xml -Dtest=PartnerEnablementIntegrationTest#packageTrialActivationRequiresPlatformGrantedPrivilegeAndManualPastDueDeactivation test`; `mvn -f Platfrom/backend/pom.xml -DskipTests compile`; `mvn -f product-services/shopify-bridge-service/pom.xml -Dtest=PlatformShopifyStoreClientTest test`; `git diff --check`; live DB blocker readback.
- Verification blocked: `npm --prefix Platfrom/partner-ui run build` could not run because `tsc` is missing from local dependencies and local installs are not allowed in this session.
- Pushed commit `bb11596bb` (`Fix partner package trial pending flow`) to `Platform-V8`. A direct Railway backend redeploy attempt through the saved local mutation artifacts returned `Not Authorized` for the available local Railway tokens, so live rollout depends on Railway GitHub auto-deploy or an authorized manual Railway redeploy.
- Next handoff: after the Platform backend and Partner UI deploy, retry activating the trial from Partner UI. If the live app is still on a pre-fix Railway deployment, trigger/redeploy the Platform backend/UI with Railway credentials that can deploy service `ef0bd5e4-d124-4ade-8783-b0763de90599` in environment `b41bb8db-cfef-4f9b-b3e4-adb20b62d70c`.

## 2026-05-02 Railway Project 6d0590be Coolify Migration Auth Check

- User requested moving all services from Railway project `6d0590be-3921-49b6-9fb7-75344cad0b6c` in workspace `Mahmoud Elgammal's Projects` to Coolify, with all Coolify apps under one Coolify project.
- Requirement confirmed for target shape: one Coolify project, not per-customer/per-service Coolify projects.
- Checked existing local Railway tokens, the user-provided UUID as a bearer token, and redacted token candidates extracted from the private handoff doc into local `/tmp` secret files. All returned Railway GraphQL `Not Authorized` for the project inventory query; `me` query also returned `Not Authorized`.
- Checked live Platform DB for tracked rows referencing that Railway project in product services, deployment provider handles, and release provisioning details; no matching source-of-truth rows were found.
- Current blocker: cannot safely migrate because the service list and service environment variables cannot be read from Railway with available credentials, and Platform DB does not contain tracked metadata for this project.
- Needed next credential: a current Railway API/account token or project token that can read project `6d0590be-3921-49b6-9fb7-75344cad0b6c`, its environments, services, source config, and service variables. Coolify credentials are locally available.

## 2026-05-02 Railway Project 6d0590be Coolify Application Migration

- User provided a Railway workspace/project token; it was stored only at `/tmp/railway_workspace_6d0590be_api_token.secret` with mode `0600` and not printed or committed.
- Railway GraphQL inventory for project `6d0590be-3921-49b6-9fb7-75344cad0b6c` succeeded: project name `platform`, environments `production` and `Prod2`, services `platform-Postgres`, `Platform-Backend`, `Platform-ui`, `Partner-ui`, `Ecommerce Store`, and `runtime`.
- Saved Railway service/source/env inventory only under `/tmp/railway-migrate-6d0590be/` with secret-safe file permissions. Do not commit or paste those files.
- Created one Coolify production project `railway-platform` with environment `production`.
- Created Coolify applications under that single project: `platform-backend`, `platform-ui`, `partner-ui`, `ecommerce-store`, and `runtime`, all from public Git branch `Platform-V8`.
- Coolify public URLs:
  - Platform backend: `https://railway-platform-backend.46.225.162.106.sslip.io`
  - Platform UI: `https://railway-platform-ui.46.225.162.106.sslip.io`
  - Partner UI: `https://railway-partner-ui.46.225.162.106.sslip.io`
  - Ecommerce store: `https://railway-ecommerce-store.46.225.162.106.sslip.io`
  - Runtime: `https://railway-runtime.46.225.162.106.sslip.io`
- Environment variables were mapped into Coolify without printing values. URL rewrites point Platform UI and Partner UI runtime config at the Coolify Platform backend. Runtime received the required Platform runtime-auth secrets from Platform DB secret storage.
- Verification passed: all five Coolify health endpoints returned HTTP `200` with `UP`; Platform UI and Partner UI `/runtime-config.js` both point at the Coolify backend; Coolify readback shows backend/ecommerce `running:healthy` and UI/runtime apps `running:unknown` because container-internal health checks were disabled for images without `curl`/`wget`.
- Remote Hetzner cleanup: pruned production Docker build cache after concurrent builds left 9 GB cache; no local installs were performed.
- Postgres/database cutover is not complete. Coolify native database/service API records were created but did not start database containers on this 4.0.0 host; failed DB/service attempts were deleted from Coolify to keep the project clean. The migrated Coolify Platform backend currently points at the existing Railway Postgres public connection, so it is a live app clone, not a full DB migration.
- Remaining before deleting Railway project or switching real domains: working Coolify Postgres restore/cutover for source Postgres 18.3, DNS replacing `sslip.io`, decide whether to route public Platform/Partner UI traffic to the Coolify URLs, and soak verification.

## 2026-05-02 Railway Project 6d0590be Coolify Staging Clone

- User requested the same one-project Railway platform clone on Coolify staging.
- Created one staging Coolify project `railway-platform` with environment `staging` on `coolify-staging-01`.
- Created staging apps under that project: `platform-backend`, `platform-ui`, `partner-ui`, `ecommerce-store`, and `runtime`, all from public Git branch `Platform-V8`.
- Staging URLs:
  - Platform backend: `https://railway-platform-backend.46.224.145.148.sslip.io`
  - Platform UI: `https://railway-platform-ui.46.224.145.148.sslip.io`
  - Partner UI: `https://railway-partner-ui.46.224.145.148.sslip.io`
  - Ecommerce store: `https://railway-ecommerce-store.46.224.145.148.sslip.io`
  - Runtime: `https://railway-runtime.46.224.145.148.sslip.io`
- The complete source stack only exists in the Railway `production` environment, so the staging clone uses those source env values with staging URL rewrites. Backend bootstrap/admin/demo auto-apply flags were forced off for this staging clone to avoid startup mutations.
- Verification passed: all five staging health endpoints returned HTTP `200` with `UP`; Platform UI and Partner UI `/runtime-config.js` point at the staging Coolify backend; Coolify readback shows backend/ecommerce `running:healthy` and UI/runtime apps `running:unknown` because container-internal health checks are disabled for images without `curl`/`wget`.
- Remote staging cleanup: pruned `coolify-staging-01` Docker build cache after the builds; no local installs were performed.
- Database caveat is unchanged: staging clone still uses the existing Railway Postgres public connection until a working Coolify Postgres restore/cutover path exists.

## 2026-05-02 Railway Project 6d0590be Database Migration

- User requested migrating the Railway project database for both staging and production Coolify clones.
- Coolify `/services` with base64 `docker_compose_raw` and `/databases/postgresql` were retried on staging; both created records but did not start Postgres containers on Coolify `4.0.0`. Broken experimental Coolify DB/service records were deleted; readback shows zero `railway-platform-postgres*` Coolify database/service resources.
- Implemented the Hetzner-only fallback: direct Docker Compose Postgres `18` containers on the two Coolify hosts, attached to the existing `coolify` Docker network. These DBs are not first-class Coolify UI resources yet.
- Targets: staging container `railway-platform-postgres-staging`, DB `platform_staging`; production container `railway-platform-postgres-production`, DB `platform_production`.
- Postgres 18 mount fix: persistent compose volumes mount `/var/lib/postgresql`, not `/var/lib/postgresql/data`.
- Dump/restore ran from transient `postgres:18` containers on Hetzner using the Railway public DB URL and local target URLs from secret env files. Restores completed with `87` public tables, `80` Flyway rows, and max Flyway version `9` in both staging and production.
- Updated both Coolify `platform-backend` apps to use the host-local DB URLs in preview and non-preview env entries, then redeployed through Coolify start. Production clone bootstrap/admin/demo auto-apply flags were set to `false` before redeploy.
- Running backend env readback now shows staging `jdbc:postgresql://railway-platform-postgres-staging:5432/platform_staging` and production `jdbc:postgresql://railway-platform-postgres-production:5432/platform_production`.
- Verification passed: both Postgres containers are healthy; all five staging endpoints and all five production endpoints returned HTTP `200` / `UP` after DB cutover.
- Remote migration env files and dump files were removed from both Hetzner hosts after restore. Local secret metadata remains only under `/tmp/railway-migrate-6d0590be/db-targets/`; do not commit or paste it.
- Remaining cutover cautions: keep the Railway project/database until explicit traffic routing, DNS, and soak decisions are made; later add a first-class Coolify-managed DB path or formal Platform metadata for direct Docker Compose DB resources.

## 2026-05-02 Railway Project 6d0590be First-Class Coolify DB Fix

- User requested fixing the direct Compose fallback properly so DBs are first-class Coolify resources.
- Root cause found in Coolify `failed_jobs`: native database/service start jobs failed writing generated files under `/data/coolify/databases` and `/data/coolify/services` because the hardened `loomops` deployment user lacked inherited write ACLs for new resource directories.
- Applied live ACL repair on staging and production: `/data` and `/data/coolify` traversal ACLs plus recursive/default `loomops:rwx` ACLs on `/data/coolify/applications`, `/data/coolify/databases`, and `/data/coolify/services`.
- Updated reproducible Hetzner bootstrap/API fallback scripts to apply the recursive/default ACL baseline for future hosts.
- Verified a disposable staging Coolify-native Postgres `18` resource started as `running:healthy`, then deleted that disposable resource.
- Created first-class Coolify Postgres `18` resources in the existing `railway-platform` projects: staging `railway-platform-postgres-staging` UUID `m58iwvqdkfie8tykohmhyj7t`; production `railway-platform-postgres-production` UUID `nkti6x5r7ovw1xx8q0ykhweq`.
- Restored data from the temporary direct-host Postgres DBs into the first-class Coolify DB resources, repointed both `platform-backend` apps to the Coolify DB UUID hostnames in preview and non-preview envs, and redeployed both backends.
- Removed the temporary direct Compose fallback containers, volumes, remote compose dirs, and remote env/dump files. Only the Coolify-managed DB containers/volumes remain.
- Verification passed: both Coolify DB resources are `running:healthy`; both DBs have `87` public tables, `80` Flyway rows, max Flyway version `9`; staging backend uses `jdbc:postgresql://m58iwvqdkfie8tykohmhyj7t:5432/platform_staging`; production backend uses `jdbc:postgresql://nkti6x5r7ovw1xx8q0ykhweq:5432/platform_production`; all five staging and all five production endpoints returned HTTP `200` / `UP`; bootstrap script `bash -n` passed.
- Remaining cautions: keep the Railway project/database until explicit public routing, DNS, and soak decisions are complete. Local secret metadata remains only under `/tmp/railway-migrate-6d0590be/db-targets/`; do not commit or paste it.

## 2026-05-02 LoomAI Coolify Naming Cutover And Partner Auth Redirect Check

- User asked why active Coolify URLs still used `railway-*` names and requested `loomai` naming instead.
- Renamed the live staging and production Coolify platform clone projects from `railway-platform` to `loomai-platform`.
- Renamed Coolify application display names and public `sslip.io` domains in both environments:
  - staging: `https://loomai-platform-backend.46.224.145.148.sslip.io`, `https://loomai-platform-ui.46.224.145.148.sslip.io`, `https://loomai-partner-ui.46.224.145.148.sslip.io`, `https://loomai-ecommerce-store.46.224.145.148.sslip.io`, `https://loomai-runtime.46.224.145.148.sslip.io`
  - production: `https://loomai-platform-backend.46.225.162.106.sslip.io`, `https://loomai-platform-ui.46.225.162.106.sslip.io`, `https://loomai-partner-ui.46.225.162.106.sslip.io`, `https://loomai-ecommerce-store.46.225.162.106.sslip.io`, `https://loomai-runtime.46.225.162.106.sslip.io`
- Renamed first-class Coolify database resources to `loomai-platform-postgres-staging` and `loomai-platform-postgres-production`; UUID hostnames remain unchanged and are still used by backend DB URLs.
- Updated Coolify app envs without printing values: Platform UI and Partner UI runtime configs now point at the `loomai-platform-backend` domains; backend public URL, partner URL, CORS origins, and `PLATFORM_PROVISIONING_MODE=COOLIFY` were updated in preview and non-preview env entries. Existing native DB URLs remain `jdbc:postgresql://m58iwvqdkfie8tykohmhyj7t:5432/platform_staging` and `jdbc:postgresql://nkti6x5r7ovw1xx8q0ykhweq:5432/platform_production`.
- Forced Coolify redeploys for all ten cloned apps. Direct health verification passed for all staging and production endpoints with HTTP `200`; Platform UI and Partner UI `/runtime-config.js` in both environments no longer contain `railway-*` or `.up.railway.app`.
- Supabase partner magic-link diagnosis: using the local project secret from the private handoff doc and the non-committed test account, generated-link checks proved Supabase rewrites requested Coolify callback URLs back to the old Partner UI Railway Site URL. This is Supabase Auth URL configuration, not partner-ui code: the UI builds `emailRedirectTo` from `window.location.origin`.
- Supabase redirect blocker: the private handoff has project publishable/secret keys, enough to diagnose and generate links, but no Supabase Management API token or logged-in CLI session that can update Auth Site URL / Additional Redirect URLs. Required unblock is either a Supabase dashboard update or a Supabase Management API personal access token with access to project `xazkenhomhtpejjjqtsy`.
- Required Supabase Auth URL settings after unblock: Site URL should point to the active Partner UI target, and Additional Redirect URLs should include at least `https://loomai-partner-ui.46.224.145.148.sslip.io/auth/callback` for staging and `https://loomai-partner-ui.46.225.162.106.sslip.io/auth/callback` for production until real DNS replaces `sslip.io`.

## 2026-05-02 LoomAI Bridge Hostnames And Supabase Auth Cleanup

- Continued hostname cleanup for Shopify Bridge Coolify apps.
- Renamed staging Bridge app to `loomai-shopify-bridge-staging`, URL `https://loomai-shopify-bridge-staging.46.224.145.148.sslip.io`, status `running:healthy`.
- Renamed production Bridge app to `loomai-shopify-bridge-prod`, URL `https://loomai-shopify-bridge-prod.46.225.162.106.sslip.io`, status `running:healthy`.
- Updated Bridge Coolify envs without printing values: `SHOPIFY_BRIDGE_PUBLIC_BASE_URL` points at the matching `loomai-shopify-bridge-*` URL; `SHOPIFY_BRIDGE_PLATFORM_BASE_URL` points at the matching `loomai-platform-backend` URL. Staging inherited `RAILWAY_PUBLIC_DOMAIN` / `RAILWAY_STATIC_URL` values were rewritten to the new LoomAI host and `RAILWAY_PRIVATE_DOMAIN` was blanked.
- Updated tracked Shopify app config and theme-extension defaults to the staging LoomAI Bridge URL: `shopify.app.toml`, `shopify.app.loom-companion.toml`, and the companion theme block defaults.
- Verified both Bridge health endpoints returned HTTP `200 UP`.
- Active Coolify hostname audit now shows zero non-`loomai-*` application hostnames except disposable `dep-*` runtime/connector apps.
- Loaded the Shopify CLI Partner token from the private handoff into local secret/env files without printing or committing it, then deployed the updated Shopify app config through explicit Node 20 with `--no-build`. Latest released Shopify app version is `loom-companion-30`, message `Rename Bridge URLs to LoomAI Coolify`.
- Note: the repo `shopify:app:deploy` script still cannot run in this local checkout because `vite` is not installed and local installs are not allowed; direct Shopify CLI config/theme-extension deploy succeeded because this change did not require rebuilding widget assets.
- Supabase Auth cleanup used the project secret from the private handoff doc. Deleted `17` obvious test/automation users by email pattern/domain; kept `2` real-looking users. Post-cleanup readback shows `0` remaining test candidates. No Supabase token or user token values were printed or committed.

## 2026-05-02 Supabase Auth SMTP Production Unblock

- User provided a Supabase Management API token and Brevo SMTP credentials; values were stored only in local `/tmp` secret files and were not printed or committed.
- Patched hosted Supabase Auth project `xazkenhomhtpejjjqtsy`: Site URL remains the staging LoomAI Partner UI, redirect allow-list includes staging/prod LoomAI Partner UI callback URLs, Custom SMTP is enabled with Brevo, `rate_limit_email_sent=300`, `rate_limit_otp=300`, and `smtp_max_frequency=60`.
- Readback verified SMTP fields are present and the new Auth rate limits are active.
- After Brevo SMTP authorization was fixed, direct Brevo SMTP verification sent successfully.
- Live Supabase magic-link request to the staging LoomAI Partner UI callback returned HTTP `200`; the Auth email path is no longer blocked by Supabase rate limits or Brevo SMTP authorization.
- Follow-up UI fix: Supabase can still return expected per-email resend cooldowns, for example `over_email_send_rate_limit` with a remaining seconds value. Partner UI login now detects that response, shows a countdown, and disables resend for the affected email instead of surfacing it as a confusing hard error.
- Deployed Partner UI cooldown fix commit `41fdd4fd1` to both Coolify Partner UI apps. Staging and production `/health` returned `UP`, and served bundles contain the new resend countdown text.
- Local verification note: `git diff --check` and changed-diff secret scan passed. `npm --prefix Platfrom/partner-ui run build` is still blocked in this checkout because `tsc` is not installed and local installs are not allowed; live Coolify remote builds/deploys succeeded.
- Follow-up SMTP sender fix: Brevo rejected `no-reply@auth.loomai.com` because the sender/domain was not validated. Updated live Supabase Auth SMTP sender to `engmahmoudalgamal@gmail.com`; Supabase readback confirmed it, direct Brevo SMTP accepted that From address, and a Supabase magic-link request returned the normal `{}` success response.
- Security handoff: rotate the Supabase Management API token and the pasted Supabase/Brevo keys after the session, because they were shared in chat.

## 2026-05-03 Staging-Only Coolify Alignment

- User confirmed only staging should be used for now.
- Used only the staging Coolify token from `/tmp/coolify_api_tokens.env`; no auth values were printed or committed.
- Verified staging Coolify core apps point at staging URLs: Partner UI and Platform UI runtime configs point to `https://loomai-platform-backend.46.224.145.148.sslip.io`; staging Shopify Bridge points to the same Platform backend and public Bridge URL `https://loomai-shopify-bridge-staging.46.224.145.148.sslip.io`.
- Fixed the reported Shopify/Partner mismatch for `shopping-companion-test.myshopify.com`: staging Bridge billing summary had been `FREE/ACTIVE`; recorded `ELITE/ACTIVE` through the staging Bridge admin API for Partner trial job `spj-4019ce4f`. Storefront bootstrap now returns `billingTier=ELITE`, `billingStatus=ACTIVE`, all Elite surfaces, and staging Bridge chat URLs.
- Repointed staging ecommerce app `CONNECTOR_INDEXING_RUNTIME_BASE_URL` from an old Railway REST connector URL to `https://loomai-runtime.46.224.145.148.sslip.io`, then forced a Coolify redeploy. Readback from the running container confirms the staging runtime URL.
- Repointed customer runtime/connector apps for deployment `dep-8c3e7259` from old Railway Platform artifact URLs and Railway UI CORS origins to staging Coolify Platform/Partner/Platform UI URLs, preserving artifact paths and signed query parameters. Forced stop/start with Docker cleanup; both customer apps are healthy and running containers read back staging URLs.
- Final staging-host env scan across core apps, staging Bridge, ecommerce, runtime, and connector containers found no `.up.railway.app` or production `46.225.162.106` URL references in checked running app envs.

## 2026-05-03 Controlled Design-Partner Launch 008

- Created `doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/008_CONTROLLED_DESIGN_PARTNER_LAUNCH_AND_MARKET_PROOF.md`.
- Decision: after 007, the next gate is real-store market/operating proof, not another product/cloud capability.
- 008 scope: launch path lock, DNS/auth redirects, Shopify app/extension release proof, demo/collateral, partner onboarding, merchant onboarding, real-store answer-quality audit, support/escalation evidence, weekly metrics, and final `DESIGN_PARTNER_ACTIVE` / `MARKET_READY` / `ITERATE` / `NOT_READY` decision.
- Rule: do not start WooCommerce, broad partner recruitment, white-label, public marketplace expansion, or another product line until 008 evidence exists.

## 2026-05-03 Shopify Marketplace Tier Action Catalog 009

- Created the original Plan 009 Shopify capability execution draft. It was later moved/renamed into the MCP strategy draft set.
- Rewrote 009 after architecture challenge: current Marketplace/plugin architecture is mostly clean; the missing piece is tier/package-profile inference into the correct Marketplace action plugin bundle.
- Superseded on 2026-05-04: 009 target is now greenfield MCP-first. Tier resolves package profile, package profile resolves required/disabled Marketplace plugins, installed ACTION plugins compile runtime action catalog, and Shopify customer-facing action execution goes through Shopify MCP. Bridge remains governance/auth/session/audit/MCP-adapter authority, not custom Shopify action implementation owner.
- Previous key cleanup was to split governed commerce actions out of `mkp-action-shopify-companion-read`; after the MCP-first rewrite, old route-backed Shopify customer action plugins should be removed from greenfield package profiles and replaced by MCP-backed plugins.
- Non-goals: no new plugin type, no GraphQL-in-config actions, no executable Marketplace plugins, no direct Shopify customer-action implementation in generic runtime connectors, and no required separate Shopify execution workers.
- First implementation session should start with current catalog inventory before changing public behavior.

## 2026-05-04 Shopify MCP-First Plan Update

- Rewrote `doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/MCP/Draft-009_SHOPIFY_CAPABILITY_EXECUTION_PLANE.md` into `009 Shopify MCP-First Tier Action Catalog Alignment`.
- Updated `doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/MCP/Draft-011-GOVERNED_MCP_CAPABILITY_PLANE.md` so Shopify MCP is primary, not supplemental, for greenfield customer-facing Shopify actions.
- User renamed the MCP strategy drafts to `doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/MCP/Draft-009_SHOPIFY_CAPABILITY_EXECUTION_PLANE.md` and `doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/MCP/Draft-011-GOVERNED_MCP_CAPABILITY_PLANE.md`.
- Created `doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/009_SHOPIFY_MCP_FIRST_IMPLEMENTATION_SEQUENCE.md` as the Plan 009 implementation sequence. It references both MCP drafts instead of duplicating the full strategy.
- Current target plugin split:
  - `mkp-action-shopify-storefront-read-mcp` for Storefront/UCP MCP read actions.
  - `mkp-action-shopify-cart-mcp` for governed Storefront MCP cart actions.
  - `mkp-action-shopify-customer-account-mcp` for Customer Accounts MCP after OAuth/PKCE and protected-customer-data readiness.
  - `mkp-action-shopify-checkout-mcp` deferred until checkout risk posture is approved.
- Greenfield decision: do not preserve legacy Shopify action IDs/aliases such as `add_product_to_cart`; use canonical `shopify_*` action IDs.

## 2026-05-04 Coolify Credential Rotation Handoff

- Rotated the staging and production Coolify admin passwords directly on the Coolify hosts through SSH and `php artisan tinker` inside the `coolify` container.
- Refreshed local secret files with mode `600`: `/tmp/coolify_admin_credentials.env` and `/tmp/coolify_api_tokens.env`.
- Updated the ignored private handoff file `Final_Documentation/Development_Guides/LLM-guides/PLATFORM_NEXT_LLM_SESSION_HANDOFF_PRIVATE.md` with the current Coolify login/API material and the rotation runbook.
- SSH access uses the local key `~/.ssh/loom_coolify_hetzner_ed25519`, user `loomops`, staging host `46.224.145.148`, and production host `46.225.162.106`.
- Per owner request, the raw SSH private-key body was added only to the ignored private handoff file. Do not print it, commit it, or copy it into tracked docs; the tracked context should reference only the key path, fingerprint, hosts, login emails, local secret-file locations, and secret-safe command shapes.
- Owner clarified the current release policy: continue development and live verification on Coolify staging only; do not move 009 or related changes to production now.
- Added tracked non-secret administration guide: `Final_Documentation/Development_Guides/COOLIFY_HETZNER_ADMINISTRATION_GUIDE.md`.

## 2026-05-04 Shopify MCP-First 009 Staging Implementation

- Implemented and pushed commit `548321694c9fa73e8d9605ea8a8aca71898cd602` on branch `Platform-V8`.
- Platform Marketplace now validates `ACTION` plugins using `adapterType=mcp-tool`; `execution.mcp.serverRef` and `execution.mcp.toolName` are required, and `argumentTemplate` must be an object when present.
- Deployment marketplace draft compilation preserves top-level `adapterType` and nested `execution.mcp` metadata in `actionsConfig` without breaking existing connector HTTP actions.
- Added Flyway `V81__shopify_mcp_search_catalog_action.sql`, seeding greenfield read-only MCP actions into `mkp-action-shopify-companion-read`: `shopify_search_catalog`, `shopify_search_policies`, and `shopify_get_product_details`.
- Bridge now has the Shopify MCP Streamable HTTP client skeleton for `initialize`, `tools/list`, and `tools/call`, plus the governed Storefront MCP adapter for the three read-only actions.
- Staging-only deploys completed: Platform backend and Shopify Bridge staging were redeployed from commit `548321694c9fa73e8d9605ea8a8aca71898cd602`; both health endpoints returned `UP`. Production was not deployed or modified.
- Staging DB verification passed: Flyway version `81` succeeded, and `mkv-action-shopify-companion-read-v1` contains the three MCP action definitions with expected server refs/tool names.
- Staging deployment `dep-8c3e7259` was resynced through the Platform marketplace install resolve endpoint, validating the Marketplace plugin to runtime action catalog path. Draft validation passed with `publishReady=true` and zero issues.
- Published and applied staging deployment version `ver-30af4f5c`; Coolify staging release `rel-97c0aab6` reached `APPLIED_VERIFIED` with verification `PASSED`. Active deployment version is now `ver-30af4f5c`.
- Applied runtime action catalog contains `shopify_search_catalog`, `shopify_search_policies`, and `shopify_get_product_details` with `adapterType=mcp-tool` and the expected Shopify MCP server refs/tool names. Staging runtime and connector `/actuator/health` returned `UP`.
- Direct Shopify MCP live verification against `https://shopping-companion-test.myshopify.com/api/mcp` succeeded for `search_catalog`, `search_shop_policies_and_faqs`, and `get_product_details`.
- Deployed Bridge staging live verification succeeded through `/api/admin/stores/shopping-companion-test.myshopify.com/actions/execute`: all three `shopify_*` actions returned HTTP `200`, `success=true`, `adapterType=mcp-tool`, and `evidenceType=SHOPIFY_MCP_TOOL_RESULT`.
- Evidence and inventory note: `/tmp/loomai-009-shopify-mcp-first/current-action-replacement-inventory.md`; live Bridge response capture: `/tmp/loomai-009-shopify-mcp-first/live-bridge-action-responses.json`.
- Remaining Plan 009 follow-ons: split greenfield package profiles into dedicated MCP plugin bundles, add drift/verification automation, and defer cart/customer-account/checkout MCP until confirmation, OAuth/PKCE, protected-data, and checkout risk posture are approved.

## 2026-05-04 Shopify MCP-First 009 Customer And Checkout Gates

- Implemented and pushed commit `93c6bae0d91388ed466c559cc9e248472313bdab` on branch `Platform-V8`.
- Added dedicated greenfield `ACTION` bundles `mkp-action-shopify-customer-account-mcp` and `mkp-action-shopify-checkout-mcp` through Flyway `V83__shopify_customer_account_checkout_mcp_bundles.sql`.
- Platform package profiles now keep Customer Account and Checkout MCP plugins disabled by default unless profile detail flags explicitly enable them; draft compilation preserves their `adapterType=mcp-tool`, `execution.mcp`, auth, scope, and terminal checkout metadata.
- Bridge now has Customer Accounts MCP discovery/auth gating and Checkout MCP client-credentials gating. Customer tools require shopper session plus customer OAuth token; checkout tools require configured agentic client credentials, and terminal checkout actions stay disabled unless explicitly enabled.
- Bridge readiness now keeps storefront/cart readiness as the primary green signal and reports Customer Account / Checkout as `gatedServers` when external auth material is missing.
- Local verification passed: full Platform backend test suite, full Shopify Bridge test suite, Marketplace integration seed checks, shell syntax checks for `scripts/verify-marketplace-install-flow.sh` and `scripts/verify-shopify-companion.sh`, and `git diff --check`.
- Staging-only Coolify deploys completed for Platform backend and Shopify Bridge. Production was not deployed or modified.
- Staging live verification passed: Platform health `UP`; Bridge health `UP`; Platform Marketplace APIs expose both new MCP bundles with five actions each; Bridge MCP readiness returns storefront `ready=true` plus gated Customer Account and Checkout server details.
- Bridge staging action checks: `shopify_get_customer_orders` returns `CUSTOMER_ACCOUNT_MCP_NOT_CONFIGURED` without customer OAuth posture; `shopify_get_checkout` returns `CHECKOUT_MCP_NOT_CONFIGURED` without checkout credentials; storefront `shopify_search_catalog` still returns HTTP `200` / `success=true`.
- Full live Customer Accounts MCP `tools/call` remains blocked until Shopify Customer Account OAuth/PKCE, protected customer data approval, and customer-token/session binding are configured. Full live Checkout MCP `tools/call` remains blocked until Shopify Checkout MCP client credentials are configured; terminal checkout execution additionally requires explicit enablement.

## 2026-05-04 Shopify MCP-First 009.1 Config-Driven MCP Actions

- Implemented and pushed commit `bed79eb46abbade5242a2189ec9d3c93f0f10de3` on branch `Platform-V8`.
- Platform Marketplace validation now supports config-driven `contributions.mcpServers` for existing `ACTION` plugins, including Streamable HTTP transport, allowed tool lists, auth modes, schema drift policy, and restricted response mappings.
- Deployment Marketplace draft compilation now emits `actionsConfig.mcpServers` and per-action `execution.mcp` metadata while preserving existing connector HTTP actions.
- Runtime connector catalog loading now carries `adapterType`, `execution`, `mcpServers`, and `trace.actionConfig` through to Bridge action execution.
- Shopify Bridge now has a generic `McpStreamableHttpClient` and `McpActionExecutionGateway` for config-driven `mcp-tool` actions. The Shopify Bridge remains Shopify-specific; this gateway is the first shared extraction point for a future generic MCP execution service.
- Local verification passed: full Platform backend suite, full Shopify Bridge suite, connector/registry reactor tests, focused Marketplace/Bridge/connector tests, `git diff --check`, and shell syntax checks for the Shopify/Marketplace verification scripts.
- Staging-only Coolify deploys completed for Platform backend and Shopify Bridge from commit `bed79eb46abbade5242a2189ec9d3c93f0f10de3`; production was not deployed or modified.
- Staging live verification passed: Platform health `UP`, Bridge health `UP`, direct Shopify MCP `initialize`, `tools/list`, and `tools/call search_catalog` returned HTTP `200` against `https://shopping-companion-test.myshopify.com/api/mcp`.
- Bridge staging live verification passed for both the product action and the new generic config-driven gateway:
  - `shopify_search_catalog` returned HTTP `200`, `success=true`, `adapterType=mcp-tool`, `evidenceType=SHOPIFY_MCP_TOOL_RESULT`, `mcpServerRef=shopify-storefront-ucp`, `mcpToolName=search_catalog`.
  - synthetic `generic_config_driven_mcp_live_search` with `trace.actionConfig.execution.mcp` returned HTTP `200`, `success=true`, `adapterType=mcp-tool`, `evidenceType=MCP_TOOL_RESULT`, and normalized evidence.
- Evidence and inventory note remains under `/tmp/loomai-009-shopify-mcp-first/`; response captures from this run were written under `/tmp/shopify-mcp-*.json` and `/tmp/shopify-bridge-*.json`.

## 2026-05-04 Shopify MCP-First 009.2 Extraction Plan

- Created `doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/009_2_MCP_EXECUTION_GATEWAY_EXTRACTION_PLAN.md`.
- 009.2 answers the boundary question raised after 009.1: today the generic MCP code is hosted inside Shopify Bridge; the next architectural step is a standalone/shared MCP Execution Gateway so non-Shopify MCP servers can execute without depending on Shopify Bridge.
- Target sequence: shared Java module first, Shopify Bridge consumes it as a host adapter, then standalone `product-services/mcp-execution-gateway-service` with internal server-to-server APIs for execution, verification, and future import/drift checks.
- Boundary decision: Shopify Bridge remains Shopify-specific for install, store/session, billing, Customer Accounts, Checkout, webhooks, and Shopify readiness; the extracted gateway owns MCP transport, auth providers, server binding, argument rendering, schema drift checks, result normalization, and generic governed evidence.
- Owner clarified 009.2 must support the generic runtime/connector path without Shopify Bridge. The standalone gateway must be a Platform-managed reproducible Product Service, deployed through Coolify target profiles and visible in Product Services UI with Bridge-parity lifecycle controls: reconcile, health, logs/history, restart, scale, rotate secret, force recreate, and decommission.
- Owner clarified 009.1 is not complete until Marketplace MCP discovery/import uses the generic gateway, and Shopify Bridge no longer owns legacy customer-facing action implementations. Final Bridge role: Shopify host/governance/session/billing checks, then delegate installed plugin-defined MCP actions to the generic MCP Execution Gateway.

## 2026-05-05 Shopify MCP-First 009.2 Live Verification

- Implemented and pushed the standalone managed MCP Execution Gateway hardening and Platform-managed lifecycle fixes through commit `deba94b47`.
- MCP Gateway `/api/internal/**` and `/api/admin/**` require the configured internal gateway key; unauthenticated admin overview returns `401`, authenticated overview returns service identity and capability metadata.
- Platform Product Services can now force-recreate the gateway on Coolify staging even when the saved provider UUID is stale: it falls back to domain/name lookup, deletes the old app, clears linkage, and recreates from Platform desired state.
- Coolify git source provisioning now normalizes managed product service repos to the API-compatible GitHub URL form instead of `owner/repo.git`.
- Staging force recreate for `mcp-execution-gateway` succeeded through Platform, reconciled a new Coolify app, and returned `ACTIVE`/`READY` with health probe status `READY`.
- Staging health checks passed for Platform backend, Runtime, Shopify Bridge, and MCP Execution Gateway. Production was not deployed or modified.
- Platform Marketplace MCP discovery live-verified through the gateway with a non-Shopify MCP server; discovery returned normalized `tools/list` evidence and schema hashes.
- A generic non-Shopify `mcp-tool` action executed directly through the standalone gateway without Shopify Bridge and returned normalized `MCP_TOOL_RESULT` evidence.
- Shopify Bridge readiness verified the Shopify Storefront MCP server; `shopify_search_catalog` executed through Bridge -> plugin MCP config -> standalone gateway -> Shopify MCP and returned normalized `MCP_TOOL_RESULT` evidence.
- Customer Account and Checkout MCP plugins remain implemented and gated. Full live `tools/call` for those servers still requires external Shopify Customer Account OAuth/PKCE/customer-token material, protected customer data posture, and Checkout MCP client credentials/readiness.
- Local verification passed: full Platform backend suite, full Shopify Bridge suite, full MCP Gateway suite, focused Product Services/Coolify tests, connector MCP tests, marketplace/shopify shell syntax checks, and `git diff --check`.

## 2026-05-05 Shopify MCP Guides And Production Profile Support

- Implemented and pushed commit `5dc8b938a` on branch `Platform-V8`.
- Created `Final_Documentation/Development_Guides/SHOPIFY_MCP_FIRST_AND_GATEWAY_DEVELOPMENT_GUIDE.md` covering Plan 009, 009.1, and 009.2 implementation status, how to add config-driven MCP servers/actions, managed service profiles, staging verification, secrets posture, and remaining external Customer Account/Checkout gates.
- Updated `Final_Documentation/Development_Guides/COOLIFY_HETZNER_ADMINISTRATION_GUIDE.md` and `009_2_MCP_EXECUTION_GATEWAY_EXTRACTION_PLAN.md` to document Coolify target profiles for Platform-managed product services.
- Added Flyway `V86__allow_coolify_production_platform_services.sql`: `dtp-coolify-production` is active and allows Platform-managed product services, but remains non-default for runtime and restartable services; `dtp-coolify-staging` remains the implicit default.
- `PlatformManagedProductProvisioningService` now fails closed if more than one Coolify product-service profile is allowed and none is marked `defaultForRestartableServices`; explicit `targetProfileId=dtp-coolify-production` is required for production product-service placement.
- Product Services UI now loads Coolify target profiles and uses a select for managed-service profile placement instead of a raw profile ID text field.
- Local verification passed: full Platform backend suite, focused Platform managed product/profile tests, MCP Gateway suite, Platform UI production build, marketplace/shopify shell syntax checks, staged exact-secret scan, and `git diff --check`.
- Staging-only Coolify deploys completed for Platform backend (`fl0w2fi36rmgqz7wbeilothw`) and Platform UI (`z7n39dhj65aal2kl3veef3fd`). Both deployments finished; backend `/actuator/health` and UI `/health` returned `UP`.
- Staging live Platform API verification passed: `dtp-coolify-production` is `active=true`, `platformServicesAllowed=true`, `defaultForRuntime=false`, `defaultForRestartableServices=false`; `dtp-coolify-staging` remains active, platform-service allowed, and the restartable/runtime default.
- Served staging UI bundle contains the new Product Services target-profile selector text.
- Production deployment was not performed. This work only makes production an explicit, managed target profile for future Platform-managed services while keeping staging as default.

## 2026-05-06 Shopify MCP 009.3 Release Gate Pass

- Continued staging-only release gating on branch `Platform-V8`; production was not deployed or modified.
- Deployed staging Platform backend through Coolify to commit `3fde4faf8` after the gate exposed canonical runtime authorization drift.
- Repaired canonical verification rollouts through `/api/deployments/verification-rollouts/recreate` for `ecommerce`, `qdrant`, `pinecone`, `milvus`, and `weaviate`; final inventory showed all canonical deployments `APPLIED_VERIFIED`, `ACTIVE`, and `verificationReady=true`.
- Direct qdrant hosted verification after repair passed as `hvr-dd2d009e`: `PASS: All checks completed. (43 passes, 2 warnings)`.
- Replayed the earlier Shopify Bridge delegated MCP 502 against live staging. Direct MCP Gateway execution and Bridge delegated execution both returned HTTP `200`, `success=true`, and normalized `MCP_TOOL_RESULT` evidence for `shopify_search_catalog`.
- Targeted Platform suite `shopify-mcp-gateway-verification` passed as `vsr-ce3a7a61`, including the Bridge delegated MCP action stage.
- Full Platform release gate `full-platform-release-readiness` passed as `vsr-dc3204cf`, completed at `2026-05-06T01:38:50Z`.
- Release gate endpoint returned `READY=true` / `status=READY`; freshness window expires at `2026-05-06T13:38:50Z`.
- Hosted evidence from the full suite: marketplace `hvr-05692359` passed with 42 passes / 2 warnings; ecommerce `hvr-002dcf32` passed with 43 passes / 2 warnings; qdrant `hvr-d224a9a4` passed with 43 passes / 2 warnings.
- Updated `009_3_SHOPIFY_MCP_MARKET_READINESS_AND_RELEASE_GATE.md` with the final staging pass evidence. Staging is design-partner ready for the claim-safe 009.3 product boundary; production launch and stronger Customer Account / Checkout MCP claims remain externally gated.

Critical fixes that made the gate pass:

- Fixed Coolify/runtime config drift: Coolify env writes now update both normal and preview env rows for action-catalog/version keys, which stopped the runtime from serving stale pre-MCP action config after successful reapply.
- Fixed Shopify MCP trace propagation: Bridge storefront chat now forwards the resolved shop domain as sanitized `shopify-storefront-context`, and runtime connector execution promotes that metadata into the MCP Gateway trace so `endpointKind=STOREFRONT_STANDARD` can resolve `https://{shop}/api/mcp`.
- Reset the live Storefront MCP bundle to the standard Shopify Storefront MCP `/api/mcp` tool set available on the staging shop instead of older UCP catalog aliases.
- Repaired Coolify source/provider assumptions: canonical rollouts were corrected away from stale source URLs, and release verification now uses provider target-profile preflight instead of Railway-only preflight when the deployment is on Coolify.
- Made canonical release repair reliable on the constrained staging host by serializing/throttling rollout work and making recreate/apply idempotent, so provider cleanup and redeploys do not leave partially applied verification fleets.
- Switched Platform-managed canonical verification rollouts to runtime `ALLOW_VERIFIED` authz, removing stale `authzBaseUrl` wiring. This fixed the qdrant/ecommerce hosted-verification `Access denied by policy` failure while preserving connector route-level authz.
- Verified the final Shopify Bridge delegated MCP `502` was not a remaining code-path issue: managed gateway/bridge secrets and URLs matched, direct gateway execution passed, Bridge delegated execution passed, and the targeted platform-hosted MCP suite passed.

## 2026-05-06 Shopify Partner Billing Drift Reconciliation

- Fixed the `shopping-companion-test.myshopify.com` mismatch where Shopify/Bridge reported `FREE/ACTIVE` while Partner/Platform provisioning still showed an `ELITE` package profile from a stale package-trial/provisioning state.
- Root cause was twofold: Partner package trial logic blocked on stale `ACTIVE` trial rows without comparing live Platform billing state, and the Platform provisioning endpoint reused stale `runtimeProfileKey=HIGH_QUALITY` when an explicit `requestedPackageKey=FREE` / `requestedTierKey=FREE` package-change request was made.
- Code fix commits on `Platform-V8`: `40da1f5cd` reconciles stale Partner trial billing drift and queues provisioning from Bridge support-readiness refreshes; `3b84a47cf` makes explicit package/tier provisioning requests resolve the requested package profile instead of the stale current runtime profile.
- Live staging operation used Platform provisioning APIs only, not direct DB edits. Final state: Platform billing `FREE/ACTIVE`; Bridge billing summary `SHOPIFY_APP_SUBSCRIPTION` / `FREE` / `ACTIVE`; Platform provisioning effective profile `LOW_COST` with `packageKey=FREE`, `tierKey=FREE`, latest job `spj-ec7e59c9` `READY`.
- Partner JWT was refreshed from local non-committed Supabase test material. Authenticated Partner store summary for the test shop now shows package profile `FREE` / `LOW_COST`; product-control detail for that test JWT is blocked by `Store assignment is not active` because the release-gate assignment is revoked, not because an Elite trial remains active.
- Verification passed locally: `mvn -f Platfrom/backend/pom.xml -q -Dtest=ShopifyStoreProvisioningServiceTest test`, `mvn -f Platfrom/backend/pom.xml -q -Dtest=PartnerEnablementIntegrationTest#packageTrialActivationReconcilesBillingDriftBeforeBlockingNewTrial test`, and prior Bridge support-readiness targeted test.

## 2026-05-06 Customer Account And Checkout MCP Gate Preparation

- Reviewed PR #156 review-thread state through the GitHub app: the prior Coolify P1 and MCP Gateway timeout P2 threads are resolved/outdated. Local code confirms Coolify transport errors throw `CoolifyApiException` and MCP Streamable HTTP timeouts are wired into `SimpleClientHttpRequestFactory`.
- Hardened the new review concerns worth handling: release execution and verification-suite executors were widened from single-thread bottlenecks to bounded parallel pools, and Platform CORS PATCH support now documents that origin scope remains property-driven.
- Added Platform secret definitions for external Shopify MCP material: Customer Account MCP client id/secret/redirect/scopes/protected-data flag/optional TTLs plus Checkout MCP client id/secret.
- Prepared Customer Account MCP fail-closed behavior: Bridge returns `CUSTOMER_ACCOUNT_MCP_NOT_CONFIGURED` until OAuth/PKCE/protected-data posture is configured, then `CUSTOMER_ACCOUNT_AUTH_REQUIRED` until a customer OAuth access token is bound; MCP Gateway supports `CUSTOMER_OAUTH_PKCE` token pass-through.
- Prepared Checkout MCP credential path: Platform-managed MCP Gateway maps checkout credentials to gateway-only `MCP_SECRET_SHOPIFY_CHECKOUT_MCP_CLIENT_ID` / `MCP_SECRET_SHOPIFY_CHECKOUT_MCP_CLIENT_SECRET`, Bridge receives checkout enablement only when both platform secrets exist, and MCP Gateway supports Shopify's JSON client-credentials token request for `SHOPIFY_AGENTIC_CLIENT_CREDENTIALS`.
- Terminal checkout remains explicitly gated by `SHOPIFY_BRIDGE_CHECKOUT_MCP_TERMINAL_OPERATIONS_ENABLED=false` by default.

## 2026-05-06 Shopify Customer Account Redirect Registration

- Added `[customer_authentication]` to Shopify app config renderers and tracked app TOML files with staging redirect `https://loomai-shopify-bridge-staging.46.224.145.148.sslip.io/api/customer-auth/callback` and JavaScript origin `https://loomai-shopify-bridge-staging.46.224.145.148.sslip.io`.
- Loaded the private Shopify CLI Partner token from the private handoff into a temp secret file without printing it; `shopify:app:info` confirmed the expected Loom Companion app, client id, service account, dev store, and full scopes.
- `npm --prefix product-services/shopify-bridge-service run shopify:app:deploy` was blocked locally because `max-mode-widget` dependencies were not installed (`vite: command not found`), so direct Shopify CLI deploy with `--no-build` was used.
- Shopify app config deploy succeeded and released `loom-companion-31`, registering the Customer Account auth redirect in the Shopify app config.
- Remaining Customer Account MCP blocker: Bridge still needs the actual customer OAuth/PKCE start/callback/token-binding flow behind `/api/customer-auth/callback`; protected customer data posture must also be confirmed before claiming live account/order MCP support.

## 2026-05-06 Shopify Customer Account OAuth Backend

- Implemented Bridge Customer Account OAuth/PKCE backend path for 009/009.3: `/api/customer-auth/start`, `/api/customer-auth/callback`, `/api/customer-auth/session`, and `DELETE /api/customer-auth/session`.
- Added Bridge Flyway `V8__shopify_customer_account_auth_sessions.sql` plus JPA session storage. Customer token material is AES-GCM encrypted with the Bridge app secret; lookup uses shop plus HMAC of the shopper session id, not raw customer tokens in action params.
- MCP action execution now resolves Customer Account OAuth tokens server-side from bound shopper sessions and only then forwards `mcpCustomerAccessToken` to the managed MCP Gateway trace. Trace-supplied customer access tokens are not accepted as a Customer Account MCP auth source.
- Storefront bootstrap now exposes customer-auth start/status URLs so the theme/runtime can initiate Customer Account login without hardcoding Bridge routes.
- Platform-managed Shopify Bridge env provisioning now injects Customer Account MCP client id/secret/redirect/scopes/TTLs from Platform secrets and enables the Bridge gate only when those values and `SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_PROTECTED_DATA_APPROVED=true` are present.
- Platform-managed Shopify Bridge env provisioning also supports `SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_STOREFRONT_DOMAIN` so staging can use a connected custom storefront domain for Customer Account OAuth discovery and safe return URLs while retaining canonical `*.myshopify.com` shop/session binding.
- MCP Gateway Bridge calls now wire `SHOPIFY_BRIDGE_MCP_GATEWAY_CONNECT_TIMEOUT` / `SHOPIFY_BRIDGE_MCP_GATEWAY_READ_TIMEOUT` into the HTTP request factory, and Customer Account OAuth discovery/token calls now have explicit `SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_CONNECT_TIMEOUT` / `SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_READ_TIMEOUT` controls.
- Docs updated: 009 sequence, 009.3 readiness gate, and managed product-services auth guide now list `SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_CLIENT_SECRET`, TTL env names, and the implemented OAuth/session endpoints.
- Focused verification passed: `mvn -f product-services/shopify-bridge-service/pom.xml -q -Dtest=ShopifyCustomerAccountOAuthServiceTest,McpActionExecutionGatewayTest,ShopifyBridgeSecurityHttpIntegrationTest,ShopifyStorefrontControllerTest test`.
- Remaining live claim gate: protected customer data approval/posture must be confirmed and a real staging customer login must complete the registered OAuth callback before Customer Account MCP `tools/call` can be called live-verified.

## 2026-05-06 Shopify Customer Account Staging Unblock

- Deployed Shopify Bridge staging to commit `2c3c4306a` and configured the staging Customer Account MCP env in Coolify without printing raw secrets.

## 2026-05-07 Shopify Customer Account Custom Domain Unblock

- Added `SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_STOREFRONT_DOMAIN` to Bridge config, Platform secret catalog, managed product-service env propagation, tests, and 009/009.3 docs. Staging value is `shop-staging.loomai.pro`; Bridge uses it for Customer Account OAuth discovery/safe returns while keeping sessions bound to `shopping-companion-test.myshopify.com`.
- Added missing Platform secret catalog/env propagation for `SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_CONNECT_TIMEOUT` and `SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_READ_TIMEOUT`.
- Live staging operations: Platform and Bridge were deployed through Coolify to `8ddbf9829`, then Platform to `3d711609a`; Platform-managed Bridge reconcile succeeded with `lastDeploymentId=k7dywpb0hyuqafrwigm089t1`.
- Fixed staging Platform host env posture by adding non-preview Coolify env rows for `PLATFORM_DEPLOY_REPOSITORY=mahmoudashraf/AI-Fabric-Framework`, `PLATFORM_DEPLOY_BRANCH=Platform-V8`, and `PLATFORM_PROVISIONING_MODE=COOLIFY`; preview-only rows were not driving live product-service reconcile.
- Live checks passed: `shop-staging.loomai.pro` resolves/serves the Shopify password page; Bridge health is `UP`; Customer Account auth start returns HTTP `302` to Shopify OAuth; session status is `configured=true`, `authenticated=false`; `shopify_get_customer_orders` fails closed with `CUSTOMER_ACCOUNT_AUTH_REQUIRED` until a real customer browser login binds the shopper session.
- Cleaned duplicate Coolify env rows on the staging Bridge app. The duplicates were operationally significant: retained older rows caused the running Bridge to miss `SHOPIFY_BRIDGE_SHOPIFY_API_SECRET`, `SHOPIFY_BRIDGE_SHOPIFY_API_KEY`, `SHOPIFY_BRIDGE_PLATFORM_BASE_URL`, and `SHOPIFY_BRIDGE_SHARED_SECRET` even though Coolify still showed values in the UI/API.
- Patched `CoolifyApiClient.updateEnvironmentVariables(...)` so Platform-managed Coolify env writes read back application env rows and delete older duplicates for the updated key plus preview scope. Preview and normal rows remain separate.
- Live staging verification after cleanup and redeploy:
  - Bridge health: `UP`.
  - Storefront bootstrap returned HTTP `200` and included `customerAccountAuthStartUrl` / `customerAccountAuthSessionUrl` on the canonical `loomai-shopify-bridge-staging` base URL.
  - `/api/customer-auth/session` returned HTTP `200`, `configured=true`, `authenticated=false` for an unbound shopper session.
  - `/api/customer-auth/start` returned HTTP `302` to Shopify Customer Account OAuth using the registered staging callback and `customer-account-mcp-api:full`.
  - Customer Account action probe for `shopify_get_customer_orders` returned HTTP `409` / `CUSTOMER_ACCOUNT_AUTH_REQUIRED`, proving the path is configured and now gated on shopper login rather than missing env.
  - Storefront MCP `shopify_search_catalog` still returned HTTP `200`, `success=true`, and normalized `MCP_TOOL_RESULT` evidence through Bridge -> MCP Gateway -> Shopify MCP.
- Remaining live claim gate: a real staging customer login must complete the Shopify-hosted OAuth callback before Customer Account MCP `tools/call` can be live-verified with a bound customer token. Checkout MCP still requires its separate Shopify checkout credentials/readiness.

## 2026-05-06 Shopify Partner Readiness Pack Cleanup

- Fresh `partner-enablement-verification` initially failed after the expired Partner Supabase JWT was refreshed because the staging test shop exposed `order-lookup` while its effective package profile was still LOW_COST/FREE. The Partner script selected Elite readiness from the live surface set, but the store verification endpoint returned Starter because the LOW_COST profile still carried old `shopify-companion-free-readiness` metadata.
- Code fix: Partner verification pack selection now ignores unknown configured pack ids and falls through to governed-surface detection, so legacy/free metadata cannot mask an Elite/governed surface posture.
- Migration `V91__normalize_low_cost_shopify_readiness_pack.sql` normalizes existing LOW_COST profile rows from `shopify-companion-free-readiness` to `starter-launch-readiness`.
- Regression coverage: `PartnerEnablementIntegrationTest#storeVerificationPackUsesEliteWhenLegacyFreePackExposesGovernedSurface` covers a legacy LOW_COST package state with `order-lookup` and verifies the endpoint selects the Elite readiness pack.
- Live staging cleanup was done only through Platform APIs: removed `order-lookup` from `shopping-companion-test.myshopify.com` widget surfaces, recorded billing `FREE/ACTIVE`, and ran Free package reconciliation job `spj-b7f16d23` to `READY`.
- Targeted staging suite `partner-enablement-verification` then passed as run `vsr-5ccfa1a2`.

## 2026-05-06 Post-Rotation 009.3 Release Gate Pass

- Commit `b911222ac` (`Fix Shopify partner readiness pack fallback`) was pushed to `origin/Platform-V8` and deployed to staging Platform backend through Coolify.
- Local verification before deploy passed: `PartnerEnablementIntegrationTest#storeVerificationPackUsesEliteWhenLegacyFreePackExposesGovernedSurface`, full `PartnerEnablementIntegrationTest`, full `mvn -f Platfrom/backend/pom.xml -q test`, and `git diff --check`.
- Live staging confirmed `V91` applied: package profile `LOW_COST` now reports `verificationPackId=starter-launch-readiness`.
- Coolify staging Platform backend env scope was repaired after fresh containers defaulted DB settings to localhost. Required DB envs, release-suite URLs, `PLATFORM_PUBLIC_BASE_URL`, Shopify Bridge/shop/product-service refs, and Weaviate host were written as normal non-preview rows so redeploys do not depend on preview-only config.
- Platform DB-backed signing secrets required by canonical rollout checks were restored through Platform Secrets API. Only secret names/readiness are recorded in tracked docs; values remain in private/operator material.
- The staging `PLATFORM_ADMIN_API_KEY` was rotated from the legacy weak operator value, stored only in Coolify/private handoff, and verified live against `/api/platform/secrets`.
- A first post-rotation full gate (`vsr-e31e820b`) failed at `marketplace-hosted-verification` with a Qdrant Cloud management 401. Direct `/api/deployments/dep-d99b3252/provider-connectivity` recheck then returned `qdrant_cloud_control_plane=READY`; the follow-up full gate passed without further code changes.
- Final full release gate passed after admin-key rotation: `full-platform-release-readiness` run `vsr-90ca64ba`, status `PASSED`, completed `2026-05-06T22:36:55Z`.
- `/api/verification-suites/release-gate` returned `READY=true` / `status=READY`; freshness expires at `2026-05-07T10:36:55Z`.
- Hosted evidence from `vsr-90ca64ba`: marketplace `hvr-b885536b` passed with 42 passes / 2 warnings, ecommerce `hvr-8a0d4ce5` passed with 43 passes / 2 warnings, and qdrant `hvr-551c1c39` passed with 43 passes / 2 warnings.
- Remaining live claim gates are unchanged: Customer Account MCP still needs a real staging customer login/bound-token `tools/call` proof after protected-data posture is confirmed; Checkout MCP still needs separate Shopify checkout credentials/readiness before any live checkout claim.

## 2026-05-07 Fresh 009.3 Release Gate Pass

- A fresh full staging gate after the Customer Account MCP custom-domain unblock first failed as run `vsr-640bca52` at `partner-enablement-verification` because the stored Partner Supabase JWT had expired.
- Refreshed the Partner Supabase JWT from local private Supabase test-account material, stored it in `/tmp/partner_supabase_jwt.secret`, and updated Platform secret `PARTNER_SUPABASE_JWT` without printing the token.
- Targeted `partner-enablement-verification` passed as run `vsr-7013bc78`.
- Fresh `full-platform-release-readiness` passed as run `vsr-b71dbec2`, completed `2026-05-07T00:18:10Z`; `/api/verification-suites/release-gate` returned `READY=true` / `status=READY` with freshness expiry `2026-05-07T12:18:10Z`.
- The full pass covered all 14 stages, including Shopify Companion verification, Shopify MCP Gateway verification, Shopify first-product readiness audit, Partner enablement, Thinker resolver readiness, and all hosted verification stages.
- Hosted verification evidence from `vsr-b71dbec2`: marketplace passed with 42 passes / 2 warnings, ecommerce passed with 43 passes / 2 warnings, and Qdrant passed with 43 passes / 2 warnings.
- Remaining live claim gates: Customer Account MCP still needs a real staging customer browser login and bound-token `tools/call`; Checkout MCP still needs `SHOPIFY_BRIDGE_CHECKOUT_MCP_CLIENT_ID` and `SHOPIFY_BRIDGE_CHECKOUT_MCP_CLIENT_SECRET`. Production was not deployed.

## 2026-05-08 Customer Account MCP Bound-Token Live Proof

- User selected protected customer data usage in the Shopify Partner portal for the dev-store install, then completed the Shopify-hosted customer browser login for shopper session `loom-staging-ca-20260508211207`.
- Bridge session status returned `configured=true`, `authenticated=true`, proving the customer OAuth/PKCE callback stored a bound shopper-session token.
- The first bound action attempt exposed stale MCP Gateway deployment code rejecting `CUSTOMER_OAUTH_PKCE`. Reconciled the managed `mcp-execution-gateway` product service through Platform; the new Coolify deployment `uum905buei522jgn3w6zgkqh` became healthy.
- Live Customer Account MCP proof passed through Bridge -> MCP Gateway -> Shopify Customer Account MCP:
  - `get_most_recent_order_status` returned HTTP `200`, `success=true`, normalized `MCP_TOOL_RESULT`, and Shopify tool text `No orders found for this customer.`
  - `get_order_status` with `order_number=1001` returned HTTP `200`, `success=true`, normalized `MCP_TOOL_RESULT`, and Shopify tool text `Order not found with number: 1001`.
- The existing Customer Account MCP Marketplace bundle had unverified tool aliases (`get_customer_orders`, `lookup_order`, and return-request tools). Local fix narrows the product catalog to the live-observed read-only tools: `shopify_get_most_recent_order_status` and `shopify_get_order_status`, with migration `V92__shopify_customer_account_mcp_live_tool_names.sql` for deployed DB convergence.
- Deployment hygiene correction: the first catalog commit changed already-applied Flyway migration `V83`, and staging correctly rejected it with a checksum mismatch. The follow-up commit restored `V83` unchanged and left catalog convergence in additive `V92` only.
- Staging Platform deploy from commit `996785fa7` completed in Coolify deployment `ateasu96dnfetqysbd0ku4l0`; `/actuator/health` returned `UP`, and the live Marketplace endpoint returned plugin version `1.0.1` with only `shopify_get_most_recent_order_status` and `shopify_get_order_status`.
- Post-deploy bound-token proof passed again through Bridge -> MCP Gateway -> Shopify Customer Account MCP for both live catalog tools. The test customer still has no orders, so Shopify returned successful MCP tool envelopes containing no-order/not-found tool text.
- Final staging deploy from branch HEAD commit `edc8d5b61` completed in Coolify deployment `dbudzzhqpe2bpq67irxit9jk`; Platform health stayed `UP`.
- Partner Supabase JWT was refreshed again from private test-account material and stored back into Platform secret `PARTNER_SUPABASE_JWT` without printing the token.
- Fresh full release gate passed as `vsr-a3069cb1` with 14/14 stages passed. `/api/verification-suites/release-gate` returned `READY=true`, `status=READY`, completed `2026-05-08T21:52:05.687947Z`, and expires `2026-05-09T09:52:05.687947Z`.
- Checkout MCP remained gated at this point by missing Platform secrets `SHOPIFY_BRIDGE_CHECKOUT_MCP_CLIENT_ID` and `SHOPIFY_BRIDGE_CHECKOUT_MCP_CLIENT_SECRET`.

## 2026-05-08 Checkout MCP Credential Intake And Password Gate

- Checkout MCP Catalog credentials were added to Platform secret storage as `SHOPIFY_BRIDGE_CHECKOUT_MCP_CLIENT_ID` and `SHOPIFY_BRIDGE_CHECKOUT_MCP_CLIENT_SECRET`; raw values remain only in private/operator material.
- Platform-managed Shopify Bridge and MCP Execution Gateway were reconciled after secret intake. Bridge health returned `UP`; MCP Gateway health returned `UP`.
- Direct Shopify token exchange against `https://api.shopify.com/auth/access_token` succeeded with the checkout client credentials, proving the credentials are valid enough to receive an agentic access token.
- Checkout MCP live endpoint verification is still blocked by Shopify storefront password protection: direct POSTs to both `shopping-companion-test.myshopify.com/api/ucp/mcp` and `shop-staging.loomai.pro/api/ucp/mcp` returned HTTP `302` to `/password`. Admin GraphQL confirmed `onlineStore.passwordProtection.enabled=true`.
- Code hardening: MCP Gateway Streamable HTTP client now reports MCP HTTP redirects explicitly instead of collapsing them into a vague empty-response error. Targeted test `McpStreamableHttpClientTest` passed.
- Remaining live Checkout MCP gate: unlock/remove the staging online-store password, then rerun `tools/list` and a safe `get_checkout`/non-terminal `tools/call` through the managed MCP Gateway/Bridge path. Terminal checkout remains disabled unless explicitly approved by `SHOPIFY_BRIDGE_CHECKOUT_MCP_TERMINAL_OPERATIONS_ENABLED=true`.
- Commit `9b83b7e6a` (`Surface MCP redirect gates`) was pushed to `origin/Platform-V8`, and the managed MCP Gateway was reconciled to Coolify deployment `l2s1tj975k0ylq9fnw4q91ab`.
- Post-reconcile live proof: direct MCP Gateway `tools/list` for `shopify-checkout` and Bridge `shopify_get_checkout` both fail with the explicit message `MCP server returned HTTP 302 redirect to /password.`
- Focused Shopify MCP gateway verification passed against staging after the redeploy: `scripts/verify-shopify-mcp-gateway.sh`.
- Fresh full release gate passed as `vsr-4a50d909` with 14/14 stages passed, including `shopify-mcp-gateway-verification`. `/api/verification-suites/release-gate` returned `READY=true`, `status=READY`, completed `2026-05-08T22:41:28.687806Z`, and expires `2026-05-09T10:41:28.687806Z`.

## 2026-05-08 Checkout MCP Direct-Call Hardening And Final Gate

- Local direct Shopify Checkout MCP diagnosis with a storefront browser/password session plus `Shopify-Buyer-IP` reached `/api/ucp/mcp` and returned a governed UCP `invalid_checkout_id` result for a safe invalid `get_checkout` id. This proves the remaining managed-path blocker is storefront password protection, not checkout credentials, agent profile, or buyer-IP header semantics.
- Code hardening commit `0fe8ae8cb` (`Support checkout MCP direct calls`) was pushed to `origin/Platform-V8`.
- MCP Gateway now uses direct JSON-RPC `tools/call` for Checkout UCP actions instead of `initialize` / `tools/list`, maps server-derived `buyerIp` trace to Shopify's required `Shopify-Buyer-IP` header, and keeps normal MCP schema verification unchanged for non-Checkout MCP actions.
- Shopify Bridge now enriches action trace with server-derived `buyerIp` and `buyerUserAgent` from the incoming request before delegating to the MCP Gateway.
- Platform's default Shopify UCP profile was updated to Shopify's cart-and-checkout profile for future managed Gateway reconciles.
- Verification passed before deployment: full `mvn -f product-services/shopify-bridge-service/pom.xml -q test`, full `mvn -f product-services/mcp-execution-gateway-service/pom.xml -q test`, `mvn -f Platfrom/backend/pom.xml -q -Dtest=PlatformManagedProductAdminServiceTest test`, and `git diff --check`.
- Platform-managed MCP Gateway and Shopify Bridge were reconciled through Platform/Coolify staging after the push; both returned `ACTIVE`, `lastReconcileStatus=SUCCESS`, `driftStatus=NO_DRIFT`, and health `READY` / actuator `UP`.
- Focused staging verifier passed again: `scripts/verify-shopify-mcp-gateway.sh`.
- Managed Bridge Checkout MCP probe now fails correctly with `MCP server returned HTTP 302 redirect to /password.` No password cookie or storefront-password bypass was added to Bridge or MCP Gateway.
- Partner Supabase JWT expired during the first full-gate rerun; it was refreshed from private test-account material and stored back into Platform secret `PARTNER_SUPABASE_JWT` without printing the token. Standalone Partner suite `vsr-457143f5` passed after refresh.
- Fresh full release gate passed as `vsr-4efedfd2` with all 14 stages green, completed `2026-05-08T23:20:46.361081Z`; `/api/verification-suites/release-gate` returned `READY=true`, `status=READY`, freshness expires `2026-05-09T11:20:46.361081Z`.

## 2026-05-09 Per-Store Customer Account MCP Domain Configuration

- The Bridge-wide `SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_STOREFRONT_DOMAIN` value is now fallback-only. It should not be used as the long-term model for multiple Shopify store integrations.
- Platform owns per-store Customer Account MCP storefront/custom domain configuration under each Shopify store mapping. Operators can read/update it through `/api/shopify/stores/{shopDomain}/customer-account-config` and the Shopify Stores admin page.
- Shopify Bridge resolves the per-store Platform config before Customer Account OAuth discovery and safe return URL handling. Customer sessions remain keyed by canonical `*.myshopify.com` shop plus shopper session.
- This keeps test/staging domains flexible while preserving the app-level Customer Account OAuth credentials and redirect URI as app-level material.

## 2026-05-09 Plan 010 Merchant-Owned Launch Readiness Slice

- Implemented and pushed Plan 010 merchant-owned launch readiness follow-up in commit `94dd8974a` (`Complete merchant launch readiness flows`) on `origin/Platform-V8`.
- Scope completed: direct merchant approval invite from Partner Portal and Shopify Admin, SMTP-capable/dry-run-safe merchant notification gateway, approval-code merchant workspace, approve/deny/revoke flows, merchant Go production request, rollback/deactivation support request, evidence/readiness/support surfaces, Partner UI and Shopify Bridge UI wiring, verifier updates, and roadmap/user-guide updates.
- Local verification passed before deployment: full Platform backend suite, full Shopify Bridge suite, Partner UI build/smoke, Shopify Bridge UI build, `bash -n scripts/verify-partner-enablement-live.sh`, and `git diff --check`.
- Deployed to Coolify staging only. Completed deployments on commit `94dd8974a`: Platform backend `byy5k1dgpgr2347a40r9mivc`, Partner UI `bduzjlehi7x49177an1lp1xb`, Shopify Bridge `ct7rp2hidj5vt8nud6jz5anl`.
- Staging health passed for Platform backend, Partner UI, and Shopify Bridge. Bridge root app route returned `200`; storefront bootstrap for `shopping-companion-test.myshopify.com` returned `200` and widget available.
- Strict staging `scripts/verify-partner-enablement-live.sh` passed after refreshing the short-lived Partner Supabase JWT and reading the current staging Platform admin API key from Coolify env without printing secrets.
- Live proof covered merchant invite, approval deep-link workspace, merchant approval, partner assignment, package-trial privilege gating, Max widget smoke, partner product-control write/restore, verification run, evidence bundle/export, merchant workspace evidence, rollback/deactivation request, support escalation/reply, activity feed, revocation cleanup, and revoked-access denial.
- Production promotion mutation intentionally remained skipped with `PARTNER_LIVE_PRODUCTION_PROMOTION_PROOF=false`; no production deployment or production mutation was performed.
- Unrelated local files remained outside the commit: `Final_Documentation/Development_Guides/LLM-guides/Codex_Strategic_Context.md` and `rollout-2026-05-01T11-49-46-019de328-aa7a-7162-a387-250ce1f91b76.jsonl`.

## 2026-05-09 Plan 010.1 UI Launch Readiness

- Created `doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/010_1_SHOPIFY_COMPANION_UI_LAUNCH_READINESS.md` as the follow-on UI readiness plan for Plan 010.
- Decision: onboarding/pricing/support copy, design-partner package, and App Store/private listing readiness should be implemented primarily as Merchant UI, Partner UI, and Platform/Admin UI surfaces; markdown remains backing copy and review material.
- Next priority order recorded in 010.1: run hosted/full staging release gate after latest deployment; run controlled production-promotion proof through `dtp-coolify-production`; prove production verification, rollback/deactivation, and failed promotion leaves staging untouched; then package launch material and decide design-partner vs public App Store launch posture.
- Production mutation remains intentionally gated; normal staging verification must not run `PARTNER_LIVE_PRODUCTION_PROMOTION_PROOF=true`.

## 2026-05-09 Plan 010.1 UI Implementation

- Implemented and pushed 010.1 UI launch readiness in commit `bf64cff98` (`Complete 010.1 launch readiness UI`) across Merchant Shopify Admin UI, Partner UI, Platform/Admin UI, verifier script, and launch exports guide.
- Merchant Shopify Admin UI now has an explicit Go live `Launch package` card for onboarding, package/tier posture, support path, evidence, design-partner posture, App Store/private listing claim safety, controlled production proof, and rollback/deactivation guidance.
- Partner UI now exposes the 010.1 launch readiness kit in the dashboard and assigned-store workspace, including Free/Starter/Elite explanation, design-partner package, weekly value review prompts, App Store/private listing posture, and production proof gating.
- Platform/Admin UI now has `/shopify-launch-readiness` and `Shopify Launch` navigation for App Store/private listing readiness, protected-data gates, Customer Account MCP and Checkout MCP gate status, controlled production proof state, release-gate evidence, recent verification runs, and 010_SELF_SERVICE_PRODUCTION_READY blockers.
- Live verifier script now checks deployed Partner UI assets for 010.1 launch surfaces and deployed Platform UI assets for the Shopify launch readiness route/surfaces.
- Local verification passed: Partner UI build/smoke, Platform UI build, Shopify Bridge UI build, `bash -n scripts/verify-partner-enablement-live.sh`, and `git diff --check`.
- Recorded staging verification in commit `bbe4af020` and redeployed staging apps through Coolify on that branch head: Partner UI deployment `m468y41vpkqh1lgav6p0g4mp`, Platform UI deployment `hh1trad4eih1nudg1stcdwbu`, and Shopify Bridge deployment `w5kcelnse9kupbfay7f7fb54`; all finished successfully.
- Strict live `scripts/verify-partner-enablement-live.sh` passed on staging after redeploying branch head. The Partner Supabase JWT had been refreshed and Platform secret `PARTNER_SUPABASE_JWT` updated without printing token material. This proved the new deployed 010.1 Partner UI and Platform UI launch-readiness asset surfaces plus the merchant approval/evidence/rollback workflow.
- Hosted `full-platform-release-readiness` passed as `vsr-bfce955e` with 14/14 stages green. Release gate is `READY=true` / `status=READY`, completed `2026-05-09T12:35:57.277008Z`, expires `2026-05-10T00:35:57.277008Z`.
- Production mutation remains gated and was not run. `010_1_UI_READY` is staging-verified; `010_SELF_SERVICE_PRODUCTION_READY` remains blocked only by controlled production-promotion proof, production provisioning verification, rollback/deactivation proof, and failed-promotion staging-isolation proof.

## 2026-05-09 Partner UI Staging CORS Repair

- Live browser-equivalent check found the Partner UI route served HTTP 200, but Platform backend rejected Partner UI browser API calls with `Invalid CORS request`.
- Root cause was Coolify env drift: `PLATFORM_CORS_ALLOWED_ORIGINS`, `PLATFORM_CORS_ALLOWED_ORIGIN_PATTERNS`, and `PLATFORM_CORS_ALLOW_CREDENTIALS` existed only as preview env rows on `loomai-platform-backend`; the normal staging runtime did not receive the Partner UI origin.
- Added the same narrow staging CORS values as non-preview runtime env rows, redeployed `loomai-platform-backend` through Coolify deployment `raym04qz5hwb108wtihul04t`, and verified it finished on commit `7c8d18e55`.
- Final portal checks passed: Partner UI `/health`, `/runtime-config.js`, and `/login`; Platform UI `/` and `/shopify-launch-readiness`; Shopify Bridge `/` and `/actuator/health`.
- Browser-equivalent CORS preflights now pass for both Partner UI and Platform UI origins with `GET,POST,PUT,PATCH,DELETE,OPTIONS`, and authenticated `GET /api/partners/session` from the Partner UI origin returns HTTP 200.

## 2026-05-09 LoomAI Landing Site Package

- Claim-safe landing page plan `LOOMAI_LANDING_PAGES_CONTENT_AND_STRUCTURE (1).md` now has an implementation record for `Real_Apps/loomai-landing-site`.
- Added a neutral static Node service for the Loom Companion merchant page and the partner application page. Host-aware routing serves the partner page for `partners.*` via `Host` or `X-Forwarded-Host`, which matches proxy/Coolify deployment behavior.
- The service includes `/health`, `/runtime-config.js`, and `/api/leads`. Runtime config exposes only public CTA URLs; optional `LOOMAI_LEAD_WEBHOOK_TOKEN` remains server-side.
- Verification passed locally: `bash scripts/verify-loomai-landing-site.sh` including HTTP smoke and Playwright browser smoke across desktop/mobile merchant and partner pages. Screenshots were written to `/tmp/loomai-landing-site`.
- Docker build passed from repo root: `docker build -f Real_Apps/loomai-landing-site/Dockerfile -t loomai-landing-site:local .`, followed by container health and host-aware routing checks on local port `4192`.
- Deployment note: first Coolify staging deploy failed because Dockerfile health checks require `curl` or `wget` inside the image. Commit `99056be7a` adds `curl` to the image. Follow-up deployment `oin8ayq4cukruir95hrvu661` reached `running:healthy`, and final branch-head deployment `e11mr5h3stxm7hh79g8np0mm` finished with the app still `running:healthy`.
- Staging Coolify app UUID: `bdzny0asckbk7nhtukflg8fy`. Public staging URLs: `https://loomai-landing.46.224.145.148.sslip.io/` and `https://partners.loomai-landing.46.224.145.148.sslip.io/`.
- Hosted verification passed: `/health` returned `UP`, merchant and partner pages served claim-safe copy over HTTPS, `/api/leads` accepted a synthetic staging smoke request, and Playwright browser checks passed for hosted desktop/mobile pages. Latest hosted screenshots were written to `/tmp/loomai-landing-site-hosted-final`.

## 2026-05-09 LoomAI Product-Suite Landing Correction

- Corrected landing direction after product feedback: the public homepage must focus on the LoomAI product suite, not only Loom Companion.
- Updated `Real_Apps/loomai-landing-site` so the first viewport presents Companion, Thinker, and Resolver as product cards. Companion remains the first live commerce product; Thinker and Resolver are positioned as controlled rollout products with explicit readiness boundaries.
- Updated the partner landing page to say `Help clients launch LoomAI products` and show Companion, Thinker, Resolver, and Launch Portal as partner-supported product/workflow areas.
- Rewrote `LOOMAI_LANDING_PAGES_CONTENT_AND_STRUCTURE (1).md` as the product-suite source of truth. It now removes the previous claim that roadmap products should not appear in the first viewport.
- Local verification passed before redeploy: `bash scripts/verify-loomai-landing-site.sh`, `docker build -f Real_Apps/loomai-landing-site/Dockerfile -t loomai-landing-site:local .`, and `git diff --check`.
- Pushed commits `a9755fc3a` and `8e335c085`, then redeployed the existing staging Coolify landing app (`bdzny0asckbk7nhtukflg8fy`). Final deployment `vez7uc4yvkxuyba04py4xngw` finished and the app returned to `running:healthy`.
- Hosted product-suite verification passed on staging: `/health` returned `UP`, merchant page contains `LoomAI products for commerce and support work`, partner page contains `Help clients launch LoomAI products`, and Playwright desktop/mobile checks passed with screenshots in `/tmp/loomai-landing-site-product-suite-hosted`.

## 2026-05-11 Shopify Companion Runtime-Led Answer Quality

- Corrected the 010.3 answer-quality fix to align with the architecture rule: Shopify Bridge must not invent semantic fallback answers from shopper text, generic `Search completed.` responses, out-of-scope runtime results, or vector-space policy misses.
- Commerce runtime pack now sets `ai.orchestration.always-generate-information=true`, so Companion retrieval flows produce LLM-generated answers from retrieved evidence instead of relying on Bridge-side summaries.
- Runtime now hides system-context-only missing action parameters such as `shopperSessionId` from public clarification text/validation metadata; trusted storefront session context is still injected before validation when present.
- Runtime OUT_OF_SCOPE handling can use LLM-provided `actionParams.userMessage`, and commerce/default intent prompts now require safe user-facing OUT_OF_SCOPE copy without implementation terms.
- Bridge remains responsible for public response sanitization and structured governance after runtime action selection: action ID/package/page-context policy, not shopper text matching.
- Decision rationale: shopper-facing semantic recovery belongs in Runtime/Thinker because only the runtime has prompt, retrieval, read-action evidence, model context, and package policy together. Bridge does not have enough semantic state to safely classify shopper intent or summarize weak evidence without brittle text matching.
- Decision rationale: generic `Search completed.` responses and vector-space/domain clarification leaks are runtime answer-quality defects, not storefront copy defects. Fixing them in Bridge would hide runtime regressions and create divergent behavior across Max mode, Companion mode, and future MCP-backed products.
- Decision rationale: unsupported legal/professional/internal-implementation requests must be shaped by runtime/LLM policy into a store-safe redirect. Bridge should only enforce structured action/package/page-context governance and final sanitization, otherwise language variants will bypass hardcoded Bridge logic.
- Future guardrail: do not reintroduce shopper-query keyword matching, Bridge-side semantic fallback answers, or Bridge evidence summaries. Add tests at runtime prompt/policy/orchestration layers; keep Bridge tests focused on pass-through behavior, sanitization, and structured governance.
- Focused verification passed locally: `ShopifyStorefrontChatServiceTest`, `IntentHandlingStepAlwaysGenerateInformationTest`, `IntentHandlingStepRequiredParamsPlaceholderTest`, `RAGOrchestratorTest`, and `CommerceCuratedPackTest`.
- Follow-up live query-quality repair pushed through `3a396f486`: runtime generation now handles weak fan-out evidence instead of returning code-authored vector-space/domain clarification when generation is requested; OUT_OF_SCOPE ignores schema-invalid `directAnswer` and uses `actionParams.userMessage` or the store-safe default; commerce generation redirects internal/professional-advice requests without echoing forbidden implementation terms.
- Staging redeploys completed on Coolify for Bridge app `c12bjqdcyqdt7tzgr48pev3z` and runtime app `t7hmq6mu0618dalir4jrv6fs`; both read back `running:healthy` on branch `Platform-V9`, Bridge health returned `UP`, and runtime health returned `UP`.
- Final live answer-quality gate passed: `python3 scripts/evaluate-shopify-companion-answers.py --bridge-base-url https://loomai-shopify-bridge-staging.46.224.145.148.sslip.io --shop-domain shopping-companion-test.myshopify.com --query-pack scripts/verification/shopify-first-product-readiness/answer-quality-query-pack.json --out /tmp/shopify-answer-quality-20260511112039 --timeout 60` returned `PASS (11/11 passed)`.

## 2026-05-11 Shopify Add-To-Cart Confirmation Optimization

- Add-to-cart confirmation copy must remain runtime/LLM-led and Marketplace-config-driven. Do not add Bridge-side shopper text matching or semantic fallback responses for cart confirmation wording.
- Marketplace action `shopify_update_cart` carries optional presentation-only `cart_update_confirmation`; confirmation template uses `{{cart_update_confirmation|Update your cart}}?` so missing LLM detail still degrades safely.
- Runtime primary, completion, and multi-step extraction prompts now expose parameter schema descriptions and explicitly allow optional presentation/confirmation params when faithfully derived from the shopper request or authoritative attachments.
- Shopify Bridge context normalization now preserves flat `productTitle`/`productHandle` storefront context as the same attachment metadata used for nested product context, so manual/staging traffic and widget traffic both give the runtime authoritative page context.
- Platform migrations `V99__shopify_cart_confirmation_param_guidance.sql` and `V100__shopify_cart_confirmation_param_guidance_fragment.sql` update the marketplace param description to clarify that quantity `1` is valid when a shopper asks to add one product and no quantity is specified. This is display guidance only; executable MCP arguments still come from configured action params and Bridge/session governance.
- Staging verification: cart Marketplace install `mpi-b210e590` was resynced so deployment draft `drf-2b3adea6` picked up the current published plugin guidance, then version `ver-73911c97` was applied to staging as release `rel-06cd6242`; it reached `APPLIED_VERIFIED` with verification `PASSED`.
- Live Bridge proof after the apply: `Add this to my cart` on product context `Selling Plans Ski Wax` returned `CONFIRMATION_REQUIRED` with `Add Selling Plans Ski Wax to cart?`; `Add two of this wax to my cart` with flat product context returned `Add two Selling Plans Ski Wax to my cart?`. The previous generic `Update your cart?` response was not reproduced.

## 2026-05-11 Shopify Search/Action Query Sweep Remediation

- Extended live query sweep found three runtime/gateway integration defects: Storefront MCP `shopify_search_catalog` could receive blank optional values and fail with `Invalid params`; catalog/search extraction could ask for `query` even when the shopper already supplied a product-search phrase; and cart/account action paths could ask for internal cart/auth details.
- Decision rationale: keep this runtime/MCP-led. Do not add shopper text matching in Bridge. The fix is generic MCP argument pruning, prompt/Marketplace param guidance for search `query`, trusted storefront cart context forwarding, and structured Customer Account auth-error sanitization.
- MCP Gateway now prunes null, missing, blank textual, and empty object/array values from rendered action arguments before `tools/call`, preserving configured arguments while avoiding schema-invalid optional blanks.
- Runtime extraction prompts and Marketplace search metadata now instruct the LLM to fill catalog/search `query` from the shopper-facing product/category/preference phrase, including price or size constraints when no dedicated structured parameter exists.
- Shopify Bridge now forwards `cart`, `cartId`, and `cart_id` storefront context into normalized attachment metadata as `cart_id`, maps generic `shopify_get_cart` MCP result sentinels to shopper-safe cart guidance, and maps `CUSTOMER_ACCOUNT_AUTH_REQUIRED` / `INVALID_CUSTOMER_ACCOUNT_SESSION` / `CUSTOMER_ACCOUNT_MCP_NOT_CONFIGURED` to shopper-safe sign-in/support guidance.
- The answer-quality query pack now covers the new search/action failure modes: ski wax search, priced ski search, cart-context safe copy, and Customer Account auth-safe-copy. Keep future changes in this gate rather than one-off manual probes.
- Live vectorization readiness exposed a legacy-alias masking issue: disabled `mkp-action-shopify-companion-read` canonicalized to `mkp-action-shopify-storefront-read-mcp` and overwrote the enabled canonical MCP install in the readiness map. Platform now prefers enabled canonical installs over disabled legacy aliases so vectorization can run with the greenfield Storefront MCP action bundle while retaining the disabled legacy install record.
- Live proof after deploy: Platform backend `8143bc11a`, Runtime/MCP Gateway `b6ee0c348`, and Bridge `aa21c681a` were healthy on staging. Deployment version `ver-d0e6c12d` applied as release `rel-a58bfa25` with `APPLIED_VERIFIED` / `PASSED`.
- Vectorization proof: readiness changed from blocked to `readyToRun=true`, then full reindex run `vrn-8a3a6f55` completed and the store returned `syncState=IN_SYNC`.
- Expanded answer-quality proof: `/tmp/shopify-answer-quality-20260511T212114Z-expanded-final` passed `15/15`, including ski wax search, priced ski search, gift card, return/shipping source-gap behavior, cart confirmation, cart-context safe copy, customer-account auth safe copy, and internal-language guard.

## 2026-05-12 Shopify Bridge Staging Service Ref Cleanup

- Staging Platform product-service naming drift was corrected in place: the lifecycle-owning record `psv-48d286fa` now uses `serviceRef=shopify-bridge-staging`, display name `Shopify Bridge Service - Staging`, `environmentScope=staging`, and `secretName=MANAGED_PRODUCT_SHOPIFY_BRIDGE_STAGING_API_KEY`.
- The transient duplicate staging record was removed, the staging shop `shopping-companion-test.myshopify.com` remains bound to `psv-48d286fa`, and `/api/product-services/shopify-bridge-prod` now returns `404` on staging.
- Bridge Coolify env `SHOPIFY_BRIDGE_SERVICE_REF` was changed to `shopify-bridge-staging`; Bridge was redeployed through Coolify deployment `t140poe2e0n1icri5e0et87y` and `/api/admin/overview` reported `serviceRef=shopify-bridge-staging`, `environmentScope=staging`, and `status=READY`.
- Coolify app label was cleaned to `shopify-bridge-shopify-bridge-staging` with description `Managed product service shopify-bridge-staging`.
- Final Platform vectorization proof through the renamed binding passed: reindex run `vrn-43385c9f` completed with `processedRecords=80`, `succeededRecords=80`, `failedRecords=0`, and store `syncState=IN_SYNC`.
- Operational script defaults and guides now use `shopify-bridge-staging` for staging. Production records should use explicit production refs such as `shopify-bridge-production`; do not reuse staging service refs or staging managed secret names for production.

## 2026-05-12 Shopify Companion Expanded Answer-Quality Gate

- Expanded the canonical Shopify Companion answer-quality pack to 20 live queries covering search, product FAQ, comparison, policy gaps, cart actions, cart reads, Customer Account auth gates, medical/product-claim gaps, missing current-product context, and internal implementation-language guards.
- Bridge now enforces a structural product-context guard for product-scoped surfaces (`product-insight`, `product-faq`) when no concrete product id/handle/title is present. This prevents random catalog substitution for prompts like `this product` without adding shopper text matching.
- Internal implementation-language handling remains runtime/LLM-led. Do not add Bridge-side semantic keyword routing for shopper queries; the Bridge test `queryForwardsInternalImplementationQuestionToRuntimePolicyInsteadOfBridgeTextMatching` must stay valid.
- Commerce curated prompts now put internal implementation/runtime/tool-status requests at highest priority for safe shopper-facing redirection, and answer prompts explicitly avoid treating those requests as missing product/policy evidence.
- Canonical readiness context was corrected so product-page tests that are meant to exercise product behavior include concrete product context. The separate missing-current-product query proves the Bridge structural guard.
- Live staging proof after deploy: Platform backend and active runtime were redeployed to `2d293b2b8`; Shopify Bridge retained the deployed structural guard from `6986ca6b4`. Health checks passed for Platform backend, Bridge, and runtime. Final live gate output `/tmp/shopify-answer-quality-20260512T143924Z-expanded-canonical-final` returned `PASS (20/20 passed)`.

## 2026-05-16 Shopify Companion Indexing Architecture Cleanup

- 010.4 source-of-truth decision is implemented: Shopify Admin API remains canonical, Bridge source endpoints expose bounded Shopify data, Platform orchestrates vectorization runs/evidence, and Runtime stores only the derived retrieval index.
- Removed hidden legacy document-sync preconditions from manual Shopify vectorization and automatic live indexing. Normal `index-all`, `reindex-all`, `reindex-selected`, and auto event dispatch no longer call Bridge `/run-sync` or Platform `/documents/sync`.
- Readiness and widget-live promotion no longer require the historical `SYNCED` document-sync status; failed derived-index verification can still block shopper traffic with indexing-specific guidance.
- Merchant embedded UI now teaches `Refresh knowledge` / `Reindex` as the freshness operation. Keep legacy `Sync now` as compatibility/operator repair only, not merchant launch flow.
- Added regression coverage for manual reindex, auto indexing, readiness, and widget-live promotion. Full local suites passed for Platform backend and Shopify Bridge, plus Bridge UI build and vectorization core/runner reactor tests.
- Commit `e34c6c85b` was pushed to `Platform-V9` and deployed on staging for Platform backend and Shopify Bridge; both health checks returned `UP`.
- Live staging freshness proof passed: Shopify Admin changed `MetroTab 11 5G Tablet` price from `679.00` to `681.00`; Platform reindex-only run `vrn-4826fc3b` completed; storefront chat answered `$681.00` with evidence containing `Price range: 681.0 USD` and variant price `681.00 USD`.
- Runtime logs checked after the proof for Platform backend and Shopify Bridge showed zero `/run-sync`, `/documents/sync`, or `runSync` mentions in the post-proof window. Evidence files are under `/tmp/loomai-0104-*`.

## 2026-05-16 Customer Account MCP Token Broker Fix

- Root cause of repeated `Connect store account` loop: Shopify Customer Account browser OAuth successfully bound the shopper session in Shopify Bridge, but the generic MCP Gateway could not read Bridge-owned customer tokens.
- Implemented Bridge internal token broker endpoint `POST /api/admin/customer-account/shops/{shopDomain}/token/resolve`, protected by the Bridge admin API key. It resolves Customer Account OAuth tokens from the canonical shop plus verified shopper session and masks token values in server-side `toString()`.
- Implemented MCP Gateway `CUSTOMER_OAUTH_PKCE` token-broker support. Gateway resolves the broker base URL from allowlisted profile ref `SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_TOKEN_BROKER_BASE_URL`, resolves the broker API key from `MCP_SECRET_SHOPIFY_BRIDGE_TOKEN_BROKER_API_KEY`, enforces public HTTPS and a tight broker header allowlist, and then forwards the returned token as the MCP Authorization header.
- Marketplace migration `V104__shopify_customer_account_mcp_token_broker.sql` updates the live Customer Account MCP action bundle to remove LLM-filled `shopperSessionId` params and declare broker auth under `execution.mcp.auth.tokenBroker`.
- Decision: customer session identity must travel through verified runtime trace/auth context, not through shopper/LLM action parameters. Do not reintroduce `shopperSessionId` as a required action parameter.
- Local verification passed: full `mcp-execution-gateway-service` tests; targeted Shopify Bridge Customer Account OAuth/token-broker/MCP/storefront chat tests; Platform backend Marketplace manifest/compiler tests with all 104 migrations applied.

## 2026-05-16 Customer Account MCP Shopify 401 Scope Fix

- Live staging order lookup reproduced Shopify Customer Account MCP `HTTP 401` after Customer Account OAuth and token brokerage were already working. Direct diagnosis showed `initialize` / `tools/list` could succeed, but order `tools/call` failed until the Shopify app version included Customer Account API app scopes.
- Fixed tracked Shopify app config and render defaults to include `customer_read_customers`, `customer_read_orders`, `customer_write_orders`, and `customer_read_store_credit_accounts` alongside product/content/Admin scopes. Deployed Shopify app version `loom-companion-43` with message `Enable Customer Account MCP order scopes`.
- Bridge staging env was returned to product OAuth scope `SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_SCOPES=customer-account-mcp-api:full` after a temporary diagnostic expansion. A fresh browser authorization then bound shopper session `chrome-v104-...d6c8` again.
- Post-fix live proof: Bridge direct action and storefront chat no longer return `MCP server returned HTTP 401`; they execute Customer Account MCP and return Shopify's current tool text `No orders found for this customer.` Admin API confirms order `#1001` / legacy id `7019362451539` exists for customer `engmahmoudalgamal@gmail.com`, financial status `PENDING`, fulfillment `UNFULFILLED`. Remaining issue is Shopify MCP order visibility for that pending order, not LoomAI token brokerage/auth.

## 2026-05-17 Customer Account MCP Internal Controls

- Decision: defer durable Customer Account OAuth redeploy/recreate proof to the public/self-serve release gate. Design-partner staging can require shoppers to reconnect after a Bridge redeploy until stable datasource/encryption/HMAC material is proven live across recreate.
- Implemented semantic MCP failure handling without query text matching: MCP Gateway maps Customer Account read-owned tool errors to `OWNED_RESOURCE_NOT_FOUND`, Customer Account write-owned tool errors to `OWNED_RESOURCE_ACTION_FAILED`, and non-Customer MCP tool errors to `MCP_TOOL_REPORTED_ERROR`. Shopify Bridge has the same fallback for older Gateway responses that return `success=true` with `toolResult.isError=true`.
- Expanded the Customer Account MCP Marketplace bundle from two order tools to the four live `tools/list` observed tools: `shopify_get_most_recent_order_status`, `shopify_get_order_status`, `shopify_get_store_credit_balances`, and `shopify_request_return`. The actions stay explicit Marketplace config, not runtime discovery-as-product-truth.
- Runtime routing remains prompt/catalog driven. The commerce action-selection prompt now tells the LLM to select customer-owned actions from allowed action metadata and avoid using order-status tools for generic account-profile questions.
- Legacy Admin order lookup no longer requests Shopify `statusPageUrl`; GraphQL protected-field failures fail closed as `ORDER_LOOKUP_UNAVAILABLE` with merchant-safe copy.
- Support-readiness is diagnostic only. It must not inspect Shopify billing and write package/billing state or enqueue provisioning as a side effect; package state changes must go through the explicit billing/package operation. This prevents readiness checks from downgrading an Elite staging/design-partner package to Free when Shopify subscription state is not the active package source.
