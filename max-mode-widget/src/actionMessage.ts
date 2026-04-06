import type { LucideIcon } from "lucide-react";
import { Package, Receipt, Search, ShoppingCart, Sparkles, TrendingUp, Wand2 } from "lucide-react";

export const parseActionMessage = (
  content: string
): { isAction: boolean; actionType: string; query: string; fullMessage: string } => {
  const actionMatch = content.match(/^Action:\s*([^:]+):\s*(.+)$/i);
  if (actionMatch) {
    return {
      isAction: true,
      actionType: actionMatch[1].trim(),
      query: actionMatch[2].trim(),
      fullMessage: content,
    };
  }
  return { isAction: false, actionType: "", query: content, fullMessage: content };
};

export const getActionIcon = (actionType: string): LucideIcon => {
  const type = actionType.toLowerCase();
  if (type.includes("list") || type.includes("search") || type.includes("find")) {
    return Search;
  } else if (type.includes("add to cart") || type.includes("cart")) {
    return ShoppingCart;
  } else if (type.includes("checkout") || type.includes("order")) {
    return Receipt;
  } else if (type.includes("product") || type.includes("details")) {
    return Package;
  } else if (type.includes("compare")) {
    return TrendingUp;
  } else if (type.includes("recommend")) {
    return Sparkles;
  }
  return Wand2; // Default action icon
};

