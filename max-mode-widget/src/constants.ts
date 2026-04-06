import type { LucideIcon } from "lucide-react";
import {
  Camera,
  Clock,
  Headphones,
  Laptop,
  List,
  MapPin,
  MessageSquare,
  Monitor,
  Receipt,
  RotateCcw,
  Search,
  ShoppingBag,
  ShoppingCart,
  Smartphone,
  Star,
  Tablet,
  Tag,
  TrendingUp,
  Truck,
  User,
} from "lucide-react";

// API URLs are NO LONGER hardcoded — they come from the widget config.
// See `getWidgetConfig().apiConfig.chatBaseUrl` and `.crudBaseUrl`.

export type MaxModePosition = "landing" | "catalog" | "search" | "cart";
export type MaxModeMode = "navigator" | "navigator_deep" | "cart_assistant" | "executor";

export interface QuickAction {
  icon: LucideIcon;
  label: string;
  query: string;
  color: string;
  bg: string;
  border: string;
  position: MaxModePosition;
  mode: MaxModeMode;
}

export interface SearchCategory {
  icon: LucideIcon;
  label: string;
  emoji: string;
  color: string;
  bg: string;
  border: string;
}

export interface AISearchCategory extends SearchCategory {
  query: string;
}

export interface BrowseProductCategory {
  id: string;
  icon: LucideIcon;
  label: string;
  query: string;
  color: string;
  description: string;
}

// Quick action tools - aligned with available backend actions
export const QUICK_ACTIONS: QuickAction[] = [
  {
    icon: Search,
    label: "Search Products",
    query: "Search for wireless headphones with good ratings and show me the prices, features, and availability",
    color: "text-blue-600",
    bg: "bg-blue-500/10",
    border: "border-blue-500/30",
    position: "search",
    mode: "navigator",
  },
  {
    icon: List,
    label: "Browse Products",
    query: "List all available products with their SKU, name, price, category, stock quantity, and ratings",
    color: "text-blue-600",
    bg: "bg-blue-500/10",
    border: "border-blue-500/30",
    position: "catalog",
    mode: "cart_assistant",
  },
  {
    icon: ShoppingCart,
    label: "My Cart",
    query: "View my cart",
    color: "text-green-600",
    bg: "bg-green-500/10",
    border: "border-green-500/30",
    position: "cart",
    mode: "executor",
  },
  {
    icon: ShoppingBag,
    label: "Checkout",
    query: "Checkout my cart",
    color: "text-orange-600",
    bg: "bg-orange-500/10",
    border: "border-orange-500/30",
    position: "cart",
    mode: "executor",
  },
  {
    icon: Receipt,
    label: "My Orders",
    query: "List my orders",
    color: "text-indigo-600",
    bg: "bg-indigo-500/10",
    border: "border-indigo-500/30",
    position: "cart",
    mode: "executor",
  },
  {
    icon: Clock,
    label: "Active Orders",
    query: "Show my active orders",
    color: "text-cyan-600",
    bg: "bg-cyan-500/10",
    border: "border-cyan-500/30",
    position: "cart",
    mode: "executor",
  },
  {
    icon: Truck,
    label: "Track Order",
    query: "Track my order shipment",
    color: "text-teal-600",
    bg: "bg-teal-500/10",
    border: "border-teal-500/30",
    position: "cart",
    mode: "executor",
  },
  {
    icon: RotateCcw,
    label: "Returns",
    query: "Create a return request",
    color: "text-red-600",
    bg: "bg-red-500/10",
    border: "border-red-500/30",
    position: "cart",
    mode: "executor",
  },
  {
    icon: Tag,
    label: "Coupons",
    query: "Show available coupons",
    color: "text-pink-600",
    bg: "bg-pink-500/10",
    border: "border-pink-500/30",
    position: "catalog",
    mode: "navigator",
  },
  {
    icon: Star,
    label: "Reviews",
    query: "Add a product review",
    color: "text-yellow-600",
    bg: "bg-yellow-500/10",
    border: "border-yellow-500/30",
    position: "catalog",
    mode: "navigator",
  },
  {
    icon: User,
    label: "My Account",
    query: "Show my account details",
    color: "text-slate-600",
    bg: "bg-slate-500/10",
    border: "border-slate-500/30",
    position: "cart",
    mode: "executor",
  },
  {
    icon: MapPin,
    label: "Addresses",
    query: "List my saved addresses",
    color: "text-emerald-600",
    bg: "bg-emerald-500/10",
    border: "border-emerald-500/30",
    position: "cart",
    mode: "executor",
  },
  {
    icon: MessageSquare,
    label: "Support",
    query: "Create a support ticket",
    color: "text-violet-600",
    bg: "bg-violet-500/10",
    border: "border-violet-500/30",
    position: "cart",
    mode: "executor",
  },
  {
    icon: TrendingUp,
    label: "Trending",
    query: "What's trending?",
    color: "text-rose-600",
    bg: "bg-rose-500/10",
    border: "border-rose-500/30",
    position: "catalog",
    mode: "navigator",
  },
];

// Product search categories matching our actual data
export const SEARCH_CATEGORIES: SearchCategory[] = [
  {
    icon: Laptop,
    label: "Laptops",
    emoji: "💻",
    color: "text-blue-600",
    bg: "bg-blue-500/10",
    border: "border-blue-500/30",
  },
  {
    icon: Smartphone,
    label: "Smartphones",
    emoji: "📱",
    color: "text-indigo-600",
    bg: "bg-indigo-500/10",
    border: "border-indigo-500/30",
  },
  {
    icon: Headphones,
    label: "Headphones",
    emoji: "🎧",
    color: "text-rose-600",
    bg: "bg-rose-500/10",
    border: "border-rose-500/30",
  },
  {
    icon: Camera,
    label: "Cameras",
    emoji: "📷",
    color: "text-green-600",
    bg: "bg-green-500/10",
    border: "border-green-500/30",
  },
  {
    icon: Monitor,
    label: "Monitors",
    emoji: "🖥️",
    color: "text-orange-600",
    bg: "bg-orange-500/10",
    border: "border-orange-500/30",
  },
];

// AI Search menu categories
export const AI_SEARCH_CATEGORIES: AISearchCategory[] = SEARCH_CATEGORIES.map((c) => ({
  ...c,
  query: `search relevance vector spaces for ${c.label} and show top results with prices and stock`,
}));

// Browse Products categories
export const BROWSE_PRODUCT_CATEGORIES: BrowseProductCategory[] = [
  {
    id: "apple-laptops",
    icon: Laptop,
    label: "Apple Laptops",
    query: "Action: List product: Apple laptops",
    color: "from-blue-500 to-cyan-500",
    description: "MacBook Pro & Air",
  },
  {
    id: "sony-headphones",
    icon: Headphones,
    label: "Sony Headphones",
    query: "Action: List product: Sony headphones",
    color: "from-purple-500 to-pink-500",
    description: "Premium noise-cancelling",
  },
  {
    id: "samsung-tablets",
    icon: Tablet,
    label: "Samsung Tablets",
    query: "Action: List product: Samsung tablets",
    color: "from-green-500 to-emerald-500",
    description: "Galaxy Tab series",
  },
  {
    id: "sony-cameras",
    icon: Camera,
    label: "Sony Cameras",
    query: "Action: List product: Sony cameras",
    color: "from-orange-500 to-red-500",
    description: "Professional photography",
  },
  {
    id: "gaming-laptops",
    icon: Laptop,
    label: "Gaming Laptops",
    query: "Action: List product: gaming laptops high performance",
    color: "from-red-500 to-pink-500",
    description: "High-performance gaming",
  },
  {
    id: "wireless-headphones",
    icon: Headphones,
    label: "Wireless Headphones",
    query: "Action: List product: wireless headphones Bluetooth",
    color: "from-indigo-500 to-purple-500",
    description: "Bluetooth connectivity",
  },
];
