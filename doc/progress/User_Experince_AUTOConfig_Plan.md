# AI Fabric Framework - Developer Experience Enhancement Guide

> **Version:** 1.1.1  
> **Status:** Proposal (DX improvements)  
> **Last Updated:** January 2026  
> **Backward Compatibility:** Not a goal (breaking changes allowed)

## Purpose

This guide proposes changes to improve the **Developer Experience (DX)** when integrating the AI Fabric Framework into Spring Boot applications.

It is intentionally aligned to the repository’s development guidelines:
- `Final_Documentation/Development_Guides/AI_FABRIC_FRAMEWORK_PHILOSOPHY.md`
- `Final_Documentation/Development_Guides/AI_LLM_CODE_GENERATION_GUIDE.md`
- `Final_Documentation/Development_Guides/CODE_REVIEW_PROMPT.md`

## Repository Reality Check (Current State)

### Module layout

This repository is organized as a Maven multi-module build under `ai-infrastructure-module/`:

```
ai-infrastructure-module/
├── pom.xml                                # aggregator (modules), not a runtime “starter”
├── ai-infrastructure-core/                # artifactId: ai-fabric-core
├── ai-infrastructure-indexing/            # artifactId: ai-infrastructure-indexing (optional indexing + scheduling)
├── ai-fabric-starter/                     # artifactId: ai-fabric-starter (convenience starter: core + indexing)
├── ai-infrastructure-web/                 # artifactId: ai-fabric-web
├── ai-infrastructure-rag/
├── ai-infrastructure-relationship-query/
├── ai-infrastructure-behavior/
├── providers/
└── victor-databases/
```

### Auto-configuration mechanism in use

Modules already use Spring Boot 3 auto-configuration via:

- `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

Examples present today:
- `ai-infrastructure-module/ai-infrastructure-core/.../AIInfrastructureAutoConfiguration.java`
- `ai-infrastructure-module/ai-infrastructure-behavior/.../BehaviorAIAutoConfiguration.java`
- `ai-infrastructure-module/ai-infrastructure-web/.../AIWebAutoConfiguration.java`
- `ai-infrastructure-module/ai-infrastructure-rag/.../RAGAutoConfiguration.java`

## Observed DX Problems (Current Codebase)

1. **Implicit scanning requirements**
   - `ai-fabric-core` contains many `@Component` classes (pipeline steps, processors, aspects).
   - `AIInfrastructureAutoConfiguration` currently registers many beans via `@Bean`, but does **not** declare `@ComponentScan` to pick up those `@Component`s automatically.
   - Outcome: consumers may need manual `@ComponentScan("com.ai.infrastructure")`.

2. **JPA entity/repository discovery isn’t guaranteed**
   - Core entities live under `com.ai.infrastructure.entity` and repositories under `com.ai.infrastructure.repository`.
   - Without framework-provided `@EntityScan` / `@EnableJpaRepositories` (or equivalent), consumers can be forced into manual wiring.
   - Behavior module already includes `@EntityScan(...)` and `@ComponentScan(...)`, but core does not.

3. **HTTP provider coupling**
   - Providers should not couple directly to `RestTemplate`.
   - Standardize HTTP concerns behind a small internal abstraction (timeouts, retries, auth, observability) to simplify provider code and testing.

4. **Config surface area is large**
   - There are multiple property classes and YAML presets; some defaults exist as module resources.
   - For a new user, it’s not obvious what the “minimum required config” is for a successful boot.

## Proposal: DX Improvements (No Backward Compatibility Constraint)

### Goal

Enable a clean, low-boilerplate “happy path” integration:

```java
@SpringBootApplication
@EnableAIInfrastructure
public class MyApplication { }
```

The framework should be responsible for scanning and registering:
- framework components
- framework entities + repositories (when applicable)
- optional modules (behavior/rag/web/relationship-query) via properties

### Non-goals

- Preserving legacy integration patterns.
- Supporting multiple competing setup styles indefinitely.

## Proposed Solution Architecture

### 1) Provide an explicit enabling annotation (single entry point)

Add an annotation in core:

- `ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/annotation/EnableAIInfrastructure.java`

It should:
- import a registrar/configuration that performs the necessary scanning
- allow opt-in/opt-out per module (behavior/rag/web/relationship-query) via attributes (optional)

Important: per framework philosophy, this is allowed to be a breaking change if it simplifies usage.

### 2) Make core self-contained for component discovery

Update core auto-configuration to ensure framework `@Component`s are registered without consumer `@ComponentScan`.

Candidate file:
- `ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/config/AIInfrastructureAutoConfiguration.java`

Options:
- Add `@ComponentScan("com.ai.infrastructure")` (broad, simplest)
- Or refactor `@Component` classes into `@Bean` registrations (more explicit, more work)

### 3) Make JPA scanning explicit and predictable

If the core module is intended to bring JPA entities/repositories:
- add `@EntityScan("com.ai.infrastructure.entity")`
- add `@EnableJpaRepositories("com.ai.infrastructure.repository")`

Candidate file:
- `ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/config/AIInfrastructureAutoConfiguration.java`

Alternatively (more modular):
- separate persistence adapters into a dedicated module and make it optional.
  - Note: this would be a bigger refactor but reduces mandatory JPA footprint.

### 4) Introduce an HTTP abstraction for providers (port/adapter)

Introduce a minimal interface in core (or a small new module) to decouple providers from `RestTemplate`.

Proposed shape:

```java
public interface HttpClient {
  <T> T postJson(String url, Object body, Class<T> responseType);
}
```

Then:
- keep a default implementation backed by Spring (could still use `RestTemplate` internally)
- move provider code to depend on `HttpClient` instead of creating/managing `RestTemplate`
- centralize timeouts/retries/logging

**Status note:** A `HttpClient` abstraction + factory already exists in `ai-fabric-core` and providers should depend on it (not raw `RestTemplate`).

### 5) Provide a “minimum config” quickstart that matches current modules

Document and keep working a minimal configuration that boots the framework.

Today, providers and vector databases are separate modules under:
- `ai-infrastructure-module/providers/`
- `ai-infrastructure-module/victor-databases/`

The guide should present:
- “fast local” option (e.g., ONNX + Lucene or Memory vector DB)
- “cloud” option (OpenAI/Azure + Qdrant/Pinecone/etc.)

## Implementation Roadmap (Suggested Phases)

### Phase 1 (Low-risk, immediate DX win)

- Add component scanning to core auto-configuration, or replace internal `@Component` usage with explicit `@Bean` creation.
- Add a single “Getting Started” page that references *existing* module artifacts.

### Phase 2 (Medium-risk, improves predictability)

- Add explicit `@EntityScan`/`@EnableJpaRepositories` in core (or split persistence module).
- Ensure each optional module’s auto-configuration follows a consistent enable/disable convention via properties.

### Phase 3 (Medium/high-risk, architectural cleanup)

- Introduce `HttpClient` abstraction and migrate providers away from direct `RestTemplate`.
- Standardize provider configuration validation (fail-fast, fail-closed).

## Validation Criteria (What “Done” Means)

1. A fresh Spring Boot app can include the framework and start without manual `@ComponentScan`.
2. JPA entities/repositories required by enabled modules work without manual `@EntityScan`/`@EnableJpaRepositories` (or are clearly separated into optional persistence modules).
3. Providers do not instantiate their own HTTP client; HTTP concerns are centralized.
4. Configuration failures are explicit and fail-fast (aligned with framework security posture).
