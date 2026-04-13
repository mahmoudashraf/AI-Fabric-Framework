# Remove Synthetic `intent.type=COMPOUND` (Use `intents[]` Only)

## Status
- Implemented (greenfield refactor)

## Problem
We currently have legacy support for a “synthetic compound intent” representation:

- **Synthetic compound intent**: a single intent with `intent.type=COMPOUND` whose children must be embedded at `intent.actionParams.intents`.

In production we are seeing:
- `ERROR: "Compound intent payload is missing component intents."`

This happens when the LLM outputs `intent.type=COMPOUND` but does **not** include `actionParams.intents` (or returns it empty). This is a contract failure, not a RAG issue.

## Goal
Make compound handling deterministic and remove an entire class of extractor failures by:
- **Stop using `intent.type=COMPOUND` entirely**
- Use only **multiple root** `intents[]` (compound is inferred when `intents.size() > 1`)

## Non‑Goals
- Backwards compatibility (greenfield).
- Adding additional heuristics / string matching in the backend.

## Proposed Changes

### A) Contract: Remove `COMPOUND` from allowed `IntentType`
**Change**: Delete `COMPOUND` from `com.ai.infrastructure.dto.IntentType`.

Implications:
- The LLM output schema must no longer include `type="COMPOUND"`.
- The backend cannot route on `IntentType.COMPOUND` anymore.

### B) Prompt: Disallow `type=COMPOUND`, require multiple root `intents[]`
Update the curated default extraction system prompt:
- Remove `COMPOUND` from the “OUTPUT JSON SCHEMA” allowed `type` enum.
- Update rules:
  - If multiple intents exist → return all component intents in root `intents[]`.
  - Never embed component intents inside another intent’s `actionParams`.

Expected file(s):
- `ai-infrastructure-module/curated/ai-curated-default/src/main/resources/prompts/intent-extraction/compound/v1/system.md`
- Any other extractor prompt variants that currently mention COMPOUND.

### C) Backend: Delete synthetic compound handler path
Remove the code path that attempts to interpret a single `IntentType.COMPOUND` intent as a container:
- Delete `IntentHandlingStep.handleSyntheticCompound(...)`.
- Remove the routing case `case COMPOUND -> ...` in `IntentHandlingStep.handleSingleIntent(...)`.

After this change:
- Multi-intent execution is handled only via `handleCompoundIntents(MultiIntentResponse ...)`.

### D) Normalization: Canonicalize compound representation
No compound flag exists in the contract. Compound is inferred as:
- `intents.size() > 1`

### E) Tests
Add tests at two levels:

1) **Core unit tests**
   - Verify `IntentHandlingStep` no longer expects `IntentType.COMPOUND`.
   - Verify top-level compound responses (multi intents) are handled sequentially.

2) **RealAPI / integration tests**
   - Regression: “compare” with attachments should not produce “missing component intents” error.
   - Multi-intent should execute deterministically in sequence.

## Migration Notes
Because this is greenfield:
- Any fixtures, prompts, or tests producing `type=COMPOUND` or `isCompound` must be updated.

## Debug/Observability
Add/update debug metadata to confirm which path ran:
- `metadata.extractionDiagnostics.extractionPath`
- `metadata.intentsCount`
- Optional: `metadata.multiIntent = (intentsCount > 1)`

## Implementation Steps (Suggested)
1) Update prompt(s) to disallow COMPOUND `type`.
2) Remove `COMPOUND` from `IntentType`.
3) Remove synthetic compound handler code path.
4) Fix tests/fixtures accordingly.
5) Run `mvn -f ai-infrastructure-module/pom.xml verify`.
