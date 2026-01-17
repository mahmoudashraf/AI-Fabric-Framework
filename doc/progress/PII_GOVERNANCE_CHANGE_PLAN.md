# PII Governance — Audit, Alerts, Enforcement, Retention (Change Plan)

## Status
Proposed

## Context / Why This Exists
The framework already supports PII detection and sanitization:
- PII detection SPI in core: `PIIDetectionService`
- Default implementation + pipeline step in `ai-infrastructure-pii`
- Core uses PII results in:
  - `AISecurityService` (optionally block on PII)
  - `ResponseSanitizer` (sanitizes response payloads)
  - `IntentHistoryService` (redaction + optional encrypted original storage)

What we **do not** have is “PII governance”: policy + accountability + lifecycle across the system:
- durable audit trail (beyond logs)
- alerting hooks (webhook/SIEM/etc.)
- centralized enforcement rules (fail-closed controls)
- retention/deletion orchestration driven by catalog/vector metadata

This plan adds those capabilities in the optional module: `ai-infrastructure-governance`.

---

## Goals
- Provide an **optional** governance layer for PII that is:
  - **Fail-closed** when configured to enforce (framework philosophy)
  - **Auditable** (queryable store of events)
  - **Pluggable** (SPI hooks for alerting and enterprise integration)
- Keep `ai-fabric-core` clean:
  - Core remains a “security/safety baseline”
  - Governance owns “enterprise ops” and policy orchestration
- Support Java 21 builds and full parent-module test pass.

## Non-goals
- Building a perfect PII detector (we already provide a regex-based implementation; users can supply their own `PIIDetectionService`).
- A full SIEM implementation; we provide SPIs + basic adapters only.
- Cross-tenant RBAC; governance features assume application-level access control.

---

## Proposed Architecture

### 1) Event model (core emits, governance consumes)
Create a single internal event type for PII detections:
- `PIIDetectedEvent` (already exists in core) should become *actually published*.

Publish from:
- `PIIDetectionStep` (INPUT side)
- `ResponseSanitizer` (OUTPUT side)
- (Optional) `AISecurityService` when it flags `PII_DETECTED`

Event payload should include:
- `requestId`, `userId`, `timestamp`
- `direction`: `INPUT` or `OUTPUT`
- `piiTypes`: list of normalized types (EMAIL, SSN, CREDIT_CARD, etc.)
- `severity`: `LOW|MEDIUM|HIGH` (based on configured high-risk types)
- `actionTaken`: `REDACTED|BLOCKED|DETECTED_ONLY|PASSTHROUGH`
- `context`: minimal metadata (no raw PII)

Rule: **Never put raw PII into events**.

### 2) Governance PII “audit store”
Add a governance-owned persistence model:
- `PiiAuditEvent` entity + repository + service
- Store minimal, non-sensitive details:
  - ids + timestamps + types + severity + actionTaken
  - optional hashed sample (only if explicitly enabled)

Implementations:
- `PiiAuditStore` (interface)
- `JpaPiiAuditStore` (default when JPA present)
- `NoopPiiAuditStore` (when disabled)

### 3) Alerting SPI (enterprise integration)
Add SPI:
- `PIIAlertingProvider`
  - `onPIIDetected(PiiAuditRecord record)` (or event)

Default behavior:
- off by default
- when enabled, governance calls provider after persisting audit record

### 4) Enforcement controls (governance-owned policies)
Add governance properties to enable “strict” behaviors:
- `ai.governance.pii.enabled`
- `ai.governance.pii.audit.enabled`
- `ai.governance.pii.alerts.enabled`
- `ai.governance.pii.enforcement.enabled`
- `ai.governance.pii.enforcement.block-on-high-risk` (fail-closed)
- `ai.governance.pii.enforcement.high-risk-types` (list)

Mechanism:
- Enforcement is applied at pipeline boundaries (input + output) via existing steps:
  - If configured, `PIIDetectionStep` can convert “detected” into “blocked” (throw/return a fail-closed orchestration result).
  - `ResponseSanitizer` can optionally fail-closed on high-risk PII before returning to client.

Note: core keeps baseline `ai.security.block-on-pii-detection`; governance adds richer, centralized rules and consistent policy behavior.

### 5) Retention & deletion (PII-aware governance)
Two distinct scopes must be explicit:

**A) In-flight PII (query/response)**
- Governed via audit + retention of audit records.
- Add retention rules for `PiiAuditEvent` table (e.g., 30/90 days).

**B) Indexed content PII (vectors/catalog entries)**
- Requires PII “tagging” at index time (not currently implemented).
- Proposed approach:
  - When indexing content (vector store), optionally analyze the indexed text using `PIIDetectionService.analyze(...)`.
  - Persist flags into vector metadata and/or `IndexCatalog` metadata:
    - `piiDetected=true`
    - `piiTypes=[...]`
    - `piiSeverity=HIGH`
  - Then governance retention can scan:
    - `IndexCatalog.scan(entityType, metadataEquals={piiDetected:true})` when provider supports filtering
    - fallback: SQL catalog scan/filter

This plan can ship A first (fast), and B as a follow-up phase.

---

## Module/Code Changes (Phased)

### Phase 1 — Event publishing (core)
- Publish `PIIDetectedEvent` from `PIIDetectionStep` (INPUT) and `ResponseSanitizer` (OUTPUT).
- Add minimal unit tests verifying publishing is gated by config and contains no raw payload.
- Keep publishers **optional** (only publish when an `ApplicationEventPublisher` is present and feature flag is enabled).

### Phase 2 — Governance PII config + audit store
- Extend `AIGovernanceProperties` with `pii.*` section.
- Implement:
  - `PiiAuditStore` + `JpaPiiAuditStore`
  - JPA entity + migration (`V*__create_pii_audit_events.sql`) inside governance module migrations.
- Add `@EventListener` in governance that consumes `PIIDetectedEvent` and writes audit records.

### Phase 3 — Alerting SPI + adapters
- Introduce `PIIAlertingProvider` SPI.
- Governance listener calls provider after audit persistence.
- Add a sample “logging alert provider” (optional) or keep as SPI only.

### Phase 4 — Enforcement (fail-closed)
- Add governance enforcement toggles.
- Implement a governance-provided pipeline step (or wrapper) that fails-closed on high-risk PII:
  - input-side: block before downstream steps
  - output-side: block before response emission
- Ensure behavior is explicit and test-covered.

### Phase 5 — PII retention for audit events
- Add scheduler in governance:
  - delete old `PiiAuditEvent` records based on retention config
- Provide JPA repository delete query + unit test.

### Phase 6 (Optional) — Indexed-content PII tagging + catalog-driven retention
- Add opt-in detection during indexing/content storage.
- Persist PII flags in vector metadata/catalog.
- Update governance retention to support PII-based filters for indexed content cleanup.

---

## Configuration (Proposed)

```yaml
ai:
  pii-detection:
    enabled: true
    mode: DETECT_ONLY # or REDACT
    detection-direction: INPUT_OUTPUT

  governance:
    enabled: true
    pii:
      enabled: true
      audit:
        enabled: true
        retention-days: 90
      alerts:
        enabled: false
      enforcement:
        enabled: false
        block-on-high-risk: true
        high-risk-types: ["CREDIT_CARD", "SSN", "PASSPORT_NUMBER"]
```

---

## Testing Plan
- Unit:
  - `PIIDetectionStep` publishes INPUT event (enabled) / does not publish (disabled)
  - `ResponseSanitizer` publishes OUTPUT event when PII detected and `publishEvents=true`
  - Governance event listener writes `PiiAuditEvent` and calls `PIIAlertingProvider` when enabled
  - Enforcement mode blocks as configured (fail-closed)
- Integration:
  - Spring context with `ai-infrastructure-governance` + `ai-infrastructure-pii` enabled composes correctly
  - Parent reactor: `mvn test -DskipITs` on Java 21

---

## Open Decisions (Choose Early)
1) **Where should event publishing be controlled?**
   - Option A: `ai.pii-detection.publish-events` (belongs to PII module)
   - Option B: `ai.governance.pii.audit.enabled` (governance decides)
   - Recommendation: publish always when detection is enabled, but governance listener can be disabled; keep a core-level “publish toggle” for cost control.

2) **Should we ever store any payload samples?**
   - Recommendation: default **no**; allow only hashed sample if explicitly enabled.

3) **PII types normalization contract**
   - Recommendation: standardize on uppercase snake-case type strings to avoid provider mismatch.

