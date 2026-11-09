import { useEffect, useState } from "react";

export function useSuggestionsController({
  starterSuggestions,
  hasConversationMessages,
}: {
  starterSuggestions: string[];
  hasConversationMessages: boolean;
}) {
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [shownSuggestions, setShownSuggestions] = useState<Set<string>>(new Set());

  useEffect(() => {
    // Authored starters are not model recommendations. Next steps come with the answer.
    setSuggestions(hasConversationMessages ? [] : starterSuggestions);
    setShowSuggestions(!hasConversationMessages && starterSuggestions.length > 0);
  }, [starterSuggestions, hasConversationMessages]);

  return {
    suggestions,
    setSuggestions,
    isLoadingSuggestions: false,
    showSuggestions,
    setShowSuggestions,
    shownSuggestions,
    setShownSuggestions,
  } as const;
}
