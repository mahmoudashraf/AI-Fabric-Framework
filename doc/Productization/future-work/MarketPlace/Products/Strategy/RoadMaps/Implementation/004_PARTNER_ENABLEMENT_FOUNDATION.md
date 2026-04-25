# Partner Enablement Foundation

Status: comprehensive implementation handoff (revised 2026-04-25)

Owner mode: technical LLM implementation session

Roadmap phase: Phase 3 - Partner Enablement Foundation

Priority: P0/P1

Depends on:

- [001_SHOPIFY_COMPANION_LAUNCH_TRUTH_ENFORCEMENT.md](001_SHOPIFY_COMPANION_LAUNCH_TRUTH_ENFORCEMENT.md)
- [002_SHOPIFY_COMPANION_STOREFRONT_PRODUCT_SHELL.md](002_SHOPIFY_COMPANION_STOREFRONT_PRODUCT_SHELL.md)
- [003_SHOPIFY_COMPANION_STARTER_LAUNCH_PACKAGE.md](003_SHOPIFY_COMPANION_STARTER_LAUNCH_PACKAGE.md)

---

## Strategic Handover

The first three Shopify Companion implementation phases are complete and verified.

Accepted state:

- Launch Truth Enforcement is complete.
- Storefront Product Shell is complete.
- Starter Launch Package is complete.
- current tiers are `Free / Starter / Elite`.
- Free is AI search only.
- Starter is full read-only embedded store intelligence.
- Starter excludes order lookup and governed actions.
- Elite is the only tier for verified governed actions.
- Shopify Companion is positioned as embedded store intelligence, not a chatbot.
- chat and Max Mode are the depth layer.
- `Knowledge Sync` is merchant-facing language.
- raw vectorization, provider, queue, replay, runtime, and debug language is operator-only.

This Phase 3 handoff starts Partner Enablement Foundation.

The partner strategy is implementation support for developers, integrators, agencies, and consultants who help client stores or current apps add LoomAI intelligence pieces. Self-service partner signup is in scope, but signup creates an empty partner workspace by default. Client-store access requires merchant approval, an approved install/claim flow, or explicit operator assignment. This is not an affiliate program, referral dashboard, passive acquisition surface, commission workflow, or white-label program.

The implementation target is now a mature partner enablement operating layer, not only a founding-partner document kit. The platform already has one working Shopify store and verified intelligent embedded surfaces, so partner enablement should be designed as a complete product capability from the start, shipped in controlled increments.

The first usable release can still start with founding partners, but the architecture and handoff must cover the complete path: partner access, client-store portfolio, intelligence catalog, client store workspace, setup and verification packs, support escalation, evidence exports, templates, auditing, and operator override. The goal is to make a serious implementation partner able to deploy and support multiple client stores without full operator access or live explanation every time.

Canonical partner offer:

- add LoomAI-powered intelligence surfaces to client stores and current apps without building the AI infrastructure from scratch
- start with Shopify Companion and the verified Starter surface catalog
- keep partner work bounded to setup, verification, support handoff, evidence, and escalation
- keep platform operator internals out of partner and merchant surfaces

Why this goes next:

- Starter is now sellable and verified enough to be used as the first partner-facing package.
- Partner enablement gives the solo developer leverage without creating a manual partner-management team.
- The intelligence catalog, setup checklist, verification pack, and escalation template will also strengthen design-partner and launch workflows.
- Building partner materials now prevents future partner sessions from inventing product claims, tier rules, or support promises.
- A mature platform needs the partner operating layer before broad market activity, otherwise every implementation becomes bespoke support from the founder.

---

## Read First

Read these before editing code or docs:

1. [CODEX_WORKING_CONTEXT.md](../../../../../../../../Final_Documentation/Development_Guides/LLM-guides/CODEX_WORKING_CONTEXT.md)
2. [Codex_Strategic_Context.md](../../../../../../../../Final_Documentation/Development_Guides/LLM-guides/Codex_Strategic_Context.md)
3. [001_SHOPIFY_COMPANION_LAUNCH_TRUTH_ENFORCEMENT.md](001_SHOPIFY_COMPANION_LAUNCH_TRUTH_ENFORCEMENT.md)
4. [002_SHOPIFY_COMPANION_STOREFRONT_PRODUCT_SHELL.md](002_SHOPIFY_COMPANION_STOREFRONT_PRODUCT_SHELL.md)
5. [003_SHOPIFY_COMPANION_STARTER_LAUNCH_PACKAGE.md](003_SHOPIFY_COMPANION_STARTER_LAUNCH_PACKAGE.md)
6. [SHOPIFY_COMPANION_LAUNCH_TRUTH.md](../SHOPIFY_COMPANION_LAUNCH_TRUTH.md)
7. [SHOPIFY_COMPANION_STARTER_LAUNCH_PACKAGE_ROADMAP.md](../SHOPIFY_COMPANION_STARTER_LAUNCH_PACKAGE_ROADMAP.md)
8. [SHOPIFY_COMPANION_FINDINGS_ROADMAP.md](../SHOPIFY_COMPANION_FINDINGS_ROADMAP.md)
9. [RoadMaps_BackLog.md](../RoadMaps_BackLog.md)
10. [PARTNER_DASHBOARD_STRATEGY_PLAN.md](../../PARTNER_DASHBOARD_STRATEGY_PLAN.md)

Supporting persona and UI context:

- [PLATFORM_UI_PERSONA_SEPARATION_PLAN.md](../../PLATFORM_UI_PERSONA_SEPARATION_PLAN.md)
- [PLATFORM_UI_REDESIGN_DIRECTION.md](../../PLATFORM_UI_REDESIGN_DIRECTION.md)

Useful existing Shopify Companion docs:

- [SHOPIFY_COMPANION_MERCHANT_LAUNCH_AND_SUPPORT_GUIDE.md](../../../../../../../../Final_Documentation/User_Guides/SHOPIFY_COMPANION_MERCHANT_LAUNCH_AND_SUPPORT_GUIDE.md)
- [SHOPIFY_COMPANION_CUSTOMER_CAPABILITIES_GUIDE.md](../../../../../../../../Final_Documentation/User_Guides/SHOPIFY_COMPANION_CUSTOMER_CAPABILITIES_GUIDE.md)
- [SHOPIFY_COMPANION_DEVELOPER_AND_STORE_ADMIN_GUIDE.md](../../../../../../../../Final_Documentation/Development_Guides/SHOPIFY_COMPANION_DEVELOPER_AND_STORE_ADMIN_GUIDE.md)
- [SHOPIFY_COMPANION_LAUNCH_REVIEW_AND_SUPPORT_EXPORTS_GUIDE.md](../../../../../../../../Final_Documentation/Development_Guides/SHOPIFY_COMPANION_LAUNCH_REVIEW_AND_SUPPORT_EXPORTS_GUIDE.md)
- [SHOPIFY_INTERNAL_DEVELOPMENT_AND_FULL_DEPLOYMENT_GUIDE.md](../../../../../../../../Final_Documentation/Development_Guides/SHOPIFY_INTERNAL_DEVELOPMENT_AND_FULL_DEPLOYMENT_GUIDE.md)

---

## Working Rule

The technical LLM session must keep this file updated:

- [CODEX_WORKING_CONTEXT.md](../../../../../../../../Final_Documentation/Development_Guides/LLM-guides/CODEX_WORKING_CONTEXT.md)

Append compact notes for:

- decisions made during implementation
- files changed
- tests run
- blockers
- skipped checks
- handoff state

Do not paste long logs, diffs, secrets, or noisy reasoning into the working context.

Use this compact template:

```text
- Partner Enablement Foundation status: <complete/partial/blocked>.
- Changed files: <compact list>.
- Decisions: <only new decisions>.
- Verification: <commands run and pass/fail>.
- Live verification: <passed/skipped/blocker/not needed>.
- Blockers: <none or compact blockers>.
- Next handoff: <next concrete step>.
```

---

## Implementation Brief

Task:

- implement the complete Partner Enablement Foundation for implementation partners

Primary outcome:

- implementation partners can understand the Shopify Companion Starter package, deploy verified LoomAI intelligence surfaces to approved/assigned client/test stores, verify each surface, monitor health, support normal setup issues, and escalate with evidence without full operator access or live platform-operator explanation

Authority boundary:

- admins/operators own the deployment level
- partners/integrators own the product implementation level
- merchants own their store-level configuration and approvals

This handoff should be treated as a mature platform implementation plan, delivered incrementally. Do not stop at a documentation kit if code-level partner capabilities are feasible. Build the self-managed implementation-partner operating system first: partners can sign up and work from an empty workspace, while client-store access remains approved, scoped, revocable, and audited.

Complete product capabilities:

- Supabase Auth login and social login for partner users
- self-service partner signup
- partner identity and scoped access
- partner-member roles
- client implementation request flow
- merchant-approved store access flow
- partner-store assignment and revocation
- partner home and client-store portfolio
- client store workspace
- intelligence-piece catalog
- sandbox/demo center
- setup/deployment checklist
- verification and launch center
- evidence/export packet generation
- support and escalation center
- vertical templates and implementation playbooks
- partner action audit trail
- operator override and revocation
- merchant-safe boundary

Auth decision:

- use Supabase Auth for partner login and identity
- support Google, Apple, and LinkedIn OIDC social login
- use the Platform backend as the source of truth for authorization, partner roles, store assignment, audit, and Shopify/Bridge access
- do not use Shopify credentials, Shopify collaborator access, or provider OAuth tokens as partner platform authorization
- do not make Supabase metadata the only source of partner permissions

Official Supabase auth references:

- Supabase Auth overview: `https://supabase.com/docs/guides/auth`
- Supabase Social Login: `https://supabase.com/docs/guides/auth/social-login`
- Google provider: `https://supabase.com/docs/guides/auth/social-login/auth-google`
- Apple provider: `https://supabase.com/docs/guides/auth/social-login/auth-apple`
- LinkedIn OIDC provider: `https://supabase.com/docs/guides/auth/social-login/auth-linkedin`

Initial intelligence catalog:

- AI search
- product insight block
- product FAQ
- comparison
- policy strip
- contextual pill
- read-only chat/depth layer

Catalog rules:

- Free = AI search only.
- Starter = all read-only embedded intelligence surfaces.
- Starter excludes order lookup.
- Elite-only surfaces must be marked later/gated/verified-only.
- Do not present governed actions as current partner-deployable Starter work.

Vertical playbooks to start with:

- fashion/apparel: sizing, reviews, product fit, policies
- electronics: comparison-heavy buying, specs, compatibility
- health/beauty: ingredient/use-case questions, policy clarity

Optional fourth playbook if cheap:

- home/furniture: dimensions, materials, delivery/return policy context

Do not:

- build affiliate/referral/commission workflows
- grant client-store access from signup alone
- build partner directory
- build certification
- build white-label packaging
- build public partner API
- build broad custom product assembly
- expose secrets, tokens, provider credentials, deployment internals, raw vectorization controls, runtime controls, or queue/replay internals to partners
- give partners deployment-level controls
- push partner/operator packet content back into the merchant Shopify admin as long inline text
- start broad platform UI redesign as a prerequisite
- start WooCommerce or second-product work
- loosen Free/Starter/Elite tier truth
- add order lookup to Free or Starter

---

## Supabase Auth Architecture

### Identity Boundary

Supabase owns:

- user sign-in
- OAuth redirects
- social identity providers
- user session tokens
- token refresh on the partner UI
- provider account linking where supported

Platform backend owns:

- partner account
- partner member profile
- partner role
- self-service signup state
- partner invitation state for operator-created/internal cases
- partner-store assignment
- partner permissions
- partner action audit
- client implementation requests
- merchant approval links/codes
- verification run records
- escalation records
- escalation reply/thread records
- partner-visible and operator-only support notes
- evidence bundle metadata
- escalation ownership
- operator override/revocation
- all authorization decisions

Shopify Bridge owns:

- Shopify Admin API access
- Shopify storefront readiness
- Shopify billing/readiness checks
- Shopify support readiness
- Shopify usage and verification evidence

Partners own:

- their authenticated user session
- assigned client implementation workflow
- setup, verification, evidence capture, support replies, and escalations inside their scope

Rule:

- Supabase identity proves who the partner user is.
- Platform authorization decides what that partner user can see and do.
- Shopify Bridge decides what is true for the Shopify store.

### Data Ownership And Storage

Supabase Auth stores only identity and session data:

- user identity
- provider identity
- OAuth login state
- verified email/profile basics
- session/JWT lifecycle

Do not store partner permissions, store assignments, support state, or business authorization as the source of truth in Supabase metadata.

Platform backend database is the source of truth for partner enablement:

- `PartnerAccount`
- `PartnerMember`
- partner roles and statuses
- self-service signup state
- client implementation requests
- merchant approval links/codes
- partner-store assignments
- revocation/suspension status
- partner action audit
- verification run records
- support escalations
- escalation reply threads
- operator-only support notes
- evidence bundle metadata
- template/playbook records

Shopify Bridge or product-service storage owns Shopify-specific truth:

- Shopify store records
- install status
- billing/tier state
- Knowledge Sync readiness
- storefront surface status
- support readiness
- usage/value signals
- Shopify verification evidence
- Shopify Admin API tokens and Shopify secrets

Evidence storage rule:

- store structured evidence metadata in the Platform backend database
- reference Shopify Bridge live/readiness data by store and evidence snapshot identifiers where possible
- store screenshots, videos, and large files only in approved object/file storage with redaction and scoped access
- never store or expose Shopify tokens, provider tokens, secrets, Railway variables, raw runtime logs, or raw vectorization internals in partner-visible evidence

Support storage rule:

- escalation records and reply threads belong to the Platform backend database
- partner-visible replies must be separated from operator-only internal notes
- merchant-visible exports must be generated from safe escalation summary fields, not raw internal threads

### Login Providers

Enable these Supabase providers for partner login:

- Google: provider key `google`
- Apple: provider key `apple`
- LinkedIn OIDC: provider key `linkedin_oidc`

Required redirect URLs:

- local partner UI callback, for example `http://localhost:<partner-ui-port>/auth/callback`
- production partner UI callback: `https://partners.loomai.pro/auth/callback`
- any preview/staging callback domains used by the deployment pipeline

Provider setup notes:

- Google requires a Google Cloud OAuth client and configured authorized origins/redirects.
- Apple requires Sign in with Apple configuration in Apple Developer and Supabase provider settings.
- LinkedIn requires a LinkedIn Developer app with `Sign In with LinkedIn using OpenID Connect` and the Supabase callback URL.
- Do not request provider scopes beyond profile/email unless a later feature explicitly needs provider API access.
- Do not store `provider_token` or `provider_refresh_token` unless a later integration explicitly needs it and a secret-handling design is approved.

### Self-Service Signup And Store Access

Self-service partner signup is in scope.

First release flow:

1. Partner signs in through Supabase with Google, Apple, or LinkedIn OIDC.
2. Platform backend validates the Supabase JWT.
3. If the identity is new, Platform backend creates a `PartnerAccount` and first `PartnerMember` with `PARTNER_ADMIN`.
4. New partner lands in an empty self-managed workspace.
5. Partner can read docs, browse the catalog, use the sandbox/demo center, use templates, and create draft client implementation requests.
6. Partner cannot see client-store data, merchant data, live store readiness, evidence bundles, or support history from signup alone.
7. Store access starts only through merchant-approved link/code, approved app install/claim flow, or explicit operator assignment.
8. After approval, partner sees only approved/assigned stores.

Client-store access flow:

1. Partner creates a client implementation request with store/client context.
2. Platform generates a merchant approval link/code or approved claim path.
3. Merchant or store admin approves the partner-store relationship in a merchant-safe flow.
4. Platform records approver, approval time, requested scope, approved scope, partner, store, and source flow.
5. Platform creates or activates the partner-store assignment.
6. Partner workspace shows the store with scoped permissions.
7. Merchant or operator can revoke the assignment.

Operator-created flow remains available for internal, demo, founding-partner, or recovery scenarios.

Edge cases:

- If Apple private relay hides the expected email, allow the partner account to exist but keep client-store access gated by merchant approval or operator assignment.
- If provider email is unverified or missing, create only a restricted workspace until identity requirements are satisfied.
- If an email belongs to multiple partner accounts, require explicit account selection or operator resolution.
- If a partner member is suspended or revoked, backend denies access even if Supabase login succeeds.
- If a partner submits a shop domain, return no store data before merchant approval or operator assignment.

### Backend Token Validation

Partner UI sends:

- `Authorization: Bearer <supabase-access-token>`

Platform backend must:

- validate Supabase JWT issuer, audience, expiry, signature, and subject
- map Supabase `sub` and verified email to a platform `PartnerMember`
- create a platform principal with partner-safe authorities
- enforce partner-store assignment on every store-scoped partner API
- reject inactive, pending, suspended, or revoked partner members
- audit login, session usage, access denial, assignment changes, and state-changing actions

Implementation options:

- preferred: Spring Security OAuth2 Resource Server with Supabase JWKS URI
- fallback: a dedicated Supabase JWT authentication filter with cached JWKS verification

Do not rely on frontend checks for store access.

### Platform Role Model

Keep operator roles separate from partner roles.

Existing roles:

- `PLATFORM_ADMIN`
- `PLATFORM_OPERATOR`
- `PLATFORM_PRODUCT_SERVICE`
- `CUSTOMER_ADMIN`
- `PUBLIC_API_CLIENT`

Add or model partner roles:

- `PARTNER_ADMIN`
- `PARTNER_IMPLEMENTER`
- `PARTNER_DEVELOPER`
- `PARTNER_SUPPORT`

Role intent:

- `PARTNER_ADMIN`: manages partner members and client assignments visible to the partner account; cannot self-assign new stores unless merchant-approved or operator-assigned.
- `PARTNER_IMPLEMENTER`: performs setup, verification, evidence capture, and notes for assigned stores.
- `PARTNER_DEVELOPER`: reads catalog, integration docs, sandbox, verification details, and implementation contracts; can create technical escalation evidence.
- `PARTNER_SUPPORT`: sees assigned stores, runbooks, support bundles, escalation workflow, and usage/value summaries.

Authorization must combine:

- platform role
- partner account status
- partner member status
- partner-store assignment
- assignment permissions
- requested action

### Partner UI Project

Create a separate partner UI project.

Recommended path:

- `Platfrom/partner-ui`

Recommended stack:

- Vite
- React
- TypeScript
- MUI or the platform design system once extracted
- `@supabase/supabase-js`
- TanStack Query
- React Router

Environment variables:

- `VITE_SUPABASE_URL`
- `VITE_SUPABASE_PUBLISHABLE_KEY`
- `VITE_PLATFORM_API_BASE_URL`
- `VITE_PARTNER_APP_URL`

Partner UI auth routes:

- `/login`
- `/auth/callback`
- `/logout`
- `/`
- `/stores`
- `/stores/:storeId`
- `/catalog`
- `/sandbox`
- `/verification`
- `/support`
- `/escalations`
- `/templates`

Login buttons:

- Continue with Google
- Continue with Apple
- Continue with LinkedIn

Partner UI must not:

- include operator nav
- expose deployment-level controls
- expose API-key login
- expose platform password login
- expose secrets or raw diagnostics
- expose unassigned stores
- store Shopify tokens

### Platform Backend Configuration

Add configuration for Supabase:

- `platform.auth.supabase.enabled`
- `platform.auth.supabase.issuer`
- `platform.auth.supabase.jwks-uri`
- `platform.auth.supabase.audience`
- `platform.auth.supabase.project-ref`
- `platform.auth.supabase.allowed-provider-ids`
- `platform.auth.supabase.require-email-verified`

Keep existing API-key/session auth temporarily for operator/admin automation until the operator UI migration is planned.

Partner endpoints should accept Supabase bearer auth. Operator endpoints may continue to use existing platform auth during transition.

### Security Rules

- Social login is authentication only, never authorization.
- All partner API responses are scoped server-side.
- Every store lookup checks active partner-store assignment.
- Every store-scoped mutation checks explicit assignment permission.
- Secrets never leave backend/operator-only surfaces.
- Shopify credentials stay in Shopify Bridge/platform secret boundaries.
- Provider OAuth tokens are not stored by default.
- Partner token expiry and revoked partner status must be handled gracefully in the UI.
- CORS must allow partner UI origins only.
- Audit every state-changing partner action.

---

## Deployment Vs Product Implementation Boundary

This implementation must keep admin/operator authority and partner/integrator authority separate.

Admins/operators own deployment-level control:

- platform deployments
- product-service registration
- environments
- runtime health
- provider configuration
- secrets and credentials
- billing integration internals
- vectorization/indexing internals
- queues, replay, retries, and diagnostics
- global security policy
- rollback and recovery
- global audit and impersonation

Partners/integrators own product implementation work:

- create client implementation requests
- request merchant-approved store access
- choose product templates and vertical playbooks
- apply Shopify Companion Starter-safe setup guidance
- configure approved intelligence surfaces within tier rules
- run verification packs
- capture launch evidence
- export handoff packets
- open support escalations with evidence
- monitor approved client-store readiness and blockers

Merchant/store owners own store-level consent and configuration:

- approve or revoke partner-store access
- authorize Shopify app install/claim flows
- approve billing changes through Shopify billing
- configure own store-facing appearance, surfaces, Knowledge Sync, and support handoff

Partners may trigger product-safe workflows that cause backend deployment or sync work behind the scenes, but partners must not see or control the deployment machinery directly.

Partner verbs should be:

- `Create client implementation`
- `Request store access`
- `Apply Starter template`
- `Run verification pack`
- `Export launch evidence`
- `Escalate blocker`

Partner verbs must not be:

- `Deploy runtime`
- `Change provider`
- `Rotate secret`
- `Replay vectorization queue`
- `Edit environment variables`
- `Change platform security policy`

Exit:

- a partner can implement and verify a product package without deployment-level authority
- an operator can debug and recover deployments without using partner-facing workflows
- a merchant can approve/revoke partner access without seeing operator internals

---

## Build Order

### Step 0: Product Boundary And Current-State Inventory

Close:

- inventory current Shopify Companion surfaces, merchant admin exports, platform product-service views, Shopify store views, verification scripts, and live evidence sources
- inventory current platform auth/session/API-key model and decide what remains operator-only during Supabase partner auth rollout
- confirm merchant admin remains focused on setup, surfaces, Knowledge Sync, billing, support handoff, usage/value, and blockers
- confirm long partner/operator packet text is not rendered inline for merchants
- confirm partner-only enablement language does not leak into shopper surfaces
- decide what is partner-facing, operator-only, merchant-facing, or shopper-facing
- decide which workflows are deployment-level admin workflows and which are product implementation workflows
- record any old affiliate/referral/commission language as deferred or retired
- record any old no-signup/private-only partner language as superseded by self-managed signup with zero default store access

Exit:

- partner enablement has a clear product boundary and does not depend on later UI redesign

### Step 1: Partner Domain And Access Model

Close:

- Supabase identity mapping contract
- partner account model
- partner member model
- partner roles:
  - partner admin
  - partner implementer
  - partner developer
  - partner support
- self-service signup and store-access approval posture
- partner session and access boundary
- partner-store assignment model
- merchant/operator revocation model
- platform-operator override model
- audit events for partner access and actions

Recommended first implementation:

- allow self-service partner signup through Supabase
- partner starts with zero client-store access
- merchant approval, approved install/claim flow, or operator assignment is required before store data appears
- partners authenticate through Supabase social login
- partner can be linked to approved/assigned stores only
- partner actions are read-mostly until explicit safe actions are defined
- all write actions require scoped permission and audit

Exit:

- one implementation partner can self-onboard, see an empty workspace, request/receive approved store access, and work without full operator access

### Step 2: Partner Workspace Shell

Close:

- new `Platfrom/partner-ui` project
- Supabase client setup
- social login screen for Google, Apple, and LinkedIn
- auth callback and logout routes
- authenticated API client that sends Supabase bearer token to Platform backend
- partner-specific navigation and information architecture
- partner home page
- client stores page
- intelligence catalog page
- verification packs page
- support/escalations page
- templates/playbooks page
- documentation entry point
- empty, loading, blocked, unauthorized, and revoked states
- clear separation from operator control plane and merchant Shopify admin

Recommended first UI:

- use a separate partner UI project; only use the current Platform UI for operator-only admin screens
- keep labels partner-safe and implementation-focused
- do not expose deployments, providers, secrets, Railway, raw vectorization controls, or runtime internals

Exit:

- partner has a dedicated workspace surface that is not a filtered operator admin panel

### Step 3: Client Store Portfolio

Close:

- assigned store list
- shop domain and merchant name
- current plan
- install status
- enabled intelligence pieces
- storefront readiness
- Knowledge Sync status
- webhook/live update health
- top blocker
- last activity
- usage/value signal
- escalation state
- owner/contact
- quick links to client workspace, verification pack, support packet

Exit:

- partner can prioritize assigned stores and see which stores need action without using operator tools

### Step 4: Client Store Workspace

Close:

- setup checklist
- source readiness
- surface placement status
- widget/settings summary
- support handoff profile
- bounded billing visibility
- Knowledge Sync summary
- verification run history
- launch readiness summary
- usage/value summary
- blocked-state next action

Partner-safe allowed actions, if implemented:

- copy/download verification pack
- copy/download support packet
- mark manual verification step complete
- add partner note
- create escalation
- request operator action

Partner actions to defer until explicitly scoped:

- raw sync/retry
- billing changes
- credential changes
- install OAuth changes
- provider/runtime/deployment changes
- governed action configuration

Exit:

- partner can onboard or inspect a client store without seeing operator internals

### Step 5: Intelligence Catalog

Close:

- durable catalog entries for every verified Shopify Companion surface
- tier availability
- required source data
- required Shopify scopes or merchant actions
- storefront placement
- setup instructions
- verification checks
- healthy result
- failure signs
- known limitations
- launch-safe claim text
- escalation evidence to capture
- demo/sandbox link or screenshot target

Catalog entries required:

- AI search
- product insight block
- product FAQ
- comparison
- policy strip
- contextual pill
- read-only chat/depth layer

Later/gated catalog entries:

- order lookup
- governed add-to-cart
- cart update
- support handoff
- advanced value reporting

Exit:

- partner can choose a surface, know whether a client store is ready, implement it, verify it, and explain it to a client

### Step 6: Sandbox And Demo Center

Close:

- demo store link and purpose
- sample product-page surfaces
- sample merchant admin flow
- sample launch packet
- sample verification pack
- sample support escalation
- before/after examples
- screenshot/demo clip checklist
- known demo limitations

Exit:

- partner can learn and sell the implementation workflow before touching a client store

### Step 7: Verification And Launch Center

Close:

- install checklist
- theme app embed checklist
- app block placement checklist
- Knowledge Sync readiness checklist
- billing/tier verification checklist
- storefront surface verification checklist
- Max Mode/depth-layer handoff verification
- analytics/value proof checklist
- Free AI-search-only gate
- Starter no-order-lookup gate
- Elite-only gated capability checks where relevant
- evidence capture checklist
- verification run status and history

Implementation rule:

- reuse existing live verifier outputs and Shopify Bridge/Platform readiness APIs where possible
- partner view should show pass/fail/blocked/next action, not raw logs by default

Exit:

- partner can run or follow a repeatable verification pack and produce evidence for launch/support

### Step 8: Evidence And Packet Generation

Close:

- shared packet generation boundary
- partner launch packet
- partner verification pack
- support bundle
- lifecycle/subscription packet
- design-partner rollout packet
- App Review support material where relevant
- compact summary cards plus copy/download actions
- no long packet walls inside merchant admin

Exit:

- partner and operator can retrieve consistent evidence without duplicating copy or drifting from live product truth

### Step 9: Support And Escalation Center

Close:

- escalation creation
- escalation list
- escalation status
- owner
- severity
- next action
- due date
- reproduction steps
- expected vs actual behavior
- attached evidence links
- verifier/manual checks already run
- client/store impact
- governed escalation reply thread
- partner-visible replies
- operator-visible replies
- operator-only internal notes
- resolution notes
- explicit separation between partner-visible replies and operator-only internal notes

Support flow:

1. Partner finds a blocker in an approved client-store workspace.
2. Partner runs or attaches the relevant verification pack.
3. Platform captures structured evidence: store, plan, enabled surfaces, Knowledge Sync state, failed checks, timestamps, partner user, and attachment links if provided.
4. Partner creates an escalation with severity, impact, expected behavior, actual behavior, next action, and due date.
5. Operator sees the escalation in the operator surface with full internal context and partner evidence.
6. Partner and operator use the escalation reply thread for partner-safe responses.
7. Operator can add internal notes that are never visible to partners or merchants.
8. Status changes are recorded as timeline events.
9. Resolution summary is written as a clean partner-visible field.
10. Merchant handoff/export uses only merchant-safe summary, resolution, and evidence fields.

Escalation statuses:

- `Open`
- `Waiting on partner`
- `Waiting on merchant`
- `Waiting on operator`
- `Resolved`
- `Closed`

Reply/thread rules:

- escalation thread is not a general-purpose chat inbox
- every reply belongs to one escalation
- every reply has author, role, visibility, timestamp, and optional attachment references
- visibility values must include `PARTNER_VISIBLE`, `OPERATOR_VISIBLE`, and `OPERATOR_INTERNAL`
- operator-only notes may mention internal diagnostics but must not include raw secret values
- partner-visible replies should use product/support language, not runtime/provider/secrets language
- merchant-visible exports must not include raw partner/operator back-and-forth unless explicitly converted into a clean handoff summary

Exit:

- support escalations arrive with enough context for the platform builder/operator to act without reconstructing the issue from chat history

### Step 10: Templates And Vertical Playbooks

Close:

- vertical presets
- source presets
- surface presets
- support handoff templates
- launch checklist templates
- troubleshooting playbooks
- partner agreement/scope checklist
- self-managed partner operating flow
- private/manual founding-partner fallback flow

Initial vertical playbooks:

- fashion/apparel: sizing, reviews, product fit, policies
- electronics: comparison-heavy buying, specs, compatibility
- health/beauty: ingredient/use-case questions, policy clarity
- home/furniture: dimensions, materials, delivery/return policy context

Exit:

- partner has repeatable starting points for first client conversations and deployments

### Step 11: Audit, Security, And Governance

Close:

- partner action audit events
- partner-store access audit
- partner note/escalation audit
- revoked access behavior
- unauthorized access tests
- secret redaction
- operator-only data redaction
- merchant-safe data redaction
- least-privilege defaults
- internal support evidence boundary

Exit:

- partner enablement is supportable without expanding trust to full operator access

### Step 12: Release And Rollout Gates

Close:

- private founding partner readiness gate
- internal operator readiness gate
- documentation readiness gate
- verification readiness gate
- support readiness gate
- self-managed signup readiness gate
- rollback/revocation procedure
- metrics dashboard or report

Private/manual founding partner gate:

- one partner account exists
- one or more stores assigned
- catalog available
- client store workspace usable
- verification pack usable
- escalation template usable
- partner cannot access unassigned stores
- partner cannot see secrets or operator internals

Self-managed partner gate:

- any verified partner user can create an empty workspace through Supabase
- self-signed partner sees catalog, sandbox, docs, templates, and draft implementation tools
- self-signed partner sees no client-store data before merchant approval, approved install/claim flow, or operator assignment
- partner can generate a store-access request link/code or approved claim path
- merchant approval activates a scoped partner-store assignment
- merchant or operator can revoke the assignment

Broad partner scale gate:

- several stores have been deployed or supported through the partner workflow
- repeated blockers are fixed or documented
- support load is measurable
- partner-store access is scoped and revocable
- templates are repeatable
- product reliability is strong enough for broader promises

Exit:

- platform can support self-managed implementation partners now and has clear gates before broader scale

---

## Implementation Slices

Use these as discrete LLM work packages. Do not collapse all of them into one risky session.

### Slice A: Supabase Auth Foundation

Deliver:

- Supabase project/provider configuration checklist
- Google, Apple, and LinkedIn OIDC login enabled in Supabase
- `Platfrom/partner-ui` project scaffold
- partner login page
- `/auth/callback` route
- Supabase session provider
- logout flow
- authenticated Platform API client with bearer token
- Platform backend Supabase JWT validation
- self-service partner account creation from first verified Supabase login
- empty workspace for partners with no approved store assignments
- partner member lookup by Supabase `sub` and verified email
- pending/revoked/unauthorized states
- local development env examples without secrets

Backend changes expected:

- add OAuth2 Resource Server/JWT support or equivalent Supabase JWT filter
- add Supabase auth configuration properties
- add partner principal mapping
- add partner-specific authorities or role handling
- keep existing operator API-key/session auth working during transition

Exit:

- a Supabase-authenticated partner user can reach the partner session endpoint
- a new Supabase-authenticated partner can create an empty partner workspace
- an unassigned partner sees no client-store data
- a revoked partner member is denied
- operator auth is not broken

### Slice B: Partner Enablement Data And Contracts

Deliver:

- partner account/member/role model
- self-service partner account creation model
- partner invite model for operator-created/internal cases
- client implementation request model
- merchant store-access approval link/code model
- partner-store assignment model
- access/revocation model
- audit model
- API summaries for partner home, portfolio, client workspace, catalog, verification, and escalations

Exit:

- contracts exist and can be tested without a polished UI

### Slice C: Partner Workspace Shell

Deliver:

- partner route/workspace
- partner navigation
- partner home
- client stores list
- empty/unauthorized/revoked states

Exit:

- one self-managed partner can log in, see an empty workspace, and see only approved/assigned stores after access is granted

### Slice D: Intelligence Catalog And Demo Center

Deliver:

- catalog entries for verified Starter surfaces
- sandbox/demo center
- launch-safe claims
- setup and verification instructions
- limitations

Exit:

- catalog is usable by a partner without reading strategy docs

### Slice E: Client Store Workspace And Verification Pack

Deliver:

- client store workspace
- setup checklist
- Knowledge Sync/source readiness
- surface placement status
- verification checklist
- evidence capture

Exit:

- partner can verify a store from the workspace

### Slice F: Support And Escalation Center

Deliver:

- support bundle/packet access
- escalation creation
- escalation status/owner/next action/due date
- evidence attachment or links
- structured escalation reply thread
- partner-visible replies
- operator-visible replies
- operator-only internal notes
- escalation timeline events
- merchant-safe resolution/handoff summary
- operator/partner/merchant visibility boundary

Exit:

- escalations are structured, threaded, actionable, and safe to export in merchant-facing form

### Slice G: Templates, Playbooks, And Rollout Gate

Deliver:

- vertical playbooks
- implementation templates
- self-managed partner operating flow
- private/manual founding-partner fallback flow
- rollout checklist
- metrics and acceptance proof

Exit:

- Partner Enablement Foundation is ready for a real self-managed implementation partner

---

## Data Model Targets

Use existing platform/customer/store entities where they fit, but keep partner concepts explicit.

Required concepts:

- `PartnerAccount`
- `PartnerInvite`
- `PartnerMember`
- `PartnerRole`
- `PartnerClientImplementationRequest`
- `PartnerStoreAccessRequest`
- `PartnerStoreAccessApproval`
- `PartnerStoreAssignment`
- `PartnerStoreAccessStatus`
- `PartnerActionAudit`
- `IntelligenceCatalogEntry`
- `PartnerVerificationPack`
- `PartnerVerificationRun`
- `PartnerVerificationStep`
- `PartnerSupportEscalation`
- `PartnerSupportEscalationStatus`
- `PartnerSupportEscalationThread`
- `PartnerSupportReply`
- `PartnerSupportNote`
- `PartnerSupportNoteVisibility`
- `PartnerEvidenceBundle`
- `PartnerEvidenceAttachment`
- `PartnerTemplate`
- `PartnerPlaybook`

Supabase identity fields:

- `supabaseUserId`
- `authProvider`
- `authProviderSubject`
- `email`
- `emailVerified`
- `displayName`
- `avatarUrl`
- `lastLoginAt`
- `lastAuthProviderSeenAt`

Relationship rules:

- partner account has many members
- partner account has many invites
- partner account can exist with zero store assignments
- self-service signup can create a partner account and first member, but not a store assignment
- partner account has many client implementation requests
- client implementation requests can produce merchant store-access requests
- partner account has many assigned stores
- store can start with zero or one primary partner
- assignment can be approved, active, suspended, or revoked
- partner can only see approved/assigned stores
- merchant approval is the preferred store-access path
- operator can override or revoke
- store assignment requires merchant approval, approved install/claim flow, or operator assignment
- every partner action that changes state is audited
- Supabase `sub` maps to at most one active partner member unless a deliberate multi-account switcher is built
- invitation email must match a verified provider email unless operator manually links the Supabase identity
- submitted shop domains and client details must not reveal store data before approved assignment
- support escalation belongs to one partner account and optionally one approved/assigned store
- support reply belongs to one escalation thread
- support reply visibility controls whether partner, operator, or merchant-export views can include it
- operator-only notes are never returned through partner APIs
- merchant exports are generated from safe resolution and evidence summary fields, not raw internal support thread content

---

## API Surface Targets

Prefer partner-safe APIs over exposing operator APIs directly.

Required read APIs:

- partner auth/session summary
- partner session summary
- partner home summary
- partner client-store portfolio
- partner client-store workspace
- intelligence catalog
- sandbox/demo center summary
- verification pack summary
- verification run history
- support escalation list/detail
- support escalation reply thread
- support escalation timeline
- templates/playbooks list/detail

Required write APIs:

- complete self-service partner signup from first Supabase login
- accept partner invite after Supabase login
- create client implementation request
- create merchant store-access approval link/code or claim path
- create/update partner note
- create/update escalation
- create partner-visible escalation reply
- attach redacted evidence to escalation
- mark manual verification step
- request operator action
- download/copy evidence packet

Operator-only APIs:

- create/update/delete platform deployments
- register or reconfigure product services
- change providers, prompts, runtime profiles, environments, or security policy
- rotate, view, or manage secrets
- run raw vectorization/replay/retry/diagnostic controls
- create partner
- invite partner member
- link Supabase identity manually when provider email cannot be matched safely
- assign store
- revoke store assignment
- suspend partner account or member
- override partner access
- resolve escalations
- add operator-visible escalation reply
- add operator-only escalation note
- redact or hide unsafe escalation evidence
- view internal evidence

Do not expose:

- raw secrets
- provider credentials
- Railway variables
- runtime admin controls
- raw vectorization/replay controls
- arbitrary sync/retry until scoped and audited
- unassigned store data

Auth/session API examples:

- `GET /api/partners/session`
- `POST /api/partners/signup/complete`
- `POST /api/partners/invites/{inviteId}/accept`
- `POST /api/partners/client-implementations`
- `POST /api/partners/client-implementations/{requestId}/store-access-links`
- `POST /api/merchant/partner-access/{approvalCode}/approve`
- `GET /api/partners/stores`
- `GET /api/partners/stores/{storeId}/workspace`
- `GET /api/partners/catalog`
- `GET /api/partners/stores/{storeId}/verification-pack`
- `POST /api/partners/stores/{storeId}/verification-steps/{stepId}/complete`
- `GET /api/partners/stores/{storeId}/evidence-bundles`
- `POST /api/partners/stores/{storeId}/escalations`
- `GET /api/partners/escalations/{escalationId}/thread`
- `POST /api/partners/escalations/{escalationId}/replies`
- `PATCH /api/partners/escalations/{escalationId}`

---

## UI Surface Targets

Partner workspace pages:

- Login
- Auth Callback
- Home
- Client Stores
- Client Store Workspace
- Intelligence Catalog
- Sandbox/Demo Center
- Verification Packs
- Support Center
- Escalations
- Templates And Playbooks
- Documentation

Partner UI should feel operational and efficient:

- dense but readable tables
- status filters
- clear blockers
- next actions
- copy/download actions
- verification state
- compact evidence summaries
- no decorative marketing hero pages

Status language:

- Ready
- Needs setup
- Blocked
- Verification failed
- Waiting on merchant
- Waiting on operator
- Revoked
- Escalated

Partner-safe language:

- `Knowledge Sync`
- `source readiness`
- `surface placement`
- `verification pack`
- `support handoff`
- `evidence bundle`

Operator-only language:

- vectorization internals
- runtime provider
- Railway deployment
- raw credentials
- replay queue
- debug logs
- infrastructure diagnostics

---

## Mature Platform Acceptance Criteria

This handoff is complete when:

- partner UI is a separate project suitable for `partners.loomai.pro`
- partner login uses Supabase Auth
- Google, Apple, and LinkedIn OIDC login are configured or documented with local/staging/production redirect URLs
- Platform backend validates Supabase JWTs and maps identities to partner members
- partner enablement is represented as a real self-managed partner workspace or equivalent mature platform surface
- data ownership is implemented or explicitly stubbed: Supabase Auth for identity/session, Platform backend database for partner/support state, Shopify Bridge/product services for Shopify truth and secrets
- self-service partner signup creates an empty partner workspace without default store access
- partner identity, roles, client implementation requests, store assignment, revocation, and audit are implemented or explicitly stubbed with a safe migration path
- merchant-approved store access or operator assignment is implemented or explicitly stubbed with a safe migration path
- partner can see only approved/assigned stores
- partner can inspect client-store readiness without operator internals
- deployment-level admin/operator controls are not exposed to partners
- partner product implementation workflows are separated from operator deployment workflows
- intelligence catalog covers verified Shopify Companion Starter surfaces
- partner can run or follow a verification pack for each surface
- Free AI-search-only and Starter no-order-lookup gates are included in partner verification
- support escalation captures owner, status, severity, next action, evidence, reply thread, internal-note boundary, timeline, and resolution notes
- support reply visibility prevents operator-only notes and raw internal evidence from leaking to partners or merchants
- evidence packets reuse live product truth and do not drift from merchant/App Review/support exports
- vertical playbooks exist for at least 3 merchant types
- merchant admin remains merchant-safe and not cluttered with partner/operator long-form content
- merchant or operator can approve/revoke partner-store access
- partner cannot access secrets, provider credentials, Railway/runtime internals, raw vectorization controls, or unassigned stores
- unassigned Supabase partner users cannot access client-store workspace data
- revoked partner members are denied even if their Supabase session is valid
- no commissions, referral tracking, white-label, public partner API, directory, or certification is introduced
- rollout gates are documented for self-managed partner launch, broad partner scale, white-label, and public APIs
- `CODEX_WORKING_CONTEXT.md` has compact completion status

Do not accept a docs-only outcome unless the implementation session proves code changes are blocked or intentionally deferred. The desired direction is mature platform capability, not only planning collateral.

---

## Technical Handover

### Session Startup Checklist

- Run `git status --short` and identify unrelated dirty files before editing.
- Read working context, strategic context, 001 completion, 002 completion, 003 completion, and required docs above.
- Search before changing so partner artifacts reuse current launch packet, support runbook, verification, billing, and storefront surface truth.
- Keep Launch Truth, Storefront Product Shell, and Starter Launch Package decisions intact.
- Stage only files touched for Partner Enablement Foundation.
- Keep chat updates short and put compact implementation state in `CODEX_WORKING_CONTEXT.md`.

Suggested first search:

```bash
rg -n "partner|Partner|implementation partner|affiliate|referral|commission|intelligence catalog|launch packet|support runbook|verification pack|design partner|Knowledge Sync|Free: AI search only|Starter remains read-only|order lookup|order-lookup|surfacePlacements|enabledSurfaces|usage-summary|App Review|screencast" \
  doc/Productization/future-work/MarketPlace/Products/Strategy \
  Final_Documentation \
  product-services/shopify-bridge-service \
  Platfrom
```

### Architecture To Preserve

- Shopify Companion remains the anchor/reference vertical.
- Partner enablement mirrors the verified Starter truth.
- Partner surfaces are implementation support surfaces, not merchant sales pages.
- Admin/operator surfaces own deployment-level controls; partner/integrator surfaces own product implementation workflows.
- Merchant admin must remain merchant-safe.
- Operator surfaces may retain diagnostics and internal language.
- Partner surfaces may show setup, verification, evidence, and bounded support context, but not raw platform internals.
- Partner-triggered workflows may call backend deployment/sync services only through product-safe APIs with scoped authorization and audit.
- Platform/Shopify bridge remains the source for live readiness, billing, support, usage, and verification evidence.
- Generated packets should come from shared logic where possible, not duplicated static copy that can drift.

### Documentation Targets

Docs are still required because partners need durable operating material. They are not a substitute for the mature platform surface defined above.

Create or update:

- `Final_Documentation/User_Guides/SHOPIFY_COMPANION_IMPLEMENTATION_PARTNER_ENABLEMENT_GUIDE.md`
- `Final_Documentation/Development_Guides/SHOPIFY_COMPANION_PARTNER_VERIFICATION_PACK_GUIDE.md`
- `Final_Documentation/Development_Guides/SHOPIFY_COMPANION_LAUNCH_REVIEW_AND_SUPPORT_EXPORTS_GUIDE.md`
- `Final_Documentation/User_Guides/SHOPIFY_COMPANION_MERCHANT_LAUNCH_AND_SUPPORT_GUIDE.md`
- `doc/Productization/future-work/MarketPlace/Products/Strategy/PARTNER_DASHBOARD_STRATEGY_PLAN.md`

If adding generated partner packet logic, start from the existing Shopify merchant UI/export logic:

- `product-services/shopify-bridge-service/ui/src/App.tsx`
- `product-services/shopify-bridge-service/ui/src/api.ts`
- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/web/ShopifyMerchantController.java`
- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/web/ShopifyBridgeAdminController.java`
- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/store/service/ShopifyBridgeMerchantStoreService.java`
- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/store/service/ShopifyBridgeSupportReadinessService.java`
- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/analytics/service/ShopifyBridgeUsageService.java`

If adding partner workspace or platform/operator retrieval surfaces, inspect first:

- `Platfrom/ui/src/App.tsx`
- `Platfrom/ui/src/pages/ProductServicesPage.tsx`
- `Platfrom/ui/src/pages/ShopifyStoresPage.tsx`
- `Platfrom/ui/src/api.ts`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/productservice/web/ProductServiceController.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/shopify/web/ShopifyAdminController.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/web/PlatformVerificationSuiteController.java`

Introduce a backend partner domain when implementing Slice A or later slices. Keep it scoped, audited, and testable. Allow self-service signup, but avoid public partner APIs and broad auth churn until implementation partners prove the workflow.

---

## Partner Kit Content Requirements

### Intelligence Catalog Entry Template

Each surface should include:

- surface name
- target shopper problem
- included tier
- storefront placement
- required source data
- required merchant setup
- verification steps
- healthy result
- failure signs
- known limitations
- launch-safe claim
- escalation evidence to capture

### Deployment Checklist

Must cover:

- client store identified
- Shopify Companion installed
- plan/tier confirmed
- theme app embed enabled
- target blocks placed
- Knowledge Sync healthy
- required source categories reachable
- storefront surfaces visible
- Max Mode/depth layer opens from embedded surfaces
- analytics/value signals available after use
- support handoff profile configured
- screenshots/evidence captured

### Verification Pack

Must cover:

- AI search
- product insight block
- product FAQ
- comparison
- policy strip
- contextual pill
- read-only depth layer
- billing/tier gates
- Free AI-search-only gate
- Starter no-order-lookup gate
- Elite-only order lookup, if present and verified for that store
- Knowledge Sync source readiness
- usage/value evidence

### Escalation Template

Must capture:

- partner name
- partner owner
- client/store
- plan
- enabled surfaces
- blocker category
- impact
- reproduction steps
- screenshots or video links
- verifier output or manual checks
- latest changed/deployed version when known
- next action owner
- due date
- resolution notes

---

## Minimum Slice Acceptance Criteria

Use this only for partial slice completion. The full handoff is complete only when the Mature Platform Acceptance Criteria above are met.

Minimum acceptable partial slice:

- Supabase auth boundary is explicit
- data ownership boundary is explicit
- partner UI project decision is explicit
- deployment-level admin authority vs product implementation partner authority is explicit
- self-service signup with zero default store access is explicit
- implementation partner positioning is explicit and does not read like affiliate/referral copy
- partner domain and access assumptions are recorded
- intelligence catalog covers verified Shopify Companion Starter surfaces
- each catalog entry has tier, source, setup, verification, limitations, and claim-safe copy
- deployment checklist is complete enough for a partner to follow without a live walkthrough
- verification pack can prove Free AI-search-only and Starter no-order-lookup boundaries
- escalation template captures owner, status, severity, next action, evidence, reply visibility, and resolution summary
- at least 3 vertical playbooks exist
- merchant admin remains merchant-safe and not cluttered with partner/operator long-form content
- no commissions, white-label, public partner API, directory, or certification is introduced
- `CODEX_WORKING_CONTEXT.md` has compact slice status and next handoff

---

## Verification

Always run:

```bash
git diff --check
```

If a slice touches only docs:

```bash
rg -n "affiliate|referral|commission|white-label|partner API|unapproved store access|order lookup.*Starter|Starter.*order lookup|Growth|Pro" \
  doc/Productization/future-work/MarketPlace/Products/Strategy \
  Final_Documentation/User_Guides \
  Final_Documentation/Development_Guides
```

Use search results to fix current-scope leaks or explicitly mark historical/deferred content.

If Shopify merchant UI/export code changes:

```bash
npm --prefix product-services/shopify-bridge-service/ui run build
bash -n scripts/verify-shopify-companion.sh
mvn -f product-services/shopify-bridge-service/pom.xml -q \
  -Dtest=ShopifyMerchantControllerTest,ShopifyBridgeAdminControllerTest,ShopifyBridgeSupportReadinessServiceTest,ShopifyBridgeUsageServiceTest,ShopifyBridgeMerchantStoreServiceTest \
  test
```

If Platform UI changes:

```bash
npm --prefix Platfrom/ui run build
```

If Partner UI changes:

```bash
npm --prefix Platfrom/partner-ui run build
```

If Platform backend changes:

```bash
mvn -f Platfrom/backend/pom.xml -q test
```

If Supabase auth changes:

```bash
mvn -f Platfrom/backend/pom.xml -q \
  -Dtest=PlatformSupabaseAuthIntegrationTest,PartnerAuthIntegrationTest,PartnerStoreAccessIntegrationTest \
  test
npm --prefix Platfrom/partner-ui run build
```

If those tests do not exist yet, the implementing session should create equivalent focused tests for:

- valid Supabase JWT accepted
- invalid issuer/audience/signature rejected
- new self-service partner gets empty workspace
- unassigned partner cannot access store data
- merchant-approved assignment can access assigned store
- pending member denied
- revoked member denied
- assigned store allowed
- unassigned store denied
- partner can create escalation only for approved/assigned store
- partner can add partner-visible escalation reply
- partner cannot read operator-only escalation note
- merchant export excludes operator-only notes and unsafe internal evidence
- operator auth still works

If live deployment or verifier behavior changes:

```bash
scripts/verify-shopify-companion.sh
```

For live bridge admin checks:

- `SHOPIFY_BRIDGE_ADMIN_API_KEY` must match the deployed `SHOPIFY_BRIDGE_SHARED_SECRET`.
- Do not print, paste, commit, or log the secret.
- Use secret files or environment variables only.

---

## Completion Section For Implementing LLM

Append a compact completion update here before ending the implementation session.

Required completion fields:

- implementation summary
- changed files
- decisions made
- tests/builds run
- live verification status
- pushed commit refs, if pushed
- blockers or no pending handoff items

Do not include secrets, long logs, or raw diffs.
