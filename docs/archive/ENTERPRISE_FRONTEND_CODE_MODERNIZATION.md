# 🏗️ Enterprise Frontend Code Modernization Plan
## Code-Level Architecture Transformation for FAANG Standards

**Focus**: Frontend-only, code-level modernization with enterprise patterns  
**Current State**: Modern React app with Context API + React Query  
**Goal**: Enterprise-grade code structure, patterns, and implementation standards

---

## 📊 **CURRENT CODE ANALYSIS**

### ✅ **Existing Strengths**
- Modern TypeScript with strict mode
- Context API + React Query hybrid architecture
- Performance monitoring hooks
- Security middleware components
- Error boundary implementation

### ⚠️ **Code-Level Gaps Identified**
- **Component Architecture**: Missing enterprise patterns (Compound Components, Render Props)
- **Type Safety**: Incomplete generic types and utility types
- **Error Handling**: Inconsistent error boundaries and error states
- **Testing Patterns**: Missing enterprise testing strategies
- **Code Organization**: Flat structure needs domain-driven organization
- **Performance Patterns**: Missing advanced optimization patterns

---

## 🎯 **ENTERPRISE CODE MODERNIZATION ROADMAP**

### **Phase 1: Advanced TypeScript Patterns (Week 1)**

#### **1.1 Enterprise Type System**
```typescript
// src/types/enterprise.ts
// Advanced TypeScript patterns for enterprise applications

// Generic API Response Types
export interface ApiResponse<T = unknown, E = string> {
  readonly data: T;
  readonly message: string;
  readonly success: boolean;
  readonly statusCode: number;
  readonly error?: E;
  readonly timestamp: string;
  readonly requestId: string;
}

// Utility Types for Enterprise Patterns
export type NonNullable<T> = T extends null | undefined ? never : T;
export type DeepPartial<T> = {
  [P in keyof T]?: T[P] extends object ? DeepPartial<T[P]> : T[P];
};
export type DeepRequired<T> = {
  [P in keyof T]-?: T[P] extends object ? DeepRequired<T[P]> : T[P];
};

// Branded Types for Domain Safety
export type UserId = string & { readonly __brand: 'UserId' };
export type ProductId = string & { readonly __brand: 'ProductId' };
export type OrderId = string & { readonly __brand: 'OrderId' };

// Conditional Types for Advanced Patterns
export type ApiEndpoint<T extends string> = T extends `/${infer U}` ? U : never;
export type ExtractApiResponse<T> = T extends ApiResponse<infer U> ? U : never;

// Mapped Types for Component Props
export type ComponentProps<T> = T extends React.ComponentType<infer P> ? P : never;
export type EventHandlers<T> = {
  [K in keyof T as K extends `on${string}` ? K : never]: T[K];
};

// Template Literal Types
export type RoutePattern<T extends string> = T extends `${infer U}/${infer V}` 
  ? U | RoutePattern<V> 
  : T;

// Advanced Generic Constraints
export interface Repository<TEntity, TId = string> {
  findById(id: TId): Promise<TEntity | null>;
  findAll(): Promise<TEntity[]>;
  create(entity: Omit<TEntity, 'id'>): Promise<TEntity>;
  update(id: TId, entity: Partial<TEntity>): Promise<TEntity>;
  delete(id: TId): Promise<void>;
}

// Discriminated Unions for State Management
export type LoadingState<T> = 
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'success'; data: T }
  | { status: 'error'; error: Error };
```

#### **1.2 Advanced Component Types**
```typescript
// src/types/components.ts
// Enterprise component type definitions

// Polymorphic Component Types
export type PolymorphicComponent<T extends React.ElementType> = {
  as?: T;
  children?: React.ReactNode;
} & Omit<React.ComponentPropsWithoutRef<T>, 'as' | 'children'>;

// Compound Component Pattern Types
export interface CompoundComponent<T> {
  Root: React.ComponentType<T>;
  Header: React.ComponentType<{ children: React.ReactNode }>;
  Body: React.ComponentType<{ children: React.ReactNode }>;
  Footer: React.ComponentType<{ children: React.ReactNode }>;
}

// Render Props Pattern Types
export interface RenderProps<T> {
  children: (props: T) => React.ReactNode;
}

// Higher-Order Component Types
export type HOC<TProps = {}> = <TComponent extends React.ComponentType<any>>(
  Component: TComponent
) => React.ComponentType<TProps & ComponentProps<TComponent>>;

// Hook Types with Advanced Patterns
export interface UseAsyncState<T> {
  data: T | null;
  loading: boolean;
  error: Error | null;
  execute: () => Promise<void>;
  reset: () => void;
}

export interface UseFormState<T> {
  values: T;
  errors: Partial<Record<keyof T, string>>;
  touched: Partial<Record<keyof T, boolean>>;
  isValid: boolean;
  isSubmitting: boolean;
  setValue: <K extends keyof T>(field: K, value: T[K]) => void;
  setError: <K extends keyof T>(field: K, error: string) => void;
  handleSubmit: (onSubmit: (values: T) => void) => (e: React.FormEvent) => void;
  resetForm: () => void;
}
```

### **Phase 2: Enterprise Component Architecture (Week 2)**

#### **2.1 Compound Component Pattern**
```typescript
// src/components/enterprise/Card/Card.tsx
// Enterprise Card component with compound pattern

import React, { createContext, useContext } from 'react';
import { Card as MuiCard, CardContent, CardHeader, CardActions } from '@mui/material';
import { styled } from '@mui/material/styles';

// Context for compound components
interface CardContextValue {
  variant: 'default' | 'outlined' | 'elevated';
  size: 'small' | 'medium' | 'large';
}

const CardContext = createContext<CardContextValue>({
  variant: 'default',
  size: 'medium'
});

// Styled components
const StyledCard = styled(MuiCard)<{ variant: string; size: string }>(({ theme, variant, size }) => ({
  ...(variant === 'outlined' && {
    border: `1px solid ${theme.palette.divider}`,
    boxShadow: 'none'
  }),
  ...(variant === 'elevated' && {
    boxShadow: theme.shadows[4]
  }),
  ...(size === 'small' && {
    padding: theme.spacing(1)
  }),
  ...(size === 'large' && {
    padding: theme.spacing(3)
  })
}));

// Root Component
interface CardRootProps {
  children: React.ReactNode;
  variant?: 'default' | 'outlined' | 'elevated';
  size?: 'small' | 'medium' | 'large';
  className?: string;
}

const CardRoot: React.FC<CardRootProps> = ({ 
  children, 
  variant = 'default', 
  size = 'medium',
  className 
}) => {
  const contextValue: CardContextValue = { variant, size };
  
  return (
    <CardContext.Provider value={contextValue}>
      <StyledCard variant={variant} size={size} className={className}>
        {children}
      </StyledCard>
    </CardContext.Provider>
  );
};

// Header Component
interface CardHeaderProps {
  title: string;
  subtitle?: string;
  avatar?: React.ReactNode;
  action?: React.ReactNode;
}

const CardHeader: React.FC<CardHeaderProps> = ({ title, subtitle, avatar, action }) => {
  const { size } = useContext(CardContext);
  
  return (
    <MuiCard.Header
      title={title}
      subheader={subtitle}
      avatar={avatar}
      action={action}
      sx={{ 
        padding: size === 'small' ? 1 : size === 'large' ? 3 : 2 
      }}
    />
  );
};

// Body Component
interface CardBodyProps {
  children: React.ReactNode;
}

const CardBody: React.FC<CardBodyProps> = ({ children }) => {
  const { size } = useContext(CardContext);
  
  return (
    <CardContent sx={{ 
      padding: size === 'small' ? 1 : size === 'large' ? 3 : 2 
    }}>
      {children}
    </CardContent>
  );
};

// Actions Component
interface CardActionsProps {
  children: React.ReactNode;
  justifyContent?: 'flex-start' | 'center' | 'flex-end';
}

const CardActions: React.FC<CardActionsProps> = ({ 
  children, 
  justifyContent = 'flex-end' 
}) => {
  const { size } = useContext(CardContext);
  
  return (
    <MuiCard.Actions sx={{ 
      padding: size === 'small' ? 1 : size === 'large' ? 3 : 2,
      justifyContent 
    }}>
      {children}
    </MuiCard.Actions>
  );
};

// Compound Component Export
export const Card: CompoundComponent<CardRootProps> = Object.assign(CardRoot, {
  Header: CardHeader,
  Body: CardBody,
  Actions: CardActions
});

// Usage Example:
// <Card variant="elevated" size="large">
//   <Card.Header title="User Profile" subtitle="Manage your account" />
//   <Card.Body>
//     <UserProfileForm />
//   </Card.Body>
//   <Card.Actions justifyContent="center">
//     <Button>Save</Button>
//   </Card.Actions>
// </Card>
```

#### **2.2 Render Props Pattern Implementation**
```typescript
// src/components/enterprise/DataProvider/DataProvider.tsx
// Enterprise data provider with render props

import React, { useState, useEffect, useCallback } from 'react';
import { ApiResponse } from '@/types/enterprise';

interface DataProviderProps<T> {
  children: (props: {
    data: T | null;
    loading: boolean;
    error: Error | null;
    refetch: () => Promise<void>;
    mutate: (data: T) => void;
  }) => React.ReactNode;
  fetchFn: () => Promise<ApiResponse<T>>;
  initialData?: T | null;
  dependencies?: React.DependencyList;
}

export function DataProvider<T>({
  children,
  fetchFn,
  initialData = null,
  dependencies = []
}: DataProviderProps<T>) {
  const [data, setData] = useState<T | null>(initialData);
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<Error | null>(null);

  const fetchData = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const response = await fetchFn();
      setData(response.data);
    } catch (err) {
      setError(err instanceof Error ? err : new Error('Unknown error'));
    } finally {
      setLoading(false);
    }
  }, dependencies);

  const mutate = useCallback((newData: T) => {
    setData(newData);
  }, []);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  return (
    <>
      {children({
        data,
        loading,
        error,
        refetch: fetchData,
        mutate
      })}
    </>
  );
}

// Usage Example:
// <DataProvider
//   fetchFn={() => api.getUsers()}
//   dependencies={[userId]}
// >
//   {({ data: users, loading, error, refetch }) => (
//     <UserList 
//       users={users} 
//       loading={loading} 
//       error={error}
//       onRefresh={refetch}
//     />
//   )}
// </DataProvider>
```

#### **2.3 Higher-Order Component Patterns**
```typescript
// src/components/enterprise/HOCs/withErrorBoundary.tsx
// Enterprise error boundary HOC

import React, { Component, ComponentType, ErrorInfo, ReactNode } from 'react';
import { Alert, Button, Box, Typography } from '@mui/material';

interface ErrorBoundaryState {
  hasError: boolean;
  error?: Error;
  errorInfo?: ErrorInfo;
}

interface ErrorFallbackProps {
  error: Error;
  resetError: () => void;
}

// Error Fallback Component
const ErrorFallback: React.FC<ErrorFallbackProps> = ({ error, resetError }) => (
  <Box sx={{ p: 3, textAlign: 'center' }}>
    <Alert severity="error" sx={{ mb: 2 }}>
      <Typography variant="h6" gutterBottom>
        Something went wrong
      </Typography>
      <Typography variant="body2" color="text.secondary">
        {error.message}
      </Typography>
    </Alert>
    <Button variant="contained" onClick={resetError}>
      Try Again
    </Button>
  </Box>
);

// Error Boundary Class Component
class ErrorBoundary extends Component<
  { children: ReactNode; fallback?: ComponentType<ErrorFallbackProps> },
  ErrorBoundaryState
> {
  constructor(props: { children: ReactNode; fallback?: ComponentType<ErrorFallbackProps> }) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    this.setState({ error, errorInfo });
    
    // Log error to monitoring service
    console.error('Error caught by boundary:', error, errorInfo);
  }

  resetError = () => {
    this.setState({ hasError: false, error: undefined, errorInfo: undefined });
  };

  render() {
    if (this.state.hasError && this.state.error) {
      const FallbackComponent = this.props.fallback || ErrorFallback;
      return <FallbackComponent error={this.state.error} resetError={this.resetError} />;
    }

    return this.props.children;
  }
}

// HOC Factory
export function withErrorBoundary<P extends object>(
  Component: ComponentType<P>,
  fallback?: ComponentType<ErrorFallbackProps>
) {
  const WrappedComponent = (props: P) => (
    <ErrorBoundary fallback={fallback}>
      <Component {...props} />
    </ErrorBoundary>
  );

  WrappedComponent.displayName = `withErrorBoundary(${Component.displayName || Component.name})`;
  
  return WrappedComponent;
}

// Usage Example:
// const SafeUserProfile = withErrorBoundary(UserProfile, CustomErrorFallback);
```

### **Phase 3: Advanced Hook Patterns (Week 3)**

#### **3.1 Enterprise Custom Hooks**
```typescript
// src/hooks/enterprise/useAsyncOperation.ts
// Enterprise async operation hook with advanced patterns

import { useState, useCallback, useRef, useEffect } from 'react';
import { ApiResponse } from '@/types/enterprise';

interface UseAsyncOperationOptions<T> {
  onSuccess?: (data: T) => void;
  onError?: (error: Error) => void;
  retryCount?: number;
  retryDelay?: number;
  enabled?: boolean;
}

interface UseAsyncOperationReturn<T> {
  data: T | null;
  loading: boolean;
  error: Error | null;
  execute: (...args: any[]) => Promise<T | null>;
  reset: () => void;
  retry: () => Promise<T | null>;
}

export function useAsyncOperation<T>(
  asyncFn: (...args: any[]) => Promise<ApiResponse<T>>,
  options: UseAsyncOperationOptions<T> = {}
): UseAsyncOperationReturn<T> {
  const {
    onSuccess,
    onError,
    retryCount = 3,
    retryDelay = 1000,
    enabled = true
  } = options;

  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<Error | null>(null);
  const [retryAttempts, setRetryAttempts] = useState<number>(0);
  
  const isMountedRef = useRef<boolean>(true);
  const lastArgsRef = useRef<any[]>([]);

  useEffect(() => {
    return () => {
      isMountedRef.current = false;
    };
  }, []);

  const executeWithRetry = useCallback(async (
    args: any[],
    attempt: number = 0
  ): Promise<T | null> => {
    if (!enabled) return null;

    try {
      setLoading(true);
      setError(null);
      
      const response = await asyncFn(...args);
      
      if (isMountedRef.current) {
        setData(response.data);
        setRetryAttempts(0);
        onSuccess?.(response.data);
        return response.data;
      }
      
      return null;
    } catch (err) {
      const error = err instanceof Error ? err : new Error('Unknown error');
      
      if (isMountedRef.current) {
        setError(error);
        onError?.(error);
        
        // Retry logic
        if (attempt < retryCount) {
          setRetryAttempts(attempt + 1);
          await new Promise(resolve => setTimeout(resolve, retryDelay * Math.pow(2, attempt)));
          return executeWithRetry(args, attempt + 1);
        }
      }
      
      throw error;
    } finally {
      if (isMountedRef.current) {
        setLoading(false);
      }
    }
  }, [asyncFn, enabled, retryCount, retryDelay, onSuccess, onError]);

  const execute = useCallback(async (...args: any[]) => {
    lastArgsRef.current = args;
    return executeWithRetry(args);
  }, [executeWithRetry]);

  const retry = useCallback(async () => {
    return executeWithRetry(lastArgsRef.current);
  }, [executeWithRetry]);

  const reset = useCallback(() => {
    setData(null);
    setError(null);
    setRetryAttempts(0);
  }, []);

  return {
    data,
    loading,
    error,
    execute,
    reset,
    retry
  };
}

// Usage Example:
// const { data: users, loading, error, execute: fetchUsers, retry } = useAsyncOperation(
//   api.getUsers,
//   {
//     onSuccess: (users) => console.log('Users loaded:', users),
//     onError: (error) => console.error('Failed to load users:', error),
//     retryCount: 3,
//     retryDelay: 1000
//   }
// );
```

#### **3.2 Advanced Form Hook**
```typescript
// src/hooks/enterprise/useAdvancedForm.ts
// Enterprise form management hook

import { useState, useCallback, useMemo } from 'react';
import { ValidationRule } from '@/types/common';

interface UseAdvancedFormOptions<T> {
  initialValues: T;
  validationRules?: Partial<Record<keyof T, ValidationRule[]>>;
  onSubmit?: (values: T) => Promise<void> | void;
  validateOnChange?: boolean;
  validateOnBlur?: boolean;
}

interface UseAdvancedFormReturn<T> {
  values: T;
  errors: Partial<Record<keyof T, string>>;
  touched: Partial<Record<keyof T, boolean>>;
  isValid: boolean;
  isSubmitting: boolean;
  isDirty: boolean;
  setValue: <K extends keyof T>(field: K, value: T[K]) => void;
  setError: <K extends keyof T>(field: K, error: string) => void;
  setTouched: <K extends keyof T>(field: K, touched: boolean) => void;
  handleChange: <K extends keyof T>(field: K) => (event: React.ChangeEvent<HTMLInputElement>) => void;
  handleBlur: <K extends keyof T>(field: K) => () => void;
  handleSubmit: (onSubmit?: (values: T) => Promise<void> | void) => (event: React.FormEvent) => Promise<void>;
  resetForm: () => void;
  validateField: <K extends keyof T>(field: K) => string | null;
  validateForm: () => boolean;
}

export function useAdvancedForm<T extends Record<string, any>>({
  initialValues,
  validationRules = {},
  onSubmit,
  validateOnChange = true,
  validateOnBlur = true
}: UseAdvancedFormOptions<T>): UseAdvancedFormReturn<T> {
  const [values, setValues] = useState<T>(initialValues);
  const [errors, setErrors] = useState<Partial<Record<keyof T, string>>>({});
  const [touched, setTouched] = useState<Partial<Record<keyof T, boolean>>>({});
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);

  // Validation function
  const validateField = useCallback(<K extends keyof T>(field: K): string | null => {
    const fieldValue = values[field];
    const rules = validationRules[field] || [];

    for (const rule of rules) {
      switch (rule.type) {
        case 'required':
          if (!fieldValue || (typeof fieldValue === 'string' && fieldValue.trim() === '')) {
            return rule.message;
          }
          break;
        case 'email':
          if (fieldValue && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(fieldValue as string)) {
            return rule.message;
          }
          break;
        case 'minLength':
          if (fieldValue && (fieldValue as string).length < (rule.value || 0)) {
            return rule.message;
          }
          break;
        case 'maxLength':
          if (fieldValue && (fieldValue as string).length > (rule.value || 0)) {
            return rule.message;
          }
          break;
        case 'pattern':
          if (fieldValue && rule.value && !(rule.value as RegExp).test(fieldValue as string)) {
            return rule.message;
          }
          break;
        case 'custom':
          if (rule.validator && !rule.validator(fieldValue)) {
            return rule.message;
          }
          break;
      }
    }

    return null;
  }, [values, validationRules]);

  // Validate entire form
  const validateForm = useCallback((): boolean => {
    const newErrors: Partial<Record<keyof T, string>> = {};
    let isValid = true;

    Object.keys(values).forEach((field) => {
      const error = validateField(field as keyof T);
      if (error) {
        newErrors[field as keyof T] = error;
        isValid = false;
      }
    });

    setErrors(newErrors);
    return isValid;
  }, [values, validateField]);

  // Set value with validation
  const setValue = useCallback(<K extends keyof T>(field: K, value: T[K]) => {
    setValues(prev => ({ ...prev, [field]: value }));
    
    if (validateOnChange) {
      const error = validateField(field);
      setErrors(prev => ({ ...prev, [field]: error || undefined }));
    }
  }, [validateField, validateOnChange]);

  // Handle change events
  const handleChange = useCallback(<K extends keyof T>(field: K) => 
    (event: React.ChangeEvent<HTMLInputElement>) => {
      const value = event.target.type === 'checkbox' 
        ? event.target.checked 
        : event.target.value;
      setValue(field, value as T[K]);
    }, [setValue]);

  // Handle blur events
  const handleBlur = useCallback(<K extends keyof T>(field: K) => () => {
    setTouched(prev => ({ ...prev, [field]: true }));
    
    if (validateOnBlur) {
      const error = validateField(field);
      setErrors(prev => ({ ...prev, [field]: error || undefined }));
    }
  }, [validateField, validateOnBlur]);

  // Handle form submission
  const handleSubmit = useCallback((customOnSubmit?: (values: T) => Promise<void> | void) => 
    async (event: React.FormEvent) => {
      event.preventDefault();
      
      setIsSubmitting(true);
      
      try {
        const isValid = validateForm();
        if (isValid) {
          const submitFn = customOnSubmit || onSubmit;
          if (submitFn) {
            await submitFn(values);
          }
        }
      } catch (error) {
        console.error('Form submission error:', error);
      } finally {
        setIsSubmitting(false);
      }
    }, [values, validateForm, onSubmit]);

  // Reset form
  const resetForm = useCallback(() => {
    setValues(initialValues);
    setErrors({});
    setTouched({});
    setIsSubmitting(false);
  }, [initialValues]);

  // Computed values
  const isValid = useMemo(() => {
    return Object.keys(errors).length === 0 && Object.values(values).every(value => 
      value !== null && value !== undefined && value !== ''
    );
  }, [errors, values]);

  const isDirty = useMemo(() => {
    return JSON.stringify(values) !== JSON.stringify(initialValues);
  }, [values, initialValues]);

  return {
    values,
    errors,
    touched,
    isValid,
    isSubmitting,
    isDirty,
    setValue,
    setError: (field, error) => setErrors(prev => ({ ...prev, [field]: error })),
    setTouched: (field, touched) => setTouched(prev => ({ ...prev, [field]: touched })),
    handleChange,
    handleBlur,
    handleSubmit,
    resetForm,
    validateField,
    validateForm
  };
}
```

### **Phase 4: Enterprise Testing Patterns (Week 4)**

#### **4.1 Component Testing Utilities**
```typescript
// src/test-utils/enterprise-testing.tsx
// Enterprise testing utilities and patterns

import React from 'react';
import { render, RenderOptions, RenderResult } from '@testing-library/react';
import { ThemeProvider } from '@mui/material/styles';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createTheme } from '@mui/material/styles';
import { vi } from 'vitest';

// Mock theme for testing
const testTheme = createTheme({
  palette: {
    mode: 'light',
    primary: { main: '#1976d2' },
    secondary: { main: '#dc004e' }
  }
});

// Mock QueryClient for testing
const createTestQueryClient = () => new QueryClient({
  defaultOptions: {
    queries: {
      retry: false,
      gcTime: 0,
    },
    mutations: {
      retry: false,
    },
  },
});

// Enterprise test wrapper
interface TestWrapperProps {
  children: React.ReactNode;
  queryClient?: QueryClient;
  theme?: any;
}

const TestWrapper: React.FC<TestWrapperProps> = ({ 
  children, 
  queryClient = createTestQueryClient(),
  theme = testTheme 
}) => (
  <QueryClientProvider client={queryClient}>
    <ThemeProvider theme={theme}>
      {children}
    </ThemeProvider>
  </QueryClientProvider>
);

// Custom render function
export function renderWithProviders(
  ui: React.ReactElement,
  options: RenderOptions & {
    queryClient?: QueryClient;
    theme?: any;
  } = {}
): RenderResult {
  const { queryClient, theme, ...renderOptions } = options;
  
  return render(ui, {
    wrapper: ({ children }) => (
      <TestWrapper queryClient={queryClient} theme={theme}>
        {children}
      </TestWrapper>
    ),
    ...renderOptions
  });
}

// Mock API utilities
export const mockApiResponse = <T>(data: T, success: boolean = true) => ({
  data,
  message: success ? 'Success' : 'Error',
  success,
  statusCode: success ? 200 : 400,
  timestamp: new Date().toISOString(),
  requestId: 'test-request-id'
});

// Mock hook utilities
export const createMockHook = <T>(returnValue: T) => vi.fn(() => returnValue);

// Test data factories
export const createTestUser = (overrides: Partial<User> = {}): User => ({
  id: '1',
  email: 'test@example.com',
  firstName: 'Test',
  lastName: 'User',
  role: 'user',
  createdAt: new Date(),
  updatedAt: new Date(),
  ...overrides
});

export const createTestProduct = (overrides: Partial<Product> = {}): Product => ({
  id: '1',
  name: 'Test Product',
  description: 'Test Description',
  price: 99.99,
  category: 'electronics',
  inStock: true,
  createdAt: new Date(),
  updatedAt: new Date(),
  ...overrides
});

// Custom matchers for testing
export const customMatchers = {
  toBeInTheDocument: (received: HTMLElement) => {
    const pass = received !== null;
    return {
      pass,
      message: () => `Expected element ${pass ? 'not ' : ''}to be in the document`
    };
  }
};

// Test utilities for async operations
export const waitForAsyncOperation = () => new Promise(resolve => setTimeout(resolve, 0));

export const mockAsyncOperation = <T>(data: T, delay: number = 100) => 
  new Promise<T>(resolve => setTimeout(() => resolve(data), delay));
```

#### **4.2 Enterprise Test Patterns**
```typescript
// src/components/enterprise/__tests__/Card.test.tsx
// Enterprise component testing patterns

import React from 'react';
import { screen, fireEvent, waitFor } from '@testing-library/react';
import { renderWithProviders, createTestUser } from '@/test-utils/enterprise-testing';
import { Card } from '../Card/Card';

describe('Card Component - Enterprise Patterns', () => {
  describe('Compound Component Pattern', () => {
    it('should render card with header, body, and actions', () => {
      renderWithProviders(
        <Card variant="elevated" size="large">
          <Card.Header title="Test Card" subtitle="Test Subtitle" />
          <Card.Body>
            <div data-testid="card-content">Card Content</div>
          </Card.Body>
          <Card.Actions justifyContent="center">
            <button>Action Button</button>
          </Card.Actions>
        </Card>
      );

      expect(screen.getByText('Test Card')).toBeInTheDocument();
      expect(screen.getByText('Test Subtitle')).toBeInTheDocument();
      expect(screen.getByTestId('card-content')).toBeInTheDocument();
      expect(screen.getByRole('button')).toBeInTheDocument();
    });

    it('should apply correct styling based on variant and size', () => {
      const { container } = renderWithProviders(
        <Card variant="outlined" size="small">
          <Card.Body>Content</Card.Body>
        </Card>
      );

      const cardElement = container.firstChild as HTMLElement;
      expect(cardElement).toHaveStyle({
        border: expect.stringContaining('1px solid'),
        boxShadow: 'none'
      });
    });
  });

  describe('Accessibility', () => {
    it('should have proper ARIA attributes', () => {
      renderWithProviders(
        <Card>
          <Card.Header title="Accessible Card" />
          <Card.Body>Content</Card.Body>
        </Card>
      );

      const cardElement = screen.getByRole('region');
      expect(cardElement).toBeInTheDocument();
    });
  });

  describe('Error Handling', () => {
    it('should handle missing children gracefully', () => {
      expect(() => {
        renderWithProviders(<Card>{null}</Card>);
      }).not.toThrow();
    });
  });
});

// src/hooks/enterprise/__tests__/useAsyncOperation.test.ts
// Enterprise hook testing patterns

import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useAsyncOperation } from '../useAsyncOperation';
import { mockApiResponse } from '@/test-utils/enterprise-testing';

const createWrapper = () => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false }
    }
  });
  
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>
      {children}
    </QueryClientProvider>
  );
};

describe('useAsyncOperation Hook - Enterprise Patterns', () => {
  const mockApiCall = vi.fn();

  beforeEach(() => {
    mockApiCall.mockClear();
  });

  it('should handle successful async operations', async () => {
    const testData = { id: '1', name: 'Test' };
    mockApiCall.mockResolvedValue(mockApiResponse(testData));

    const { result } = renderHook(
      () => useAsyncOperation(mockApiCall),
      { wrapper: createWrapper() }
    );

    expect(result.current.loading).toBe(false);
    expect(result.current.data).toBe(null);

    const promise = result.current.execute();

    expect(result.current.loading).toBe(true);

    await waitFor(() => {
      expect(result.current.loading).toBe(false);
    });

    const data = await promise;
    expect(data).toEqual(testData);
    expect(result.current.data).toEqual(testData);
    expect(result.current.error).toBe(null);
  });

  it('should handle retry logic on failure', async () => {
    const error = new Error('Network error');
    mockApiCall
      .mockRejectedValueOnce(error)
      .mockRejectedValueOnce(error)
      .mockResolvedValue(mockApiResponse({ success: true }));

    const { result } = renderHook(
      () => useAsyncOperation(mockApiCall, { retryCount: 2, retryDelay: 10 }),
      { wrapper: createWrapper() }
    );

    await result.current.execute();

    expect(mockApiCall).toHaveBeenCalledTimes(3);
    expect(result.current.data).toEqual({ success: true });
  });

  it('should call success and error callbacks', async () => {
    const onSuccess = vi.fn();
    const onError = vi.fn();
    const testData = { id: '1' };

    mockApiCall.mockResolvedValue(mockApiResponse(testData));

    const { result } = renderHook(
      () => useAsyncOperation(mockApiCall, { onSuccess, onError }),
      { wrapper: createWrapper() }
    );

    await result.current.execute();

    expect(onSuccess).toHaveBeenCalledWith(testData);
    expect(onError).not.toHaveBeenCalled();
  });
});
```

### **Phase 5: Performance Optimization Patterns (Week 5)**

#### **5.1 Advanced Memoization Patterns**
```typescript
// src/hooks/enterprise/useMemoizedCallback.ts
// Enterprise memoization patterns

import { useCallback, useMemo, useRef, useEffect } from 'react';

// Memoized callback with dependency comparison
export function useMemoizedCallback<T extends (...args: any[]) => any>(
  callback: T,
  deps: React.DependencyList
): T {
  const ref = useRef<{ callback: T; deps: React.DependencyList }>({ callback, deps });
  
  // Deep comparison of dependencies
  const depsChanged = useMemo(() => {
    if (ref.current.deps.length !== deps.length) return true;
    return deps.some((dep, index) => dep !== ref.current.deps[index]);
  }, [deps]);

  if (depsChanged) {
    ref.current = { callback, deps };
  }

  return useCallback(ref.current.callback, []);
}

// Memoized selector for complex computations
export function useMemoizedSelector<T, R>(
  data: T,
  selector: (data: T) => R,
  deps: React.DependencyList = []
): R {
  return useMemo(() => selector(data), [data, ...deps]);
}

// Stable reference hook
export function useStableReference<T>(value: T): T {
  const ref = useRef<T>(value);
  
  useEffect(() => {
    ref.current = value;
  }, [value]);
  
  return ref.current;
}

// Usage Example:
// const expensiveComputation = useMemoizedCallback(
//   (data: ComplexData[]) => {
//     return data.reduce((acc, item) => acc + item.value, 0);
//   },
//   [data]
// );
```

#### **5.2 Virtual Scrolling Implementation**
```typescript
// src/components/enterprise/VirtualList/VirtualList.tsx
// Enterprise virtual scrolling component

import React, { useState, useEffect, useRef, useMemo, useCallback } from 'react';
import { FixedSizeList as List } from 'react-window';
import { Box, Skeleton } from '@mui/material';

interface VirtualListProps<T> {
  items: T[];
  itemHeight: number;
  height: number;
  renderItem: (item: T, index: number) => React.ReactNode;
  loading?: boolean;
  onLoadMore?: () => void;
  threshold?: number;
  overscanCount?: number;
}

export function VirtualList<T>({
  items,
  itemHeight,
  height,
  renderItem,
  loading = false,
  onLoadMore,
  threshold = 0.8,
  overscanCount = 5
}: VirtualListProps<T>) {
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const listRef = useRef<List>(null);

  // Memoized item renderer
  const ItemRenderer = useCallback(({ index, style }: { index: number; style: React.CSSProperties }) => {
    const item = items[index];
    
    if (!item) {
      return (
        <div style={style}>
          <Skeleton variant="rectangular" height={itemHeight} />
        </div>
      );
    }

    return (
      <div style={style}>
        {renderItem(item, index)}
      </div>
    );
  }, [items, renderItem, itemHeight]);

  // Load more handler
  const handleScroll = useCallback(({ scrollTop, scrollHeight, clientHeight }: any) => {
    if (!onLoadMore || isLoadingMore) return;

    const scrollPercentage = (scrollTop + clientHeight) / scrollHeight;
    
    if (scrollPercentage >= threshold) {
      setIsLoadingMore(true);
      onLoadMore();
    }
  }, [onLoadMore, isLoadingMore, threshold]);

  // Reset loading state when items change
  useEffect(() => {
    if (isLoadingMore) {
      setIsLoadingMore(false);
    }
  }, [items.length, isLoadingMore]);

  return (
    <Box sx={{ height, width: '100%' }}>
      <List
        ref={listRef}
        height={height}
        itemCount={items.length + (loading ? 1 : 0)}
        itemSize={itemHeight}
        onScroll={handleScroll}
        overscanCount={overscanCount}
      >
        {ItemRenderer}
      </List>
    </Box>
  );
}

// Usage Example:
// <VirtualList
//   items={products}
//   itemHeight={100}
//   height={400}
//   renderItem={(product, index) => (
//     <ProductCard key={product.id} product={product} />
//   )}
//   onLoadMore={loadMoreProducts}
//   loading={isLoading}
// />
```

---

## 🎯 **IMPLEMENTATION TIMELINE**

### **Week 1: Advanced TypeScript Patterns**
- Implement enterprise type system
- Add branded types and utility types
- Create advanced generic patterns
- Set up discriminated unions

### **Week 2: Enterprise Component Architecture**
- Implement compound component patterns
- Add render props patterns
- Create higher-order components
- Build enterprise component library

### **Week 3: Advanced Hook Patterns**
- Implement enterprise custom hooks
- Add advanced form management
- Create async operation hooks
- Build hook composition patterns

### **Week 4: Enterprise Testing Patterns**
- Set up enterprise testing utilities
- Implement component testing patterns
- Add hook testing strategies
- Create test data factories

### **Week 5: Performance Optimization**
- Implement advanced memoization
- Add virtual scrolling
- Create performance monitoring
- Optimize bundle size

---

## 📈 **EXPECTED OUTCOMES**

### **Code Quality Improvements**
- **Type Safety**: 100% TypeScript coverage with advanced patterns
- **Component Reusability**: Compound components and render props
- **Testing Coverage**: 90%+ test coverage with enterprise patterns
- **Performance**: 50% improvement in render performance

### **Developer Experience**
- **Code Reusability**: Enterprise component library
- **Type Safety**: Advanced TypeScript patterns
- **Testing**: Comprehensive testing utilities
- **Documentation**: Self-documenting code patterns

### **Maintainability**
- **Architecture**: Clear separation of concerns
- **Patterns**: Consistent enterprise patterns
- **Testing**: Comprehensive test coverage
- **Performance**: Optimized rendering patterns

---

**Status**: 🎯 **ENTERPRISE FRONTEND MODERNIZATION PLAN COMPLETE**  
**Focus**: 📝 **CODE-LEVEL IMPLEMENTATION PATTERNS**  
**Timeline**: ⏱️ **5 WEEKS TO ENTERPRISE CODE STANDARDS**  
**Outcome**: 🚀 **FAANG-LEVEL FRONTEND CODE ARCHITECTURE**
