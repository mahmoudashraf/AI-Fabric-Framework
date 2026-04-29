import { useCallback, useEffect, useMemo, useRef, useState } from "react";

import { AlertCircle, Ban, Bot, CheckCircle2, HelpCircle, Info, XCircle, Zap } from "lucide-react";

import { useToast } from "@/hooks/use-toast";

import type { MaxModeMode, MaxModePosition, QuickAction } from "@/constants";
import { AI_SEARCH_CATEGORIES, BROWSE_PRODUCT_CATEGORIES, QUICK_ACTIONS, SEARCH_CATEGORIES } from "@/constants";
import {
  emitEvent,
  getWidgetConfig,
  getWidgetIdentity,
  isCartCrudEnabled,
  type MaxModeHostAttachment,
  type MaxModeHostConfig,
} from "@/config";
import { fetchRuntimeAuthContext, fetchRuntimeShellConfig } from "@/api/chat";
import type { ChatMessage, Document, ResultType, RuntimeAuthContextSummary, RuntimeShellConfigSummary } from "@/types";
import { useAttachmentsController } from "./useAttachmentsController";
import { useCartController } from "./useCartController";
import { useChatFlow } from "./useChatFlow";
import { useClarificationFlow } from "./useClarificationFlow";
import { useConfirmationFlow } from "./useConfirmationFlow";
import { useConversationsController } from "./useConversationsController";
import { useMaxModePersistence } from "./useMaxModePersistence";
import { useMaxModeViewSync } from "./useMaxModeViewSync";
import { useNewDocsPreviewActions } from "./useNewDocsPreviewActions";
import { useSearchControls } from "./useSearchControls";
import { useSuggestionsController } from "./useSuggestionsController";

const PENDING_PROMPTS_KEY = "maxmode_widget_pending_prompts";

type PendingPrompt = {
  id: string;
  message: string;
  open?: boolean;
  position?: MaxModePosition;
  mode?: MaxModeMode;
  requestContext?: Record<string, any>;
};

const CONVERSATION_MODES: MaxModeMode[] = ["navigator", "navigator_deep", "thinker_deep", "cart_assistant", "executor"];

function loadPendingPrompts(): PendingPrompt[] {
  try {
    const raw = sessionStorage.getItem(PENDING_PROMPTS_KEY);
    if (!raw) {
      return [];
    }
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function savePendingPrompts(prompts: PendingPrompt[]) {
  try {
    if (!prompts.length) {
      sessionStorage.removeItem(PENDING_PROMPTS_KEY);
      return;
    }
    sessionStorage.setItem(PENDING_PROMPTS_KEY, JSON.stringify(prompts));
  } catch {}
}

function removePendingPrompt(promptId: string | undefined) {
  if (!promptId) {
    return;
  }
  const remaining = loadPendingPrompts().filter((prompt) => prompt.id !== promptId);
  savePendingPrompts(remaining);
}

function expectedAuthModeForIntegrationMode(mode: string): string | null {
  switch (mode) {
    case "backend-mediated-private-runtime":
      return "PRIVATE_RUNTIME_BACKEND_MEDIATED";
    case "public-runtime-authenticated":
      return "PUBLIC_RUNTIME_AUTHENTICATED";
    case "public-runtime-anonymous":
      return "PUBLIC_RUNTIME_ANONYMOUS";
    default:
      return null;
  }
}

function validateRuntimeAuthContext(
  integrationMode: string,
  authContext: RuntimeAuthContextSummary,
): string | null {
  const expectedAuthMode = expectedAuthModeForIntegrationMode(integrationMode);
  if (expectedAuthMode && authContext.authMode !== expectedAuthMode) {
    return `Runtime auth mode mismatch. Expected ${expectedAuthMode} but received ${authContext.authMode || "none"}.`;
  }

  if (integrationMode === "public-runtime-anonymous" && authContext.subjectType !== "ANONYMOUS_SESSION") {
    return `Anonymous public mode requires subjectType ANONYMOUS_SESSION, but received ${authContext.subjectType || "none"}.`;
  }

  if (!authContext.subjectId?.trim()) {
    return "Runtime auth context did not return a verified subject identifier.";
  }

  return null;
}

function shellPromptMode(moduleId?: string): MaxModeMode {
  switch (moduleId) {
    case "cart":
    case "orders":
    case "purchase-orders":
    case "customer-account":
    case "addresses":
    case "support":
      return "executor";
    case "policies":
    case "reviews":
      return "navigator_deep";
    default:
      return "navigator";
  }
}

function shellPromptPosition(moduleId?: string): MaxModePosition {
  switch (moduleId) {
    case "cart":
    case "orders":
    case "purchase-orders":
    case "customer-account":
    case "addresses":
    case "support":
      return "cart";
    case "product-catalog":
    case "reviews":
      return "catalog";
    default:
      return "search";
  }
}

function shellPromptPalette(index: number) {
  const palettes = [
    { color: "text-blue-600", bg: "bg-blue-500/10", border: "border-blue-500/30" },
    { color: "text-green-600", bg: "bg-green-500/10", border: "border-green-500/30" },
    { color: "text-indigo-600", bg: "bg-indigo-500/10", border: "border-indigo-500/30" },
    { color: "text-orange-600", bg: "bg-orange-500/10", border: "border-orange-500/30" },
  ];
  return palettes[index % palettes.length];
}

function deriveQuickActions(
  hostConfig: MaxModeHostConfig | undefined,
  shellConfig: RuntimeShellConfigSummary | null,
): QuickAction[] {
  const hostStarterPrompts = hostConfig?.starterPrompts?.filter(
    (prompt) => prompt?.label?.trim() && prompt?.query?.trim(),
  );
  if (hostStarterPrompts?.length) {
    return hostStarterPrompts.map((prompt, index) => {
      const palette = shellPromptPalette(index);
      return {
        icon: Zap,
        label: prompt.label.trim(),
        query: prompt.query.trim(),
        color: palette.color,
        bg: palette.bg,
        border: palette.border,
        position: prompt.position ?? "search",
        mode: prompt.mode ?? "navigator",
      };
    });
  }

  const starterPrompts = shellConfig?.starterPrompts?.filter(
    (prompt) => prompt?.label?.trim() && prompt?.query?.trim(),
  );
  if (!starterPrompts?.length) {
    return QUICK_ACTIONS;
  }
  return starterPrompts.map((prompt, index) => {
    const palette = shellPromptPalette(index);
    return {
      icon: Zap,
      label: prompt.label!.trim(),
      query: prompt.query!.trim(),
      color: palette.color,
      bg: palette.bg,
      border: palette.border,
      position: shellPromptPosition(prompt.moduleId),
      mode: shellPromptMode(prompt.moduleId),
    };
  });
}

function deriveWelcomeMessage(hostConfig: MaxModeHostConfig | undefined, shellConfig: RuntimeShellConfigSummary | null) {
  const hostMessage = hostConfig?.welcomeMessage?.trim();
  if (hostMessage) {
    return hostMessage;
  }
  const title = shellConfig?.greetingTitle?.trim();
  const message = shellConfig?.greetingMessage?.trim();
  if (title && message) {
    return `${title}\n${message}`;
  }
  if (message) {
    return message;
  }
  return "👋 Welcome to MAX Mode - your AI-powered shopping assistant! I can help you find products, manage orders, apply coupons, and much more. Try the quick actions above or just ask me anything!";
}

function deriveStarterSuggestions(hostConfig: MaxModeHostConfig | undefined) {
  const suggestions = hostConfig?.starterSuggestions
    ?.map((value) => value?.trim())
    .filter((value): value is string => Boolean(value));
  if (suggestions?.length) {
    return suggestions.slice(0, 4);
  }
  return [];
}

function sanitizeRequestContext(hostConfig: MaxModeHostConfig | undefined) {
  if (!hostConfig?.requestContext || typeof hostConfig.requestContext !== "object") {
    return undefined;
  }
  return { ...hostConfig.requestContext };
}

function sanitizeConversationMode(value: string | null | undefined, fallback: MaxModeMode = "navigator"): MaxModeMode {
  if (typeof value !== "string") {
    return fallback;
  }
  const normalized = value.trim() as MaxModeMode;
  return CONVERSATION_MODES.includes(normalized) ? normalized : fallback;
}

function sanitizeAllowedConversationModes(
  values: MaxModeMode[] | undefined,
  fallback: MaxModeMode,
): MaxModeMode[] {
  const normalized = (values ?? [])
    .map((value) => sanitizeConversationMode(value, fallback))
    .filter((value, index, array) => array.indexOf(value) === index);
  if (!normalized.includes(fallback)) {
    normalized.push(fallback);
  }
  return normalized.length ? normalized : [fallback];
}

function sanitizePageModeMappings(
  values: Record<string, MaxModeMode> | undefined,
  allowedConversationModes: MaxModeMode[],
): Record<string, MaxModeMode> {
  const normalized: Record<string, MaxModeMode> = {};
  if (!values || typeof values !== "object") {
    return normalized;
  }
  Object.entries(values).forEach(([key, value]) => {
    const normalizedKey = key.trim().toLowerCase();
    const normalizedMode = sanitizeConversationMode(value);
    if (!normalizedKey || !allowedConversationModes.includes(normalizedMode)) {
      return;
    }
    normalized[normalizedKey] = normalizedMode;
  });
  return normalized;
}

function pageModeGroupToPosition(pageGroup: string | null | undefined): MaxModePosition {
  switch ((pageGroup ?? "").trim().toLowerCase()) {
    case "product":
    case "collection":
    case "content":
      return "catalog";
    case "search":
      return "search";
    case "cart":
    case "account":
      return "cart";
    default:
      return "landing";
  }
}

function derivePageModeGroup(hostRequestContext: Record<string, any> | undefined): string | null {
  if (!hostRequestContext || typeof hostRequestContext !== "object") {
    return null;
  }
  const explicitGroup = hostRequestContext.shopifyPageModeGroup;
  if (typeof explicitGroup === "string" && explicitGroup.trim()) {
    return explicitGroup.trim().toLowerCase();
  }
  const pageType = hostRequestContext.pageType;
  if (typeof pageType !== "string" || !pageType.trim()) {
    return null;
  }
  const normalized = pageType.trim().toLowerCase();
  if (normalized === "product") {
    return "product";
  }
  if (normalized === "collection" || normalized === "list-collections") {
    return "collection";
  }
  if (normalized === "search") {
    return "search";
  }
  if (normalized === "cart") {
    return "cart";
  }
  if (
    normalized === "customers/account" ||
    normalized === "customers/login" ||
    normalized === "customers/register" ||
    normalized === "customers/order" ||
    normalized === "account" ||
    normalized === "orders"
  ) {
    return "account";
  }
  if (normalized === "article" || normalized === "blog" || normalized === "page") {
    return "content";
  }
  return "landing";
}

function sanitizeHostAttachments(hostAttachments: MaxModeHostAttachment[] | undefined) {
  if (!hostAttachments?.length) {
    return [];
  }
  return hostAttachments
    .filter((attachment) => attachment && attachment.type?.trim() && attachment.data && typeof attachment.data === "object")
    .map((attachment) => ({
      type: attachment.type.trim(),
      data: attachment.data,
    }));
}

export function useMaxModeController({
  isOpen,
  assistantLabel,
  showUtilityPanel,
}: {
  isOpen: boolean;
  assistantLabel?: string;
  showUtilityPanel?: boolean;
}) {
  const { toast } = useToast();
  const widgetConfig = getWidgetConfig();
  const hostConfig = widgetConfig.host;
  const debugEnabled = widgetConfig.features?.debug === true;
  const resolvedAssistantLabel = assistantLabel?.trim() || hostConfig?.assistantLabel?.trim() || "MAX AI";
  const resolvedShowUtilityPanel = showUtilityPanel ?? (hostConfig?.showUtilityPanel ?? true);
  const hostStarterSuggestions = useMemo(() => deriveStarterSuggestions(hostConfig), [hostConfig]);
  const hostRequestContext = useMemo(() => sanitizeRequestContext(hostConfig), [hostConfig]);
  const hostInitialAttachments = useMemo(() => sanitizeHostAttachments(hostConfig?.initialAttachments), [hostConfig]);
  const pageModeGroup = useMemo(() => derivePageModeGroup(hostRequestContext), [hostRequestContext]);
  const defaultConversationMode = useMemo(
    () => sanitizeConversationMode(hostConfig?.defaultConversationMode, "navigator"),
    [hostConfig?.defaultConversationMode],
  );
  const allowedConversationModes = useMemo(
    () => sanitizeAllowedConversationModes(hostConfig?.allowedConversationModes, defaultConversationMode),
    [hostConfig?.allowedConversationModes, defaultConversationMode],
  );
  const pageModeMappings = useMemo(
    () => sanitizePageModeMappings(hostConfig?.pageModeMappings, allowedConversationModes),
    [allowedConversationModes, hostConfig?.pageModeMappings],
  );
  const effectiveConversationMode = useMemo(() => {
    const hostEffectiveMode = sanitizeConversationMode(hostConfig?.effectiveConversationMode, defaultConversationMode);
    if (allowedConversationModes.includes(hostEffectiveMode)) {
      return hostEffectiveMode;
    }
    const mappedMode = pageModeGroup ? pageModeMappings[pageModeGroup] : undefined;
    if (mappedMode && allowedConversationModes.includes(mappedMode)) {
      return mappedMode;
    }
    return defaultConversationMode;
  }, [
    allowedConversationModes,
    defaultConversationMode,
    hostConfig?.effectiveConversationMode,
    pageModeGroup,
    pageModeMappings,
  ]);
  const initialPosition = useMemo(() => pageModeGroupToPosition(pageModeGroup), [pageModeGroup]);

  const [runtimeShellConfig, setRuntimeShellConfig] = useState<RuntimeShellConfigSummary | null>(null);
  const quickActions = useMemo(() => deriveQuickActions(hostConfig, runtimeShellConfig), [hostConfig, runtimeShellConfig]);
  const searchCategories = SEARCH_CATEGORIES;
  const aiSearchCategories = AI_SEARCH_CATEGORIES;
  const browseProductCategories = BROWSE_PRODUCT_CATEGORIES;

  const [chatQuery, setChatQuery] = useState("");
  const [chatMessages, setChatMessages] = useState<ChatMessage[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [currentConversationId, setCurrentConversationId] = useState<string | null>(null);
  const [attachedItems, setAttachedItems] = useState<Array<{ type: string; data: any }>>([]);
  const [contextDocuments, setContextDocuments] = useState<Document[]>([]);
  const [focusedMessageId, setFocusedMessageId] = useState<string | null>(null);
  const [isPanelVisible, setIsPanelVisible] = useState(true);
  const [expandedActions, setExpandedActions] = useState<{ [key: string]: number }>({});
  const [collectingItem, setCollectingItem] = useState<{ title: string; type: string } | null>(null);
  const [isQuickActionsOpen, setIsQuickActionsOpen] = useState(false);
  const [isBottomSheetOpen, setIsBottomSheetOpen] = useState(false);
  const [isInputFocused, setIsInputFocused] = useState(false);
  const [confirmationStatus, setConfirmationStatus] = useState<{ [key: string]: 'pending' | 'confirmed' | 'rejected' }>({});
  const [isAISearchOpen, setIsAISearchOpen] = useState(false);
  const [isFloatingMenuCollapsed, setIsFloatingMenuCollapsed] = useState(false);
  const [newDocuments, setNewDocuments] = useState<Document[]>([]);
  const [isNewDocsPreviewOpen, setIsNewDocsPreviewOpen] = useState(false);
  const [viewedDocumentIds, setViewedDocumentIds] = useState<Set<string>>(new Set());
  // Position state for routing
  const [currentPosition, setCurrentPosition] = useState<MaxModePosition>(initialPosition);
  const [currentMode, setCurrentMode] = useState<MaxModeMode>(effectiveConversationMode);

  // Debug modal state - stores last request/response for inspection
  const [isDebugModalOpen, setIsDebugModalOpen] = useState(false);
  const [lastRequestData, setLastRequestData] = useState<any>(null);
  const [lastResponseData, setLastResponseData] = useState<any>(null);
  // Per-message debug data - when set, shows debug for specific message
  const [selectedDebugMessage, setSelectedDebugMessage] = useState<ChatMessage | null>(null);
  // Full JSON panel expansion state
  // Query expansion state for RAG debug
  // Search category submenu state
  const [isSearchCategoryOpen, setIsSearchCategoryOpen] = useState(false);
  const [isBrowseProductsOpen, setIsBrowseProductsOpen] = useState(false);
  const [searchCategory, setSearchCategory] = useState<string | null>(null);
  const cartEnabled = isCartCrudEnabled(widgetConfig);
  const identity = useMemo(
    () => getWidgetIdentity(),
    [widgetConfig.integrationMode],
  );
  const authContextProbeKeyRef = useRef<string | null>(null);
  const authContextProbeInFlightRef = useRef(false);
  const shellConfigProbeKeyRef = useRef<string | null>(null);
  const shellConfigProbeInFlightRef = useRef(false);
  const pendingPromptFlushInFlightRef = useRef(false);

  const {
    suggestions,
    setSuggestions,
    isLoadingSuggestions,
    showSuggestions,
    setShowSuggestions,
    shownSuggestions,
    setShownSuggestions,
  } = useSuggestionsController({
    attachedItems,
    starterSuggestions: hostStarterSuggestions,
    requestContext: hostRequestContext,
  });

  const {
    cartData,
    isCartView,
    selectedProduct,
    addToCart,
    fetchCart,
    removeFromCart,
    openCart,
    closeCart,
    openProductDetails,
    closeProductDetails,
  } = useCartController({
    enabled: cartEnabled,
    toast,
    setIsBottomSheetOpen,
    setIsPanelVisible,
  });

  const {
    isConversationsOpen,
    setIsConversationsOpen,
    conversations,
    isLoadingConversations,
    isViewingOldConversation,
    oldConversationLocked,
    loadConversations,
    openConversation,
    handleDeleteConversation,
    startNewConversation,
    openConversationsPanel,
  } = useConversationsController({
    isOpen,
    chatMessagesLength: chatMessages.length,
    currentConversationId,
    setCurrentConversationId,
    setChatMessages,
    setIsLoading,
    setAttachedItems,
    setSuggestions,
    setContextDocuments,
    toast,
  });

  const { handleChatQuery } = useChatFlow({
    chatQuery,
    setChatQuery,
    chatMessagesLength: chatMessages.length,
    setChatMessages,
    attachedItems,
    searchCategory,
    currentConversationId,
    setCurrentConversationId,
    setIsLoading,
    setSuggestions,
    setCurrentPosition,
    setCurrentMode,
    setLastRequestData,
    setLastResponseData,
    setSelectedDebugMessage,
    currentPosition,
    currentMode,
    requestContext: hostRequestContext,
  });

  const handleProgrammaticPrompt = useCallback(
    async (prompt: PendingPrompt | null | undefined) => {
      if (!prompt?.message?.trim()) {
        return;
      }
      await handleChatQuery(prompt.message.trim(), prompt.position, prompt.mode, prompt.requestContext);
    },
    [handleChatQuery],
  );

  const { handleConfirmation } = useConfirmationFlow({
    attachedItems,
    currentConversationId,
    setConfirmationStatus,
    setChatMessages,
    setContextDocuments,
    setCurrentConversationId,
    setIsLoading,
    toast,
  });

  useEffect(() => {
    if (!isOpen) {
      return;
    }
    if (pendingPromptFlushInFlightRef.current) {
      return;
    }
    const queued = loadPendingPrompts();
    if (queued.length === 0) {
      return;
    }

    let cancelled = false;
    pendingPromptFlushInFlightRef.current = true;
    savePendingPrompts([]);

    void (async () => {
      try {
        for (const prompt of queued) {
          if (cancelled) {
            break;
          }
          await handleProgrammaticPrompt(prompt);
        }
      } finally {
        pendingPromptFlushInFlightRef.current = false;
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [handleProgrammaticPrompt, isOpen]);

  useEffect(() => {
    function onProgrammaticPrompt(event: Event) {
      const detail = (event as CustomEvent<PendingPrompt>).detail;
      if (!detail || !detail.message?.trim() || !isOpen) {
        return;
      }
      removePendingPrompt(detail.id);
      void handleProgrammaticPrompt(detail);
    }

    window.addEventListener("maxmode:send-message", onProgrammaticPrompt as EventListener);
    return () => {
      window.removeEventListener("maxmode:send-message", onProgrammaticPrompt as EventListener);
    };
  }, [handleProgrammaticPrompt, isOpen]);

  const { handleClarificationSubmit } = useClarificationFlow({
    attachedItems,
    currentConversationId,
    setChatMessages,
    setContextDocuments,
    setCurrentConversationId,
    setIsLoading,
    toast,
  });

  const messagesEndRef = useRef<HTMLDivElement>(null);
  const latestMessageRef = useRef<HTMLDivElement>(null);
  const contextPanelRef = useRef<HTMLDivElement>(null);
  const contextPanelEndRef = useRef<HTMLDivElement>(null);
  const chatInputRef = useRef<HTMLTextAreaElement>(null);
  const aiSearchRowRef = useRef<HTMLDivElement>(null);
  const aiSearchButtonRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!isOpen) {
      authContextProbeKeyRef.current = null;
      authContextProbeInFlightRef.current = false;
      return;
    }

    const shouldProbe = widgetConfig.apiConfig.runtimeAuth?.probeAuthContextOnOpen ?? true;
    if (!shouldProbe) {
      return;
    }

    const probeKey = JSON.stringify({
      integrationMode: identity.integrationMode,
      chatBaseUrl: widgetConfig.apiConfig.chatBaseUrl,
      authContextUrl:
        widgetConfig.apiConfig.runtimeRoutes?.authContextUrl
        ?? widgetConfig.apiConfig.runtimeAuth?.authContextUrl
        ?? null,
    });

    if (authContextProbeInFlightRef.current || authContextProbeKeyRef.current === probeKey) {
      return;
    }

    authContextProbeInFlightRef.current = true;
    authContextProbeKeyRef.current = probeKey;
    let cancelled = false;

    const reportProbeError = (message: string, authContext?: RuntimeAuthContextSummary) => {
      if (cancelled) {
        return;
      }
      toast({
        title: "Runtime Auth Misconfigured",
        description: message,
        variant: "destructive",
      });
      emitEvent("error", {
        code: "runtime-auth-context-probe-failed",
        message,
        integrationMode: identity.integrationMode,
        authContext,
      });
    };

    void (async () => {
      try {
        const authContext = await fetchRuntimeAuthContext();
        if (cancelled) {
          return;
        }
        const validationError = validateRuntimeAuthContext(identity.integrationMode, authContext);
        if (validationError) {
          reportProbeError(validationError, authContext);
          return;
        }
        if (authContext.warnings?.length) {
          emitEvent("error", {
            code: "runtime-auth-context-warning",
            message: authContext.warnings.join(" "),
            integrationMode: identity.integrationMode,
            authContext,
          });
        }
      } catch (error) {
        reportProbeError(
          error instanceof Error
            ? error.message
            : "Runtime auth-context probe failed before chat initialization.",
        );
      } finally {
        authContextProbeInFlightRef.current = false;
      }
    })();

    return () => {
      cancelled = true;
      authContextProbeInFlightRef.current = false;
    };
  }, [
    isOpen,
    identity.integrationMode,
    widgetConfig.apiConfig.chatBaseUrl,
    widgetConfig.apiConfig.runtimeRoutes?.authContextUrl,
    widgetConfig.apiConfig.runtimeAuth?.authContextUrl,
    widgetConfig.apiConfig.runtimeAuth?.probeAuthContextOnOpen,
    toast,
  ]);

  useEffect(() => {
    if (!isOpen) {
      return;
    }

    const shouldProbeShellConfig = widgetConfig.apiConfig.probeShellConfigOnOpen ?? true;
    if (!shouldProbeShellConfig) {
      return;
    }

    const probeKey = JSON.stringify({
      integrationMode: identity.integrationMode,
      chatBaseUrl: widgetConfig.apiConfig.chatBaseUrl,
      shellConfigUrl: widgetConfig.apiConfig.runtimeRoutes?.shellConfigUrl ?? null,
    });

    if (shellConfigProbeInFlightRef.current || shellConfigProbeKeyRef.current === probeKey) {
      return;
    }

    shellConfigProbeInFlightRef.current = true;
    shellConfigProbeKeyRef.current = probeKey;
    let cancelled = false;

    void (async () => {
      try {
        const shellConfig = await fetchRuntimeShellConfig();
        if (!cancelled) {
          setRuntimeShellConfig(shellConfig ?? null);
        }
      } catch (error) {
        if (!cancelled) {
          emitEvent("error", {
            code: "runtime-shell-config-probe-failed",
            message: error instanceof Error ? error.message : "Runtime shell-config probe failed.",
            integrationMode: identity.integrationMode,
          });
        }
      } finally {
        shellConfigProbeInFlightRef.current = false;
      }
    })();

    return () => {
      cancelled = true;
      shellConfigProbeInFlightRef.current = false;
    };
  }, [
    isOpen,
    identity.integrationMode,
    widgetConfig.apiConfig.chatBaseUrl,
    widgetConfig.apiConfig.probeShellConfigOnOpen,
    widgetConfig.apiConfig.runtimeRoutes?.shellConfigUrl,
  ]);

  useMaxModePersistence({
    chatMessages,
    setChatMessages,
    attachedItems,
    setAttachedItems,
    currentPosition,
    setCurrentPosition,
    currentMode,
    setCurrentMode,
    currentConversationId,
    setCurrentConversationId,
    contextDocuments,
    setContextDocuments,
    hostInitialAttachments,
  });

  useMaxModeViewSync({
    isOpen,
    chatMessages,
    setChatMessages,
    latestMessageRef,
    chatInputRef,
    isAISearchOpen,
    setIsAISearchOpen,
    aiSearchRowRef,
    aiSearchButtonRef,
    isPanelVisible,
    contextPanelRef,
    contextPanelEndRef,
    setFocusedMessageId,
    setContextDocuments,
    setNewDocuments,
    setIsNewDocsPreviewOpen,
    setViewedDocumentIds,
    welcomeContent: deriveWelcomeMessage(hostConfig, runtimeShellConfig),
  });

  const {
    isItemAttached,
    handleReattachItem,
    handleAttachActionResultItem,
    handleAttachDocument,
    removeNonAiAttachmentByIndex,
    removeAiSearchAttachment,
  } = useAttachmentsController({
    attachedItems,
    setAttachedItems,
    setCollectingItem,
    setCurrentPosition,
    setCurrentMode,
    chatInputRef,
    toast,
  });

  useEffect(() => {
    function onAttachItem(event: Event) {
      if (!isOpen) {
        return;
      }
      const detail = (event as CustomEvent<{ item?: { type: string; data: any } }>).detail;
      if (!detail?.item?.type || !detail.item.data || typeof detail.item.data !== "object") {
        return;
      }
      handleReattachItem(detail.item);
    }

    window.addEventListener("maxmode:attach-item", onAttachItem as EventListener);
    return () => {
      window.removeEventListener("maxmode:attach-item", onAttachItem as EventListener);
    };
  }, [handleReattachItem, isOpen]);

  const { handleAISearchCategory, handleSelectSearchCategory, clearSearchCategory } = useSearchControls({
    aiSearchCategories,
    setAttachedItems,
    setIsAISearchOpen,
    setSearchCategory,
    setCurrentPosition,
    setCurrentMode,
    chatInputRef,
    toast,
  });

  const { handleOpenBottomSheet, handleCloseNewDocsPreview } = useNewDocsPreviewActions({
    newDocuments,
    setIsBottomSheetOpen,
    setIsNewDocsPreviewOpen,
    setNewDocuments,
    setViewedDocumentIds,
  });

  const openDebugInspector = useCallback((message?: ChatMessage) => {
    if (!debugEnabled) {
      return;
    }
    if (message) setSelectedDebugMessage(message);
    setIsDebugModalOpen(true);
  }, [debugEnabled]);

  const closeDebugInspector = useCallback(() => {
    setIsDebugModalOpen(false);
    setSelectedDebugMessage(null);
  }, []);

  const resendChatQuery = useCallback(
    async (fullMessage: string) => {
      setChatQuery(fullMessage);
      await handleChatQuery(fullMessage);
    },
    [handleChatQuery],
  );

  const reattachItemWithToast = useCallback(
    (item: { type: string; data: any }, isAlreadyAttached: boolean) => {
      if (isAlreadyAttached) {
        toast({
          title: "Already Attached",
          description: `This item is already attached to chat`,
          variant: "default",
        });
        return;
      }
      handleReattachItem(item);
      toast({
        title: "💬 Re-attached to Chat",
        description: `Item is now part of the conversation`,
      });
    },
    [handleReattachItem, toast],
  );

  const openSourcesMobile = useCallback((messageId: string) => {
    setIsBottomSheetOpen(true);
    setFocusedMessageId(messageId);
  }, []);

  const openSourcesDesktop = useCallback((messageId: string) => {
    setIsPanelVisible(true);
    setFocusedMessageId(messageId);
  }, []);

  const expandActionResults = useCallback((messageId: string, nextCount: number) => {
    setExpandedActions((prev) => ({
      ...prev,
      [messageId]: nextCount,
    }));
  }, []);

  const dismissSuggestions = useCallback(() => {
    setShowSuggestions(false);
    setShownSuggestions((prev) => new Set([...prev, ...suggestions]));
  }, [suggestions]);

  const selectSuggestion = useCallback(
    async (suggestion: string) => {
      setShownSuggestions((prev) => new Set([...prev, suggestion]));
      await handleChatQuery(suggestion);
    },
    [handleChatQuery],
  );

  const attachCartToChat = useCallback(() => {
    if (!cartData || !cartData.items || cartData.items.length === 0) return;
    const cartAttachment = {
      type: "cart",
      data: {
        title: `Cart (${cartData.items.length} items - $${cartData.total?.toFixed(2)})`,
        items: cartData.items,
        subtotal: cartData.subtotal,
        discount: cartData.discount,
        total: cartData.total,
        couponCode: cartData.couponCode,
      },
    };
    setAttachedItems((prev) => [...prev, cartAttachment]);
    setCurrentPosition("cart");
    setCurrentMode("cart_assistant");
    toast({
      title: "💬 Cart Added to Chat",
      description: "Your cart details are now part of the conversation",
    });
  }, [cartData, toast]);

  const proceedToCheckoutFromCart = useCallback(
    async ({ closeCartAfter }: { closeCartAfter: boolean }) => {
      setChatQuery("Checkout my cart");
      await handleChatQuery("Checkout my cart", "cart", "executor");
      if (closeCartAfter) closeCart();
    },
    [handleChatQuery, closeCart],
  );

  const browseProductsFromCart = useCallback(async () => {
    closeCart();
    setChatQuery("Show me available products");
    await handleChatQuery("Show me available products", "search", "navigator");
  }, [handleChatQuery, closeCart]);

  const handleQuickAction = (query: string, position?: MaxModePosition, mode?: MaxModeMode) => {
    setChatQuery(query);
    setTimeout(() => handleChatQuery(query, position, mode), 100);
  };

  const showSampleDocuments = () => {
    const sampleDocs: Document[] = [
      {
        id: "1",
        title: "Return Policy",
        content: "You can return items within 30 days of purchase. Items must be in original condition with tags attached.",
        type: "policy",
        metadata: { category: "returns", updated: "2024-01-15" }
      },
      {
        id: "2",
        title: "Wireless Headphones Pro",
        content: "Premium noise-canceling headphones with 30-hour battery life and crystal-clear audio.",
        type: "product",
        metadata: { price: "$299.99", stock: "In Stock", imageUrl: "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=400&h=300&fit=crop" }
      },
      {
        id: "3",
        title: "Shipping Information",
        content: "Free shipping on orders over $50. Standard delivery takes 3-5 business days.",
        type: "document",
        metadata: { region: "US", type: "shipping" }
      }
    ];
    setContextDocuments(sampleDocs);
    toast({
      title: "✨ Sample Documents Loaded",
      description: "Check the right panel!",
    });
  };

  const getResultStyles = (resultType?: ResultType) => {
    switch (resultType) {
      case "ACTION_EXECUTED":
        return { icon: CheckCircle2, bg: "bg-green-500/10", border: "border-green-500/30", text: "text-green-700", iconColor: "text-green-600", label: "Action Executed" };
      case "ACTION_DENIED":
        return { icon: Ban, bg: "bg-red-500/10", border: "border-red-500/30", text: "text-red-700", iconColor: "text-red-600", label: "Action Denied" };
      case "INFORMATION_PROVIDED":
        return { icon: Info, bg: "bg-muted", border: "border-transparent", text: "text-foreground", iconColor: "text-muted-foreground", label: "Information", hideBadge: true };
      case "CONFIRMATION_REQUIRED":
        return { icon: HelpCircle, bg: "bg-yellow-500/10", border: "border-yellow-500/30", text: "text-yellow-700", iconColor: "text-yellow-600", label: "Confirmation Required" };
      case "CLARIFICATION_REQUIRED":
        return { icon: AlertCircle, bg: "bg-orange-500/10", border: "border-orange-500/30", text: "text-orange-700", iconColor: "text-orange-600", label: "Clarification Needed" };
      case "COMPOUND_HANDLED":
        return { icon: Zap, bg: "bg-purple-500/10", border: "border-purple-500/30", text: "text-purple-700", iconColor: "text-purple-600", label: "Compound Action" };
      case "ERROR":
        return { icon: XCircle, bg: "bg-red-500/10", border: "border-red-500/30", text: "text-red-700", iconColor: "text-red-600", label: "Error" };
      default:
        return { icon: Bot, bg: "bg-muted", border: "border-transparent", text: "text-foreground", iconColor: "text-muted-foreground", label: "Response", hideBadge: true };
    }
  };
  return {
    toast,
    // core ui/state
    chatQuery,
    setChatQuery,
    chatMessages,
    setChatMessages,
    isLoading,
    setIsLoading,
    currentConversationId,
    setCurrentConversationId,
    attachedItems,
    setAttachedItems,
    contextDocuments,
    setContextDocuments,
    focusedMessageId,
    setFocusedMessageId,
    isPanelVisible,
    setIsPanelVisible,
    expandedActions,
    setExpandedActions,
    suggestions,
    setSuggestions,
    isLoadingSuggestions,
    showSuggestions,
    setShowSuggestions,
    shownSuggestions,
    setShownSuggestions,
    identity,
    assistantLabel: resolvedAssistantLabel,
    showUtilityPanel: resolvedShowUtilityPanel,
    collectingItem,
    setCollectingItem,
    isQuickActionsOpen,
    setIsQuickActionsOpen,
    isBottomSheetOpen,
    setIsBottomSheetOpen,
    isInputFocused,
    setIsInputFocused,
    isSearchCategoryOpen,
    setIsSearchCategoryOpen,
    searchCategory,
    setSearchCategory,
    currentPosition,
    setCurrentPosition,
    currentMode,
    setCurrentMode,
    allowedConversationModes,
    debugEnabled,
    cartEnabled,
    isDebugModalOpen,
    setIsDebugModalOpen,
    selectedDebugMessage,
    setSelectedDebugMessage,
    lastRequestData,
    setLastRequestData,
    lastResponseData,
    setLastResponseData,
    isConversationsOpen,
    setIsConversationsOpen,
    conversations,
    isLoadingConversations,
    isViewingOldConversation,
    oldConversationLocked,
    confirmationStatus,
    setConfirmationStatus,
    selectedProduct,
    isCartView,
    cartData,
    viewedDocumentIds,
    setViewedDocumentIds,
    newDocuments,
    setNewDocuments,
    isNewDocsPreviewOpen,
    setIsNewDocsPreviewOpen,
    isAISearchOpen,
    setIsAISearchOpen,
    isFloatingMenuCollapsed,
    setIsFloatingMenuCollapsed,
    isBrowseProductsOpen,
    setIsBrowseProductsOpen,
    // refs
    chatInputRef,
    latestMessageRef,
    messagesEndRef,
    contextPanelRef,
    contextPanelEndRef,
    aiSearchRowRef,
    aiSearchButtonRef,
    // derived constants
    quickActions,
    searchCategories,
    aiSearchCategories,
    browseProductCategories,
    // helpers/handlers
    getResultStyles,
    isItemAttached,
    handleReattachItem,
    handleAttachActionResultItem,
    clearSearchCategory,
    loadConversations,
    openConversation,
    handleDeleteConversation,
    startNewConversation,
    openConversationsPanel,
    handleQuickAction,
    handleChatQuery,
    handleConfirmation: (messageId: string, confirmed: boolean, _message: ChatMessage) => handleConfirmation(messageId, confirmed),
    handleClarificationSubmit,
    addToCart,
    fetchCart,
    removeFromCart,
    openCart,
    closeCart,
    openProductDetails,
    closeProductDetails,
    handleAttachDocument,
    handleAISearchCategory,
    handleOpenBottomSheet,
    handleCloseNewDocsPreview,
    showSampleDocuments,
    handleSelectSearchCategory: (category: string) =>
      handleSelectSearchCategory(category, {
        closeMenus: () => {
          setIsSearchCategoryOpen(false);
          setIsQuickActionsOpen(false);
        },
      }),
    // view helpers (reduce UI wiring in MaxModeView)
    openDebugInspector,
    closeDebugInspector,
    resendChatQuery,
    reattachItemWithToast,
    openSourcesMobile,
    openSourcesDesktop,
    expandActionResults,
    removeNonAiAttachmentByIndex,
    removeAiSearchAttachment,
    dismissSuggestions,
    selectSuggestion,
    attachCartToChat,
    proceedToCheckoutFromCart,
    browseProductsFromCart,
  } as const;
}

export type MaxModeController = ReturnType<typeof useMaxModeController>;
