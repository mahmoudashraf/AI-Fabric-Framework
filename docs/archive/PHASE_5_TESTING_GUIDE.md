# Phase 5: Testing & Quality Assurance - Complete Guide

## 🎯 Overview

Phase 5 has been implemented with a comprehensive testing infrastructure for enterprise components, hooks, and utilities.

---

## 📚 What Was Created

### 1. Test Data Factories

**Location:** `frontend/src/test-utils/factories/`

#### User Factory (`user.factory.ts`)
```typescript
import { createTestUser, createTestAdmin, createTestUserList } from '@/test-utils/factories';

// Create a single test user
const user = createTestUser({ email: 'custom@example.com' });

// Create admin user
const admin = createTestAdmin();

// Create multiple users
const users = createTestUserList(10);
```

#### Product Factory (`product.factory.ts`)
```typescript
import { createTestProduct, createTestProductList } from '@/test-utils/factories';

// Create single product
const product = createTestProduct({ price: 99.99 });

// Create out of stock product
const outOfStock = createOutOfStockProduct();

// Create multiple products
const products = createTestProductList(20);
```

#### Customer Factory (`customer.factory.ts`)
```typescript
import { createTestCustomer, createTestOrder } from '@/test-utils/factories';

// Create customer
const customer = createTestCustomer({ orders: 50 });

// Create order
const order = createTestOrder({ status: 1 });
```

### 2. API Mocks

**Location:** `frontend/src/test-utils/mocks/`

#### API Mock Utilities (`api.mock.ts`)
```typescript
import { 
  mockApiResponse, 
  mockSuccessResponse, 
  mockErrorResponse,
  mockFetch,
  mockAsyncOperation
} from '@/test-utils/mocks';

// Mock successful API response
const response = mockSuccessResponse({ id: 1, name: 'Test' });

// Mock error response
const error = mockErrorResponse('Operation failed', 400);

// Mock fetch call
const mockFetchData = mockFetch({ data: 'test' }, true, 100);

// Mock async operation
const asyncOp = mockAsyncOperation({ result: 'success' }, 100);
```

#### Mock Handlers (`handlers.mock.ts`)
```typescript
import { 
  userHandlers, 
  productHandlers, 
  customerHandlers,
  resetAllHandlers 
} from '@/test-utils/mocks';

// Mock user API calls
userHandlers.getUsers(); // Returns list of test users
userHandlers.getUser('1'); // Returns single test user
userHandlers.createUser(userData); // Creates test user

// Similar for products and customers
productHandlers.getProducts();
customerHandlers.getCustomers();

// Reset all mocks between tests
resetAllHandlers();
```

### 3. Comprehensive Tests

#### HOC Tests

**withErrorBoundary** (`withErrorBoundary.test.tsx`)
- ✅ Renders component without error
- ✅ Catches and handles errors
- ✅ Preserves component display name
- ✅ Forwards props correctly
- ✅ Uses custom fallback when provided
- ✅ Error recovery functionality
- ✅ Handles nested component errors
- ✅ Isolates errors to individual instances

**withLoading** (`withLoading.test.tsx`)
- ✅ Renders nothing when loading is true
- ✅ Renders component when loading is false
- ✅ Forwards all props except loading
- ✅ State transitions (loading ↔ loaded)
- ✅ Works with other HOCs
- ✅ Handles edge cases (undefined, null)

**ErrorFallback** (`ErrorFallback.test.tsx`)
- ✅ Renders error message
- ✅ Renders Try Again button
- ✅ Calls resetError on button click
- ✅ Applies correct styling
- ✅ Handles different error types
- ✅ Accessibility features
- ✅ Component updates

#### Hook Tests

**useAsyncOperation** (`useAsyncOperation.test.ts`)
- ✅ Initializes with correct default values
- ✅ Executes async operation successfully
- ✅ Handles async operation failure
- ✅ Sets loading state during execution
- ✅ Retries specified number of times
- ✅ Succeeds on retry
- ✅ Respects retry delay
- ✅ Manual retry function works
- ✅ Calls onSuccess/onError callbacks
- ✅ Handles multiple executions
- ✅ Passes parameters to async function
- ✅ Edge cases (undefined, null, zero retries)

---

## 🚀 How to Use

### Running Tests

```bash
# Run all tests
npm test

# Run enterprise tests only
npm test -- --testPathPattern=enterprise

# Run specific test file
npm test -- withErrorBoundary.test

# Run with coverage
npm test -- --coverage

# Watch mode
npm test -- --watch
```

### Writing New Tests

#### 1. Component Test Template

```typescript
import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { renderWithProviders } from '@/test-utils/enterprise-testing';
import MyComponent from '../MyComponent';

describe('MyComponent', () => {
  it('renders correctly', () => {
    render(<MyComponent />);
    expect(screen.getByText('Expected text')).toBeInTheDocument();
  });

  it('handles user interaction', () => {
    render(<MyComponent />);
    const button = screen.getByRole('button');
    fireEvent.click(button);
    // Assert expected behavior
  });
});
```

#### 2. Hook Test Template

```typescript
import { renderHook, act, waitFor } from '@testing-library/react';
import { useMyHook } from '../useMyHook';

describe('useMyHook', () => {
  it('initializes correctly', () => {
    const { result } = renderHook(() => useMyHook());
    expect(result.current.value).toBe(initialValue);
  });

  it('updates value', () => {
    const { result } = renderHook(() => useMyHook());
    
    act(() => {
      result.current.setValue('new value');
    });
    
    expect(result.current.value).toBe('new value');
  });
});
```

#### 3. Async Test Template

```typescript
import { renderHook, act, waitFor } from '@testing-library/react';
import { mockAsyncOperation } from '@/test-utils/mocks';

describe('Async functionality', () => {
  it('handles async operation', async () => {
    const mockFn = jest.fn().mockResolvedValue('success');
    const { result } = renderHook(() => useAsyncHook(mockFn));

    await act(async () => {
      await result.current.execute();
    });

    await waitFor(() => {
      expect(result.current.data).toBe('success');
    });
  });
});
```

### Using Test Factories

```typescript
import { 
  createTestUser, 
  createTestProduct, 
  createTestCustomer 
} from '@/test-utils/factories';

describe('My Component', () => {
  it('displays user information', () => {
    const user = createTestUser({ 
      firstName: 'John', 
      lastName: 'Doe' 
    });
    
    render(<UserCard user={user} />);
    expect(screen.getByText('John Doe')).toBeInTheDocument();
  });
});
```

### Using API Mocks

```typescript
import { mockSuccessResponse, mockErrorResponse } from '@/test-utils/mocks';

describe('API Integration', () => {
  it('handles successful API call', async () => {
    const mockData = { id: 1, name: 'Test' };
    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      json: async () => mockSuccessResponse(mockData)
    });

    // Test your component
  });

  it('handles API error', async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: false,
      json: async () => mockErrorResponse('Failed', 400)
    });

    // Test error handling
  });
});
```

---

## 📊 Test Coverage

### Current Coverage

| Category | Files | Test Coverage |
|----------|-------|---------------|
| **HOCs** | 3/3 | ✅ 100% |
| **Enterprise Hooks** | 1/4 | 🔄 25% |
| **Components** | 1/1 | ✅ 100% |
| **Test Utils** | - | ✅ Complete |
| **Factories** | - | ✅ Complete |
| **Mocks** | - | ✅ Complete |

### Coverage Goals

- **Phase 5 Target:** 90%+ test coverage for enterprise components
- **HOCs:** ✅ 100% covered
- **Critical Hooks:** 🔄 In progress
- **Test Infrastructure:** ✅ Complete

---

## 🎓 Best Practices

### DO ✅

1. **Use Test Factories**
   ```typescript
   const user = createTestUser({ email: 'test@example.com' });
   ```

2. **Test User Interactions**
   ```typescript
   fireEvent.click(button);
   expect(handleClick).toHaveBeenCalled();
   ```

3. **Test Error Scenarios**
   ```typescript
   mockFn.mockRejectedValue(new Error('Test error'));
   ```

4. **Use Descriptive Test Names**
   ```typescript
   it('calls onSuccess callback when operation completes', async () => {
   ```

5. **Test Accessibility**
   ```typescript
   expect(button).toHaveAccessibleName();
   ```

### DON'T ❌

1. **Don't test implementation details**
   ```typescript
   // Bad: Testing internal state
   expect(component.state.value).toBe('test');
   
   // Good: Testing behavior
   expect(screen.getByText('test')).toBeInTheDocument();
   ```

2. **Don't write flaky tests**
   ```typescript
   // Bad: Arbitrary timeout
   setTimeout(() => expect(...), 1000);
   
   // Good: Wait for specific condition
   await waitFor(() => expect(...));
   ```

3. **Don't forget to clean up**
   ```typescript
   afterEach(() => {
     jest.clearAllMocks();
     resetAllHandlers();
   });
   ```

---

## 🔧 Configuration

### Jest Configuration

**File:** `frontend/jest.config.js`

```javascript
module.exports = {
  testEnvironment: 'jsdom',
  setupFilesAfterEnv: ['<rootDir>/jest.setup.js'],
  testPathIgnorePatterns: ['<rootDir>/.next/', '<rootDir>/node_modules/'],
  collectCoverageFrom: [
    'src/components/enterprise/**/*.{js,jsx,ts,tsx}',
    'src/hooks/enterprise/**/*.{js,jsx,ts,tsx}',
    'src/test-utils/**/*.{js,jsx,ts,tsx}'
  ],
  coverageThreshold: {
    global: {
      branches: 80,
      functions: 80,
      lines: 80,
      statements: 80
    }
  }
};
```

---

## 📝 Test Examples

### Example 1: Testing Error Boundary

```typescript
it('catches and displays error', () => {
  const ErrorComponent = () => {
    throw new Error('Test error');
  };
  
  const WrappedComponent = withErrorBoundary(ErrorComponent);
  render(<WrappedComponent />);
  
  // Error boundary should catch the error
  // No white screen should appear
});
```

### Example 2: Testing Async Hook

```typescript
it('retries on failure then succeeds', async () => {
  const mockFn = jest
    .fn()
    .mockRejectedValueOnce(new Error('Fail'))
    .mockResolvedValueOnce('Success');
  
  const { result } = renderHook(() => 
    useAsyncOperation(mockFn, { retryCount: 2 })
  );
  
  await act(async () => {
    await result.current.execute();
  });
  
  expect(mockFn).toHaveBeenCalledTimes(2);
  expect(result.current.data).toBe('Success');
});
```

### Example 3: Testing with Factories

```typescript
it('displays customer list', () => {
  const customers = createTestCustomerList(5);
  
  render(<CustomerList customers={customers} />);
  
  customers.forEach(customer => {
    expect(screen.getByText(customer.name)).toBeInTheDocument();
  });
});
```

---

## 🎯 Next Steps

### Remaining Tasks

- [ ] Write tests for useAdvancedForm hook
- [ ] Write tests for useTableLogic hook
- [ ] Create component testing examples
- [ ] Set up test coverage reporting
- [ ] Create comprehensive testing documentation

### Future Enhancements

- Integration tests for full user flows
- E2E tests with Playwright/Cypress
- Visual regression tests
- Performance tests
- Accessibility audits

---

## 📖 Resources

### Documentation
- [Testing Library Docs](https://testing-library.com/docs/react-testing-library/intro/)
- [Jest Documentation](https://jestjs.io/docs/getting-started)
- [React Testing Best Practices](https://kentcdodds.com/blog/common-mistakes-with-react-testing-library)

### Internal Guides
- `HOW_TO_USE_ERROR_HANDLING.md` - Error handling patterns
- `PHASE_4_USAGE_INDEX.md` - Enterprise patterns
- `COMPREHENSIVE_MODERNIZATION_PLAN.md` - Full plan

---

## ✅ Summary

Phase 5 provides:
- ✅ Complete test infrastructure
- ✅ Test data factories
- ✅ API mocks and handlers
- ✅ Comprehensive HOC tests
- ✅ Hook tests (useAsyncOperation)
- ✅ Best practices and examples
- ✅ Easy-to-use utilities

**You can now write comprehensive tests for any component or hook in your codebase!**
