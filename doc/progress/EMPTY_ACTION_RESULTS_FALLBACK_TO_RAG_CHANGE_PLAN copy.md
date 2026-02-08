# Empty READ Action Result → RAG Replacement — Change Plan

## Status
Implemented

## Problem
In real chat usage, the LLM sometimes selects an ACTION (a “tool”) that returns **no results**. When this happens, users perceive the system as broken even though the knowledge base could answer via RAG.

We want a deterministic, greenfield behavior: **READ actions are helper tools**. If a READ action returns an empty successful payload, the orchestrator should **replace** that output with a RAG-based INFORMATION response.

## Goals
- Avoid “dead ends” when READ actions return empty results.
- Keep the mechanism deterministic and developer-controlled (no action-name heuristics).
- Do not run this behavior for write/side-effecting actions.
- Keep responses simple (no duplicated payloads).

## Non-goals
- Tuning prompt/tool-bias at intent extraction time.
- Preserving backward compatibility with legacy action metadata.
- Returning “combined” action + RAG payloads (we intentionally return only the RAG result).

---

## Design (Greenfield)

### 1) Developer must declare action access mode
Actions explicitly declare their access mode via `@AIAction(accessMode = ...)`:
- `READ` — retrieval-style tool (no side effects)
- `WRITE_ONLY` — mutating tool
- `READ_WRITE` — both (treated as non-READ for fallback)

This is required (no default). The framework propagates this into `AIActionMetaData.accessMode`.

### 2) Fallback behavior (READ only)
When an action executes:
1) If `meta.accessMode != READ` → normal action behavior.
2) If `meta.accessMode == READ` and the action returns **success=true** but the payload is **empty** → run INFORMATION (RAG) and return that result.

This is “replace”, not “attach”.

### 3) How “empty action result” is detected
We do not rely on message text. “Empty” is detected using the framework’s **explicit list payload contract**:
- `data._count == 0` OR `data._items` is an empty list

The orchestrator only evaluates “empty” for actions that return list payloads built with
`ActionResultContracts.list(...)`. If the payload does not use the list contract keys, the fallback does not trigger
(fail-closed).

### 4) RAG strategy
- **Query**: use `intent.optimizedQuery` when present, else the pipeline effective query.
- **Vector spaces**: if the action intent did not specify `vectorSpace`, use **all known vector spaces** from the knowledge base overview (fan-out).
- **Generation**: run generation only when `ai.service.features.enableGeneration=true`.

If RAG is not available (no `RAGProvider`) or vector spaces cannot be resolved, the fallback does not trigger.

---

## Implementation notes
- Add `ActionAccessMode` enum.
- Make `@AIAction.accessMode()` required.
- Store access mode on `AIActionMetaData`.
- In `IntentHandlingStep`, after a READ action executes successfully:
  - if the result payload is empty, call the existing INFORMATION handler (`handleInformation(...)`) with a synthetic INFORMATION intent that performs RAG.

---

## Acceptance criteria
- Empty successful READ actions return an INFORMATION response grounded in the KB (RAG), not an “empty action executed” payload.
- WRITE_ONLY / READ_WRITE actions never trigger this fallback.
- No string/prefix heuristics are used to classify actions.
