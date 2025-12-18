# PLAN — Batch 2 (Sequence 2) (Tickets: P1.1-C, P1.1-D)

## 0) Summary
- **Goal**: Implement AIEmbeddingService for vector generation and AISearchService for semantic search capabilities
- **Architecture note**: Build embedding and search services with caching and performance optimization
- **Tickets covered**: P1.1-C (Add AIEmbeddingService for Vector Generation), P1.1-D (Create AISearchService for Semantic Search)
- **Non-goals / out of scope**: Vector database integration, RAG system, behavioral AI, advanced features

## 1) Backend changes
- **Services**:
  - `AIEmbeddingService` - Text preprocessing, chunking, vector generation
  - `EmbeddingProcessor` - Text processing and optimization
  - `AISearchService` - Semantic search and vector similarity
  - `VectorSearchService` - Vector database search operations
- **Dependencies**:
  - OpenAI embeddings client
  - Caffeine for caching
  - Spring Cache for performance
- **Configuration**:
  - Embedding model settings
  - Chunking configuration
  - Cache settings and TTL
  - Search parameters and limits
- **DTOs & mappers**:
  - `AIEmbeddingRequest`, `AIEmbeddingResponse`
  - `AISearchRequest`, `AISearchResponse`
  - `EmbeddingChunk`, `SearchResult`
  - MapStruct mappers for DTO conversion
- **Error handling**:
  - Embedding generation failures
  - Search timeout handling
  - Cache miss handling
- **Performance optimization**:
  - Batch processing for embeddings
  - Caching for frequent searches
  - Connection pooling
  - Async processing

## 2) Frontend changes (Next.js App Router)
- **Routes/pages**: None in this batch (AI module is backend-only)
- **Components**: None in this batch
- **Data fetching (React Query keys)**: None in this batch
- **Forms & validation (RHF + Zod)**: None in this batch
- **AI hooks**: None in this batch (will be added in Phase 2)
- **Accessibility notes**: N/A for backend module

## 3) OpenAPI & Types
- **Springdoc annotations**:
  - `@Operation` for embedding and search endpoints
  - `@ApiResponse` for error codes
  - `@Schema` for request/response DTOs
- **Regenerate FE types**: N/A for backend module

## 4) Tests
- **BE unit/slice**:
  - `AIEmbeddingServiceTest` - Embedding generation
  - `EmbeddingProcessorTest` - Text processing
  - `AISearchServiceTest` - Semantic search
  - `VectorSearchServiceTest` - Vector operations
- **BE integration (Testcontainers)**:
  - `EmbeddingIntegrationTest` - Full embedding flow
  - `SearchIntegrationTest` - Full search flow
- **FE unit**: N/A for backend module
- **E2E happy-path (Playwright)**: N/A for backend module
- **Coverage target**: 90%+ for embedding/search services, 80%+ overall

## 5) Seed & Sample Data (dev only)
- **What to seed**:
  - Sample text documents for embedding
  - Test search queries
  - Mock embedding responses
- **How to run**:
  - `mvn clean install` - Build AI module
  - `mvn spring-boot:run` - Run with test configuration

## 6) Acceptance Criteria Matrix
- **P1.1-C**:
  - [ ] AC#1: Text is preprocessed and chunked correctly
  - [ ] AC#2: Embeddings are generated efficiently
  - [ ] AC#3: Caching reduces API calls
  - [ ] AC#4: Performance monitoring tracks metrics
  - [ ] AC#5: Error handling works for embedding failures
- **P1.1-D**:
  - [ ] AC#1: Semantic search returns relevant results
  - [ ] AC#2: Vector similarity search works correctly
  - [ ] AC#3: Result ranking and filtering function properly
  - [ ] AC#4: Search performance is optimized
  - [ ] AC#5: Caching improves response times

## 7) Risks & Rollback
- **Risks**:
  - OpenAI embedding API rate limits
  - Large text processing performance issues
  - Cache memory usage
- **Mitigations**:
  - Implement rate limiting and batching
  - Optimize text processing algorithms
  - Configure appropriate cache sizes
- **Rollback plan**:
  - Disable embedding and search services
  - Revert to basic AI functionality
  - Clean up cache configuration

## 8) Commands to run (print, don't execute yet)
```bash
# Maven module setup
cd ai-infrastructure-spring-boot-starter
mvn clean install
mvn spring-boot:run

# Testing
mvn test
mvn test -Dtest=EmbeddingIntegrationTest
mvn test -Dtest=SearchIntegrationTest

# Integration with Easy Luxury
cd ../backend
mvn clean install
mvn spring-boot:run
```

## 9) Deliverables
- **File tree (added/changed)**:
  ```
  ai-infrastructure-spring-boot-starter/
  ├── src/main/java/com/ai/infrastructure/
  │   ├── core/AIEmbeddingService.java, AISearchService.java
  │   ├── processor/EmbeddingProcessor.java
  │   ├── search/VectorSearchService.java
  │   ├── dto/AIEmbeddingRequest.java, AISearchRequest.java, etc.
  │   └── cache/AICacheConfig.java
  └── src/test/java/com/ai/infrastructure/
      ├── core/AIEmbeddingServiceTest.java, AISearchServiceTest.java
      ├── processor/EmbeddingProcessorTest.java
      └── integration/EmbeddingIntegrationTest.java, SearchIntegrationTest.java
  ```
- **Generated diffs**: Will be provided after implementation
- **OpenAPI delta summary**: Embedding and search service endpoints
- **Proposed conventional commits (per ticket)**:
  - `feat(ai-embeddings): implement AIEmbeddingService for vector generation [P1.1-C]`
  - `feat(ai-search): create AISearchService for semantic search [P1.1-D]`
