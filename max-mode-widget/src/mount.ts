/**
 * Shadow DOM mounting system for the IIFE (script-tag) build.
 *
 * Creates an isolated Shadow DOM container, injects styles,
 * and renders the React widget tree inside it.
 */
import React from "react";
import ReactDOM from "react-dom/client";

import { setWidgetConfig, getWidgetConfig, type MaxModeWidgetConfig } from "./config";
import { buildThemeCSS } from "./theme";
import { WidgetShell } from "./entries/WidgetShell";

const WIDGET_CONTAINER_ID = "max-mode-widget-container";
const SHADOW_HOST_ID = "max-mode-widget-shadow-host";

let _root: ReactDOM.Root | null = null;
let _shadowRoot: ShadowRoot | null = null;
let _isOpen = false;
let _currentConfig: MaxModeWidgetConfig | null = null;

export function mountWidget(config: MaxModeWidgetConfig): void {
  // Prevent double mount
  if (document.getElementById(SHADOW_HOST_ID)) {
    console.warn("[MaxMode] Widget already mounted. Call MaxMode.destroy() first.");
    return;
  }

  setWidgetConfig(config);
  _currentConfig = config;

  // Create Shadow DOM host
  const host = document.createElement("div");
  host.id = SHADOW_HOST_ID;
  host.style.cssText =
    "position:fixed;z-index:2147483647;top:0;left:0;width:0;height:0;pointer-events:none;";
  document.body.appendChild(host);

  _shadowRoot = host.attachShadow({ mode: "open" });

  // Inject theme CSS variables
  const themeCSS = buildThemeCSS(config.theme);
  if (themeCSS) {
    const themeStyle = document.createElement("style");
    themeStyle.textContent = themeCSS;
    _shadowRoot.appendChild(themeStyle);
  }

  // Move the injected widget styles into Shadow DOM
  const injectedStyle = document.getElementById("max-mode-widget-styles");
  if (injectedStyle) {
    _shadowRoot.appendChild(injectedStyle.cloneNode(true));
    injectedStyle.remove();
  }

  // Create the React render container
  const container = document.createElement("div");
  container.id = WIDGET_CONTAINER_ID;
  container.className = "max-mode-widget-root";
  container.style.cssText = "all:initial;pointer-events:auto;";
  _shadowRoot.appendChild(container);

  // Render widget
  _root = ReactDOM.createRoot(container);
  renderWidget();
}

function renderWidget() {
  if (!_root || !_currentConfig) return;

  _root.render(
    React.createElement(WidgetShell, {
      config: _currentConfig,
      isOpen: _isOpen,
      onOpenChange: (open: boolean) => {
        _isOpen = open;
        renderWidget();
      },
    }),
  );
}

export function openWidget(): void {
  _isOpen = true;
  renderWidget();
}

export function closeWidget(): void {
  _isOpen = false;
  renderWidget();
}

export function toggleWidget(): void {
  _isOpen = !_isOpen;
  renderWidget();
}

export function destroyWidget(): void {
  if (_root) {
    _root.unmount();
    _root = null;
  }
  const host = document.getElementById(SHADOW_HOST_ID);
  if (host) host.remove();
  _shadowRoot = null;
  _isOpen = false;
  _currentConfig = null;
}
