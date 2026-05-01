# 006.2 Resolver Dry-Run And Policy Simulation

Status: implemented, deployed, and live verified as the Resolver preview portion of the full 006 slice (created 2026-04-29)

Owner mode: technical LLM implementation session

Roadmap phase: `006.2` — Resolver dry-run and policy simulation

Priority: P1 after `006.1`

Depends on:

- [006_THINKER_RESOLVER_GOVERNED_ISSUE_RESOLUTION_BLUEPRINT.md](006_THINKER_RESOLVER_GOVERNED_ISSUE_RESOLUTION_BLUEPRINT.md)
- [006_1_THINKER_PHASE_1_READ_ONLY_ISSUE_RESOLUTION_PRODUCTIZATION.md](006_1_THINKER_PHASE_1_READ_ONLY_ISSUE_RESOLUTION_PRODUCTIZATION.md)

Next phases:

- [006_3_GOVERNED_LOW_RISK_WRITE_EXECUTION.md](006_3_GOVERNED_LOW_RISK_WRITE_EXECUTION.md)
- [006_4_PRODUCTIZED_RESOLUTION_ASSISTANT_READINESS_AND_ROLLOUT.md](006_4_PRODUCTIZED_RESOLUTION_ASSISTANT_READINESS_AND_ROLLOUT.md)

---

## Strategic Handover

`006.2` introduces Resolver behavior without executing real writes.

The product still resolves issues through:

- issue sessions
- evidence bundles
- resolution plans
- policy decisions
- audit records

The new capability is:

> Show what the governed Resolver would do, why policy allows or denies it, what confirmation would be required, and what result is expected, without mutating customer or third-party state.

Do not implement real writes in this phase.

Do not expose this as autonomous action capability.

Do not skip dry-run because a write action appears low risk. `006.2` is the safety proof phase that must exist before `006.3`.

---

## Current Code Status

As of 2026-04-29:

- `006.1` issue sessions, evidence, plans, and controls are implemented locally.
- Resolver proposal, policy decision, and dry-run persistence now exists.
- Policy decisions are computed by Platform/product policy, not LLM self-report.
- Dry-run is non-mutating and must complete before governed execution.

This phase must build on the `006.1` IssueSession/EvidenceBundle/ResolutionPlan primitives. Do not create a parallel session model.

---

## Product Definition

Resolver Dry-Run is a simulation layer for write-capable resolution.

It is allowed to:

- classify a write intent from an existing resolution plan
- validate the intent against action registry and policy
- produce an explainable allow/deny/needs-confirmation decision
- run a non-mutating dry-run when an authoritative product/service boundary supports it
- produce an expected execution preview
- record audit events for proposed, denied, simulated, and blocked actions

It is not allowed to:

- execute a real write
- call a third-party write endpoint
- change customer/store/account state
- silently promote a dry-run into execution
- bypass tenant, store, user, or deployment binding
- let the LLM define action availability or policy

---

## Required Capabilities

### Write Intent Proposal

Add a model that records:

- issue session id
- proposed action id
- action family
- target domain and product boundary
- actor context
- tenant/store/deployment binding
- parameters in redacted and operator forms
- source evidence references
- LLM proposal text
- server-normalized intent
- proposal status

Proposal statuses:

- `PROPOSED`
- `POLICY_DENIED`
- `DRY_RUN_READY`
- `DRY_RUN_COMPLETED`
- `CONFIRMATION_REQUIRED`
- `APPROVAL_REQUIRED`
- `BLOCKED`
- `FAILED`

### Resolver Policy Decision

Add policy decision output:

- allowed/denied/blocked/needs confirmation/needs approval
- risk level
- required scopes
- missing scopes or missing evidence
- tier requirement
- actor requirement
- dry-run requirement
- confirmation text template
- operator-safe denial reason
- user-safe denial reason

Policy must fail closed.

### Dry-Run Result

Add dry-run result output:

- dry-run id
- target action
- validated parameters
- expected state transition
- expected side effects
- warnings
- unsupported fields
- idempotency posture
- rollback/compensation posture
- freshness of evidence used
- product boundary that performed or refused the dry-run

Dry-run results must clearly say when the product boundary does not support simulation.

### Confirmation Preview

Add confirmation preview only. It must not accept confirmation in this phase.

Preview includes:

- what would happen
- risk level
- who would be affected
- required identity/scope
- evidence supporting the action
- what cannot be guaranteed
- human escalation path

---

## API Surface Targets

Operator APIs:

- `GET /api/operator/resolver/dry-runs`
- `GET /api/operator/resolver/dry-runs/{dryRunId}`
- `GET /api/operator/resolver/policy-decisions`
- `GET /api/operator/thinker/sessions/{sessionId}/resolver-preview`

Internal APIs:

- pipeline-internal creation of write intent proposal from a `006.1` resolution plan
- product-boundary dry-run adapter call where supported

Partner APIs:

- `GET /api/partners/thinker-sessions/{sessionId}/resolver-preview` scoped to assigned stores and redacted

Do not expose:

- execute endpoints
- confirmation acceptance endpoints
- write action mutation endpoints

---

## UI Surface Targets

Operator UI:

- Resolver preview on Thinker session detail
- policy decision timeline
- dry-run result detail
- denied/blocked action review
- simulation readiness checklist

Partner UI:

- partner-safe Resolver preview for assigned stores only
- no raw parameters, no internal policy internals, no secrets
- escalation button using existing support escalation patterns

End-user UI:

- only user-safe preview language if product opts in
- no "Confirm" or "Execute" button in this phase
- clear handoff when the issue requires a human or future governed action

Merchant/admin UI:

- optional health/readiness card only
- no dry-run internals

---

## Safety Requirements

- Every write intent proposal is audited.
- Every policy denial is audited.
- Every dry-run attempt and result is audited.
- Dry-run adapters must be non-mutating by contract and test.
- Missing dry-run support blocks execution readiness.
- Prompt-injected write proposals are rejected and audited.
- Cross-tenant/store proposals are blocked before dry-run.
- Parameters shown to end users and partners are redacted.
- Policy decisions are computed by platform/product policy, never by LLM self-report.

---

## Readiness Pack

Add or extend the Thinker/Resolver readiness pack with scenarios:

- write needed but blocked by tier
- write needed but blocked by policy
- write proposal accepted for dry-run only
- write proposal denied by missing evidence
- dry-run unsupported by product boundary
- confirmation would be required
- approval would be required
- prompt injection tries to force execution
- cross-tenant proposal blocked
- stale evidence blocks dry-run readiness

Pass criteria:

- no real writes occur
- every scenario has an audit event
- UI can explain allow/deny/simulation status
- partner-visible output is redacted
- support export explains what happened without raw logs

---

## Verification

Always run:

```bash
git diff --check
```

Expected focused tests to add or run:

```bash
mvn -f Platfrom/backend/pom.xml -q \
  -Dtest=ResolverPolicyDecisionTest,ResolverDryRunServiceTest,ResolverOperatorApiTest,ResolverPartnerPreviewTest \
  test
```

If UI changes:

```bash
npm --prefix Platfrom/ui run build
npm --prefix Platfrom/partner-ui run build
```

If product-boundary dry-run adapters are added:

```bash
mvn -f product-services/shopify-bridge-service/pom.xml -q \
  -Dtest=ShopifyBridgeResolverDryRunTest \
  test
```

Create equivalent focused tests if these exact test classes do not exist yet.

---

## Exit Criteria

`006.2` is complete only when:

- Resolver preview is attached to `006.1` issue sessions.
- Write intent proposals are persisted and audited.
- Policy decisions are explainable and fail closed.
- Dry-run/simulation can run only when a product boundary supports non-mutating preview.
- No real writes can execute from this phase.
- Operator UI can inspect policy and dry-run evidence.
- Partner UI can view assigned-store redacted previews.
- Readiness scenarios pass.
- Completion status is added to `CODEX_WORKING_CONTEXT.md`.

This phase has been implemented together with the narrow `006.3` support-escalation execution path under explicit product direction. Do not add additional write families without a new readiness pass.

## Implementation Summary - 2026-04-29

Implemented:

- Resolver intent proposals tied to Thinker sessions and source evidence.
- Policy decisions with allow/deny outcome, reasons, required confirmation, approval posture, and product boundary.
- Non-mutating dry-runs with expected result and risk output.
- Operator UI for proposal creation, dry-run execution, and policy ledger review.
- Partner-safe preview projection with operator-only parameters redacted.

Verification proof:

- Integration tests create a proposal from real session evidence, assert policy `ALLOWED`, run dry-run, and then use the dry-run as the only path to the governed support escalation execution.
- Live readiness script checks proposal creation, policy, dry-run, policy ledger, and partner redaction.
