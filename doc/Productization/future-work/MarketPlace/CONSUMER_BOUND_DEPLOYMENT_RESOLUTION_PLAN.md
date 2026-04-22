# Consumer-Bound Deployment Resolution Plan

Status: implementation plan (2026-04-16)

This document defines a simplified external identity model for deployments.

The goal is:

- customers may own multiple deployments
- each deployment may be consumed by one external consumer
- external backends/frontends should use a stable `consumerId`
- the platform resolves `consumerId -> current deployment`
- the platform returns the same `status` and `credentials` integration contract it already returns today

This is intentionally simpler than a generic alias or handle-routing system.

---

## 1) Executive Summary

The current public provisioning model exposes deployment-centric public surfaces:

- `POST /api/public/deployments`
- `GET /api/public/deployments/{deploymentId}/status`
- `GET /api/public/deployments/{deploymentId}/credentials`

That is good enough for provisioning and controlled integrations, but it is weak as a long-lived external identity because external callers must keep a concrete `deploymentId`.

The proposed model adds a new customer-owned object:

- `Consumer`

Each `Consumer` is bound to exactly one active deployment at a time.
External callers use `consumerId` instead of `deploymentId`.
The platform resolves the binding and returns the same integration payloads it already returns today.

This gives:

- stable external identity
- clean deployment swaps and rollback
- no runtime auth rewrite
- no platform proxying of live chat traffic

---

## 2) Problem Statement

Today the platform already supports:

- stable internal deployment ids
- public provisioning bindings through `externalDeploymentKey`
- runtime connection discovery through `status` and `credentials`
- multiple runtime auth postures

But the current model still leaves one gap:

- if a customer wants to replace one deployment with another for the same external consumer, the external integrator is still effectively tied to a concrete `deploymentId`

Examples:

- storefront widget should keep one stable consumer identity while the customer swaps the underlying deployment
- customer backend should not need to reconfigure a new deployment id after every migration or replacement
- rollback should be a binding change, not an external integration rewrite

---

## 3) Design Goal

Introduce a stable customer-owned external identity:

- `consumerId`

And make the platform responsible for:

- resolving the current deployment for that consumer
- returning the normal deployment integration contract

Important rule:

- `consumerId` is a routing key, not a secret
- current auth modes remain the source of truth for authorization

---

## 4) Non-Goals

This plan does not introduce:

- a new runtime auth model
- a new runtime proxy in front of chat
- a custom domain router
- a generic global alias system across all entities
- bare `consumerId` as the universal authenticator

This plan also does not replace:

- current deployment-based public provisioning APIs
- current deployment `status` or `credentials` payloads

It adds a new stable lookup surface above them.

---

## 5) Domain Model

### 5.1 `Consumer`

Recommended fields:

- `id`
- `customerId`
- `consumerId`
- `displayName`
- `description`
- `status`
- `exposureMode`
- `createdAt`
- `updatedAt`

Rules:

- `consumerId` is customer-scoped and stable
- `consumerId` should be URL-safe and human-readable
- `consumerId` is immutable once created

Recommended `exposureMode` values:

- `PRIVATE_BACKEND`
- `PUBLIC_BROWSER`

This is not a new auth model.
It is only a discovery and governance hint for which integration posture the platform should expose.

### 5.2 `ConsumerDeploymentBinding`

Recommended fields:

- `id`
- `consumerId`
- `deploymentId`
- `active`
- `createdAt`
- `updatedAt`

Rules:

- one consumer has exactly one active deployment binding
- one deployment may have at most one active consumer binding

### 5.3 `ConsumerBindingHistory`

Recommended fields:

- `id`
- `consumerId`
- `fromDeploymentId`
- `toDeploymentId`
- `reason`
- `actor`
- `createdAt`

Rules:

- every rebind writes history
- history is retained for audit and rollback posture

---

## 6) Public Contract Shape

The simplest path is to add consumer-based parallel routes:

- `GET /api/public/consumers/{consumerId}/status`
- `GET /api/public/consumers/{consumerId}/credentials`

These routes should:

1. resolve `consumerId`
2. load the active bound deployment
3. return the same underlying payload shape used today for deployment-based responses

Recommended response additions:

- `consumerId`
- `boundDeploymentId`

Everything else should remain aligned with current public responses:

- deployment status
- health summary
- active version
- runtime base URL
- integration summary

This keeps the consumer route additive and low-risk.

---

## 7) Relationship To Current Public Provisioning Model

Current public provisioning concepts remain valid:

- `externalDeploymentKey` stays the idempotency key for public deployment creation
- `deploymentId` stays the concrete deployment identity
- current deployment-centric routes remain supported

New rule:

- `externalDeploymentKey` is for provisioning lifecycle
- `consumerId` is for external consumption lifecycle

This is an important separation.

Provisioning creates or updates deployments.
Consumers represent stable external integrations.

---

## 8) Auth Compatibility

This plan does not contradict current auth modes.

Current auth remains:

- `PLATFORM_PROXY_SESSION`
- `PRIVATE_RUNTIME_BACKEND_MEDIATED`
- `PUBLIC_RUNTIME_AUTHENTICATED`
- `PUBLIC_RUNTIME_ANONYMOUS`

Consumer resolution happens before runtime auth posture selection.

Flow:

1. caller requests `consumerId`
2. platform resolves current deployment binding
3. platform returns the same `integration` contract as today
4. caller follows the advertised runtime auth mode

Important rule:

- `consumerId` must not be treated as a credential
- existing platform/public/runtime auth checks continue to decide whether the caller may access the surface

Recommended policy:

- `PRIVATE_BACKEND` consumers should only expose backend-mediated or authenticated integration guidance
- `PUBLIC_BROWSER` consumers may expose public-runtime bootstrap hints when the bound deployment supports them

---

## 9) Admin UI Shape

Customer or platform admin should be able to:

- create a consumer
- list consumers for a customer
- view current bound deployment
- bind or rebind a consumer to a deployment
- view binding history
- see current integration posture

Minimal UI screens:

1. `Customers -> Consumers`
2. `Consumer detail`
3. `Bind / Rebind deployment`
4. `Binding history`

The deployment UI should also show whether a deployment is already consumed by a consumer.

---

## 10) Operational Rules

### 10.1 Rebind

When a consumer is rebound:

- new requests resolve to the new deployment immediately after the binding change is committed
- old runtime sessions are not forcibly proxied or migrated
- session continuity remains the responsibility of the current runtime auth/session model

### 10.2 Archive

If a bound deployment is archived:

- consumer resolution should report blocked or inactive status
- `credentials` should not pretend the consumer is healthy

### 10.3 Delete

If a bound deployment is hard-deleted:

- the consumer must become unbound or blocked
- delete must fail or require reassignment if a live consumer still points at the deployment

Recommended first rule:

- block hard delete while an active consumer binding exists

That is safer than silently orphaning public integrations.

---

## 11) Verification Requirements

Required verification scenarios:

1. create consumer and bind deployment
2. fetch consumer `status`
3. fetch consumer `credentials`
4. confirm payload matches deployment-based payload plus consumer metadata
5. rebind consumer to a second deployment
6. confirm the same `consumerId` now resolves to the new deployment
7. confirm binding history is recorded
8. confirm archived deployment returns blocked posture
9. confirm hard delete is blocked while active consumer binding exists
10. confirm current auth postures still flow through unchanged

Live verification should include:

- one `PRIVATE_BACKEND` consumer
- one `PUBLIC_BROWSER` consumer

---

## 12) Implementation Waves

### Wave 0: Contract and Schema

Build:

- `Consumer` entity
- `ConsumerDeploymentBinding` entity
- `ConsumerBindingHistory` entity
- migration scripts

Acceptance:

- unique consumer ids per customer
- one active binding per consumer
- one active consumer per deployment

### Wave 1: Admin API and UI

Build:

- create/list/get consumers
- bind/rebind endpoint
- binding history endpoint
- admin UI

Acceptance:

- customer/platform admin can fully manage consumers and bindings

### Wave 2: Public Consumer Resolution

Build:

- `GET /api/public/consumers/{consumerId}/status`
- `GET /api/public/consumers/{consumerId}/credentials`

Acceptance:

- responses align with current deployment-based public contract
- consumer metadata added cleanly

### Wave 3: Governance and Guardrails

Build:

- block delete while consumer binding exists
- archive and binding posture checks
- audit events for create, bind, rebind, unbind

Acceptance:

- destructive operations cannot silently break public consumers

### Wave 4: Verification and Live Rollout

Build:

- automated tests
- hosted verification scripts
- admin regression coverage

Acceptance:

- rebind is proven live without external integration changes

---

## 13) Complexity Assessment

This is a medium-sized feature.

Complexity by area:

- data model: low to medium
- backend API: medium
- admin UI: medium
- public contract: low
- auth impact: low
- verification: medium

This is much smaller than:

- building a new runtime gateway
- replacing public provisioning with a new resolve architecture
- inventing a second runtime auth model

---

## 14) Recommended Decision

This feature is worth doing.

Reasons:

- solves the real external-stability gap in the current model
- does not fight current runtime auth architecture
- keeps external integrations stable while deployments are replaced
- is simpler and more product-shaped than a generic alias system

Recommended rule:

- external consumers should prefer `consumerId`
- internal platform operations should continue to use `deploymentId`

That keeps external identity stable and internal control concrete.
