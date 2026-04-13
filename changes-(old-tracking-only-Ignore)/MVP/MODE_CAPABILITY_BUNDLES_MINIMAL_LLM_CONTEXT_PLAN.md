# Mode Capability Bundles — Minimal LLM Context (Prompt + Pipeline Gating) Plan

## Why
We want a **reliable v1** that supports distinct e‑commerce use cases without overwhelming the LLM with irrelevant sections:

- **Executor**: user wants **defined read/write actions** (orders/returns/support). RAG is noise.
- **Navigator Deep**: user wants **deep information** (reviews/policies/alternatives) and broader retrieval. Actions are noise.
- **Navigator**: keep **current behavior** (actions + RAG as today).

This plan reduces LLM confusion and cost by:
1) injecting **only the needed prompt sections** per mode, and
2) enforcing those same constraints in the pipeline (fail‑closed).

This aligns with:
- `Final_Documentation/Development_Guides/LLM-guides/AI_FABRIC_FRAMEWORK_PHILOSOPHY.md` (“LLM decides, config constrains”, “fail‑closed”, “remove complexity”)
- `Final_Documentation/Development_Guides/CURATED_MODES_PACKS_GUIDE.md` (packs ship config + prompt overlays only)
- `Final_Documentation/Development_Guides/LLM_STANDARD_CHAT_PROMPTING_GUIDE.md` (multi‑message prompting; no retrieval pollution)
- `changes/MVP/V1_PRODUCTION_WORKING_SOLUTION_DEFINITION_OF_DONE.md` (navigator / navigator_deep / copilot behaviors)

---

## Scope (Greenfield)
### In scope
- Introduce a **capability bundle** model that drives:
  - which prompt sections are rendered for intent extraction and generation
  - which pipeline paths are allowed (actions vs retrieval)
- Add curated configuration for:
  - `executor` mode
  - `navigator_deep` mode
- **Do not change `navigator` behavior** (keep as‑is; include actions and retrieval as today).

### Out of scope (v1)
- Removing/renaming the existing mode resolution system.
- Introducing complex “mode enums” in core logic.
- Any UI changes beyond setting `mode` / `position` in requests.

---

## Definitions
### Capability bundle
A small, server‑authoritative set of booleans resolved per request (from profile + mode overrides):

- `actionsEnabled` (whether ACTION intents are allowed to be executed)
- `retrievalEnabled` (whether INFORMATION intents may use retrieval/RAG)
- `deepRetrievalEnabled` (whether “broad expansion beyond pinned” is allowed; only meaningful in navigator_deep)
- `suggestionsEnabled` (optional; whether to run suggestions step)

> Note: “Enabled” means “pipeline may execute it”. The LLM can still *propose* anything, but the runtime must enforce the capability constraints.

### Retrieval budgets (for “wide to UI, quality to LLM”)
We distinguish:
- **what we return to the UI** (`ragResponse.documents`, potentially broader), and
- **what we feed to the LLM** (`ragResponse.context`, bounded, high‑quality).

This requires explicit, policy-driven budgets:
- `maxSpaces` (fanout cap)
- `topKPerSpace` (per-space retrieval cap)
- `maxDocumentsReturnedToClient` (UI payload cap)
- `maxDocumentsUsedForContext` (LLM grounding cap)
- `maxContextChars` (LLM context cap)

---

## Desired mode behavior (v1)
### 1) `navigator` (unchanged)
- **Prompt**: include both action catalog sections and retrieval/KB sections exactly as today.
- **Pipeline**: allow actions and retrieval exactly as today.

### 2) `navigator_deep` (new capability bundle)
- **Prompt**: include retrieval/KB sections + deep retrieval instructions; **exclude action catalog**.
- **Pipeline**:
  - `actionsEnabled=false` → ACTION intents terminate with `CLARIFICATION_REQUIRED` (“Switch to executor to run actions”)
  - `retrievalEnabled=true`
  - `deepRetrievalEnabled=true` (expansion beyond pinned allowed)
- **Retrieval strategy** (deep):
  - fanout is allowed (bounded): combine
    - LLM-selected spaces (from KB overview),
    - attachment/pinned target spaces (as hints),
    - and/or a curated allowlist if configured.
  - return a wider document set to UI (up to `maxDocumentsReturnedToClient`)
  - feed a curated subset to the LLM for answer generation (up to `maxDocumentsUsedForContext` + `maxContextChars`)

### 3) `executor` (new capability bundle)
- **Prompt**: include action catalog + confirmation rules.
  - Default: **exclude** retrieval/KB sections.
  - Optional: include a minimal “policy retrieval” section that lists allowlisted spaces only (no KB dump).
- **Pipeline**:
  - `actionsEnabled=true`
  - Default: `retrievalEnabled=false` → INFORMATION intents that require retrieval terminate with `CLARIFICATION_REQUIRED` (“Switch to navigator to search the KB”)
  - Optional (policy lookup in executor):
    - `retrievalEnabled=true` but **restricted**:
      - retrieval allowed only when `retrievalVectorSpacesAllowlist` is configured and non-empty
      - retrieval is bounded by a small budget (UI docs + LLM context caps)
  - Optional: allow pure conversational acknowledgements without retrieval.

---

## Configuration (curated‑pack driven)
### Curated pack selection semantics (default pack behavior)
We want curated packs to be **explicit, non‑layered**, and predictable:

- **One active pack per app/environment** (no inheritance/merging in v1).
  - If the app sets `ai.curated.pack=commerce`, the `default` pack is **not** loaded/merged.
- The **`default` pack is navigator‑only**:
  - It should define **only** the `navigator` mode (no position routing; all positions resolve to `navigator`).
  - It should **not** ship `navigator_deep` / `executor` / `cart_assistant` overrides.
- **Fail‑closed for unsupported modes**:
  - If a request specifies a mode that is **not supported by the active pack**, terminate with `CLARIFICATION_REQUIRED` (or `ERROR` if that is more consistent) and include a debug/data payload such as:
    - `data.supportedModes=["navigator"]`
    - `data.suggestedPack="commerce"` (optional)

> Rationale: “default pack = navigator only” avoids accidental partial enablement of advanced modes and keeps v1 predictable.

### New mode overrides (additive)
Extend `ai.orchestration.modes.<mode>` overrides to include the capability bundle flags:

```yaml
ai:
  orchestration:
    modes:
      navigator_deep:
        retrieval-enabled: true
        deep-retrieval-enabled: true
        actions-enabled: false
      executor:
        actions-enabled: true
        retrieval-enabled: false
```

Defaults:
- If an override is unset, derive from profile / existing behavior (so `navigator` remains unchanged).

### Deep retrieval budgets (navigator_deep)
Add a bounded “wide-to-UI, quality-to-LLM” budget under mode overrides:

```yaml
ai:
  orchestration:
    modes:
      navigator_deep:
        rag:
          fanout-enabled: true
          max-spaces: 6
          top-k-per-space: 8
          max-documents-returned-to-client: 30
          max-documents-used-for-context: 10
          max-context-chars: 12000
```

### Executor restricted retrieval (policies)
Enable only when explicitly configured:

```yaml
ai:
  orchestration:
    modes:
      executor:
        retrieval-enabled: true
        rag:
          retrieval-vector-spaces-allowlist:
            - policy
            - returns_policy
            - shipping_policy
          max-documents-returned-to-client: 10
          max-documents-used-for-context: 6
          max-context-chars: 8000
```

### Position routing defaults (curated packs)
Default pack:
- No position routing (all positions resolve to `navigator`).

Commerce pack (optional):
- May use minimal routing such as `landing/catalog/search → navigator`.

No default routing for cart/support/etc in the default pack (apps can override via curated packs).

---

## Prompt construction changes (minimal, deterministic)
Apply the capability bundle to **prompt section rendering**, not to query concatenation:

### Intent extraction prompt (system rules)
- If `actionsEnabled=false` → do not include ACTION SPECS / action selection instructions.
- If `retrievalEnabled=false` → do not include KB overview / retrieval rules (vector spaces, retrieval hints, etc.).
- If `deepRetrievalEnabled=true` → include deep retrieval guidance (bounded, explicit).
  - In deep mode: instruct the model to pick relevant vector spaces and respect budgets (max spaces / docs).
  - In executor restricted retrieval: list allowlisted spaces only and instruct the model to use retrieval only for those spaces (policies/support knowledge), not product browsing.

### Generation prompt (system rules)
- Similar gating:
  - when actions disabled, generation should not suggest “run action X”
  - when retrieval disabled, generation should not claim “I searched the KB”

### Critical invariant (per LLM standard prompting guide)
- Never pollute embedding/retrieval queries with:
  - history scaffolding
  - attachments blocks
  - action specs

---

## Pipeline enforcement (fail‑closed)
Even if the LLM output violates capability constraints:

### If `actionsEnabled=false` and intent is ACTION
- Terminate with `CLARIFICATION_REQUIRED`
- Include:
  - `data.suggestedMode="executor"`
  - reason: “Actions are disabled in this mode.”

### If `retrievalEnabled=false` and intent requires retrieval
- Terminate with `CLARIFICATION_REQUIRED`
- Include:
  - `data.suggestedMode="navigator"`
  - reason: “Retrieval is disabled in this mode.”

### If `retrievalEnabled=true` but retrieval allowlist is configured and intent requests a non-allowlisted space
- Terminate with `CLARIFICATION_REQUIRED`
- Include:
  - `data.allowedVectorSpaces=[...]`
  - reason: “Retrieval vector space is not allowed in this mode.”

### If `deepRetrievalEnabled=false` but intent requests broad expansion
Use existing deep gating approach (see `changes/MVP/DEEP_MODE_GATING_SCHEMA_PROMPT_PIPELINE_DEBUG_PLAN.md`):
- return `CLARIFICATION_REQUIRED` suggesting `navigator_deep`

---

## Observability (debug)
Add/extend `metadata.orchestrationPolicy` debug block to include:
- `actionsEnabled`
- `retrievalEnabled`
- `deepRetrievalEnabled`
- `capabilitiesSource` (PROFILE | MODE | REQUEST_MODE/POSITION)

This allows UI/debug tooling to understand “why didn’t it run RAG?” without reading logs.

Add/extend `metadata.rag` debug block to include:
- `fanoutEnabled`, `fanoutSpacesSelected`, `maxSpaces`, `topKPerSpace`
- `maxDocumentsReturnedToClient`, `maxDocumentsUsedForContext`, `maxContextChars`
- `restrictedVectorSpacesAllowlist` (when configured)

---

## Tests (must‑have)
### Unit tests
- Mode resolution sets capability bundle correctly.
- Prompt builder includes/excludes sections based on capabilities.

### Integration / Real‑API tests (targeted)
- `navigator_deep`: ACTION intent → `CLARIFICATION_REQUIRED` with `suggestedMode=executor`
- `executor` (default): retrieval request → `CLARIFICATION_REQUIRED` with `suggestedMode=navigator`
- `executor` (allowlisted): policy retrieval request → retrieval executes only against allowlisted spaces (bounded)
- `navigator`: unchanged baseline behavior (ensure no regressions)

---

## Rollout plan (safe)
1) Add capability flags + debug fields (no behavior changes).
2) Wire capability flags into prompt section rendering.
3) Add pipeline enforcement gates.
4) Add curated packs:
   - `default` (navigator‑only) + “unsupported mode” fail‑closed behavior.
   - `commerce` defaults for `navigator_deep` and `executor`.
5) Add docs updates:
   - extend `Final_Documentation/Development_Guides/CURATED_MODES_PACKS_GUIDE.md` with new flags
   - add a short “Mode capabilities” section to `changes/MVP/V1_PRODUCTION_WORKING_SOLUTION_DEFINITION_OF_DONE.md` (optional)
