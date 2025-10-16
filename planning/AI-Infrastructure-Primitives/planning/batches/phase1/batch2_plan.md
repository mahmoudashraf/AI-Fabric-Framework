# PLAN — Batch 2 (Tickets: P1.1-C, P1.1-D)

## 0) Summary
- **Goal**: Implement AI Core Service with OpenAI integration and auto-generated RAG services
- **Architecture note**: Extend AI Core Service with comprehensive OpenAI integration and create dynamic service generation
- **Tickets covered**: P1.1-C (AI Core Service), P1.1-D (Auto-Generated RAG Services)
- **Non-goals / out of scope**: Behavioral AI, advanced UI features, complex automation

## 1) Backend changes
- **Facade layer (controllers depend on facades, not services)**:
  - Pattern: Controllers → Facades → Services → Repositories
  - Facades:
    - `AIGenerationFacade` — aggregates `AIGenerationService`, `AIEmbeddingService` for AI generation orchestration
    - `EntityAIFacade` — coordinates `AIServiceRegistry`, `AICoreService` for entity-specific AI features
  - Services (new/updated):
    - `AICoreService` - Central AI functionality with OpenAI integration
    - `AIEmbeddingService` - Embedding generation and management
    - `AISearchService` - Semantic search operations
    - `AIGenerationService` - Content generation with OpenAI
    - `AIServiceRegistry` - Dynamic service registration
- **Auto-generated services**:
  - `ProductRAGService` - Auto-generated for Product entities
  - `UserRAGService` - Auto-generated for User entities
  - `OrderRAGService` - Auto-generated for Order entities
  - Dynamic service generation based on @AICapable entities
- **Endpoints (method → path → purpose)**:
  - `POST /api/ai/generate-response` → Generate AI responses
  - `POST /api/ai/create-embedding` → Create embeddings
  - `GET /api/ai/models` → List available AI models
  - `POST /api/ai/entities/{type}/search` → Entity-specific search
  - `POST /api/ai/entities/{type}/recommend` → Entity recommendations
  - `POST /api/ai/entities/{type}/analyze` → Entity analysis
- **OpenAI integration**:
  - GPT-4 for content generation
  - text-embedding-3-small for embeddings
  - Rate limiting and error handling
  - Model selection and configuration
- **Dynamic service generation**:
  - Runtime service creation for @AICapable entities
  - Automatic endpoint generation
  - Service discovery and registration
  - Configuration management per entity type
- **DTOs & mappers**:
  - `GenerationRequest`, `GenerationResponse`
  - `EmbeddingRequest`, `EmbeddingResponse`
  - `ModelInfo`, `ModelListResponse`
  - `EntitySearchRequest`, `EntityRecommendationRequest`
- **Configuration**:
  - OpenAI API key management
  - Model selection and fallbacks
  - Rate limiting configuration
  - Service generation settings

## 2) Frontend changes (Next.js App Router)
- **Routes/pages**:
  - `/ai/generate` - AI content generation
  - `/ai/entities/{type}` - Entity-specific AI features
  - `/ai/models` - AI model management
- **Components (reusing existing)**:
  - **Generation**: Extend existing form components for AI generation
  - **Entity Management**: Adapt table components for entity AI features
  - **Model Selection**: Reuse dropdown components for model selection
  - **Status Display**: Use existing status components for AI operations
- **AI Hooks (new)**:
  - `useAIGeneration` - Content generation functionality
  - `useAIEmbedding` - Embedding operations
  - `useAIModels` - Model management
  - `useEntityAISearch` - Entity-specific search
  - `useEntityAIRecommendations` - Entity recommendations
  - `useEntityAIAnalysis` - Entity analysis
- **Data fetching (React Query keys)**:
  - `['ai', 'generate']` - Generation requests
  - `['ai', 'embedding']` - Embedding operations
  - `['ai', 'models']` - Available models
  - `['ai', 'entities', type, 'search']` - Entity search
  - `['ai', 'entities', type, 'recommend']` - Entity recommendations
- **Forms & validation (RHF + Zod)**:
  - AI generation form with prompt validation
  - Entity search form with filters
  - Model selection form with configuration

## 3) OpenAPI & Types
- **Springdoc annotations**:
  - `@Operation` for all AI generation endpoints
  - `@ApiResponse` for generation responses
  - `@SecurityRequirement` for authentication
- **Regenerate FE types**:
  - Generate TypeScript types for AI generation
  - Create entity-specific AI types
  - Update existing types for AI integration

## 4) Tests
- **BE unit/slice**:
  - `AICoreServiceTest` - Core AI functionality
  - `AIEmbeddingServiceTest` - Embedding operations
  - `AIGenerationServiceTest` - Content generation
  - `AIServiceRegistryTest` - Service registration
  - `ProductRAGServiceTest` - Auto-generated service
- **BE integration (Testcontainers)**:
  - `AIGenerationIntegrationTest` - Full generation flow
  - `EntityAIServicesIntegrationTest` - Entity AI features
  - `OpenAIIntegrationTest` - OpenAI API integration
- **FE unit**:
  - `useAIGeneration.test.tsx` - Generation hook
  - `useEntityAISearch.test.tsx` - Entity search hook
  - `AIGenerationComponent.test.tsx` - Generation component
- **E2E happy-path (Playwright)**:
  - AI content generation → Display results
  - Entity search → Filter and display
  - Model selection → Apply configuration
- **Coverage target**: 80%+ for AI services, 70%+ overall

## 5) Seed & Sample Data (dev only)
- **What to seed**:
  - Sample AI generation prompts
  - Test entities for AI features
  - Model configurations
  - Service generation examples
- **How to run**:
  - `mvn liquibase:update` - Apply migrations
  - `mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"` - Run with seed data

## 6) Acceptance Criteria Matrix
- **P1.1-C**:
  - [ ] AC#1: AI responses are generated successfully using OpenAI
  - [ ] AC#2: Embeddings are created and stored correctly
  - [ ] AC#3: Rate limiting prevents API abuse
  - [ ] AC#4: Error handling works correctly for API failures
  - [ ] AC#5: Model selection and configuration work
- **P1.1-D**:
  - [ ] AC#1: RAG services are auto-generated for @AICapable entities
  - [ ] AC#2: Search endpoints work for each entity type
  - [ ] AC#3: Recommendations are relevant and accurate
  - [ ] AC#4: Analysis provides meaningful insights
  - [ ] AC#5: Service generation is configurable

## 7) Risks & Rollback
- **Risks**:
  - OpenAI API rate limits and costs
  - Dynamic service generation complexity
  - Performance impact of AI operations
  - Service discovery and registration issues
- **Mitigations**:
  - Implement comprehensive rate limiting
  - Cache AI responses and embeddings
  - Optimize service generation performance
  - Implement fallback mechanisms
- **Rollback plan**:
  - Disable AI generation features
  - Revert to static service definitions
  - Remove dynamic service generation
  - Restore previous AI service state

## 8) Commands to run (print, don't execute yet)
```bash
# Backend setup
cd backend
mvn clean install
mvn liquibase:update
mvn spring-boot:run

# Frontend setup
cd frontend
npm install
npm run dev

# Testing
mvn test
npm run test
npm run test:e2e

# Docker
docker compose up -d
```

## 9) Deliverables
- **File tree (added/changed)**:
  ```
  backend/
  ├── src/main/java/com/easyluxury/
  │   ├── service/AICoreService.java, AIGenerationService.java, AIServiceRegistry.java
  │   ├── service/rag/ProductRAGService.java, UserRAGService.java, OrderRAGService.java
  │   ├── controller/AIGenerationController.java, EntityAIController.java
  │   ├── dto/GenerationRequest.java, EmbeddingRequest.java, ModelInfo.java
  │   └── config/AIServiceConfig.java, OpenAIConfig.java
  ├── src/main/resources/
  │   └── application.yml (updated with OpenAI config)
  └── pom.xml (updated with OpenAI dependencies)
  
  frontend/
  ├── src/
  │   ├── hooks/useAIGeneration.ts, useEntityAISearch.ts, useAIModels.ts
  │   ├── components/ai/AIGenerationComponent.tsx, EntityAIFeatures.tsx
  │   ├── app/(dashboard)/ai/generate/page.tsx
  │   └── app/(dashboard)/ai/entities/[type]/page.tsx
  └── package.json (updated with AI generation dependencies)
  ```
- **Generated diffs**: Will be provided after implementation
- **OpenAPI delta summary**: New AI generation and entity-specific endpoints
- **Proposed conventional commits (per ticket)**:
  - `feat(ai): implement AI Core Service with OpenAI integration [P1.1-C]`
  - `feat(ai): add auto-generated RAG services for @AICapable entities [P1.1-D]`