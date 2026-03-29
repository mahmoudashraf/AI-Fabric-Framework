# Platform User Types Guide

Status: current branch guide (2026-03-29)

This guide explains the main user types for the AI Enablement Platform in `Platfrom/`, what each user is responsible for, and which detailed guide they should follow.

The platform is built for a **control plane / data plane** model:

- the **platform** is where deployments are created, configured, versioned, applied, and verified
- the **runtime** and **REST connector** remain immutable deployment targets

The main user of the platform today is the **internal AI enablement operator**. Customer-facing users and vertical consumers are supported, but with different scopes.

---

## 1) User Types At A Glance

### 1.1 Platform Admin

Primary user for:

- platform setup
- secret management
- deployment governance
- privileged troubleshooting

Current status:

- **implemented**

Guide:

- `Final_Documentation/User_Guides/PLATFORM_ADMIN_USER_GUIDE.md`

### 1.2 Platform Operator

Primary user for:

- day-to-day deployment operations
- draft editing
- publish/apply flows
- verification and diagnostics

Current status:

- **implemented**

Guide:

- `Final_Documentation/User_Guides/PLATFORM_OPERATOR_USER_GUIDE.md`

### 1.3 Customer Admin

Primary user for:

- customer-side deployment ownership
- customer-safe lifecycle visibility
- coordination with your enablement team

Current status:

- **product persona defined**
- **separate backend role not yet enforced**

Guide:

- `Final_Documentation/User_Guides/CUSTOMER_ADMIN_USER_GUIDE.md`

### 1.4 Customer Operator

Primary user for:

- monitoring deployment health
- checking release/verification state
- providing operational context back to support or implementation teams

Current status:

- **product persona defined**
- **separate backend role not yet enforced**

Guide:

- `Final_Documentation/User_Guides/CUSTOMER_OPERATOR_USER_GUIDE.md`

### 1.5 Public API Client

Primary user for:

- vertical backends such as a future Shopify app backend
- programmatic deployment creation and lifecycle requests

Current status:

- **implemented**

Guide:

- `Final_Documentation/User_Guides/PUBLIC_API_CLIENT_USER_GUIDE.md`

---

## 2) Current Role Matrix

This matrix reflects the current branch behavior, not the final long-term product role model.

| Capability | Platform Admin | Platform Operator | Customer Admin | Customer Operator | Public API Client |
|---|---|---|---|---|---|
| Sign in to platform UI | Yes | Yes | Planned | Planned | No |
| Create deployment from UI | Yes | Yes | Planned customer-safe flow | No | No |
| Edit draft config | Yes | Yes | Planned limited flow | No | No |
| Publish/apply versions from UI | Yes | Yes | Planned limited flow | No | No |
| Rerun verification | Yes | Yes | Planned view/request flow | No | No |
| View diagnostics | Yes | Yes | Planned customer-safe diagnostics | Planned customer-safe diagnostics | No |
| View audit events | Yes | Yes | Planned limited visibility | No | No |
| List platform secrets | Yes | Yes | No | No | No |
| Update/clear platform secrets | Yes | No | No | No | No |
| Use public provisioning API | No | No | No | No | Yes |

---

## 3) What Is Implemented Today

Implemented now:

- platform session login/logout
- platform admin/operator backend role separation
- deployment creation
- draft editing for:
  - actions
  - knowledge/entity config
  - providers
  - security
  - connector routing
- validation
- publish/apply
- Railway plan preview and Railway provisioning support
- verification and diagnostics
- platform secret management
- public provisioning API for machine clients

Not fully implemented yet:

- separate customer-admin and customer-operator auth roles
- org/project ownership isolation
- OIDC / enterprise SSO
- Shopify backend integration on top of the public API

---

## 4) Entry Points

### 4.1 Platform UI

Typical local development entry point:

- `http://localhost:5173`

Used by:

- Platform Admin
- Platform Operator
- later Customer Admin / Customer Operator

### 4.2 Platform Backend

Typical local development backend:

- `http://localhost:8088`

Important routes:

- `GET /api/platform/overview`
- `POST /api/platform/auth/login`
- `POST /api/platform/auth/logout`
- `GET /api/platform/auth/session`

### 4.3 Public Provisioning API

Public machine-client routes:

- `POST /api/public/deployments`
- `GET /api/public/deployments/{deploymentId}`
- `GET /api/public/deployments/{deploymentId}/status`
- `POST /api/public/deployments/{deploymentId}/apply`
- `GET /api/public/deployments/{deploymentId}/credentials`

Used by:

- Public API Client
- future Shopify backend

---

## 5) Recommended Operating Model

For your startup product right now:

- **Platform Admin** should be a small trusted internal group
- **Platform Operator** should be the main day-to-day user
- **Customer Admin** should be introduced through the customer-facing lifecycle UX, but with careful scoping
- **Customer Operator** should be primarily read/observe oriented
- **Public API Client** should be used by vertical backends, not browsers

This keeps the platform safe while still letting you productize deployment creation and integration.

---

## 6) Companion Docs

- `changes/Productization/CONFIGURABLE_AI_ENABLEMENT_PLATFORM_PLAN.md`
- `changes/Productization/PLATFORM_PHASE_18_PLUS_EXECUTION_PLAN.md`
- `changes/Productization/PLATFORM_PUBLIC_PROVISIONING_API_CONTRACT.md`
- `Final_Documentation/User_Guides/PRODUCT_REST_BRANCH_USER_GUIDE.md`

