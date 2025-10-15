'use client';

import OptimizedDashboard from '@/views/dashboard/default-optimized';
import { withErrorBoundary } from '@/components/enterprise/HOCs';

// ==============================|| OPTIMIZED DEFAULT DASHBOARD PAGE ||============================== //

/**
 * OPTIMIZED DEFAULT DASHBOARD PAGE
 * 
 * This page demonstrates:
 * - Code splitting for heavy charts
 * - Progressive loading with skeleton UI
 * - Error boundaries for resilience
 * - Better performance and user experience
 */

function OptimizedDefaultDashboardPage() {
  return <OptimizedDashboard />;
}

export default withErrorBoundary(OptimizedDefaultDashboardPage);
