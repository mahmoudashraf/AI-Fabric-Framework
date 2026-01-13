# PII Separation (Progress)

## Goal

- Make PII capabilities optional and configuration-driven (no hard coupling from core).
- Keep framework integration simple: core works without PII; when enabled, PII plugs in via auto-config.

## Key Decisions

- Keep a small SPI in core: `com.ai.infrastructure.privacy.pii.PIIDetectionService` (interface).
- Move PII runtime (properties + default implementation + pipeline step) into a dedicated module:
  `ai-infrastructure-module/ai-infrastructure-pii`.
- Create PII beans only when `ai.pii-detection.enabled=true`.

## What Changed

- New module: `ai-infrastructure-module/ai-infrastructure-pii`
  - Auto-config: `com.ai.infrastructure.pii.config.PIIAutoConfiguration`
  - Properties moved from core: `com.ai.infrastructure.config.PIIDetectionProperties`
  - Default implementation: `com.ai.infrastructure.privacy.pii.DefaultPIIDetectionService`
  - Pipeline step moved from core and now config-driven: `com.ai.infrastructure.intent.orchestration.pipeline.steps.PIIDetectionStep`
- Core refactor:
  - Replaced `com.ai.infrastructure.privacy.pii.PIIDetectionService` class with an interface (SPI).
  - Removed PII bean wiring and properties registration from `com.ai.infrastructure.config.AIInfrastructureAutoConfiguration`.
  - Decoupled `com.ai.infrastructure.intent.orchestration.pipeline.steps.ResponseSanitizationStep` from `PIIDetectionProperties`.
- Starters / build:
  - Added module to `ai-infrastructure-module/pom.xml`.
  - Added `ai-infrastructure-pii` to `ai-infrastructure-module/ai-fabric-starter/pom.xml`.

## Tests / Verification

- `cd ai-infrastructure-module && mvn verify`
- `cd ai-infrastructure-module && mvn -DskipTests install` (for Real_Apps)
- Real apps:
  - `Real_Apps/sub-management-hub-simple` clean build + startup smoke
  - `Real_Apps/sub-management-hub` clean build + startup smoke

## Remaining TODOs

- Decide/implement “advanced” PII providers:
  - Option A (simple): ship additional modules that provide a `@Primary PIIDetectionService` implementation.
  - Option B (selector): support multiple `PIIDetectionService` beans with property-based selection + fail-fast.
- Update user-facing docs that still reference old locations/behavior (PII in core, pipeline step dependencies).
- Expand Real_Apps documentation per app (use cases + setup scenarios).

