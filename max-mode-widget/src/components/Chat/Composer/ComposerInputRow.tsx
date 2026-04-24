import type { RefObject } from "react";

import { motion } from "framer-motion";
import { History, Info, Loader2, Search, Send, X } from "lucide-react";

import { AISearchDisplay } from "@/components/AISearchDisplay";
import type { MaxModeMode, MaxModePosition } from "@/constants";
import { Button } from "@/ui/button";
import { Textarea } from "@/ui/textarea";
import { ModeSelector } from "./ModeSelector";

export function ComposerInputRow({
  searchCategory,
  hasAiSearch,
  aiSearchCategory,
  onClearSearchCategory,
  onRemoveAiSearch,
  oldConversationLocked,
  onOpenHistory,
  chatQuery,
  onChatQueryChange,
  isInputFocused,
  onInputFocusChange,
  chatInputRef,
  isLoading,
  currentPosition,
  currentMode,
  availableModes,
  onModeChange,
  onOpenDebug,
  nonAiAttachmentsCount,
  onSubmit,
}: {
  searchCategory: string | null;
  hasAiSearch: boolean;
  aiSearchCategory: string | null;
  onClearSearchCategory: () => void;
  onRemoveAiSearch: () => void;
  oldConversationLocked: boolean;
  onOpenHistory: () => void;
  chatQuery: string;
  onChatQueryChange: (value: string) => void;
  isInputFocused: boolean;
  onInputFocusChange: (focused: boolean) => void;
  chatInputRef: RefObject<HTMLTextAreaElement>;
  isLoading: boolean;
  currentPosition: MaxModePosition;
  currentMode: MaxModeMode;
  availableModes: MaxModeMode[];
  onModeChange: (mode: MaxModeMode) => void;
  onOpenDebug: () => void;
  nonAiAttachmentsCount: number;
  onSubmit: () => void;
}) {
  return (
    <div className="space-y-1.5">
      {/* Mobile toggles row */}
      <div className="flex md:hidden items-center gap-1.5 px-1">
        <ModeSelector currentMode={currentMode} availableModes={availableModes} onModeChange={onModeChange} />
        <span
          className={`text-[10px] font-bold px-2 py-0.5 rounded-full shadow-sm ${
            currentPosition === "cart"
              ? "bg-orange-500 text-white"
              : currentPosition === "search"
                ? "bg-blue-500 text-white"
                : currentPosition === "catalog"
                  ? "bg-indigo-500 text-white"
                  : "bg-green-500 text-white"
          }`}
        >
          {currentPosition}
        </span>
        <button
          onClick={onOpenDebug}
          className="ml-auto h-5 w-5 rounded-full bg-gray-800/80 text-white flex items-center justify-center"
          title="View API Debug Info"
        >
          <Info className="h-3 w-3" />
        </button>
      </div>

      {/* Input row */}
      <div className="flex items-center gap-2 md:gap-3">
      <Button
        size="icon"
        variant="outline"
        onClick={onOpenHistory}
        className="h-12 w-12 md:h-14 md:w-14 rounded-2xl border-2 border-purple-400/50 bg-white shadow-lg transition-all hover:scale-105 hover:border-purple-500 hover:bg-purple-50 dark:bg-gray-900 dark:hover:bg-purple-900/30 flex-shrink-0"
        title="Chat History"
      >
        <History className="h-5 w-5 md:h-6 md:w-6 text-purple-600" />
      </Button>

      <div className="relative flex-1">
        {searchCategory && !hasAiSearch && (
          <motion.div
            initial={{ opacity: 0, scale: 0.95 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.95 }}
            className="absolute top-2 left-2 z-10"
          >
            <div className="inline-flex items-center gap-1.5 px-2.5 py-1 bg-gradient-to-r from-blue-600 via-purple-600 to-pink-600 rounded-lg shadow-lg border border-white/30">
              <Search className="h-2.5 w-2.5 text-white flex-shrink-0" />
              <span className="text-[10px] font-bold text-white max-w-[120px] truncate">{searchCategory}</span>
              <button
                onClick={onClearSearchCategory}
                className="h-4 w-4 rounded-md bg-white/20 hover:bg-white/35 flex items-center justify-center transition-all hover:scale-105 ml-0.5"
                title="Clear"
              >
                <X className="h-2.5 w-2.5 text-white stroke-[3]" />
              </button>
            </div>
          </motion.div>
        )}

        {hasAiSearch && <AISearchDisplay category={aiSearchCategory || "All Categories"} onRemove={onRemoveAiSearch} />}

        <Textarea
          ref={chatInputRef}
          placeholder={
            oldConversationLocked
              ? "This conversation is locked..."
              : searchCategory || hasAiSearch
                ? "Type your search query..."
                : nonAiAttachmentsCount > 0
                  ? `Ask about ${nonAiAttachmentsCount} item${nonAiAttachmentsCount === 1 ? "" : "s"}...`
                  : "Ask me anything..."
          }
          value={chatQuery}
          onChange={(e) => onChatQueryChange(e.target.value)}
          onFocus={() => onInputFocusChange(true)}
          onBlur={() => onInputFocusChange(false)}
          onKeyPress={(e) => {
            if (e.key === "Enter" && !e.shiftKey && !oldConversationLocked) {
              e.preventDefault();
              onSubmit();
            }
          }}
          disabled={oldConversationLocked}
          className={`${
            isInputFocused ? "min-h-[80px] sm:min-h-[100px] md:min-h-[80px]" : "min-h-[56px] sm:min-h-[60px] md:min-h-[80px]"
          } ${searchCategory || hasAiSearch ? "pt-9 sm:pt-10" : "pt-4"} pb-4 pr-14 pl-4 text-sm sm:text-base resize-none border border-gray-200 rounded-2xl shadow-lg focus:shadow-xl leading-relaxed transition-all bg-white dark:bg-gray-900 dark:border-gray-700 ${
            oldConversationLocked ? "bg-gray-100 dark:bg-gray-800 cursor-not-allowed opacity-60" : ""
          }`}
          style={{
            fontFamily:
              '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
            fontSize: "16px",
          }}
        />

        <div className="hidden md:flex absolute right-3 bottom-16 items-center gap-1.5">
          <ModeSelector currentMode={currentMode} availableModes={availableModes} onModeChange={onModeChange} />
          <span
            className={`text-[10px] font-bold px-2 py-0.5 rounded-full shadow-sm ${
              currentPosition === "cart"
                ? "bg-orange-500 text-white"
                : currentPosition === "search"
                  ? "bg-blue-500 text-white"
                  : currentPosition === "catalog"
                    ? "bg-indigo-500 text-white"
                    : "bg-green-500 text-white"
            }`}
          >
            {currentPosition}
          </span>
          <button
            onClick={onOpenDebug}
            className="h-5 w-5 rounded-full bg-gray-800/80 text-white flex items-center justify-center hover:bg-gray-700 transition-colors"
            title="View API Debug Info"
          >
            <Info className="h-3 w-3" />
          </button>
        </div>

        <Button
          size="icon"
          onClick={onSubmit}
          disabled={isLoading || !chatQuery.trim() || oldConversationLocked}
          className="absolute right-2 bottom-2 h-10 w-10 md:h-11 md:w-11 rounded-xl bg-gradient-to-br from-blue-600 to-blue-700 hover:from-blue-700 hover:to-blue-800 shadow-xl disabled:opacity-50 disabled:cursor-not-allowed transition-all hover:scale-105 border border-white/20"
        >
          {isLoading ? <Loader2 className="h-5 w-5 md:h-5.5 md:w-5.5 animate-spin text-white" /> : <Send className="h-5 w-5 md:h-5.5 md:w-5.5 text-white" />}
        </Button>
      </div>
    </div>
    </div>
  );
}
