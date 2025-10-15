'use client';

import React, { Suspense, lazy } from 'react';
import { Box, CircularProgress, Typography, Paper, Skeleton } from '@mui/material';

/**
 * PHASE 6: Performance Optimization - Lazy Loading Examples
 * 
 * This file demonstrates various lazy loading patterns for optimal performance:
 * 1. Basic lazy loading with Suspense
 * 2. Multiple lazy components
 * 3. Conditional lazy loading
 * 4. Lazy loading with error boundaries
 */

// ==================== EXAMPLE 1: Basic Lazy Loading ====================

// Lazy load a heavy component only when needed
export const HeavyChart = lazy(() => import('@/components/enterprise/performance/heavy-components/HeavyChart'));
export const HeavyDataGrid = lazy(() => import('@/components/enterprise/performance/heavy-components/HeavyDataGrid'));
export const HeavyImageGallery = lazy(() => import('@/components/enterprise/performance/heavy-components/HeavyImageGallery'));

/**
 * Custom loading fallback component
 */
const LoadingFallback = ({ message = 'Loading...' }: { message?: string }) => (
  <Box
    sx={{
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      justifyContent: 'center',
      minHeight: 200,
      gap: 2,
    }}
  >
    <CircularProgress />
    <Typography variant="body2" color="text.secondary">
      {message}
    </Typography>
  </Box>
);

/**
 * Skeleton loading fallback for better UX
 */
const SkeletonFallback = () => (
  <Box sx={{ p: 2 }}>
    <Skeleton variant="rectangular" height={40} sx={{ mb: 2 }} />
    <Skeleton variant="rectangular" height={200} sx={{ mb: 2 }} />
    <Skeleton variant="rectangular" height={100} />
  </Box>
);

// ==================== EXAMPLE 2: Lazy Component with Suspense ====================

export const LazyChartExample = () => (
  <Paper sx={{ p: 2, mb: 2 }}>
    <Typography variant="h6" gutterBottom>
      Lazy Loaded Chart Component
    </Typography>
    <Suspense fallback={<LoadingFallback message="Loading chart..." />}>
      <HeavyChart />
    </Suspense>
  </Paper>
);

// ==================== EXAMPLE 3: Multiple Lazy Components ====================

export const MultipleLazyComponents = () => (
  <Box>
    <Typography variant="h5" gutterBottom>
      Multiple Lazy Loaded Components
    </Typography>
    
    {/* Chart Section */}
    <Paper sx={{ p: 2, mb: 2 }}>
      <Typography variant="h6" gutterBottom>
        Charts
      </Typography>
      <Suspense fallback={<SkeletonFallback />}>
        <HeavyChart />
      </Suspense>
    </Paper>

    {/* Data Grid Section */}
    <Paper sx={{ p: 2, mb: 2 }}>
      <Typography variant="h6" gutterBottom>
        Data Grid
      </Typography>
      <Suspense fallback={<LoadingFallback message="Loading data grid..." />}>
        <HeavyDataGrid />
      </Suspense>
    </Paper>

    {/* Image Gallery Section */}
    <Paper sx={{ p: 2, mb: 2 }}>
      <Typography variant="h6" gutterBottom>
        Image Gallery
      </Typography>
      <Suspense fallback={<LoadingFallback message="Loading gallery..." />}>
        <HeavyImageGallery />
      </Suspense>
    </Paper>
  </Box>
);

// ==================== EXAMPLE 4: Conditional Lazy Loading ====================

interface ConditionalLazyProps {
  showChart?: boolean;
  showGrid?: boolean;
  showGallery?: boolean;
}

export const ConditionalLazyLoading = ({
  showChart = false,
  showGrid = false,
  showGallery = false,
}: ConditionalLazyProps) => (
  <Box>
    <Typography variant="h5" gutterBottom>
      Conditional Lazy Loading
    </Typography>
    <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
      Components are only loaded when the condition is true
    </Typography>

    {showChart && (
      <Paper sx={{ p: 2, mb: 2 }}>
        <Suspense fallback={<SkeletonFallback />}>
          <HeavyChart />
        </Suspense>
      </Paper>
    )}

    {showGrid && (
      <Paper sx={{ p: 2, mb: 2 }}>
        <Suspense fallback={<LoadingFallback />}>
          <HeavyDataGrid />
        </Suspense>
      </Paper>
    )}

    {showGallery && (
      <Paper sx={{ p: 2, mb: 2 }}>
        <Suspense fallback={<LoadingFallback />}>
          <HeavyImageGallery />
        </Suspense>
      </Paper>
    )}
  </Box>
);

// ==================== EXAMPLE 5: Route-based Lazy Loading ====================

// For use in routing configuration
export const lazyRoutes = {
  // Dashboard routes
  dashboard: lazy(() => import('@/app/(dashboard)/dashboard/default/page')),
  analytics: lazy(() => import('@/views/dashboard/analytics')),
  
  // Application routes
  customers: lazy(() => import('@/views/apps/customer/customer-list')),
  products: lazy(() => import('@/views/apps/e-commerce/product-list')),
  
  // User profile routes
  profile: lazy(() => import('@/views/apps/user/social-profile')),
};

// ==================== EXAMPLE 6: HOC for Lazy Loading ====================

/**
 * Higher-order component for adding lazy loading with consistent loading UI
 */
export function withLazyLoading<P extends object>(
  importFunc: () => Promise<{ default: React.ComponentType<P> }>,
  fallback?: React.ReactNode
) {
  const LazyComponent = lazy(importFunc);

  return function LazyWrapper(props: P) {
    return (
      <Suspense fallback={fallback || <LoadingFallback />}>
        <LazyComponent {...(props as any)} />
      </Suspense>
    );
  };
}

// Usage example:
// const LazyCustomerList = withLazyLoading(
//   () => import('./CustomerList'),
//   <SkeletonFallback />
// );

// ==================== EXAMPLE 7: Preloading Strategy ====================

/**
 * Preload a lazy component before it's needed
 * Useful for predictive loading based on user behavior
 */
export const preloadComponent = (
  importFunc: () => Promise<{ default: React.ComponentType<any> }>
) => {
  const component = lazy(importFunc);
  // Trigger the import
  importFunc();
  return component;
};

// Usage:
// // Preload on mouse enter
// <button onMouseEnter={() => preloadComponent(() => import('./HeavyModal'))}>
//   Open Modal
// </button>

// ==================== EXAMPLE 8: Named Exports Lazy Loading ====================

/**
 * Lazy load components with named exports
 */
export const LazyNamedExports = () => {
  const LazyAnalytics = lazy(() =>
    import('./components/Analytics').then((module) => ({
      default: module.Analytics,
    }))
  );

  const LazyReports = lazy(() =>
    import('@/components/enterprise/performance/components/Reports').then((module) => ({
      default: module.Reports,
    }))
  );

  return (
    <Box>
      <Suspense fallback={<LoadingFallback />}>
        <LazyAnalytics />
      </Suspense>
      
      <Suspense fallback={<LoadingFallback />}>
        <LazyReports />
      </Suspense>
    </Box>
  );
};

// ==================== BEST PRACTICES ====================

/**
 * LAZY LOADING BEST PRACTICES:
 * 
 * 1. Use lazy loading for:
 *    - Routes/pages
 *    - Heavy components (charts, grids, galleries)
 *    - Modal dialogs
 *    - Tabs that aren't immediately visible
 *    - Components below the fold
 * 
 * 2. Don't lazy load:
 *    - Small components
 *    - Components in critical rendering path
 *    - Components that appear on every page
 * 
 * 3. Always provide meaningful loading fallbacks
 * 
 * 4. Consider preloading for better UX:
 *    - On mouse enter for buttons/links
 *    - On route change prediction
 *    - Based on user behavior patterns
 * 
 * 5. Group related lazy imports to reduce chunks
 * 
 * 6. Use React.memo with lazy components to prevent unnecessary re-renders
 * 
 * 7. Monitor bundle sizes and chunk loading times
 */

export default {
  LazyChartExample,
  MultipleLazyComponents,
  ConditionalLazyLoading,
  withLazyLoading,
  preloadComponent,
  HeavyChart,
  HeavyDataGrid,
  HeavyImageGallery,
  lazyRoutes,
  LazyNamedExports,
};
