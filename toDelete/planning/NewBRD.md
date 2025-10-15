# EasyLuxury — Comprehensive Business Requirements Document

## Table of Contents
1. [Executive Summary](#executive-summary)
2. [Business Objectives](#business-objectives)
3. [Stakeholders](#stakeholders)
4. [Tech Stack](#tech-stack)
5. [Roles & Access](#roles--access)
6. [Domain Model](#domain-model)
7. [API Design](#api-design)
8. [Data Model](#data-model)
9. [UX Flows](#ux-flows)
10. [Phase Plan](#phase-plan)
11. [Scope of Work](#scope-of-work)
12. [User Stories](#user-stories)
13. [Technical Requirements](#technical-requirements)
14. [Success Metrics](#success-metrics)
15. [Revenue Model](#revenue-model)
16. [Risks & Mitigations](#risks--mitigations)
17. [Compliance & Sustainability](#compliance--sustainability)

---

## Executive Summary

EasyLuxury is a mobile-first web platform that enables property owners in Egypt and the Gulf to design, furnish, and manage their properties for personal use or rental income. The platform uses Pre-Approved design recommendations, a marketplace of verified service providers, and integrated rental management to convert empty units into luxury living spaces.

The platform simplifies the finishing and furnishing process for remote or investment property owners by providing localized design recommendations optimized for rental returns, connecting owners to vetted service providers, and streamlining the end-to-end lifecycle from empty unit to furnished rental property.

---

## Business Objectives

- Simplify the finishing and furnishing process for remote or investment property owners
- Provide Pre-Approved, localized design recommendations optimized for rental returns
- Connect owners to vetted service providers (furniture, appliance, cleaning, etc.)
- Streamline the end-to-end lifecycle: from empty unit → furnished → rented
- Increase property ROI via better occupancy and optimized furnishing costs
- Support B2B (developers) and B2C (owners) use cases

---

## Stakeholders

### Primary Stakeholders
- **Agency User** – Register as an agency, offer multiple services (finishing, furniture, cleaning, renting), manage properties and packages, and assign team members to agency account
- **General User** – Register/login, submit property, request styles, and optionally activate agency profile (upon approval) from settings to access management features

### Secondary Stakeholders
- **Property Owners** – Submit properties, manage rentals, track ROI
- **Service Providers** – Furniture vendors, refurbishment agencies, cleaning services
- **Project Managers** – Coordinate between owners and providers
- **Tenants** – Browse and book rental properties
- **Platform Admins** – Moderate content, approve agencies, resolve disputes

---

## Tech Stack

### Backend
- **Java 21** with Spring Boot 3.x
- **Spring Security** with OAuth2 Client
- **JWT** for authentication
- **Hibernate/JPA** for ORM
- **Liquibase** for database migrations
- **MapStruct** for object mapping
- **Jakarta Validation** for input validation
- **Springdoc OpenAPI** for API documentation

### Database & Infrastructure
- **PostgreSQL** as primary database
- **Redis** for sessions and caching
- **S3-compatible object store** (MinIO/S3) for media storage
- **Docker Compose** for development environment
- **GitHub Actions** for CI/CD

### Frontend
- **React 18** with TypeScript
- **Vite** as build tool
- **React Router** for routing
- **React Query (TanStack Query)** for data fetching
- **Tailwind CSS** + **shadcn/ui** for styling
- **Zod** + **React Hook Form** for form validation
- **Axios** for HTTP client
- **PWA** (Progressive Web App) with mobile-first design

### Integrations
- **OAuth Providers**: Google, Apple, Facebook
- **Stripe** for payments and payouts
- **SendGrid/SES** for email services
- **Firebase/OneSignal** for push notifications (Phase 3)
- **Mapbox** for maps and location services
- **ImgProxy/Cloudinary** for image transformations (optional)

### Testing
- **Backend**: JUnit5, Testcontainers, Mockito
- **Frontend**: Vitest + React Testing Library, Playwright for e2e testing

### Observability
- **Spring Boot Actuator** for health checks
- **OpenTelemetry** for distributed tracing (Phase 3)
- **Sentry** for error tracking (Frontend & Backend)

---

## Roles & Access

### User Roles Hierarchy
1. **Visitor** - Read-only browsing access
2. **General User** - Can request services, manage own properties
3. **Agency User** - Organization owner; can list services, create packages, manage team, quotes, bookings; requires admin approval
4. **Agency Staff** - Delegated by Agency User; scoped permissions
5. **Admin** - Approve agencies, moderate content, resolve disputes
6. **SuperAdmin** - Platform configuration and system management

### Permission Matrix
- **Property Management**: General Users, Agency Users, Agency Staff
- **Service Offering**: Agency Users, Agency Staff (with restrictions)
- **Team Management**: Agency Users only
- **Content Moderation**: Admin, SuperAdmin
- **Platform Configuration**: SuperAdmin only

---

## Domain Model

### Core Entities
- **User** - Base user entity with authentication and profile information
- **Session** - User session management
- **OAuthIdentity** - Social login identity linking

### Agency Management
- **Agency** - Service provider organization
- **AgencyMember** - Team members with roles (OWNER|MANAGER|STAFF)

### Service Catalog
- **ServiceType** - Finishing, furniture, cleaning, renting, etc.
- **ServiceOffering** - Agency ↔ service types mapping with prices and SLA metadata
- **Package** - Service packages with type, title, description, price, items, images

### Property & Project Management
- **Property** - Property details including address, geo-location, photos, documents
- **Request/Inquiry** - User service requests
- **Quote** - Agency responses to requests
- **Booking/WorkOrder** - Accepted quotes converted to work orders
- **Invoice/Payment** - Financial transactions

### Quality & Feedback
- **Review/Rating** - User feedback on services
- **Media** - Images and documents
- **Notification** - Email/push/in-app notifications
- **AuditLog** - System activity tracking

---

## API Design

### Base Configuration
- **Base URL**: `/api/v1`
- **Authentication**: Bearer JWT tokens
- **Documentation**: OpenAPI served at `/swagger-ui` and `/v3/api-docs`
- **Error Format**: RFC7807 problem+json
- **Data Format**: snake-free camelCase DTOs with Jakarta validation

### Key Endpoints

#### Authentication
```
POST /auth/register          # Email + password registration
POST /auth/login            # User login
POST /auth/oauth/{provider} # Google/Apple/Facebook OAuth
POST /auth/refresh          # Token refresh
POST /auth/logout           # User logout
```

#### User Management
```
GET  /me                    # Get current user profile
PATCH /me                   # Update user profile
POST /me/properties         # Create user property
GET  /me/properties         # List user properties
```

#### Agency Management
```
POST /agencies              # Submit agency for approval
GET  /agencies/{id}         # Get agency details
PATCH /agencies/{id}        # Update agency information
POST /agencies/{id}/members # Add agency member
DELETE /agencies/{id}/members/{userId} # Remove agency member
POST /agencies/{id}/services # Link service types/pricing
POST /agencies/{id}/packages # Create service package
GET  /agencies/{id}/packages # List agency packages
```

#### Catalog & Search
```
GET /service-types          # Get seeded service types
GET /agencies/search        # Search agencies with filters
```

#### Property Management
```
POST /properties            # Create property
GET  /properties/{id}       # Get property details
PATCH /properties/{id}      # Update property
POST /properties/{id}/media # Upload property media
```

#### Requests & Quotes
```
POST /requests             # Create service request
GET  /requests/{id}         # Get request details
PATCH /requests/{id}        # Update request status
POST /requests/{id}/quotes  # Submit quote response
PATCH /quotes/{id}          # Accept/decline quote
```

#### Bookings & Work Orders
```
POST /bookings             # Create booking from accepted quote
GET  /bookings/{id}        # Get booking details
PATCH /bookings/{id}       # Update booking status
```

#### Payments (Stripe Integration)
```
POST /payments/intent      # Create payment intent (client secret)
POST /payments/webhook     # Stripe webhook handler
```

#### Reviews & Ratings
```
POST /bookings/{id}/reviews # Submit booking review
```

#### Admin Operations
```
GET  /admin/agencies?status=PENDING # Get pending agency approvals
POST /admin/agencies/{id}/approve  # Approve agency
POST /admin/agencies/{id}/reject   # Reject agency
GET  /admin/metrics         # Get platform metrics
```

#### Media Management
```
POST /media/upload         # Pre-signed upload URL
GET  /media/{id}           # Get media file
```

---

## Data Model (ERD)

### Core Tables
```sql
-- User Management
User(id, email, passwordHash, roles[], status, createdAt)
OAuthIdentity(id, userId, provider, providerUserId)

-- Agency Management
Agency(id, ownerUserId, status, name, bio, services[])
AgencyMember(agencyId, userId, role: OWNER|MANAGER|STAFF)

-- Service Catalog
StylePackage(id, name, type: FINISHING|FURNITURE|CLEANING|RENTING, description, images[], priceRange, preApproved)

-- Property & Project Management
Property(id, ownerId, location, size, purpose, budget, photos[])
Project(id, propertyId, managerId?, status, acceptedBidId?, timeline)
Bid(id, projectId, supplierAgencyId, items[], cost, deliveryDays, status)

-- Rental Management
Listing(id, propertyId, title, nightlyPrice?, availabilityMode=MANUAL, visible)
Booking(id, listingId, startDate, endDate, status, holdExpiresAt?)

-- Quality & Feedback
Review(id, subjectType, subjectId, authorId, rating, text)
Media(id, ownerType, ownerId, url, kind, mime)
AuditLog(id, actorUserId, entity, entityId, action, diff, createdAt)
Notification(id, userId, type, payload, readAt)
```

---

## UX Flows (Mobile-First)

### Primary User Journeys

#### Property Owner Journey
1. **Onboarding**: Register/Login → Complete Profile → Add Property
2. **Design Selection**: Browse Style Library → Choose/Request Design → Review Package
3. **Service Request**: Submit Request → Receive Quotes → Compare Offers → Accept Quote
4. **Project Management**: Assign Project Manager → Track Progress → Review Completion
5. **Rental Management**: Set Rental Terms → Publish Listing → Manage Bookings → Track Performance

#### Agency User Journey
1. **Registration**: Submit Agency Application → Admin Approval → Complete Profile
2. **Service Setup**: Configure Services → Create Packages → Set Pricing
3. **Team Management**: Invite Staff → Assign Roles → Manage Permissions
4. **Business Operations**: Receive Requests → Submit Quotes → Manage Bookings → Track Performance

#### Tenant Journey
1. **Discovery**: Browse Listings → Apply Filters → View Details
2. **Booking**: Select Dates → Pay Deposit → Confirm Reservation
3. **Stay Management**: Check-in → Report Issues → Check-out
4. **Feedback**: Leave Review → Rate Experience

### Mobile-First Design Principles
- Touch-friendly interface with appropriate tap targets
- Progressive disclosure of information
- Offline capability for core features
- Fast loading with optimized images
- Intuitive navigation patterns

---

## Phase Plan (3 Phases)

### Phase 1 — Foundations & Onboarding (MVP)
**Duration**: Q3 2025

**Core Features**:
- Authentication (email/password + Google/Apple/Facebook OAuth)
- JWT-based session management
- User profile management
- Property CRUD operations
- Media upload functionality
- Agency submission and admin approval workflow
- Service types catalog
- Agency services and package management
- Public browse/search for agencies and packages
- Request system (user → agency)
- Basic quote reply system (no payments)
- PWA shell with responsive UI
- Basic email notifications

**Success Criteria**:
- Users can register and submit properties
- Agencies can register and get approved
- Basic request/quote workflow functions
- Mobile-responsive interface works across devices

### Phase 2 — Operations & Monetization
**Duration**: Q4 2025

**Core Features**:
- Quote acceptance workflow
- Bookings and Work Orders lifecycle management
- Stripe payment integration (intents, webhooks)
- Invoice generation and management
- Platform fee collection and agency payouts
- Review and rating system
- Team management with granular permissions
- Activity feed and audit logging
- Comprehensive dashboards (agency + admin)
- Advanced filtering, pagination, and sorting
- In-app notification system
- Email template management

**Success Criteria**:
- Complete payment flow works end-to-end
- Agencies can manage teams effectively
- Review system improves service quality
- Platform generates revenue through transactions

### Phase 3 — Marketplace & Enhancements
**Duration**: Q1 2026

**Core Features**:
- Advanced search with map bounds, price ranges, availability
- Calendar integration and scheduling
- Push notifications via Firebase/OneSignal
- Content moderation tools
- Dispute resolution workflows
- Analytics integration (OpenTelemetry + Sentry)
- Data export capabilities (CSV)
- AI assistance features:
  - Brief/form generation from user prompts
  - Quote templating
- Internationalization (English/Arabic)
- CMS blocks for marketing pages

**Success Criteria**:
- Advanced marketplace features drive user engagement
- AI features improve user experience
- Platform supports multiple languages
- Comprehensive analytics provide business insights

---

## Scope of Work

### User Types & Capabilities

#### General User
- Browse available services and agencies
- Submit property details and requirements
- Manage personal properties and rentals
- Request quotes and manage bookings
- Leave reviews and ratings
- Optional agency profile activation (requires approval)

#### Agency User
- Register and verify business profile
- Specify service offerings (finishing, furniture, cleaning, renting)
- Create and manage service packages
- Invite and manage team members
- Receive and respond to service requests
- Submit quotes and manage bookings
- Access agency-specific dashboards
- Track performance and analytics

#### Agency Staff
- Access delegated permissions from agency owner
- Manage assigned properties and packages
- Respond to requests within scope
- Update project status and milestones
- Upload completion documentation

### Service Categories

#### Finishing Services
- Complete refurbishment packages
- Material selection and sourcing
- Quality control and project management
- Timeline and milestone tracking

#### Furniture Services
- Furniture selection and procurement
- Custom furniture design and manufacturing
- Delivery and installation
- Inventory management

#### Cleaning Services
- One-time deep cleaning
- Scheduled maintenance cleaning
- Rental turnover cleaning
- Quality inspection and reporting

#### Rental Management
- Property listing and marketing
- Booking management and calendar sync
- Guest communication and support
- Revenue tracking and reporting

### Platform Features

#### Property Owner Portal
- Property submission wizard
- Visual style library and selection
- Quote comparison and negotiation
- Project tracking and milestone updates
- Rental management dashboard
- Performance analytics and reporting

#### Service Provider Dashboard
- Business profile management
- Portfolio and work sample uploads
- Request management and quote submission
- Project tracking and completion documentation
- Team management and delegation
- Performance analytics and client feedback

#### Admin Panel
- User and agency approval workflow
- Content moderation and dispute resolution
- Platform metrics and analytics
- Featured content and provider management
- System configuration and maintenance

---

## User Stories

### Property Owner Stories
- As a property owner, I want to submit my property details so that I can initiate the furnishing process
- As a property owner, I want to choose from pre-approved design styles so that I can visualize my future space
- As a property owner, I want to compare offers from different service providers so that I can choose the best deal
- As a property owner, I want to assign a project manager so that I have a single point of contact
- As a property owner, I want to track project milestones and receive photo updates so that I stay informed
- As a property owner, I want to set rental terms and manage bookings so that I can maximize my ROI
- As a property owner, I want to delegate rental management to an agency so that I can remain hands-off
- As a property owner, I want to access analytics on bookings and performance so that I can evaluate ROI

### Service Provider Stories
- As a furniture vendor, I want to register and verify my profile so that I can access potential projects
- As a furniture vendor, I want to upload my portfolio so that property owners can see examples of my work
- As a furniture vendor, I want to receive project invitations so that I can submit targeted offers
- As a furniture vendor, I want to submit bids with material lists and timelines so that owners have complete information
- As a refurbishment agency, I want to offer bundled services so that owners can book complete packages
- As a cleaning agency, I want to offer scheduled services so that I can support rental readiness
- As a service provider, I want to respond to reviews so that I can maintain my reputation

### Project Manager Stories
- As a project manager, I want to view all ongoing projects so that I can ensure progress and deadlines
- As a project manager, I want to communicate with vendors and owners in one place so that coordination is efficient
- As a project manager, I want to log milestones and delays so that stakeholders stay informed

### Tenant Stories
- As a tenant, I want to browse listings with filters so that I find suitable accommodations
- As a tenant, I want to book rentals and pay deposits online so that the process is smooth
- As a tenant, I want to leave reviews after my stay so that I can help others
- As a tenant, I want to report issues during my stay so that they can be resolved promptly

### Admin Stories
- As an admin, I want to manage all users and projects so that I ensure platform compliance
- As an admin, I want to approve or reject service providers so that only verified professionals operate
- As an admin, I want to feature high-performing providers so that quality is promoted
- As an admin, I want to intervene in disputes so that trust and satisfaction remain high
- As an admin, I want to monitor analytics and usage trends so that I can inform platform decisions

---

## Technical Requirements

### Performance Requirements
- Page load times < 3 seconds on mobile
- API response times < 500ms for 95% of requests
- Support for 1000+ concurrent users
- 99.9% uptime SLA

### Security Requirements
- OAuth2 + JWT authentication
- HTTPS encryption for all communications
- Input validation and sanitization
- SQL injection prevention
- XSS protection
- CSRF protection
- Role-based access control

### Scalability Requirements
- Horizontal scaling capability
- Database connection pooling
- CDN integration for static assets
- Caching strategy for frequently accessed data
- Microservices architecture readiness

### Integration Requirements
- Stripe payment processing
- Email service integration (SendGrid/SES)
- Social login providers (Google, Apple, Facebook)
- Map services (Mapbox)
- Push notification services (Firebase/OneSignal)
- Image processing services (ImgProxy/Cloudinary)

---

## Success Metrics

### User Engagement Metrics
- Monthly Active Users (MAU)
- Daily Active Users (DAU)
- User retention rates (Day 1, Day 7, Day 30)
- Session duration and frequency
- Feature adoption rates

### Business Metrics
- Number of properties submitted
- Number of agencies registered and approved
- Quote acceptance rates
- Average project value
- Platform revenue and commission collected
- Customer satisfaction scores (NPS)

### Technical Metrics
- System uptime and availability
- API response times and error rates
- Mobile app performance metrics
- Security incident frequency
- Data processing accuracy

### Quality Metrics
- Review ratings and feedback scores
- Dispute resolution times
- Service completion rates
- Customer support ticket volume
- Platform trust and safety metrics

---

## Revenue Model

### Primary Revenue Streams
- **Commission on Deals**: 8-12% on total project value
- **Rental Service Fee**: 5-10% of booking value (when active)
- **Agency Subscription Plans**: Monthly/annual fees for property managers

### Future Revenue Opportunities
- Premium service packages
- Insurance and warranty products
- Maintenance and support services
- Advertising and featured listings
- Data analytics and insights
- White-label solutions for developers

### Pricing Strategy
- Competitive commission rates to attract service providers
- Value-based pricing for premium features
- Freemium model for basic property management
- Volume discounts for enterprise clients

---

## Risks & Mitigations

### Technical Risks
- **Risk**: System scalability challenges
- **Mitigation**: Cloud-native architecture with auto-scaling capabilities

- **Risk**: Data security breaches
- **Mitigation**: Comprehensive security audit, encryption, and monitoring

- **Risk**: Third-party service dependencies
- **Mitigation**: Multiple provider options and fallback mechanisms

### Business Risks
- **Risk**: Low user adoption
- **Mitigation**: Strong onboarding flow, referral programs, and marketing

- **Risk**: Quality control issues
- **Mitigation**: Rigorous provider vetting and review systems

- **Risk**: Regulatory compliance
- **Mitigation**: Legal review and compliance monitoring

### Market Risks
- **Risk**: Competition from established players
- **Mitigation**: Focus on unique value proposition and local market expertise

- **Risk**: Economic downturn affecting property investment
- **Mitigation**: Diversified service offerings and flexible pricing

---

## Compliance & Sustainability

### Data Protection
- GDPR-compliant user data handling
- User consent management
- Data retention policies
- Right to deletion and portability
- Privacy by design principles

### Environmental Considerations
- Enable eco-friendly material filtering
- Promote sustainable service providers
- Carbon footprint tracking for deliveries
- Green building certification support

### Ethical Standards
- Transparent provider reviews and ratings
- Fair pricing and commission structures
- Anti-discrimination policies
- Worker rights protection
- Community impact assessment

### Operational Compliance
- Role-based access control
- Audit logging and monitoring
- Uptime SLA > 99%
- Data backup and recovery procedures
- Incident response protocols

---

## Conclusion

EasyLuxury represents a comprehensive platform that addresses the complex needs of property owners, service providers, and tenants in the Middle East luxury property market. Through a phased approach, the platform will evolve from a basic marketplace to a sophisticated ecosystem that leverages AI, mobile-first design, and integrated payment systems to deliver exceptional user experiences and sustainable business growth.

The success of EasyLuxury depends on careful execution of the technical architecture, strong user onboarding processes, and continuous improvement based on user feedback and market demands. With proper implementation of the outlined features and adherence to the defined success metrics, EasyLuxury is positioned to become the leading platform for luxury property management in the region.
