/**
 * useMaxMode() — programmatic control hook for React consumers.
 *
 * Provides imperative methods to open/close the widget,
 * attach products, and send messages.
 */
import { useState, useCallback } from "react";
import { emitEvent } from "@/config";
import type { SharedAttachment } from "@/context";

export interface UseMaxModeReturn {
  /** Whether the widget is currently open */
  isOpen: boolean;
  /** Open the widget */
  open: () => void;
  /** Close the widget */
  close: () => void;
  /** Toggle the widget open/closed */
  toggle: () => void;
  /** Attach a generic item to the chat context */
  attachItem: (item: SharedAttachment) => void;
  /** Attach a product to the chat context */
  attachProduct: (product: { sku: string; name: string; price: number; [key: string]: any }) => void;
  /** Get all pending attachments */
  pendingAttachments: SharedAttachment[];
  /** Clear pending attachments */
  clearAttachments: () => void;
}

export function useMaxMode(): UseMaxModeReturn {
  const [isOpen, setIsOpen] = useState(false);
  const [pendingAttachments, setPendingAttachments] = useState<SharedAttachment[]>([]);

  const open = useCallback(() => {
    setIsOpen(true);
    emitEvent("widget:opened");
  }, []);

  const close = useCallback(() => {
    setIsOpen(false);
    emitEvent("widget:closed");
  }, []);

  const toggle = useCallback(() => {
    setIsOpen((prev) => {
      const next = !prev;
      emitEvent(next ? "widget:opened" : "widget:closed");
      return next;
    });
  }, []);

  const attachItem = useCallback((attachment: SharedAttachment) => {
    if (!attachment?.type || !attachment?.data || typeof attachment.data !== "object") {
      return;
    }
    setPendingAttachments((prev) => {
      const exists = prev.some(
        (a) =>
          a.type === attachment.type &&
          ((a.data.id && a.data.id === attachment.data.id) ||
            (a.data.sku && a.data.sku === attachment.data.sku)),
      );
      if (exists) return prev;
      return [...prev, attachment];
    });
  }, []);

  const attachProduct = useCallback(
    (product: { sku: string; name: string; price: number; [key: string]: any }) => {
      const attachment: SharedAttachment = {
        type: "product",
        data: product,
      };
      attachItem(attachment);
    },
    [attachItem],
  );

  const clearAttachments = useCallback(() => {
    setPendingAttachments([]);
  }, []);

  return {
    isOpen,
    open,
    close,
    toggle,
    attachItem,
    attachProduct,
    pendingAttachments,
    clearAttachments,
  };
}
