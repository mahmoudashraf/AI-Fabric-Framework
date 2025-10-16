# PLAN — Batch 4 (Tickets: P1.1-G, P1.1-H)

## 0) Summary
- **Goal**: Implement admin panel and notification system to complete Phase 1 of AIMatchly platform
- **Architecture note**: Comprehensive admin dashboard with moderation tools and notification system for user communication
- **Tickets covered**: P1.1-G (Admin Panel & Moderation), P1.1-H (Basic Notifications & Email)
- **Non-goals / out of scope**: Advanced analytics, complex notification rules, payment integration

## 1) Backend changes
- **Entities (new/updated)**: 
  - `AuditLog` (id, entityType, entityId, action, userId, details, createdAt)
  - `Notification` (id, userId, type, title, message, read, createdAt, updatedAt)
  - `EmailTemplate` (id, name, subject, body, variables, active, createdAt, updatedAt)
  - `AdminAction` (id, adminId, action, targetType, targetId, details, createdAt)

- **Liquibase changesets**:
  - `V008__admin_system.yaml` - Create audit_logs, notifications, email_templates, admin_actions tables
  - `V009__notification_system.yaml` - Add notification preferences and delivery tracking

- **Endpoints (method → path → purpose)**:
  - `GET /api/admin/agencies` → List all agencies with filtering
  - `GET /api/admin/briefs` → List all briefs with filtering
  - `GET /api/admin/metrics` → Get platform metrics
  - `POST /api/admin/agencies/{id}/verify` → Verify agency
  - `POST /api/admin/agencies/{id}/featured` → Feature agency
  - `POST /api/admin/agencies/{id}/suspend` → Suspend agency
  - `GET /api/admin/audit-logs` → Get audit logs
  - `GET /api/notifications` → Get user notifications
  - `POST /api/notifications/mark-read` → Mark notification as read
  - `POST /api/notifications/send` → Send notification (admin)
  - `GET /api/email-templates` → Get email templates
  - `POST /api/email-templates` → Create email template

- **Admin Panel Features**:
  - Agency management and verification
  - Brief monitoring and moderation
  - Platform metrics dashboard
  - Audit log viewing and filtering
  - Content moderation tools
  - Featured agency management

- **Notification System Features**:
  - In-app notifications
  - Email notifications
  - Notification preferences
  - Email template management
  - Notification delivery tracking

- **DTOs & mappers**:
  - `AuditLogDto`, `AuditLogMapper` (MapStruct)
  - `NotificationDto`, `NotificationMapper` (MapStruct)
  - `EmailTemplateDto`, `EmailTemplateMapper` (MapStruct)
  - `AdminActionDto`, `AdminActionMapper` (MapStruct)
  - `PlatformMetricsDto`, `PlatformMetricsMapper` (MapStruct)

- **Services**:
  - `AdminService` - Admin operations and moderation
  - `AuditService` - Audit logging and tracking
  - `NotificationService` - Notification management and delivery
  - `EmailService` - Email template and delivery
  - `MetricsService` - Platform metrics and analytics

- **Validation rules**:
  - Admin action validation
  - Notification content validation
  - Email template validation
  - Audit log validation

- **Error model (problem+json codes)**:
  - `400` - Validation errors
  - `401` - Unauthorized
  - `403` - Forbidden (admin only)
  - `404` - Resource not found
  - `500` - Email service error

## 2) Frontend changes (Next.js App Router)
- **Routes/pages**:
  - `/admin/dashboard` - Admin dashboard overview
  - `/admin/agencies` - Agency management
  - `/admin/briefs` - Brief monitoring
  - `/admin/metrics` - Platform metrics
  - `/admin/audit-logs` - Audit log viewer
  - `/admin/email-templates` - Email template management
  - `/notifications` - User notification center
  - `/notifications/settings` - Notification preferences

- **Components (reusing existing)**:
  - **Dashboard**: Extend `LatestCustomerTableCard` from `/components/dashboard/Analytics/` for admin metrics
  - **Tables**: Reuse table patterns from `/components/users/list/Style1/UserList.tsx` for admin lists
  - **Cards**: Use `MainCard`, `SubCard` from `/components/ui-component/cards/` for admin sections
  - **Forms**: Extend existing form components for admin actions
  - **Status indicators**: Use `Chip` component from `/components/ui-component/extended/Chip.tsx` for status
  - **Modals**: Use existing modal components for admin actions

- **Data fetching (React Query keys)**:
  - `['admin', 'agencies']` - Admin agency list
  - `['admin', 'briefs']` - Admin brief list
  - `['admin', 'metrics']` - Platform metrics
  - `['admin', 'audit-logs']` - Audit logs
  - `['notifications']` - User notifications
  - `['email-templates']` - Email templates

- **Forms & validation (RHF + Zod)**:
  - Admin action forms (verify, suspend, feature)
  - Email template creation/editing form
  - Notification preference form
  - Audit log filtering form

- **Admin Panel Features**:
  - Comprehensive dashboard with key metrics
  - Agency management with bulk actions
  - Brief monitoring and moderation
  - Audit log viewer with filtering
  - Email template management

- **Notification Features**:
  - Notification center with read/unread states
  - Notification preferences management
  - Email template editor
  - Notification delivery tracking

- **Accessibility notes**: 
  - ARIA labels for admin actions
  - Keyboard navigation support
  - Screen reader friendly admin interface
  - Notification accessibility

## 3) OpenAPI & Types
- **Springdoc annotations**:
  - `@Operation` for all endpoints
  - `@ApiResponse` for error codes
  - `@SecurityRequirement` for JWT authentication
  - `@Tag` for endpoint grouping

- **Regenerate FE types**:
  - Generate TypeScript types from OpenAPI spec
  - Create new types for admin, notification, and email functionality
  - Update existing types for enhanced features

## 4) Tests
- **BE unit/slice**:
  - `AdminServiceTest` - Admin operations and moderation
  - `AuditServiceTest` - Audit logging and tracking
  - `NotificationServiceTest` - Notification management
  - `EmailServiceTest` - Email template and delivery
  - `MetricsServiceTest` - Platform metrics
  - `AdminControllerTest` - Admin endpoints

- **BE integration (Testcontainers)**:
  - `AdminControllerIntegrationTest` - Admin operations flow
  - `NotificationControllerIntegrationTest` - Notification system flow
  - `EmailControllerIntegrationTest` - Email template management

- **FE unit**:
  - `AdminDashboard.test.tsx` - Admin dashboard testing
  - `AgencyManagement.test.tsx` - Agency management testing
  - `NotificationCenter.test.tsx` - Notification center testing
  - `EmailTemplateEditor.test.tsx` - Email template testing

- **E2E happy-path (Playwright)**:
  - Admin login → Agency management → Verification workflow
  - Notification system → Email template management
  - Audit log viewing → Admin actions

- **Coverage target**: 80%+ for admin services, 70%+ overall

## 5) Seed & Sample Data (dev only)
- **What to seed**:
  - Admin user with full permissions
  - Sample agencies in different statuses
  - Sample briefs for monitoring
  - Email templates for common notifications
  - Sample audit logs
  - Platform metrics data

- **How to run**:
  - `mvn liquibase:update` - Apply migrations
  - `mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"` - Run with seed data

## 6) Acceptance Criteria Matrix
- **P1.1-G**:
  - [ ] AC#1: Admin dashboard with key metrics and overview
  - [ ] AC#2: Agency management with verification and moderation tools
  - [ ] AC#3: Brief monitoring and content moderation
  - [ ] AC#4: Audit logging for all admin actions
  - [ ] AC#5: Featured agency management

- **P1.1-H**:
  - [ ] AC#1: In-app notification system with read/unread states
  - [ ] AC#2: Email notifications for key events
  - [ ] AC#3: Notification preference management
  - [ ] AC#4: Email template management system
  - [ ] AC#5: Notification delivery tracking

## 7) Risks & Rollback
- **Risks**:
  - Admin panel complexity and user experience
  - Email service reliability
  - Notification system performance
  - Frontend component integration

- **Mitigations**:
  - Progressive admin panel enhancement
  - Email service error handling and fallbacks
  - Notification system optimization
  - Extensive component testing

- **Rollback plan**:
  - Revert admin panel enhancements
  - Disable email notifications
  - Rollback notification system
  - Restore basic admin functionality

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
  │   ├── entity/AuditLog.java, Notification.java, EmailTemplate.java, AdminAction.java
  │   ├── dto/AuditLogDto.java, NotificationDto.java, EmailTemplateDto.java, PlatformMetricsDto.java
  │   ├── mapper/AuditLogMapper.java, NotificationMapper.java, EmailTemplateMapper.java
  │   ├── controller/AdminController.java, NotificationController.java, EmailController.java
  │   ├── service/AdminService.java, AuditService.java, NotificationService.java, EmailService.java, MetricsService.java
  │   └── repository/AuditLogRepository.java, NotificationRepository.java, EmailTemplateRepository.java
  ├── src/main/resources/
  │   ├── application.yml (email configuration)
  │   └── db/changelog/V008__admin_system.yaml, V009__notification_system.yaml
  └── pom.xml (email dependencies)
  
  frontend/
  ├── src/
  │   ├── components/admin/AdminDashboard.tsx (extend existing dashboard components)
  │   ├── components/admin/AgencyManagement.tsx (extend existing table components)
  │   ├── components/admin/AuditLogViewer.tsx (extend existing table components)
  │   ├── components/notifications/NotificationCenter.tsx (extend existing card components)
  │   ├── components/notifications/EmailTemplateEditor.tsx (extend existing form components)
  │   ├── app/(dashboard)/admin/dashboard/page.tsx (extend dashboard layout patterns)
  │   ├── app/(dashboard)/admin/agencies/page.tsx (extend dashboard layout patterns)
  │   ├── app/(dashboard)/admin/briefs/page.tsx (extend dashboard layout patterns)
  │   ├── app/(dashboard)/admin/metrics/page.tsx (extend dashboard layout patterns)
  │   ├── app/(dashboard)/admin/audit-logs/page.tsx (extend dashboard layout patterns)
  │   ├── app/(dashboard)/notifications/page.tsx (extend dashboard layout patterns)
  │   └── app/(dashboard)/notifications/settings/page.tsx (extend dashboard layout patterns)
  └── package.json
  ```
- **Generated diffs**: Will be provided after implementation
- **OpenAPI delta summary**: New admin and notification endpoints
- **Proposed conventional commits (per ticket)**:
  - `feat(admin): implement comprehensive admin panel and moderation tools [P1.1-G]`
  - `feat(notifications): add notification system and email management [P1.1-H]`