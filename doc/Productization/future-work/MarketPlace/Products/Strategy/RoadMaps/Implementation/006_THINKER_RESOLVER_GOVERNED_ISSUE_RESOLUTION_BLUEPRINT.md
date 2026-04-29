# Thinker Resolver Governed Issue Resolution Blueprint

Status: implemented, deployed, and live verified for the first governed product slice (revised 2026-04-29)

Owner mode: strategic/product architecture LLM session

Roadmap phase: Next Product Archetype Candidate

Priority: P0 product line; first Shopify Companion Elite slice completed after the first-product readiness gate

Depends on:

- [005_SHOPIFY_COMPANION_FIRST_PRODUCT_READINESS_AUDIT.md](005_SHOPIFY_COMPANION_FIRST_PRODUCT_READINESS_AUDIT.md)

Related foundation:

- [PLAN_READ_ONLY_ACTION_RESOLVER_AND_THINKER_MODES.md](../../../../../../../../Final_Documentation/System_Archtecture_Guides/PLAN_READ_ONLY_ACTION_RESOLVER_AND_THINKER_MODES.md)
- [PLAN_SHOPIFY_CONTROL_AND_EXECUTION_PLANE_SEPARATION.md](../../../../../../../../Final_Documentation/System_Archtecture_Guides/PLAN_SHOPIFY_CONTROL_AND_EXECUTION_PLANE_SEPARATION.md)
- [004_PARTNER_ENABLEMENT_FOUNDATION.md](004_PARTNER_ENABLEMENT_FOUNDATION.md)

Implementation roadmap family:

- [006_1_THINKER_PHASE_1_READ_ONLY_ISSUE_RESOLUTION_PRODUCTIZATION.md](006_1_THINKER_PHASE_1_READ_ONLY_ISSUE_RESOLUTION_PRODUCTIZATION.md)
- [006_2_RESOLVER_DRY_RUN_AND_POLICY_SIMULATION.md](006_2_RESOLVER_DRY_RUN_AND_POLICY_SIMULATION.md)
- [006_3_GOVERNED_LOW_RISK_WRITE_EXECUTION.md](006_3_GOVERNED_LOW_RISK_WRITE_EXECUTION.md)
- [006_4_PRODUCTIZED_RESOLUTION_ASSISTANT_READINESS_AND_ROLLOUT.md](006_4_PRODUCTIZED_RESOLUTION_ASSISTANT_READINESS_AND_ROLLOUT.md)

---

## Strategic Handover

Thinker/Resolver is the next major product archetype after Shopify Companion first-product readiness.

Current gate status:

- `005` reached `DESIGN_PARTNER_READY` on 2026-04-29.
- The full release gate passed as `vsr-df616f36`.
- The Platform readiness UI now reads the `shopify-first-product-readiness-audit` stage from full release-gate evidence.
- Implementation has started and the initial full-stack governed slice now spans `006.1` through `006.4`: Thinker issue sessions, Resolver dry-run, one low-risk governed execution family, operator/partner/merchant UI, guides, and release-gate wiring.
- Live verification passed on 2026-04-29 against the deployed Platform backend, Partner UI, and `shopping-companion-test.myshopify.com`.

Product goal:

> A governed issue-resolution assistant that can understand user problems, inspect trusted system state, propose a resolution plan, and execute approved read/write actions with policy, confirmation, audit, and recovery.

This is higher leverage than launching another search/chat product because it moves the platform from:

- answering questions
- to resolving user issues
- with evidence and governed action execution

Strategic posture:

- `006.1` remains the conceptual foundation; the current implementation also includes the narrow `006.2`/`006.3`/`006.4` support-escalation path requested by product direction.
- Do not market this as autonomous write access.
- Position it as governed resolution, not "AI can do anything".
- Treat write actions as a platform risk boundary, not a UI feature.
- Build the product class once, then adapt it to Shopify, SaaS support, CRM, internal tools, and partner client apps.
- Do not reimplement the existing read-action reasoning loop; build on the current platform capability and extend it into governed resolution.

Recommended positioning:

> Governed issue-resolution assistant with evidence, approvals, audited actions, and safe handoff.

Avoid positioning:

- autonomous agent
- chatbot with write access
- AI support bot that can change accounts
- unrestricted workflow automation
- self-healing app without policy boundaries

---

## Current Platform Foundation

The platform already supports the important Thinker-side primitive:

> LLM plans one or more eligible read actions, the framework executes them under policy, the loop may continue for bounded iterations, and final generation uses the resulting action evidence with optional RAG cooperation.

Code-level anchors:

- `ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/intent/orchestration/information/ReadActionResolutionService.java`
- `ai-infrastructure-module/ai-infrastructure-core/src/test/java/com/ai/infrastructure/intent/orchestration/information/ReadActionResolutionServiceTest.java`
- `ai-infrastructure-module/ai-infrastructure-core/src/test/java/com/ai/infrastructure/intent/orchestration/pipeline/steps/IntentHandlingStepReadActionResolutionTest.java`
- `ai-infrastructure-module/curated/ai-curated-commerce/src/main/resources/ai-curated/packs/commerce.yml`

Current supported behavior:

- `resolver_assistant` mode can run single-pass read-action resolution.
- `thinker` mode can run iterative read-action resolution.
- read actions are allowlisted and must be `READ`.
- actions must be `readActionResolutionEligible`.
- planner proposals are bounded by max iterations, max actions per iteration, and max total actions.
- action evidence can answer directly or cooperate with RAG.
- final user-facing generation happens after read-action evidence is gathered.
- diagnostics include planner iterations, executed actions, grounding-usable action count, RAG cooperation, and final decision.

Important boundary:

- This existing capability is read-only.
- It is a Thinker foundation, not the full Resolver product.
- Write-capable resolving still needs policy, confirmation, dry-run, audit, recovery, escalation, product UI, and readiness gates.
- Older notes that describe the read-action planning loop as missing should be treated as historical unless updated by current code inspection.

Product implication:

- Phase 1 should productize and prove the existing read-action loop for issue resolution.
- Phase 2 and beyond should add Resolver dry-run and governed write execution.
- The main new product risk is no longer "can the LLM use multiple read actions before generation"; it is "can we govern write-capable resolution safely."

Roadmap identity:

- `006` is the parent product line, not an implementation slice.
- `006.1` is the first implementation phase and was previously drafted as `007`.
- `006.2`, `006.3`, and `006.4` extend the same product line; they are not separate products.
- A future `007` should be reserved for a different product line after Thinker/Resolver, not for Phase 1 of this one.

## Build Sequence Implementation Plan

Build the product line in this order. Do not skip a phase because later phases depend on earlier data models, audit semantics, UI evidence, and safety proofs.

### `006.1` Thinker Read-Only Issue Resolution

Current code foundation:

- `ReadActionResolutionService` exists and already enforces allowlisted `READ` actions.
- The curated commerce pack already includes `resolver_assistant` and iterative `thinker` modes.

New product work:

- issue sessions
- evidence bundles
- resolution plans
- Elite-only Thinker activation gates
- source-cited end-user depth-layer answer
- operator session inspection
- partner-safe session summaries
- readiness scenarios for read-only diagnosis, prompt injection, stale evidence, cross-tenant attempts, and write-required escalation

Implemented exit:

- a sandbox Elite deployment can run read-only issue diagnosis end to end with no write path available.

### `006.2` Resolver Dry-Run And Policy Simulation

Current prerequisite:

- `006.1` issue sessions, evidence, and plans exist.

New product work:

- write intent proposal model
- policy decision model
- dry-run/simulation result
- confirmation preview only
- denied/simulated action audit
- operator/partner preview surfaces

Implemented exit:

- the system can show what it would do, why policy allows or denies it, and what confirmation would be required, with zero real mutation.

### `006.3` Governed Low-Risk Write Execution

Current prerequisite:

- `006.2` dry-run and policy simulation are proven.

New product work:

- one low-risk action family
- explicit confirmation
- execution gateway
- idempotency
- product-boundary execution
- post-action verification
- audit before and after execution
- emergency kill switch

Implemented exit:

- one reversible or low-risk write executes safely in sandbox after policy, dry-run when required, confirmation, idempotency, and audit.

### `006.4` Productized Resolution Assistant Readiness And Rollout

Current prerequisite:

- `006.3` proves at least one governed low-risk write.

New product work:

- product packaging
- operator readiness UI
- partner rollout workflow
- merchant-safe health surfaces
- support runbook and export packet
- pricing/tier posture
- design-partner rollout packet
- Thinker/Resolver readiness suite

Implemented exit:

- non-founder operators can configure, verify, audit, support, and roll out the product without reading chat history.

## Implementation Summary - 2026-04-29

Backend:

- Added persisted Thinker/Resolver tables in `V70__thinker_resolver_governed_issue_resolution.sql`.
- Added the `com.ai.fabric.platform.backend.thinker` package with deployment controls, issue sessions, evidence, plans, audit events, Resolver proposals, policy decisions, dry-runs, executions, and operator/partner/Shopify controllers.
- Connected `PublicConsumerBridgeChatService` so eligible Thinker-mode public chat can record real issue sessions and evidence from runtime read-action diagnostics.
- Enforced Shopify Companion Elite gating, per-deployment kill switches, fail-closed policy, explicit confirmation, idempotency, partner assignment checks, and partner-safe redaction.
- Implemented one real low-risk execution family: `SUPPORT_ESCALATION`, writing existing Partner Enablement evidence bundle and support escalation records.

Frontend:

- Added Platform UI route `/thinker-resolver` for readiness, controls, sessions, evidence, export, proposals, dry-runs, executions, and ledgers.
- Added Partner UI route `/thinker` plus a store-workspace Thinker tab for assigned-store redacted support work.
- Added Shopify Bridge merchant-session Thinker health and an embedded merchant admin health card.

Verification and guides:

- Added `scripts/verify-thinker-resolver-readiness.sh`.
- Added standalone and full release-gate suite wiring for `thinker-resolver-readiness`.
- Added operator, partner, and developer guides under `Final_Documentation`.

Current proof state:

- Local compile/build proof passed before final test hardening: Platform backend compile, Shopify Bridge compile, Platform UI build, Partner UI build, Bridge UI build, and shell syntax checks.
- Focused backend and bridge tests are being hardened against real schema constraints; final pass and live verification must be recorded before marking deployed complete.

---

## Read First

Before expanding this blueprint into implementation, read:

1. [CODEX_WORKING_CONTEXT.md](../../../../../../../../Final_Documentation/Development_Guides/LLM-guides/CODEX_WORKING_CONTEXT.md)
2. [Codex_Strategic_Context.md](../../../../../../../../Final_Documentation/Development_Guides/LLM-guides/Codex_Strategic_Context.md)
3. [005_SHOPIFY_COMPANION_FIRST_PRODUCT_READINESS_AUDIT.md](005_SHOPIFY_COMPANION_FIRST_PRODUCT_READINESS_AUDIT.md)
4. [PLAN_READ_ONLY_ACTION_RESOLVER_AND_THINKER_MODES.md](../../../../../../../../Final_Documentation/System_Archtecture_Guides/PLAN_READ_ONLY_ACTION_RESOLVER_AND_THINKER_MODES.md)
5. [PLAN_SHOPIFY_CONTROL_AND_EXECUTION_PLANE_SEPARATION.md](../../../../../../../../Final_Documentation/System_Archtecture_Guides/PLAN_SHOPIFY_CONTROL_AND_EXECUTION_PLANE_SEPARATION.md)

Working rule:

- Keep [CODEX_WORKING_CONTEXT.md](../../../../../../../../Final_Documentation/Development_Guides/LLM-guides/CODEX_WORKING_CONTEXT.md) updated with compact decisions/status.
- Keep [Codex_Strategic_Context.md](../../../../../../../../Final_Documentation/Development_Guides/LLM-guides/Codex_Strategic_Context.md) updated when product sequence or strategic positioning changes.
- Do not bury write-action safety decisions in implementation code only.

---

## Product Definition

Thinker/Resolver is two cooperating layers.

### Thinker

Purpose:

- understand the user's issue
- classify issue type and risk
- ask clarifying questions when needed
- inspect trusted read-only system state
- collect evidence
- identify possible root causes
- propose a resolution plan
- decide whether a write action is needed
- explain uncertainty and escalation paths

Thinker is allowed to:

- run planner-eligible read-only actions through the existing read-action resolution loop
- use multiple bounded read actions before final generation when policy allows
- use retrieval when it improves evidence
- summarize facts
- propose next steps
- recommend a resolver action

Thinker is not allowed to:

- execute write actions
- invent system state
- override policies
- create permissions
- mark a resolution complete without evidence

### Resolver

Purpose:

- turn an approved plan into governed action execution
- validate action eligibility
- run dry-run/simulation when available
- request confirmation or approval
- execute safe read/write actions
- record audit trail
- verify the result
- expose rollback or compensation guidance when possible
- escalate when the action is unsafe, unsupported, or ambiguous

Resolver is allowed to:

- execute registered actions only
- execute write actions only when policy permits
- require user/operator confirmation based on risk
- stop and escalate if confidence or policy is insufficient

Resolver is not allowed to:

- execute unregistered actions
- bypass tenant/user/app permissions
- perform destructive actions without explicit policy and confirmation
- silently retry risky writes
- hide failures
- claim a rollback exists when it does not

---

## Product Truth

Non-negotiable truths:

- Thinker/Resolver is a governed resolution system, not a chatbot.
- The LLM is not the authority for action availability, safety, permissions, or execution.
- The platform is the authority for policy, validation, execution boundaries, audit, and fail-closed behavior.
- Read actions and write actions must be classified separately.
- Write actions require explicit governance before any customer-facing claim.
- High-risk writes require confirmation or approval.
- Destructive actions are out of scope for early versions unless rollback/compensation and approval are proven.
- Every write attempt must have an audit record, whether it succeeds, fails, is denied, or is cancelled.
- Every resolution must show evidence, not only a final answer.
- Human handoff is a product feature, not a failure.

Tier principle:

- Read-only thinking can be lower tier.
- Governed resolving belongs to a higher tier because it requires policy, audit, support, and accountability.
- Shopify Companion Free/Starter must stay read-only; governed actions remain Elite-only until proven.

---

## Target Users

Primary buyer:

- developers, integrators, agencies, and platform teams adding AI issue-resolution to existing apps

Primary operator:

- app/platform admin configuring actions, policies, and escalation

Primary end user:

- customer, shopper, employee, or account user trying to resolve an issue

Partner role:

- configure client-specific action packs
- run resolution readiness tests
- attach evidence to escalations
- support client rollout
- never redefine canonical safety thresholds

Merchant/customer role:

- approve installation, access, and scoped action permissions
- configure visible product behavior
- review audit/support outcomes where appropriate

---

## First Use Cases

Good first use cases:

- answer and resolve account setup issues
- diagnose failed checkout or failed workflow states
- update low-risk user preferences after confirmation
- resend verification or notification emails
- create a support ticket with evidence
- apply non-destructive configuration changes
- explain and route billing/subscription questions without direct destructive action
- Shopify Elite governed action rehearsal using current audit primitives

Avoid first:

- refunds
- cancellations
- financial adjustments
- permission grants
- account deletion
- irreversible data mutation
- broad admin automation
- cross-system write chains

First reference implementation should be a bounded support domain with low-risk writes, not a broad all-actions assistant.

---

## Core Capability Map

### Issue Session

Captures:

- tenant/customer/app context
- end-user identity context
- issue text and conversation state
- detected issue category
- risk level
- evidence gathered
- proposed resolution plan
- chosen action path
- final outcome
- escalation state

### Evidence Bundle

Contains:

- read-action results
- planner iteration diagnostics
- retrieval snippets
- user-provided context
- system state snapshot metadata
- confidence and uncertainty
- source timestamps
- redaction state

Rules:

- evidence must be inspectable
- evidence must be redacted for the viewer
- stale evidence must be marked stale
- final answers must distinguish observed facts from inferred causes

### Resolution Plan

Contains:

- summary of issue
- diagnosis
- options considered
- recommended action
- action risk
- expected outcome
- user-facing explanation
- required confirmation or approval
- rollback/compensation note
- escalation fallback

### Action Registry

Every action must define:

- action id
- product/app boundary
- owner service
- access mode: `READ`, `WRITE`, or `READ_WRITE`
- side-effect level
- risk level
- allowed actor types
- required scopes
- tenant/store/customer binding rules
- parameter schema
- validation rules
- dry-run support
- idempotency support
- rollback/compensation support
- confirmation requirement
- approval requirement
- audit classification
- rate limits
- timeout and retry policy
- result redaction policy

### Policy Engine

Must decide:

- whether Thinker may call a read action
- whether Resolver may propose a write action
- whether Resolver may execute a write action
- whether confirmation is required
- whether operator approval is required
- whether action is blocked by tier, tenant, identity, risk, or missing evidence
- whether escalation is required

Policy must be fail-closed.

### Execution Gateway

Responsibilities:

- validate action is registered
- validate tenant/app/user binding
- validate scopes and policies
- run dry-run when required
- execute action through authoritative product/service boundary
- normalize action result
- record audit trail
- return evidence to the resolution session

The runtime should not call sensitive third-party APIs directly when a product boundary exists.

For Shopify:

- Shopify control/execution plane owns Shopify calls.
- Platform/runtime requests approved capability execution through the Shopify boundary.
- Deployment-scoped config may request capabilities, but Shopify credentials and policy remain centralized.

---

## Safety Model

Action risk levels:

- `READ_ONLY`: no state change
- `LOW_WRITE`: reversible or low-impact preference/config update
- `MEDIUM_WRITE`: meaningful state change requiring confirmation
- `HIGH_WRITE`: financial, access, legal, destructive, irreversible, or trust-sensitive action
- `BLOCKED`: out of scope for AI execution

Required controls:

- read action allowlist
- write action allowlist
- tenant/customer binding validation
- user identity validation
- actor permission check
- plan approval check
- dry-run for write-capable actions when supported
- explicit confirmation for medium/high risk
- operator approval for high-risk or sensitive writes
- audit trail before and after execution
- idempotency key for writes
- retry policy that avoids duplicate writes
- rollback/compensation declaration
- escalation path

Hard blocks:

- no unregistered action execution
- no policy mutation by LLM
- no permission creation by LLM
- no hidden writes
- no destructive autonomous actions
- no cross-tenant action execution
- no execution when evidence is stale or binding is ambiguous
- no action from prompt-injected instructions

---

## UI Requirements

Thinker/Resolver needs three UI surfaces.

### End-User Resolution Surface

Purpose:

- describe the issue
- show what the assistant understands
- ask clarifying questions
- present evidence-backed diagnosis
- show proposed resolution
- request confirmation when needed
- show final outcome or escalation

Required states:

- collecting issue
- thinking/gathering evidence
- needs clarification
- plan proposed
- confirmation required
- action running
- resolved
- blocked
- escalated
- failed with recovery path

### Operator/Admin Console

Purpose:

- configure action registry
- configure policies
- configure risk thresholds
- review audit logs
- review denied actions
- inspect resolution quality
- manage escalations
- disable actions quickly

Required views:

- action catalog
- policy matrix
- risk/confirmation settings
- audit trail
- resolution sessions
- failed/blocked actions
- escalation queue
- readiness tests

### Partner/Integrator Surface

Purpose:

- configure client-specific action packs
- map app APIs to action schemas
- run readiness tests
- collect evidence packets
- escalate failed resolution cases

Scope rule:

- partner UI may operationalize resolution setup later
- platform operator UI owns canonical policy and safety thresholds first
- merchant/client approval is required before partner-configured write actions affect client systems

---

## Data Ownership

Platform owns:

- action registry
- policy definitions
- issue session metadata
- resolution plans
- audit records
- approval records
- evidence metadata
- readiness test results

Product/service boundary owns:

- domain credentials
- domain-specific execution
- domain-specific source truth
- service-specific rate limits
- third-party API behavior

Partner owns:

- client implementation notes
- client-specific test packs
- evidence packets they generate
- escalation context

Partner does not own:

- platform safety thresholds
- product truth
- global action policy
- merchant/client consent
- third-party credentials

---

## Implementation Phases

### Phase 0: Blueprint And Readiness Gate

Goal:

- keep this as a blueprint until `005` is complete
- choose first reference domain
- define minimum action governance contract
- define resolution readiness audit pack

Exit criteria:

- Shopify Companion readiness audit is complete or explicitly accepted as the active blocker
- first reference domain is selected
- low-risk write use cases are identified
- action governance contract is reviewed

### Phase 1: Thinker-Only Read Resolution (`006.1`)

Goal:

- productize the existing read-action resolution loop for issue diagnosis
- resolve issues with bounded multi-read-action planning and evidence
- no write action execution

Scope:

- issue session model
- existing read-action planning loop integration
- issue-resolution mode and prompts
- evidence bundle
- resolution plan
- escalation when write is needed
- readiness test pack

Exit criteria:

- issue diagnosis is grounded in evidence
- multi-read-action evidence can feed final generation
- no write action can execute
- source gaps are handled honestly
- operator can inspect evidence and plan

### Phase 2: Resolver Dry-Run (`006.2`)

Goal:

- introduce Resolver without real writes
- validate plans, policies, confirmations, and audit model

Scope:

- write action proposal
- policy check
- dry-run/simulation
- confirmation UI
- audit record for proposed/denied/simulated actions
- failure and escalation paths

Exit criteria:

- Resolver can show what it would do
- denied writes are explainable
- audit trail exists before real writes
- no real state mutation happens

### Phase 3: Governed Low-Risk Writes (`006.3`)

Goal:

- allow bounded, reversible, low-risk writes with confirmation and audit

Scope:

- one reference action pack
- idempotency key
- confirmation
- execution gateway
- post-action verification
- rollback/compensation note
- operator audit

Exit criteria:

- low-risk writes execute only when policy permits
- failed writes are visible and recoverable
- action duplication is prevented
- audit evidence is sufficient for support review

### Phase 4: Productized Resolution Assistant (`006.4`)

Goal:

- package Thinker/Resolver as a reusable product archetype

Scope:

- product shell
- operator/admin console
- partner setup flow
- readiness audit UI integration
- query/scenario pack
- support runbook
- pricing/tier model

Exit criteria:

- product can be deployed to a second bounded domain
- partner can configure client-specific tests without changing canonical safety
- non-founder operator can review readiness and audit evidence

---

## Readiness Audit For Thinker Resolver

This product needs its own readiness audit before any design partner.

Required scenario categories:

- issue diagnosis with enough evidence
- issue diagnosis with missing evidence
- clarification required
- read-only resolution
- write needed but blocked by tier
- write needed but blocked by policy
- write proposed with dry-run
- write requires confirmation
- write denied by permission
- write execution success
- write execution failure
- stale evidence
- prompt injection attempt
- cross-tenant attempt
- escalation required

Minimum pass criteria:

- every scenario has expected behavior
- unsafe writes fail closed
- action result evidence is visible
- audit record exists for proposed, denied, simulated, executed, failed, and cancelled actions
- UI shows status clearly
- support packet can explain what happened without reading chat history
- no secrets or raw credentials appear in evidence

---

## Product UI Readiness

Do not ship Thinker/Resolver without visible governance.

Required UI proof:

- end-user sees diagnosis before action
- user sees what action will happen before confirmation
- action risk is visible in plain language
- result is visible after execution
- escalation is available
- operator sees action policy and audit history
- partner sees only scoped client/store/workspace data
- stale evidence is visually flagged
- blocked actions explain why without exposing internals

---

## Commercial Model

Likely packaging:

- Thinker-only read resolution: lower paid tier or platform add-on
- Resolver dry-run: higher tier or implementation package
- Governed writes: premium tier because audit, support, and risk are higher
- High-risk domain actions: custom approval, enterprise, or implementation-led only

Do not price governed writes as a simple usage add-on until support and audit costs are known.

---

## Partner Enablement Relationship

Thinker/Resolver strengthens Partner Enablement, but should not start as a partner-only feature.

Partner can:

- configure client-specific action packs
- write client-specific scenario tests
- run readiness audits for assigned clients
- collect evidence
- escalate failures
- support rollout

Partner cannot:

- bypass platform policy
- override canonical thresholds
- enable write actions without merchant/client approval
- access unassigned client data
- change product-level action truth

---

## Shopify Relationship

Thinker/Resolver can become the long-term foundation for Shopify Elite governed actions.

Shopify mapping:

- Free: AI search only
- Starter: read-only embedded intelligence
- Elite: governed resolving only after audit, confirmation, execution, and support paths are ready

Do not use Shopify as the first broad write-action playground. Use Shopify only where the control/execution boundary, entitlement, and audit model are already proven.

---

## Resolved Decisions For First Slice

- First reference domain: Shopify Companion Elite deep issue diagnosis.
- First write action family: product-owned Partner Support escalation and Partner Evidence Bundle creation.
- Policy boundary: Platform owns policy decisions, kill switches, audit ledgers, and execution records; product services expose health and product-context surfaces.
- Operator surface: Platform UI owns investigation, policy, dry-run, and execution ledgers; Partner UI owns assigned-store redacted views.
- Approval posture: first slice supports operator-governed low-risk execution only; merchant/partner assignment gates still apply before partner visibility.
- Dry-run posture: every Resolver execution path in the first slice has a dry-run preview before governed execution.

---

## Non-Goals

- Autonomous destructive actions.
- Broad workflow automation.
- Public marketplace action packs.
- Partner-defined safety policy.
- Unbounded API agents.
- Runtime-owned third-party credentials.
- Write action support for Free or Starter Shopify tiers.
- WooCommerce or second vertical implementation inside this blueprint.

---

## Acceptance Criteria For This Blueprint

This blueprint is complete when:

- Thinker and Resolver responsibilities are separate.
- Read/write action boundaries are explicit.
- Policy, confirmation, audit, and recovery are non-negotiable.
- UI surfaces are defined.
- Partner role is supportive, not authoritative.
- Shopify relationship is clear and does not weaken Free/Starter read-only truth.
- Implementation is gated behind `005`.

---

## Next Handoff

The `006.x` family is implemented for the first Shopify Companion Elite governed slice. Next work should expand from this product line in controlled increments:

1. Add a second low-risk action family only after it has dry-run, policy, execution ledger, recovery guidance, and live verifier coverage.
2. Add merchant-facing approval prompts only when the UI can show clear before/after state and policy outcome.
3. Add partner-authored diagnostics only after operator-owned policy prevents partner bypass.
4. Start secondary domains only after Shopify Companion Elite has design-partner usage evidence.

---

## Completion Section For Future LLM

Append compact completion notes here when this blueprint is revised or promoted.

Required fields:

- decision summary
- selected reference domain, if chosen
- changed files
- validation performed
- blockers
- next handoff

Do not paste long logs, secrets, or raw diffs.

### 2026-04-29 Completion Notes

- Decision summary: implemented Thinker/Resolver as one product line with Shopify Companion Elite as the first reference vertical and support escalation as the first governed low-risk action family.
- Changed files: Platform backend Thinker package, Partner Enablement permission backfill, Shopify Bridge Thinker health projection, Platform UI, Partner UI, Shopify Bridge UI, release-gate catalog/script wiring, verification scripts, and operator/partner/developer guides.
- Validation performed: local backend tests, full Platform backend test suite, full Shopify Bridge test suite, Platform UI build, Partner UI build, Bridge UI build, shell syntax checks, `git diff --check`, changed-code no-stub scan, and live `scripts/verify-thinker-resolver-readiness.sh`.
- Live proof: verifier passed on 2026-04-29 against Platform backend `https://ai-fabric-framework-production-324f.up.railway.app`, Partner UI `https://ai-fabric-framework-production-158d.up.railway.app`, and store `shopping-companion-test.myshopify.com` with `THINKER_REQUIRE_PARTNER_PROOF=true` and `THINKER_EXECUTE_LOW_RISK=true`.
- Deployment proof: implementation commits `7931a918` and `809696dc` were pushed to `Platform-V6`; deploy-branch commits `0b1ca07d` and `8bfc30e3` were pushed to `Platform_V1`.
- Blockers: none for the first governed slice.
- Next handoff: expand governed action families only with real dry-run, policy, audit, execution, recovery, partner-scope, release-gate, and live verification coverage.
