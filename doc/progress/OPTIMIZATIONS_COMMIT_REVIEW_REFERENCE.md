# Optimizations Commit Review (Reference)

This document summarizes what each referenced commit changed and whether it is worth keeping for a production‑ready v1.

## Goal of this review
- Provide a quick “what changed” and “keep vs gate vs drop” assessment.
- Highlight coupling/risks (prompt complexity, step ordering, rehydration cost).
- Help decide a minimal v1 subset vs optional enhancements.

---

## 1) `f0dc6f3cb8d1129e4ba822d8569aee5869b70357`
**Title:** Create `ATTACHMENT_QUERY_HINT_FALLBACK_DEEP_MODE_PLAN.md`  
**Type:** Documentation only  
**Files:** `changes/optimisation/ATTACHMENT_QUERY_HINT_FALLBACK_DEEP_MODE_PLAN.md`

**What it adds**
- A plan to optionally add an “attachment query hint” fallback path in deep mode.

**Assessment**
- **Keep (doc):** ✅ Useful design guidance.
- **Risk:** none (no code changes).

---

## 2) `4d1c25153b0cbf17a957eec91fa1a7d6170e05c7`
**Title:** Pinned Targets Set Reasoning + Attachment Comparison (No Retrieval Drift)

**What it changed**
- Prompt updates across curated packs (default/commerce/catalog/support) to:
  - Treat pinned targets as the candidate set for comparison/choice requests.
  - Prefer `requiresRetrieval=false` when pinned targets are sufficient to compare/choose.
  - Expand `requiresTargetResolution` examples to include `these/those/them`.
- Minor label tweak in `IntentHandlingStep` (“compare among these”).
- RAG generation prompts reinforced pinned‑targets comparison behavior.
- Added doc: `changes/LLM_PINNED_TARGETS_SET_REASONING_AND_COMPARISON_PLAN.md`.

**Assessment**
- **Keep:** ✅ Improves compare/choose behavior (reduces retrieval drift).
- **Risk:** Low; primarily prompt edits.
- **Note:** This commit still used stronger “authoritative” framing in places; later commits softened that language.

---

## 3) `a6910a8550b691aa20b16ff18fae0dd902fbb064`
**Title:** Pinned Targets (Attachments Default) + TTL + Action Result Pinning + LLM Set Reasoning

**What it changed (high level)**
- Added pipeline step: `ActionCommandNormalizationStep` (UI “Action:” / “Suggestion:” handling).
- Attachment behavior improvements:
  - If `activeAttachmentIds` is missing, treat all attachments as active (implicit).
  - If attachment has no id, generate a **synthetic id** (SHA‑256 based) and tag `_syntheticId=true`.
- Prompt terminology moved toward “PINNED TARGETS” and “not a scope restriction”.
- Target resolution: if no `activeAttachmentIds`, treat **all attachments** as active.
- Chat session pipeline:
  - Store/persist targets from action results and/or active attachments (with TTL behavior considerations).
  - Seeds working set and pinned targets for follow‑ups.
- Real app handlers: many action handlers updated to return `ActionTargetRef` content/metadata for better grounding.

**Assessment**
- **Keep (core):** ✅ This is foundational for real UI integration (active list optional, id generation, better prompt rules).
- **Risks / concerns**
  - Early version of `ActionCommandNormalizationStep` removed too much context (fixed later in `ac663acb…`).
  - Step ordering conflicts were possible (also addressed later).
  - Synthetic ids are useful for id‑less docs, but you should prefer UI‑provided ids for entities (product/order/etc).

---

## 4) `dcffc4df270c6ca1daec595a5a5a71a2ec0a3371`
**Title:** Finalizing app changes

**What it changed**
- Real app catalog actions improved pinned target payload quality:
  - Truncate `contentText` for pinned targets (bounded content).
- UI migration guide updated.

**Assessment**
- **Keep:** ✅ Strong hardening; prevents huge pinned payload dumps.
- **Risk:** Low.

---

## 5) `b93b21370811b3818602fdd53f8a43081410f72b`
**Title:** Add Hydration step

**What it changed**
- New pipeline step: `AttachmentRehydrationStep`
  - If UI sends only `{id, vectorSpace}` (or missing content/metadata), fetch full record from vector DB.
  - Fill bounded `contentText` + scalar metadata, PII‑processed if PIIDetectionService available.

**Assessment**
- **Gate / optional:** ⚠️ Useful only when the UI is “thin” (id‑only attachments).
- **Costs / risks**
  - Extra vector DB lookups (latency/cost).
  - Step ordering + dependencies (requires vector db availability, vectorSpace correctness).
  - Must remain **config‑gated** and ideally only run when attachment content/metadata is missing.

---

## 6) `ac663acb570d2245f9c9cca32f7c74cba0558a33`
**Title:** Fix Action Pattern is not followed

**What it changed**
- `ActionCommandNormalizationStep` improved:
  - Strip only the outer `Action:`/`Suggestion:` prefix; preserve label + payload so LLM can understand intent.
  - Adjusted step order to avoid collisions.
- Retrieval query hardening:
  - Extract real user query from prompts containing injected sections/markers.
  - Reject/replace polluted `optimizedQuery` (containing injected markers like `PINNED TARGETS` / `BEGIN QUERY`).
  - Adds tests for retrieval query sanitization.

**Assessment**
- **Keep (must‑have):** ✅ This is core “production hardening”.
- **Impact:** reduces “ragQuery contains prompt text” and improves action label behavior.

---

## 7) `0c990076ab5faa41c0c767c11604f9419293bacd`
**Title:** Update `system.md` (compound extraction rules)

**What it changed**
- Prompt rule change: for search/list/filter requests, **prefer**:
  - `requiresRetrieval=true` and `requiresGeneration=true`
  - Only set `requiresGeneration=false` when user explicitly requests raw results.

**Assessment**
- **Keep:** ✅ Aligns with “RAG docs are not user-friendly” → you want generation.
- **Tradeoff:** More generation calls (expected).

---

## 8) `0c689f7c189b8f6ef66093e21497989564cc0e81`
**Title:** Update `GetProductDetailsActionHandler.java`

**What it changed**
- Avoids `null` in error message: uses exception message or class name.

**Assessment**
- **Keep:** ✅ Small hygiene improvement.

---

## 9) `00b8cf357885bceac8649cd98ff5b69777a8e125`
**Title:** Softening the authoritative pinned targets

**What it changed**
- Prompt wording shifted from strict “AUTHORITATIVE” framing to:
  - “PINNED TARGETS (user-selected candidates…)”
- Similar softening in RAG generation prompts and other templates.

**Assessment**
- **Keep:** ✅ Reduces “scope prison” behavior and makes the system less brittle.

---

## 10) `4ae6bb73c1aa996d73c7b9a695d45c07e2605097`
**Title:** Pinned Targets + Working Set Rebalance

**What it changed**
- Introduced `recentTargets` in pipeline context (low priority, from conversation state).
- Promotion rule: only promote recent targets to actionable `resolvedTargets` when `requiresTargetResolution=true`.

**Assessment**
- **Keep:** ✅ Correct separation: prevents old targets from blinding new searches.
- **Important dependency:** the extractor must actually “see” recent targets or it won’t set `requiresTargetResolution` reliably (addressed by `b319b6dd…`).

---

## 11) `a485882e1e9edc9fbddc33904d8536157c9dd2b9`
**Title:** Update Real app chat (write actions emit targets)

**What it changed**
- Real app write actions updated to return explicit pinned targets in results (ticket/return/review/offer).

**Assessment**
- **Keep:** ✅ Required for reliable action follow-ups (“cancel it”, “did you create that ticket?”).

---

## 12) `b319b6ddbbe50e531f7ed681e0309ea2e181866d`
**Title:** Fix Missing Context that made Rag more frequent

**What it changed**
- Ensured the extractor sees missing context (recent targets) via prompt injection.
- Prompt tweaks to interpret `RECENT TARGETS` properly.

**Assessment**
- **Keep (paired with 4ae6bb73):** ✅ Without this, follow‑ups often fall back to RAG.
- **Risk:** If wording is wrong, RECENT TARGETS can reintroduce “scope bias”. Must emphasize “not a scope restriction”.

---

## Recommended v1 subset (pragmatic)
### Must-have (core reliability)
- `ac663acb…` query sanitization + action prefix normalization
- `00b8cf35…` soften pinned target language
- `4ae6bb73…` + `b319b6dd…` recentTargets rebalance + extractor visibility
- Real app target refs: `dcffc4df…` + `a485882e…`

### Optional (gated)
- `b93b2137…` attachment rehydration (only for id-only attachment UIs)
- `f0dc6f3c…` attachment query-hint fallback (deep mode only)

---

## Key takeaway
The “best” optimizations are the ones that:
- reduce polluted queries (`optimizedQuery` sanitization),
- keep UI label intent intact (Action prefix normalization),
- separate pinned (active attachments) from recent (prior turns),
- keep prompts consistent: pinned targets help but do not restrict new searches.

