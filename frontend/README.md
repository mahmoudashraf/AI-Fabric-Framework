## Enterprise Components & Hooks (Template Examples)

This template includes a small enterprise-ready toolkit under `src/components/enterprise` and `src/hooks/enterprise`:

- MainCard (enhanced, compound-style): `MainCard.Header`, `MainCard.Body`, `MainCard.Actions`
- DataProvider (render props)
- withErrorBoundary HOC
- Hooks: `useAsyncOperation`, `useAdvancedForm`, memoization utilities
- VirtualList (virtual scrolling)

Example page: `/simple/pages/enterprise-examples` shows how to compose these pieces using `MainCard`.

# Kiwi React Material Next.js - Modernized

A modern, production-ready React application built with Next.js, TypeScript, and Material-UI, featuring comprehensive modernization improvements.

## 🚀 Features

- **Modern TypeScript Configuration**: Enhanced with strict mode and latest ES2022 features
- **Comprehensive Testing**: Jest, React Testing Library, and comprehensive test coverage
- **Performance Optimizations**: Lazy loading, virtual scrolling, and performance monitoring
- **Security Measures**: CSRF protection, input sanitization, and secure file uploads
- **Error Handling**: Global error boundaries and comprehensive error tracking
- **Analytics & Monitoring**: Built-in analytics, performance monitoring, and A/B testing
- **Custom Hooks**: Reusable hooks for common patterns and functionality
- **Modern Component Patterns**: Memoization, lazy loading, and optimized rendering

## 📁 Project Structure

```
src/
├── components/
│   ├── common/           # Reusable common components
│   │   ├── ErrorBoundary.tsx
│   │   ├── GlobalErrorHandler.tsx
│   │   ├── LoadingState.tsx
│   │   ├── PerformanceOptimized.tsx
│   │   └── SecurityMiddleware.tsx
│   ├── dashboard/        # Dashboard-specific components
│   └── ui-component/     # UI component library
├── hooks/
│   ├── useAnalytics.ts   # Analytics and monitoring hooks
│   ├── useCommon.ts      # Common utility hooks
│   ├── useForm.ts        # Form management hooks
│   ├── usePerformance.ts # Performance monitoring hooks
│   └── useSecurity.ts    # Security-related hooks
├── types/
│   ├── common.ts         # Common TypeScript interfaces
│   └── index.ts          # Type exports
├── utils/                # Utility functions
├── store/                # Redux store configuration
└── themes/               # Material-UI theme configuration
```

## 🛠️ Getting Started

### Prerequisites

- Node.js 18+
- npm or yarn

### Installation

1. Clone the repository:

```bash
git clone <repository-url>
cd kiwi-react-material-next-ts
```

2. Install dependencies:

```bash
npm install
# or
yarn install
```

3. Set up environment variables:

```bash
cp .env.example .env.local
# Edit .env.local with your configuration
```

4. Run the development server:

```bash
npm run dev
# or
yarn dev
```

5. Open [http://localhost:3000](http://localhost:3000) in your browser.

## 📋 Available Scripts

- `npm run dev` - Start development server
- `npm run build` - Build for production
- `npm run start` - Start production server
- `npm run lint` - Run ESLint
- `npm run lint:fix` - Fix ESLint issues
- `npm run test` - Run tests
- `npm run test:watch` - Run tests in watch mode
- `npm run test:coverage` - Run tests with coverage
- `npm run test:ci` - Run tests for CI
- `npm run format` - Format code with Prettier
- `npm run format:check` - Check code formatting
- `npm run type-check` - Run TypeScript type checking

## 🧪 Testing

The project includes comprehensive testing setup with:

- **Jest** for test runner
- **React Testing Library** for component testing
- **Jest DOM** for DOM assertions
- **Coverage reporting** with thresholds

### Running Tests

```bash
# Run all tests
npm test

# Run tests in watch mode
npm run test:watch

# Run tests with coverage
npm run test:coverage

# Run tests for CI
npm run test:ci
```

### Test Structure

Tests are co-located with components:

```
src/
├── components/
│   └── dashboard/
│       └── Analytics/
│           ├── TotalRevenueCard.tsx
│           └── __tests__/
│               └── TotalRevenueCard.test.tsx
```

## 🔧 Configuration

### TypeScript Configuration

The project uses modern TypeScript configuration with:

- ES2022 target
- Strict mode enabled
- Enhanced path mapping
- Comprehensive type checking

### ESLint Configuration

Enhanced ESLint setup with:

- TypeScript support
- React hooks rules
- Import ordering
- Prettier integration

### Prettier Configuration

Consistent code formatting with:

- 100 character line width
- Single quotes
- ES5 trailing commas
- LF line endings

## 🚀 Performance Optimizations

### Lazy Loading

Components are lazy-loaded for better performance:

```typescript
import { withLazyLoading } from '@/components/common/PerformanceOptimized';

const LazyComponent = withLazyLoading(() => import('./HeavyComponent'));
```

### Virtual Scrolling

For large lists, use virtual scrolling:

```typescript
import { VirtualList } from '@/components/common/PerformanceOptimized';

<VirtualList
  items={largeDataSet}
  itemHeight={50}
  containerHeight={400}
  renderItem={(item, index) => <ListItem key={index} item={item} />}
/>
```

### Performance Monitoring

Track performance metrics:

```typescript
import { usePerformanceMonitoring } from '@/hooks/usePerformance';

const { measureRenderTime, measureAsyncOperation } = usePerformanceMonitoring();

// Measure component render time
const endMeasurement = measureRenderTime('MyComponent');

// Measure async operations
const result = await measureAsyncOperation('api-call', async () => {
  return await fetchData();
});
```

## 🔒 Security Features

### Input Sanitization

Sanitize user inputs to prevent XSS:

```typescript
import { useInputSanitization } from '@/hooks/useSecurity';

const { sanitizeInput, validateEmail, validatePassword } = useInputSanitization();

const cleanInput = sanitizeInput(userInput);
const isValidEmail = validateEmail(email);
const passwordValidation = validatePassword(password);
```

### CSRF Protection

Protect against CSRF attacks:

```typescript
import { useCSRFProtection } from '@/hooks/useSecurity';

const { csrfToken, validateCSRFToken } = useCSRFProtection();

// Include token in requests
const response = await fetch('/api/data', {
  headers: {
    'X-CSRF-Token': csrfToken,
  },
});
```

### Rate Limiting

Implement rate limiting:

```typescript
import { useRateLimit } from '@/hooks/useSecurity';

const { isAllowed, remainingRequests } = useRateLimit(10, 60000); // 10 requests per minute

if (isAllowed()) {
  // Proceed with operation
} else {
  // Show rate limit message
}
```

## 📊 Analytics & Monitoring

### Event Tracking

Track user interactions:

```typescript
import { useEventTracking } from '@/hooks/useAnalytics';

const { trackEvent, trackUserBehavior } = useEventTracking();

// Track custom events
trackEvent('button_click', { buttonId: 'submit-form' });

// Track user behavior
trackUserBehavior('click', 'navigation-menu', { menuItem: 'dashboard' });
```

### Performance Tracking

Monitor performance metrics:

```typescript
import { usePerformanceMonitoring } from '@/hooks/usePerformance';

const { trackPerformance } = usePerformanceMonitoring();

// Track custom performance metrics
trackPerformance('api_response_time', 150, { endpoint: '/api/users' });
```

### A/B Testing

Implement A/B testing:

```typescript
import { useABTesting } from '@/hooks/useAnalytics';

const { variant, trackConversion } = useABTesting('button-color', ['red', 'blue']);

// Track conversions
trackConversion('signup', 1);
```

## 🎨 Custom Hooks

### Form Management

```typescript
import { useForm } from '@/hooks/useForm';

const { formState, handleFieldChange, validateForm } = useForm({
  email: '',
  password: '',
});

// Handle field changes
<input onChange={handleFieldChange('email')} />
```

### Local Storage

```typescript
import { useLocalStorage } from '@/hooks/useCommon';

const [value, setValue] = useLocalStorage('user-preference', 'default');
```

### Debounced Search

```typescript
import { useDebouncedSearch } from '@/hooks/usePerformance';

const { results, loading, search } = useDebouncedSearch(searchFunction);

// Trigger search
search('query');
```

## 🚨 Error Handling

### Error Boundaries

Wrap components with error boundaries:

```typescript
import { ErrorBoundary } from '@/components/common';

<ErrorBoundary>
  <RiskyComponent />
</ErrorBoundary>
```

### Global Error Handler

The app includes a global error handler for unhandled errors:

```typescript
import { GlobalErrorHandler } from '@/components/common';

<GlobalErrorHandler>
  <App />
</GlobalErrorHandler>
```

## 📈 Best Practices

### Component Development

1. **Use TypeScript interfaces** for all props
2. **Implement proper error boundaries** for risky components
3. **Use React.memo** for expensive components
4. **Implement proper loading states** with skeletons
5. **Add comprehensive tests** for all components

### Performance

1. **Lazy load** heavy components
2. **Use virtual scrolling** for large lists
3. **Implement proper memoization** with useMemo and useCallback
4. **Monitor performance** with built-in tools
5. **Optimize bundle size** with proper imports

### Security

1. **Sanitize all user inputs**
2. **Implement CSRF protection**
3. **Use secure storage** for sensitive data
4. **Validate file uploads**
5. **Implement rate limiting**

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/new-feature`
3. Make your changes
4. Run tests: `npm test`
5. Run linting: `npm run lint`
6. Commit your changes: `git commit -m 'Add new feature'`
7. Push to the branch: `git push origin feature/new-feature`
8. Submit a pull request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🆘 Support

For support and questions:

- Create an issue in the repository
- Check the documentation
- Review the test files for examples

## 🔄 Migration Guide

If you're upgrading from an older version:

1. **Update dependencies**: Run `npm install` to get latest versions
2. **Update TypeScript**: The new config is stricter - fix any type errors
3. **Update components**: Use new modern patterns and hooks
4. **Run tests**: Ensure all tests pass with new setup
5. **Update imports**: Use new path mappings (@/ instead of relative paths)

## 📚 Additional Resources

- [Next.js Documentation](https://nextjs.org/docs)
- [Material-UI Documentation](https://mui.com/)
- [TypeScript Handbook](https://www.typescriptlang.org/docs/)
- [React Testing Library](https://testing-library.com/docs/react-testing-library/intro/)
- [Jest Documentation](https://jestjs.io/docs/getting-started)
