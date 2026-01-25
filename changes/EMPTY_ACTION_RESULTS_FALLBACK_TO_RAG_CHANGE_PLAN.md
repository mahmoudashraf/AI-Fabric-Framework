# Empty Action Result → RAG Fallback — Change Plan

## Status
Proposed

## Problem
In real chat usage, users often phrase requests as “search/find …”. If the LLM selects a **user-provided search action** (ACTION) and that action returns **0 results**, the user experience degrades:
- repeated retries with the same query (“find women foot wear” → 0 → repeat)
- perception that “the system is broken”, even when the knowledge base / vector search could answer
- increased misrouting impact as teams add more “search_*” actions (tool bias grows)

We want a **safe, deterministic fallback**: when a retrieval-style action returns an empty result, automatically attempt a RAG INFORMATION flow to salvage the answer.

## Goals
- Improve UX for “empty search” outcomes by falling back to RAG automatically.
- Keep behavior **safe-by-default** and **opt-in** via configuration.
- Avoid loops and hidden side effects:
  - never re-run actions
  - never auto-run mutating actions
  - never “guess” sensitive domains
- Preserve observability:
  - clients can see both the action result and the fallback RAG result
  - metadata explains *why* fallback ran and what strategy was used

## Non-goals
- Fixing action selection/tool bias at intent extraction time.
- Replacing domain search actions (catalog search, CRM search, etc.).
- Implementing a full agent loop (plan → tool → observe → tool).

---

## Proposed behavior (high level)
When an ACTION executes successfully and appears “retrieval-like” and “empty”:
1) Return the action result as usual (for audit/diagnostics)
2) Also run a RAG INFORMATION flow for the same user query (or optimized query)
3) Return a combined response where:
   - primary result is still `ACTION_EXECUTED`
   - metadata indicates `emptyActionFallback.used=true`
   - fallback response is attached under a predictable key

This turns “0 results” into “0 results + best-effort answer from KB/RAG”.

---

## Safety gates (must be enforced)
### Gate 1 — Feature flag (default off)
- `ai.orchestration.empty-action-fallback.enabled=false`

### Gate 2 — Only for retrieval-style actions
Fallback is allowed only when the action is **read-only / retrieval-like**.

How to decide (v1, simplest):
- Allowlist by action name prefix (recommended):
  - e.g., `search_`, `get_`, `list_`, `find_`
- Optional denylist for known non-retrieval actions.

Recommended improvement (stronger and more future-proof):
- Add **action access-mode metadata** to action declarations: `READ | READ_WRITE | WRITE_ONLY`.
  - Fallback runs only when `accessMode == READ`.
  - `READ_WRITE` and `WRITE_ONLY` never trigger fallback automatically.
  - If metadata is missing (backward compatibility), fall back to name-prefix allowlist + empty-payload detection.
- This also helps prevent accidental fallback on “read-looking” actions that actually have side effects.

### Gate 3 — Only when action result is empty
We should not rely on message text (“No matching …”) because it’s not stable.

Instead, define “empty” using the action result payload shape (recommended conventions):
- `data.count == 0`
- OR `data.items/results/products/documents` exists and is an empty list
- OR `data.total == 0`

If none of these can be evaluated, do not fallback (fail-closed).

### Gate 4 — Never for mutating actions
Any action requiring confirmation (or classified as write) must not trigger fallback automatically.

### Gate 5 — No infinite loops
Fallback runs at most once per request and must be marked in pipeline context/metadata.

---

## RAG fallback strategy
### Query selection
Use best available query text:
1) `intent.optimizedQuery` if present
2) else the processed query (PII-sanitized if applicable)
3) else the raw user query

### Vector space selection
We need a deterministic choice that doesn’t introduce dangerous guessing:
- If the intent already has `vectorSpace`, use it.
- Else:
  - if orchestration is in `DETERMINISTIC_RAG_GENERATE`, fan-out is already supported; reuse the same “all spaces” behavior
  - otherwise, either:
    - fan-out across all known vector spaces (config-gated), OR
    - return clarification (“Which domain should I search?”)

Recommended v1 default: **no extra fan-out guessing in LLM-driven mode** (keep it safe), but allow opt-in:
- `ai.orchestration.empty-action-fallback.fan-out-when-vectorSpace-missing=true|false`

### Generation behavior
Reuse existing INFORMATION pipeline rules:
- if generation is enabled, return a grounded answer
- else return “search completed” + documents

---

## Output contract (what clients see)
Keep the existing result type as `ACTION_EXECUTED`, and attach fallback details:
- `data.actionResult` (existing)
- `data.emptyActionFallback` (new):
  - `used: true|false`
  - `reason: "EMPTY_ACTION_RESULT"`
  - `ragVectorSpacesUsed: [...]`
  - `ragSuccess: true|false`
  - `ragResult` (bounded, same structure as INFORMATION result data)

Optional config:
- `ai.orchestration.empty-action-fallback.override-message=true|false`
  - if true and fallback produced a good answer, replace the top-level message with the fallback answer
  - always keep the original action message inside `data.actionResult`

---

## Configuration proposal (names illustrative)
Under `ai.orchestration.empty-action-fallback`:
- `enabled` (default `false`)
- `actionNamePrefixes` (default `[]` → rely only on payload emptiness)
- `excludedActions` (default `["relationship_query"]`)
- `override-message` (default `true`)
- `fan-out-when-vectorSpace-missing` (default `false`)

---

## Test plan
### Unit tests
- ACTION with `data.count=0` triggers fallback when enabled and action allowlisted.
- ACTION with non-empty result does not fallback.
- ACTION with missing/unknown data shape does not fallback (fail-closed).
- ACTION requiring confirmation never triggers fallback.
- Ensure only one fallback occurs (no loops).

### Integration tests
- Minimal RAGProvider stub + a dummy “search” action that returns count=0:
  - assert combined response includes fallback payload
  - assert message override behavior matches config

---

## Rollout
1) Implement behind feature flag (off by default).
2) Update Real Apps to return a consistent “empty search” shape (`count` + list) so detection is reliable.
3) Enable in one demo app and observe telemetry (how often fallback triggers, whether it helps).
4) Iterate on allowlist/payload conventions, then document as best practice.

---

## Acceptance criteria
- When enabled, “search action returns 0 results” yields a helpful RAG-based response without breaking auditability.
- When disabled, existing behavior is unchanged.
- No fallback runs for write actions or unclear data shapes.
- Output includes deterministic metadata indicating fallback usage and reasoning.
