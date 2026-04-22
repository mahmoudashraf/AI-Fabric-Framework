import { useCallback, useEffect, useMemo, useRef, useState } from "react";

import { AlertCircle, Ban, Bot, CheckCircle2, HelpCircle, Info, XCircle, Zap } from "lucide-react";

import { useToast } from "@/hooks/use-toast";

import type { QuickAction } from "@/constants";
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

function shellPromptMode(moduleId?: string): "navigator" | "navigator_deep" | "cart_assistant" | "executor" {
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

function shellPromptPosition(moduleId?: string): "landing" | "catalog" | "search" | "cart" {
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
  const resolvedAssistantLabel = assistantLabel?.trim() || hostConfig?.assistantLabel?.trim() || "MAX AI";
  const resolvedShowUtilityPanel = showUtilityPanel ?? (hostConfig?.showUtilityPanel ?? true);
  const hostStarterSuggestions = useMemo(() => deriveStarterSuggestions(hostConfig), [hostConfig]);
  const hostRequestContext = useMemo(() => sanitizeRequestContext(hostConfig), [hostConfig]);
  const hostInitialAttachments = useMemo(() => sanitizeHostAttachments(hostConfig?.initialAttachments), [hostConfig]);

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
  const [currentPosition, setCurrentPosition] = useState<"landing" | "catalog" | "search" | "cart">("landing");
  const [currentMode, setCurrentMode] = useState<"navigator" | "navigator_deep" | "cart_assistant" | "executor">("navigator");

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
    if (message) setSelectedDebugMessage(message);
    setIsDebugModalOpen(true);
  }, []);

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

  const handleQuickAction = (query: string, position?: "landing" | "catalog" | "search" | "cart", mode?: "navigator" | "navigator_deep" | "cart_assistant" | "executor") => {
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
