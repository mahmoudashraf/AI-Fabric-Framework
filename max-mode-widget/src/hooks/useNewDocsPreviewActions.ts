import { useCallback } from "react";
import type { Dispatch, SetStateAction } from "react";

import type { Document } from "@/types";

export function useNewDocsPreviewActions({
  newDocuments,
  setIsBottomSheetOpen,
  setIsNewDocsPreviewOpen,
  setNewDocuments,
  setViewedDocumentIds,
}: {
  newDocuments: Document[];
  setIsBottomSheetOpen: Dispatch<SetStateAction<boolean>>;
  setIsNewDocsPreviewOpen: Dispatch<SetStateAction<boolean>>;
  setNewDocuments: Dispatch<SetStateAction<Document[]>>;
  setViewedDocumentIds: Dispatch<SetStateAction<Set<string>>>;
}) {
  const handleOpenBottomSheet = useCallback(() => {
    setIsBottomSheetOpen(true);

    const newDocIds = newDocuments.map((doc) => doc.id);
    setViewedDocumentIds((prev) => new Set([...prev, ...newDocIds]));

    if (newDocuments.length > 0) {
      setTimeout(() => {
        const firstNewDocId = newDocuments[0].id;
        const element = document.querySelector(`[data-doc-id="${firstNewDocId}"]`);
        if (element) element.scrollIntoView({ behavior: "smooth", block: "center" });
      }, 300);

      setTimeout(() => {
        setNewDocuments([]);
      }, 500);
    }
  }, [newDocuments, setIsBottomSheetOpen, setNewDocuments, setViewedDocumentIds]);

  const handleCloseNewDocsPreview = useCallback(() => {
    setIsNewDocsPreviewOpen(false);

    const newDocIds = newDocuments.map((doc) => doc.id);
    setViewedDocumentIds((prev) => new Set([...prev, ...newDocIds]));

    setTimeout(() => {
      setNewDocuments([]);
    }, 300);
  }, [newDocuments, setIsNewDocsPreviewOpen, setNewDocuments, setViewedDocumentIds]);

  return { handleOpenBottomSheet, handleCloseNewDocsPreview } as const;
}

