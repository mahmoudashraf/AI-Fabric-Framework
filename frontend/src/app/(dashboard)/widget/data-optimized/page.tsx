'use client';

import OptimizedWidgetData from 'views/widget/data-optimized';
import { withErrorBoundary } from '@/components/enterprise/HOCs';

// ==============================|| OPTIMIZED WIDGET DATA PAGE ||============================== //

/**
 * OPTIMIZED WIDGET DATA PAGE
 * 
 * This page demonstrates:
 * - Code splitting for heavy widgets
 * - Progressive loading with skeleton UI
 * - Error boundaries for resilience
 * - Better performance and user experience
 */

function OptimizedWidgetDataPage() {
  return <OptimizedWidgetData />;
}

export default withErrorBoundary(OptimizedWidgetDataPage);
