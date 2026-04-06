/**
 * React entry point — for npm consumers who already have React.
 *
 * Usage:
 *   import { MaxModeWidget, MaxModeProvider, useMaxMode } from '@anthropic/max-mode-widget';
 *   import '@anthropic/max-mode-widget/styles.css';
 */

// Core widget component
export { MaxModeWidget } from "./MaxModeWidget";

// Provider (wraps the widget for context isolation)
export { MaxModeProvider } from "@/context";

// Programmatic hook
export { useMaxMode } from "@/hooks/useMaxMode";

// Config & types (for consumers who want to configure programmatically)
export type {
  MaxModeWidgetConfig,
  MaxModeApiConfig,
  MaxModeFeatures,
  MaxModeThemeConfig,
  MaxModeEvent,
  MaxModeEventType,
} from "@/config";

// Domain types
export type {
  MaxModeProps,
  Product,
  ChatMessage,
  Document,
  Conversation,
  ResultType,
  ChatResult,
  SanitizedPayload,
  SmartSuggestion,
} from "@/types";

export type { SharedAttachment } from "@/context";

// Style import path hint (actual import is '@anthropic/max-mode-widget/styles.css')
