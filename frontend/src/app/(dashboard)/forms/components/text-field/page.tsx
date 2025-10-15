'use client';

import TextField from 'views/forms/components/text-field';
import { withErrorBoundary } from '@/components/enterprise/HOCs';

// ==============================|| PAGE ||============================== //

function TextFieldPage() {
  return <TextField />;
}

export default withErrorBoundary(TextFieldPage);
