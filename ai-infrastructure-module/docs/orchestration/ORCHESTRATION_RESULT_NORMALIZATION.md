## Orchestration Result Normalization (Provider-Agnostic Contract)

### Problem

Real-provider orchestration can vary across LLMs (OpenAI, Anthropic, etc.) even when the underlying system outcome is identical.

Common examples:
- **Missing action handler**: the system deterministically knows the action does not exist, but the final result may be wrapped in a provider-dependent shape such as `COMPOUND_HANDLED`.
- **Compound responses**: one child intent can be a hard `ERROR` while other children succeed; different models may change wrapper types/messages.

This variability makes integration tests flaky and makes client handling inconsistent.

### Goal

Define and enforce a **provider-agnostic, deterministic contract** for the final `OrchestrationResult` returned by the pipeline and persisted in intent history.

### Contract (What the product guarantees)

The final (normalized) `OrchestrationResult` must be stable across providers:

- **`type`**: canonical top-level outcome (e.g., `ERROR`, `INFORMATION_PROVIDED`, `ACTION_EXECUTED`, ...)
- **`success`**: deterministic boolean derived from system facts
- **`errorCode`**: stable identifier for client handling when `type=ERROR`
- **`message`**: product-owned message; must not depend on LLM wording to be correct
- **`children/data`**: optional details for debugging, but must not change the top-level contract

### Normalization Rules (System-Fact Driven)

Normalization should only be applied when the system has deterministic facts:

1. **Any child `ERROR` bubbles to top-level `ERROR`**
   - If `children.anyMatch(type == ERROR)` then:
     - `type = ERROR`
     - `success = false`
     - `errorCode = child.errorCode (if present) else CHILD_ERROR`
     - `message = child.message (preferred)`

2. **Missing action handler is a canonical error**
   - When the action registry cannot resolve a handler:
     - `type = ERROR`
     - `success = false`
     - `errorCode = ACTION_NOT_FOUND`
     - `message` includes `"No action handler registered ..."`

### Implementation

This repository enforces the contract by introducing:

- **`OrchestrationResultNormalizer`**: central normalization logic (conservative, system-fact only).
- **`OrchestrationResultNormalizationStep`**: pipeline step executed after intent handling and before metadata/suggestions/sanitization.
- **`OrchestrationResult#errorCode`**: stable field to carry canonical error codes to:
  - response sanitization output (when error codes are enabled)
  - intent history persistence

Feature flag:
- `ai.orchestration.result-normalization.enabled` (default: `true`)

### Testing Guidance

Integration tests should assert the **contract** (type/success/errorCode/sanitization invariants) and avoid asserting provider-dependent wrapper types/messages.

When an action handler is missing, tests should validate `errorCode=ACTION_NOT_FOUND` rather than matching exact LLM phrasing.

