# Implemented Authentication and Authorization Flow Guide

Status: implementation-aligned guide (2026-04-08)

This guide summarizes the auth model that the codebase now implements or expects as the current target contract.

It is not a replacement for the design plans in this folder. It is the practical companion for:

- runtime and platform implementation work
- POC and widget integration work
- deployment verification and operations

Related documents:

- `AUTH_IMPLEMENTATION_SEQUENCE_PLAN.md`
- `CUSTOMER_STOREFRONT_PRIVATE_RUNTIME_AUTH_PLAN.md`
- `PUBLIC_RUNTIME_BROWSER_CHAT_AUTH_PLAN.md`
- `SHOPIFY_APP_ARCHITECTURE_PLAN.md`

---

## 1. Core Rules

The current auth model is built around these locked rules:

- runtime and connector must derive identity from verified auth context
- caller-supplied `userId`, `ownerId`, role, customer, or tenant fields are not authoritative identity
- the connector is treated as an internal executor, not the public integration boundary
- secure customer-facing chat should use runtime surfaces, not direct connector ingress
- anonymous public chat still requires a short-lived anonymous bearer token
- platform or host proxies must fail closed when required runtime private auth material is missing

The practical effect is:

- secure chat routes are under `/api/chat/me/*`
- anonymous public bootstrap is under `/api/public/chat/session`
- connector read-style operational surfaces are now exposed through runtime admin proxy routes

---

## 2. Implemented Modes

### 2.1 Private runtime mode

This is the default production posture.

Shape:

- browser -> trusted host or platform backend
- trusted host or platform backend -> private runtime
- runtime -> private connector

Identity:

- caller is authenticated as a trusted backend
- end-user identity is conveyed as verified auth context

Use this for:

- enterprise storefront backends
- first-party platform proxy paths
- POC migration path
- assistant or packaged integrations that should not expose runtime directly

### 2.2 Public runtime mode

This is an explicit opt-in posture.

Shape:

- browser -> runtime
- runtime -> private connector

Identity:

- browser sends a short-lived bearer token
- token may represent:
  - an authenticated end user
  - an anonymous public session

Use this for:

- low-friction public chat
- anonymous storefront chat
- widget-only deployments where direct browser access is intentional

### 2.3 Internal platform/operator mode

This is the first-party operational posture used by:

- hosted verification
- POC backend proxy
- runtime-backed admin reads
- platform vector and operational probes

It is implemented as a private-runtime variant using:

- trusted backend API key
- optional signed private authorization assertion

---

## 3. Runtime Entry Surfaces

### 3.1 Verified chat routes

These are the secure runtime chat routes for verified callers:

- `POST /api/chat/me/query`
- `POST /api/chat/me/suggestions`
- `GET /api/chat/me/auth-context`
- `GET /api/chat/me/conversations`
- `GET /api/chat/me/conversations/{conversationId}`
- `DELETE /api/chat/me/conversations/{conversationId}`

These routes are identity-derived:

- ownership comes from verified auth context
- legacy query-param identity on these routes is rejected

These routes should be the normal integration target for:

- private-runtime callers
- secure widget modes
- platform POC proxy

### 3.2 Public anonymous bootstrap

Anonymous public chat bootstrap is:

- `POST /api/public/chat/session`

Behavior:

- runtime is the default issuer of the anonymous session token
- browser does not self-issue anonymous identity
- unexpected request fields are rejected
- runtime should apply origin checks, rate limiting, and abuse controls here

The issued token represents an anonymous session identity, not a real authenticated user.

### 3.3 Runtime-backed connector operational reads

If the connector is private, supported operational reads should come through runtime.

Current runtime admin proxy surfaces include:

- `GET /api/admin/connector/overview`
- `GET /api/admin/connector/health`
- `GET /api/admin/connector/actions/overview`
- `GET /api/admin/connector/config`
- `GET /api/admin/connector/logs`
- `GET /api/admin/connector/actions/{actionId}`

This keeps connector diagnostics, config summaries, and action catalog reads available without re-opening direct connector access.

---

## 4. Auth Material by Mode

### 4.1 Private runtime mode

Required auth layers:

- trusted caller authentication
- verified end-user or system auth context

In the platform’s current first-party implementation, runtime private access is carried by:

- `X-AIFABRIC-RUNTIME-API-KEY`
- `X-AIFABRIC-RUNTIME-AUTHORIZATION`

Platform-managed deployments source those values from platform-side secrets and signed assertions. Consumers should not hardcode them in browser code.

### 4.2 Public runtime authenticated mode

Required auth material:

- browser-safe short-lived bearer token

This token should carry bounded auth context such as:

- subject
- deployment
- customer or tenant when applicable
- session
- scopes
- expiry

### 4.3 Public runtime anonymous mode

Required auth material:

- short-lived anonymous bearer token

Default issuer:

- runtime bootstrap endpoint at `/api/public/chat/session`

This token still exists even though the user is anonymous. Anonymous must not mean tokenless.

---

## 5. Ownership and Conversation Semantics

Conversation ownership is now expected to be auth-derived.

Important distinctions:

- `conversationId` identifies a chat thread
- session identity comes from verified auth context
- ownership for `/api/chat/me/conversations*` comes from the resolved identity, not caller query params

For anonymous public chat:

- the anonymous bearer token carries the session identity
- runtime can persist conversations against that anonymous owner identity

For private-runtime callers:

- the proxy or host does not supply raw public `ownerId`
- runtime resolves the effective owner from verified auth context

---

## 6. Widget and Host Integration Expectations

The widget can still be the main storefront chat UI, but it must honor auth mode.

Secure integration modes should target runtime, not connector:

- `backend-mediated-private-runtime`
- `public-runtime-authenticated`
- `public-runtime-anonymous`

The old static-header pattern is not the preferred production path.

The widget should:

- call `/api/chat/me/*` for verified flows
- call `/api/public/chat/session` only for public anonymous bootstrap
- avoid sending raw browser `userId` or `ownerId` as authoritative identity

---

## 7. POC and Platform Proxy Adaptation

The platform POC path is no longer a special exemption.

The first-party platform backend must follow the same secured runtime contract as any other private-runtime caller.

That means:

- POC chat should use verified `/api/chat/me/*` routes
- POC import and operational reads should prefer runtime-backed secured transport
- missing trusted-backend auth or private assertion material should fail closed

This is important because POC is a live first-party consumer and a reference path for later assistant work.

---

## 8. Verification and Operations

Hosted verification and manual verification now depend on the same runtime private posture.

The deployment verification flow expects:

- platform operator access to fetch hosted verification context
- `APP_ADMIN_API_KEY` for runtime admin/application verification
- runtime private headers or secrets when runtime-backed operational verification is required

The state suite and deployment verification scripts should therefore be treated as auth-sensitive regression lanes, not just functional tests.

Operational rule:

- if a verification path needs runtime-backed connector reads, it should go through runtime admin surfaces
- direct connector compatibility should not be the primary verification path

---

## 9. Fail-Closed Rules

The following fail-closed rules are intentional:

- secure `/api/chat/me/*` routes reject legacy identity query params
- anonymous public mode does not allow browser self-issued identity
- missing runtime trusted-backend auth should block first-party private-runtime flows
- auth-disabled development shortcuts must not silently become privileged production behavior

If local development needs a shortcut, it should be clearly isolated as non-production behavior and never represent real customer auth.

---

## 10. What This Guide Means for New Work

New runtime, widget, POC, assistant, or storefront work should assume:

- runtime is the customer-facing chat boundary
- connector is private
- identity comes from verified auth context
- public anonymous chat still requires runtime-issued session tokens
- platform and host proxies are just private-runtime callers, not separate security models

If a new integration needs chat, it should start by choosing one of these:

1. private runtime
2. public runtime authenticated
3. public runtime anonymous

It should not invent a fourth identity model.
