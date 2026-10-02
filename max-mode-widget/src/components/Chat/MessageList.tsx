import type { CSSProperties, RefObject } from "react";

import { AnimatePresence, motion } from "framer-motion";

import type { ChatMessage, CustomerAccountConnectAction, ResultType } from "@/types";
import type { MaxModePresentationResultReference } from "@/actionPresentation";
import type { AiStyles } from "./MessageBubble";
import { MessageBubble } from "./MessageBubble";
import { AIThinkingAnimation } from "./AIThinkingAnimation";

export function MessageList({
  containerClassName,
  containerStyle,
  messages,
  latestMessageRef,
  messagesEndRef,
  isLoading,
  getAiStyles,
  isPanelVisible,
  attachedItems,
  confirmationStatus,
  expandedActions,
  debugEnabled,
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
  onPresentationAsk,
  onAttachPresentationResult,
  onDetachPresentationResult,
  isPresentationResultAttached,
  onCustomerAccountConnect,
  onClarificationSubmit,
  contentClassName = "max-w-3xl",
}: {
  containerClassName: string;
  containerStyle?: CSSProperties;
  messages: ChatMessage[];
  latestMessageRef: RefObject<HTMLDivElement>;
  messagesEndRef: RefObject<HTMLDivElement>;
  isLoading: boolean;
  getAiStyles: (resultType?: ResultType, success?: boolean) => AiStyles;
  isPanelVisible: boolean;
  attachedItems: Array<{ type: string; data: any }>;
  confirmationStatus: Record<string, "confirmed" | "rejected" | undefined>;
  expandedActions: Record<string, number | undefined>;
  debugEnabled: boolean;
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
  onPresentationAsk: (query: string, references: readonly MaxModePresentationResultReference[]) => Promise<void> | void;
  onAttachPresentationResult: (reference: MaxModePresentationResultReference) => void;
  onDetachPresentationResult: (reference: MaxModePresentationResultReference) => void;
  isPresentationResultAttached: (referenceKey: string, sourceMessageId: string) => boolean;
  onCustomerAccountConnect: (action: CustomerAccountConnectAction) => void;
  onClarificationSubmit?: (action: string, parameters: Record<string, any>) => void;
  contentClassName?: string;
}) {
  return (
    <div className={containerClassName} style={containerStyle}>
      <div className={`${contentClassName} mx-auto space-y-4`}>
        <AnimatePresence mode="popLayout">
          {messages.map((message, index) => {
            const aiStyles = message.type === "ai" ? getAiStyles(message.resultType, message.success) : null;
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
                debugEnabled={debugEnabled}
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
                onPresentationAsk={onPresentationAsk}
                onAttachPresentationResult={onAttachPresentationResult}
                onDetachPresentationResult={onDetachPresentationResult}
                isPresentationResultAttached={isPresentationResultAttached}
                onCustomerAccountConnect={onCustomerAccountConnect}
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
