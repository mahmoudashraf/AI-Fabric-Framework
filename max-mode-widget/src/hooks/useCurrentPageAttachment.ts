import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { Dispatch, RefObject, SetStateAction } from "react";

import type { MaxModeCurrentPageAttachmentConfig, MaxModeHostAttachment } from "@/config";
import {
  captureCurrentPageAttachment,
  currentPageAttachmentCharacters,
  currentLocationFingerprint,
  isCurrentPageAttachment,
  resolveCurrentPageMaxChars,
  resolveCurrentPageMaxPages,
  resolveCurrentPageMaxTotalChars,
} from "@/pageAttachment";

type ToastFn = (opts: any) => void;

export function useCurrentPageAttachment({
  config,
  attachedItems,
  setAttachedItems,
  chatInputRef,
  toast,
}: {
  config?: MaxModeCurrentPageAttachmentConfig;
  attachedItems: MaxModeHostAttachment[];
  setAttachedItems: Dispatch<SetStateAction<MaxModeHostAttachment[]>>;
  chatInputRef: RefObject<HTMLTextAreaElement>;
  toast: ToastFn;
}) {
  const enabled = Boolean(config && config.enabled !== false);
  const [isCapturingCurrentPage, setIsCapturingCurrentPage] = useState(false);
  const [activeLocationFingerprint, setActiveLocationFingerprint] = useState(currentLocationFingerprint);
  const invalidatedRef = useRef(false);
  const currentPageAttachments = useMemo(
    () => attachedItems.filter(isCurrentPageAttachment),
    [attachedItems],
  );
  const currentPageAttachment = useMemo(
    () => currentPageAttachments.find(
      (item) => item.data?._locationFingerprint === activeLocationFingerprint,
    ),
    [activeLocationFingerprint, currentPageAttachments],
  );
  const currentPageAttachmentMaxChars = resolveCurrentPageMaxChars(config);
  const currentPageAttachmentMaxPages = resolveCurrentPageMaxPages(config);
  const currentPageAttachmentMaxTotalChars = resolveCurrentPageMaxTotalChars(config);
  const currentPageAttachmentTotalChars = useMemo(
    () => currentPageAttachments.reduce(
      (total, item) => total + currentPageAttachmentCharacters(item),
      0,
    ),
    [currentPageAttachments],
  );
  const canAttachCurrentPage = Boolean(currentPageAttachment)
    || (
      currentPageAttachments.length < currentPageAttachmentMaxPages
      && currentPageAttachmentTotalChars < currentPageAttachmentMaxTotalChars
    );

  const removeCurrentPageAttachment = useCallback((attachmentId: string) => {
    setAttachedItems((current) => current.filter(
      (item) => !isCurrentPageAttachment(item) || item.data?.id !== attachmentId,
    ));
  }, [setAttachedItems]);

  const removeAllCurrentPageAttachments = useCallback(() => {
    setAttachedItems((current) => current.filter((item) => !isCurrentPageAttachment(item)));
  }, [setAttachedItems]);

  const attachCurrentPage = useCallback(async () => {
    if (!enabled || isCapturingCurrentPage) return;
    setIsCapturingCurrentPage(true);
    try {
      const attachment = await captureCurrentPageAttachment(config);
      const existing = currentPageAttachments.find((item) => item.data?.id === attachment.data?.id);
      const nextPageCount = currentPageAttachments.length + (existing ? 0 : 1);
      if (nextPageCount > currentPageAttachmentMaxPages) {
        throw new Error(`Remove a page before attaching another (maximum ${currentPageAttachmentMaxPages}).`);
      }
      const nextTotalCharacters = currentPageAttachmentTotalChars
        - currentPageAttachmentCharacters(existing)
        + currentPageAttachmentCharacters(attachment);
      if (nextTotalCharacters > currentPageAttachmentMaxTotalChars) {
        throw new Error(
          `Remove a page before attaching more text (maximum ${currentPageAttachmentMaxTotalChars} characters).`,
        );
      }
      setAttachedItems((current) => {
        const existingIndex = current.findIndex(
          (item) => isCurrentPageAttachment(item) && item.data?.id === attachment.data?.id,
        );
        if (existingIndex < 0) return [...current, attachment];
        return current.map((item, index) => index === existingIndex ? attachment : item);
      });
      invalidatedRef.current = false;
      toast({
        title: existing ? "Page refreshed" : "Page attached",
        description: `${nextPageCount} of ${currentPageAttachmentMaxPages} pages ready (${nextTotalCharacters} total characters).`,
      });
      setTimeout(() => chatInputRef.current?.focus(), 50);
    } catch (error) {
      toast({
        title: "Page could not be attached",
        description: error instanceof Error ? error.message : "The page did not expose readable text.",
        variant: "destructive",
      });
    } finally {
      setIsCapturingCurrentPage(false);
    }
  }, [
    chatInputRef,
    config,
    currentPageAttachmentMaxPages,
    currentPageAttachmentMaxTotalChars,
    currentPageAttachmentTotalChars,
    currentPageAttachments,
    enabled,
    isCapturingCurrentPage,
    setAttachedItems,
    toast,
  ]);

  useEffect(() => {
    if (enabled) return;
    removeAllCurrentPageAttachments();
  }, [enabled, removeAllCurrentPageAttachments]);

  useEffect(() => {
    function onAttachCurrentPage() {
      void attachCurrentPage();
    }
    window.addEventListener("maxmode:attach-current-page", onAttachCurrentPage);
    return () => window.removeEventListener("maxmode:attach-current-page", onAttachCurrentPage);
  }, [attachCurrentPage]);

  useEffect(() => {
    if (!enabled) return;
    const syncLocation = () => {
      const nextFingerprint = currentLocationFingerprint();
      setActiveLocationFingerprint((current) => current === nextFingerprint ? current : nextFingerprint);
    };
    window.addEventListener("popstate", syncLocation);
    window.addEventListener("hashchange", syncLocation);
    const intervalId = window.setInterval(syncLocation, 500);
    return () => {
      window.removeEventListener("popstate", syncLocation);
      window.removeEventListener("hashchange", syncLocation);
      window.clearInterval(intervalId);
    };
  }, [enabled]);

  useEffect(() => {
    if (
      !enabled
      || currentPageAttachments.length === 0
      || config?.invalidateOnNavigation === false
      || currentPageAttachments.every(
        (item) => item.data?._locationFingerprint === activeLocationFingerprint,
      )
      || invalidatedRef.current
    ) return;
    invalidatedRef.current = true;
    removeAllCurrentPageAttachments();
    toast({
      title: "Page context removed",
      description: "Attach the new page when you want the assistant to read it.",
    });
  }, [
    activeLocationFingerprint,
    config?.invalidateOnNavigation,
    currentPageAttachments,
    enabled,
    removeAllCurrentPageAttachments,
    toast,
  ]);

  return {
    currentPageAttachmentEnabled: enabled,
    currentPageAttachment,
    currentPageAttachments,
    currentPageAttachmentMaxChars,
    currentPageAttachmentMaxPages,
    currentPageAttachmentMaxTotalChars,
    currentPageAttachmentTotalChars,
    canAttachCurrentPage,
    isCapturingCurrentPage,
    attachCurrentPage,
    removeCurrentPageAttachment,
  } as const;
}
