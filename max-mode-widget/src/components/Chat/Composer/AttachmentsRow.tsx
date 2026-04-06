import { AnimatePresence, motion } from "framer-motion";
import { ChevronDown, MessageSquarePlus, Paperclip, Search, Sparkles, X } from "lucide-react";

import { Button } from "@/ui/button";
import { Card, CardContent } from "@/ui/card";

import type { AttachedItem } from "@/types";

export function AttachmentsRow({
  items,
  showAttachments,
  onRemoveAttachment,
  onDismissAttachments,
  onShowAttachments,
}: {
  items: AttachedItem[];
  showAttachments: boolean;
  onRemoveAttachment: (filteredIndex: number) => void;
  onDismissAttachments: () => void;
  onShowAttachments: () => void;
}) {
  if (items.length === 0) return null;

  return (
    <>
      <AnimatePresence>
        {items.length > 0 && showAttachments && (
          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -10 }}
            className="mb-3"
          >
            <div className="p-3 md:p-4 bg-white dark:bg-gray-800 rounded-xl border-2 border-blue-400 shadow-lg">
              <div className="flex items-center gap-3 mb-3">
                <Button
                  size="icon"
                  variant="ghost"
                  className="h-7 w-7 rounded-lg bg-blue-100 hover:bg-blue-200 dark:bg-blue-900/30 dark:hover:bg-blue-900/50 text-blue-600 hover:text-blue-900"
                  onClick={onDismissAttachments}
                  aria-label="Collapse attachments"
                >
                  <ChevronDown className="h-4 w-4" />
                </Button>
                <p className="text-xs font-semibold text-purple-800 dark:text-purple-200 flex items-center gap-2">
                  <Paperclip className="h-4 w-4" />
                  Attachments ({items.length})
                </p>
              </div>
              <div className="flex flex-wrap gap-1.5 md:gap-2 max-h-[100px] md:max-h-[140px] overflow-y-auto">
                <AnimatePresence mode="popLayout">
                  {items.map((item, idx) => {
                    const isAISearch = item.type === "ai-search";
                    return (
                      <motion.div
                        key={idx}
                        initial={{ opacity: 0, scale: 0.9, y: 10 }}
                        animate={{ opacity: 1, scale: 1, y: 0 }}
                        exit={{ opacity: 0, scale: 0.9, y: -10 }}
                        transition={{ type: "spring", damping: 20 }}
                      >
                        <Card
                          className={`border-2 shadow-lg hover:shadow-xl transition-all ${
                            isAISearch
                              ? "border-indigo-400 bg-gradient-to-br from-indigo-100 to-purple-100 dark:from-indigo-900 dark:to-purple-900"
                              : "border-blue-400 bg-gradient-to-br from-blue-100 to-blue-50 dark:from-blue-900 dark:to-blue-800"
                          }`}
                        >
                          <CardContent className="p-2 md:p-2.5 flex items-center gap-2">
                            <motion.div
                              whileHover={{ rotate: [0, -5, 5, -5, 0] }}
                              transition={{ duration: 0.5 }}
                              className={`p-1.5 rounded-lg flex-shrink-0 ${
                                isAISearch
                                  ? "bg-gradient-to-br from-indigo-600 to-purple-600"
                                  : "bg-gradient-to-br from-blue-600 to-blue-500"
                              }`}
                            >
                              {isAISearch ? (
                                <Search className="h-3 w-3 text-white" />
                              ) : (
                                <MessageSquarePlus className="h-3 w-3 text-white" />
                              )}
                            </motion.div>
                            <div className="flex-1 min-w-0">
                              <p
                                className={`text-[11px] font-bold truncate ${
                                  isAISearch ? "text-cyan-900 dark:text-cyan-100" : "text-purple-900 dark:text-purple-100"
                                }`}
                              >
                                {item.data.title || item.data.productName || item.data.name || `Item ${item.data.id || ""}`}
                              </p>
                            </div>
                            <Button
                              size="icon"
                              variant="ghost"
                              onClick={() => onRemoveAttachment(idx)}
                              className="h-6 w-6 flex-shrink-0 hover:bg-red-500/20 text-purple-700 hover:text-red-600"
                              aria-label="Remove attachment"
                            >
                              <X className="h-3 w-3" />
                            </Button>
                          </CardContent>
                        </Card>
                      </motion.div>
                    );
                  })}
                </AnimatePresence>
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {items.length > 0 && !showAttachments && (
        <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} className="mb-3">
          <Button
            size="sm"
            variant="outline"
            onClick={onShowAttachments}
            className="w-full bg-white dark:bg-gray-800 hover:bg-blue-50 dark:hover:bg-gray-700 border-2 border-blue-400 hover:border-blue-500 text-purple-700 dark:text-purple-300 shadow-lg"
          >
            <Paperclip className="h-3.5 w-3.5 mr-2" />
            Attachments ({items.length})
          </Button>
        </motion.div>
      )}
    </>
  );
}
