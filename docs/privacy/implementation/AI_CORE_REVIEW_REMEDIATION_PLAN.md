## AI Core & Integration Tests Remediation Plan

### 1. PII Detection Direction
- Replace `PIIDetectionDirection` enum values with `INPUT` and `INPUT_OUTPUT`.
- Update all property bindings, builders, defaults, and configuration docs that reference deprecated values.
- Modify `PIIDetectionService` logic to honour the new modes (input-only vs input+output) and remove dead paths.
- Adjust unit/integration tests to validate the updated behaviour and delete assertions tied to removed values.

### 2. Entity Access Policy Enforcement
- In `AIAccessControlService`, fail closed by throwing a descriptive error when no `EntityAccessPolicy` bean is present.
- Strip history/caching extras so the service only validates input, delegates to the hook, and logs via `AuditService`.
- Update affected integration tests to cover the required hook presence and simplified flow.

### 3. Compliance Infrastructure Boundaries
- Remove compliance scoring, statistics, anomaly detection, and other business logic from `AIComplianceService`.
- Keep only hook invocation, DTO wiring, and audit logging so customers supply all compliance decision logic.
- Delete unused helpers/fields and rewrite tests to focus on hook delegation.
- Refresh documentation/Javadoc to state the library supplies infrastructure only.

### 4. Data Lifecycle Orchestration
- Reintroduce `UserDataDeletionService`, `BehaviorRetentionService`, scheduled executors, and cache eviction hooks.
- Wire the services in `AIInfrastructureAutoConfiguration` so deletion and retention flows call customer hooks.
- Ensure audit logging, cascading deletion, and retention timing match previously documented behaviour.
- Add/restore tests covering deletion requests, retention scheduling, and hook execution.

### 5. Documentation Updates
- Document the new PII direction contract, mandatory `EntityAccessPolicy` hook, and compliance responsibility split.
- Update changelog/README guidance to highlight infrastructure-only scope and removal of embedded business logic.
