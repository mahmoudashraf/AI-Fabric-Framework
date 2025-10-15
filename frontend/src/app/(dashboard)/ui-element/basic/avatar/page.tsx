'use client';

import Avatar from 'views/ui-elements/basic/avatar';
import { withErrorBoundary } from '@/components/enterprise/HOCs';

// ==============================|| PAGE ||============================== //

function AvatarPage() {
  return <Avatar />;
}

export default withErrorBoundary(AvatarPage);
