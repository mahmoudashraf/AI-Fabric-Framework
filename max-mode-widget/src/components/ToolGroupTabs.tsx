import { Focus } from "lucide-react";

import type { MaxModeToolScope } from "@/config";
import type { MaxModeResolvedToolGroup } from "@/hooks/useMaxModeController";

export function ToolGroupTabs({
  groups,
  activeScope,
  contextLabel,
  onSelect,
  compact = false,
  idPrefix = "max-mode-tools",
}: {
  groups: MaxModeResolvedToolGroup[];
  activeScope: MaxModeToolScope;
  contextLabel?: string;
  onSelect: (scope: MaxModeToolScope) => void;
  compact?: boolean;
  idPrefix?: string;
}) {
  if (groups.length === 0) return null;

  return (
    <div
      className={`flex min-w-0 flex-wrap items-center ${compact ? "gap-2" : "gap-3"}`}
      data-max-mode-tool-groups
    >
      <div
        role="tablist"
        aria-label="Assistant tool groups"
        className="inline-flex flex-shrink-0 items-center gap-1 rounded-lg border border-gray-200 bg-gray-100 p-1 dark:border-gray-700 dark:bg-gray-900"
      >
        {groups.map((group) => {
          const Icon = group.icon;
          const selected = activeScope === group.scope;
          return (
            <button
              key={group.scope}
              type="button"
              role="tab"
              aria-selected={selected}
              aria-controls={`${idPrefix}-${group.scope}`}
              disabled={!group.available}
              title={group.available ? group.label : "Attach context to enable this tool group"}
              onClick={() => onSelect(group.scope)}
              data-max-mode-tool-scope={group.scope}
              className={`inline-flex min-w-0 items-center gap-2 rounded-md font-semibold transition ${
                compact ? "min-h-8 px-2.5 text-xs" : "min-h-9 px-3 text-sm"
              } ${
                selected
                  ? "bg-white text-blue-700 shadow-sm ring-1 ring-blue-200 dark:bg-gray-800 dark:text-blue-300 dark:ring-blue-800"
                  : "text-gray-600 hover:bg-white/70 hover:text-gray-950 dark:text-gray-300 dark:hover:bg-gray-800"
              } disabled:cursor-not-allowed disabled:opacity-45`}
            >
              <Icon className={compact ? "h-3.5 w-3.5 flex-shrink-0" : "h-4 w-4 flex-shrink-0"} />
              <span className="whitespace-nowrap">{group.label}</span>
            </button>
          );
        })}
      </div>

      {activeScope === "contextual" && contextLabel && (
        <div
          className={`inline-flex min-w-0 items-center gap-1.5 rounded-md border border-blue-200 bg-blue-50 font-semibold text-blue-800 dark:border-blue-800 dark:bg-blue-950/40 dark:text-blue-200 ${
            compact ? "max-w-full px-2 py-1 text-[11px]" : "max-w-72 px-2.5 py-1.5 text-xs"
          }`}
          data-max-mode-active-context-label={contextLabel}
          aria-label={`Active context: ${contextLabel}`}
        >
          <Focus className="h-3.5 w-3.5 flex-shrink-0" />
          <span className="truncate">{contextLabel}</span>
        </div>
      )}
    </div>
  );
}
