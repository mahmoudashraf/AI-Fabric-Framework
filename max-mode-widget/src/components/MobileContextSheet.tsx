import { AnimatePresence, motion } from "framer-motion";
import { ArrowRight, Sparkles, X } from "lucide-react";

import { Button } from "@/ui/button";

import type { Document } from "@/types";
import { CartView } from "./MobileContextSheet/CartView";
import { DocumentsView } from "./MobileContextSheet/DocumentsView";
import { ProductDetailsView } from "./MobileContextSheet/ProductDetailsView";

export function MobileContextSheet({
  isOpen,
  setIsOpen,
  contextDocuments,
  selectedProduct,
  isCartView,
  cartData,
  viewedDocumentIds,
  newDocuments,
  isItemAttached,
  onCloseAll,
  onCloseCart,
  onCloseProductDetails,
  onOpenProductDetails,
  onRemoveFromCart,
  onProceedToCheckout,
  onAttachCartToChat,
  onBrowseProducts,
  onAddToCart,
  onAttachProductToChat,
  onAttachDocument,
}: {
  isOpen: boolean;
  setIsOpen: (open: boolean) => void;
  contextDocuments: Document[];
  selectedProduct: Document | null;
  isCartView: boolean;
  cartData: any;
  viewedDocumentIds: Set<string>;
  newDocuments: Document[];
  isItemAttached: (itemId: string) => boolean;
  onCloseAll: () => void;
  onCloseCart: () => void;
  onCloseProductDetails: () => void;
  onOpenProductDetails: (doc: Document) => void;
  onRemoveFromCart: (sku: string) => void;
  onProceedToCheckout: () => void;
  onAttachCartToChat: () => void;
  onBrowseProducts: () => void;
  onAddToCart: (product: Document) => void;
  onAttachProductToChat: (product: Document) => void;
  onAttachDocument: (doc: Document) => void;
}) {
  const handleProceedToCheckout = () => {
    onProceedToCheckout();
    setIsOpen(false);
    onCloseCart();
  };

  const handleBrowseProducts = () => {
    setIsOpen(false);
    onBrowseProducts();
  };

  const handleAddToCart = () => {
    if (!selectedProduct) return;
    onAddToCart(selectedProduct);
    setIsOpen(false);
    onCloseProductDetails();
  };

  const handleAttachProductToChat = () => {
    if (!selectedProduct) return;
    onAttachProductToChat(selectedProduct);
    setIsOpen(false);
    onCloseProductDetails();
  };

  return (
    <AnimatePresence>
      {isOpen && contextDocuments.length > 0 && (
        <>
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={() => setIsOpen(false)}
            className="md:hidden fixed inset-0 bg-black/50 backdrop-blur-sm z-[60]"
          />

          <motion.div
            initial={{ y: "100%" }}
            animate={{ y: 0 }}
            exit={{ y: "100%" }}
            transition={{ type: "spring", damping: 30, stiffness: 300 }}
            drag="y"
            dragConstraints={{ top: 0, bottom: 0 }}
            dragElastic={{ top: 0, bottom: 0.5 }}
            onDragEnd={(e, { offset, velocity }) => {
              if (offset.y > 100 || velocity.y > 500) {
                setIsOpen(false);
              }
            }}
            className="md:hidden fixed bottom-0 left-0 right-0 bg-gradient-to-b from-blue-50 to-white dark:from-gray-900 dark:via-blue-900/20 dark:to-gray-900 rounded-t-3xl shadow-2xl z-[70] max-h-[80vh] flex flex-col"
          >
            <div className="flex justify-center pt-3 pb-2">
              <div className="w-12 h-1.5 bg-gray-300 dark:bg-gray-700 rounded-full" />
            </div>

            <div className="px-4 py-3 border-b border-blue-200 dark:border-blue-800 flex items-center justify-between">
              <div className="flex items-center gap-2">
                {selectedProduct && (
                  <Button
                    variant="ghost"
                    size="icon"
                    onClick={onCloseProductDetails}
                    className="h-8 w-8 -ml-2"
                    aria-label="Back"
                  >
                    <ArrowRight className="h-5 w-5 rotate-180" />
                  </Button>
                )}
                <div className="p-2 bg-gradient-to-br from-blue-600 to-blue-500 rounded-lg">
                  <Sparkles className="h-4 w-4 text-white" />
                </div>
                <div>
                  <h3 className="text-sm font-bold bg-gradient-to-r from-blue-600 to-blue-500 bg-clip-text text-transparent">
                    {isCartView ? "Shopping Cart" : selectedProduct ? "Product Details" : "Context Documents"}
                  </h3>
                  <p className="text-[10px] text-muted-foreground">
                    {isCartView
                      ? "View and manage your cart"
                      : selectedProduct
                        ? "View details and add to cart"
                        : `${contextDocuments.length} ${contextDocuments.length === 1 ? "item" : "items"} • Tap to view`}
                  </p>
                </div>
              </div>
              <Button variant="ghost" size="icon" onClick={onCloseAll} className="h-8 w-8" aria-label="Close">
                <X className="h-5 w-5" />
              </Button>
            </div>

            <div className="flex-1 overflow-y-auto px-4 py-4 space-y-3">
              {isCartView ? (
                <CartView
                  cartData={cartData}
                  onRemoveFromCart={onRemoveFromCart}
                  onProceedToCheckout={handleProceedToCheckout}
                  onAttachCartToChat={onAttachCartToChat}
                  onBrowseProducts={handleBrowseProducts}
                />
              ) : selectedProduct ? (
                <ProductDetailsView
                  selectedProduct={selectedProduct}
                  onAddToCart={handleAddToCart}
                  onAttachProductToChat={handleAttachProductToChat}
                />
              ) : (
                <DocumentsView
                  contextDocuments={contextDocuments}
                  newDocuments={newDocuments}
                  viewedDocumentIds={viewedDocumentIds}
                  isItemAttached={isItemAttached}
                  onOpenProductDetails={onOpenProductDetails}
                  onAttachDocument={onAttachDocument}
                />
              )}
            </div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
}
