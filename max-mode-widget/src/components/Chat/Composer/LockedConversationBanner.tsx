import { motion } from "framer-motion";
import { Lock, Plus } from "lucide-react";

import { Button } from "@/ui/button";

export function LockedConversationBanner({ onStartNewConversation }: { onStartNewConversation: () => void }) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 5 }}
      animate={{ opacity: 1, y: 0 }}
      className="mb-2 p-2 bg-amber-100 dark:bg-amber-900/30 border-2 border-amber-300 dark:border-amber-700 rounded-xl flex items-center gap-2"
    >
      <Lock className="h-4 w-4 text-amber-600 dark:text-amber-400" />
      <span className="text-sm text-amber-700 dark:text-amber-300 font-medium">This conversation is locked (read-only)</span>
      <Button size="sm" variant="outline" onClick={onStartNewConversation} className="ml-auto text-xs h-7 bg-white dark:bg-gray-800">
        <Plus className="h-3 w-3 mr-1" />
        New Chat
      </Button>
    </motion.div>
  );
}

