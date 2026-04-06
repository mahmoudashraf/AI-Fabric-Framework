/**
 * WidgetShell — root component for the IIFE (script-tag) build.
 * Renders inside the Shadow DOM with a floating launcher button.
 */
import React from "react";
import { MessageCircle, X } from "lucide-react";
import { MaxModeProvider } from "@/context";
import { MaxModePage } from "@/components/MaxModePage";
import { ToastContainer } from "@/ui/toast-container";
import { emitEvent, type MaxModeWidgetConfig } from "@/config";

import "@/styles/index.css";

interface WidgetShellProps {
  config: MaxModeWidgetConfig;
  isOpen: boolean;
  onOpenChange: (open: boolean) => void;
}

export function WidgetShell({ config, isOpen, onOpenChange }: WidgetShellProps) {
  const showLauncher = config.launcher !== false;
  const position = config.position ?? "bottom-right";
  const isRight = position === "bottom-right";

  const handleOpen = () => {
    onOpenChange(true);
    emitEvent("widget:opened");
  };

  const handleClose = () => {
    onOpenChange(false);
    emitEvent("widget:closed");
    config.onClose?.();
  };

  return (
    <MaxModeProvider>
      {/* Floating launcher button */}
      {showLauncher && !isOpen && (
        <button
          onClick={handleOpen}
          aria-label="Open AI Assistant"
          style={{
            position: "fixed",
            bottom: "24px",
            [isRight ? "right" : "left"]: "24px",
            width: "60px",
            height: "60px",
            borderRadius: "50%",
            background: "linear-gradient(135deg, #6366f1, #8b5cf6, #a855f7)",
            color: "white",
            border: "none",
            cursor: "pointer",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            boxShadow: "0 4px 20px rgba(99, 102, 241, 0.4), 0 2px 8px rgba(0,0,0,0.1)",
            transition: "all 0.3s cubic-bezier(0.4, 0, 0.2, 1)",
            zIndex: 2147483646,
            pointerEvents: "auto",
          }}
          onMouseEnter={(e) => {
            (e.target as HTMLButtonElement).style.transform = "scale(1.1)";
            (e.target as HTMLButtonElement).style.boxShadow =
              "0 6px 28px rgba(99, 102, 241, 0.5), 0 4px 12px rgba(0,0,0,0.15)";
          }}
          onMouseLeave={(e) => {
            (e.target as HTMLButtonElement).style.transform = "scale(1)";
            (e.target as HTMLButtonElement).style.boxShadow =
              "0 4px 20px rgba(99, 102, 241, 0.4), 0 2px 8px rgba(0,0,0,0.1)";
          }}
        >
          <MessageCircle size={28} strokeWidth={2} />
        </button>
      )}

      {/* Widget overlay */}
      {isOpen && (
        <div
          style={{
            position: "fixed",
            inset: 0,
            zIndex: 2147483646,
            pointerEvents: "auto",
          }}
        >
          <MaxModePage isOpen={isOpen} onClose={handleClose} />
        </div>
      )}

      <ToastContainer />
    </MaxModeProvider>
  );
}
