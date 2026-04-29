# 006.4 Productized Resolution Assistant Readiness And Rollout

Status: future implementation handoff, blocked until `006.3` governed low-risk writes are complete and live verified (created 2026-04-29)

Owner mode: technical LLM implementation session

Roadmap phase: `006.4` — product packaging, readiness, and controlled rollout

Priority: P1 after `006.3`

Depends on:

- [006_THINKER_RESOLVER_GOVERNED_ISSUE_RESOLUTION_BLUEPRINT.md](006_THINKER_RESOLVER_GOVERNED_ISSUE_RESOLUTION_BLUEPRINT.md)
- [006_1_THINKER_PHASE_1_READ_ONLY_ISSUE_RESOLUTION_PRODUCTIZATION.md](006_1_THINKER_PHASE_1_READ_ONLY_ISSUE_RESOLUTION_PRODUCTIZATION.md)
- [006_2_RESOLVER_DRY_RUN_AND_POLICY_SIMULATION.md](006_2_RESOLVER_DRY_RUN_AND_POLICY_SIMULATION.md)
- [006_3_GOVERNED_LOW_RISK_WRITE_EXECUTION.md](006_3_GOVERNED_LOW_RISK_WRITE_EXECUTION.md)

---

## Strategic Handover

`006.4` turns the Thinker/Resolver capability into an operable product package.

This phase is not about adding more action power. It is about making the product usable by non-founder operators, partners, support teams, and design partners without relying on chat history or ad hoc runbooks.

The product line remains one product:

> Thinker/Resolver Governed Issue Resolution

Shopify Companion Elite remains the first reference vertical, but the core platform capability must stay reusable.

---

## Current Code Status

As of 2026-04-29:

- `006.1`, `006.2`, and `006.3` are planned phases.
- Platform already has verification-suite and readiness-audit patterns from Shopify Companion `005`.
- Partner Enablement already has assigned-store scoping, evidence bundles, support escalations, and partner-safe exports.
- Shopify Companion already has merchant launch/support/export material.
- No productized Thinker/Resolver readiness UI, support runbook, pricing/tier model, partner onboarding flow, or second-store proof exists yet.

`006.4` must reuse existing verification, partner, and support patterns. Do not create a parallel release-gate mechanism.

---

## Product Package

The product package must define:

- product truth
- tier/capability matrix
- supported domains
- supported action families
- safety controls
- readiness gates
- operator guide
- partner guide
- support runbook
- evidence export format
- pricing/tier posture
- design-partner validation plan

Initial packaging:

- Thinker: read-only diagnosis and evidence-backed resolution plans.
- Resolver Preview: dry-run/simulation and policy explanation.
- Resolver Governed: confirmed low-risk writes.
- Enterprise controls: stricter approval, custom action packs, implementation-led rollout, and higher-risk action review.

---

## Operator Console Maturity

Operator must be able to:

- view product readiness
- run readiness scenarios
- inspect issue sessions
- inspect policy decisions
- inspect dry-runs
- inspect executions
- export support packets
- disable modes/action families
- review blocked/denied actions
- inspect stale evidence
- see release-gate status

The console must not require reading raw logs or chat history for normal support.

---

## Partner Enablement Integration

Partners may:

- view assigned-store issue sessions
- view partner-safe evidence
- run scoped readiness tests if enabled
- create support escalations with attached evidence
- add client-specific scenario questions as supplemental evidence

Partners may not:

- redefine canonical thresholds
- change global policy
- enable writes without merchant/client consent
- access unassigned stores
- view raw action payloads, secrets, or operator-only reasoning

Update Partner UI and partner docs only after core operator readiness is stable.

---

## Merchant And Customer Experience

Merchant/admin surfaces should show:

- whether Thinker/Resolver is enabled
- recent health
- blocked setup requirements
- support handoff posture
- safe high-level outcome counts

Merchant/admin surfaces must not show:

- raw evidence payloads
- internal reasoning traces
- provider/runtime/vector internals
- secret material
- policy internals intended only for operators

End users should see:

- diagnosis with clear evidence
- confirmation for governed actions
- final outcome
- escalation path

End users should not see:

- raw action payloads
- internal policy details
- unsupported promises

---

## Readiness Audit

Create a Thinker/Resolver readiness suite using the same Platform verification-suite pattern proven by Shopify Companion `005`.

Required scenario groups:

- read-only diagnosis sufficient evidence
- read-only diagnosis insufficient evidence
- clarification required
- write needed but blocked by tier
- write needed but blocked by policy
- dry-run allowed
- dry-run denied
- confirmation required
- user declines confirmation
- governed low-risk write succeeds
- governed low-risk write fails with recovery path
- stale evidence blocks action
- prompt injection blocked
- cross-tenant attempt blocked
- partner redaction verified
- operator export complete

Readiness output:

- decision
- checklist
- scenario results
- evidence artifacts
- support/export packet
- blocker list
- next handoff

Do not mark design-partner ready without UI-visible evidence and support packet coverage.

---

## Documentation Targets

Create or update:

- user/operator guide for Thinker/Resolver
- developer integration guide
- partner rollout guide
- merchant-safe capability guide
- support runbook
- readiness audit guide
- action-family authoring guide
- product truth document

Docs must state:

- Thinker/Resolver is governed issue resolution, not autonomous write access.
- Write actions are only available where policy, confirmation, audit, and product-boundary execution are proven.
- Human handoff is a feature.
- Shopify Companion Free/Starter remain read-only.
- Elite is the first Shopify tier where governed resolution can appear after readiness proof.

---

## Rollout Gates

Internal gate:

- all readiness scenarios pass in sandbox
- operator UI can explain every scenario
- support packet is complete
- kill switch works

Design-partner gate:

- one consented design-partner store/client
- merchant/client understands capability boundaries
- partner/operator support owner assigned
- support runbook ready
- rollback/disable plan ready

Controlled launch gate:

- design-partner outcomes reviewed
- incident/support findings resolved
- evidence freshness policy set
- pricing/tier posture approved
- public claims reviewed against product truth

Do not claim broad market proof from internal or single-store readiness.

---

## Verification

Always run:

```bash
git diff --check
```

Expected verification:

```bash
mvn -f Platfrom/backend/pom.xml -q test
npm --prefix Platfrom/ui run build
npm --prefix Platfrom/partner-ui run build
```

If Shopify Bridge surfaces or merchant admin change:

```bash
mvn -f product-services/shopify-bridge-service/pom.xml -q test
npm --prefix product-services/shopify-bridge-service/ui run build
```

Expected live verification:

```bash
scripts/run-thinker-resolver-readiness-pack.sh --target <sandbox-target>
```

If the script does not exist, create it in this phase using the existing Platform verification-suite pattern.

---

## Exit Criteria

`006.4` is complete only when:

- the product package is clear and documented.
- operator UI can run and inspect readiness.
- partner UI can support assigned-store/client cases safely.
- merchant/customer surfaces show safe capability and health.
- support runbook and export packet are complete.
- sandbox readiness suite passes.
- one design-partner rollout packet is ready.
- product truth prevents autonomous-write overclaiming.
- completion status is added to `CODEX_WORKING_CONTEXT.md`.

Only after this phase should the team consider secondary domains or broader partner-configured action packs.
