'use client';

import DefaultDashboard from 'views/dashboard/default';
import { withErrorBoundary } from '@/components/enterprise/HOCs';

// ==============================|| PAGE ||============================== //

function DefaultDashboardPage() {
  return <DefaultDashboard />;
}

export default withErrorBoundary(DefaultDashboardPage);
