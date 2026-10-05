import type { ReactNode } from "react";

import { Button } from "@/ui/button";
import { Card, CardContent } from "@/ui/card";
import { CheckCircle2, ExternalLink, Paperclip, Search, Sparkles, Star } from "lucide-react";

import { formatFieldName, formatFieldValue } from "@/utils";
import { normalizeShopifyMcpCatalogResult } from "@/shopifyMcpResults";
import {
  isActionRendererAvailable,
  resolveActionPresentation,
  type MaxModePresentationResultReference,
} from "@/actionPresentation";
import { getWidgetConfig } from "@/config";
import { ActionPresentationSurface } from "./ActionPresentationSurface";

type ResultRecord = Record<string, any>;

const PRIMARY_ARRAY_KEYS = ["items", "products", "results"];
const SECONDARY_ARRAY_KEYS = ["documents", "policies"];
const SUMMARY_FIELDS = new Set(["query", "count", "totalResults", "returnedResults"]);
const ACTION_RESULT_ENVELOPE_FIELDS = new Set(["message", "success", "error", "errorCode"]);
const SENSITIVE_FIELD_PATTERN = /(authorization|cookie|credential|password|private|secret|token|api.?key)/i;
const MAX_GENERIC_FIELDS = 24;
const MAX_GENERIC_ARRAY_ITEMS = 20;

const isRecord = (value: unknown): value is ResultRecord =>
  typeof value === "object" && value !== null && !Array.isArray(value);

const isProductLike = (item: any) => {
  if (!isRecord(item)) return false;
  const hasName = item.name || item.Name || item.title || item.Title;
  const hasCommerceSignal =
    item.price !== undefined ||
    item.Price !== undefined ||
    item.imageUrl ||
    item.ImageUrl ||
    item.image ||
    item.Image ||
    item.sku ||
    item.Sku ||
    item.primarySku ||
    item.PrimarySku ||
    item.vendor ||
    item.Vendor ||
    item.productType ||
    item.ProductType ||
    item.storefrontUrl ||
    item.StorefrontUrl ||
    item.available !== undefined ||
    item.Available !== undefined ||
    item.inventoryQuantity !== undefined ||
    item.InventoryQuantity !== undefined;
  return Boolean(hasName && hasCommerceSignal);
};

const hasRenderableArray = (value: ResultRecord) =>
  Object.values(value).some((entry) => Array.isArray(entry));

const flattenActionResultEnvelope = (value: ResultRecord): ResultRecord | null => {
  if (!isRecord(value.data)) return null;
  const siblingKeys = Object.keys(value).filter((key) => key !== "data");
  if (!siblingKeys.every((key) => ACTION_RESULT_ENVELOPE_FIELDS.has(key))) return null;

  const flattened = { ...value.data };
  for (const key of siblingKeys) {
    if (flattened[key] === undefined) flattened[key] = value[key];
  }
  return flattened;
};

const unwrapActionResultData = (value: any): any => {
  if (!isRecord(value)) return value;

  const shopifyMcpCatalogResult = normalizeShopifyMcpCatalogResult(value);
  if (shopifyMcpCatalogResult) {
    return shopifyMcpCatalogResult;
  }

  if (isRecord(value.actionResult)) {
    return unwrapActionResultData(value.actionResult.data);
  }

  if (isRecord(value.data)) {
    if (hasRenderableArray(value.data)) {
      return value.data;
    }

    const flattened = flattenActionResultEnvelope(value);
    if (flattened) {
      return flattened;
    }

    if (isRecord(value.data.data) && hasRenderableArray(value.data.data)) {
      return value.data.data;
    }
  }

  return value;
};

const getDisplayArrayKeys = (record: ResultRecord) => {
  const arrayKeys = Object.keys(record).filter((key) => Array.isArray(record[key]));
  const primaryKeys = arrayKeys.filter((key) => PRIMARY_ARRAY_KEYS.includes(key));
  if (primaryKeys.length > 0) {
    return primaryKeys;
  }
  const secondaryKeys = arrayKeys.filter((key) => SECONDARY_ARRAY_KEYS.includes(key));
  if (secondaryKeys.length > 0) {
    return secondaryKeys;
  }
  return arrayKeys.filter((key) => record[key].some((item: any) => isProductLike(item) || isRecord(item)));
};

const getSummaryEntries = (record: ResultRecord, excludedKeys: string[] = []) =>
  Object.entries(record).filter(([key, value]) => {
    if (excludedKeys.includes(key) || !SUMMARY_FIELDS.has(key)) return false;
    return value === null || ["string", "number", "boolean"].includes(typeof value);
  });

const formatPrice = (price: any) => {
  if (typeof price === "number") {
    return `$${price.toLocaleString()}`;
  }
  const raw = String(price);
  return /^[^\d-]/.test(raw) ? raw : `$${raw}`;
};

const visibleRecordEntries = (record: ResultRecord) =>
  Object.entries(record)
    .filter(([key, value]) => value !== undefined && !SENSITIVE_FIELD_PATTERN.test(key))
    .slice(0, MAX_GENERIC_FIELDS);

function StructuredResultValue({ value, depth = 0 }: { value: unknown; depth?: number }): ReactNode {
  if (Array.isArray(value)) {
    const visible = value.slice(0, MAX_GENERIC_ARRAY_ITEMS);
    if (visible.length === 0) return <span className="text-muted-foreground">None</span>;
    return (
      <ul className="m-0 min-w-0 space-y-1.5 p-0 list-none">
        {visible.map((item, index) => (
          <li key={index} className="min-w-0 rounded-md border border-gray-200 bg-white/80 px-2.5 py-2 dark:border-gray-700 dark:bg-gray-900/50">
            <StructuredResultValue value={item} depth={depth + 1} />
          </li>
        ))}
        {value.length > visible.length && (
          <li className="text-xs text-muted-foreground">{value.length - visible.length} more values</li>
        )}
      </ul>
    );
  }

  if (isRecord(value)) {
    if (depth >= 3) {
      return <span className="whitespace-pre-wrap break-words [overflow-wrap:anywhere]">{formatFieldValue(value)}</span>;
    }
    return (
      <StructuredResultRecord
        record={value}
        depth={depth + 1}
        className="rounded-md border border-gray-200 bg-white/70 dark:border-gray-700 dark:bg-gray-900/40"
      />
    );
  }

  if (typeof value === "boolean") {
    return (
      <span className={`inline-flex w-fit rounded-full px-2 py-0.5 text-xs font-bold ${value ? "bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300" : "bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-300"}`}>
        {value ? "Yes" : "No"}
      </span>
    );
  }

  return (
    <span className="whitespace-pre-wrap break-words [overflow-wrap:anywhere]">
      {formatFieldValue(value)}
    </span>
  );
}

function StructuredResultRecord({
  record,
  depth = 0,
  className = "",
}: {
  record: ResultRecord;
  depth?: number;
  className?: string;
}) {
  const entries = visibleRecordEntries(record);
  if (entries.length === 0) {
    return <p className="m-0 text-xs text-muted-foreground">No displayable result details.</p>;
  }
  return (
    <dl className={`m-0 min-w-0 divide-y divide-gray-200 overflow-hidden dark:divide-gray-700 ${className}`}>
      {entries.map(([key, value]) => (
        <div
          key={key}
          className="grid min-w-0 grid-cols-1 gap-1 px-3 py-2.5 sm:grid-cols-[minmax(100px,0.35fr)_minmax(0,1fr)] sm:gap-3"
        >
          <dt className="min-w-0 text-[11px] font-bold uppercase text-muted-foreground">
            {formatFieldName(key)}
          </dt>
          <dd className="m-0 min-w-0 text-left text-sm font-medium text-foreground">
            <StructuredResultValue value={value} depth={depth} />
          </dd>
        </div>
      ))}
      {Object.keys(record).length > entries.length && (
        <div className="px-3 py-2 text-xs text-muted-foreground">Additional internal fields are not displayed.</div>
      )}
    </dl>
  );
}

export const ActionResultRenderer = ({
  data,
  messageId,
  expandedCount,
  onExpand,
  onAttach,
  isAttached,
  actionName,
  onPresentationAsk,
  onAttachPresentationResult,
  onDetachPresentationResult,
  isPresentationResultAttached,
  disableCustomPresentation = false,
}: {
  data: any;
  messageId: string;
  expandedCount: number;
  onExpand: (count: number) => void;
  onAttach?: (item: any) => void;
  isAttached?: (itemId: string) => boolean;
  actionName?: string;
  onPresentationAsk?: (query: string, references: readonly MaxModePresentationResultReference[]) => Promise<void> | void;
  onAttachPresentationResult?: (reference: MaxModePresentationResultReference) => void;
  onDetachPresentationResult?: (reference: MaxModePresentationResultReference) => void;
  isPresentationResultAttached?: (referenceKey: string, sourceMessageId: string) => boolean;
  disableCustomPresentation?: boolean;
}) => {
  const displayData = unwrapActionResultData(data);

  if (!displayData) return null;

  if (!disableCustomPresentation && actionName) {
    const presentation = resolveActionPresentation({
      config: getWidgetConfig().host?.actionPresentation,
      actionName,
      actionData: displayData,
      messageId,
    });
    if (
      presentation
      && isActionRendererAvailable(presentation.renderer)
      && onPresentationAsk
      && onAttachPresentationResult
      && onDetachPresentationResult
    ) {
      const selectedResultKeys = presentation.resultReferences
        .filter((reference) => isPresentationResultAttached?.(reference.key, reference.sourceMessageId))
        .map((reference) => reference.key);
      const genericFallback = (
        <ActionResultRenderer
          data={data}
          messageId={messageId}
          expandedCount={expandedCount}
          onExpand={onExpand}
          onAttach={onAttach}
          isAttached={isAttached}
          actionName={actionName}
          disableCustomPresentation
        />
      );
      return (
        <div className="mt-3" data-max-mode-action-presentation={presentation.rendererId}>
          <ActionPresentationSurface
            presentation={presentation}
            selectedResultKeys={selectedResultKeys}
            fallback={genericFallback}
            onAsk={onPresentationAsk}
            onAttachResult={onAttachPresentationResult}
            onDetachResult={onDetachPresentationResult}
          />
        </div>
      );
    }
  }

  // Render a product card with image
  const renderProductCard = (item: any, idx: number) => {
    // Handle different field casing from API (Name vs name, ImageUrl vs imageUrl, etc.)
    const name = item.name || item.Name || item.title || item.Title || "Product";
    const price = item.price ?? item.Price;
    const imageUrl = item.imageUrl || item.ImageUrl || item.image || item.Image;
    const category = item.category || item.Category || item.productType || item.ProductType;
    const sku = item.sku || item.Sku || item.primarySku || item.PrimarySku;
    const stockQty =
      item.inStockQty ??
      item.InStockQty ??
      item.stockQuantity ??
      item.StockQuantity ??
      item.inventoryQuantity ??
      item.InventoryQuantity;
    const rating = item.rating ?? item.Rating;
    const brand = item.brand || item.Brand || item.vendor || item.Vendor;
    const available = item.available ?? item.Available ?? item.availableForSale ?? item.AvailableForSale;
    const storefrontUrl = item.storefrontUrl || item.StorefrontUrl || item.url || item.Url;
    const itemId = item.id || item.Id || sku;
    const isItemAlreadyAttached = isAttached && itemId ? isAttached(itemId) : false;
    const stockLabel =
      available === false
        ? "Unavailable"
        : stockQty !== undefined
          ? stockQty > 0
            ? `${stockQty} in stock`
            : available === true
              ? "Available"
              : "Out"
          : available === true
            ? "Available"
            : undefined;
    const stockClass =
      available === false || stockLabel === "Out"
        ? "bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400"
        : stockQty !== undefined && stockQty <= 50
          ? "bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400"
          : "bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400";

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
                {formatPrice(price)}
              </span>
            )}
            {stockLabel && (
              <span className={`text-[9px] font-semibold px-1.5 py-0.5 rounded-full ${stockClass}`}>
                {stockLabel}
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

          {storefrontUrl && (
            <Button asChild size="sm" variant="outline" className="mt-2 h-7 w-full text-[11px] rounded-lg">
              <a href={storefrontUrl} target="_blank" rel="noreferrer">
                View product
                <ExternalLink className="h-3 w-3" />
              </a>
            </Button>
          )}
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
            <StructuredResultRecord
              record={Object.fromEntries(
                Object.entries(item).filter(([key]) => !["imageUrl", "image", "images"].includes(key)),
              )}
            />
          ) : (
            <p className="min-w-0 text-foreground"><StructuredResultValue value={item} /></p>
          )}
        </CardContent>
      </Card>
    );
  };

  // Handle arrays
  if (Array.isArray(displayData)) {
    const visibleItems = displayData.slice(0, expandedCount || 6);
    const remaining = displayData.length - visibleItems.length;
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
            <span className="font-medium">{displayData.length} results found</span>
          </div>
        </div>
        {hasProducts ? (
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
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
  if (isRecord(displayData)) {
    // Check if object has array-like properties
    const arrayKeys = getDisplayArrayKeys(displayData);

    if (arrayKeys.length > 0) {
      return (
        <div className="mt-3 space-y-3">
          {arrayKeys.map((arrayKey) => {
            const arrayData = displayData[arrayKey];
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
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
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
          {getSummaryEntries(displayData, arrayKeys)
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
          className="min-w-0 overflow-hidden border border-blue-200 bg-white/70 text-sm shadow-sm backdrop-blur-sm dark:border-blue-800 dark:bg-gray-800/70"
          style={{
            fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
          }}
          data-max-mode-generic-action-result
        >
          <CardContent className="min-w-0 p-0">
            <StructuredResultRecord record={displayData} />
          </CardContent>
        </Card>
      </div>
    );
  }

  return <p className="mt-3 text-xs text-muted-foreground">{formatFieldValue(displayData)}</p>;
};
