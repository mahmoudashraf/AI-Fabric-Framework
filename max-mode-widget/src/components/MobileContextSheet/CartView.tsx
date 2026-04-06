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
    <div className="space-y-3">
      {cartData && cartData.items && cartData.items.length > 0 ? (
        <>
          {cartData.items.map((item: any, idx: number) => (
            <Card key={idx} className="border-2 border-purple-200 bg-white/80">
              <CardContent className="p-3">
                <div className="flex items-start gap-2">
                  <div className="flex-1">
                    <h4 className="font-semibold text-sm text-gray-900 mb-1">{item.productName || item.sku}</h4>
                    <p className="text-xs text-gray-600 mb-1">SKU: {item.sku}</p>
                    <div className="flex items-center justify-between">
                      <span className="text-base font-bold text-purple-600">${item.price}</span>
                      <span className="text-xs text-gray-500">Qty: {item.quantity}</span>
                    </div>
                    <div className="mt-1 text-xs font-semibold text-gray-900">
                      Subtotal: ${(item.price * item.quantity).toFixed(2)}
                    </div>
                  </div>
                  <Button
                    size="icon"
                    variant="ghost"
                    onClick={() => onRemoveFromCart(item.sku)}
                    className="h-7 w-7 text-red-600 hover:bg-red-50 hover:text-red-700"
                    aria-label="Remove from cart"
                  >
                    <X className="h-3 w-3" />
                  </Button>
                </div>
              </CardContent>
            </Card>
          ))}

          <Card className="border-2 border-blue-300 bg-gradient-to-br from-blue-50 to-white">
            <CardContent className="p-3 space-y-2">
              <div className="flex justify-between text-xs">
                <span className="text-gray-600">Subtotal:</span>
                <span className="font-semibold">${cartData.subtotal?.toFixed(2) || "0.00"}</span>
              </div>
              {cartData.discount > 0 && (
                <div className="flex justify-between text-xs">
                  <span className="text-gray-600">Discount:</span>
                  <span className="font-semibold text-green-600">-${cartData.discount?.toFixed(2)}</span>
                </div>
              )}
              <div className="border-t border-blue-200 pt-2 flex justify-between">
                <span className="text-base font-bold text-gray-900">Total:</span>
                <span className="text-base font-bold text-gray-900">${cartData.total?.toFixed(2) || "0.00"}</span>
              </div>
              {cartData.couponCode && (
                <div className="text-[10px] text-gray-500 flex items-center gap-1">
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
            >
              <BrainCircuit className="h-5 w-5 mr-2" />
              Attach Cart to Chat
            </Button>
          </div>
        </>
      ) : (
        <div className="flex flex-col items-center justify-center text-center py-12">
          <ShoppingCart className="h-12 w-12 text-gray-300 mb-3" />
          <h3 className="text-base font-semibold text-gray-900 mb-1">Your cart is empty</h3>
          <p className="text-xs text-gray-500 mb-3">Add some products to get started!</p>
          <Button
            onClick={onBrowseProducts}
            className="bg-gradient-to-r from-blue-600 to-blue-500 hover:from-blue-700 hover:to-blue-600 text-white"
            size="sm"
          >
            Browse Products
          </Button>
        </div>
      )}
    </div>
  );
}

