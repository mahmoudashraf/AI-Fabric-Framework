import { useCallback } from "react";
import type { RefObject } from "react";

import type { Document } from "@/types";

type ToastFn = (opts: any) => void;

export function useAttachmentsController({
  attachedItems,
  setAttachedItems,
  setCollectingItem,
  setCurrentPosition,
  setCurrentMode,
  chatInputRef,
  toast,
}: {
  attachedItems: Array<{ type: string; data: any }>;
  setAttachedItems: (updater: any) => void;
  setCollectingItem: (value: { title: string; type: string } | null) => void;
  setCurrentPosition: (pos: "landing" | "catalog" | "search" | "cart") => void;
  setCurrentMode: (mode: "navigator" | "navigator_deep" | "cart_assistant" | "executor") => void;
  chatInputRef: RefObject<HTMLTextAreaElement>;
  toast: ToastFn;
}) {
  const isItemAttached = useCallback(
    (itemId: string) => {
      return attachedItems.some((item) => (item.data.id && item.data.id === itemId) || (item.data.sku && item.data.sku === itemId));
    },
    [attachedItems],
  );

  const handleReattachItem = useCallback(
    (item: { type: string; data: any }) => {
      setAttachedItems((prev: Array<{ type: string; data: any }>) => {
        const exists = prev.some(
          (existing) => existing.type === item.type && (existing.data.id === item.data.id || existing.data.sku === item.data.sku),
        );
        if (exists) return prev;
        return [...prev, item];
      });
    },
    [setAttachedItems],
  );

  const handleAttachActionResultItem = useCallback(
    (item: any) => {
      const normalizedItem: any = {};

      const hasProductFields = item.sku || item.Sku || item.price !== undefined || item.Price !== undefined;
      const hasOrderFields = item.orderId || item.OrderId || item.orderNumber || item.OrderNumber;
      const hasReviewFields = item.rating !== undefined && (item.comment || item.Comment);
      const hasCouponFields = item.code || item.Code || item.discountType || item.DiscountType;

      let itemType = "item";
      if (hasProductFields && !hasOrderFields) itemType = "product";
      else if (hasOrderFields) itemType = "order";
      else if (hasReviewFields) itemType = "review";
      else if (hasCouponFields) itemType = "coupon";

      if (itemType === "product") {
        normalizedItem.id = item.id || item.Id || item.ID;
        normalizedItem.sku = item.sku || item.Sku || item.SKU;
        normalizedItem.name = item.name || item.Name || item.title || item.Title;
        normalizedItem.description = item.description || item.Description || "";
        normalizedItem.price = item.price ?? item.Price;
        normalizedItem.category = item.category || item.Category;
        normalizedItem.brand = item.brand || item.Brand;
        normalizedItem.inStockQty = item.inStockQty ?? item.InStockQty ?? item.stockQuantity ?? item.StockQuantity;
        normalizedItem.imageUrl = item.imageUrl || item.ImageUrl || item.image || item.Image;
        normalizedItem.rating = item.rating ?? item.Rating;
        normalizedItem.reviewCount = item.reviewCount ?? item.ReviewCount;
        Object.keys(item).forEach((key) => {
          const lowerKey = key.toLowerCase();
          if (normalizedItem[lowerKey] === undefined && item[key] !== undefined) {
            normalizedItem[lowerKey] = item[key];
          }
        });
      } else {
        Object.keys(item).forEach((key) => {
          const lowerKey = key.charAt(0).toLowerCase() + key.slice(1);
          normalizedItem[lowerKey] = item[key];
        });
      }

      const title = normalizedItem.name || normalizedItem.productName || normalizedItem.title || `Item ${normalizedItem.id || ""}`;

      const isAlreadyAttached = attachedItems.some(
        (attached) => attached.type === itemType && (attached.data.id === normalizedItem.id || attached.data.sku === normalizedItem.sku),
      );
      if (isAlreadyAttached) {
        toast({
          title: "Already Attached",
          description: `"${title}" is already attached to chat`,
          variant: "default",
        });
        return;
      }

      setAttachedItems((prev: Array<{ type: string; data: any }>) => [...prev, { type: itemType, data: normalizedItem }]);
      setCurrentPosition("cart");
      setCurrentMode("cart_assistant");

      toast({
        title: "💬 Added to Chat",
        description: `"${title}" is now part of the conversation`,
      });

      setTimeout(() => {
        chatInputRef.current?.focus();
      }, 100);
    },
    [attachedItems, chatInputRef, setAttachedItems, setCurrentMode, setCurrentPosition, toast],
  );

  const handleAttachDocument = useCallback(
    (doc: Document) => {
      if (attachedItems.some((item) => item.type === "document" && item.data.id === doc.id)) {
        toast({
          title: "Already Attached",
          description: `"${doc.title}" is already attached to chat`,
          variant: "default",
        });
        return;
      }

      setCollectingItem({ title: doc.title, type: doc.type });
      setTimeout(() => setCollectingItem(null), 1500);

      setAttachedItems((prev: Array<{ type: string; data: any }>) => [...prev, { type: "document", data: doc }]);
      setCurrentPosition("cart");
      setCurrentMode("cart_assistant");

      toast({
        title: "💬 Added to Chat",
        description: `"${doc.title}" is now part of the conversation`,
      });

      setTimeout(() => {
        chatInputRef.current?.focus();
      }, 100);
    },
    [attachedItems, chatInputRef, setAttachedItems, setCollectingItem, setCurrentMode, setCurrentPosition, toast],
  );

  const removeNonAiAttachmentByIndex = useCallback(
    (filteredIndex: number) => {
      setAttachedItems((prev: Array<{ type: string; data: any }>) => {
        let nonAiIndex = -1;
        return prev.filter((item) => {
          if (item.type === "ai-search") return true;
          nonAiIndex += 1;
          return nonAiIndex !== filteredIndex;
        });
      });
    },
    [setAttachedItems],
  );

  const removeAiSearchAttachment = useCallback(() => {
    setAttachedItems((prev: Array<{ type: string; data: any }>) => prev.filter((item) => item.type !== "ai-search"));
  }, [setAttachedItems]);

  return {
    isItemAttached,
    handleReattachItem,
    handleAttachActionResultItem,
    handleAttachDocument,
    removeNonAiAttachmentByIndex,
    removeAiSearchAttachment,
  } as const;
}
