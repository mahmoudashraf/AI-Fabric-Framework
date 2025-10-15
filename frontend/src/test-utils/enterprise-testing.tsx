import React from 'react';
import { render, type RenderOptions, type RenderResult } from '@testing-library/react';
import { ThemeProvider, createTheme, type Theme } from '@mui/material/styles';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

const testTheme = createTheme({
  palette: {
    mode: 'light',
    primary: { main: '#1976d2' },
    secondary: { main: '#dc004e' }
  }
});

const createTestQueryClient = () =>
  new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0 },
      mutations: { retry: false }
    }
  });

interface TestWrapperProps {
  children: React.ReactNode;
  queryClient?: QueryClient;
  theme?: Theme;
}

const TestWrapper: React.FC<TestWrapperProps> = ({ children, queryClient = createTestQueryClient(), theme = testTheme }) => (
  <QueryClientProvider client={queryClient}>
    <ThemeProvider theme={theme}>{children}</ThemeProvider>
  </QueryClientProvider>
);

export function renderWithProviders(
  ui: React.ReactElement,
  options: RenderOptions & { queryClient?: QueryClient; theme?: Theme } = {}
): RenderResult {
  const { queryClient, theme, ...renderOptions } = options;
  return render(ui, {
    wrapper: ({ children }) => {
      const props: { queryClient?: QueryClient; theme?: Theme } = {};
      if (queryClient) props.queryClient = queryClient;
      if (theme) props.theme = theme;
      return <TestWrapper {...props}>{children}</TestWrapper>;
    },
    ...renderOptions
  });
}

export const mockApiResponse = <T,>(data: T, success: boolean = true) => ({
  data,
  message: success ? 'Success' : 'Error',
  success,
  statusCode: success ? 200 : 400,
  timestamp: new Date().toISOString(),
  requestId: 'test-request-id'
});

export const waitForAsyncOperation = () => new Promise((resolve) => setTimeout(resolve, 0));

export const mockAsyncOperation = <T,>(data: T, delay: number = 100) =>
  new Promise<T>((resolve) => setTimeout(() => resolve(data), delay));

// Test data factories
// Note: Deprecated - use factory functions from '@/test-utils/factories' instead
// This is kept for backwards compatibility only
export const createTestUser = (overrides: Record<string, unknown> = {}): Record<string, unknown> => ({
  id: '1',
  email: 'test@example.com',
  firstName: 'Test',
  lastName: 'User',
  role: 'user',
  createdAt: new Date().toISOString(),
  updatedAt: new Date().toISOString(),
  ...overrides
});

// Note: Deprecated - use factory functions from '@/test-utils/factories' instead
// This is kept for backwards compatibility only
export const createTestProduct = (overrides: Record<string, unknown> = {}): Record<string, unknown> => ({
  id: '1',
  name: 'Test Product',
  description: 'Test Description',
  price: 99.99,
  category: 'electronics',
  inStock: true,
  createdAt: new Date().toISOString(),
  updatedAt: new Date().toISOString(),
  ...overrides
});

// Note: Deprecated - use factory functions from '@/test-utils/factories' instead
// This is kept for backwards compatibility only
export const createTestCustomer = (overrides: Record<string, unknown> = {}): Record<string, unknown> => ({
  id: '1',
  name: 'Test Customer',
  email: 'customer@example.com',
  phone: '123-456-7890',
  address: '123 Test St',
  city: 'Test City',
  country: 'Test Country',
  createdAt: new Date().toISOString(),
  ...overrides
});

// Note: Deprecated - use factory functions from '@/test-utils/factories' instead
// This is kept for backwards compatibility only
export const createTestOrder = (overrides: Record<string, unknown> = {}): Record<string, unknown> => ({
  id: '1',
  customerId: '1',
  orderNumber: 'ORD-001',
  status: 'pending',
  total: 199.99,
  items: [],
  createdAt: new Date().toISOString(),
  updatedAt: new Date().toISOString(),
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

// Re-export factories and mocks for convenience
export * from './factories';
export * from './mocks';


