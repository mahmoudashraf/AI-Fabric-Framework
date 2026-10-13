import { Headphones, Loader2, UserRound } from "lucide-react";

import type { ConversationControlState } from "@/types";
import { Button } from "@/ui/button";

export function ConversationHandoverStatusBar({
  state,
  handoverStatus,
  displayName,
  canCancel,
  canResume,
  busy,
  onCancel,
  onRetry,
  onResume,
  onOpen,
}: {
  state: ConversationControlState;
  handoverStatus?: string | null;
  displayName?: string | null;
  canCancel: boolean;
  canResume: boolean;
  busy: boolean;
  onCancel: () => void;
  onRetry: () => void;
  onResume: () => void;
  onOpen: () => void;
}) {
  if (state === "AI_ACTIVE") return null;
  const expired = state === "HUMAN_PENDING" && handoverStatus === "EXPIRED";
  const pending = state === "HUMAN_PENDING" || state === "AI_RESUME_PENDING";
  const active = state === "HUMAN_ACTIVE";
  return (
    <div className="mb-2 flex flex-wrap items-center gap-2 rounded-xl border border-emerald-200 bg-emerald-50 px-3 py-2 text-emerald-950 shadow-sm dark:border-emerald-900 dark:bg-emerald-950/60 dark:text-emerald-100">
      <button type="button" onClick={onOpen} className="flex min-w-0 flex-1 items-center gap-2 text-left">
        {pending && !expired ? <Loader2 className="h-4 w-4 flex-shrink-0 animate-spin" /> : active ? <UserRound className="h-4 w-4 flex-shrink-0" /> : <Headphones className="h-4 w-4 flex-shrink-0" />}
        <span className="truncate text-xs font-bold">
          {expired ? "Human support wait expired" : state === "HUMAN_PENDING" ? "Connecting to a person" : state === "AI_RESUME_PENDING" ? "Returning to AI" : displayName || "Human support active"}
        </span>
      </button>
      {expired && <Button type="button" size="sm" disabled={busy} onClick={onRetry}>Retry</Button>}
      {state === "HUMAN_PENDING" && canCancel && (
        <Button type="button" size="sm" variant="outline" disabled={busy} onClick={onCancel}>{expired ? "Continue with AI" : "Cancel"}</Button>
      )}
      {state === "HUMAN_ACTIVE" && canResume && (
        <Button type="button" size="sm" disabled={busy} onClick={onResume}>Continue with AI</Button>
      )}
    </div>
  );
}
