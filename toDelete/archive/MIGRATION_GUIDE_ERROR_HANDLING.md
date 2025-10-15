# 🔄 Migration Guide: Adding Error Handling to Existing Code

## Step-by-Step Guide to Modernize Your Components

This guide shows you how to add Phase 4 error handling patterns to your existing components with minimal changes.

---

## 📋 Table of Contents

1. [Simple Component Migration](#1-simple-component-migration)
2. [Components with API Calls](#2-components-with-api-calls)
3. [Components with Context](#3-components-with-context)
4. [Complex Components](#4-complex-components)
5. [Migration Checklist](#5-migration-checklist)

---

## 1. Simple Component Migration

### Before (Unprotected)

```typescript
// src/views/my-page.tsx
'use client';

import { Box, Typography } from '@mui/material';

const MyPage = () => {
  return (
    <Box>
      <Typography variant="h4">My Page</Typography>
      <Typography>Content goes here</Typography>
    </Box>
  );
};

export default MyPage;
```

### After (Protected)

```typescript
// src/views/my-page.tsx
'use client';

import { Box, Typography } from '@mui/material';
import { withErrorBoundary } from '@/components/enterprise';  // Add this import

const MyPage = () => {
  return (
    <Box>
      <Typography variant="h4">My Page</Typography>
      <Typography>Content goes here</Typography>
    </Box>
  );
};

export default withErrorBoundary(MyPage);  // Wrap the export
```

**Changes:**
- ✅ Added 1 import
- ✅ Modified 1 line (export)
- ✅ Zero changes to component logic

---

## 2. Components with API Calls

### Before (Manual Error Handling)

```typescript
// src/views/user-list.tsx
'use client';

import React from 'react';
import { CircularProgress, Alert } from '@mui/material';

const UserList = () => {
  const [users, setUsers] = React.useState([]);
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState(null);

  React.useEffect(() => {
    const fetchUsers = async () => {
      setLoading(true);
      setError(null);
      
      try {
        const response = await fetch('/api/users');
        const data = await response.json();
        setUsers(data);
      } catch (err) {
        setError(err.message);
      } finally {
        setLoading(false);
      }
    };

    fetchUsers();
  }, []);

  if (loading) return <CircularProgress />;
  if (error) return <Alert severity="error">{error}</Alert>;

  return (
    <div>
      {users.map(user => (
        <div key={user.id}>{user.name}</div>
      ))}
    </div>
  );
};

export default UserList;
```

### After (With Retry Logic)

```typescript
// src/views/user-list.tsx
'use client';

import React from 'react';
import { CircularProgress, Alert, Button } from '@mui/material';
import { withErrorBoundary } from '@/components/enterprise';
import { useAsyncOperation } from '@/hooks/enterprise';
import { useNotifications } from '@/contexts/NotificationContext';

const UserList = () => {
  const [users, setUsers] = React.useState([]);
  const { showNotification } = useNotifications();

  // Replace manual state management with useAsyncOperation
  const { loading, error, execute, retry } = useAsyncOperation(
    async () => {
      const response = await fetch('/api/users');
      if (!response.ok) throw new Error('Failed to fetch users');
      return response.json();
    },
    {
      retryCount: 2,           // Auto-retry 2 times
      retryDelay: 500,         // Wait 500ms between retries
      onSuccess: (data) => {
        setUsers(data);
      },
      onError: (err) => {
        showNotification({
          message: 'Failed to load users',
          variant: 'error',
          alert: { color: 'error', variant: 'filled' }
        });
      }
    }
  );

  React.useEffect(() => {
    execute();
  }, []);

  if (loading) return <CircularProgress />;
  
  if (error) {
    return (
      <Alert 
        severity="error"
        action={
          <Button color="inherit" size="small" onClick={retry}>
            Retry
          </Button>
        }
      >
        Failed to load users
      </Alert>
    );
  }

  return (
    <div>
      {users.map(user => (
        <div key={user.id}>{user.name}</div>
      ))}
    </div>
  );
};

export default withErrorBoundary(UserList);
```

**Benefits:**
- ✅ Automatic retry on failure (2 attempts with 500ms delay)
- ✅ User notifications
- ✅ Retry button for manual recovery
- ✅ Error boundary protection
- ✅ Cleaner code (no manual try-catch)

---

## 3. Components with Context

### Before (Basic Context Usage)

```typescript
// src/views/customer-list.tsx
'use client';

import React from 'react';
import { useCustomer } from '@/contexts/CustomerContext';
import { CircularProgress } from '@mui/material';

const CustomerList = () => {
  const { state, getCustomers } = useCustomer();
  const [loading, setLoading] = React.useState(true);

  React.useEffect(() => {
    const loadData = async () => {
      try {
        await getCustomers();
      } catch (error) {
        console.error('Failed to load customers:', error);
      } finally {
        setLoading(false);
      }
    };
    
    loadData();
  }, []);

  if (loading) return <CircularProgress />;

  return (
    <div>
      {state.customers.map(customer => (
        <div key={customer.id}>{customer.name}</div>
      ))}
    </div>
  );
};

export default CustomerList;
```

### After (With Error Handling & Retry)

```typescript
// src/views/customer-list.tsx
'use client';

import React from 'react';
import { useCustomer } from '@/contexts/CustomerContext';
import { useNotifications } from '@/contexts/NotificationContext';
import { CircularProgress, Alert, Button } from '@mui/material';
import { withErrorBoundary } from '@/components/enterprise';
import { useAsyncOperation } from '@/hooks/enterprise';

const CustomerList = () => {
  const { state, getCustomers } = useCustomer();
  const { showNotification } = useNotifications();

  const { loading, error, execute, retry } = useAsyncOperation(
    async () => {
      await getCustomers();
      return true;
    },
    {
      retryCount: 2,
      retryDelay: 500,
      onError: () => {
        showNotification({
          message: 'Failed to load customers',
          variant: 'error',
          alert: { color: 'error', variant: 'filled' }
        });
      }
    }
  );

  React.useEffect(() => {
    execute();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (loading) return <CircularProgress />;
  
  if (error) {
    return (
      <Alert 
        severity="error"
        action={
          <Button color="inherit" size="small" onClick={retry}>
            Retry
          </Button>
        }
      >
        Failed to load customers
      </Alert>
    );
  }

  return (
    <div>
      {state.customers.map(customer => (
        <div key={customer.id}>{customer.name}</div>
      ))}
    </div>
  );
};

export default withErrorBoundary(CustomerList);
```

**Benefits:**
- ✅ Context methods now have retry logic
- ✅ User feedback via notifications
- ✅ Manual retry option
- ✅ Component protected by error boundary

---

## 4. Complex Components

### Before (Form with Submit)

```typescript
// src/views/create-invoice.tsx
'use client';

import React from 'react';
import { Button, TextField, CircularProgress } from '@mui/material';
import { useFormik } from 'formik';

const CreateInvoice = () => {
  const [submitting, setSubmitting] = React.useState(false);
  const [error, setError] = React.useState(null);

  const formik = useFormik({
    initialValues: {
      customerName: '',
      amount: ''
    },
    onSubmit: async (values) => {
      setSubmitting(true);
      setError(null);
      
      try {
        const response = await fetch('/api/invoices', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(values)
        });
        
        if (!response.ok) throw new Error('Failed to create invoice');
        
        alert('Invoice created successfully!');
        formik.resetForm();
      } catch (err) {
        setError(err.message);
      } finally {
        setSubmitting(false);
      }
    }
  });

  return (
    <form onSubmit={formik.handleSubmit}>
      {error && <div style={{ color: 'red' }}>{error}</div>}
      
      <TextField
        name="customerName"
        label="Customer Name"
        value={formik.values.customerName}
        onChange={formik.handleChange}
        fullWidth
        margin="normal"
      />
      
      <TextField
        name="amount"
        label="Amount"
        value={formik.values.amount}
        onChange={formik.handleChange}
        fullWidth
        margin="normal"
      />
      
      <Button 
        type="submit" 
        variant="contained"
        disabled={submitting}
      >
        {submitting ? <CircularProgress size={24} /> : 'Create Invoice'}
      </Button>
    </form>
  );
};

export default CreateInvoice;
```

### After (With Error Handling)

```typescript
// src/views/create-invoice.tsx
'use client';

import React from 'react';
import { Button, TextField, CircularProgress, Alert } from '@mui/material';
import { useFormik } from 'formik';
import { withErrorBoundary } from '@/components/enterprise';
import { useAsyncOperation } from '@/hooks/enterprise';
import { useNotifications } from '@/contexts/NotificationContext';

const CreateInvoice = () => {
  const { showNotification } = useNotifications();

  // Use async operation for form submission
  const { loading: submitting, error, execute: submitInvoice } = useAsyncOperation(
    async (values: any) => {
      const response = await fetch('/api/invoices', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(values)
      });
      
      if (!response.ok) throw new Error('Failed to create invoice');
      
      return response.json();
    },
    {
      retryCount: 1,  // Retry once for form submissions
      retryDelay: 1000,
      onSuccess: () => {
        showNotification({
          message: 'Invoice created successfully!',
          variant: 'success',
          alert: { color: 'success', variant: 'filled' }
        });
        formik.resetForm();
      },
      onError: (err) => {
        showNotification({
          message: 'Failed to create invoice',
          variant: 'error',
          alert: { color: 'error', variant: 'filled' }
        });
      }
    }
  );

  const formik = useFormik({
    initialValues: {
      customerName: '',
      amount: ''
    },
    onSubmit: async (values) => {
      await submitInvoice(values);
    }
  });

  return (
    <form onSubmit={formik.handleSubmit}>
      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          Failed to create invoice. Please try again.
        </Alert>
      )}
      
      <TextField
        name="customerName"
        label="Customer Name"
        value={formik.values.customerName}
        onChange={formik.handleChange}
        fullWidth
        margin="normal"
        disabled={submitting}
      />
      
      <TextField
        name="amount"
        label="Amount"
        value={formik.values.amount}
        onChange={formik.handleChange}
        fullWidth
        margin="normal"
        disabled={submitting}
      />
      
      <Button 
        type="submit" 
        variant="contained"
        disabled={submitting}
        sx={{ mt: 2 }}
      >
        {submitting ? (
          <>
            <CircularProgress size={20} sx={{ mr: 1 }} />
            Creating...
          </>
        ) : (
          'Create Invoice'
        )}
      </Button>
    </form>
  );
};

export default withErrorBoundary(CreateInvoice);
```

**Benefits:**
- ✅ Automatic retry for transient failures
- ✅ User-friendly notifications
- ✅ Better error display
- ✅ Disabled form during submission
- ✅ Component protected by error boundary

---

## 5. Migration Checklist

### For Every Component

- [ ] **Step 1:** Add import
  ```typescript
  import { withErrorBoundary } from '@/components/enterprise';
  ```

- [ ] **Step 2:** Wrap export
  ```typescript
  export default withErrorBoundary(MyComponent);
  ```

### For Components with API Calls

- [ ] **Step 3:** Add async hook import
  ```typescript
  import { useAsyncOperation } from '@/hooks/enterprise';
  ```

- [ ] **Step 4:** Replace manual state management
  ```typescript
  const { loading, error, execute } = useAsyncOperation(
    async () => { /* your API call */ },
    { retryCount: 2, retryDelay: 500 }
  );
  ```

- [ ] **Step 5:** Add notification context
  ```typescript
  import { useNotifications } from '@/contexts/NotificationContext';
  const { showNotification } = useNotifications();
  ```

- [ ] **Step 6:** Add success/error callbacks
  ```typescript
  const { execute } = useAsyncOperation(apiCall, {
    onSuccess: () => showNotification({ /* success */ }),
    onError: () => showNotification({ /* error */ })
  });
  ```

- [ ] **Step 7:** Update loading/error UI
  ```typescript
  if (loading) return <CircularProgress />;
  if (error) return <Alert severity="error">...</Alert>;
  ```

---

## 🎯 Quick Migration Examples

### Quick Migration #1: Add Error Boundary Only

```typescript
// Before
export default MyComponent;

// After (add 2 lines)
import { withErrorBoundary } from '@/components/enterprise';
export default withErrorBoundary(MyComponent);
```

### Quick Migration #2: Add Retry to Existing Fetch

```typescript
// Before
React.useEffect(() => {
  fetch('/api/data')
    .then(r => r.json())
    .then(setData);
}, []);

// After
import { useAsyncOperation } from '@/hooks/enterprise';

const { execute } = useAsyncOperation(
  async () => {
    const r = await fetch('/api/data');
    return r.json();
  },
  {
    retryCount: 2,
    retryDelay: 500,
    onSuccess: setData
  }
);

React.useEffect(() => {
  execute();
}, []);
```

### Quick Migration #3: Add Notifications

```typescript
// Before
try {
  await apiCall();
  console.log('Success');
} catch (err) {
  console.error('Error:', err);
}

// After
import { useAsyncOperation } from '@/hooks/enterprise';
import { useNotifications } from '@/contexts/NotificationContext';

const { showNotification } = useNotifications();
const { execute } = useAsyncOperation(apiCall, {
  onSuccess: () => showNotification({ 
    message: 'Success!', 
    variant: 'success' 
  }),
  onError: () => showNotification({ 
    message: 'Error occurred', 
    variant: 'error' 
  })
});
```

---

## 📊 Migration Priority

### High Priority (Migrate First)
1. ✅ Page components in `src/views/`
2. ✅ Components that fetch data
3. ✅ Forms with submissions
4. ✅ Complex feature components

### Medium Priority
1. ✅ Dashboard widgets
2. ✅ List components
3. ✅ Components with user interactions

### Low Priority
1. ⚠️ Simple presentational components
2. ⚠️ Small utility components
3. ⚠️ Pure UI components without logic

---

## 🔧 Common Migration Patterns

### Pattern: useEffect with Fetch

```typescript
// Before
React.useEffect(() => {
  fetch('/api/data')
    .then(r => r.json())
    .then(setData)
    .catch(console.error);
}, []);

// After
const { execute } = useAsyncOperation(
  async () => (await fetch('/api/data')).json(),
  { 
    retryCount: 2,
    onSuccess: setData,
    onError: (err) => console.error(err)
  }
);

React.useEffect(() => { execute(); }, []);
```

### Pattern: Async/Await in useEffect

```typescript
// Before
React.useEffect(() => {
  const loadData = async () => {
    try {
      const data = await fetchData();
      setData(data);
    } catch (err) {
      setError(err);
    }
  };
  loadData();
}, []);

// After
const { execute } = useAsyncOperation(
  fetchData,
  { 
    retryCount: 2,
    onSuccess: setData 
  }
);

React.useEffect(() => { execute(); }, []);
```

### Pattern: Button Click Handler

```typescript
// Before
const handleClick = async () => {
  setLoading(true);
  try {
    await apiCall();
    alert('Success!');
  } catch (err) {
    alert('Error: ' + err.message);
  } finally {
    setLoading(false);
  }
};

// After
const { loading, execute: handleClick } = useAsyncOperation(
  apiCall,
  {
    onSuccess: () => alert('Success!'),
    onError: (err) => alert('Error: ' + err.message)
  }
);
```

---

## ✅ Verification

After migration, verify:

1. **Component renders correctly** ✓
2. **Error boundary catches errors** ✓
3. **Retry logic works on failure** ✓
4. **Loading states display properly** ✓
5. **Notifications appear** ✓
6. **No console errors** ✓
7. **TypeScript compiles** ✓

---

## 🆘 Troubleshooting

### Issue: "Can't find module '@/components/enterprise'"

**Solution:** Check your import path. It might be:
```typescript
import { withErrorBoundary } from '@/components/enterprise';
// or
import { withErrorBoundary } from 'components/enterprise';
// or
import { withErrorBoundary } from '../../components/enterprise';
```

### Issue: "Infinite retry loop"

**Solution:** Your operation is throwing an error that should not be retried. Either:
- Set `retryCount: 0`
- Fix the underlying error
- Add better error detection

### Issue: "Component not re-rendering after error"

**Solution:** Make sure you're:
- Calling the `retry` function from useAsyncOperation
- Not preventing re-renders with unnecessary memoization

---

## 🎓 Summary

Migration is simple:
1. Add error boundary to every component (1-2 lines)
2. Replace manual error handling with `useAsyncOperation`
3. Add user notifications
4. Test thoroughly

The result: **More reliable, user-friendly components with better error handling!** 🚀
