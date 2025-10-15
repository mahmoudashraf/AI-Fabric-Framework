'use client';

import ChartWidget from 'views/widget/chart';
import { withErrorBoundary } from '@/components/enterprise/HOCs';

// ==============================|| PAGE ||============================== //

function ChartWidgetPage() {
  return <ChartWidget />;
}

export default withErrorBoundary(ChartWidgetPage);
