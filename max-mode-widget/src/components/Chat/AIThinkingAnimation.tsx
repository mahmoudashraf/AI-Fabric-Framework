import { motion, AnimatePresence } from "framer-motion";
import { Bot, Sparkles, FileText, MessageSquare, Clock } from "lucide-react";
import { useState, useEffect, useRef } from "react";
import type { ChatMessage } from "@/types";

interface AIThinkingAnimationProps {
  messages: ChatMessage[];
  attachedItems?: Array<{ type: string; data: any }>;
}

export function AIThinkingAnimation({ messages, attachedItems = [] }: AIThinkingAnimationProps) {
  const [currentStep, setCurrentStep] = useState(0);
  const thinkingRef = useRef<HTMLDivElement>(null);

  const lastUserMessage = [...messages].reverse().find((m) => m.type === "user");
  const userQuery = lastUserMessage?.content || "your request";
  const displayQuery = userQuery.length > 70 ? userQuery.substring(0, 70) + "..." : userQuery;
  const previousMessageCount = messages.length - (lastUserMessage ? 1 : 0);
  const uniqueDocuments = Array.from(new Map(
    messages.flatMap((m) => m.documents || []).map((doc) => [doc.id, doc])
  ).values());

  // Context counts are not evidence of retrieval or tool execution.
  const pendingMessages = ["Waiting for a response"];
  if (attachedItems.length > 0) {
    pendingMessages.push(`${attachedItems.length} attached item${attachedItems.length === 1 ? "" : "s"}`);
  }
  if (previousMessageCount > 0) {
    pendingMessages.push(`${previousMessageCount} other message${previousMessageCount === 1 ? "" : "s"} in this conversation`);
  }
  if (uniqueDocuments.length > 0) {
    pendingMessages.push(`${uniqueDocuments.length} document${uniqueDocuments.length === 1 ? "" : "s"} in this conversation`);
  }

  useEffect(() => {
    setCurrentStep(0);

    const interval = setInterval(() => {
      setCurrentStep((prev) => (prev + 1) % pendingMessages.length);
    }, 1800);

    return () => clearInterval(interval);
  }, [messages, attachedItems.length, pendingMessages.length]);

  useEffect(() => {
    // Scroll into view when component appears
    if (thinkingRef.current) {
      thinkingRef.current.scrollIntoView({ behavior: "smooth", block: "end" });
    }
  }, []);

  return (
    <motion.div
      ref={thinkingRef}
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: -10 }}
      className="flex justify-start mb-4"
    >
      <div className="max-w-2xl w-full">
        <div className="bg-gradient-to-br from-blue-50 to-indigo-50 dark:from-blue-950/30 dark:to-indigo-950/30 p-4 rounded-2xl shadow-lg border-2 border-blue-200 dark:border-blue-800">
          {/* Header with AI icon */}
          <div className="flex items-center gap-3 mb-3">
            <div className="relative">
              <motion.div
                animate={{
                  scale: [1, 1.05, 1],
                }}
                transition={{
                  repeat: Infinity,
                  duration: 2,
                  ease: "easeInOut",
                }}
                className="p-2.5 bg-gradient-to-br from-blue-600 to-indigo-600 rounded-xl shadow-md"
              >
                <Bot className="h-5 w-5 text-white" />
              </motion.div>
              <motion.div
                animate={{
                  scale: [0.8, 1.2, 0.8],
                  opacity: [0.5, 1, 0.5],
                }}
                transition={{
                  repeat: Infinity,
                  duration: 2,
                  ease: "easeInOut",
                }}
                className="absolute -top-1 -right-1"
              >
                <Sparkles className="h-3.5 w-3.5 text-yellow-500 fill-yellow-500" />
              </motion.div>
            </div>
            <div className="flex-1">
              <motion.div className="text-sm font-bold bg-gradient-to-r from-blue-700 to-indigo-700 dark:from-blue-300 dark:to-indigo-300 bg-clip-text text-transparent">
                Response pending...
              </motion.div>
            </div>
          </div>

          {/* User request context */}
          <div className="mb-3 p-3 bg-white/70 dark:bg-gray-900/50 rounded-xl border border-blue-100 dark:border-blue-900 backdrop-blur-sm">
            <div className="flex items-start gap-2">
              <MessageSquare className="h-4 w-4 text-blue-600 dark:text-blue-400 mt-0.5 flex-shrink-0" />
              <div className="flex-1 min-w-0">
                <div className="text-[10px] font-semibold text-blue-600 dark:text-blue-400 uppercase tracking-wide mb-1">
                  Your request
                </div>
                <div className="text-sm text-gray-800 dark:text-gray-200 font-medium line-clamp-2">
                  "{displayQuery}"
                </div>
              </div>
            </div>
          </div>

          {/* Existing conversation documents, not current-request evidence */}
          {uniqueDocuments.length > 0 && (
            <motion.div
              initial={{ opacity: 0, height: 0 }}
              animate={{ opacity: 1, height: "auto" }}
              className="mb-3 space-y-2"
            >
              <div className="flex items-center gap-2 text-[10px] font-semibold text-emerald-600 dark:text-emerald-400 uppercase tracking-wide">
                <FileText className="h-3.5 w-3.5" />
                <span>Conversation documents ({uniqueDocuments.length})</span>
              </div>
              {uniqueDocuments.slice(0, 3).map((doc, index) => (
                <motion.div
                  key={doc.id}
                  initial={{ opacity: 0, x: -10 }}
                  animate={{ opacity: 1, x: 0 }}
                  transition={{ delay: index * 0.1 }}
                  className="p-2.5 bg-white/70 dark:bg-gray-900/50 rounded-lg border border-emerald-100 dark:border-emerald-900 backdrop-blur-sm"
                >
                  <div className="flex items-start gap-2">
                    <FileText className="h-3.5 w-3.5 text-emerald-600 dark:text-emerald-400 mt-0.5 flex-shrink-0" />
                    <div className="flex-1 min-w-0">
                      <div className="text-xs text-gray-800 dark:text-gray-200 font-bold line-clamp-1 mb-0.5">
                        {doc.title}
                      </div>
                      {doc.metadata?.description && (
                        <div className="text-[10px] text-gray-600 dark:text-gray-400 line-clamp-2">
                          {doc.metadata.description}
                        </div>
                      )}
                      <div className="flex items-center gap-2 mt-1">
                        <span className="text-[9px] text-emerald-600 dark:text-emerald-400 font-semibold">
                          {doc.type}
                        </span>
                      </div>
                    </div>
                  </div>
                </motion.div>
              ))}
              {uniqueDocuments.length > 3 && (
                <div className="text-[10px] text-gray-500 dark:text-gray-400 text-center">
                  +{uniqueDocuments.length - 3} more document{uniqueDocuments.length - 3 > 1 ? "s" : ""}
                </div>
              )}
            </motion.div>
          )}

          {/* Rotating pending status and factual context */}
          <AnimatePresence mode="wait">
            <motion.div
              key={currentStep}
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -10 }}
              transition={{ duration: 0.3 }}
              className="mb-3 p-3 bg-gradient-to-r from-blue-500/10 to-indigo-500/10 dark:from-blue-500/20 dark:to-indigo-500/20 rounded-xl border-2 border-blue-300/50 dark:border-blue-700/50"
            >
              <div className="flex items-center gap-3">
                <motion.div
                  animate={{
                    rotate: 360,
                  }}
                  transition={{
                    repeat: Infinity,
                    duration: 2,
                    ease: "linear",
                  }}
                  className="flex-shrink-0"
                >
                  <Clock className="h-4 w-4 text-blue-600 dark:text-blue-400" />
                </motion.div>
                <span className="text-sm font-bold text-blue-800 dark:text-blue-200">
                  {pendingMessages[currentStep] || pendingMessages[0]}
                </span>
              </div>
            </motion.div>
          </AnimatePresence>

          {/* Highlighted context is not completed execution progress */}
          <div className="space-y-1.5">
            {pendingMessages.map((step, index) => (
              <motion.div
                key={step}
                initial={{ opacity: 0, x: -10 }}
                animate={{ opacity: 1, x: 0 }}
                transition={{ delay: index * 0.1 }}
                className="flex items-center gap-2"
              >
                <motion.div
                  animate={{
                    scale: index === currentStep ? [1, 1.4, 1] : 1,
                    opacity: index === currentStep ? 1 : 0.3,
                  }}
                  transition={{
                    duration: 0.4,
                  }}
                  className={`w-1.5 h-1.5 rounded-full ${
                    index === currentStep
                      ? "bg-gradient-to-r from-blue-600 to-indigo-600"
                      : "bg-gray-300 dark:bg-gray-700"
                  }`}
                />
                <span
                  className={`text-xs transition-all duration-300 ${
                    index === currentStep
                      ? "font-semibold text-blue-700 dark:text-blue-300"
                      : "text-gray-400 dark:text-gray-600"
                  }`}
                >
                  {step}
                </span>
              </motion.div>
            ))}
          </div>

          {/* Animated progress bar */}
          <div className="mt-3 h-1 bg-gray-200 dark:bg-gray-800 rounded-full overflow-hidden">
            <motion.div
              animate={{
                x: ["-100%", "100%"],
              }}
              transition={{
                repeat: Infinity,
                duration: 1.5,
                ease: "easeInOut",
              }}
              className="h-full w-1/3 bg-gradient-to-r from-blue-600 via-indigo-600 to-blue-600 rounded-full shadow-lg"
            />
          </div>
        </div>
      </div>
    </motion.div>
  );
}
