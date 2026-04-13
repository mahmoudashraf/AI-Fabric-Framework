/**
 * Runtime configuration store for the Max Mode widget.
 *
 * The IIFE entry calls `setWidgetConfig()` from `MaxMode.init()`.
 * The React entry reads config from the `<MaxModeProvider>` props.
 * Internal code reads the singleton via `getWidgetConfig()`.
 */

export interface MaxModeApiConfig {
  /** Base URL for the chat / orchestration API */
  chatBaseUrl: string;
  /** Optional base URL for business CRUD operations such as cart APIs */
  crudBaseUrl?: string;
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
}

export interface MaxModeRuntimeBootstrapResult {
  token: string;
  tokenType?: string;
  authMode?: string;
  subjectType?: string;
  sessionId?: string;
  expiresAt?: string;
}

export interface MaxModeRuntimeRouteConfig {
  chatQueryUrl?: string;
  suggestionsUrl?: string;
  authContextUrl?: string;
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

export interface MaxModeWidgetConfig {
  /** API endpoints and auth */
  apiConfig: MaxModeApiConfig;
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
  /** Callback for widget events (cart changes, messages, etc.) */
  onEvent?: (event: MaxModeEvent) => void;
  /** Callback when widget is closed */
  onClose?: () => void;
}

export type MaxModeEventType =
  | "widget:opened"
  | "widget:closed"
  | "message:sent"
  | "message:received"
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
    runtimeRoutes: undefined,
  },
  integrationMode: "backend-mediated-private-runtime",
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
  onEvent: undefined,
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
    },
    features: {
      ...DEFAULT_CONFIG.features,
      ...config.features,
    },
    theme: {
      ...DEFAULT_CONFIG.theme,
      ...config.theme,
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
