'use client';

import Cards from 'views/ui-elements/basic/cards';
import { withErrorBoundary } from '@/components/enterprise/HOCs';

// ==============================|| PAGE ||============================== //

function CardsPage() {
  return <Cards />;
}

export default withErrorBoundary(CardsPage);
