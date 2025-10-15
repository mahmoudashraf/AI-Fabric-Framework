'use client';

import Email from 'views/apps/mail';
import { withErrorBoundary } from '@/components/enterprise/HOCs';

// ==============================|| PAGE ||============================== //

function EmailPage() {
  return <Email />;
}

export default withErrorBoundary(EmailPage);
