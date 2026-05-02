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
- Clarified indexing boundary: Shopify Bridge uses platform vectorization; merchant UI should say Knowledge Sync, while raw vectorization/index/reindex/replay controls belong to partner/operator.
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
