import type { RefObject } from "react";

import { AnimatePresence, motion } from "framer-motion";

import type { ChatMessage, ResultType } from "@/types";
import type { AiStyles } from "./MessageBubble";
import { MessageBubble } from "./MessageBubble";
import { AIThinkingAnimation } from "./AIThinkingAnimation";

export function MessageList({
  containerClassName,
  messages,
  latestMessageRef,
  messagesEndRef,
  isLoading,
  getAiStyles,
  isPanelVisible,
  attachedItems,
  confirmationStatus,
  expandedActions,
  onOpenDebug,
  onResendAction,
  onReattachItem,
  onOpenSourcesMobile,
  onOpenSourcesDesktop,
  onConfirm,
  onExpandActionResults,
  isItemAttached,
  onAttachActionResultItem,
  onNextStepClick,
  onClarificationSubmit,
}: {
  containerClassName: string;
  messages: ChatMessage[];
  latestMessageRef: RefObject<HTMLDivElement>;
  messagesEndRef: RefObject<HTMLDivElement>;
  isLoading: boolean;
  getAiStyles: (resultType?: ResultType) => AiStyles;
  isPanelVisible: boolean;
  attachedItems: Array<{ type: string; data: any }>;
  confirmationStatus: Record<string, "confirmed" | "rejected" | undefined>;
  expandedActions: Record<string, number | undefined>;
  onOpenDebug: (message: ChatMessage) => void;
  onResendAction: (fullMessage: string) => void;
  onReattachItem: (item: { type: string; data: any }, isAlreadyAttached: boolean) => void;
  onOpenSourcesMobile: (messageId: string) => void;
  onOpenSourcesDesktop: (messageId: string) => void;
  onConfirm: (messageId: string, confirmed: boolean, message: ChatMessage) => void;
  onExpandActionResults: (messageId: string, nextCount: number) => void;
  isItemAttached: (itemId: string) => boolean;
  onAttachActionResultItem: (item: any) => void;
  onNextStepClick: (query: string) => void;
  onClarificationSubmit?: (action: string, parameters: Record<string, any>) => void;
}) {
  return (
    <div className={containerClassName}>
      <div className="max-w-3xl mx-auto space-y-4">
        <AnimatePresence mode="popLayout">
          {messages.map((message, index) => {
            const aiStyles = message.type === "ai" ? getAiStyles(message.resultType) : null;
            const isLatest = index === messages.length - 1;

            return (
              <MessageBubble
                key={message.id}
                message={message}
                isLatest={isLatest}
                latestMessageRef={latestMessageRef}
                aiStyles={aiStyles}
                isPanelVisible={isPanelVisible}
                attachedItems={attachedItems}
                confirmationStatus={confirmationStatus}
                expandedCount={expandedActions[message.id] || 4}
                onOpenDebug={onOpenDebug}
                onResendAction={onResendAction}
                onReattachItem={onReattachItem}
                onOpenSourcesMobile={onOpenSourcesMobile}
                onOpenSourcesDesktop={onOpenSourcesDesktop}
                onConfirm={onConfirm}
                onExpandActionResults={onExpandActionResults}
                isItemAttached={isItemAttached}
                onAttachActionResultItem={onAttachActionResultItem}
                onNextStepClick={onNextStepClick}
                onClarificationSubmit={onClarificationSubmit}
              />
            );
          })}
        </AnimatePresence>

        {isLoading && <AIThinkingAnimation messages={messages} attachedItems={attachedItems} />}

        <div ref={messagesEndRef} />
      </div>
    </div>
  );
}

