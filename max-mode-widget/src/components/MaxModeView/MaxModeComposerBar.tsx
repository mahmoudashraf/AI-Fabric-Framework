import { Composer } from "../Chat/Composer";

import type { MaxModeController } from "@/hooks/useMaxModeController";
import { getWidgetConfig } from "@/config";

export function MaxModeComposerBar({ controller }: { controller: MaxModeController }) {
  const {
    attachedItems,
    removeNonAiAttachmentByIndex,
    searchCategory,
    clearSearchCategory,
    removeAiSearchAttachment,
    suggestions,
    showSuggestions,
    setShowSuggestions,
    isLoadingSuggestions,
    dismissSuggestions,
    selectSuggestion,
    oldConversationLocked,
    startNewConversation,
    openConversationsPanel,
    chatQuery,
    setChatQuery,
    isInputFocused,
    setIsInputFocused,
    chatInputRef,
    isLoading,
    currentPosition,
    currentMode,
    allowedConversationModes,
    debugEnabled,
    conversationsEnabled,
    setCurrentMode,
    openDebugInspector,
    handleChatQuery,
  } = controller;
  const handoverConfig = getWidgetConfig().apiConfig.conversationHandover;

  return (
    <Composer
      attachedItems={attachedItems}
      onRemoveAttachment={removeNonAiAttachmentByIndex}
      searchCategory={searchCategory}
      onClearSearchCategory={clearSearchCategory}
      onRemoveAiSearch={removeAiSearchAttachment}
      suggestions={suggestions}
      showSuggestions={showSuggestions}
      isLoadingSuggestions={isLoadingSuggestions}
      onDismissSuggestions={dismissSuggestions}
      onShowSuggestions={() => setShowSuggestions(true)}
      onSuggestionSelect={selectSuggestion}
      oldConversationLocked={oldConversationLocked}
      onStartNewConversation={startNewConversation}
      onOpenHistory={openConversationsPanel}
      chatQuery={chatQuery}
      onChatQueryChange={(value) => setChatQuery(value)}
      isInputFocused={isInputFocused}
      onInputFocusChange={(focused) => setIsInputFocused(focused)}
      chatInputRef={chatInputRef}
      isLoading={isLoading}
      currentPosition={currentPosition}
      currentMode={currentMode}
      availableModes={controller.handoverBlocksAi ? [currentMode] : allowedConversationModes}
      onModeChange={setCurrentMode}
      debugEnabled={debugEnabled}
      conversationsEnabled={conversationsEnabled}
      onOpenDebug={() => openDebugInspector()}
      onSubmit={() => handleChatQuery()}
      handoverEnabled={controller.handoverEnabled}
      handoverState={controller.conversationControlState}
      handoverStatus={controller.conversationControl?.handoverStatus}
      handoverDisplayName={controller.conversationControl?.safeHumanDisplayName}
      handoverCanCancel={handoverConfig?.userCanCancelPending === true}
      handoverCanResume={handoverConfig?.userCanResumeAi === true}
      handoverBusy={controller.isHandoverBusy}
      onOpenHandover={controller.openHandover}
      onCancelHandover={() => void controller.cancelHandover()}
      onRetryHandover={() => void controller.retryHandover()}
      onResumeAi={() => void controller.resumeAi()}
    />
  );
}
