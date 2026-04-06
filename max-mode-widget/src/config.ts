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
  /** User identifier for cart/conversation scoping */
  userId?: string;
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
  },
  userId: undefined,
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

export function emitEvent(type: MaxModeEventType, data?: any): void {
  const event: MaxModeEvent = {
    type,
    data,
    timestamp: new Date().toISOString(),
  };
  _config.onEvent?.(event);
}
