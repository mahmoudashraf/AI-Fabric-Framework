# LoomAI Observability Starter Implementation Checklist

Status: execution checklist (2026-04-22)

This document is the task checklist companion to:

- `./LOOMAI_OBSERVABILITY_STARTER_IMPLEMENTATION_PLAN.md`

Use this document for execution tracking.

Use the implementation plan for:

- architecture decisions
- boundaries
- rollout rationale
- acceptance criteria

---

## 0) Pre-Start Checklist

- [ ] Confirm the implementation baseline is the permanent architecture, not a throwaway observability layer
- [ ] Confirm module location: `ai-infrastructure-module/loomai-observability-starter`
- [ ] Confirm initial adoption targets:
  - `Platfrom/backend`
  - `ai-infrastructure-module/ai-fabric-runtime`
  - `product-services/shopify-bridge-service`
- [ ] Confirm canonical `product` names for the initial consumers
- [ ] Confirm canonical `service` names for the initial consumers
- [ ] Confirm managed observability sinks remain external and are not part of this coding stream
- [ ] Confirm collector-first transport is the baseline:
  - services emit locally
  - collectors batch and forward
  - request paths never call managed observability vendors directly
- [ ] Confirm compile-time lint is optional follow-on work, not a delivery blocker
- [ ] Confirm the existing operator surfaces that should consume the telemetry contract:
  - `/verification`
  - `/verification-ops`
  - `/diagnostics`
- [ ] Confirm platform summaries should be materialized asynchronously instead of built by live multi-provider fan-out on each page request

Exit gate:

- [ ] Team agrees on module location, naming, and rollout order

---

## 1) Module Creation

### 1.1 Multi-module registration

- [ ] Create module directory: `ai-infrastructure-module/loomai-observability-starter`
- [ ] Add module to `ai-infrastructure-module/pom.xml`
- [ ] Create `pom.xml` for the starter module
- [ ] Align dependency versions with the existing framework BOM and Spring Boot version

### 1.2 Spring Boot auto-configuration structure

- [ ] Create base package structure for the starter
- [ ] Create `LoomaiObservabilityAutoConfiguration`
- [ ] Create `LoomaiObservabilityProperties`
- [ ] Create `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- [ ] Add any required resource files under `src/main/resources`

### 1.3 Build verification

- [ ] Confirm the new module builds independently
- [ ] Confirm the parent multi-module build recognizes the new module
- [ ] Confirm Boot auto-configuration registration is discovered correctly

Exit gate:

- [ ] Starter module exists and builds cleanly inside the framework tree

---

## 2) Shared Configuration Model

### 2.1 Required properties

- [ ] Add required properties:
  - `loomai.observability.product`
  - `loomai.observability.service`

### 2.2 Optional configuration groups

- [ ] Add logging configuration group
- [ ] Add metrics configuration group
- [ ] Add tracing configuration group
- [ ] Add error-tracking configuration group
- [ ] Add health/self-check configuration group if still needed after implementation review

### 2.3 Defaulting rules

- [ ] Define safe defaults for local development
- [ ] Define safe defaults for production deployments
- [ ] Keep secrets env-driven rather than file-driven

Exit gate:

- [ ] Property model is stable and clear enough for all three initial consumers

---

## 3) Logging Foundation

### 3.1 Structured log baseline

- [ ] Add shared JSON logging configuration
- [ ] Add `AsyncAppender` baseline so logs are dispatched locally without request-path remote shipping
- [ ] Standardize fields:
  - `timestamp`
  - `level`
  - `logger`
  - `message`
  - `product`
  - `service`
  - `environment`
  - `deploymentId`
  - `releaseId`
  - `verificationRunId`
  - `requestId`
  - `tenantId`
  - `merchantId`
  - `traceId`
  - `spanId`
  - `errorClass`

### 3.2 Context propagation

- [ ] Add request filter or interceptor for MDC population
- [ ] Generate a `requestId` when absent
- [ ] Surface `deploymentId`, `releaseId`, and `verificationRunId` when those contexts already exist
- [ ] Read `tenantId` from existing request/auth context when available
- [ ] Read `merchantId` from existing Shopify context when available
- [ ] Clear MDC safely at request completion

### 3.3 Redaction

- [ ] Redact obvious secrets and auth tokens
- [ ] Redact obvious PII classes that are already agreed in the design/policy docs
- [ ] Ensure logging redaction rules are reusable by error tracking where possible

### 3.4 Logging performance and backpressure

- [ ] Keep caller data disabled by default
- [ ] Define queue size and discarding policy for the async appender
- [ ] Ensure WARN / ERROR are preserved preferentially under pressure
- [ ] Ensure logging never performs managed-sink I/O on the request path

### 3.5 Logging verification

- [ ] Add tests for request-context population
- [ ] Add tests for correlation-field population when context exists
- [ ] Add tests for missing-context fallback behavior
- [ ] Add tests for secret redaction
- [ ] Add tests or config assertions for async appender/backpressure behavior where practical

Exit gate:

- [ ] A consuming service emits structured logs with the shared field model and context propagation

---

## 4) Metrics Foundation

### 4.1 Micrometer defaults

- [ ] Add common tags:
  - `product`
  - `service`
  - `environment`
- [ ] Ensure Prometheus-compatible exposure works with Spring Actuator
- [ ] Confirm starter does not conflict with existing service metrics
- [ ] Ensure high-cardinality context stays out of meter tags by default

### 4.2 Cardinality enforcement

- [ ] Implement cardinality meter filter
- [ ] Add central whitelist resource/config
- [ ] Enforce downgrade or rejection behavior for forbidden labels
- [ ] Ensure route-template normalization for endpoint labels where applicable

### 4.3 Policy alignment

- [ ] Align implementation with `CARDINALITY_GOVERNANCE_POLICY.md`
- [ ] Verify allowed `tenantId` / `merchantId` metric usage paths
- [ ] Verify banned labels are rejected consistently
- [ ] Align low-cardinality metric tags with Micrometer Observation API conventions
- [ ] Treat high-cardinality identifiers as logs/traces data, not general metric tags

### 4.4 Metrics verification

- [ ] Add unit tests for allowed labels
- [ ] Add unit tests for banned labels
- [ ] Add unit tests for whitelist behavior
- [ ] Add unit tests for endpoint normalization if implemented

Exit gate:

- [ ] Metrics are exposed consistently and guarded centrally against cardinality mistakes

---

## 5) Tracing and Error Hooks

### 5.1 Tracing support

- [ ] Add trace-context propagation support
- [ ] Align service naming with starter properties
- [ ] Propagate `deploymentId`, `releaseId`, and `verificationRunId` where relevant
- [ ] Propagate `tenantId` into tracing context or baggage
- [ ] Keep Java-agent installation outside starter responsibilities
- [ ] Target a collector-first OTLP path instead of service-to-vendor direct export where possible

### 5.2 Error tracking

- [ ] Add optional Sentry auto-configuration
- [ ] Enrich events with:
  - `product`
  - `service`
  - `tenantId`
  - `merchantId`
  - `traceId`
- [ ] Reuse or align redaction behavior with logging
- [ ] Keep error shipping non-blocking from the point of view of the request path

### 5.3 Verification

- [ ] Add tests for optional-on / optional-off Sentry behavior
- [ ] Add tests for trace context propagation hooks where practical

Exit gate:

- [ ] The starter supports tracing and optional error tracking without custom wiring in each service

---

## 6) Test Support

### 6.1 Starter tests

- [ ] Add starter unit test suite
- [ ] Add starter integration tests
- [ ] Add tests for property binding
- [ ] Add tests for auto-configuration conditions

### 6.2 Consumer smoke-test support

- [ ] Define a reusable observability smoke-test pattern
- [ ] Keep the smoke-test support usable without hosted sink dependencies
- [ ] Document how consuming services should use it

### 6.3 Operator-surface contract

- [ ] Define the starter-emitted identifiers and status dimensions the platform should surface into `/verification`, `/verification-ops`, and `/diagnostics`
- [ ] Confirm how logs, traces, and errors will be correlated or deep-linked from those pages
- [ ] Keep the UI implementation outside the starter, but document the telemetry contract clearly
- [ ] Confirm the platform will read precomputed diagnostic summaries first and only pivot to raw evidence on demand

Exit gate:

- [ ] The starter can be validated locally and in CI without relying on external observability backends

---

## 7) Platform Backend Adoption

### 7.1 Dependency and configuration

- [ ] Add starter dependency to `Platfrom/backend/pom.xml`
- [ ] Add `loomai.observability.product`
- [ ] Add `loomai.observability.service`
- [ ] Expand management exposure to include metrics/prometheus as required

### 7.2 Context wiring

- [ ] Confirm request context propagation in platform APIs
- [ ] Confirm `deploymentId`, `releaseId`, and `verificationRunId` exposure in release and verification flows where those values already exist
- [ ] Confirm `tenantId` exposure where platform flows already carry tenant data
- [ ] Add only minimal hooks required to surface existing context

### 7.3 Validation

- [ ] Add one observability smoke test for Platform backend
- [ ] Verify structured logs
- [ ] Verify metrics exposure
- [ ] Verify the backend can support the telemetry contract needed by `/verification`, `/verification-ops`, and `/diagnostics`

Exit gate:

- [ ] Platform backend runs on the starter baseline without deployment-lifecycle or marketplace logic rewrites

---

## 8) Runtime Adoption

### 8.1 Dependency and configuration

- [ ] Add starter dependency to `ai-infrastructure-module/ai-fabric-runtime/pom.xml`
- [ ] Add `loomai.observability.product`
- [ ] Add `loomai.observability.service`
- [ ] Expand management exposure to include metrics/prometheus as required

### 8.2 Context wiring

- [ ] Resolve `tenantId` from existing runtime auth context
- [ ] Resolve `requestId` from existing runtime/webhook/request flow where available
- [ ] Surface `deploymentId`, `releaseId`, and `verificationRunId` in runtime-owned verification or execution flows where known
- [ ] Add only minimal integration hooks to surface already-known context

### 8.3 Validation

- [ ] Add one observability smoke test for Runtime
- [ ] Verify structured logs
- [ ] Verify metrics exposure
- [ ] Verify backend-to-runtime verification flows preserve correlation fields end-to-end

Exit gate:

- [ ] Runtime runs on the starter baseline without orchestration or RAG behavior changes

---

## 9) Shopify Bridge Adoption

### 9.1 Dependency and configuration

- [ ] Add starter dependency to `product-services/shopify-bridge-service/pom.xml`
- [ ] Add `loomai.observability.product`
- [ ] Add `loomai.observability.service`
- [ ] Expand management exposure to include metrics/prometheus as required

### 9.2 Context wiring

- [ ] Confirm `merchantId` source for Shopify requests
- [ ] Confirm `tenantId` mapping for Shopify requests
- [ ] Add only the minimal integration hook needed to surface existing context

### 9.3 Validation

- [ ] Add one observability smoke test for Shopify Bridge
- [ ] Verify logs include merchant and request context
- [ ] Verify metrics exposure
- [ ] Verify Shopify-side telemetry aligns with the same shared field model used by platform and runtime

Exit gate:

- [ ] Shopify Bridge runs on the starter baseline without business-logic changes

---

## 10) Cross-Service Validation

- [ ] Verify all three services use the same starter module
- [ ] Verify all three services use the same field naming conventions
- [ ] Verify all three services expose a consistent metrics baseline
- [ ] Verify cardinality protection is centrally enforced
- [ ] Verify optional error tracking can be enabled consistently
- [ ] Verify request and tenant context are visible end-to-end in the core flow
- [ ] Verify `deploymentId`, `releaseId`, `verificationRunId`, `requestId`, and `traceId` correlation works across platform backend and runtime for verification flows
- [ ] Verify the existing `/verification`, `/verification-ops`, and `/diagnostics` surfaces have a clear telemetry contract or drill-down path
- [ ] Verify no service requires synchronous managed-sink network calls on customer request paths
- [ ] Verify operator surfaces can correlate precomputed summaries with raw-evidence pivots

Exit gate:

- [ ] The three-service baseline is coherent enough to support Shopify launch readiness and product #2 reuse

---

## 11) Follow-On Tracks

These are valid follow-on tasks after the baseline lands.

- [ ] Evaluate compile-time lint processor
- [ ] Add deeper SDK instrumentation only where needed
- [ ] Add local self-check endpoint if still justified after baseline rollout
- [ ] Add stronger tracing integration with deployment templates
- [ ] Extend starter adoption to future product services
- [ ] Add alert, SLO, and trend surfaces to `/verification-ops` and `/diagnostics`
- [ ] Add richer log/trace/error drill-down from verification and diagnostics pages
- [ ] Treat product validation analytics as a separate follow-on track from infrastructure observability

---

## 12) Final Acceptance Checklist

- [ ] `ai-infrastructure-module/loomai-observability-starter` exists and builds
- [ ] Boot auto-configuration registration works
- [ ] Shared property model is documented and stable
- [ ] Structured JSON logging is live
- [ ] MDC context propagation is live
- [ ] Cardinality enforcement is live
- [ ] Prometheus-compatible metrics exposure is live
- [ ] Optional Sentry support is live
- [ ] Platform backend is adopted
- [ ] Runtime is adopted
- [ ] Shopify Bridge is adopted
- [ ] Each adopted service has an observability smoke test
- [ ] Existing verification and diagnostics surfaces have a documented telemetry contract
- [ ] No service depends on request-path vendor calls for observability
- [ ] No critical business-logic rewrite was required

---

## 13) Execution Notes

- Keep this checklist updated as work progresses.
- If a task requires business-logic changes, stop and compare against the implementation plan boundary rules before proceeding.
- If compile-time lint or sink-verification work threatens the baseline delivery, move it to the follow-on section rather than expanding initial scope.
