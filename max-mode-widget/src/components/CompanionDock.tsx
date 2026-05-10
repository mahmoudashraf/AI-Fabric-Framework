import { useEffect, useMemo, useState } from "react";
import { Loader2, MessageCircle, PackageSearch, Search, Send, ShoppingCart, Sparkles, X, Zap } from "lucide-react";

import { MessageList } from "@/components/Chat/MessageList";
import type { MaxModeMode } from "@/constants";
import type { MaxModeController } from "@/hooks/useMaxModeController";
import { Button } from "@/ui/button";

function modeLabel(mode: MaxModeMode) {
  switch (mode) {
    case "navigator_deep":
      return "Deep";
    case "thinker_deep":
      return "Thinker";
    case "cart_assistant":
      return "Cart";
    case "executor":
      return "Action";
    default:
      return "Guide";
  }
}

export function CompanionDock({
  controller,
  onOpenMax,
}: {
  controller: MaxModeController;
  onOpenMax: () => void;
}) {
  const [expanded, setExpanded] = useState(false);
  const nonAiAttachments = controller.attachedItems.filter((item) => item.type !== "ai-search");
  const quickActions = useMemo(() => controller.quickActions.slice(0, 3), [controller.quickActions]);
  const hasAiSearch = Boolean(controller.attachedItems.find((item) => item.type === "ai-search"));
  const aiSearchCategory =
    (controller.attachedItems.find((item) => item.type === "ai-search")?.data?.category as string | undefined) || null;

  useEffect(() => {
    if (controller.isInputFocused) {
      setExpanded(true);
    }
  }, [controller.isInputFocused]);

  const submit = () => {
    if (!controller.chatQuery.trim() || controller.oldConversationLocked || controller.isLoading) {
      return;
    }
    setExpanded(true);
    void controller.handleChatQuery();
  };

  return (
    <section
      aria-label={controller.assistantLabel}
      className="fixed bottom-4 left-1/2 z-[2147483645] w-[min(78rem,calc(100vw-1rem))] -translate-x-1/2 pointer-events-auto text-gray-950 dark:text-gray-100"
    >
      {expanded && (
        <div className="mb-0 overflow-hidden rounded-t-[1.35rem] border border-blue-200/80 bg-white/98 shadow-[0_24px_72px_rgba(15,23,42,0.18)] ring-1 ring-white/80 dark:border-gray-700 dark:bg-gray-950">
          <div className="flex items-center justify-between gap-4 border-b border-gray-200/80 bg-gradient-to-r from-blue-50 via-white to-fuchsia-50 px-5 py-4 dark:border-gray-800 dark:from-gray-900 dark:via-gray-950 dark:to-gray-900">
            <div className="flex min-w-0 items-center gap-3">
              <span className="flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-2xl bg-blue-600/10 text-blue-700 ring-1 ring-blue-600/20 dark:bg-blue-500/15 dark:text-blue-300">
                <MessageCircle className="h-5 w-5" />
              </span>
              <div className="min-w-0">
                <h2 className="truncate text-base font-bold text-gray-950 dark:text-white">{controller.assistantLabel}</h2>
                <p className="truncate text-xs font-medium text-gray-500 dark:text-gray-400">
                  Shopping context is active on this page
                </p>
              </div>
            </div>
            <button
              type="button"
              onClick={() => setExpanded(false)}
              className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-xl text-gray-500 transition hover:bg-white hover:text-gray-950 dark:hover:bg-gray-800 dark:hover:text-white"
              aria-label="Minimize assistant"
            >
              <X className="h-5 w-5" />
            </button>
          </div>

          <div className="max-h-[min(46vh,28rem)] min-h-[18rem] overflow-hidden bg-white dark:bg-gray-950">
            {controller.chatMessages.length === 0 && !controller.isLoading ? (
              <div className="flex h-full min-h-[18rem] flex-col items-center justify-center px-6 text-center">
                <div className="mb-4 flex h-14 w-14 items-center justify-center rounded-2xl border border-gray-200 text-gray-400 shadow-sm dark:border-gray-800">
                  <MessageCircle className="h-7 w-7" />
                </div>
                <p className="text-lg font-semibold text-gray-700 dark:text-gray-200">Start a conversation below</p>
                <p className="mt-2 max-w-xl text-sm text-gray-500 dark:text-gray-400">
                  Ask about products, policies, collections, cart decisions, or what fits this page.
                </p>
              </div>
            ) : (
              <MessageList
                containerClassName="h-[min(46vh,28rem)] overflow-y-auto px-4 py-5"
                messages={controller.chatMessages}
                latestMessageRef={controller.latestMessageRef}
                messagesEndRef={controller.messagesEndRef}
                isLoading={controller.isLoading}
                getAiStyles={controller.getResultStyles}
                isPanelVisible={controller.isPanelVisible}
                attachedItems={controller.attachedItems}
                confirmationStatus={controller.confirmationStatus as Record<string, "confirmed" | "rejected">}
                expandedActions={controller.expandedActions}
                debugEnabled={controller.debugEnabled}
                onOpenDebug={controller.openDebugInspector}
                onResendAction={(fullMessage) => {
                  void controller.resendChatQuery(fullMessage);
                }}
                onReattachItem={controller.reattachItemWithToast}
                onOpenSourcesMobile={controller.openSourcesMobile}
                onOpenSourcesDesktop={controller.openSourcesDesktop}
                onConfirm={(messageId, confirmed, message) => controller.handleConfirmation(messageId, confirmed, message)}
                onExpandActionResults={controller.expandActionResults}
                isItemAttached={controller.isItemAttached}
                onAttachActionResultItem={controller.handleAttachActionResultItem}
                onNextStepClick={(query) => {
                  void controller.resendChatQuery(query);
                }}
                onClarificationSubmit={controller.handleClarificationSubmit}
              />
            )}
          </div>

          {quickActions.length > 0 && (
            <div className="flex flex-wrap gap-2 border-t border-gray-100 bg-white px-4 py-3 dark:border-gray-800 dark:bg-gray-950">
              {quickActions.map((action, index) => {
                const Icon = action.icon ?? [Search, ShoppingCart, PackageSearch][index] ?? Sparkles;
                return (
                  <button
                    key={`${action.label}-${index}`}
                    type="button"
                    onClick={() => {
                      setExpanded(true);
                      controller.handleQuickAction(action.query, action.position, action.mode);
                    }}
                    className="inline-flex min-h-10 items-center gap-2 rounded-xl border border-blue-200 bg-white px-3 text-sm font-semibold text-blue-700 shadow-sm transition hover:-translate-y-0.5 hover:border-blue-300 hover:bg-blue-50 dark:border-blue-800 dark:bg-gray-900 dark:text-blue-300 dark:hover:bg-blue-950/40"
                  >
                    <Icon className="h-4 w-4" />
                    <span>{action.label}</span>
                  </button>
                );
              })}
            </div>
          )}
        </div>
      )}

      <div className="rounded-[1.5rem] border-2 border-blue-600 bg-white/98 p-2 shadow-[0_22px_64px_rgba(37,99,235,0.22)] dark:bg-gray-950">
        {expanded && (
          <div className="mb-2 flex flex-wrap justify-end gap-2 px-2">
            <span className="inline-flex items-center gap-1 rounded-full bg-gray-100 px-3 py-1 text-xs font-bold text-gray-700 dark:bg-gray-800 dark:text-gray-200">
              <Sparkles className="h-3 w-3" />
              {modeLabel(controller.currentMode)}
            </span>
            <span className="rounded-full bg-gray-100 px-3 py-1 text-xs font-bold text-gray-700 dark:bg-gray-800 dark:text-gray-200">
              Assist
            </span>
            <span className="rounded-full bg-green-500 px-3 py-1 text-xs font-bold text-white">
              {controller.currentPosition}
            </span>
          </div>
        )}

        <div className="flex items-end gap-2">
          <textarea
            ref={controller.chatInputRef}
            data-max-mode-autofocus="false"
            rows={1}
            value={controller.chatQuery}
            disabled={controller.oldConversationLocked}
            aria-label={`Ask ${controller.assistantLabel}`}
            placeholder={
              controller.oldConversationLocked
                ? "This conversation is locked..."
                : controller.searchCategory || hasAiSearch
                  ? "Type your search query..."
                  : nonAiAttachments.length > 0
                    ? `Ask about ${nonAiAttachments.length} item${nonAiAttachments.length === 1 ? "" : "s"}...`
                    : "Ask me anything..."
            }
            onFocus={() => {
              setExpanded(true);
              controller.setIsInputFocused(true);
            }}
            onBlur={() => controller.setIsInputFocused(false)}
            onChange={(event) => controller.setChatQuery(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Enter" && !event.shiftKey) {
                event.preventDefault();
                submit();
              }
            }}
            className="min-h-14 flex-1 resize-none rounded-[1.15rem] border-0 bg-transparent px-4 py-4 text-base leading-relaxed text-gray-800 outline-none placeholder:text-gray-500 disabled:cursor-not-allowed disabled:opacity-60 dark:text-gray-100"
            style={{
              fontFamily:
                '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
            }}
          />
          <div className="flex flex-shrink-0 items-center gap-2 pb-1">
            <Button
              type="button"
              onClick={onOpenMax}
              className="h-12 rounded-2xl border border-fuchsia-300 bg-fuchsia-50 px-4 text-sm font-extrabold text-fuchsia-700 shadow-sm hover:bg-fuchsia-100 dark:border-fuchsia-700 dark:bg-fuchsia-950/50 dark:text-fuchsia-200"
              title="Open Max Mode"
            >
              <Zap className="mr-1.5 h-4 w-4" />
              MAX
            </Button>
            <Button
              type="button"
              onClick={submit}
              disabled={controller.isLoading || !controller.chatQuery.trim() || controller.oldConversationLocked}
              className="h-12 w-12 rounded-2xl bg-blue-600 text-white shadow-lg hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
              title="Send message"
            >
              {controller.isLoading ? <Loader2 className="h-5 w-5 animate-spin" /> : <Send className="h-5 w-5" />}
            </Button>
          </div>
        </div>

        {(controller.searchCategory || aiSearchCategory) && (
          <div className="mt-1 px-3 text-xs font-semibold text-blue-700 dark:text-blue-300">
            Search context: {controller.searchCategory || aiSearchCategory}
          </div>
        )}
      </div>
    </section>
  );
}
