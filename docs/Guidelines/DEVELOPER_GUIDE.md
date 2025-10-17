# 👨‍💻 Developer Guide - Using Modern Patterns

**Document Purpose:** Practical guide for developers on how to use enterprise patterns and modern features

**Audience:** Developers (new and existing team members)  
**Time to Productivity:** ~30 minutes

---

## 📋 Table of Contents

1. [Quick Start](#quick-start)
2. [Enterprise Hooks](#enterprise-hooks)
3. [Form Development](#form-development)
4. [Table Development](#table-development)
5. [Error Handling](#error-handling)
6. [State Management](#state-management)
7. [Performance Optimization](#performance-optimization)
8. [Best Practices](#best-practices)
9. [Common Patterns](#common-patterns)
10. [Troubleshooting](#troubleshooting)

---

## 🚀 Quick Start

### Setup Your Development Environment

```bash
# 1. Clone and install
git clone <repository-url>
cd project
npm install

# 2. Start development server
npm run dev

# 3. Run tests
npm test

# 4. Type check
npm run type-check
```

### Database Management

#### Clean Database for Development

When you need to start fresh with a clean database:

```bash
# Clean all data from the database
./CLEAN_DATABASE.sh

# After cleanup, restart backend to reload mock users
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

**⚠️ Warning:** This command will delete ALL data from the database. Use only in development!

#### Populate Mock Data

After cleaning the database, populate it with mock users:

```bash
# Feed mock users to database
curl -X POST http://localhost:8080/api/mock/feed/users

# Check status
curl http://localhost:8080/api/mock/feed/status
```

### Your First Component

```typescript
import { withErrorBoundary } from '@/components/enterprise';

const MyComponent: React.FC = () => {
  return (
    <div>
      <h1>Hello World</h1>
    </div>
  );
};

// ✅ ALWAYS wrap page components with error boundary
export default withErrorBoundary(MyComponent);
```

---

## 🪝 Enterprise Hooks

### 1. useAdvancedForm - Form Management

**When to use:**
- ANY form with validation
- User input forms
- Profile editing
- Settings pages

**Basic Example:**
```typescript
import { useAdvancedForm } from '@/hooks/enterprise';

interface LoginFormData {
  email: string;
  password: string;
}

const LoginForm: React.FC = () => {
  const form = useAdvancedForm<LoginFormData>({
    initialValues: {
      email: '',
      password: ''
    },
    validationRules: {
      email: [
        { type: 'required', message: 'Email is required' },
        { type: 'email', message: 'Invalid email' }
      ],
      password: [
        { type: 'required', message: 'Password is required' },
        { type: 'minLength', value: 8, message: 'Min 8 characters' }
      ]
    },
    onSubmit: async (values) => {
      await api.login(values);
    }
  });

  return (
    <form onSubmit={form.handleSubmit()}>
      <TextField
        label="Email"
        value={form.values.email}
        onChange={form.handleChange('email')}
        error={Boolean(form.errors.email)}
        helperText={form.errors.email}
      />
      <TextField
        label="Password"
        type="password"
        value={form.values.password}
        onChange={form.handleChange('password')}
        error={Boolean(form.errors.password)}
        helperText={form.errors.password}
      />
      <Button
        type="submit"
        disabled={!form.isValid || form.isSubmitting}
      >
        {form.isSubmitting ? 'Logging in...' : 'Login'}
      </Button>
    </form>
  );
};
```

**Validation Types:**
```typescript
// Required
{ type: 'required', message: 'This field is required' }

// Email
{ type: 'email', message: 'Invalid email format' }

// Min/Max Length
{ type: 'minLength', value: 8, message: 'Minimum 8 characters' }
{ type: 'maxLength', value: 100, message: 'Maximum 100 characters' }

// Pattern (Regex)
{
  type: 'pattern',
  value: /^(?=.*[A-Z])(?=.*[a-z])(?=.*\d)(?=.*[@$!%*?&])/,
  message: 'Must contain uppercase, lowercase, number, and special character'
}

// Custom Validator
{
  type: 'custom',
  validator: (value, formValues) => value === formValues?.password,
  message: 'Passwords must match'
}
```

**Advanced Example (with all features):**
```typescript
const form = useAdvancedForm<ProfileFormData>({
  initialValues: {
    name: user?.name || '',
    email: user?.email || '',
    password: '',
    confirmPassword: '',
    bio: ''
  },
  validationRules: {
    name: [
      { type: 'required', message: 'Name is required' },
      { type: 'minLength', value: 2, message: 'Min 2 chars' }
    ],
    email: [
      { type: 'required', message: 'Email is required' },
      { type: 'email', message: 'Invalid email' }
    ],
    password: [
      { type: 'minLength', value: 8, message: 'Min 8 chars' },
      {
        type: 'pattern',
        value: /^(?=.*[A-Z])(?=.*[a-z])(?=.*\d)(?=.*[@$!%*?&])/,
        message: 'Must contain uppercase, lowercase, number, special char'
      }
    ],
    confirmPassword: [
      {
        type: 'custom',
        validator: (value, formValues) => !formValues?.password || value === formValues.password,
        message: 'Passwords must match'
      }
    ]
  },
  validateOnChange: true,  // Real-time validation
  validateOnBlur: true,    // Validate on field blur
  onSubmit: async (values) => {
    await api.updateProfile(values);
    notifications.success('Profile updated!');
  }
});

// Access form state
console.log(form.isDirty);      // Has user changed anything?
console.log(form.isValid);      // Are all fields valid?
console.log(form.isSubmitting); // Is form currently submitting?

// Manual operations
form.setFieldValue('email', 'new@email.com');
form.setFieldError('email', 'Email already exists');
form.resetForm(); // Reset to initial values
```

---

### 2. useTableLogic - Table Management

**When to use:**
- ANY data table
- Lists with sorting/filtering
- Paginated data
- Selectable rows

**Basic Example:**
```typescript
import { useTableLogic } from '@/hooks/useTableLogic';

interface Customer {
  id: string;
  name: string;
  email: string;
  location: string;
  orders: number;
}

const CustomerList: React.FC = () => {
  // 1. Fetch data (React Query)
  const { data: customers = [] } = useQuery({
    queryKey: ['customers'],
    queryFn: () => api.getCustomers()
  });

  // 2. Setup table logic
  const table = useTableLogic<Customer>({
    data: customers,
    searchFields: ['name', 'email', 'location'],
    defaultOrderBy: 'name',
    defaultRowsPerPage: 10,
    rowIdentifier: 'id'
  });

  // 3. Render table
  return (
    <Box>
      {/* Search */}
      <TextField
        placeholder="Search customers..."
        value={table.search}
        onChange={table.handleSearch}
      />

      {/* Table */}
      <Table>
        <TableHead>
          <TableRow>
            {/* Select All */}
            <TableCell padding="checkbox">
              <Checkbox
                checked={table.selected.length === customers.length}
                onChange={table.handleSelectAllClick}
              />
            </TableCell>
            
            {/* Sortable Columns */}
            <TableCell>
              <TableSortLabel
                active={table.orderBy === 'name'}
                direction={table.orderBy === 'name' ? table.order : 'asc'}
                onClick={(e) => table.handleRequestSort(e, 'name')}
              >
                Name
              </TableSortLabel>
            </TableCell>
            <TableCell>Email</TableCell>
            <TableCell>Location</TableCell>
            <TableCell>Orders</TableCell>
          </TableRow>
        </TableHead>

        <TableBody>
          {table.sortedAndPaginatedRows.map((customer) => (
            <TableRow
              key={customer.id}
              selected={table.isSelected(customer)}
              onClick={(e) => table.handleClick(e, customer)}
            >
              <TableCell padding="checkbox">
                <Checkbox checked={table.isSelected(customer)} />
              </TableCell>
              <TableCell>{customer.name}</TableCell>
              <TableCell>{customer.email}</TableCell>
              <TableCell>{customer.location}</TableCell>
              <TableCell>{customer.orders}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>

      {/* Pagination */}
      <TablePagination
        component="div"
        count={table.filteredRows.length}
        page={table.page}
        onPageChange={table.handleChangePage}
        rowsPerPage={table.rowsPerPage}
        onRowsPerPageChange={table.handleChangeRowsPerPage}
        rowsPerPageOptions={[5, 10, 25, 50]}
      />
    </Box>
  );
};

export default withErrorBoundary(CustomerList);
```

**What you get:**
```typescript
// Data
table.sortedAndPaginatedRows  // Final rows to display
table.filteredRows            // Filtered but not paginated

// State
table.order                   // 'asc' | 'desc'
table.orderBy                 // Current sort column
table.selected                // Selected rows
table.page                    // Current page
table.rowsPerPage             // Rows per page
table.search                  // Search term

// Handlers
table.handleRequestSort()     // Sort by column
table.handleSelectAllClick()  // Select/deselect all
table.handleClick()           // Select/deselect row
table.handleChangePage()      // Change page
table.handleChangeRowsPerPage() // Change rows per page
table.handleSearch()          // Search/filter

// Utilities
table.isSelected(row)         // Check if row selected
table.emptyRows               // Empty rows for last page
```

---

### 3. useAsyncOperation - Async with Retry

**When to use:**
- API calls that might fail
- Operations needing retry logic
- Loading/error states

**Example:**
```typescript
import { useAsyncOperation } from '@/hooks/enterprise';

const MyComponent: React.FC = () => {
  const { execute, loading, error, data } = useAsyncOperation(
    api.fetchData,
    {
      retryCount: 2,        // Retry 2 times on failure
      retryDelay: 500,      // Wait 500ms between retries
      onSuccess: (data) => {
        notifications.success('Data loaded!');
      },
      onError: (err) => {
        notifications.error('Failed to load data');
      }
    }
  );

  useEffect(() => {
    execute();
  }, []);

  if (loading) return <LoadingState />;
  if (error) return <ErrorState error={error} />;

  return <DataDisplay data={data} />;
};
```

---

## 📝 Form Development

### Pattern: Login Form

```typescript
interface LoginFormData {
  email: string;
  password: string;
  rememberMe: boolean;
}

const LoginForm: React.FC = () => {
  const form = useAdvancedForm<LoginFormData>({
    initialValues: {
      email: '',
      password: '',
      rememberMe: false
    },
    validationRules: {
      email: [
        { type: 'required', message: 'Email is required' },
        { type: 'email', message: 'Invalid email' }
      ],
      password: [
        { type: 'required', message: 'Password is required' },
        { type: 'minLength', value: 8, message: 'Min 8 characters' }
      ]
    },
    onSubmit: async (values) => {
      await authService.login(values);
      router.push('/dashboard');
    }
  });

  return (
    <form onSubmit={form.handleSubmit()}>
      <TextField
        label="Email"
        value={form.values.email}
        onChange={form.handleChange('email')}
        error={Boolean(form.errors.email)}
        helperText={form.errors.email}
        fullWidth
      />
      
      <TextField
        label="Password"
        type="password"
        value={form.values.password}
        onChange={form.handleChange('password')}
        error={Boolean(form.errors.password)}
        helperText={form.errors.password}
        fullWidth
      />
      
      <FormControlLabel
        control={
          <Checkbox
            checked={form.values.rememberMe}
            onChange={(e) => form.setFieldValue('rememberMe', e.target.checked)}
          />
        }
        label="Remember me"
      />
      
      <Button
        type="submit"
        variant="contained"
        fullWidth
        disabled={!form.isValid || form.isSubmitting}
      >
        {form.isSubmitting ? 'Logging in...' : 'Login'}
      </Button>
    </form>
  );
};
```

### Pattern: Profile Update Form

```typescript
interface ProfileFormData {
  name: string;
  email: string;
  bio: string;
  avatar: File | null;
}

const ProfileForm: React.FC = () => {
  const { user } = useAuth();
  
  const form = useAdvancedForm<ProfileFormData>({
    initialValues: {
      name: user?.name || '',
      email: user?.email || '',
      bio: user?.bio || '',
      avatar: null
    },
    validationRules: {
      name: [
        { type: 'required', message: 'Name is required' },
        { type: 'minLength', value: 2, message: 'Min 2 characters' }
      ],
      email: [
        { type: 'required', message: 'Email is required' },
        { type: 'email', message: 'Invalid email' }
      ],
      bio: [
        { type: 'maxLength', value: 500, message: 'Max 500 characters' }
      ]
    },
    onSubmit: async (values) => {
      // Handle file upload if avatar exists
      if (values.avatar) {
        const formData = new FormData();
        formData.append('avatar', values.avatar);
        await api.uploadAvatar(formData);
      }
      
      await api.updateProfile(values);
      notifications.success('Profile updated!');
    }
  });

  const handleFileChange = (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0] || null;
    form.setFieldValue('avatar', file);
  };

  return (
    <form onSubmit={form.handleSubmit()}>
      <TextField
        label="Name"
        value={form.values.name}
        onChange={form.handleChange('name')}
        error={Boolean(form.errors.name)}
        helperText={form.errors.name}
        fullWidth
      />
      
      <TextField
        label="Email"
        value={form.values.email}
        onChange={form.handleChange('email')}
        error={Boolean(form.errors.email)}
        helperText={form.errors.email}
        fullWidth
      />
      
      <TextField
        label="Bio"
        value={form.values.bio}
        onChange={form.handleChange('bio')}
        error={Boolean(form.errors.bio)}
        helperText={form.errors.bio}
        multiline
        rows={4}
        fullWidth
      />
      
      <input
        type="file"
        accept="image/*"
        onChange={handleFileChange}
      />
      
      <Box sx={{ display: 'flex', gap: 2 }}>
        <Button
          type="submit"
          variant="contained"
          disabled={!form.isDirty || !form.isValid || form.isSubmitting}
        >
          {form.isSubmitting ? 'Saving...' : 'Save Changes'}
        </Button>
        
        <Button
          variant="outlined"
          onClick={form.resetForm}
          disabled={!form.isDirty}
        >
          Cancel
        </Button>
      </Box>
    </form>
  );
};
```

---

## 📊 Table Development

### Pattern: Product List with Actions

```typescript
interface Product {
  id: string;
  name: string;
  category: string;
  price: number;
  stock: number;
  status: 'active' | 'inactive';
}

const ProductList: React.FC = () => {
  // Fetch data
  const { data: products = [], refetch } = useQuery({
    queryKey: ['products'],
    queryFn: () => api.getProducts()
  });

  // Table logic
  const table = useTableLogic<Product>({
    data: products,
    searchFields: ['name', 'category'],
    defaultOrderBy: 'name',
    defaultRowsPerPage: 10,
    rowIdentifier: 'id'
  });

  // Delete mutation
  const deleteMutation = useMutation({
    mutationFn: (id: string) => api.deleteProduct(id),
    onSuccess: () => {
      refetch();
      notifications.success('Product deleted');
    }
  });

  const handleDelete = (id: string) => {
    if (confirm('Delete this product?')) {
      deleteMutation.mutate(id);
    }
  };

  return (
    <Box>
      {/* Toolbar */}
      <Box sx={{ display: 'flex', gap: 2, mb: 2 }}>
        <TextField
          placeholder="Search products..."
          value={table.search}
          onChange={table.handleSearch}
          InputProps={{
            startAdornment: <SearchIcon />
          }}
        />
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={() => router.push('/products/new')}
        >
          Add Product
        </Button>
      </Box>

      {/* Selected Actions */}
      {table.selected.length > 0 && (
        <Alert severity="info" sx={{ mb: 2 }}>
          {table.selected.length} selected
          <Button onClick={() => {/* bulk delete */}}>
            Delete Selected
          </Button>
        </Alert>
      )}

      {/* Table */}
      <Table>
        <TableHead>
          <TableRow>
            <TableCell padding="checkbox">
              <Checkbox
                checked={table.selected.length === products.length}
                onChange={table.handleSelectAllClick}
              />
            </TableCell>
            <TableCell>
              <TableSortLabel
                active={table.orderBy === 'name'}
                direction={table.orderBy === 'name' ? table.order : 'asc'}
                onClick={(e) => table.handleRequestSort(e, 'name')}
              >
                Name
              </TableSortLabel>
            </TableCell>
            <TableCell>Category</TableCell>
            <TableCell>
              <TableSortLabel
                active={table.orderBy === 'price'}
                direction={table.orderBy === 'price' ? table.order : 'asc'}
                onClick={(e) => table.handleRequestSort(e, 'price')}
              >
                Price
              </TableSortLabel>
            </TableCell>
            <TableCell>Stock</TableCell>
            <TableCell>Status</TableCell>
            <TableCell>Actions</TableCell>
          </TableRow>
        </TableHead>

        <TableBody>
          {table.sortedAndPaginatedRows.map((product) => (
            <TableRow
              key={product.id}
              selected={table.isSelected(product)}
            >
              <TableCell padding="checkbox">
                <Checkbox
                  checked={table.isSelected(product)}
                  onClick={(e) => table.handleClick(e, product)}
                />
              </TableCell>
              <TableCell>{product.name}</TableCell>
              <TableCell>{product.category}</TableCell>
              <TableCell>${product.price}</TableCell>
              <TableCell>{product.stock}</TableCell>
              <TableCell>
                <Chip
                  label={product.status}
                  color={product.status === 'active' ? 'success' : 'default'}
                />
              </TableCell>
              <TableCell>
                <IconButton onClick={() => router.push(`/products/${product.id}/edit`)}>
                  <EditIcon />
                </IconButton>
                <IconButton onClick={() => handleDelete(product.id)}>
                  <DeleteIcon />
                </IconButton>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>

      {/* Pagination */}
      <TablePagination
        component="div"
        count={table.filteredRows.length}
        page={table.page}
        onPageChange={table.handleChangePage}
        rowsPerPage={table.rowsPerPage}
        onRowsPerPageChange={table.handleChangeRowsPerPage}
        rowsPerPageOptions={[5, 10, 25, 50]}
      />
    </Box>
  );
};

export default withErrorBoundary(ProductList);
```

---

## 🛡️ Error Handling

### Always Wrap Page Components

```typescript
// ❌ DON'T: Page component without error boundary
export default MyPage;

// ✅ DO: Page component with error boundary
export default withErrorBoundary(MyPage);
```

### Custom Error Fallback

```typescript
const CustomErrorFallback: React.FC<ErrorFallbackProps> = ({ error, resetError }) => (
  <Box sx={{ p: 4, textAlign: 'center' }}>
    <Alert severity="error" sx={{ mb: 2 }}>
      <Typography variant="h6">Oops! Something went wrong</Typography>
      <Typography variant="body2" color="text.secondary">
        {error.message}
      </Typography>
    </Alert>
    <Button variant="contained" onClick={resetError}>
      Try Again
    </Button>
    <Button variant="text" onClick={() => router.push('/')}>
      Go Home
    </Button>
  </Box>
);

export default withErrorBoundary(MyComponent, CustomErrorFallback);
```

### Error Handling in Async Operations

```typescript
const MyComponent: React.FC = () => {
  const { execute, loading, error } = useAsyncOperation(
    api.fetchData,
    {
      retryCount: 2,
      retryDelay: 500,
      onError: (err) => {
        // Custom error handling
        if (err.status === 401) {
          router.push('/login');
        } else {
          notifications.error('Failed to load data');
        }
      }
    }
  );

  // ... rest of component
};
```

---

## 🗄️ State Management

### Use Context for UI State

```typescript
// Access auth state
const { user, login, logout, isLoading } = useAuth();

// Access theme
const { theme, toggleTheme, setTheme } = useTheme();

// Access notifications
const { showSuccess, showError, showInfo } = useNotifications();

// Example: Login flow
const handleLogin = async (credentials) => {
  try {
    await login(credentials);
    showSuccess('Welcome back!');
    router.push('/dashboard');
  } catch (error) {
    showError('Login failed');
  }
};
```

### Use React Query for Server Data

```typescript
// Fetch data
const { data, isLoading, error, refetch } = useQuery({
  queryKey: ['users'],
  queryFn: () => api.getUsers(),
  staleTime: 5 * 60 * 1000 // 5 minutes
});

// Mutate data
const mutation = useMutation({
  mutationFn: (userData) => api.createUser(userData),
  onSuccess: () => {
    queryClient.invalidateQueries(['users']);
    notifications.showSuccess('User created!');
  }
});

// Use in component
<Button onClick={() => mutation.mutate(userData)}>
  Create User
</Button>
```

---

## ⚡ Performance Optimization

### Virtual Scrolling for Large Lists

```typescript
import { VirtualList } from '@/components/enterprise/VirtualList';

const LargeProductList: React.FC = () => {
  const { data: products = [] } = useQuery({
    queryKey: ['products'],
    queryFn: () => api.getAllProducts() // 10,000+ items
  });

  return (
    <VirtualList
      items={products}
      itemHeight={80}
      height={600}
      renderItem={(product) => (
        <ProductCard product={product} />
      )}
      onLoadMore={() => {/* load more */}}
    />
  );
};
```

### Memoization

```typescript
import { useMemoizedCallback, useMemoizedSelector } from '@/hooks/enterprise';

const MyComponent: React.FC = () => {
  // Stable callback
  const handleClick = useMemoizedCallback(
    async (id: string) => {
      await api.process(id);
    },
    [api]
  );

  // Efficient selector
  const activeItems = useMemoizedSelector(
    items,
    (items) => items.filter(i => i.active)
  );

  return <div>{/* ... */}</div>;
};
```

### Code Splitting

```typescript
import dynamic from 'next/dynamic';

// Lazy load heavy component
const HeavyChart = dynamic(() => import('./HeavyChart'), {
  loading: () => <Skeleton height={400} />,
  ssr: false
});

// Use in component
<HeavyChart data={chartData} />
```

---

## ✅ Best Practices

### DO ✅

```typescript
// ✅ Always use TypeScript
interface User {
  id: string;
  name: string;
}

// ✅ Always wrap pages with error boundaries
export default withErrorBoundary(MyPage);

// ✅ Use enterprise hooks for forms
const form = useAdvancedForm<FormData>({...});

// ✅ Use enterprise hooks for tables
const table = useTableLogic<DataType>({...});

// ✅ Validate all forms
validationRules: {
  email: [{ type: 'email', message: 'Invalid' }]
}

// ✅ Show loading states
if (isLoading) return <Skeleton />;

// ✅ Handle errors gracefully
if (error) return <ErrorState error={error} />;

// ✅ Use React Query for server data
const { data } = useQuery({...});

// ✅ Use Context for UI state
const { user } = useAuth();
```

### DON'T ❌

```typescript
// ❌ No TypeScript 'any'
const data: any = ...;

// ❌ No unprotected pages
export default MyPage; // Missing error boundary!

// ❌ No unvalidated forms
<form onSubmit={handleSubmit}>
  <input /> {/* No validation! */}
</form>

// ❌ No manual table logic
const [order, setOrder] = useState('asc');
const [orderBy, setOrderBy] = useState('name');
// ... 200 lines of boilerplate

// ❌ No silent failures
onClick={async () => {
  await api.save(); // No error handling!
}}

// ❌ No missing loading states
return <Table data={data} />; // What if loading?

// ❌ No direct state mutation
user.name = 'New Name'; // Use setState or Context!
```

---

## 🎯 Common Patterns

### Pattern: Data Fetching + Display

```typescript
const DataPage: React.FC = () => {
  const { data, isLoading, error } = useQuery({
    queryKey: ['data'],
    queryFn: () => api.getData()
  });

  if (isLoading) return <Skeleton />;
  if (error) return <ErrorState error={error} />;
  if (!data) return <EmptyState />;

  return <DataDisplay data={data} />;
};

export default withErrorBoundary(DataPage);
```

### Pattern: Form + API

```typescript
const CreateItemForm: React.FC = () => {
  const router = useRouter();
  
  const mutation = useMutation({
    mutationFn: (data) => api.createItem(data),
    onSuccess: () => {
      notifications.showSuccess('Item created!');
      router.push('/items');
    }
  });

  const form = useAdvancedForm<ItemData>({
    initialValues: { name: '', description: '' },
    validationRules: {
      name: [{ type: 'required', message: 'Name required' }]
    },
    onSubmit: async (values) => {
      await mutation.mutateAsync(values);
    }
  });

  return <form onSubmit={form.handleSubmit()}>{/* ... */}</form>;
};
```

### Pattern: Table + CRUD

```typescript
const ItemsPage: React.FC = () => {
  const { data: items = [], refetch } = useQuery({
    queryKey: ['items'],
    queryFn: () => api.getItems()
  });

  const table = useTableLogic<Item>({
    data: items,
    searchFields: ['name', 'description']
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => api.deleteItem(id),
    onSuccess: () => {
      refetch();
      notifications.showSuccess('Item deleted');
    }
  });

  return (
    <Box>
      <Table>
        {/* Table implementation with table.* handlers */}
      </Table>
    </Box>
  );
};

export default withErrorBoundary(ItemsPage);
```

---

## 🔧 Troubleshooting

### Issue: Form not validating

**Problem:**
```typescript
const form = useAdvancedForm({
  initialValues: { email: '' },
  // Missing validationRules!
  onSubmit: async (values) => { ... }
});
```

**Solution:**
```typescript
const form = useAdvancedForm({
  initialValues: { email: '' },
  validationRules: {
    email: [{ type: 'email', message: 'Invalid email' }]
  },
  onSubmit: async (values) => { ... }
});
```

### Issue: Table not sorting

**Problem:**
```typescript
<TableCell>Name</TableCell> // Missing TableSortLabel!
```

**Solution:**
```typescript
<TableCell>
  <TableSortLabel
    active={table.orderBy === 'name'}
    direction={table.orderBy === 'name' ? table.order : 'asc'}
    onClick={(e) => table.handleRequestSort(e, 'name')}
  >
    Name
  </TableSortLabel>
</TableCell>
```

### Issue: Context not available

**Problem:**
```typescript
const MyComponent = () => {
  const { user } = useAuth(); // Error: useAuth must be used within AuthProvider
};
```

**Solution:**
Ensure component is wrapped with providers:
```typescript
// In _app.tsx or layout
<ProviderWrapper>
  <MyComponent />
</ProviderWrapper>
```

### Issue: TypeScript error on generic hook

**Problem:**
```typescript
const table = useTableLogic({ data: customers }); // Type error!
```

**Solution:**
```typescript
const table = useTableLogic<Customer>({ data: customers });
```

---

## 📚 Additional Resources

### Code Examples
- **Forms:** `frontend/src/components/users/account-profile/Profile3/Profile.tsx`
- **Tables:** `frontend/src/views/apps/customer/customer-list.tsx`
- **Hooks:** `frontend/src/hooks/enterprise/`
- **Tests:** `frontend/src/hooks/enterprise/__tests__/`

### Documentation
- **[PROJECT_OVERVIEW.md](PROJECT_OVERVIEW.md)** - Project overview
- **[TECHNICAL_ARCHITECTURE.md](TECHNICAL_ARCHITECTURE.md)** - Architecture details
- **[MIGRATION_GUIDE.md](MIGRATION_GUIDE.md)** - Migration history
- **[docs/FRONTEND_DEVELOPMENT_GUIDE.md](docs/FRONTEND_DEVELOPMENT_GUIDE.md)** - Comprehensive guide

### External Links
- [React Documentation](https://react.dev)
- [Next.js Documentation](https://nextjs.org/docs)
- [Material-UI Documentation](https://mui.com/)
- [React Query Documentation](https://tanstack.com/query/latest)
- [TypeScript Handbook](https://www.typescriptlang.org/docs/)

---

## 🎓 Quick Reference Card

```typescript
// 1. Import enterprise hooks
import { useAdvancedForm, useTableLogic } from '@/hooks/enterprise';
import { withErrorBoundary } from '@/components/enterprise';

// 2. Create form
const form = useAdvancedForm<FormData>({
  initialValues: { ... },
  validationRules: { ... },
  onSubmit: async (values) => { ... }
});

// 3. Create table
const table = useTableLogic<DataType>({
  data: items,
  searchFields: ['name', 'email']
});

// 4. Fetch data
const { data, isLoading } = useQuery({
  queryKey: ['key'],
  queryFn: () => api.fetch()
});

// 5. Mutate data
const mutation = useMutation({
  mutationFn: (data) => api.create(data),
  onSuccess: () => refetch()
});

// 6. Wrap with error boundary
export default withErrorBoundary(MyComponent);
```

---

**Last Updated:** October 2025  
**Status:** ✅ Production Ready  
**Time to Productivity:** ~30 minutes
