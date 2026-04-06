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
  onAddToCart: (product: Document) => void;
  onAttachProductToChat: (product: Document) => void;
}) {
  return (
    <div
      className="flex-1 overflow-y-auto space-y-6"
      style={{
        scrollbarWidth: "thin",
        scrollbarColor: "rgba(168, 85, 247, 0.5) rgba(243, 232, 255, 0.2)",
      }}
    >
      {selectedProduct.metadata?.imageUrl && (
        <div className="relative h-80 overflow-hidden rounded-2xl bg-gradient-to-br from-blue-100 to-white">
          <img src={selectedProduct.metadata.imageUrl} alt={selectedProduct.title} className="w-full h-full object-cover" />
          <div className="absolute inset-0 bg-gradient-to-t from-black/50 to-transparent" />
        </div>
      )}

      <div className="space-y-4">
        <div>
          <h3 className="text-2xl font-bold bg-gradient-to-r from-blue-600 to-blue-500 bg-clip-text text-transparent mb-2">
            {selectedProduct.title}
          </h3>
          <Badge variant="outline" className="text-xs bg-blue-100 border-blue-300 text-blue-700">
            {selectedProduct.type}
          </Badge>
        </div>

        <div className="p-4 bg-white/60 rounded-xl border-2 border-purple-200">
          <p className="text-sm text-gray-700 leading-relaxed">{selectedProduct.content}</p>
        </div>

        {selectedProduct.metadata && (
          <div className="space-y-3">
            <h4 className="font-semibold text-gray-900">Product Details</h4>
            <div className="grid grid-cols-2 gap-3">
              {Object.entries(selectedProduct.metadata).map(([key, value]) => {
                if (key === "imageUrl") return null;
                return (
                  <div key={key} className="p-3 bg-white/60 rounded-lg border border-purple-200">
                    <p className="text-xs text-gray-500 mb-1">{formatFieldName(key)}</p>
                    <p className="text-sm font-semibold text-gray-900">{formatFieldValue(value)}</p>
                  </div>
                );
              })}
            </div>
          </div>
        )}

        <div className="sticky bottom-0 bg-gradient-to-t from-blue-50 via-blue-50/50 to-transparent pt-6 pb-2 space-y-3">
          <Button
            onClick={() => onAddToCart(selectedProduct)}
            className="w-full bg-gradient-to-r from-green-600 to-emerald-600 hover:from-green-700 hover:to-emerald-700 text-white shadow-lg"
            size="lg"
          >
            <ShoppingCart className="h-5 w-5 mr-2" />
            Add to Cart
          </Button>
          <Button
            onClick={() => onAttachProductToChat(selectedProduct)}
            variant="outline"
            className="w-full border-blue-300 text-blue-600 hover:bg-blue-50"
            size="lg"
          >
            <BrainCircuit className="h-5 w-5 mr-2" />
            Attach to Chat
          </Button>
        </div>
      </div>
    </div>
  );
}

