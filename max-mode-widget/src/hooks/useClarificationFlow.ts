import { useCallback } from "react";
import type { Dispatch, SetStateAction } from "react";

import { postChatQuery } from "@/api/chat";
import type { ChatMessage, ChatResult, Document, ResultType } from "@/types";
import { normalizeMessageContent } from "@/utils";

type ToastFn = (opts: any) => void;

export function useClarificationFlow({
  attachedItems,
  currentConversationId,
  setChatMessages,
  setContextDocuments,
  setCurrentConversationId,
  setIsLoading,
  toast,
}: {
  attachedItems: Array<{ type: string; data: any }>;
  currentConversationId: string | null;
  setChatMessages: Dispatch<SetStateAction<ChatMessage[]>>;
  setContextDocuments: Dispatch<SetStateAction<Document[]>>;
  setCurrentConversationId: Dispatch<SetStateAction<string | null>>;
  setIsLoading: Dispatch<SetStateAction<boolean>>;
  toast: ToastFn;
}) {
  const handleClarificationSubmit = useCallback(
    async (action: string, parameters: Record<string, any>) => {
      const paramStr = Object.entries(parameters)
        .map(([key, value]) => `${key}: ${value}`)
        .join(", ");
      const query = `Proceed with ${action.replace(/_/g, " ")} using: ${paramStr}`;

      const userMessage: ChatMessage = {
        id: Date.now().toString(),
        type: "user",
        content: query,
        timestamp: new Date().toISOString(),
      };

      setChatMessages((prev) => [...prev, userMessage]);
      setIsLoading(true);

      try {
        const { data } = await postChatQuery({
          query,
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
          messageContent = data.result.sanitizedPayload.message || "Action processed.";
          result = data.result;
          resultType = data.result.type;
        } else {
          messageContent = data.response || data.message || "Action processed.";
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
      } catch (error) {
        console.error("Error processing clarification:", error);
        toast({
          title: "Error",
          description: "Failed to submit clarification. Please try again.",
          variant: "destructive",
        });

        const errorMessage: ChatMessage = {
          id: (Date.now() + 1).toString(),
          type: "ai",
          content: "I apologize, but I encountered an error processing your clarification. Please try again.",
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
      setContextDocuments,
      setCurrentConversationId,
      setIsLoading,
      toast,
    ],
  );

  return { handleClarificationSubmit } as const;
}
