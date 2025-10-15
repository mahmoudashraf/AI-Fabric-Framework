'use client';

import PerformanceComparison from '@/views/performance-comparison';
import { withErrorBoundary } from '@/components/enterprise/HOCs';

// ==============================|| PERFORMANCE COMPARISON PAGE ||============================== //

/**
 * PERFORMANCE COMPARISON PAGE
 * 
 * This page demonstrates the performance improvements achieved through:
 * - Code splitting
 * - Lazy loading
 * - Progressive loading
 * - Optimized bundle sizes
 */

function PerformanceComparisonPage() {
  return <PerformanceComparison />;
}

export default withErrorBoundary(PerformanceComparisonPage);
