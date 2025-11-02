# Optional ONNX Starter Package Plan

## Objective
Create an opt-in “ONNX starter” so the core AI infrastructure module remains lightweight while still offering ready-to-use local embeddings for teams (and our integration tests) that prefer ONNX.

## Outcomes
- Core artifact (`ai-infrastructure-core`) stays lean and agnostic about bundled models.
- A new optional artifact provides the ONNX runtime + default MiniLM model + wiring for the `ONNXEmbeddingProvider`.
- Documentation and build pipelines reflect the new packaging pattern.

## Scope
- ✅ Produce build changes and docs for a separate starter artifact.
- ✅ Ensure integration tests and local dev can depend on the starter seamlessly.
- ✅ Provide migration notes for teams already using the core module.
- ❌ Do not change core embedding provider logic or default DI behaviour (remains pluggable).

## Workstream Overview

### 1. Packaging & Dependencies
- Create a new Maven module under `ai-infrastructure-module` (e.g. `ai-infrastructure-onnx-starter`).
- Move ONNX runtime, tokenizer, and MiniLM model assets to the starter module resources.
- Configure Maven to publish the module (attach proper classifier if needed for models).
- Ensure the starter exposes a Spring auto-configuration (or simple configuration class) that registers `ONNXEmbeddingProvider` when on classpath.
- Validate the module shade/filters so the model files land under `META-INF/resources` or a predictable path.

### 2. Core Module Adjustments
- Confirm the core module keeps provider interfaces and no longer bundles heavy model binaries.
- Introduce detection logic (if necessary) that sets ONNX as default only when starter is present; otherwise fall back to existing behaviour.
- Update integration tests to depend on the starter module instead of local copies of the model.

### 3. Integration Tests & Pipelines
- Update `integration-tests/pom.xml` to include the starter dependency for IT execution.
- Verify CI caches or downloads the ONNX model through Maven (remove custom setup scripts if any).
- Run regression suite to ensure test isolation still works (unique Lucene paths etc.).

### 4. Developer Experience
- Add documentation:
  - How to enable ONNX via the starter (`pom.xml` snippet).
  - Notes on disk footprint and optional usage.
  - Instructions for overriding the model path if teams want to swap models.
- Provide migration guide for existing users to adopt the starter (e.g. remove manually downloaded models).

### 5. Release & Distribution
- Update release pipeline to publish both core and starter artifacts.
- Tag the starter with same version as core for consistency.
- Sanity check that the model’s licence allows redistribution (include NOTICE if required).

## Timeline
- **Day 1:** Set up new module, relocate model assets, adjust core wiring.
- **Day 2:** Update integration tests, documentation, and run full validation.
- **Day 3:** Final review, release pipeline updates, and publish artifacts.

## Risks & Mitigations
- **Model size impact on CI:** Cache Maven repository in CI to avoid repeated downloads.
- **Platform compatibility:** Document that ONNX runtime may need native binaries; encourage consumers to include appropriate classifier artifacts.
- **Version drift:** Keep starter pinned to a tested model version; note version in release notes.

## Success Criteria
- Integration tests run without manual ONNX setup.
- Consumers can opt-in with a single dependency addition.
- Core artifact size remains minimal, and downstream teams unaffected unless they choose to adopt the starter.
