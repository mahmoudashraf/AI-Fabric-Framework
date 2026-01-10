# Real API Provider Stabilization Guidelines (Provider-Agnostic)

This document captures the **restrictions, recommendations, and agreed engineering rules** used to stabilize Real API integration tests across multiple AI providers (OpenAI, Anthropic, Cohere, Gemini, Azure, etc.) without “short-cutting” tests or introducing provider-specific hacks.

It is written so a future chat/session can continue work consistently even if the current context is lost.

---

## Core principles (non‑negotiable)

### Provider-agnostic first
- **Always prefer provider-agnostic solutions** in shared orchestration / parsing / normalization layers.
- Only add provider-specific behavior if:
  - the provider has a **documented, stable contract difference** that cannot be normalized safely, and
  - the change is **isolated to the provider module** and does not leak into tests as “special cases”.

### Do not “fix” flaky tests by weakening the product contract
- Tests exist to protect user-facing behavior. If a test is failing due to provider variability, the first question is:
  - “Is the contract unclear or under-specified?” → **clarify contract + enforce it in code**
  - “Is the provider output non-deterministic but acceptable?” → **assert invariants**, not exact prose
  - “Is the provider output unacceptable (hallucinated actions/filters)?” → **guardrails / sanitization**

### No production code changes that only exist to satisfy a single test
- **Never embed test-specific action names, queries, or regex overrides in production** (e.g., “if query contains X then set action=Y”).
- If a test needs deterministic conditions, use **test-only overrides** (`@TestConfiguration`, mocked registries, fixed fixtures), not production branches.

### Enforce a stable orchestration result contract
Provider outputs will vary. The **top-level orchestration contract must not**.
- The orchestration pipeline must produce a **canonical** `OrchestrationResult` surface that tests can assert against reliably.

---

## The agreed orchestration contract

### Canonical result fields
The orchestration layer must produce a normalized `OrchestrationResult` that is stable across providers. Tests should assert against these fields (and their invariants) rather than provider-specific text.

- **`type`**: A canonical `OrchestrationResultType` describing what the system did.
- **`success`**: Boolean success for the overall request (after normalization).
- **`errorCode`** (string, optional): A canonical error identifier when the result is an error.
- **Action-related fields**:
  - **`actionWasAttempted`**: Whether an action handler was actually invoked.
  - **`actionName` / `action`**: The chosen action (if any), normalized by registry rules.
- **`responseMessage`** (or equivalent narrative): Free-form, provider-variable. Tests should not assert exact wording.
- **History / audit fields** (if persisted): must reflect the same canonical classification as the final result after normalization.

### Canonical success semantics
The most important stability guarantee is that “success” and “type” mean the same thing no matter which provider produced the upstream output.

- **`ACTION_EXECUTED`**: An action handler was invoked and completed successfully.
- **`INFORMATION_PROVIDED`**: The system answered or summarized using RAG / retrieval / generation.
- **`OUT_OF_SCOPE`**: The user request did not match any available action AND is not an information request the system should fulfill.
  - **Preferred semantics**: `type=OUT_OF_SCOPE`, `success=true`, `actionWasAttempted=false`.
- **`ERROR`**: The pipeline failed in a user-visible way.
  - `success=false`
  - `errorCode` must be set to a stable identifier when the failure is deterministically classifiable (e.g., `ACTION_NOT_FOUND`).

### Canonical error codes (examples)
Use stable strings that tests can assert reliably. Add to the taxonomy only when the system can determine them deterministically.

- **`ACTION_NOT_FOUND`**: The LLM selected an action, but no handler exists after registry normalization.
- **`ACCESS_DENIED`** (relationship query): policy denies entity types / paths.
- **`PROVIDER_AUTH` / `PROVIDER_RATE_LIMIT` / `PROVIDER_NETWORK` / `PROVIDER_ERROR`** (connectivity verifier): connectivity classification used to fail fast and produce actionable diagnostics.

---

## The agreed “normalization layer” approach

### Why we introduced normalization
Different providers may:
- return different top-level types for logically equivalent outcomes,
- vary in how they represent compound intents,
- emit brittle/partial JSON,
- hallucinate actions, or
- fail transiently (5xx/429/timeout).

Instead of “tweaking tests per provider”, we **define a canonical contract** and implement a **normalization step** to enforce it.

### What belongs in normalization
Normalization is responsible for **deterministic interpretation** of pipeline outputs (not provider output parsing itself).

Typical normalization responsibilities:
- **Bubble up child errors** in compound results.
- **Promote a primary child type** (e.g., if the compound intent contains a successful action execution, the top-level result should reflect the canonical actionable outcome).
- **Derive stable `errorCode` values** for deterministic failures (e.g., missing action handler).
- Ensure **`success` is consistent** with canonical semantics.

### What does *not* belong in normalization
- Provider-specific parsing rules that are only relevant for one vendor API.
- Prompt hacks like “if query contains X then set action to Y”.
- Any logic that depends on specific user queries used by tests.

### Placement in the orchestrator pipeline
Normalization must run:
- **after** intent extraction, action selection, and action execution have produced their raw result(s),
- **before** results are returned to callers and before test assertions consume them.

### Debug snapshots (for test diagnostics)
When diagnosing provider variability or transient failures, we capture a minimal snapshot of recent normalized results.

Guidelines:
- Keep snapshots **small, non-PII**, and **safe for CI logs**.
- Clear snapshot history at the start of each provider-matrix combination so failures are attributable.
- Include snapshots in assertion failures to avoid “short logs” hiding the true sequence of events.

---

## Prompt rules: action selection must be grounded (no invented actions)

### The rule we agreed on
The system prompt must explicitly instruct providers that:
- **Action selection MUST be grounded in `AVAILABLE ACTIONS`.**
- The model must **never invent actions** that are not in the list (examples of forbidden invented actions: `summarize`, `search`, `lookup`, `answer_question`).
- “Summarize / explain / answer using knowledge base” requests are **INFORMATION** intents (use retrieval/generation flags) **NOT** ACTION.
- If **no available action matches** the user request, return **OUT_OF_SCOPE**.

Rationale:
- Providers differ in how aggressively they “help” by inventing action labels.
- The orchestrator must protect itself from hallucinated actions.

### Prompt changes: how to do it without breaking tests
When changing prompts:
- Treat the prompt as an API. **Document the intent of each rule** (not the rule number).
- Avoid unit tests that assert “rule N exists”. Tests should assert the presence of **stable, meaningful substrings**.
- Keep examples in prompts minimal and representative; example-heavy prompts can cause some providers to “copy” constraints into outputs.

---

## Action discovery and action-name normalization

### Make actions visible to the prompt (provider-agnostic)
The system prompt can only enforce “available actions” if the system reliably exposes them.

Guidelines:
- Prefer an `AIActionProvider` that derives action metadata from actual `ActionHandler` beans.
- Keep action metadata generation deterministic and safe for logs (no secrets, no PII).

### Normalize action names defensively
Providers may return:
- `clear vector index`
- `clear-vector-index`
- `clear_vector_index`
- `clear_vector_index action`

Guidelines:
- The action handler registry must normalize action names so these variants resolve to the same handler.
- Normalization should be **tolerant** (spaces/hyphens → `_`, collapse repeats, strip trailing `_action`) but not so broad that unrelated actions collide.

---

## Guardrails after parsing (provider-agnostic safety nets)

### Misclassified ACTION intents that are really RAG requests
Some providers misclassify “summarize / explain” requests as `ACTION` intents and supply a hallucinated action name.

Agreed guardrail:
- If an intent is `ACTION` but:
  - **no action handler exists** (after normalization), and
  - the intent clearly contains RAG signals (`vectorSpace`, `requiresRetrieval=true`, `requiresGeneration=true`),
  - then coerce it to **`INFORMATION`**.

Important restriction:
- This guardrail must be conservative and based on strong signals. It must not turn real actionable requests into information requests.

### Relationship-query plan guardrails (hallucinated filters)
Providers sometimes introduce filters that are not implied by the user query (often copied from examples).

Agreed guardrail behavior:
- Remove date/time constraints if the user did not mention time.
- Remove “same/matching” cross-entity comparisons unless explicitly requested.
- Remove `riskScore` constraints unless explicitly mentioned.

Goal:
- Provider-agnostic reduction of false negatives (empty result sets caused by invented constraints).

---

## Provider implementation guidelines (logging, retries, safety)

### Logging rules (must follow configuration)
Provider modules must:
- Use the application logger (SLF4J) and **never** `System.out.println`.
- Avoid logging secrets:
  - Never log API keys.
  - If the key is present in a URL query string, log a redacted form (e.g., `key=***`).
- Keep “banner logs” (request/response headers) at a level that respects the selected verbosity.

Operational goal:
- When the workflow is set to “quiet”, logs must actually be quiet (except errors).
- When set to “normal/verbose”, provider logs must be sufficiently detailed for debugging.

### Retry/backoff for transient provider failures
Real API tests are exposed to transient failures (timeouts, 429, 5xx). Provider clients should implement:
- Exponential backoff with jitter
- A small bounded attempt count (e.g., 3)
- A retry predicate for transient statuses: `408`, `425`, `429`, and `5xx`
- Retries for network/timeout exceptions (`ResourceAccessException`)

Restrictions:
- Do not retry auth failures (401/403).
- Do not retry malformed request failures (400) unless the provider explicitly documents it as transient.
- Keep retry logic contained within provider modules (not in tests).

---

## Connectivity pre-checks (fail fast, fail clearly)

### Why we added a connectivity verifier
When credentials are missing/invalid, failing deep in the suite creates confusing errors and wastes time.

Agreed approach:
- Run a minimal LLM request at the start of Real API test scripts.
- Classify failures into actionable buckets:
  - AUTH error (401/403)
  - RATE LIMITED (429)
  - PROVIDER error (5xx)
  - NETWORK error (timeouts/DNS)
  - CONFIG error (missing model/key)

Restrictions:
- The connectivity check should be opt-in by default (system property) so local development is not blocked.
- The scripts/workflow decide when to enable it for CI.

---

## Real API test philosophy: “robust, not watered down”

### What tests should assert (provider-agnostic invariants)
In Real API tests, prefer assertions on:
- **Canonical `type`** (after normalization)
- **`success`** semantics
- **`errorCode`** when a deterministic error is expected
- **Safety invariants**:
  - No secret leakage
  - PII is redacted in sanitized outputs when required
  - No unintended action execution succeeds
  - `sanitizedPayload` mirrors the canonical classification (`type`, `success`, `errorCode`)

Avoid brittle assertions on:
- Exact natural-language response text
- Exact ordering/formatting of model JSON (unless you’re explicitly testing parsing)

### When it’s acceptable for a test to allow multiple outcomes
If a provider occasionally misclassifies despite prompt constraints, tests may allow a small set of outcomes **only if**:
- All allowed outcomes satisfy the same safety invariants, and
- The test still fails if an unsafe condition occurs (e.g., “action executed successfully” when it must not).

Example pattern:
- Preferred: `OUT_OF_SCOPE` with `success=true`, `actionWasAttempted=false`
- Allowed fallback: `ERROR` with `success=false`, and if `actionWasAttempted=true` then `errorCode=ACTION_NOT_FOUND`

### How to make a test deterministic without “short cutting”
Use test-only configuration to constrain the environment:
- Override the available actions list to a known small set.
- Provide no action handlers (or a controlled set) in that test context.
- Use stable fixtures and deterministic data.

Do **not**:
- Add production code branches keyed to the test query.
- Skip assertions that protect real invariants just to pass CI.

---

## Provider scorecards (non-gating performance indicator)

### Why we track “correct rate” in addition to pass/fail
Provider outputs are stochastic and drift over time. A single CI run is a noisy sample, so we track provider quality as a **rate over many runs**:
- “How often was the plan/intents correct?” vs “did it fail once?”
- This provides a stable signal for comparing providers and detecting regressions.

### The two-lane rule (do not replace hard gates)
- **Lane A (hard gates / CI)**: deterministic contract + safety invariants must not flap (JSON/shape, schema validity, normalization invariants, safe execution).
- **Lane B (scorecards)**: rate-based metrics over repeated runs (constraint fidelity, schema validity rate, compound handling rate, etc.).

### Where the detailed rubric + implementation plan lives
- See: `PROVIDER_SCORECARDS_AND_EVALUATION.md`

---

## Relationship-query module: stability + contract clarity

### Access policy semantics must be consistent
Contract rule:
- If `getAllowedEntityTypesForUser()` returns an **empty list**, interpret it as **deny all** (hard deny).

Test rule:
- If a test intends “allow all”, the policy must return an explicit list of allowed entity types (derived from schema), not `[]`.

### Avoid leaking HTTP 500 in integration tests
Integration tests should not fail due to raw server 5xx responses when the server can return a structured error payload.

Agreed approach:
- Convert known domain exceptions into a structured `RAGResponse` (or equivalent) with `success=false` and HTTP 200.
- Ensure response timestamps are serializable/deserializable consistently.

### Timestamp parsing must be tolerant in tests
If `RAGResponse.timestamp` is `LocalDateTime`:
- Some responses serialize without offset (ISO local).
- Some serialize with offset / `Z` (ISO offset).

Agreed test-side approach:
- Use a lenient deserializer that accepts both, to avoid `RestClientException` during response extraction.

### Query generation must match test DB semantics
H2 `LIKE` is case-sensitive by default.

Agreed approach:
- Treat `LIKE` as case-insensitive for string values by applying `LOWER()` to both sides when building JPQL.

---

## CI/workflow hygiene for Real API tests

### Environment variables: avoid empty-string overrides
Spring property binding treats empty strings as “present”, which can disable defaults and cause validation failures.

Rules:
- Never set boolean env vars to an empty string; use `false`.
- Avoid step-level env blocks that override globally-derived defaults with blank values.

### Secrets: always mask keys in logs
Rules:
- Add explicit masking for all provider keys in workflows.
- Provider modules must also redact keys from URLs/log messages.

### Keep CI summary scripts robust
Rules:
- Ensure any output directory exists (`mkdir -p`) before writing artifacts.
- When injecting booleans into inline Python, use Python literals (`True`/`False`) rather than JSON (`true`/`false`).

### Never commit downloaded CI logs
Rules:
- Store downloaded artifacts under ignored directories (e.g., `tmp/`).
- Keep `.gitignore` updated to prevent accidental commits.

---

## How to make changes (agreed workflow)

### Change decision tree
When a Real API test fails:
1. **Check if it’s a transient provider error** (5xx/429/timeout).
   - If yes: improve retry/backoff/logging; do not weaken tests.
2. **Check if it’s a contract ambiguity** (success/type semantics differ).
   - If yes: update the canonical contract + normalization layer; then update tests to assert the contract.
3. **Check if it’s a provider hallucination** (invented actions/filters).
   - If yes: add provider-agnostic guardrails (prompt + sanitization + post-parse checks).
4. **Check if it’s test determinism** (test relies on exact text or exact plan formatting).
   - If yes: refactor test to assert invariants and use test-only configuration/fixtures.

### Required artifacts for meaningful changes
For changes that affect behavior:
- Add/update documentation (like this file) explaining the rule.
- Add unit tests around normalization/guardrails where possible.
- Update integration tests to assert canonical invariants.

---

## Debugging playbook (fast triage)

### If provider logs look “too short”
Check:
- Whether logs are using `System.out` (bad) vs logger (good).
- Whether the selected log level is suppressing the expected logs.
- Whether the provider is failing early (auth/config) and the suite never reaches deeper steps.

### If the failure message is misleading
Improve diagnostics by:
- Including the last few normalized snapshots in assertion failure output.
- Ensuring snapshots are cleared per provider combination.
- Logging request/response metadata at a level consistent with workflow configuration.

---

## Adding a new provider (minimum quality bar)

When introducing a provider module:
- **Implement request/response logging** with safe redaction.
- **Implement retry/backoff** for transient failures.
- Ensure provider configs are validated clearly (missing key/model should fail with actionable message).
- Add module dependencies to the integration-test modules so provider is actually available at runtime.
- Update workflows to configure and mask its credentials consistently.

---

## Summary: the “golden rules” we must keep following
- **Normalize outcomes, don’t rewrite tests per provider.**
- **Never ship test-only behavior in production.**
- **Assert invariants, not prose.**
- **Ground ACTION selection strictly in AVAILABLE ACTIONS.**
- **Retry transient failures; fail fast on auth/config.**
- **Keep logs safe (mask secrets) and controllable (respect log levels).**

---

## Key code locations (for future sessions)

### Orchestration contract + normalization
- `ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/intent/orchestration/OrchestrationResult.java`
- `ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/intent/orchestration/OrchestrationResultNormalizer.java`
- `ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/intent/orchestration/pipeline/steps/OrchestrationResultNormalizationStep.java`
- `ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/intent/orchestration/pipeline/steps/IntentHandlingStep.java`
- Debug snapshots: `ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/intent/orchestration/OrchestrationResultDebugSnapshotStore.java`
- Detailed doc: `ai-infrastructure-module/docs/orchestration/ORCHESTRATION_RESULT_NORMALIZATION.md`

### Prompt construction + intent extraction guardrails
- Prompt rules: `ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/intent/EnrichedPromptBuilder.java`
- Intent parsing + post-parse guardrails: `ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/intent/IntentQueryExtractor.java`
- Action metadata exposure: `ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/intent/action/ActionHandlersAIActionProvider.java`
- Action lookup tolerance: `ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/intent/action/ActionHandlerRegistry.java`

### Provider resilience (retry/logging/secret redaction)
- OpenAI: `ai-infrastructure-module/providers/ai-infrastructure-provider-openai/.../OpenAIProvider.java`
- Anthropic: `ai-infrastructure-module/providers/ai-infrastructure-provider-anthropic/.../AnthropicProvider.java`
- Cohere (LLM + embedding): `ai-infrastructure-module/providers/ai-infrastructure-provider-cohere/.../CohereProvider.java`, `.../CohereEmbeddingProvider.java`
- Gemini (LLM + embedding): `ai-infrastructure-module/providers/ai-infrastructure-provider-gemini/.../GeminiProvider.java`, `.../GeminiEmbeddingProvider.java`
- Azure OpenAI (LLM + embedding): `ai-infrastructure-module/providers/ai-infrastructure-provider-azure/.../AzureOpenAIProvider.java`, `.../AzureOpenAIEmbeddingProvider.java`

### Connectivity verification
- `ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/testing/RealApiConnectivityVerifier.java`
- `ai-infrastructure-module/integration-Testing/integration-tests/src/test/java/com/ai/infrastructure/it/realapi/RealApiConnectivityVerificationTest.java`
- Scripts:
  - `ai-infrastructure-module/integration-Testing/integration-tests/run-provider-matrix-tests.sh`
  - `ai-infrastructure-module/integration-Testing/relationship-query-integration-tests/run-relationship-query-realapi-tests.sh`

### Relationship query guardrails + test stability
- Plan sanitization: `ai-infrastructure-module/ai-infrastructure-relationship-query/src/main/java/com/ai/infrastructure/relationship/service/RelationshipQueryPlanner.java`
- Case-insensitive LIKE in JPQL: `ai-infrastructure-module/ai-infrastructure-relationship-query/src/main/java/com/ai/infrastructure/relationship/service/DynamicJPAQueryBuilder.java`
- Access policy test implementation: `ai-infrastructure-module/integration-Testing/relationship-query-integration-tests/src/test/java/com/ai/infrastructure/relationship/it/config/TestRelationshipQueryAccessControlPolicy.java`
- Exception-to-response mapping (tests): `ai-infrastructure-module/integration-Testing/relationship-query-integration-tests/src/main/java/com/ai/infrastructure/relationship/it/api/RelationshipQueryExceptionHandler.java`
- Lenient time parsing (tests): `ai-infrastructure-module/integration-Testing/relationship-query-integration-tests/src/test/java/com/ai/infrastructure/relationship/it/config/BackendEnvTestConfiguration.java`

### CI/workflow hygiene
- Manual real-api workflow: `.github/workflows/integration-tests-manual.yml`
- Provider env setup: `.github/actions/configure-providers/action.yml`

