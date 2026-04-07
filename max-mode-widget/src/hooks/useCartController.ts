import { useCallback, useState } from "react";

import type { Document } from "@/types";

import { addCartItem, getActiveCart, removeCartItem } from "@/api/cart";

type ToastFn = (opts: any) => void;

export function useCartController({
  enabled,
  userId,
  toast,
  setIsBottomSheetOpen,
  setIsPanelVisible,
}: {
  enabled: boolean;
  userId?: string;
  toast: ToastFn;
  setIsBottomSheetOpen: (open: boolean) => void;
  setIsPanelVisible: (visible: boolean) => void;
}) {
  const [cartData, setCartData] = useState<any>(null);
  const [isCartView, setIsCartView] = useState(false);
  const [selectedProduct, setSelectedProduct] = useState<Document | null>(null);

  const notifyUnavailable = useCallback(() => {
    toast({
      title: "Cart Unavailable",
      description:
        "Cart and business CRUD actions are disabled because apiConfig.crudBaseUrl is not configured.",
      variant: "destructive",
    });
  }, [toast]);

  const addToCart = useCallback(
    async (product: Document, quantity: number = 1) => {
      if (!enabled) {
        notifyUnavailable();
        return null;
      }
      try {
        const sku = product.metadata?.sku || product.id;
        const cart = await addCartItem(userId, sku, quantity);
        toast({
          title: "🛒 Added to Cart!",
          description: `${product.title} has been added to your cart`,
        });
        return cart;
      } catch (error) {
        toast({
          title: "❌ Error",
          description: "Failed to add item to cart. Please try again.",
          variant: "destructive",
        });
        console.error("Error adding to cart:", error);
      }
    },
    [enabled, notifyUnavailable, toast, userId],
  );

  const fetchCart = useCallback(async () => {
    if (!enabled) {
      const emptyCart = {
        items: [],
        subtotal: 0,
        discount: 0,
        total: 0,
      };
      setCartData(emptyCart);
      return emptyCart;
    }
    try {
      const cart = await getActiveCart(userId);
      if (cart) {
        setCartData(cart);
        return cart;
      }

      const emptyCart = {
        items: [],
        subtotal: 0,
        discount: 0,
        total: 0,
      };
      setCartData(emptyCart);
      return emptyCart;
    } catch (error) {
      console.error("Error fetching cart:", error);
      const emptyCart = {
        items: [],
        subtotal: 0,
        discount: 0,
        total: 0,
      };
      setCartData(emptyCart);
      toast({
        title: "⚠️ Cart Load Issue",
        description: "Could not load cart. Showing empty cart.",
        variant: "destructive",
      });
      return emptyCart;
    }
  }, [enabled, toast, userId]);

  const removeFromCart = useCallback(
    async (sku: string) => {
      if (!enabled) {
        notifyUnavailable();
        return null;
      }
      try {
        const cart = await removeCartItem(userId, sku);
        setCartData(cart);
        toast({
          title: "🗑️ Removed from Cart",
          description: "Item has been removed from your cart",
        });
        return cart;
      } catch (error) {
        toast({
          title: "❌ Error",
          description: "Failed to remove item. Please try again.",
          variant: "destructive",
        });
        console.error("Error removing from cart:", error);
      }
    },
    [enabled, notifyUnavailable, toast, userId],
  );

  const openCart = useCallback(async () => {
    if (!enabled) {
      notifyUnavailable();
      return;
    }
    setIsCartView(true);
    setSelectedProduct(null);
    await fetchCart();

    if (window.innerWidth < 768) {
      setIsBottomSheetOpen(true);
    } else {
      setIsPanelVisible(true);
    }
  }, [enabled, fetchCart, notifyUnavailable, setIsBottomSheetOpen, setIsPanelVisible]);

  const closeCart = useCallback(() => {
    setIsCartView(false);
    setCartData(null);
  }, []);

  const openProductDetails = useCallback((doc: Document) => {
    setSelectedProduct(doc);
    setIsCartView(false);
  }, []);

  const closeProductDetails = useCallback(() => {
    setSelectedProduct(null);
  }, []);

  return {
    cartData,
    isCartView,
    selectedProduct,
    addToCart,
    fetchCart,
    removeFromCart,
    openCart,
    closeCart,
    openProductDetails,
    closeProductDetails,
  } as const;
}
