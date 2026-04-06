import { useCallback } from "react";
import type { Dispatch, SetStateAction } from "react";

import { postChatQuery } from "@/api/chat";
import type { ChatMessage, ChatResult, Document, ResultType } from "@/types";
import { normalizeMessageContent } from "@/utils";

type ToastFn = (opts: any) => void;

export function useConfirmationFlow({
  attachedItems,
  currentConversationId,
  setConfirmationStatus,
  setChatMessages,
  setContextDocuments,
  setCurrentConversationId,
  setIsLoading,
  toast,
}: {
  attachedItems: Array<{ type: string; data: any }>;
  currentConversationId: string | null;
  setConfirmationStatus: Dispatch<SetStateAction<Record<string, "pending" | "confirmed" | "rejected">>>;
  setChatMessages: Dispatch<SetStateAction<ChatMessage[]>>;
  setContextDocuments: Dispatch<SetStateAction<Document[]>>;
  setCurrentConversationId: Dispatch<SetStateAction<string | null>>;
  setIsLoading: Dispatch<SetStateAction<boolean>>;
  toast: ToastFn;
}) {
  const handleConfirmation = useCallback(
    async (messageId: string, confirmed: boolean) => {
      setConfirmationStatus((prev) => ({
        ...prev,
        [messageId]: confirmed ? "confirmed" : "rejected",
      }));

      const confirmationQuery = confirmed ? "Yes, confirm" : "No, cancel";

      const userMessage: ChatMessage = {
        id: Date.now().toString(),
        type: "user",
        content: confirmationQuery,
        timestamp: new Date().toISOString(),
      };

      setChatMessages((prev) => [...prev, userMessage]);
      setIsLoading(true);

      try {
        const { data } = await postChatQuery({
          query: confirmationQuery,
          userId: "demo-user",
          sessionId: "demo-session",
          conversationId: currentConversationId || undefined,
          attachments: attachedItems,
        });

        if (data.conversationId && !currentConversationId) {
          setCurrentConversationId(data.conversationId);
        }

        let messageContent: unknown = "";
        let result: ChatResult | undefined;
        let resultType: ResultType | undefined;

        if (data.result && data.result.sanitizedPayload) {
          messageContent = data.result.sanitizedPayload.message || "I processed your confirmation.";
          result = data.result;
          resultType = data.result.type;
        } else {
          messageContent = data.response || data.message || "I processed your confirmation.";
        }

        const aiMessage: ChatMessage = {
          id: (Date.now() + 1).toString(),
          type: "ai",
          content: normalizeMessageContent(messageContent),
          timestamp: new Date().toISOString(),
          result,
          resultType,
        };

        setChatMessages((prev) => [...prev, aiMessage]);

        if (data.documents && Array.isArray(data.documents)) {
          setContextDocuments(data.documents);
        }

        toast({
          title: confirmed ? "✅ Confirmed" : "❌ Rejected",
          description: confirmed ? "Your confirmation has been processed" : "Action cancelled",
        });
      } catch (error) {
        console.error("Error processing confirmation:", error);
        toast({
          title: "Error",
          description: "Failed to process confirmation. Please try again.",
          variant: "destructive",
        });

        const errorMessage: ChatMessage = {
          id: (Date.now() + 1).toString(),
          type: "ai",
          content: "I apologize, but I encountered an error processing your confirmation. Please try again.",
          timestamp: new Date().toISOString(),
          resultType: "INFORMATION_PROVIDED",
        };

        setChatMessages((prev) => [...prev, errorMessage]);
      } finally {
        setIsLoading(false);
      }
    },
    [
      attachedItems,
      currentConversationId,
      setChatMessages,
      setConfirmationStatus,
      setContextDocuments,
      setCurrentConversationId,
      setIsLoading,
      toast,
    ],
  );

  return { handleConfirmation } as const;
}

