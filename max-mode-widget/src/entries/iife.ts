/**
 * IIFE entry point — exposes `window.MaxMode` for script-tag usage.
 *
 * Usage:
 *   <script src="https://cdn.example.com/max-mode-widget.iife.js"></script>
 *   <script>
 *     MaxMode.init({
 *       apiConfig: {
 *         chatBaseUrl: "https://your-api.com/api"
 *       },
 *       integrationMode: "public-runtime-authenticated",
 *       features: { cart: false },
 *       theme: { primaryColor: "#6366f1" },
 *       position: "bottom-right",
 *     });
 *   </script>
 */

import type { MaxModeWidgetConfig } from "@/config";
import type { SharedAttachment } from "@/context";
import type { MaxModeMode, MaxModePosition } from "@/constants";
import { mountWidget, openWidget, closeWidget, toggleWidget, destroyWidget } from "@/mount";

const PENDING_PROMPTS_KEY = "maxmode_widget_pending_prompts";

export interface MaxModeSendMessageOptions {
  open?: boolean;
  position?: MaxModePosition;
  mode?: MaxModeMode;
  requestContext?: Record<string, any>;
}

export interface MaxModeQueuedPrompt {
  id: string;
  message: string;
  open: boolean;
  position?: MaxModePosition;
  mode?: MaxModeMode;
  requestContext?: Record<string, any>;
}

export interface MaxModeAPI {
  /** Initialize and mount the widget */
  init: (config: Partial<MaxModeWidgetConfig> & { apiConfig: MaxModeWidgetConfig["apiConfig"] }) => void;
  /** Open the widget */
  open: () => void;
  /** Close the widget */
  close: () => void;
  /** Toggle widget open/closed */
  toggle: () => void;
  /** Attach a generic item to the chat */
  attachItem: (item: SharedAttachment) => void;
  /** Attach a product to the chat */
  attachProduct: (product: { sku: string; name: string; price: number; [key: string]: any }) => void;
  /** Send a message programmatically */
  sendMessage: (message: string, options?: MaxModeSendMessageOptions) => void;
  /** Destroy and unmount the widget completely */
  destroy: () => void;
  /** Widget version */
  version: string;
}

const PENDING_ATTACHMENTS_KEY = "maxmode_widget_pending_attachments";

const MaxModeInstance: MaxModeAPI = {
  version: "1.0.0",

  init(config) {
    if (!config.apiConfig?.chatBaseUrl) {
      console.error("[MaxMode] apiConfig.chatBaseUrl is required");
      return;
    }
    // Mount with full config
    mountWidget(config as MaxModeWidgetConfig);
  },

  open() {
    openWidget();
  },

  close() {
    closeWidget();
  },

  toggle() {
    toggleWidget();
  },

  attachItem(item) {
    if (!item?.type || !item?.data || typeof item.data !== "object") {
      console.warn("[MaxMode] attachItem() requires an item with a type and object data payload");
      return;
    }
    queueAttachment(item);
    try {
      window.dispatchEvent(new CustomEvent("maxmode:attach-item", { detail: { item } }));
    } catch {}
  },

  attachProduct(product) {
    MaxModeInstance.attachItem({ type: "product", data: product });
  },

  sendMessage(message, options) {
    const normalized = typeof message === "string" ? message.trim() : "";
    if (!normalized) {
      console.warn("[MaxMode] sendMessage() requires a non-empty message");
      return;
    }
    const queuedPrompt: MaxModeQueuedPrompt = {
      id: createPromptId(),
      message: normalized,
      open: options?.open !== false,
      position: options?.position,
      mode: options?.mode,
      requestContext: options?.requestContext,
    };
    enqueuePrompt(queuedPrompt);
    if (queuedPrompt.open) {
      openWidget();
    }
    try {
      window.dispatchEvent(new CustomEvent("maxmode:send-message", { detail: queuedPrompt }));
    } catch {}
  },

  destroy() {
    destroyWidget();
  },
};

// Expose globally
(window as any).MaxMode = MaxModeInstance;

function createPromptId() {
  if (window.crypto && typeof window.crypto.randomUUID === "function") {
    return "prompt-" + window.crypto.randomUUID();
  }
  return "prompt-" + Math.random().toString(36).slice(2) + Date.now().toString(36);
}

function enqueuePrompt(prompt: MaxModeQueuedPrompt) {
  try {
    const existing = JSON.parse(sessionStorage.getItem(PENDING_PROMPTS_KEY) || "[]");
    existing.push(prompt);
    sessionStorage.setItem(PENDING_PROMPTS_KEY, JSON.stringify(existing));
  } catch {}
}

export default MaxModeInstance;

function queueAttachment(item: SharedAttachment) {
  try {
    const existing = JSON.parse(sessionStorage.getItem(PENDING_ATTACHMENTS_KEY) || "[]");
    const alreadyQueued = Array.isArray(existing) && existing.some((entry) =>
      entry?.type === item.type &&
      ((entry?.data?.id && entry.data.id === item.data.id) ||
        (entry?.data?.sku && entry.data.sku === item.data.sku)),
    );
    if (alreadyQueued) {
      return;
    }
    const next = Array.isArray(existing) ? [...existing, item] : [item];
    sessionStorage.setItem(PENDING_ATTACHMENTS_KEY, JSON.stringify(next));
  } catch {}
}
