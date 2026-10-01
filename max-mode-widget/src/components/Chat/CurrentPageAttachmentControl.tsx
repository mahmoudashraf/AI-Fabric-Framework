import { FilePlus2, FileText, Loader2, RefreshCw, X } from "lucide-react";

import type { MaxModeHostAttachment } from "@/config";
import { Button } from "@/ui/button";

export function CurrentPageAttachmentControl({
  attachment,
  isCapturing,
  maxChars,
  onAttach,
  onRemove,
  showAttachmentChip = true,
}: {
  attachment?: MaxModeHostAttachment;
  isCapturing: boolean;
  maxChars: number;
  onAttach: () => void;
  onRemove: () => void;
  showAttachmentChip?: boolean;
}) {
  const attached = Boolean(attachment);
  const title = attachment?.data?.title || "Current page";
  const metadata = attachment?.data?.metadata || {};
  const capturedCharacters = metadata.capturedCharacters as number | undefined;
  const detail = attached
    ? `${title}${capturedCharacters ? `, ${capturedCharacters} characters` : ""}${metadata.truncated ? ", truncated" : ""}`
    : `Attach readable text from this page, up to ${maxChars} characters`;

  return (
    <div className="flex min-w-0 items-center gap-2" data-max-mode-current-page-control>
      <Button
        type="button"
        size="icon"
        variant="ghost"
        className="h-9 w-9 flex-shrink-0 rounded-lg border border-gray-200 bg-white text-blue-700 shadow-sm hover:border-blue-300 hover:bg-blue-50 dark:border-gray-700 dark:bg-gray-900 dark:text-blue-300 dark:hover:bg-gray-800"
        onClick={onAttach}
        disabled={isCapturing}
        aria-label={attached ? "Refresh attached page" : "Attach current page"}
        title={attached ? "Refresh attached page" : "Attach current page"}
        data-max-mode-current-page-action={attached ? "refresh" : "attach"}
      >
        {isCapturing ? (
          <Loader2 className="h-4 w-4 animate-spin" />
        ) : attached ? (
          <RefreshCw className="h-4 w-4" />
        ) : (
          <FilePlus2 className="h-4 w-4" />
        )}
      </Button>

      {attached && showAttachmentChip && (
        <div
          className="flex min-w-0 max-w-[min(28rem,70vw)] items-center gap-2 rounded-lg border border-blue-200 bg-blue-50 px-2.5 py-1.5 text-blue-950 dark:border-blue-800 dark:bg-blue-950/40 dark:text-blue-100"
          title={detail}
          data-max-mode-current-page-chip
        >
          <FileText className="h-4 w-4 flex-shrink-0 text-blue-600 dark:text-blue-300" />
          <span className="truncate text-xs font-semibold">{title}</span>
          <button
            type="button"
            className="flex h-6 w-6 flex-shrink-0 items-center justify-center rounded-md text-blue-700 hover:bg-blue-100 hover:text-red-600 dark:text-blue-200 dark:hover:bg-blue-900/70"
            onClick={onRemove}
            aria-label="Remove attached page"
            title="Remove attached page"
            data-max-mode-current-page-remove
          >
            <X className="h-3.5 w-3.5" />
          </button>
        </div>
      )}
    </div>
  );
}
