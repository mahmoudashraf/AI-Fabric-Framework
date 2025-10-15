# 🗺️ Component Mapping & Reuse Guide - EasyLuxury Platform

**Document Purpose:** Detailed mapping of existing template components to new EasyLuxury requirements with specific widget reuse recommendations

**Created:** October 2025  
**Related:** `FRONTEND_ADAPTATION_PLAN.md`

---

## 📋 Table of Contents

1. [Page-to-Page Mapping](#page-to-page-mapping)
2. [Phase 1 Component Reuse Guide](#phase-1-component-reuse-guide)
3. [Widget Library Reference](#widget-library-reference)
4. [Step-by-Step Component Adaptation](#step-by-step-component-adaptation)
5. [Quick Reference Tables](#quick-reference-tables)

---

## 🎯 Page-to-Page Mapping

### Complete Mapping Table

| EasyLuxury Page | Existing Template Page | Reuse % | Key Changes |
|-----------------|------------------------|---------|-------------|
| **Authentication** |
| `/auth/login` | `views/authentication/auth3/login.tsx` | 90% | Update branding, add Supabase |
| `/auth/register` | `views/authentication/auth3/register.tsx` | 85% | Add role selection |
| `/auth/oauth-callback` | NEW | 0% | Build new |
| **Property Owner** |
| `/property/submit` | `views/apps/e-commerce/checkout.tsx` | 60% | Adapt wizard steps |
| `/property/[id]` | `views/apps/e-commerce/product-details.tsx` | 70% | Property details instead of product |
| `/property/list` | `views/apps/e-commerce/products.tsx` | 75% | Property cards instead of products |
| `/property/[id]/style-select` | `views/apps/e-commerce/products.tsx` | 60% | Style gallery view |
| **Agency** |
| `/agency/register` | `views/authentication/auth3/register.tsx` + wizard | 50% | Multi-step agency signup |
| `/agency/dashboard` | `views/dashboard/default.tsx` | 80% | Agency metrics instead of generic |
| `/agency/packages` | `views/apps/customer/product.tsx` | 70% | Package management |
| `/agency/[id]` (public) | `views/apps/user/social-profile.tsx` | 75% | Agency profile |
| `/agency/team` | `views/apps/user/list/list1.tsx` | 80% | Team member management |
| **Project Management** |
| `/project/[id]` | `views/apps/customer/order-details.tsx` | 70% | Project instead of order |
| `/project/list` | `views/apps/customer/order-list.tsx` | 75% | Projects instead of orders |
| `/project/[id]/timeline` | `views/ui-elements/advance/timeline.tsx` | 65% | Project milestones |
| `/project/[id]/bids` | `views/apps/customer/product-review.tsx` | 60% | Bid comparison |
| **Marketplace** |
| `/marketplace/search` | `views/apps/e-commerce/products.tsx` | 70% | Provider search |
| `/marketplace/styles` | `views/apps/e-commerce/products.tsx` | 75% | Style library |
| `/provider/[id]` | `views/apps/user/social-profile.tsx` | 80% | Provider public profile |
| **Rental Management** |
| `/listing/[id]` (public) | `views/apps/e-commerce/product-details.tsx` | 70% | Listing details |
| `/listing/manage` | `views/apps/e-commerce/products.tsx` | 75% | My listings |
| `/booking/calendar` | `views/apps/calendar.tsx` | 60% | Rental calendar |
| `/booking/list` | `views/apps/customer/order-list.tsx` | 75% | Bookings list |
| **Admin** |
| `/admin/dashboard` | `views/dashboard/analytics.tsx` | 85% | Platform analytics |
| `/admin/agencies` | `views/apps/customer/customer-list.tsx` | 80% | Agency approvals |
| `/admin/users` | `views/apps/customer/customer-list.tsx` | 85% | User management |

---

## 🔧 Phase 1 Component Reuse Guide

### Ticket P1.1-A: Auth & RBAC

#### 1. Login Page (`/auth/login`)

**Base Template:**
```
views/authentication/auth3/login.tsx
```

**Components to Reuse:**

| Component | Location | Usage | Changes |
|-----------|----------|-------|---------|
| `AuthWrapper` | `components/authentication/AuthWrapper.tsx` | Page layout | Update logo/branding |
| `AuthCardWrapper` | `components/authentication/AuthCardWrapper.tsx` | Card container | Keep as-is |
| `AuthFooter` | `components/ui-component/cards/AuthFooter.tsx` | Footer | Update links |
| `MainCard` | `components/ui-component/cards/MainCard.tsx` | Card wrapper | Keep as-is |
| `useAdvancedForm` | `hooks/enterprise/useAdvancedForm.ts` | Form logic | ✅ Use exactly as-is |

**Build New:**
- `SupabaseAuthProvider.tsx` - Supabase integration
- `OAuthButtons.tsx` - Social login buttons

**Example Adaptation:**
```typescript
// REUSE: Layout structure from existing login
import AuthWrapper from '@/components/authentication/AuthWrapper';
import AuthCardWrapper from '@/components/authentication/AuthCardWrapper';
import { useAdvancedForm } from '@/hooks/enterprise';

// NEW: Supabase integration
import { useSupabaseAuth } from '@/hooks/useSupabaseAuth';
import OAuthButtons from '@/components/authentication/OAuthButtons';

const LoginPage = () => {
  const { signIn } = useSupabaseAuth(); // NEW
  
  // REUSE: Form pattern
  const form = useAdvancedForm<LoginFormData>({
    initialValues: { email: '', password: '' },
    validationRules: {
      email: [
        { type: 'required', message: 'Email required' },
        { type: 'email', message: 'Invalid email' }
      ],
      password: [{ type: 'required', message: 'Password required' }]
    },
    onSubmit: async (values) => {
      await signIn(values.email, values.password); // NEW
    }
  });

  return (
    <AuthWrapper> {/* REUSE */}
      <AuthCardWrapper> {/* REUSE */}
        <form onSubmit={form.handleSubmit()}>
          {/* REUSE: TextField components */}
          <TextField
            value={form.values.email}
            onChange={form.handleChange('email')}
            error={Boolean(form.errors.email)}
            helperText={form.errors.email}
          />
          {/* ... */}
          
          {/* NEW: OAuth buttons */}
          <OAuthButtons />
        </form>
      </AuthCardWrapper>
    </AuthWrapper>
  );
};

export default withErrorBoundary(LoginPage); // REUSE
```

---

### Ticket P1.2-B: Property Submission

#### 1. Property Submission Wizard (`/property/submit`)

**Base Template:**
```
views/apps/e-commerce/checkout.tsx (has multi-step flow)
components/forms/forms-wizard/BasicWizard/ (wizard pattern)
```

**Components to Reuse:**

| Component | Location | Usage | Purpose |
|-----------|----------|-------|---------|
| `MainCard` | `components/ui-component/cards/MainCard.tsx` | Card container | Wizard container |
| `Stepper` | Material-UI | Step indicator | Progress display |
| `Grid` | Material-UI | Layout | Form layout |
| `TextField` | Material-UI | Inputs | All text inputs |
| `Button` | Material-UI | Navigation | Next/Back/Submit |
| `Autocomplete` | Material-UI | City selector | Location selection |
| `useAdvancedForm` | `hooks/enterprise` | ✅ Form logic | Step validation |
| `withErrorBoundary` | `hooks/enterprise` | ✅ Error protection | Page wrapper |

**Build New:**
- `PropertyWizard.tsx` - Main wizard component
- `MapPicker.tsx` - Location selection with map
- `MediaUpload.tsx` - Photo/document upload
- `BudgetSelector.tsx` - Budget input with currency

**Detailed Step Breakdown:**

##### Step 1: Location (`Step1_Location.tsx`)

**Reuse Pattern:**
```typescript
import { MainCard } from '@/components/ui-component/cards/MainCard';
import { Grid, TextField, Autocomplete } from '@mui/material';
import { useAdvancedForm } from '@/hooks/enterprise';

// NEW: Map integration
import MapPicker from '@/components/property/MapPicker';

const Step1_Location = ({ onNext, initialData }) => {
  const form = useAdvancedForm({
    initialValues: initialData || {
      street: '',
      city: '',
      country: '',
      coordinates: { lat: 0, lng: 0 }
    },
    validationRules: {
      street: [{ type: 'required', message: 'Street address required' }],
      city: [{ type: 'required', message: 'City required' }]
    },
    onSubmit: (values) => onNext(values)
  });

  return (
    <MainCard title="Property Location"> {/* REUSE */}
      <Grid container spacing={3}> {/* REUSE */}
        <Grid item xs={12}>
          {/* REUSE: TextField */}
          <TextField
            fullWidth
            label="Street Address"
            value={form.values.street}
            onChange={form.handleChange('street')}
            error={Boolean(form.errors.street)}
            helperText={form.errors.street}
          />
        </Grid>
        
        <Grid item xs={12} md={6}>
          {/* REUSE: Autocomplete for city */}
          <Autocomplete
            options={cities}
            value={form.values.city}
            onChange={(e, newValue) => form.setFieldValue('city', newValue)}
            renderInput={(params) => (
              <TextField {...params} label="City" />
            )}
          />
        </Grid>
        
        <Grid item xs={12}>
          {/* NEW: Map picker */}
          <MapPicker
            value={form.values.coordinates}
            onChange={(coords) => form.setFieldValue('coordinates', coords)}
          />
        </Grid>
        
        <Grid item xs={12}>
          {/* REUSE: Button */}
          <Button
            variant="contained"
            onClick={form.handleSubmit()}
            disabled={!form.isValid}
          >
            Next
          </Button>
        </Grid>
      </Grid>
    </MainCard>
  );
};
```

##### Step 2: Details (`Step2_Details.tsx`)

**Reuse from:** `views/apps/e-commerce/checkout.tsx` (billing info section)

```typescript
<MainCard title="Property Details"> {/* REUSE */}
  <Grid container spacing={3}> {/* REUSE */}
    <Grid item xs={12} md={6}>
      <TextField
        fullWidth
        label="Property Size (sqm)"
        type="number"
        value={form.values.size}
        onChange={form.handleChange('size')}
      />
    </Grid>
    
    <Grid item xs={12} md={6}>
      {/* REUSE: Select from Material-UI */}
      <FormControl fullWidth>
        <InputLabel>Property Type</InputLabel>
        <Select
          value={form.values.propertyType}
          onChange={form.handleChange('propertyType')}
        >
          <MenuItem value="APARTMENT">Apartment</MenuItem>
          <MenuItem value="VILLA">Villa</MenuItem>
          <MenuItem value="TOWNHOUSE">Townhouse</MenuItem>
        </Select>
      </FormControl>
    </Grid>
    
    <Grid item xs={12} md={4}>
      <TextField
        fullWidth
        label="Bedrooms"
        type="number"
        value={form.values.bedrooms}
        onChange={form.handleChange('bedrooms')}
      />
    </Grid>
    
    <Grid item xs={12} md={4}>
      <TextField
        fullWidth
        label="Bathrooms"
        type="number"
        value={form.values.bathrooms}
        onChange={form.handleChange('bathrooms')}
      />
    </Grid>
  </Grid>
</MainCard>
```

##### Step 3: Photos (`Step3_Photos.tsx`)

**Build New:** MediaUpload component

**Components to reference:**
- `components/forms/plugins/Dropzone` (if exists, check implementation)
- Use Material-UI `Button` + `input[type="file"]`

```typescript
import { MainCard } from '@/components/ui-component/cards/MainCard';
import MediaUpload from '@/components/property/MediaUpload'; // NEW

const Step3_Photos = ({ onNext, initialData }) => {
  const [files, setFiles] = useState<File[]>(initialData?.photos || []);
  
  const form = useAdvancedForm({
    initialValues: { photos: files },
    validationRules: {
      photos: [{
        type: 'custom',
        validator: (val) => val.length >= 3,
        message: 'At least 3 photos required'
      }]
    },
    onSubmit: async (values) => {
      // Upload to S3/MinIO
      const urls = await getPresignedUrls(values.photos.length);
      await uploadFiles(urls, values.photos);
      onNext(values);
    }
  });

  return (
    <MainCard title="Property Photos"> {/* REUSE */}
      <MediaUpload
        files={files}
        onChange={setFiles}
        maxFiles={20}
        acceptedTypes={['image/jpeg', 'image/png']}
        maxFileSize={5 * 1024 * 1024} // 5MB
      />
      
      {form.errors.photos && (
        <Alert severity="error">{form.errors.photos}</Alert>
      )}
      
      <Button
        variant="contained"
        onClick={form.handleSubmit()}
        disabled={!form.isValid || form.isSubmitting}
      >
        {form.isSubmitting ? 'Uploading...' : 'Next'}
      </Button>
    </MainCard>
  );
};
```

##### Step 4: Budget (`Step4_Budget.tsx`)

**Reuse from:** Form input patterns

```typescript
<MainCard title="Budget & Purpose"> {/* REUSE */}
  <Grid container spacing={3}>
    <Grid item xs={12} md={6}>
      <TextField
        fullWidth
        label="Budget"
        type="number"
        value={form.values.budget}
        onChange={form.handleChange('budget')}
        InputProps={{
          startAdornment: (
            <InputAdornment position="start">
              {form.values.currency}
            </InputAdornment>
          )
        }}
      />
    </Grid>
    
    <Grid item xs={12} md={6}>
      <FormControl fullWidth>
        <InputLabel>Currency</InputLabel>
        <Select
          value={form.values.currency}
          onChange={form.handleChange('currency')}
        >
          <MenuItem value="EGP">EGP</MenuItem>
          <MenuItem value="SAR">SAR</MenuItem>
          <MenuItem value="AED">AED</MenuItem>
        </Select>
      </FormControl>
    </Grid>
    
    <Grid item xs={12}>
      <FormControl component="fieldset">
        <FormLabel>Property Purpose</FormLabel>
        <RadioGroup
          value={form.values.purpose}
          onChange={form.handleChange('purpose')}
        >
          <FormControlLabel value="PERSONAL" control={<Radio />} label="Personal Use" />
          <FormControlLabel value="INVESTMENT" control={<Radio />} label="Investment" />
          <FormControlLabel value="RENTAL" control={<Radio />} label="Rental Income" />
        </RadioGroup>
      </FormControl>
    </Grid>
  </Grid>
</MainCard>
```

##### Step 5: Review (`Step5_Review.tsx`)

**Reuse from:** `views/apps/e-commerce/checkout.tsx` (order review)

```typescript
import { MainCard, SubCard } from '@/components/ui-component/cards';

<MainCard title="Review & Submit"> {/* REUSE */}
  <Stack spacing={2}>
    {/* REUSE: SubCard for sections */}
    <SubCard title="Location">
      <Typography>{formData.street}, {formData.city}</Typography>
    </SubCard>
    
    <SubCard title="Details">
      <Grid container spacing={1}>
        <Grid item xs={6}>
          <Typography variant="body2">Size: {formData.size} sqm</Typography>
        </Grid>
        <Grid item xs={6}>
          <Typography variant="body2">Type: {formData.propertyType}</Typography>
        </Grid>
      </Grid>
    </SubCard>
    
    <SubCard title="Photos">
      <Grid container spacing={2}>
        {formData.photos.map((photo, i) => (
          <Grid item xs={6} md={3} key={i}>
            <img src={URL.createObjectURL(photo)} style={{ width: '100%' }} />
          </Grid>
        ))}
      </Grid>
    </SubCard>
    
    <Box sx={{ display: 'flex', gap: 2 }}>
      <Button onClick={onBack}>Back</Button>
      <Button variant="contained" onClick={handleSubmit}>
        Submit Property
      </Button>
    </Box>
  </Stack>
</MainCard>
```

---

#### 2. Property List (`/property/list`)

**Base Template:**
```
views/apps/e-commerce/products.tsx
```

**Components to Reuse:**

| Component | Location | Usage |
|-----------|----------|-------|
| `ProductCard` | `components/ui-component/cards/ProductCard.tsx` | Adapt to `PropertyCard` |
| `useTableLogic` | `hooks/enterprise` | ✅ Sorting/filtering |
| `Grid` | Material-UI | Card grid layout |
| `TextField` | Material-UI | Search input |
| `Chip` | Material-UI | Status badges |
| `Pagination` | Material-UI | Page navigation |

**Adaptation Example:**
```typescript
// ADAPT: ProductCard → PropertyCard
// Keep: Image, title, subtitle, action button layout
// Change: Product data → Property data

import { ProductCard } from '@/components/ui-component/cards/ProductCard';

const PropertyCard = ({ property }) => {
  return (
    <Card>
      <CardMedia
        component="img"
        height="200"
        image={property.photos[0]}
        alt={property.address}
      />
      <CardContent>
        {/* REUSE: Typography components */}
        <Typography variant="h5">{property.address}</Typography>
        <Typography variant="body2" color="text.secondary">
          {property.city}, {property.country}
        </Typography>
        
        {/* REUSE: Chip for status */}
        <Chip
          label={property.status}
          color={property.status === 'ACTIVE' ? 'success' : 'default'}
          size="small"
        />
        
        <Grid container spacing={1} sx={{ mt: 1 }}>
          <Grid item xs={4}>
            <Typography variant="caption">
              {property.bedrooms} beds
            </Typography>
          </Grid>
          <Grid item xs={4}>
            <Typography variant="caption">
              {property.bathrooms} baths
            </Typography>
          </Grid>
          <Grid item xs={4}>
            <Typography variant="caption">
              {property.size} sqm
            </Typography>
          </Grid>
        </Grid>
      </CardContent>
      <CardActions>
        {/* REUSE: Button component */}
        <Button size="small" onClick={() => router.push(`/property/${property.id}`)}>
          View Details
        </Button>
        <Button size="small" onClick={() => router.push(`/property/${property.id}/edit`)}>
          Edit
        </Button>
      </CardActions>
    </Card>
  );
};

// List page with filtering
const PropertyList = () => {
  const { data: properties = [] } = useProperties();
  
  // REUSE: useTableLogic for filtering
  const table = useTableLogic<Property>({
    data: properties,
    searchFields: ['address', 'city', 'country'],
    defaultOrderBy: 'createdAt',
    defaultRowsPerPage: 12
  });

  return (
    <Box>
      {/* REUSE: TextField for search */}
      <TextField
        fullWidth
        placeholder="Search properties..."
        value={table.search}
        onChange={table.handleSearch}
        InputProps={{
          startAdornment: <SearchIcon />
        }}
      />
      
      {/* REUSE: Grid layout */}
      <Grid container spacing={3}>
        {table.sortedAndPaginatedRows.map(property => (
          <Grid item xs={12} sm={6} md={4} key={property.id}>
            <PropertyCard property={property} />
          </Grid>
        ))}
      </Grid>
      
      {/* REUSE: Pagination */}
      <Pagination
        count={Math.ceil(table.filteredRows.length / table.rowsPerPage)}
        page={table.page + 1}
        onChange={(e, page) => table.handleChangePage(e, page - 1)}
      />
    </Box>
  );
};

export default withErrorBoundary(PropertyList);
```

---

#### 3. Property Detail (`/property/[id]`)

**Base Template:**
```
views/apps/e-commerce/product-details.tsx
```

**Components to Reuse:**

| Component | Location | Usage |
|-----------|----------|-------|
| `MainCard` | `components/ui-component/cards/MainCard.tsx` | Main container |
| `SubCard` | `components/ui-component/cards/SubCard.tsx` | Section cards |
| `Grid` | Material-UI | Layout |
| `Tabs` | Material-UI | Section navigation |
| `ImageList` | Material-UI | Photo gallery |
| `Chip` | Material-UI | Tags/status |
| `Button` | Material-UI | Actions |

**Adaptation Example:**
```typescript
// REUSE: Layout structure from product-details.tsx

import { MainCard, SubCard } from '@/components/ui-component/cards';
import { Tabs, Tab, ImageList, ImageListItem } from '@mui/material';

const PropertyDetail = ({ propertyId }) => {
  const { data: property, isLoading } = useQuery({
    queryKey: ['property', propertyId],
    queryFn: () => api.getProperty(propertyId)
  });

  if (isLoading) return <Skeleton variant="rectangular" height={600} />;

  return (
    <Grid container spacing={3}>
      {/* Left column: Photos */}
      <Grid item xs={12} md={7}>
        <MainCard>
          {/* REUSE: ImageList for gallery */}
          <ImageList cols={1} rowHeight={400}>
            <ImageListItem>
              <img src={property.photos[0]} alt="Main" />
            </ImageListItem>
          </ImageList>
          
          <ImageList cols={4} gap={8}>
            {property.photos.slice(1).map((photo, i) => (
              <ImageListItem key={i}>
                <img src={photo} alt={`Photo ${i + 1}`} />
              </ImageListItem>
            ))}
          </ImageList>
        </MainCard>
      </Grid>
      
      {/* Right column: Details */}
      <Grid item xs={12} md={5}>
        <MainCard>
          {/* REUSE: Typography components */}
          <Typography variant="h3">{property.address}</Typography>
          <Typography variant="h5" color="text.secondary">
            {property.city}, {property.country}
          </Typography>
          
          {/* REUSE: Chip for status */}
          <Chip
            label={property.status}
            color={property.status === 'ACTIVE' ? 'success' : 'default'}
          />
          
          <Divider sx={{ my: 2 }} />
          
          {/* REUSE: SubCard for sections */}
          <SubCard title="Property Details">
            <Grid container spacing={2}>
              <Grid item xs={6}>
                <Typography variant="caption" color="text.secondary">
                  Size
                </Typography>
                <Typography variant="body1">{property.size} sqm</Typography>
              </Grid>
              <Grid item xs={6}>
                <Typography variant="caption" color="text.secondary">
                  Type
                </Typography>
                <Typography variant="body1">{property.propertyType}</Typography>
              </Grid>
              <Grid item xs={6}>
                <Typography variant="caption" color="text.secondary">
                  Bedrooms
                </Typography>
                <Typography variant="body1">{property.bedrooms}</Typography>
              </Grid>
              <Grid item xs={6}>
                <Typography variant="caption" color="text.secondary">
                  Bathrooms
                </Typography>
                <Typography variant="body1">{property.bathrooms}</Typography>
              </Grid>
            </Grid>
          </SubCard>
          
          <SubCard title="Budget">
            <Typography variant="h4">
              {property.currency} {property.budget.toLocaleString()}
            </Typography>
          </SubCard>
          
          {/* REUSE: Button group */}
          <Stack direction="row" spacing={2} sx={{ mt: 2 }}>
            <Button variant="contained" fullWidth>
              Select Style
            </Button>
            <Button variant="outlined" fullWidth>
              Edit Property
            </Button>
          </Stack>
        </MainCard>
      </Grid>
    </Grid>
  );
};

export default withErrorBoundary(PropertyDetail);
```

---

### Ticket P1.1-B: Agency Registration

#### Agency Registration Wizard (`/agency/register`)

**Base Template:**
```
components/forms/forms-wizard/ValidationWizard/ (multi-step with validation)
views/authentication/auth3/register.tsx (registration flow)
```

**Components to Reuse:**

| Component | Location | Usage |
|-----------|----------|-------|
| `Stepper` | Material-UI | Wizard progress |
| `MainCard` | `components/ui-component/cards/MainCard.tsx` | Step containers |
| `useAdvancedForm` | `hooks/enterprise` | ✅ Form validation |
| `TextField` | Material-UI | All inputs |
| `Checkbox` | Material-UI | Service selection |
| `FormControlLabel` | Material-UI | Checkbox labels |
| File input | Material-UI Button + input | Document uploads |

**Build New:**
- `AgencyRegistrationWizard.tsx` - Main wizard
- `ServiceTypeSelector.tsx` - Service selection UI
- `BusinessDocUpload.tsx` - Document upload

---

### Ticket P1.2-C: Style Selection

#### Style Library (`/marketplace/styles`)

**Base Template:**
```
views/apps/e-commerce/products.tsx (grid view with filters)
```

**Components to Reuse:**

| Component | Location | Usage |
|-----------|----------|-------|
| `ProductCard` | `components/ui-component/cards/ProductCard.tsx` | Style card (adapt) |
| `Grid` | Material-UI | Gallery layout |
| `TextField` | Material-UI | Search |
| `FormControl` + `Select` | Material-UI | Filter dropdowns |
| `Chip` | Material-UI | Filter tags |
| `Dialog` | Material-UI | Style detail modal |
| `useTableLogic` | `hooks/enterprise` | ✅ Filtering |

**StyleCard Adaptation:**
```typescript
// ADAPT: ProductCard → StyleCard

const StyleCard = ({ style }) => {
  return (
    <Card>
      <CardMedia
        component="img"
        height="250"
        image={style.images[0]}
        alt={style.name}
      />
      <CardContent>
        <Typography variant="h5">{style.name}</Typography>
        <Typography variant="body2" color="text.secondary">
          {style.type}
        </Typography>
        
        <Box sx={{ display: 'flex', justifyContent: 'space-between', mt: 1 }}>
          <Typography variant="caption">
            {style.priceRange.currency} {style.priceRange.min.toLocaleString()} - {style.priceRange.max.toLocaleString()}
          </Typography>
          {style.preApproved && (
            <Chip label="Pre-Approved" color="success" size="small" />
          )}
        </Box>
        
        <Box sx={{ mt: 1 }}>
          {style.features.slice(0, 3).map((feature, i) => (
            <Chip key={i} label={feature} size="small" sx={{ mr: 0.5, mb: 0.5 }} />
          ))}
        </Box>
      </CardContent>
      <CardActions>
        <Button size="small" onClick={() => onSelect(style)}>
          Select Style
        </Button>
        <Button size="small" onClick={() => onViewDetails(style)}>
          View Details
        </Button>
      </CardActions>
    </Card>
  );
};
```

---

## 📦 Widget Library Reference

### Card Components

| Component | Path | Best For | EasyLuxury Use Case |
|-----------|------|----------|---------------------|
| `MainCard` | `ui-component/cards/MainCard.tsx` | Primary containers | Page sections, forms |
| `SubCard` | `ui-component/cards/SubCard.tsx` | Nested sections | Details sections |
| `ProductCard` | `ui-component/cards/ProductCard.tsx` | Grid items | Property/Style cards |
| `UserProfileCard` | `ui-component/cards/UserProfileCard.tsx` | User profiles | Agency profiles |
| `ContactCard` | `ui-component/cards/ContactCard.tsx` | Contact display | Team members |
| `AnalyticsChartCard` | `ui-component/cards/AnalyticsChartCard.tsx` | Metrics | Dashboards |
| `GalleryCard` | `ui-component/cards/GalleryCard.tsx` | Image galleries | Property photos |
| `HoverDataCard` | `ui-component/cards/HoverDataCard.tsx` | Stats display | KPI cards |
| `IconNumberCard` | `ui-component/cards/IconNumberCard.tsx` | Number display | Stat widgets |

### Form Components

| Component | Path | Best For | Use In |
|-----------|------|----------|--------|
| `TextField` | Material-UI | Text inputs | All forms |
| `Select` | Material-UI | Dropdowns | Property type, currency |
| `Autocomplete` | Material-UI | Search select | City selection |
| `DatePicker` | Material-UI X | Date inputs | Booking dates |
| `Checkbox` | Material-UI | Multi-select | Service types |
| `Radio` | Material-UI | Single choice | Property purpose |
| `Slider` | Material-UI | Range input | Budget range |
| `Rating` | Material-UI | Star rating | Review forms |

### Layout Components

| Component | Use For | Example |
|-----------|---------|---------|
| `Grid` | Responsive layout | All page layouts |
| `Stack` | Vertical/horizontal spacing | Button groups |
| `Box` | Flex container | Custom layouts |
| `Divider` | Section separation | Between sections |
| `Paper` | Elevated surface | Content blocks |

### Navigation Components

| Component | Use For | Example |
|-----------|---------|---------|
| `Tabs` | Section switching | Property detail tabs |
| `Stepper` | Wizard progress | Multi-step forms |
| `Breadcrumbs` | Page hierarchy | Navigation trail |
| `Pagination` | Page navigation | List pagination |

### Data Display

| Component | Use For | Example |
|-----------|---------|---------|
| `Table` | Tabular data | Booking lists |
| `List` | Vertical lists | Property features |
| `Chip` | Tags/badges | Status, categories |
| `Avatar` | User images | Team members |
| `Badge` | Notifications | Unread counts |
| `Tooltip` | Help text | Field descriptions |

---

## 🎯 Quick Reference Tables

### Form Pattern Consistency

**ALWAYS use for all forms:**
```typescript
const form = useAdvancedForm<T>({
  initialValues: { /* ... */ },
  validationRules: { /* ... */ },
  onSubmit: async (values) => { /* ... */ }
});
```

### Table Pattern Consistency

**ALWAYS use for all lists:**
```typescript
const table = useTableLogic<T>({
  data: items,
  searchFields: ['field1', 'field2'],
  defaultOrderBy: 'field'
});
```

### Error Boundary

**ALWAYS wrap all pages:**
```typescript
export default withErrorBoundary(MyPage);
```

---

## 🔄 Adaptation Workflow

### For Each New Component:

1. **Find Base Template**
   - Look up in page-to-page mapping table
   - Identify closest existing component

2. **Identify Reusable Widgets**
   - Check widget library reference
   - List specific components to reuse

3. **Plan Adaptations**
   - What to keep as-is
   - What to modify
   - What to build new

4. **Follow Patterns**
   - Use `useAdvancedForm` for forms
   - Use `useTableLogic` for lists
   - Wrap with `withErrorBoundary`

5. **Test & Iterate**
   - Mobile responsiveness
   - Validation
   - Error handling

---

## ✅ Summary

### Reuse Strategy:
- **90%** of Material-UI components
- **80%** of card components (with adaptation)
- **100%** of enterprise patterns (useAdvancedForm, useTableLogic, etc.)
- **60-80%** of page layouts
- **40%** of domain-specific logic (need new)

### Key Takeaways:
1. ✅ Leverage existing card/form/layout components
2. ✅ Always use enterprise hooks (useAdvancedForm, useTableLogic)
3. ✅ Adapt product/customer components to property/agency
4. ✅ Build new domain-specific business logic
5. ✅ Maintain consistent patterns throughout

---

**This guide ensures maximum code reuse while building domain-specific functionality for EasyLuxury!** 🚀
