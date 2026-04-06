/**
 * <MaxModeWidget /> — the main React component for npm consumers.
 *
 * Wraps the Max Mode experience with config provider, toast renderer,
 * and optional theming. Drop this into any React app.
 */
import React, { useEffect, useMemo, useState } from "react";

import {
  setWidgetConfig,
  type MaxModeApiConfig,
  type MaxModeFeatures,
  type MaxModeThemeConfig,
  type MaxModeEvent,
} from "@/config";
import { MaxModeProvider, type SharedAttachment } from "@/context";
import { MaxModePage } from "@/components/MaxModePage";
import { ToastContainer } from "@/ui/toast-container";
import { applyThemeToElement } from "@/theme";

// Import widget styles
import "@/styles/index.css";

export interface MaxModeWidgetProps {
  /** Whether the widget is open/visible */
  isOpen: boolean;
  /** Called when the user closes the widget */
  onClose: () => void;
  /** API endpoints and auth configuration */
  apiConfig: MaxModeApiConfig;
  /** User identifier for cart/conversation scoping */
  userId?: string;
  /** Items to pre-attach to the chat */
  initialAttachments?: SharedAttachment[];
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
  userId,
  features,
  theme,
  onEvent,
}: MaxModeWidgetProps) {
  // Sync props to the global config singleton
  useEffect(() => {
    setWidgetConfig({
      apiConfig,
      userId,
      features,
      theme,
      onEvent,
      onClose,
    });
  }, [apiConfig, userId, features, theme, onEvent, onClose]);

  // Theme container ref
  const containerRef = React.useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (containerRef.current) {
      applyThemeToElement(containerRef.current, theme);
    }
  }, [theme]);

  return (
    <div ref={containerRef} className="max-mode-widget-root">
      <MaxModeProvider>
        <MaxModePage isOpen={isOpen} onClose={onClose} />
        <ToastContainer />
      </MaxModeProvider>
    </div>
  );
}
