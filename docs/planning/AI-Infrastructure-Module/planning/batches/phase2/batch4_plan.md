# PLAN — Batch 4 (Sequence 8) (Tickets: P2.2-B, P2.2-C)

## 0) Summary
- **Goal**: Implement UserAIService with behavioral tracking and OrderAIService for order analysis
- **Architecture note**: Create domain-specific AI services for User and Order entities with behavioral analysis capabilities
- **Tickets covered**: P2.2-B (Implement UserAIService with Behavioral Tracking), P2.2-C (Add OrderAIService for Order Analysis)
- **Non-goals / out of scope**: Advanced AI features, frontend integration, library extraction

## 1) Backend changes
- **Entities**:
  - Update `User` entity with `@AICapable` annotation
  - Update `Order` entity with `@AICapable` annotation
  - Add `UserBehavior` entity for behavioral tracking
  - Add AI-specific fields to User and Order entities
- **Services**:
  - `UserAIService` - User-specific AI operations with behavioral tracking
  - `UserBehaviorService` - User behavior analysis and insights
  - `OrderAIService` - Order analysis and pattern recognition
  - `OrderPatternService` - Order pattern analysis and fraud detection
- **DTOs & mappers**:
  - `UserAIInsightsRequest`, `UserAIInsightsResponse`
  - `UserBehaviorRequest`, `UserBehaviorResponse`
  - `OrderAIAnalysisRequest`, `OrderAIAnalysisResponse`
  - `OrderPatternRequest`, `OrderPatternResponse`
  - MapStruct mappers for User and Order AI DTOs
- **Error handling**:
  - User AI service error handling
  - Order AI service error handling
  - Behavioral tracking error handling

## 2) Frontend changes (Next.js App Router)
- **Routes/pages**: None in this batch
- **Components**: None in this batch
- **Data fetching (React Query keys)**: None in this batch
- **Forms & validation (RHF + Zod)**: None in this batch
- **AI hooks**: None in this batch (will be added in later batches)
- **Accessibility notes**: N/A

## 3) OpenAPI & Types
- **Springdoc annotations**:
  - `@Operation` for User and Order AI endpoints
  - `@ApiResponse` for error codes
  - `@Schema` for User and Order AI DTOs
- **Regenerate FE types**: N/A for this batch

## 4) Tests
- **BE unit/slice**:
  - `UserAIServiceTest` - User AI operations
  - `UserBehaviorServiceTest` - User behavior analysis
  - `OrderAIServiceTest` - Order AI analysis
  - `OrderPatternServiceTest` - Order pattern recognition
- **BE integration (Testcontainers)**:
  - `UserAIIntegrationTest` - Full User AI flow
  - `OrderAIIntegrationTest` - Full Order AI flow
- **FE unit**: N/A
- **E2E happy-path (Playwright)**: N/A
- **Coverage target**: 85%+ for User and Order AI services

## 5) Seed & Sample Data (dev only)
- **What to seed**:
  - Sample users with behavioral data
  - Test orders with analysis data
  - Mock AI insights and patterns
- **How to run**:
  - `mvn liquibase:update`
  - `mvn spring-boot:run`

## 6) Acceptance Criteria Matrix
- **P2.2-B**:
  - [ ] AC#1: User entity is marked as @AICapable
  - [ ] AC#2: UserAIService provides behavioral AI functionality
  - [ ] AC#3: User behavior is tracked correctly
  - [ ] AC#4: User preferences are learned
  - [ ] AC#5: User recommendations are generated
- **P2.2-C**:
  - [ ] AC#1: Order entity is marked as @AICapable
  - [ ] AC#2: OrderAIService provides order analysis
  - [ ] AC#3: Order patterns are recognized
  - [ ] AC#4: Fraud detection works correctly
  - [ ] AC#5: Integration with existing OrderService works

## 7) Risks & Rollback
- **Risks**:
  - User and Order entity modification conflicts
  - Behavioral tracking performance impact
  - AI analysis complexity
- **Mitigations**:
  - Test entity changes thoroughly
  - Optimize behavioral tracking
  - Simplify AI analysis algorithms
- **Rollback plan**:
  - Revert User and Order entity changes
  - Disable behavioral tracking
  - Remove User and Order AI services

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
  │   ├── User.java (updated with @AICapable annotation)
  │   ├── Order.java (updated with @AICapable annotation)
  │   └── UserBehavior.java (new entity)
  ├── src/main/java/com/easyluxury/ai/
  │   ├── service/UserAIService.java, UserBehaviorService.java
  │   ├── service/OrderAIService.java, OrderPatternService.java
  │   ├── dto/UserAIInsightsRequest.java, OrderAIAnalysisRequest.java, etc.
  │   └── mapper/UserAIMapper.java, OrderAIMapper.java
  └── src/test/java/com/easyluxury/ai/
      ├── service/UserAIServiceTest.java, OrderAIServiceTest.java
      └── integration/UserAIIntegrationTest.java, OrderAIIntegrationTest.java
  ```
- **Generated diffs**: Will be provided after implementation
- **OpenAPI delta summary**: User and Order AI service endpoints
- **Proposed conventional commits (per ticket)**:
  - `feat(user-ai): implement UserAIService with behavioral tracking [P2.2-B]`
  - `feat(order-ai): add OrderAIService for order analysis [P2.2-C]`
