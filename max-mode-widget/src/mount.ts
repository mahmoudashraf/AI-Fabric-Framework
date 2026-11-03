/**
 * Shadow DOM mounting system for the IIFE (script-tag) build.
 *
 * Creates an isolated Shadow DOM container, injects styles,
 * and renders the React widget tree inside it.
 */
import React from "react";
import ReactDOM from "react-dom/client";

import { setWidgetConfig, getWidgetConfig, type MaxModeWidgetConfig } from "./config";
import { bindWidgetColorScheme, buildThemeCSS } from "./theme";
import { WidgetShell } from "./entries/WidgetShell";
import { bindWidgetViewport } from "./viewport";
import { updateWorkspaceHost, resetWorkspaceHostScope } from "./workspaceHost";
import { resetRuntimeCredentialCache } from "./api/client";

const WIDGET_CONTAINER_ID = "max-mode-widget-container";
const SHADOW_HOST_ID = "max-mode-widget-shadow-host";

let _root: ReactDOM.Root | null = null;
let _shadowRoot: ShadowRoot | null = null;
let _isOpen = false;
let _currentConfig: MaxModeWidgetConfig | null = null;
let _widgetStyleText = "";
let _releaseViewport: (() => void) | null = null;
let _releaseColorScheme: (() => void) | null = null;

export function isWidgetMounted(): boolean {
  return Boolean(document.getElementById(SHADOW_HOST_ID));
}

export function mountWidget(config: MaxModeWidgetConfig): void {
  // Prevent double mount
  if (document.getElementById(SHADOW_HOST_ID)) {
    console.warn("[MaxMode] Widget already mounted. Call MaxMode.destroy() first.");
    return;
  }

  setWidgetConfig(config);
  resetRuntimeCredentialCache();
  _currentConfig = config;

  // Create Shadow DOM host
  const host = document.createElement("div");
  host.id = SHADOW_HOST_ID;
  host.style.cssText =
    "display:block !important;position:fixed;z-index:2147483647;top:var(--max-mode-viewport-top,0px);left:var(--max-mode-viewport-left,0px);width:var(--max-mode-viewport-width,100vw);height:var(--max-mode-viewport-height,100dvh);pointer-events:none;overflow:visible;";
  document.body.appendChild(host);
  _releaseViewport = bindWidgetViewport(host);

  _shadowRoot = host.attachShadow({ mode: "open" });

  // Inject theme CSS variables
  const themeCSS = buildThemeCSS(config.theme);
  if (themeCSS) {
    const themeStyle = document.createElement("style");
    themeStyle.dataset.maxModeTheme = 'true';
    themeStyle.textContent = themeCSS;
    _shadowRoot.appendChild(themeStyle);
  }

  // Move the injected widget styles into Shadow DOM
  const injectedStyle = document.getElementById("max-mode-widget-styles");
  if (injectedStyle) {
    _widgetStyleText = injectedStyle.textContent || _widgetStyleText;
    injectedStyle.remove();
  }
  if (_widgetStyleText) {
    const widgetStyle = document.createElement("style");
    widgetStyle.id = "max-mode-widget-shadow-styles";
    widgetStyle.textContent = _widgetStyleText;
    _shadowRoot.appendChild(widgetStyle);
  }

  // Create the React render container
  const container = document.createElement("div");
  container.id = WIDGET_CONTAINER_ID;
  container.className = "max-mode-widget-root";
  container.style.cssText = "all:initial;position:fixed;inset:0;pointer-events:none;font-family:var(--mxw-font-family,Inter,system-ui,sans-serif);color:hsl(var(--mxw-foreground));line-height:1.5;";
  _shadowRoot.appendChild(container);
  _releaseColorScheme = bindWidgetColorScheme(container, config.theme?.darkMode);

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
  if (_currentConfig?.workspaceHost) updateWorkspaceHost(_currentConfig.workspaceHost, { isOpen: true });
  renderWidget();
}

export function closeWidget(): void {
  _isOpen = false;
  if (_currentConfig?.workspaceHost) updateWorkspaceHost(_currentConfig.workspaceHost, { isOpen: false });
  renderWidget();
}

export function toggleWidget(): void {
  _isOpen = !_isOpen;
  if (_currentConfig?.workspaceHost) updateWorkspaceHost(_currentConfig.workspaceHost, { isOpen: _isOpen });
  renderWidget();
}

/** Trusted host rebinding of an idle engine; layout, drafts and subscriptions stay mounted. */
export function setWidgetConnection(config: {
  apiConfig: MaxModeWidgetConfig['apiConfig'];
  integrationMode?: MaxModeWidgetConfig['integrationMode'];
  hostSession?: MaxModeWidgetConfig['hostSession'];
  readOnly?: boolean;
}): void {
  if (!_currentConfig || !_currentConfig.workspaceHost) throw new Error('The workspace is not mounted.');
  if (_currentConfig.workspaceHost.getState().busy) throw new Error('Wait for the current conversation operation to finish.');
  if (!config.apiConfig?.chatBaseUrl) throw new Error('A trusted chat API connection is required.');
  const previous = _currentConfig.hostSession;
  const next = config.hostSession ?? previous;
  if (!previous && next) throw new Error('Changing the host identity requires a fresh workspace.');
  if (previous && next?.accountKey !== previous.accountKey) {
    throw new Error('Changing the host identity requires a fresh workspace.');
  }
  if (previous && next?.namespace !== previous.namespace) resetWorkspaceHostScope(_currentConfig.workspaceHost);
  _currentConfig = {
    ..._currentConfig,
    apiConfig: config.apiConfig,
    integrationMode: config.integrationMode ?? _currentConfig.integrationMode,
    hostSession: next,
    host: { ..._currentConfig.host, readOnly: config.readOnly ?? _currentConfig.host?.readOnly },
  };
  setWidgetConfig(_currentConfig);
  resetRuntimeCredentialCache();
  renderWidget();
}

export function setWidgetTheme(theme: MaxModeWidgetConfig['theme']): void {
  if (!_currentConfig || !_shadowRoot) throw new Error('The workspace is not mounted.');
  _currentConfig = { ..._currentConfig, theme: { ..._currentConfig.theme, ...theme } };
  setWidgetConfig(_currentConfig);
  let style = _shadowRoot.querySelector<HTMLStyleElement>('style[data-max-mode-theme]');
  if (!style) {
    style = document.createElement('style');
    style.dataset.maxModeTheme = 'true';
    _shadowRoot.prepend(style);
  }
  style.textContent = buildThemeCSS(_currentConfig.theme) || '';
  const container = _shadowRoot.getElementById(WIDGET_CONTAINER_ID);
  if (container) {
    _releaseColorScheme?.();
    _releaseColorScheme = bindWidgetColorScheme(container, _currentConfig.theme?.darkMode);
  }
  renderWidget();
}

export function setWidgetReadOnly(readOnly: boolean): void {
  if (!_currentConfig) throw new Error('The workspace is not mounted.');
  if (Boolean(_currentConfig.host?.readOnly) === readOnly) return;
  _currentConfig = { ..._currentConfig, host: { ..._currentConfig.host, readOnly } };
  setWidgetConfig(_currentConfig);
  renderWidget();
}

export function destroyWidget(): void {
  if (_root) {
    _root.unmount();
    _root = null;
  }
  _releaseViewport?.();
  _releaseColorScheme?.();
  _releaseColorScheme = null;
  _releaseViewport = null;
  const host = document.getElementById(SHADOW_HOST_ID);
  if (host) host.remove();
  _shadowRoot = null;
  _isOpen = false;
  _currentConfig = null;
}
