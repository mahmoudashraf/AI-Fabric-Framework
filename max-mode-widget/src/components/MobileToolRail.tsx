import { AnimatePresence, motion } from "framer-motion";
import { ChevronsLeft, ChevronsRight } from "lucide-react";

import type { MaxModeToolScope } from "@/config";
import type { MaxModeResolvedToolRailItem } from "@/hooks/useMaxModeController";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";

const TONE_STYLES: Record<MaxModeResolvedToolRailItem["tone"], string> = {
  primary: "bg-blue-600 text-white hover:bg-blue-700",
  teal: "bg-emerald-600 text-white hover:bg-emerald-700",
  violet: "bg-violet-600 text-white hover:bg-violet-700",
  amber: "bg-amber-500 text-white hover:bg-amber-600",
  neutral: "bg-gray-700 text-white hover:bg-gray-800",
};

export function MobileToolRail({
  items,
  activeToolScope,
  contextualToolsAvailable,
  contextDocumentsCount,
  collapsed,
  onCollapsedChange,
  onOpenTools,
  onOpenDocuments,
  onPrompt,
}: {
  items: MaxModeResolvedToolRailItem[];
  activeToolScope: MaxModeToolScope;
  contextualToolsAvailable: boolean;
  contextDocumentsCount: number;
  collapsed: boolean;
  onCollapsedChange: (collapsed: boolean) => void;
  onOpenTools: (scope: MaxModeToolScope) => void;
  onOpenDocuments: () => void;
  onPrompt: (item: MaxModeResolvedToolRailItem) => void;
}) {
  const visibleItems = items.filter(
    (item) => item.action !== "open-documents" || contextDocumentsCount > 0,
  );
  if (visibleItems.length === 0) return null;

  return (
    <div
      className="fixed right-2 z-40 flex flex-col-reverse items-center gap-2 md:hidden"
      style={{ bottom: "calc(var(--max-mode-composer-inset, 180px) + 12px)" }}
      data-max-mode-tool-rail
    >
      <Button
        type="button"
        onClick={() => onCollapsedChange(!collapsed)}
        className="h-9 w-9 rounded-full border-2 border-white/40 bg-gray-700 p-0 text-white shadow-lg hover:bg-gray-800"
        aria-label={collapsed ? "Expand tool rail" : "Collapse tool rail"}
        aria-expanded={!collapsed}
      >
        {collapsed ? <ChevronsLeft className="h-4 w-4" /> : <ChevronsRight className="h-4 w-4" />}
      </Button>

      <AnimatePresence initial={false}>
        {!collapsed && visibleItems.map((item, index) => {
          const disabled = (
            item.action === "open-tools"
            && item.scope === "contextual"
            && !contextualToolsAvailable
          ) || (item.action === "prompt" && item.requiresContext && !contextualToolsAvailable);
          const active = item.action === "open-tools" && item.scope === activeToolScope;
          const Icon = item.icon;
          return (
            <motion.div
              key={item.id}
              initial={{ scale: 0.8, opacity: 0, x: 12 }}
              animate={{ scale: 1, opacity: 1, x: 0 }}
              exit={{ scale: 0.8, opacity: 0, x: 12 }}
              transition={{ type: "spring", damping: 22, delay: index * 0.03 }}
              className="flex flex-col items-center gap-1"
            >
              <Button
                type="button"
                disabled={disabled}
                onClick={() => {
                  if (item.action === "open-tools") {
                    onOpenTools(item.scope ?? "default");
                  } else if (item.action === "open-documents") {
                    onOpenDocuments();
                  } else {
                    onPrompt(item);
                  }
                }}
                className={`relative h-11 w-11 rounded-full border-2 border-white/40 p-0 shadow-xl disabled:cursor-not-allowed disabled:opacity-40 ${TONE_STYLES[item.tone]} ${active ? "ring-4 ring-blue-200" : ""}`}
                aria-label={railItemAriaLabel(item)}
                aria-pressed={item.action === "open-tools" ? active : undefined}
                data-max-mode-tool-rail-item={item.id}
                data-max-mode-tool-rail-action={item.action}
              >
                <Icon className="h-5 w-5" />
                {item.action === "open-documents" && contextDocumentsCount > 0 && (
                  <Badge className="absolute -right-1 -top-1 flex h-5 min-w-5 items-center justify-center rounded-full bg-red-500 px-1 text-[9px] font-bold text-white">
                    {contextDocumentsCount > 99 ? "99+" : contextDocumentsCount}
                  </Badge>
                )}
              </Button>
              <span className="max-w-16 truncate rounded-full bg-white/95 px-2 py-0.5 text-[9px] font-bold text-gray-800 shadow-sm dark:bg-gray-900/95 dark:text-gray-200">
                {item.label}
              </span>
            </motion.div>
          );
        })}
      </AnimatePresence>
    </div>
  );
}

function railItemAriaLabel(item: MaxModeResolvedToolRailItem) {
  if (item.action === "open-tools") return `Open ${item.label} tools`;
  if (item.action === "open-documents") return `Open ${item.label}`;
  return `Run ${item.label}`;
}
