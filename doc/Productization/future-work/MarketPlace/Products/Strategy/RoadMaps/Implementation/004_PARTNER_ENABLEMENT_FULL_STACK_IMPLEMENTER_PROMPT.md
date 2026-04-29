# Partner Enablement Full-Stack Implementer Prompt

Status: implemented baseline plus historical implementer prompt (revised 2026-04-29)

This file originally carried the first Partner Enablement implementation prompt. The current code has moved beyond that first-slice shape, so use this revision as the truth before reusing any older prompt text below.

## Current Implementation Truth - 2026-04-29

Partner Enablement is implemented as an extraction-ready Platform backend domain under `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/partner/` and a separate Partner UI under `Platfrom/partner-ui`.

Current production contract:

- Supabase proves partner identity; Platform DB owns partner accounts, members, roles, assignments, approvals, implementation requests, audits, support escalations, replies, notes, verification runs, evidence bundles, template applications, and product-control permissions.
- New partner signup creates an empty workspace. Client-store data appears only after merchant approval or operator/admin assignment.
- Production approval is installed-store-first: partner selects an eligible installed Shopify store from Platform-owned store mappings; merchant approves, denies, or revokes inside the connected Shopify/admin surface; Platform stores approval and assignment records.
- The old public shareable approval-link flow remains a compatibility endpoint only and is not the production-default implementation path.
- Partner-created implementation requests always request full product implementation access to the selected installed store. Partners do not choose tier or requested surfaces; those are merchant/store configuration truth.
- Product-scoped partner control is implemented through partner-safe endpoints that delegate reads/writes to canonical Shopify store services. Partner UI must not own duplicate product config buckets.
- Partner UI can run partner-safe verification packs, export evidence, apply templates, add partner notes, open support escalations, and use the live Max widget test for assigned stores.
- Merchant and operator revocation remove active partner access. Operator/admin override is reserved for emergency/recovery.
- The Partner UI API client permits only `/api/partners/*` and `/api/merchant/partner-access/*`; operator/admin endpoints remain out of reach.
- No stubs, placeholders, or dummy implementations are allowed. Gateway contracts must have real adapters or fail closed; tests and live verification must prove the behavior.

Current release-gate wiring:

- `partner-enablement-verification` is a standalone Platform verification suite.
- `full-platform-release-readiness` includes Partner Enablement as a blocking suite stage after Shopify Companion verification.
- The strict live verifier is `scripts/verify-partner-enablement-live.sh` with `PARTNER_LIVE_STRICT=true`, a deployed Partner UI URL, live Platform backend URL, and a fresh non-committed `PARTNER_SUPABASE_JWT`.
- The full release gate can still fail for unrelated provider-rate blockers; treat the standalone Partner Enablement suite as the focused 004 proof.

Current implemented API surface includes:

- `GET /api/partners/session`
- `POST /api/partners/signup/complete`
- `GET /api/partners/stores`
- `GET /api/partners/stores/{storeId}`
- `GET /api/partners/eligible-stores`
- `POST /api/partners/client-implementations`
- `GET /api/partners/client-implementations`
- `GET /api/partners/client-implementations/{requestId}`
- `GET /api/partners/catalog`
- `GET /api/partners/templates`
- `POST /api/partners/templates/{templateId}/applications`
- `GET /api/partners/verification-packs`
- store-scoped verification run, manual step, evidence bundle, note, support, member/profile, product-control, and Max widget routes under `/api/partners/*`
- merchant/admin approval routes under `/api/merchant/partner-access/requests`

## Completion Proof

Latest durable 004 proof before this 005 handoff:

- Partner Enablement foundation, installed-store approval, request visibility, release-gate wiring, product-scoped controls, and Max widget live test were implemented, pushed, deployed, and live verified on Railway.
- Recent strict live verification passed against Platform `https://ai-fabric-framework-production-324f.up.railway.app`, Partner UI `https://ai-fabric-framework-production-158d.up.railway.app`, and `shopping-companion-test.myshopify.com`.
- Package profile approved choices were implemented, pushed, deployed, and live verified at `GET /api/shopify/package-profiles/options`.
- This revision closes the remaining documentation mismatch by making the installed-store-first, merchant-configured tier/surface, canonical store-config source-of-truth model explicit.
- This revision also tightens the partner-facing package summary boundary so partner store payloads expose only merchant-safe package/tier/cost/readiness context and do not leak runtime, vector provider, provisioning, or operator sync fields.

## Historical Prompt

The prompt below is preserved for context. Do not implement it verbatim without applying the current implementation truth above.

---

```text
You are implementing the first full-stack Partner Enablement slice for LoomAI.

Repo root:
`/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo`

Read first:
1. `Final_Documentation/Development_Guides/LLM-guides/CODEX_WORKING_CONTEXT.md`
2. `Final_Documentation/Development_Guides/LLM-guides/Codex_Strategic_Context.md`
3. `doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/004_PARTNER_ENABLEMENT_FOUNDATION.md`
4. `doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/004_PARTNER_ENABLEMENT_UI_DESIGN.md`
5. `doc/Productization/future-work/MarketPlace/Products/Strategy/PLATFORM_UI_PERSONA_SEPARATION_PLAN.md`
6. `doc/Productization/future-work/MarketPlace/Products/Strategy/PLATFORM_UI_REDESIGN_DIRECTION.md`

This is not a UI-only task. Implement the first backend + frontend vertical slice.

Current backend stack:
- Java 21
- Spring Boot 3.2
- Maven
- Spring Web
- Spring Security
- Spring Data JPA
- Flyway migrations
- PostgreSQL in normal environments
- H2 in tests

Current frontend stack:
- React 18
- TypeScript
- Vite
- MUI v6
- Emotion
- `@mui/icons-material`
- React Router
- TanStack Query
- React Hook Form
- Zod
- Supabase JS may be added for partner auth

Do not introduce Tailwind, shadcn, Next.js, Chakra, Ant Design, Redux/Zustand, or a new design framework.

Core product constraints:
- Partner UI lives at `Platfrom/partner-ui`.
- Partner backend domain lives inside `Platfrom/backend`.
- Build the backend as a separate, extraction-ready partner domain module/package first.
- Do not split Partner Enablement into a separate deployable service in this slice.
- Partner signup is self-service through Supabase.
- Signup creates an empty partner workspace.
- Partner cannot see client-store data until merchant approval, approved install/claim flow, or operator assignment.
- Partners own product implementation workflows only.
- Admins/operators own deployment-level controls.
- Never expose deployments, providers, secrets, runtime controls, raw vectorization/replay, Railway, environment variables, or operator diagnostics to partner UI.
- Never call operator endpoints from partner UI.
- Partner APIs must be under `/api/partners/*` or `/api/merchant/partner-access/*`.
- Support escalations are structured cases with governed reply visibility, not loose chat.
- No affiliate/referral/commission/white-label/public partner API/directory/certification scope.

Backend modularity requirement:
Build Partner Enablement inside the existing Platform backend using this package boundary:

`Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/partner/`

Suggested package layout:
- `config/`
- `security/`
- `entity/`
- `repository/`
- `service/`
- `web/`
- `model/`
- `audit/`
- `gateway/`

Boundary rules:
- `partner/web` exposes only `/api/partners/*` and `/api/merchant/partner-access/*`.
- `partner/service` owns partner business rules.
- `partner/entity` owns partner DB tables.
- `partner/security` owns Supabase partner principal mapping and partner authorization.
- `partner/gateway` owns narrow contracts to existing Platform/Shopify capabilities.
- Existing deployment/provider/secret/vectorization code must not leak into partner UI or partner APIs.
- Partner code may call existing platform/shopify services only through narrow gateway interfaces.

Create real gateway contracts/adapters where needed. Do not create stubs, dummy adapters, or placeholder behavior:
- `PartnerStoreAccessGateway`
- `PartnerShopifyStoreReadModel`
- `PartnerEvidenceSource`
- `PartnerAuditPublisher`
- `PartnerNotificationGateway`
- `PartnerCatalogSource`
- `PartnerVerificationSource`

Gateway intent:
- partner domain can read partner-safe Shopify/store readiness summaries without owning Shopify tokens
- partner domain can request product-safe verification/evidence operations without exposing raw runtime or vectorization controls
- partner domain can publish audit/notification events without depending on UI/operator internals
- partner domain can later be extracted by replacing gateway implementations with HTTP/message clients

Backend first-slice scope:
1. Add partner domain model with Flyway migration.
   Inspect existing migrations and use the next sequential version.
   Minimum tables/entities:
   - `PartnerAccount`
   - `PartnerMember`
   - `PartnerRole`
   - `PartnerClientImplementationRequest`
   - `PartnerStoreAccessRequest`
   - `PartnerStoreAccessApproval`
   - `PartnerStoreAssignment`
   - `PartnerActionAudit`
   - `PartnerSupportEscalation`
   - `PartnerSupportReply`
   - `PartnerEvidenceBundle` metadata shell

2. Add repository/service/controller layers under the partner package.

3. Add Supabase auth boundary:
   - add config properties:
     - `platform.auth.supabase.enabled`
     - `platform.auth.supabase.issuer`
     - `platform.auth.supabase.jwks-uri`
     - `platform.auth.supabase.audience`
     - `platform.auth.supabase.project-ref`
     - `platform.auth.supabase.require-email-verified`
   - prefer Spring Security OAuth2 Resource Server if practical
   - keep existing operator/session/API-key auth working
   - Supabase identity proves who the user is; Platform DB decides authorization

4. Implement partner-safe APIs:
   - `GET /api/partners/session`
   - `POST /api/partners/signup/complete`
   - `GET /api/partners/stores`
   - `POST /api/partners/client-implementations`
   - `GET /api/partners/client-implementations/{requestId}`
   - `POST /api/partners/client-implementations/{requestId}/store-access-links` (compatibility only; production flow is installed-store-first merchant approval)
   - `POST /api/merchant/partner-access/{approvalCode}/approve`
   - `GET /api/partners/catalog`
   - `GET /api/partners/support/escalations`
   - `POST /api/partners/stores/{storeId}/escalations`
   - `GET /api/partners/escalations/{escalationId}/thread`
   - `POST /api/partners/escalations/{escalationId}/replies`

5. Enforce authorization:
   - new Supabase partner can create/read own empty workspace
   - unassigned partner cannot access store data
   - partner can access only approved/assigned stores
   - partner can create escalation only for approved/assigned store
   - partner can read only `PARTNER_VISIBLE` replies
   - operator-only notes are never returned by partner APIs

6. Add focused backend tests:
   - self-service partner signup creates empty account/workspace
   - Supabase-authenticated partner session returns no stores before assignment
   - unassigned partner store access denied
   - approved assignment allows store-scoped access
   - partner escalation cannot be created for unassigned store
   - partner-visible reply is returned
   - operator-internal reply/note is not returned
   - existing operator auth still works

Frontend first-slice scope:
1. Scaffold `Platfrom/partner-ui`.
2. Add Vite React TypeScript setup consistent with `Platfrom/ui`.
3. Add MUI theme factory from `004_PARTNER_ENABLEMENT_UI_DESIGN.md`.
4. Add Supabase client/provider:
   - Vite env vars for Supabase URL and anon key
   - graceful missing-config state
   - `/login`
   - `/auth/callback`
   - `RequireAuth`
5. Add API client:
   - inject Supabase bearer token
   - allow only `/api/partners/*` and `/api/merchant/partner-access/*`
   - reject operator/admin endpoint paths client-side
   - validate responses with Zod
6. Add AppShell:
   - left rail
   - top bar
   - light/dark support
   - no deployment/operator nav
7. Implement first real pages against backend APIs:
   - Dashboard / Empty Partner Workspace using `GET /api/partners/session`
   - Client Stores using `GET /api/partners/stores`
   - New Implementation using `POST /api/partners/client-implementations`
   - Approval status/detail page using implementation request endpoint
   - Intelligence Catalog using backend catalog endpoint, even if backend starts with static Starter-safe catalog records
   - Support list + escalation detail/thread foundation
8. Add core components:
   - `StatusChip`
   - `PageHeader`
   - `DataTable`
   - `EmptyState`
   - `DetailDrawer`
   - `ConfirmDialog`
   - `TierBadge`
   - `VisuallyHidden`
   - `EscalationThread`
   - `EvidenceAttachment`

Catalog/tier truth:
- Free = AI search only.
- Starter = read-only embedded intelligence surfaces.
- Starter excludes order lookup.
- Elite-only surfaces must be gated/deferred.
- Do not present governed actions as currently partner-deployable Starter work.

Data ownership:
- Supabase stores identity/session only.
- Platform backend DB owns partner state, assignments, verification, escalations, reply threads, and evidence metadata.
- Shopify Bridge/product services own Shopify truth, billing, Knowledge Sync, verification evidence, tokens, and secrets.
- Partner UI must never expose tokens, secrets, or raw internals.

Verification:
Run:
- `git diff --check`
- `mvn -f Platfrom/backend/pom.xml -q test`
- `npm --prefix Platfrom/partner-ui run build`

If you add focused backend tests, run them explicitly too.

Platform live release gate:
- Do not consider this slice release-ready from local tests alone.
- If partner auth, partner APIs, store assignment/access, evidence bundles, verification packs, escalation visibility, deployed partner UI routing, or release-verification behavior changes, live verification must be part of the Platform release gate.
- Extend an existing Platform-owned live verification path or add a focused partner live verifier before marking the release complete.
- Minimum live gate coverage:
  - deployed Platform backend health and `/api/partners/session` reachability
  - deployed Partner UI route/artifact reachable
  - valid Supabase partner JWT accepted and invalid JWT rejected
  - new partner sees empty workspace and no client stores
  - unassigned partner cannot access client-store data
  - approved/assigned partner can access only assigned partner-safe store summary
  - revoked/suspended partner denied
  - catalog returns Free AI-search-only, Starter read-only surfaces, and no Starter order lookup
  - verification pack output includes Free AI-search-only and Starter no-order-lookup checks
  - evidence export excludes secrets, raw vectorization/runtime/provider data, and operator-only notes
  - escalation create/read works only for approved/assigned stores
  - operator-only replies/notes are not returned to partner UI
  - partner state-changing action audit records exist
  - existing operator/admin auth and platform release verification still pass
- Live verification may be skipped only for docs-only work or if no deployed behavior/API/UI/auth/evidence/release-gate contract changed. Record the exact skip reason in `CODEX_WORKING_CONTEXT.md`.

Working context:
Append compact status to:
`Final_Documentation/Development_Guides/LLM-guides/CODEX_WORKING_CONTEXT.md`

Include:
- changed files
- decisions
- verification
- blockers
- next handoff

Commit and push:
- Stage only relevant files.
- Do not touch unrelated `.DS_Store`.
- Commit with a concise message.
- Push to current branch.

Expected final response:
- backend implemented
- frontend implemented
- verification commands/results
- commit SHA pushed
- blockers or next slice
```
