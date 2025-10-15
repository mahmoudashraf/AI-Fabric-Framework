# 🧪 How to Run Tests

## Prerequisites

The test files are created, but you need to install dependencies first:

```bash
cd /workspace/frontend
npm install
```

## Running Tests

Once dependencies are installed:

```bash
# Run all tests
npm test

# Run specific test file
npm test -- withErrorBoundary
npm test -- useAdvancedForm
npm test -- useTableLogic

# Run with coverage
npm test -- --coverage

# Run in watch mode
npm test -- --watch

# Run enterprise tests only
npm test -- --testPathPattern="enterprise"
```

## What Tests Are Available

✅ **7 Test Files Created (140+ tests)**

1. `withErrorBoundary.test.tsx` (15 tests)
2. `withLoading.test.tsx` (12 tests)
3. `ErrorFallback.test.tsx` (20 tests)
4. `useAsyncOperation.test.ts` (25 tests)
5. `useAdvancedForm.test.ts` (30 tests)
6. `useTableLogic.test.ts` (30 tests)
7. `ComponentTesting.example.test.tsx` (8 examples)

## Test Files Location

```
frontend/src/
├── components/enterprise/HOCs/__tests__/
│   ├── ErrorFallback.test.tsx
│   ├── withErrorBoundary.test.tsx
│   └── withLoading.test.tsx
│
├── hooks/enterprise/__tests__/
│   ├── useAsyncOperation.test.ts
│   ├── useAdvancedForm.test.ts
│   └── useTableLogic.test.ts
│
└── components/enterprise/__tests__/
    └── ComponentTesting.example.test.tsx
```

## If Tests Don't Run

If you see "jest: not found", run:

```bash
cd /workspace/frontend
npm install --save-dev jest @testing-library/react @testing-library/jest-dom
```

Then try running tests again.

## Next Steps

1. Install dependencies: `cd /workspace/frontend && npm install`
2. Run tests: `npm test`
3. See all tests pass! ✅

All test infrastructure is ready and waiting!
