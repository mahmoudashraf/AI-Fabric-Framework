# PLAN — Batch 2 (Sequence 6) (Tickets: P2.1-C, P2.1-D)

## 0) Summary
- **Goal**: Implement AIFacade and AI endpoints for business logic integration
- **Architecture note**: Create comprehensive AI facade and REST endpoints for AI operations
- **Tickets covered**: P2.1-C (Implement AIFacade), P2.1-D (Create AI Endpoints)
- **Non-goals / out of scope**: Domain-specific AI services, advanced AI features, frontend integration

## 1) Backend changes
- **Controllers**:
  - `AIController` - Main AI operations endpoint
  - `AIHealthController` - AI health and status endpoints
  - `AIConfigurationController` - AI configuration management
- **Services**:
  - Enhance `AIFacade` with comprehensive AI operations
  - `AIEndpointService` - Endpoint-specific AI logic
  - `AIMonitoringService` - AI service monitoring
- **DTOs & mappers**:
  - `AIGenerationRequest/Response` - AI generation DTOs
  - `AIEmbeddingRequest/Response` - Embedding DTOs
  - `AISearchRequest/Response` - Search DTOs
  - `RAGRequest/Response` - RAG operation DTOs
  - MapStruct mappers for all AI DTOs
- **Error handling**:
  - AI service error handling
  - Validation error responses
  - Rate limiting error handling

## 2) Frontend changes (Next.js App Router)
- **Routes/pages**: None in this batch
- **Components**: None in this batch
- **Data fetching (React Query keys)**: None in this batch
- **Forms & validation (RHF + Zod)**: None in this batch
- **AI hooks**: None in this batch (will be added in later batches)
- **Accessibility notes**: N/A

## 3) OpenAPI & Types
- **Springdoc annotations**:
  - `@Operation` for all AI endpoints
  - `@ApiResponse` for success and error responses
  - `@Tag` for API grouping
  - `@Schema` for request/response documentation
- **Regenerate FE types**: N/A for this batch

## 4) Tests
- **BE unit/slice**:
  - `AIControllerTest` - Controller unit tests
  - `AIHealthControllerTest` - Health endpoint tests
  - `AIConfigurationControllerTest` - Configuration endpoint tests
  - `AIEndpointServiceTest` - Service layer tests
- **BE integration (Testcontainers)**:
  - `AIEndpointIntegrationTest` - Full endpoint integration
  - `AIMonitoringIntegrationTest` - Monitoring integration
- **FE unit**: N/A
- **E2E happy-path (Playwright)**: N/A
- **Coverage target**: 85%+ for AI endpoints

## 5) Seed & Sample Data (dev only)
- **What to seed**:
  - Sample AI requests for testing
  - Test AI responses
  - Mock AI service data
- **How to run**:
  - `mvn liquibase:update`
  - `mvn spring-boot:run`

## 6) Acceptance Criteria Matrix
- **P2.1-C**:
  - [ ] AC#1: AIFacade provides comprehensive AI operations
  - [ ] AC#2: All AI service methods are accessible through facade
  - [ ] AC#3: Error handling is properly implemented
  - [ ] AC#4: Performance is optimized
  - [ ] AC#5: Logging and monitoring are integrated
- **P2.1-D**:
  - [ ] AC#1: AI endpoints are properly documented
  - [ ] AC#2: All endpoints return appropriate responses
  - [ ] AC#3: Error handling works correctly
  - [ ] AC#4: Rate limiting is implemented
  - [ ] AC#5: Health checks are functional

## 7) Risks & Rollback
- **Risks**:
  - Endpoint performance issues
  - AI service integration problems
  - Rate limiting configuration
- **Mitigations**:
  - Implement proper caching
  - Test AI service integration thoroughly
  - Configure rate limiting appropriately
- **Rollback plan**:
  - Remove AI endpoints
  - Revert AIFacade changes
  - Clean up AI-specific code

## 8) Commands to run (print, don't execute yet)
```bash
# Backend setup
cd backend
mvn clean install
mvn spring-boot:run

# Frontend setup
cd frontend
npm install
npm run dev

# Testing
mvn test
npm run test
npm run test:e2e
```

## 9) Deliverables
- **File tree (added/changed)**:
  ```
  backend/
  ├── src/main/java/com/easyluxury/ai/
  │   ├── controller/
  │   │   ├── AIController.java
  │   │   ├── AIHealthController.java
  │   │   └── AIConfigurationController.java
  │   ├── service/
  │   │   ├── AIEndpointService.java
  │   │   └── AIMonitoringService.java
  │   ├── facade/AIFacade.java (enhanced)
  │   └── dto/
  │       ├── AIGenerationRequest.java
  │       ├── AIGenerationResponse.java
  │       ├── AIEmbeddingRequest.java
  │       ├── AIEmbeddingResponse.java
  │       ├── AISearchRequest.java
  │       ├── AISearchResponse.java
  │       ├── RAGRequest.java
  │       └── RAGResponse.java
  └── src/test/java/com/easyluxury/ai/
      ├── controller/
      │   ├── AIControllerTest.java
      │   ├── AIHealthControllerTest.java
      │   └── AIConfigurationControllerTest.java
      └── service/
          ├── AIEndpointServiceTest.java
          └── AIMonitoringServiceTest.java
  ```
- **Generated diffs**: Will be provided after implementation
- **OpenAPI delta summary**: AI operation endpoints
- **Proposed conventional commits (per ticket)**:
  - `feat(ai-facade): implement comprehensive AIFacade for business logic [P2.1-C]`
  - `feat(ai-endpoints): create AI REST endpoints for operations [P2.1-D]`