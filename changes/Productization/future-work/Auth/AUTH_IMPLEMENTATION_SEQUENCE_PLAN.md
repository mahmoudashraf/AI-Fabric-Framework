# Authentication and Authorization Implementation Sequence Plan

Status: detailed execution plan (2026-04-06)

This document turns the auth design set in this folder into a concrete implementation sequence.

It does not replace the mode-specific design plans.

It explains:

- what should be built first
- what is shared across all auth modes
- what is mode-specific
- how to migrate from the current trusted-client request shape
- how assistant and storefront integrations should align with the same foundation

Related design docs:

- `CUSTOMER_STOREFRONT_PRIVATE_RUNTIME_AUTH_PLAN.md`
- `PUBLIC_RUNTIME_BROWSER_CHAT_AUTH_PLAN.md`
- `SHOPIFY_APP_ARCHITECTURE_PLAN.md`

---

## 1) Executive Summary

The auth work should be implemented in this order:

1. Build one shared runtime identity and authorization foundation.
2. Ship the stricter private-runtime model first as the default production path.
3. Add the public-runtime browser-token model as an explicit opt-in mode on top of the same foundation.
4. Adapt the existing first-party platform POC proxy onto that completed foundation before reusing it as a reference path.
5. Package assistant, Shopify, and similar integrations on top of those modes rather than inventing separate auth stacks.

The most important implementation rule is:

- stop trusting caller-supplied `userId`, `ownerId`, role, customer, or tenant fields as authoritative identity

The runtime and connector should instead derive identity from verified auth context.

That shared change is the real foundation.

Without it, every higher-level mode remains brittle.

Delivery-order clarification:

- this auth work should be built before assistant productization and before Shopify or similar packaged integrations
- the existing platform POC proxy should adopt the shared auth foundation immediately after the core shared auth and mode work, because it is already a live first-party caller path
- POC is therefore part of the auth rollout sequence, not an optional later consumer
- assistant and Shopify references in this document exist to keep the auth foundation compatible with those later consumers
- they are not prerequisites for starting or completing the core auth foundation work
- the core auth delivery should land first, then the platform POC path should migrate to it, then assistant and Shopify should adopt that foundation

---

## 2) Target Modes

This sequence supports three product modes.

### 2.1 Mode A: Private runtime

Shape:

- browser -> storefront or app backend
- storefront or app backend -> private runtime
- runtime -> private connector

Identity model:

- service-to-service caller auth
- separate signed end-user context

This is the default and preferred production posture.

### 2.2 Mode B: Public runtime

Shape:

- browser -> public runtime
- runtime -> private connector

Identity model:

- short-lived browser-safe bearer tokens
- anonymous and authenticated variants

This is an explicit opt-in easier-integration posture.

### 2.3 Mode C: Packaged integration

Example:

- Shopify app

Shape:

- packaged app backend usually defaults to Mode A
- later variants may adopt Mode B if productized carefully

This is a packaging and go-to-market layer, not a separate auth foundation.

---

## 3) Current Product Gaps

The current product has useful primitives but is not yet ready for these modes without hardening.

### 3.1 Runtime still accepts trusted-client identity fields

Today the runtime chat contract still accepts caller identity fields directly.

Examples include:

- `userId`
- `ownerId`
- session identifiers used without verified actor context

That is acceptable for internal or demo traffic, but not for customer-facing production auth.

### 3.2 Conversation ownership is not yet fully auth-derived

Chat storage and retrieval still need to become explicitly tied to:

- verified token subject
- verified anonymous session subject
- or verified backend-issued end-user assertion

### 3.3 Connector ingress auth is not a customer identity model

Current connector API-key ingress is useful as:

- transport protection
- internal caller protection

It is not sufficient as:

- end-user authentication
- customer authorization
- browser-safe public identity

### 3.4 Auth-disabled synthetic privilege is still a dangerous anti-pattern

The current platform codebase still contains development shortcuts where disabled auth can imply synthetic privileged identity in some flows.

That must not shape the product implementation.

For customer-facing auth work:

- `auth disabled` must never mean `trusted caller`
- `auth disabled` must never mean `admin`

### 3.5 Existing platform POC proxy still fabricates runtime-facing identity

The current first-party platform POC path already proxies chat requests from the platform backend into deployment runtimes.

That makes it an important first consumer of the shared auth foundation.

Today that path still constructs synthetic runtime-facing values such as:

- runtime `userId`
- runtime `ownerId`
- runtime `sessionId`

from the current platform actor, then passes those values into runtime chat and conversation calls.

That is useful evidence of the intended UX shape, but it is not the target security contract.

The POC path must therefore migrate onto the same verified auth-context model as the broader auth work before it should be treated as a secure reference path for assistant or customer-facing chat.

### 3.6 External APIs and UI still assume the connector is directly reachable

Some current integration and operator-facing surfaces still expose or assume both:

- `runtimeBaseUrl`
- `connectorBaseUrl`

as if they are equally usable external entry points.

That assumption breaks once the connector is treated as fully private.

Connector-adjacent operational APIs such as:

- config
- data summaries
- status
- readiness
- logs
- diagnostics
- capabilities or metadata

must be re-homed behind runtime surfaces for supported external integration postures, with the platform optionally aggregating runtime-backed views for first-party operator experiences.

---

## 4) Locked Execution Decisions

### 4.1 Shared foundation first

Do not implement private-runtime auth and public-runtime auth as independent code paths from the start.

Build one shared auth foundation in runtime and connector first.

### 4.2 Private-runtime first

Ship Mode A first as the default productized posture.

Reasons:

- simpler trust boundary
- easier enterprise adoption
- better fit for secure customer backends
- keeps browser out of the AI identity boundary

### 4.3 Public-runtime is opt-in, not fallback

Mode B is a deliberate capability.

It must not silently appear because a runtime URL is exposed publicly.

### 4.4 Anonymous does not mean tokenless

Anonymous public chat still requires:

- a short-lived anonymous bearer token
- a real session subject
- abuse controls

Default issuer:

- runtime bootstrap endpoint

Alternative issuer:

- trusted site or app backend

### 4.5 Connector remains private in both modes

The connector is not the public integration surface.

Keep it private and treat it as:

- internal executor
- upstream integration layer

If the connector is fully private, then customer-facing or operator-facing read APIs must not require direct connector reachability.

That means connector-adjacent endpoints for:

- config
- data
- status
- summaries
- logs
- diagnostics
- capabilities

should move behind runtime APIs instead of remaining part of the external connector contract, with the platform allowed to aggregate those runtime-backed views for first-party use.

### 4.6 Existing first-party POC must adopt this same foundation before reuse

The current deployment POC console is already a first-party browser -> platform backend -> runtime path.

It should therefore be migrated immediately after the shared auth foundation lands.

Track C assistant work may reuse the POC interaction pattern only after that POC auth migration is complete.

Do not treat the current synthetic POC runtime identity contract as an acceptable template for new chat surfaces.

### 4.7 Assistant work should reuse this same foundation

The platform assistant should not grow its own special auth stack.

It should be a consumer of the same shared primitives:

- verified auth context
- signed short-lived action context
- explicit authorization checks
- auth-mode selection

---

## 5) Recommended Execution Item List

This section is the wave-style execution checklist for the auth work.

Numbering note:

- this auth implementation plan starts at item `1`
- the items below are the primary execution checklist
- the later sections in this document explain the same items in more detail

Execution-order clarification:

- items `1` through `20` are the core shared auth and external-surface implementation scope
- items `21` through `23` adapt the existing first-party platform POC path onto that completed auth foundation
- items `24` through `29` are downstream adoption and alignment items for later assistant and packaged-integration work
- items `30` through `33` cover migration and completion hardening across the auth rollout
- assistant and Shopify should not block the execution of items `1` through `23`
- the intended order is:
  - complete core auth foundation first
  - consolidate external connector-adjacent APIs behind runtime surfaces, with platform aggregation only for first-party/operator views
  - then migrate the platform POC path onto that foundation
  - then integrate assistant and Shopify onto that foundation

### Shared foundation

1. canonical runtime auth context: add one shared runtime auth context model with subject type, auth mode, deployment scope, customer scope, tenant scope, session id, issuer, expiry, and granted scopes
2. runtime ingress auth resolver: add one runtime auth resolution layer that can validate private-runtime service callers, private-runtime end-user assertions, and public-runtime bearer tokens, then derive the canonical auth context
3. request-identity de-authoritization: stop treating request `userId`, `ownerId`, role, customer, and tenant fields as authoritative identity inputs for chat ownership, retrieval access, and action authorization
4. conversation ownership normalization: make stored and retrieved conversation ownership derive from verified subject identity rather than caller-supplied identity fields
5. remote authz contract hardening: standardize one authz request and response contract for runtime and connector authorization checks, with canonical verified `authContext` carried explicitly and legacy `userId` or `sessionId` retained only as compatibility aliases
6. auth observability and diagnostics: add safe auth-mode, subject-type, issuer, deployment-scope, allow or deny diagnostics, and explicit legacy-path migration metadata across runtime and connector paths
7. explicit auth-mode configuration: add clear runtime configuration for private-runtime mode, public-runtime mode, accepted issuers, anonymous support, and token audiences instead of implicit behavior
8. external connector-surface consolidation: move externally consumed connector-adjacent config, data, status, summary, logs, and diagnostics endpoints behind runtime APIs when connector is private, with platform aggregation only for first-party/operator surfaces
9. auth-mode-aware deployment metadata and UI: make provisioning/customer APIs, widget-facing metadata, and platform UI express runtime/connector exposure by auth mode instead of always surfacing both URLs as direct user-facing endpoints

### Private-runtime implementation

10. trusted backend caller auth: add machine-to-machine caller authentication for storefront or app backends using deployment-scoped service credentials or signed service JWTs
11. backend-issued end-user assertion verification: validate storefront or app backend-issued signed end-user context before runtime processing
12. private-runtime request-contract migration: update runtime chat and conversation APIs so auth-derived identity is the primary contract and old request identity fields become compatibility-only hints
13. private-runtime authorization hook-up: ensure runtime and connector consult customer-owned authorization services for sensitive retrieval and action execution
14. private-runtime regression and examples: add local and live verification plus integration examples for the private-runtime mode

### Public-runtime implementation

15. public bootstrap endpoint: add a runtime bootstrap endpoint for anonymous or public chat session establishment
16. runtime-issued anonymous token flow: mint short-lived anonymous browser tokens from the runtime bootstrap endpoint by default, with explicit abuse controls
17. authenticated public token validation: validate trusted signed browser-safe end-user tokens for logged-in public users
18. public-mode authorization branching: enforce different policy envelopes for anonymous public chat versus authenticated public chat
19. public-mode abuse controls: add origin checks, rate limiting, token TTL controls, and challenge-escalation hooks for public runtime traffic
20. public-runtime regression and examples: add local and live verification plus widget or embed expectations for the public-runtime mode

### First-party POC adaptation

These items are intentionally placed before assistant and packaged-integration adoption.

They exist because the platform POC console is already a real first-party consumer of runtime chat and conversation APIs.

21. platform POC proxy identity migration: replace synthetic runtime-facing `userId`, `ownerId`, and fixed POC session derivation with the shared verified auth-context model
22. POC conversation ownership and reset alignment: make POC conversation fetch, reset, and trace views rely on verified subject ownership and session semantics instead of legacy proxy-owned identifiers
23. POC regression and operator proof: add local and live proof that the deployment POC console still works end to end after the auth migration, including prompt preview, conversation continuity, and permission denial

### Downstream packaging and assistant alignment

These items are intentionally downstream of the core auth implementation.

They exist so later consumers adopt the shared auth foundation instead of inventing separate auth stacks.

24. Shopify and packaged-backend default posture: align packaged integrations to use private-runtime mode by default
25. shop-to-deployment or package-to-deployment mapping: add the mapping and lifecycle contract needed for packaged integrations to resolve the correct deployment
26. assistant shared auth foundation alignment: make the platform assistant consume the same canonical auth context and authorization model rather than a one-off auth stack
27. platform-proxy assistant mode: implement or preserve `PLATFORM_PROXY_SESSION` as the assistant phase-1 posture on top of the shared foundation
28. public assistant extension path: define the later `PUBLIC_RUNTIME_BROWSER_TOKEN` assistant path so anonymous and authenticated public assistant traffic can reuse the same runtime auth contracts
29. signed assistant action context: keep assistant action preflight and governed execution on a separate short-lived signed action context token aligned with the shared auth foundation

### Migration and completion

30. compatibility period: keep legacy request identity fields only as compatibility shims while verified auth context becomes primary
31. warning period: emit explicit warnings when request identity fields conflict with verified auth context or when public runtime is enabled without explicit token issuer configuration, and attach concrete deprecation plus sunset metadata on legacy chat and conversation routes
32. removal period: remove authoritative identity semantics from request payload identity fields entirely after migration stabilizes
33. completion verification: prove all supported modes through local and live verification, including private-runtime authenticated flows, public-runtime anonymous flows, public-runtime authenticated flows, the migrated platform POC proxy flow, and assistant platform-proxy flows

---

## 6) Shared Foundation Workstream

This workstream is required for all modes.

### 5.1 Introduce a canonical runtime auth context

Add a single runtime auth context model that represents:

- subject id
- subject type
- auth mode
- deployment scope
- customer scope
- tenant scope when relevant
- session id
- token issuer
- token expiry
- granted scopes
- caller type

Recommended subject types:

- `END_USER`
- `ANONYMOUS_SESSION`
- `TRUSTED_BACKEND`
- `INTERNAL_PLATFORM_USER`
- `SYSTEM_PROCESS`

Recommended auth modes:

- `PRIVATE_RUNTIME_BACKEND_MEDIATED`
- `PUBLIC_RUNTIME_ANONYMOUS`
- `PUBLIC_RUNTIME_AUTHENTICATED`
- `PLATFORM_PROXY_SESSION`

### 5.2 Add runtime auth resolution middleware

The runtime needs a single ingress auth resolver that can:

- validate service caller auth for private-runtime mode
- validate signed end-user context for private-runtime mode
- validate short-lived bearer tokens for public-runtime mode
- derive the canonical runtime auth context
- reject conflicting or incomplete auth material

This resolver should become the source of truth for chat ownership and authorization decisions.

### 5.3 Stop treating request identity fields as authoritative

Update runtime request handling so:

- `userId`
- `ownerId`
- similar subject identifiers

are no longer used as the authority source for:

- chat ownership
- conversation reads
- deletes
- retrieval access
- action authorization

Backward compatibility can remain temporarily, but only as:

- derived defaults
- compatibility shims
- logging aids

not as the actual security decision input.

### 5.4 Normalize conversation ownership

Store and fetch conversations by verified subject identity.

Recommended rule:

- authenticated user -> subject-owned conversation
- anonymous public chat -> anonymous session-owned conversation
- platform assistant proxy -> current platform actor-owned conversation

If the client sends `sessionId` or `conversationId`, the runtime may use those as routing hints, but final authorization must still be based on the verified auth context.

### 5.5 Tighten remote authz contract

Standardize one remote authz request shape for runtime and connector.

Recommended fields:

- `subjectId`
- `subjectType`
- `authMode`
- `deploymentId`
- `customerId`
- `tenantId`
- `sessionId`
- `operation`
- `resourceType`
- `resourceId`
- `requestedScopes`
- `requestContext`

Recommended response:

- `allowed`
- `reasonCode`
- `reasonMessage`
- `resolvedScope`
- `requiresConfirmation`
- `requiresApproval`

### 5.6 Add auth observability

Every auth-relevant request path should log safe auth metadata such as:

- auth mode
- subject type
- deployment id
- token issuer
- allow or deny outcome

Do not log:

- raw tokens
- secret values
- customer credentials

### 5.7 Add explicit auth-mode configuration

Add runtime config that makes auth mode selection explicit.

Recommended posture flags:

- public runtime enabled or disabled
- anonymous mode enabled or disabled
- accepted token issuers
- accepted audiences
- private trusted-backend mode enabled or disabled

Do not rely on implicit URL exposure or disabled auth as the mode selector.

### 5.8 Consolidate external connector-adjacent operational surfaces

Once the connector is private, it should no longer be part of the supported external read contract.

That means externally consumed endpoints for:

- config
- data summaries
- status
- readiness
- logs
- diagnostics
- capabilities

should move behind runtime surfaces, with platform aggregation only for first-party/operator views.

This applies to:

- customer integrations
- public provisioning metadata
- widget-facing metadata
- first-party operator UI

### 5.9 Normalize internal system subjects

Internal first-party probes, verification flows, and import processes should stop presenting themselves as user-shaped identities once the shared auth model exists.

Those flows should use explicit system-subject classification instead of looking like ordinary end-user or operator traffic.

That includes operational request envelopes such as runtime data-sync traces:

- carry canonical verified `authContext` for system and platform-proxy callers
- retain legacy `trace.userId` / `trace.sessionId` only as compatibility aliases during migration
- prefer canonical `authContext.subjectId` and `authContext.sessionId` for downstream authorization and audit decisions

---

## 7) Token and Assertion Contracts

### 6.1 Private-runtime end-user assertion

This assertion is sent from the trusted storefront or app backend to the runtime.

Recommended claims:

- `iss`
- `aud`
- `sub`
- `subjectType=END_USER`
- `deploymentId`
- `customerId`
- `tenantId` when relevant
- `sessionId`
- `storeCustomerId` when relevant
- `scopes`
- `exp`
- `jti`

This token normally stays server-side in the storefront or app backend.

### 6.2 Public-runtime anonymous token

This is the browser-safe anonymous token.

Recommended claims:

- `iss`
- `aud`
- `sub=anon:<session-id>`
- `subjectType=ANONYMOUS_SESSION`
- `deploymentId`
- `sessionId`
- `scopes`
- `exp`
- `jti`

Default issuer:

- runtime bootstrap endpoint

### 6.3 Public-runtime authenticated token

This is the browser-safe token for logged-in public users.

Recommended claims:

- `iss`
- `aud`
- `sub`
- `subjectType=END_USER`
- `deploymentId`
- `customerId`
- optional `tenantId`
- `sessionId`
- optional `storeCustomerId`
- `scopes`
- `exp`
- `jti`

### 6.4 Internal assistant action context token

Assistant action execution should use a separate short-lived signed action context token for:

- actor binding
- deployment binding
- action preflight
- governed execution routes

This token should not be reused as the generic customer storefront token.

---

## 8) Mode A Implementation: Private Runtime

### 7.1 Machine caller authentication

Implement one or more trusted backend caller options:

- deployment-scoped service API key
- signed service JWT
- mTLS later if required

Phase 1 recommendation:

- deployment-scoped service API key or signed service JWT

### 7.2 End-user assertion verification

Runtime validates the backend-issued end-user assertion on every request.

Validation should include:

- issuer
- audience
- expiry
- deployment binding
- signature

### 7.3 Runtime request model updates

Update runtime chat and conversation APIs so they can consume auth-derived identity cleanly.

Recommended changes:

- accept authenticated context via headers or bearer token
- de-emphasize or deprecate raw `userId`
- ensure conversation CRUD checks auth-derived owner

### 7.4 Authorization hookup

Before sensitive retrieval or actions:

- runtime calls customer-owned authz endpoint
- connector can perform a second check for action execution when needed

### 7.5 Regression requirements

Must prove:

- trusted backend caller required
- invalid backend caller denied
- missing end-user assertion denied
- forged end-user assertion denied
- authenticated customer can read only what authz allows
- authenticated customer cannot exceed store-owned policy

---

## 9) Mode B Implementation: Public Runtime

### 8.1 Public bootstrap endpoint

Add a bootstrap endpoint such as:

- `POST /api/public/chat/session`

Responsibilities:

- origin validation
- abuse protection
- rate limiting
- anonymous token minting
- optional anonymous conversation bootstrap

### 8.2 Anonymous token issuance

The runtime issues the anonymous token by default.

That fact should be explicit in product docs, API docs, and widget integration docs.

Only a trusted backend may replace the runtime as issuer.

The browser must never self-issue anonymous identity.

### 8.3 Authenticated public token validation

If the embedding site has a logged-in user:

- it may pass a signed browser-safe token to the runtime

Runtime must validate:

- issuer
- audience
- expiry
- deployment binding
- signature

### 8.4 Public-mode authorization

Authorization must branch by effective auth mode.

Anonymous:

- low-risk read-only scope only

Authenticated:

- wider but still explicit scope

No valid token:

- fail closed

### 8.5 Abuse controls

Add:

- per-origin rate limits
- per-session rate limits
- challenge escalation hooks
- token TTL limits
- safe replay protection where practical

### 8.6 Regression requirements

Must prove:

- anonymous bootstrap issues valid short-lived token
- anonymous token can chat within low-risk scope
- anonymous token cannot access customer-specific data
- authenticated public token can access only permitted data
- expired token denied
- invalid issuer denied
- connector remains private

---

## 10) Existing First-Party POC Adaptation

The platform POC console should migrate onto the shared auth foundation before assistant reuse.

### 9.1 Why POC is in scope for auth

The POC console is already a real first-party flow:

- browser -> platform session
- platform backend POC proxy -> deployment runtime

That means it is not just a UX experiment.

It is already exercising runtime chat and conversation contracts from a first-party product surface.

### 9.2 Current gap to remove

The current POC proxy still fabricates runtime-facing identity and ownership values from the platform actor.

That includes synthetic:

- `userId`
- `ownerId`
- `sessionId`

derived by the platform proxy rather than by the shared auth foundation.

That behavior should be retired.

### 9.3 Target POC security posture

The target POC posture should be:

- browser authenticates only to the platform with the normal platform session
- platform backend derives the verified current actor from platform auth context
- platform backend forwards auth-derived subject context to runtime through the shared auth foundation contract
- any temporary compatibility fallback exists only for older runtimes that do not expose the verified `/api/chat/me/*` surface yet, not for `401` or other auth failures
- runtime conversation ownership derives from verified auth context
- POC reset and fetch operations are authorized against the same verified subject ownership rules
- POC and other first-party operator flows use runtime-backed or platform-aggregated surfaces for connector-adjacent diagnostics rather than direct connector endpoints

### 9.4 Why this comes before assistant reuse

The Track C assistant intentionally reuses the proven POC interaction pattern for its simple first UI.

That reuse should only apply to:

- UI interaction shape
- chat proxy posture
- operator workflow feel

not to the legacy synthetic identity contract.

The platform should therefore migrate POC first, then let assistant build on the migrated pattern.

### 9.5 POC regression requirements

Must prove:

- platform-authenticated operator can still use the POC chat console
- conversation continuity survives the identity migration
- prompt preview and trace views still work
- permission denial still behaves correctly for read-only actors
- no runtime request path depends on synthetic `userId` or `ownerId`

---

## 11) Mode C Implementation: Shopify App Packaging

### 9.1 Default packaging posture

Shopify app packaging should default to Mode A:

- Shopify/store app backend verifies customer or merchant identity
- app backend talks to private runtime

### 9.2 Packaging-specific work

Add:

- shop-to-deployment mapping
- installation and uninstall lifecycle
- merchant admin controls
- runtime health visibility
- deployment readiness checks

### 9.3 Optional future posture

If a later Shopify product wants low-friction public storefront chat:

- it may adopt Mode B

But that should be a deliberate variant, not the default packaging path.

---

## 12) Assistant Alignment

The assistant work should consume the same auth foundation.

### 10.1 Platform assistant phase 1

Use:

- `PLATFORM_PROXY_SESSION`

Shape:

- browser -> platform backend
- platform backend -> assistant runtime

### 10.2 Future assistant public mode

If the assistant framework later powers public customer chat:

- it should reuse the public-runtime bearer-token mode

Do not build a separate assistant-only public auth stack.

### 10.3 Shared assistant requirements

Assistant flows still need:

- auth-derived actor identity
- explicit action preflight
- fail-closed authorization
- no trust in payload `userId`

---

## 13) Detailed Implementation Sequence

The recommended sequence is:

### Phase 0: Contracts and diagnostics

1. Add canonical runtime auth context types.
2. Add auth-mode enums and config.
3. Add structured auth diagnostics and safe logging.
4. Document the migration rule that request identity fields are no longer authoritative.

### Phase 1: Runtime identity foundation

5. Add runtime ingress auth resolver.
6. Add token or assertion validation helpers.
7. Change chat ownership to use auth-derived subject.
8. Change conversation read and delete to use auth-derived subject.
9. Add compatibility logging when request `userId` differs from auth-derived subject.

### Phase 2: Remote authorization hardening

10. Standardize remote authz request or response contracts.
11. Add runtime pre-retrieval authz hooks where missing.
12. Add connector action-time authz hooks where missing.
13. Add explicit deny-reason propagation to callers.

### Phase 3: External surface consolidation

14. Move externally consumed connector-adjacent config, data, status, summary, logs, and diagnostics APIs behind runtime surfaces, with platform aggregation only for first-party/operator views.
15. Make public provisioning metadata, widget-facing metadata, and platform UI endpoint surfaces auth-mode aware so they do not imply the connector is directly reachable.

### Phase 4: Private-runtime production path

16. Add trusted backend caller auth.
17. Add backend-issued end-user assertion verification.
18. Add deployment-scoped configuration for accepted issuers and audiences.
19. Add private-runtime regression coverage.
20. Publish private-runtime integration guide and examples.

### Phase 5: Public-runtime opt-in path

21. Add anonymous bootstrap endpoint.
22. Add runtime-issued anonymous token flow.
23. Add authenticated public token validation.
24. Add public-mode rate limiting and abuse hooks.
25. Add public-runtime regression coverage.
26. Publish public-runtime integration guide and widget expectations.

### Phase 6: Existing first-party POC adaptation

27. Migrate the platform POC proxy off synthetic runtime identity.
28. Align POC conversation ownership and reset semantics with verified subject ownership.
29. Add POC local and live verification on top of the shared auth foundation.

### Phase 7: Packaging and assistant convergence

30. Align Shopify app architecture to Mode A by default.
31. Align widget and assistant surfaces to consume the same token contracts and runtime-first operational surfaces.
32. Align platform assistant action context token handling with shared auth primitives.
33. Add end-to-end packaging or assistant live verification.

---

## 14) Testing Matrix

### 12.1 Unit

- token validation
- auth-mode resolution
- owner-derivation helpers
- deny and allow authz mapping

### 12.2 Integration

- runtime conversation ownership
- runtime chat under each auth mode
- connector authorization on action execution
- runtime-backed replacements for connector-adjacent operational APIs
- invalid token and missing token cases

### 12.3 Live verification

- one private-runtime authenticated customer flow
- one public-runtime anonymous flow
- one public-runtime authenticated flow
- one auth-mode-aware runtime/platform operational surface flow with no direct connector requirement
- one assistant platform-proxy flow

---

## 15) Migration Strategy

### 15.1 Compatibility period

During migration, retain the old request fields for compatibility but treat them as:

- optional hints
- diagnostics inputs
- deprecated contract elements

### 15.2 Warning period

Emit warnings when:

- `userId` is present but no verified auth subject exists
- `userId` conflicts with auth-derived subject
- public runtime is enabled without explicit token issuer configuration

### 15.3 Removal period

After compatibility stabilization:

- remove identity authority from request payload fields entirely

---

## 16) Completion Criteria

This auth work is complete only when:

- runtime derives identity from verified auth context
- request identity fields are no longer authoritative
- private-runtime mode is production-ready
- public-runtime mode is available as explicit opt-in
- anonymous public chat uses short-lived issued tokens, not tokenless access
- connector remains private in both modes
- customer-facing connector-adjacent config, data, status, summary, and logs surfaces are served from runtime-backed APIs rather than direct connector reachability
- internal first-party probes and import flows use explicit system-subject semantics rather than user-shaped identity placeholders
- the existing platform POC proxy no longer depends on synthetic runtime identity
- assistant flows can reuse the same shared auth foundation when their implementation starts
- auth-disabled shortcuts are clearly excluded from production behavior

Core-auth completion clarification:

- the auth foundation can be considered complete before assistant and Shopify are actually implemented
- the existing first-party POC path is part of the auth rollout and should be adapted before assistant reuse is treated as complete or trustworthy
- what must be true at auth completion time is that those later consumers can adopt the same foundation without requiring a redesign
- actual assistant and Shopify delivery remains follow-on work

---

## 17) Recommendation

The correct implementation strategy is:

- build one shared auth core
- adapt the existing first-party POC proxy onto that core
- ship private-runtime as the default production mode
- add public-runtime as the explicit opt-in mode
- package Shopify and assistant work on top of that core

That keeps the system secure, flexible, and easier to reason about than building separate auth stacks for every product surface.
