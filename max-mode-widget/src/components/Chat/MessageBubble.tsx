import type { RefObject } from "react";
import { useState } from "react";

import { AnimatePresence, motion } from "framer-motion";
import type { LucideIcon } from "lucide-react";
import {
  AlertCircle,
  ArrowRight,
  Ban,
  CheckCircle2,
  ChevronDown,
  Edit3,
  Eye,
  FileText,
  HelpCircle,
  Info,
  Lightbulb,
  Paperclip,
  Plus,
  RotateCcw,
  Search,
  Send,
  Sparkles,
  Star,
  XCircle,
  Zap,
} from "lucide-react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";

import { Button } from "@/ui/button";

import { getActionIcon, parseActionMessage } from "@/actionMessage";
import type { ChatMessage, Document } from "@/types";
import { normalizeMessageContent } from "@/utils";
import { ActionResultRenderer } from "../ActionResultRenderer";

export type AiStyles = {
  icon: LucideIcon;
  bg: string;
  border: string;
  text: string;
  iconColor: string;
  label: string;
  hideBadge?: boolean;
};

export function MessageBubble({
  message,
  isLatest,
  latestMessageRef,
  aiStyles,
  isPanelVisible,
  attachedItems,
  confirmationStatus,
  expandedCount,
  onOpenDebug,
  onResendAction,
  onReattachItem,
  onOpenSourcesMobile,
  onOpenSourcesDesktop,
  onConfirm,
  onExpandActionResults,
  isItemAttached,
  onAttachActionResultItem,
  onNextStepClick,
  onClarificationSubmit,
}: {
  message: ChatMessage;
  isLatest: boolean;
  latestMessageRef: RefObject<HTMLDivElement>;
  aiStyles?: AiStyles | null;
  isPanelVisible: boolean;
  attachedItems: Array<{ type: string; data: any }>;
  confirmationStatus: Record<string, "confirmed" | "rejected" | undefined>;
  expandedCount: number;
  onOpenDebug: (message: ChatMessage) => void;
  onResendAction: (fullMessage: string) => void;
  onReattachItem: (item: { type: string; data: any }, isAlreadyAttached: boolean) => void;
  onOpenSourcesMobile: (messageId: string) => void;
  onOpenSourcesDesktop: (messageId: string) => void;
  onConfirm: (messageId: string, confirmed: boolean, message: ChatMessage) => void;
  onExpandActionResults: (messageId: string, nextCount: number) => void;
  isItemAttached: (itemId: string) => boolean;
  onAttachActionResultItem: (item: any) => void;
  onNextStepClick: (query: string) => void;
  onClarificationSubmit?: (action: string, parameters: Record<string, any>) => void;
}) {
  const [selectedDocument, setSelectedDocument] = useState<Document | null>(null);
  const [isSuggestionExpanded, setIsSuggestionExpanded] = useState(false);
  const [clarificationValues, setClarificationValues] = useState<Record<string, string>>({});
  const [clarificationEditingField, setClarificationEditingField] = useState<string | null>(null);
  const [clarificationSubmitted, setClarificationSubmitted] = useState(false);
  const Icon = message.type === "ai" ? aiStyles?.icon : undefined;

  return (
    <>
    <motion.div
      ref={isLatest ? latestMessageRef : null}
      data-message-id={message.id}
      initial={{ opacity: 0, x: message.type === "user" ? 20 : -20, scale: 0.95 }}
      animate={{ opacity: 1, x: 0, scale: 1 }}
      exit={{ opacity: 0, scale: 0.95 }}
      transition={{ type: "spring", damping: 25, stiffness: 300 }}
      className={`flex ${message.type === "user" ? "justify-end" : "justify-start"}`}
    >
      <div
        className={`max-w-[85%] md:max-w-[90%] rounded-3xl overflow-hidden ${
          message.type === "user"
            ? "bg-gradient-to-br from-blue-700 via-blue-800 to-blue-900 text-white shadow-lg mr-16 md:mr-0"
            : `${aiStyles?.bg} shadow-xl relative`
        }`}
      >
        {message.type === "ai" && Icon && !aiStyles?.hideBadge && (
          <div className="relative">
            <div className={`px-4 md:px-5 py-2 md:py-2.5 flex items-center justify-between bg-gradient-to-r ${aiStyles?.bg}`}>
              <div className="flex items-center gap-2">
                <div className={`p-1.5 rounded-lg ${aiStyles?.iconColor} bg-white/20`}>
                  <Icon className="h-3.5 w-3.5 md:h-4 md:w-4 text-white" />
                </div>
                <span className="text-[11px] md:text-xs font-bold text-white drop-shadow-sm">{aiStyles?.label}</span>
              </div>
              {message.debugData && (
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    onOpenDebug(message);
                  }}
                  className="p-1.5 rounded-lg hover:bg-white/20 transition-colors"
                  title="View API Debug Data"
                >
                  <Info className="h-4 w-4 text-white/80 hover:text-white" />
                </button>
              )}
            </div>
            <div className="h-px bg-gradient-to-r from-transparent via-white/30 to-transparent" />
          </div>
        )}

        <div className={`p-4 md:p-5 ${message.type === "ai" ? "bg-white dark:bg-gray-900" : ""}`}>
          {message.type === "user" && message.searchCategory && (
            <div className="mb-2">
              <span className="inline-flex items-center gap-1.5 px-2.5 sm:px-3 py-1 sm:py-1.5 bg-white/20 border border-white/30 rounded-full text-[10px] sm:text-xs font-semibold">
                <Search className="h-3 w-3" />
                {message.searchCategory}
              </span>
            </div>
          )}

          {message.attachedItems && message.attachedItems.length > 0 && (
            <div className="mb-3 space-y-2">
              {message.attachedItems.map((item, idx) => {
                const isAISearch = item.type === "ai-search";
                const isAlreadyAttached = attachedItems.some(
                  (attached) =>
                    attached.type === item.type && (attached.data.id === item.data.id || attached.data.sku === item.data.sku),
                );
                return (
                  <div
                    key={idx}
                    className={`p-2 rounded-lg border text-xs flex items-center gap-2 ${
                      isAISearch
                        ? "bg-gradient-to-r from-cyan-500/20 to-purple-500/20 border-cyan-400/50"
                        : "bg-white/20 border-white/30"
                    }`}
                  >
                    {isAISearch ? <Search className="h-3 w-3 text-cyan-600" /> : <Paperclip className="h-3 w-3" />}
                    <span className="flex-1 font-semibold">
                      {item.data.title || `${item.data.type || item.type}: ${item.data.name || ""}`}
                    </span>
                    {!isAISearch && (
                      <button
                        onClick={() => onReattachItem(item, isAlreadyAttached)}
                        className={`p-1 rounded transition-colors ${
                          isAlreadyAttached
                            ? "text-green-500 cursor-default"
                            : "text-gray-400 hover:text-blue-600 hover:bg-blue-100/20"
                        }`}
                        title={isAlreadyAttached ? "Already attached" : "Re-attach to chat"}
                      >
                        {isAlreadyAttached ? <CheckCircle2 className="h-3.5 w-3.5" /> : <Plus className="h-3.5 w-3.5" />}
                      </button>
                    )}
                  </div>
                );
              })}
            </div>
          )}

          {(() => {
            const content = normalizeMessageContent(message.content);
            const parsedAction = parseActionMessage(content);

            if (message.type === "user" && parsedAction.isAction) {
              const ActionIcon = getActionIcon(parsedAction.actionType);
              return (
                <div className="relative inline-block max-w-full">
                  <Button
                    onClick={() => onResendAction(parsedAction.fullMessage)}
                    size="icon"
                    variant="ghost"
                    className="absolute -left-2 md:-left-3 -top-2 md:-top-3 h-8 w-8 md:h-9 md:w-9 rounded-full bg-blue-600 hover:bg-blue-700 text-white transition-all hover:scale-110 shadow-xl z-10 border-2 border-white dark:border-gray-900"
                    title="Resend action"
                  >
                    <RotateCcw className="h-3.5 w-3.5 md:h-4 md:w-4" />
                  </Button>

                  <div className="inline-flex items-center gap-2 md:gap-2.5 px-3 md:px-4 py-2 md:py-2.5 bg-gradient-to-br from-blue-500 to-blue-600 rounded-full shadow-lg max-w-full">
                    <div className="p-1.5 md:p-2 bg-white/20 backdrop-blur-sm rounded-lg flex-shrink-0">
                      <ActionIcon className="h-3.5 w-3.5 md:h-4 md:w-4 text-white" />
                    </div>
                    <span className="text-sm md:text-base font-bold text-white pr-1 md:pr-2 truncate">
                      {parsedAction.query}
                    </span>
                  </div>
                </div>
              );
            }

            return message.type === "ai" ? (
              <div
                className="prose prose-sm md:prose-base max-w-none dark:prose-invert prose-headings:font-bold prose-h1:text-2xl prose-h2:text-xl prose-h3:text-lg prose-p:leading-relaxed prose-strong:font-bold prose-strong:text-gray-900 dark:prose-strong:text-white prose-ul:list-disc prose-ol:list-decimal prose-li:marker:text-blue-600 dark:prose-li:marker:text-blue-400"
                style={{
                  fontFamily:
                    '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
                }}
              >
                <ReactMarkdown remarkPlugins={[remarkGfm]}>{content}</ReactMarkdown>
              </div>
            ) : (
              <p
                className="text-base md:text-lg whitespace-pre-wrap leading-relaxed font-medium"
                style={{
                  fontFamily:
                    '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
                }}
              >
                {content}
              </p>
            );
          })()}

          {message.resultType === "CONFIRMATION_REQUIRED" &&
            confirmationStatus[message.id] !== "confirmed" &&
            confirmationStatus[message.id] !== "rejected" && (
              <div className="mt-4 flex gap-2">
                <Button
                  onClick={() => onConfirm(message.id, true, message)}
                  size="sm"
                  className="flex-1 bg-gradient-to-r from-green-600 to-emerald-600 hover:from-green-700 hover:to-emerald-700 text-white shadow-lg"
                >
                  <CheckCircle2 className="h-4 w-4 mr-1.5" />
                  Confirm
                </Button>
                <Button
                  onClick={() => onConfirm(message.id, false, message)}
                  size="sm"
                  variant="outline"
                  className="flex-1 border-red-300 text-red-600 hover:bg-red-50 hover:text-red-700 hover:border-red-400"
                >
                  <XCircle className="h-4 w-4 mr-1.5" />
                  Reject
                </Button>
              </div>
            )}

          {message.resultType === "CONFIRMATION_REQUIRED" && confirmationStatus[message.id] === "confirmed" && (
            <div className="mt-3 p-3 bg-green-50 border-2 border-green-200 rounded-lg">
              <p className="text-sm text-green-800 font-semibold flex items-center gap-2">
                <CheckCircle2 className="h-4 w-4" />
                Confirmed
              </p>
            </div>
          )}

          {message.resultType === "CONFIRMATION_REQUIRED" && confirmationStatus[message.id] === "rejected" && (
            <div className="mt-3 p-3 bg-red-50 border-2 border-red-200 rounded-lg">
              <p className="text-sm text-red-800 font-semibold flex items-center gap-2">
                <XCircle className="h-4 w-4" />
                Rejected
              </p>
            </div>
          )}

          {message.resultType === "INFORMATION_PROVIDED" &&
            message.documents &&
            message.documents.length > 0 &&
            (message.result as any)?.data?.answer == null && (
              <div className="mt-4 space-y-2">
                <div className="flex items-center gap-1.5 mb-1">
                  <FileText className="h-3.5 w-3.5 text-purple-500" />
                  <span className="text-[11px] font-bold text-gray-600 dark:text-gray-300">
                    {message.documents.length} Result{message.documents.length !== 1 ? "s" : ""} Found
                  </span>
                </div>
                {message.documents.map((doc, idx) => {
                  const typeColors: Record<string, string> = {
                    review: "bg-amber-100 dark:bg-amber-900/40 text-amber-700 dark:text-amber-300 border-amber-200 dark:border-amber-700",
                    product: "bg-blue-100 dark:bg-blue-900/40 text-blue-700 dark:text-blue-300 border-blue-200 dark:border-blue-700",
                    order: "bg-green-100 dark:bg-green-900/40 text-green-700 dark:text-green-300 border-green-200 dark:border-green-700",
                  };
                  const typeColor = typeColors[doc.type] || "bg-purple-100 dark:bg-purple-900/40 text-purple-700 dark:text-purple-300 border-purple-200 dark:border-purple-700";
                  const TypeIcon = doc.type === "review" ? Star : FileText;

                  return (
                    <motion.button
                      key={doc.id}
                      initial={{ opacity: 0, y: 6 }}
                      animate={{ opacity: 1, y: 0 }}
                      transition={{ delay: idx * 0.06 }}
                      onClick={() => setSelectedDocument(doc)}
                      className="w-full text-left rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800/50 hover:border-purple-400 dark:hover:border-purple-500 hover:shadow-md transition-all group overflow-hidden"
                    >
                      <div className="p-3">
                        <div className="flex items-start gap-2.5">
                          <div className={`p-1.5 rounded-lg ${typeColor} flex-shrink-0 mt-0.5`}>
                            <TypeIcon className="h-3.5 w-3.5" />
                          </div>
                          <div className="flex-1 min-w-0">
                            <div className="flex items-center gap-2 mb-1">
                              <span className={`text-[9px] font-bold px-1.5 py-0.5 rounded-full ${typeColor}`}>
                                {doc.type}
                              </span>
                              <span className="text-[9px] text-gray-400 dark:text-gray-500">
                                id: {doc.id}
                              </span>
                              {doc.score != null && (
                                <span className="ml-auto text-[9px] font-bold text-purple-600 dark:text-purple-400">
                                  {(doc.score * 100).toFixed(1)}% match
                                </span>
                              )}
                            </div>
                            <p className="text-xs text-gray-700 dark:text-gray-300 line-clamp-2 leading-relaxed">
                              {doc.content}
                            </p>
                          </div>
                          <div className="flex-shrink-0 p-1 rounded-lg opacity-0 group-hover:opacity-100 transition-opacity bg-purple-100 dark:bg-purple-900/40">
                            <Eye className="h-3.5 w-3.5 text-purple-600 dark:text-purple-400" />
                          </div>
                        </div>
                      </div>
                    </motion.button>
                  );
                })}
              </div>
            )}

          {message.resultType === "INFORMATION_PROVIDED" &&
            message.documents &&
            message.documents.length > 0 &&
            (message.result as any)?.data?.answer != null && (
              <Button
                onClick={() => onOpenSourcesMobile(message.id)}
                size="sm"
                className="mt-3 md:hidden bg-gradient-to-br from-blue-500 via-purple-500 to-pink-500 hover:from-blue-600 hover:via-purple-600 hover:to-pink-600 text-white shadow-lg text-xs rounded-full px-4 py-2 flex items-center gap-2"
              >
                <Sparkles className="h-3.5 w-3.5" />
                <span className="font-semibold">Sources Used</span>
                <span className="px-1.5 py-0.5 bg-white/25 rounded-full text-[10px] font-bold">{message.documents.length}</span>
              </Button>
            )}

          {message.resultType === "INFORMATION_PROVIDED" &&
            message.documents &&
            message.documents.length > 0 &&
            (message.result as any)?.data?.answer != null &&
            !isPanelVisible && (
              <Button
                onClick={() => onOpenSourcesDesktop(message.id)}
                size="sm"
                className="mt-3 hidden md:inline-flex bg-gradient-to-br from-blue-500 via-purple-500 to-pink-500 hover:from-blue-600 hover:via-purple-600 hover:to-pink-600 text-white shadow-lg rounded-full px-5 py-2.5 items-center gap-2.5"
              >
                <Sparkles className="h-4 w-4" />
                <span className="font-semibold">View Sources Used</span>
                <span className="px-2 py-0.5 bg-white/25 rounded-full text-xs font-bold">{message.documents.length}</span>
              </Button>
            )}

          {message.result?.sanitizedPayload?.type === "ACTION_EXECUTED" &&
            message.result?.sanitizedPayload?.data?.actionResult?.data && (
              <ActionResultRenderer
                data={message.result.sanitizedPayload.data.actionResult.data}
                messageId={message.id}
                expandedCount={expandedCount}
                onExpand={(count) => onExpandActionResults(message.id, count)}
                isAttached={isItemAttached}
                onAttach={(item) => onAttachActionResultItem(item)}
              />
            )}

          {/* Fallback for ACTION_EXECUTED without actionResult.data (e.g. order creation) */}
          {message.result?.sanitizedPayload?.type === "ACTION_EXECUTED" &&
            message.result?.sanitizedPayload?.data &&
            !message.result?.sanitizedPayload?.data?.actionResult?.data && (
              <ActionResultRenderer
                data={message.result.sanitizedPayload.data}
                messageId={message.id}
                expandedCount={expandedCount}
                onExpand={(count) => onExpandActionResults(message.id, count)}
                isAttached={isItemAttached}
                onAttach={(item) => onAttachActionResultItem(item)}
              />
            )}

          {/* Clarification Required - interactive form */}
          {message.resultType === "CLARIFICATION_REQUIRED" && (() => {
            const cData = message.result?.sanitizedPayload?.data;
            const missingParams: string[] = cData?.missingRequiredParameters || [];
            const providedParams: Record<string, any> = cData?.providedParameters || {};
            const actionName: string = cData?.action || "";
            const hasForm = missingParams.length > 0 || Object.keys(providedParams).length > 0;

            const getFormValues = () => {
              const vals: Record<string, string> = { ...clarificationValues };
              for (const key of Object.keys(providedParams)) {
                if (!(key in vals)) {
                  vals[key] = providedParams[key] != null && providedParams[key] !== "" ? String(providedParams[key]) : "";
                }
              }
              for (const key of missingParams) {
                if (!(key in vals)) {
                  vals[key] = providedParams[key] != null && providedParams[key] !== "" ? String(providedParams[key]) : "";
                }
              }
              return vals;
            };
            const formValues = getFormValues();

            const isMissing = (key: string) => missingParams.includes(key);
            const hasProvidedValue = (key: string) => providedParams[key] != null && providedParams[key] !== "" && !isMissing(key);

            const allKeys = [
              ...missingParams,
              ...Object.keys(providedParams).filter((k) => !missingParams.includes(k)),
            ];

            const canSubmit = missingParams.every((key) => {
              const val = clarificationValues[key] ?? formValues[key];
              return val && val.trim() !== "";
            });

            const handleFieldChange = (key: string, value: string) => {
              setClarificationValues((prev) => ({ ...prev, [key]: value }));
            };

            const handleSubmit = () => {
              if (!canSubmit || clarificationSubmitted || !onClarificationSubmit) return;
              const finalValues: Record<string, any> = {};
              for (const key of allKeys) {
                const val = clarificationValues[key] ?? formValues[key];
                if (typeof providedParams[key] === "number") {
                  const num = Number(val);
                  finalValues[key] = isNaN(num) ? val : num;
                } else {
                  finalValues[key] = val;
                }
              }
              setClarificationSubmitted(true);
              onClarificationSubmit(actionName, finalValues);
            };

            return (
              <div className="mt-4 rounded-xl border-2 border-orange-200 dark:border-orange-700 bg-gradient-to-br from-orange-50 to-amber-50 dark:from-orange-900/20 dark:to-amber-900/20 overflow-hidden">
                <div className="px-4 py-2.5 bg-gradient-to-r from-orange-600 to-amber-600 flex items-center gap-2">
                  <div className="p-1.5 rounded-lg bg-white/20">
                    <AlertCircle className="h-3.5 w-3.5 text-white" />
                  </div>
                  <span className="text-[11px] md:text-xs font-bold text-white drop-shadow-sm">
                    {hasForm ? "Complete Required Information" : "Clarification Needed"}
                  </span>
                  {actionName && (
                    <span className="ml-auto text-[9px] font-bold px-1.5 py-0.5 rounded-full bg-white/20 text-white">
                      {actionName.replace(/_/g, " ")}
                    </span>
                  )}
                </div>

                <div className="p-4 space-y-3">
                  {!hasForm && cData?.options && (
                    <ul className="space-y-1">
                      {(cData.options as string[]).map((option: string, idx: number) => (
                        <li key={idx} className="text-xs text-orange-700 dark:text-orange-300 flex items-center gap-1.5">
                          <span className="w-1.5 h-1.5 rounded-full bg-orange-400 flex-shrink-0" />
                          {option}
                        </li>
                      ))}
                    </ul>
                  )}

                  {hasForm && !clarificationSubmitted && (
                    <>
                      {allKeys.map((key) => {
                        const missing = isMissing(key);
                        const hasValue = hasProvidedValue(key);
                        const isEditing = clarificationEditingField === key || missing || !hasValue;
                        const currentValue = clarificationValues[key] ?? formValues[key];

                        return (
                          <div key={key} className="space-y-1">
                            <div className="flex items-center gap-1.5">
                              <label className="text-[11px] font-bold text-gray-700 dark:text-gray-300 uppercase tracking-wide">
                                {key.replace(/([A-Z])/g, " $1").replace(/^./, (s) => s.toUpperCase()).trim()}
                              </label>
                              {missing && (
                                <span className="text-[9px] font-bold px-1.5 py-0.5 rounded-full bg-red-500/20 text-red-600 dark:text-red-400">
                                  Required
                                </span>
                              )}
                              {hasValue && !isEditing && (
                                <button
                                  onClick={() => setClarificationEditingField(key)}
                                  className="ml-auto flex items-center gap-0.5 text-[9px] font-semibold text-blue-600 dark:text-blue-400 hover:text-blue-700 transition-colors"
                                >
                                  <Edit3 className="h-2.5 w-2.5" />
                                  Edit
                                </button>
                              )}
                            </div>

                            {isEditing ? (
                              <input
                                type="text"
                                value={currentValue}
                                onChange={(e) => handleFieldChange(key, e.target.value)}
                                onKeyDown={(e) => {
                                  if (e.key === "Enter" && canSubmit) handleSubmit();
                                }}
                                placeholder={`Enter ${key.replace(/([A-Z])/g, " $1").toLowerCase().trim()}...`}
                                className={`w-full px-3 py-2 text-sm rounded-lg border-2 bg-white dark:bg-gray-800 transition-all focus:outline-none focus:ring-2 ${
                                  missing && (!currentValue || !currentValue.trim())
                                    ? "border-red-300 dark:border-red-600 focus:ring-red-500/30 focus:border-red-400"
                                    : "border-orange-200 dark:border-orange-700 focus:ring-orange-500/30 focus:border-orange-400"
                                }`}
                                style={{
                                  fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
                                }}
                              />
                            ) : (
                              <div
                                onClick={() => setClarificationEditingField(key)}
                                className="w-full px-3 py-2 text-sm rounded-lg border-2 border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800/50 cursor-pointer hover:border-blue-300 dark:hover:border-blue-600 transition-all flex items-center justify-between group"
                              >
                                <span className="text-gray-800 dark:text-gray-200 font-medium">{currentValue}</span>
                                <Edit3 className="h-3 w-3 text-gray-400 opacity-0 group-hover:opacity-100 transition-opacity" />
                              </div>
                            )}
                          </div>
                        );
                      })}

                      <div className="pt-2">
                        <Button
                          onClick={handleSubmit}
                          disabled={!canSubmit}
                          size="sm"
                          className="w-full bg-gradient-to-r from-orange-600 to-amber-600 hover:from-orange-700 hover:to-amber-700 text-white shadow-lg disabled:opacity-50 disabled:cursor-not-allowed"
                        >
                          <Send className="h-3.5 w-3.5 mr-1.5" />
                          Submit & Proceed
                        </Button>
                      </div>
                    </>
                  )}

                  {hasForm && clarificationSubmitted && (
                    <div className="p-3 bg-green-50 dark:bg-green-900/20 border-2 border-green-200 dark:border-green-700 rounded-lg">
                      <p className="text-sm text-green-800 dark:text-green-200 font-semibold flex items-center gap-2">
                        <CheckCircle2 className="h-4 w-4" />
                        Parameters submitted
                      </p>
                    </div>
                  )}
                </div>
              </div>
            );
          })()}

          {message.result?.sanitizedPayload?.type === "COMPOUND_HANDLED" &&
            message.result?.sanitizedPayload?.data?.results &&
            Array.isArray(message.result.sanitizedPayload.data.results) && (
              <div className="mt-4 space-y-2">
                {(message.result.sanitizedPayload.data.results as any[]).map((subResult: any, idx: number) => {
                  const subType = subResult.type;
                  const subIcon =
                    subType === "ACTION_EXECUTED" ? CheckCircle2
                    : subType === "ACTION_DENIED" ? Ban
                    : subType === "CONFIRMATION_REQUIRED" ? HelpCircle
                    : subType === "CLARIFICATION_REQUIRED" ? AlertCircle
                    : subType === "INFORMATION_PROVIDED" ? Info
                    : Zap;
                  const SubIcon = subIcon;
                  const colorMap: Record<string, string> = {
                    ACTION_EXECUTED: "bg-green-50 dark:bg-green-900/20 border-green-200 dark:border-green-700 text-green-800 dark:text-green-200",
                    ACTION_DENIED: "bg-red-50 dark:bg-red-900/20 border-red-200 dark:border-red-700 text-red-800 dark:text-red-200",
                    CONFIRMATION_REQUIRED: "bg-yellow-50 dark:bg-yellow-900/20 border-yellow-200 dark:border-yellow-700 text-yellow-800 dark:text-yellow-200",
                    CLARIFICATION_REQUIRED: "bg-orange-50 dark:bg-orange-900/20 border-orange-200 dark:border-orange-700 text-orange-800 dark:text-orange-200",
                    INFORMATION_PROVIDED: "bg-blue-50 dark:bg-blue-900/20 border-blue-200 dark:border-blue-700 text-blue-800 dark:text-blue-200",
                  };
                  const colors = colorMap[subType] || "bg-gray-50 dark:bg-gray-800 border-gray-200 dark:border-gray-700 text-gray-800 dark:text-gray-200";

                  return (
                    <div key={idx} className={`p-2.5 rounded-lg border ${colors}`}>
                      <div className="flex items-center gap-2 mb-1">
                        <SubIcon className="h-3.5 w-3.5 flex-shrink-0" />
                        <span className="text-xs font-bold">{subType?.replace(/_/g, " ") || "Result"}</span>
                        {subResult.success !== undefined && (
                          <span className={`text-[9px] font-bold px-1.5 py-0.5 rounded ${subResult.success ? "bg-green-500/20 text-green-700 dark:text-green-300" : "bg-red-500/20 text-red-700 dark:text-red-300"}`}>
                            {subResult.success ? "OK" : "FAILED"}
                          </span>
                        )}
                      </div>
                      {subResult.message && (
                        <p className="text-[11px] opacity-80">{subResult.message}</p>
                      )}
                      {subResult.data?.actionResult?.data && (
                        <ActionResultRenderer
                          data={subResult.data.actionResult.data}
                          messageId={`${message.id}-sub-${idx}`}
                          expandedCount={expandedCount}
                          onExpand={(count) => onExpandActionResults(message.id, count)}
                          isAttached={isItemAttached}
                          onAttach={(item) => onAttachActionResultItem(item)}
                        />
                      )}
                    </div>
                  );
                })}
              </div>
            )}

          {message.type === "ai" && message.result?.smartSuggestion &&
            (message.result.smartSuggestion.response || message.result.smartSuggestion.query || (message.result.smartSuggestion.documents && message.result.smartSuggestion.documents.length > 0)) && (
            <motion.div
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.2 }}
              className="mt-4"
            >
              <div className="rounded-xl border-2 border-amber-200 dark:border-amber-700 bg-gradient-to-br from-amber-50 to-yellow-50 dark:from-amber-900/20 dark:to-yellow-900/20 overflow-hidden">
                <div className="px-3 py-2 bg-gradient-to-r from-amber-100 to-yellow-100 dark:from-amber-800/30 dark:to-yellow-800/30 flex items-center gap-2">
                  <div className="p-1 rounded-md bg-amber-500/20">
                    <Lightbulb className="h-3.5 w-3.5 text-amber-600 dark:text-amber-400" />
                  </div>
                  <span className="text-[11px] font-bold text-amber-800 dark:text-amber-200">
                    Smart Suggestion
                  </span>
                  <span className="ml-auto text-[9px] font-bold px-1.5 py-0.5 rounded-full bg-amber-500/20 text-amber-700 dark:text-amber-300">
                    {Math.round((message.result.smartSuggestion.confidence ?? 0) * 100)}% confidence
                  </span>
                </div>
                <div className="p-3 space-y-2">
                  {message.result.smartSuggestion.query && (
                    <div className="flex items-center gap-2 px-2.5 py-1.5 rounded-lg bg-amber-500/10 border border-amber-200 dark:border-amber-700">
                      <Search className="h-3 w-3 text-amber-600 dark:text-amber-400 flex-shrink-0" />
                      <span className="text-[11px] font-semibold text-amber-800 dark:text-amber-200">
                        {message.result.smartSuggestion.query}
                      </span>
                    </div>
                  )}
                  {message.result.smartSuggestion.response && (() => {
                    const response = message.result!.smartSuggestion!.response!;
                    const isLong = response.length > 200;
                    return (
                      <div>
                        <div className={`prose prose-sm max-w-none dark:prose-invert prose-p:leading-relaxed prose-p:text-gray-700 dark:prose-p:text-gray-300 ${
                          isLong && !isSuggestionExpanded ? "max-h-[100px] overflow-hidden relative" : ""
                        }`}>
                          <ReactMarkdown remarkPlugins={[remarkGfm]}>
                            {response}
                          </ReactMarkdown>
                          {isLong && !isSuggestionExpanded && (
                            <div className="absolute bottom-0 left-0 right-0 h-10 bg-gradient-to-t from-amber-50 dark:from-amber-900/20 to-transparent pointer-events-none" />
                          )}
                        </div>
                        {isLong && (
                          <button
                            onClick={() => setIsSuggestionExpanded(!isSuggestionExpanded)}
                            className="flex items-center gap-1 mt-1 text-[10px] font-semibold text-amber-600 dark:text-amber-400 hover:text-amber-700 dark:hover:text-amber-300 transition-colors"
                          >
                            <ChevronDown className={`h-3 w-3 transition-transform ${isSuggestionExpanded ? "rotate-180" : ""}`} />
                            {isSuggestionExpanded ? "Show less" : "Show more"}
                          </button>
                        )}
                      </div>
                    );
                  })()}
                  {!message.result.smartSuggestion.response && message.result.smartSuggestion.rationale && (
                    <p className="text-[11px] text-gray-500 dark:text-gray-400">
                      {message.result.smartSuggestion.rationale}
                    </p>
                  )}
                  {message.result.smartSuggestion.documents && message.result.smartSuggestion.documents.length > 0 && (
                    <div className="flex items-center gap-1.5 pt-1 border-t border-amber-200/50 dark:border-amber-700/50">
                      <FileText className="h-3 w-3 text-amber-500" />
                      <span className="text-[10px] text-amber-600 dark:text-amber-400 font-medium">
                        {message.result.smartSuggestion.documents.length} document{message.result.smartSuggestion.documents.length !== 1 ? "s" : ""} added to panel
                      </span>
                    </div>
                  )}
                </div>
              </div>
            </motion.div>
          )}

          {message.type === "ai" && message.result?.nextSteps && message.result.nextSteps.length > 0 && (
            <motion.div
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.3 }}
              className="mt-3"
            >
              <div className="flex items-center gap-1.5 mb-2">
                <Zap className="h-3 w-3 text-blue-500" />
                <span className="text-[10px] font-bold text-gray-500 dark:text-gray-400 uppercase tracking-wide">
                  Next Steps
                </span>
              </div>
              <div className="space-y-1.5">
                {message.result.nextSteps.map((step: any, idx: number) => (
                  <motion.button
                    key={idx}
                    initial={{ opacity: 0, x: -8 }}
                    animate={{ opacity: 1, x: 0 }}
                    transition={{ delay: 0.35 + idx * 0.08 }}
                    onClick={() => onNextStepClick(step.query)}
                    className="w-full flex items-center gap-2.5 px-3 py-2 rounded-xl bg-gradient-to-r from-blue-50 to-indigo-50 dark:from-blue-900/20 dark:to-indigo-900/20 border border-blue-200 dark:border-blue-700 hover:border-blue-400 dark:hover:border-blue-500 hover:shadow-md transition-all group text-left"
                  >
                    <div className="h-6 w-6 rounded-lg bg-blue-500/10 flex items-center justify-center flex-shrink-0 group-hover:bg-blue-500/20 transition-colors">
                      <ArrowRight className="h-3 w-3 text-blue-600 dark:text-blue-400 group-hover:translate-x-0.5 transition-transform" />
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="text-xs font-semibold text-gray-800 dark:text-gray-200 truncate">
                        {step.query}
                      </p>
                      {step.rationale && (
                        <p className="text-[10px] text-gray-400 dark:text-gray-500 truncate">
                          {step.rationale}
                        </p>
                      )}
                    </div>
                    {step.confidence && (
                      <span className="text-[9px] font-bold px-1.5 py-0.5 rounded-full bg-blue-100 dark:bg-blue-800/40 text-blue-600 dark:text-blue-300 flex-shrink-0">
                        {Math.round(step.confidence * 100)}%
                      </span>
                    )}
                  </motion.button>
                ))}
              </div>
            </motion.div>
          )}

          {message.type === "ai" && message.debugData && (
            <div className="mt-3 flex justify-end">
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  onOpenDebug(message);
                }}
                className="flex items-center gap-1 text-[10px] text-gray-400 hover:text-blue-600 transition-colors"
                title="View API Debug Data"
              >
                <Info className="h-3 w-3" />
                <span>Debug</span>
              </button>
            </div>
          )}
        </div>
      </div>
    </motion.div>

      <AnimatePresence>
        {selectedDocument && (
          <>
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              onClick={() => setSelectedDocument(null)}
              className="fixed inset-0 bg-black/50 backdrop-blur-sm z-[100]"
            />
            <motion.div
              initial={{ opacity: 0, scale: 0.92, y: 20 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.92, y: 20 }}
              transition={{ type: "spring", damping: 25, stiffness: 300 }}
              className="fixed inset-4 md:inset-x-[15%] md:inset-y-[10%] bg-white dark:bg-gray-900 rounded-2xl shadow-2xl z-[101] overflow-hidden flex flex-col"
            >
              <div className="px-5 py-4 border-b border-gray-200 dark:border-gray-700 flex items-center justify-between bg-gradient-to-r from-purple-50 to-blue-50 dark:from-purple-900/20 dark:to-blue-900/20">
                <div className="flex items-center gap-3">
                  <div className="p-2 rounded-xl bg-purple-500/10">
                    <FileText className="h-5 w-5 text-purple-600 dark:text-purple-400" />
                  </div>
                  <div>
                    <h2 className="text-sm font-bold text-gray-800 dark:text-gray-200">
                      {selectedDocument.title || `Document ${selectedDocument.id}`}
                    </h2>
                    <div className="flex items-center gap-2 mt-0.5">
                      <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full ${
                        selectedDocument.type === "review"
                          ? "bg-amber-100 dark:bg-amber-900/40 text-amber-700 dark:text-amber-300"
                          : selectedDocument.type === "product"
                            ? "bg-blue-100 dark:bg-blue-900/40 text-blue-700 dark:text-blue-300"
                            : "bg-purple-100 dark:bg-purple-900/40 text-purple-700 dark:text-purple-300"
                      }`}>
                        {selectedDocument.type}
                      </span>
                      <span className="text-[10px] text-gray-400">ID: {selectedDocument.id}</span>
                      {selectedDocument.score != null && (
                        <span className="text-[10px] font-bold text-purple-600 dark:text-purple-400">
                          {(selectedDocument.score * 100).toFixed(1)}% relevance
                        </span>
                      )}
                    </div>
                  </div>
                </div>
                <button
                  onClick={() => setSelectedDocument(null)}
                  className="p-2 rounded-xl hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
                >
                  <XCircle className="h-5 w-5 text-gray-400 hover:text-gray-600" />
                </button>
              </div>

              <div className="flex-1 overflow-y-auto p-5">
                <div className="space-y-4">
                  <div>
                    <h3 className="text-xs font-bold text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-2">
                      Content
                    </h3>
                    <div className="p-4 bg-gray-50 dark:bg-gray-800/50 rounded-xl border border-gray-200 dark:border-gray-700">
                      <p className="text-sm text-gray-700 dark:text-gray-300 leading-relaxed whitespace-pre-wrap">
                        {selectedDocument.content}
                      </p>
                    </div>
                  </div>

                  {selectedDocument.metadata && Object.keys(selectedDocument.metadata).length > 0 && (
                    <div>
                      <h3 className="text-xs font-bold text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-2">
                        Metadata
                      </h3>
                      <div className="grid grid-cols-2 gap-2">
                        {Object.entries(selectedDocument.metadata)
                          .filter(([, v]) => v != null && v !== "")
                          .map(([key, value]) => (
                            <div
                              key={key}
                              className="p-2.5 bg-gray-50 dark:bg-gray-800/50 rounded-lg border border-gray-200 dark:border-gray-700"
                            >
                              <div className="text-[10px] font-semibold text-gray-400 dark:text-gray-500 uppercase">
                                {key}
                              </div>
                              <div className="text-xs text-gray-700 dark:text-gray-300 mt-0.5 break-all">
                                {String(value)}
                              </div>
                            </div>
                          ))}
                      </div>
                    </div>
                  )}

                  {(selectedDocument.score != null || selectedDocument.similarity != null) && (
                    <div>
                      <h3 className="text-xs font-bold text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-2">
                        Relevance Scores
                      </h3>
                      <div className="flex gap-3">
                        {selectedDocument.score != null && (
                          <div className="flex-1 p-3 bg-purple-50 dark:bg-purple-900/20 rounded-xl border border-purple-200 dark:border-purple-700 text-center">
                            <div className="text-2xl font-bold text-purple-600 dark:text-purple-400">
                              {(selectedDocument.score * 100).toFixed(1)}%
                            </div>
                            <div className="text-[10px] text-purple-500 font-medium mt-0.5">Score</div>
                          </div>
                        )}
                        {selectedDocument.similarity != null && (
                          <div className="flex-1 p-3 bg-blue-50 dark:bg-blue-900/20 rounded-xl border border-blue-200 dark:border-blue-700 text-center">
                            <div className="text-2xl font-bold text-blue-600 dark:text-blue-400">
                              {(selectedDocument.similarity * 100).toFixed(1)}%
                            </div>
                            <div className="text-[10px] text-blue-500 font-medium mt-0.5">Similarity</div>
                          </div>
                        )}
                      </div>
                    </div>
                  )}
                </div>
              </div>
            </motion.div>
          </>
        )}
      </AnimatePresence>
    </>
  );
}
