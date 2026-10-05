import { motion } from "framer-motion";

import { ToolGroupTabs } from "@/components/ToolGroupTabs";
import type { MaxModeToolScope } from "@/config";
import type { QuickAction } from "@/constants";
import type { MaxModeResolvedToolGroup } from "@/hooks/useMaxModeController";

export function QuickActionsDesktop({
  quickActions,
  toolGroups,
  activeToolScope,
  activeContextLabel,
  onSelectToolScope,
  onQuickAction,
}: {
  quickActions: QuickAction[];
  toolGroups: MaxModeResolvedToolGroup[];
  activeToolScope: MaxModeToolScope;
  activeContextLabel?: string;
  onSelectToolScope: (scope: MaxModeToolScope) => void;
  onQuickAction: (query: string, position?: QuickAction["position"], mode?: QuickAction["mode"]) => void;
}) {
  if (toolGroups.length === 0 && quickActions.length === 0) return null;

  return (
    <div className="absolute left-0 right-0 top-0 z-10 hidden border-b border-gray-200 bg-white px-6 py-3 dark:border-gray-800 dark:bg-gray-950 md:block">
      {toolGroups.length > 0 && (
        <div className="mb-2 pr-48">
          <ToolGroupTabs
            groups={toolGroups}
            activeScope={activeToolScope}
            contextLabel={activeContextLabel}
            onSelect={onSelectToolScope}
            compact
            idPrefix="max-mode-desktop-tools"
          />
        </div>
      )}
      <div
        id={`max-mode-desktop-tools-${activeToolScope}`}
        role={toolGroups.length > 0 ? "tabpanel" : undefined}
        className="flex gap-2 overflow-x-auto scrollbar-hide"
      >
        {quickActions.slice(0, 8).map((action, index) => (
          <motion.button
            key={`${activeToolScope}-${action.label}`}
            type="button"
            initial={{ opacity: 0, y: -10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: index * 0.05 }}
            onClick={() => onQuickAction(action.query, action.position, action.mode)}
            data-max-mode-quick-action={action.label}
            className={`flex min-h-11 min-w-[80px] items-center gap-2 rounded-lg border px-3 py-2 transition-all hover:-translate-y-0.5 ${action.bg} ${action.border}`}
          >
            <action.icon className={`h-4 w-4 ${action.color}`} />
            <span className="whitespace-nowrap text-xs font-semibold text-foreground">{action.label}</span>
          </motion.button>
        ))}
      </div>
    </div>
  );
}
