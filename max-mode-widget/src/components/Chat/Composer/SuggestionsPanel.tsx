import { AnimatePresence, motion } from "framer-motion";
import { ArrowRight, ChevronDown, Sparkles, Wand2 } from "lucide-react";

import { Button } from "@/ui/button";

export function SuggestionsPanel({
  suggestions,
  showSuggestions,
  isLoadingSuggestions,
  onDismissSuggestions,
  onShowSuggestions,
  onSuggestionSelect,
}: {
  suggestions: string[];
  showSuggestions: boolean;
  isLoadingSuggestions: boolean;
  onDismissSuggestions: () => void;
  onShowSuggestions: () => void;
  onSuggestionSelect: (suggestion: string) => void;
}) {
  if (suggestions.length === 0 && !isLoadingSuggestions) return null;

  return (
    <>
      <AnimatePresence>
        {suggestions.length > 0 && showSuggestions && (
          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -10 }}
            className="mb-3"
          >
            <div className="p-4 bg-white dark:bg-gray-800 rounded-xl border-2 border-blue-400 shadow-lg">
              <div className="flex items-center gap-3 mb-3">
                <Button
                  size="icon"
                  variant="ghost"
                  className="h-7 w-7 rounded-lg bg-blue-100 hover:bg-blue-200 dark:bg-blue-900/30 dark:hover:bg-blue-900/50 text-blue-600 hover:text-blue-900"
                  onClick={onDismissSuggestions}
                  aria-label="Dismiss suggestions"
                >
                  <ChevronDown className="h-4 w-4" />
                </Button>
                <p className="text-xs font-semibold text-purple-800 dark:text-purple-200 flex items-center gap-2">
                  <Wand2 className="h-4 w-4" />
                  AI Suggestions
                </p>
              </div>
              <div className="flex flex-wrap gap-2">
                {suggestions.map((suggestion, idx) => (
                  <motion.div
                    key={idx}
                    initial={{ opacity: 0, scale: 0.9 }}
                    animate={{ opacity: 1, scale: 1 }}
                    transition={{ delay: idx * 0.1 }}
                    whileHover={{ scale: 1.05 }}
                    whileTap={{ scale: 0.95 }}
                  >
                    <Button
                      size="sm"
                      variant="outline"
                      className="text-sm h-auto py-2 px-3 whitespace-normal text-left bg-white/80 hover:bg-purple-100 border-purple-300 hover:border-purple-500 transition-all group leading-relaxed"
                      style={{
                        fontFamily:
                          '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
                      }}
                      onClick={() => onSuggestionSelect(suggestion)}
                    >
                      <span className="flex items-center gap-1.5">
                        <ArrowRight className="h-3 w-3 group-hover:translate-x-1 transition-transform" />
                        {suggestion}
                      </span>
                    </Button>
                  </motion.div>
                ))}
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {suggestions.length > 0 && !showSuggestions && !isLoadingSuggestions && (
        <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} className="mb-3">
          <Button
            size="sm"
            variant="outline"
            onClick={onShowSuggestions}
            className="w-full bg-white dark:bg-gray-800 hover:bg-blue-50 dark:hover:bg-gray-700 border-2 border-blue-400 hover:border-blue-500 text-purple-700 dark:text-purple-300 shadow-lg"
          >
            <Wand2 className="h-3.5 w-3.5 mr-2" />
            Show AI Suggestions ({suggestions.length})
          </Button>
        </motion.div>
      )}

      {isLoadingSuggestions && (
        <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="mb-3">
          <div className="p-3 bg-white dark:bg-gray-800 rounded-lg border-2 border-blue-400 shadow-lg">
            <p className="text-xs text-purple-700 dark:text-purple-300 flex items-center gap-2">
              <motion.div animate={{ rotate: 360 }} transition={{ duration: 2, repeat: Infinity, ease: "linear" }}>
                <Sparkles className="h-3.5 w-3.5" />
              </motion.div>
              Generating smart suggestions...
            </p>
          </div>
        </motion.div>
      )}
    </>
  );
}

