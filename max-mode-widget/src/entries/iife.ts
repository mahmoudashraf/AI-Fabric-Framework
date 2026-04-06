/**
 * IIFE entry point — exposes `window.MaxMode` for script-tag usage.
 *
 * Usage:
 *   <script src="https://cdn.example.com/max-mode-widget.iife.js"></script>
 *   <script>
 *     MaxMode.init({
 *       apiConfig: {
 *         chatBaseUrl: "https://your-api.com/api",
 *         crudBaseUrl: "https://your-crud-api.com/api",
 *         headers: { "X-API-KEY": "your-key" }
 *       },
 *       userId: "user_123",
 *       theme: { primaryColor: "#6366f1" },
 *       position: "bottom-right",
 *     });
 *   </script>
 */

import type { MaxModeWidgetConfig } from "@/config";
import type { SharedAttachment } from "@/context";
import { mountWidget, openWidget, closeWidget, toggleWidget, destroyWidget } from "@/mount";

export interface MaxModeAPI {
  /** Initialize and mount the widget */
  init: (config: Partial<MaxModeWidgetConfig> & { apiConfig: MaxModeWidgetConfig["apiConfig"] }) => void;
  /** Open the widget */
  open: () => void;
  /** Close the widget */
  close: () => void;
  /** Toggle widget open/closed */
  toggle: () => void;
  /** Attach a product to the chat */
  attachProduct: (product: { sku: string; name: string; price: number; [key: string]: any }) => void;
  /** Send a message programmatically */
  sendMessage: (message: string) => void;
  /** Destroy and unmount the widget completely */
  destroy: () => void;
  /** Widget version */
  version: string;
}

// Pending attachments queue (for products attached before widget opens)
const _pendingProducts: SharedAttachment[] = [];

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

  attachProduct(product) {
    _pendingProducts.push({ type: "product", data: product });
    // If widget is mounted, add to sessionStorage for pickup
    try {
      const existing = JSON.parse(
        sessionStorage.getItem("maxmode_widget_pending_attachments") || "[]",
      );
      existing.push({ type: "product", data: product });
      sessionStorage.setItem(
        "maxmode_widget_pending_attachments",
        JSON.stringify(existing),
      );
    } catch {}
  },

  sendMessage(_message: string) {
    // TODO: Expose a ref-based message send through the widget shell
    console.warn("[MaxMode] sendMessage() is not yet implemented in IIFE mode");
  },

  destroy() {
    destroyWidget();
  },
};

// Expose globally
(window as any).MaxMode = MaxModeInstance;

export default MaxModeInstance;
