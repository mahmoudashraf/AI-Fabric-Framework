import { useCallback, useEffect, useMemo, useRef, useState } from "react";

import {
  AlertCircle,
  Ban,
  Bot,
  Calendar,
  CheckCircle2,
  FileText,
  GitCompare,
  HelpCircle,
  Info,
  MapPin,
  Phone,
  Search,
  Shield,
  Sparkles,
  XCircle,
  Zap,
} from "lucide-react";

import { useToast } from "@/hooks/use-toast";

import type { MaxModeMode, MaxModePosition, QuickAction } from "@/constants";
import { AI_SEARCH_CATEGORIES, BROWSE_PRODUCT_CATEGORIES, QUICK_ACTIONS, SEARCH_CATEGORIES } from "@/constants";
import {
  emitEvent,
  getWidgetConfig,
  isCartCrudEnabled,
  type MaxModeHostAttachment,
  type MaxModeHostConfig,
  type MaxModeHostStarterPrompt,
  type MaxModeHostStarterPromptIcon,
  type MaxModeToolScope,
  type MaxModeWidgetConfig,
} from "@/config";
import { fetchRuntimeAuthContext, fetchRuntimeShellConfig } from "@/api/chat";
import {
  isPublicRuntimeSessionInvalidatedError,
  subscribePublicRuntimeSessionInvalidation,
} from "@/api/client";
import type { MaxModePresentationResultReference } from "@/actionPresentation";
import { presentationReferenceAttachmentId } from "@/actionPresentation";
import { ACTION_RESULT_ATTACHMENT_TYPE, toActionResultAttachedItem } from "@/attachments";
import { buildCustomerAccountConnectUrl } from "@/chatResult";
import { useMaxModeContextOptional } from "@/context";
import type {
  ChatMessage,
  CustomerAccountConnectAction,
  Document,
  ResultType,
  RuntimeAuthContextSummary,
  RuntimeShellConfigSummary,
} from "@/types";
import { useAttachmentsController } from "./useAttachmentsController";
import { useCartController } from "./useCartController";
import { useChatFlow } from "./useChatFlow";
import { useClarificationFlow } from "./useClarificationFlow";
import { useConfirmationFlow } from "./useConfirmationFlow";
import { useConversationsController } from "./useConversationsController";
import { useCurrentPageAttachment } from "./useCurrentPageAttachment";
import { useMaxModePersistence } from "./useMaxModePersistence";
import { useMaxModeViewSync } from "./useMaxModeViewSync";
import { useNewDocsPreviewActions } from "./useNewDocsPreviewActions";
import { useSearchControls } from "./useSearchControls";
import { useSuggestionsController } from "./useSuggestionsController";

const PENDING_PROMPTS_KEY = "maxmode_widget_pending_prompts";
const PENDING_ATTACHMENTS_KEY = "maxmode_widget_pending_attachments";

type PendingPrompt = {
  id: string;
  message: string;
  open?: boolean;
  position?: MaxModePosition;
  mode?: MaxModeMode;
  requestContext?: Record<string, any>;
  attachments?: Array<{ type: string; data: any }>;
};

const CONVERSATION_MODES: MaxModeMode[] = ["conversational", "navigator", "navigator_deep", "thinker_deep", "cart_assistant", "executor"];

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

function consumePendingAttachments(attachedItems: Array<{ type: string; data: any }>) {
  try {
    const parsed = JSON.parse(sessionStorage.getItem(PENDING_ATTACHMENTS_KEY) || "[]");
    if (!Array.isArray(parsed) || parsed.length === 0) {
      return;
    }
    const remaining = parsed.filter((queued) => !attachedItems.some((attached) =>
      sameAttachment(attached, queued),
    ));
    if (remaining.length === 0) {
      sessionStorage.removeItem(PENDING_ATTACHMENTS_KEY);
    } else if (remaining.length !== parsed.length) {
      sessionStorage.setItem(PENDING_ATTACHMENTS_KEY, JSON.stringify(remaining));
    }
  } catch {}
}

function sameAttachment(
  first: { type?: string; data?: any } | null | undefined,
  second: { type?: string; data?: any } | null | undefined,
) {
  if (!first?.type || first.type !== second?.type) {
    return false;
  }
  const firstId = first.data?.id;
  const secondId = second.data?.id;
  if (firstId && secondId) {
    return firstId === secondId;
  }
  const firstSku = first.data?.sku;
  const secondSku = second.data?.sku;
  return Boolean(firstSku && secondSku && firstSku === secondSku);
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

const HOST_PROMPT_ICONS = {
  calendar: Calendar,
  compare: GitCompare,
  details: FileText,
  location: MapPin,
  phone: Phone,
  search: Search,
  shield: Shield,
  sparkles: Sparkles,
} satisfies Record<MaxModeHostStarterPromptIcon, typeof Zap>;

function hostPromptIcon(icon?: MaxModeHostStarterPromptIcon) {
  return icon ? HOST_PROMPT_ICONS[icon] : Zap;
}

export interface MaxModeResolvedToolGroup {
  scope: MaxModeToolScope;
  label: string;
  icon: QuickAction["icon"];
  actions: QuickAction[];
  available: boolean;
}

type MaxModeToolGroupDefinitions = {
  initialScope: MaxModeToolScope;
  default: Omit<MaxModeResolvedToolGroup, "available">;
  contextual: Omit<MaxModeResolvedToolGroup, "available"> & {
    contextLabel?: string;
    availableWithoutAttachments: boolean;
  };
};

function deriveHostPromptActions(
  prompts: MaxModeHostStarterPrompt[] | undefined,
  defaultConversationMode: MaxModeMode,
  allowedConversationModes: MaxModeMode[],
): QuickAction[] {
  return (prompts || [])
    .filter((prompt) => prompt?.label?.trim() && prompt?.query?.trim())
    .map((prompt, index) => {
      const palette = shellPromptPalette(index);
      return {
        icon: hostPromptIcon(prompt.icon),
        label: prompt.label.trim(),
        query: prompt.query.trim(),
        color: palette.color,
        bg: palette.bg,
        border: palette.border,
        position: prompt.position ?? "search",
        mode: prompt.mode && allowedConversationModes.includes(prompt.mode)
          ? prompt.mode
          : defaultConversationMode,
      };
    });
}

function deriveToolGroupDefinitions(
  hostConfig: MaxModeHostConfig | undefined,
  defaultConversationMode: MaxModeMode,
  allowedConversationModes: MaxModeMode[],
): MaxModeToolGroupDefinitions | null {
  const configured = hostConfig?.toolGroups;
  if (!configured?.default || !configured.contextual) {
    return null;
  }
  const defaultLabel = configured.default.label?.trim() || "Browse/Search";
  const contextualLabel = configured.contextual.label?.trim() || "Current context";
  return {
    initialScope: configured.initialScope === "contextual" ? "contextual" : "default",
    default: {
      scope: "default",
      label: defaultLabel,
      icon: hostPromptIcon(configured.default.icon ?? "search"),
      actions: deriveHostPromptActions(
        configured.default.tools,
        defaultConversationMode,
        allowedConversationModes,
      ),
    },
    contextual: {
      scope: "contextual",
      label: contextualLabel,
      icon: hostPromptIcon(configured.contextual.icon ?? "details"),
      actions: deriveHostPromptActions(
        configured.contextual.tools,
        defaultConversationMode,
        allowedConversationModes,
      ),
      contextLabel: configured.contextual.contextLabel?.trim() || undefined,
      availableWithoutAttachments: configured.contextual.availableWithoutAttachments === true,
    },
  };
}

function deriveQuickActions(
  hostConfig: MaxModeHostConfig | undefined,
  shellConfig: RuntimeShellConfigSummary | null,
  defaultConversationMode: MaxModeMode,
  allowedConversationModes: MaxModeMode[],
): QuickAction[] {
  const hostStarterPrompts = hostConfig?.starterPrompts?.filter(
    (prompt) => prompt?.label?.trim() && prompt?.query?.trim(),
  );
  if (hostStarterPrompts?.length) {
    return deriveHostPromptActions(
      hostStarterPrompts,
      defaultConversationMode,
      allowedConversationModes,
    );
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
      mode: allowedConversationModes.includes(shellPromptMode(prompt.moduleId))
        ? shellPromptMode(prompt.moduleId)
        : defaultConversationMode,
    };
  });
}

function cleanContextLabel(value: unknown): string | undefined {
  if (typeof value !== "string" || !value.trim()) return undefined;
  return value.replace(/\s+/g, " ").trim().slice(0, 100);
}

function derivedAttachmentContextLabel(attachment: MaxModeHostAttachment | undefined): string | undefined {
  if (!attachment) return undefined;
  const candidates = [
    attachment.data?.contextLabel,
    attachment.data?.name,
    attachment.data?.title,
    attachment.data?.label,
  ];
  const label = candidates.find((value) => typeof value === "string" && value.trim());
  return cleanContextLabel(label);
}

function resolveActiveContextLabel(
  attachments: MaxModeHostAttachment[],
  fallback?: string,
): string | undefined {
  const explicitLabels = attachments
    .map((attachment) => cleanContextLabel(attachment.contextLabel))
    .filter((label): label is string => Boolean(label));
  const derivedLabels = attachments
    .map(derivedAttachmentContextLabel)
    .filter((label): label is string => Boolean(label));
  const label = explicitLabels[explicitLabels.length - 1]
    || cleanContextLabel(fallback)
    || derivedLabels[derivedLabels.length - 1];
  if (label && attachments.length > 1) {
    return `${label} +${attachments.length - 1}`;
  }
  return label;
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
      ...(attachment.contextLabel?.trim()
        ? { contextLabel: attachment.contextLabel.trim().slice(0, 100) }
        : {}),
    }));
}

export function useMaxModeController({
  isOpen,
  assistantLabel,
  showUtilityPanel,
  widgetConfig: providedWidgetConfig,
}: {
  isOpen: boolean;
  assistantLabel?: string;
  showUtilityPanel?: boolean;
  widgetConfig?: MaxModeWidgetConfig;
}) {
  const { toast } = useToast();
  const widgetConfig = providedWidgetConfig ?? getWidgetConfig();
  const hostConfig = widgetConfig.host;
  const debugEnabled = widgetConfig.features?.debug === true;
  const conversationsEnabled = widgetConfig.features?.conversations ?? true;
  const resolvedAssistantLabel = assistantLabel?.trim() || hostConfig?.assistantLabel?.trim() || "MAX AI";
  const resolvedShowUtilityPanel = showUtilityPanel ?? (hostConfig?.showUtilityPanel ?? true);
  const hostStarterSuggestions = useMemo(() => deriveStarterSuggestions(hostConfig), [hostConfig]);
  const hostRequestContext = useMemo(() => sanitizeRequestContext(hostConfig), [hostConfig]);
  const hostInitialAttachments = useMemo(() => sanitizeHostAttachments(hostConfig?.initialAttachments), [hostConfig]);
  const pageModeGroup = useMemo(() => derivePageModeGroup(hostRequestContext), [hostRequestContext]);
  const [runtimeShellConfig, setRuntimeShellConfig] = useState<RuntimeShellConfigSummary | null>(null);
  const defaultConversationMode = useMemo(
    () => sanitizeConversationMode(
      hostConfig?.defaultConversationMode ?? runtimeShellConfig?.defaultConversationMode,
      "navigator",
    ),
    [hostConfig?.defaultConversationMode, runtimeShellConfig?.defaultConversationMode],
  );
  const allowedConversationModes = useMemo(
    () => sanitizeAllowedConversationModes(
      hostConfig?.allowedConversationModes ?? runtimeShellConfig?.allowedConversationModes,
      defaultConversationMode,
    ),
    [hostConfig?.allowedConversationModes, runtimeShellConfig?.allowedConversationModes, defaultConversationMode],
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

  const toolGroupDefinitions = useMemo(
    () => deriveToolGroupDefinitions(
      hostConfig,
      defaultConversationMode,
      allowedConversationModes,
    ),
    [allowedConversationModes, defaultConversationMode, hostConfig],
  );
  const legacyQuickActions = useMemo(
    () => deriveQuickActions(
      hostConfig,
      runtimeShellConfig,
      defaultConversationMode,
      allowedConversationModes,
    ),
    [allowedConversationModes, defaultConversationMode, hostConfig, runtimeShellConfig],
  );
  const searchCategories = SEARCH_CATEGORIES;
  const aiSearchCategories = AI_SEARCH_CATEGORIES;
  const browseProductCategories = BROWSE_PRODUCT_CATEGORIES;

  const [chatQuery, setChatQuery] = useState("");
  const [chatMessages, setChatMessages] = useState<ChatMessage[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [currentConversationId, setCurrentConversationId] = useState<string | null>(null);
  const [attachedItems, setAttachedItems] = useState<MaxModeHostAttachment[]>([]);
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
  const [activeToolScope, setActiveToolScope] = useState<MaxModeToolScope>(() => (
    toolGroupDefinitions?.initialScope === "contextual"
      && toolGroupDefinitions.contextual.availableWithoutAttachments
      ? "contextual"
      : "default"
  ));
  const contextualAttachments = useMemo(
    () => attachedItems.filter((item) => item.type !== "ai-search"),
    [attachedItems],
  );
  const contextualToolsAvailable = Boolean(
    toolGroupDefinitions
      && (
        contextualAttachments.length > 0
        || toolGroupDefinitions.contextual.availableWithoutAttachments
      ),
  );
  const activeContextLabel = useMemo(
    () => resolveActiveContextLabel(
      contextualAttachments,
      toolGroupDefinitions?.contextual.contextLabel,
    ),
    [contextualAttachments, toolGroupDefinitions?.contextual.contextLabel],
  );
  const previousContextAttachmentCountRef = useRef(contextualAttachments.length);

  useEffect(() => {
    const previousCount = previousContextAttachmentCountRef.current;
    const currentCount = contextualAttachments.length;
    previousContextAttachmentCountRef.current = currentCount;

    if (!toolGroupDefinitions) {
      setActiveToolScope("default");
      return;
    }
    if (currentCount > previousCount) {
      setActiveToolScope("contextual");
      return;
    }
    if (previousCount > 0 && currentCount === 0) {
      setActiveToolScope("default");
      return;
    }
    if (!contextualToolsAvailable) {
      setActiveToolScope("default");
    }
  }, [contextualAttachments.length, contextualToolsAvailable, toolGroupDefinitions]);

  const selectToolScope = useCallback((scope: MaxModeToolScope) => {
    if (scope === "contextual" && !contextualToolsAvailable) {
      return;
    }
    setActiveToolScope(scope);
  }, [contextualToolsAvailable]);

  const toolGroups = useMemo<MaxModeResolvedToolGroup[]>(() => {
    if (!toolGroupDefinitions) return [];
    return [
      { ...toolGroupDefinitions.default, available: true },
      { ...toolGroupDefinitions.contextual, available: contextualToolsAvailable },
    ];
  }, [contextualToolsAvailable, toolGroupDefinitions]);

  const quickActions = toolGroupDefinitions
    ? (activeToolScope === "contextual"
      ? toolGroupDefinitions.contextual.actions
      : toolGroupDefinitions.default.actions)
    : legacyQuickActions;

  useEffect(() => {
    setCurrentMode((current) => allowedConversationModes.includes(current) ? current : effectiveConversationMode);
  }, [allowedConversationModes, effectiveConversationMode]);

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
  const identity = useMemo(() => ({
    integrationMode: widgetConfig.integrationMode ?? "backend-mediated-private-runtime" as const,
  }), [widgetConfig.integrationMode]);
  const authContextProbeKeyRef = useRef<string | null>(null);
  const authContextProbeInFlightRef = useRef(false);
  const shellConfigProbeKeyRef = useRef<string | null>(null);
  const shellConfigProbeInFlightRef = useRef(false);
  const pendingPromptFlushInFlightRef = useRef(false);
  const maxModeContext = useMaxModeContextOptional();

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
    enabled: conversationsEnabled,
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

  useEffect(() => subscribePublicRuntimeSessionInvalidation((event) => {
    startNewConversation();
    setChatQuery("");
    setIsLoading(false);
    setConfirmationStatus({});
    setFocusedMessageId(null);
    setExpandedActions({});
    setCollectingItem(null);
    setLastRequestData(null);
    setLastResponseData(null);
    setSelectedDebugMessage(null);
    authContextProbeKeyRef.current = null;
    try {
      sessionStorage.removeItem(PENDING_PROMPTS_KEY);
    } catch {}
    maxModeContext?.clearPersistedState();
    emitEvent("conversation:reset", {
      reason: event.reason,
      previousSessionId: event.previousSessionId,
    });
    toast({
      title: event.reason === "conversation-access-denied" ? "Conversation refreshed" : "Guest session refreshed",
      description: event.reason === "conversation-access-denied"
        ? "That conversation is not available to this session. A new conversation is ready; please send your request again."
        : "For your security, the previous conversation was cleared. Please send your request again.",
    });
  }), [maxModeContext, startNewConversation, toast]);

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
    requestContextProvider: hostConfig?.requestContextProvider,
  });

  const handleProgrammaticPrompt = useCallback(
    async (prompt: PendingPrompt | null | undefined) => {
      if (!prompt?.message?.trim()) {
        return;
      }
      await handleChatQuery(
        prompt.message.trim(),
        prompt.position,
        prompt.mode,
        prompt.requestContext,
        prompt.attachments,
      );
    },
    [handleChatQuery],
  );

  const { handleConfirmation } = useConfirmationFlow({
    attachedItems,
    currentConversationId,
    currentMode,
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
    consumePendingAttachments(attachedItems);
  }, [attachedItems]);

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
    currentMode,
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

  const currentPageAttachmentController = useCurrentPageAttachment({
    config: hostConfig?.currentPageAttachment,
    attachedItems,
    setAttachedItems,
    chatInputRef,
    toast,
  });

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
        if (isPublicRuntimeSessionInvalidatedError(error)) {
          return;
        }
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
        if (isPublicRuntimeSessionInvalidatedError(error)) {
          return;
        }
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

  const isPresentationResultAttached = useCallback(
    (referenceKey: string, sourceMessageId: string) => {
      const id = `action-result:${sourceMessageId}:${referenceKey}`;
      return attachedItems.some((item) => item.type === ACTION_RESULT_ATTACHMENT_TYPE && item.data?.id === id);
    },
    [attachedItems],
  );

  const handleAttachPresentationResult = useCallback(
    (reference: MaxModePresentationResultReference) => {
      const item = toActionResultAttachedItem(reference);
      setAttachedItems((previous) => {
        if (previous.some((existing) => existing.type === item.type && existing.data?.id === item.data.id)) {
          return previous;
        }
        return [...previous, item];
      });
      toast({
        title: "Added to chat",
        description: `${reference.label} is available as selected result context.`,
      });
      setTimeout(() => chatInputRef.current?.focus(), 100);
    },
    [chatInputRef, setAttachedItems, toast],
  );

  const handleDetachPresentationResult = useCallback(
    (reference: MaxModePresentationResultReference) => {
      const id = presentationReferenceAttachmentId(reference);
      setAttachedItems((previous) => previous.filter(
        (item) => item.type !== ACTION_RESULT_ATTACHMENT_TYPE || item.data?.id !== id,
      ));
    },
    [setAttachedItems],
  );

  const handleActionPresentationAsk = useCallback(
    async (query: string, references: readonly MaxModePresentationResultReference[]) => {
      const safeReferences = references.slice(0, 4);
      const ephemeralAttachments = safeReferences.map(toActionResultAttachedItem);
      await handleChatQuery(
        query,
        undefined,
        undefined,
        safeReferences.length > 0
          ? {
            uiSelectedResults: safeReferences.map((reference) => ({
              key: reference.key,
              label: reference.label,
              scope: reference.scope,
              sourceMessageId: reference.sourceMessageId,
              sourceActionName: reference.sourceActionName,
            })),
          }
          : undefined,
        ephemeralAttachments,
      );
    },
    [handleChatQuery],
  );

  useEffect(() => {
    function onAttachItem(event: Event) {
      const detail = (event as CustomEvent<{ item?: MaxModeHostAttachment }>).detail;
      if (!detail?.item?.type || !detail.item.data || typeof detail.item.data !== "object") {
        return;
      }
      handleReattachItem(detail.item);
    }

    window.addEventListener("maxmode:attach-item", onAttachItem as EventListener);
    return () => {
      window.removeEventListener("maxmode:attach-item", onAttachItem as EventListener);
    };
  }, [handleReattachItem]);

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

  const connectCustomerAccount = useCallback((action: CustomerAccountConnectAction) => {
    const targetUrl = buildCustomerAccountConnectUrl(action);
    if (!targetUrl) {
      toast({
        title: "Account connection unavailable",
        description: "This store has not published a customer account connection URL yet.",
        variant: "destructive",
      });
      return;
    }
    emitEvent("customer-account-auth:start");
    window.location.assign(targetUrl);
  }, [toast]);

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

  const getResultStyles = (resultType?: ResultType, success = true) => {
    switch (resultType) {
      case "ACTION_EXECUTED":
        if (!success) {
          return { icon: XCircle, bg: "bg-red-500/10", border: "border-red-500/30", text: "text-red-700", iconColor: "text-red-600", label: "Action Failed" };
        }
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
    conversationsEnabled,
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
    toolGroups,
    activeToolScope,
    activeContextLabel,
    contextualToolsAvailable,
    searchCategories,
    aiSearchCategories,
    browseProductCategories,
    // helpers/handlers
    getResultStyles,
    isItemAttached,
    handleReattachItem,
    handleAttachActionResultItem,
    isPresentationResultAttached,
    handleAttachPresentationResult,
    handleDetachPresentationResult,
    handleActionPresentationAsk,
    clearSearchCategory,
    loadConversations,
    openConversation,
    handleDeleteConversation,
    startNewConversation,
    openConversationsPanel,
    selectToolScope,
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
    connectCustomerAccount,
    expandActionResults,
    removeNonAiAttachmentByIndex,
    removeAiSearchAttachment,
    ...currentPageAttachmentController,
    dismissSuggestions,
    selectSuggestion,
    attachCartToChat,
    proceedToCheckoutFromCart,
    browseProductsFromCart,
  } as const;
}

export type MaxModeController = ReturnType<typeof useMaxModeController>;
