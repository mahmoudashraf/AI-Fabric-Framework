import type { RefObject } from "react";

import { AnimatePresence, motion } from "framer-motion";
import { ArrowRight, Eye, EyeOff, Sparkles } from "lucide-react";

import { Button } from "@/ui/button";

import type { Document } from "@/types";
import { CartView } from "./DesktopContextPanel/CartView";
import { DocumentsView } from "./DesktopContextPanel/DocumentsView";
import { ProductDetailsView } from "./DesktopContextPanel/ProductDetailsView";

export function DesktopContextPanel({
  contextDocuments,
  isPanelVisible,
  setIsPanelVisible,
  selectedProduct,
  isCartView,
  cartData,
  focusedMessageId,
  newDocuments,
  viewedDocumentIds,
  contextPanelRef,
  contextPanelEndRef,
  isItemAttached,
  onOpenProductDetails,
  onCloseCart,
  onCloseProductDetails,
  onRemoveFromCart,
  onProceedToCheckout,
  onAttachCartToChat,
  onBrowseProducts,
  onAddToCart,
  onAttachProductToChat,
  onAttachDocument,
}: {
  contextDocuments: Document[];
  isPanelVisible: boolean;
  setIsPanelVisible: (visible: boolean) => void;
  selectedProduct: Document | null;
  isCartView: boolean;
  cartData: any;
  focusedMessageId: string | null;
  newDocuments: Document[];
  viewedDocumentIds: Set<string>;
  contextPanelRef: RefObject<HTMLDivElement>;
  contextPanelEndRef: RefObject<HTMLDivElement>;
  isItemAttached: (itemId: string) => boolean;
  onOpenProductDetails: (doc: Document) => void;
  onCloseCart: () => void;
  onCloseProductDetails: () => void;
  onRemoveFromCart: (sku: string) => void;
  onProceedToCheckout: () => void;
  onAttachCartToChat: () => void;
  onBrowseProducts: () => void;
  onAddToCart: (product: Document) => void;
  onAttachProductToChat: (product: Document) => void;
  onAttachDocument: (doc: Document) => void;
}) {
  return (
    <>
      <AnimatePresence>
        {contextDocuments.length > 0 && isPanelVisible && (
          <motion.div
            initial={{ opacity: 0, x: 420 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0, x: 420 }}
            transition={{ type: "spring", damping: 25, stiffness: 200 }}
            className={`hidden md:flex absolute top-[165px] right-0 bottom-0 mr-2 ${
              selectedProduct || isCartView ? "w-[700px] max-w-[700px]" : "w-[420px] max-w-[420px]"
            } border-l-2 border-blue-500/30 bg-gradient-to-b from-blue-50/95 to-white/95 dark:from-gray-900/95 dark:via-blue-900/20 dark:to-gray-900/95 backdrop-blur-xl p-6 shadow-2xl z-10 flex-col transition-all duration-300`}
          >
            <div className="bg-gradient-to-br from-blue-600 to-blue-500 backdrop-blur-md p-5 rounded-2xl mb-6 shadow-2xl border-2 border-white/20">
              <div className="flex items-center justify-between mb-3">
                <div className="flex items-center gap-3">
                  <motion.div
                    animate={{ rotate: [0, 360] }}
                    transition={{ duration: 8, repeat: Infinity, ease: "linear" }}
                    className="p-2 bg-white/20 rounded-lg backdrop-blur-sm"
                  >
                    <Sparkles className="h-5 w-5 text-white" />
                  </motion.div>
                  <div>
                    <h2 className="font-bold text-lg text-white">
                      {isCartView ? "Shopping Cart" : selectedProduct ? "Product Details" : "Context Panel"}
                    </h2>
                    <p className="text-xs text-white/80">
                      {isCartView
                        ? "View and manage your cart"
                        : selectedProduct
                          ? "View details and add to cart"
                          : `${contextDocuments.length} ${
                              contextDocuments.length === 1 ? "document" : "documents"
                            } • Click to view`}
                    </p>
                  </div>
                </div>
                <div className="flex gap-2">
                  {(selectedProduct || isCartView) && (
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={isCartView ? onCloseCart : onCloseProductDetails}
                      className="h-8 px-3 text-xs text-white hover:bg-white/20 border border-white/30"
                    >
                      <ArrowRight className="h-3 w-3 mr-1 rotate-180" />
                      Back
                    </Button>
                  )}
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => setIsPanelVisible(false)}
                    className="h-8 px-3 text-xs text-white hover:bg-white/20 border border-white/30"
                  >
                    <EyeOff className="h-3 w-3 mr-1" />
                    Hide
                  </Button>
                </div>
              </div>
              <div className="h-1 bg-gradient-to-r from-yellow-300 via-pink-300 to-purple-300 rounded-full" />
            </div>

            {isCartView ? (
              <CartView
                cartData={cartData}
                onRemoveFromCart={onRemoveFromCart}
                onProceedToCheckout={onProceedToCheckout}
                onAttachCartToChat={onAttachCartToChat}
                onBrowseProducts={onBrowseProducts}
              />
            ) : selectedProduct ? (
              <ProductDetailsView selectedProduct={selectedProduct} onAddToCart={onAddToCart} onAttachProductToChat={onAttachProductToChat} />
            ) : (
              <DocumentsView
                contextDocuments={contextDocuments}
                focusedMessageId={focusedMessageId}
                newDocuments={newDocuments}
                viewedDocumentIds={viewedDocumentIds}
                contextPanelRef={contextPanelRef}
                contextPanelEndRef={contextPanelEndRef}
                isItemAttached={isItemAttached}
                onOpenProductDetails={onOpenProductDetails}
                onAttachDocument={onAttachDocument}
              />
            )}
          </motion.div>
        )}
      </AnimatePresence>

      <AnimatePresence>
        {contextDocuments.length > 0 && !isPanelVisible && (
          <motion.div
            initial={{ opacity: 0, scale: 0.8, x: 100 }}
            animate={{ opacity: 1, scale: 1, x: 0 }}
            exit={{ opacity: 0, scale: 0.8, x: 100 }}
            transition={{ type: "spring", damping: 20 }}
            className="hidden md:block absolute top-16 right-4 z-20"
          >
            <Button
              onClick={() => setIsPanelVisible(true)}
              size="lg"
              className="bg-gradient-to-br from-blue-600 to-blue-500 hover:from-blue-700 hover:to-blue-600 text-white shadow-2xl border-2 border-white/30 rounded-full px-6"
            >
              <Eye className="h-5 w-5 mr-2" />
              Show Panel
            </Button>
          </motion.div>
        )}
      </AnimatePresence>
    </>
  );
}
