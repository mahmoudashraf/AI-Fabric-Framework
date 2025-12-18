#!/bin/bash

# Phase 2 - Easy Luxury Integration batches

# Batch 1 (Sequence 5)
cat > /workspace/planning/AI-Infrastructure-Module/planning/batches/phase2/batch1.md << 'BATCH1_EOF'
Use docs/PROJECT_GUIDELINES.yaml as the single source of truth.
Use docs/FRONTEND_DEVELOPMENT_GUIDE.md as the UI Development guide.

Load planning/phase2.yaml.  
Implement tickets **P2.1-A (Add AI Module Dependency to Easy Luxury)** and **P2.1-B (Create EasyLuxuryAIConfig)**.  

Plan first: list files, DTOs, endpoints, Maven configuration, and acceptance criteria.  
Wait for my approval before coding.

⚠️ IMPORTANT: Output ONLY the PLAN using the following Markdown Checklist template.  
Do NOT generate or edit code until I explicitly reply "OK, proceed".

# PLAN — Batch 1 (Sequence 5) (Tickets: P2.1-A, P2.1-B)

## 0) Summary
- Goal:
- Tickets covered:
- Non-goals / out of scope:

## 1) Backend changes
- Maven dependencies:
- Configuration:
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
- P2.1-A:
  - [ ] AC#1 …
  - [ ] AC#2 …
- P2.1-B:
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
BATCH1_EOF

# Batch 1 Plan (Sequence 5)
cat > /workspace/planning/AI-Infrastructure-Module/planning/batches/phase2/batch1_plan.md << 'BATCH1_PLAN_EOF'
# PLAN — Batch 1 (Sequence 5) (Tickets: P2.1-A, P2.1-B)

## 0) Summary
- **Goal**: Integrate AI module into Easy Luxury with basic configuration and dependency setup
- **Architecture note**: Add AI module as dependency and create Easy Luxury specific AI configuration
- **Tickets covered**: P2.1-A (Add AI Module Dependency to Easy Luxury), P2.1-B (Create EasyLuxuryAIConfig)
- **Non-goals / out of scope**: Domain-specific AI services, AI endpoints, advanced features

## 1) Backend changes
- **Maven dependencies**:
  - Add `ai-infrastructure-spring-boot-starter` dependency to Easy Luxury POM
  - Update Spring Boot version compatibility
  - Add AI-specific dependencies if needed
- **Configuration**:
  - `EasyLuxuryAIConfig` - Project-specific AI settings
  - `application.yml` updates for AI configuration
  - Environment-specific AI settings
- **Services**:
  - `AIFacade` - Basic AI operations facade
  - `AIHealthService` - AI health monitoring
- **DTOs & mappers**:
  - `AIConfigurationDto`, `AIHealthDto`
  - MapStruct mappers for AI DTOs
- **Error handling**:
  - AI service error handling
  - Configuration validation errors

## 2) Frontend changes (Next.js App Router)
- **Routes/pages**: None in this batch
- **Components**: None in this batch
- **Data fetching (React Query keys)**: None in this batch
- **Forms & validation (RHF + Zod)**: None in this batch
- **AI hooks**: None in this batch (will be added in later batches)
- **Accessibility notes**: N/A

## 3) OpenAPI & Types
- **Springdoc annotations**:
  - `@Operation` for AI configuration endpoints
  - `@ApiResponse` for error codes
- **Regenerate FE types**: N/A for this batch

## 4) Tests
- **BE unit/slice**:
  - `EasyLuxuryAIConfigTest` - Configuration validation
  - `AIFacadeTest` - Basic AI operations
- **BE integration (Testcontainers)**:
  - `AIIntegrationTest` - AI module integration
- **FE unit**: N/A
- **E2E happy-path (Playwright)**: N/A
- **Coverage target**: 80%+ for AI integration

## 5) Seed & Sample Data (dev only)
- **What to seed**:
  - Sample AI configuration
  - Test AI settings
- **How to run**:
  - `mvn liquibase:update`
  - `mvn spring-boot:run`

## 6) Acceptance Criteria Matrix
- **P2.1-A**:
  - [ ] AC#1: AI module dependency is added to POM
  - [ ] AC#2: Auto-configuration works correctly
  - [ ] AC#3: AI services are available for injection
  - [ ] AC#4: Configuration is properly loaded
  - [ ] AC#5: Basic AI functionality is accessible
- **P2.1-B**:
  - [ ] AC#1: Easy Luxury specific AI configuration is created
  - [ ] AC#2: Settings are properly validated
  - [ ] AC#3: Configuration is documented
  - [ ] AC#4: Error handling works correctly
  - [ ] AC#5: Settings are applied to AI services

## 7) Risks & Rollback
- **Risks**:
  - Dependency conflicts
  - Configuration issues
  - Auto-configuration problems
- **Mitigations**:
  - Test dependency compatibility
  - Validate configuration thoroughly
  - Test auto-configuration
- **Rollback plan**:
  - Remove AI module dependency
  - Revert configuration changes
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
  ├── pom.xml (updated with AI dependency)
  ├── src/main/java/com/easyluxury/ai/
  │   ├── config/EasyLuxuryAIConfig.java
  │   ├── facade/AIFacade.java
  │   └── dto/AIConfigurationDto.java, AIHealthDto.java
  ├── src/main/resources/
  │   └── application.yml (updated with AI config)
  └── src/test/java/com/easyluxury/ai/
      ├── config/EasyLuxuryAIConfigTest.java
      └── facade/AIFacadeTest.java
  ```
- **Generated diffs**: Will be provided after implementation
- **OpenAPI delta summary**: AI configuration endpoints
- **Proposed conventional commits (per ticket)**:
  - `feat(ai-integration): add AI module dependency to Easy Luxury [P2.1-A]`
  - `feat(ai-config): create EasyLuxuryAIConfig for project-specific settings [P2.1-B]`
BATCH1_PLAN_EOF

echo "Phase 2 Batch 1 created successfully"
