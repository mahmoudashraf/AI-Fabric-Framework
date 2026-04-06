import type { Dispatch, SetStateAction } from "react";

import { AnimatePresence, motion } from "framer-motion";
import { ChevronDown, Package, X } from "lucide-react";

import { Button } from "@/ui/button";

import type { BrowseProductCategory, QuickAction, SearchCategory } from "@/constants";

export function QuickActionsMobileSheet({
  isOpen,
  setIsOpen,
  quickActions,
  isSearchCategoryOpen,
  setIsSearchCategoryOpen,
  isBrowseProductsOpen,
  setIsBrowseProductsOpen,
  searchCategories,
  browseProductCategories,
  onSelectSearchCategory,
  onQuickAction,
}: {
  isOpen: boolean;
  setIsOpen: Dispatch<SetStateAction<boolean>>;
  quickActions: QuickAction[];
  isSearchCategoryOpen: boolean;
  setIsSearchCategoryOpen: Dispatch<SetStateAction<boolean>>;
  isBrowseProductsOpen: boolean;
  setIsBrowseProductsOpen: Dispatch<SetStateAction<boolean>>;
  searchCategories: SearchCategory[];
  browseProductCategories: BrowseProductCategory[];
  onSelectSearchCategory: (categoryLabel: string) => void;
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
            className="md:hidden fixed inset-0 bg-black/50 backdrop-blur-sm z-[60]"
          />

          <motion.div
            initial={{ y: "100%" }}
            animate={{ y: 0 }}
            exit={{ y: "100%" }}
            transition={{ type: "spring", damping: 30, stiffness: 300 }}
            className="md:hidden fixed bottom-0 left-0 right-0 bg-white dark:bg-gray-900 rounded-t-3xl shadow-2xl z-[70] max-h-[70vh] overflow-hidden"
          >
            <div className="flex justify-center pt-3 pb-2">
              <div className="w-12 h-1.5 bg-gray-300 dark:bg-gray-700 rounded-full" />
            </div>

            <div className="px-6 py-4 border-b border-gray-200 dark:border-gray-800 flex items-center justify-between">
              <h3 className="text-lg font-bold bg-gradient-to-r from-blue-600 to-blue-500 bg-clip-text text-transparent">
                Quick Actions
              </h3>
              <Button variant="ghost" size="icon" onClick={() => setIsOpen(false)} className="h-8 w-8">
                <X className="h-5 w-5" />
              </Button>
            </div>

            <div className="p-6 overflow-y-auto max-h-[calc(70vh-120px)]">
              {isSearchCategoryOpen ? (
                <div>
                  <button
                    onClick={() => setIsSearchCategoryOpen(false)}
                    className="flex items-center gap-2 text-blue-600 dark:text-blue-400 mb-4 text-sm font-medium"
                  >
                    <ChevronDown className="h-4 w-4 rotate-90" />
                    Back to Actions
                  </button>
                  <h4 className="text-sm font-bold text-gray-700 dark:text-gray-300 mb-3">Search by Category</h4>
                  <div className="grid grid-cols-2 gap-3">
                    {searchCategories.map((cat, catIdx) => (
                      <motion.button
                        key={catIdx}
                        initial={{ opacity: 0, y: 10 }}
                        animate={{ opacity: 1, y: 0 }}
                        transition={{ delay: catIdx * 0.05 }}
                        onClick={() => onSelectSearchCategory(cat.label)}
                        className={`flex items-center gap-3 p-4 rounded-2xl ${cat.bg} border-2 ${cat.border} active:scale-95 transition-all`}
                      >
                        <span className="text-2xl">{cat.emoji}</span>
                        <span className={`text-sm font-bold ${cat.color}`}>{cat.label}</span>
                      </motion.button>
                    ))}
                  </div>
                </div>
              ) : isBrowseProductsOpen ? (
                <div>
                  <button
                    onClick={() => setIsBrowseProductsOpen(false)}
                    className="flex items-center gap-2 text-blue-600 dark:text-blue-400 mb-4 text-sm font-medium"
                  >
                    <ChevronDown className="h-4 w-4 rotate-90" />
                    Back to Actions
                  </button>
                  <div className="flex items-center gap-2 mb-3">
                    <Package className="h-4 w-4 text-blue-600 dark:text-blue-400" />
                    <h4 className="text-sm font-bold text-gray-700 dark:text-gray-300">Browse Products</h4>
                  </div>
                  <div className="grid grid-cols-2 gap-3">
                    {browseProductCategories.map((category, idx) => {
                      const Icon = category.icon;
                      return (
                        <motion.div
                          key={category.id}
                          initial={{ opacity: 0, y: 20 }}
                          animate={{ opacity: 1, y: 0 }}
                          transition={{ delay: idx * 0.05 }}
                          onClick={() => {
                            onQuickAction(category.query, "search", "navigator");
                            setIsBrowseProductsOpen(false);
                            setIsOpen(false);
                          }}
                          className="cursor-pointer group"
                        >
                          <div className="overflow-hidden rounded-xl border-2 border-gray-200 dark:border-gray-700 hover:border-purple-500 active:scale-95 transition-all">
                            <div
                              className={`h-16 bg-gradient-to-br ${category.color} flex items-center justify-center relative overflow-hidden`}
                            >
                              <Icon className="h-8 w-8 text-white/90 group-hover:scale-110 transition-transform" />
                              <div className="absolute inset-0 bg-black/0 group-hover:bg-black/10 transition-all" />
                            </div>
                            <div className="p-2 bg-white dark:bg-gray-800">
                              <h3 className="font-semibold text-xs mb-0.5">{category.label}</h3>
                              <p className="text-[10px] text-muted-foreground">{category.description}</p>
                            </div>
                          </div>
                        </motion.div>
                      );
                    })}
                  </div>
                </div>
              ) : (
                <div className="grid grid-cols-3 gap-3">
                  {quickActions.map((action, idx) => (
                    <motion.button
                      key={idx}
                      initial={{ opacity: 0, scale: 0.9 }}
                      animate={{ opacity: 1, scale: 1 }}
                      transition={{ delay: idx * 0.03 }}
                      onClick={() => {
                        if (action.label === "Search Products") {
                          setIsSearchCategoryOpen(true);
                        } else if (action.label === "Browse Products") {
                          setIsBrowseProductsOpen(true);
                        } else {
                          onQuickAction(action.query, action.position, action.mode);
                          setIsOpen(false);
                        }
                      }}
                      className={`flex flex-col items-center gap-2 p-4 rounded-2xl ${action.bg} border-2 ${action.border} active:scale-95 transition-all`}
                    >
                      <action.icon className={`h-7 w-7 ${action.color}`} />
                      <span className="text-[11px] font-semibold text-foreground text-center leading-tight">{action.label}</span>
                    </motion.button>
                  ))}
                </div>
              )}
            </div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
}

