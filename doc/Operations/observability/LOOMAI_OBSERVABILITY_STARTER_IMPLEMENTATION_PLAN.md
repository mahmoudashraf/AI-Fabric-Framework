# LoomAI Observability Starter Implementation Plan

Status: concrete implementation plan (2026-04-22)

This document turns the observability starter design into an executable engineering plan for this repository.

It assumes the following product and engineering rule:

- build one durable observability foundation from day one
- roll it out incrementally across services
- do not build a throwaway implementation that will be replaced later

It should be read with:

- `doc/Productization/future-work/MarketPlace/Products/Strategy/OBSERVABILITY_AND_RELIABILITY_FOUNDATION_PLAN.md`
- `doc/Operations/observability/LOOMAI_OBSERVABILITY_STARTER_DESIGN.md`
- `doc/Operations/observability/CARDINALITY_GOVERNANCE_POLICY.md`

---

## 1) Executive Summary

The correct implementation shape is:

- one shared Spring Boot starter module
- one shared observability configuration model
- one shared logging / metrics / tracing / error baseline
- one shared enforcement point for cardinality and context propagation
- staged adoption across the control-plane-critical services first:
  - `Platfrom/backend`
  - `ai-infrastructure-module/ai-fabric-runtime`
  - `product-services/shopify-bridge-service`

This plan is intentionally **not**:

- a "small temporary MVP" that will later be rewritten
- a full internal observability platform
- a business-logic refactor of runtime, platform, or Shopify services

The permanent value comes from centralizing cross-cutting concerns in one framework module while keeping product and runtime logic unchanged.

The repo now already has meaningful verification and diagnostics surfaces. The starter is therefore not creating release safety from zero. It is standardizing the telemetry foundation underneath the existing release-control, verification, and diagnostics flows.

---

## 2) Hard Decisions

These decisions are part of the implementation baseline.

### 2.1 One mature implementation, not V1 then V2

We will build the starter as the permanent architecture now.

That means:

- the module structure should be production-grade
- the configuration model should be stable
- the rollout can be phased
- the design should not assume a future rewrite

### 2.2 Module location

The starter should live inside the existing framework multi-module tree:

- `ai-infrastructure-module/loomai-observability-starter`

This is the correct location for this repo.

It should not live under:

- `platform/`

because the platform backend, runtime, and product services all need to consume the same shared module and the framework module tree already contains the existing Boot auto-configuration modules.

### 2.3 Rollout strategy

The rollout should be:

1. implement the starter module fully
2. adopt it in Platform backend
3. adopt it in Runtime
4. wire the emitted telemetry and correlation model into the existing verification and diagnostics surfaces
5. adopt it in Shopify Bridge
6. then add deeper service-specific instrumentation where needed

This is phased adoption of one implementation, not phased redesign.

### 2.4 Existing operator surfaces are consumers, not competitors

The starter should support and deepen the existing platform surfaces:

- deployment-scoped verification
- platform verification ops
- deployment diagnostics

It should not attempt to replace them with a second release-ops UI concept inside the starter itself.

### 2.5 Business-logic boundary

The starter should not require changes to:

- orchestration logic
- action execution semantics
- RAG behavior
- deployment lifecycle semantics
- Shopify install or billing logic

It is a cross-cutting infrastructure layer.

Small touch points are acceptable where a service needs to expose already-known context such as:

- `tenantId`
- `merchantId`
- `requestId`

but the starter must not become a reason to rewrite product logic.

### 2.6 Build-vs-buy rule

We build:

- the shared in-process starter
- shared defaults and enforcement
- service wiring
- smoke tests

We do not build:

- a custom log backend
- a custom tracing backend
- a custom alerting system
- a custom metrics storage system

Managed sinks remain the operating assumption.

---

## 3) Why This Must Land Now

The current product and platform direction already assumes multi-service operation, live merchant traffic, and repeated product delivery. The platform also now already contains real release-ops and diagnostics surfaces. The bottleneck is therefore no longer "we need a page for verification" or "we need a page for diagnostics." The bottleneck is telemetry consistency and cross-service correlation behind those pages.

The observability starter is important now because it:

- gives the existing verification and diagnostics pages a consistent telemetry contract
- enables correlation across `deploymentId`, `releaseId`, `verificationRunId`, `requestId`, and `traceId`
- prevents every service from wiring observability differently
- creates a reusable foundation for Shopify and product #2
- enforces cardinality and context rules in code instead of tribal memory
- reduces support and incident-response chaos before the portfolio expands

Without the starter, every additional product multiplies operational inconsistency.

---

## 4) Current Repo Reality

The implementation plan should respect what already exists.

### 4.1 What already exists

- Spring Boot Actuator is already present in the three critical services:
  - `product-services/shopify-bridge-service/pom.xml`
  - `Platfrom/backend/pom.xml`
  - `ai-infrastructure-module/ai-fabric-runtime/pom.xml`
- The services already expose basic management endpoints
- The repo already has concrete observability design documents and cardinality policy
- The codebase already carries tenant-related context in several core paths
- The platform UI already includes:
  - deployment-scoped verification and hosted verification flows
  - platform-admin verification orchestration and release-gate views
  - deployment diagnostics with drift, remediation, logs, and audit evidence

### 4.2 What does not yet exist as a shared implementation

- no shared Boot starter for observability
- no shared MDC filter or correlation baseline
- no shared metric guardrails
- no consistent metric exposure baseline
- no central optional error-tracking integration
- no uniform tracing/context propagation baseline across the three core services
- no common telemetry contract that the existing verification and diagnostics surfaces can rely on across backend, runtime, and bridge

### 4.3 Implementation implication

This is an additive framework extraction underneath an already strong release-ops layer, not a recovery rewrite and not a new UI project.

That lowers risk and makes the starter a good candidate for immediate implementation.

---

## 5) Permanent Scope

This section defines what the starter must contain in its first permanent release.

### 5.1 Module contents

The starter should contain:

- `LoomaiObservabilityAutoConfiguration`
- `LoomaiObservabilityProperties`
- logging configuration and context propagation
- metrics defaults and cardinality enforcement
- tracing configuration hooks
- optional Sentry integration
- reusable test support
- Spring Boot auto-configuration registration

### 5.2 Required consumer properties

Every consuming service must set:

- `loomai.observability.product`
- `loomai.observability.service`

Everything else should have correct defaults or optional opt-in configuration.

### 5.3 Logging baseline

The permanent baseline should include:

- structured JSON logging
- MDC fields for:
  - `deploymentId`
  - `releaseId`
  - `verificationRunId`
  - `requestId`
  - `tenantId`
  - `merchantId`
  - `traceId`
  - `spanId`
  - `product`
  - `service`
- secret and obvious-PII redaction
- shared field naming across services

### 5.4 Metrics baseline

The permanent baseline should include:

- common Micrometer tags:
  - `product`
  - `service`
  - `environment`
- Prometheus endpoint exposure
- runtime cardinality guardrails from the governance policy
- support for safe per-tenant metrics only where explicitly allowed

### 5.5 Tracing baseline

The permanent baseline should include:

- trace context propagation hooks
- service name alignment
- propagation of `deploymentId`, `releaseId`, and `verificationRunId` where those contexts exist
- tenant context propagation into tracing baggage or equivalent
- compatibility with OpenTelemetry-based deployment configuration

The starter should own in-process configuration and propagation, not deployment of the Java agent itself.

### 5.6 Error-tracking baseline

The permanent baseline should include:

- optional Sentry wiring
- shared event enrichment with:
  - `product`
  - `service`
  - `tenantId`
  - `merchantId`
  - `traceId`
- redaction parity with logging

### 5.7 Test baseline

The permanent baseline should include:

- starter-level unit tests
- starter-level integration tests
- one reusable observability smoke-test pattern for consumer services

### 5.8 Operator-surface integration baseline

The first permanent release should define a stable telemetry and correlation model consumable by the existing control-plane surfaces, specifically:

- `/verification`
- `/verification-ops`
- `/diagnostics`

This means the starter should emit or preserve stable identifiers and status dimensions that can be joined or surfaced consistently:

- `deploymentId`
- `releaseId`
- `verificationRunId`
- `requestId`
- `traceId`

The starter does not render UI itself, but it should make those pages easier to deepen with telemetry drill-down, trend, and alert/SLO cards later.

---

## 6) Explicit Non-Goals For Initial Implementation

These items may be valid later, but they are not required for the first complete implementation of the starter.

- custom dashboards generated by the starter
- alert-rule generation by the starter
- custom APM backend work
- full compile-time lint processor in the first delivery
- custom wrappers for every external SDK before the baseline ships
- remote sink verification endpoint that proves Grafana or Sentry delivery end-to-end
- replacing the existing verification or diagnostics pages
- duplicating release-gate logic inside the starter

The rule is simple:

- build the durable shared baseline first
- add deeper automation after adoption proves where it helps

This is still one mature implementation because the core module boundary and configuration model remain stable.

---

## 7) Architecture Refinements Relative To The Existing Design

The current design document is strong, but the implementation plan adopts a few refinements to keep execution clean.

### 7.1 Keep the module in the framework tree

Implementation target:

- `ai-infrastructure-module/loomai-observability-starter`

not:

- `platform/loomai-observability-starter`

### 7.2 Do not block release on compile-time lint

The annotation-processor lint idea is good, but it should be a follow-on track unless it is very cheap to implement.

Reason:

- it is not required to establish the starter architecture
- it introduces more build complexity than the logging/metrics baseline itself
- it should not delay adoption in the three critical services

If easy, it can land in the same stream. If not, it should be tracked separately.

### 7.3 Do not make remote sink verification a first blocker

A local self-check endpoint is acceptable if it proves local wiring.

It should not attempt to prove:

- logs arrived in Loki
- traces arrived in Tempo
- metrics were ingested by the hosted backend

Those belong to environment verification and deployment checks, not to the starter itself.

### 7.4 Treat Java-agent rollout as deployment concern

The starter should prepare services for tracing and context propagation.

It should not be responsible for:

- installing the OpenTelemetry Java agent
- managing `JAVA_TOOL_OPTIONS`

That belongs in deployment templates and hosted environment configuration.

---

## 8) Concrete Implementation Plan

### Phase 1: Starter module creation

Create:

- `ai-infrastructure-module/loomai-observability-starter/pom.xml`
- `src/main/java/...`
- `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- starter resource files such as shared logback config and whitelist configuration

Add the module to:

- `ai-infrastructure-module/pom.xml`

Exit condition:

- the starter builds as an ordinary framework module
- Boot auto-configuration registration works

### Phase 2: Shared configuration and logging foundation

Implement:

- `LoomaiObservabilityProperties`
- JSON logback baseline
- request filter / interceptor for MDC context
- request-id generation when absent
- redaction hooks for obvious secrets and common PII

Exit condition:

- a consumer service can add the starter and emit structured logs with consistent context fields

### Phase 3: Metrics foundation

Implement:

- common Micrometer tags
- Prometheus exposure defaults
- cardinality meter filter
- whitelist loading

Exit condition:

- metric names and tags are normalized
- disallowed label patterns are rejected or downgraded consistently

### Phase 4: Tracing and error hooks

Implement:

- trace context propagation support
- OpenTelemetry-friendly service configuration
- optional Sentry auto-configuration and event enrichment

Exit condition:

- a consumer service can carry request and tenant context consistently into trace and error events

### Phase 5: Test support and starter verification

Implement:

- starter unit tests
- minimal integration tests
- reusable smoke-test support for consuming services

Exit condition:

- starter behavior is testable without external hosted infrastructure

### Phase 6: Consumer adoption

Adopt in order:

1. `product-services/shopify-bridge-service`
2. `Platfrom/backend`
3. `ai-infrastructure-module/ai-fabric-runtime`

Per service, do:

- add dependency
- add required properties
- expand management endpoint exposure
- resolve any service-specific context extraction hooks
- add one observability smoke test

Exit condition:

- all three critical services run on the same observability baseline

---

## 9) Service Touch Points

This section makes the blast radius explicit.

### 9.1 Shopify Bridge

Expected changes:

- add starter dependency
- set `loomai.observability.product=shopify-companion`
- set `loomai.observability.service=shopify-bridge`
- expand management exposure to metrics/prometheus
- wire merchant/shop context into MDC if not already surfaced automatically

No intended changes:

- Shopify OAuth flow
- billing logic
- sync logic
- storefront product logic

### 9.2 Platform backend

Expected changes:

- add starter dependency
- set `loomai.observability.product=platform-control-plane` or final canonical product name
- set `loomai.observability.service=platform-backend`
- expand management exposure
- expose platform request/tenant context where already known

No intended changes:

- deployment provisioning semantics
- release/version logic
- marketplace composition logic

### 9.3 Runtime

Expected changes:

- add starter dependency
- set `loomai.observability.product` to the deployed runtime product family or a stable runtime product value
- set `loomai.observability.service=runtime`
- expand management exposure
- resolve tenant context from existing runtime auth context

No intended changes:

- orchestration behavior
- action routing behavior
- RAG behavior
- prompt or shell behavior

---

## 10) Acceptance Criteria

The implementation is successful when all of the following are true:

1. the repo contains a real shared module at `ai-infrastructure-module/loomai-observability-starter`
2. the starter is registered through Spring Boot auto-configuration
3. Shopify Bridge, Platform backend, and Runtime consume the same starter
4. all three services emit structured logs with consistent context keys
5. all three services expose Prometheus-compatible metrics
6. cardinality rules are enforced centrally rather than by service-by-service discipline
7. optional Sentry integration can be enabled by configuration without custom wiring per service
8. request and tenant context flow across the core services without business-logic rewrites
9. each of the three services has at least one observability smoke test
10. no critical feature flow required a redesign of runtime or product business logic

---

## 11) Risks and Mitigations

- **Risk: the starter becomes a second platform project.**
  Mitigation: keep scope limited to shared in-process observability concerns and sink integration hooks.

- **Risk: tracing work expands into deployment work too early.**
  Mitigation: keep Java-agent rollout and hosted sink setup outside the starter implementation boundary.

- **Risk: platform naming becomes inconsistent across products.**
  Mitigation: define canonical `product` and `service` values before the first adoption PR.

- **Risk: metric cardinality enforcement is too weak or too aggressive.**
  Mitigation: centralize the whitelist and validate against the three initial services before wider rollout.

- **Risk: adoption causes hidden service-specific context gaps.**
  Mitigation: rollout to Shopify Bridge first, then Platform, then Runtime; fix extraction hooks as they appear without changing business logic.

---

## 12) Recommended Immediate Next Action

The next action should be:

1. approve this implementation plan as the execution baseline
2. treat `LOOMAI_OBSERVABILITY_STARTER_DESIGN.md` as the architectural reference
3. begin Phase 1 and Phase 2 in the framework tree

The key principle remains:

- one mature implementation
- no throwaway observability layer
- no product-logic rewrite
- phased adoption for safety
