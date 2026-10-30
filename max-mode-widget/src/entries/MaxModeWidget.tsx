/**
 * <MaxModeWidget /> — the main React component for npm consumers.
 *
 * Wraps the Max Mode experience with config provider, toast renderer,
 * and optional theming. Drop this into any React app.
 */
import React, { useEffect, useLayoutEffect, useMemo } from "react";

import {
  setWidgetConfig,
  getWidgetStorageKey,
  type MaxModeApiConfig,
  type MaxModeIntegrationMode,
  type MaxModeFeatures,
  type MaxModeHostConfig,
  type MaxModeThemeConfig,
  type MaxModeEvent,
  type MaxModeWidgetConfig,
} from "@/config";
import { MaxModeProvider, type SharedAttachment } from "@/context";
import { MaxModePage } from "@/components/MaxModePage";
import { ToastContainer } from "@/ui/toast-container";
import { applyThemeToElement, bindWidgetColorScheme } from "@/theme";
import { bindWidgetViewport } from "@/viewport";
import type { WorkspaceHostApi, WorkspaceHostSession } from "../workspaceHost";
import { resetRuntimeCredentialCache } from "../api/client";

// Import widget styles
import "@/styles/index.css";

export interface MaxModeWidgetProps {
  /** Whether the widget is open/visible */
  isOpen: boolean;
  /** Called when the user closes the widget */
  onClose: () => void;
  /** API endpoints and auth configuration */
  apiConfig: MaxModeApiConfig;
  /** Integration/auth posture */
  integrationMode?: MaxModeIntegrationMode;
  storageNamespace?: string;
  hostSession?: WorkspaceHostSession;
  workspaceHost?: WorkspaceHostApi;
  /** Items to pre-attach to the chat */
  initialAttachments?: SharedAttachment[];
  /** Host UX, routing, and optional current-page attachment configuration */
  host?: MaxModeHostConfig;
  /** Feature toggles */
  features?: MaxModeFeatures;
  /** Theme customization */
  theme?: MaxModeThemeConfig;
  /** Event callback */
  onEvent?: (event: MaxModeEvent) => void;
}

export function MaxModeWidget({
  isOpen,
  onClose,
  apiConfig,
  integrationMode,
  storageNamespace,
  hostSession,
  workspaceHost,
  initialAttachments,
  host,
  features,
  theme,
  onEvent,
}: MaxModeWidgetProps) {
  const resolvedHost = useMemo(() => ({
    ...host,
    initialAttachments: initialAttachments ?? host?.initialAttachments,
  }), [host, initialAttachments]);
  const resolvedConfig = useMemo<MaxModeWidgetConfig>(() => ({
    apiConfig,
    integrationMode,
    storageNamespace,
    hostSession,
    workspaceHost,
    features,
    theme,
    host: resolvedHost,
    onEvent,
    onClose,
  }), [apiConfig, features, integrationMode, storageNamespace, hostSession, workspaceHost, onClose, onEvent, resolvedHost, theme]);

  // API helpers still read the singleton; layout timing precedes controller effects.
  const credentialScope = React.useRef<string | null>(null);
  useLayoutEffect(() => {
    setWidgetConfig(resolvedConfig);
    const scope = getWidgetStorageKey("runtime-scope", resolvedConfig);
    if (scope !== credentialScope.current) {
      resetRuntimeCredentialCache();
      credentialScope.current = scope;
    }
  }, [resolvedConfig]);

  // Theme container ref
  const containerRef = React.useRef<HTMLDivElement>(null);
  useLayoutEffect(() => {
    if (containerRef.current) return bindWidgetViewport(containerRef.current);
  }, []);
  useEffect(() => {
    if (containerRef.current) {
      applyThemeToElement(containerRef.current, theme);
    }
  }, [theme]);
  useLayoutEffect(() => {
    if (containerRef.current) return bindWidgetColorScheme(containerRef.current, theme?.darkMode);
  }, [theme?.darkMode]);

  return (
    <div ref={containerRef} className="max-mode-widget-root">
      <MaxModeProvider config={resolvedConfig} key={JSON.stringify([storageNamespace, hostSession?.namespace, hostSession?.accountKey])}>
        <MaxModePage isOpen={isOpen} onClose={onClose} widgetConfig={resolvedConfig} />
        <ToastContainer />
      </MaxModeProvider>
    </div>
  );
}
