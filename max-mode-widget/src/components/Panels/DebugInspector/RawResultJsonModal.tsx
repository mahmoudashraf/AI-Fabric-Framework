import { AnimatePresence, motion } from "framer-motion";
import { FileText, Minimize2 } from "lucide-react";

import type { ChatMessage, DebugData } from "../../../types";

export function RawResultJsonModal({
  isOpen,
  onClose,
  selectedDebugMessage,
  lastResponseData,
}: {
  isOpen: boolean;
  onClose: () => void;
  selectedDebugMessage: ChatMessage | null;
  lastResponseData: DebugData["response"] | null;
}) {
  return (
    <AnimatePresence>
      {isOpen && (
        <>
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={onClose}
            className="fixed inset-0 bg-black/70 backdrop-blur-sm z-[110]"
          />
          <motion.div
            initial={{ opacity: 0, scale: 0.9 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.9 }}
            transition={{ type: "spring", damping: 25, stiffness: 300 }}
            className="fixed inset-4 md:inset-8 lg:inset-12 bg-gray-900 rounded-xl shadow-2xl z-[111] overflow-hidden flex flex-col"
          >
            <div className="flex items-center justify-between px-4 py-3 border-b border-gray-700 bg-gray-800">
              <div className="flex items-center gap-2">
                <FileText className="h-4 w-4 text-purple-400" />
                <span className="text-sm font-bold text-white">Raw Result JSON</span>
                <span className="text-xs text-gray-400">Full Response Data</span>
              </div>
              <button
                onClick={onClose}
                className="p-1.5 rounded-lg hover:bg-gray-700 text-gray-400 hover:text-white transition-colors"
              >
                <Minimize2 className="h-4 w-4" />
              </button>
            </div>
            <div className="flex-1 overflow-auto p-4">
              <pre className="text-xs text-green-400 font-mono whitespace-pre-wrap">
                {(() => {
                  const debugResponse = selectedDebugMessage?.debugData?.response || lastResponseData;
                  return JSON.stringify(debugResponse?.data?.result, null, 2);
                })()}
              </pre>
            </div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
}

