# PLAN — Batch 1 (Sequence 1) (Tickets: P1.1-A, P1.1-B)

## 0) Summary
- **Goal**: Create standalone ai-infrastructure-spring-boot-starter module with core AI capabilities and OpenAI integration
- **Architecture note**: Build as separate Maven module with Spring Boot auto-configuration for easy integration
- **Tickets covered**: P1.1-A (Create Maven Module Structure), P1.1-B (Implement AICoreService with OpenAI Integration)
- **Non-goals / out of scope**: Vector database integration, RAG system, behavioral AI, advanced features

## 1) Backend changes
- **Maven module structure**:
  - Create `ai-infrastructure-spring-boot-starter/` directory
  - `pom.xml` with Spring Boot starter configuration
  - Package structure: `com.ai.infrastructure.*`
  - Auto-configuration setup with `spring.factories`
- **Dependencies**:
  - Spring Boot 3.x, Spring AI, OpenAI Java client
  - Jackson, MapStruct, Lombok, Jakarta Validation
  - Spring Boot Test, Testcontainers for testing
- **Configuration**:
  - `AIProviderConfig` with OpenAI settings
  - `AIInfrastructureAutoConfiguration` for auto-configuration
  - `application-ai.yml` with default settings
- **Services**:
  - `AICoreService` - Central AI service with OpenAI integration
  - `AIEmbeddingService` - Embedding generation service
  - `AISearchService` - Semantic search service
- **DTOs & mappers**:
  - `AIGenerationRequest`, `AIGenerationResponse`
  - `AIEmbeddingRequest`, `AIEmbeddingResponse`
  - `AISearchRequest`, `AISearchResponse`
  - MapStruct mappers for DTO conversion
- **Auto-configuration**:
  - `@ConditionalOnClass` for AI services
  - `@ConditionalOnProperty` for feature toggles
  - `@EnableConfigurationProperties` for AI settings
- **Error handling**:
  - `AIException` hierarchy
  - `AIErrorHandler` with proper error responses
  - Rate limiting and retry logic

## 2) Frontend changes (Next.js App Router)
- **Routes/pages**: None in this batch (AI module is backend-only)
- **Components**: None in this batch
- **Data fetching (React Query keys)**: None in this batch
- **Forms & validation (RHF + Zod)**: None in this batch
- **AI hooks**: None in this batch (will be added in Phase 2)
- **Accessibility notes**: N/A for backend module

## 3) OpenAPI & Types
- **Springdoc annotations**:
  - `@Operation` for AI service endpoints
  - `@ApiResponse` for error codes
  - `@Schema` for request/response DTOs
- **Regenerate FE types**: N/A for backend module

## 4) Tests
- **BE unit/slice**:
  - `AICoreServiceTest` - Core AI functionality
  - `AIEmbeddingServiceTest` - Embedding generation
  - `AISearchServiceTest` - Semantic search
  - `AIProviderConfigTest` - Configuration validation
  - `AIInfrastructureAutoConfigurationTest` - Auto-configuration
- **BE integration (Testcontainers)**:
  - `AIIntegrationTest` - Full AI service integration
  - `OpenAIIntegrationTest` - OpenAI API integration
- **FE unit**: N/A for backend module
- **E2E happy-path (Playwright)**: N/A for backend module
- **Coverage target**: 90%+ for AI services, 80%+ overall

## 5) Seed & Sample Data (dev only)
- **What to seed**:
  - Sample AI configuration for testing
  - Mock OpenAI responses for integration tests
- **How to run**:
  - `mvn clean install` - Build AI module
  - `mvn spring-boot:run` - Run with test configuration

## 6) Acceptance Criteria Matrix
- **P1.1-A**:
  - [ ] AC#1: Maven module structure is created with proper package organization
  - [ ] AC#2: Dependencies are properly configured in pom.xml
  - [ ] AC#3: Spring Boot starter auto-configuration works
  - [ ] AC#4: Module can be built and packaged successfully
  - [ ] AC#5: Basic package structure is established
- **P1.1-B**:
  - [ ] AC#1: AICoreService generates content successfully with OpenAI
  - [ ] AC#2: Embeddings are created and stored correctly
  - [ ] AC#3: Semantic search returns relevant results
  - [ ] AC#4: Error handling and retry logic works
  - [ ] AC#5: Rate limiting prevents API abuse

## 7) Risks & Rollback
- **Risks**:
  - OpenAI API rate limits and costs
  - Maven module configuration complexity
  - Spring Boot auto-configuration issues
- **Mitigations**:
  - Implement caching and rate limiting
  - Comprehensive Maven configuration testing
  - Thorough auto-configuration testing
- **Rollback plan**:
  - Remove AI module dependency
  - Revert to previous configuration
  - Clean up Maven module

## 8) Commands to run (print, don't execute yet)
```bash
# Maven module setup
cd ai-infrastructure-spring-boot-starter
mvn clean install
mvn spring-boot:run

# Testing
mvn test
mvn test -Dtest=AIIntegrationTest

# Integration with Easy Luxury
cd ../backend
mvn clean install
mvn spring-boot:run
```

## 9) Deliverables
- **File tree (added/changed)**:
  ```
  ai-infrastructure-spring-boot-starter/
  ├── pom.xml
  ├── src/main/java/com/ai/infrastructure/
  │   ├── annotation/AICapable.java
  │   ├── core/AICoreService.java, AIEmbeddingService.java, AISearchService.java
  │   ├── config/AIProviderConfig.java, AIInfrastructureAutoConfiguration.java
  │   ├── dto/AIGenerationRequest.java, AIGenerationResponse.java, etc.
  │   ├── exception/AIException.java, AIErrorHandler.java
  │   └── processor/AICapableProcessor.java
  ├── src/main/resources/
  │   ├── META-INF/spring.factories
  │   └── application-ai.yml
  └── src/test/java/com/ai/infrastructure/
      ├── core/AICoreServiceTest.java
      ├── config/AIProviderConfigTest.java
      └── integration/AIIntegrationTest.java
  ```
- **Generated diffs**: Will be provided after implementation
- **OpenAPI delta summary**: AI service endpoints for content generation and search
- **Proposed conventional commits (per ticket)**:
  - `feat(ai-module): create Maven module structure with Spring Boot starter [P1.1-A]`
  - `feat(ai-core): implement AICoreService with OpenAI integration [P1.1-B]`
