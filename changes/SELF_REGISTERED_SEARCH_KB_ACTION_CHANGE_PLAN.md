# Self-Registered `search_knowledge_base` Action — Change Plan

## Status
Proposed

## Background
Today “search my knowledge base …” is modeled as an `INFORMATION` intent (retrieval + optional generation).

However, some LLM providers will occasionally emit a tool-like “search” as an `ACTION` (e.g. `action=search_knowledge_base`) even when our system prompt explicitly says this is `INFORMATION`.
When that happens, the pipeline can misroute (unregistered ACTION) or fail to apply expected clarification flows (missing `vectorSpace`).

This plan explores introducing a **self-registered** read-only action (`search_knowledge_base`) to make this behavior an explicit, stable product surface.

## Problem
- Provider outputs may return `ACTION` with an unregistered action name (e.g. `search_knowledge_base`).
- Even when the JSON is syntactically valid, the intent is semantically mis-typed, causing:
  - `ACTION_NOT_FOUND` / pipeline errors
  - lost “vectorSpace clarification” behavior (domain selection)
  - inconsistent outcomes across providers and test matrix combinations

## Goals
- Provide a stable, explicit “search KB” interface that is **provider-agnostic** and **fail-closed**.
- Preserve the current user experience:
  - If domain/vectorSpace is missing → **CLARIFICATION_REQUIRED** (never guess).
  - Retrieval-only vs synthesized answer behavior remains deterministic.
- Reduce provider variance causing “valid JSON but wrong type” routing issues.
- Improve observability (consistent metadata, metrics, and error codes).

## Non-goals
- Replacing the existing `INFORMATION` retrieval flow.
- Creating a new “prompt tool ecosystem” or dynamic tool registry loaded over the network.
- Weakening security/PII/compliance behaviors.

## Key Concerns (Product + Architecture)
This change is not “free”; it impacts core framework semantics:

1) **Action semantics drift**
- Current philosophy: ACTIONs are for state-changing operations.
- KB search is read-only and already well-served by `INFORMATION`.
- If we introduce `search_knowledge_base` as an action, we must either:
  - redefine “ACTION includes read-only operations”, or
  - accept an inconsistency between our documented rules and the action registry.

2) **Increased ACTION misrouting**
- Once an action exists, many models prefer choosing it because it looks like a “tool”.
- That can pull normal information queries away from the `INFORMATION` path, potentially reducing quality
  (because the LLM’s intent selection shifts toward ACTION even when synthesis is needed).

3) **Duplication / two ways to do the same thing**
- “Search KB” currently works via `INFORMATION` + vectorSpace routing + retrieval/generation.
- Adding an action can create two parallel implementations unless we explicitly route the action through the same code path.

4) **Clarification and routing parity**
- The existing `VectorSpaceResolutionStep` only resolves missing vectorSpace for `INFORMATION` intents.
- A new `search_knowledge_base` action would require either:
  - extending `VectorSpaceResolutionStep` to also handle this action, or
  - adding a dedicated step for this action,
  - otherwise domain selection and clarification UX will regress.

5) **Security model clarity**
- The framework is fail-closed. If KB access should be scoped per domain/vectorSpace, we need an explicit policy surface.
- Introducing a search action without a policy story can accidentally imply “all domains are searchable”.

## Options

### Option A (Recommended): Keep `INFORMATION` as the primary model + strengthen canonicalization
- Treat KB search as `INFORMATION` (as today).
- Add deterministic normalization (post-processing) so provider variance does not break routing:
  - if model outputs “search” as ACTION → coerce to INFORMATION safely
  - accept `actionParams.vectorSpace` as equivalent to `intent.vectorSpace`
- Keep the existing clarification policy (vectorSpace missing → CLARIFICATION_REQUIRED).

**Pros**
- Preserves the framework’s “ACTION = state change” contract.
- Minimizes misrouting risk.
- No duplicated execution path.

**Cons**
- Still relies on LLM output being “close enough”, then normalized deterministically.
- Does not expose “search KB” as an explicit API surface for integrators.

### Option B: Introduce `search_knowledge_base` as a self-registered read-only action (behind a feature flag)
Add an ActionHandler:
- Name: `search_knowledge_base`
- Category: `knowledge_base`
- Params:
  - `query` (required)
  - `vectorSpace` (optional; if missing → clarification)
  - `limit` (optional)
  - `returnMode` (optional: `IDS|FULL|SNIPPETS` depending on existing DTOs)
  - `requiresGeneration` / `generationInstructions` (optional)

**Critical requirement:** The action handler must route through the same retrieval/generation code path as `INFORMATION`
(to avoid behavior divergence).

**Pros**
- Stable, explicit contract for integrators and the prompt layer.
- Clear telemetry: “this was a KB search action”.

**Cons / Risks**
- Violates (or forces redefinition of) “ACTION = state change”.
- Likely increases ACTION selection frequency across providers (more tool bias).
- Requires pipeline changes to keep vectorSpace clarification parity.
- Needs an explicit domain access policy story to remain enterprise-ready.

## Proposed Design (if Option B is selected)

### 1) Feature flag (default off)
- `ai.actions.search-knowledge-base.enabled=false` (default)
- When disabled:
  - the action is NOT registered
  - prompt rules remain as-is (KB search is INFORMATION)
- When enabled:
  - register the action handler bean
  - optionally adjust system prompt to mention the action, but only when enabled (avoid tool bias by default)

### 2) Pipeline parity: vectorSpace resolution
Ensure the action gets the same “domain missing → clarification” behavior as INFORMATION:
- Extend `VectorSpaceResolutionStep.requiresResolution(...)` to also handle:
  - `IntentType.ACTION` where `action == "search_knowledge_base"` and `requiresRetrieval == true` and vectorSpace missing

### 3) Execution behavior
The action handler should:
- Fail-closed if access policies deny the requested vectorSpace(s)
- If `vectorSpace` is missing and routing policy requires clarification → return CLARIFICATION_REQUIRED (not OUT_OF_SCOPE)
- Use retrieval-only vs retrieval+generation semantics consistent with existing `IntentHandlingStep`

### 4) Security & policy surface
Introduce an SPI (or reuse existing policy infrastructure) for per-vectorSpace authorization:
- `VectorSpaceAccessPolicy`:
  - `boolean canSearch(String userId, String vectorSpace)`
  - fail-closed if any requested space is denied

### 5) Observability
Add metadata keys:
- `kbSearch.actionUsed=true`
- `kbSearch.vectorSpace=<resolved>`
- `kbSearch.routingStrategy=...`

## Testing Strategy
- Unit tests:
  - action handler parameter validation (fail-closed)
  - vectorSpace resolution parity (missing vectorSpace → CLARIFICATION_REQUIRED)
  - policy denial is fail-closed with explicit error code
- Integration tests:
  - provider matrix: ensure enabling the action does not increase misrouting or regress existing suites
  - realapi: confirm stable behavior across OpenAI/Cohere/Gemini/Anthropic with action enabled

## Rollout Plan
1. Land policy + pipeline parity changes (no behavior changes when flag is off)
2. Add action handler behind feature flag (off by default)
3. Add docs and example configuration for enabling it
4. Run matrix suites with flag on in a dedicated workflow/job
5. Decide whether to make it default-on (only if results show no regression in routing quality)

## Acceptance Criteria
- Default behavior unchanged when `ai.actions.search-knowledge-base.enabled=false`
- When enabled:
  - missing vectorSpace triggers CLARIFICATION_REQUIRED (never guess)
  - action execution uses the same retrieval/generation semantics as INFORMATION
  - fail-closed access is enforced for vectorSpace authorization
  - provider-matrix + chat-session clarification flows remain stable

