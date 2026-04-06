/**
 * Self-contained toast notification renderer.
 * Renders in the widget's DOM tree — no portal needed.
 */
import React, { useEffect, useState } from "react";
import { X } from "lucide-react";
import { cn } from "@/lib/utils";
import { useToast, type ToastData } from "@/hooks/use-toast";

export function ToastContainer() {
  const { toasts, dismiss } = useToast();

  if (toasts.length === 0) return null;

  return (
    <div className="mxw-fixed mxw-bottom-4 mxw-right-4 mxw-z-[99999] mxw-flex mxw-flex-col mxw-gap-2 mxw-max-w-[360px]">
      {toasts.map((t) => (
        <ToastItem key={t.id} toast={t} onDismiss={() => dismiss(t.id)} />
      ))}
    </div>
  );
}

function ToastItem({
  toast,
  onDismiss,
}: {
  toast: ToastData;
  onDismiss: () => void;
}) {
  const [isVisible, setIsVisible] = useState(false);

  useEffect(() => {
    // Trigger enter animation
    requestAnimationFrame(() => setIsVisible(true));
  }, []);

  return (
    <div
      className={cn(
        "mxw-pointer-events-auto mxw-relative mxw-flex mxw-w-full mxw-items-center mxw-justify-between mxw-gap-3 mxw-overflow-hidden mxw-rounded-lg mxw-border mxw-p-4 mxw-pr-8 mxw-shadow-lg mxw-transition-all mxw-duration-300",
        toast.variant === "destructive"
          ? "mxw-border-destructive mxw-bg-destructive mxw-text-destructive-foreground"
          : "mxw-border mxw-bg-background mxw-text-foreground",
        isVisible
          ? "mxw-translate-x-0 mxw-opacity-100"
          : "mxw-translate-x-full mxw-opacity-0",
      )}
    >
      <div className="mxw-grid mxw-gap-1">
        {toast.title && (
          <div className="mxw-text-sm mxw-font-semibold">{toast.title}</div>
        )}
        {toast.description && (
          <div className="mxw-text-sm mxw-opacity-90">{toast.description}</div>
        )}
      </div>
      <button
        onClick={onDismiss}
        className="mxw-absolute mxw-right-2 mxw-top-2 mxw-rounded-md mxw-p-1 mxw-text-foreground/50 mxw-opacity-0 mxw-transition-opacity hover:mxw-text-foreground group-hover:mxw-opacity-100"
        style={{ opacity: 1 }}
      >
        <X className="mxw-h-4 mxw-w-4" />
      </button>
    </div>
  );
}
