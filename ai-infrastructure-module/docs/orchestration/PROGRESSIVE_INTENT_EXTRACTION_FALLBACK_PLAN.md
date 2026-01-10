## Progressive Intent Extraction Fallback Plan (Compound → Repair → Multi‑Step)

This is an implementation plan for making intent extraction **more reliable across providers** by using a **progressive fallback ladder**:

1. **Compound (single request) fast-path**
2. **Repair loop** (only for structural/schema correctness)
3. **Multi-step extraction** (decomposed prompts with step-level validation)

This plan is designed to work **with** the existing provider-agnostic stabilization strategy:
- **Canonical orchestration contract** + **normalization layer**
- Provider-agnostic guardrails (action grounding, filter sanitization, etc.)
- Provider-module retry/backoff for transient HTTP failures

---

### Goals
- **Reduce flaky failures** caused by malformed / truncated / partially valid model outputs.
- Keep behavior **provider-agnostic**: same logic regardless of OpenAI/Anthropic/Cohere/Gemini/Azure.
- Make intent extraction **observable** (which path was used, why it fell back).
- Keep the system **bounded**: no unbounded “ask again until it works”.
- Enable **separate LLM provider selection** for:
  - **orchestration** (intent extraction / planning / structured outputs)
  - **generation** (final narrative answers / RAG responses)

### Non-goals
- Do not use “repair” to fix **semantic correctness** (e.g., “pick a better action”) via repeated LLM loops.
- Do not introduce provider-specific extraction code in the core orchestrator.
- Do not weaken tests; keep asserting canonical invariants.

---

## Proposed runtime behavior (the fallback ladder)

### Definitions
- **Structural failure**: invalid JSON, schema mismatch, wrong field types, missing required fields, unparseable enums, truncated payload.
- **Semantic failure**: output is structurally valid but logically wrong (e.g., action unrelated to request, hallucinated filters).

We only use “repair” for **structural failures**. Semantic issues should be handled by:
- prompt constraints (“actions must be from AVAILABLE ACTIONS”),
- deterministic registries/guardrails,
- and canonical normalization.

### Ladder overview

#### 1) Compound fast-path (single request)
- Build the full system prompt (current approach).
- Request a single JSON payload.
- Parse to `MultiIntentResponse`.
- Run **deterministic validation** (schema + invariants).

If valid → return it (continue pipeline).

If invalid due to **structural failure** → go to Repair (step 2).

If valid but “unsafe” (e.g., ACTION not in registry) → do **existing deterministic guardrails** (e.g., coerce misclassified RAG-as-ACTION), then re-validate.

#### 2) Repair loop (structural/schema-only)
- Take the *original prompt intent* + a minimal snippet of the malformed output (bounded) + machine-generated validation errors.
- Ask the model to return **only valid JSON** matching the schema.
- Parse + validate again.

Constraints:
- **Max attempts**: 1 (default). Allow 2 only if metrics show strong benefit.
- **Never** use repair for semantic “reasoning” corrections.

If repair succeeds → return result.

If repair fails → go to Multi-step (step 3).

#### 3) Multi-step extraction (decomposed, validated)
Break intent extraction into small prompts that are easier for providers to satisfy.

Recommended step sequence (minimal viable):
1. **Classify**: ACTION vs INFORMATION vs OUT_OF_SCOPE (and whether retrieval is required).
2. **If ACTION**: pick `action` from `AVAILABLE ACTIONS` (hard constraint: must be one of them).
3. **If INFORMATION**: output retrieval/generation requirements + vectorSpace, etc.
4. **If relationship_query is selected**: generate the relationship query plan using the schema prompt (existing planner flow).

Each step is:
- short prompt
- strict schema
- deterministic validation before proceeding to the next step

If any step fails structurally:
- optionally apply **one** step-level repair (same rules as above),
- otherwise fail with a structured extraction error (which will be normalized downstream).

---

## Implementation plan (code-level)

### 1) Introduce an internal “intent extraction engine”
Create a small orchestration-internal abstraction so strategies can be swapped without spreading conditionals.

**New types (suggested):**
- `IntentExtractionEngine`
  - `MultiIntentResponse extract(String query, OrchestrationContext ctx)`
  - returns the best-effort valid `MultiIntentResponse` or throws an `IntentExtractionException` with structured metadata
- `IntentExtractionStrategy` interface
  - `ExtractionAttempt attemptExtract(...)` (returns parsed output + validation status + diagnostics)

**Strategies:**
- `CompoundIntentExtractionStrategy` (existing behavior)
- `RepairingCompoundIntentExtractionStrategy` (wraps compound + repair loop)
- `MultiStepIntentExtractionStrategy` (decomposed prompts)

**Selection logic:**
- `ProgressiveIntentExtractionEngine`:
  - try compound
  - if structural fail → repair
  - if still structural fail → multi-step

### 2) Add deterministic validators (schema + invariants)
Add a validator component that can run without provider-specific logic.

**New types (suggested):**
- `IntentExtractionValidator`
  - `ValidationResult validate(MultiIntentResponse response, OrchestrationContext ctx)`
  - returns: `ok`, `errors[]`, `warnings[]`, `errorCategory` (STRUCTURAL vs UNSAFE vs OTHER)

**Validation rules (minimum):**
- Response has at least one intent.
- Intent type is present and recognized.
- If `type=ACTION`:
  - action name exists
  - action resolves to an actual handler after registry normalization (**or** intent is coercible by deterministic rules)
  - action is grounded in `AVAILABLE ACTIONS`
- If `type=INFORMATION`:
  - if it claims retrieval/generation, required fields exist (vectorSpace, etc.)

### 3) Implement repair prompts (schema-corrector)
Repair prompt must be:
- explicit schema
- machine-generated error list
- strict output constraints (“return JSON only”)
- bounded input (don’t paste huge prompts/responses into the repair loop)

**Repair input should include:**
- The JSON schema (or a compact field contract)
- The model’s previous output (truncated to a safe window)
- The validation errors (field paths + reason)

**Repair output expectation:**
- A valid JSON object matching the schema, with no prose.

### 4) Implement multi-step prompts (small, hard constraints)
Use minimal prompts per step and validate each step.

Suggested schemas:
- `IntentClassification`:
  - `primaryType`: `ACTION|INFORMATION|OUT_OF_SCOPE`
  - `requiresRetrieval`: boolean
  - `requiresGeneration`: boolean
- `ActionSelection`:
  - `action`: string (MUST be one of AVAILABLE ACTIONS)
  - `confidence`: number 0..1
- `InformationSelection`:
  - `vectorSpace`: string (required if requiresRetrieval)
  - `semanticQuery`: string (optional)

Then map these into the existing `MultiIntentResponse` structure.

### 5) Observability and diagnostics
Add structured logs/metrics to understand how often the slow paths are used:
- extraction path: `compound|repair|multi_step`
- failure category: `parse_error|schema_error|timeout|provider_error|unsafe_action|...`
- attempt count and latency per attempt

Where to surface this:
- logs
- orchestration debug snapshots (safe, non-PII)
- optional headers/metadata in test outputs (behind a flag)

### 6) Feature flags and rollout safety
Add properties to control behavior:
- `ai.intent-extraction.progressive.enabled` (default: `false` initially, then flip to `true` after proving)
- `ai.intent-extraction.repair.enabled` (default: `true` when progressive is enabled)
- `ai.intent-extraction.repair.max-attempts` (default: `1`)
- `ai.intent-extraction.multi-step.enabled` (default: `true` when progressive is enabled)
- `ai.intent-extraction.force-mode` (optional: `compound|repair|multi_step` for debugging)

Rollout plan:
- Stage 1: add engine + validators, keep default `compound` only.
- Stage 2: enable repair in CI real-api runs.
- Stage 3: enable multi-step fallback only after repair metrics are stable.
- Stage 4: enable progressive by default for real-api contexts only (or behind profile).

---

## Configuring a specific LLM provider for **orchestration** vs **generation**

Today the system typically uses `ai.providers.llm-provider` as the default LLM for all tasks. For stability and cost control, we want **two separate provider selections**:

- **Orchestration LLM**: intent extraction, classification, action selection, relationship-query planning (structured outputs).
- **Generation LLM**: final narrative generation / RAG response wording (can be higher quality model).

### Proposed configuration shape

Add a new nested config block (suggested):

- `ai.providers.orchestration.llm-provider`
- `ai.providers.orchestration.model` (optional override)
- `ai.providers.orchestration.maxTokens` (optional)
- `ai.providers.orchestration.temperature` (optional; default low for structure)

- `ai.providers.generation.llm-provider`
- `ai.providers.generation.model` (optional override)
- `ai.providers.generation.maxTokens` (optional)
- `ai.providers.generation.temperature` (optional)

Fallback rules:
- If orchestration/generation provider is not set, fall back to `ai.providers.llm-provider`.
- If orchestration/generation model is not set, fall back to the provider’s configured model (existing per-provider config).

### Example YAML

```yaml
ai:
  providers:
    # default (backward compatible)
    llm-provider: openai

    orchestration:
      llm-provider: cohere
      temperature: 0.1
      maxTokens: 1200

    generation:
      llm-provider: openai
      model: gpt-4o
      temperature: 0.3
      maxTokens: 2000
```

### Environment variable mapping (suggested)
- `ORCHESTRATION_LLM_PROVIDER`
- `ORCHESTRATION_LLM_MODEL`
- `GENERATION_LLM_PROVIDER`
- `GENERATION_LLM_MODEL`

(Keep the existing `LLM_PROVIDER` env var as the global default.)

### Required code changes (high level)
1. Extend `AIProviderConfig` to include:
   - `OrchestrationLlmConfig orchestration`
   - `GenerationLlmConfig generation`
   - helpers like:
     - `resolveOrchestrationLlmDefaults()`
     - `resolveGenerationLlmDefaults()`
2. Extend `AIProviderManager` / `AICoreService` APIs so callers can specify a **purpose**:
   - `generateContent(request, Purpose.ORCHESTRATION)`
   - `generateContent(request, Purpose.GENERATION)`
3. Wire usage:
   - `IntentQueryExtractor` uses **ORCHESTRATION** defaults/provider
   - RAG answer generation uses **GENERATION** defaults/provider
4. Update validator (`AIProviderConfigValidator`) to validate both when configured.

### Why this matters for progressive fallback
Multi-step extraction and repair loops are “structure-first”. They benefit from:
- low temperature
- JSON-friendly models
- predictable outputs

But final generation may benefit from:
- higher quality model
- more tokens

Separating the provider choice lets you optimize each without destabilizing the other.

---

## Testing plan (no shortcuts)

### Unit tests (deterministic)
- `IntentExtractionValidatorTest`:
  - structural validity checks
  - action-in-registry checks
  - coercion rules stay deterministic
- `ProgressiveIntentExtractionEngineTest`:
  - compound succeeds → no fallback
  - compound parse fails → repair is invoked
  - repair fails → multi-step is invoked
  - bounded attempt enforcement

### Integration tests (real-api)
Focus on invariants:
- final `OrchestrationResult` matches canonical contract (already enforced by normalization)
- no successful unintended action execution
- sanitized payload mirrors canonical classification

Add observability assertions only where deterministic:
- record that a fallback path was used (optional, behind debug flag)

### Provider matrix tests
For provider-matrix runs:
- keep progressive fallback **enabled** (to reduce flake)
- keep bounded attempts low to avoid cost blowups
- report per-combination metrics (fallback path counts)

---

## Operational considerations

### Cost/latency guardrails
- Strict attempt caps.
- Global max latency per request (fail with a structured extraction error).
- Prefer repair before multi-step (repair is cheaper than multiple steps).

### Failure reporting
When extraction ultimately fails, error output should include:
- which modes were attempted
- validation errors
- provider response status category (if known)
- last safe snippet of provider output (bounded)

This is critical for debugging “short logs” issues.

---

## Acceptance criteria (definition of done)
- Progressive fallback is implemented behind flags.
- Structural failures drop significantly in real-api CI (measurable).
- No increase in unsafe action execution.
- Separate orchestration/generation provider configuration is supported and validated.
- Tests remain invariant-based and provider-agnostic.

