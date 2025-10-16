# PLAN — Batch 1 (Tickets: P1.1-A, P1.1-B)

## 0) Summary
- **Goal**: Implement RAG system foundation with Pinecone integration and @AICapable annotation framework
- **Architecture note**: Extend existing AIService with RAG capabilities and create annotation processing system
- **Tickets covered**: P1.1-A (RAG System Foundation), P1.1-B (@AICapable Annotation Framework)
- **Non-goals / out of scope**: Behavioral AI, advanced features, complex UI adaptations

## 1) Backend changes
- **Entities (new/updated)**:
  - `AIKnowledgeBase` (id, entityType, entityId, content, embedding, metadata, createdAt, updatedAt)
  - `AIEmbedding` (id, content, embedding, model, dimensions, createdAt)
  - `AICapableEntity` (id, entityType, entityId, features, configuration, isActive, createdAt)
  - `AIConfiguration` (id, key, value, type, description, isActive, createdAt, updatedAt)
- **Liquibase changesets**:
  - `V001__ai_knowledge_base.yaml` - Create AI knowledge base and embedding tables
- **Endpoints (method → path → purpose)**:
  - `POST /api/ai/index-document` → Index document for RAG
  - `POST /api/ai/semantic-search` → Perform semantic search
  - `GET /api/ai/health` → Check AI system health
  - `GET /api/ai/entities/{type}/search` → Search specific entity type
  - `POST /api/ai/entities/{type}/index` → Index entity for AI
- **Service layer (extend existing AIService)**:
  - `AIService` - Extend with RAG capabilities
  - `AICoreService` - Central AI functionality
  - `AICapableService` - Handle @AICapable entities
  - `AIEmbeddingService` - Manage embeddings
  - `AISearchService` - Handle semantic search
- **Annotation processing**:
  - `@AICapable` - Mark entities for AI capabilities
  - `@AIEmbedding` - Mark fields for embedding generation
  - `@AIKnowledge` - Mark fields for knowledge base inclusion
  - `AICapableProcessor` - Process annotations at runtime
- **DTOs & mappers**:
  - `AIKnowledgeBaseDto`, `AIEmbeddingDto`, `AICapableEntityDto`
  - `SearchRequest`, `SearchResponse`, `IndexRequest`, `IndexResponse`
  - `AIHealthResponse`, `AIConfigurationDto`
- **Dependencies (add to pom.xml)**:
  - Pinecone client for vector database
  - OpenAI embeddings API integration
  - Custom annotation processing
- **Configuration**:
  - Pinecone API key and environment
  - OpenAI embeddings model settings
  - AI feature flags and toggles

## 2) Frontend changes (Next.js App Router)
- **Routes/pages**:
  - `/ai/search` - AI search interface
  - `/ai/health` - AI system health dashboard
  - `/admin/ai/config` - AI configuration management
- **Components (reusing existing)**:
  - **Search**: Extend existing search components from `/components/ui-component/forms/` for AI search
  - **Health Dashboard**: Adapt `LatestCustomerTableCard` from `/components/dashboard/Analytics/` for AI health
  - **Configuration**: Reuse `ValidationWizard` from `/components/forms/forms-wizard/` for AI config
  - **Status Indicators**: Use existing `Chip` component for AI status display
  - **Cards**: Leverage `MainCard`, `SubCard` for consistent layouts
- **AI Hooks (new)**:
  - `useAISearch` - Semantic search functionality
  - `useAIHealth` - AI system health monitoring
  - `useAIConfiguration` - AI configuration management
  - `useAIEmbedding` - Embedding generation and management
- **Data fetching (React Query keys)**:
  - `['ai', 'search']` - Search results
  - `['ai', 'health']` - System health
  - `['ai', 'config']` - Configuration
  - `['ai', 'entities', entityType]` - Entity-specific AI data
- **Forms & validation (RHF + Zod)**:
  - AI search form with validation
  - AI configuration form with settings
  - Entity indexing form for @AICapable entities

## 3) OpenAPI & Types
- **Springdoc annotations**:
  - `@Operation` for all AI endpoints
  - `@ApiResponse` for error codes and responses
  - `@SecurityRequirement` for authentication
- **Regenerate FE types**:
  - Generate TypeScript types from OpenAPI spec
  - Create AI-specific type definitions
  - Update existing types for AI integration

## 4) Tests
- **BE unit/slice**:
  - `AIServiceTest` - RAG functionality
  - `AICapableServiceTest` - Annotation processing
  - `AIEmbeddingServiceTest` - Embedding operations
  - `AISearchServiceTest` - Search functionality
  - `AICoreServiceTest` - Core AI operations
- **BE integration (Testcontainers)**:
  - `AIControllerIntegrationTest` - Full AI flow
  - `RAGIntegrationTest` - RAG system integration
  - `AICapableIntegrationTest` - Annotation processing
- **FE unit**:
  - `useAISearch.test.tsx` - AI search hook
  - `useAIHealth.test.tsx` - Health monitoring
  - `AISearchComponent.test.tsx` - Search component
- **E2E happy-path (Playwright)**:
  - Document indexing → Search → Results display
  - @AICapable entity processing → AI features activation
  - AI configuration → Feature toggling
- **Coverage target**: 80%+ for AI services, 70%+ overall

## 5) Seed & Sample Data (dev only)
- **What to seed**:
  - Sample AI configurations
  - Test documents for indexing
  - @AICapable entity examples
  - Pinecone test data
- **How to run**:
  - `mvn liquibase:update` - Apply migrations
  - `mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"` - Run with seed data

## 6) Acceptance Criteria Matrix
- **P1.1-A**:
  - [ ] AC#1: Documents can be indexed to Pinecone vector database
  - [ ] AC#2: Semantic search returns relevant results with confidence scores
  - [ ] AC#3: AI health endpoint shows system status and metrics
  - [ ] AC#4: RAG service tests achieve ≥80% coverage
  - [ ] AC#5: Search performance is <200ms for typical queries
- **P1.1-B**:
  - [ ] AC#1: @AICapable annotation processes correctly at runtime
  - [ ] AC#2: AI features auto-generate for annotated entities
  - [ ] AC#3: Configuration is stored and retrievable
  - [ ] AC#4: Annotation processor works without compilation errors
  - [ ] AC#5: AI capabilities are togglable per entity

## 7) Risks & Rollback
- **Risks**:
  - Pinecone API integration complexity
  - OpenAI embeddings API rate limits
  - Vector database performance issues
  - Annotation processing overhead
- **Mitigations**:
  - Comprehensive API integration testing
  - Rate limiting and caching implementation
  - Performance monitoring and optimization
  - Gradual annotation processing rollout
- **Rollback plan**:
  - Disable AI features via configuration
  - Revert Pinecone integration
  - Remove annotation processing
  - Restore previous AIService state

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
  ├── src/main/java/com/easy/luxury/
  │   ├── entity/AIKnowledgeBase.java, AIEmbedding.java, AICapableEntity.java
  │   ├── dto/AIKnowledgeBaseDto.java, SearchRequest.java, etc.
  │   ├── mapper/AIKnowledgeBaseMapper.java, etc.
  │   ├── controller/AIController.java, AISearchController.java
  │   ├── service/AICoreService.java, AICapableService.java, AIEmbeddingService.java
  │   ├── annotation/AICapable.java, @AIEmbedding.java, @AIKnowledge.java
  │   ├── processor/AICapableProcessor.java
  │   └── repository/AIKnowledgeBaseRepository.java, etc.
  ├── src/main/resources/
  │   ├── application.yml (updated with AI config)
  │   └── db/changelog/V001__ai_knowledge_base.yaml
  └── pom.xml (updated with Pinecone dependency)
  
  frontend/
  ├── src/
  │   ├── hooks/useAISearch.ts, useAIHealth.ts, useAIConfiguration.ts
  │   ├── components/ai/AISearchComponent.tsx, AIHealthIndicator.tsx
  │   ├── app/(dashboard)/ai/search/page.tsx
  │   ├── app/(dashboard)/ai/health/page.tsx
  │   └── app/(dashboard)/admin/ai/config/page.tsx
  └── package.json (updated with AI dependencies)
  ```
- **Generated diffs**: Will be provided after implementation
- **OpenAPI delta summary**: New AI endpoints and RAG functionality
- **Proposed conventional commits (per ticket)**:
  - `feat(ai): implement RAG system with Pinecone integration [P1.1-A]`
  - `feat(ai): add @AICapable annotation framework [P1.1-B]`