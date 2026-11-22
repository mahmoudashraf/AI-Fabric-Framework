import { useCallback } from "react";
import type { RefObject } from "react";

import type { AttachedItem, Document } from "@/types";
import { attachedActionItem, matchesAttachment, sameAttachment, type AttachmentItemSource } from "@/attachmentSelection";

type ToastFn = (opts: any) => void;

export function useAttachmentsController({
  enabled = true,
  attachedItems,
  setAttachedItems,
  setCollectingItem,
  chatInputRef,
  toast,
}: {
  enabled?: boolean;
  attachedItems: AttachedItem[];
  setAttachedItems: (updater: any) => void;
  setCollectingItem: (value: { title: string; type: string } | null) => void;
  chatInputRef: RefObject<HTMLTextAreaElement>;
  toast: ToastFn;
}) {
  const isItemAttached = useCallback(
    (itemId: string) => {
      return attachedItems.some((item) => matchesAttachment(item, itemId));
    },
    [attachedItems],
  );

  const handleReattachItem = useCallback(
    (item: AttachedItem) => {
      if (!enabled) { toast({ title: 'Attachments unavailable', description: 'Attachments are disabled for this workspace.' }); return; }
      setAttachedItems((prev: AttachedItem[]) => {
        const exists = prev.some((existing) => sameAttachment(existing, item));
        if (exists) return prev;
        return [...prev, item];
      });
    },
    [enabled, setAttachedItems, toast],
  );

  const handleAttachActionResultItem = useCallback(
    (item: any, source?: AttachmentItemSource) => {
      if (!enabled) { toast({ title: 'Attachments unavailable', description: 'Attachments are disabled for this workspace.' }); return; }
      const attachment = attachedActionItem(item, source);
      const title = attachment.data.name || attachment.data.productName || attachment.data.title || `Item ${attachment.data.id ?? ""}`;
      const isAlreadyAttached = attachedItems.some((attached) => sameAttachment(attached, attachment));
      if (isAlreadyAttached) {
        toast({
          title: "Already Attached",
          description: `"${title}" is already attached to chat`,
          variant: "default",
        });
        return;
      }

      setAttachedItems((prev: AttachedItem[]) => prev.some((attached) => sameAttachment(attached, attachment))
        ? prev : [...prev, attachment]);

      toast({
        title: "💬 Added to Chat",
        description: `"${title}" is now part of the conversation`,
      });

      setTimeout(() => {
        chatInputRef.current?.focus();
      }, 100);
    },
    [enabled, attachedItems, chatInputRef, setAttachedItems, toast],
  );

  const handleAttachDocument = useCallback(
    (doc: Document) => {
      if (!enabled) { toast({ title: 'Attachments unavailable', description: 'Attachments are disabled for this workspace.' }); return; }
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

      toast({
        title: "💬 Added to Chat",
        description: `"${doc.title}" is now part of the conversation`,
      });

      setTimeout(() => {
        chatInputRef.current?.focus();
      }, 100);
    },
    [enabled, attachedItems, chatInputRef, setAttachedItems, setCollectingItem, toast],
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
