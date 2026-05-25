import { motion } from "framer-motion";
import { BrainCircuit, X } from "lucide-react";

import { Button } from "@/ui/button";

export function MaxModeHeader({
  onClose,
  assistantLabel,
}: {
  onClose: () => void;
  assistantLabel: string;
}) {
  return (
    <motion.div
      initial={{ opacity: 0, y: -20, x: 20 }}
      animate={{ opacity: 1, y: 0, x: 0 }}
      className="fixed top-3 right-3 md:top-4 md:right-4 z-50 flex items-center gap-2"
    >
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
