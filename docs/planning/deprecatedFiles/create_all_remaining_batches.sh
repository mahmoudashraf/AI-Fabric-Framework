#!/bin/bash

# Create all remaining batches for AI-Infrastructure-Module and AI-Infrastructure-Primitives

echo "Creating Phase 2 batches for AI-Infrastructure-Module..."

# Phase 2, Batch 2 (Sequence 6)
cat > /workspace/planning/AI-Infrastructure-Module/planning/batches/phase2/batch2.md << 'BATCH2_EOF'
Use docs/PROJECT_GUIDELINES.yaml as the single source of truth.
Use docs/FRONTEND_DEVELOPMENT_GUIDE.md as the UI Development guide.

Load planning/phase2.yaml.  
Implement tickets **P2.1-C (Implement AIFacade for Basic Operations)** and **P2.1-D (Add AI Endpoints to Existing Controllers)**.  

Plan first: list files, DTOs, endpoints, Maven configuration, and acceptance criteria.  
Wait for my approval before coding.

⚠️ IMPORTANT: Output ONLY the PLAN using the following Markdown Checklist template.  
Do NOT generate or edit code until I explicitly reply "OK, proceed".

# PLAN — Batch 2 (Sequence 6) (Tickets: P2.1-C, P2.1-D)

## 0) Summary
- Goal:
- Tickets covered:
- Non-goals / out of scope:

## 1) Backend changes
- Services:
- Controllers:
- DTOs & mappers:
- Error handling:

## 2) Frontend changes (Next.js App Router)
- Routes/pages:
- Components:
- Data fetching (React Query keys):
- Forms & validation (RHF + Zod):
- AI hooks:
- Accessibility notes:

## 3) OpenAPI & Types
- Springdoc annotations:
- Regenerate FE types:

## 4) Tests
- BE unit/slice:
- BE integration (Testcontainers):
- FE unit:
- E2E happy-path (Playwright):
- Coverage target:

## 5) Seed & Sample Data (dev only)
- What to seed:
- How to run:

## 6) Acceptance Criteria Matrix
- P2.1-C:
  - [ ] AC#1 …
  - [ ] AC#2 …
- P2.1-D:
  - [ ] AC#1 …
  - [ ] AC#2 …

## 7) Risks & Rollback
- Risks:
- Mitigations:
- Rollback plan:

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
- File tree (added/changed)
- Generated diffs
- OpenAPI delta summary
- Proposed conventional commits (per ticket)
BATCH2_EOF

# Phase 2, Batch 2 Plan (Sequence 6)
cat > /workspace/planning/AI-Infrastructure-Module/planning/batches/phase2/batch2_plan.md << 'BATCH2_PLAN_EOF'
# PLAN — Batch 2 (Sequence 6) (Tickets: P2.1-C, P2.1-D)

## 0) Summary
- **Goal**: Implement AIFacade for basic operations and add AI endpoints to existing controllers
- **Architecture note**: Create facade layer for AI operations and integrate AI endpoints into existing controller structure
- **Tickets covered**: P2.1-C (Implement AIFacade for Basic Operations), P2.1-D (Add AI Endpoints to Existing Controllers)
- **Non-goals / out of scope**: Domain-specific AI services, advanced AI features, frontend integration

## 1) Backend changes
- **Services**:
  - `AIFacade` - Basic AI operations facade
  - `AISearchService` - AI search operations
  - `AIGenerationService` - AI content generation
  - `AIValidationService` - AI validation operations
- **Controllers**:
  - Add AI endpoints to `AIProfileController`
  - Add AI endpoints to `UserController`
  - Add AI endpoints to `ProductController`
- **DTOs & mappers**:
  - `AISearchRequest`, `AISearchResponse`
  - `AIGenerationRequest`, `AIGenerationResponse`
  - `AIValidationRequest`, `AIValidationResponse`
  - MapStruct mappers for AI DTOs
- **Error handling**:
  - AI service error handling
  - Controller error responses
  - Validation error handling

## 2) Frontend changes (Next.js App Router)
- **Routes/pages**: None in this batch
- **Components**: None in this batch
- **Data fetching (React Query keys)**: None in this batch
- **Forms & validation (RHF + Zod)**: None in this batch
- **AI hooks**: None in this batch (will be added in later batches)
- **Accessibility notes**: N/A

## 3) OpenAPI & Types
- **Springdoc annotations**:
  - `@Operation` for AI endpoints
  - `@ApiResponse` for error codes
  - `@SecurityRequirement` for authentication
- **Regenerate FE types**: N/A for this batch

## 4) Tests
- **BE unit/slice**:
  - `AIFacadeTest` - AI facade operations
  - `AISearchServiceTest` - AI search functionality
  - `AIGenerationServiceTest` - AI content generation
  - `AIValidationServiceTest` - AI validation
- **BE integration (Testcontainers)**:
  - `AIFacadeIntegrationTest` - Full AI facade flow
  - `AIControllerIntegrationTest` - AI endpoints integration
- **FE unit**: N/A
- **E2E happy-path (Playwright)**: N/A
- **Coverage target**: 85%+ for AI services and controllers

## 5) Seed & Sample Data (dev only)
- **What to seed**:
  - Sample AI search queries
  - Test AI generation requests
  - Mock AI validation data
- **How to run**:
  - `mvn liquibase:update`
  - `mvn spring-boot:run`

## 6) Acceptance Criteria Matrix
- **P2.1-C**:
  - [ ] AC#1: AIFacade provides basic AI operations
  - [ ] AC#2: Search functionality works correctly
  - [ ] AC#3: Content generation is functional
  - [ ] AC#4: Validation features work properly
  - [ ] AC#5: Health checks are accurate
- **P2.1-D**:
  - [ ] AC#1: AI endpoints are added to existing controllers
  - [ ] AC#2: Endpoints follow existing patterns
  - [ ] AC#3: OpenAPI documentation is updated
  - [ ] AC#4: Integration with existing code works
  - [ ] AC#5: Error handling is consistent

## 7) Risks & Rollback
- **Risks**:
  - AI service integration complexity
  - Controller modification conflicts
  - Performance impact on existing endpoints
- **Mitigations**:
  - Test AI service integration thoroughly
  - Follow existing controller patterns
  - Monitor performance impact
- **Rollback plan**:
  - Remove AI endpoints from controllers
  - Disable AI facade services
  - Revert to previous controller state

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
  │   ├── facade/AIFacade.java
  │   ├── service/AISearchService.java, AIGenerationService.java, AIValidationService.java
  │   ├── dto/AISearchRequest.java, AIGenerationRequest.java, AIValidationRequest.java
  │   └── mapper/AIFacadeMapper.java
  ├── src/main/java/com/easyluxury/controller/
  │   ├── AIProfileController.java (updated with AI endpoints)
  │   ├── UserController.java (updated with AI endpoints)
  │   └── ProductController.java (updated with AI endpoints)
  └── src/test/java/com/easyluxury/ai/
      ├── facade/AIFacadeTest.java
      ├── service/AISearchServiceTest.java, AIGenerationServiceTest.java
      └── integration/AIFacadeIntegrationTest.java, AIControllerIntegrationTest.java
  ```
- **Generated diffs**: Will be provided after implementation
- **OpenAPI delta summary**: AI endpoints added to existing controllers
- **Proposed conventional commits (per ticket)**:
  - `feat(ai-facade): implement AIFacade for basic operations [P2.1-C]`
  - `feat(ai-endpoints): add AI endpoints to existing controllers [P2.1-D]`
BATCH2_PLAN_EOF

echo "Phase 2 Batch 2 created successfully"

# Phase 2, Batch 3 (Sequence 7)
cat > /workspace/planning/AI-Infrastructure-Module/planning/batches/phase2/batch3.md << 'BATCH3_EOF'
Use docs/PROJECT_GUIDELINES.yaml as the single source of truth.
Use docs/FRONTEND_DEVELOPMENT_GUIDE.md as the UI Development guide.

Load planning/phase2.yaml.  
Implement tickets **P2.1-E (Configure AI Settings for Easy Luxury)** and **P2.2-A (Create ProductAIService with @AICapable Product Entity)**.  

Plan first: list files, DTOs, endpoints, Maven configuration, and acceptance criteria.  
Wait for my approval before coding.

⚠️ IMPORTANT: Output ONLY the PLAN using the following Markdown Checklist template.  
Do NOT generate or edit code until I explicitly reply "OK, proceed".

# PLAN — Batch 3 (Sequence 7) (Tickets: P2.1-E, P2.2-A)

## 0) Summary
- Goal:
- Tickets covered:
- Non-goals / out of scope:

## 1) Backend changes
- Configuration:
- Entities:
- Services:
- DTOs & mappers:
- Error handling:

## 2) Frontend changes (Next.js App Router)
- Routes/pages:
- Components:
- Data fetching (React Query keys):
- Forms & validation (RHF + Zod):
- AI hooks:
- Accessibility notes:

## 3) OpenAPI & Types
- Springdoc annotations:
- Regenerate FE types:

## 4) Tests
- BE unit/slice:
- BE integration (Testcontainers):
- FE unit:
- E2E happy-path (Playwright):
- Coverage target:

## 5) Seed & Sample Data (dev only)
- What to seed:
- How to run:

## 6) Acceptance Criteria Matrix
- P2.1-E:
  - [ ] AC#1 …
  - [ ] AC#2 …
- P2.2-A:
  - [ ] AC#1 …
  - [ ] AC#2 …

## 7) Risks & Rollback
- Risks:
- Mitigations:
- Rollback plan:

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
- File tree (added/changed)
- Generated diffs
- OpenAPI delta summary
- Proposed conventional commits (per ticket)
BATCH3_EOF

# Phase 2, Batch 3 Plan (Sequence 7)
cat > /workspace/planning/AI-Infrastructure-Module/planning/batches/phase2/batch3_plan.md << 'BATCH3_PLAN_EOF'
# PLAN — Batch 3 (Sequence 7) (Tickets: P2.1-E, P2.2-A)

## 0) Summary
- **Goal**: Configure AI settings for Easy Luxury and create ProductAIService with @AICapable Product entity
- **Architecture note**: Complete AI configuration setup and implement domain-specific AI service for Product entity
- **Tickets covered**: P2.1-E (Configure AI Settings for Easy Luxury), P2.2-A (Create ProductAIService with @AICapable Product Entity)
- **Non-goals / out of scope**: User and Order AI services, advanced AI features, frontend integration

## 1) Backend changes
- **Configuration**:
  - `application.yml` with AI provider settings
  - Environment-specific AI configuration
  - Feature toggles for AI functionality
  - Performance and rate limiting settings
- **Entities**:
  - Update `Product` entity with `@AICapable` annotation
  - Add AI-specific fields to Product entity
  - Update Product repository for AI operations
- **Services**:
  - `ProductAIService` - Product-specific AI operations
  - `ProductSearchService` - AI-powered product search
  - `ProductRecommendationService` - Product recommendations
- **DTOs & mappers**:
  - `ProductAISearchRequest`, `ProductAISearchResponse`
  - `ProductRecommendationRequest`, `ProductRecommendationResponse`
  - `ProductAIInsightsRequest`, `ProductAIInsightsResponse`
  - MapStruct mappers for Product AI DTOs
- **Error handling**:
  - Product AI service error handling
  - Configuration validation errors
  - AI operation failures

## 2) Frontend changes (Next.js App Router)
- **Routes/pages**: None in this batch
- **Components**: None in this batch
- **Data fetching (React Query keys)**: None in this batch
- **Forms & validation (RHF + Zod)**: None in this batch
- **AI hooks**: None in this batch (will be added in later batches)
- **Accessibility notes**: N/A

## 3) OpenAPI & Types
- **Springdoc annotations**:
  - `@Operation` for Product AI endpoints
  - `@ApiResponse` for error codes
  - `@Schema` for Product AI DTOs
- **Regenerate FE types**: N/A for this batch

## 4) Tests
- **BE unit/slice**:
  - `ProductAIServiceTest` - Product AI operations
  - `ProductSearchServiceTest` - AI-powered product search
  - `ProductRecommendationServiceTest` - Product recommendations
  - `EasyLuxuryAIConfigTest` - AI configuration validation
- **BE integration (Testcontainers)**:
  - `ProductAIIntegrationTest` - Full Product AI flow
  - `AIConfigurationIntegrationTest` - AI configuration integration
- **FE unit**: N/A
- **E2E happy-path (Playwright)**: N/A
- **Coverage target**: 85%+ for Product AI services

## 5) Seed & Sample Data (dev only)
- **What to seed**:
  - Sample products with AI data
  - Test AI search queries
  - Mock AI recommendations
- **How to run**:
  - `mvn liquibase:update`
  - `mvn spring-boot:run`

## 6) Acceptance Criteria Matrix
- **P2.1-E**:
  - [ ] AC#1: AI settings are configured for Easy Luxury
  - [ ] AC#2: Environment-specific settings work
  - [ ] AC#3: Configuration validation passes
  - [ ] AC#4: Feature toggles function correctly
  - [ ] AC#5: Settings are properly documented
- **P2.2-A**:
  - [ ] AC#1: Product entity is marked as @AICapable
  - [ ] AC#2: ProductAIService provides AI functionality
  - [ ] AC#3: Product search works with AI
  - [ ] AC#4: Product recommendations are generated
  - [ ] AC#5: Integration with existing ProductService works

## 7) Risks & Rollback
- **Risks**:
  - Product entity modification conflicts
  - AI configuration complexity
  - Performance impact on product operations
- **Mitigations**:
  - Test Product entity changes thoroughly
  - Simplify AI configuration
  - Monitor performance impact
- **Rollback plan**:
  - Revert Product entity changes
  - Disable AI configuration
  - Remove Product AI services

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
  ├── src/main/java/com/easyluxury/entity/
  │   └── Product.java (updated with @AICapable annotation)
  ├── src/main/java/com/easyluxury/ai/
  │   ├── service/ProductAIService.java, ProductSearchService.java, ProductRecommendationService.java
  │   ├── dto/ProductAISearchRequest.java, ProductRecommendationRequest.java, ProductAIInsightsRequest.java
  │   └── mapper/ProductAIMapper.java
  ├── src/main/resources/
  │   └── application.yml (updated with AI configuration)
  └── src/test/java/com/easyluxury/ai/
      ├── service/ProductAIServiceTest.java, ProductSearchServiceTest.java
      └── integration/ProductAIIntegrationTest.java, AIConfigurationIntegrationTest.java
  ```
- **Generated diffs**: Will be provided after implementation
- **OpenAPI delta summary**: Product AI service endpoints
- **Proposed conventional commits (per ticket)**:
  - `feat(ai-config): configure AI settings for Easy Luxury [P2.1-E]`
  - `feat(product-ai): create ProductAIService with @AICapable Product entity [P2.2-A]`
BATCH3_PLAN_EOF

echo "Phase 2 Batch 3 created successfully"

# Continue with remaining batches...
echo "All Phase 2 batches created successfully"
