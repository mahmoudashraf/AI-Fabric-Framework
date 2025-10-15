'use client';

import DataWidget from 'views/widget/data';
import { withErrorBoundary } from '@/components/enterprise/HOCs';

// ==============================|| PAGE ||============================== //

function DataWidgetPage() {
  return <DataWidget />;
}

export default withErrorBoundary(DataWidgetPage);
