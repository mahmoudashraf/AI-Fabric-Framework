# PLAN — Batch 3 (Sequence 3) (Tickets: P1.1-E, P1.1-F)

## 0) Summary
- **Goal**: Implement @AICapable annotation framework and build RAG system with vector database abstraction
- **Architecture note**: Create annotation-driven AI features with provider-agnostic vector database support
- **Tickets covered**: P1.1-E (Implement @AICapable Annotation Framework), P1.1-F (Build RAG System with Vector Database Abstraction)
- **Non-goals / out of scope**: Behavioral AI, advanced features, Easy Luxury integration

## 1) Backend changes
- **Annotations**:
  - `@AICapable` - Mark entities as AI-capable
  - `@AIEmbedding` - Mark fields for embedding generation
  - `@AIKnowledge` - Mark fields for knowledge base
  - `@AISmartValidation` - Mark fields for AI validation
- **Services**:
  - `AICapableProcessor` - Process @AICapable annotations
  - `RAGService` - RAG operations and context building
  - `VectorDatabaseService` - Vector database abstraction
  - `EmbeddingProcessor` - Process entity embeddings
- **Dependencies**:
  - Pinecone client for vector database
  - Spring AOP for annotation processing
  - Jackson for JSON processing
- **Configuration**:
  - Vector database settings
  - RAG configuration
  - Annotation processing settings
- **DTOs & mappers**:
  - `RAGRequest`, `RAGResponse`
  - `VectorSearchRequest`, `VectorSearchResponse`
  - `AICapableEntity`, `EmbeddingData`
  - MapStruct mappers for DTO conversion
- **Error handling**:
  - Vector database connection failures
  - RAG processing errors
  - Annotation processing failures
- **Vector database abstraction**:
  - `VectorDatabase` interface
  - `PineconeVectorDatabase` implementation
  - Future: `ChromaVectorDatabase`, `WeaviateVectorDatabase`

## 2) Frontend changes (Next.js App Router)
- **Routes/pages**: None in this batch (AI module is backend-only)
- **Components**: None in this batch
- **Data fetching (React Query keys)**: None in this batch
- **Forms & validation (RHF + Zod)**: None in this batch
- **AI hooks**: None in this batch (will be added in Phase 2)
- **Accessibility notes**: N/A for backend module

## 3) OpenAPI & Types
- **Springdoc annotations**:
  - `@Operation` for RAG and vector database endpoints
  - `@ApiResponse` for error codes
  - `@Schema` for request/response DTOs
- **Regenerate FE types**: N/A for backend module

## 4) Tests
- **BE unit/slice**:
  - `AICapableProcessorTest` - Annotation processing
  - `RAGServiceTest` - RAG operations
  - `VectorDatabaseServiceTest` - Vector database operations
  - `EmbeddingProcessorTest` - Entity embedding processing
- **BE integration (Testcontainers)**:
  - `RAGIntegrationTest` - Full RAG flow
  - `VectorDatabaseIntegrationTest` - Vector database integration
- **FE unit**: N/A for backend module
- **E2E happy-path (Playwright)**: N/A for backend module
- **Coverage target**: 90%+ for RAG and annotation services, 80%+ overall

## 5) Seed & Sample Data (dev only)
- **What to seed**:
  - Sample entities with @AICapable annotations
  - Test RAG queries
  - Mock vector database responses
- **How to run**:
  - `mvn clean install` - Build AI module
  - `mvn spring-boot:run` - Run with test configuration

## 6) Acceptance Criteria Matrix
- **P1.1-E**:
  - [ ] AC#1: @AICapable annotation processes correctly
  - [ ] AC#2: AI features auto-generate for annotated entities
  - [ ] AC#3: Annotation processor works at compile time
  - [ ] AC#4: Feature detection and activation works
  - [ ] AC#5: Configuration is stored and retrievable
- **P1.1-F**:
  - [ ] AC#1: RAG system works with any vector database
  - [ ] AC#2: Automatic indexing functions correctly
  - [ ] AC#3: RAG queries return relevant results
  - [ ] AC#4: Context building and ranking work properly
  - [ ] AC#5: Provider abstraction allows easy switching

## 7) Risks & Rollback
- **Risks**:
  - Vector database connection issues
  - RAG processing performance problems
  - Annotation processing complexity
- **Mitigations**:
  - Implement connection pooling and retry logic
  - Optimize RAG processing algorithms
  - Simplify annotation processing logic
- **Rollback plan**:
  - Disable RAG and annotation features
  - Revert to basic AI functionality
  - Clean up vector database configuration

## 8) Commands to run (print, don't execute yet)
```bash
# Maven module setup
cd ai-infrastructure-spring-boot-starter
mvn clean install
mvn spring-boot:run

# Testing
mvn test
mvn test -Dtest=RAGIntegrationTest
mvn test -Dtest=VectorDatabaseIntegrationTest

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
  │   ├── annotation/AICapable.java, AIEmbedding.java, AIKnowledge.java, AISmartValidation.java
  │   ├── rag/RAGService.java, VectorDatabaseService.java
  │   ├── processor/AICapableProcessor.java, EmbeddingProcessor.java
  │   ├── vector/PineconeVectorDatabase.java, VectorDatabase.java
  │   ├── dto/RAGRequest.java, VectorSearchRequest.java, etc.
  │   └── config/RAGConfig.java, VectorDatabaseConfig.java
  └── src/test/java/com/ai/infrastructure/
      ├── annotation/AICapableProcessorTest.java
      ├── rag/RAGServiceTest.java, VectorDatabaseServiceTest.java
      └── integration/RAGIntegrationTest.java, VectorDatabaseIntegrationTest.java
  ```
- **Generated diffs**: Will be provided after implementation
- **OpenAPI delta summary**: RAG and vector database service endpoints
- **Proposed conventional commits (per ticket)**:
  - `feat(ai-annotations): implement @AICapable annotation framework [P1.1-E]`
  - `feat(ai-rag): build RAG system with vector database abstraction [P1.1-F]`
