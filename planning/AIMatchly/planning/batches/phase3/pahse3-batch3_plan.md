# PLAN — Batch 3.3 (Tickets: P3.1-E, P3.1-F, P3.1-G, P3.1-H, P3.1-I, P3.1-J)

## 0) Summary
- **Goal**: Complete Phase 3 with Pro Plan features, advanced analytics, concierge services, security enhancements, performance optimization, and advanced AI features
- **Architecture note**: Comprehensive platform completion with advanced features, security, performance, and AI capabilities
- **Tickets covered**: P3.1-E (Pro Plan Features), P3.1-F (Advanced Analytics & Reporting), P3.1-G (Concierge & Featured Placements), P3.1-H (Advanced Security & Compliance), P3.1-I (Performance Optimization & Scaling), P3.1-J (Advanced Matching & AI Features)
- **Non-goals / out of scope**: Additional payment methods, complex project management, advanced reporting

## 1) Backend changes
- **Entities (new/updated)**: 
  - `ProPlanFeature` (id, planId, feature, enabled, createdAt)
  - `AnalyticsEvent` (id, userId, event, data, timestamp, createdAt)
  - `Report` (id, name, type, config, generatedAt, createdAt)
  - `ConciergeService` (id, buyerId, agencyId, status, fee, createdAt, updatedAt)
  - `FeaturedPlacement` (id, agencyId, startDate, endDate, active, createdAt)
  - `SecurityAudit` (id, userId, action, ip, userAgent, createdAt)
  - `PerformanceMetric` (id, endpoint, responseTime, timestamp, createdAt)
  - `AIModel` (id, name, version, config, active, createdAt, updatedAt)

- **Liquibase changesets**:
  - `V020__pro_plan_features.yaml` - Add Pro Plan feature gating
  - `V021__advanced_analytics.yaml` - Create analytics_events, reports tables
  - `V022__concierge_system.yaml` - Create concierge_services, featured_placements tables
  - `V023__security_compliance.yaml` - Create security_audits, compliance_logs tables
  - `V024__performance_optimization.yaml` - Create performance_metrics, cache_configs tables
  - `V025__ai_features.yaml` - Create ai_models, ai_insights tables

- **Endpoints (method → path → purpose)**:
  - `GET /api/pro-plan/features` → Get Pro Plan features
  - `POST /api/users/pro-plan/upgrade` → Upgrade to Pro Plan
  - `GET /api/analytics/advanced` → Get advanced analytics
  - `POST /api/reports/generate` → Generate custom report
  - `GET /api/concierge/services` → Get concierge services
  - `POST /api/concierge/intro` → Request concierge introduction
  - `GET /api/featured/agencies` → Get featured agencies
  - `POST /api/agencies/{id}/featured` → Feature agency
  - `GET /api/security/audit` → Get security audit logs
  - `POST /api/security/scrub-pii` → Scrub PII data
  - `GET /api/performance/metrics` → Get performance metrics
  - `POST /api/performance/optimize` → Optimize performance
  - `GET /api/ai/insights` → Get AI insights
  - `POST /api/ai/analyze` → Analyze with AI

- **Pro Plan Features**:
  - Higher tile cap (unlimited use cases)
  - Advanced analytics dashboard
  - Auto-ingestion capabilities
  - Application fee waivers
  - Proof Pack fast-track
  - Priority gallery placement

- **Advanced Analytics Features**:
  - Market insights and trends
  - Benchmark reports
  - Custom report generation
  - Agency performance comparisons
  - Real-time analytics updates

- **Concierge & Featured Features**:
  - Concierge introduction service (100 credits or cash)
  - Featured agency placements
  - Featured agency gallery
  - Concierge service tracking

- **Security & Compliance Features**:
  - PII scrubbing before LLM processing
  - GDPR compliance (export/delete, DPA)
  - Advanced audit logging
  - Rate limiting on sensitive endpoints
  - Security headers and policies

- **Performance Optimization Features**:
  - Redis caching implementation
  - Database query optimization
  - CDN integration
  - Performance monitoring
  - Load testing and optimization

- **Advanced AI Features**:
  - Advanced matching algorithms
  - AI-powered insights and recommendations
  - Explainable AI for matching decisions
  - Machine learning model improvements

- **DTOs & mappers**:
  - `ProPlanFeatureDto`, `ProPlanFeatureMapper` (MapStruct)
  - `AnalyticsEventDto`, `AnalyticsEventMapper` (MapStruct)
  - `ReportDto`, `ReportMapper` (MapStruct)
  - `ConciergeServiceDto`, `ConciergeServiceMapper` (MapStruct)
  - `FeaturedPlacementDto`, `FeaturedPlacementMapper` (MapStruct)
  - `SecurityAuditDto`, `SecurityAuditMapper` (MapStruct)
  - `PerformanceMetricDto`, `PerformanceMetricMapper` (MapStruct)
  - `AIModelDto`, `AIModelMapper` (MapStruct)

- **Services**:
  - `ProPlanService` - Pro Plan feature management
  - `AdvancedAnalyticsService` - Advanced analytics
  - `ReportService` - Report generation
  - `ConciergeService` - Concierge management
  - `FeaturedService` - Featured placements
  - `SecurityService` - Security and compliance
  - `PerformanceService` - Performance optimization
  - `AIService` - Advanced AI features

- **Validation rules**:
  - Pro Plan feature validation
  - Analytics query validation
  - Security action validation
  - Performance metric validation
  - AI model validation

- **Error model (problem+json codes)**:
  - `400` - Validation errors
  - `401` - Unauthorized
  - `403` - Forbidden (Pro Plan required)
  - `404` - Resource not found
  - `409` - Already featured
  - `422` - AI analysis failed
  - `500` - Performance error

## 2) Frontend changes (Next.js App Router)
- **Routes/pages**:
  - `/pro-plan/features` - Pro Plan features
  - `/pro-plan/upgrade` - Pro Plan upgrade
  - `/analytics/advanced` - Advanced analytics
  - `/reports` - Report generation
  - `/concierge` - Concierge services
  - `/marketplace/featured` - Featured agencies
  - `/security/audit` - Security audit
  - `/performance/metrics` - Performance metrics
  - `/ai/insights` - AI insights

- **Components (reusing existing)**:
  - **Forms**: Extend `ValidationWizard` from `/components/forms/forms-wizard/ValidationWizard/` for Pro Plan
  - **Cards**: Use `MainCard`, `SubCard` from `/components/ui-component/cards/` for feature display
  - **Tables**: Reuse table patterns from `/components/users/list/Style1/UserList.tsx` for analytics
  - **Charts**: Extend existing chart components for advanced analytics
  - **Status indicators**: Use `Chip` component from `/components/ui-component/extended/Chip.tsx` for status

- **Data fetching (React Query keys)**:
  - `['pro-plan', 'features']` - Pro Plan features
  - `['analytics', 'advanced']` - Advanced analytics
  - `['reports']` - Reports
  - `['concierge', 'services']` - Concierge services
  - `['featured', 'agencies']` - Featured agencies
  - `['security', 'audit']` - Security audit
  - `['performance', 'metrics']` - Performance metrics
  - `['ai', 'insights']` - AI insights

- **Forms & validation (RHF + Zod)**:
  - Pro Plan upgrade form
  - Report generation form
  - Concierge service form
  - Security settings form
  - Performance configuration form

- **Pro Plan Features**:
  - Feature gating and display
  - Upgrade workflow
  - Advanced analytics access
  - Auto-ingestion configuration

- **Advanced Analytics Features**:
  - Market insights dashboard
  - Benchmark reports
  - Custom report generation
  - Agency performance comparisons

- **Concierge Features**:
  - Concierge service interface
  - Featured agency gallery
  - Service tracking and management

- **Security Features**:
  - Security audit dashboard
  - PII scrubbing interface
  - Compliance management

- **Performance Features**:
  - Performance monitoring dashboard
  - Optimization configuration
  - Load testing interface

- **AI Features**:
  - AI insights dashboard
  - Advanced matching interface
  - AI analysis tools

- **Accessibility notes**: 
  - ARIA labels for all forms
  - Keyboard navigation support
  - Screen reader friendly analytics
  - Security interface accessibility

## 3) OpenAPI & Types
- **Springdoc annotations**:
  - `@Operation` for all endpoints
  - `@ApiResponse` for error codes
  - `@SecurityRequirement` for JWT authentication
  - `@Tag` for endpoint grouping

- **Regenerate FE types**:
  - Generate TypeScript types from OpenAPI spec
  - Create new types for all advanced features
  - Update existing types for enhanced functionality

## 4) Tests
- **BE unit/slice**:
  - `ProPlanServiceTest` - Pro Plan feature management
  - `AdvancedAnalyticsServiceTest` - Advanced analytics
  - `ReportServiceTest` - Report generation
  - `ConciergeServiceTest` - Concierge management
  - `SecurityServiceTest` - Security and compliance
  - `PerformanceServiceTest` - Performance optimization
  - `AIServiceTest` - Advanced AI features

- **BE integration (Testcontainers)**:
  - `ProPlanControllerIntegrationTest` - Pro Plan flow
  - `AnalyticsControllerIntegrationTest` - Analytics flow
  - `SecurityControllerIntegrationTest` - Security flow
  - `PerformanceControllerIntegrationTest` - Performance flow

- **FE unit**:
  - `ProPlanFeatures.test.tsx` - Pro Plan features testing
  - `AdvancedAnalytics.test.tsx` - Advanced analytics testing
  - `ConciergeServices.test.tsx` - Concierge services testing
  - `SecurityAudit.test.tsx` - Security audit testing
  - `PerformanceMetrics.test.tsx` - Performance metrics testing
  - `AIInsights.test.tsx` - AI insights testing

- **E2E happy-path (Playwright)**:
  - Pro Plan upgrade → Feature activation → Advanced analytics
  - Concierge service → Featured placement → Agency promotion
  - Security audit → Compliance management → PII scrubbing
  - Performance optimization → Load testing → Metrics monitoring
  - AI insights → Advanced matching → Analysis tools

- **Coverage target**: 80%+ for all advanced services, 70%+ overall

## 5) Seed & Sample Data (dev only)
- **What to seed**:
  - Pro Plan feature configurations
  - Advanced analytics data
  - Report templates
  - Concierge service examples
  - Featured agency placements
  - Security audit examples
  - Performance metrics data
  - AI model configurations

- **How to run**:
  - `mvn liquibase:update` - Apply migrations
  - `mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"` - Run with seed data

## 6) Acceptance Criteria Matrix
- **P3.1-E**:
  - [ ] AC#1: Pro Plan feature gating and display
  - [ ] AC#2: Higher tile cap and advanced analytics access
  - [ ] AC#3: Auto-ingestion capabilities
  - [ ] AC#4: Application fee waivers
  - [ ] AC#5: Proof Pack fast-track and priority placement

- **P3.1-F**:
  - [ ] AC#1: Market insights and trends dashboard
  - [ ] AC#2: Benchmark reports and custom report generation
  - [ ] AC#3: Agency performance comparisons
  - [ ] AC#4: Real-time analytics updates
  - [ ] AC#5: Advanced analytics visualization

- **P3.1-G**:
  - [ ] AC#1: Concierge introduction service (100 credits or cash)
  - [ ] AC#2: Featured agency placements
  - [ ] AC#3: Featured agency gallery
  - [ ] AC#4: Concierge service tracking
  - [ ] AC#5: Service management interface

- **P3.1-H**:
  - [ ] AC#1: PII scrubbing before LLM processing
  - [ ] AC#2: GDPR compliance (export/delete, DPA)
  - [ ] AC#3: Advanced audit logging
  - [ ] AC#4: Rate limiting on sensitive endpoints
  - [ ] AC#5: Security headers and policies

- **P3.1-I**:
  - [ ] AC#1: p95 page load < 2.5s
  - [ ] AC#2: p95 matching < 800ms at 1k agencies
  - [ ] AC#3: 99.5% availability target
  - [ ] AC#4: Performance monitoring dashboard
  - [ ] AC#5: Load testing and optimization

- **P3.1-J**:
  - [ ] AC#1: Advanced matching algorithms
  - [ ] AC#2: AI-powered insights and recommendations
  - [ ] AC#3: Explainable AI for matching decisions
  - [ ] AC#4: Machine learning model improvements
  - [ ] AC#5: AI analysis tools and interface

## 7) Risks & Rollback
- **Risks**:
  - Feature complexity and integration
  - Performance optimization challenges
  - Security compliance requirements
  - AI model accuracy and reliability
  - Frontend integration complexity

- **Mitigations**:
  - Progressive feature rollout
  - Comprehensive performance testing
  - Security compliance validation
  - AI model testing and validation
  - Extensive frontend testing

- **Rollback plan**:
  - Disable advanced features
  - Revert to basic functionality
  - Rollback performance optimizations
  - Restore basic security measures
  - Disable AI features

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
  ├── src/main/java/com/aimatchly/
  │   ├── entity/ProPlanFeature.java, AnalyticsEvent.java, Report.java, ConciergeService.java, FeaturedPlacement.java, SecurityAudit.java, PerformanceMetric.java, AIModel.java
  │   ├── dto/ProPlanFeatureDto.java, AnalyticsEventDto.java, ReportDto.java, ConciergeServiceDto.java, FeaturedPlacementDto.java, SecurityAuditDto.java, PerformanceMetricDto.java, AIModelDto.java
  │   ├── mapper/ProPlanFeatureMapper.java, AnalyticsEventMapper.java, ReportMapper.java, ConciergeServiceMapper.java, FeaturedPlacementMapper.java, SecurityAuditMapper.java, PerformanceMetricMapper.java, AIModelMapper.java
  │   ├── controller/ProPlanController.java, AdvancedAnalyticsController.java, ReportController.java, ConciergeController.java, FeaturedController.java, SecurityController.java, PerformanceController.java, AIController.java
  │   ├── service/ProPlanService.java, AdvancedAnalyticsService.java, ReportService.java, ConciergeService.java, FeaturedService.java, SecurityService.java, PerformanceService.java, AIService.java
  │   └── repository/ProPlanFeatureRepository.java, AnalyticsEventRepository.java, ReportRepository.java, ConciergeServiceRepository.java, FeaturedPlacementRepository.java, SecurityAuditRepository.java, PerformanceMetricRepository.java, AIModelRepository.java
  ├── src/main/resources/
  │   └── db/changelog/V020__pro_plan_features.yaml, V021__advanced_analytics.yaml, V022__concierge_system.yaml, V023__security_compliance.yaml, V024__performance_optimization.yaml, V025__ai_features.yaml
  └── pom.xml
  
  frontend/
  ├── src/
  │   ├── components/pro-plan/ProPlanFeatures.tsx (extend existing card components)
  │   ├── components/analytics/AdvancedAnalytics.tsx (extend existing chart components)
  │   ├── components/reports/ReportGenerator.tsx (extend existing form components)
  │   ├── components/concierge/ConciergeServices.tsx (extend existing card components)
  │   ├── components/featured/FeaturedAgencies.tsx (extend existing card components)
  │   ├── components/security/SecurityAudit.tsx (extend existing table components)
  │   ├── components/performance/PerformanceMetrics.tsx (extend existing chart components)
  │   ├── components/ai/AIInsights.tsx (extend existing chart components)
  │   ├── app/(dashboard)/pro-plan/features/page.tsx (extend dashboard layout patterns)
  │   ├── app/(dashboard)/pro-plan/upgrade/page.tsx (extend dashboard layout patterns)
  │   ├── app/(dashboard)/analytics/advanced/page.tsx (extend dashboard layout patterns)
  │   ├── app/(dashboard)/reports/page.tsx (extend dashboard layout patterns)
  │   ├── app/(dashboard)/concierge/page.tsx (extend dashboard layout patterns)
  │   ├── app/(minimal)/marketplace/featured/page.tsx (extend minimal layout patterns)
  │   ├── app/(dashboard)/security/audit/page.tsx (extend dashboard layout patterns)
  │   ├── app/(dashboard)/performance/metrics/page.tsx (extend dashboard layout patterns)
  │   └── app/(dashboard)/ai/insights/page.tsx (extend dashboard layout patterns)
  └── package.json
  ```
- **Generated diffs**: Will be provided after implementation
- **OpenAPI delta summary**: New advanced feature endpoints
- **Proposed conventional commits (per ticket)**:
  - `feat(pro-plan): implement Pro Plan features and upgrade system [P3.1-E]`
  - `feat(analytics): add advanced analytics and reporting system [P3.1-F]`
  - `feat(concierge): add concierge services and featured placements [P3.1-G]`
  - `feat(security): implement advanced security and compliance features [P3.1-H]`
  - `feat(performance): add performance optimization and scaling features [P3.1-I]`
  - `feat(ai): implement advanced AI features and matching algorithms [P3.1-J]`