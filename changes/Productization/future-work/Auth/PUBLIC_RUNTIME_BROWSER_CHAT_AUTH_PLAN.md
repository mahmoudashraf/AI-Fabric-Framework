# Public Runtime Browser Chat Authentication and Authorization Plan

Status: detailed planning document (2026-04-06)

This document defines the recommended product design for the easier integration model where:

- the runtime is directly reachable from the browser
- the connector still remains private
- chat must support both anonymous and authenticated end users
- the deployment is intended to be easy to embed into public websites and storefronts

This plan is intentionally separate from the stricter private-runtime model.

It exists for deployments that prioritize:

- easy integration
- low-friction website embedding
- anonymous public chat
- optional customer login enrichment

It must still be secure enough for real production use.

---

## 1) Executive Summary

If the runtime is directly accessible from the browser, the safe design is:

- browser -> public runtime
- runtime -> private connector
- runtime or connector -> authorization endpoint when sensitive access is requested
- connector -> upstream systems using deployment-owned server-side credentials

The runtime must not rely on:

- browser-held static connector keys
- browser-held static runtime admin keys
- request-body `userId`
- query-string `ownerId`

Instead, the runtime needs a dedicated browser-safe authentication model built around short-lived bearer tokens.

This plan supports two end-user modes:

- anonymous public chat
- authenticated public chat

The anonymous mode must be intentionally low-privilege.

The authenticated mode must derive identity from a verified signed token or token exchange flow.

This document defines an explicit opt-in mode.

It does not replace the stricter private-runtime model.

Browser-held bearer tokens are allowed only in this public-runtime mode.

---

## 2) Why This Model Exists

Some customers will want the easiest possible integration:

- drop-in website chatbot
- no mandatory storefront backend mediation for each chat request
- public FAQ and product-assistance experience
- optional login-aware behavior when the site already knows the customer

That ease of integration creates a different trust boundary than the private-runtime model.

The browser is untrusted.

So if the runtime is reachable from the browser, the runtime itself must become capable of:

- validating browser-safe bearer tokens
- distinguishing anonymous vs authenticated users
- scoping conversation ownership from validated identity
- enforcing lower privilege for public access
- rate-limiting and abuse-hardening the public ingress

---

## 3) Goals

This plan should achieve all of the following:

- support very easy integration into public websites
- support anonymous public chat safely
- support authenticated public chat when a site already has a signed customer token
- keep the connector private
- keep upstream store or system credentials server-side
- make runtime identity derivation authoritative
- make authorization explicit and fail-closed where sensitive data or actions are involved
- avoid browser exposure of deployment secrets
- preserve compatibility with future Shopify, WooCommerce, and custom storefront integrations

---

## 4) Non-Goals

This plan does not require:

- browser-direct connector access
- static deployment API keys exposed in frontend code
- platform admin session auth for end users
- giving anonymous users access to customer-specific data
- making all actions available from the public web
- removing the stricter private-runtime model

---

## 5) Supported Modes

### 5.1 Anonymous public chat

This is the easiest integration mode.

The end user is not logged in.

The deployment is allowed to answer only low-risk questions such as:

- product discovery
- catalog browsing
- FAQ
- policy explanations
- general support guidance

It must not expose:

- order history
- account information
- customer-specific pricing
- customer-specific documents
- privileged write actions

### 5.2 Authenticated public chat

The website has already authenticated the end user and can provide a signed token that the runtime can validate directly.

This mode can unlock richer behavior, such as:

- customer-specific conversation continuity
- order-status questions
- account-aware support
- narrower action execution

It still must not trust frontend-supplied identity fields outside the validated token.

### 5.3 Mixed mode

A single deployment may support both:

- anonymous chat for general users
- authenticated chat for logged-in users

The runtime must determine the effective auth mode per request from the presented token.

---

## 6) Locked Design Decisions

### 6.1 Runtime is the only public AI ingress

If browser access is enabled, it should be enabled for runtime only.

The connector should remain private.

This keeps the public contract smaller and avoids exposing raw action execution semantics.

### 6.2 Public browser access requires bearer-token auth

The browser must not use:

- `CONNECTOR_API_KEY`
- runtime admin keys
- shared static deployment API keys

The public runtime must accept only browser-safe short-lived bearer tokens.

### 6.3 Anonymous users still get a signed session identity

Even for non-logged-in users, the runtime should not operate completely unauthenticated.

The recommended model is:

- browser bootstraps an anonymous chat session
- runtime bootstrap endpoint issues a short-lived anonymous token by default
- subsequent browser requests use that token

Alternative:

- a trusted site or app backend may issue the anonymous token instead if the integration already has one and wants to keep bootstrap outside the runtime

The browser must never mint this token for itself.

This allows:

- rate limiting by session
- ownership of chat history
- scoped anonymous permissions
- abuse controls

The anonymous bootstrap endpoint itself must still be protected by:

- origin checks
- rate limiting
- abuse throttling
- optional CAPTCHA or challenge escalation

### 6.4 Authenticated users must use verified signed identity

If the website has a logged-in customer, that customer identity must be conveyed through a signed token or signed assertion that the runtime can validate directly.

The runtime must derive the subject from the validated token, not from request payload fields.

### 6.5 Authorization is mode-dependent

Authorization rules must change based on auth mode:

- anonymous
- authenticated public
- internal trusted caller if supported later

Anonymous mode must default to strict read-only low-risk scope.

### 6.6 Connector stays private

The connector is still an internal executor.

Public websites should not call:

- `/actions/execute`
- `/api/chat`
- `/api/authz/*`

on the connector directly.

### 6.7 Conversation ownership comes from verified identity

Conversation ownership must be derived from:

- anonymous token subject
- authenticated token subject

The runtime must stop treating `userId` or `ownerId` as authoritative caller input.

### 6.8 Public runtime must never use auth-disabled admin bypass

This public-runtime mode is the place where an auth-disabled shortcut is most dangerous.

If a runtime supports direct browser access, then `auth disabled` must not mean:

- every browser request is accepted as authenticated
- a synthetic admin principal is injected
- anonymous traffic silently gains elevated capability

Why this is especially dangerous here:

- public browser traffic is the least trustworthy traffic in the system
- anonymous chat is supposed to be intentionally low-privilege
- authenticated public chat is supposed to be constrained by short-lived verified tokens
- a synthetic privileged principal would collapse both modes into an effectively over-privileged bypass

That would defeat the entire reason this plan distinguishes:

- anonymous public chat
- authenticated public chat
- explicit privileged internal flows

Planning rule:

- public runtime mode must fail closed when no valid bearer token is present
- anonymous access must still use a short-lived anonymous session token
- disabling auth must never silently convert browser traffic into a trusted or admin caller

Acceptable development alternatives:

- a local-only anonymous bootstrap mode with strict read-only scope
- explicit fixture tokens minted by a local test helper
- a clearly labeled dev principal with non-production scope that is impossible to confuse with real admin identity

Unacceptable production-like shortcut:

- `auth disabled` => `admin by default`

That shortcut would make abuse controls, conversation ownership, action gating, and authorization testing unreliable.

---

## 7) Trust Boundaries

### 7.1 Browser

The browser is untrusted.

It can hold:

- short-lived bearer tokens
- anonymous session tokens
- customer-issued signed tokens

It must not hold:

- deployment admin credentials
- connector ingress keys
- store API credentials

### 7.2 Runtime

The runtime is the public AI ingress.

It is responsible for:

- validating bearer tokens
- determining auth mode
- deriving subject identity
- enforcing conversation ownership
- invoking remote authz when required
- orchestrating retrieval and action execution

### 7.3 Connector

The connector is private.

It is responsible for:

- upstream action execution
- use of deployment-owned credentials
- optional downstream authz enforcement with runtime-provided verified subject context

---

## 8) Token Model

### 8.1 Anonymous token

Recommended claims:

- `iss`
- `aud`
- `sub = anon:<session-id>`
- `deploymentId`
- `sessionId`
- `authMode = ANONYMOUS`
- `scopes`
- `iat`
- `exp`
- optional `siteId` or `shopId`

Recommended TTL:

- 15 to 60 minutes

Recommended renewal:

- sliding renewal or session refresh endpoint

Recommended default issuer:

- runtime bootstrap endpoint

Allowed alternate issuer:

- trusted site or app backend

Disallowed issuer:

- browser client code

### 8.2 Authenticated public token

Recommended claims:

- `iss`
- `aud`
- `sub = <customer-id>`
- `deploymentId`
- `sessionId`
- `authMode = AUTHENTICATED_PUBLIC`
- `scopes`
- `iat`
- `exp`
- optional `customerId`
- optional `tenantId`
- optional `storeCustomerId`
- optional stable role/segment attributes

### 8.3 Signing options

Preferred:

- JWT with asymmetric signature and JWKS validation

Allowed phase-1 simplification:

- HMAC-signed token with per-deployment secret rotation

The signing model must support:

- expiry
- deployment scoping
- audience binding
- key rotation

---

## 9) Bootstrap Flows

### 9.1 Anonymous bootstrap

1. Browser loads public chat UI.
2. Browser calls runtime bootstrap endpoint such as `POST /api/public/chat/session`.
3. Runtime issues:
   - anonymous bearer token
   - session id
   - allowed scopes
   - expiry metadata
4. Browser uses that token for subsequent chat requests.

This is the default anonymous issuance flow for the easy-integration public mode.

### 9.2 Authenticated bootstrap

Recommended:

1. Website authenticates the customer using its normal auth system.
2. Website or backend obtains a signed customer token scoped to the deployment.
3. Browser uses that token when calling the runtime.

Alternative:

1. Browser calls a site backend.
2. Site backend exchanges site session for a short-lived runtime token.
3. Browser uses the returned token for runtime calls.

So the token-location rule is:

- private-runtime mode: token normally remains backend-side
- public-runtime mode: browser may hold only short-lived bearer tokens issued by a trusted server

---

## 10) Authorization Model

### 10.1 Anonymous authorization

Anonymous mode should default to allow only:

- public catalog exploration
- FAQ answers
- policy and shipping info
- generic support flows

Anonymous mode should default-deny:

- account lookup
- order lookup
- personal profile access
- admin actions
- connector writes
- customer-specific vector retrieval

### 10.2 Authenticated public authorization

Authenticated public mode can allow more capabilities, but still should be explicitly policy-driven.

Examples:

- read own orders
- read own tickets
- initiate support workflows
- update own cart if the customer policy allows it

Any customer-specific retrieval or write should still go through remote authz evaluation before execution.

### 10.3 Remote authz contract

The runtime should continue using a remote authz policy model, but with verified subject context.

Request payload should include:

- `subject`
- `deploymentId`
- `sessionId`
- `authMode`
- `resourceType`
- `resourceId`
- `operation`
- `userAttributes`
- `metadata`

Response should include:

- `allowed`
- `reasonCode`
- optional filtered scope
- optional obligations such as confirmation requirement

Fail mode must be:

- fail-closed for sensitive operations
- optionally fail-soft only for explicitly public low-risk content if productized that way

---

## 11) Runtime Changes Required

### 11.1 Add bearer-token validation at runtime ingress

The public runtime needs auth middleware that:

- validates anonymous or authenticated public token
- derives auth context
- rejects invalid or expired tokens

### 11.2 Replace trusted request-body identity usage

Current trusted-client behavior must be removed from the public-runtime path.

The runtime must stop using request-supplied:

- `userId`
- `ownerId`

as authoritative identity.

### 11.3 Derive chat ownership from token subject

Conversation create/read/delete must be bound to:

- `anon:<session-id>` for anonymous mode
- verified customer subject for authenticated mode

### 11.4 Add public bootstrap endpoints

The runtime should expose public bootstrap/session endpoints for:

- anonymous session creation
- token refresh if supported
- optional capability introspection for the UI

### 11.5 Separate public auth from admin auth

Public runtime auth must be a separate ingress model from:

- admin API key auth
- internal trusted automation auth

They must not share credentials or expectations.

---

## 12) Connector Changes Required

The connector should remain private.

Phase 1 connector requirements:

- accept verified subject context from runtime
- use deployment-owned credentials for upstream calls
- never rely on browser-originated identity fields
- optionally perform second-hop authz checks for sensitive actions

The connector does not need to become browser-auth aware in phase 1 of this model.

---

## 13) Security Guardrails

### 13.1 Rate limiting

Required for anonymous mode.

Recommended dimensions:

- IP
- anonymous session id
- deployment id
- origin

### 13.2 Abuse controls

Recommended options:

- CAPTCHA or challenge escalation on suspicious traffic
- burst throttling
- content abuse detection
- automated session revocation

### 13.3 Origin allowlist

Deployments should be able to constrain allowed browser origins.

### 13.4 Strict capability allowlist

Anonymous mode must use a narrow allowlist of supported actions and knowledge domains.

### 13.5 Secret isolation

Store or system credentials must remain deployment-scoped and server-side only.

### 13.6 Logging and auditability

Every request should log:

- deployment id
- auth mode
- subject
- session id
- authorization decision
- action execution outcome

---

## 14) Deployment Configuration Surface

Each deployment should eventually be able to choose:

- `ingressMode = PRIVATE_BACKEND_ONLY | PUBLIC_RUNTIME`
- `publicAuthMode = ANONYMOUS_ONLY | ANONYMOUS_AND_AUTHENTICATED | AUTHENTICATED_ONLY`
- allowed public origins
- bootstrap token TTL
- anonymous scopes
- authenticated scopes
- rate-limit profile
- remote authz requirement level

---

## 15) Recommended Product Positioning

### 15.1 Default enterprise posture

Default to:

- private runtime
- storefront backend mediation

because it is safer.

### 15.2 Easy integration posture

Offer public runtime mode as an explicit product capability for:

- low-friction web chat
- public support bots
- catalog assistants
- website FAQ assistants

Make its limitations explicit:

- anonymous mode is lower privilege
- sensitive customer data requires authenticated public mode

---

## 16) Implementation Sequence

1. Add runtime public bearer-token authentication middleware.
2. Add anonymous bootstrap/session issuance endpoint.
3. Add authenticated public token validation support.
4. Derive chat ownership from verified subject, not request-body `userId`.
5. Add deployment-level public ingress configuration.
6. Add origin allowlist and rate-limit policies.
7. Keep connector private and adapt it to consume verified runtime subject context.
8. Add remote authz enrichment for anonymous vs authenticated public modes.
9. Add regression coverage for anonymous public and authenticated public flows.

---

## 17) Verification Requirements

Local and integration verification should cover:

- anonymous bootstrap success
- anonymous token expiry and refresh behavior
- anonymous conversation ownership isolation
- authenticated token validation
- rejection of forged `userId`
- rejection of expired or wrong-audience tokens
- origin restriction behavior
- rate-limit behavior
- fail-closed authz on protected actions

Live verification should cover:

- public anonymous chat happy path
- authenticated public chat happy path
- protected action denial for anonymous mode
- customer-specific retrieval denial without authenticated token

---

## 18) Completion Criteria

This plan is complete when the product can support both:

- a public anonymous chatbot embedded on a website
- a public authenticated chatbot for logged-in users

while ensuring that:

- connector stays private
- runtime does not trust browser-supplied identity fields
- conversation ownership derives from verified auth context
- anonymous mode is intentionally low-privilege
- authenticated mode supports explicit authz enforcement
