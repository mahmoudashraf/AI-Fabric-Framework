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
    <div className="fixed bottom-4 right-4 z-[99999] flex max-w-[360px] flex-col gap-2">
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
        "pointer-events-auto relative flex w-full items-center justify-between gap-3 overflow-hidden rounded-lg border p-4 pr-8 shadow-lg transition-all duration-300",
        toast.variant === "destructive"
          ? "border-destructive bg-destructive text-destructive-foreground"
          : "border bg-background text-foreground",
        isVisible
          ? "translate-x-0 opacity-100"
          : "translate-x-full opacity-0",
      )}
    >
      <div className="grid gap-1">
        {toast.title && (
          <div className="text-sm font-semibold">{toast.title}</div>
        )}
        {toast.description && (
          <div className="text-sm opacity-90">{toast.description}</div>
        )}
      </div>
      <button
        onClick={onDismiss}
        className="absolute right-2 top-2 rounded-md p-1 text-foreground/50 opacity-0 transition-opacity hover:text-foreground group-hover:opacity-100"
        style={{ opacity: 1 }}
      >
        <X className="h-4 w-4" />
      </button>
    </div>
  );
}
