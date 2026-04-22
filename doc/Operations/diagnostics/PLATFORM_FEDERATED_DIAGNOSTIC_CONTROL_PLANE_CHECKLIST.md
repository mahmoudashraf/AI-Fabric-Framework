# Platform Federated Diagnostic Control Plane Checklist

Status: execution checklist (2026-04-22)

This document is the execution checklist companion to:

- `PLATFORM_FEDERATED_DIAGNOSTIC_CONTROL_PLANE_PLAN.md`
- `MANAGED_SERVICES_FEDERATED_DIAGNOSTIC_ADAPTER_CONTRACT.md`

Use this checklist for rollout tracking.

## 0) Pre-Start Decisions

- [ ] Confirm the platform is the single operator entry point for diagnosis
- [ ] Confirm raw telemetry backends stay external and managed
- [ ] Confirm summaries should be materialized asynchronously wherever practical
- [ ] Confirm operator pages must load from platform-native summaries first, then pivot to raw evidence
- [ ] Confirm the assistant will consume investigation APIs rather than scrape UI assumptions

Exit gate:

- [ ] Team agrees on the federated control-plane boundary

## 1) Canonical Correlation Contract

- [ ] Standardize `deploymentId`
- [ ] Standardize `releaseId`
- [ ] Standardize `verificationRunId`
- [ ] Standardize `serviceRef`
- [ ] Standardize `tenantId`
- [ ] Standardize `merchantId`
- [ ] Standardize `requestId`
- [ ] Standardize `traceId`
- [ ] Document which surfaces are expected to carry which identifiers

Exit gate:

- [ ] Core investigation identifiers are agreed and documented

## 2) Platform-Native Summary Model

- [ ] Define investigation summary DTO
- [ ] Define alert summary DTO
- [ ] Define error summary DTO
- [ ] Define managed-service diagnostics summary DTO
- [ ] Define dependency / blast-radius summary DTO
- [ ] Define deep-link / pivot DTO

Exit gate:

- [ ] The platform eye has a reusable internal response model

## 3) Async Materialization

- [ ] Identify which summaries can be updated from platform lifecycle events
- [ ] Identify which summaries need scheduled refresh
- [ ] Define projector or materializer responsibilities
- [ ] Ensure summaries can be refreshed without blocking customer request paths
- [ ] Ensure summaries can be refreshed without live fan-out on every operator page load

Exit gate:

- [ ] Summary refresh strategy is defined and async by design

## 4) Managed-Service Adapter Rollout

- [ ] Normalize managed inference service summary shape
- [ ] Add managed inference diagnostics-summary support
- [ ] Add managed inference logs or logs-summary-plus-pivot support
- [ ] Normalize managed product service diagnostics-summary support
- [ ] Normalize runtime-backed deployment diagnostics-summary support
- [ ] Review governed action coverage across managed-service families

Exit gate:

- [ ] Core managed-service families can participate in one investigation model

## 5) Operator Surface Integration

- [ ] Define how `/verification` consumes the shared correlation and summary model
- [ ] Define how `/verification-ops` consumes alert, release, and verification summaries
- [ ] Define how `/diagnostics` consumes deployment and managed-service summaries
- [ ] Define how `/platform-diagnostics` consumes fleet-level summaries
- [ ] Add clear pivot patterns from summary cards to raw logs, traces, metrics, and errors

Exit gate:

- [ ] Existing operator surfaces are connected by one investigation contract

## 6) All-In-One Platform Eye

- [ ] Define the summary rail or summary panel pattern
- [ ] Include current risk posture
- [ ] Include recent release / verification signal summary
- [ ] Include recent alerts summary
- [ ] Include recent top errors summary
- [ ] Include dependency and blast-radius summary
- [ ] Include governed next actions where allowed

Exit gate:

- [ ] The platform can present one coherent investigation summary before raw drill-down

## 7) External Evidence Pivots

- [ ] Define Grafana logs pivot pattern
- [ ] Define Grafana traces pivot pattern
- [ ] Define Grafana metrics pivot pattern
- [ ] Define Sentry issue pivot pattern
- [ ] Define provider-native console pivot pattern where relevant
- [ ] Ensure pivots preserve correlation context where practical

Exit gate:

- [ ] Raw evidence is reachable without making the platform own raw storage

## 8) Assistant-Ready Investigation APIs

- [ ] Expose normalized investigation summary APIs
- [ ] Expose alerts and errors summary APIs
- [ ] Expose evidence bundle APIs with citations and pivots
- [ ] Define permission filtering for assistant responses
- [ ] Ensure assistant answers can cite deterministic platform evidence

Exit gate:

- [ ] The assistant can investigate from the same platform model used by admins

## 9) Final Validation

- [ ] Operators can begin investigation in the platform by default
- [ ] First render of key operator pages does not require synchronous fan-out to every telemetry provider
- [ ] Platform-native summaries and raw-evidence pivots are both available
- [ ] Managed-service diagnostics participate in the same investigation model
- [ ] Correlation fields work end-to-end across release, verification, diagnostics, and raw evidence pivots
- [ ] The platform remains a federated control plane, not a custom observability backend
