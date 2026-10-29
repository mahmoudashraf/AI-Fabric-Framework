/**
 * WidgetShell — root component for the IIFE (script-tag) build.
 * Renders inside the Shadow DOM with a floating launcher button.
 */
import React from "react";
import { MessageCircle } from "lucide-react";
import { MaxModeProvider } from "@/context";
import { CompanionDock } from "@/components/CompanionDock";
import { AssistantBrandMark } from "@/components/AssistantBrandMark";
import { MaxModeView } from "@/components/MaxModeView";
import { useMaxModeController } from "@/hooks/useMaxModeController";
import { ToastContainer } from "@/ui/toast-container";
import { emitEvent, type MaxModeWidgetConfig } from "@/config";

import "@/styles/index.css";

interface WidgetShellProps {
  config: MaxModeWidgetConfig;
  isOpen: boolean;
  onOpenChange: (open: boolean) => void;
}

export function WidgetShell({ config, isOpen, onOpenChange }: WidgetShellProps) {
  return (
    <MaxModeProvider>
      <WidgetShellContent config={config} isOpen={isOpen} onOpenChange={onOpenChange} />
    </MaxModeProvider>
  );
}

function WidgetShellContent({ config, isOpen, onOpenChange }: WidgetShellProps) {
  const showLauncher = config.launcher !== false;
  const showCompanionDock = config.host?.companionDock === true;
  const position = config.position ?? "bottom-right";
  const isRight = position === "bottom-right";
  const launcherVariant = config.host?.launcherVariant ?? "icon";
  const launcherLabel = config.host?.launcherLabel?.trim() || "Ask the assistant";
  const launcherAriaLabel = config.host?.launcherAriaLabel?.trim() || launcherLabel;
  const assistantLabel = config.host?.assistantLabel?.trim() || "MAX AI";
  const showUtilityPanel = config.host?.showUtilityPanel ?? true;
  const controller = useMaxModeController({
    isOpen: isOpen || showCompanionDock,
    assistantLabel,
    showUtilityPanel,
    widgetConfig: config,
  });

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
    <>
      {showCompanionDock && !isOpen && (
        <CompanionDock
          controller={controller}
          onOpenMax={handleOpen}
          contextLabel={config.host?.companionContextLabel}
          modeLabel={config.host?.companionModeLabel}
          placeholder={config.host?.companionPlaceholder}
          emptyMessage={config.host?.companionEmptyMessage}
        />
      )}

      {/* Floating launcher button */}
      {showLauncher && !isOpen && (
        <button
          onClick={handleOpen}
          aria-label={launcherAriaLabel}
          title={launcherLabel}
          style={{
            position: "fixed",
            bottom: "calc(var(--max-mode-viewport-bottom, 0px) + max(24px, env(safe-area-inset-bottom, 0px)))",
            [isRight ? "right" : "left"]: isRight
              ? "calc(var(--max-mode-viewport-right, 0px) + max(24px, env(safe-area-inset-right, 0px)))"
              : "calc(var(--max-mode-viewport-left, 0px) + max(24px, env(safe-area-inset-left, 0px)))",
            minWidth: launcherVariant === "pill" ? "auto" : "60px",
            width: launcherVariant === "pill" ? "auto" : "60px",
            height: "60px",
            padding: launcherVariant === "pill" ? "0 20px" : undefined,
            borderRadius: launcherVariant === "pill" ? "9999px" : "50%",
            background: "hsl(var(--mxw-primary, 221.2 83.2% 53.3%))",
            color: "white",
            border: "none",
            cursor: "pointer",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            gap: launcherVariant === "pill" ? "10px" : undefined,
            boxShadow: "0 4px 20px hsl(var(--mxw-primary, 221.2 83.2% 53.3%) / 0.4), 0 2px 8px rgba(0,0,0,0.1)",
            transition: "all 0.3s cubic-bezier(0.4, 0, 0.2, 1)",
            zIndex: 2147483646,
            pointerEvents: "auto",
            fontWeight: 700,
            fontSize: launcherVariant === "pill" ? "14px" : undefined,
          }}
          onMouseEnter={(e) => {
            e.currentTarget.style.transform = "scale(1.1)";
            e.currentTarget.style.boxShadow =
              "0 6px 28px hsl(var(--mxw-primary, 221.2 83.2% 53.3%) / 0.5), 0 4px 12px rgba(0,0,0,0.15)";
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.transform = "scale(1)";
            e.currentTarget.style.boxShadow =
              "0 4px 20px hsl(var(--mxw-primary, 221.2 83.2% 53.3%) / 0.4), 0 2px 8px rgba(0,0,0,0.1)";
          }}
        >
          <AssistantBrandMark logoUrl={config.theme?.logoUrl} fallbackIcon={MessageCircle} className="h-7 w-7" />
          {launcherVariant === "pill" && <span>{launcherLabel}</span>}
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
          <MaxModeView onClose={handleClose} controller={controller} />
        </div>
      )}

      <ToastContainer />
    </>
  );
}
