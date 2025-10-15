'use client';

import AutoComplete from 'views/forms/components/autocomplete';
import { withErrorBoundary } from '@/components/enterprise/HOCs';

// ==============================|| PAGE ||============================== //

function AutoCompletePage() {
  return <AutoComplete />;
}

export default withErrorBoundary(AutoCompletePage);
