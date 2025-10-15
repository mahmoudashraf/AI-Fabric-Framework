'use client';

import Calendar from 'views/apps/calendar';
import { withErrorBoundary } from '@/components/enterprise/HOCs';

// ==============================|| PAGE ||============================== //

function CalendarPage() {
  return <Calendar />;
}

export default withErrorBoundary(CalendarPage);
