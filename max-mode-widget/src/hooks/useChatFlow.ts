import { useCallback } from "react";
import type { Dispatch, SetStateAction } from "react";

import { postChatQuery, resolvedChatQueryUrl } from "@/api/chat";
import { emitEvent } from "@/config";
import type { MaxModeHostRequestContextProvider } from "@/config";
import type { MaxModeMode } from "@/constants";
import type { ChatMessage, ChatResult, DebugData, Document, ResultType } from "@/types";
import { hasShopifyRequestContext, normalizeMessageContent, withRequestContext } from "@/utils";
import { summarizeShopifyMcpCatalogResult } from "@/shopifyMcpResults";
import { canonicalChatResult, extractChatResultMessage, extractCustomerAccountConnectAction } from "@/chatResult";

function firstString(...values: unknown[]) {
  for (const value of values) {
    if (typeof value !== "string") continue;
    const trimmed = value.trim();
    if (trimmed) return trimmed;
  }
  return undefined;
}

function objectValue(value: unknown): Record<string, any> {
  return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, any> : {};
}

function normalizeRuntimeDocument(doc: any, idx: number, options?: { entityType?: string; idPrefix?: string }): Document {
  const metadata = { ...objectValue(doc?.metadata) };
  let title = firstString(doc?.title, doc?.name, doc?.label, metadata.title, metadata.name);
  const content = firstString(doc?.content, doc?.contentText, doc?.text, doc?.summary, doc?.snippet, doc?.excerpt);

  if (!title && content) {
    const parts = content.split(" ");
    const titleParts = parts
      .slice(1, 8)
      .filter(
        (word: string) =>
          !word.match(/^[A-Z]+-[A-Z]+-\d+$/) && !word.match(/^\d+\.\d+$/) && word.toLowerCase() !== "usd",
      );
    title = titleParts.join(" ") + (parts.length > 8 ? "..." : "");
  }

  let docType = firstString(doc?.type, metadata.vectorSpace, metadata.classification);
  const entityType = options?.entityType || "document";
  if (!docType) {
    docType = entityType.includes(",") ? entityType.split(",")[0].trim() : entityType;
  }

  const imageUrl = firstString(
    doc?.imageUrl,
    doc?.image,
    doc?.imageSrc,
    doc?.featuredImage,
    metadata.imageUrl,
    metadata.image_url,
    metadata.featuredImage,
    metadata.featured_image,
    metadata.image,
  );
  const imageAltText = firstString(doc?.imageAltText, doc?.altText, metadata.imageAltText, metadata.altText, metadata.image_alt_text);
  const url = firstString(doc?.url, doc?.link, doc?.storefrontUrl, metadata.url, metadata.link, metadata.storefrontUrl);
  const storefrontUrl = firstString(doc?.storefrontUrl, metadata.storefrontUrl, url);
  const handle = firstString(doc?.handle, metadata.handle);

  if (imageUrl && !metadata.imageUrl) metadata.imageUrl = imageUrl;
  if (imageAltText && !metadata.imageAltText) metadata.imageAltText = imageAltText;
  if (url && !metadata.url) metadata.url = url;
  if (storefrontUrl && !metadata.storefrontUrl) metadata.storefrontUrl = storefrontUrl;
  if (handle && !metadata.handle) metadata.handle = handle;

  return {
    id: firstString(doc?.id, doc?.sourceId, doc?.url, doc?.link, title) || `${options?.idPrefix || "doc"}-${idx}`,
    title: title || `Document ${idx + 1}`,
    content: content || "No content available",
    type: docType || "document",
    metadata,
    imageUrl,
    imageAltText,
    url,
    storefrontUrl,
    handle,
    product_variant_id: firstString(doc?.product_variant_id, metadata.product_variant_id, metadata.variantId, metadata.variant_id),
    firstAvailableVariantTitle: firstString(doc?.firstAvailableVariantTitle, metadata.firstAvailableVariantTitle),
    priceRange: firstString(doc?.priceRange, metadata.priceRange, metadata.price, metadata.amount),
    availability: firstString(doc?.availability, metadata.availability, metadata.status),
    score: typeof doc?.score === "number" ? doc.score : typeof metadata.score === "number" ? metadata.score : undefined,
    similarity: typeof doc?.similarity === "number" ? doc.similarity : typeof metadata.similarity === "number" ? metadata.similarity : undefined,
  };
}

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
  requestContextProvider,
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
  setCurrentMode: Dispatch<SetStateAction<MaxModeMode>>;
  setLastRequestData: Dispatch<SetStateAction<any>>;
  setLastResponseData: Dispatch<SetStateAction<any>>;
  setSelectedDebugMessage: Dispatch<SetStateAction<ChatMessage | null>>;
  currentPosition: "landing" | "catalog" | "search" | "cart";
  currentMode: MaxModeMode;
  requestContext?: Record<string, any>;
  requestContextProvider?: MaxModeHostRequestContextProvider;
}) {
  const handleChatQuery = useCallback(
    async (
      presetQuery?: string,
      actionPosition?: "landing" | "catalog" | "search" | "cart",
      actionMode?: MaxModeMode,
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
      let mode: MaxModeMode;

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

      // Deep/Thinker mode is highest priority — overrides generic search routing.
      if (currentMode === "navigator_deep" || currentMode === "thinker_deep") {
        mode = currentMode;
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
          if (item.data.availability) contentParts.push(`Availability: ${item.data.availability}`);
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

          const sourceMetadata: Record<string, any> = { ...(item.data.metadata || {}) };
          delete sourceMetadata.productVariantId;
          delete sourceMetadata.firstAvailableVariantId;
          delete sourceMetadata.variantId;

          const fullMetadata: Record<string, any> = {
            ...sourceMetadata,
            id: item.data.id,
            sku: item.data.sku,
            category: item.data.category || item.data.type,
            name: item.data.name,
            title: item.data.title,
            price: item.data.price,
            availability: item.data.availability,
            product_variant_id: item.data.product_variant_id,
            firstAvailableVariantTitle: item.data.firstAvailableVariantTitle,
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

        let liveRequestContext: Record<string, any> | undefined;
        if (typeof requestContextProvider === "function") {
          try {
            const resolvedContext = await requestContextProvider();
            if (resolvedContext && typeof resolvedContext === "object" && !Array.isArray(resolvedContext)) {
              liveRequestContext = resolvedContext;
            }
          } catch (error) {
            console.warn("[MaxMode] Failed to resolve live request context:", error);
          }
        }

        const mergedRequestContext = {
          ...(requestContext || {}),
          ...(liveRequestContext || {}),
          ...(extraRequestContext || {}),
        };
        if (hasShopifyRequestContext(mergedRequestContext)) {
          mergedRequestContext.shopifyEffectiveConversationMode = mode;
        }
        const requestPayload = withRequestContext({
          query: apiQuery,
          conversationId: currentConversationId || undefined,
          position,
          mode,
          attachments: attachmentsWithMetadata.length > 0 ? attachmentsWithMetadata : undefined,
        }, mergedRequestContext);

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

        const customerAccountConnect = extractCustomerAccountConnectAction(data);
        const canonicalResult = canonicalChatResult(data);

        if (canonicalResult?.sanitizedPayload) {
          messageContent = extractChatResultMessage(data, "I processed your query successfully.");
          result = canonicalResult;
          resultType = canonicalResult.type;
          const resultData = result.sanitizedPayload.data || (data.result?.data ?? {});
          messageContent =
            summarizeShopifyMcpCatalogResult(resultData) ||
            summarizeShopifyMcpCatalogResult(data.result?.data) ||
            messageContent;

          // Strip empty smart suggestions
          if (result.smartSuggestion) {
            const ss = result.smartSuggestion;
            if (!ss.response && !ss.query && (!ss.documents || ss.documents.length === 0)) {
              result = { ...result, smartSuggestion: undefined };
            }
          }

          if (resultType === "INFORMATION_PROVIDED" || resultType === "COMPOUND_HANDLED") {
            const rawDocs =
              data.sources ||
              data.ragResponse?.documents ||
              data.documents ||
              data.result?.data?.documents ||
              data.result?.data?.ragResponse?.documents ||
              resultData.documents ||
              resultData.ragResponse?.documents ||
              [];

            const entityType =
              data.ragResponse?.entityType || data.result?.data?.ragResponse?.entityType || resultData.ragResponse?.entityType || "document";

            if (rawDocs.length > 0) {
              messageDocs = rawDocs.map((doc: any, idx: number) => normalizeRuntimeDocument(doc, idx, { entityType }));
            }
          }

          // Merge smart suggestion documents into messageDocs for the panel
          const smartSugDocs = result.smartSuggestion?.documents || data.result?.data?.smartSuggestion?.documents || resultData.smartSuggestion?.documents;
          if (smartSugDocs && Array.isArray(smartSugDocs) && smartSugDocs.length > 0) {
            const existingIds = new Set((messageDocs || []).map((d) => d.id));
            const normalizedSugDocs: Document[] = smartSugDocs
              .filter((doc: any) => !existingIds.has(doc.id))
              .map((doc: any, idx: number) => {
                const normalized = normalizeRuntimeDocument(doc, idx, { idPrefix: "suggestion-doc" });
                return {
                  ...normalized,
                  metadata: { ...normalized.metadata, fromSuggestion: true },
                };
              });
            messageDocs = [...(messageDocs || []), ...normalizedSugDocs];
          }
        } else {
          messageContent = extractChatResultMessage(data, "I processed your query successfully.");
        }

        emitEvent("message:received", {
          conversationId: data.conversationId || currentConversationId,
          resultType,
          success: result?.success ?? data.result?.success ?? data.success ?? true,
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
          success: result?.success ?? data.result?.success ?? data.success ?? true,
          customerAccountConnect,
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
      requestContextProvider,
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
