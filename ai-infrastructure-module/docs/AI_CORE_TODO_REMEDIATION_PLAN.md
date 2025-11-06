## AI Core Remediation Plan

### Objective
- Consolidate outstanding TODOs, mocked implementations, and hard-coded production gaps in `ai-infrastructure-core`
- Provide a phased remediation path that lands production-ready services without breaking existing consumers

### Current Gaps
- **Vector database integration (implemented)**
  - `PineconeVectorDatabaseService` now performs authenticated REST calls for upsert, query, fetch, delete, and stats endpoints
  - Legacy `VectorDatabase` wiring delegates to the real service; in-memory fallbacks are removed from production profiles
  - `AIInfrastructureAutoConfiguration` honours `ai.vector-db.type` when wiring vector database beans
- **Service beans left unimplemented (addressed)**
  - `aiAutoGeneratorService` now instantiates `DefaultAIAutoGeneratorService`, wiring `AICoreService` and `AIServiceConfig`; regression coverage added in `DefaultAIAutoGeneratorServiceTest`
  - `aiIntelligentCacheService` now provisions `DefaultAIIntelligentCacheService` with feature-flag aware configuration, TTL/tag management, and unit coverage in `DefaultAIIntelligentCacheServiceTest`
- **Mocked or fallback-only runtime paths**
  - `AICapabilityService` produces `mock-vector-id-*` identifiers when `VectorManagementService` is absent instead of failing fast
  - `AISearchService` (`core/AISearchService.java`) and `VectorSearchService` (`search/VectorSearchService.java`) still maintain deprecated in-memory stores meant for demos
  - `MockAIService` (`mock/MockAIService.java`) can be enabled via configuration; production profiles must ensure the flag is disabled
- **Core service provider abstraction (addressed)**
  - `AICoreService` now routes generation through `AIProviderManager` and embeddings through `AIEmbeddingService`; add regression coverage to keep multi-provider support from regressing
- **Validation heuristics**
  - `AIValidationService` (`validation/AIValidationService.java`) seeds `consistencyScore` and `accuracyScore` with `Math.random()` demo values
- **Configuration gaps**
  - `AIConfigurationService.loadFromExternalSources()` is a placeholder, preventing remote config sync
  - `providerConfig` bean (`config/AIInfrastructureAutoConfiguration.java`) hard-codes OpenAI base URL, rate limits, and priorities instead of relying on externalized config
- **Provider coverage**
  - `AnthropicProvider.generateEmbedding()` throws `UnsupportedOperationException`, leaving no controlled fallback path when Anthropic is the configured embedding provider

### Remediation Strategy
- **Phase 1 — Vector platform readiness**
  - ✅ Implement a Pinecone client adapter (auth, upsert, query, delete) in `PineconeVectorDatabaseService`
  - ✅ Replace the in-memory `PineconeVectorDatabase` with a service-backed adapter and remove fallback behaviour from production paths
  - ✅ Update `AIInfrastructureAutoConfiguration.vectorDatabase` to respect `ai.vector-db.type` and align bean lifecycles with the configured backend
  - Add integration tests (e.g., contract tests using Pinecone mock/standalone server) plus smoke scripts for real environments
- **Phase 2 — Complete feature-flagged services**
  - ✅ Deliver `AIAutoGeneratorService` implementation (define interface contracts, integrate with `AICoreService`, cover error handling and observability)
  - ✅ Deliver `AIIntelligentCacheService` with cache warmers, invalidation hooks, and configuration-driven toggles; provide unit tests and configuration docs
  - Harden fallbacks in `AICapabilityService` by requiring `VectorManagementService` (fail startup if missing) or injecting a no-op implementation for non-vector deployments
  - Backfill integration tests to confirm `AICoreService` honors provider selection logic and expose configuration docs for multi-provider deployments
- **Phase 3 — Replace demo logic**
  - Remove deprecated in-memory stores from `AISearchService` and `VectorSearchService`; if backward compatibility is needed, guard behind explicit dev-only flags
  - Compute `consistencyScore` and `accuracyScore` in `AIValidationService` using deterministic analytics (schema rule checks, statistical validation) instead of random numbers; document formulas and add regression tests
  - Ensure production builds disable `MockAIService` by default and guard property overrides via profiling / configuration checks
- **Phase 4 — Configuration and provider hygiene**
  - Implement `AIConfigurationService.loadFromExternalSources()` to support selected config backends (e.g., Consul, AWS AppConfig); expose refresh cadence via properties
  - Refactor the `providerConfig` bean to hydrate from `AIProviderConfig` fields (base URL, rate limits, retry policy) without hard-coded literals; validate inputs during startup
  - For `AnthropicProvider.generateEmbedding()`, either wire a delegate embedding provider or document unsupported status with clear fallback logic so calling code can recover predictably

### Deliverables & Validation
- Code updates across the modules above with comprehensive unit, integration, and (where applicable) contract tests
- Updated configuration samples demonstrating Pinecone credentials, auto-generator settings, intelligent cache tuning, and provider overrides
- Observability dashboards/alerts for the new services (embedding throughput, cache performance, Pinecone error rates)
- Rollout checklist covering migration steps, environment variables, and feature flag flips per phase

### Next Steps
- Confirm remediation ownership per phase and timebox implementation sprints
- Schedule architecture review for the Pinecone integration design
- Align QA plan to cover both Pinecone-enabled and Lucene-only deployments before production rollout
