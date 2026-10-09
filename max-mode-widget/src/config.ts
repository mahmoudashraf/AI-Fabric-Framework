/**
 * Runtime configuration store for the Max Mode widget.
 *
 * The IIFE entry calls `setWidgetConfig()` from `MaxMode.init()`.
 * The React entry reads config from the `<MaxModeProvider>` props.
 * Internal code reads the singleton via `getWidgetConfig()`.
 */

import type { RuntimeShellConfigSummary } from "@/types";
import type { MaxModeMode, MaxModePosition } from "@/constants";
import type { MaxModeActionPresentationConfig } from "@/actionPresentation";

export interface MaxModeApiConfig {
  /** Base URL for the chat / orchestration API */
  chatBaseUrl: string;
  /** Optional base URL for business CRUD operations such as cart APIs */
  crudBaseUrl?: string;
  /**
   * Optional static headers added to every widget API request.
   *
   * Useful when the host authenticates platform adapter routes with an
   * operator-scoped API key rather than a browser cookie session.
   */
  defaultHeaders?: Record<string, string>;
  /**
   * Fetch credentials policy for widget API requests.
   *
   * Use `"include"` when the host serves the widget from one origin and the
   * backing adapter/API lives on another origin but still relies on browser
   * session cookies.
   */
  fetchCredentials?: RequestCredentials;
  /**
   * Optional explicit runtime route URLs.
   *
   * These should be preferred when the host already has route-level metadata
   * such as the platform provisioning `preferred*Url` fields.
   *
   * Values may be absolute URLs or paths relative to `chatBaseUrl`.
   */
  runtimeRoutes?: MaxModeRuntimeRouteConfig;
  /** Optional public-runtime auth helpers for secure browser-facing modes */
  runtimeAuth?: MaxModeRuntimeAuthConfig;
  /**
   * When true, probe the runtime shell-config route when the widget opens.
   *
   * Defaults to true. Hosts with explicitly managed welcome/actions can disable
   * this to avoid unnecessary route probes.
   */
  probeShellConfigOnOpen?: boolean;
}

export interface MaxModeRuntimeBootstrapResult {
  token: string;
  tokenType?: string;
  authMode?: string;
  subjectType?: string;
  sessionId?: string;
  expiresAt?: string;
  shellConfig?: RuntimeShellConfigSummary;
}

export interface MaxModeRuntimeRouteConfig {
  chatQueryUrl?: string;
  suggestionsUrl?: string;
  authContextUrl?: string;
  shellConfigUrl?: string;
  conversationsUrl?: string;
  conversationItemUrlTemplate?: string;
}

export interface MaxModeRuntimeAuthConfig {
  /** Authorization header name used by the runtime public token surface */
  authorizationHeader?: string;
  /** Token scheme prefix used by the runtime public token surface */
  tokenScheme?: string;
  /** Optional explicit bootstrap URL. Defaults to `${chatBaseUrl}/public/chat/session`. */
  bootstrapUrl?: string;
  /** Optional same-session renewal URL. Defaults to `${bootstrapUrl}/renew`. */
  renewUrl?: string;
  /**
   * Optional explicit auth-context probe URL or path.
   *
   * Defaults to `/chat/me/auth-context`.
   */
  authContextUrl?: string;
  /**
   * When true, probe the runtime auth context when the widget opens.
   *
   * Defaults to true for secure modes so integration mistakes fail early.
   */
  probeAuthContextOnOpen?: boolean;
  /**
   * Optional host-provided bearer token supplier.
   *
   * If present, secure public modes will prefer this before anonymous bootstrap.
   */
  getBearerToken?: () => Promise<string | null | undefined> | string | null | undefined;
  /**
   * Optional host-provided anonymous bootstrap implementation.
   *
   * If absent, the widget will call the runtime bootstrap URL directly.
   */
  bootstrapAnonymous?: () => Promise<MaxModeRuntimeBootstrapResult>;
}

export type MaxModeIntegrationMode =
  | "backend-mediated-private-runtime"
  | "public-runtime-authenticated"
  | "public-runtime-anonymous";

export interface MaxModeFeatures {
  /** Show shopping cart panel (default: true) */
  cart?: boolean;
  /** Show debug inspector (default: false) */
  debug?: boolean;
  /** Show conversation history (default: true) */
  conversations?: boolean;
  /** Show quick action buttons (default: true) */
  quickActions?: boolean;
}

export interface MaxModeThemeConfig {
  /** Primary brand color (CSS color value) */
  primaryColor?: string;
  /** Border radius in CSS units (default: "0.5rem") */
  borderRadius?: string;
  /** Font family (default: "Inter, system-ui, sans-serif") */
  fontFamily?: string;
  /** Dark mode: true, false, or "auto" to follow system preference */
  darkMode?: boolean | "auto";
}

export interface MaxModeHostAttachment {
  type: string;
  data: Record<string, any>;
  /** Optional host-owned label used to identify the active contextual tool scope. */
  contextLabel?: string;
}

export interface MaxModeCurrentPageContent {
  /** Human-readable page title shown in the attachment chip */
  title?: string;
  /** Trusted host-provided or browser-extracted page text */
  text: string;
  /** Optional source URL. Query parameters, hashes, and credentials are removed before sending. */
  url?: string;
}

export type MaxModeCurrentPageContentProvider = () =>
  | MaxModeCurrentPageContent
  | null
  | undefined
  | Promise<MaxModeCurrentPageContent | null | undefined>;

export interface MaxModeCurrentPageAttachmentConfig {
  /** Enable the attach-current-page control. Defaults to true when this object is present. */
  enabled?: boolean;
  /** Maximum extracted characters. Defaults to 1800 and is capped at 20000. */
  maxChars?: number;
  /** Maximum number of separately attached pages. Defaults to 3 and is capped at 10. */
  maxPages?: number;
  /** Maximum characters across all attached pages. Defaults to maxChars * maxPages and is capped at 50000. */
  maxTotalChars?: number;
  /** Optional host DOM selector to use as the page-content root. */
  rootSelector?: string;
  /** Additional selectors excluded from browser text extraction. */
  excludeSelectors?: string[];
  /** Optional host-owned extractor for SPAs or pages with structured content. */
  contentProvider?: MaxModeCurrentPageContentProvider;
  /**
   * Remove captured context when browser navigation changes. Defaults to true.
   * Set to false only when the host intentionally lets users collect pages in
   * the widget's existing tab-scoped session.
   */
  invalidateOnNavigation?: boolean;
}

export type MaxModeHostStarterPromptIcon =
  | "calendar"
  | "compare"
  | "details"
  | "documents"
  | "location"
  | "phone"
  | "search"
  | "shield"
  | "sparkles"
  | "tools";

export interface MaxModeHostStarterPrompt {
  label: string;
  query: string;
  position?: MaxModePosition;
  mode?: MaxModeMode;
  /** Optional provider-neutral visual semantic for this host-owned tool. */
  icon?: MaxModeHostStarterPromptIcon;
}

export type MaxModeToolScope = "default" | "contextual";

export interface MaxModeHostToolGroup {
  /** Host-owned visible group label, for example "Browse" or "Current context". */
  label: string;
  /** Optional provider-neutral visual semantic for the group selector. */
  icon?: MaxModeHostStarterPromptIcon;
  /** Host-owned tools available while this scope is selected. */
  tools: MaxModeHostStarterPrompt[];
  /**
   * Context label used when the host page itself supplies context without an
   * attachment. Attachment-level context labels take precedence.
   */
  contextLabel?: string;
  /**
   * Keep the contextual scope available without an attachment. This is useful
   * on detail pages where the host page is already an authoritative context.
   * It has no effect on the default scope.
   */
  availableWithoutAttachments?: boolean;
}

export interface MaxModeHostToolGroups {
  /** Initial scope. Contextual falls back to default when no context is available. */
  initialScope?: MaxModeToolScope;
  default: MaxModeHostToolGroup;
  contextual: MaxModeHostToolGroup;
}

export type MaxModeHostToolRailTone = "primary" | "teal" | "violet" | "amber" | "neutral";

interface MaxModeHostToolRailItemBase {
  /** Stable host-owned identifier used for rendering and test automation. */
  id: string;
  /** Short visible label. Keep this compact because the rail is mobile-first. */
  label: string;
  /** Provider-neutral icon semantic rendered by the widget. */
  icon?: MaxModeHostStarterPromptIcon;
  /** Bounded widget-owned color treatment. Arbitrary CSS is not accepted. */
  tone?: MaxModeHostToolRailTone;
}

export type MaxModeHostToolRailItem = MaxModeHostToolRailItemBase & (
  | {
      action: "open-tools";
      /** Optional tool scope to select before opening the mobile tool sheet. */
      scope?: MaxModeToolScope;
    }
  | {
      action: "open-documents";
    }
  | {
      action: "prompt";
      /** Host-owned prompt sent through the normal chat route. */
      query: string;
      position?: MaxModePosition;
      mode?: MaxModeMode;
      /** Disable the prompt until contextual page or attachment state exists. */
      requiresContext?: boolean;
    }
);

export interface MaxModeHostToolRailConfig {
  /** Ordered host-owned commands. An explicit empty array hides the rail. */
  items: MaxModeHostToolRailItem[];
  /** Start the mobile rail collapsed. Defaults to false. */
  initiallyCollapsed?: boolean;
}

export interface MaxModeHostCustomerAccountAuthConfig {
  /** Bridge Customer Account OAuth start URL exposed by the Shopify bootstrap */
  startUrl?: string;
  /** Optional Bridge Customer Account session-status URL for host probes */
  sessionUrl?: string;
  /** Stable storefront shopper session id sent with chat requests */
  shopperSessionId?: string;
  /** Optional return URL after Shopify Customer Account authorization */
  returnTo?: string;
}

export type MaxModeHostRequestContextProvider = () =>
  | Record<string, any>
  | undefined
  | null
  | Promise<Record<string, any> | undefined | null>;

export interface MaxModeHostConfig {
  /** Host-specific full-screen experience. Defaults to the generic Max Mode workspace. */
  experience?: "default" | "shopify-shopping-workspace";
  /** Visible launcher label for storefront/product hosts */
  launcherLabel?: string;
  /** Accessible launcher label override */
  launcherAriaLabel?: string;
  /** Visual launcher style for hosts that want a pill instead of an icon button */
  launcherVariant?: "icon" | "pill";
  /** Assistant label shown in the widget header */
  assistantLabel?: string;
  /** Optional host-owned welcome message */
  welcomeMessage?: string;
  /** Optional host-owned starter prompts shown as quick actions */
  starterPrompts?: MaxModeHostStarterPrompt[];
  /**
   * Optional two-scope tool contract. The widget understands only `default`
   * and `contextual`; all labels, icons, and queries remain host-owned.
   */
  toolGroups?: MaxModeHostToolGroups;
  /** Optional generic command rail rendered in mobile Max Mode. */
  toolRail?: MaxModeHostToolRailConfig;
  /** Optional host-owned starter suggestions shown above the composer */
  starterSuggestions?: string[];
  /** Optional host-owned request payload merged into query and suggestions calls */
  requestContext?: Record<string, any>;
  /** Optional host-owned live request context merged just before each chat query */
  requestContextProvider?: MaxModeHostRequestContextProvider;
  /** Default user-selectable conversation mode for this host */
  defaultConversationMode?: MaxModeMode;
  /** Effective conversation mode after host/page routing is resolved */
  effectiveConversationMode?: MaxModeMode;
  /** User-selectable advanced modes intentionally enabled by the host */
  allowedConversationModes?: MaxModeMode[];
  /** Optional page-group -> mode routing hints provided by the host */
  pageModeMappings?: Record<string, MaxModeMode>;
  /** Optional host-owned initial attachments/context */
  initialAttachments?: MaxModeHostAttachment[];
  /** Optional reviewed action-result presentation registry and mappings */
  actionPresentation?: MaxModeActionPresentationConfig;
  /** Opt-in current-page text attachment support */
  currentPageAttachment?: MaxModeCurrentPageAttachmentConfig;
  /** Hide POC-only utility controls when embedding in storefronts */
  showUtilityPanel?: boolean;
  /** Render the compact storefront dock that shares the Max Mode chat runtime */
  companionDock?: boolean;
  /** Compact context text shown in the companion dock header and status badge */
  companionContextLabel?: string;
  /** Compact mode label shown beside the companion composer */
  companionModeLabel?: string;
  /** Host-specific companion composer placeholder */
  companionPlaceholder?: string;
  /** Host-specific empty-state guidance in the expanded companion */
  companionEmptyMessage?: string;
  /** Optional Shopify Customer Account auth handoff used for customer-owned resources */
  customerAccountAuth?: MaxModeHostCustomerAccountAuthConfig;
}

export interface MaxModeWidgetConfig {
  /** API endpoints and auth */
  apiConfig: MaxModeApiConfig;
  /**
   * Optional stable namespace for tab-scoped conversation, attachment, and
   * runtime-session state. Platform installations set this automatically.
   */
  storageNamespace?: string;
  /**
   * Integration/auth posture.
   *
   * Secure modes derive identity from host/runtime auth context and do not send
   * browser-supplied request identity fields.
   *
   * Default: "backend-mediated-private-runtime".
   */
  integrationMode?: MaxModeIntegrationMode;
  /** Feature toggles */
  features?: MaxModeFeatures;
  /** Visual customization */
  theme?: MaxModeThemeConfig;
  /** Launcher button position */
  position?: "bottom-right" | "bottom-left";
  /** Set to false to hide the default floating launcher button */
  launcher?: boolean;
  /** Optional host-owned UX overrides and initial context */
  host?: MaxModeHostConfig;
  /** Callback for widget events (cart changes, messages, etc.) */
  onEvent?: (event: MaxModeEvent) => void;
  /** Revalidate installation/assignment state before clearing into a new conversation. */
  beforeNewConversation?: () => Promise<void> | void;
  /** Callback when widget is closed */
  onClose?: () => void;
}

export type MaxModeEventType =
  | "widget:opened"
  | "widget:closed"
  | "conversation:reset"
  | "message:sent"
  | "message:received"
  | "action-presentation:rendered"
  | "action-presentation:fallback"
  | "action-presentation:command"
  | "customer-account-auth:start"
  | "cart:add"
  | "cart:remove"
  | "cart:checkout"
  | "product:view"
  | "error";

export interface MaxModeEvent {
  type: MaxModeEventType;
  data?: any;
  timestamp: string;
}

// ---------------------------------------------------------------------------
// Singleton config store
// ---------------------------------------------------------------------------

const DEFAULT_CONFIG: MaxModeWidgetConfig = {
  apiConfig: {
    chatBaseUrl: "",
    crudBaseUrl: undefined,
    defaultHeaders: undefined,
    fetchCredentials: undefined,
    runtimeRoutes: undefined,
    probeShellConfigOnOpen: true,
  },
  integrationMode: "backend-mediated-private-runtime",
  storageNamespace: undefined,
  features: {
    cart: true,
    debug: false,
    conversations: true,
    quickActions: true,
  },
  theme: {
    primaryColor: undefined,
    borderRadius: "0.5rem",
    fontFamily: "Inter, system-ui, sans-serif",
    darkMode: false,
  },
  position: "bottom-right",
  launcher: true,
  host: {
    experience: "default",
    launcherLabel: undefined,
    launcherAriaLabel: undefined,
    launcherVariant: "icon",
    assistantLabel: undefined,
    welcomeMessage: undefined,
    starterPrompts: undefined,
    toolGroups: undefined,
    toolRail: undefined,
    starterSuggestions: undefined,
    requestContext: undefined,
    requestContextProvider: undefined,
    defaultConversationMode: undefined,
    effectiveConversationMode: undefined,
    allowedConversationModes: undefined,
    pageModeMappings: undefined,
    initialAttachments: undefined,
    actionPresentation: undefined,
    currentPageAttachment: undefined,
    showUtilityPanel: true,
    companionDock: false,
    companionContextLabel: undefined,
    companionModeLabel: undefined,
    companionPlaceholder: undefined,
    companionEmptyMessage: undefined,
    customerAccountAuth: undefined,
  },
  onEvent: undefined,
  beforeNewConversation: undefined,
  onClose: undefined,
};

let _config: MaxModeWidgetConfig = { ...DEFAULT_CONFIG };

export function setWidgetConfig(config: Partial<MaxModeWidgetConfig>): void {
  _config = {
    ...DEFAULT_CONFIG,
    ...config,
    apiConfig: {
      ...DEFAULT_CONFIG.apiConfig,
      ...config.apiConfig,
      runtimeRoutes: {
        ...DEFAULT_CONFIG.apiConfig.runtimeRoutes,
        ...config.apiConfig?.runtimeRoutes,
      },
      probeShellConfigOnOpen:
        config.apiConfig?.probeShellConfigOnOpen ?? DEFAULT_CONFIG.apiConfig.probeShellConfigOnOpen,
    },
    features: {
      ...DEFAULT_CONFIG.features,
      ...config.features,
    },
    theme: {
      ...DEFAULT_CONFIG.theme,
      ...config.theme,
    },
    host: {
      ...DEFAULT_CONFIG.host,
      ...config.host,
    },
  };

  if ((_config.features?.cart ?? true) && !hasCrudApiBaseUrl(_config)) {
    console.info(
      "[MaxMode] apiConfig.crudBaseUrl is not configured. Cart/business CRUD UI will be disabled " +
      "while chat, auth bootstrap, and secure conversation routes continue to use chatBaseUrl.",
    );
  }
}

export function getWidgetConfig(): MaxModeWidgetConfig {
  return _config;
}

export function getWidgetStorageKey(baseKey: string): string {
  const namespace = _config.storageNamespace?.trim();
  return namespace ? `${baseKey}:${namespace}` : baseKey;
}

export interface MaxModeResolvedIdentity {
  integrationMode: MaxModeIntegrationMode;
}

export function usesAnonymousBootstrapSession(mode: MaxModeIntegrationMode): boolean {
  return mode === "public-runtime-anonymous";
}

export function getWidgetIdentity(): MaxModeResolvedIdentity {
  const integrationMode = _config.integrationMode ?? DEFAULT_CONFIG.integrationMode!;
  return { integrationMode };
}

export function emitEvent(type: MaxModeEventType, data?: any): void {
  const event: MaxModeEvent = {
    type,
    data,
    timestamp: new Date().toISOString(),
  };
  _config.onEvent?.(event);
}

export function hasCrudApiBaseUrl(config: MaxModeWidgetConfig = _config): boolean {
  return Boolean(config.apiConfig.crudBaseUrl?.trim());
}

export function isCartCrudEnabled(config: MaxModeWidgetConfig = _config): boolean {
  return (config.features?.cart ?? true) && hasCrudApiBaseUrl(config);
}
