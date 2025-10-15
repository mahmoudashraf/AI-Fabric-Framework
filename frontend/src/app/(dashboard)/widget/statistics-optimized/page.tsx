'use client';

import OptimizedWidgetStatistics from '@/views/widget/statistics-optimized';
import { withErrorBoundary } from '@/components/enterprise/HOCs';

// ==============================|| OPTIMIZED WIDGET STATISTICS PAGE ||============================== //

/**
 * OPTIMIZED WIDGET STATISTICS PAGE
 * 
 * This page demonstrates:
 * - Code splitting for heavy statistics cards
 * - Progressive loading with skeleton UI
 * - Error boundaries for resilience
 * - Better performance and user experience
 */

function OptimizedWidgetStatisticsPage() {
  return <OptimizedWidgetStatistics />;
}

export default withErrorBoundary(OptimizedWidgetStatisticsPage);
