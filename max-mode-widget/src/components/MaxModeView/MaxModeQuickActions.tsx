import { QuickActionsDesktop } from "../QuickActionsDesktop";
import { QuickActionsMobileSheet } from "../QuickActionsMobileSheet";

import type { MaxModeController } from "@/hooks/useMaxModeController";

export function MaxModeQuickActions({ controller }: { controller: MaxModeController }) {
  const {
    quickActions,
    toolGroups,
    activeToolScope,
    activeContextLabel,
    selectToolScope,
    isQuickActionsOpen,
    setIsQuickActionsOpen,
    handleQuickAction,
  } = controller;

  return (
    <>
      <QuickActionsDesktop
        quickActions={quickActions}
        toolGroups={toolGroups}
        activeToolScope={activeToolScope}
        activeContextLabel={activeContextLabel}
        onSelectToolScope={selectToolScope}
        onQuickAction={handleQuickAction}
      />

      <QuickActionsMobileSheet
        isOpen={isQuickActionsOpen}
        setIsOpen={setIsQuickActionsOpen}
        quickActions={quickActions}
        toolGroups={toolGroups}
        activeToolScope={activeToolScope}
        activeContextLabel={activeContextLabel}
        onSelectToolScope={selectToolScope}
        onQuickAction={handleQuickAction}
      />
    </>
  );
}
