import { useEffect, useRef } from "react";
import type { Dispatch, SetStateAction } from "react";

import { useMaxModeContextOptional } from "@/context";

import type { ChatMessage, Document } from "@/types";

export function useMaxModePersistence({
  chatMessages,
  setChatMessages,
  attachedItems,
  setAttachedItems,
  currentPosition,
  setCurrentPosition,
  currentMode,
  setCurrentMode,
  currentConversationId,
  setCurrentConversationId,
  contextDocuments,
  setContextDocuments,
}: {
  chatMessages: ChatMessage[];
  setChatMessages: Dispatch<SetStateAction<ChatMessage[]>>;
  attachedItems: Array<{ type: string; data: any }>;
  setAttachedItems: Dispatch<SetStateAction<Array<{ type: string; data: any }>>>;
  currentPosition: "landing" | "catalog" | "search" | "cart";
  setCurrentPosition: Dispatch<SetStateAction<"landing" | "catalog" | "search" | "cart">>;
  currentMode: "navigator" | "navigator_deep" | "cart_assistant" | "executor";
  setCurrentMode: Dispatch<SetStateAction<"navigator" | "navigator_deep" | "cart_assistant" | "executor">>;
  currentConversationId: string | null;
  setCurrentConversationId: Dispatch<SetStateAction<string | null>>;
  contextDocuments: Document[];
  setContextDocuments: Dispatch<SetStateAction<Document[]>>;
}) {
  const maxModeContext = useMaxModeContextOptional();
  const hasLoadedPersistedState = useRef(false);

  useEffect(() => {
    if (!maxModeContext || hasLoadedPersistedState.current) return;
    hasLoadedPersistedState.current = true;

    const persistedState = maxModeContext.loadPersistedState();
    if (persistedState) {
      if (persistedState.chatMessages.length > 0) setChatMessages(persistedState.chatMessages);
      if (persistedState.attachedItems.length > 0) setAttachedItems(persistedState.attachedItems);
      if (persistedState.currentPosition) setCurrentPosition(persistedState.currentPosition as "landing" | "catalog" | "search" | "cart");
      if (persistedState.currentMode) setCurrentMode(persistedState.currentMode as "navigator" | "navigator_deep" | "cart_assistant" | "executor");
      if (persistedState.conversationId) setCurrentConversationId(persistedState.conversationId);
      if (persistedState.contextDocuments && persistedState.contextDocuments.length > 0) setContextDocuments(persistedState.contextDocuments);
    }

    const pending = maxModeContext.getPendingAttachments();
    if (pending && pending.length > 0) {
      setAttachedItems((prev) => {
        const newItems = pending.filter(
          (p) =>
            !prev.some(
              (existing) =>
                existing.type === p.type && (existing.data.id === p.data.id || existing.data.sku === p.data.sku),
            ),
        );
        return [...prev, ...newItems];
      });
      maxModeContext.clearPendingAttachments();
    }
  }, [
    maxModeContext,
    setAttachedItems,
    setChatMessages,
    setContextDocuments,
    setCurrentConversationId,
    setCurrentMode,
    setCurrentPosition,
  ]);

  useEffect(() => {
    if (!maxModeContext) return;

    maxModeContext.updateMaxModeState({
      chatMessages,
      attachedItems,
      currentPosition: currentPosition as any,
      currentMode: currentMode as any,
      conversationId: currentConversationId,
      contextDocuments,
    });
  }, [attachedItems, chatMessages, contextDocuments, currentConversationId, currentMode, currentPosition, maxModeContext]);
}

