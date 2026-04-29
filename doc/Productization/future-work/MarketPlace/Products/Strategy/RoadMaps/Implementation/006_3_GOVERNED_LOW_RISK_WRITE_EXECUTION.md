# 006.3 Governed Low-Risk Write Execution

Status: future implementation handoff, blocked until `006.2` dry-run is complete and live verified (created 2026-04-29)

Owner mode: technical LLM implementation session

Roadmap phase: `006.3` — first governed low-risk writes

Priority: P1 after `006.2`

Depends on:

- [006_THINKER_RESOLVER_GOVERNED_ISSUE_RESOLUTION_BLUEPRINT.md](006_THINKER_RESOLVER_GOVERNED_ISSUE_RESOLUTION_BLUEPRINT.md)
- [006_1_THINKER_PHASE_1_READ_ONLY_ISSUE_RESOLUTION_PRODUCTIZATION.md](006_1_THINKER_PHASE_1_READ_ONLY_ISSUE_RESOLUTION_PRODUCTIZATION.md)
- [006_2_RESOLVER_DRY_RUN_AND_POLICY_SIMULATION.md](006_2_RESOLVER_DRY_RUN_AND_POLICY_SIMULATION.md)

Next phase:

- [006_4_PRODUCTIZED_RESOLUTION_ASSISTANT_READINESS_AND_ROLLOUT.md](006_4_PRODUCTIZED_RESOLUTION_ASSISTANT_READINESS_AND_ROLLOUT.md)

---

## Strategic Handover

`006.3` is the first phase where Resolver may execute a real write.

This phase must stay narrow:

- one bounded action family
- low-risk only
- reversible or compensatable where practical
- explicit user confirmation
- idempotency protection
- product-boundary execution
- audit before and after execution
- post-action verification

Do not include refunds, cancellations, financial adjustments, permission grants, account deletion, irreversible mutations, or broad admin automation.

---

## Current Code Status

As of 2026-04-29:

- No general Resolver write-execution product layer exists.
- Shopify Companion has governed-action packaging language and order-lookup/support-readiness posture, but Free/Starter remain read-only and governed writes are not broadly shipped.
- Platform has approval and audit primitives that can inform implementation, but not a customer-facing governed Resolver execution gateway.
- `006.3` must reuse `006.1` sessions and `006.2` policy/dry-run results. Do not create separate write-session objects detached from Thinker/Resolver lineage.

---

## Recommended First Action Families

Acceptable candidates:

- create a support ticket with evidence
- resend a notification or verification email
- update a low-risk user preference
- save a support handoff note
- apply a non-destructive configuration toggle

Preferred first reference action:

- create a support escalation/ticket with evidence, because Partner Enablement already has support escalation and evidence bundle patterns.

Avoid:

- refunds
- cancellations
- address changes
- order edits
- payment or billing changes
- permission grants
- account deletion
- cross-system write chains

---

## Required Capabilities

### Execution Gateway

Add a governed execution gateway that:

- requires a persisted write intent proposal
- requires a passing policy decision
- requires a current dry-run result when policy says dry-run is mandatory
- requires explicit confirmation
- validates tenant/store/deployment binding
- validates actor identity and scope
- validates idempotency key
- executes through the authoritative product/service boundary
- records pre-execution audit
- records post-execution audit
- performs post-action verification
- records failure and recovery guidance

The runtime must not call third-party write APIs directly when a product/service boundary exists.

### Confirmation

Confirmation must include:

- action name
- affected target
- risk level
- exact bounded effect
- evidence basis
- dry-run result reference
- what cannot be undone automatically
- support/handoff path

Confirmation must not include:

- raw secrets
- raw third-party payloads
- hidden parameters
- broad "do anything" language

### Idempotency

Every write execution requires:

- idempotency key
- dedupe window
- duplicate handling behavior
- audit record for duplicate attempts

Retries must not duplicate writes.

### Post-Action Verification

After execution:

- verify expected state changed or expected artifact exists
- record verification status
- update issue session terminal outcome
- update resolution plan with actual outcome
- expose user-safe success/failure text
- expose operator-safe diagnostics

---

## API Surface Targets

Operator APIs:

- `POST /api/operator/resolver/executions/{proposalId}/approve` if operator approval is required
- `GET /api/operator/resolver/executions`
- `GET /api/operator/resolver/executions/{executionId}`
- `POST /api/operator/resolver/executions/{executionId}/disable-action-family`

End-user or trusted host APIs:

- confirmation accept/decline endpoint scoped to the active session and signed/short-lived confirmation context

Partner APIs:

- assigned-store execution summaries
- evidence-linked escalation view
- no raw action payloads

Do not expose a generic public write endpoint.

---

## UI Surface Targets

End-user:

- confirmation card
- decline/cancel path
- running state
- success/failure result
- support handoff when blocked or failed

Operator:

- execution queue
- execution detail
- audit trail
- policy decision
- dry-run result
- idempotency status
- post-action verification
- emergency action-family disable

Partner:

- assigned-store execution summary
- support escalation link
- no secrets, raw payloads, or operator-only policy internals

Merchant/admin:

- high-level health and recent outcome count
- no raw execution internals

---

## Safety Requirements

- No write without policy pass.
- No write without confirmation.
- No write without idempotency.
- No write without tenant/store/user binding validation.
- No write from stale or insufficient evidence.
- No write from LLM-provided action definitions.
- No hidden retry for risky writes.
- No cross-tenant execution.
- No destructive or high-risk writes in this phase.
- Emergency kill switch must stop the action family without redeploying.

---

## Readiness Pack

Required scenarios:

- confirmed low-risk write succeeds
- user declines confirmation
- duplicate confirmation does not duplicate write
- stale dry-run blocks execution
- missing scope blocks execution
- policy denial blocks execution
- cross-tenant attempt blocks execution
- prompt injection cannot force execution
- product boundary failure is surfaced with recovery path
- post-action verification failure is recorded
- emergency kill switch blocks execution

Pass criteria:

- all writes are audited before and after execution
- duplicate writes are prevented
- user-visible messages are clear and bounded
- partner/operator exports explain what happened
- no secrets or raw credentials appear in evidence

---

## Verification

Always run:

```bash
git diff --check
```

Expected focused tests to add or run:

```bash
mvn -f Platfrom/backend/pom.xml -q \
  -Dtest=ResolverExecutionGatewayTest,ResolverConfirmationTest,ResolverIdempotencyTest,ResolverExecutionAuditTest,ResolverKillSwitchTest \
  test
```

If product-boundary execution is added to Shopify Bridge:

```bash
mvn -f product-services/shopify-bridge-service/pom.xml -q \
  -Dtest=ShopifyBridgeGovernedWriteExecutionTest,ShopifyBridgeGovernedWriteIdempotencyTest \
  test
```

If UI changes:

```bash
npm --prefix Platfrom/ui run build
npm --prefix Platfrom/partner-ui run build
npm --prefix product-services/shopify-bridge-service/ui run build
```

Create equivalent focused tests if these exact test classes do not exist yet.

---

## Exit Criteria

`006.3` is complete only when:

- one low-risk action family executes through policy, confirmation, idempotency, and product-boundary execution.
- duplicate writes are prevented.
- failure paths are visible and recoverable.
- audit evidence is sufficient for support review.
- readiness scenarios pass against a sandbox target.
- a kill switch can disable the action family without redeploying.
- completion status is added to `CODEX_WORKING_CONTEXT.md`.

Do not start `006.4` until `006.3` has live sandbox proof.
