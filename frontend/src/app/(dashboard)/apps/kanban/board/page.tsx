'use client';

import KanbanBoard from 'views/apps/kanban/board';
import { withErrorBoundary } from '@/components/enterprise/HOCs';

// ==============================|| PAGE ||============================== //

function KanbanBoardPage() {
  return <KanbanBoard />;
}

export default withErrorBoundary(KanbanBoardPage);
