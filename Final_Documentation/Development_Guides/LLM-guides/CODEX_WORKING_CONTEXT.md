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

## 2026-04-25 Partner Enablement Supabase And Deployment Unblock

- Used ignored private handoff section `7.5 Subabse Partner UI` as the Supabase credential source; extracted runtime/admin values into `/tmp/partner_supabase.env` without committing secrets.
- Created a confirmed non-social Supabase email/password test account; JWT and account details are stored only in `/tmp/partner_supabase_jwt.secret` and `/tmp/partner_supabase_test_account.env`.
- JWT claims match the expected Supabase issuer/audience/email provider, but this project token lacks a top-level `email_verified` claim; first email-only Platform test should set `PLATFORM_SUPABASE_REQUIRE_EMAIL_VERIFIED=false`.
- Production valid-JWT live check still returns `401` because the deployed Platform backend does not include partner routes; public merchant approval probe also returned `401`. Production deploy branch is `Platform_V1`, while the Partner Enablement slice is on `Platform-V6`; do not blindly fast-forward production because the branch diff is very large.
- Added `Final_Documentation/Development_Guides/PARTNER_ENABLEMENT_DEPLOYMENT_GUIDE.md` plus Partner UI runtime config and Railway deployment assets for `partners.loomai.pro`.
- Temporary Partner UI Railway URL `https://ai-fabric-framework-production-158d.up.railway.app` is reachable: `/health` and `/runtime-config.js` return HTTP `200`, and `/` serves the app. Runtime config currently returns empty values, so set `PARTNER_UI_PLATFORM_API_BASE_URL`, `PARTNER_UI_SUPABASE_URL`, and `PARTNER_UI_SUPABASE_ANON_KEY` on the Railway service and redeploy before UI login proof.
- After Railway env values were added, temporary Partner UI runtime config is populated and points at `https://ai-fabric-framework-production-324f.up.railway.app`; live verifier now passes backend health, unauth/invalid JWT rejection, UI health, UI runtime config, and UI route. Remaining failure is backend authenticated partner access: valid Supabase JWT returns HTTP `401`, and public merchant approval route also returns HTTP `401`, so the deployed Platform backend still needs the partner-enabled branch/env.
- Platform diagnostics later confirmed the backend is on `Platform-V6`; public merchant approval with a JSON body returned HTTP `400` for a fake code, proving partner routes are deployed. Remaining `GET /api/partners/session` HTTP `401` is partner JWT auth config/defaults. Patched backend defaults to enable the launch Supabase issuer/JWKS, default email verification gate off for email-only test, include the temporary Railway Partner UI in CORS, and accept `user_metadata.email_verified` from Supabase email tokens.
