import { useCallback, useState } from "react";

import type { Document } from "@/types";

import { addCartItem, getActiveCart, removeCartItem } from "@/api/cart";

type ToastFn = (opts: any) => void;

export function useCartController({
  userId,
  toast,
  setIsBottomSheetOpen,
  setIsPanelVisible,
}: {
  userId: string;
  toast: ToastFn;
  setIsBottomSheetOpen: (open: boolean) => void;
  setIsPanelVisible: (visible: boolean) => void;
}) {
  const [cartData, setCartData] = useState<any>(null);
  const [isCartView, setIsCartView] = useState(false);
  const [selectedProduct, setSelectedProduct] = useState<Document | null>(null);

  const addToCart = useCallback(
    async (product: Document, quantity: number = 1) => {
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
    [toast, userId],
  );

  const fetchCart = useCallback(async () => {
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
  }, [toast, userId]);

  const removeFromCart = useCallback(
    async (sku: string) => {
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
    [toast, userId],
  );

  const openCart = useCallback(async () => {
    setIsCartView(true);
    setSelectedProduct(null);
    await fetchCart();

    if (window.innerWidth < 768) {
      setIsBottomSheetOpen(true);
    } else {
      setIsPanelVisible(true);
    }
  }, [fetchCart, setIsBottomSheetOpen, setIsPanelVisible]);

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

