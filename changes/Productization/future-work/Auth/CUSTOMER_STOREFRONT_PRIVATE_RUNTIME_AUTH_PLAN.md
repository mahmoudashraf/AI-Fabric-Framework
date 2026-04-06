# Customer Storefront to Private Runtime Authentication and Authorization Plan

Status: detailed planning document (2026-04-06)

This document defines the recommended product design for customer-facing chat deployments where:

- the AI deployment is a normal customer deployment
- the browser does not call the deployment directly
- only the customer's storefront backend can call the runtime
- the connector remains private
- customer-specific authorization is enforced before sensitive retrieval or action execution

This plan is intentionally generic.

It should work for:

- Shopify-backed storefronts
- WooCommerce-backed storefronts
- custom storefronts with their own backend session model

It is not a platform-admin assistant plan and it should not inherit Track C assumptions.

---

## 1) Executive Summary

For customer deployments, the correct baseline is:

- browser -> storefront backend
- storefront backend -> private runtime
- runtime -> private connector
- runtime or connector -> customer authorization service
- connector -> upstream store APIs using deployment-owned credentials

The storefront backend must authenticate as a trusted caller.

The storefront backend must also convey the logged-in customer identity to the runtime through a short-lived signed end-user context token or equivalent signed assertion.

The runtime must not trust:

- request-body `userId`
- query-string `ownerId`
- caller-supplied role fields

Authorization should remain explicit and fail-closed.

The customer or storefront system should remain the source of truth for customer-facing business authorization, while the AI deployment remains the execution surface.

This document defines the default and preferred production posture.

If another document in this folder allows browser-held bearer tokens, that applies only to the explicit `PUBLIC_RUNTIME` mode and not to this private-runtime model.

---

## 2) Why This Model Is Needed

The current product already contains useful primitives:

- runtime remote HTTP authorization in `ai-fabric-runtime`
- connector authz proxy support in the generic REST connector
- deployment-scoped secret management

But the current chat ingress still reflects a trusted-client shape:

- `ChatQueryRequest` accepts `userId`
- conversation retrieval and deletion accept caller-provided ownership identifiers
- runtime chat is not yet built around a verified external customer identity token

That is acceptable for:

- internal demos
- trusted backend callers
- POC wiring

It is not sufficient for a real customer storefront integration.

---

## 3) Goals

This plan should achieve all of the following:

- support a real logged-in storefront customer chatting with the customer's deployed runtime
- keep the runtime private from the browser
- keep the connector private from the browser
- preserve flexible integration with Shopify, WooCommerce, or custom storefronts
- make customer identity authoritative at runtime
- make authorization explicit, customer-controlled, and fail-closed
- keep upstream store credentials in deployment secrets, not in the browser
- avoid baking storefront-specific business logic into the runtime core

---

## 4) Non-Goals

This plan does not require:

- browser-direct connector access
- browser-direct runtime access
- platform-user session auth for storefront customers
- the platform becoming the source of truth for store customer identity
- the platform hardcoding Shopify- or WooCommerce-specific customer policy rules
- replacing the customer's own session/login system

---

## 5) Locked Design Decisions

### 5.1 The browser must not call the deployment directly

The default productized posture for storefront integrations should be:

- no browser-direct runtime access
- no browser-direct connector access

The deployment should be treated as a private application backend dependency of the storefront.

This avoids:

- browser-held deployment credentials
- CORS complexity
- trusting forgeable client identity fields
- exposing connector ingress semantics directly to end users

### 5.2 Storefront backend to private runtime still requires authentication

Private network reachability is not sufficient.

The storefront backend must authenticate to the runtime as a trusted machine caller.

Recommended service-auth options:

- mTLS
- signed service JWT
- deployment-scoped service API key
- trusted gateway identity if the customer environment already has a stronger service mesh or ingress identity model

At least one service-auth mechanism is required.

### 5.3 End-user identity must be conveyed separately from service identity

The runtime must know both:

- which trusted backend is calling
- which logged-in customer that backend is acting for

That means each request needs:

- service-to-service authentication
- end-user context

The end-user context should be a short-lived signed token or signed assertion.

In this private-runtime mode, that end-user token should normally stay server-side within the storefront or app backend.

It does not need to be exposed to the browser unless the integration deliberately switches to the separate public-runtime model.

### 5.4 Runtime is the primary ingress, connector is an internal executor

The runtime should be the public integration point for the storefront backend.

Recommended traffic shape:

- storefront backend -> runtime
- runtime -> connector when actions are needed

The connector should remain private in phase 1 and should not be treated as the main customer-facing ingress.

### 5.5 Runtime must not trust caller-supplied identity fields

The runtime must stop relying on request-supplied identity fields as authoritative.

Examples of fields that must not be trusted on their own:

- `userId`
- `ownerId`
- `role`
- `customerId`
- `tenantId`

Those values may still exist in internal request DTOs for compatibility, but authorization and conversation ownership must be derived from verified auth context instead.

### 5.6 Customer authorization stays customer-owned

For storefront customers, the correct source of truth for business authorization is the customer or storefront domain.

Examples:

- can this user view this order?
- can this user update this cart?
- can this user open a support case for this account?

Those rules belong to the storefront domain, not the platform.

The runtime and connector should therefore consult a customer-provided authorization endpoint or equivalent customer-provided policy component before sensitive retrieval and action execution.

For packaged integrations such as a Shopify app, the customer-owned policy boundary may be implemented by the customer's app backend rather than by the browser-facing storefront itself.

### 5.7 Deployment-owned store credentials stay server-side

Any credentials required to call Shopify, WooCommerce, or a custom store backend must remain:

- deployment-scoped
- server-side
- secret-managed

The storefront browser must never receive:

- store API access tokens
- deployment connector API keys
- deployment runtime admin credentials

---

## 6) Trust Boundaries

Track these boundaries explicitly.

### 6.1 Browser boundary

The browser is an untrusted client.

It can hold:

- storefront session cookies
- storefront-issued frontend tokens

It must not be the source of truth for AI deployment identity.

### 6.2 Storefront backend boundary

The storefront backend is the trusted application backend for the customer.

It is responsible for:

- authenticating the logged-in customer
- creating or forwarding signed end-user context for AI calls
- calling the runtime over a trusted channel

### 6.3 Runtime boundary

The runtime is the trusted AI ingress for the customer deployment.

It is responsible for:

- verifying service auth
- verifying end-user context
- deriving the authoritative user identity
- enforcing chat-session ownership based on that identity
- invoking customer authorization hooks
- orchestrating retrieval and action execution

### 6.4 Connector boundary

The connector is an internal action executor.

It is responsible for:

- calling upstream store APIs
- using deployment secrets
- optionally consulting customer authz endpoints
- never trusting raw caller-supplied user fields without verified upstream context

---

## 7) Recommended End-to-End Flow

### 7.1 Chat query flow

1. Customer logs into the storefront.
2. Storefront backend verifies the customer session.
3. Storefront backend constructs a short-lived signed end-user context token.
4. Storefront backend calls the private runtime with:
   - service authentication
   - end-user context token
   - chat payload
5. Runtime validates:
   - service caller
   - end-user token signature, audience, expiry, and deployment scope
6. Runtime derives the authoritative subject and conversation owner from the validated token.
7. Runtime executes retrieval and orchestration.
8. If retrieval or action policy requires authorization, runtime calls the customer authz endpoint.
9. If an action is needed, runtime calls the private connector.
10. Connector uses deployment-owned credentials to talk to the store or customer backend.
11. Runtime returns the result to the storefront backend.
12. Storefront backend returns the result to the browser.

### 7.2 Conversation read/delete flow

Conversation ownership must use the same derived subject from the verified end-user context token.

The runtime must not accept arbitrary conversation-owner identifiers from the caller as authority.

### 7.3 Action execution flow

1. Runtime determines an action is needed.
2. Runtime or connector sends an authz check to the customer authorization service with the verified subject and requested operation.
3. If denied, runtime returns a clean denial response.
4. If allowed, connector executes the upstream API call using deployment secrets.
5. Runtime returns an audited action result.

---

## 8) Authentication Model

Two distinct auth layers are required.

### 8.1 Layer A: service-to-service authentication

Purpose:

- prove the caller is the trusted storefront backend

Recommended request headers or equivalent:

- `Authorization: Bearer <service-jwt>`
- or `X-AIFABRIC-SERVICE-KEY`
- or mTLS client certificate identity

This layer authenticates the calling system, not the end user.

### 8.2 Layer B: end-user context authentication

Purpose:

- prove which logged-in customer the request is for

Recommended approach:

- short-lived signed JWT issued by the storefront backend

The runtime should validate this token using:

- configured public key or JWKS
- or a shared signing secret in simpler deployments

This layer authenticates the end-user subject for the deployment.

### 8.3 Why both layers are needed

Without service auth:

- any reachable internal caller could impersonate the storefront backend

Without end-user context:

- runtime would know the caller system but not the actual logged-in customer

Both are required.

---

## 9) End-User Context Token Contract

The first productized token should be simple and explicit.

Recommended claims:

- `iss`: issuing storefront backend
- `aud`: runtime deployment audience
- `sub`: logged-in customer id
- `deploymentId`: bound deployment id
- `tenantId` or `customerId`: optional deployment ownership scope
- `sessionId`: storefront session id or equivalent request session reference
- `storeCustomerId`: optional store-native identity
- `scopes`: optional capability hints
- `iat`
- `exp`

Recommended properties:

- short-lived
- deployment-bound
- audience-bound
- signed
- not browser-generated

Recommended rules:

- reject expired tokens
- reject tokens for the wrong deployment
- reject tokens with wrong audience
- reject unsigned or unverifiable tokens

---

## 10) Authorization Model

### 10.1 Customer-owned authz endpoint

The default product model should use a customer-owned authorization endpoint.

Recommended endpoint family:

- `POST /api/authz/check`

This can be:

- a customer backend endpoint
- a storefront backend endpoint
- an authz service behind the customer backend

### 10.2 Authorization request shape

Recommended fields:

- `requestId`
- `userId`
- `sessionId`
- `deploymentId`
- `resourceType`
- `resourceId`
- `operationType`
- `metadata`
- `userAttributes`

The key point is that `userId` and related fields in this request are derived from a validated token, not blindly copied from user input.

### 10.3 Authorization response shape

Recommended response:

- `granted`
- `reason`
- `policyVersion`

Optional later additions:

- filtered scopes
- redacted resource constraints
- allowed entity subsets

### 10.4 Fail-closed behavior

If authz is unavailable or invalid, deny.

This is already aligned with runtime behavior in `RemoteHttpEntityAccessPolicy`.

---

## 11) Runtime Changes Required

### 11.1 Add runtime ingress authentication middleware

Runtime needs a real customer-ingress auth layer for chat and conversation endpoints.

Recommended new responsibilities:

- validate service auth
- validate end-user token
- build authoritative runtime auth context
- expose that context to controllers and orchestration code

### 11.2 Stop trusting request-body `userId`

Current runtime chat DTOs and controllers still allow caller-supplied identity fields.

That must change so that:

- `userId` is derived from verified auth context
- `sessionId` is either derived or validated against the token and caller
- conversation owner is derived from the verified subject

### 11.3 Harden conversation ownership

Conversation read, list, write, and delete must use the verified subject as the owner identity.

Caller-provided ownership identifiers should become:

- ignored
- deprecated
- or internal-only compatibility fields not used for authorization

### 11.4 Preserve admin preview as a separate trust path

Current runtime preview and admin endpoints should remain distinct from storefront customer flows.

Customer ingress auth and admin auth must not collapse into one mechanism.

---

## 12) Connector Changes Required

### 12.1 Keep connector private by default

Connector should not be the browser-facing ingress for storefront customers.

This means:

- no customer-browser CORS dependency for the connector
- no storefront-browser connector API key

### 12.2 Accept verified user context only from trusted upstream

When runtime calls the connector, it may pass end-user context forward.

But that context should come from a verified runtime auth context, not raw user input.

Recommended implementation options:

- signed internal context header
- internal JWT minted by runtime
- explicit structured auth context forwarded over private service-to-service calls

### 12.3 Keep upstream store auth separate

Connector still needs its own upstream auth model for:

- Shopify
- WooCommerce
- custom customer APIs

That is a separate concern from customer identity and authorization.

---

## 13) Secret and Credential Model

### 13.1 Required credential categories

The deployment may require these secret classes:

- storefront-backend to runtime service auth key or verification material
- storefront-issued JWT verification key or shared secret
- customer authorization service auth credential if needed
- upstream store API credentials

### 13.2 Secret storage rules

All of these must stay server-side.

They should be managed as deployment or customer-owned platform secrets, not exposed to the storefront browser.

### 13.3 Deployment-scoped posture

This model should integrate naturally with deployment-scoped secret overrides where customer-specific credentials differ by deployment.

---

## 14) Integration Profiles

This design should be generic from day one.

Separate these concepts.

### 14.1 Customer identity provider profile

Examples:

- `SHOPIFY_CUSTOMER_SESSION`
- `WOOCOMMERCE_CUSTOMER_SESSION`
- `CUSTOM_BACKEND_SESSION`
- `OIDC_FRONTED_BY_CUSTOM_BACKEND`

This profile answers:

- how the storefront backend authenticates the user
- how it mints or signs end-user context for the runtime

### 14.2 Upstream system profile

Examples:

- `SHOPIFY`
- `WOOCOMMERCE`
- `CUSTOM_REST`
- `HEADLESS_COMMERCE_BACKEND`

This profile answers:

- what system the connector calls
- what credentials it needs
- what action routes exist

These two profile types must stay separate.

---

## 15) Current Codebase Alignment

This plan should build on the existing product rather than replacing it.

### 15.1 Useful existing runtime foundations

- `RuntimeAuthzProperties`
- `RemoteHttpEntityAccessPolicy`
- chat-session ownership hooks
- deployment-scoped runtime and connector wiring

### 15.2 Current gaps that must be closed

- runtime chat request currently accepts caller-supplied `userId`
- conversation ownership parameters are caller-supplied
- no dedicated customer-ingress auth layer for private-runtime storefront traffic
- connector is currently shaped around static API-key ingress rather than verified end-user context

---

## 16) Implementation Items

Execute this work in the following order.

1. Define the private-runtime storefront integration contract and lock the deployment posture: browser cannot reach runtime or connector directly.
2. Add runtime ingress authentication middleware for service auth plus end-user context token validation.
3. Introduce a runtime auth context object that exposes authoritative subject, session, deployment scope, and caller identity.
4. Refactor chat and conversation endpoints to derive user identity from runtime auth context instead of request-body or query-string fields.
5. Add deployment configuration for customer-ingress auth mode and verification material.
6. Standardize the remote authz request and response contract for customer-owned authorization services.
7. Wire runtime retrieval and action authorization through the verified subject and remote authz path.
8. Add private runtime-to-connector user-context propagation using a trusted internal token or signed context header.
9. Keep connector private and remove any requirement for customer-browser connector access in the productized storefront path.
10. Add integration profiles separating customer identity provider type from upstream store type.
11. Add deployment secret requirements for service auth, token verification, authz service auth, and upstream store credentials.
12. Add operator visibility for the configured customer-ingress auth posture, authz posture, and verification health.
13. Add local regression for runtime auth middleware, token validation, derived conversation ownership, and remote authz deny behavior.
14. Add live integration verification against a realistic customer-backend-to-private-runtime path.
15. Document operational setup, token rotation, failure modes, and recovery.

---

## 17) Verification and Regression Requirements

This plan should not be considered complete until the following are covered.

### 17.1 Local regression

- service auth is required when the private storefront mode is enabled
- invalid or expired end-user tokens are rejected
- deployment-mismatched tokens are rejected
- runtime derives `userId` from verified auth context
- conversation ownership ignores forged caller-provided user fields
- authz endpoint deny or timeout fails closed
- connector receives only trusted propagated auth context

### 17.2 Integration or live regression

- a simulated storefront backend can chat with the private runtime successfully
- retrieval is allowed only when customer authz grants it
- an action is denied when customer authz denies it
- upstream action execution uses deployment-owned credentials only
- browser direct access is not required for the supported path

---

## 18) Operational Guidance

Recommended deployment posture:

- runtime private
- connector private
- storefront backend trusted
- short-lived end-user tokens
- explicit remote authz
- fail closed on policy errors

Recommended observability:

- log caller system identity
- log subject id
- log deployment id
- log authz decision
- log denied operation and reason
- never log secret values or raw store credentials

---

## 19) Completion Criteria

This plan is complete only when:

- a customer storefront backend can call a private runtime securely
- runtime derives the real end-user subject from verified auth context
- runtime no longer relies on caller-supplied `userId` or `ownerId` for authorization decisions
- customer-owned authz can allow or deny retrieval and actions
- connector remains private in the supported storefront path
- upstream store credentials remain server-side and deployment-scoped
- local and integration regression cover the full trust chain

---

## 20) Immediate Recommendation

The first implementation pass should start with these four items:

1. runtime ingress authentication middleware
2. derived runtime auth context and removal of trusted caller-supplied `userId`
3. standardized remote authz contract for customer backends
4. private runtime-to-connector propagated auth context

That gives the product:

- a secure private-runtime customer integration baseline
- a flexible foundation for Shopify, WooCommerce, and custom storefronts
- explicit separation between caller auth, end-user identity, authorization, and upstream store credentials
