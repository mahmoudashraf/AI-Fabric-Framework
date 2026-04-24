import { useCallback } from "react";
import type { Dispatch, SetStateAction } from "react";

import { postChatQuery, resolvedChatQueryUrl } from "@/api/chat";
import { emitEvent } from "@/config";
import type { ChatMessage, ChatResult, DebugData, Document, ResultType } from "@/types";
import { normalizeMessageContent } from "@/utils";

export function useChatFlow({
  chatQuery,
  setChatQuery,
  chatMessagesLength,
  setChatMessages,
  attachedItems,
  searchCategory,
  currentConversationId,
  setCurrentConversationId,
  setIsLoading,
  setSuggestions,
  setCurrentPosition,
  setCurrentMode,
  setLastRequestData,
  setLastResponseData,
  setSelectedDebugMessage,
  currentPosition,
  currentMode,
  requestContext,
}: {
  chatQuery: string;
  setChatQuery: Dispatch<SetStateAction<string>>;
  chatMessagesLength: number;
  setChatMessages: Dispatch<SetStateAction<ChatMessage[]>>;
  attachedItems: Array<{ type: string; data: any }>;
  searchCategory: string | null;
  currentConversationId: string | null;
  setCurrentConversationId: Dispatch<SetStateAction<string | null>>;
  setIsLoading: Dispatch<SetStateAction<boolean>>;
  setSuggestions: Dispatch<SetStateAction<string[]>>;
  setCurrentPosition: Dispatch<SetStateAction<"landing" | "catalog" | "search" | "cart">>;
  setCurrentMode: Dispatch<SetStateAction<"navigator" | "navigator_deep" | "cart_assistant" | "executor">>;
  setLastRequestData: Dispatch<SetStateAction<any>>;
  setLastResponseData: Dispatch<SetStateAction<any>>;
  setSelectedDebugMessage: Dispatch<SetStateAction<ChatMessage | null>>;
  currentPosition: "landing" | "catalog" | "search" | "cart";
  currentMode: "navigator" | "navigator_deep" | "cart_assistant" | "executor";
  requestContext?: Record<string, any>;
}) {
  const handleChatQuery = useCallback(
    async (
      presetQuery?: string,
      actionPosition?: "landing" | "catalog" | "search" | "cart",
      actionMode?: "navigator" | "navigator_deep" | "cart_assistant" | "executor",
      extraRequestContext?: Record<string, any>,
    ) => {
      const query = presetQuery ?? chatQuery;
      if (!query.trim()) return;

      const currentSearchCategory = searchCategory;
      const aiSearchAttachment = attachedItems.find((item) => item.type === "ai-search");

      let apiQuery = query;
      if (currentSearchCategory) {
        apiQuery = `search relevance vector spaces for ${currentSearchCategory} ${query}`;
      } else if (aiSearchAttachment) {
        apiQuery = `search relevance vector spaces for ${aiSearchAttachment.data.category} ${query}`;
      }

      const userMessage: ChatMessage = {
        id: Date.now().toString(),
        type: "user",
        content: query,
        timestamp: new Date().toISOString(),
        attachedItems: attachedItems.length > 0 ? [...attachedItems] : undefined,
        searchCategory: currentSearchCategory || undefined,
      };

      setChatMessages((prev) => [...prev, userMessage]);
      setChatQuery("");
      setIsLoading(true);

      const currentAttachments = attachedItems.filter((item) => item.type !== "ai-search");
      setSuggestions([]);

      const hasAttachments = currentAttachments.length > 0;
      const isFirstQuery = chatMessagesLength === 0;

      let position: "landing" | "catalog" | "search" | "cart";
      let mode: "navigator" | "navigator_deep" | "cart_assistant" | "executor";

      if (actionPosition && actionMode) {
        position = actionPosition;
        mode = actionMode;
      } else if (hasAttachments) {
        position = "cart";
        mode = "cart_assistant";
      } else {
        position = actionPosition ?? currentPosition;
        mode = actionMode ?? currentMode;
      }

      // Search category tag forces navigator mode (position = search)
      const hasSearchTag = !!(currentSearchCategory || aiSearchAttachment);
      if (hasSearchTag) {
        position = "search";
        mode = "navigator";
      }

      // Deep mode is highest priority — overrides everything
      if (currentMode === "navigator_deep") {
        mode = "navigator_deep";
      }

      emitEvent("message:sent", {
        query,
        conversationId: currentConversationId,
        position,
        mode,
        attachmentsCount: attachedItems.length,
      });

      setCurrentPosition(position);
      setCurrentMode(mode);

      try {
        const attachmentsWithMetadata = currentAttachments.map((item) => {
          const contentParts: string[] = [];
          if (item.data.sku) contentParts.push(`SKU: ${item.data.sku}`);
          if (item.data.name) contentParts.push(item.data.name);
          if (item.data.title) contentParts.push(item.data.title);
          if (item.data.description) contentParts.push(item.data.description);
          if (item.data.content) contentParts.push(item.data.content);
          if (item.data.price) contentParts.push(`Price: ${item.data.price} ${item.data.currency || "USD"}`);
          if (item.data.category) contentParts.push(`Category: ${item.data.category}`);
          if (item.data.status) contentParts.push(`Status: ${item.data.status}`);
          if (item.data.orderId) contentParts.push(`Order ID: ${item.data.orderId}`);
          if (item.data.orderNumber) contentParts.push(`Order #${item.data.orderNumber}`);
          const contentText = contentParts.join(" | ");

          let vectorSpace = "product";
          if (item.type === "order") {
            vectorSpace = "order";
          } else if (item.type === "document") {
            const docCategory = item.data.metadata?.category?.toLowerCase();
            vectorSpace = docCategory === "order" ? "order" : "product";
          }

          const fullMetadata: Record<string, any> = {
            ...(item.data.metadata || {}),
            id: item.data.id,
            sku: item.data.sku,
            category: item.data.category || item.data.type,
            name: item.data.name,
            title: item.data.title,
            price: item.data.price,
            totalPrice: item.data.totalPrice,
            quantity: item.data.quantity,
            status: item.data.status,
            orderId: item.data.orderId,
            orderNumber: item.data.orderNumber,
            productName: item.data.productName,
            currency: item.data.currency,
            createdAt: item.data.createdAt,
            rating: item.data.rating,
            code: item.data.code,
            discountType: item.data.discountType,
            discountValue: item.data.discountValue,
            score: item.data.score,
            similarity: item.data.similarity,
          };

          Object.keys(fullMetadata).forEach((key) => {
            if (fullMetadata[key] === undefined) delete fullMetadata[key];
          });

          const rawId = item.data.id || item.data.orderId?.toString() || item.data.sku || Date.now().toString();
          const cleanId = String(rawId).replace(/[\[\]\(\)"'`]/g, "").trim();

          return {
            id: cleanId,
            vectorSpace,
            contentText,
            metadata: fullMetadata,
            source: item.type,
            url: String(item.data.url || "").replace(/[\[\]\(\)"'`]/g, "").trim(),
            imageUrl: String(item.data.imageUrl || item.data.metadata?.imageUrl || "").replace(/[\[\]\(\)"'`]/g, "").trim(),
          };
        });

        const explicitMode = mode !== "navigator" ? mode : undefined;

        const requestPayload = {
          query: apiQuery,
          conversationId: currentConversationId || undefined,
          position,
          mode: explicitMode,
          attachments: attachmentsWithMetadata.length > 0 ? attachmentsWithMetadata : undefined,
          ...(requestContext || {}),
          ...(extraRequestContext || {}),
        };

        setLastRequestData({
          endpoint: resolvedChatQueryUrl(),
          method: "POST",
          timestamp: new Date().toISOString(),
          payload: requestPayload,
        });
        setSelectedDebugMessage(null);

        const { data, status, durationMs } = await postChatQuery(requestPayload);

        setLastResponseData({
          timestamp: new Date().toISOString(),
          status,
          data,
          durationMs,
        });

        if (data.conversationId && !currentConversationId) {
          setCurrentConversationId(data.conversationId);
        }

        let messageContent: unknown = "";
        let result: ChatResult | undefined;
        let resultType: ResultType | undefined;
        let messageDocs: Document[] | undefined;

        if (data.result && data.result.sanitizedPayload) {
          messageContent = data.result.sanitizedPayload.message || "I processed your query successfully.";
          result = data.result;
          resultType = data.result.type;

          // Strip empty smart suggestions
          if (result.smartSuggestion) {
            const ss = result.smartSuggestion;
            if (!ss.response && !ss.query && (!ss.documents || ss.documents.length === 0)) {
              result = { ...result, smartSuggestion: undefined };
            }
          }

          if (resultType === "INFORMATION_PROVIDED" || resultType === "COMPOUND_HANDLED") {
            const rawDocs =
              data.result.data?.documents ||
              data.result.data?.ragResponse?.documents ||
              data.result.sanitizedPayload.data?.documents ||
              data.result.sanitizedPayload.data?.ragResponse?.documents ||
              [];

            const entityType =
              data.result.data?.ragResponse?.entityType || data.result.sanitizedPayload.data?.ragResponse?.entityType || "document";

            if (rawDocs.length > 0) {
              messageDocs = rawDocs.map((doc: any, idx: number) => {
                let title = doc.title;
                if (!title && doc.content) {
                  const parts = String(doc.content).split(" ");
                  const titleParts = parts
                    .slice(1, 8)
                    .filter(
                      (word: string) =>
                        !word.match(/^[A-Z]+-[A-Z]+-\\d+$/) && !word.match(/^\\d+\\.\\d+$/) && word.toLowerCase() !== "usd",
                    );
                  title = titleParts.join(" ") + (parts.length > 8 ? "..." : "");
                }

                let docType = doc.type || doc.metadata?.vectorSpace || doc.metadata?.classification;
                if (!docType) {
                  if (entityType.includes(",")) docType = entityType.split(",")[0].trim();
                  else docType = entityType;
                }

                return {
                  id: doc.id || `doc-${idx}`,
                  title: title || `Document ${idx + 1}`,
                  content: doc.content || "No content available",
                  type: docType,
                  metadata: doc.metadata || {},
                  score: doc.score,
                  similarity: doc.similarity,
                };
              });
            }
          }

          // Merge smart suggestion documents into messageDocs for the panel
          const smartSugDocs = data.result.smartSuggestion?.documents || data.result.data?.smartSuggestion?.documents;
          if (smartSugDocs && Array.isArray(smartSugDocs) && smartSugDocs.length > 0) {
            const existingIds = new Set((messageDocs || []).map((d) => d.id));
            const normalizedSugDocs: Document[] = smartSugDocs
              .filter((doc: any) => !existingIds.has(doc.id))
              .map((doc: any, idx: number) => ({
                id: doc.id || `suggestion-doc-${idx}`,
                title: doc.title || doc.content?.slice(0, 60) || `Suggestion Doc ${idx + 1}`,
                content: doc.content || "No content available",
                type: doc.type || doc.metadata?.vectorSpace || "document",
                metadata: { ...doc.metadata, fromSuggestion: true },
                score: doc.score,
                similarity: doc.similarity,
              }));
            messageDocs = [...(messageDocs || []), ...normalizedSugDocs];
          }
        } else {
          messageContent = data.response || data.message || "I processed your query successfully.";
        }

        emitEvent("message:received", {
          conversationId: data.conversationId || currentConversationId,
          resultType,
          success: data.result?.success ?? data.success ?? true,
          durationMs,
        });

        const messageId = (Date.now() + 1).toString();
        if (messageDocs) messageDocs = messageDocs.map((doc) => ({ ...doc, messageId }));

        const messageDebugData: DebugData = {
          request: {
            endpoint: resolvedChatQueryUrl(),
            method: "POST",
            timestamp: new Date().toISOString(),
            payload: requestPayload,
          },
          response: {
            timestamp: new Date().toISOString(),
            status,
            data,
            durationMs,
          },
        };

        const aiMessage: ChatMessage = {
          id: messageId,
          type: "ai",
          content: normalizeMessageContent(messageContent),
          timestamp: new Date().toISOString(),
          result,
          resultType,
          documents: messageDocs,
          debugData: messageDebugData,
        };

        setChatMessages((prev) => [...prev, aiMessage]);
      } catch (error) {
        emitEvent("error", {
          source: "chat-query",
          message: error instanceof Error ? error.message : "Unknown chat query failure",
        });
        const errorMessage: ChatMessage = {
          id: (Date.now() + 1).toString(),
          type: "ai",
          content: "Sorry, I encountered an error processing your request.",
          timestamp: new Date().toISOString(),
          resultType: "ERROR",
        };
        setChatMessages((prev) => [...prev, errorMessage]);
      } finally {
        setIsLoading(false);
      }
    },
    [
      attachedItems,
      chatMessagesLength,
      chatQuery,
      currentConversationId,
      currentMode,
      currentPosition,
      requestContext,
      searchCategory,
      setChatMessages,
      setChatQuery,
      setCurrentConversationId,
      setCurrentMode,
      setCurrentPosition,
      setIsLoading,
      setLastRequestData,
      setLastResponseData,
      setSelectedDebugMessage,
      setSuggestions,
    ],
  );

  return { handleChatQuery } as const;
}
