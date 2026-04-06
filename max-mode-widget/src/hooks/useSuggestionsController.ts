import { useEffect, useState } from "react";

import { getChatSuggestions } from "@/api/chat";

export function useSuggestionsController({
  attachedItems,
}: {
  attachedItems: Array<{ type: string; data: any }>;
}) {
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [isLoadingSuggestions, setIsLoadingSuggestions] = useState(false);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [shownSuggestions, setShownSuggestions] = useState<Set<string>>(new Set());

  useEffect(() => {
    if (attachedItems.length === 0) {
      setSuggestions([]);
      return;
    }

    const timeoutId = setTimeout(async () => {
      setIsLoadingSuggestions(true);
      try {
        const attachments = attachedItems.map((item) => {
          const parts: string[] = [];
          if (item.data.name) parts.push(item.data.name);
          if (item.data.title) parts.push(item.data.title);
          if (item.data.category) parts.push(`(${item.data.category})`);
          if (item.data.price) parts.push(`$${item.data.price}`);
          if (item.data.code) parts.push(`Code: ${item.data.code}`);

          let vectorSpace = "product";
          if (item.type === "order") vectorSpace = "order";
          else if (item.type === "review") vectorSpace = "review";
          else if (item.type === "coupon") vectorSpace = "coupon";

          return {
            id: item.data.id || item.data.sku || Date.now().toString(),
            vectorSpace,
            contentText: parts.join(" - "),
            source: "ui-card",
          };
        });

        const contentParts = attachedItems.map((item) => {
          const name = item.data.name || item.data.title || item.data.code || "Item";
          return `${item.type}: ${name}`;
        });

        const data = await getChatSuggestions({
          content: contentParts.join("; ") || "Give me suggestions based on attached items",
          userId: "demo-user",
          maxSuggestions: 4,
          attachments: attachments.length > 0 ? attachments : undefined,
        });

        if (data.suggestions && Array.isArray(data.suggestions)) {
          const newSuggestions = data.suggestions.filter((s: unknown): s is string => typeof s === "string" && s.length > 0);
          if (newSuggestions.length > 0) {
            setSuggestions(newSuggestions.slice(0, 4));
            // Keep suggestions closed by default - user must open them explicitly
          }
        }
      } catch (error) {
        console.error("Failed to load suggestions:", error);
        const genericSuggestions = [
          "Tell me more about this",
          "What are the key details?",
          "How does this compare to alternatives?",
          "What should I know before deciding?",
        ];
        setSuggestions(genericSuggestions);
        // Keep suggestions closed by default - user must open them explicitly
      } finally {
        setIsLoadingSuggestions(false);
      }
    }, 10000); // 10s delay to avoid interrupting early interactions

    return () => clearTimeout(timeoutId);
  }, [attachedItems]);

  return {
    suggestions,
    setSuggestions,
    isLoadingSuggestions,
    showSuggestions,
    setShowSuggestions,
    shownSuggestions,
    setShownSuggestions,
  } as const;
}
