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
  /** Base URL for CRUD operations (cart, conversations) */
  crudBaseUrl: string;
  /** Extra headers sent with every API request (e.g. auth tokens) */
  headers?: Record<string, string>;
  /** Extra headers sent only to the chat/orchestration API */
  chatHeaders?: Record<string, string>;
  /** Extra headers sent only to the CRUD API */
  crudHeaders?: Record<string, string>;
  /** Optional public-runtime auth helpers for secure browser-facing modes */
  runtimeAuth?: MaxModeRuntimeAuthConfig;
}

export interface MaxModeRuntimeBootstrapRequest {
  sessionId?: string;
}

export interface MaxModeRuntimeBootstrapResult {
  token: string;
  tokenType?: string;
  authMode?: string;
  subjectType?: string;
  sessionId?: string;
  expiresAt?: string;
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
   * Defaults to `/chat/me/auth-context` for secure modes and `/chat/auth-context`
   * for legacy compatibility mode.
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
  bootstrapAnonymous?: (
    request: MaxModeRuntimeBootstrapRequest,
  ) => Promise<MaxModeRuntimeBootstrapResult>;
}

export type MaxModeIntegrationMode =
  | "backend-mediated-private-runtime"
  | "public-runtime-authenticated"
  | "public-runtime-anonymous"
  | "legacy-static-header";

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
   * Default: "legacy-static-header" for backward compatibility.
   */
  integrationMode?: MaxModeIntegrationMode;
  /**
   * @deprecated Legacy static-header mode only.
   * Secure modes derive identity from verified backend/runtime auth context instead.
   */
  userId?: string;
  /**
   * @deprecated Legacy static-header mode only, except as an anonymous bootstrap hint
   * for `public-runtime-anonymous`.
   */
  sessionId?: string;
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
    crudBaseUrl: "",
    headers: {},
    chatHeaders: {},
    crudHeaders: {},
  },
  integrationMode: "legacy-static-header",
  userId: undefined,
  sessionId: undefined,
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
let _memorySessionId: string | null = null;

export function setWidgetConfig(config: Partial<MaxModeWidgetConfig>): void {
  _config = {
    ...DEFAULT_CONFIG,
    ...config,
    apiConfig: {
      ...DEFAULT_CONFIG.apiConfig,
      ...config.apiConfig,
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

  if (
    !usesLegacyRequestIdentity(_config.integrationMode ?? DEFAULT_CONFIG.integrationMode!)
    && (Boolean(_config.userId?.trim())
      || (Boolean(_config.sessionId?.trim()) && !usesAnonymousBootstrapSession(_config.integrationMode)))
  ) {
    console.warn(
      "[MaxMode] userId is ignored outside 'legacy-static-header'. " +
      "sessionId is only used as an anonymous bootstrap hint in 'public-runtime-anonymous'.",
    );
  }
}

export function getWidgetConfig(): MaxModeWidgetConfig {
  return _config;
}

export interface MaxModeResolvedIdentity {
  integrationMode: MaxModeIntegrationMode;
  requestIdentityEnabled: boolean;
  userId?: string;
  sessionId?: string;
  ownerId?: string;
}

export function usesLegacyRequestIdentity(mode: MaxModeIntegrationMode): boolean {
  return mode === "legacy-static-header";
}

export function usesAnonymousBootstrapSession(mode: MaxModeIntegrationMode): boolean {
  return mode === "public-runtime-anonymous";
}

function buildSessionStorageKey(baseUrl: string): string {
  return `max-mode-widget.sessionId:${encodeURIComponent(baseUrl || "default")}`;
}

function buildPublicRuntimeSessionStorageKey(baseUrl: string): string {
  return `max-mode-widget.publicRuntime.sessionId:${encodeURIComponent(baseUrl || "default")}`;
}

function generateSessionId(): string {
  const random = Math.random().toString(36).slice(2, 10);
  return `anon-${Date.now().toString(36)}-${random}`;
}

function getStoredSessionId(baseUrl: string): string | null {
  if (typeof window === "undefined" || !window.sessionStorage) {
    return _memorySessionId;
  }
  try {
    return window.sessionStorage.getItem(buildSessionStorageKey(baseUrl));
  } catch {
    return _memorySessionId;
  }
}

function persistSessionId(baseUrl: string, sessionId: string): void {
  _memorySessionId = sessionId;
  if (typeof window === "undefined" || !window.sessionStorage) {
    return;
  }
  try {
    window.sessionStorage.setItem(buildSessionStorageKey(baseUrl), sessionId);
  } catch {
    // Ignore storage failures and keep the in-memory fallback.
  }
}

function resolveSessionId(): string {
  const configured = _config.sessionId?.trim();
  if (configured) {
    persistSessionId(_config.apiConfig.chatBaseUrl, configured);
    return configured;
  }

  const stored = getStoredSessionId(_config.apiConfig.chatBaseUrl)?.trim();
  if (stored) {
    return stored;
  }

  const generated = generateSessionId();
  persistSessionId(_config.apiConfig.chatBaseUrl, generated);
  return generated;
}

function getStoredPublicRuntimeSessionId(baseUrl: string): string | null {
  if (typeof window === "undefined" || !window.sessionStorage) {
    return null;
  }
  try {
    return window.sessionStorage.getItem(buildPublicRuntimeSessionStorageKey(baseUrl));
  } catch {
    return null;
  }
}

function persistPublicRuntimeSessionId(baseUrl: string, sessionId: string): void {
  if (typeof window === "undefined" || !window.sessionStorage) {
    return;
  }
  try {
    window.sessionStorage.setItem(buildPublicRuntimeSessionStorageKey(baseUrl), sessionId);
  } catch {
    // Ignore storage failures for anonymous public-runtime hints.
  }
}

export function resolveAnonymousBootstrapSessionId(): string {
  const configured = _config.sessionId?.trim();
  if (configured) {
    persistPublicRuntimeSessionId(_config.apiConfig.chatBaseUrl, configured);
    return configured;
  }

  const stored = getStoredPublicRuntimeSessionId(_config.apiConfig.chatBaseUrl)?.trim();
  if (stored) {
    return stored;
  }

  const generated = generateSessionId();
  persistPublicRuntimeSessionId(_config.apiConfig.chatBaseUrl, generated);
  return generated;
}

export function updateAnonymousBootstrapSessionId(sessionId?: string | null): void {
  const normalized = sessionId?.trim();
  if (!normalized) {
    return;
  }
  persistPublicRuntimeSessionId(_config.apiConfig.chatBaseUrl, normalized);
}

export function getWidgetIdentity(): MaxModeResolvedIdentity {
  const integrationMode = _config.integrationMode ?? DEFAULT_CONFIG.integrationMode!;
  const requestIdentityEnabled = usesLegacyRequestIdentity(integrationMode);
  const userId = requestIdentityEnabled ? _config.userId?.trim() || undefined : undefined;
  const sessionId = requestIdentityEnabled ? resolveSessionId() : undefined;
  return {
    integrationMode,
    requestIdentityEnabled,
    userId,
    sessionId,
    ownerId: requestIdentityEnabled ? userId || sessionId : undefined,
  };
}

export function emitEvent(type: MaxModeEventType, data?: any): void {
  const event: MaxModeEvent = {
    type,
    data,
    timestamp: new Date().toISOString(),
  };
  _config.onEvent?.(event);
}
