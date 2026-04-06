import type { RefObject } from "react";
import { useState } from "react";

import { LockedConversationBanner } from "./Composer/LockedConversationBanner";
import { AttachmentsRow } from "./Composer/AttachmentsRow";
import { ComposerInputRow } from "./Composer/ComposerInputRow";
import { SuggestionsPanel } from "./Composer/SuggestionsPanel";
import type { AttachedItem } from "./types";

export type { AttachedItem } from "./types";

export function Composer({
  attachedItems,
  onRemoveAttachment,
  searchCategory,
  onClearSearchCategory,
  onRemoveAiSearch,
  suggestions,
  showSuggestions,
  isLoadingSuggestions,
  onDismissSuggestions,
  onShowSuggestions,
  onSuggestionSelect,
  oldConversationLocked,
  onStartNewConversation,
  onOpenHistory,
  chatQuery,
  onChatQueryChange,
  isInputFocused,
  onInputFocusChange,
  chatInputRef,
  isLoading,
  currentPosition,
  currentMode,
  onModeChange,
  onOpenDebug,
  onSubmit,
}: {
  attachedItems: AttachedItem[];
  onRemoveAttachment: (filteredIndex: number) => void;
  searchCategory: string | null;
  onClearSearchCategory: () => void;
  onRemoveAiSearch: () => void;
  suggestions: string[];
  showSuggestions: boolean;
  isLoadingSuggestions: boolean;
  onDismissSuggestions: () => void;
  onShowSuggestions: () => void;
  onSuggestionSelect: (suggestion: string) => void;
  oldConversationLocked: boolean;
  onStartNewConversation: () => void;
  onOpenHistory: () => void;
  chatQuery: string;
  onChatQueryChange: (value: string) => void;
  isInputFocused: boolean;
  onInputFocusChange: (focused: boolean) => void;
  chatInputRef: RefObject<HTMLTextAreaElement>;
  isLoading: boolean;
  currentPosition: "landing" | "catalog" | "search" | "cart";
  currentMode: "navigator" | "navigator_deep" | "cart_assistant" | "executor";
  onModeChange: (mode: "navigator" | "navigator_deep" | "cart_assistant" | "executor") => void;
  onOpenDebug: () => void;
  onSubmit: () => void;
}) {
  const [showAttachments, setShowAttachments] = useState(true);
  const hasAiSearch = Boolean(attachedItems.find((item) => item.type === "ai-search"));
  const nonAiAttachments = attachedItems.filter((item) => item.type !== "ai-search");
  const aiSearchCategory = (attachedItems.find((item) => item.type === "ai-search")?.data?.category as string | undefined) || null;

  return (
    <>
      {/* Floating attachments and suggestions above input box */}
      <div className="absolute bottom-28 md:bottom-34 left-0 right-0 z-30 px-3 md:px-6 pointer-events-none">
        <div className="max-w-3xl mx-auto pointer-events-auto">
          <AttachmentsRow
            items={nonAiAttachments}
            showAttachments={showAttachments}
            onRemoveAttachment={onRemoveAttachment}
            onDismissAttachments={() => setShowAttachments(false)}
            onShowAttachments={() => setShowAttachments(true)}
          />

          <SuggestionsPanel
            suggestions={suggestions}
            showSuggestions={showSuggestions}
            isLoadingSuggestions={isLoadingSuggestions}
            onDismissSuggestions={onDismissSuggestions}
            onShowSuggestions={onShowSuggestions}
            onSuggestionSelect={onSuggestionSelect}
          />
        </div>
      </div>

      {/* Fixed input box at bottom */}
      <div className="absolute bottom-0 left-0 right-0 z-50 bg-white/80 dark:bg-gray-900/80 backdrop-blur-xl border-t-2 border-purple-500/30 p-3 md:p-6">
        <div className="max-w-3xl mx-auto">
          {oldConversationLocked && <LockedConversationBanner onStartNewConversation={onStartNewConversation} />}

          <ComposerInputRow
            searchCategory={searchCategory}
            hasAiSearch={hasAiSearch}
            aiSearchCategory={aiSearchCategory}
            onClearSearchCategory={onClearSearchCategory}
            onRemoveAiSearch={onRemoveAiSearch}
            oldConversationLocked={oldConversationLocked}
            onOpenHistory={onOpenHistory}
            chatQuery={chatQuery}
            onChatQueryChange={onChatQueryChange}
            isInputFocused={isInputFocused}
            onInputFocusChange={onInputFocusChange}
            chatInputRef={chatInputRef}
            isLoading={isLoading}
            currentPosition={currentPosition}
            currentMode={currentMode}
            onModeChange={onModeChange}
            onOpenDebug={onOpenDebug}
            nonAiAttachmentsCount={nonAiAttachments.length}
            onSubmit={onSubmit}
          />
        </div>
      </div>
    </>
  );
}
