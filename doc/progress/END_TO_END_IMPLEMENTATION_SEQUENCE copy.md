# End-to-End Implementation Sequence (Policy + Curated Modes + Attachments + Prompt SPI)

## Status
In progress (Phase 7 complete; Phase 8 complete)

## Purpose
Provide a single, testable implementation sequence for the full agreed solution:
- **Stable core pipeline** driven by a server-authoritative **`OrchestrationPolicy`**
- **Curated modes via modules** (transparent presets + prompt bundles; no custom intent extraction engines)
- **Attachments + metadata + working set** to stop drift and make actions deterministic from UI context
- **Prompt template SPI + externalized prompts** (OSS default), with optional enterprise stores/management

This document is a sequencing layer that references the detailed plans below.

## Related documents (source of truth per area)
- `changes/CURATED_MODES_MODULES_AND_POLICY_PROMPT_SPI_ARCHITECTURE_PLAN.md` (agreed architecture boundary)
- `changes/MODE_DRIVEN_ORCHESTRATION_POLICY_PLAN.md` (position→mode→policy; server-authoritative)
- `changes/OPTIMIZATION_PROFILES_AND_DETERMINISTIC_RAG_INTEGRATION_PLAN.md` (profiles; deterministic integration; precedence)
- `changes/ATTACHMENTS_METADATA_AND_RAG_OPTIMIZATION_MASTER_PLAN.md` (attachments, metadata, target resolution, working set)
- `changes/PROMPT_TEMPLATES_EXTERNALIZATION_CHANGE_PLAN.md` (externalize prompts; versioning; renderer)
- `changes/ENTERPRISE_PLUGGABLE_OPTIMIZATIONS_AND_PROMPT_MANAGEMENT_PLAN.md` (enterprise stores + governance + experiments)
- (optional) `changes/INTENT_METADATA_RAG_QUERY_AUGMENTATION_CHANGE_PLAN.md` (explicit metadata key for retrieval hints)

---

## Global constraints (must hold throughout)
- **Greenfield:** break APIs/configs if needed; no compatibility shims.
- **Fail-closed:** ambiguous targets → ask `CLARIFICATION_REQUIRED`, never guess.
- **Contracts over heuristics:** avoid domain-key guessing in core.
- **Server-authoritative:** client can *request* `position/mode`, server selects effective policy via allowlists.
- **Observability:** every request should expose effective policy + prompt version in debug metadata.

---

## Phase 0 — Baseline audit + stabilization (short)
**Goal:** ensure we have a stable baseline before introducing new routing layers.

Deliverables:
- Confirm parent `mvn -f ai-infrastructure-module/pom.xml verify` passes.
- Ensure the chat history has actionable refs available to LLM for follow-ups (already implemented via “Action Context”).

Validation gate:
- `mvn -f ai-infrastructure-module/pom.xml verify`

---

## Phase 1 — Core architecture: OrchestrationPolicy as single source of truth (foundation)
**Goal:** eliminate scattered flag reads; establish policy resolution as the “root of behavior”.

Implement (core):
1) **Introduce/Finalize `OrchestrationPolicy`** object and add it to `PipelineContext`.
2) Add early **PolicyResolutionStep**:
   - inputs: global profile + request `position/mode` signals
   - outputs: effective policy + debug metadata snapshot
3) Update critical pipeline steps to consume policy (not raw properties):
   - `IntentExtractionStep` / prompt selection paths
   - `VectorSpaceResolutionStep` deterministic fan-out behavior
   - `IntentHandlingStep` INFORMATION behavior (LLM-driven vs deterministic)
   - confirmation/action-eligibility filtering hooks

References:
- `changes/MODE_DRIVEN_ORCHESTRATION_POLICY_PLAN.md`
- `changes/OPTIMIZATION_PROFILES_AND_DETERMINISTIC_RAG_INTEGRATION_PLAN.md`

Validation gate:
- Unit tests for precedence: `profile < mode < position routing`
- Parent verify: `mvn -f ai-infrastructure-module/pom.xml verify`
- Ensure debug metadata includes:
  - `policy.profile`, `policy.mode`, `policy.position`
  - `policy.informationModeEffective`, `policy.promptModeEffective`

---

## Phase 2 — Prompt template SPI + externalization (enabler for curated modules)
**Goal:** make prompts versioned and swappable without code edits, while remaining testable and fail-closed.

Implement (core):
1) Add `PromptTemplateStore` + `PromptRenderer` (strict placeholder validation, bounded output).
2) Externalize existing hardcoded templates into classpath resources (`v1`), matching current behavior.
3) Wire `EnrichedPromptBuilder` / multi-step extraction prompts to load templates by:
   - effective policy prompt mode
   - configured template version
   - provider variant (optional)

References:
- `changes/PROMPT_TEMPLATES_EXTERNALIZATION_CHANGE_PLAN.md`
- `changes/ENTERPRISE_PLUGGABLE_OPTIMIZATIONS_AND_PROMPT_MANAGEMENT_PLAN.md` (SPI design)

Validation gate:
- Unit tests:
  - missing placeholder → fail fast
  - missing template resource → fail fast at startup
- Parent verify: `mvn -f ai-infrastructure-module/pom.xml verify`
- RealAPI suites pin prompt versions to prevent drift.

---

## Phase 3 — Modes/profiles as “coherent bundles” (OSS defaults + reproducibility)
**Goal:** ship sane presets that do not require users to coordinate many flags.

Implement (core):
1) Add/Finalize:
   - `ai.orchestration.profile` presets (DEFAULT/PRODUCTION_NAVIGATOR/PRODUCTION_CHAT)
   - `ai.orchestration.modes.*` (policy bundles)
   - `ai.orchestration.positionRouting.*` (position → mode mapping)
2) Define precedence rules explicitly in code and docs.

References:
- `changes/OPTIMIZATION_PROFILES_AND_DETERMINISTIC_RAG_INTEGRATION_PLAN.md`
- `changes/MODE_DRIVEN_ORCHESTRATION_POLICY_PLAN.md`

Validation gate:
- Unit tests for:
  - deterministic mode (INFO always retrieve+generate)
  - chatty mode (directAnswer path when requiresRetrieval=false)
- Parent verify

---

## Phase 4 — Attachments v1: request contract + prompt injection (authoritative UI context)
**Goal:** stop guessing what the user is referring to; make UI selections explicit and authoritative.

Implement (core + demo app):
1) Extend chat request contract to accept:
   - `attachments[]` + `activeAttachmentIds[]`
   - `position` and/or `mode` (request-level signals)
2) Add **AttachmentNormalizationStep** (bounded, scalar-only metadata, PII-aware).
3) Add **Prompt augmentation** section that is injected **before history**:
   - “ATTACHMENTS (authoritative)” with `id`, `vectorSpace`, and bounded `metadata`.

References:
- `changes/ATTACHMENTS_METADATA_AND_RAG_OPTIMIZATION_MASTER_PLAN.md`
- `changes/MODE_DRIVEN_ORCHESTRATION_POLICY_PLAN.md` (position)

Validation gate:
- Add an integration test that sends attachments and asserts:
  - effective prompt contains the attachment block
  - request metadata indicates attachments were accepted/truncated deterministically
- Manual smoke in demo UI: “buy it” on a selected card does not select unrelated SKU.

---

## Phase 5 — Target resolution + retrieval scoping (prevent drift)
**Goal:** follow-ups like “compare both / buy it / add to cart” resolve targets deterministically.

Implement:
1) Add **TargetResolutionStep** (fail-closed):
   - uses active attachments first
   - then working-set (later phase)
   - else asks clarification
2) Constrain retrieval vector spaces:
   - if attachments exist and policy allows, limit to attachment vector spaces
3) Action parameter binding prefers resolved targets over “best guess from RAG”.

References:
- `changes/ATTACHMENTS_METADATA_AND_RAG_OPTIMIZATION_MASTER_PLAN.md`

Validation gate:
- Integration test: “compare both” with two attachments compares those two only.
- Integration test: “add to cart” uses active attachment SKU deterministically.
- Parent verify

---

## Phase 6 — Retrieval working set (follow-up stability)
**Goal:** stop “context lost” across 2–3 turns due to broad re-search.

Implement:
1) Persist a bounded working set per turn when RAG runs:
   - vectorSpaces used + top doc ids (+ optional scores)
2) Allow follow-ups to reuse the working set:
   - “it/both/this” resolves from the working set when no active attachments exist
3) Add policy knobs:
   - enable/disable working set per mode

References:
- `changes/ATTACHMENTS_METADATA_AND_RAG_OPTIMIZATION_MASTER_PLAN.md`

Validation gate:
- Integration test: follow-up “compare both” without attachments uses the last working set, not broad search.
- Parent verify

---

## Phase 7 — Curated modes as modules (transparent presets + prompt bundles)
**Goal:** ship “ready-to-use” bundles without code injection.

Implement (new modules):
1) Create curated packs:
   - `ai-curated-catalog` (navigator + deterministic)
   - `ai-curated-commerce` (cart assistant + orders/returns)
   - `ai-curated-support` (issue resolver; safer confirmation defaults)
2) Each pack includes:
   - `modes.*` YAML defaults
   - prompt templates (classpath) for their recommended modes
   - docs and pinned test profiles

References:
- `changes/CURATED_MODES_MODULES_AND_POLICY_PROMPT_SPI_ARCHITECTURE_PLAN.md`

Validation gate:
- RealAPI suites run with the curated pack enabled and pinned prompt versions.
- Demonstrate reproducibility: behavior is identical given the same policy + prompt version.

---

## Phase 8 — Optional: explicit intent-metadata retrieval hints (bounded, fail-closed)
**Goal:** allow intent extractor to provide extra retrieval hints in a strict contract.

Implement (optional):
- Support `intentResponse.metadata.retrievalQueryHint` only when:
  - exactly one retrieval intent exists
  - hint passes strict validation (length, no PII markers)

Reference:
- `changes/INTENT_METADATA_RAG_QUERY_AUGMENTATION_CHANGE_PLAN.md`

Validation gate:
- Unit tests for safe/unsafe hint handling.
- RealAPI test: hint changes queryUsed marker deterministically.

---

## Phase 9 — Enterprise add-ons (optional; no core behavior changes)
**Goal:** monetize governance/operations while keeping behavior transparent and reproducible.

Implement (separate module/repo preferred):
1) DB-backed `PromptTemplateStore` and `PolicyStore`
2) Admin APIs (CRUD, approvals, audit)
3) Optional experiments (A/B), cost budgets, rollback tooling

Reference:
- `changes/ENTERPRISE_PLUGGABLE_OPTIMIZATIONS_AND_PROMPT_MANAGEMENT_PLAN.md`

Validation gate:
- Store availability failure semantics are explicit (fail-fast vs explicit fallback).
- Audit log correctness and RBAC tests.

---

## Final acceptance checklist (end-to-end)
1) **Position-aware behavior**
   - `position=cart` reliably enables cart assistant mode and action allowlists.
2) **Attachment grounding**
   - UI-selected item is always used for actions (“buy it”, “add to cart”).
3) **Drift resistance**
   - follow-ups do not mix unrelated products (phone vs sneakers).
4) **Deterministic vs chatty trade-offs**
   - PRODUCTION_NAVIGATOR answers reliably (deterministic RAG+generate)
   - PRODUCTION_CHAT can return short replies without retrieval when appropriate
5) **Reproducibility**
   - effective policy + prompt versions are visible in debug metadata and pinned in tests.
