# Thinker Phase 1 Read-Only Issue Resolution Productization

Status: implementation handoff (2026-04-25)

Owner mode: technical LLM implementation session

Roadmap phase: Thinker/Resolver Phase 1 — Thinker-only read-only issue resolution

Priority: P1 (do not start before `005` reaches `DESIGN_PARTNER_READY` for Shopify Companion)

Depends on:

- [001_SHOPIFY_COMPANION_LAUNCH_TRUTH_ENFORCEMENT.md](001_SHOPIFY_COMPANION_LAUNCH_TRUTH_ENFORCEMENT.md)
- [002_SHOPIFY_COMPANION_STOREFRONT_PRODUCT_SHELL.md](002_SHOPIFY_COMPANION_STOREFRONT_PRODUCT_SHELL.md)
- [003_SHOPIFY_COMPANION_STARTER_LAUNCH_PACKAGE.md](003_SHOPIFY_COMPANION_STARTER_LAUNCH_PACKAGE.md)
- [004_PARTNER_ENABLEMENT_FOUNDATION.md](004_PARTNER_ENABLEMENT_FOUNDATION.md)
- [005_SHOPIFY_COMPANION_FIRST_PRODUCT_READINESS_AUDIT.md](005_SHOPIFY_COMPANION_FIRST_PRODUCT_READINESS_AUDIT.md)
- [006_THINKER_RESOLVER_GOVERNED_ISSUE_RESOLUTION_BLUEPRINT.md](006_THINKER_RESOLVER_GOVERNED_ISSUE_RESOLUTION_BLUEPRINT.md)

Related foundation:

- [PLAN_READ_ONLY_ACTION_RESOLVER_AND_THINKER_MODES.md](../../../../../../../../Final_Documentation/System_Archtecture_Guides/PLAN_READ_ONLY_ACTION_RESOLVER_AND_THINKER_MODES.md)
- [PLAN_SHOPIFY_CONTROL_AND_EXECUTION_PLANE_SEPARATION.md](../../../../../../../../Final_Documentation/System_Archtecture_Guides/PLAN_SHOPIFY_CONTROL_AND_EXECUTION_PLANE_SEPARATION.md)

---

## Strategic Handover

The `006` blueprint defined Thinker/Resolver as the next product archetype. This handoff implements Phase 1: productize the existing read-action resolution loop for issue diagnosis. No write actions in this phase. No Resolver in this phase.

Accepted state from prior phases:

- Shopify Companion is the anchor reference vertical.
- Free / Starter / Elite tier truth is enforced.
- Free is AI search only.
- Starter is read-only embedded intelligence with no order lookup.
- Elite is the only tier eligible for governed actions, and governed actions remain unbuilt as a customer-facing capability.
- Partner Enablement Foundation provides the partner workspace, scoped access, evidence bundle, and escalation pattern.
- The platform already implements bounded multi-read-action planning through `ReadActionResolutionService`.

Phase 1 goal:

> Wrap the existing read-action resolution loop into a productized issue-resolution surface that captures issue context, runs bounded read-only diagnosis, produces an evidence bundle and resolution plan, and escalates when a write would be needed — without ever executing a write.

Strategic posture:

- Do not introduce write actions in this phase.
- Do not introduce Resolver in this phase.
- Do not call third-party APIs from the runtime; use product/service boundaries.
- Build on `ReadActionResolutionService` and the curated `commerce.yml` action pack — do not reimplement.
- Keep Thinker output inspectable: every resolution must show evidence and source, not just a final answer.
- Keep prompt-injection defense visible from the start, not bolted on later.
- Frame the Phase 1 surface as a diagnosis and explanation feature, not an "AI agent."

Why this goes next after `005`:

- Phase 1 is the lowest-risk, highest-leverage step in the Thinker/Resolver roadmap because the planning loop already exists.
- It produces an immediately useful Loom Companion Elite feature: deeper, evidence-backed answers when a shopper asks a multi-step question.
- It exercises the issue session, evidence bundle, resolution plan, and escalation primitives that Phase 2 (dry-run) and Phase 3 (governed writes) will require.
- It validates the prompt and policy boundary before any write action is in scope.
- It gives partners and operators a concrete artifact (evidence + plan) to evaluate, support, and audit.

What this is not:

- Not autonomous resolution.
- Not write execution.
- Not a replacement for retrieval-augmented chat — it is a deeper mode that runs only when needed.
- Not a Shopify-only capability — the productized surface should be usable by any future bridge service.

---

## Read First

Read these before editing code or docs:

1. [CODEX_WORKING_CONTEXT.md](../../../../../../../../Final_Documentation/Development_Guides/LLM-guides/CODEX_WORKING_CONTEXT.md)
2. [Codex_Strategic_Context.md](../../../../../../../../Final_Documentation/Development_Guides/LLM-guides/Codex_Strategic_Context.md)
3. [006_THINKER_RESOLVER_GOVERNED_ISSUE_RESOLUTION_BLUEPRINT.md](006_THINKER_RESOLVER_GOVERNED_ISSUE_RESOLUTION_BLUEPRINT.md)
4. [005_SHOPIFY_COMPANION_FIRST_PRODUCT_READINESS_AUDIT.md](005_SHOPIFY_COMPANION_FIRST_PRODUCT_READINESS_AUDIT.md)
5. [PLAN_READ_ONLY_ACTION_RESOLVER_AND_THINKER_MODES.md](../../../../../../../../Final_Documentation/System_Archtecture_Guides/PLAN_READ_ONLY_ACTION_RESOLVER_AND_THINKER_MODES.md)
6. [PLAN_SHOPIFY_CONTROL_AND_EXECUTION_PLANE_SEPARATION.md](../../../../../../../../Final_Documentation/System_Archtecture_Guides/PLAN_SHOPIFY_CONTROL_AND_EXECUTION_PLANE_SEPARATION.md)

Code anchors to read before changing:

- `ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/intent/orchestration/information/ReadActionResolutionService.java`
- `ai-infrastructure-module/ai-infrastructure-core/src/test/java/com/ai/infrastructure/intent/orchestration/information/ReadActionResolutionServiceTest.java`
- `ai-infrastructure-module/ai-infrastructure-core/src/test/java/com/ai/infrastructure/intent/orchestration/pipeline/steps/IntentHandlingStepReadActionResolutionTest.java`
- `ai-infrastructure-module/curated/ai-curated-commerce/src/main/resources/ai-curated/packs/commerce.yml`
- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/web/ShopifyMerchantController.java`
- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/web/ShopifyBridgeAdminController.java`

---

## Working Rule

The technical LLM session must keep this file updated:

- [CODEX_WORKING_CONTEXT.md](../../../../../../../../Final_Documentation/Development_Guides/LLM-guides/CODEX_WORKING_CONTEXT.md)

Use this compact template:

```text
- Thinker Phase 1 status: <complete/partial/blocked>.
- Reference domain: <chosen domain>.
- Changed files: <compact list>.
- Decisions: <only new decisions>.
- Verification: <commands run and pass/fail>.
- Live verification: <passed/skipped/blocker/not needed>.
- Blockers: <none or compact blockers>.
- Next handoff: <next concrete step>.
```

Do not paste long logs, diffs, secrets, or noisy reasoning into the working context.

---

## Reference Domain Selection

The blueprint left reference domain selection open. This handoff selects:

**Primary reference domain: Shopify Companion Elite — read-only deep diagnosis surface.**

Rationale:

- Shopify Companion is the anchor vertical with verified embedded intelligence.
- Existing curated commerce action pack (`commerce.yml`) contains the read actions Thinker needs.
- Elite is the only tier where users expect deeper, multi-step reasoning and evidence-backed answers.
- Phase 1 introduces no write capability, so it does not violate Free/Starter read-only truth.
- The output (evidence + resolution plan + optional escalation) is directly useful for Loom Companion's existing support handoff.

First scenario pack scope:

- product comparison with conflicting attributes ("which of these two has X feature?")
- policy lookup that requires reading multiple pages ("can I return this if I bought it from a marketplace listing?")
- shipping/availability questions that require checking product + variant + collection data
- multi-step product discovery ("show me a winter jacket under £100 from your sustainability collection")
- "I cannot find X" diagnosis (does the product exist, is it out of stock, is it filtered out)

Out of scope for Phase 1 first scenario pack:

- order status, order history, refund eligibility (these need order read access and belong to Elite governed actions Phase 3)
- account changes
- cart writes
- support ticket creation
- any third-party API call from the runtime

Secondary reference domain (optional, after Shopify Phase 1 is verified):

- Document Q&A: same Thinker pattern over uploaded documents instead of Shopify catalog.
- Internal Wiki: same pattern over Confluence/Notion.

Do not start a secondary domain in the same session that delivers Shopify Phase 1.

---

## Implementation Brief

Task:

- productize the existing read-action resolution loop into a Thinker issue-resolution surface for Shopify Companion Elite

Primary outcome:

- a shopper asking a multi-step question on an Elite store gets an answer grounded in bounded read-action evidence, with visible sources, with an explicit "I do not have enough information" path, and with safe escalation when an action would be needed

Authority boundary:

- platform owns issue session, evidence bundle, resolution plan, and policy
- product/service boundary owns the actual read-action execution against Shopify data
- runtime never calls Shopify Admin/Storefront APIs directly when the bridge service exists
- LLM proposes which read actions to run, platform decides which are eligible, bridge service executes them
- LLM never executes a write
- LLM never extends its own action allowlist
- LLM cannot mark a resolution complete without evidence

Complete product capabilities for Phase 1:

- Issue session model
- Issue classification (light: question type, risk class, evidence requirements)
- Multi-read-action planning loop (existing)
- Bounded iterations and bounded action count (existing)
- Evidence bundle assembly
- Resolution plan generation
- "Insufficient evidence" path with honest user-facing messaging
- "Write would be needed" escalation path
- Source attribution with redaction policy
- Prompt-injection defense at the issue intake boundary
- Operator audit log for issue sessions, planner iterations, executed read actions, evidence, and final outcomes
- Partner-visible issue session summary in the partner workspace
- Merchant-safe support packet entry for issue sessions
- UI surfaces: end-user (in companion chat depth layer), operator (audit/inspection), partner (read-only issue session summary)

Not in Phase 1:

- write action proposal
- write action policy
- dry-run/simulation
- confirmation flow
- approval flow
- rollback/compensation
- governed write execution
- Resolver UI
- Resolver API
- Resolver action registry beyond what's needed to mark "would-write" intents and escalate

---

## Build Order

### Step 0: Inventory and Boundary

Close:

- inventory the current `ReadActionResolutionService` capabilities and limits (max iterations, max actions per iteration, max total actions, eligibility rules)
- inventory the curated commerce action pack and confirm which actions are `READ` and `readActionResolutionEligible`
- inventory existing Loom Companion chat depth layer and decide where the Thinker mode hooks in
- inventory existing Shopify Bridge Service action execution path
- inventory existing audit and evidence storage patterns
- decide what is operator-only (planner iteration logs, raw action result payloads), partner-visible (issue session summary, evidence overview), merchant-safe (support packet entry), and end-user (final answer, source citations, escalation message)

Exit:

- Phase 1 has a clear product boundary and does not duplicate existing platform capability

### Step 1: Issue Session Model

Close:

- `IssueSession` entity (tenant, store, deployment, user identity context, locale, channel, started-at, completed-at, status, mode)
- `IssueSessionMode` enum (`THINKER_DEEP`, `THINKER_LIGHT` — Phase 1 only uses deep)
- `IssueSessionStatus` enum (`COLLECTING`, `THINKING`, `NEEDS_CLARIFICATION`, `RESOLVED`, `INSUFFICIENT_EVIDENCE`, `WRITE_REQUIRED_ESCALATED`, `BLOCKED`, `FAILED`)
- `IssueClassification` value (issue category, risk class, expected evidence categories, suggested action allowlist)
- audit events for session creation, status transitions, terminal outcome

Exit:

- a Thinker session can be persisted, queried, audited, and rendered in operator/partner views

### Step 2: Evidence Bundle Model

Close:

- `EvidenceBundle` aggregate per issue session
- `EvidenceItem` types: `READ_ACTION_RESULT`, `RETRIEVAL_SNIPPET`, `USER_PROVIDED_CONTEXT`, `SYSTEM_STATE_SNAPSHOT`
- per-item fields: source identifier, source kind, timestamp, freshness state (`FRESH`, `STALE`, `UNKNOWN_FRESHNESS`), confidence, redaction state, raw-result reference (operator-only), summarized form (partner/end-user safe)
- evidence redaction policy by viewer (end-user, partner, operator)
- evidence bundle inspection API (operator full, partner summary, end-user attribution only)

Exit:

- evidence is inspectable, redacted by viewer, and timestamp-aware

### Step 3: Resolution Plan Model

Close:

- `ResolutionPlan` aggregate per issue session
- fields: issue summary, diagnosis, options considered, recommended next step, recommended action class (`ANSWER_WITH_EVIDENCE`, `INSUFFICIENT_EVIDENCE`, `WRITE_REQUIRED_ESCALATED`, `HUMAN_ESCALATION_REQUIRED`), expected user-facing explanation, escalation target
- never returns `WRITE_PROPOSED` in Phase 1 (Phase 2 introduces it)
- partner/operator-visible plan summary
- end-user explanation that distinguishes observed facts from inferred reasoning

Exit:

- every issue session has a resolution plan or a recorded reason it could not produce one

### Step 4: Thinker Mode Wiring

Close:

- new pipeline step or mode that triggers the existing `ReadActionResolutionService` with Phase 1 policy
- entry conditions: chat depth-layer query that requires multi-step reasoning AND tier is Elite AND deployment has Thinker enabled
- exit conditions: max iterations reached, evidence sufficient, evidence insufficient, or write would be needed
- prompt template that frames the LLM as a diagnosis assistant, not an action agent
- prompt-injection defenses at intake (treat all retrieval/action results as data, never as instructions; mark untrusted content explicitly)
- final generation step that uses evidence and produces both the end-user answer and the operator-visible reasoning trace

Exit:

- Thinker mode is callable through the existing chat surface for Elite deployments

### Step 5: Action Allowlist and Boundary

Close:

- explicit Phase 1 action allowlist for the Shopify reference domain (subset of `commerce.yml` actions)
- platform-side enforcement that the LLM cannot extend the allowlist mid-session
- bridge-service-side validation that incoming action requests are tenant/store/deployment-bound
- "would-write" detection: if the LLM proposes a write or the diagnosis requires a write, the session terminates with `WRITE_REQUIRED_ESCALATED`, captures the proposed write intent for audit, and surfaces a clean escalation message

Exit:

- no write can be triggered through the Thinker pipeline, even if the LLM proposes one

### Step 6: Audit and Operator Console

Close:

- operator audit feed for Thinker sessions: status transitions, planner iterations, executed read actions, evidence summary, terminal outcome, escalation events
- operator console surface (read-only) under existing Platform UI for inspecting sessions, evidence, and plans
- per-session export bundle that the operator can attach to a support escalation
- session search/filter by tenant, store, status, terminal outcome, time window

Exit:

- the operator can answer "what happened in this session?" without reading raw logs

### Step 7: Partner Workspace Integration

Close:

- partner workspace surface entry for Thinker sessions on assigned stores only
- partner-safe summary (no raw action payloads, no internal reasoning trace, no secrets)
- evidence bundle download in partner-safe form
- escalation entry point that prefills issue session evidence into a new partner support escalation
- audit trail of partner views and exports

Exit:

- a partner can review Thinker outcomes for assigned stores and escalate with structured evidence

### Step 8: End-User Surface

Close:

- depth-layer chat behavior change: when Thinker is engaged, show a brief "let me check on that" indicator and a final answer with source attribution
- visible source citations from evidence
- explicit "insufficient evidence" message when the bundle cannot support a confident answer
- explicit "this needs a human" message when escalation is required, with link/button to existing support handoff
- never expose operator/partner internal reasoning to the end user
- never expose raw action payloads to the end user

Exit:

- end users see a more capable, more honest companion without seeing the machinery

### Step 9: Readiness Test Pack

Close:

- automated readiness scenarios covering the Phase 1 first-scenario list
- explicit unsafe scenarios that must fail closed: prompt-injection attempt, cross-tenant attempt, write-required scenario, stale evidence
- repeatable verification script that produces a pass/fail report and an evidence sample
- partner-visible verification pack entry that uses the readiness test results

Exit:

- the readiness pack can be run before exposing Thinker to a design partner store

### Step 10: Rollout Gates

Close:

- internal verification gate: all Phase 1 scenarios pass against a sandbox Elite store
- design-partner readiness gate: at least one Elite design-partner store is selected and consented
- merchant-safe rollout copy and support runbook entry
- operator kill switch per deployment to disable Thinker without redeploying
- decision log for any scenario that is intentionally deferred to Phase 2 or Phase 3

Exit:

- Thinker Phase 1 can be turned on for a single Elite design-partner store and turned off cleanly

---

## Implementation Slices

Use these as discrete LLM work packages.

### Slice A: Domain Inventory and Reference Domain Lock-In

Deliver:

- inventory notes in `CODEX_WORKING_CONTEXT.md`
- explicit Phase 1 action allowlist for Shopify Companion Elite (commit as a config or constant, not a free-form list)
- decision record entry confirming Shopify Elite read-only diagnosis as the Phase 1 reference domain
- mapping of existing chat depth-layer entry points where Thinker mode hooks in

Exit:

- the action boundary and reference domain are written down, not implied

### Slice B: Issue Session, Evidence, and Plan Models

Deliver:

- `IssueSession`, `EvidenceBundle`, `ResolutionPlan` models with persistence
- audit hooks
- viewer-scoped serialization (operator full, partner summary, end-user attribution-only)
- focused unit tests for state transitions, evidence redaction, and plan validation

Exit:

- contracts exist and pass tests without UI integration

### Slice C: Thinker Pipeline Step

Deliver:

- pipeline step or mode that wraps `ReadActionResolutionService` for Thinker
- prompt template for diagnosis (not action)
- prompt-injection defenses at intake
- terminal outcome mapping (sufficient evidence → `RESOLVED`, insufficient → `INSUFFICIENT_EVIDENCE`, write needed → `WRITE_REQUIRED_ESCALATED`)
- focused tests covering each terminal outcome

Exit:

- Thinker can be triggered programmatically and produces a session, evidence bundle, and plan

### Slice D: Operator Console Inspection

Deliver:

- read-only operator UI page under existing Platform UI for issue sessions
- session list with filters
- session detail with evidence and plan
- export-bundle action

Exit:

- operator can review and export a session without reading logs

### Slice E: Partner Workspace Integration

Deliver:

- partner workspace surface listing Thinker sessions for assigned stores
- partner-safe session detail
- escalation entry that prefills evidence
- partner audit events

Exit:

- partner can support an Elite design-partner store using Thinker output

### Slice F: End-User Depth-Layer Behavior

Deliver:

- chat depth-layer flag/switch for Thinker mode
- visible source citations on returned answers
- "insufficient evidence" and "needs human" branches
- never-expose-internals checks

Exit:

- end-user experience is honest, useful, and merchant-safe

### Slice G: Readiness Pack and Rollout Gate

Deliver:

- automated readiness scenario runner
- explicit unsafe scenarios that must fail closed
- per-deployment kill switch
- decision log entry promoting Thinker Phase 1 from blueprint to design-partner-ready

Exit:

- a single Elite design-partner store can be onboarded with confidence

---

## Data Model Targets

Required concepts:

- `IssueSession`
- `IssueSessionMode`
- `IssueSessionStatus`
- `IssueClassification`
- `EvidenceBundle`
- `EvidenceItem`
- `EvidenceItemKind`
- `EvidenceFreshness`
- `ResolutionPlan`
- `ResolutionRecommendation`
- `ThinkerActionAllowlistEntry`
- `ThinkerSessionAudit`
- `ThinkerEscalationIntent` (records "write would be needed" intents without executing them)

Relationship rules:

- one `IssueSession` has at most one `EvidenceBundle`
- one `IssueSession` has at most one `ResolutionPlan`
- `EvidenceItem` references existing read-action execution records when possible (do not duplicate result payloads)
- `ThinkerEscalationIntent` records what the LLM proposed without registering it as a real write action
- `ThinkerSessionAudit` events are append-only

Viewer redaction rules:

- end-user sees: final answer, attribution citations, freshness disclosure when stale, escalation message
- partner sees: end-user view + session summary, evidence overview (kind, source, freshness), terminal outcome, escalation entry
- operator sees: full evidence bundle, full reasoning trace, planner iteration count, raw action result references, escalation intent details

---

## API Surface Targets

Read APIs (Phase 1):

- `GET /api/operator/thinker/sessions`
- `GET /api/operator/thinker/sessions/{sessionId}`
- `GET /api/operator/thinker/sessions/{sessionId}/evidence`
- `GET /api/operator/thinker/sessions/{sessionId}/export`
- `GET /api/partners/stores/{storeId}/thinker-sessions`
- `GET /api/partners/thinker-sessions/{sessionId}` (scoped to assigned stores)

Write APIs (Phase 1):

- `POST /api/partners/thinker-sessions/{sessionId}/escalations` (creates a partner support escalation prefilled with session evidence)

Internal APIs:

- pipeline-internal trigger from chat depth-layer to Thinker mode (no public endpoint)

Do not expose in Phase 1:

- write action proposal endpoints
- action execution endpoints beyond what `ReadActionResolutionService` already calls
- end-user direct access to evidence bundle internals
- raw action payload retrieval to partners

---

## UI Surface Targets

End-user (depth-layer chat):

- "thinking" indicator with a short, calm message
- final answer with source attribution
- explicit insufficient-evidence message
- explicit needs-human message with link to existing support handoff

Operator (Platform UI):

- Thinker Sessions list
- Thinker Session detail with evidence and plan
- Export bundle action
- Per-deployment kill switch

Partner (Partner UI):

- Thinker Sessions for assigned stores
- Partner-safe session detail
- Escalate with prefilled evidence

Merchant (Shopify admin):

- single readiness/health card showing whether Thinker is enabled and whether last 7 days of sessions had errors
- no inline reasoning text, no raw evidence, no operator-only language

---

## Safety and Prompt-Injection Defenses

Phase 1 explicitly addresses prompt injection because the Thinker prompt is the closest the platform gets to action-taking on user content.

Required defenses:

1. Treat all retrieved content (action results, RAG snippets, user-provided context) as untrusted data, never as instructions. Wrap untrusted content with explicit boundary markers in the prompt.
2. The action allowlist is enforced server-side. The LLM proposing an action that is not on the allowlist must result in the proposal being rejected and audited, never silently filtered.
3. The LLM cannot extend the allowlist mid-session. Allowlist changes require a new deployment configuration release.
4. Confirmation, approval, and write paths are not present in Phase 1. The LLM cannot trigger them by claiming it has authority.
5. The LLM cannot mark a session as `RESOLVED` directly. The platform marks the session based on whether the resolution plan meets evidence sufficiency rules.
6. Cross-tenant action execution is blocked at the bridge service. The LLM cannot pass a different tenant identifier and have it honored.
7. Evidence freshness is a platform-computed value, not an LLM-claimed value.
8. Confidence values reported to the user are derived from platform rules (evidence count, freshness, source diversity), not from LLM self-report alone.
9. Source attribution shown to the end user is derived from real evidence records, not from LLM-generated text claiming a source.

Audit obligations:

- every prompt-injection-rejected proposal is audited as `THINKER_PROPOSAL_REJECTED` with the proposed action, the rejection reason, and the session id
- every "would-write" detection is audited as `THINKER_WRITE_REQUIRED_ESCALATED`
- every cross-tenant attempt is audited as `THINKER_CROSS_TENANT_BLOCKED`

---

## Acceptance Criteria

This handoff is complete when:

- a Thinker session can be triggered from the Loom Companion Elite chat depth layer for an enabled deployment
- the session uses the existing `ReadActionResolutionService` with a Phase 1 action allowlist
- the session produces an evidence bundle and a resolution plan
- the session never executes a write action
- the session correctly terminates with `RESOLVED`, `INSUFFICIENT_EVIDENCE`, `WRITE_REQUIRED_ESCALATED`, or `BLOCKED`
- end users see a depth-layer answer with source attribution and an honest insufficient-evidence path
- operators see a Thinker Sessions list and detail view with evidence and plan
- partners see Thinker sessions for assigned stores and can escalate with prefilled evidence
- merchant admin shows a single Thinker health card without inline operator/partner content
- the readiness scenario pack passes against a sandbox Elite store
- prompt-injection scenarios fail closed and are audited
- cross-tenant scenarios fail closed and are audited
- write-required scenarios escalate cleanly and never call a write
- per-deployment Thinker kill switch works without redeploy
- `CODEX_WORKING_CONTEXT.md` has compact completion status
- decision log records that Thinker Phase 1 is design-partner ready

Do not accept docs-only completion. Phase 1 must be wired end-to-end against `ReadActionResolutionService` for a sandbox Elite deployment.

---

## Technical Handover

### Session Startup Checklist

- Run `git status --short` and identify unrelated dirty files before editing.
- Read `006` blueprint, `005` readiness audit, and the existing `ReadActionResolutionService` and `commerce.yml` action pack before changing anything.
- Search before changing so Thinker artifacts reuse existing chat depth layer, audit primitives, and bridge service action execution paths.
- Keep prior phase decisions intact (Free is AI search only; Starter is read-only; Elite is the only governed-action tier; partner workspace boundaries from `004`).
- Stage only files touched for Thinker Phase 1.
- Keep chat updates short and put compact implementation state in `CODEX_WORKING_CONTEXT.md`.

Suggested first search:

```bash
rg -n "ReadActionResolutionService|readActionResolutionEligible|thinker|Thinker|resolver|Resolver|issue session|IssueSession|evidence bundle|EvidenceBundle|resolution plan|ResolutionPlan|commerce.yml|chat depth|depth layer" \
  ai-infrastructure-module \
  product-services/shopify-bridge-service \
  Platfrom \
  Final_Documentation \
  doc/Productization/future-work/MarketPlace/Products/Strategy
```

### Architecture To Preserve

- `ReadActionResolutionService` is the planning loop. Do not reimplement it.
- Curated commerce action pack remains the source of read action definitions.
- Chat depth layer is the existing entry point. Thinker is a mode of the depth layer, not a parallel chat.
- Shopify Bridge Service is the execution boundary for Shopify reads. Runtime does not call Shopify directly.
- Free/Starter/Elite tier truth is enforced. Thinker mode is gated to Elite deployments.
- Audit primitives reuse existing platform audit infrastructure.
- Partner workspace integration follows `004` partner authorization model.
- Merchant admin remains merchant-safe. No raw evidence or reasoning trace inline.

### Documentation Targets

Create or update:

- `Final_Documentation/User_Guides/SHOPIFY_COMPANION_THINKER_DEEP_DIAGNOSIS_GUIDE.md` (merchant/operator-facing description, no internals)
- `Final_Documentation/Development_Guides/THINKER_PHASE_1_INTEGRATION_GUIDE.md` (developer-facing integration notes)
- `Final_Documentation/Development_Guides/THINKER_PHASE_1_OPERATOR_AUDIT_GUIDE.md` (operator audit and review procedure)
- `doc/Productization/future-work/MarketPlace/Products/Strategy/PARTNER_DASHBOARD_STRATEGY_PLAN.md` (partner-side Thinker integration entry)
- decision log entry promoting Thinker Phase 1 reference domain to "Shopify Companion Elite — read-only deep diagnosis"

---

## Verification

Always run:

```bash
git diff --check
```

If pipeline or planner code changes:

```bash
mvn -f ai-infrastructure-module/ai-infrastructure-core/pom.xml -q \
  -Dtest=ReadActionResolutionServiceTest,IntentHandlingStepReadActionResolutionTest,ThinkerSessionPipelineTest \
  test
```

If issue session, evidence bundle, or resolution plan models are added:

```bash
mvn -f Platfrom/backend/pom.xml -q \
  -Dtest=IssueSessionPersistenceTest,EvidenceBundleRedactionTest,ResolutionPlanValidationTest,ThinkerOperatorApiTest,ThinkerPartnerApiTest \
  test
```

If those tests do not exist yet, the implementing session should create equivalent focused tests for:

- session state transitions
- evidence freshness tagging
- evidence redaction by viewer
- resolution plan terminal outcome mapping
- write-required detection terminates the session without execution
- cross-tenant attempt blocked
- prompt-injection proposal rejected and audited
- partner can only see assigned-store sessions
- operator can export a full bundle
- merchant admin shows only the safe health card

If Shopify bridge action execution path changes:

```bash
mvn -f product-services/shopify-bridge-service/pom.xml -q \
  -Dtest=ShopifyBridgeReadActionExecutionTest,ShopifyBridgeThinkerActionAllowlistTest \
  test
```

If Platform UI changes:

```bash
npm --prefix Platfrom/ui run build
```

If Partner UI changes:

```bash
npm --prefix Platfrom/partner-ui run build
```

If Loom Companion chat depth layer or storefront surface changes:

```bash
npm --prefix product-services/shopify-bridge-service/ui run build
bash -n scripts/verify-shopify-companion.sh
```

For sandbox Elite deployment readiness scenario run:

```bash
scripts/run-thinker-readiness-pack.sh --deployment <sandbox-elite-deployment-id>
```

If that script does not exist yet, create it as part of Slice G.

---

## Phase 2 Preview

Phase 2 (Resolver Dry-Run) will:

- introduce write action proposals without executing them
- introduce dry-run/simulation outputs
- introduce confirmation UI
- introduce policy decisions for write actions
- introduce audit records for proposed/denied/simulated actions
- not execute any real writes

Do not begin Phase 2 in the same handoff as Phase 1. Phase 2 requires its own readiness audit, its own action governance contract review, and its own design-partner consent if real merchants are involved.

---

## Completion Section For Implementing LLM

Append a compact completion update here before ending the implementation session.

Required completion fields:

- implementation summary
- changed files
- decisions made
- tests/builds run
- live verification status against sandbox Elite deployment
- pushed commit refs, if pushed
- blockers or no pending handoff items

Do not include secrets, long logs, or raw diffs.
