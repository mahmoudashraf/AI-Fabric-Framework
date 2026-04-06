import { useCallback } from "react";
import type { RefObject } from "react";

type ToastFn = (opts: any) => void;

export function useSearchControls({
  aiSearchCategories,
  setAttachedItems,
  setIsAISearchOpen,
  setSearchCategory,
  setCurrentPosition,
  setCurrentMode,
  chatInputRef,
  toast,
}: {
  aiSearchCategories: ReadonlyArray<{ label: string }>;
  setAttachedItems: (updater: any) => void;
  setIsAISearchOpen: (open: boolean) => void;
  setSearchCategory: (category: string | null) => void;
  setCurrentPosition: (pos: "landing" | "catalog" | "search" | "cart") => void;
  setCurrentMode: (mode: "navigator" | "navigator_deep" | "cart_assistant" | "executor") => void;
  chatInputRef: RefObject<HTMLTextAreaElement>;
  toast: ToastFn;
}) {
  const handleAISearchCategory = useCallback(
    (category: (typeof aiSearchCategories)[number]) => {
      const searchAttachment = {
        type: "ai-search",
        data: {
          category: category.label,
          title: `AI Search: ${category.label}`,
        },
      };

      setAttachedItems((prev: Array<{ type: string; data: any }>) => [
        ...prev.filter((item) => item.type !== "ai-search"),
        searchAttachment,
      ]);
      setIsAISearchOpen(false);

      setSearchCategory(null);
      setCurrentPosition("search");
      setCurrentMode("navigator");

      toast({
        title: "🔍 AI Search Attached",
        description: `${category.label} search criteria added to chat`,
      });

      setTimeout(() => {
        chatInputRef.current?.focus();
      }, 100);
    },
    [aiSearchCategories, chatInputRef, setAttachedItems, setCurrentMode, setCurrentPosition, setIsAISearchOpen, setSearchCategory, toast],
  );

  const handleSelectSearchCategory = useCallback(
    (category: string, { closeMenus }: { closeMenus: () => void }) => {
      setSearchCategory(category);
      closeMenus();
      setAttachedItems((prev: Array<{ type: string; data: any }>) => prev.filter((item) => item.type !== "ai-search"));
      setTimeout(() => chatInputRef.current?.focus(), 100);
    },
    [chatInputRef, setAttachedItems, setSearchCategory],
  );

  const clearSearchCategory = useCallback(() => {
    setSearchCategory(null);
  }, [setSearchCategory]);

  return { handleAISearchCategory, handleSelectSearchCategory, clearSearchCategory } as const;
}
