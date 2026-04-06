import { Composer } from "../Chat/Composer";

import type { MaxModeController } from "@/hooks/useMaxModeController";

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
    setCurrentMode,
    openDebugInspector,
    handleChatQuery,
  } = controller;

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
      onModeChange={setCurrentMode}
      onOpenDebug={() => openDebugInspector()}
      onSubmit={() => handleChatQuery()}
    />
  );
}

