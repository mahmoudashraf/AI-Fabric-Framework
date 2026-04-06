import { AnimatePresence, motion } from "framer-motion";
import { History, Loader2, Lock, MessageSquare, Plus, Trash2, X } from "lucide-react";

import type { MouseEvent } from "react";

import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";

import type { Conversation } from "@/types";

export function ConversationHistoryPanel({
  isOpen,
  onClose,
  conversations,
  isLoadingConversations,
  currentConversationId,
  onStartNewConversation,
  onOpenConversation,
  onDeleteConversation,
}: {
  isOpen: boolean;
  onClose: () => void;
  conversations: Conversation[];
  isLoadingConversations: boolean;
  currentConversationId: string | null;
  onStartNewConversation: () => void;
  onOpenConversation: (conversationId: string) => void;
  onDeleteConversation: (conversationId: string, event: MouseEvent) => void;
}) {
  return (
    <>
      {/* Conversations History Panel */}
      <AnimatePresence>
        {isOpen && (
          <>
            {/* Backdrop */}
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              onClick={onClose}
              className="fixed inset-0 bg-black/60 backdrop-blur-sm z-[100]"
            />

            {/* Panel */}
            <motion.div
              initial={{ opacity: 0, x: -20 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: -20 }}
              transition={{ type: "spring", damping: 25, stiffness: 300 }}
              className="fixed inset-y-2 left-2 w-[320px] md:w-[380px] bg-white dark:bg-gray-900 rounded-2xl shadow-2xl z-[101] overflow-hidden flex flex-col"
            >
              {/* Header */}
              <div className="flex items-center justify-between px-4 py-3 border-b border-gray-200 dark:border-gray-700 bg-gradient-to-r from-blue-600 to-blue-500">
                <div className="flex items-center gap-2">
                  <div className="h-8 w-8 rounded-lg bg-white/20 flex items-center justify-center">
                    <History className="h-4 w-4 text-white" />
                  </div>
                  <div>
                    <h2 className="text-sm font-bold text-white">Chat History</h2>
                    <p className="text-[10px] text-white/70">{conversations.length} conversations</p>
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  <Button
                    size="sm"
                    variant="ghost"
                    onClick={onStartNewConversation}
                    className="h-8 text-white hover:bg-white/20 text-xs"
                  >
                    <Plus className="h-3.5 w-3.5 mr-1" />
                    New
                  </Button>
                  <Button
                    size="icon"
                    variant="ghost"
                    onClick={onClose}
                    className="h-7 w-7 rounded-lg text-white hover:bg-white/20"
                  >
                    <X className="h-4 w-4" />
                  </Button>
                </div>
              </div>

              {/* Conversations List */}
              <div className="flex-1 overflow-auto p-3">
                {isLoadingConversations ? (
                  <div className="flex flex-col items-center justify-center h-full text-center py-8">
                    <Loader2 className="h-8 w-8 animate-spin text-purple-500 mb-3" />
                    <p className="text-sm text-gray-500">Loading conversations...</p>
                  </div>
                ) : conversations.length === 0 ? (
                  <div className="flex flex-col items-center justify-center h-full text-center py-8">
                    <div className="h-16 w-16 rounded-full bg-purple-100 dark:bg-purple-900/30 flex items-center justify-center mb-4">
                      <MessageSquare className="h-8 w-8 text-purple-500" />
                    </div>
                    <p className="text-sm font-medium text-gray-700 dark:text-gray-300">No conversations yet</p>
                    <p className="text-xs text-gray-500 mt-1">Start chatting to create your first conversation</p>
                    <Button
                      size="sm"
                      onClick={onStartNewConversation}
                      className="mt-4 bg-gradient-to-r from-purple-600 to-pink-600"
                    >
                      <Plus className="h-3.5 w-3.5 mr-1" />
                      Start New Chat
                    </Button>
                  </div>
                ) : (
                  <div className="space-y-2">
                    {conversations.map((conv) => {
                      const isActive = currentConversationId === conv.id;
                      const isLocked = conv.status === "LOCKED" || conv.status === "CLOSED";
                      const date = new Date(conv.lastInteractionAt || conv.createdAt);
                      const formattedDate = date.toLocaleDateString(undefined, {
                        month: 'short',
                        day: 'numeric',
                        hour: '2-digit',
                        minute: '2-digit',
                      });

                      return (
                        <motion.div
                          key={conv.id}
                          initial={{ opacity: 0, y: 5 }}
                          animate={{ opacity: 1, y: 0 }}
                          whileHover={{ scale: 1.01 }}
                          onClick={() => onOpenConversation(conv.id)}
                          className={`group relative p-3 rounded-xl cursor-pointer transition-all border-2 ${
                            isActive
                              ? 'bg-purple-100 dark:bg-purple-900/30 border-purple-400'
                              : 'bg-gray-50 dark:bg-gray-800 border-transparent hover:border-purple-300 hover:bg-purple-50 dark:hover:bg-purple-900/20'
                          }`}
                        >
                          <div className="flex items-start gap-3">
                            <div className={`h-9 w-9 rounded-lg flex items-center justify-center flex-shrink-0 ${
                              isActive ? 'bg-purple-500 text-white' : 'bg-purple-100 dark:bg-purple-900/50 text-purple-600'
                            }`}>
                              {isLocked ? (
                                <Lock className="h-4 w-4" />
                              ) : (
                                <MessageSquare className="h-4 w-4" />
                              )}
                            </div>
                            <div className="flex-1 min-w-0">
                              <div className="flex items-center gap-2">
                                <p className="text-sm font-medium text-gray-900 dark:text-gray-100 truncate">
                                  {conv.title || `Conversation ${conv.id.slice(0, 8)}...`}
                                </p>
                                {isLocked && (
                                  <Badge variant="outline" className="text-[9px] bg-amber-100 border-amber-300 text-amber-700 px-1.5 py-0">
                                    Locked
                                  </Badge>
                                )}
                              </div>
                              <div className="flex items-center gap-2 mt-1">
                                <span className="text-[10px] text-gray-500 dark:text-gray-400">
                                  {formattedDate}
                                </span>
                                {conv.turnsCount && (
                                  <span className="text-[10px] text-gray-400 dark:text-gray-500">
                                    • {conv.turnsCount} messages
                                  </span>
                                )}
                              </div>
                            </div>
                            {/* Delete button */}
                            <Button
                              size="icon"
                              variant="ghost"
                              onClick={(e) => onDeleteConversation(conv.id, e)}
                              className="h-7 w-7 opacity-0 group-hover:opacity-100 transition-opacity text-red-500 hover:text-red-700 hover:bg-red-100 dark:hover:bg-red-900/30"
                              title="Delete conversation"
                            >
                              <Trash2 className="h-3.5 w-3.5" />
                            </Button>
                          </div>
                        </motion.div>
                      );
                    })}
                  </div>
                )}
              </div>

              {/* Footer */}
              <div className="px-4 py-3 border-t border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800/50">
                <p className="text-[10px] text-gray-500 text-center">
                  User: demo-user • Session: demo-session
                </p>
              </div>
            </motion.div>
          </>
        )}
      </AnimatePresence>
    </>
  );
}
