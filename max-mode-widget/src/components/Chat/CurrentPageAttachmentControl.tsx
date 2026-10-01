import { FilePlus2, FileText, Loader2, RefreshCw, X } from "lucide-react";

import type { MaxModeHostAttachment } from "@/config";
import { Button } from "@/ui/button";

export function CurrentPageAttachmentControl({
  attachments,
  attachment,
  isCapturing,
  maxChars,
  maxPages,
  maxTotalChars,
  totalChars,
  canAttach,
  onAttach,
  onRemove,
  showAttachmentChip = true,
}: {
  attachments: MaxModeHostAttachment[];
  attachment?: MaxModeHostAttachment;
  isCapturing: boolean;
  maxChars: number;
  maxPages: number;
  maxTotalChars: number;
  totalChars: number;
  canAttach: boolean;
  onAttach: () => void;
  onRemove: (attachmentId: string) => void;
  showAttachmentChip?: boolean;
}) {
  const attached = Boolean(attachment);
  const atLimit = !attached && !canAttach;
  const actionLabel = attached
    ? "Refresh current page"
    : atLimit
      ? "Page attachment limit reached"
      : "Attach current page";
  const actionDetail = attached
    ? `Refresh this page. ${attachments.length} of ${maxPages} pages attached.`
    : atLimit
      ? `Remove a page before attaching another. ${attachments.length} of ${maxPages} pages and ${totalChars} of ${maxTotalChars} characters are attached.`
      : `Attach readable text from this page, up to ${maxChars} characters. ${attachments.length} of ${maxPages} pages attached.`;

  return (
    <div className="flex min-w-0 flex-wrap items-center gap-2" data-max-mode-current-page-control>
      <Button
        type="button"
        size="icon"
        variant="ghost"
        className="h-9 w-9 flex-shrink-0 rounded-lg border border-gray-200 bg-white text-blue-700 shadow-sm hover:border-blue-300 hover:bg-blue-50 dark:border-gray-700 dark:bg-gray-900 dark:text-blue-300 dark:hover:bg-gray-800"
        onClick={onAttach}
        disabled={isCapturing || atLimit}
        aria-label={actionLabel}
        title={actionDetail}
        data-max-mode-current-page-action={attached ? "refresh" : atLimit ? "limit" : "attach"}
      >
        {isCapturing ? (
          <Loader2 className="h-4 w-4 animate-spin" />
        ) : attached ? (
          <RefreshCw className="h-4 w-4" />
        ) : (
          <FilePlus2 className="h-4 w-4" />
        )}
      </Button>

      {showAttachmentChip && attachments.map((item) => {
        const title = item.data?.title || "Current page";
        const metadata = item.data?.metadata || {};
        const capturedCharacters = metadata.capturedCharacters as number | undefined;
        const detail = `${title}${capturedCharacters ? `, ${capturedCharacters} characters` : ""}${metadata.truncated ? ", truncated" : ""}`;
        return (
          <div
            key={item.data?.id || title}
            className="flex min-w-0 max-w-[min(28rem,70vw)] items-center gap-2 rounded-lg border border-blue-200 bg-blue-50 px-2.5 py-1.5 text-blue-950 dark:border-blue-800 dark:bg-blue-950/40 dark:text-blue-100"
            title={detail}
            data-max-mode-current-page-chip
          >
            <FileText className="h-4 w-4 flex-shrink-0 text-blue-600 dark:text-blue-300" />
            <span className="truncate text-xs font-semibold">{title}</span>
            <button
              type="button"
              className="flex h-6 w-6 flex-shrink-0 items-center justify-center rounded-md text-blue-700 hover:bg-blue-100 hover:text-red-600 dark:text-blue-200 dark:hover:bg-blue-900/70"
              onClick={() => onRemove(item.data?.id)}
              aria-label={`Remove attached page: ${title}`}
              title={`Remove ${title}`}
              data-max-mode-current-page-remove
            >
              <X className="h-3.5 w-3.5" />
            </button>
          </div>
        );
      })}
    </div>
  );
}
