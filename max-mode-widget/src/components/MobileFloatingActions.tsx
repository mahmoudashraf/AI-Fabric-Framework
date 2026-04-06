import type { RefObject } from "react";

import { AnimatePresence, motion } from "framer-motion";
import {
  ArrowRight,
  BrainCircuit,
  ChevronsLeft,
  ChevronsRight,
  FileText,
  List,
  Package,
  Search,
  ShoppingCart,
  X,
} from "lucide-react";

import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";

import type { AISearchCategory, BrowseProductCategory } from "@/constants";

export function MobileFloatingActions({
  isAISearchOpen,
  setIsAISearchOpen,
  aiSearchCategories,
  aiSearchRowRef,
  aiSearchButtonRef,
  onAISearchCategory,
  isFloatingMenuCollapsed,
  setIsFloatingMenuCollapsed,
  contextDocumentsCount,
  onOpenDocuments,
  onOpenCart,
  isQuickActionsOpen,
  setIsQuickActionsOpen,
  isBrowseProductsOpen,
  setIsBrowseProductsOpen,
  browseProductCategories,
  onBrowseProductCategory,
}: {
  isAISearchOpen: boolean;
  setIsAISearchOpen: (open: boolean) => void;
  aiSearchCategories: AISearchCategory[];
  aiSearchRowRef: RefObject<HTMLDivElement>;
  aiSearchButtonRef: RefObject<HTMLDivElement>;
  onAISearchCategory: (category: AISearchCategory) => void;
  isFloatingMenuCollapsed: boolean;
  setIsFloatingMenuCollapsed: (collapsed: boolean) => void;
  contextDocumentsCount: number;
  onOpenDocuments: () => void;
  onOpenCart: () => void;
  isQuickActionsOpen: boolean;
  setIsQuickActionsOpen: (open: boolean) => void;
  isBrowseProductsOpen: boolean;
  setIsBrowseProductsOpen: (open: boolean) => void;
  browseProductCategories: BrowseProductCategory[];
  onBrowseProductCategory: (category: BrowseProductCategory) => void;
}) {
  return (
    <>
      <AnimatePresence>
        {isAISearchOpen && (
          <motion.div
            ref={aiSearchRowRef}
            initial={{ opacity: 0, x: -20 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0, x: -20 }}
            transition={{ type: "spring", damping: 20 }}
            className="md:hidden fixed bottom-32 left-3 right-20 z-40 flex items-center gap-2 overflow-x-auto scrollbar-hide px-2 py-2 bg-white/80 dark:bg-gray-900/80 backdrop-blur-sm rounded-full"
          >
            {aiSearchCategories.map((category, idx) => (
              <motion.button
                key={category.label}
                initial={{ opacity: 0, scale: 0.8 }}
                animate={{ opacity: 1, scale: 1 }}
                transition={{ delay: idx * 0.05 }}
                onClick={() => {
                  onAISearchCategory(category);
                  setIsAISearchOpen(false);
                }}
                className="flex-shrink-0 flex flex-col items-center gap-1"
              >
                <div
                  className={`h-12 w-12 rounded-full border-2 ${category.border} flex items-center justify-center transition-all active:scale-95 hover:scale-105`}
                >
                  <category.icon className={`h-5 w-5 ${category.color}`} />
                </div>
                <span
                  className={`text-[8px] font-semibold ${category.color} leading-tight text-center max-w-[48px] bg-white/90 dark:bg-gray-800/90 px-1.5 py-0.5 rounded-full`}
                >
                  {category.label}
                </span>
              </motion.button>
            ))}
          </motion.div>
        )}
      </AnimatePresence>

      <div className="md:hidden fixed bottom-32 right-1 z-40 flex flex-col-reverse items-center gap-3">
        <motion.div
          initial={{ scale: 0.9, opacity: 0 }}
          animate={{ scale: 1, opacity: 1 }}
          className="flex flex-col items-center gap-1"
        >
          <Button
            onClick={() => setIsFloatingMenuCollapsed(!isFloatingMenuCollapsed)}
            size="lg"
            className="h-10 w-10 rounded-full bg-gradient-to-br from-gray-600 to-gray-700 hover:from-gray-700 hover:to-gray-800 text-white shadow-xl border-2 border-white/30"
            aria-label={isFloatingMenuCollapsed ? "Expand menu" : "Collapse menu"}
          >
            {isFloatingMenuCollapsed ? <ChevronsLeft className="h-4 w-4" /> : <ChevronsRight className="h-4 w-4" />}
          </Button>
        </motion.div>

        <AnimatePresence>
          {!isFloatingMenuCollapsed && (
            <motion.div
              ref={aiSearchButtonRef}
              initial={{ scale: 0, opacity: 0, x: 20 }}
              animate={{ scale: 1, opacity: 1, x: 0 }}
              exit={{ scale: 0, opacity: 0, x: 20 }}
              transition={{ type: "spring", damping: 20 }}
              className="flex flex-col items-center gap-1"
            >
              <Button
                onClick={() => setIsAISearchOpen(!isAISearchOpen)}
                size="lg"
                className="h-12 w-12 rounded-full bg-gradient-to-br from-indigo-600 to-purple-600 hover:from-indigo-700 hover:to-purple-700 text-white shadow-2xl border-2 border-white/30"
                aria-label="Toggle AI search"
              >
                <Search className="h-5 w-5" />
              </Button>
              <span className="text-[9px] font-bold text-gray-700 dark:text-gray-300 bg-white/90 dark:bg-gray-800/90 px-2 py-0.5 rounded-full shadow-sm whitespace-nowrap">
                AI Search
              </span>
            </motion.div>
          )}
        </AnimatePresence>

        <AnimatePresence>
          {!isFloatingMenuCollapsed && (
            <motion.div
              initial={{ scale: 0, opacity: 0, x: 20 }}
              animate={{ scale: 1, opacity: 1, x: 0 }}
              exit={{ scale: 0, opacity: 0, x: 20 }}
              transition={{ type: "spring", damping: 20, delay: 0.05 }}
              className="flex flex-col items-center gap-1"
            >
              <Button
                onClick={() => setIsBrowseProductsOpen(!isBrowseProductsOpen)}
                size="lg"
                className="h-12 w-12 rounded-full bg-gradient-to-br from-blue-600 to-blue-500 hover:from-blue-700 hover:to-blue-600 text-white shadow-2xl border-2 border-white/30"
                aria-label="Browse products"
              >
                <List className="h-5 w-5" />
              </Button>
              <span className="text-[9px] font-bold text-gray-700 dark:text-gray-300 bg-white/90 dark:bg-gray-800/90 px-2 py-0.5 rounded-full shadow-sm whitespace-nowrap">
                Products
              </span>
            </motion.div>
          )}
        </AnimatePresence>

        <AnimatePresence>
          {contextDocumentsCount > 0 && !isFloatingMenuCollapsed && (
            <motion.div
              initial={{ scale: 0, opacity: 0, x: 20 }}
              animate={{ scale: 1, opacity: 1, x: 0 }}
              exit={{ scale: 0, opacity: 0, x: 20 }}
              transition={{ type: "spring", damping: 20, delay: 0.1 }}
              className="flex flex-col items-center gap-1"
            >
              <Button
                onClick={onOpenDocuments}
                size="lg"
                className="h-12 w-12 rounded-full bg-gradient-to-br from-purple-600 via-pink-600 to-blue-600 hover:from-purple-700 hover:via-pink-700 hover:to-blue-700 text-white shadow-2xl border-2 border-white/30 relative"
                aria-label="Open documents"
              >
                <FileText className="h-5 w-5" />
                <Badge className="absolute -top-1 -right-1 h-5 w-5 rounded-full bg-red-500 text-white text-[9px] font-bold flex items-center justify-center p-0">
                  {contextDocumentsCount}
                </Badge>
              </Button>
              <span className="text-[9px] font-bold text-gray-700 dark:text-gray-300 bg-white/90 dark:bg-gray-800/90 px-2 py-0.5 rounded-full shadow-sm whitespace-nowrap">
                Docs
              </span>
            </motion.div>
          )}
        </AnimatePresence>

        <AnimatePresence>
          {!isFloatingMenuCollapsed && (
            <motion.div
              initial={{ scale: 0, opacity: 0, x: 20 }}
              animate={{ scale: 1, opacity: 1, x: 0 }}
              exit={{ scale: 0, opacity: 0, x: 20 }}
              transition={{ type: "spring", damping: 20, delay: 0.15 }}
              className="flex flex-col items-center gap-1"
            >
              <Button
                onClick={onOpenCart}
                size="lg"
                className="h-12 w-12 rounded-full bg-gradient-to-br from-green-500 via-emerald-500 to-teal-500 hover:from-green-600 hover:via-emerald-600 hover:to-teal-600 text-white shadow-2xl border-2 border-white/30"
                aria-label="Open cart"
              >
                <ShoppingCart className="h-5 w-5" />
              </Button>
              <span className="text-[9px] font-bold text-gray-700 dark:text-gray-300 bg-white/90 dark:bg-gray-800/90 px-2 py-0.5 rounded-full shadow-sm">
                Cart
              </span>
            </motion.div>
          )}
        </AnimatePresence>

        <AnimatePresence>
          {!isQuickActionsOpen && !isFloatingMenuCollapsed && (
            <motion.div
              initial={{ scale: 0, opacity: 0, x: 20 }}
              animate={{ scale: 1, opacity: 1, x: 0 }}
              exit={{ scale: 0, opacity: 0, x: 20 }}
              transition={{ type: "spring", damping: 20, delay: 0.2 }}
              className="flex flex-col items-center gap-1"
            >
              <Button
                onClick={() => setIsQuickActionsOpen(true)}
                size="lg"
                className="h-12 w-12 rounded-full bg-gradient-to-br from-blue-600 to-blue-500 hover:from-blue-700 hover:to-blue-600 text-white shadow-2xl border-2 border-white/30"
                aria-label="Open quick actions"
              >
                <BrainCircuit className="h-5 w-5" />
              </Button>
              <span className="text-[9px] font-bold text-gray-700 dark:text-gray-300 bg-white/90 dark:bg-gray-800/90 px-2 py-0.5 rounded-full shadow-sm whitespace-nowrap">
                Actions
              </span>
            </motion.div>
          )}
        </AnimatePresence>
      </div>

      <AnimatePresence>
        {isBrowseProductsOpen && (
          <>
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              className="md:hidden fixed inset-0 bg-black/50 backdrop-blur-sm z-30"
              onClick={() => setIsBrowseProductsOpen(false)}
            />

            <motion.div
              initial={{ y: "100%" }}
              animate={{ y: 0 }}
              exit={{ y: "100%" }}
              transition={{ type: "spring", damping: 30, stiffness: 300 }}
              className="md:hidden fixed bottom-0 left-0 right-0 bg-white dark:bg-gray-900 rounded-t-3xl shadow-2xl z-40 max-h-[65vh] overflow-hidden"
            >
              <div className="flex justify-center pt-3 pb-2">
                <div className="w-12 h-1.5 bg-gray-300 dark:bg-gray-700 rounded-full" />
              </div>

              <div className="px-5 py-4 border-b border-blue-200/50 dark:border-blue-800/50 flex items-center justify-between bg-gradient-to-r from-blue-50 to-white dark:from-gray-800 dark:to-blue-900/30">
                <div className="flex items-center gap-3">
                  <div className="p-2.5 bg-gradient-to-br from-blue-600 to-blue-500 rounded-xl shadow-lg">
                    <Package className="h-5 w-5 text-white" />
                  </div>
                  <div>
                    <h3 className="text-lg font-bold bg-gradient-to-r from-blue-600 to-blue-500 bg-clip-text text-transparent">
                      Browse Products
                    </h3>
                    <p className="text-[10px] text-gray-600 dark:text-gray-400 font-medium">Quick category search</p>
                  </div>
                </div>
                <Button
                  variant="ghost"
                  size="icon"
                  onClick={() => setIsBrowseProductsOpen(false)}
                  className="h-9 w-9 hover:bg-blue-100 dark:hover:bg-blue-900/50 rounded-xl"
                  aria-label="Close browse products"
                >
                  <X className="h-5 w-5 text-gray-600 dark:text-gray-400" />
                </Button>
              </div>

              <div className="p-4 overflow-y-auto max-h-[calc(65vh-120px)]">
                <div className="space-y-2.5">
                  {browseProductCategories.map((category, idx) => {
                    const Icon = category.icon;
                    return (
                      <motion.div
                        key={category.id}
                        initial={{ opacity: 0, y: 10 }}
                        animate={{ opacity: 1, y: 0 }}
                        transition={{ delay: idx * 0.03 }}
                        onClick={() => {
                          onBrowseProductCategory(category);
                          setIsBrowseProductsOpen(false);
                        }}
                        className="cursor-pointer group"
                      >
                        <div
                          className={`relative overflow-hidden flex items-center gap-3.5 p-4 rounded-2xl border border-transparent bg-gradient-to-br ${category.color} hover:shadow-xl active:scale-[0.98] transition-all shadow-md`}
                        >
                          <div className="relative z-10 p-3 bg-white/25 backdrop-blur-sm rounded-xl flex-shrink-0 group-hover:scale-110 transition-transform">
                            <Icon className="h-6 w-6 text-white drop-shadow-md" />
                          </div>

                          <div className="relative z-10 flex-1 min-w-0">
                            <h3 className="font-bold text-base text-white mb-0.5 drop-shadow-sm">{category.label}</h3>
                            <p className="text-xs text-white/90 font-medium">{category.description}</p>
                          </div>

                          <div className="relative z-10 flex-shrink-0">
                            <div className="p-2 bg-white/20 backdrop-blur-sm rounded-lg group-hover:bg-white/30 transition-all">
                              <ArrowRight className="h-4 w-4 text-white group-hover:translate-x-0.5 transition-transform" />
                            </div>
                          </div>

                          <div className="absolute inset-0 bg-gradient-to-r from-transparent via-white/10 to-transparent -translate-x-full group-hover:translate-x-full transition-transform duration-700" />
                        </div>
                      </motion.div>
                    );
                  })}
                </div>
              </div>
            </motion.div>
          </>
        )}
      </AnimatePresence>
    </>
  );
}

