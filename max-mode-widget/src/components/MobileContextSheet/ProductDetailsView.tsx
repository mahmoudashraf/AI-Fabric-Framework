import { BrainCircuit, ShoppingCart } from "lucide-react";

import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";

import type { Document } from "@/types";
import { formatFieldName, formatFieldValue } from "@/utils";

export function ProductDetailsView({
  selectedProduct,
  onAddToCart,
  onAttachProductToChat,
}: {
  selectedProduct: Document;
  onAddToCart: () => void;
  onAttachProductToChat: () => void;
}) {
  return (
    <div className="space-y-4">
      {selectedProduct.metadata?.imageUrl && (
        <div className="relative h-64 overflow-hidden rounded-xl bg-gradient-to-br from-blue-100 to-white -mx-4">
          <img src={selectedProduct.metadata.imageUrl} alt={selectedProduct.title} className="w-full h-full object-cover" />
        </div>
      )}

      <div>
        <h3 className="text-xl font-bold bg-gradient-to-r from-blue-600 to-blue-500 bg-clip-text text-transparent mb-2">
          {selectedProduct.title}
        </h3>
        <Badge variant="outline" className="text-xs bg-blue-100 border-blue-300 text-blue-700">
          {selectedProduct.type}
        </Badge>
      </div>

      <div className="p-3 bg-white/80 rounded-lg border-2 border-blue-200">
        <p className="text-sm text-gray-700 leading-relaxed">{selectedProduct.content}</p>
      </div>

      {selectedProduct.metadata && (
        <div className="space-y-3">
          <h4 className="font-semibold text-gray-900 text-sm">Product Details</h4>
          <div className="space-y-2">
            {Object.entries(selectedProduct.metadata).map(([key, value]) => {
              if (key === "imageUrl") return null;
              return (
                <div key={key} className="p-3 bg-white/80 rounded-lg border border-blue-200">
                  <p className="text-xs text-gray-500 mb-0.5">{formatFieldName(key)}</p>
                  <p className="text-sm font-semibold text-gray-900">{formatFieldValue(value)}</p>
                </div>
              );
            })}
          </div>
        </div>
      )}

      <div className="sticky bottom-0 bg-gradient-to-t from-white via-white to-transparent pt-4 pb-2 space-y-2 -mx-4 px-4">
        <Button
          onClick={onAddToCart}
          className="w-full bg-gradient-to-r from-green-600 to-emerald-600 hover:from-green-700 hover:to-emerald-700 text-white shadow-lg"
          size="lg"
        >
          <ShoppingCart className="h-5 w-5 mr-2" />
          Add to Cart
        </Button>
        <Button
          onClick={onAttachProductToChat}
          variant="outline"
          className="w-full border-blue-300 text-blue-600 hover:bg-blue-50"
        >
          <BrainCircuit className="h-5 w-5 mr-2" />
          Attach to Chat
        </Button>
      </div>
    </div>
  );
}

