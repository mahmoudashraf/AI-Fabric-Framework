import { ConversationHistoryPanel } from "../Conversations/ConversationHistoryPanel";
import { DebugInspectorPanel } from "../Panels/DebugInspectorPanel";

import type { MaxModeController } from "@/hooks/useMaxModeController";

export function MaxModeOverlays({ controller }: { controller: MaxModeController }) {
  const {
    isDebugModalOpen,
    debugEnabled,
    closeDebugInspector,
    selectedDebugMessage,
    lastRequestData,
    lastResponseData,
    isConversationsOpen,
    setIsConversationsOpen,
    conversations,
    isLoadingConversations,
    currentConversationId,
    startNewConversation,
    openConversation,
    handleDeleteConversation,
    identity,
  } = controller;

  return (
    <>
      {debugEnabled && (
        <DebugInspectorPanel
          isOpen={isDebugModalOpen}
          onClose={closeDebugInspector}
          selectedDebugMessage={selectedDebugMessage}
          lastRequestData={lastRequestData}
          lastResponseData={lastResponseData}
        />
      )}

      <ConversationHistoryPanel
        isOpen={isConversationsOpen}
        onClose={() => setIsConversationsOpen(false)}
        conversations={conversations}
        isLoadingConversations={isLoadingConversations}
        currentConversationId={currentConversationId}
        onStartNewConversation={startNewConversation}
        onOpenConversation={openConversation}
        onDeleteConversation={handleDeleteConversation}
        integrationMode={identity.integrationMode}
      />
    </>
  );
}
