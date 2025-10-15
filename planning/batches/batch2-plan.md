# PLAN — Batch 2 (Tickets: P1.2-A, P1.2-B, P1.2-C)

## 0) Summary

### Goal:
Implement core domain entities (Property, StylePackage, Project, Bid, Listing) with full CRUD operations, property submission workflow with media uploads to S3/MinIO, and style selection from a pre-approved library.

### Tickets covered:
- **P1.2-A**: Core schema (Property, StylePackage, Project, Bid, Listing)
- **P1.2-B**: Property submission + media uploads
- **P1.2-C**: Style selection from pre-approved library

### Non-goals / out of scope:
- Project publication and bid management (P1.2-E) - separate ticket
- Project tracking with milestones (P1.2-F) - separate ticket
- Agency packages (P1.2-D) - separate ticket
- Booking/rental functionality (P1.3-A/B) - Phase 1.3
- Payment integration - Phase 3
- OTA calendar sync - Phase 3

---

## 1) Backend changes

### Entities (new/updated):

#### New Entities:

**Property.java** (`com.easyluxury.entity.Property`)
- `id: UUID` (PK)
- `owner: User` (ManyToOne, FK)
- `address: Address` (Embeddable)
- `size: BigDecimal` (square meters)
- `bedrooms: Integer`
- `bathrooms: Integer`
- `propertyType: PropertyType` (ENUM: APARTMENT, VILLA, TOWNHOUSE, PENTHOUSE, STUDIO)
- `purpose: PropertyPurpose` (ENUM: PERSONAL, INVESTMENT, RENTAL)
- `status: PropertyStatus` (ENUM: DRAFT, SUBMITTED, UNDER_REVIEW, ACTIVE, ARCHIVED)
- `budget: BigDecimal`
- `currency: String` (EGP, SAR, AED)
- `photos: List<PropertyMedia>` (OneToMany, cascade)
- `documents: List<PropertyMedia>` (OneToMany, cascade)
- `selectedStyle: StylePackage` (ManyToOne, nullable)
- `createdAt: LocalDateTime`
- `updatedAt: LocalDateTime`

**Address** (Embeddable)
- `street: String`
- `city: String`
- `country: String`
- `postalCode: String`
- `latitude: Double`
- `longitude: Double`

**PropertyMedia.java** (`com.easyluxury.entity.PropertyMedia`)
- `id: UUID` (PK)
- `property: Property` (ManyToOne, FK)
- `mediaType: MediaType` (ENUM: PHOTO, DOCUMENT, VIDEO)
- `url: String` (public URL after upload)
- `fileName: String`
- `fileSize: Long` (bytes)
- `mimeType: String`
- `displayOrder: Integer`
- `uploadedAt: LocalDateTime`

**StylePackage.java** (`com.easyluxury.entity.StylePackage`)
- `id: UUID` (PK)
- `name: String`
- `description: String`
- `styleType: StyleType` (ENUM: FURNITURE, FINISHING, COMPLETE)
- `images: List<StyleImage>` (OneToMany, cascade)
- `priceRangeMin: BigDecimal`
- `priceRangeMax: BigDecimal`
- `currency: String`
- `features: String` (JSON string or comma-separated)
- `preApproved: Boolean` (default true)
- `popularity: Integer` (for sorting)
- `active: Boolean` (default true)
- `createdAt: LocalDateTime`
- `updatedAt: LocalDateTime`

**StyleImage.java** (`com.easyluxury.entity.StyleImage`)
- `id: UUID` (PK)
- `stylePackage: StylePackage` (ManyToOne, FK)
- `url: String`
- `displayOrder: Integer`

**Project.java** (`com.easyluxury.entity.Project`)
- `id: UUID` (PK)
- `property: Property` (OneToOne, FK)
- `owner: User` (ManyToOne, FK)
- `title: String`
- `description: String`
- `status: ProjectStatus` (ENUM: CREATED, OPEN, SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED)
- `budget: BigDecimal`
- `currency: String`
- `startDate: LocalDate` (nullable)
- `endDate: LocalDate` (nullable)
- `createdAt: LocalDateTime`
- `updatedAt: LocalDateTime`

**Bid.java** (`com.easyluxury.entity.Bid`)
- `id: UUID` (PK)
- `project: Project` (ManyToOne, FK)
- `agency: Agency` (ManyToOne, FK)
- `amount: BigDecimal`
- `currency: String`
- `proposedDuration: Integer` (days)
- `notes: String`
- `status: BidStatus` (ENUM: SUBMITTED, ACCEPTED, REJECTED, WITHDRAWN)
- `submittedAt: LocalDateTime`
- `respondedAt: LocalDateTime` (nullable)

**Listing.java** (`com.easyluxury.entity.Listing`)
- `id: UUID` (PK)
- `property: Property` (OneToOne, FK)
- `owner: User` (ManyToOne, FK)
- `title: String`
- `description: String`
- `pricePerNight: BigDecimal`
- `currency: String`
- `minimumStay: Integer` (nights)
- `maximumStay: Integer` (nights)
- `checkInTime: String`
- `checkOutTime: String`
- `published: Boolean`
- `publishedAt: LocalDateTime` (nullable)
- `createdAt: LocalDateTime`
- `updatedAt: LocalDateTime`

### Liquibase changesets:

**V003__core_domain.yaml**
- Create `properties` table
- Create `property_media` table
- Create `style_packages` table
- Create `style_images` table
- Create `projects` table
- Create `bids` table
- Create `listings` table
- Add FK constraints
- Add indexes on `owner_id`, `property_id`, `agency_id`, `status` columns
- Add unique constraint on `projects.property_id`
- Add unique constraint on `listings.property_id`

### Endpoints (method → path → purpose):

#### Property Endpoints:
- **POST** `/api/properties` → Create property submission
- **GET** `/api/properties` → List authenticated user's properties (paginated)
- **GET** `/api/properties/{id}` → Get property detail
- **PUT** `/api/properties/{id}` → Update property
- **DELETE** `/api/properties/{id}` → Delete property (soft delete)
- **POST** `/api/properties/{id}/media:presign` → Get presigned URLs for media upload
- **DELETE** `/api/properties/{id}/media/{mediaId}` → Delete media item
- **PUT** `/api/properties/{id}/style` → Associate style with property

#### Style Package Endpoints:
- **GET** `/api/styles` → List all pre-approved styles (filterable by type)
- **GET** `/api/styles/{id}` → Get style detail
- **POST** `/api/admin/styles` → Create style package (admin only)
- **PUT** `/api/admin/styles/{id}` → Update style package (admin only)
- **DELETE** `/api/admin/styles/{id}` → Delete style package (admin only)

#### Project Endpoints (Core Schema Only):
- **POST** `/api/projects` → Create project (placeholder for P1.2-E)
- **GET** `/api/projects/{id}` → Get project detail (placeholder for P1.2-E)

#### Bid Endpoints (Core Schema Only):
- **POST** `/api/projects/{id}/bids` → Submit bid (placeholder for P1.2-E)
- **GET** `/api/bids/{id}` → Get bid detail (placeholder for P1.2-E)

#### Listing Endpoints (Core Schema Only):
- **POST** `/api/listings` → Create listing (placeholder for P1.2-G)
- **GET** `/api/listings/{id}` → Get listing detail (placeholder for P1.2-G)

### DTOs & mappers:

#### Property DTOs:
- `CreatePropertyRequest` (address, size, bedrooms, bathrooms, propertyType, purpose, budget, currency)
- `UpdatePropertyRequest` (size, bedrooms, bathrooms, budget)
- `PropertyDto` (all fields + owner info + media list + selected style)
- `PropertyMediaDto` (id, url, fileName, mediaType, displayOrder)
- `PropertyListDto` (id, address, size, bedrooms, bathrooms, status, primaryPhoto)
- `PresignedUploadRequest` (fileNames: List<String>, mediaType: MediaType)
- `PresignedUploadResponse` (uploads: List<PresignedUrl>)
- `PresignedUrl` (fileName, uploadUrl, publicUrl, expiresIn)
- `SelectStyleRequest` (stylePackageId: UUID)

#### Style Package DTOs:
- `StylePackageDto` (all fields + images)
- `StyleImageDto` (id, url, displayOrder)
- `CreateStylePackageRequest` (name, description, styleType, priceRange, currency, features, images)
- `UpdateStylePackageRequest` (name, description, priceRange, features, active)

#### Project/Bid/Listing DTOs (minimal for schema):
- `ProjectDto` (basic fields for schema validation)
- `BidDto` (basic fields for schema validation)
- `ListingDto` (basic fields for schema validation)

#### Mappers (MapStruct):
- `PropertyMapper` (@Mapper, componentModel = "spring")
- `StylePackageMapper` (@Mapper, componentModel = "spring")
- `ProjectMapper` (basic)
- `BidMapper` (basic)
- `ListingMapper` (basic)

### Security/RBAC:

**Property Endpoints:**
- POST `/api/properties` → `@PreAuthorize("hasAnyAuthority('OWNER', 'AGENCY_OWNER')")`
- GET `/api/properties` → `@PreAuthorize("hasAnyAuthority('OWNER', 'AGENCY_OWNER')")`
- GET `/api/properties/{id}` → `@PreAuthorize("hasAnyAuthority('OWNER', 'AGENCY_OWNER', 'AGENCY_MEMBER', 'ADMIN')")` + row-level check (user owns property or admin)
- PUT `/api/properties/{id}` → `@PreAuthorize("hasAnyAuthority('OWNER', 'AGENCY_OWNER')")` + row-level check (user owns property)
- DELETE `/api/properties/{id}` → `@PreAuthorize("hasAnyAuthority('OWNER', 'AGENCY_OWNER')")` + row-level check (user owns property)
- POST `/api/properties/{id}/media:presign` → `@PreAuthorize("hasAnyAuthority('OWNER', 'AGENCY_OWNER')")` + row-level check
- DELETE `/api/properties/{id}/media/{mediaId}` → `@PreAuthorize("hasAnyAuthority('OWNER', 'AGENCY_OWNER')")` + row-level check
- PUT `/api/properties/{id}/style` → `@PreAuthorize("hasAnyAuthority('OWNER', 'AGENCY_OWNER')")` + row-level check

**Style Endpoints:**
- GET `/api/styles` → `@PreAuthorize("isAuthenticated()")`
- GET `/api/styles/{id}` → `@PreAuthorize("isAuthenticated()")`
- POST `/api/admin/styles` → `@PreAuthorize("hasAuthority('ADMIN')")`
- PUT `/api/admin/styles/{id}` → `@PreAuthorize("hasAuthority('ADMIN')")`
- DELETE `/api/admin/styles/{id}` → `@PreAuthorize("hasAuthority('ADMIN')")`

### Validation rules:

**CreatePropertyRequest:**
- `address.street`: @NotBlank, @Size(max = 255)
- `address.city`: @NotBlank, @Size(max = 100)
- `address.country`: @NotBlank, @Size(max = 100)
- `address.latitude`: @NotNull, @Min(-90), @Max(90)
- `address.longitude`: @NotNull, @Min(-180), @Max(180)
- `size`: @NotNull, @Positive, @DecimalMin("1.0")
- `bedrooms`: @NotNull, @Min(0), @Max(50)
- `bathrooms`: @NotNull, @Min(0), @Max(50)
- `propertyType`: @NotNull
- `purpose`: @NotNull
- `budget`: @NotNull, @Positive
- `currency`: @NotBlank, @Pattern(regexp = "EGP|SAR|AED")

**PresignedUploadRequest:**
- `fileNames`: @NotEmpty, @Size(min = 1, max = 20)
- `mediaType`: @NotNull

**SelectStyleRequest:**
- `stylePackageId`: @NotNull

**CreateStylePackageRequest:**
- `name`: @NotBlank, @Size(max = 255)
- `description`: @NotBlank, @Size(max = 2000)
- `styleType`: @NotNull
- `priceRangeMin`: @NotNull, @Positive
- `priceRangeMax`: @NotNull, @Positive
- `currency`: @NotBlank, @Pattern(regexp = "EGP|SAR|AED")
- Custom validation: priceRangeMax >= priceRangeMin

### Error model (problem+json codes):

**Error Codes:**
- `PROPERTY_NOT_FOUND` (404) - Property with given ID not found
- `PROPERTY_ACCESS_DENIED` (403) - User does not own this property
- `STYLE_NOT_FOUND` (404) - Style package with given ID not found
- `STYLE_NOT_APPROVED` (400) - Style package is not pre-approved
- `INVALID_MEDIA_TYPE` (400) - Unsupported media type
- `MEDIA_NOT_FOUND` (404) - Media item not found
- `UPLOAD_URL_GENERATION_FAILED` (500) - Failed to generate presigned URL
- `VALIDATION_ERROR` (400) - Request validation failed (with details array)
- `DUPLICATE_PROPERTY` (409) - Property at this address already exists for user
- `BUDGET_CURRENCY_MISMATCH` (400) - Budget and style package currencies don't match

**Error Response Format:**
```json
{
  "code": "PROPERTY_NOT_FOUND",
  "message": "Property with ID {id} not found",
  "details": []
}
```

---

## 2) Frontend changes (Next.js App Router)

### Routes/pages:

**Property Routes:**
- `/app/(dashboard)/owner/properties/page.tsx` → List user's properties
- `/app/(dashboard)/owner/properties/new/page.tsx` → Property submission wizard
- `/app/(dashboard)/owner/properties/[id]/page.tsx` → Property detail view
- `/app/(dashboard)/owner/properties/[id]/edit/page.tsx` → Edit property
- `/app/(dashboard)/owner/properties/[id]/style-select/page.tsx` → Style selection

**Marketplace Routes:**
- `/app/(dashboard)/marketplace/styles/page.tsx` → Browse style library

### Components:

**Property Components** (`src/components/property/`):

1. **PropertyWizard/** (Multi-step form)
   - `PropertyWizard.tsx` - Main wizard container with stepper
   - `Step1_Location.tsx` - Address + map picker (reuse: MainCard, Grid, TextField, Autocomplete; new: MapPicker)
   - `Step2_Details.tsx` - Size, type, rooms (reuse: MainCard, Grid, TextField, Select)
   - `Step3_Photos.tsx` - Photo uploads (reuse: MainCard; new: MediaUpload)
   - `Step4_Budget.tsx` - Budget + purpose (reuse: MainCard, TextField, Select, Radio)
   - `Step5_Review.tsx` - Review and submit (reuse: MainCard, SubCard, Typography, Grid)

2. **PropertyCard.tsx** - Adapt from `ProductCard` (reuse: Card, CardMedia, CardContent, Chip, Grid)

3. **PropertyDetail.tsx** - Full property view (reuse: MainCard, SubCard, ImageList, Typography, Chip, Divider)

4. **PropertyList.tsx** - Grid of property cards (reuse: Grid, Pagination, TextField; use: useTableLogic)

5. **MediaUpload/** (Photo/document upload)
   - `MediaUpload.tsx` - Main upload component with drag-drop
   - `ImagePreview.tsx` - Thumbnail grid with delete
   - `UploadProgress.tsx` - Progress indicator

6. **MapPicker.tsx** - Interactive map for location selection (new, integrate with mapping library)

**Marketplace Components** (`src/components/marketplace/`):

1. **StyleLibrary/** (Style browsing)
   - `StyleLibrary.tsx` - Main grid view with filters (reuse: Grid, TextField, useTableLogic)
   - `StyleCard.tsx` - Adapt from `ProductCard` (reuse: Card, CardMedia, Chip, Box)
   - `StyleDetail.tsx` - Full style view with images (reuse: MainCard, ImageList, Typography, Chip)
   - `StyleFilter.tsx` - Filter by type/price (reuse: FormControl, Select, Slider)

2. **StyleSelector.tsx** - Select style for property (reuse: Grid, Dialog, Button)

### Data fetching (React Query keys):

**Property Queries:**
```typescript
propertyQueries = {
  all: ['properties'],
  list: () => [...propertyQueries.all, 'list'],
  detail: (id: string) => [...propertyQueries.all, 'detail', id],
  presignedUrls: (propertyId: string) => [...propertyQueries.all, 'presign', propertyId]
}
```

**Style Queries:**
```typescript
styleQueries = {
  all: ['styles'],
  list: (filters?: StyleFilters) => [...styleQueries.all, 'list', filters],
  detail: (id: string) => [...styleQueries.all, 'detail', id]
}
```

**Mutations:**
- `useCreateProperty` → POST /api/properties
- `useUpdateProperty` → PUT /api/properties/{id}
- `useDeleteProperty` → DELETE /api/properties/{id}
- `useUploadMedia` → POST /api/properties/{id}/media:presign + PUT to presigned URL
- `useDeleteMedia` → DELETE /api/properties/{id}/media/{mediaId}
- `useSelectStyle` → PUT /api/properties/{id}/style

### Forms & validation (RHF + Zod):

**Property Form Schema (Zod):**
```typescript
const locationSchema = z.object({
  street: z.string().min(1, 'Street address required').max(255),
  city: z.string().min(1, 'City required').max(100),
  country: z.string().min(1, 'Country required').max(100),
  postalCode: z.string().max(20).optional(),
  coordinates: z.object({
    lat: z.number().min(-90).max(90),
    lng: z.number().min(-180).max(180)
  })
});

const propertyFormSchema = z.object({
  address: locationSchema,
  size: z.number().positive('Size must be positive'),
  bedrooms: z.number().int().min(0).max(50),
  bathrooms: z.number().int().min(0).max(50),
  propertyType: z.enum(['APARTMENT', 'VILLA', 'TOWNHOUSE', 'PENTHOUSE', 'STUDIO']),
  purpose: z.enum(['PERSONAL', 'INVESTMENT', 'RENTAL']),
  budget: z.number().positive('Budget must be positive'),
  currency: z.enum(['EGP', 'SAR', 'AED']),
  photos: z.array(z.instanceof(File)).min(3, 'At least 3 photos required'),
  documents: z.array(z.instanceof(File)).optional()
});
```

**Style Filter Schema:**
```typescript
const styleFilterSchema = z.object({
  type: z.enum(['FURNITURE', 'FINISHING', 'COMPLETE']).optional(),
  priceMin: z.number().optional(),
  priceMax: z.number().optional(),
  search: z.string().optional()
});
```

### Uploads/media:

**Media Upload Flow:**
1. User selects files in `MediaUpload` component
2. Validate file types (image/jpeg, image/png) and size (max 5MB per file)
3. Call `POST /api/properties/{id}/media:presign` with file names
4. Receive presigned PUT URLs and public URLs
5. Upload files directly to S3/MinIO using presigned URLs (with progress tracking)
6. On success, property automatically includes new media in response
7. Display uploaded images in gallery

**Supported Media Types:**
- Photos: image/jpeg, image/png (max 5MB each, max 20 files)
- Documents: application/pdf (max 10MB each, max 5 files)

### Accessibility notes:

- All form inputs have proper `label` or `aria-label`
- Stepper has `aria-current="step"` for active step
- Image galleries have alt text from `fileName`
- Error messages are associated with inputs via `aria-describedby`
- Upload buttons have keyboard support (Enter/Space)
- Focus management in wizard (auto-focus first field on step change)
- Color contrast ratio ≥ 4.5:1 for all text
- Skip links for navigation
- Screen reader announcements for step changes

---

## 3) OpenAPI & Types

### Springdoc annotations:

**PropertyController:**
```java
@Tag(name = "Properties", description = "Property management endpoints")
@RestController
@RequestMapping("/api/properties")
public class PropertyController {
    
    @Operation(summary = "Create property", description = "Submit a new property")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Property created"),
        @ApiResponse(responseCode = "400", description = "Validation error"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PostMapping
    public ResponseEntity<PropertyDto> createProperty(@Valid @RequestBody CreatePropertyRequest request);
    
    @Operation(summary = "Get presigned upload URLs", description = "Get presigned URLs for media upload")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "URLs generated"),
        @ApiResponse(responseCode = "404", description = "Property not found"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PostMapping("/{id}/media:presign")
    public ResponseEntity<PresignedUploadResponse> getPresignedUrls(
        @PathVariable UUID id, 
        @Valid @RequestBody PresignedUploadRequest request
    );
    
    @Operation(summary = "Select style", description = "Associate a style package with property")
    @PutMapping("/{id}/style")
    public ResponseEntity<PropertyDto> selectStyle(
        @PathVariable UUID id,
        @Valid @RequestBody SelectStyleRequest request
    );
}
```

**StylePackageController:**
```java
@Tag(name = "Styles", description = "Style package endpoints")
@RestController
@RequestMapping("/api/styles")
public class StylePackageController {
    
    @Operation(summary = "List styles", description = "Get all pre-approved style packages")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Styles retrieved")
    })
    @GetMapping
    public ResponseEntity<Page<StylePackageDto>> listStyles(
        @RequestParam(required = false) StyleType type,
        Pageable pageable
    );
}
```

### Regenerate FE types:

**Commands:**
```bash
# 1. Start backend (if not running)
make be_run

# 2. Export OpenAPI spec
make openapi

# 3. Generate TypeScript types (in frontend/)
npx openapi-typescript ../backend/openapi.json -o src/types/api.ts

# 4. Verify types compile
npm run type-check
```

**Generated Types Location:**
- `frontend/src/types/api.ts` - Auto-generated from OpenAPI
- `frontend/src/types/property.ts` - Additional FE-only types
- `frontend/src/types/marketplace.ts` - Additional FE-only types

---

## 4) Tests

### BE unit/slice:

**Unit Tests (Service Layer):**
- `PropertyServiceTest.java`
  - `testCreateProperty_Success()`
  - `testCreateProperty_UserNotFound()`
  - `testGetProperty_Success()`
  - `testGetProperty_NotFound()`
  - `testUpdateProperty_Success()`
  - `testUpdateProperty_NotOwner()`
  - `testDeleteProperty_Success()`
  - `testGeneratePresignedUrls_Success()`
  - `testSelectStyle_Success()`
  - `testSelectStyle_StyleNotFound()`

- `StylePackageServiceTest.java`
  - `testListStyles_AllTypes()`
  - `testListStyles_FilterByType()`
  - `testGetStyle_Success()`
  - `testGetStyle_NotFound()`

**Slice Tests (Controller Layer):**
- `PropertyControllerTest.java`
  - `testCreateProperty_ValidRequest_Returns201()`
  - `testCreateProperty_InvalidRequest_Returns400()`
  - `testGetProperty_Exists_Returns200()`
  - `testGetProperty_NotFound_Returns404()`
  - `testGetProperty_NotOwner_Returns403()`
  - `testGetPresignedUrls_ValidRequest_Returns200()`
  - `testSelectStyle_ValidRequest_Returns200()`

- `StylePackageControllerTest.java`
  - `testListStyles_Returns200()`
  - `testListStyles_WithTypeFilter_ReturnsFiltered()`
  - `testGetStyle_Exists_Returns200()`

### BE integration (Testcontainers):

**PropertyIntegrationTest.java:**
```java
@SpringBootTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PropertyIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");
    
    @Test
    void testPropertyCreationWorkflow() {
        // 1. Create user
        // 2. Create property
        // 3. Get presigned URLs
        // 4. Verify property media
        // 5. Select style
        // 6. Verify style association
    }
    
    @Test
    void testPropertyListPagination() {
        // Create multiple properties and test pagination
    }
    
    @Test
    void testPropertyAccessControl() {
        // Verify user can only access their own properties
    }
}
```

**StylePackageIntegrationTest.java:**
```java
@SpringBootTest
@Testcontainers
class StylePackageIntegrationTest {
    @Test
    void testStyleListingAndFiltering() {
        // Seed styles, test filtering by type
    }
}
```

### FE unit:

**Component Tests:**
- `PropertyWizard.test.tsx`
  - Renders all 5 steps
  - Navigation between steps works
  - Form validation on each step
  - Submit calls mutation

- `MediaUpload.test.tsx`
  - File selection works
  - File type validation
  - File size validation
  - Upload progress tracking
  - Delete uploaded file

- `PropertyCard.test.tsx`
  - Renders property data
  - Handles missing photos
  - Click navigates to detail

- `StyleCard.test.tsx`
  - Renders style data
  - Displays price range
  - Shows pre-approved badge

**Hook Tests:**
- `useProperty.test.ts`
  - `useCreateProperty` mutation
  - `useProperties` query with caching
  - `useUploadMedia` with progress

### E2E happy-path (Playwright):

**property-submission.spec.ts:**
```typescript
test('Property submission flow', async ({ page }) => {
  // 1. Login as OWNER
  await page.goto('/auth/login');
  await login(page, 'owner@test.com', 'password');
  
  // 2. Navigate to new property
  await page.goto('/owner/properties/new');
  
  // 3. Fill Step 1 - Location
  await page.fill('[name="address.street"]', '123 Test St');
  await page.fill('[name="address.city"]', 'Cairo');
  await page.click('button:has-text("Next")');
  
  // 4. Fill Step 2 - Details
  await page.fill('[name="size"]', '100');
  await page.fill('[name="bedrooms"]', '2');
  await page.fill('[name="bathrooms"]', '2');
  await page.selectOption('[name="propertyType"]', 'APARTMENT');
  await page.click('button:has-text("Next")');
  
  // 5. Upload photos in Step 3
  await page.setInputFiles('input[type="file"]', [
    'test/fixtures/photo1.jpg',
    'test/fixtures/photo2.jpg',
    'test/fixtures/photo3.jpg'
  ]);
  await page.waitForSelector('.upload-complete');
  await page.click('button:has-text("Next")');
  
  // 6. Fill Step 4 - Budget
  await page.fill('[name="budget"]', '500000');
  await page.selectOption('[name="currency"]', 'EGP');
  await page.click('[value="INVESTMENT"]');
  await page.click('button:has-text("Next")');
  
  // 7. Review and submit Step 5
  await expect(page.locator('text=123 Test St')).toBeVisible();
  await page.click('button:has-text("Submit Property")');
  
  // 8. Verify redirect to property detail
  await expect(page).toHaveURL(/\/owner\/properties\/[a-f0-9-]+$/);
  await expect(page.locator('text=Property submitted successfully')).toBeVisible();
});
```

**style-selection.spec.ts:**
```typescript
test('Style selection flow', async ({ page }) => {
  // 1. Login and navigate to property
  await page.goto('/owner/properties/[test-property-id]');
  
  // 2. Click "Select Style"
  await page.click('button:has-text("Select Style")');
  
  // 3. Browse styles
  await page.waitForSelector('.style-card');
  
  // 4. Filter by type
  await page.selectOption('[name="type"]', 'FURNITURE');
  
  // 5. Select a style
  await page.click('.style-card:first-child');
  await page.click('button:has-text("Select This Style")');
  
  // 6. Verify style appears on property detail
  await expect(page.locator('.selected-style')).toBeVisible();
});
```

### Coverage target:

- **Backend:** 70%+ line coverage (service + controller layers)
- **Frontend:** 70%+ component coverage
- **E2E:** Happy paths for P1.2-B and P1.2-C

---

## 5) Seed & Sample Data (dev only)

### What to seed:

**Admin User:**
- Email: admin@easyluxury.com
- Role: ADMIN

**Property Owners:**
- Email: owner1@test.com, owner2@test.com
- Role: OWNER

**Style Packages (10-15 samples):**
- 5× FURNITURE styles (Modern, Classic, Minimalist, Luxury, Mediterranean)
- 5× FINISHING styles (Contemporary, Traditional, Industrial, Scandinavian, Art Deco)
- 3× COMPLETE packages (combining furniture + finishing)
- All with sample images (placeholder URLs or actual S3 URLs)
- Price ranges: 50,000 - 500,000 EGP/SAR/AED
- All marked as `preApproved: true`, `active: true`

**Sample Properties (5-10):**
- Mix of APARTMENT, VILLA, TOWNHOUSE
- Various cities: Cairo, Dubai, Riyadh, Alexandria
- Mix of purposes: PERSONAL, INVESTMENT, RENTAL
- 2-3 with selected styles
- Sample photos (placeholder URLs)

### How to run:

**Backend Seeder:**
```java
// src/main/java/com/easyluxury/seeder/CoreDomainSeeder.java
@Component
@Profile("dev")
public class CoreDomainSeeder implements CommandLineRunner {
    
    @Override
    public void run(String... args) throws Exception {
        if (Arrays.asList(args).contains("--seed=true")) {
            seedUsers();
            seedStylePackages();
            seedProperties();
            log.info("Core domain seeding complete");
        }
    }
    
    private void seedStylePackages() {
        // Create 15 style packages with images
    }
    
    private void seedProperties() {
        // Create 10 sample properties with media
    }
}
```

**Commands:**
```bash
# Start services
make up

# Run seeder
make seed

# Verify data
curl http://localhost:8080/api/styles | jq
curl http://localhost:8080/api/properties | jq
```

---

## 6) Acceptance Criteria Matrix

### P1.2-A (Core Schema):
- **AC#1**: All entities (Property, PropertyMedia, StylePackage, StyleImage, Project, Bid, Listing) created with correct JPA mappings
- **AC#2**: Liquibase changeset V003 runs successfully on clean DB
- **AC#3**: All FK constraints and indexes created correctly
- **AC#4**: Cascade operations work (e.g., deleting Property deletes PropertyMedia)
- **AC#5**: Unique constraints enforced (property_id in projects/listings)
- **AC#6**: Enums defined correctly (PropertyType, PropertyPurpose, PropertyStatus, StyleType, etc.)
- **AC#7**: No N+1 query issues (lazy loading configured correctly)

### P1.2-B (Property Submission + Media Uploads):
- **AC#1**: User can submit property through 5-step wizard (Location → Details → Photos → Budget → Review)
- **AC#2**: Each wizard step validates independently (cannot proceed with invalid data)
- **AC#3**: At least 3 photos required for submission
- **AC#4**: Photos upload to MinIO using presigned PUT URLs
- **AC#5**: Upload progress shown for each file
- **AC#6**: Property detail page displays all uploaded photos in gallery
- **AC#7**: User can delete individual photos/documents
- **AC#8**: Only property owner can view/edit their properties (RBAC enforced)
- **AC#9**: Map picker allows location selection with lat/lng coordinates
- **AC#10**: Property list shows all user's properties with pagination
- **AC#11**: Property card displays primary photo, address, size, bedrooms/bathrooms
- **AC#12**: Mobile-responsive wizard and gallery
- **AC#13**: File type and size validation on upload (jpg/png, max 5MB)

### P1.2-C (Style Selection):
- **AC#1**: Style library displays all pre-approved styles in grid layout
- **AC#2**: Styles filterable by type (FURNITURE, FINISHING, COMPLETE)
- **AC#3**: Each style card shows name, type, price range, sample images
- **AC#4**: User can click style to view full detail (all images, features, description)
- **AC#5**: User can select style for their property from style library
- **AC#6**: Selected style persists to Property entity (selectedStyle FK)
- **AC#7**: Selected style appears on property overview/detail page
- **AC#8**: User can change selected style (update association)
- **AC#9**: Admin can create/edit/delete style packages (separate admin endpoints)
- **AC#10**: Only active and pre-approved styles shown to regular users
- **AC#11**: Price range filter works (slider or min/max inputs)
- **AC#12**: Search by style name works

---

## 7) Risks & Rollback

### Risks:

**Risk 1: S3/MinIO Integration Complexity**
- **Impact:** High - Core feature dependency
- **Probability:** Medium
- **Description:** Presigned URL generation may fail; upload to S3 may timeout; CORS issues

**Risk 2: Large File Uploads**
- **Impact:** Medium - UX degradation
- **Probability:** Medium
- **Description:** 5MB photos × 20 files = 100MB uploads may timeout on slow connections

**Risk 3: Schema Cascades**
- **Impact:** High - Data integrity
- **Probability:** Low
- **Description:** Incorrect cascade settings could cause accidental data loss

**Risk 4: RBAC Bypass**
- **Impact:** Critical - Security
- **Probability:** Low
- **Description:** Missing row-level checks could allow users to access others' properties

**Risk 5: N+1 Query Problems**
- **Impact:** Medium - Performance
- **Probability:** Medium
- **Description:** Lazy loading of PropertyMedia/StyleImages could cause N+1 queries

### Mitigations:

**Mitigation 1 (S3/MinIO):**
- Use LocalStack for local testing
- Implement retry logic with exponential backoff
- Configure CORS properly in docker-compose
- Add integration tests with Testcontainers + MinIO

**Mitigation 2 (Large Uploads):**
- Client-side file compression before upload
- Upload files in parallel (max 3 concurrent)
- Show clear progress indicators
- Implement resume capability if needed (Phase 2)

**Mitigation 3 (Cascades):**
- Explicitly define cascade types (CascadeType.PERSIST, CascadeType.MERGE)
- Add integration tests for delete operations
- Use soft delete for Property entity (status = ARCHIVED)

**Mitigation 4 (RBAC):**
- Always check user ID in service layer before row-level operations
- Add integration tests for access control
- Use `@PreAuthorize` on all endpoints
- Log all access attempts for audit

**Mitigation 5 (N+1):**
- Use `@EntityGraph` or `fetch join` for PropertyMedia
- Add Hibernate SQL logging in tests
- Monitor query count in integration tests
- Use `@BatchSize` annotation where appropriate

### Rollback plan:

**Database Rollback:**
```bash
# Rollback Liquibase changeset
cd backend
./mvnw liquibase:rollback -Dliquibase.rollbackCount=1

# Or rollback to specific tag
./mvnw liquibase:rollback -Dliquibase.rollbackTag=v002
```

**Code Rollback:**
```bash
# Revert to previous commit
git revert <commit-hash>

# Or revert merge
git revert -m 1 <merge-commit-hash>

# Push to trigger rollback deployment
git push origin main
```

**Frontend Rollback:**
- Feature flags for new routes (environment variable)
- Remove new routes from navigation menu
- Redirect to old pages if needed

**Data Cleanup (if needed):**
```sql
-- Manual cleanup if rollback fails
DELETE FROM property_media WHERE property_id IN (SELECT id FROM properties WHERE created_at > '2025-10-11');
DELETE FROM properties WHERE created_at > '2025-10-11';
DELETE FROM style_images WHERE style_package_id IN (SELECT id FROM style_packages WHERE created_at > '2025-10-11');
DELETE FROM style_packages WHERE created_at > '2025-10-11';
```

---

## 8) Commands to run (print, don't execute yet)

```bash
# ============================================
# SETUP (one-time)
# ============================================

# Start infrastructure (Postgres + MinIO)
docker compose up -d

# Verify services running
docker ps

# Check Postgres
docker exec -it easyluxury-postgres psql -U easyluxury -c "SELECT version();"

# Check MinIO (web UI: http://localhost:9001)
# Default creds: minioadmin / minioadmin


# ============================================
# BACKEND DEVELOPMENT
# ============================================

# Run backend (applies Liquibase migrations automatically)
make be_run

# OR with Maven directly
cd backend
./mvnw spring-boot:run

# Run backend tests (unit + integration with Testcontainers)
make be_test

# OR with Maven
cd backend
./mvnw test

# Run only unit tests
cd backend
./mvnw test -Dtest="*Test" -DfailIfNoTests=false

# Run only integration tests
cd backend
./mvnw test -Dtest="*IntegrationTest" -DfailIfNoTests=false

# Check test coverage
cd backend
./mvnw jacoco:report
open target/site/jacoco/index.html

# Run Liquibase commands manually
cd backend
./mvnw liquibase:status
./mvnw liquibase:update
./mvnw liquibase:rollback -Dliquibase.rollbackCount=1


# ============================================
# SEEDING (dev only)
# ============================================

# Run seeder
make seed

# OR manually
cd backend
./mvnw spring-boot:run -Dspring-boot.run.arguments="--seed=true"


# ============================================
# OPENAPI / TYPE GENERATION
# ============================================

# Generate OpenAPI JSON (backend must be running)
make openapi

# OR manually
curl -s http://localhost:8080/v3/api-docs > backend/openapi.json

# Generate TypeScript types for frontend
cd frontend
npx openapi-typescript ../backend/openapi.json -o src/types/api.ts

# Type check frontend
cd frontend
npm run type-check


# ============================================
# FRONTEND DEVELOPMENT
# ============================================

# Install dependencies (first time)
cd frontend
npm ci

# Run frontend dev server
make fe_dev

# OR manually
cd frontend
npm run dev

# Build frontend for production
make fe_build

# OR manually
cd frontend
npm run build

# Lint frontend
make lint

# OR manually
cd frontend
npm run lint

# Format code
make fmt

# OR manually
cd frontend
npx prettier --write .


# ============================================
# TESTING
# ============================================

# Run ALL backend tests
make be_test

# Run frontend unit tests
cd frontend
npm test

# Run frontend tests in watch mode
cd frontend
npm test -- --watch

# Run E2E tests (Playwright)
cd frontend
npx playwright test

# Run E2E tests in UI mode
cd frontend
npx playwright test --ui

# Run specific E2E test
cd frontend
npx playwright test property-submission.spec.ts


# ============================================
# VERIFICATION
# ============================================

# Check backend health
curl http://localhost:8080/actuator/health

# Check OpenAPI docs
open http://localhost:8080/api/docs

# List properties (requires auth token)
curl -H "Authorization: Bearer <token>" http://localhost:8080/api/properties

# List styles (requires auth)
curl -H "Authorization: Bearer <token>" http://localhost:8080/api/styles

# Check frontend build
cd frontend && npm run build && npm run start


# ============================================
# CLEANUP
# ============================================

# Stop all services
make down

# OR manually
docker compose down -v

# Clean backend build
cd backend
./mvnw clean

# Clean frontend build
cd frontend
rm -rf .next node_modules
npm ci
```

---

## 9) Deliverables

### File tree (added/changed)

**Backend:**
```
backend/src/main/java/com/easyluxury/
├── entity/
│   ├── Property.java                          [NEW]
│   ├── PropertyMedia.java                     [NEW]
│   ├── Address.java                           [NEW] (Embeddable)
│   ├── StylePackage.java                      [NEW]
│   ├── StyleImage.java                        [NEW]
│   ├── Project.java                           [NEW]
│   ├── Bid.java                               [NEW]
│   └── Listing.java                           [NEW]
├── repository/
│   ├── PropertyRepository.java                [NEW]
│   ├── PropertyMediaRepository.java           [NEW]
│   ├── StylePackageRepository.java            [NEW]
│   ├── StyleImageRepository.java              [NEW]
│   ├── ProjectRepository.java                 [NEW]
│   ├── BidRepository.java                     [NEW]
│   └── ListingRepository.java                 [NEW]
├── dto/
│   ├── property/
│   │   ├── CreatePropertyRequest.java         [NEW]
│   │   ├── UpdatePropertyRequest.java         [NEW]
│   │   ├── PropertyDto.java                   [NEW]
│   │   ├── PropertyListDto.java               [NEW]
│   │   ├── PropertyMediaDto.java              [NEW]
│   │   ├── PresignedUploadRequest.java        [NEW]
│   │   ├── PresignedUploadResponse.java       [NEW]
│   │   ├── PresignedUrl.java                  [NEW]
│   │   └── SelectStyleRequest.java            [NEW]
│   ├── style/
│   │   ├── StylePackageDto.java               [NEW]
│   │   ├── StyleImageDto.java                 [NEW]
│   │   ├── CreateStylePackageRequest.java     [NEW]
│   │   └── UpdateStylePackageRequest.java     [NEW]
│   ├── project/
│   │   └── ProjectDto.java                    [NEW] (basic)
│   ├── bid/
│   │   └── BidDto.java                        [NEW] (basic)
│   └── listing/
│       └── ListingDto.java                    [NEW] (basic)
├── mapper/
│   ├── PropertyMapper.java                    [NEW]
│   ├── StylePackageMapper.java                [NEW]
│   ├── ProjectMapper.java                     [NEW]
│   ├── BidMapper.java                         [NEW]
│   └── ListingMapper.java                     [NEW]
├── service/
│   ├── PropertyService.java                   [NEW]
│   ├── StylePackageService.java               [NEW]
│   ├── S3Service.java                         [NEW]
│   ├── ProjectService.java                    [NEW] (basic)
│   ├── BidService.java                        [NEW] (basic)
│   └── ListingService.java                    [NEW] (basic)
├── facade/
│   ├── PropertyFacade.java                    [NEW]
│   └── StylePackageFacade.java                [NEW]
├── controller/
│   ├── PropertyController.java                [NEW]
│   ├── StylePackageController.java            [NEW]
│   ├── ProjectController.java                 [NEW] (basic)
│   ├── BidController.java                     [NEW] (basic)
│   └── ListingController.java                 [NEW] (basic)
├── seeder/
│   └── CoreDomainSeeder.java                  [NEW]
└── config/
    └── S3Config.java                          [NEW]

backend/src/main/resources/
├── db/changelog/
│   ├── db.changelog-master.yaml               [MODIFIED] (add V003)
│   └── V003__core_domain.yaml                 [NEW]
└── application.yml                            [MODIFIED] (add S3 config)

backend/src/test/java/com/easyluxury/
├── service/
│   ├── PropertyServiceTest.java               [NEW]
│   └── StylePackageServiceTest.java           [NEW]
├── controller/
│   ├── PropertyControllerTest.java            [NEW]
│   └── StylePackageControllerTest.java        [NEW]
└── integration/
    ├── PropertyIntegrationTest.java           [NEW]
    └── StylePackageIntegrationTest.java       [NEW]
```

**Frontend:**
```
frontend/src/
├── app/(dashboard)/
│   ├── owner/
│   │   └── properties/
│   │       ├── page.tsx                       [NEW] (list)
│   │       ├── new/
│   │       │   └── page.tsx                   [NEW] (wizard)
│   │       └── [id]/
│   │           ├── page.tsx                   [NEW] (detail)
│   │           ├── edit/
│   │           │   └── page.tsx               [NEW]
│   │           └── style-select/
│   │               └── page.tsx               [NEW]
│   └── marketplace/
│       └── styles/
│           └── page.tsx                       [NEW]
├── components/
│   ├── property/
│   │   ├── PropertyWizard/
│   │   │   ├── PropertyWizard.tsx             [NEW]
│   │   │   ├── Step1_Location.tsx             [NEW]
│   │   │   ├── Step2_Details.tsx              [NEW]
│   │   │   ├── Step3_Photos.tsx               [NEW]
│   │   │   ├── Step4_Budget.tsx               [NEW]
│   │   │   └── Step5_Review.tsx               [NEW]
│   │   ├── MediaUpload/
│   │   │   ├── MediaUpload.tsx                [NEW]
│   │   │   ├── ImagePreview.tsx               [NEW]
│   │   │   └── UploadProgress.tsx             [NEW]
│   │   ├── PropertyCard.tsx                   [NEW]
│   │   ├── PropertyDetail.tsx                 [NEW]
│   │   ├── PropertyList.tsx                   [NEW]
│   │   └── MapPicker.tsx                      [NEW]
│   └── marketplace/
│       ├── StyleLibrary/
│       │   ├── StyleLibrary.tsx               [NEW]
│       │   ├── StyleCard.tsx                  [NEW]
│       │   ├── StyleDetail.tsx                [NEW]
│       │   └── StyleFilter.tsx                [NEW]
│       └── StyleSelector.tsx                  [NEW]
├── contexts/
│   ├── PropertyContext.tsx                    [NEW]
│   └── MarketplaceContext.tsx                 [NEW]
├── hooks/
│   ├── useProperty.ts                         [NEW]
│   ├── useStyle.ts                            [NEW]
│   └── useMediaUpload.ts                      [NEW]
├── types/
│   ├── api.ts                                 [GENERATED]
│   ├── property.ts                            [NEW]
│   └── marketplace.ts                         [NEW]
├── services/
│   ├── propertyService.ts                     [NEW]
│   ├── styleService.ts                        [NEW]
│   └── uploadService.ts                       [NEW]
└── __tests__/
    ├── components/
    │   ├── PropertyWizard.test.tsx            [NEW]
    │   ├── MediaUpload.test.tsx               [NEW]
    │   ├── PropertyCard.test.tsx              [NEW]
    │   └── StyleCard.test.tsx                 [NEW]
    ├── hooks/
    │   └── useProperty.test.ts                [NEW]
    └── e2e/
        ├── property-submission.spec.ts        [NEW]
        └── style-selection.spec.ts            [NEW]
```

**Infrastructure:**
```
docker-compose.yml                             [MODIFIED] (ensure MinIO configured)
Makefile                                       [NO CHANGE]
backend/pom.xml                                [MODIFIED] (add AWS SDK dependency if needed)
frontend/package.json                          [MODIFIED] (add mapping library for MapPicker)
```

### Generated diffs

**Summary of changes:**
- **Backend:** ~3,500 lines (entities, repos, services, controllers, DTOs, mappers, tests)
- **Frontend:** ~2,500 lines (components, pages, hooks, contexts, tests)
- **Database:** 1 new changeset with 7 tables
- **Tests:** ~1,500 lines (unit + integration + E2E)

**Key diffs:**
1. V003__core_domain.yaml creates all core tables
2. PropertyController implements all CRUD + presigned URL endpoints
3. PropertyWizard implements 5-step form with validation
4. MediaUpload implements drag-drop with progress tracking
5. StyleLibrary implements filterable grid view

### OpenAPI delta summary

**New Tags:**
- `Properties` - Property management endpoints
- `Styles` - Style package endpoints

**New Endpoints:**
```
POST   /api/properties
GET    /api/properties
GET    /api/properties/{id}
PUT    /api/properties/{id}
DELETE /api/properties/{id}
POST   /api/properties/{id}/media:presign
DELETE /api/properties/{id}/media/{mediaId}
PUT    /api/properties/{id}/style

GET    /api/styles
GET    /api/styles/{id}
POST   /api/admin/styles
PUT    /api/admin/styles/{id}
DELETE /api/admin/styles/{id}

POST   /api/projects          (placeholder)
GET    /api/projects/{id}     (placeholder)
POST   /api/projects/{id}/bids (placeholder)
GET    /api/bids/{id}         (placeholder)
POST   /api/listings          (placeholder)
GET    /api/listings/{id}     (placeholder)
```

**New Schemas:**
- `CreatePropertyRequest`
- `UpdatePropertyRequest`
- `PropertyDto`
- `PropertyListDto`
- `PropertyMediaDto`
- `PresignedUploadRequest`
- `PresignedUploadResponse`
- `PresignedUrl`
- `SelectStyleRequest`
- `StylePackageDto`
- `StyleImageDto`
- `CreateStylePackageRequest`
- `UpdateStylePackageRequest`
- `ProjectDto`
- `BidDto`
- `ListingDto`

### Proposed conventional commits (per ticket)

**P1.2-A (Core Schema):**
```
feat(backend): add core domain entities (Property, StylePackage, Project, Bid, Listing)

- Add Property entity with Address embeddable
- Add PropertyMedia entity for photos/documents
- Add StylePackage and StyleImage entities
- Add Project, Bid, Listing entities (basic)
- Create V003__core_domain.yaml Liquibase changeset
- Add repositories for all entities
- Configure JPA mappings and cascades
- Add indexes and unique constraints

BREAKING CHANGE: New database schema requires migration

Refs: P1.2-A
```

**P1.2-B (Property Submission + Media Uploads):**
```
feat(property): implement property submission with media uploads

Backend:
- Add PropertyController with CRUD endpoints
- Add PropertyService with business logic
- Add S3Service for presigned URL generation
- Implement DTOs and MapStruct mappers
- Add RBAC with @PreAuthorize
- Add validation rules
- Add unit, slice, and integration tests

Frontend:
- Add 5-step PropertyWizard component
- Add MediaUpload with drag-drop and progress
- Add PropertyCard and PropertyDetail components
- Add PropertyList with pagination
- Add MapPicker for location selection
- Implement React Query hooks
- Add Zod validation schemas
- Add E2E test for submission flow

Closes: P1.2-B
```

**P1.2-C (Style Selection):**
```
feat(marketplace): implement style package library and selection

Backend:
- Add StylePackageController with list/detail endpoints
- Add StylePackageService for filtering
- Add admin endpoints for style management
- Add DTOs and mappers
- Add tests

Frontend:
- Add StyleLibrary with filterable grid
- Add StyleCard and StyleDetail components
- Add StyleFilter for type/price filtering
- Add style selection to property flow
- Implement React Query hooks
- Add E2E test for style selection

Closes: P1.2-C
```

**Seeding:**
```
feat(seed): add core domain seeder for dev environment

- Add CoreDomainSeeder with CommandLineRunner
- Seed 15 sample style packages
- Seed 10 sample properties
- Add sample images and data
- Document seeding commands

Refs: P1.2-A, P1.2-B, P1.2-C
```

---

**END OF PLAN**

**Status:** ✅ Ready for implementation  
**Created:** 2025-10-11
