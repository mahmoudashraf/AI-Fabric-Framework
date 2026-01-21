# Post-Action LLM Generation for Action Handlers — Change Plan

## Status
Implemented (core support)

## Problem
Today, “post-action generation” (sending action results to an LLM to produce a grounded summary/answer) is only supported for the `relationship_query` action via:
- `ai.relationship-query.post-action-generation.*`, and
- `intent.requiresGeneration` / `intent.generationInstructions`.

Framework users want the same capability for **custom actions** (and built-in non-relationship actions) without copying orchestration logic or adding substring heuristics.

## Goals
- Allow an action handler to opt-in to “post-action generation” so the framework can:
  - execute the action,
  - build a bounded “facts” payload from the action result (explicitly shaped by the handler),
  - call the LLM with `LlmPurpose.GENERATION`,
  - return the generated message alongside the raw `ActionResult`.
- Keep this **LLM-driven** (no semantic substring parsing in code).
- Keep it **safe-by-default** (bounded payload, PII-aware, explicit enablement).
- Keep core APIs stable for existing handlers (no breaking changes required for handlers that don’t opt in).

## Non-goals
- A generic “auto-summarize everything” default for all actions.
- Sending arbitrary/large domain objects to the LLM without explicit shaping and bounds.
- Adding language-dependent text parsing heuristics to infer what to summarize.

## Proposed Design

### 1) Configuration: simple global gate
Add a generic configuration block:

- `ai.post-action-generation.enabled` (default `false`)
- `ai.post-action-generation.max-chars` (default `12000`)
- `ai.post-action-generation.max-tokens` (default `800`)
- `ai.post-action-generation.temperature` (default `0.2`)

### 2) Opt-in: action handler provides LLM-safe facts (explicit shaping)
Add a default method on `ActionHandler`:

- `Optional<Map<String,Object>> buildPostActionLlmFacts(ActionResult actionResult, OrchestrationContext ctx)`

Rules:
- If the method returns `Optional.empty()` (default), the framework does **not** run post-action generation.
- This method must be **side-effect free** and must not re-run the action; it only shapes the already-produced `ActionResult`.
- The returned map should contain primitives/maps/lists (bounded and safe).

### 3) Execution: generic post-action generation hook in `IntentHandlingStep`
Generalize the `relationship_query` flow with a new generic path:

1) Execute the action handler → `ActionResult` (executed **once**)
2) Decide if post-action generation should run:
   - `ai.post-action-generation.enabled=true`
   - intent requested generation (`requiresGeneration=true` or `generationInstructions` present)
   - handler opted in by returning non-empty facts (`buildPostActionLlmFacts(...)`)
   - `ActionResult.success == true`
3) Build a bounded facts payload (JSON when possible) and call `AICoreService.generateContent(..., LlmPurpose.GENERATION)`
4) Return:
   - `actionResult` (raw)
   - `postActionGeneration` (metadata: used, truncated, includedItems, model)
   - `summary` (generated content)

### 4) Security / Governance requirements
- Facts payload must be bounded by `max-chars`.
- The generation prompt must be explicit:
  - “Use ONLY the provided facts. If facts are insufficient, say so.”
- PII:
  - Run the same sanitization policy (or a “facts sanitizer”) on the facts payload before sending to the LLM, so we don’t leak raw PII into prompts when detection is enabled.
- Fail behavior:
  - If generation fails/timeouts, return the original `ActionResult` (and set `postActionGeneration.used=false` with a `skippedReason`).

## Relationship to Existing `relationship_query` Implementation
Two viable approaches:
1) **Short-term**: keep `ai.relationship-query.post-action-generation.*` as-is and add the generic mechanism for other actions.
2) **Follow-up refactor**: reimplement `relationship_query` post-action generation through the generic mechanism and treat `ai.relationship-query.post-action-generation.*` as an alias (or remove it, greenfield-style).

This change plan proposes option (1) first to minimize risk and keep the current behavior stable.

## Test Plan
### Unit tests (core)
- `IntentHandlingStep`:
  - runs post-action generation only when enabled and conditions met
  - bounded payload (max chars) is enforced
  - generation failure doesn’t fail the action result
  - handler opt-out skips generation

### Integration tests
Add a small test action (or reuse an existing safe action) that overrides `buildPostActionLlmFacts(...)`.
- Verify result contains:
  - `actionResult`
  - `postActionGeneration`
  - `summary`

### RealAPI tests (provider matrix)
- Add one RealAPI IT that:
  - forces a deterministic action output,
  - enables `ai.post-action-generation.enabled=true`,
  - asserts `summary` contains a stable token present only in the facts payload.

## Acceptance Criteria
- Framework supports post-action generation for any action via configuration + (optional) facts provider.
- No breaking changes for existing action handlers.
- Bounded, safe prompt payload; no raw object dumps.
- Tests cover success, failure, and “disabled” paths.

## Rollout
- Ship behind `ai.post-action-generation.enabled=false`.
- Encourage framework users to enable per action and provide a facts provider for structured outputs.
