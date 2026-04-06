import { Button } from "@/ui/button";
import { Card, CardContent } from "@/ui/card";
import { CheckCircle2, Paperclip, Search, Sparkles, Star } from "lucide-react";

import { formatFieldName, formatFieldValue } from "@/utils";

export const ActionResultRenderer = ({
  data,
  messageId,
  expandedCount,
  onExpand,
  onAttach,
  isAttached,
}: {
  data: any;
  messageId: string;
  expandedCount: number;
  onExpand: (count: number) => void;
  onAttach?: (item: any) => void;
  isAttached?: (itemId: string) => boolean;
}) => {
  if (!data) return null;

  // Check if item looks like a product (has name/title and price or imageUrl) - handle different casing
  const isProductLike = (item: any) => {
    if (typeof item !== "object" || item === null) return false;
    const hasName = item.name || item.Name || item.title || item.Title;
    const hasPrice = item.price !== undefined || item.Price !== undefined;
    const hasImage = item.imageUrl || item.ImageUrl || item.image || item.Image;
    const hasSku = item.sku || item.Sku;
    return hasName && (hasPrice || hasImage || hasSku);
  };

  // Render a product card with image
  const renderProductCard = (item: any, idx: number) => {
    // Handle different field casing from API (Name vs name, ImageUrl vs imageUrl, etc.)
    const name = item.name || item.Name || item.title || item.Title || "Product";
    const price = item.price ?? item.Price;
    const imageUrl = item.imageUrl || item.ImageUrl || item.image || item.Image;
    const category = item.category || item.Category;
    const sku = item.sku || item.Sku;
    const stockQty = item.inStockQty ?? item.InStockQty ?? item.stockQuantity ?? item.StockQuantity;
    const rating = item.rating ?? item.Rating;
    const brand = item.brand || item.Brand;
    const itemId = item.id || item.Id || sku;
    const isItemAlreadyAttached = isAttached && itemId ? isAttached(itemId) : false;

    return (
      <div
        key={itemId || idx}
        className="relative group bg-white dark:bg-gray-800 rounded-2xl shadow-lg border-2 border-blue-200 dark:border-blue-700 hover:border-blue-400 dark:hover:border-blue-500 transition-all hover:shadow-xl overflow-hidden"
      >
        {onAttach && (
          <Button
            size="icon"
            variant="ghost"
            className={`absolute top-2 right-2 z-10 h-8 w-8 ${
              isItemAlreadyAttached
                ? "bg-gradient-to-br from-green-500 to-emerald-500 hover:from-green-600 hover:to-emerald-600"
                : "bg-gradient-to-br from-blue-600 to-blue-500 hover:from-blue-700 hover:to-blue-600"
            } text-white shadow-lg border border-white/30 hover:scale-110 transition-all`}
            onClick={(e) => {
              e.stopPropagation();
              onAttach(item);
            }}
            title={isItemAlreadyAttached ? "Already in Chat" : "Attach to Chat"}
          >
            {isItemAlreadyAttached ? <CheckCircle2 className="h-4 w-4" /> : <Paperclip className="h-4 w-4" />}
          </Button>
        )}

        {imageUrl && (
          <div className="aspect-square w-full overflow-hidden bg-gradient-to-br from-gray-100 to-gray-200 dark:from-gray-700 dark:to-gray-800 relative">
            <img
              src={imageUrl}
              alt={name}
              className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
              onError={(e) => {
                (e.target as HTMLImageElement).style.display = "none";
              }}
            />
          </div>
        )}

        <div className="p-2.5">
          <div className="flex items-center gap-1.5 mb-0.5">
            {brand && (
              <span className="text-[9px] font-bold text-blue-600 dark:text-blue-400 uppercase tracking-wide">
                {brand}
              </span>
            )}
            {category && <span className="text-[9px] text-gray-500 dark:text-gray-400">{category}</span>}
          </div>

          <h4 className="font-bold text-xs text-gray-800 dark:text-gray-100 line-clamp-2 mb-1.5">{name}</h4>

          <div className="flex items-center justify-between">
            {price !== undefined && (
              <span className="text-base font-bold bg-gradient-to-r from-green-600 to-emerald-600 bg-clip-text text-transparent">
                ${typeof price === "number" ? price.toLocaleString() : price}
              </span>
            )}
            {stockQty !== undefined && (
              <span
                className={`text-[9px] font-semibold px-1.5 py-0.5 rounded-full ${
                  stockQty > 50
                    ? "bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400"
                    : stockQty > 0
                      ? "bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400"
                      : "bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400"
                }`}
              >
                {stockQty > 0 ? `${stockQty}` : "Out"}
              </span>
            )}
          </div>

          <div className="flex items-center justify-between mt-1.5 pt-1.5 border-t border-gray-100 dark:border-gray-700">
            {rating !== undefined && (
              <div className="flex items-center gap-0.5">
                <Star className="h-2.5 w-2.5 fill-yellow-400 text-yellow-400" />
                <span className="text-[10px] font-medium text-gray-600 dark:text-gray-400">{rating}</span>
              </div>
            )}
            {sku && <span className="text-[9px] text-gray-400 dark:text-gray-500 font-mono truncate max-w-[80px]">{sku}</span>}
          </div>
        </div>
      </div>
    );
  };

  // Render generic item card (non-product)
  const renderGenericCard = (item: any, idx: number) => {
    const itemId = item.id || item.Id || item.sku || item.Sku;
    const isItemAlreadyAttached = isAttached && itemId ? isAttached(itemId) : false;

    return (
      <Card
        key={itemId || idx}
        className="text-sm bg-white/60 dark:bg-gray-800/60 backdrop-blur-sm border-2 border-blue-200 hover:border-blue-400 transition-colors relative group"
        style={{
          fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
        }}
      >
        {onAttach && typeof item === "object" && item !== null && (
          <Button
            size="icon"
            variant="ghost"
            className={`absolute top-2 right-2 h-8 w-8 ${
              isItemAlreadyAttached
                ? "bg-gradient-to-br from-green-500 to-emerald-500 hover:from-green-600 hover:to-emerald-600"
                : "bg-gradient-to-br from-blue-600 to-blue-500 hover:from-blue-700 hover:to-blue-600"
            } text-white shadow-lg border border-white/30 hover:scale-110 transition-all z-10`}
            onClick={(e) => {
              e.stopPropagation();
              onAttach(item);
            }}
            title={isItemAlreadyAttached ? "Already in Chat" : "Attach to Chat"}
          >
            {isItemAlreadyAttached ? <CheckCircle2 className="h-4 w-4" /> : <Paperclip className="h-4 w-4" />}
          </Button>
        )}
        <CardContent className="p-3 pr-12">
          {typeof item === "object" && item !== null ? (
            <div className="space-y-2">
              {Object.entries(item)
                .filter(([key]) => !["imageUrl", "image", "images"].includes(key))
                .map(([key, value]) => (
                  <div key={key} className="flex items-start justify-between gap-2">
                    <span className="text-muted-foreground font-semibold min-w-[100px]">{formatFieldName(key)}:</span>
                    <span className="text-foreground text-right flex-1 font-medium">
                      {typeof value === "object" && value !== null && !Array.isArray(value) ? (
                        <div className="space-y-1">
                          {Object.entries(value).map(([nestedKey, nestedValue]) => (
                            <div key={nestedKey} className="text-[10px]">
                              <span className="text-muted-foreground">{formatFieldName(nestedKey)}: </span>
                              <span>{formatFieldValue(nestedValue)}</span>
                            </div>
                          ))}
                        </div>
                      ) : (
                        formatFieldValue(value)
                      )}
                    </span>
                  </div>
                ))}
            </div>
          ) : (
            <p className="text-foreground">{formatFieldValue(item)}</p>
          )}
        </CardContent>
      </Card>
    );
  };

  // Handle arrays
  if (Array.isArray(data)) {
    const visibleItems = data.slice(0, expandedCount || 6);
    const remaining = data.length - visibleItems.length;
    const hasProducts = visibleItems.some(isProductLike);

    return (
      <div className="mt-3">
        <div className="flex items-center gap-2 mb-3 pb-2 border-b-2 border-blue-200 dark:border-blue-800">
          <div className="flex items-center gap-2 px-3 py-1.5 bg-gradient-to-r from-blue-500/10 to-purple-500/10 rounded-full border border-blue-300/50 dark:border-blue-700/50">
            <Search className="h-3.5 w-3.5 text-blue-600 dark:text-blue-400" />
            <span className="text-xs font-bold text-blue-700 dark:text-blue-300">Items</span>
          </div>
          <div className="flex items-center gap-1 text-[10px] text-gray-500 dark:text-gray-400">
            <Sparkles className="h-3 w-3" />
            <span className="font-medium">{data.length} results found</span>
          </div>
        </div>
        {hasProducts ? (
          <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
            {visibleItems.map((item: any, idx: number) =>
              isProductLike(item) ? renderProductCard(item, idx) : renderGenericCard(item, idx)
            )}
          </div>
        ) : (
          <div className="space-y-2">
            {visibleItems.map((item: any, idx: number) => renderGenericCard(item, idx))}
          </div>
        )}
        {remaining > 0 && (
          <Button
            size="sm"
            variant="ghost"
            className="w-full mt-3 text-xs bg-gradient-to-r from-blue-500/10 to-blue-400/10 hover:from-blue-500/20 hover:to-blue-400/20 border border-blue-300"
            onClick={() => onExpand((expandedCount || 6) + 6)}
          >
            Show {Math.min(6, remaining)} more
          </Button>
        )}
      </div>
    );
  }

  // Handle objects
  if (typeof data === "object" && data !== null) {
    // Check if object has array-like properties
    const arrayKeys = Object.keys(data).filter((key) => Array.isArray(data[key]));

    if (arrayKeys.length > 0) {
      return (
        <div className="mt-3 space-y-3">
          {arrayKeys.map((arrayKey) => {
            const arrayData = data[arrayKey];
            const visibleItems = arrayData.slice(0, expandedCount || 6);
            const remaining = arrayData.length - visibleItems.length;
            const hasProducts = visibleItems.some(isProductLike);

            return (
              <div key={arrayKey}>
                <div className="flex items-center gap-2 mb-3 pb-2 border-b-2 border-blue-200 dark:border-blue-800">
                  <div className="flex items-center gap-2 px-3 py-1.5 bg-gradient-to-r from-blue-500/10 to-purple-500/10 rounded-full border border-blue-300/50 dark:border-blue-700/50">
                    <Search className="h-3.5 w-3.5 text-blue-600 dark:text-blue-400" />
                    <span className="text-xs font-bold text-blue-700 dark:text-blue-300">{formatFieldName(arrayKey)}</span>
                  </div>
                  <div className="flex items-center gap-1 text-[10px] text-gray-500 dark:text-gray-400">
                    <Sparkles className="h-3 w-3" />
                    <span className="font-medium">{arrayData.length} results found</span>
                  </div>
                </div>
                {hasProducts ? (
                  <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                    {visibleItems.map((item: any, idx: number) =>
                      isProductLike(item) ? renderProductCard(item, idx) : renderGenericCard(item, idx)
                    )}
                  </div>
                ) : (
                  <div className="space-y-2">
                    {visibleItems.map((item: any, idx: number) => renderGenericCard(item, idx))}
                  </div>
                )}
                {remaining > 0 && (
                  <Button
                    size="sm"
                    variant="ghost"
                    className="w-full mt-3 text-xs bg-gradient-to-r from-blue-500/10 to-blue-400/10 hover:from-blue-500/20 hover:to-blue-400/20 border border-blue-300"
                    onClick={() => onExpand((expandedCount || 6) + 6)}
                  >
                    Show {Math.min(6, remaining)} more
                  </Button>
                )}
              </div>
            );
          })}
          {Object.entries(data)
            .filter(([key]) => !Array.isArray(data[key]))
            .map(([key, value]) => (
              <div key={key} className="text-xs">
                <span className="text-muted-foreground font-semibold">{formatFieldName(key)}: </span>
                <span className="text-foreground font-medium">{formatFieldValue(value)}</span>
              </div>
            ))}
        </div>
      );
    }

    return (
      <div className="mt-3">
        <Card
          className="text-sm bg-white/60 dark:bg-gray-800/60 backdrop-blur-sm border-2 border-blue-200"
          style={{
            fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
          }}
        >
          <CardContent className="p-3">
            <div className="space-y-2">
              {Object.entries(data).map(([key, value]) => (
                <div key={key} className="flex items-start justify-between gap-2">
                  <span className="text-muted-foreground font-semibold min-w-[120px]">{formatFieldName(key)}:</span>
                  <span className="text-foreground text-right flex-1 font-medium">{formatFieldValue(value)}</span>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      </div>
    );
  }

  return <p className="mt-3 text-xs text-muted-foreground">{formatFieldValue(data)}</p>;
};

