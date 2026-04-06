import type { Dispatch, SetStateAction } from "react";

import { AnimatePresence, motion } from "framer-motion";
import { Package } from "lucide-react";

import type { BrowseProductCategory, QuickAction, SearchCategory } from "@/constants";

export function QuickActionsDesktop({
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
    <>
      <div className="hidden md:block absolute top-0 left-0 right-0 px-6 py-4 bg-white/50 dark:bg-gray-800/50 backdrop-blur-sm border-b z-10">
        <div className="flex gap-2 overflow-x-auto pb-2 scrollbar-hide">
          {quickActions.slice(0, 8).map((action, idx) => (
            <div key={idx}>
              {action.label === "Search Products" ? (
                <motion.button
                  initial={{ opacity: 0, y: -10 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: idx * 0.05 }}
                  onClick={() => setIsSearchCategoryOpen(!isSearchCategoryOpen)}
                  className={`flex flex-col items-center gap-1 p-3 rounded-xl ${action.bg} border ${action.border} hover:scale-105 transition-all min-w-[80px] ${isSearchCategoryOpen ? "ring-2 ring-blue-500" : ""}`}
                >
                  <action.icon className={`h-5 w-5 ${action.color}`} />
                  <span className="text-[10px] font-medium text-foreground whitespace-nowrap">{action.label}</span>
                </motion.button>
              ) : action.label === "Browse Products" ? (
                <motion.button
                  initial={{ opacity: 0, y: -10 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: idx * 0.05 }}
                  onClick={() => setIsBrowseProductsOpen(!isBrowseProductsOpen)}
                  className={`flex flex-col items-center gap-1 p-3 rounded-xl ${action.bg} border ${action.border} hover:scale-105 transition-all min-w-[80px] ${isBrowseProductsOpen ? "ring-2 ring-blue-500" : ""}`}
                >
                  <action.icon className={`h-5 w-5 ${action.color}`} />
                  <span className="text-[10px] font-medium text-foreground whitespace-nowrap">{action.label}</span>
                </motion.button>
              ) : (
                <motion.button
                  initial={{ opacity: 0, y: -10 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: idx * 0.05 }}
                  onClick={() => onQuickAction(action.query, action.position, action.mode)}
                  className={`flex flex-col items-center gap-1 p-3 rounded-xl ${action.bg} border ${action.border} hover:scale-105 transition-all min-w-[80px]`}
                >
                  <action.icon className={`h-5 w-5 ${action.color}`} />
                  <span className="text-[10px] font-medium text-foreground whitespace-nowrap">{action.label}</span>
                </motion.button>
              )}
            </div>
          ))}
        </div>
      </div>

      <AnimatePresence>
        {isSearchCategoryOpen && (
          <>
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              className="hidden md:block fixed inset-0 z-40"
              onClick={() => setIsSearchCategoryOpen(false)}
            />
            <motion.div
              initial={{ opacity: 0, y: -10, scale: 0.95 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: -10, scale: 0.95 }}
              className="hidden md:block fixed top-[80px] left-6 bg-white dark:bg-gray-800 rounded-2xl shadow-2xl border-2 border-blue-200 dark:border-blue-700 p-4 z-50 min-w-[320px]"
            >
              <div className="text-sm font-bold text-blue-600 dark:text-blue-400 mb-3 px-1">Select Category to Search</div>
              <div className="flex flex-wrap gap-2">
                {searchCategories.map((cat, catIdx) => (
                  <motion.button
                    key={catIdx}
                    initial={{ opacity: 0, scale: 0.9 }}
                    animate={{ opacity: 1, scale: 1 }}
                    transition={{ delay: catIdx * 0.03 }}
                    onClick={() => onSelectSearchCategory(cat.label)}
                    className={`flex items-center gap-2 px-4 py-2.5 rounded-full ${cat.bg} border-2 ${cat.border} hover:scale-105 transition-all text-left shadow-sm`}
                  >
                    <span className="text-base">{cat.emoji}</span>
                    <span className={`text-sm font-semibold ${cat.color}`}>{cat.label}</span>
                  </motion.button>
                ))}
              </div>
            </motion.div>
          </>
        )}
      </AnimatePresence>

      <AnimatePresence>
        {isBrowseProductsOpen && (
          <>
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              className="hidden md:block fixed inset-0 z-40"
              onClick={() => setIsBrowseProductsOpen(false)}
            />
            <motion.div
              initial={{ opacity: 0, y: -10, scale: 0.95 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: -10, scale: 0.95 }}
              className="hidden md:block fixed top-[80px] left-6 bg-white dark:bg-gray-800 rounded-2xl shadow-2xl border-2 border-blue-200 dark:border-blue-700 p-6 z-50 max-w-[600px]"
            >
              <div className="flex items-center gap-2 mb-4">
                <Package className="h-5 w-5 text-blue-600 dark:text-blue-400" />
                <div className="text-sm font-bold text-blue-600 dark:text-blue-400">Browse Products</div>
              </div>
              <div className="grid grid-cols-3 gap-3">
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
                      }}
                      className="cursor-pointer group"
                    >
                      <div className="overflow-hidden rounded-xl border-2 border-gray-200 dark:border-gray-700 hover:border-blue-500 transition-all hover:shadow-lg">
                        <div
                          className={`h-20 bg-gradient-to-br ${category.color} flex items-center justify-center relative overflow-hidden`}
                        >
                          <Icon className="h-10 w-10 text-white/90 group-hover:scale-110 transition-transform" />
                          <div className="absolute inset-0 bg-black/0 group-hover:bg-black/10 transition-all" />
                        </div>
                        <div className="p-3 bg-white dark:bg-gray-800">
                          <h3 className="font-semibold text-sm mb-0.5">{category.label}</h3>
                          <p className="text-xs text-muted-foreground">{category.description}</p>
                        </div>
                      </div>
                    </motion.div>
                  );
                })}
              </div>
            </motion.div>
          </>
        )}
      </AnimatePresence>
    </>
  );
}

