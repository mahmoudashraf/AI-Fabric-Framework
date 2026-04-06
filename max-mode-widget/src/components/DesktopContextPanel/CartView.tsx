import { BrainCircuit, ShoppingBag, ShoppingCart, Tag, X } from "lucide-react";

import { Button } from "@/ui/button";
import { Card, CardContent } from "@/ui/card";

export function CartView({
  cartData,
  onRemoveFromCart,
  onProceedToCheckout,
  onAttachCartToChat,
  onBrowseProducts,
}: {
  cartData: any;
  onRemoveFromCart: (sku: string) => void;
  onProceedToCheckout: () => void;
  onAttachCartToChat: () => void;
  onBrowseProducts: () => void;
}) {
  return (
    <div
      className="flex-1 overflow-y-auto space-y-4"
      style={{
        scrollbarWidth: "thin",
        scrollbarColor: "rgba(168, 85, 247, 0.5) rgba(243, 232, 255, 0.2)",
      }}
    >
      {cartData && cartData.items && cartData.items.length > 0 ? (
        <>
          <div className="space-y-3">
            {cartData.items.map((item: any, idx: number) => (
              <Card key={idx} className="border-2 border-blue-200 bg-white/60">
                <CardContent className="p-4">
                  <div className="flex items-start gap-3">
                    <div className="flex-1">
                      <h4 className="font-semibold text-gray-900 mb-1">{item.productName || item.sku}</h4>
                      <p className="text-sm text-gray-600 mb-2">SKU: {item.sku}</p>
                      <div className="flex items-center justify-between">
                        <span className="text-lg font-bold text-gray-900">${item.price}</span>
                        <span className="text-sm text-gray-500">Qty: {item.quantity}</span>
                      </div>
                      <div className="mt-2 text-sm font-semibold text-gray-900">
                        Subtotal: ${(item.price * item.quantity).toFixed(2)}
                      </div>
                    </div>
                    <Button
                      size="icon"
                      variant="ghost"
                      onClick={() => onRemoveFromCart(item.sku)}
                      className="h-8 w-8 text-red-600 hover:bg-red-50 hover:text-red-700"
                    >
                      <X className="h-4 w-4" />
                    </Button>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>

          <Card className="border-2 border-blue-300 bg-gradient-to-br from-blue-50 to-white">
            <CardContent className="p-4 space-y-2">
              <div className="flex justify-between text-sm">
                <span className="text-gray-600">Subtotal:</span>
                <span className="font-semibold">${cartData.subtotal?.toFixed(2) || "0.00"}</span>
              </div>
              {cartData.discount > 0 && (
                <div className="flex justify-between text-sm">
                  <span className="text-gray-600">Discount:</span>
                  <span className="font-semibold text-green-600">-${cartData.discount?.toFixed(2)}</span>
                </div>
              )}
              <div className="border-t border-blue-200 pt-2 flex justify-between">
                <span className="text-lg font-bold text-gray-900">Total:</span>
                <span className="text-lg font-bold text-gray-900">${cartData.total?.toFixed(2) || "0.00"}</span>
              </div>
              {cartData.couponCode && (
                <div className="text-xs text-gray-500 flex items-center gap-1">
                  <Tag className="h-3 w-3" />
                  Coupon: {cartData.couponCode}
                </div>
              )}
            </CardContent>
          </Card>

          <div className="space-y-2">
            <Button
              onClick={onProceedToCheckout}
              className="w-full bg-gradient-to-r from-green-600 to-emerald-600 hover:from-green-700 hover:to-emerald-700 text-white shadow-lg"
              size="lg"
            >
              <ShoppingBag className="h-5 w-5 mr-2" />
              Proceed to Checkout
            </Button>
            <Button
              onClick={onAttachCartToChat}
              variant="outline"
              className="w-full border-blue-300 text-blue-600 hover:bg-blue-50"
              size="lg"
            >
              <BrainCircuit className="h-5 w-5 mr-2" />
              Attach Cart to Chat
            </Button>
          </div>
        </>
      ) : (
        <div className="flex flex-col items-center justify-center h-full text-center py-12">
          <ShoppingCart className="h-16 w-16 text-gray-300 mb-4" />
          <h3 className="text-xl font-semibold text-gray-900 mb-2">Your cart is empty</h3>
          <p className="text-sm text-gray-500 mb-4">Add some products to get started!</p>
          <Button
            onClick={onBrowseProducts}
            className="bg-gradient-to-r from-blue-600 to-blue-500 hover:from-blue-700 hover:to-blue-600 text-white"
          >
            Browse Products
          </Button>
        </div>
      )}
    </div>
  );
}

