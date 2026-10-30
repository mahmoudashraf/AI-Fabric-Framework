import { useCallback, useEffect, useRef, useState } from "react";

import type { ChatMessage, Conversation, ConversationDetail } from "@/types";
import { toChatMessages } from "../conversationHistory";
import { WorkspaceHostError } from "../workspaceHost";

import { deleteConversation, getConversation, listConversations } from "@/api/conversations";
import { isPublicRuntimeSessionInvalidatedError } from "@/api/client";

export function useConversationsController({
  enabled = true,
  autoRestoreRecent = true,
  isPersistenceHydrated,
  isOpen,
  chatMessagesLength,
  currentConversationId,
  setCurrentConversationId,
  setChatMessages,
  setIsLoading,
  setAttachedItems,
  setSuggestions,
  setContextDocuments,
  beforeNewConversation,
  toast,
}: {
  enabled?: boolean;
  autoRestoreRecent?: boolean;
  isPersistenceHydrated: boolean;
  isOpen: boolean;
  chatMessagesLength: number;
  currentConversationId: string | null;
  setCurrentConversationId: (id: string | null) => void;
  setChatMessages: (updater: ChatMessage[] | ((prev: ChatMessage[]) => ChatMessage[])) => void;
  setIsLoading: (loading: boolean) => void;
  setAttachedItems: (updater: any) => void;
  setSuggestions: (suggestions: string[]) => void;
  setContextDocuments: (docs: any[]) => void;
  beforeNewConversation?: () => Promise<void> | void;
  toast: (opts: any) => void;
}) {
  const [isConversationsOpen, setIsConversationsOpen] = useState(false);
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [isLoadingConversations, setIsLoadingConversations] = useState(false);
  const [isViewingOldConversation, setIsViewingOldConversation] = useState(false);
  const [oldConversationLocked, setOldConversationLocked] = useState(false);
  const [loadedConversation, setLoadedConversation] = useState<ConversationDetail | null>(null);
  const historyRequest = useRef<{ revision: number; abort?: AbortController }>({ revision: 0 });
  useEffect(() => () => {
    historyRequest.current.revision++;
    historyRequest.current.abort?.abort();
  }, []);

  const loadConversations = useCallback(async () => {
    if (!enabled) {
      setConversations([]);
      return;
    }
    setIsLoadingConversations(true);
    try {
      const data = await listConversations();
      setConversations(data);
    } catch (error) {
      if (isPublicRuntimeSessionInvalidatedError(error)) {
        return;
      }
      console.error("Failed to load conversations:", error);
      toast({
        title: "Error",
        description: "Failed to load conversations",
        variant: "destructive",
      });
    } finally {
      setIsLoadingConversations(false);
    }
  }, [enabled, toast]);

  const resetConversation = useCallback(() => {
    historyRequest.current.revision++;
    historyRequest.current.abort?.abort();
    setLoadedConversation(null);
    setChatMessages([]);
    setCurrentConversationId(null);
    setIsViewingOldConversation(false);
    setOldConversationLocked(false);
    setAttachedItems([]);
    setSuggestions([]);
    setContextDocuments([]);
    setIsConversationsOpen(false);
  }, [setAttachedItems, setChatMessages, setContextDocuments, setCurrentConversationId, setSuggestions]);

  const startNewConversation = useCallback(async () => {
    try {
      setIsLoading(true);
      await beforeNewConversation?.();
      resetConversation();
    } catch (error) {
      toast({
        title: "Unable to start a new conversation",
        description: error instanceof Error ? error.message : "Workspace assignment could not be refreshed.",
        variant: "destructive",
      });
    } finally {
      setIsLoading(false);
    }
  }, [beforeNewConversation, resetConversation, setIsLoading, toast]);

  const loadConversation = useCallback(
    async (conversationId: string, refresh = false): Promise<ConversationDetail> => {
      const revision = ++historyRequest.current.revision;
      historyRequest.current.abort?.abort();
      const abort = new AbortController();
      const deadline = setTimeout(() => abort.abort(), 30_000);
      historyRequest.current.abort = abort;
      setIsLoading(true);
      if (!refresh) {
        setChatMessages([]);
        setCurrentConversationId(null);
        setLoadedConversation(null);
        setAttachedItems([]);
        setSuggestions([]);
        setContextDocuments([]);
      }
      try {
        const data = await getConversation(conversationId, abort.signal);
        if (revision !== historyRequest.current.revision || abort.signal.aborted) {
          throw new WorkspaceHostError("CONVERSATION_SUPERSEDED", "Another conversation selection superseded this request.");
        }
        if (data.id !== conversationId) {
          throw new WorkspaceHostError("CONVERSATION_MISMATCH", "The server returned a different conversation.");
        }

        const messages = toChatMessages(data, conversationId);

        setChatMessages(messages);
        setCurrentConversationId(conversationId);
        setLoadedConversation(data);
        setIsViewingOldConversation(true);
        const isLocked = data.status === "LOCKED"
          || data.status === "CLOSED"
          || data.controlState === "CLOSED";
        setOldConversationLocked(isLocked);
        setIsConversationsOpen(false);
        return data;
      } finally {
        clearTimeout(deadline);
        if (revision === historyRequest.current.revision) setIsLoading(false);
      }
    },
    [setAttachedItems, setChatMessages, setContextDocuments, setCurrentConversationId, setIsLoading, setSuggestions],
  );

  const openConversation = useCallback(async (conversationId: string) => {
    try {
      const data = await loadConversation(conversationId);
      toast({ title: "Conversation Loaded", description: data.status === "LOCKED" || data.status === "CLOSED" || data.controlState === "CLOSED"
        ? "This conversation is locked (read-only)" : "You can continue this conversation" });
    } catch (error) {
      if (isPublicRuntimeSessionInvalidatedError(error) || (error instanceof WorkspaceHostError && error.code === "CONVERSATION_SUPERSEDED")) return;
      toast({ title: "Error", description: "Failed to load conversation", variant: "destructive" });
    }
  }, [loadConversation, toast]);

  const handleDeleteConversation = useCallback(
    async (conversationId: string, e: any) => {
      e.stopPropagation();
      try {
        await deleteConversation(conversationId);
        setConversations((prev) => prev.filter((c) => c.id !== conversationId));
        if (currentConversationId === conversationId) {
          void startNewConversation();
        }
        toast({
          title: "Deleted",
          description: "Conversation removed",
        });
      } catch (error) {
        if (isPublicRuntimeSessionInvalidatedError(error)) {
          return;
        }
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
    if (!enabled) {
      setIsConversationsOpen(false);
      return;
    }
    void loadConversations();
    setIsConversationsOpen(true);
  }, [enabled, loadConversations]);

  const hasLoadedRecentConversation = useRef(false);
  useEffect(() => {
    if (!isPersistenceHydrated) return;
    if (!enabled || !autoRestoreRecent) {
      hasLoadedRecentConversation.current = true;
      return;
    }
    if (!isOpen || hasLoadedRecentConversation.current) return;
    if (chatMessagesLength > 0 || currentConversationId) {
      hasLoadedRecentConversation.current = true;
      return;
    }

    let cancelled = false;
    const loadRecentConversation = async () => {
      try {
        const convList = await listConversations();
        if (cancelled || convList.length === 0) return;

        const sorted = [...convList].sort((a, b) => {
          const dateA = new Date(a.lastInteractionAt || a.createdAt).getTime();
          const dateB = new Date(b.lastInteractionAt || b.createdAt).getTime();
          return dateB - dateA;
        });

        const recentUnlocked = sorted.find((c) => c.status !== "LOCKED" && c.status !== "CLOSED");
        if (!recentUnlocked) return;

        const data = await getConversation(recentUnlocked.id);
        // A turn, selection or unmount supersedes background history restoration.
        if (cancelled) return;
        const messages = toChatMessages(data, recentUnlocked.id);

        setChatMessages(messages);
        setCurrentConversationId(recentUnlocked.id);
        setIsViewingOldConversation(false);
        setOldConversationLocked(false);
      } catch (error) {
        if (isPublicRuntimeSessionInvalidatedError(error)) {
          return;
        }
        console.error("Failed to load recent conversation:", error);
      } finally {
        if (!cancelled) hasLoadedRecentConversation.current = true;
      }
    };

    void loadRecentConversation();
    return () => { cancelled = true; };
  }, [autoRestoreRecent, chatMessagesLength, currentConversationId, enabled, isOpen, isPersistenceHydrated, setChatMessages, setCurrentConversationId]);

  return {
    isConversationsOpen,
    setIsConversationsOpen,
    conversations,
    isLoadingConversations,
    isViewingOldConversation,
    oldConversationLocked,
    loadConversations,
    openConversation,
    loadConversation,
    loadedConversation,
    handleDeleteConversation,
    startNewConversation,
    resetConversation,
    openConversationsPanel,
  } as const;
}
