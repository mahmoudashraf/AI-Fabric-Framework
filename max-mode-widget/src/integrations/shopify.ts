/**
 * Shopify integration module.
 *
 * Auto-detects Shopify product pages, extracts product data,
 * and integrates with the Shopify Cart API.
 *
 * Usage (in Liquid theme):
 *   <script src="https://cdn.example.com/max-mode-widget.iife.js"></script>
 *   <script>
 *     MaxMode.init({
 *       apiConfig: { chatBaseUrl: "...", crudBaseUrl: "..." },
 *       shopify: {
 *         enabled: true,
 *         cartIntegration: true,
 *         autoAttachProduct: true,
 *       },
 *     });
 *   </script>
 */

export interface ShopifyConfig {
  /** Enable Shopify-specific features */
  enabled: boolean;
  /** Sync cart operations with Shopify's Cart API */
  cartIntegration?: boolean;
  /** Auto-attach the current product page's product to the widget */
  autoAttachProduct?: boolean;
  /** CSS selector to find product info (default: auto-detect) */
  productSelector?: string;
}

export interface ShopifyProduct {
  id: number;
  title: string;
  handle: string;
  description: string;
  vendor: string;
  type: string;
  price: number;
  compareAtPrice?: number;
  images: string[];
  variants: ShopifyVariant[];
  url: string;
}

export interface ShopifyVariant {
  id: number;
  title: string;
  price: number;
  sku: string;
  available: boolean;
}

interface ShopifyWindow {
  Shopify?: {
    shop: string;
    theme?: { name: string; id: number };
    currency?: { active: string };
  };
  ShopifyAnalytics?: any;
  meta?: {
    product?: {
      id: number;
      gid: string;
      vendor: string;
      type: string;
      variants: Array<{ id: number; sku: string; price: number }>;
    };
    page?: { pageType: string };
  };
}

/**
 * Detect if we're running inside a Shopify storefront.
 */
export function isShopifyStore(): boolean {
  const win = window as unknown as ShopifyWindow;
  return !!(win.Shopify?.shop || win.ShopifyAnalytics);
}

/**
 * Detect if the current page is a Shopify product page.
 */
export function isProductPage(): boolean {
  const win = window as unknown as ShopifyWindow;
  if (win.meta?.page?.pageType === "product") return true;
  return /\/products\/[^/]+\/?$/.test(window.location.pathname);
}

/**
 * Extract the current product's data from the Shopify page.
 * Uses the global product JSON (injected by Shopify themes).
 */
export async function getCurrentProduct(): Promise<ShopifyProduct | null> {
  try {
    // Method 1: Try fetching product JSON from Shopify's .json endpoint
    const handle = window.location.pathname.match(/\/products\/([^/?#]+)/)?.[1];
    if (handle) {
      const res = await fetch(`/products/${handle}.json`);
      if (res.ok) {
        const { product } = await res.json();
        return {
          id: product.id,
          title: product.title,
          handle: product.handle,
          description: product.body_html?.replace(/<[^>]*>/g, "") || "",
          vendor: product.vendor,
          type: product.product_type,
          price: product.variants?.[0]?.price
            ? parseFloat(product.variants[0].price)
            : 0,
          compareAtPrice: product.variants?.[0]?.compare_at_price
            ? parseFloat(product.variants[0].compare_at_price)
            : undefined,
          images: product.images?.map((img: any) => img.src) || [],
          variants: (product.variants || []).map((v: any) => ({
            id: v.id,
            title: v.title,
            price: parseFloat(v.price),
            sku: v.sku || "",
            available: v.available,
          })),
          url: `/products/${product.handle}`,
        };
      }
    }

    // Method 2: Try reading from the page's meta object
    const win = window as unknown as ShopifyWindow;
    if (win.meta?.product) {
      const meta = win.meta.product;
      return {
        id: meta.id,
        title: document.querySelector("h1")?.textContent?.trim() || "Product",
        handle: handle || "",
        description: "",
        vendor: meta.vendor,
        type: meta.type,
        price: meta.variants?.[0]?.price
          ? meta.variants[0].price / 100
          : 0,
        images: [],
        variants: (meta.variants || []).map((v) => ({
          id: v.id,
          title: "",
          price: v.price / 100,
          sku: v.sku || "",
          available: true,
        })),
        url: window.location.pathname,
      };
    }
  } catch (e) {
    console.warn("[MaxMode/Shopify] Failed to extract product:", e);
  }

  return null;
}

/**
 * Add an item to the Shopify cart via their AJAX API.
 */
export async function addToShopifyCart(
  variantId: number,
  quantity = 1,
): Promise<any> {
  const res = await fetch("/cart/add.js", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      items: [{ id: variantId, quantity }],
    }),
  });
  if (!res.ok) throw new Error(`Shopify cart add failed: ${res.status}`);
  return res.json();
}

/**
 * Get the current Shopify cart contents.
 */
export async function getShopifyCart(): Promise<any> {
  const res = await fetch("/cart.js");
  if (!res.ok) throw new Error(`Shopify cart fetch failed: ${res.status}`);
  return res.json();
}

/**
 * Remove an item from the Shopify cart.
 */
export async function removeFromShopifyCart(
  variantId: number,
): Promise<any> {
  const res = await fetch("/cart/change.js", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ id: variantId, quantity: 0 }),
  });
  if (!res.ok) throw new Error(`Shopify cart remove failed: ${res.status}`);
  return res.json();
}

/**
 * Initialize Shopify-specific behaviors.
 * Called automatically when `shopify.enabled` is set in config.
 */
export async function initShopifyIntegration(
  config: ShopifyConfig,
): Promise<void> {
  if (!config.enabled || !isShopifyStore()) return;

  // Auto-attach current product if on a product page
  if (config.autoAttachProduct && isProductPage()) {
    const product = await getCurrentProduct();
    if (product && (window as any).MaxMode) {
      (window as any).MaxMode.attachProduct({
        sku: product.variants[0]?.sku || product.handle,
        name: product.title,
        price: product.price,
        description: product.description,
        images: product.images,
        vendor: product.vendor,
        shopifyId: product.id,
        shopifyVariantId: product.variants[0]?.id,
        url: product.url,
      });
    }
  }
}
