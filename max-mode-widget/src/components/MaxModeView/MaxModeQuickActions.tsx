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
    searchCategories,
    browseProductCategories,
    isSearchCategoryOpen,
    setIsSearchCategoryOpen,
    isBrowseProductsOpen,
    setIsBrowseProductsOpen,
    isQuickActionsOpen,
    setIsQuickActionsOpen,
    handleSelectSearchCategory,
    handleQuickAction,
  } = controller;

  return (
    <>
      <QuickActionsDesktop
        quickActions={quickActions}
        isSearchCategoryOpen={isSearchCategoryOpen}
        setIsSearchCategoryOpen={setIsSearchCategoryOpen}
        isBrowseProductsOpen={isBrowseProductsOpen}
        setIsBrowseProductsOpen={setIsBrowseProductsOpen}
        searchCategories={searchCategories}
        browseProductCategories={browseProductCategories}
        toolGroups={toolGroups}
        activeToolScope={activeToolScope}
        activeContextLabel={activeContextLabel}
        onSelectToolScope={selectToolScope}
        onSelectSearchCategory={handleSelectSearchCategory}
        onQuickAction={handleQuickAction}
      />

      <QuickActionsMobileSheet
        isOpen={isQuickActionsOpen}
        setIsOpen={setIsQuickActionsOpen}
        quickActions={quickActions}
        isSearchCategoryOpen={isSearchCategoryOpen}
        setIsSearchCategoryOpen={setIsSearchCategoryOpen}
        isBrowseProductsOpen={isBrowseProductsOpen}
        setIsBrowseProductsOpen={setIsBrowseProductsOpen}
        searchCategories={searchCategories}
        browseProductCategories={browseProductCategories}
        toolGroups={toolGroups}
        activeToolScope={activeToolScope}
        activeContextLabel={activeContextLabel}
        onSelectToolScope={selectToolScope}
        onSelectSearchCategory={handleSelectSearchCategory}
        onQuickAction={handleQuickAction}
      />
    </>
  );
}
