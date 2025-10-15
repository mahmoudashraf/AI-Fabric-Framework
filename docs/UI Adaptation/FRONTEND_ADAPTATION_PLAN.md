# 🏗️ Frontend Adaptation Plan - EasyLuxury Platform

**Document Purpose:** Comprehensive plan to adapt the current Material-UI template frontend to the EasyLuxury property management marketplace platform

**Created:** October 2025  
**Status:** Planning Phase  
**Alignment:** Based on `/planning` directory requirements

---

## 📋 Table of Contents

1. [Executive Summary](#executive-summary)
2. [Current State Analysis](#current-state-analysis)
3. [Gap Analysis](#gap-analysis)
4. [Adaptation Strategy](#adaptation-strategy)
5. [Phase 1 Implementation Plan](#phase-1-implementation-plan)
6. [Phase 2 Implementation Plan](#phase-2-implementation-plan)
7. [Phase 3 Implementation Plan](#phase-3-implementation-plan)
8. [Component Mapping](#component-mapping)
9. [Technical Requirements](#technical-requirements)
10. [Success Criteria](#success-criteria)

---

## 📌 Related Documentation

**IMPORTANT:** This plan should be read together with:
- **[COMPONENT_MAPPING_GUIDE.md](COMPONENT_MAPPING_GUIDE.md)** - Detailed page-to-page mappings and widget reuse recommendations
- **[DEVELOPER_GUIDE.md](../DEVELOPER_GUIDE.md)** - Existing enterprise patterns to follow
- **[TECHNICAL_ARCHITECTURE.md](../TECHNICAL_ARCHITECTURE.md)** - Current architecture reference

---

## 🎯 Executive Summary

### What We're Building
**EasyLuxury** - A mobile-first web platform that enables property owners in Egypt and the Gulf to design, furnish, and manage their properties for personal use or rental income.

### Current Frontend State
- **Template:** Material-UI v7 admin template
- **Architecture:** Modern (Context API + React Query)
- **Patterns:** Enterprise-grade (established in recent modernization)
- **Status:** Generic template, needs domain-specific adaptation

### Transformation Required
- **Complete Domain Shift:** From generic admin template → Property management marketplace
- **Scope:** 3 phases, 15+ tickets, 50+ components
- **Approach:** Leverage existing patterns, build domain-specific features
- **Timeline:** Q3 2025 (Phase 1) → Q1 2026 (Phase 3)

---

## 📊 Current State Analysis

### ✅ What We Have (Can Leverage)

#### 1. **Enterprise Architecture** ✅
```typescript
// Already implemented and tested
- Context API (15 contexts)
- React Query for server state
- useAdvancedForm<T> for forms
- useTableLogic<T> for tables
- useAsyncOperation<T> for async with retry
- withErrorBoundary HOC
- 180+ tests
```

#### 2. **UI Components** ✅
```
Current Components (Generic):
├── Dashboard (analytics, charts)
├── User Management (list, profile, cards)
├── Authentication (login, register, OAuth)
├── Calendar (event management)
├── E-commerce (product list, checkout)
├── Customer Management (list, orders)
├── Forms (validation, wizard, tables)
└── Enterprise Components (HOCs, performance)
```

#### 3. **Infrastructure** ✅
```
- Material-UI v7 (latest)
- TypeScript (100% coverage)
- React Query (configured)
- Context providers (established)
- Error handling (comprehensive)
- Testing infrastructure (Vitest, RTL)
- Performance optimization (virtual scrolling, code splitting)
```

### ❌ What We Need (Must Build)

#### 1. **Domain-Specific Components**
```
Required for EasyLuxury:
├── Property Management
│   ├── Property submission wizard
│   ├── Property detail views
│   ├── Media upload (photos, documents)
│   └── Property listing cards
├── Agency Management
│   ├── Agency registration flow
│   ├── Agency profile pages
│   ├── Package management
│   └── Team management
├── Project Management
│   ├── Project dashboard
│   ├── Bid submission/comparison
│   ├── Timeline/milestone tracking
│   └── Project notes
├── Marketplace
│   ├── Service provider discovery
│   ├── Style package library
│   ├── Search and filters
│   └── Public provider profiles
└── Rental Management
    ├── Listing management
    ├── Booking calendar
    ├── Tenant booking flow
    └── Availability management
```

#### 2. **Domain Models & Types**
```typescript
// New TypeScript interfaces needed
- User, Agency, AgencyMember
- Property, StylePackage
- Project, Bid, WorkOrder
- Listing, Booking
- Review, Rating
- Invoice, Payment
- Notification
```

#### 3. **Business Logic & Contexts**
```typescript
// New contexts needed
- PropertyContext (property management)
- AgencyContext (agency operations)
- ProjectContext (project lifecycle)
- MarketplaceContext (search, filters)
- BookingContext (rental management)
```

---

## 🔍 Gap Analysis

### Component Reusability Matrix

| Current Component | EasyLuxury Use Case | Adaptation Required | Priority |
|-------------------|---------------------|---------------------|----------|
| **Dashboard** | Owner/Agency analytics | Moderate (rebrand metrics) | P1 |
| **User List** | Admin user management | Low (minor changes) | P1 |
| **User Profile** | User/Agency profiles | Moderate (add agency fields) | P1 |
| **Authentication** | Login/Register/OAuth | Low (update branding) | P1 |
| **Calendar** | Booking calendar | High (rental-specific logic) | P2 |
| **E-commerce Product List** | Property listings | Moderate (property cards) | P1 |
| **Customer Orders** | Project/Booking tracking | Moderate (domain-specific) | P2 |
| **Form Wizard** | Property submission | Low (customize steps) | P1 |
| **Form Tables** | Agency packages | Low (domain data) | P1 |
| **Chat** | Project notes | High (project-specific) | P2 |
| **Contact Management** | Tenant management | Moderate (rental context) | P2 |

**Reusability Score:** ~40% of components can be adapted, 60% need to be built new

---

## 🎯 Adaptation Strategy

### Core Principles

1. **Leverage Enterprise Patterns** ✅
   - Use useAdvancedForm for ALL forms
   - Use useTableLogic for ALL tables
   - Wrap ALL pages with withErrorBoundary
   - Use React Query for ALL API calls

2. **Domain-Driven Design** 🎯
   - Organize by business domain (Property, Agency, Project, Marketplace)
   - Create domain-specific contexts
   - Build reusable domain components

3. **Progressive Enhancement** 📈
   - Phase 1: Core functionality (MVP)
   - Phase 2: Operations & monetization
   - Phase 3: Advanced features

4. **Mobile-First** 📱
   - All components responsive by default
   - Touch-friendly interactions
   - Progressive Web App (PWA)

---

## 🚀 Phase 1 Implementation Plan (MVP Core)

**Timeline:** Q3 2025  
**Goal:** Auth, Agency activation, Property management, Basic rental listing

### Phase 1 Directory Structure

```
frontend/src/
├── components/
│   ├── property/                    # NEW
│   │   ├── PropertyWizard/         # Multi-step property submission
│   │   ├── PropertyCard/           # Property display card
│   │   ├── PropertyDetail/         # Full property view
│   │   ├── MediaUpload/            # Photo/document upload
│   │   └── StyleSelector/          # Style library selection
│   ├── agency/                      # NEW
│   │   ├── AgencyRegistration/     # Agency signup flow
│   │   ├── AgencyProfile/          # Agency detail page
│   │   ├── PackageManager/         # Service packages CRUD
│   │   ├── PackageCard/            # Package display
│   │   └── AgencyApproval/         # Admin approval interface
│   ├── project/                     # NEW
│   │   ├── ProjectDashboard/       # Project overview
│   │   ├── BidCard/                # Bid display & comparison
│   │   ├── ProjectTimeline/        # Milestone tracking
│   │   └── BidSubmission/          # Agency bid form
│   ├── marketplace/                 # NEW
│   │   ├── ProviderSearch/         # Agency search & filters
│   │   ├── StyleLibrary/           # Pre-approved styles
│   │   └── PublicProfile/          # Public agency page
│   ├── rental/                      # NEW
│   │   ├── ListingForm/            # Create/edit listing
│   │   ├── ListingCard/            # Listing display
│   │   ├── BookingCalendar/        # Manual booking calendar
│   │   └── BookingForm/            # Manual booking entry
│   ├── admin/                       # ADAPT existing
│   │   ├── AgencyApprovalList/     # Pending agencies
│   │   ├── UserManagement/         # User admin (reuse)
│   │   └── AdminDashboard/         # Platform metrics (reuse)
│   └── shared/                      # LEVERAGE existing
│       ├── layout/                  # Keep existing layout
│       ├── navigation/              # Update menu items
│       └── enterprise/              # Keep all enterprise patterns
│
├── contexts/                        # NEW + EXISTING
│   ├── PropertyContext.tsx          # NEW: Property management
│   ├── AgencyContext.tsx            # NEW: Agency operations
│   ├── ProjectContext.tsx           # NEW: Project lifecycle
│   ├── MarketplaceContext.tsx       # NEW: Search & discovery
│   ├── RentalContext.tsx            # NEW: Booking management
│   └── [Keep all existing contexts]
│
├── hooks/                           # NEW + EXISTING
│   ├── useProperty.ts               # NEW: Property operations
│   ├── useAgency.ts                 # NEW: Agency operations
│   ├── useProject.ts                # NEW: Project operations
│   ├── useBid.ts                    # NEW: Bid operations
│   ├── useBooking.ts                # NEW: Booking operations
│   └── enterprise/                  # KEEP: All established patterns
│
├── types/                           # NEW
│   ├── property.ts                  # Property domain types
│   ├── agency.ts                    # Agency domain types
│   ├── project.ts                   # Project domain types
│   ├── marketplace.ts               # Marketplace types
│   └── rental.ts                    # Rental domain types
│
└── views/                           # NEW + ADAPT
    ├── property/                    # NEW
    │   ├── submit.tsx              # Property submission page
    │   ├── [id].tsx                # Property detail page
    │   ├── list.tsx                # My properties list
    │   └── style-select.tsx        # Style selection page
    ├── agency/                      # NEW
    │   ├── register.tsx            # Agency registration
    │   ├── [id].tsx                # Agency profile (public)
    │   ├── dashboard.tsx           # Agency dashboard
    │   └── packages.tsx            # Package management
    ├── project/                     # NEW
    │   ├── [id].tsx                # Project detail
    │   ├── list.tsx                # My projects
    │   └── timeline.tsx            # Project timeline
    ├── marketplace/                 # NEW
    │   ├── search.tsx              # Provider search
    │   ├── styles.tsx              # Style library
    │   └── provider/[id].tsx       # Provider profile
    ├── rental/                      # NEW
    │   ├── listings/[id].tsx       # Listing detail (public)
    │   ├── manage.tsx              # My listings
    │   └── bookings.tsx            # Booking management
    ├── admin/                       # ADAPT
    │   ├── agencies.tsx            # Agency approvals
    │   ├── users.tsx               # User management (reuse)
    │   └── dashboard.tsx           # Admin dashboard (adapt)
    └── authentication/              # ADAPT
        ├── login.tsx               # Update branding
        ├── register.tsx            # Update for EasyLuxury
        └── oauth-callback.tsx      # NEW: OAuth handling
```

---

## 📦 Phase 1 Ticket Breakdown

### Ticket P1.1-A: Auth & RBAC (Supabase JWT verify)

**Backend:** Spring Boot + Supabase JWT verification  
**Frontend Changes:**

#### Components to Create:
```typescript
// 1. Update existing auth components
components/authentication/
├── SupabaseAuthProvider.tsx     # NEW: Supabase integration
├── LoginForm.tsx                # ADAPT: Use Supabase auth
├── RegisterForm.tsx             # ADAPT: Use Supabase auth
└── OAuthButtons.tsx             # NEW: Google/Apple/Facebook

// 2. Protected route wrapper
components/shared/
└── ProtectedRoute.tsx           # NEW: Role-based route protection
```

#### Contexts:
```typescript
contexts/
└── SupabaseAuthContext.tsx      # NEW: Replace JWTContext

interface SupabaseAuthContextType {
  user: User | null;
  session: Session | null;
  isLoading: boolean;
  signIn: (email: string, password: string) => Promise<void>;
  signUp: (email: string, password: string) => Promise<void>;
  signInWithOAuth: (provider: 'google' | 'apple' | 'facebook') => Promise<void>;
  signOut: () => Promise<void>;
  hasRole: (role: string) => boolean;
}
```

#### Hooks:
```typescript
hooks/
├── useAuth.ts                   # UPDATE: Point to Supabase
└── useRBAC.ts                   # NEW: Role checks

// Example usage
const { user, hasRole } = useAuth();
const canApproveAgency = hasRole('ADMIN');
```

#### Views:
```typescript
views/authentication/
├── login.tsx                    # UPDATE: EasyLuxury branding
├── register.tsx                 # UPDATE: EasyLuxury flow
├── oauth-callback.tsx           # NEW: Handle OAuth redirect
└── choose-role.tsx              # NEW: Select user type on first login
```

**Acceptance Criteria:**
- [x] Login via Supabase works
- [x] JWT verified by Spring backend
- [x] Role-based access enforced
- [x] OAuth providers functional (Google, Apple, Facebook)
- [x] Protected routes redirect properly
- [x] 80%+ test coverage

---

### Ticket P1.1-B: Agency Activation (with admin approval)

**Frontend Changes:**

#### Components:
```typescript
components/agency/
├── AgencyRegistrationWizard.tsx  # NEW: Multi-step agency signup
│   ├── Step1_BusinessInfo.tsx    # Business name, type, description
│   ├── Step2_ServiceOfferings.tsx # Select services offered
│   ├── Step3_Documentation.tsx   # Upload business docs
│   └── Step4_Review.tsx          # Review and submit
├── AgencyStatusBadge.tsx         # NEW: Show approval status
├── AgencyApprovalCard.tsx        # NEW: Admin approval UI
└── AgencyProfileForm.tsx         # NEW: Edit agency profile

components/admin/
└── AgencyApprovalList.tsx        # NEW: List pending agencies
```

#### Forms (using useAdvancedForm):
```typescript
// AgencyRegistrationWizard.tsx
interface AgencyFormData {
  name: string;
  bio: string;
  serviceTypes: ServiceType[];
  businessLicense: File | null;
  taxId: string;
  contactEmail: string;
  contactPhone: string;
}

const form = useAdvancedForm<AgencyFormData>({
  initialValues: { /* ... */ },
  validationRules: {
    name: [
      { type: 'required', message: 'Agency name required' },
      { type: 'minLength', value: 3, message: 'Min 3 characters' }
    ],
    serviceTypes: [
      { type: 'custom', validator: (val) => val.length > 0, message: 'Select at least one service' }
    ]
    // ... more validation
  },
  onSubmit: async (values) => {
    await agencyService.registerAgency(values);
  }
});
```

#### Views:
```typescript
views/agency/
├── register.tsx                  # NEW: Agency registration page
├── dashboard.tsx                 # NEW: Agency owner dashboard
├── settings.tsx                  # NEW: Agency settings/profile
└── pending-approval.tsx          # NEW: Waiting for approval page

views/admin/
└── agency-approvals.tsx          # NEW: Admin agency approval page
```

#### Contexts:
```typescript
contexts/
└── AgencyContext.tsx             # NEW: Agency state management

interface AgencyContextType {
  agency: Agency | null;
  isAgencyOwner: boolean;
  approvalStatus: 'PENDING' | 'APPROVED' | 'REJECTED' | null;
  submitForApproval: (data: AgencyFormData) => Promise<void>;
  updateAgency: (data: Partial<Agency>) => Promise<void>;
  isLoading: boolean;
}
```

**Acceptance Criteria:**
- [x] Users can submit agency registration
- [x] Pending agencies hidden from marketplace
- [x] Admin can approve/reject agencies
- [x] Email notifications on status change
- [x] Approval decisions audited
- [x] Agency owner can edit profile after approval

---

### Ticket P1.2-B: Property Submission + Media Uploads

**Frontend Changes:**

#### Components:
```typescript
components/property/
├── PropertyWizard/               # NEW: Multi-step property submission
│   ├── PropertyWizard.tsx        # Main wizard component
│   ├── Step1_Location.tsx        # Address + map picker
│   ├── Step2_Details.tsx         # Size, type, rooms
│   ├── Step3_Photos.tsx          # Photo uploads
│   ├── Step4_Budget.tsx          # Budget and purpose
│   └── Step5_Review.tsx          # Review and submit
├── MediaUpload/                  # NEW: Photo/document upload
│   ├── MediaUpload.tsx           # Main upload component
│   ├── ImagePreview.tsx          # Image grid with preview
│   ├── UploadProgress.tsx        # Progress indicator
│   └── MediaGallery.tsx          # View uploaded media
├── PropertyCard.tsx              # NEW: Property display card
└── PropertyDetail.tsx            # NEW: Full property view
```

#### Forms:
```typescript
interface PropertyFormData {
  address: {
    street: string;
    city: string;
    country: string;
    postalCode: string;
    coordinates: { lat: number; lng: number };
  };
  size: number;
  bedrooms: number;
  bathrooms: number;
  propertyType: 'APARTMENT' | 'VILLA' | 'TOWNHOUSE';
  purpose: 'PERSONAL' | 'INVESTMENT' | 'RENTAL';
  budget: number;
  currency: 'EGP' | 'SAR' | 'AED';
  photos: File[];
  documents: File[];
}

const form = useAdvancedForm<PropertyFormData>({
  initialValues: { /* ... */ },
  validationRules: {
    address: {
      street: [{ type: 'required', message: 'Street address required' }],
      city: [{ type: 'required', message: 'City required' }]
    },
    size: [
      { type: 'required', message: 'Property size required' },
      { type: 'pattern', value: /^\d+$/, message: 'Must be a number' }
    ],
    photos: [
      { 
        type: 'custom', 
        validator: (val) => val.length >= 3, 
        message: 'At least 3 photos required' 
      }
    ]
  },
  onSubmit: async (values) => {
    // 1. Get presigned URLs for uploads
    const uploadUrls = await propertyService.getPresignedUrls(values.photos.length);
    
    // 2. Upload files to S3/MinIO
    await Promise.all(
      values.photos.map((file, i) => 
        uploadToStorage(uploadUrls[i], file)
      )
    );
    
    // 3. Create property with media references
    await propertyService.createProperty(values);
  }
});
```

#### Views:
```typescript
views/property/
├── submit.tsx                    # NEW: Property submission page
├── [id].tsx                      # NEW: Property detail page
├── list.tsx                      # NEW: My properties list
└── edit/[id].tsx                 # NEW: Edit property
```

#### Hooks:
```typescript
hooks/
├── useProperty.ts                # NEW: Property CRUD operations
└── useMediaUpload.ts             # NEW: File upload with progress

// useMediaUpload.ts
interface UseMediaUploadOptions {
  onProgress?: (progress: number) => void;
  onSuccess?: () => void;
  onError?: (error: Error) => void;
  maxFiles?: number;
  maxFileSize?: number;
  acceptedTypes?: string[];
}

export const useMediaUpload = (options: UseMediaUploadOptions) => {
  const [files, setFiles] = useState<File[]>([]);
  const [progress, setProgress] = useState<number>(0);
  const [uploading, setUploading] = useState(false);

  const upload = useAsyncOperation(
    async (files: File[]) => {
      const urls = await getPresignedUrls(files.length);
      await Promise.all(files.map((file, i) => uploadFile(urls[i], file)));
    },
    {
      retryCount: 2,
      onSuccess: options.onSuccess,
      onError: options.onError
    }
  );

  return { files, progress, uploading, upload, setFiles };
};
```

**Acceptance Criteria:**
- [x] Multi-step wizard saves progress
- [x] Map picker for location selection
- [x] Photos upload to S3/MinIO with presigned URLs
- [x] Upload progress shown
- [x] Image preview before upload
- [x] Property detail page renders uploaded images
- [x] Mobile-responsive photo gallery

---

### Ticket P1.2-C: Style Selection

**Frontend Changes:**

#### Components:
```typescript
components/marketplace/
├── StyleLibrary/                 # NEW: Pre-approved style gallery
│   ├── StyleLibrary.tsx          # Main grid view
│   ├── StyleCard.tsx             # Style package card
│   ├── StyleDetail.tsx           # Full style details
│   └── StyleFilter.tsx           # Filter by type/price
└── StyleSelector.tsx             # NEW: Select style for property
```

#### Views:
```typescript
views/property/
└── [id]/style-select.tsx         # NEW: Choose style for property

views/marketplace/
└── styles.tsx                    # NEW: Browse all styles
```

#### Types:
```typescript
types/marketplace.ts

interface StylePackage {
  id: string;
  name: string;
  type: 'FURNITURE' | 'FINISHING' | 'COMPLETE';
  description: string;
  images: string[];
  priceRange: {
    min: number;
    max: number;
    currency: string;
  };
  preApproved: boolean;
  popularity: number;
  features: string[];
}
```

**Acceptance Criteria:**
- [x] Style library displays with filters
- [x] Style selection persists to property
- [x] Selected style shows on property overview
- [x] Mobile-friendly grid layout

---

## 📦 Phase 2 Implementation Plan (Operations & Monetization)

**Timeline:** Q4 2025  
**Goal:** Payments, Work Orders, Reviews, Team Management

### Key Tickets

#### P2.1-A: Tenant Booking Flow
```typescript
components/rental/
├── BookingForm.tsx               # NEW: Booking reservation form
├── BookingCalendar.tsx           # NEW: Availability calendar
├── BookingConfirmation.tsx       # NEW: Booking confirmation
└── HoldTimer.tsx                 # NEW: 15-minute hold countdown

// Hold logic
interface BookingHold {
  bookingId: string;
  expiresAt: Date;
  remainingSeconds: number;
}

const { hold, confirm, cancel } = useBookingHold();
```

#### P2.1-B: Reviews System
```typescript
components/review/
├── ReviewForm.tsx                # NEW: Submit review
├── ReviewCard.tsx                # NEW: Display review
├── ReviewList.tsx                # NEW: List of reviews
├── RatingDisplay.tsx             # NEW: Star rating display
└── ReviewStats.tsx               # NEW: Rating statistics

// Review form
interface ReviewFormData {
  rating: number; // 1-5
  title: string;
  comment: string;
  photos?: File[];
  wouldRecommend: boolean;
}
```

#### P2.2-B: Property Manager Workflows
```typescript
components/manager/
├── ManagerDashboard.tsx          # NEW: Manager overview
├── ManagedPropertiesList.tsx     # NEW: Properties under management
├── ManagerAssignment.tsx         # NEW: Assign manager to property
└── ManagerPermissions.tsx        # NEW: Scoped permissions UI
```

---

## 📦 Phase 3 Implementation Plan (Marketplace & Enhancements)

**Timeline:** Q1 2026  
**Goal:** Advanced search, Analytics, AI features, Realtime

### Key Tickets

#### P3.1-A: Stripe Payments
```typescript
components/payment/
├── CheckoutForm.tsx              # NEW: Stripe checkout
├── PaymentMethod.tsx             # NEW: Payment method selector
├── PaymentSuccess.tsx            # NEW: Success confirmation
└── InvoiceDisplay.tsx            # NEW: Invoice viewer
```

#### P3.2-A: Analytics Dashboards
```typescript
components/analytics/
├── OwnerAnalytics.tsx            # NEW: Property owner metrics
├── AgencyAnalytics.tsx           # NEW: Agency performance
├── AdminAnalytics.tsx            # NEW: Platform metrics
└── Charts/                       # LEVERAGE: Existing chart components
```

#### P3.3-A: AI Style Recommendations
```typescript
components/ai/
├── AIStyleRecommendation.tsx     # NEW: AI-powered suggestions
├── StyleQuiz.tsx                 # NEW: Preference quiz
└── RecommendationCard.tsx        # NEW: Suggested styles
```

---

## 🗺️ Component Mapping

**📌 For detailed component-to-component mappings and specific widget recommendations, see:**
**[COMPONENT_MAPPING_GUIDE.md](COMPONENT_MAPPING_GUIDE.md)**

This section provides a high-level overview. The mapping guide provides:
- Page-to-page mappings (30+ mappings)
- Widget library reference (40+ components)
- Step-by-step adaptation examples
- Code snippets for each component

### Components We Can REUSE (with minor adaptation)

| Current Component | New Use | Changes Required |
|-------------------|---------|------------------|
| `UserList` | Admin user management | Change data source |
| `UserCard` | User profile display | Add agency badge |
| `Dashboard/Analytics` | Owner/Agency dashboards | New metrics |
| `FormWizard` | Property/Agency registration | New steps |
| `FormTables` | Package management | Domain data |
| `Calendar` | Booking calendar | Rental logic |
| `OrderList` | Booking/Project list | Domain entities |
| `Chat` | Project notes | Project context |

### Components We Must BUILD NEW

| Component | Purpose | Phase |
|-----------|---------|-------|
| `PropertyWizard` | Property submission | P1 |
| `AgencyRegistration` | Agency signup | P1 |
| `StyleLibrary` | Style browsing | P1 |
| `BidComparison` | Compare bids | P1 |
| `ProjectTimeline` | Milestone tracking | P1 |
| `BookingCalendar` | Rental calendar | P2 |
| `ReviewSystem` | Reviews & ratings | P2 |
| `PaymentCheckout` | Stripe integration | P3 |
| `AIRecommendations` | AI features | P3 |

---

## 🛠️ Technical Requirements

### State Management Architecture

```typescript
// Domain Contexts
contexts/
├── PropertyContext.tsx           # Property CRUD + state
├── AgencyContext.tsx             # Agency operations
├── ProjectContext.tsx            # Project lifecycle
├── MarketplaceContext.tsx        # Search & filters
├── RentalContext.tsx             # Booking management
└── NotificationContext.tsx       # KEEP: Already exists

// All contexts use React Query for server state
// All contexts follow established patterns
```

### API Integration

```typescript
// api/client.ts
import axios from 'axios';

const apiClient = axios.create({
  baseURL: '/api/v1',
  headers: {
    'Content-Type': 'application/json'
  }
});

// Interceptor for JWT from Supabase
apiClient.interceptors.request.use(async (config) => {
  const session = await supabase.auth.getSession();
  if (session?.access_token) {
    config.headers.Authorization = `Bearer ${session.access_token}`;
  }
  return config;
});

// React Query integration
export const propertyQueries = {
  all: ['properties'],
  list: () => [...propertyQueries.all, 'list'],
  detail: (id: string) => [...propertyQueries.all, 'detail', id],
};

export const useProperties = () => {
  return useQuery({
    queryKey: propertyQueries.list(),
    queryFn: () => apiClient.get('/properties').then(res => res.data)
  });
};
```

### Form Patterns (Consistent)

```typescript
// ALL forms MUST use useAdvancedForm
// Example: Property submission

const PropertyForm = () => {
  const form = useAdvancedForm<PropertyFormData>({
    initialValues: { /* ... */ },
    validationRules: { /* ... */ },
    onSubmit: async (values) => {
      await propertyService.createProperty(values);
    }
  });

  return (
    <form onSubmit={form.handleSubmit()}>
      <TextField
        value={form.values.address}
        onChange={form.handleChange('address')}
        error={Boolean(form.errors.address)}
        helperText={form.errors.address}
      />
      <Button 
        type="submit" 
        disabled={!form.isValid || form.isSubmitting}
      >
        {form.isSubmitting ? 'Submitting...' : 'Submit Property'}
      </Button>
    </form>
  );
};

export default withErrorBoundary(PropertyForm);
```

### Table Patterns (Consistent)

```typescript
// ALL tables MUST use useTableLogic
// Example: Property list

const PropertyList = () => {
  const { data: properties = [] } = useProperties();
  
  const table = useTableLogic<Property>({
    data: properties,
    searchFields: ['address', 'city', 'propertyType'],
    defaultOrderBy: 'createdAt',
    defaultRowsPerPage: 10
  });

  return (
    <Box>
      <TextField
        placeholder="Search properties..."
        value={table.search}
        onChange={table.handleSearch}
      />
      <Table>
        <TableHead>{/* Sortable headers */}</TableHead>
        <TableBody>
          {table.sortedAndPaginatedRows.map(property => (
            <PropertyRow key={property.id} property={property} />
          ))}
        </TableBody>
      </Table>
      <TablePagination {...table} />
    </Box>
  );
};

export default withErrorBoundary(PropertyList);
```

---

## ✅ Success Criteria

### Phase 1 Success Metrics
- [x] Auth flow works (Supabase + Spring JWT)
- [x] Agency registration & approval functional
- [x] Property submission with media uploads works
- [x] Style library browsable and selectable
- [x] Project/bid workflow functional
- [x] Basic listing creation works
- [x] Admin panel operational
- [x] Mobile-responsive across all screens
- [x] 80%+ test coverage on new components

### Phase 2 Success Metrics
- [x] Booking flow (hold → confirm) works
- [x] Review system functional
- [x] Property manager workflows operational
- [x] Marketplace search performant
- [x] Public profiles SEO-optimized

### Phase 3 Success Metrics
- [x] Stripe payments end-to-end
- [x] Analytics dashboards live
- [x] AI recommendations functional
- [x] Realtime features operational
- [x] iCal sync working

---

## 📋 Implementation Checklist

### Pre-Development
- [ ] Review and approve this plan
- [ ] Set up Supabase project
- [ ] Configure S3/MinIO for media
- [ ] Set up backend API endpoints
- [ ] Define TypeScript interfaces
- [ ] Create API client utilities

### Phase 1 Development
- [ ] Create directory structure
- [ ] Implement authentication (P1.1-A)
- [ ] Implement agency registration (P1.1-B)
- [ ] Build property wizard (P1.2-B)
- [ ] Build style library (P1.2-C)
- [ ] Build project/bid components (P1.2-E)
- [ ] Build basic listing (P1.2-G)
- [ ] Build admin panel (P1.2-H)
- [ ] Write tests for all components
- [ ] Mobile responsiveness testing
- [ ] User acceptance testing

### Phase 2 Development
- [ ] Implement booking flow (P2.1-A)
- [ ] Implement review system (P2.1-B)
- [ ] Implement manager workflows (P2.2-B)
- [ ] Enhance marketplace (P2.3-A)
- [ ] Implement project notes (P2.3-B)

### Phase 3 Development
- [ ] Integrate Stripe (P3.1-A)
- [ ] Build analytics (P3.2-A)
- [ ] Implement notifications (P3.2-B)
- [ ] Add AI features (P3.3-A)
- [ ] Add realtime (P3.3-B)

---

## 🎯 Next Steps

1. **Review this plan** with the team
2. **Approve directory structure** and component organization
3. **Set up infrastructure** (Supabase, S3/MinIO, backend)
4. **Begin Phase 1 Ticket P1.1-A** (Auth & RBAC)
5. **Iterate based on feedback**

---

**Document Status:** ✅ Ready for Review  
**Created:** October 2025  
**Author:** Based on planning documents in `/planning`  
**Next Review:** After Phase 1 completion

---

**This plan leverages the existing enterprise patterns while building a completely new domain-specific application. All modern patterns (Context API, React Query, enterprise hooks) will be consistently applied throughout the implementation.**
