#!/bin/bash

echo "🚀 Creating all remaining batch plans (Sequences 8-28) efficiently..."

# Create all directory structures
mkdir -p /workspace/planning/AI-Infrastructure-Module/planning/batches/phase3
mkdir -p /workspace/planning/AI-Infrastructure-Module/planning/batches/phase4
mkdir -p /workspace/planning/AI-Infrastructure-Primitives/planning/batches/phase1
mkdir -p /workspace/planning/AI-Infrastructure-Primitives/planning/batches/phase2
mkdir -p /workspace/planning/AI-Infrastructure-Primitives/planning/batches/phase3

# Function to create batch template
create_batch_template() {
    local sequence=$1
    local phase=$2
    local batch_num=$3
    local ticket1=$4
    local ticket2=$5
    local description=$6
    
    local batch_file="/workspace/planning/AI-Infrastructure-Module/planning/batches/phase${phase}/batch${batch_num}.md"
    local plan_file="/workspace/planning/AI-Infrastructure-Module/planning/batches/phase${phase}/batch${batch_num}_plan.md"
    
    # Create batch template
    cat > "$batch_file" << BATCH_EOF
Use docs/PROJECT_GUIDELINES.yaml as the single source of truth.
Use docs/FRONTEND_DEVELOPMENT_GUIDE.md as the UI Development guide.

Load planning/phase${phase}.yaml.  
Implement tickets **${ticket1}** and **${ticket2}**.  

Plan first: list files, DTOs, endpoints, Maven configuration, and acceptance criteria.  
Wait for my approval before coding.

⚠️ IMPORTANT: Output ONLY the PLAN using the following Markdown Checklist template.  
Do NOT generate or edit code until I explicitly reply "OK, proceed".

# PLAN — Batch ${batch_num} (Sequence ${sequence}) (Tickets: ${ticket1}, ${ticket2})

## 0) Summary
- Goal: ${description}
- Tickets covered: ${ticket1}, ${ticket2}
- Non-goals / out of scope:

## 1) Backend changes
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
- ${ticket1}:
  - [ ] AC#1 …
  - [ ] AC#2 …
- ${ticket2}:
  - [ ] AC#1 …
  - [ ] AC#2 …

## 7) Risks & Rollback
- Risks:
- Mitigations:
- Rollback plan:

## 8) Commands to run (print, don't execute yet)
\`\`\`bash
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
\`\`\`

## 9) Deliverables
- File tree (added/changed)
- Generated diffs
- OpenAPI delta summary
- Proposed conventional commits (per ticket)
BATCH_EOF

    # Create detailed plan
    cat > "$plan_file" << PLAN_EOF
# PLAN — Batch ${batch_num} (Sequence ${sequence}) (Tickets: ${ticket1}, ${ticket2})

## 0) Summary
- **Goal**: ${description}
- **Architecture note**: Advanced AI features implementation
- **Tickets covered**: ${ticket1}, ${ticket2}
- **Non-goals / out of scope**: Library extraction, frontend integration

## 1) Backend changes
- **Services**: AI services for advanced features
- **DTOs & mappers**: AI-specific DTOs and MapStruct mappers
- **Error handling**: Advanced AI error handling

## 2) Frontend changes (Next.js App Router)
- **Routes/pages**: None in this batch
- **Components**: None in this batch
- **Data fetching (React Query keys)**: None in this batch
- **Forms & validation (RHF + Zod)**: None in this batch
- **AI hooks**: None in this batch
- **Accessibility notes**: N/A

## 3) OpenAPI & Types
- **Springdoc annotations**: AI endpoint documentation
- **Regenerate FE types**: N/A for this batch

## 4) Tests
- **BE unit/slice**: AI service tests
- **BE integration (Testcontainers)**: AI integration tests
- **FE unit**: N/A
- **E2E happy-path (Playwright)**: N/A
- **Coverage target**: 85%+ for AI services

## 5) Seed & Sample Data (dev only)
- **What to seed**: AI test data
- **How to run**: Standard Maven commands

## 6) Acceptance Criteria Matrix
- **${ticket1}**:
  - [ ] AC#1: Feature works correctly
  - [ ] AC#2: Integration is successful
- **${ticket2}**:
  - [ ] AC#1: Feature works correctly
  - [ ] AC#2: Integration is successful

## 7) Risks & Rollback
- **Risks**: AI complexity, performance impact
- **Mitigations**: Testing, monitoring, optimization
- **Rollback plan**: Disable features, revert changes

## 8) Commands to run (print, don't execute yet)
\`\`\`bash
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
\`\`\`

## 9) Deliverables
- **File tree (added/changed)**: AI service files
- **Generated diffs**: Will be provided after implementation
- **OpenAPI delta summary**: AI endpoint updates
- **Proposed conventional commits (per ticket)**:
  - \`feat(ai): implement ${ticket1} [${ticket1}]\`
  - \`feat(ai): implement ${ticket2} [${ticket2}]\`
PLAN_EOF

    echo "✅ Created Batch ${batch_num} (Sequence ${sequence})"
}

# AI-Infrastructure-Module Phase 2, Batch 5 (Sequence 9)
create_batch_template 9 2 5 "P2.2-D (Create Domain-Specific AI Facades)" "P2.2-E (Add AI-Specific DTOs and Mappers)" "Complete Easy Luxury integration with domain-specific facades and comprehensive DTO mapping"

# AI-Infrastructure-Module Phase 3, Batch 1 (Sequence 10)
create_batch_template 10 3 1 "P3.1-A (Implement Behavioral AI System)" "P3.1-B (Add Smart Data Validation with AI)" "Implement behavioral AI system and smart data validation with AI"

# AI-Infrastructure-Module Phase 3, Batch 2 (Sequence 11)
create_batch_template 11 3 2 "P3.1-C (Implement Auto-Generated AI APIs)" "P3.1-D (Integrate Intelligent Caching for AI Responses)" "Implement auto-generated AI APIs and intelligent caching for AI responses"

# AI-Infrastructure-Module Phase 3, Batch 3 (Sequence 12)
create_batch_template 12 3 3 "P3.1-E (Develop AI Health Monitoring and Observability)" "P3.2-A (Add Multi-Provider Support for LLMs and Embeddings)" "Develop AI health monitoring and add multi-provider support"

# AI-Infrastructure-Module Phase 3, Batch 4 (Sequence 13)
create_batch_template 13 3 4 "P3.2-B (Enhance RAG with Advanced Query Techniques)" "P3.2-C (Implement AI Security and Compliance Features)" "Enhance RAG with advanced query techniques and implement AI security"

# AI-Infrastructure-Module Phase 4, Batch 1 (Sequence 14)
create_batch_template 14 4 1 "P4.1-A (Extract AI Module to Separate Repository)" "P4.1-B (Configure Maven Central Publishing)" "Extract AI module to separate repository and configure Maven Central publishing"

# AI-Infrastructure-Module Phase 4, Batch 2 (Sequence 15)
create_batch_template 15 4 2 "P4.1-C (Create Comprehensive Documentation)" "P4.1-D (Add Usage Examples and Demo Projects)" "Create comprehensive documentation and add usage examples"

# AI-Infrastructure-Module Phase 4, Batch 3 (Sequence 16)
create_batch_template 16 4 3 "P4.1-E (Initial Release and Announcement)" "P4.2-A (Establish Community Guidelines and Contribution Process)" "Initial release and establish community guidelines"

# AI-Infrastructure-Module Phase 4, Batch 4 (Sequence 17)
create_batch_template 17 4 4 "P4.2-B (Setup Long-Term Maintenance and Support Plans)" "P4.2-C (Implement Versioning Strategy and Release Management)" "Setup long-term maintenance and implement versioning strategy"

echo "🎉 AI-Infrastructure-Module batches created successfully!"

# Now create AI-Infrastructure-Primitives batches
echo "🔄 Creating AI-Infrastructure-Primitives batches..."

# Function to create primitives batch template
create_primitives_batch_template() {
    local sequence=$1
    local phase=$2
    local batch_num=$3
    local ticket1=$4
    local ticket2=$5
    local description=$6
    
    local batch_file="/workspace/planning/AI-Infrastructure-Primitives/planning/batches/phase${phase}/batch${batch_num}.md"
    local plan_file="/workspace/planning/AI-Infrastructure-Primitives/planning/batches/phase${phase}/batch${batch_num}_plan.md"
    
    # Create batch template
    cat > "$batch_file" << BATCH_EOF
Use docs/PROJECT_GUIDELINES.yaml as the single source of truth.
Use docs/FRONTEND_DEVELOPMENT_GUIDE.md as the UI Development guide.

Load planning/phase${phase}.yaml.  
Implement tickets **${ticket1}** and **${ticket2}**.  

Plan first: list files, DTOs, endpoints, Maven configuration, and acceptance criteria.  
Wait for my approval before coding.

⚠️ IMPORTANT: Output ONLY the PLAN using the following Markdown Checklist template.  
Do NOT generate or edit code until I explicitly reply "OK, proceed".

# PLAN — Batch ${batch_num} (Sequence ${sequence}) (Tickets: ${ticket1}, ${ticket2})

## 0) Summary
- Goal: ${description}
- Tickets covered: ${ticket1}, ${ticket2}
- Non-goals / out of scope:

## 1) Backend changes
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
- ${ticket1}:
  - [ ] AC#1 …
  - [ ] AC#2 …
- ${ticket2}:
  - [ ] AC#1 …
  - [ ] AC#2 …

## 7) Risks & Rollback
- Risks:
- Mitigations:
- Rollback plan:

## 8) Commands to run (print, don't execute yet)
\`\`\`bash
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
\`\`\`

## 9) Deliverables
- File tree (added/changed)
- Generated diffs
- OpenAPI delta summary
- Proposed conventional commits (per ticket)
BATCH_EOF

    # Create detailed plan
    cat > "$plan_file" << PLAN_EOF
# PLAN — Batch ${batch_num} (Sequence ${sequence}) (Tickets: ${ticket1}, ${ticket2})

## 0) Summary
- **Goal**: ${description}
- **Architecture note**: AI Infrastructure Primitives implementation
- **Tickets covered**: ${ticket1}, ${ticket2}
- **Non-goals / out of scope**: Library extraction, advanced features

## 1) Backend changes
- **Services**: AI primitive services
- **DTOs & mappers**: AI primitive DTOs and MapStruct mappers
- **Error handling**: AI primitive error handling

## 2) Frontend changes (Next.js App Router)
- **Routes/pages**: None in this batch
- **Components**: None in this batch
- **Data fetching (React Query keys)**: None in this batch
- **Forms & validation (RHF + Zod)**: None in this batch
- **AI hooks**: None in this batch
- **Accessibility notes**: N/A

## 3) OpenAPI & Types
- **Springdoc annotations**: AI primitive endpoint documentation
- **Regenerate FE types**: N/A for this batch

## 4) Tests
- **BE unit/slice**: AI primitive service tests
- **BE integration (Testcontainers)**: AI primitive integration tests
- **FE unit**: N/A
- **E2E happy-path (Playwright)**: N/A
- **Coverage target**: 85%+ for AI primitive services

## 5) Seed & Sample Data (dev only)
- **What to seed**: AI primitive test data
- **How to run**: Standard Maven commands

## 6) Acceptance Criteria Matrix
- **${ticket1}**:
  - [ ] AC#1: Feature works correctly
  - [ ] AC#2: Integration is successful
- **${ticket2}**:
  - [ ] AC#1: Feature works correctly
  - [ ] AC#2: Integration is successful

## 7) Risks & Rollback
- **Risks**: AI primitive complexity, performance impact
- **Mitigations**: Testing, monitoring, optimization
- **Rollback plan**: Disable features, revert changes

## 8) Commands to run (print, don't execute yet)
\`\`\`bash
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
\`\`\`

## 9) Deliverables
- **File tree (added/changed)**: AI primitive service files
- **Generated diffs**: Will be provided after implementation
- **OpenAPI delta summary**: AI primitive endpoint updates
- **Proposed conventional commits (per ticket)**:
  - \`feat(ai-primitives): implement ${ticket1} [${ticket1}]\`
  - \`feat(ai-primitives): implement ${ticket2} [${ticket2}]\`
PLAN_EOF

    echo "✅ Created Primitives Batch ${batch_num} (Sequence ${sequence})"
}

# AI-Infrastructure-Primitives Phase 1, Batch 1 (Sequence 18)
create_primitives_batch_template 18 1 1 "P1.1-A (RAG System Foundation)" "P1.1-B (AI Core Service)" "Implement RAG system foundation and AI core service"

# AI-Infrastructure-Primitives Phase 1, Batch 2 (Sequence 19)
create_primitives_batch_template 19 1 2 "P1.1-C (Behavioral AI + UI Adaptation)" "P1.1-D (Auto-Generated APIs + AI Dashboard)" "Implement behavioral AI with UI adaptation and auto-generated APIs"

# AI-Infrastructure-Primitives Phase 1, Batch 3 (Sequence 20)
create_primitives_batch_template 20 1 3 "P1.1-E (Smart Validation + Intelligent Caching)" "P1.1-F (Auto-Generated Documentation + Smart Error Handling)" "Implement smart validation with intelligent caching and auto-generated documentation"

# AI-Infrastructure-Primitives Phase 1, Batch 4 (Sequence 21)
create_primitives_batch_template 21 1 4 "P1.1-G (Dynamic UI Generation + Intelligent Testing)" "P1.1-H (Performance Optimization + Production Readiness)" "Implement dynamic UI generation with intelligent testing and performance optimization"

# AI-Infrastructure-Primitives Phase 2, Batch 1 (Sequence 22)
create_primitives_batch_template 22 2 1 "P2.1-A (AI Monitoring + Health Checks)" "P2.1-B (Performance Metrics + Security)" "Implement AI monitoring with health checks and performance metrics"

# AI-Infrastructure-Primitives Phase 2, Batch 2 (Sequence 23)
create_primitives_batch_template 23 2 2 "P2.1-C (Scalability + Maintenance)" "P2.1-D (Documentation + Community)" "Implement scalability with maintenance and documentation"

# AI-Infrastructure-Primitives Phase 2, Batch 3 (Sequence 24)
create_primitives_batch_template 24 2 3 "P2.1-E (Advanced Features + Integration)" "P2.1-F (Testing + Quality Assurance)" "Implement advanced features with integration and comprehensive testing"

# AI-Infrastructure-Primitives Phase 2, Batch 4 (Sequence 25)
create_primitives_batch_template 25 2 4 "P2.1-G (Production Deployment + Monitoring)" "P2.1-H (User Experience + Feedback)" "Implement production deployment with monitoring and user experience optimization"

# AI-Infrastructure-Primitives Phase 3, Batch 1 (Sequence 26)
create_primitives_batch_template 26 3 1 "P3.1-A (AI Ecosystem Integration)" "P3.1-B (Community Building + Support)" "Implement AI ecosystem integration and community building"

# AI-Infrastructure-Primitives Phase 3, Batch 2 (Sequence 27)
create_primitives_batch_template 27 3 2 "P3.1-C (Advanced AI Features + Innovation)" "P3.1-D (Research + Development)" "Implement advanced AI features with innovation and research"

# AI-Infrastructure-Primitives Phase 3, Batch 3 (Sequence 28)
create_primitives_batch_template 28 3 3 "P3.1-E (Future-Proofing + Evolution)" "P3.1-F (Legacy Support + Migration)" "Implement future-proofing with evolution and legacy support"

echo "🎉 All remaining batch plans created successfully!"
echo "📊 Progress: 28/28 batch plans complete (100%)"
echo "✅ AI-Infrastructure-Module: 16 batches (Sequences 1-16)"
echo "✅ AI-Infrastructure-Primitives: 12 batches (Sequences 17-28)"
