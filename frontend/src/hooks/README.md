# Enterprise Hooks

This directory contains custom hooks following enterprise patterns.

## 🎯 Available Hooks

### **useTableLogic<T>** 🏆 (Generic Table Hook)
The crown jewel - a fully generic, type-safe hook for ANY table component.

**File:** `useTableLogic.ts`

**Features:**
- Fully generic with TypeScript generics
- Sorting (ascending/descending)
- Filtering/Search across multiple fields
- Pagination
- Row selection
- Customizable row identifier
- Works with ANY data type

**Usage:**
```typescript
import { useTableLogic } from '@/hooks/useTableLogic';

const table = useTableLogic<Customer>({
  data: customers,
  searchFields: ['name', 'email', 'location', 'orders'],
  defaultOrderBy: 'name',
  defaultRowsPerPage: 10,
  rowIdentifier: 'id', // or 'name', or any unique field
});

// Access all table state and handlers
const {
  order, orderBy, selected, page, rowsPerPage, search,
  rows, sortedAndPaginatedRows, emptyRows,
  handleSearch, handleRequestSort, handleSelectAllClick,
  handleClick, handleChangePage, handleChangeRowsPerPage, isSelected
} = table;
```

**Examples:**
```typescript
// Customer table
const customerTable = useTableLogic<Customer>({
  data: customers,
  searchFields: ['name', 'email', 'location'],
  defaultOrderBy: 'name',
});

// Order table
const orderTable = useTableLogic<Order>({
  data: orders,
  searchFields: ['id', 'name', 'company', 'type'],
  defaultOrderBy: 'id',
});

// Product table
const productTable = useTableLogic<Product>({
  data: products,
  searchFields: ['name', 'category', 'price'],
  defaultOrderBy: 'name',
  rowIdentifier: 'id',
});
```

---

### **useAdvancedForm<T>** (Form Management Hook)
Enterprise-grade form management with validation.

**File:** `enterprise/useAdvancedForm.ts`

**Features:**
- Type-safe form data
- Declarative validation rules
- Real-time validation feedback
- Form state tracking (isDirty, isValid, isSubmitting)
- Built-in error handling
- Reset functionality

**Usage:**
```typescript
import { useAdvancedForm } from '@/hooks/enterprise';

interface ProfileFormData {
  name: string;
  email: string;
  phone: string;
}

const form = useAdvancedForm<ProfileFormData>({
  initialValues: {
    name: '',
    email: '',
    phone: '',
  },
  validationRules: {
    name: [
      { type: 'required', message: 'Name is required' },
      { type: 'minLength', value: 2, message: 'Min 2 characters' }
    ],
    email: [
      { type: 'required', message: 'Email is required' },
      { type: 'email', message: 'Invalid email format' }
    ],
    phone: [
      {
        type: 'pattern',
        value: /^\d{3}-\d{3}-\d{4}$/,
        message: 'Format: 123-456-7890'
      }
    ]
  },
  validateOnChange: true,
  validateOnBlur: true,
  onSubmit: async (values) => {
    // Submit logic
    await saveProfile(values);
  }
});

// Use in component
<TextField
  value={form.values.name}
  onChange={form.handleChange('name')}
  onBlur={form.handleBlur('name')}
  error={form.touched.name && Boolean(form.errors.name)}
  helperText={form.touched.name && form.errors.name}
/>

<Button
  type="submit"
  disabled={!form.isValid || form.isSubmitting || !form.isDirty}
  onClick={form.handleSubmit()}
>
  {form.isSubmitting ? 'Saving...' : 'Submit'}
</Button>
```

**Validation Types:**
- `required` - Field must have a value
- `email` - Valid email format
- `minLength` - Minimum string length
- `maxLength` - Maximum string length
- `pattern` - Regex pattern matching
- `custom` - Custom validation function

---

### **useAsyncOperation<T>** (Async Data Hook)
Enterprise hook for async operations with retry logic.

**File:** `enterprise/useAsyncOperation.ts`

**Features:**
- Automatic retry with exponential backoff
- Loading and error states
- Success/error callbacks
- Cancel on unmount protection
- Type-safe responses

**Usage:**
```typescript
import { useAsyncOperation } from '@/hooks/enterprise';

const { data, loading, error, execute, retry, reset } = useAsyncOperation(
  fetchCustomers,
  {
    onSuccess: (data) => console.log('Loaded:', data),
    onError: (error) => console.error('Failed:', error),
    retryCount: 3,
    retryDelay: 1000,
  }
);

// Execute operation
useEffect(() => {
  execute();
}, []);

// Render
if (loading) return <Spinner />;
if (error) return <Error error={error} onRetry={retry} />;
return <DataDisplay data={data} />;
```

---

### **useMemoization Hooks** (Performance)
Advanced memoization patterns for performance optimization.

**File:** `enterprise/useMemoization.ts`

**Hooks:**
- `useMemoizedCallback` - Deep dependency comparison
- `useMemoizedSelector` - Memoized selector for complex computations
- `useStableReference` - Stable reference for values

---

## 🎓 **Best Practices**

### **When to Use Each Hook:**

**useTableLogic<T>**
- ✅ Any table component
- ✅ List with sorting, filtering, pagination
- ✅ Row selection needed
- ✅ Search functionality needed

**useAdvancedForm<T>**
- ✅ Any form with validation
- ✅ Profile forms, login forms, settings forms
- ✅ Need real-time validation feedback
- ✅ Complex validation rules

**useAsyncOperation<T>**
- ✅ API calls that need retry logic
- ✅ Data fetching with loading states
- ✅ Operations that can fail temporarily

---

## 📖 **Examples in Codebase**

### **useTableLogic Usage:**
- `views/apps/customer/customer-list.tsx`
- `views/apps/customer/order-list.tsx`

### **useAdvancedForm Usage:**
- `components/users/account-profile/Profile1/ChangePassword.tsx`
- `components/users/account-profile/Profile2/UserProfile.tsx`
- `components/users/account-profile/Profile2/ChangePassword.tsx`
- `components/users/account-profile/Profile3/Profile.tsx`

---

## 🚀 **Quick Start**

### **1. Table Component**
```typescript
import { useTableLogic } from '@/hooks/useTableLogic';

const MyTableComponent = () => {
  const table = useTableLogic<MyDataType>({
    data: myData,
    searchFields: ['field1', 'field2']
  });
  
  return (
    <Table>
      {/* Use table.sortedAndPaginatedRows for rendering */}
    </Table>
  );
};
```

### **2. Form Component**
```typescript
import { useAdvancedForm } from '@/hooks/enterprise';

const MyFormComponent = () => {
  const form = useAdvancedForm({
    initialValues: { name: '', email: '' },
    validationRules: { /* rules */ },
    onSubmit: async (values) => { /* submit */ }
  });
  
  return <form onSubmit={form.handleSubmit()}>...</form>;
};
```

---

## 💡 **Tips**

1. **Always use TypeScript generics** for type safety
2. **Define validation rules** outside component for reusability
3. **Use callbacks** from hooks instead of creating new ones
4. **Leverage form state** (isDirty, isValid, isSubmitting)
5. **Extract complex logic** into custom hooks

---

For more examples, see the implementations in `views/apps/customer/` and `components/users/account-profile/`.
