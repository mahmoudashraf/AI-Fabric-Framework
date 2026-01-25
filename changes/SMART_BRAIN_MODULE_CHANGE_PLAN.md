# Smart Brain (Generic Agent Mode) — Change/Analysis Plan

## Status
Draft

## Summary (what this is)
“Smart Brain” is an optional **agent mode/profile** on top of the existing Orchestrator that:
- discovers **capabilities** from the host app (user-provided actions + retrieval actions)
- uses LLM intent extraction/planning to choose what to do next
- executes through the existing action framework (access control + confirmation + audits)
- returns grounded answers + optional “next best actions” suggestions

Smart Brain is intentionally **domain-agnostic**. Vertical “packs” (like offers/promotions) are separate and optional.

---

## 1) What do you mean by “generic” Smart Brain?
In this repo, the Orchestrator is already the “brain” (intent extraction + routing + safety rails).

So “Smart Brain” only makes sense if it adds **productized agent behavior** on top of what exists today:
- a clearer **capability model** (read vs write, risk levels, constraints)
- a consistent **policy layer** (allowlists, budgets, rate limits, per-tenant gates)
- optional **multi-step execution** (execute → observe → decide next), not just “extract one intent”
- opinionated **observability + auditing** for tool use

If Smart Brain is just “a new module that calls actions generically”, that’s mostly already supported via `ActionHandler` + `AIActionProvider` + the prompt builder.

---

## 2) Is the generic idea good or bad?
### Good idea when
- You want a **reusable agent runtime** for many domains (support, ops, commerce, analytics, admin tooling).
- You want “agentic” behavior with **enterprise guardrails** (PII/compliance/access control/confirmation).
- You want a **BYO-actions** framework (apps expose capabilities; the brain coordinates them).

### Bad idea when
- The goal is “an AGI brain that can do anything” without strict policy/constraints (it becomes unsafe and unpredictable).
- You try to compete head-on with generic agent frameworks on features alone (the market is crowded).
- You don’t have a clear “first vertical demo” to prove value (generic platforms are hard to sell without a killer use case).

**Recommendation:** Build Smart Brain as “Agent Mode for AI Fabric” and ship it with 1–2 reference packs/apps to prove it.

---

## 3) What is already supported in this repo (today)?
### 3.1 Capability discovery + prompt context
- Available actions are exposed via `AIActionProvider` and are included in system context.
- `SystemContextBuilder` already aggregates:
  - available actions
  - knowledge base overview (if present)
  - optional behavior context via `BehaviorContextProvider`
  - relationship-query entity types (via reflection)

### 3.2 Safe action execution primitives
- Actions run through `ActionHandler` with:
  - `validateActionAllowed(...)` for permission checks
  - `requiresConfirmation()` for human-in-the-loop writes
  - action drafts for missing required parameters
  - pending action store for confirmation flows
- Post-action grounded generation is supported when handlers provide explicit facts (`buildPostActionLlmFacts(...)`).

### 3.3 “Multi-intent” exists (but is pre-planned)
- `COMPOUND` intents can run multiple child intents, but children are decided **before** any action executes (no “observe → decide” loop).

---

## 4) What’s missing for a true generic Smart Brain?
### Runtime gaps
- A real **agent loop** option for single-request multi-step tasks:
  - plan → call tool → observe result → revise plan → next tool
- Clear **tool budgeting**:
  - max tool calls per request
  - max total tokens/cost
  - max wall time

### Governance gaps
- A first-class way to classify actions by:
  - read vs write
  - side effects (internal vs external)
  - data sensitivity
  - risk level
- Configurable **policy enforcement** (allow/deny, budgets, cooldowns) that is not “prompt-only”.

### Product gaps
- A consistent “agent result” contract:
  - final answer
  - executed actions (with outputs)
  - proposed actions (if any)
  - audit metadata (model, policy decisions, truncations)
- An evaluation harness (golden tests) to prevent prompt regressions.

---

## 5) Proposed design (how Smart Brain fits)
### 5.1 Capability model (keep BYO-actions)
Smart Brain treats the app as the source of capabilities:
- **Retrieval actions**: read-only system lookups (DB queries, analytics snapshots, configuration reads)
- **Mutation actions**: writes (create/update/publish/send), defaulting to confirmation + stricter access control

No “hardcoded domain” in Smart Brain core.

### 5.2 Policy layer (config-first, non-breaking)
Add a config-driven policy map keyed by action name (no API breaks needed):
- risk level (LOW/MEDIUM/HIGH)
- requires confirmation override (force-confirm)
- allowed roles/tenants
- rate limits / budgets (per hour/day)
- data sensitivity flags (e.g., disallow PII-returning actions unless opted-in)

### 5.3 Execution modes (same idea, generic)
1) **Advisor mode (default MVP)**: no writes; Smart Brain recommends and explains.
2) **Assisted mode**: writes are allowed but must be confirmed (existing confirmation flow).
3) **Autopilot mode (later)**: limited writes allowed only within strict policy + circuit breakers.

### 5.4 Two implementation options
**Option A — Multi-turn agent (lowest risk)**
- Use existing Orchestrator + conversation memory:
  - Smart Brain recommends the next best action(s)
  - user confirms/executes over subsequent turns
- Pros: simplest, safest; uses current primitives
- Cons: slower UX; no single-turn multi-step execution

**Option B — Add a bounded agent loop (higher capability)**
- New “agent loop” orchestrator path:
  - step 1: ask LLM for next action call (or finish)
  - step 2: execute action
  - step 3: feed bounded action facts back to LLM
  - repeat up to `maxSteps`
- Must enforce strict budgets, truncation, and logging.

### 5.5 Vertical packs (examples, not core)
Smart Brain core stays generic; packs supply actions + docs + demos, e.g.:
- **Offers pack** (see `changes/SMART_BRAIN_OFFERS_PACK_CHANGE_PLAN.md`)
- Support ops pack (ticket triage, knowledge base updates)
- Admin/ops pack (feature flags, incident checks, runbooks)
- Subscription pack (upgrade/downgrade, billing remediation)

### 5.6 Safety / governance requirements (non-negotiable)
- Hard policy checks in code (not prompt-only).
- Confirmation required for high-risk writes by default.
- PII/compliance rules apply to:
  - prompts (facts payload)
  - action results (sanitization/redaction where configured)
- Audit log:
  - requested intent/task
  - selected actions + inputs/outputs
  - policy decisions (allow/deny, forced confirmation, truncations)
  - model/provider metadata

---

## 6) Development path (recommended)
### Phase 0 — Define Smart Brain contract (1–2 weeks)
- Define `SmartBrainResult` shape (answer + executed/proposed actions + audit metadata).
- Define the config policy model (action risk, budgets, allowlists).

### Phase 1 — MVP (Advisor mode) (2–4 weeks)
- No new runtime loop; rely on existing orchestration.
- Add a “Smart Brain prompt profile” that:
  - prefers retrieval actions over guessing
  - asks clarifying questions when required params are missing
  - returns a recommended next step when blocked
- Ship 1 Real App demo that exposes 3–5 retrieval actions and 1 confirmed mutation action.

### Phase 2 — Governance + observability (2–4 weeks)
- Enforce action policies centrally (deny/allow, budgets, force-confirm).
- Add structured audit events for every action selection/execution.

### Phase 3 — Optional bounded agent loop (4–8 weeks)
- Implement observe→decide loop with strict step/time/token budgets.
- Add regression tests for loop behavior and failure modes.

---

## 7) Test plan
### Unit tests
- Policy enforcement (deny/allow, force-confirm, budgets, rate limits).
- Bounded facts payload behavior for loop iterations (truncation, sanitization).

### Integration tests
- Seed a small app context + a few actions (read + write) and assert:
  - advisor mode never executes writes
  - assisted mode triggers confirmation for writes
  - policies deny disallowed actions

### Real API tests (optional, keys-only)
- One stable prompt that validates provider wiring and that tool selection follows policy gates.

---

## 8) Acceptance criteria (v1)
- Users can register custom actions (retrieval + mutation) and Smart Brain uses them generically.
- Default behavior is safe:
  - advisor mode (no writes)
  - confirmation for writes when enabled
  - policies are enforced in code
- Outputs are auditable and bounded:
  - action inputs/outputs recorded
  - facts-only generation supported for action results

---

## 9) Open questions
- Do we want Smart Brain to be “multi-turn only” for v1, or do we need the agent loop immediately?
- What policy model is required on day one (risk + allowlist only, or budgets too)?
- Where do audit logs live (DB table, structured logs, both)?
- How do we expose Smart Brain UX: chat endpoint, admin console, or both?

