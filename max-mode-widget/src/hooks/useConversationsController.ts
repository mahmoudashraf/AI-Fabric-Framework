import { useCallback, useEffect, useRef, useState } from "react";

import type { Conversation } from "@/types";
import type { ChatMessage } from "@/types";

import { deleteConversation, getConversation, listConversations } from "@/api/conversations";

export function useConversationsController({
  isOpen,
  chatMessagesLength,
  currentConversationId,
  setCurrentConversationId,
  setChatMessages,
  setIsLoading,
  setAttachedItems,
  setSuggestions,
  setContextDocuments,
  toast,
}: {
  isOpen: boolean;
  chatMessagesLength: number;
  currentConversationId: string | null;
  setCurrentConversationId: (id: string | null) => void;
  setChatMessages: (updater: ChatMessage[] | ((prev: ChatMessage[]) => ChatMessage[])) => void;
  setIsLoading: (loading: boolean) => void;
  setAttachedItems: (updater: any) => void;
  setSuggestions: (suggestions: string[]) => void;
  setContextDocuments: (docs: any[]) => void;
  toast: (opts: any) => void;
}) {
  const [isConversationsOpen, setIsConversationsOpen] = useState(false);
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [isLoadingConversations, setIsLoadingConversations] = useState(false);
  const [isViewingOldConversation, setIsViewingOldConversation] = useState(false);
  const [oldConversationLocked, setOldConversationLocked] = useState(false);

  const loadConversations = useCallback(async () => {
    setIsLoadingConversations(true);
    try {
      const data = await listConversations("demo-user");
      setConversations(data);
    } catch (error) {
      console.error("Failed to load conversations:", error);
      toast({
        title: "Error",
        description: "Failed to load conversations",
        variant: "destructive",
      });
    } finally {
      setIsLoadingConversations(false);
    }
  }, [toast]);

  const startNewConversation = useCallback(() => {
    setChatMessages([]);
    setCurrentConversationId(null);
    setIsViewingOldConversation(false);
    setOldConversationLocked(false);
    setAttachedItems([]);
    setSuggestions([]);
    setContextDocuments([]);
    setIsConversationsOpen(false);
  }, [setAttachedItems, setChatMessages, setContextDocuments, setCurrentConversationId, setSuggestions]);

  const openConversation = useCallback(
    async (conversationId: string) => {
      try {
        setIsLoading(true);
        const data = await getConversation(conversationId, "demo-user");

        const messages: ChatMessage[] = [];
        data.turns.forEach((turn, idx) => {
          messages.push({
            id: `${conversationId}-user-${idx}`,
            type: "user",
            content: turn.userQuery,
            timestamp: turn.timestamp,
          });
          messages.push({
            id: `${conversationId}-ai-${idx}`,
            type: "ai",
            content: turn.aiResponse,
            timestamp: turn.timestamp,
          });
        });

        setChatMessages(messages);
        setCurrentConversationId(conversationId);
        setIsViewingOldConversation(true);
        const isLocked = data.status === "LOCKED" || data.status === "CLOSED";
        setOldConversationLocked(isLocked);
        setIsConversationsOpen(false);
        setAttachedItems([]);
        setSuggestions([]);

        toast({
          title: "Conversation Loaded",
          description: isLocked ? "This conversation is locked (read-only)" : "You can continue this conversation",
        });
      } catch (error) {
        console.error("Failed to open conversation:", error);
        toast({
          title: "Error",
          description: "Failed to load conversation",
          variant: "destructive",
        });
      } finally {
        setIsLoading(false);
      }
    },
    [setAttachedItems, setChatMessages, setCurrentConversationId, setIsLoading, setSuggestions, toast],
  );

  const handleDeleteConversation = useCallback(
    async (conversationId: string, e: any) => {
      e.stopPropagation();
      try {
        await deleteConversation(conversationId, "demo-user");
        setConversations((prev) => prev.filter((c) => c.id !== conversationId));
        if (currentConversationId === conversationId) {
          startNewConversation();
        }
        toast({
          title: "Deleted",
          description: "Conversation removed",
        });
      } catch (error) {
        console.error("Failed to delete conversation:", error);
        toast({
          title: "Error",
          description: "Failed to delete conversation",
          variant: "destructive",
        });
      }
    },
    [currentConversationId, startNewConversation, toast],
  );

  const openConversationsPanel = useCallback(() => {
    void loadConversations();
    setIsConversationsOpen(true);
  }, [loadConversations]);

  const hasLoadedRecentConversation = useRef(false);
  useEffect(() => {
    if (!isOpen || hasLoadedRecentConversation.current) return;
    if (chatMessagesLength > 0) {
      hasLoadedRecentConversation.current = true;
      return;
    }

    const loadRecentConversation = async () => {
      try {
        const convList = await listConversations("demo-user");
        if (convList.length === 0) return;

        const sorted = [...convList].sort((a, b) => {
          const dateA = new Date(a.lastInteractionAt || a.createdAt).getTime();
          const dateB = new Date(b.lastInteractionAt || b.createdAt).getTime();
          return dateB - dateA;
        });

        const recentUnlocked = sorted.find((c) => c.status !== "LOCKED" && c.status !== "CLOSED");
        if (!recentUnlocked) return;

        const data = await getConversation(recentUnlocked.id, "demo-user");
        const messages: ChatMessage[] = [];
        data.turns.forEach((turn, idx) => {
          messages.push({
            id: `${recentUnlocked.id}-user-${idx}`,
            type: "user",
            content: turn.userQuery,
            timestamp: turn.timestamp,
          });
          messages.push({
            id: `${recentUnlocked.id}-ai-${idx}`,
            type: "ai",
            content: turn.aiResponse,
            timestamp: turn.timestamp,
          });
        });

        setChatMessages(messages);
        setCurrentConversationId(recentUnlocked.id);
        setIsViewingOldConversation(false);
        setOldConversationLocked(false);
      } catch (error) {
        console.error("Failed to load recent conversation:", error);
      } finally {
        hasLoadedRecentConversation.current = true;
      }
    };

    void loadRecentConversation();
  }, [chatMessagesLength, isOpen, setChatMessages, setCurrentConversationId]);

  return {
    isConversationsOpen,
    setIsConversationsOpen,
    conversations,
    isLoadingConversations,
    isViewingOldConversation,
    oldConversationLocked,
    loadConversations,
    openConversation,
    handleDeleteConversation,
    startNewConversation,
    openConversationsPanel,
  } as const;
}

