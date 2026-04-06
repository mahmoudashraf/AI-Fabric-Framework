import { useEffect, useRef } from "react";
import type { RefObject } from "react";

import type { ChatMessage, Document } from "@/types";

export function useMaxModeViewSync({
  isOpen,
  chatMessages,
  setChatMessages,
  latestMessageRef,
  chatInputRef,
  isAISearchOpen,
  setIsAISearchOpen,
  aiSearchRowRef,
  aiSearchButtonRef,
  isPanelVisible,
  contextPanelRef,
  contextPanelEndRef,
  setFocusedMessageId,
  setContextDocuments,
  setNewDocuments,
  setIsNewDocsPreviewOpen,
  setViewedDocumentIds,
}: {
  isOpen: boolean;
  chatMessages: ChatMessage[];
  setChatMessages: (updater: ChatMessage[] | ((prev: ChatMessage[]) => ChatMessage[])) => void;
  latestMessageRef: RefObject<HTMLDivElement>;
  chatInputRef: RefObject<HTMLTextAreaElement>;
  isAISearchOpen: boolean;
  setIsAISearchOpen: (open: boolean) => void;
  aiSearchRowRef: RefObject<HTMLDivElement>;
  aiSearchButtonRef: RefObject<HTMLDivElement>;
  isPanelVisible: boolean;
  contextPanelRef: RefObject<HTMLDivElement>;
  contextPanelEndRef: RefObject<HTMLDivElement>;
  setFocusedMessageId: (id: string | null) => void;
  setContextDocuments: (docs: Document[] | ((prev: Document[]) => Document[])) => void;
  setNewDocuments: (docs: Document[] | ((prev: Document[]) => Document[])) => void;
  setIsNewDocsPreviewOpen: (open: boolean) => void;
  setViewedDocumentIds: (ids: Set<string> | ((prev: Set<string>) => Set<string>)) => void;
}) {
  useEffect(() => {
    if (latestMessageRef.current && chatMessages.length > 0) {
      latestMessageRef.current.scrollIntoView({ behavior: "smooth", block: "start" });
    }
  }, [chatMessages, latestMessageRef]);

  useEffect(() => {
    if (chatInputRef.current && isOpen) {
      chatInputRef.current.focus();
    }
  }, [chatMessages, chatInputRef, isOpen]);

  useEffect(() => {
    if (!isAISearchOpen) return;

    const handleClickOutside = (event: MouseEvent) => {
      const target = event.target as Node;
      if (
        aiSearchRowRef.current &&
        !aiSearchRowRef.current.contains(target) &&
        aiSearchButtonRef.current &&
        !aiSearchButtonRef.current.contains(target)
      ) {
        setIsAISearchOpen(false);
      }
    };

    document.addEventListener("mousedown", handleClickOutside);
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
    };
  }, [aiSearchButtonRef, aiSearchRowRef, isAISearchOpen, setIsAISearchOpen]);

  const prevDocCountRef = useRef(0);
  useEffect(() => {
    const allDocs: Document[] = [];
    chatMessages.forEach((message) => {
      if (message.documents && message.documents.length > 0) allDocs.push(...message.documents);
    });

    const prevCount = prevDocCountRef.current;

    if (prevCount === 0 && allDocs.length > 0) {
      const existingIds = new Set(allDocs.map((doc) => doc.id));
      setViewedDocumentIds(existingIds);
    }

    if (allDocs.length > prevCount && prevCount > 0) {
      const newDocs = allDocs.slice(prevCount);
      const sortedNewDocs = [...newDocs].sort((a, b) => {
        const scoreA = a.score ?? a.similarity ?? 0;
        const scoreB = b.score ?? b.similarity ?? 0;
        return scoreB - scoreA;
      });
      setNewDocuments(sortedNewDocs);

      if (window.innerWidth < 768) {
        setIsNewDocsPreviewOpen(true);
      }
    }

    setContextDocuments(allDocs);
    prevDocCountRef.current = allDocs.length;

    if (allDocs.length > prevCount && contextPanelEndRef.current && isPanelVisible) {
      setTimeout(() => {
        contextPanelEndRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
      }, 100);
    }
  }, [chatMessages, contextPanelEndRef, isPanelVisible, setContextDocuments, setIsNewDocsPreviewOpen, setNewDocuments, setViewedDocumentIds]);

  useEffect(() => {
    const observer = new IntersectionObserver(
      (entries) => {
        let visibleMessageWithDocs: ChatMessage | null = null;

        for (const entry of entries) {
          if (!entry.isIntersecting) continue;
          const messageId = entry.target.getAttribute("data-message-id");
          const message = chatMessages.find((m) => m.id === messageId);
          if (message && message.documents && message.documents.length > 0) {
            visibleMessageWithDocs = message;
            break;
          }
        }

        if (!visibleMessageWithDocs) return;

        setFocusedMessageId(visibleMessageWithDocs.id);
        const firstDocElement = document.querySelector(`[data-doc-message-id="${visibleMessageWithDocs.id}"]`);
        if (firstDocElement && contextPanelRef.current) {
          const container = contextPanelRef.current;
          const elementTop = (firstDocElement as HTMLElement).offsetTop;
          const containerHeight = container.clientHeight;
          container.scrollTo({
            top: elementTop - containerHeight / 4,
            behavior: "smooth",
          });
        }
      },
      {
        root: null,
        rootMargin: "-50% 0px -50% 0px",
        threshold: 0,
      },
    );

    const messageElements = document.querySelectorAll("[data-message-id]");
    messageElements.forEach((el) => observer.observe(el));

    return () => observer.disconnect();
  }, [chatMessages, contextPanelRef, setFocusedMessageId]);

  useEffect(() => {
    if (!isOpen || chatMessages.length !== 0) return;

    const welcomeMessage: ChatMessage = {
      id: "welcome",
      type: "ai",
      content:
        "👋 Welcome to MAX Mode - your AI-powered shopping assistant! I can help you find products, manage orders, apply coupons, and much more. Try the quick actions above or just ask me anything!",
      timestamp: new Date().toISOString(),
      resultType: "INFORMATION_PROVIDED",
    };
    setChatMessages([welcomeMessage]);
  }, [isOpen, chatMessages.length, setChatMessages]);
}

