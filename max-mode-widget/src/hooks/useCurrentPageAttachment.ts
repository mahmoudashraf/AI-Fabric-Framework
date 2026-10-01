import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { Dispatch, RefObject, SetStateAction } from "react";

import type { MaxModeCurrentPageAttachmentConfig, MaxModeHostAttachment } from "@/config";
import {
  captureCurrentPageAttachment,
  currentLocationFingerprint,
  isCurrentPageAttachment,
  resolveCurrentPageMaxChars,
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
  const invalidatedRef = useRef(false);
  const currentPageAttachment = useMemo(
    () => attachedItems.find(isCurrentPageAttachment),
    [attachedItems],
  );

  const removeCurrentPageAttachment = useCallback(() => {
    setAttachedItems((current) => current.filter((item) => !isCurrentPageAttachment(item)));
  }, [setAttachedItems]);

  const attachCurrentPage = useCallback(async () => {
    if (!enabled || isCapturingCurrentPage) return;
    setIsCapturingCurrentPage(true);
    try {
      const attachment = await captureCurrentPageAttachment(config);
      setAttachedItems((current) => [
        ...current.filter((item) => !isCurrentPageAttachment(item)),
        attachment,
      ]);
      invalidatedRef.current = false;
      toast({
        title: "Page attached",
        description: `Current page context is ready (${attachment.data.metadata.capturedCharacters} characters).`,
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
  }, [chatInputRef, config, enabled, isCapturingCurrentPage, setAttachedItems, toast]);

  useEffect(() => {
    if (enabled) return;
    removeCurrentPageAttachment();
  }, [enabled, removeCurrentPageAttachment]);

  useEffect(() => {
    function onAttachCurrentPage() {
      void attachCurrentPage();
    }
    window.addEventListener("maxmode:attach-current-page", onAttachCurrentPage);
    return () => window.removeEventListener("maxmode:attach-current-page", onAttachCurrentPage);
  }, [attachCurrentPage]);

  useEffect(() => {
    if (!enabled || !currentPageAttachment || config?.invalidateOnNavigation === false) return;
    const capturedFingerprint = currentPageAttachment.data?._locationFingerprint;
    if (!capturedFingerprint) return;

    const invalidateIfNavigated = () => {
      if (invalidatedRef.current || capturedFingerprint === currentLocationFingerprint()) return;
      invalidatedRef.current = true;
      removeCurrentPageAttachment();
      toast({
        title: "Page context removed",
        description: "Attach the new page when you want the assistant to read it.",
      });
    };

    window.addEventListener("popstate", invalidateIfNavigated);
    window.addEventListener("hashchange", invalidateIfNavigated);
    const intervalId = window.setInterval(invalidateIfNavigated, 500);
    return () => {
      window.removeEventListener("popstate", invalidateIfNavigated);
      window.removeEventListener("hashchange", invalidateIfNavigated);
      window.clearInterval(intervalId);
    };
  }, [config?.invalidateOnNavigation, currentPageAttachment, enabled, removeCurrentPageAttachment, toast]);

  return {
    currentPageAttachmentEnabled: enabled,
    currentPageAttachment,
    currentPageAttachmentMaxChars: resolveCurrentPageMaxChars(config),
    isCapturingCurrentPage,
    attachCurrentPage,
    removeCurrentPageAttachment,
  } as const;
}
