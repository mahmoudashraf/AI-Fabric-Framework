import { useEffect, useRef, useState } from "react";
import type { Dispatch, SetStateAction } from "react";

import { useMaxModeContextOptional } from "@/context";

import type { MaxModeMode, MaxModePosition } from "@/constants";
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
  hostInitialAttachments,
}: {
  chatMessages: ChatMessage[];
  setChatMessages: Dispatch<SetStateAction<ChatMessage[]>>;
  attachedItems: Array<{ type: string; data: any }>;
  setAttachedItems: Dispatch<SetStateAction<Array<{ type: string; data: any }>>>;
  currentPosition: MaxModePosition;
  setCurrentPosition: Dispatch<SetStateAction<MaxModePosition>>;
  currentMode: MaxModeMode;
  setCurrentMode: Dispatch<SetStateAction<MaxModeMode>>;
  currentConversationId: string | null;
  setCurrentConversationId: Dispatch<SetStateAction<string | null>>;
  contextDocuments: Document[];
  setContextDocuments: Dispatch<SetStateAction<Document[]>>;
  hostInitialAttachments: Array<{ type: string; data: any }>;
}) {
  const maxModeContext = useMaxModeContextOptional();
  const hasLoadedPersistedState = useRef(false);
  const [hasHydratedPersistedState, setHasHydratedPersistedState] = useState(false);

  useEffect(() => {
    if (hasLoadedPersistedState.current) return;
    hasLoadedPersistedState.current = true;
    if (!maxModeContext) {
      setHasHydratedPersistedState(true);
      return;
    }

    const persistedState = maxModeContext.loadPersistedState();
    if (persistedState) {
      setChatMessages(persistedState.chatMessages);
      setAttachedItems(persistedState.attachedItems);
      setCurrentPosition(persistedState.currentPosition as MaxModePosition);
      setCurrentMode(persistedState.currentMode as MaxModeMode);
      setCurrentConversationId(persistedState.conversationId);
      setContextDocuments(persistedState.contextDocuments || []);
    }
    if (!persistedState && hostInitialAttachments.length > 0) {
      setAttachedItems((prev) => {
        const newItems = hostInitialAttachments.filter(
          (hostAttachment) =>
            !prev.some(
              (existing) =>
                existing.type === hostAttachment.type &&
                (existing.data.id === hostAttachment.data.id || existing.data.sku === hostAttachment.data.sku),
            ),
        );
        return newItems.length > 0 ? [...prev, ...newItems] : prev;
      });
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
    setHasHydratedPersistedState(true);
  }, [
    maxModeContext,
    setAttachedItems,
    setChatMessages,
    setContextDocuments,
    setCurrentConversationId,
    setCurrentMode,
    setCurrentPosition,
    hostInitialAttachments,
  ]);

  useEffect(() => {
    if (!maxModeContext || !hasHydratedPersistedState) return;

    maxModeContext.updateMaxModeState({
      chatMessages,
      attachedItems,
      currentPosition: currentPosition as any,
      currentMode: currentMode as any,
      conversationId: currentConversationId,
      contextDocuments,
    });
  }, [
    attachedItems,
    chatMessages,
    contextDocuments,
    currentConversationId,
    currentMode,
    currentPosition,
    hasHydratedPersistedState,
    maxModeContext,
  ]);

  return hasHydratedPersistedState;
}
