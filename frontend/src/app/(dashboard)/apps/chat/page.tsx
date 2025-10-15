'use client';

import Chat from 'views/apps/chat';
import { withErrorBoundary } from '@/components/enterprise/HOCs';

// ==============================|| PAGE ||============================== //

function ChatPage() {
  return <Chat />;
}

export default withErrorBoundary(ChatPage);
