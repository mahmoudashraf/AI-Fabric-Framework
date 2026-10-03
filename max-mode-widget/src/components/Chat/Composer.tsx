import type { RefObject } from "react";
import { useLayoutEffect, useRef, useState } from "react";

import type { MaxModeMode, MaxModePosition } from "@/constants";

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
  availableModes,
  onModeChange,
  debugEnabled,
  conversationsEnabled,
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
  currentPosition: MaxModePosition;
  currentMode: MaxModeMode;
  availableModes: MaxModeMode[];
  onModeChange: (mode: MaxModeMode) => void;
  debugEnabled: boolean;
  conversationsEnabled: boolean;
  onOpenDebug: () => void;
  onSubmit: () => void;
}) {
  const [showAttachments, setShowAttachments] = useState(true);
  const composerStackRef = useRef<HTMLDivElement>(null);
  const hasAiSearch = Boolean(attachedItems.find((item) => item.type === "ai-search"));
  const nonAiAttachments = attachedItems.filter((item) => item.type !== "ai-search");
  const aiSearchCategory = (attachedItems.find((item) => item.type === "ai-search")?.data?.category as string | undefined) || null;

  useLayoutEffect(() => {
    const composerStack = composerStackRef.current;
    const maxModeView = composerStack?.closest<HTMLElement>("[data-max-mode-view]");
    if (!composerStack || !maxModeView || typeof ResizeObserver === "undefined") return;

    const updateInset = () => {
      const inset = Math.ceil(composerStack.getBoundingClientRect().height + 16);
      maxModeView.style.setProperty("--max-mode-composer-inset", `${inset}px`);
    };
    const observer = new ResizeObserver(updateInset);
    observer.observe(composerStack);
    updateInset();

    return () => {
      observer.disconnect();
      maxModeView.style.removeProperty("--max-mode-composer-inset");
    };
  }, []);

  return (
    <div
      ref={composerStackRef}
      className="pointer-events-none absolute bottom-0 left-0 right-0 z-50"
    >
      <div className="px-3 md:px-6">
        <div className="pointer-events-auto mx-auto max-w-3xl">
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

      <div
        className="pointer-events-auto border-t border-gray-200 bg-white p-3 md:p-6 dark:border-gray-800 dark:bg-gray-950"
        data-max-mode-composer-input-shell
      >
        <div className="mx-auto max-w-3xl">
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
            availableModes={availableModes}
            onModeChange={onModeChange}
            debugEnabled={debugEnabled}
            conversationsEnabled={conversationsEnabled}
            onOpenDebug={onOpenDebug}
            nonAiAttachmentsCount={nonAiAttachments.length}
            onSubmit={onSubmit}
          />
        </div>
      </div>
    </div>
  );
}
