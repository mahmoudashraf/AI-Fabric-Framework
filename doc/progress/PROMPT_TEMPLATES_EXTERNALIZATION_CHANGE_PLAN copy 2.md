# Prompt Templates Externalization (Multi-Step Intent Extraction) — Change Plan

## Status
Proposed

## Scope
Extract the hardcoded prompt templates and JSON schemas from:
- `ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/intent/extraction/MultiStepIntentExtractionStrategy.java`

Into versioned, testable templates that can be tuned without code edits.

## Problem
Today `MultiStepIntentExtractionStrategy` embeds:
- classification prompt + schema
- action selection prompt + schema

This makes it harder to:
- iterate prompt variants safely (A/B or provider-tuned prompts)
- review prompt changes independently from code changes
- evolve prompts without triggering broad recompiles/redeploys
- introduce prompt versioning and rollout strategies

## Goals
- Centralize prompts as **versioned templates**
- Support **per-provider overrides** (OpenAI/Anthropic/Gemini) while staying provider-agnostic by default
- Keep prompts **type-safe** at call sites (placeholders validated, required values present)
- Make prompt behavior **testable** (unit tests asserting rendered prompt includes required sections)
- Preserve current behavior and guardrails:
  - “JSON only” outputs
  - structural-only correction semantics
  - action selection constrained to registry list

## Non-goals
- Replacing all prompts across the entire framework in one PR
- Introducing a full prompt-management UI
- Adding dynamic network loading of prompts

## Proposed Design

### 1) Prompt Template Interface (Core)
Introduce a small prompt rendering API in core:
- `PromptTemplate` (string template + named placeholders)
- `PromptRenderer` (renders template with map, validates missing placeholders)
- `PromptBundle` (grouped templates by feature/module + version)

### 2) Storage Strategy
Use classpath resources under a stable path:
- `ai-infrastructure-module/ai-infrastructure-core/src/main/resources/prompts/intent-extraction/multi-step/v1/`
  - `classify.md`
  - `select-actions.md`

Each template contains:
- schema contract
- rules
- placeholders (e.g., `{{user_query}}`, `{{allowed_actions}}`, `{{action_intents}}`)

### 3) Configuration & Versioning
Add config:
- `ai.prompts.intent-extraction.multi-step.version=v1` (default `v1`)
- Optional per-provider override:
  - `ai.prompts.providers.openai.intent-extraction.multi-step.version=v1-openai`

### 4) Integration in `MultiStepIntentExtractionStrategy`
Replace inline strings with:
- load template by version
- render with deterministic inputs
- keep `jsonOnlyResponseParameters()` and “Return JSON only” system prompts unchanged

### 5) Testing
Add unit tests:
- template loads successfully for default version
- renderer fails-fast on missing placeholders (fail-closed)
- rendered prompts include required schema blocks and injected values
- multi-step strategy still produces the same `MultiIntentResponse` structure for fixed mocked LLM outputs

## Rollout Plan
1. Add prompt rendering + template loader utilities in core
2. Add `v1` templates matching current embedded prompts
3. Switch `MultiStepIntentExtractionStrategy` to use templates
4. Add provider-specific variants only when validated by RealAPI tests

## Risks & Mitigations
- **Runtime missing resource** → fail fast at startup via Spring bean initialization
- **Prompt drift** → enforce prompt version pinning in config + tests
- **Security/logging** → never log full rendered prompt at INFO; use length/snippet only

## Acceptance Criteria
- No behavior regressions in existing unit/integration tests
- Prompt changes are possible by editing resource files + bumping configured version
- Clear versioned directory structure and an ADR documenting the choice

