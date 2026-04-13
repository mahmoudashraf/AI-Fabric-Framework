# Deep Mode Gating (Broad Search) + Intent Flag + Debug Contract

## Context / Problem
When a request includes pinned targets (active attachments / resolved targets), `requiresRetrieval=true` currently mixes two very different needs:

1) **Grounding retrieval**: fetch *related* info about the pinned items (reviews, policies, warranty, shipping, etc.)
2) **Expansion retrieval**: search for *new candidates beyond pinned items* (alternatives, more options, “show me similar but cheaper”, etc.)

This causes:
- Unnecessary/expensive RAG calls even when pinned targets already contain sufficient info (compare, price, choose).
- “Pinned pollution” failures when broad search uses pinned attributes incorrectly.
- No clean way for UI to ask for an explicit “deep search”.

## Goals
- Provide a **realistic separation** between **normal** vs **deep** search behaviors.
- Keep it **domain-agnostic** and **avoid backend string matching**.
- Keep “reviews/policies grounding” available in normal mode.
- Gate “broad candidate expansion beyond pinned” behind a UI/user-controlled deep mode.
- Make the execution path **observable** via debug metadata.

## Non-goals
- No backend heuristics like checking for words (“alternatives”, “cheaper”, “it/them”).
- No coupling to domain-specific vectorSpaces (the LLM chooses vectorSpace from KB overview).

---

## Proposed Changes

### 1) Extend the Intent Contract (LLM output)
Add an optional boolean to `INFORMATION` intents:

```json
{
  "type": "INFORMATION",
  "requiresRetrieval": true,
  "requiresGeneration": true,
  "searchBeyondPinned": false
}
```

#### Semantics
- `searchBeyondPinned=false` (default): retrieval is for **grounding** (reviews/policies/etc) OR for a **new focus** query not asking to expand beyond pinned candidates.
- `searchBeyondPinned=true`: user is requesting **candidate expansion** beyond pinned targets (deep-only).

Notes:
- This flag does **not** replace `requiresRetrieval`.
- This flag is only meaningful when pinned targets exist (active attachments / resolved targets).

### 2) Add an Explicit Deep Mode (UI-controlled)
Add a deep mode that the UI can send:
- `mode = "navigator_deep"`

Normal mode remains:
- `mode = "navigator"`

Optional: keep `position` routing unchanged; this is mode-level behavior.

### 3) Pipeline Gating Rule (No heuristics)
In normal navigator mode, block only **expansion** retrieval beyond pinned:

**If all are true:**
- pinned targets exist (active attachments / resolved targets),
- `requiresRetrieval=true`,
- `searchBeyondPinned=true`,
- effective mode is **NOT** `navigator_deep`,

**Then:**
- return `CLARIFICATION_REQUIRED`
- `message`: “Enable deep search to look beyond pinned items?”
- include machine-readable flags in `data`:
  - `deepSearchRequired=true`
  - `suggestedMode="navigator_deep"`

**Else:** proceed normally.

This preserves:
- Normal “compare/choose from pinned” (no retrieval) flows.
- Normal grounding retrieval for reviews/policies.
- Normal “new-focus” retrieval queries unrelated to pinned targets.

---

## Prompt Rules (Curated Packs)
Update curated intent-extraction prompts (default + commerce at minimum) to:

### A) Compare/choose from pinned targets
When pinned targets exist and the request is about choosing/comparing among them:
- `requiresRetrieval=false`
- `requiresGeneration=true`
- `requiresTargetResolution=true` (so deterministic pipeline can resolve the referenced subset when needed)
- `searchBeyondPinned=false`

### B) Grounding across a different vectorSpace (allowed in normal mode)
When pinned targets exist but the user asks for *related* info typically not in the pinned snippet:
- `requiresRetrieval=true`
- `searchBeyondPinned=false`
- choose `vectorSpace` from KB overview if available (e.g., reviews, policies).

### C) Candidate expansion beyond pinned (deep-only)
When pinned targets exist and user asks for *more options / alternatives* beyond pinned:
- `requiresRetrieval=true`
- `searchBeyondPinned=true`
- choose `vectorSpace` (usually product/catalog) from KB overview if available.

### D) New focus (not about pinned targets)
Pinned targets are **not a scope restriction**.
If the user asks for a new search/list unrelated to pinned targets:
- `requiresRetrieval=true`
- `searchBeyondPinned=false` (this is not “beyond pinned”; it’s just a new query)

---

## Backend Implementation Notes

### Data model
- Extend `Intent` DTO to include:
  - `Boolean searchBeyondPinned`
- Extend `IntentExtractionValidator` to accept the new field.

### Gating insertion point
Implement gating in the `INFORMATION` path before any RAG call:
- Likely in `IntentHandlingStep.handleInformation(...)` after intent extraction and target resolution availability is known (targets in context).

Pseudo:
```java
boolean hasPinnedTargets = pipelineContext.hasResolvedTargetsFromActiveAttachments(); // structural
boolean deepMode = policy.modeEffective().equals(\"navigator_deep\");

if (hasPinnedTargets
    && intent.requiresRetrievalOrDefault(true)
    && Boolean.TRUE.equals(intent.getSearchBeyondPinned())
    && !deepMode) {
  return clarificationRequiredDeepSearch();
}
```

No keyword checks.

---

## Debug / Observability Contract
Add explicit metadata fields so UI/devs can understand “why a path happened”.

### Suggested response metadata additions
Under `metadata.intentMetadata`:
- `searchBeyondPinned` (boolean)
- `requiresTargetResolution` (boolean, already present in some flows)
- `requiresRetrieval` (boolean)

Under `metadata.deepSearch`:
- `requestedMode` (string)
- `effectiveMode` (string)
- `gated` (boolean)
- `gateReason` (string enum), e.g. `BROAD_SEARCH_REQUIRES_DEEP_MODE`

Under `data.ragResponse.metadata` (if RAG executed):
- `ragExecuted=true`
- `ragReason` (`GROUNDING` | `EXPANSION` | `NEW_FOCUS`), derived structurally:
  - if `searchBeyondPinned=true` → `EXPANSION`
  - else if pinned targets exist and retrieval happened → `GROUNDING`
  - else → `NEW_FOCUS`

---

## Tests / Validation

### Unit tests
- JSON parsing accepts `searchBeyondPinned`.
- Validator allows the new field for INFORMATION intents.
- Gating returns `CLARIFICATION_REQUIRED` only under the exact conditions.

### Real-app manual checks (chat-capabilities-demo)
Pinned targets exist (2 attachments active):
1) “compare these and pick the best” → no RAG, generation-only
2) “any negative reviews on them?” → RAG executes (grounding)
3) “show me 3 cheaper alternatives”:
   - `navigator` → CLARIFICATION_REQUIRED with deepSearchRequired
   - `navigator_deep` → RAG executes (expansion)

### API trace checklist
For each call, confirm in response:
- `metadata.intentMetadata.searchBeyondPinned`
- `metadata.deepSearch.gated` (true/false)
- `data.ragResponse` present only when expected

---

## UI Guidance (Migration)
- Default to `mode="navigator"`.
- Add a “Deep Search” UI control that resubmits the same user query with:
  - `mode="navigator_deep"`
- When backend responds with `CLARIFICATION_REQUIRED` and `data.deepSearchRequired=true`:
  - UI should offer the user to run deep search and resend with `navigator_deep`.

