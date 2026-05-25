import type { FormEvent, MouseEvent, ReactNode } from "react";
import { useEffect, useMemo, useState } from "react";

import {
  ArrowRight,
  BadgeCheck,
  Box,
  Check,
  ChevronRight,
  Circle,
  ExternalLink,
  FileText,
  Filter,
  Gift,
  Home,
  ImageIcon,
  Info,
  MessageSquarePlus,
  PackageSearch,
  Search,
  ShoppingBag,
  ShoppingCart,
  SlidersHorizontal,
  Sparkles,
  Tag,
  Truck,
  X,
} from "lucide-react";
import { motion } from "framer-motion";

import { MessageList } from "./Chat/MessageList";
import { MaxModeCollectionAnimation } from "./MaxModeView/MaxModeCollectionAnimation";
import { MaxModeComposerBar } from "./MaxModeView/MaxModeComposerBar";
import { MaxModeOverlays } from "./MaxModeView/MaxModeOverlays";

import { getWidgetConfig } from "@/config";
import type { MaxModeMode, MaxModePosition } from "@/constants";
import type { Document } from "@/types";
import type { MaxModeController } from "@/hooks/useMaxModeController";

type ShopifyWorkspaceState = "discovery" | "browsing" | "product" | "comparison" | "policy" | "cart";

type ProductCardModel = {
  id: string;
  title: string;
  subtitle?: string;
  price?: string;
  compareAtPrice?: string;
  availability?: string;
  imageUrl?: string;
  url?: string;
  variantId?: number;
  source?: Document;
};

type ShopifyCartSnapshot = {
  item_count: number;
  total_price: number;
  items: Array<{
    id: number;
    title: string;
    product_title?: string;
    variant_title?: string;
    quantity: number;
    final_line_price: number;
    image?: string;
    url?: string;
  }>;
};

type ShopifyStorefrontProduct = {
  id?: number | string;
  title?: string;
  handle?: string;
  vendor?: string;
  product_type?: string;
  body_html?: string;
  featured_image?: string;
  images?: Array<{ src?: string } | string>;
  variants?: Array<{
    id?: number | string;
    title?: string;
    price?: number | string;
    compare_at_price?: number | string | null;
    available?: boolean;
  }>;
};

const SHOPIFY_SCROLLBAR_STYLE = `
.shopify-rich-scrollbar {
  scrollbar-width: auto;
  scrollbar-color: #818cf8 #eef2ff;
  scrollbar-gutter: stable;
}
.shopify-rich-scrollbar::-webkit-scrollbar {
  width: 14px;
}
.shopify-rich-scrollbar::-webkit-scrollbar-track {
  background: #eef2ff;
  border-radius: 9999px;
}
.shopify-rich-scrollbar::-webkit-scrollbar-thumb {
  background: linear-gradient(180deg, #818cf8, #4f46e5);
  border: 3px solid #eef2ff;
  border-radius: 9999px;
}
.shopify-rich-scrollbar::-webkit-scrollbar-thumb:hover {
  background: linear-gradient(180deg, #6366f1, #3730a3);
}
`;

const SHOPIFY_QUICK_ASKS = [
  { label: "Best sellers", query: "Show me your best sellers", position: "search" as MaxModePosition, mode: "thinker_deep" as MaxModeMode },
  { label: "Compare products", query: "Compare your top product categories", position: "search" as MaxModePosition, mode: "thinker_deep" as MaxModeMode },
  { label: "Shipping policy", query: "What is your shipping policy?", position: "landing" as MaxModePosition, mode: "thinker_deep" as MaxModeMode },
  { label: "Returns", query: "What is your return policy?", position: "landing" as MaxModePosition, mode: "thinker_deep" as MaxModeMode },
];

const COLLECTIONS = [
  { label: "Products", query: "Show me your product categories", icon: PackageSearch },
  { label: "Deals", query: "Show me products with current discounts", icon: Tag },
  { label: "Gift ideas", query: "Help me find gift ideas from this store", icon: Gift },
  { label: "Policies", query: "Show me store policies that matter before buying", icon: Info },
];

const POLICY_TOPICS = [
  { label: "Shipping", query: "What is your shipping policy?", icon: Truck },
  { label: "Returns", query: "What is your return policy?", icon: ArrowRight },
  { label: "Payment", query: "What payment options are available?", icon: BadgeCheck },
  { label: "FAQ", query: "What should I know before buying from this store?", icon: Info },
];

export function ShopifyShoppingWorkspace({
  onClose,
  controller,
}: {
  onClose: () => void;
  controller: MaxModeController;
}) {
  const config = getWidgetConfig();
  const requestContext = config.host?.requestContext ?? {};
  const storeName = deriveStoreName(requestContext);
  const pageGroup = String(requestContext.shopifyPageModeGroup || "").toLowerCase();
  const [workspaceState, setWorkspaceState] = useState<ShopifyWorkspaceState>(() =>
    initialWorkspaceState(controller, pageGroup),
  );
  const [showDiscoveryHome, setShowDiscoveryHome] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [cart, setCart] = useState<ShopifyCartSnapshot | null>(null);
  const [cartError, setCartError] = useState<string | null>(null);
  const [storefrontProducts, setStorefrontProducts] = useState<ProductCardModel[]>([]);
  const [storefrontProductsLoading, setStorefrontProductsLoading] = useState(false);
  const products = useMemo(() => deriveProducts(controller.contextDocuments, requestContext), [
    controller.contextDocuments,
    requestContext,
  ]);
  const policyDocs = useMemo(() => derivePolicyDocuments(controller.contextDocuments), [controller.contextDocuments]);
  const sourceDocs = useMemo(() => deriveSourceDocuments(controller.contextDocuments), [controller.contextDocuments]);
  const sourceStorefrontProducts = useShopifySourceProducts(sourceDocs);
  const liveProductPool = useMemo(
    () => dedupeProducts([...sourceStorefrontProducts, ...storefrontProducts]),
    [sourceStorefrontProducts, storefrontProducts],
  );
  const hydratedProducts = useMemo(
    () => hydrateProductsFromStorefront(products, liveProductPool),
    [products, liveProductPool],
  );
  const displayProducts = hydratedProducts.length ? hydratedProducts : storefrontProducts;
  const sourceProductPool = useMemo(
    () => dedupeProducts([...hydratedProducts, ...sourceStorefrontProducts, ...storefrontProducts]),
    [hydratedProducts, sourceStorefrontProducts, storefrontProducts],
  );
  const spotlight = useMemo(
    () => deriveSpotlight(controller.selectedProduct, displayProducts, requestContext),
    [controller.selectedProduct, displayProducts, requestContext],
  );

  useEffect(() => {
    if (showDiscoveryHome) {
      return;
    }
    const next = deriveWorkspaceState(controller, pageGroup, products, policyDocs);
    setWorkspaceState((current) => (current === "comparison" || current === "policy" ? current : next));
  }, [
    controller.currentPosition,
    controller.isCartView,
    controller.selectedProduct,
    pageGroup,
    policyDocs.length,
    products.length,
    showDiscoveryHome,
  ]);

  useEffect(() => {
    let cancelled = false;
    setStorefrontProductsLoading(true);
    fetchShopifyStorefrontProducts(8)
      .then((nextProducts) => {
        if (!cancelled) {
          setStorefrontProducts(nextProducts);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setStorefrontProducts([]);
        }
      })
      .finally(() => {
        if (!cancelled) {
          setStorefrontProductsLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    let cancelled = false;
    fetchShopifyCart()
      .then((snapshot) => {
        if (!cancelled) {
          setCart(snapshot);
          setCartError(null);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setCartError("Cart unavailable");
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const dispatchPrompt = (query: string, position: MaxModePosition, mode: MaxModeMode) => {
    setSearchQuery("");
    setShowDiscoveryHome(false);
    controller.handleQuickAction(query, position, mode);
  };

  const openStartPage = () => {
    setSearchQuery("");
    controller.closeCart();
    controller.closeProductDetails();
    controller.setCurrentPosition("landing");
    controller.setCurrentMode(controller.allowedConversationModes.includes("thinker_deep") ? "thinker_deep" : controller.allowedConversationModes[0] ?? "navigator");
    setWorkspaceState("discovery");
    setShowDiscoveryHome(true);
  };

  const updateWorkspaceState = (state: ShopifyWorkspaceState) => {
    setShowDiscoveryHome(false);
    setWorkspaceState(state);
  };

  const handleSearchSubmit = (event: FormEvent) => {
    event.preventDefault();
    const query = searchQuery.trim();
    if (!query) {
      return;
    }
    updateWorkspaceState("browsing");
    dispatchPrompt(query, "search", "thinker_deep");
  };

  const handleAddVariant = async (product: ProductCardModel) => {
    if (!product.variantId) {
      dispatchPrompt(`Add ${product.title} to my cart.`, "cart", "executor");
      return;
    }
    await addVariantToShopifyCart(product.variantId, 1);
    const snapshot = await fetchShopifyCart();
    setCart(snapshot);
    updateWorkspaceState("cart");
  };

  const isProductAttached = (product: ProductCardModel) => {
    return controller.isItemAttached(product.source?.id || product.id) || controller.isItemAttached(product.id);
  };

  const attachProductToChat = (product: ProductCardModel) => {
    if (product.source) {
      controller.handleAttachDocument(product.source);
      return;
    }
    const attachment = productToChatAttachment(product);
    controller.handleAttachActionResultItem(attachment.data);
  };

  const isCartLineAttached = (item: ShopifyCartSnapshot["items"][number]) => {
    return controller.isItemAttached(cartLineAttachmentId(item)) || controller.isItemAttached(String(item.id));
  };

  const attachCartLineToChat = (item: ShopifyCartSnapshot["items"][number]) => {
    controller.handleAttachActionResultItem(cartLineToChatItem(item));
  };

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="fixed inset-0 z-[100] overflow-hidden bg-[#f7f8fc] font-sans text-slate-950"
    >
      <style>{SHOPIFY_SCROLLBAR_STYLE}</style>
      <div className="hidden h-full min-h-0 grid-rows-[72px_minmax(0,1fr)] md:grid">
        <ShopifyWorkspaceTopBar
          storeName={storeName}
          cartCount={cart?.item_count ?? 0}
          searchQuery={searchQuery}
          onSearchQueryChange={setSearchQuery}
          onSearchSubmit={handleSearchSubmit}
          onStartPage={openStartPage}
          onCart={() => updateWorkspaceState("cart")}
          onClose={onClose}
        />
        <div className="grid min-h-0 grid-cols-[280px_minmax(0,1fr)_340px] border-t border-slate-200">
          <ShopifyLeftRail
            state={workspaceState}
            products={displayProducts}
            spotlight={spotlight}
            policyDocs={policyDocs}
            cart={cart}
            onState={updateWorkspaceState}
            onPrompt={dispatchPrompt}
            isProductAttached={isProductAttached}
            onAttachProduct={attachProductToChat}
            isCartLineAttached={isCartLineAttached}
            onAttachCartLine={attachCartLineToChat}
            isDocumentAttached={controller.isItemAttached}
            onAttachDocument={controller.handleAttachDocument}
            onOpenProduct={(product) => {
              if (product.source) controller.openProductDetails(product.source);
              updateWorkspaceState("product");
            }}
          />
          <ShopifyConversationColumn
            controller={controller}
            state={workspaceState}
            storeName={storeName}
            products={displayProducts}
            spotlight={spotlight}
            policyDocs={policyDocs}
            onPrompt={dispatchPrompt}
            onState={updateWorkspaceState}
            onAddVariant={handleAddVariant}
            isProductAttached={isProductAttached}
            onAttachProduct={attachProductToChat}
            isDocumentAttached={controller.isItemAttached}
            onAttachDocument={controller.handleAttachDocument}
            showStartPage={showDiscoveryHome}
          />
          <ShopifyRightPanel
            state={workspaceState}
            products={displayProducts}
            spotlight={spotlight}
            policyDocs={policyDocs}
            sourceDocs={sourceDocs}
            sourceProducts={sourceProductPool}
            cart={cart}
            cartError={cartError}
            productsLoading={storefrontProductsLoading && products.length === 0}
            onState={updateWorkspaceState}
            onPrompt={dispatchPrompt}
            isProductAttached={isProductAttached}
            onAttachProduct={attachProductToChat}
            isCartLineAttached={isCartLineAttached}
            onAttachCartLine={attachCartLineToChat}
            isDocumentAttached={controller.isItemAttached}
            onAttachDocument={controller.handleAttachDocument}
            onOpenProduct={(product) => {
              if (product.source) controller.openProductDetails(product.source);
              updateWorkspaceState("product");
            }}
            onAddVariant={handleAddVariant}
          />
        </div>
      </div>

      <div className="flex h-full flex-col bg-white md:hidden">
        <ShopifyMobileWorkspace
          storeName={storeName}
          state={workspaceState}
          products={displayProducts}
          spotlight={spotlight}
          policyDocs={policyDocs}
          cart={cart}
          controller={controller}
          onClose={onClose}
          onPrompt={dispatchPrompt}
          onState={updateWorkspaceState}
          onAddVariant={handleAddVariant}
          isProductAttached={isProductAttached}
          onAttachProduct={attachProductToChat}
          isDocumentAttached={controller.isItemAttached}
          onAttachDocument={controller.handleAttachDocument}
          onStartPage={openStartPage}
          showStartPage={showDiscoveryHome}
        />
      </div>

      <MaxModeCollectionAnimation collectingItem={controller.collectingItem} />
      <MaxModeOverlays controller={controller} />
    </motion.div>
  );
}

function ShopifyWorkspaceTopBar({
  storeName,
  cartCount,
  searchQuery,
  onSearchQueryChange,
  onSearchSubmit,
  onStartPage,
  onCart,
  onClose,
}: {
  storeName: string;
  cartCount: number;
  searchQuery: string;
  onSearchQueryChange: (value: string) => void;
  onSearchSubmit: (event: FormEvent) => void;
  onStartPage: () => void;
  onCart: () => void;
  onClose: () => void;
}) {
  return (
    <div className="flex h-[72px] items-center gap-5 bg-white px-6">
      <button className="flex min-w-[220px] items-center gap-3 text-left" onClick={() => window.location.assign(shopifyRoute(""))}>
        <span className="flex h-10 w-10 items-center justify-center rounded-2xl border border-slate-200 bg-white shadow-sm">
          <ShoppingBag className="h-5 w-5" />
        </span>
        <span className="min-w-0">
          <span className="block truncate text-lg font-extrabold">{storeName}</span>
          <span className="block text-xs font-medium text-slate-500">Shopping workspace</span>
        </span>
      </button>
      <form onSubmit={onSearchSubmit} className="mx-auto flex h-11 w-full max-w-[520px] items-center gap-2 rounded-full bg-slate-100 px-4">
        <Search className="h-4 w-4 text-slate-400" />
        <input
          value={searchQuery}
          onChange={(event) => onSearchQueryChange(event.target.value)}
          className="h-full min-w-0 flex-1 bg-transparent text-sm font-medium text-slate-700 outline-none placeholder:text-slate-400"
          placeholder="Search products, sizes, policies..."
        />
      </form>
      <button
        onClick={onStartPage}
        aria-label="Back to Max Mode start page"
        className="inline-flex h-10 items-center gap-2 rounded-full px-3 text-sm font-bold text-slate-600 hover:bg-slate-100 hover:text-slate-950"
      >
        <Home className="h-4 w-4" />
        Start
      </button>
      <button className="inline-flex h-10 items-center gap-2 rounded-full bg-indigo-50 px-4 text-sm font-bold text-indigo-600">
        <Sparkles className="h-4 w-4" />
        Max Mode
      </button>
      <button
        onClick={onCart}
        className="inline-flex h-10 items-center gap-2 rounded-full px-3 text-sm font-semibold text-slate-600 hover:bg-slate-100"
      >
        <ShoppingCart className="h-4 w-4" />
        Cart ({cartCount})
      </button>
      <button
        onClick={onClose}
        aria-label="Close Max Mode"
        className="flex h-10 w-10 items-center justify-center rounded-full text-slate-500 hover:bg-slate-100 hover:text-slate-900"
      >
        <X className="h-5 w-5" />
      </button>
    </div>
  );
}

function ShopifyLeftRail({
  state,
  products,
  spotlight,
  policyDocs,
  cart,
  onState,
  onPrompt,
  isProductAttached,
  onAttachProduct,
  isCartLineAttached,
  onAttachCartLine,
  isDocumentAttached,
  onAttachDocument,
  onOpenProduct,
}: {
  state: ShopifyWorkspaceState;
  products: ProductCardModel[];
  spotlight: ProductCardModel | null;
  policyDocs: Document[];
  cart: ShopifyCartSnapshot | null;
  onState: (state: ShopifyWorkspaceState) => void;
  onPrompt: (query: string, position: MaxModePosition, mode: MaxModeMode) => void;
  isProductAttached: (product: ProductCardModel) => boolean;
  onAttachProduct: (product: ProductCardModel) => void;
  isCartLineAttached: (item: ShopifyCartSnapshot["items"][number]) => boolean;
  onAttachCartLine: (item: ShopifyCartSnapshot["items"][number]) => void;
  isDocumentAttached: (itemId: string) => boolean;
  onAttachDocument: (doc: Document) => void;
  onOpenProduct: (product: ProductCardModel) => void;
}) {
  return (
    <aside className="min-h-0 overflow-y-auto border-r border-slate-200 bg-slate-50 px-5 py-6">
      {state === "browsing" ? (
        <RailSection title="Filters">
          <FilterGroup title="Category" values={["All", "Products", "Deals"]} selected="All" />
          <FilterGroup title="Sort by" values={["Relevant", "Price low", "Price high"]} selected="Relevant" />
          <div className="rounded-2xl border border-slate-200 bg-white p-4">
            <div className="mb-3 flex items-center justify-between text-sm font-bold">
              <span>Stock</span>
              <span className="text-emerald-600">In stock first</span>
            </div>
            <div className="h-2 rounded-full bg-slate-100">
              <div className="h-2 w-2/3 rounded-full bg-indigo-500" />
            </div>
          </div>
        </RailSection>
      ) : state === "product" && spotlight ? (
        <RailSection title="About this product">
          <ProductRailCard
            product={spotlight}
            onOpen={() => onOpenProduct(spotlight)}
            isAttached={isProductAttached(spotlight)}
            onAttach={() => onAttachProduct(spotlight)}
          />
          <button
            onClick={() => onPrompt(`Compare ${spotlight.title} with similar options.`, "search", "thinker_deep")}
            className="w-full rounded-2xl border border-indigo-200 bg-white px-4 py-3 text-sm font-bold text-indigo-600"
          >
            Compare similar products
          </button>
        </RailSection>
      ) : state === "comparison" ? (
        <RailSection title="Comparing">
          {(products.length ? products.slice(0, 3) : spotlight ? [spotlight] : []).map((product) => (
            <SelectableProductCard
              key={product.id}
              product={product}
              selected
              onClick={() => onOpenProduct(product)}
              isAttached={isProductAttached(product)}
              onAttach={() => onAttachProduct(product)}
            />
          ))}
          <button
            onClick={() => onPrompt("Add another product to this comparison.", "search", "thinker_deep")}
            className="w-full rounded-2xl border border-indigo-300 bg-white px-4 py-3 text-sm font-bold text-indigo-600"
          >
            Add product
          </button>
        </RailSection>
      ) : state === "policy" ? (
        <RailSection title="Store info">
          {POLICY_TOPICS.map((topic, index) => (
            <button
              key={topic.label}
              onClick={() => onPrompt(topic.query, "landing", "thinker_deep")}
              className={`flex w-full items-center gap-3 rounded-2xl border px-4 py-4 text-left ${
                index === 0 ? "border-indigo-400 bg-indigo-50" : "border-slate-200 bg-white"
              }`}
            >
              <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-indigo-100 text-indigo-600">
                <topic.icon className="h-5 w-5" />
              </span>
              <span className="font-bold">{topic.label}</span>
            </button>
          ))}
          {policyDocs.slice(0, 3).map((doc) => (
            <PolicyMiniCard
              key={doc.id}
              doc={doc}
              isAttached={isDocumentAttached(doc.id)}
              onAttach={() => onAttachDocument(doc)}
            />
          ))}
        </RailSection>
      ) : state === "cart" ? (
        <RailSection title="Your picks">
          {cart?.items?.length ? (
            cart.items.slice(0, 4).map((item) => (
              <CartLineCard
                key={`${item.id}-${item.quantity}`}
                item={item}
                isAttached={isCartLineAttached(item)}
                onAttach={() => onAttachCartLine(item)}
              />
            ))
          ) : (
            <EmptyRailCard title="Cart is ready" body="Ask for a product or choose an item to start building your cart." />
          )}
          <RailSection title="You might also like" nested>
            {products.slice(0, 2).map((product) => (
              <SelectableProductCard
                key={product.id}
                product={product}
                onClick={() => onOpenProduct(product)}
                isAttached={isProductAttached(product)}
                onAttach={() => onAttachProduct(product)}
              />
            ))}
          </RailSection>
        </RailSection>
      ) : (
        <RailSection title="Discover">
          {COLLECTIONS.map((collection, index) => (
            <button
              key={collection.label}
              onClick={() => {
                onState(collection.label === "Policies" ? "policy" : "browsing");
                onPrompt(collection.query, collection.label === "Policies" ? "landing" : "search", "thinker_deep");
              }}
              className={`flex w-full items-center gap-3 rounded-2xl border px-4 py-4 text-left ${
                index === 0 ? "border-indigo-400 bg-indigo-50" : "border-slate-200 bg-white"
              }`}
            >
              <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-indigo-100 text-indigo-600">
                <collection.icon className="h-5 w-5" />
              </span>
              <span>
                <span className="block font-extrabold">{collection.label}</span>
                <span className="text-sm text-slate-500">Browse with AI</span>
              </span>
            </button>
          ))}
          <RailSection title="Quick ask" nested>
            {SHOPIFY_QUICK_ASKS.map((ask) => (
              <button
                key={ask.label}
                onClick={() => {
                  onState(ask.label.includes("Shipping") || ask.label.includes("Returns") ? "policy" : "browsing");
                  onPrompt(ask.query, ask.position, ask.mode);
                }}
                className="flex w-full items-center justify-between rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm font-bold text-slate-700"
              >
                {ask.label}
                <ChevronRight className="h-4 w-4 text-slate-400" />
              </button>
            ))}
          </RailSection>
        </RailSection>
      )}
    </aside>
  );
}

function ShopifyConversationColumn({
  controller,
  state,
  storeName,
  products,
  spotlight,
  policyDocs,
  onPrompt,
  onState,
  onAddVariant,
  isProductAttached,
  onAttachProduct,
  isDocumentAttached,
  onAttachDocument,
  showStartPage,
}: {
  controller: MaxModeController;
  state: ShopifyWorkspaceState;
  storeName: string;
  products: ProductCardModel[];
  spotlight: ProductCardModel | null;
  policyDocs: Document[];
  onPrompt: (query: string, position: MaxModePosition, mode: MaxModeMode) => void;
  onState: (state: ShopifyWorkspaceState) => void;
  onAddVariant: (product: ProductCardModel) => Promise<void>;
  isProductAttached: (product: ProductCardModel) => boolean;
  onAttachProduct: (product: ProductCardModel) => void;
  isDocumentAttached: (itemId: string) => boolean;
  onAttachDocument: (doc: Document) => void;
  showStartPage: boolean;
}) {
  const hasMessages = controller.chatMessages.some((message) => message.type === "user");
  const showDiscovery = showStartPage || !hasMessages;
  return (
    <main className="relative min-h-0 bg-white">
      {showDiscovery && (
        <div className="absolute inset-x-0 top-0 z-10 px-10 py-10">
          <DiscoveryHero
            storeName={storeName}
            products={products}
            spotlight={spotlight}
            onPrompt={onPrompt}
            onState={onState}
            onAddVariant={onAddVariant}
            isProductAttached={isProductAttached}
            onAttachProduct={onAttachProduct}
          />
        </div>
      )}
      {!showDiscovery && hasMessages && (
        <div className="absolute inset-x-0 top-0 z-10 px-10 pt-7">
          <StateSummary
            state={state}
            products={products}
            spotlight={spotlight}
            policyDocs={policyDocs}
            onPrompt={onPrompt}
            isProductAttached={isProductAttached}
            onAttachProduct={onAttachProduct}
            isDocumentAttached={isDocumentAttached}
            onAttachDocument={onAttachDocument}
          />
        </div>
      )}
      {!showDiscovery && hasMessages && (
        <MessageList
          containerClassName="absolute inset-x-0 bottom-0 top-0 overflow-y-auto px-8 pb-[150px] pt-[170px]"
          messages={controller.chatMessages}
          latestMessageRef={controller.latestMessageRef}
          messagesEndRef={controller.messagesEndRef}
          isLoading={controller.isLoading}
          getAiStyles={controller.getResultStyles}
          isPanelVisible={false}
          attachedItems={controller.attachedItems}
          confirmationStatus={controller.confirmationStatus as Record<string, "confirmed" | "rejected">}
          expandedActions={controller.expandedActions}
          debugEnabled={controller.debugEnabled}
          onOpenDebug={controller.openDebugInspector}
          onResendAction={(fullMessage) => void controller.resendChatQuery(fullMessage)}
          onReattachItem={controller.reattachItemWithToast}
          onOpenSourcesMobile={controller.openSourcesMobile}
          onOpenSourcesDesktop={controller.openSourcesDesktop}
          onConfirm={(messageId, confirmed, msg) => controller.handleConfirmation(messageId, confirmed, msg)}
          onExpandActionResults={controller.expandActionResults}
          isItemAttached={controller.isItemAttached}
          onAttachActionResultItem={controller.handleAttachActionResultItem}
          onNextStepClick={(query) => void controller.resendChatQuery(query)}
          onCustomerAccountConnect={controller.connectCustomerAccount}
          onClarificationSubmit={controller.handleClarificationSubmit}
        />
      )}
      <MaxModeComposerBar controller={controller} />
    </main>
  );
}

function ShopifyRightPanel({
  state,
  products,
  spotlight,
  policyDocs,
  sourceDocs,
  sourceProducts,
  cart,
  cartError,
  productsLoading,
  onState,
  onPrompt,
  isProductAttached,
  onAttachProduct,
  isCartLineAttached,
  onAttachCartLine,
  isDocumentAttached,
  onAttachDocument,
  onOpenProduct,
  onAddVariant,
}: {
  state: ShopifyWorkspaceState;
  products: ProductCardModel[];
  spotlight: ProductCardModel | null;
  policyDocs: Document[];
  sourceDocs: Document[];
  sourceProducts: ProductCardModel[];
  cart: ShopifyCartSnapshot | null;
  cartError: string | null;
  productsLoading: boolean;
  onState: (state: ShopifyWorkspaceState) => void;
  onPrompt: (query: string, position: MaxModePosition, mode: MaxModeMode) => void;
  isProductAttached: (product: ProductCardModel) => boolean;
  onAttachProduct: (product: ProductCardModel) => void;
  isCartLineAttached: (item: ShopifyCartSnapshot["items"][number]) => boolean;
  onAttachCartLine: (item: ShopifyCartSnapshot["items"][number]) => void;
  isDocumentAttached: (itemId: string) => boolean;
  onAttachDocument: (doc: Document) => void;
  onOpenProduct: (product: ProductCardModel) => void;
  onAddVariant: (product: ProductCardModel) => Promise<void>;
}) {
  return (
    <aside className="shopify-rich-scrollbar min-h-0 overflow-y-auto border-l border-slate-200 bg-slate-50 px-4 py-6">
      {state === "policy" ? (
        <RightPanelSection title="Policy details">
          {policyDocs.length ? (
            policyDocs.slice(0, 3).map((doc) => (
              <PolicyCard
                key={doc.id}
                doc={doc}
                isAttached={isDocumentAttached(doc.id)}
                onAttach={() => onAttachDocument(doc)}
              />
            ))
          ) : (
            <EmptyRailCard title="No policy sources yet" body="Ask a policy question and indexed store documents will appear here when returned." />
          )}
        </RightPanelSection>
      ) : state === "cart" ? (
        <RightPanelSection title="Cart summary">
          <CartSummary
            cart={cart}
            cartError={cartError}
            isCartLineAttached={isCartLineAttached}
            onAttachCartLine={onAttachCartLine}
          />
          {products.length > 0 && (
            <RightPanelSection title="Also consider" nested>
              <div className="grid grid-cols-2 gap-3">
                {products.slice(0, 4).map((product) => (
                  <MiniProductCard
                    key={product.id}
                    product={product}
                    onClick={() => onAddVariant(product)}
                    actionLabel="Add"
                    isAttached={isProductAttached(product)}
                    onAttach={() => onAttachProduct(product)}
                  />
                ))}
              </div>
            </RightPanelSection>
          )}
        </RightPanelSection>
      ) : state === "comparison" ? (
        <RightPanelSection title="Comparison">
          <ComparisonCard
            products={products}
            onOpenProduct={onOpenProduct}
            isProductAttached={isProductAttached}
            onAttachProduct={onAttachProduct}
          />
        </RightPanelSection>
      ) : spotlight ? (
        <RightPanelSection title={state === "product" ? "Product gallery" : "Product spotlight"}>
          <SpotlightCard
            product={spotlight}
            onOpenProduct={() => onOpenProduct(spotlight)}
            onAddVariant={() => onAddVariant(spotlight)}
            isAttached={isProductAttached(spotlight)}
            onAttach={() => onAttachProduct(spotlight)}
          />
          <button
            onClick={() => {
              onState("comparison");
              onPrompt(`Compare ${spotlight.title} with similar options.`, "search", "thinker_deep");
            }}
            className="mt-3 w-full rounded-2xl border border-indigo-300 bg-white px-4 py-3 text-sm font-bold text-indigo-600"
          >
            Compare
          </button>
          {products.length > 1 && (
            <RightPanelSection title="Trending" nested>
              <div className="grid grid-cols-2 gap-3">
                {products.slice(0, 4).map((product) => (
                  <MiniProductCard
                    key={product.id}
                    product={product}
                    onClick={() => onOpenProduct(product)}
                    isAttached={isProductAttached(product)}
                    onAttach={() => onAttachProduct(product)}
                  />
                ))}
              </div>
            </RightPanelSection>
          )}
        </RightPanelSection>
      ) : (
        <RightPanelSection title="Spotlight">
          {productsLoading ? (
            <EmptyRailCard title="Loading store picks" body="Fetching live storefront products for this shopping panel." />
          ) : (
            <QuickBrowseCard
              onPrompt={(ask) => {
                onState(ask.label.includes("Shipping") || ask.label.includes("Returns") ? "policy" : "browsing");
                onPrompt(ask.query, ask.position, ask.mode);
              }}
            />
          )}
        </RightPanelSection>
      )}
      {sourceDocs.length > 0 && (
        <RightPanelSection title="Grounding sources">
          <div className="space-y-3">
            {sourceDocs.slice(0, 6).map((doc) => (
              <SourceEvidenceCard
                key={`${doc.messageId || "source"}-${doc.id}`}
                doc={doc}
                product={resolveSourceProduct(doc, sourceProducts)}
                isAttached={isDocumentAttached(doc.id)}
                onAttach={() => onAttachDocument(doc)}
              />
            ))}
          </div>
        </RightPanelSection>
      )}
    </aside>
  );
}

function ShopifyMobileWorkspace({
  storeName,
  state,
  products,
  spotlight,
  policyDocs,
  cart,
  controller,
  onClose,
  onPrompt,
  onState,
  onAddVariant,
  isProductAttached,
  onAttachProduct,
  isDocumentAttached,
  onAttachDocument,
  onStartPage,
  showStartPage,
}: {
  storeName: string;
  state: ShopifyWorkspaceState;
  products: ProductCardModel[];
  spotlight: ProductCardModel | null;
  policyDocs: Document[];
  cart: ShopifyCartSnapshot | null;
  controller: MaxModeController;
  onClose: () => void;
  onPrompt: (query: string, position: MaxModePosition, mode: MaxModeMode) => void;
  onState: (state: ShopifyWorkspaceState) => void;
  onAddVariant: (product: ProductCardModel) => Promise<void>;
  isProductAttached: (product: ProductCardModel) => boolean;
  onAttachProduct: (product: ProductCardModel) => void;
  isDocumentAttached: (itemId: string) => boolean;
  onAttachDocument: (doc: Document) => void;
  onStartPage: () => void;
  showStartPage: boolean;
}) {
  const hasMessages = controller.chatMessages.some((message) => message.type === "user");
  const showDiscovery = showStartPage || !hasMessages;
  return (
    <div className="relative flex h-full min-h-0 flex-col bg-white">
      <div className="flex h-14 shrink-0 items-center justify-between border-b border-slate-200 px-4">
        <div className="flex items-center gap-1">
          <button
            onClick={onClose}
            aria-label="Close Max Mode"
            className="flex h-10 w-10 items-center justify-center rounded-full hover:bg-slate-100"
          >
            <X className="h-5 w-5" />
          </button>
          <button
            onClick={onStartPage}
            aria-label="Back to Max Mode start page"
            className="flex h-10 w-10 items-center justify-center rounded-full text-slate-600 hover:bg-slate-100 hover:text-slate-950"
          >
            <Home className="h-5 w-5" />
          </button>
        </div>
        <div className="inline-flex items-center gap-2 text-sm font-extrabold text-indigo-600">
          <Sparkles className="h-4 w-4" />
          Max Mode
        </div>
        <button onClick={() => onState("cart")} className="flex h-10 items-center gap-1 rounded-full px-2 text-sm text-slate-600">
          <ShoppingCart className="h-4 w-4" />
          {cart?.item_count ?? 0}
        </button>
      </div>
      <div className="relative min-h-0 flex-1 overflow-y-auto bg-white px-4 pb-[140px] pt-4">
        <div className="mb-4 rounded-3xl border border-slate-200 bg-white p-4 shadow-sm">
          <div className="flex items-center justify-between">
            <div>
              <div className="text-lg font-extrabold">{storeName}</div>
              <div className="text-sm text-slate-500">Shop, compare, and ask naturally.</div>
            </div>
            <span className="rounded-full bg-emerald-50 px-3 py-1 text-xs font-bold text-emerald-600">Online</span>
          </div>
        </div>

        {showDiscovery && (
          <>
            <div className="mb-4 grid grid-cols-2 gap-3">
              {(products.length ? products.slice(0, 4) : COLLECTIONS).map((item: any) => {
                const product = "title" in item ? (item as ProductCardModel) : null;
                return (
                  <div key={product?.id ?? item.label} className="relative rounded-2xl border border-slate-200 bg-white shadow-sm">
                    {product && (
                      <AttachToChatButton
                        isAttached={isProductAttached(product)}
                        onAttach={(event) => {
                          onAttachProduct(product);
                        }}
                        compact
                        className="absolute right-2 top-2 z-10"
                      />
                    )}
                    <button
                      onClick={() => {
                        if (product) {
                          onState("product");
                          if (product.source) controller.openProductDetails(product.source);
                        } else {
                          onState("browsing");
                          onPrompt(item.query, "search", "thinker_deep");
                        }
                      }}
                      className="block w-full p-3 text-left"
                    >
                      <ProductVisual product={product ?? undefined} className="mb-3 h-20" />
                      <div className="line-clamp-2 text-sm font-extrabold">{product?.title ?? item.label}</div>
                      {product?.price && <div className="text-sm font-bold text-slate-700">{product.price}</div>}
                    </button>
                  </div>
                );
              })}
            </div>
            <div className="mb-4 flex flex-wrap gap-2">
              {SHOPIFY_QUICK_ASKS.slice(0, 4).map((ask) => (
                <button
                  key={ask.label}
                  onClick={() => onPrompt(ask.query, ask.position, ask.mode)}
                  className="rounded-full border border-slate-200 px-3 py-2 text-sm font-bold text-slate-700"
                >
                  {ask.label}
                </button>
              ))}
            </div>
            {spotlight && (
              <SpotlightCard
                product={spotlight}
                onOpenProduct={() => {
                  onState("product");
                  if (spotlight.source) controller.openProductDetails(spotlight.source);
                }}
                onAddVariant={() => onAddVariant(spotlight)}
                isAttached={isProductAttached(spotlight)}
                onAttach={() => onAttachProduct(spotlight)}
                compact
              />
            )}
          </>
        )}

        {!showDiscovery && hasMessages && (
          <MessageList
            containerClassName="relative px-0 pb-4"
            messages={controller.chatMessages}
            latestMessageRef={controller.latestMessageRef}
            messagesEndRef={controller.messagesEndRef}
            isLoading={controller.isLoading}
            getAiStyles={controller.getResultStyles}
            isPanelVisible={false}
            attachedItems={controller.attachedItems}
            confirmationStatus={controller.confirmationStatus as Record<string, "confirmed" | "rejected">}
            expandedActions={controller.expandedActions}
            debugEnabled={controller.debugEnabled}
            onOpenDebug={controller.openDebugInspector}
            onResendAction={(fullMessage) => void controller.resendChatQuery(fullMessage)}
            onReattachItem={controller.reattachItemWithToast}
            onOpenSourcesMobile={controller.openSourcesMobile}
            onOpenSourcesDesktop={controller.openSourcesDesktop}
            onConfirm={(messageId, confirmed, msg) => controller.handleConfirmation(messageId, confirmed, msg)}
            onExpandActionResults={controller.expandActionResults}
            isItemAttached={controller.isItemAttached}
            onAttachActionResultItem={controller.handleAttachActionResultItem}
            onNextStepClick={(query) => void controller.resendChatQuery(query)}
            onCustomerAccountConnect={controller.connectCustomerAccount}
            onClarificationSubmit={controller.handleClarificationSubmit}
          />
        )}

        {state === "policy" && policyDocs.length > 0 && policyDocs.slice(0, 1).map((doc) => (
          <PolicyCard
            key={doc.id}
            doc={doc}
            isAttached={isDocumentAttached(doc.id)}
            onAttach={() => onAttachDocument(doc)}
          />
        ))}
      </div>
      <MaxModeComposerBar controller={controller} />
      <MaxModeCollectionAnimation collectingItem={controller.collectingItem} />
      <MaxModeOverlays controller={controller} />
    </div>
  );
}

function DiscoveryHero({
  storeName,
  products,
  spotlight,
  onPrompt,
  onState,
  onAddVariant,
  isProductAttached,
  onAttachProduct,
}: {
  storeName: string;
  products: ProductCardModel[];
  spotlight: ProductCardModel | null;
  onPrompt: (query: string, position: MaxModePosition, mode: MaxModeMode) => void;
  onState: (state: ShopifyWorkspaceState) => void;
  onAddVariant: (product: ProductCardModel) => Promise<void>;
  isProductAttached: (product: ProductCardModel) => boolean;
  onAttachProduct: (product: ProductCardModel) => void;
}) {
  return (
    <div className="mx-auto max-w-2xl rounded-[2rem] bg-slate-100 p-7 shadow-sm">
      <div className="mb-5 inline-flex h-10 w-10 items-center justify-center rounded-2xl bg-white text-indigo-600 shadow-sm">
        <Sparkles className="h-5 w-5" />
      </div>
      <h2 className="text-2xl font-extrabold">Welcome to {storeName}</h2>
      <p className="mt-3 max-w-xl text-base text-slate-600">
        Ask about products, policies, availability, fit, or checkout help. I can browse the catalog with you and keep useful store context visible.
      </p>
      <div className="mt-5 flex flex-wrap gap-2">
        {SHOPIFY_QUICK_ASKS.map((ask) => (
          <button
            key={ask.label}
            onClick={() => {
              onState(ask.label.includes("Shipping") || ask.label.includes("Returns") ? "policy" : "browsing");
              onPrompt(ask.query, ask.position, ask.mode);
            }}
            className="rounded-full bg-white px-4 py-2 text-sm font-bold text-slate-700 shadow-sm"
          >
            {ask.label}
          </button>
        ))}
      </div>
      {spotlight && (
        <div className="mt-6">
          <SpotlightCard
            product={spotlight}
            onOpenProduct={() => onState("product")}
            onAddVariant={() => onAddVariant(spotlight)}
            isAttached={isProductAttached(spotlight)}
            onAttach={() => onAttachProduct(spotlight)}
            compact
          />
        </div>
      )}
      {products.length > 1 && (
        <div className="mt-4 grid grid-cols-3 gap-3">
          {products.slice(0, 3).map((product) => (
            <MiniProductCard
              key={product.id}
              product={product}
              onClick={() => onState("browsing")}
              isAttached={isProductAttached(product)}
              onAttach={() => onAttachProduct(product)}
            />
          ))}
        </div>
      )}
    </div>
  );
}

function StateSummary({
  state,
  products,
  spotlight,
  policyDocs,
  onPrompt,
  isProductAttached,
  onAttachProduct,
  isDocumentAttached,
  onAttachDocument,
}: {
  state: ShopifyWorkspaceState;
  products: ProductCardModel[];
  spotlight: ProductCardModel | null;
  policyDocs: Document[];
  onPrompt: (query: string, position: MaxModePosition, mode: MaxModeMode) => void;
  isProductAttached: (product: ProductCardModel) => boolean;
  onAttachProduct: (product: ProductCardModel) => void;
  isDocumentAttached: (itemId: string) => boolean;
  onAttachDocument: (doc: Document) => void;
}) {
  if (state === "comparison" && products.length > 1) {
    return <ComparisonCard products={products} compact isProductAttached={isProductAttached} onAttachProduct={onAttachProduct} />;
  }
  if (state === "product" && spotlight) {
    return (
      <div className="mx-auto max-w-2xl rounded-3xl bg-slate-100 p-5">
        <div className="relative flex gap-4">
          <AttachToChatButton
            isAttached={isProductAttached(spotlight)}
            onAttach={(event) => {
              event.stopPropagation();
              onAttachProduct(spotlight);
            }}
            className="absolute right-0 top-0 z-10"
          />
          <ProductVisual product={spotlight} className="h-24 w-28 shrink-0" />
          <div className="min-w-0">
            <div className="text-lg font-extrabold">{spotlight.title}</div>
            <div className="mt-1 line-clamp-2 text-sm text-slate-600">{spotlight.subtitle}</div>
            <div className="mt-3 flex gap-2">
              <button
                onClick={() => onPrompt(`Compare ${spotlight.title} with similar products.`, "search", "thinker_deep")}
                className="rounded-full bg-white px-3 py-2 text-xs font-bold text-slate-700"
              >
                Compare
              </button>
              <button
                onClick={() => spotlight.url && window.open(spotlight.url, "_blank", "noopener,noreferrer")}
                className="rounded-full bg-indigo-600 px-3 py-2 text-xs font-bold text-white"
              >
                View in Store
              </button>
            </div>
          </div>
        </div>
      </div>
    );
  }
  if (state === "policy" && policyDocs.length > 0) {
    return (
      <PolicyCard
        doc={policyDocs[0]}
        compact
        isAttached={isDocumentAttached(policyDocs[0].id)}
        onAttach={() => onAttachDocument(policyDocs[0])}
      />
    );
  }
  if (products.length > 0) {
    return (
      <div className="mx-auto max-w-2xl rounded-3xl bg-slate-100 p-5">
        <div className="mb-3 flex items-center gap-2 text-sm font-bold text-slate-500">
          <Sparkles className="h-4 w-4 text-indigo-500" />
          Shoppable results
        </div>
        <div className="grid grid-cols-3 gap-3">
          {products.slice(0, 3).map((product) => (
            <MiniProductCard
              key={product.id}
              product={product}
              onClick={() => onPrompt(`Tell me more about ${product.title}.`, "catalog", "thinker_deep")}
              isAttached={isProductAttached(product)}
              onAttach={() => onAttachProduct(product)}
            />
          ))}
        </div>
      </div>
    );
  }
  return null;
}

function RailSection({
  title,
  children,
  nested = false,
}: {
  title: string;
  children: ReactNode;
  nested?: boolean;
}) {
  return (
    <section className={nested ? "space-y-3" : "space-y-3 pb-6"}>
      <h3 className="text-xs font-extrabold uppercase tracking-[0.18em] text-slate-500">{title}</h3>
      {children}
    </section>
  );
}

function RightPanelSection({
  title,
  children,
  nested = false,
}: {
  title: string;
  children: ReactNode;
  nested?: boolean;
}) {
  return (
    <section className={nested ? "mt-6 space-y-3" : "space-y-3"}>
      <h3 className="text-xs font-extrabold uppercase tracking-[0.18em] text-slate-500">{title}</h3>
      {children}
    </section>
  );
}

function FilterGroup({ title, values, selected }: { title: string; values: string[]; selected: string }) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-4">
      <div className="mb-3 text-sm font-extrabold">{title}</div>
      <div className="space-y-2">
        {values.map((value) => (
          <div key={value} className="flex items-center gap-2 text-sm text-slate-700">
            {value === selected ? <Check className="h-4 w-4 text-indigo-600" /> : <Circle className="h-4 w-4 text-slate-300" />}
            {value}
          </div>
        ))}
      </div>
    </div>
  );
}

function AttachToChatButton({
  isAttached = false,
  onAttach,
  compact = false,
  className = "",
}: {
  isAttached?: boolean;
  onAttach: (event: MouseEvent<HTMLButtonElement>) => void;
  compact?: boolean;
  className?: string;
}) {
  return (
    <button
      type="button"
      onClick={(event) => {
        event.preventDefault();
        event.stopPropagation();
        onAttach(event);
      }}
      aria-label={isAttached ? "Already attached to AI chat" : "Attach to AI chat"}
      title={isAttached ? "Already attached to AI chat" : "Attach to AI chat"}
      className={`inline-flex items-center justify-center rounded-full border border-white/80 shadow-lg transition hover:scale-105 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 ${
        compact ? "h-8 w-8" : "h-10 w-10"
      } ${isAttached ? "bg-emerald-500 text-white" : "bg-indigo-600 text-white hover:bg-indigo-700"} ${className}`}
    >
      {isAttached ? <Check className={compact ? "h-4 w-4" : "h-5 w-5"} /> : <MessageSquarePlus className={compact ? "h-4 w-4" : "h-5 w-5"} />}
    </button>
  );
}

function ProductRailCard({
  product,
  onOpen,
  isAttached = false,
  onAttach,
}: {
  product: ProductCardModel;
  onOpen: () => void;
  isAttached?: boolean;
  onAttach?: () => void;
}) {
  return (
    <div className="relative rounded-2xl border border-slate-200 bg-white shadow-sm">
      {onAttach && <AttachToChatButton isAttached={isAttached} onAttach={() => onAttach()} compact className="absolute right-3 top-3 z-10" />}
      <button onClick={onOpen} className="block w-full p-4 text-left">
        <ProductVisual product={product} className="mb-3 h-28" />
        <div className="line-clamp-2 text-lg font-extrabold">{product.title}</div>
        {product.compareAtPrice && <div className="text-sm text-slate-400 line-through">{product.compareAtPrice}</div>}
        {product.price && <div className="text-2xl font-extrabold">{product.price}</div>}
        <AvailabilityLabel value={product.availability} />
      </button>
    </div>
  );
}

function SpotlightCard({
  product,
  onOpenProduct,
  onAddVariant,
  compact = false,
  isAttached = false,
  onAttach,
}: {
  product: ProductCardModel;
  onOpenProduct: () => void;
  onAddVariant: () => void | Promise<void>;
  compact?: boolean;
  isAttached?: boolean;
  onAttach?: () => void;
}) {
  return (
    <div className="relative rounded-3xl border border-slate-200 bg-white p-4 shadow-sm">
      {onAttach && <AttachToChatButton isAttached={isAttached} onAttach={() => onAttach()} className="absolute right-4 top-4 z-10" compact={compact} />}
      <ProductVisual product={product} className={compact ? "mb-3 h-24" : "mb-4 h-52"} />
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="line-clamp-2 text-xl font-extrabold">{product.title}</div>
          {product.price && <div className="mt-2 text-3xl font-extrabold">{product.price}</div>}
        </div>
        {product.compareAtPrice && <span className="rounded-full bg-red-50 px-2 py-1 text-xs font-extrabold text-red-500">Sale</span>}
      </div>
      <AvailabilityLabel value={product.availability} />
      <div className="mt-4 grid grid-cols-2 gap-2">
        <button onClick={onOpenProduct} className="rounded-2xl border border-indigo-300 px-3 py-3 text-sm font-bold text-indigo-600">
          Details
        </button>
        <button onClick={onAddVariant} className="rounded-2xl bg-indigo-600 px-3 py-3 text-sm font-bold text-white">
          Add
        </button>
      </div>
      {product.subtitle && <p className="mt-3 line-clamp-3 text-sm text-slate-600">{product.subtitle}</p>}
    </div>
  );
}

function SelectableProductCard({
  product,
  selected = false,
  onClick,
  isAttached = false,
  onAttach,
}: {
  product: ProductCardModel;
  selected?: boolean;
  onClick: () => void;
  isAttached?: boolean;
  onAttach?: () => void;
}) {
  return (
    <div
      className={`relative rounded-2xl border shadow-sm ${
        selected ? "border-indigo-400 bg-indigo-50" : "border-slate-200 bg-white"
      }`}
    >
      {onAttach && <AttachToChatButton isAttached={isAttached} onAttach={() => onAttach()} compact className="absolute right-3 top-3 z-10" />}
      <button onClick={onClick} className="block w-full p-4 text-left">
        <ProductVisual product={product} className="mb-3 h-20" />
        <div className="line-clamp-2 font-extrabold">{product.title}</div>
        {product.price && <div className="font-bold">{product.price}</div>}
        {selected && <div className="mt-1 text-sm font-bold text-emerald-600">selected</div>}
      </button>
    </div>
  );
}

function MiniProductCard({
  product,
  onClick,
  actionLabel,
  isAttached = false,
  onAttach,
}: {
  product: ProductCardModel;
  onClick: () => void;
  actionLabel?: string;
  isAttached?: boolean;
  onAttach?: () => void;
}) {
  return (
    <div className="relative min-w-0 rounded-2xl border border-slate-200 bg-white shadow-sm">
      {onAttach && <AttachToChatButton isAttached={isAttached} onAttach={() => onAttach()} compact className="absolute right-2 top-2 z-10" />}
      <button onClick={onClick} className="block w-full p-3 text-left">
        <ProductVisual product={product} className="mb-2 h-20" />
        <div className="line-clamp-2 text-sm font-extrabold">{product.title}</div>
        {product.price && <div className="text-sm font-bold">{product.price}</div>}
        {actionLabel && <div className="mt-2 text-sm font-extrabold text-indigo-600">+ {actionLabel}</div>}
      </button>
    </div>
  );
}

function ProductVisual({ product, className }: { product?: ProductCardModel; className: string }) {
  return (
    <div className={`flex items-center justify-center overflow-hidden rounded-2xl bg-indigo-100 ${className}`}>
      {product?.imageUrl ? (
        <img src={product.imageUrl} alt="" className="h-full w-full object-cover" loading="lazy" />
      ) : (
        <Box className="h-10 w-10 text-slate-700" />
      )}
    </div>
  );
}

function AvailabilityLabel({ value }: { value?: string }) {
  if (!value) {
    return null;
  }
  const available = /available|in stock/i.test(value);
  return (
    <div className={`mt-2 inline-flex items-center gap-2 text-sm font-bold ${available ? "text-emerald-600" : "text-amber-600"}`}>
      <span className={`h-2 w-2 rounded-full ${available ? "bg-emerald-500" : "bg-amber-500"}`} />
      {value}
    </div>
  );
}

function PolicyMiniCard({
  doc,
  isAttached = false,
  onAttach,
}: {
  doc: Document;
  isAttached?: boolean;
  onAttach?: () => void;
}) {
  return (
    <div className="relative rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
      {onAttach && <AttachToChatButton isAttached={isAttached} onAttach={() => onAttach()} compact className="absolute right-3 top-3 z-10" />}
      <div className="flex items-start gap-3 pr-8">
        <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-indigo-50 text-indigo-600">
          <FileText className="h-4 w-4" />
        </span>
        <div className="min-w-0">
          <div className="line-clamp-2 text-sm font-extrabold">{doc.title}</div>
          <p className="mt-1 line-clamp-3 text-xs leading-5 text-slate-500">{truncate(doc.content, 180)}</p>
        </div>
      </div>
    </div>
  );
}

function PolicyCard({
  doc,
  compact = false,
  isAttached = false,
  onAttach,
}: {
  doc: Document;
  compact?: boolean;
  isAttached?: boolean;
  onAttach?: () => void;
}) {
  return (
    <div className={`relative rounded-3xl border border-slate-200 bg-white shadow-sm ${compact ? "mx-auto max-w-2xl p-5" : "p-5"}`}>
      {onAttach && <AttachToChatButton isAttached={isAttached} onAttach={() => onAttach()} compact className="absolute right-4 top-4 z-10" />}
      <div className="mb-3 flex items-center gap-2 pr-10 text-sm font-bold text-slate-500">
        <Info className="h-4 w-4 text-indigo-500" />
        Policy info
      </div>
      <h4 className="text-xl font-extrabold">{doc.title}</h4>
      <p className="mt-3 whitespace-pre-wrap text-sm leading-6 text-slate-600">{truncate(doc.content, compact ? 320 : 640)}</p>
    </div>
  );
}

function SourceEvidenceCard({
  doc,
  product,
  isAttached = false,
  onAttach,
}: {
  doc: Document;
  product?: ProductCardModel | null;
  isAttached?: boolean;
  onAttach?: () => void;
}) {
  const score = typeof doc.score === "number" ? doc.score : typeof doc.similarity === "number" ? doc.similarity : null;
  const sourceType = friendlySourceType(String(doc.type || doc.metadata?.vectorSpace || "source"));
  const parsed = parseDocumentContent(doc.content);
  const title = product?.title || cleanProductTitle(stringFrom(parsed?.name || doc.metadata?.title || doc.metadata?.name || doc.title) || "Source");
  const imageUrl = product?.imageUrl || documentImageUrl(doc);
  const url = product?.url || sourceUrlFromDocument(doc);
  const availability = product?.availability || stringFrom((doc as any).availability || doc.metadata?.availability);
  const price = product?.price || stringFrom((doc as any).priceRange || doc.metadata?.priceRange || doc.metadata?.price);
  const preview = sourcePreview(doc, parsed);
  return (
    <div className="overflow-hidden rounded-3xl border border-indigo-100 bg-white shadow-sm ring-1 ring-white">
      <div className="relative h-28 bg-gradient-to-br from-indigo-100 via-violet-100 to-slate-100">
        {imageUrl ? (
          <img src={imageUrl} alt="" loading="lazy" className="h-full w-full object-cover" />
        ) : (
          <div className="flex h-full items-center justify-center">
            <ImageIcon className="h-9 w-9 text-indigo-400" />
          </div>
        )}
        <div className="absolute left-3 top-3 inline-flex items-center gap-1 rounded-full bg-white/90 px-2 py-1 text-[11px] font-extrabold text-indigo-700 shadow-sm">
          <FileText className="h-3.5 w-3.5" />
          {sourceType}
        </div>
        {score != null && (
          <span className="absolute right-3 top-3 rounded-full bg-indigo-600 px-2 py-1 text-[11px] font-extrabold text-white shadow-sm">
            {Math.round(score * 100)}%
          </span>
        )}
        {onAttach && <AttachToChatButton isAttached={isAttached} onAttach={() => onAttach()} compact className="absolute bottom-3 right-3 z-10" />}
      </div>
      <div className="p-4">
        <h4 className="line-clamp-2 text-sm font-extrabold leading-5 text-slate-950">{title}</h4>
        {(price || availability) && (
          <div className="mt-2 flex flex-wrap items-center gap-2">
            {price && <span className="rounded-full bg-slate-100 px-2 py-1 text-[11px] font-extrabold text-slate-700">{price}</span>}
            {availability && (
              <span className="rounded-full bg-emerald-50 px-2 py-1 text-[11px] font-extrabold text-emerald-700">{availability}</span>
            )}
          </div>
        )}
        <p className="mt-3 line-clamp-4 whitespace-pre-wrap text-xs leading-5 text-slate-600">{truncate(preview, 360)}</p>
        {url && (
          <button
            onClick={() => window.open(url, "_blank", "noopener,noreferrer")}
            className="mt-4 inline-flex w-full items-center justify-center gap-2 rounded-2xl border border-indigo-200 bg-indigo-50 px-3 py-2 text-xs font-extrabold text-indigo-700 hover:bg-indigo-100"
          >
            Open source
            <ExternalLink className="h-3.5 w-3.5" />
          </button>
        )}
      </div>
    </div>
  );
}

function ComparisonCard({
  products,
  onOpenProduct,
  compact = false,
  isProductAttached,
  onAttachProduct,
}: {
  products: ProductCardModel[];
  onOpenProduct?: (product: ProductCardModel) => void;
  compact?: boolean;
  isProductAttached?: (product: ProductCardModel) => boolean;
  onAttachProduct?: (product: ProductCardModel) => void;
}) {
  const compared = products.slice(0, 2);
  if (compared.length < 2) {
    return <EmptyRailCard title="Comparison ready" body="Ask the assistant to compare two products from the catalog." />;
  }
  return (
    <div className={`rounded-3xl border border-slate-200 bg-white shadow-sm ${compact ? "mx-auto max-w-2xl p-5" : "p-4"}`}>
      <div className="mb-4 grid grid-cols-2 gap-3">
        {compared.map((product) => (
          <div key={product.id} className="relative rounded-2xl border border-slate-200">
            {onAttachProduct && (
              <AttachToChatButton
                isAttached={isProductAttached?.(product) ?? false}
                onAttach={() => onAttachProduct(product)}
                compact
                className="absolute right-2 top-2 z-10"
              />
            )}
            <button onClick={() => onOpenProduct?.(product)} className="block w-full p-3 text-left">
              <ProductVisual product={product} className="mb-2 h-20" />
              <div className="line-clamp-2 text-sm font-extrabold">{product.title}</div>
              {product.price && <div className="font-bold">{product.price}</div>}
            </button>
          </div>
        ))}
      </div>
      <div className="overflow-hidden rounded-2xl border border-slate-200 text-sm">
        <CompareRow label="Price" left={compared[0].price || "Not indexed"} right={compared[1].price || "Not indexed"} />
        <CompareRow label="Stock" left={compared[0].availability || "Unknown"} right={compared[1].availability || "Unknown"} />
      </div>
      <div className="mt-4 rounded-2xl bg-indigo-50 p-4 text-sm font-bold text-indigo-700">
        AI can explain the tradeoffs from indexed product evidence and current store context.
      </div>
    </div>
  );
}

function CompareRow({ label, left, right }: { label: string; left: string; right: string }) {
  return (
    <div className="grid grid-cols-[90px_1fr_1fr] border-b border-slate-200 last:border-b-0">
      <div className="bg-slate-50 px-3 py-3 font-bold text-slate-500">{label}</div>
      <div className="px-3 py-3 font-bold">{left}</div>
      <div className="px-3 py-3 font-bold">{right}</div>
    </div>
  );
}

function CartSummary({
  cart,
  cartError,
  isCartLineAttached,
  onAttachCartLine,
}: {
  cart: ShopifyCartSnapshot | null;
  cartError: string | null;
  isCartLineAttached?: (item: ShopifyCartSnapshot["items"][number]) => boolean;
  onAttachCartLine?: (item: ShopifyCartSnapshot["items"][number]) => void;
}) {
  if (cartError) {
    return <EmptyRailCard title="Cart unavailable" body="The storefront cart could not be loaded in this browser session." />;
  }
  if (!cart || cart.item_count === 0) {
    return <EmptyRailCard title="Cart is empty" body="Add an item from a product card or ask the assistant to help complete your cart." />;
  }
  return (
    <div className="rounded-3xl border border-slate-200 bg-white p-4 shadow-sm">
      <div className="space-y-3">
        {cart.items.slice(0, 4).map((item) => (
          <CartLineCard
            key={`${item.id}-${item.quantity}`}
            item={item}
            isAttached={isCartLineAttached?.(item) ?? false}
            onAttach={onAttachCartLine ? () => onAttachCartLine(item) : undefined}
          />
        ))}
      </div>
      <div className="mt-4 border-t border-slate-200 pt-4">
        <div className="flex items-center justify-between text-sm text-slate-600">
          <span>Subtotal</span>
          <span className="font-bold text-slate-950">{formatCents(cart.total_price)}</span>
        </div>
        <button
          onClick={() => window.location.assign(shopifyRoute("checkout"))}
          className="mt-4 inline-flex w-full items-center justify-center gap-2 rounded-2xl bg-indigo-600 px-4 py-3 font-extrabold text-white"
        >
          Checkout
          <ArrowRight className="h-4 w-4" />
        </button>
      </div>
    </div>
  );
}

function CartLineCard({
  item,
  isAttached = false,
  onAttach,
}: {
  item: ShopifyCartSnapshot["items"][number];
  isAttached?: boolean;
  onAttach?: () => void;
}) {
  return (
    <div className="relative flex gap-3 rounded-2xl border border-slate-200 bg-white p-3 pr-12">
      {onAttach && <AttachToChatButton isAttached={isAttached} onAttach={() => onAttach()} compact className="absolute right-2 top-2 z-10" />}
      <div className="h-16 w-16 shrink-0 overflow-hidden rounded-xl bg-indigo-100">
        {item.image ? <img src={item.image} alt="" className="h-full w-full object-cover" loading="lazy" /> : <ShoppingCart className="m-5 h-6 w-6" />}
      </div>
      <div className="min-w-0 flex-1">
        <div className="line-clamp-2 text-sm font-extrabold">{item.product_title || item.title}</div>
        {item.variant_title && item.variant_title !== "Default Title" && <div className="text-xs text-slate-500">{item.variant_title}</div>}
        <div className="mt-1 text-sm font-bold">
          {formatCents(item.final_line_price)} x {item.quantity}
        </div>
      </div>
    </div>
  );
}

function EmptyRailCard({ title, body }: { title: string; body: string }) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
      <div className="font-extrabold">{title}</div>
      <p className="mt-1 text-sm text-slate-500">{body}</p>
    </div>
  );
}

function QuickBrowseCard({ onPrompt }: { onPrompt: (ask: (typeof SHOPIFY_QUICK_ASKS)[number]) => void }) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
      <div className="flex items-center gap-2">
        <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-indigo-50 text-indigo-600">
          <Sparkles className="h-4 w-4" />
        </span>
        <div>
          <div className="font-extrabold">Explore the store</div>
          <p className="text-sm text-slate-500">Ask Max Mode to bring product cards into this panel.</p>
        </div>
      </div>
      <div className="mt-4 grid gap-2">
        {SHOPIFY_QUICK_ASKS.slice(0, 3).map((ask) => (
          <button
            key={ask.label}
            onClick={() => onPrompt(ask)}
            className="flex w-full items-center justify-between rounded-xl border border-slate-200 bg-slate-50 px-3 py-2 text-left text-sm font-bold text-slate-700 hover:border-indigo-200 hover:bg-indigo-50 hover:text-indigo-700"
          >
            {ask.label}
            <ChevronRight className="h-4 w-4 text-slate-400" />
          </button>
        ))}
      </div>
    </div>
  );
}

function productToChatAttachment(product: ProductCardModel) {
  const productVariantId =
    stringFrom(product.source?.product_variant_id) ||
    stringFrom(product.source?.metadata?.product_variant_id) ||
    stringFrom(product.source?.metadata?.firstAvailableVariantId) ||
    (product.variantId ? `gid://shopify/ProductVariant/${product.variantId}` : undefined);
  const metadata: Record<string, unknown> = {
    title: product.title,
    price: product.price,
    availability: product.availability,
    product_variant_id: productVariantId,
    url: product.url,
    imageUrl: product.imageUrl,
  };
  Object.keys(metadata).forEach((key) => {
    if (metadata[key] === undefined || metadata[key] === "") {
      delete metadata[key];
    }
  });

  const data: Record<string, unknown> = {
    id: product.id,
    name: product.title,
    title: product.title,
    description: product.subtitle,
    content: product.subtitle,
    price: product.price,
    category: "product",
    availability: product.availability,
    imageUrl: product.imageUrl,
    url: product.url,
    product_variant_id: productVariantId,
    variantId: product.variantId,
    metadata,
  };
  Object.keys(data).forEach((key) => {
    if (data[key] === undefined || data[key] === "") {
      delete data[key];
    }
  });

  return { type: "product", data };
}

function cartLineAttachmentId(item: ShopifyCartSnapshot["items"][number]) {
  return `cart-line-${item.id}`;
}

function cartLineToChatItem(item: ShopifyCartSnapshot["items"][number]) {
  const title = item.product_title || item.title;
  const data: Record<string, unknown> = {
    id: cartLineAttachmentId(item),
    name: title,
    title,
    description: [item.variant_title && item.variant_title !== "Default Title" ? item.variant_title : undefined, `Quantity: ${item.quantity}`]
      .filter(Boolean)
      .join(" | "),
    price: formatCents(item.final_line_price),
    quantity: item.quantity,
    category: "cart-item",
    status: "in cart",
    imageUrl: item.image,
    url: item.url ? shopifyRoute(item.url) : undefined,
  };
  Object.keys(data).forEach((key) => {
    if (data[key] === undefined || data[key] === "") {
      delete data[key];
    }
  });
  return data;
}

function initialWorkspaceState(controller: MaxModeController, pageGroup: string): ShopifyWorkspaceState {
  if (controller.isCartView || controller.currentPosition === "cart" || pageGroup === "cart" || pageGroup === "account") {
    return "cart";
  }
  if (controller.selectedProduct || pageGroup === "product") {
    return "product";
  }
  if (pageGroup === "collection" || controller.currentPosition === "catalog" || controller.currentPosition === "search") {
    return "browsing";
  }
  return "discovery";
}

function deriveWorkspaceState(
  controller: MaxModeController,
  pageGroup: string,
  products: ProductCardModel[],
  policyDocs: Document[],
): ShopifyWorkspaceState {
  if (controller.isCartView || controller.currentPosition === "cart" || pageGroup === "cart" || pageGroup === "account") {
    return "cart";
  }
  if (controller.selectedProduct || pageGroup === "product") {
    return "product";
  }
  if (policyDocs.length > 0 && products.length === 0) {
    return "policy";
  }
  if (products.length > 1 || pageGroup === "collection" || controller.currentPosition === "catalog" || controller.currentPosition === "search") {
    return "browsing";
  }
  return "discovery";
}

function deriveProducts(documents: Document[], requestContext: Record<string, any>): ProductCardModel[] {
  const products = documents
    .map((doc) => toProductCard(doc))
    .filter((product): product is ProductCardModel => Boolean(product));
  if (products.length) {
    return dedupeProducts(products).slice(0, 8);
  }
  const currentProduct = requestContext.product;
  if (currentProduct && typeof currentProduct === "object" && currentProduct.title) {
    const variantId = numericVariantId(currentProduct.variantId);
    return [
      {
        id: String(currentProduct.id || currentProduct.handle || currentProduct.title),
        title: String(currentProduct.title),
        subtitle: [currentProduct.vendor, currentProduct.type].filter(Boolean).join(" / ") || undefined,
        price: formatCentsFromUnknown(currentProduct.priceCents),
        availability: variantId ? "Available" : undefined,
        url: currentProduct.handle ? shopifyRoute(`products/${currentProduct.handle}`) : undefined,
        variantId,
      },
    ];
  }
  return [];
}

function deriveSpotlight(selectedProduct: unknown, products: ProductCardModel[], requestContext: Record<string, any>) {
  if (selectedProduct && typeof selectedProduct === "object") {
    const asDoc = selectedProduct as Document;
    const card = toProductCard(asDoc);
    if (card) {
      return card;
    }
  }
  if (products.length > 0) {
    return products[0];
  }
  return deriveProducts([], requestContext)[0] ?? null;
}

function derivePolicyDocuments(documents: Document[]) {
  return documents.filter((doc) => {
    const type = String(doc.type || "").toLowerCase();
    const category = String(doc.metadata?.category || doc.metadata?.vectorSpace || "").toLowerCase();
    return type.includes("policy") || category.includes("policy") || category.includes("shipping") || category.includes("return");
  });
}

function deriveSourceDocuments(documents: Document[]) {
  const seen = new Set<string>();
  return documents.filter((doc) => {
    if (!doc || (!doc.content && !doc.title)) {
      return false;
    }
    const key = String(doc.id || doc.title || doc.content);
    if (seen.has(key)) {
      return false;
    }
    seen.add(key);
    return true;
  });
}

function hydrateProductsFromStorefront(products: ProductCardModel[], storefrontProducts: ProductCardModel[]) {
  if (!products.length || !storefrontProducts.length) {
    return products;
  }
  return products.map((product) => {
    const storefront = resolveProductFromPool(product, storefrontProducts);
    if (!storefront) {
      return product;
    }
    return {
      ...product,
      title: product.title || storefront.title,
      subtitle: product.subtitle || storefront.subtitle,
      price: product.price || storefront.price,
      compareAtPrice: product.compareAtPrice || storefront.compareAtPrice,
      availability: product.availability || storefront.availability,
      imageUrl: product.imageUrl || storefront.imageUrl,
      url: product.url || storefront.url,
      variantId: product.variantId || storefront.variantId,
    };
  });
}

function toProductCard(doc: Document): ProductCardModel | null {
  const type = String(doc.type || doc.metadata?.category || doc.metadata?.vectorSpace || "").toLowerCase();
  const looksLikeProduct =
    type.includes("product") ||
    Boolean(doc.product_variant_id) ||
    Boolean(doc.metadata?.product_variant_id) ||
    Boolean(doc.metadata?.variantId) ||
    Boolean(doc.metadata?.price) ||
    Boolean(doc.priceRange);
  if (!looksLikeProduct) {
    return null;
  }
  const title = cleanProductTitle(doc.title || doc.metadata?.title || doc.metadata?.name || "Product");
  const variant = doc.product_variant_id || doc.metadata?.product_variant_id || doc.metadata?.variantId || doc.metadata?.variant_id;
  const sourceUrl = sourceUrlFromDocument(doc);
  const handle = shopifyProductHandleFromDocument(doc);
  return {
    id: String(doc.id || title),
    title,
    subtitle: truncate(stripJsonNoise(doc.content || doc.metadata?.description || ""), 180),
    price: stringFrom(doc.priceRange || doc.metadata?.priceRange || doc.metadata?.price || doc.metadata?.amount),
    compareAtPrice: stringFrom(doc.metadata?.compareAtPrice || doc.metadata?.compare_at_price),
    availability: doc.availability || doc.metadata?.availability || doc.metadata?.status,
    imageUrl: documentImageUrl(doc),
    url: sourceUrl || (handle ? shopifyRoute(`products/${handle}`) : undefined),
    variantId: numericVariantId(variant),
    source: doc,
  };
}

function dedupeProducts(products: ProductCardModel[]) {
  const seen = new Set<string>();
  return products.filter((product) => {
    const key = [product.id, product.title].filter(Boolean).join("|").toLowerCase();
    if (seen.has(key)) {
      return false;
    }
    seen.add(key);
    return true;
  });
}

function useShopifySourceProducts(sourceDocs: Document[]) {
  const handles = useMemo(() => {
    const seen = new Set<string>();
    return sourceDocs
      .map((doc) => shopifyProductHandleFromDocument(doc))
      .filter((handle): handle is string => Boolean(handle))
      .filter((handle) => {
        if (seen.has(handle)) {
          return false;
        }
        seen.add(handle);
        return true;
      })
      .slice(0, 8);
  }, [sourceDocs]);
  const handlesKey = handles.join("|");
  const [products, setProducts] = useState<ProductCardModel[]>([]);

  useEffect(() => {
    if (!handles.length) {
      setProducts([]);
      return;
    }
    let cancelled = false;
    Promise.all(handles.map((handle) => fetchShopifyProductByHandle(handle).catch(() => null)))
      .then((nextProducts) => {
        if (!cancelled) {
          setProducts(dedupeProducts(nextProducts.filter((product): product is ProductCardModel => Boolean(product))));
        }
      })
      .catch(() => {
        if (!cancelled) {
          setProducts([]);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [handlesKey]);

  return products;
}

function resolveSourceProduct(doc: Document, products: ProductCardModel[]) {
  const docCard = toProductCard(doc);
  const storefront = docCard ? resolveProductFromPool(docCard, products) : resolveProductByDocument(doc, products);
  if (docCard && storefront) {
    return {
      ...docCard,
      title: storefront.title || docCard.title,
      subtitle: docCard.subtitle || storefront.subtitle,
      price: docCard.price || storefront.price,
      compareAtPrice: docCard.compareAtPrice || storefront.compareAtPrice,
      availability: docCard.availability || storefront.availability,
      imageUrl: docCard.imageUrl || storefront.imageUrl,
      url: docCard.url || storefront.url,
      variantId: docCard.variantId || storefront.variantId,
    };
  }
  return storefront || docCard;
}

function resolveProductFromPool(product: ProductCardModel, products: ProductCardModel[]) {
  const productHandle = shopifyProductHandleFromUrl(product.url);
  const normalizedTitle = normalizeLookupText(product.title);
  return products.find((candidate) => {
    if (product.variantId && candidate.variantId && product.variantId === candidate.variantId) {
      return true;
    }
    if (product.id && candidate.id && String(product.id) === String(candidate.id)) {
      return true;
    }
    const candidateHandle = shopifyProductHandleFromUrl(candidate.url);
    if (productHandle && candidateHandle && productHandle === candidateHandle) {
      return true;
    }
    return normalizedTitle && normalizedTitle === normalizeLookupText(candidate.title);
  });
}

function resolveProductByDocument(doc: Document, products: ProductCardModel[]) {
  const handle = shopifyProductHandleFromDocument(doc);
  const title = cleanProductTitle(stringFrom(parseDocumentContent(doc.content)?.name || doc.title) || "");
  return products.find((candidate) => {
    if (handle && shopifyProductHandleFromUrl(candidate.url) === handle) {
      return true;
    }
    return title && normalizeLookupText(title) === normalizeLookupText(candidate.title);
  });
}

function shopifyProductHandleFromDocument(doc: Document) {
  const metadata = doc.metadata || {};
  return (
    stringFrom(metadata.handle) ||
    shopifyProductHandleFromUrl(sourceUrlFromDocument(doc)) ||
    shopifyProductHandleFromUrl(stringFrom(metadata.storefrontUrl))
  );
}

function shopifyProductHandleFromUrl(value: string | undefined) {
  if (!value) {
    return undefined;
  }
  try {
    const parsed = new URL(value, typeof window !== "undefined" ? window.location.href : "https://store.local/");
    const match = parsed.pathname.match(/\/products\/([^/?#]+)/i);
    return match?.[1] ? decodeURIComponent(match[1]) : undefined;
  } catch {
    const match = value.match(/\/products\/([^/?#]+)/i);
    return match?.[1] ? decodeURIComponent(match[1]) : undefined;
  }
}

function sourceUrlFromDocument(doc: Document) {
  return stringFrom((doc as any).url || (doc as any).storefrontUrl || doc.metadata?.url || doc.metadata?.storefrontUrl);
}

function documentImageUrl(doc: Document) {
  return stringFrom(
    (doc as any).imageUrl ||
      (doc as any).featuredImage ||
      doc.metadata?.imageUrl ||
      doc.metadata?.image_url ||
      doc.metadata?.featuredImage ||
      doc.metadata?.featured_image ||
      doc.metadata?.image,
  );
}

function parseDocumentContent(value: string | undefined): Record<string, any> | null {
  if (!value) {
    return null;
  }
  const trimmed = value.trim();
  if (!trimmed.startsWith("{")) {
    return null;
  }
  try {
    const parsed = JSON.parse(trimmed);
    return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed : null;
  } catch {
    return null;
  }
}

function sourcePreview(doc: Document, parsed: Record<string, any> | null) {
  return (
    stringFrom(parsed?.description) ||
    stringFrom(parsed?.summary) ||
    stringFrom(doc.metadata?.summary || doc.metadata?.description) ||
    stripJsonNoise(doc.content || "")
  );
}

function friendlySourceType(value: string) {
  return value
    .replace(/[-_]+/g, " ")
    .replace(/\b\w/g, (char) => char.toUpperCase())
    .trim() || "Source";
}

function normalizeLookupText(value: string | undefined) {
  return String(value || "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, " ")
    .trim();
}

function cleanProductTitle(value: string) {
  const normalized = value.replace(/\\"/g, '"');
  if (normalized.includes('","description"')) {
    return normalized.split('","description"')[0].replace(/^"+|"+$/g, "").trim() || value;
  }
  return normalized.replace(/^"+|"+$/g, "").trim();
}

function stripJsonNoise(value: string) {
  return value
    .replace(/\\"/g, '"')
    .replace(/[{}[\]]/g, " ")
    .replace(/"\w+":/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function truncate(value: string | undefined, max: number) {
  if (!value) {
    return "";
  }
  return value.length > max ? value.slice(0, max - 1).trimEnd() + "..." : value;
}

function stringFrom(value: unknown): string | undefined {
  if (value === undefined || value === null || value === "") {
    return undefined;
  }
  return String(value);
}

function deriveStoreName(requestContext: Record<string, any>) {
  const title = String(requestContext.pageTitle || "").trim();
  if (title && !/password|not found/i.test(title)) {
    return title;
  }
  if (typeof window !== "undefined") {
    const shop = (window as any).Shopify?.shop;
    if (typeof shop === "string" && shop.trim()) {
      return shop.replace(".myshopify.com", "").replace(/[-_]+/g, " ");
    }
  }
  return "Store";
}

function numericVariantId(value: unknown): number | undefined {
  if (typeof value === "number" && Number.isFinite(value)) {
    return value;
  }
  const text = typeof value === "string" ? value.trim() : "";
  if (!text) {
    return undefined;
  }
  const match = text.match(/(\d+)$/);
  if (!match) {
    return undefined;
  }
  const parsed = Number(match[1]);
  return Number.isFinite(parsed) ? parsed : undefined;
}

function shopifyRoute(path: string) {
  const root = typeof window !== "undefined" ? ((window as any).Shopify?.routes?.root || "/") : "/";
  const normalizedRoot = String(root || "/").replace(/\/?$/, "/");
  return normalizedRoot + path.replace(/^\/+/, "");
}

function formatCentsFromUnknown(value: unknown) {
  if (value === undefined || value === null || value === "") {
    return undefined;
  }
  const cents = Number(value);
  if (!Number.isFinite(cents)) {
    return String(value);
  }
  return formatCents(cents);
}

function formatCents(cents: number) {
  const currency = typeof window !== "undefined" ? ((window as any).Shopify?.currency?.active || "USD") : "USD";
  return new Intl.NumberFormat(undefined, { style: "currency", currency }).format(cents / 100);
}

async function fetchShopifyStorefrontProducts(limit: number): Promise<ProductCardModel[]> {
  const response = await fetch(shopifyRoute(`products.json?limit=${Math.max(1, Math.min(limit, 20))}`), {
    headers: { Accept: "application/json" },
    credentials: "same-origin",
  });
  if (!response.ok) {
    throw new Error(`Shopify product fetch failed: ${response.status}`);
  }
  const contentType = response.headers.get("content-type") || "";
  if (!contentType.includes("application/json")) {
    throw new Error("Shopify product fetch did not return JSON");
  }
  const payload = (await response.json()) as { products?: ShopifyStorefrontProduct[] };
  const cards = (payload.products || [])
    .map((product) => toStorefrontProductCard(product))
    .filter((product): product is ProductCardModel => Boolean(product));
  return dedupeProducts(cards).slice(0, limit);
}

async function fetchShopifyProductByHandle(handle: string): Promise<ProductCardModel | null> {
  const response = await fetch(shopifyRoute(`products/${encodeURIComponent(handle)}.js`), {
    headers: { Accept: "application/json" },
    credentials: "same-origin",
  });
  if (!response.ok) {
    throw new Error(`Shopify product fetch failed: ${response.status}`);
  }
  const contentType = response.headers.get("content-type") || "";
  if (!contentType.includes("application/json") && !contentType.includes("javascript")) {
    throw new Error("Shopify product detail fetch did not return product JSON");
  }
  return toStorefrontProductCard((await response.json()) as ShopifyStorefrontProduct);
}

function toStorefrontProductCard(product: ShopifyStorefrontProduct): ProductCardModel | null {
  if (!product || !product.title) {
    return null;
  }
  const variants = Array.isArray(product.variants) ? product.variants : [];
  const selectedVariant = variants.find((variant) => variant.available !== false) || variants[0];
  const image = firstStorefrontImage(product);
  return {
    id: String(product.id || product.handle || product.title),
    title: cleanProductTitle(String(product.title)),
    subtitle: truncate(
      [product.product_type, product.vendor, stripHtml(String(product.body_html || ""))].filter(Boolean).join(" / "),
      140,
    ),
    price: formatStorefrontPrice(selectedVariant?.price),
    compareAtPrice: formatStorefrontPrice(selectedVariant?.compare_at_price),
    availability: selectedVariant?.available === false ? "Unavailable" : "In stock",
    imageUrl: image,
    url: product.handle ? shopifyRoute(`products/${product.handle}`) : undefined,
    variantId: numericVariantId(selectedVariant?.id),
  };
}

function firstStorefrontImage(product: ShopifyStorefrontProduct) {
  const image = Array.isArray(product.images) ? product.images[0] : undefined;
  if (typeof image === "string") {
    return image;
  }
  return image?.src || product.featured_image || undefined;
}

function formatStorefrontPrice(value: unknown) {
  if (value === undefined || value === null || value === "") {
    return undefined;
  }
  const amount = Number(value);
  if (!Number.isFinite(amount)) {
    return String(value);
  }
  const currency = typeof window !== "undefined" ? ((window as any).Shopify?.currency?.active || "USD") : "USD";
  return new Intl.NumberFormat(undefined, { style: "currency", currency }).format(amount);
}

function stripHtml(value: string) {
  return value.replace(/<[^>]*>/g, " ").replace(/\s+/g, " ").trim();
}

async function fetchShopifyCart(): Promise<ShopifyCartSnapshot> {
  const response = await fetch(shopifyRoute("cart.js"), {
    headers: { Accept: "application/json" },
    credentials: "same-origin",
  });
  if (!response.ok) {
    throw new Error(`Shopify cart fetch failed: ${response.status}`);
  }
  return response.json();
}

async function addVariantToShopifyCart(variantId: number, quantity: number) {
  const response = await fetch(shopifyRoute("cart/add.js"), {
    method: "POST",
    headers: { Accept: "application/json", "Content-Type": "application/json" },
    credentials: "same-origin",
    body: JSON.stringify({ items: [{ id: variantId, quantity }] }),
  });
  if (!response.ok) {
    throw new Error(`Shopify cart add failed: ${response.status}`);
  }
  return response.json();
}
