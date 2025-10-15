# 🛡️ How to Use Error Handling Throughout the Codebase

## Quick Reference Guide for Phase 4 Patterns

This guide shows you how to apply enterprise error handling and reliability patterns to any component in your codebase.

---

## 📚 Table of Contents

1. [Error Boundaries - Protecting Components](#1-error-boundaries---protecting-components)
2. [Async Operations with Retry](#2-async-operations-with-retry)
3. [Custom Error Fallbacks](#3-custom-error-fallbacks)
4. [Loading States](#4-loading-states)
5. [Common Patterns](#5-common-patterns)
6. [Best Practices](#6-best-practices)
7. [Real-World Examples](#7-real-world-examples)

---

## 1. Error Boundaries - Protecting Components

### Basic Usage

Wrap any component with `withErrorBoundary` to protect it from crashes:

```typescript
// Before - Unprotected component
const MyComponent = () => {
  return <div>My Content</div>;
};

export default MyComponent;
```

```typescript
// After - Protected component
import { withErrorBoundary } from '@/components/enterprise';

const MyComponent = () => {
  return <div>My Content</div>;
};

export default withErrorBoundary(MyComponent);
```

### What This Does

✅ Catches React rendering errors  
✅ Prevents white screens  
✅ Shows user-friendly error message  
✅ Provides "Try Again" button  
✅ Preserves component name for debugging

### When to Use

Apply to:
- ✅ **All page components** (views/pages)
- ✅ **Major feature components** (dashboards, forms)
- ✅ **Components that fetch data**
- ✅ **Complex UI sections**
- ❌ Small, simple presentational components (optional)

---

## 2. Async Operations with Retry

### Basic Usage

Use `useAsyncOperation` for any API calls or async operations:

```typescript
import { useAsyncOperation } from '@/hooks/enterprise';
import { useNotifications } from '@/contexts/NotificationContext';

const MyComponent = () => {
  const { showNotification } = useNotifications();
  
  // Define async operation with retry
  const { data, loading, error, execute, retry } = useAsyncOperation(
    async () => {
      const response = await fetch('/api/data');
      return response.json();
    },
    {
      retryCount: 3,        // Retry 3 times on failure
      retryDelay: 1000,     // Wait 1 second between retries
      onSuccess: (data) => {
        console.log('Data loaded:', data);
      },
      onError: (error) => {
        showNotification({
          message: 'Failed to load data',
          variant: 'error',
          alert: { color: 'error', variant: 'filled' }
        });
      }
    }
  );

  // Load data on mount
  React.useEffect(() => {
    execute();
  }, []);

  if (loading) return <CircularProgress />;
  if (error) return <Button onClick={retry}>Retry</Button>;
  
  return <div>{JSON.stringify(data)}</div>;
};

export default withErrorBoundary(MyComponent);
```

### Configuration Options

```typescript
interface AsyncOptions<T> {
  retryCount?: number;      // Number of retries (default: 0)
  retryDelay?: number;      // Delay between retries in ms (default: 0)
  onSuccess?: (data: T) => void;   // Success callback
  onError?: (error: unknown) => void;  // Error callback
}
```

### Common Configurations

```typescript
// Quick retry for transient failures
const { execute } = useAsyncOperation(apiCall, {
  retryCount: 2,
  retryDelay: 500
});

// Aggressive retry for critical operations
const { execute } = useAsyncOperation(apiCall, {
  retryCount: 5,
  retryDelay: 2000
});

// No retry, just error handling
const { execute } = useAsyncOperation(apiCall, {
  onError: handleError
});
```

---

## 3. Custom Error Fallbacks

### Using Custom Error UI

You can provide your own error fallback component:

```typescript
import { withErrorBoundary, ErrorFallbackProps } from '@/components/enterprise';

// Custom error fallback
const MyCustomErrorFallback = ({ error, resetError }: ErrorFallbackProps) => {
  return (
    <Box sx={{ p: 4, textAlign: 'center' }}>
      <Typography variant="h4" color="error">
        Oops! Something went wrong
      </Typography>
      <Typography variant="body1" sx={{ mt: 2 }}>
        {error.message}
      </Typography>
      <Button 
        variant="contained" 
        onClick={resetError}
        sx={{ mt: 3 }}
      >
        Try Again
      </Button>
    </Box>
  );
};

// Use custom fallback
const MyComponent = () => {
  return <div>Content</div>;
};

export default withErrorBoundary(MyComponent, MyCustomErrorFallback);
```

### When to Use Custom Fallbacks

- Need specific error messaging for a feature
- Want to match specific design requirements
- Need additional error recovery options
- Want to log errors differently

---

## 4. Loading States

### Using withLoading HOC

```typescript
import { withLoading } from '@/components/enterprise';

const MyComponent = ({ data }: { data: any }) => {
  return <div>{data}</div>;
};

// Wrap with loading HOC
const MyComponentWithLoading = withLoading(MyComponent);

// Use it
<MyComponentWithLoading loading={isLoading} data={data} />
```

### Combining with Error Boundaries

```typescript
import { withErrorBoundary, withLoading } from '@/components/enterprise';

const MyComponent = ({ data }: { data: any }) => {
  return <div>{data}</div>;
};

// Compose HOCs - order matters!
const Enhanced = withErrorBoundary(withLoading(MyComponent));

export default Enhanced;
```

---

## 5. Common Patterns

### Pattern 1: Data Fetching Component

```typescript
import { withErrorBoundary } from '@/components/enterprise';
import { useAsyncOperation } from '@/hooks/enterprise';

const DataFetchingComponent = () => {
  const { data, loading, error, execute } = useAsyncOperation(
    async () => {
      const response = await fetch('/api/users');
      return response.json();
    },
    {
      retryCount: 2,
      retryDelay: 500,
      onError: (err) => console.error('Failed to fetch:', err)
    }
  );

  React.useEffect(() => {
    execute();
  }, []);

  if (loading) return <Skeleton variant="rectangular" height={200} />;
  if (error) return <Alert severity="error">Failed to load data</Alert>;
  
  return (
    <List>
      {data?.map((item: any) => (
        <ListItem key={item.id}>{item.name}</ListItem>
      ))}
    </List>
  );
};

export default withErrorBoundary(DataFetchingComponent);
```

### Pattern 2: Form with Submit Handler

```typescript
import { withErrorBoundary } from '@/components/enterprise';
import { useAsyncOperation } from '@/hooks/enterprise';
import { useNotifications } from '@/contexts/NotificationContext';

const FormComponent = () => {
  const { showNotification } = useNotifications();
  
  const { loading, execute: submitForm } = useAsyncOperation(
    async (formData: any) => {
      const response = await fetch('/api/submit', {
        method: 'POST',
        body: JSON.stringify(formData)
      });
      return response.json();
    },
    {
      retryCount: 1,
      onSuccess: () => {
        showNotification({
          message: 'Form submitted successfully!',
          variant: 'success'
        });
      },
      onError: () => {
        showNotification({
          message: 'Failed to submit form',
          variant: 'error'
        });
      }
    }
  );

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    submitForm({ /* form data */ });
  };

  return (
    <form onSubmit={handleSubmit}>
      {/* form fields */}
      <Button type="submit" disabled={loading}>
        {loading ? 'Submitting...' : 'Submit'}
      </Button>
    </form>
  );
};

export default withErrorBoundary(FormComponent);
```

### Pattern 3: Context + Async Operations

```typescript
import { withErrorBoundary } from '@/components/enterprise';
import { useAsyncOperation } from '@/hooks/enterprise';
import { useMyContext } from '@/contexts/MyContext';

const ContextAwareComponent = () => {
  const context = useMyContext();
  
  const { execute: loadData } = useAsyncOperation(
    async () => {
      await context.fetchData();
      return true;
    },
    {
      retryCount: 2,
      retryDelay: 500
    }
  );

  React.useEffect(() => {
    loadData();
  }, []);

  return <div>{/* render using context.state */}</div>;
};

export default withErrorBoundary(ContextAwareComponent);
```

### Pattern 4: Multiple Async Operations

```typescript
import { withErrorBoundary } from '@/components/enterprise';
import { useAsyncOperation } from '@/hooks/enterprise';

const MultiOperationComponent = () => {
  const { execute: loadUsers, loading: loadingUsers } = useAsyncOperation(
    async () => fetch('/api/users').then(r => r.json()),
    { retryCount: 2 }
  );

  const { execute: loadPosts, loading: loadingPosts } = useAsyncOperation(
    async () => fetch('/api/posts').then(r => r.json()),
    { retryCount: 2 }
  );

  const { execute: deleteItem, loading: deleting } = useAsyncOperation(
    async (id: string) => {
      await fetch(`/api/items/${id}`, { method: 'DELETE' });
    },
    { onSuccess: () => console.log('Deleted!') }
  );

  React.useEffect(() => {
    Promise.all([loadUsers(), loadPosts()]);
  }, []);

  const loading = loadingUsers || loadingPosts;

  return (
    <div>
      {loading && <CircularProgress />}
      <Button onClick={() => deleteItem('123')} disabled={deleting}>
        Delete
      </Button>
    </div>
  );
};

export default withErrorBoundary(MultiOperationComponent);
```

---

## 6. Best Practices

### ✅ DO

1. **Always wrap page components** with error boundaries
   ```typescript
   export default withErrorBoundary(MyPage);
   ```

2. **Use retry for network operations**
   ```typescript
   const { execute } = useAsyncOperation(apiCall, { 
     retryCount: 2,
     retryDelay: 500 
   });
   ```

3. **Provide user feedback**
   ```typescript
   const { execute } = useAsyncOperation(apiCall, {
     onSuccess: () => showSuccessMessage(),
     onError: () => showErrorMessage()
   });
   ```

4. **Handle loading states**
   ```typescript
   if (loading) return <Loader />;
   ```

5. **Keep retry delays reasonable**
   - Transient failures: 500ms - 1s
   - Network issues: 1s - 3s
   - Heavy operations: 3s - 5s

### ❌ DON'T

1. **Don't retry non-idempotent operations** without careful consideration
   ```typescript
   // Bad - might create duplicate payments
   const { execute } = useAsyncOperation(processPayment, { 
     retryCount: 3 
   });
   ```

2. **Don't use excessive retries**
   ```typescript
   // Bad - will hammer the server
   const { execute } = useAsyncOperation(apiCall, { 
     retryCount: 100,
     retryDelay: 100 
   });
   ```

3. **Don't ignore errors silently**
   ```typescript
   // Bad - user doesn't know what happened
   const { execute } = useAsyncOperation(apiCall, {
     onError: () => {} // Silent failure
   });
   ```

4. **Don't wrap every tiny component**
   ```typescript
   // Unnecessary for simple presentational components
   const Button = () => <button>Click</button>;
   export default withErrorBoundary(Button); // Overkill
   ```

---

## 7. Real-World Examples

### Example 1: Dashboard Component

```typescript
import { withErrorBoundary } from '@/components/enterprise';
import { useAsyncOperation } from '@/hooks/enterprise';

const Dashboard = () => {
  const [dashboardData, setDashboardData] = React.useState(null);

  const { loading, error, execute, retry } = useAsyncOperation(
    async () => {
      const response = await fetch('/api/dashboard');
      if (!response.ok) throw new Error('Failed to load dashboard');
      return response.json();
    },
    {
      retryCount: 3,
      retryDelay: 2000,
      onSuccess: (data) => setDashboardData(data),
      onError: (err) => console.error('Dashboard error:', err)
    }
  );

  React.useEffect(() => {
    execute();
  }, []);

  if (loading) {
    return (
      <Grid container spacing={2}>
        <Grid item xs={12} md={6}>
          <Skeleton variant="rectangular" height={200} />
        </Grid>
        <Grid item xs={12} md={6}>
          <Skeleton variant="rectangular" height={200} />
        </Grid>
      </Grid>
    );
  }

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
        Failed to load dashboard data
      </Alert>
    );
  }

  return (
    <Grid container spacing={2}>
      {/* Render dashboard widgets */}
    </Grid>
  );
};

export default withErrorBoundary(Dashboard);
```

### Example 2: User Profile with Multiple Endpoints

```typescript
import { withErrorBoundary } from '@/components/enterprise';
import { useAsyncOperation } from '@/hooks/enterprise';
import { useNotifications } from '@/contexts/NotificationContext';

const UserProfile = ({ userId }: { userId: string }) => {
  const { showNotification } = useNotifications();
  const [profile, setProfile] = React.useState(null);
  const [posts, setPosts] = React.useState([]);

  // Load profile
  const { loading: loadingProfile, execute: loadProfile } = useAsyncOperation(
    async () => {
      const res = await fetch(`/api/users/${userId}`);
      return res.json();
    },
    {
      retryCount: 2,
      retryDelay: 1000,
      onSuccess: (data) => setProfile(data),
      onError: () => {
        showNotification({
          message: 'Failed to load profile',
          variant: 'error'
        });
      }
    }
  );

  // Load posts
  const { loading: loadingPosts, execute: loadPosts } = useAsyncOperation(
    async () => {
      const res = await fetch(`/api/users/${userId}/posts`);
      return res.json();
    },
    {
      retryCount: 2,
      retryDelay: 1000,
      onSuccess: (data) => setPosts(data)
    }
  );

  // Update profile
  const { loading: updating, execute: updateProfile } = useAsyncOperation(
    async (updates: any) => {
      const res = await fetch(`/api/users/${userId}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(updates)
      });
      return res.json();
    },
    {
      onSuccess: (data) => {
        setProfile(data);
        showNotification({
          message: 'Profile updated successfully',
          variant: 'success'
        });
      },
      onError: () => {
        showNotification({
          message: 'Failed to update profile',
          variant: 'error'
        });
      }
    }
  );

  React.useEffect(() => {
    Promise.all([loadProfile(), loadPosts()]);
  }, [userId]);

  const loading = loadingProfile || loadingPosts;

  if (loading) return <CircularProgress />;

  return (
    <Box>
      <Card>
        <CardContent>
          <Typography variant="h5">{profile?.name}</Typography>
          <Typography variant="body2">{profile?.email}</Typography>
          <Button 
            onClick={() => updateProfile({ name: 'New Name' })}
            disabled={updating}
          >
            {updating ? 'Updating...' : 'Update Profile'}
          </Button>
        </CardContent>
      </Card>

      <Box sx={{ mt: 3 }}>
        <Typography variant="h6">Posts</Typography>
        {posts.map((post: any) => (
          <Card key={post.id} sx={{ mb: 2 }}>
            <CardContent>
              <Typography>{post.title}</Typography>
            </CardContent>
          </Card>
        ))}
      </Box>
    </Box>
  );
};

export default withErrorBoundary(UserProfile);
```

### Example 3: Search Component with Debouncing

```typescript
import { withErrorBoundary } from '@/components/enterprise';
import { useAsyncOperation } from '@/hooks/enterprise';

const SearchComponent = () => {
  const [query, setQuery] = React.useState('');
  const [results, setResults] = React.useState([]);

  const { loading, execute: search } = useAsyncOperation(
    async (searchQuery: string) => {
      if (!searchQuery) return [];
      const res = await fetch(`/api/search?q=${encodeURIComponent(searchQuery)}`);
      return res.json();
    },
    {
      retryCount: 1,
      retryDelay: 500,
      onSuccess: (data) => setResults(data)
    }
  );

  // Debounce search
  React.useEffect(() => {
    const timer = setTimeout(() => {
      if (query) {
        search(query);
      } else {
        setResults([]);
      }
    }, 500);

    return () => clearTimeout(timer);
  }, [query]);

  return (
    <Box>
      <TextField
        fullWidth
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        placeholder="Search..."
        InputProps={{
          endAdornment: loading && <CircularProgress size={20} />
        }}
      />
      
      <List>
        {results.map((result: any) => (
          <ListItem key={result.id}>
            <ListItemText primary={result.title} />
          </ListItem>
        ))}
      </List>
    </Box>
  );
};

export default withErrorBoundary(SearchComponent);
```

---

## 🎯 Quick Start Checklist

For any new component:

- [ ] Import `withErrorBoundary` from `@/components/enterprise`
- [ ] Wrap your component export: `export default withErrorBoundary(MyComponent)`
- [ ] For API calls, use `useAsyncOperation` from `@/hooks/enterprise`
- [ ] Configure retry logic based on operation criticality
- [ ] Add success/error callbacks for user feedback
- [ ] Handle loading states in your UI
- [ ] Test error scenarios

---

## 📖 Further Reading

- See `PHASE_4_IMPLEMENTATION_SUMMARY.md` for technical details
- See `COMPREHENSIVE_MODERNIZATION_PLAN.md` for overall architecture
- Check existing components in `src/views/apps/` for examples
- Review HOC implementations in `src/components/enterprise/HOCs/`

---

## 🆘 Need Help?

Common issues and solutions:

**Q: My component isn't catching errors**  
A: Make sure you're exporting with `withErrorBoundary(Component)`, not just `Component`

**Q: Retries aren't working**  
A: Check that you're calling `execute()` and that your operation is actually failing

**Q: Custom fallback not showing**  
A: Verify you're passing it as second argument: `withErrorBoundary(Component, CustomFallback)`

**Q: Loading state stuck**  
A: Ensure your async function either resolves or throws an error

---

**Remember:** Error handling is about creating a better user experience. Apply these patterns to make your app more reliable and professional! 🚀
