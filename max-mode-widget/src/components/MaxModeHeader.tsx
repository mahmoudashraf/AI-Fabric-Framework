import { motion } from "framer-motion";
import { BrainCircuit, Headphones, Loader2, UserRound, X } from "lucide-react";

import type { ConversationControlState } from "@/types";

import { Button } from "@/ui/button";

export function MaxModeHeader({
  onClose,
  assistantLabel,
  handoverEnabled,
  handoverState,
  handoverBusy,
  onOpenHandover,
}: {
  onClose: () => void;
  assistantLabel: string;
  handoverEnabled: boolean;
  handoverState: ConversationControlState;
  handoverBusy: boolean;
  onOpenHandover: () => void;
}) {
  return (
    <motion.div
      initial={{ opacity: 0, y: -20, x: 20 }}
      animate={{ opacity: 1, y: 0, x: 0 }}
      className="fixed top-3 right-3 md:top-4 md:right-4 z-50 flex items-center gap-2"
    >
      {handoverEnabled && (
        <Button
          type="button"
          variant="outline"
          onClick={onOpenHandover}
          disabled={handoverBusy}
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
      <div className="flex items-center gap-2 bg-gradient-to-r from-blue-600 to-blue-500 rounded-full pl-3 pr-1 py-1 shadow-xl border-2 border-white/30">
        <div className="flex items-center gap-2">
          <motion.div
            animate={{ scale: [1, 1.1, 1] }}
            transition={{ duration: 2, repeat: Infinity, ease: "easeInOut" }}
          >
            <BrainCircuit className="h-4 w-4 md:h-5 md:w-5 text-white" />
          </motion.div>
          <span className="text-xs md:text-sm font-bold text-white">{assistantLabel}</span>
        </div>
        <Button
          variant="ghost"
          size="icon"
          onClick={onClose}
          className="text-white hover:bg-white/20 h-7 w-7 md:h-8 md:w-8 rounded-full"
          aria-label="Close MAX Mode"
        >
          <X className="h-4 w-4 md:h-4.5 md:w-4.5" />
        </Button>
      </div>
    </motion.div>
  );
}
