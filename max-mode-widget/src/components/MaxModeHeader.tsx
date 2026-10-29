import { motion } from "framer-motion";
import { Headphones, Loader2, LogIn, LogOut, UserRound, X } from "lucide-react";

import type { ConversationControlState } from "@/types";
import { emitEvent, getWidgetConfig, type MaxModeHostAuthenticationConfig } from "@/config";
import { AssistantBrandMark } from "@/components/AssistantBrandMark";

import { Button } from "@/ui/button";

export function MaxModeHeader({
  onClose,
  assistantLabel,
  handoverEnabled,
  handoverState,
  handoverBusy,
  onOpenHandover,
  authentication,
  showCloseButton,
}: {
  onClose: () => void;
  assistantLabel: string;
  handoverEnabled: boolean;
  handoverState: ConversationControlState;
  handoverBusy: boolean;
  onOpenHandover: () => void;
  authentication?: MaxModeHostAuthenticationConfig;
  showCloseButton: boolean;
}) {
  return (
    <motion.div
      initial={{ opacity: 0, y: -20, x: 20 }}
      animate={{ opacity: 1, y: 0, x: 0 }}
      className="fixed top-3 right-3 md:top-4 md:right-4 z-50 flex max-w-[calc(100%-1.5rem)] items-center gap-2"
    >
      {handoverEnabled && (
        <Button
          type="button"
          variant="outline"
          onClick={onOpenHandover}
          disabled={handoverBusy}
          aria-label={handoverState === "AI_ACTIVE" ? "Talk to a person" : handoverState === "HUMAN_ACTIVE" ? "Human support" : "Connecting to a person"}
          className={`h-9 rounded-full border px-3 text-xs font-bold shadow-lg ${handoverState === "AI_ACTIVE" ? "border-gray-200 bg-white text-gray-800 hover:bg-gray-50" : "border-emerald-300 bg-emerald-50 text-emerald-800 hover:bg-emerald-100"}`}
        >
          {handoverBusy || handoverState === "HUMAN_PENDING" || handoverState === "AI_RESUME_PENDING"
            ? <Loader2 className="mr-1.5 h-4 w-4 animate-spin" />
            : handoverState === "HUMAN_ACTIVE"
              ? <UserRound className="mr-1.5 h-4 w-4" />
              : <Headphones className="mr-1.5 h-4 w-4" />}
          <span className="hidden sm:inline">{handoverState === "AI_ACTIVE" ? "Talk to a person" : handoverState === "HUMAN_ACTIVE" ? "Human support" : "Connecting"}</span>
        </Button>
      )}
      {authentication?.status === "anonymous" && authentication.signInAvailable && (
        <Button
          type="button"
          variant="outline"
          onClick={() => {
            emitEvent("authentication:sign-in", { interactionPolicy: authentication.interactionPolicy });
            void authentication.onSignIn?.();
          }}
          className="h-9 rounded-full border border-blue-200 bg-white px-3 text-xs font-bold text-blue-700 shadow-lg hover:bg-blue-50"
        >
          <LogIn className="mr-1.5 h-4 w-4" />
          <span>Sign in</span>
        </Button>
      )}
      {authentication?.status === "authenticated" && authentication.signOutAvailable && (
        <Button
          type="button"
          variant="outline"
          onClick={() => {
            emitEvent("authentication:sign-out", {});
            void authentication.onSignOut?.();
          }}
          className="h-9 rounded-full border border-gray-200 bg-white px-3 text-xs font-bold text-gray-800 shadow-lg hover:bg-gray-50"
          title={authentication.displayName ? `Signed in as ${authentication.displayName}` : "Signed in"}
        >
          <LogOut className="mr-1.5 h-4 w-4" />
          <span className="hidden sm:inline">Sign out</span>
        </Button>
      )}
      <div className="flex min-w-0 items-center gap-2 bg-primary rounded-full pl-3 pr-1 py-1 shadow-xl border-2 border-white/30">
        <div className="flex min-w-0 items-center gap-2">
          <motion.div
            animate={{ scale: [1, 1.1, 1] }}
            transition={{ duration: 2, repeat: Infinity, ease: "easeInOut" }}
            className="shrink-0 text-white"
          >
            <AssistantBrandMark logoUrl={getWidgetConfig().theme?.logoUrl} className="h-5 w-5 md:h-6 md:w-6" />
          </motion.div>
          <span className="truncate text-xs md:text-sm font-bold text-white" title={assistantLabel}>{assistantLabel}</span>
        </div>
        {showCloseButton && (
          <Button
            variant="ghost"
            size="icon"
            onClick={onClose}
            className="shrink-0 text-white hover:bg-white/20 h-7 w-7 md:h-8 md:w-8 rounded-full"
            aria-label="Close AI Workspace"
          >
            <X className="h-4 w-4 md:h-4.5 md:w-4.5" />
          </Button>
        )}
      </div>
    </motion.div>
  );
}
