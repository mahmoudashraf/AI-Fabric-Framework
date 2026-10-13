import { ConversationHistoryPanel } from "../Conversations/ConversationHistoryPanel";
import { DebugInspectorPanel } from "../Panels/DebugInspectorPanel";
import { ConversationHandoverDialog } from "../Handover/ConversationHandoverDialog";

import { getWidgetConfig } from "@/config";

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
  const handoverConfig = getWidgetConfig().apiConfig.conversationHandover;

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

      {controller.handoverEnabled && (
        <ConversationHandoverDialog
          open={controller.isHandoverDialogOpen}
          onOpenChange={controller.setIsHandoverDialogOpen}
          state={controller.conversationControlState}
          handoverStatus={controller.conversationControl?.handoverStatus}
          displayName={controller.conversationControl?.safeHumanDisplayName}
          fields={controller.handoverIntakeSchema?.fields || handoverConfig?.intake?.fields || []}
          anonymousAllowed={controller.handoverIntakeSchema?.anonymousAllowed ?? handoverConfig?.intake?.anonymousAllowed !== false}
          canCancel={handoverConfig?.userCanCancelPending === true}
          canResume={handoverConfig?.userCanResumeAi === true}
          busy={controller.isHandoverBusy}
          onSubmit={(values, consentedFields, anonymous) => {
            void controller.submitHandover(values, consentedFields, anonymous);
          }}
          onCancel={() => void controller.cancelHandover()}
          onRetry={() => void controller.retryHandover()}
          onResume={() => void controller.resumeAi()}
        />
      )}
    </>
  );
}
