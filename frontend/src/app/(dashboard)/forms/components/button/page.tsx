'use client';

import Button from 'views/forms/components/button';
import { withErrorBoundary } from '@/components/enterprise/HOCs';

// ==============================|| PAGE ||============================== //

function ButtonPage() {
  return <Button />;
}

export default withErrorBoundary(ButtonPage);
