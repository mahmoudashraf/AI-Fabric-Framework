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
}

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
  /** User identifier for authenticated cart/conversation scoping */
  userId?: string;
  /** Optional explicit session id for anonymous or mixed-mode flows */
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
}

export function getWidgetConfig(): MaxModeWidgetConfig {
  return _config;
}

export interface MaxModeResolvedIdentity {
  userId?: string;
  sessionId: string;
  ownerId: string;
}

function buildSessionStorageKey(baseUrl: string): string {
  return `max-mode-widget.sessionId:${encodeURIComponent(baseUrl || "default")}`;
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

export function getWidgetIdentity(): MaxModeResolvedIdentity {
  const userId = _config.userId?.trim() || undefined;
  const sessionId = resolveSessionId();
  return {
    userId,
    sessionId,
    ownerId: userId || sessionId,
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
