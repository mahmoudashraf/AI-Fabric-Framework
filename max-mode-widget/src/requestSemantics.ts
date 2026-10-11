import type { MaxModeMode } from "@/constants";

export interface ChatRequestSemanticsInput {
  query: string;
  currentPosition: "landing" | "catalog" | "search" | "cart";
  currentMode: MaxModeMode;
  actionPosition?: "landing" | "catalog" | "search" | "cart";
  actionMode?: MaxModeMode;
  searchCategory?: string | null;
  attachmentSearchCategory?: string | null;
}

export interface ChatRequestSemantics {
  query: string;
  position: "landing" | "catalog" | "search" | "cart";
  mode: MaxModeMode;
  searchCategory?: string;
}

/**
 * Resolves only explicit host/user controls. Presentation types and attached
 * content are deliberately absent so they cannot rewrite runtime semantics.
 */
export function resolveChatRequestSemantics(
  input: ChatRequestSemanticsInput,
): ChatRequestSemantics {
  const searchCategory = firstString(
    input.searchCategory,
    input.attachmentSearchCategory,
  );
  let position = input.actionPosition ?? input.currentPosition;
  let mode = input.actionMode ?? input.currentMode;

  if (searchCategory) {
    position = "search";
    mode = "navigator";
  }
  if (input.currentMode === "navigator_deep" || input.currentMode === "thinker_deep") {
    mode = input.currentMode;
  }

  return {
    query: input.query,
    position,
    mode,
    ...(searchCategory ? { searchCategory } : {}),
  };
}

/** Returns only a source-declared routing scope; item types never infer one. */
export function explicitAttachmentVectorSpace(data: any): string | undefined {
  return firstString(
    data?.vectorSpace,
    data?.entityType,
    data?.metadata?.vectorSpace,
    data?.metadata?.entityType,
  );
}

function firstString(...values: unknown[]): string | undefined {
  for (const value of values) {
    if (typeof value !== "string") continue;
    const trimmed = value.trim();
    if (trimmed) return trimmed;
  }
  return undefined;
}
