import type { Dispatch, SetStateAction } from "react";

import { AnimatePresence, motion } from "framer-motion";
import { X } from "lucide-react";

import { ToolGroupTabs } from "@/components/ToolGroupTabs";
import type { MaxModeToolScope } from "@/config";
import type { QuickAction } from "@/constants";
import type { MaxModeResolvedToolGroup } from "@/hooks/useMaxModeController";
import { Button } from "@/ui/button";

export function QuickActionsMobileSheet({
  isOpen,
  setIsOpen,
  quickActions,
  toolGroups,
  activeToolScope,
  activeContextLabel,
  onSelectToolScope,
  onQuickAction,
}: {
  isOpen: boolean;
  setIsOpen: Dispatch<SetStateAction<boolean>>;
  quickActions: QuickAction[];
  toolGroups: MaxModeResolvedToolGroup[];
  activeToolScope: MaxModeToolScope;
  activeContextLabel?: string;
  onSelectToolScope: (scope: MaxModeToolScope) => void;
  onQuickAction: (query: string, position?: QuickAction["position"], mode?: QuickAction["mode"]) => void;
}) {
  return (
    <AnimatePresence>
      {isOpen && (
        <>
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={() => setIsOpen(false)}
            className="fixed inset-0 z-[60] bg-black/50 backdrop-blur-sm md:hidden"
          />

          <motion.div
            initial={{ y: "100%" }}
            animate={{ y: 0 }}
            exit={{ y: "100%" }}
            transition={{ type: "spring", damping: 30, stiffness: 300 }}
            className="fixed bottom-0 left-0 right-0 z-[70] max-h-[70vh] overflow-hidden rounded-t-3xl bg-white shadow-2xl dark:bg-gray-900 md:hidden"
          >
            <div className="flex justify-center pb-2 pt-3">
              <div className="h-1.5 w-12 rounded-full bg-gray-300 dark:bg-gray-700" />
            </div>

            <div className="flex items-center justify-between border-b border-gray-200 px-6 py-4 dark:border-gray-800">
              <h3 className="text-lg font-bold text-blue-700 dark:text-blue-300">Tools</h3>
              <Button
                variant="ghost"
                size="icon"
                onClick={() => setIsOpen(false)}
                className="h-8 w-8"
                aria-label="Close tools"
              >
                <X className="h-5 w-5" />
              </Button>
            </div>

            <div className="max-h-[calc(70vh-120px)] overflow-y-auto p-6">
              {toolGroups.length > 0 && (
                <div className="mb-5 overflow-x-auto pb-1">
                  <ToolGroupTabs
                    groups={toolGroups}
                    activeScope={activeToolScope}
                    contextLabel={activeContextLabel}
                    onSelect={onSelectToolScope}
                    compact
                    idPrefix="max-mode-mobile-tools"
                  />
                </div>
              )}
              <div
                id={`max-mode-mobile-tools-${activeToolScope}`}
                role={toolGroups.length > 0 ? "tabpanel" : undefined}
                className="grid grid-cols-3 gap-3"
              >
                {quickActions.map((action, index) => (
                  <motion.button
                    key={`${activeToolScope}-${action.label}`}
                    type="button"
                    initial={{ opacity: 0, scale: 0.9 }}
                    animate={{ opacity: 1, scale: 1 }}
                    transition={{ delay: index * 0.03 }}
                    data-max-mode-quick-action={action.label}
                    onClick={() => {
                      onQuickAction(action.query, action.position, action.mode);
                      setIsOpen(false);
                    }}
                    className={`flex flex-col items-center gap-2 rounded-2xl border-2 p-4 transition-all active:scale-95 ${action.bg} ${action.border}`}
                  >
                    <action.icon className={`h-7 w-7 ${action.color}`} />
                    <span className="text-center text-[11px] font-semibold leading-tight text-foreground">{action.label}</span>
                  </motion.button>
                ))}
              </div>
            </div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
}
