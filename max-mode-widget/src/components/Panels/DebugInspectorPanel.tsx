import { useState } from "react";

import { AnimatePresence, motion } from "framer-motion";
import { FileText, X } from "lucide-react";

import { Button } from "@/ui/button";

import type { ChatMessage, DebugData } from "@/types";

import { DebugInspectorContent } from "./DebugInspector/DebugInspectorContent";
import { RawResultJsonModal } from "./DebugInspector/RawResultJsonModal";

export function DebugInspectorPanel({
  isOpen,
  onClose,
  selectedDebugMessage,
  lastRequestData,
  lastResponseData,
}: {
  isOpen: boolean;
  onClose: () => void;
  selectedDebugMessage: ChatMessage | null;
  lastRequestData: DebugData["request"] | null;
  lastResponseData: DebugData["response"] | null;
}) {
  const [isJsonPanelExpanded, setIsJsonPanelExpanded] = useState(false);
  const [isQueryExpanded, setIsQueryExpanded] = useState(false);

  return (
    <>
      <AnimatePresence>
        {isOpen && (
          <>
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              onClick={onClose}
              className="fixed inset-0 bg-black/60 backdrop-blur-sm z-[100] xl:hidden"
            />

            <motion.div
              initial={{ opacity: 0, x: -20 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: -20 }}
              transition={{ type: "spring", damping: 25, stiffness: 300 }}
              className="fixed inset-2 md:inset-4 xl:inset-y-2 xl:left-2 xl:right-auto xl:w-[400px] bg-white dark:bg-gray-900 rounded-2xl shadow-2xl z-[101] overflow-hidden flex flex-col"
            >
              <div className="flex items-center justify-between px-4 py-3 border-b border-gray-200 dark:border-gray-700 bg-gradient-to-r from-purple-600 to-pink-600">
                <div className="flex items-center gap-2">
                  <div className="h-8 w-8 rounded-lg bg-white/20 flex items-center justify-center">
                    <FileText className="h-4 w-4 text-white" />
                  </div>
                  <div>
                    <h2 className="text-sm font-bold text-white">API Debug Inspector</h2>
                    <p className="text-[10px] text-white/70">Request & Response</p>
                  </div>
                </div>
                <Button size="icon" variant="ghost" onClick={onClose} className="h-7 w-7 rounded-lg text-white hover:bg-white/20">
                  <X className="h-4 w-4" />
                </Button>
              </div>

              <div className="flex-1 overflow-auto p-3">
                <DebugInspectorContent
                  selectedDebugMessage={selectedDebugMessage}
                  lastRequestData={lastRequestData}
                  lastResponseData={lastResponseData}
                  isQueryExpanded={isQueryExpanded}
                  setIsQueryExpanded={setIsQueryExpanded}
                  onExpandJson={() => setIsJsonPanelExpanded(true)}
                />
              </div>
            </motion.div>
          </>
        )}
      </AnimatePresence>

      <RawResultJsonModal
        isOpen={isJsonPanelExpanded}
        onClose={() => setIsJsonPanelExpanded(false)}
        selectedDebugMessage={selectedDebugMessage}
        lastResponseData={lastResponseData}
      />
    </>
  );
}

