import type { AttachedItem } from "@/types";

export interface AttachmentItemSource {
  messageId: string;
  actionName?: string;
  collection?: string;
  index: number;
}

function identifier(...values: unknown[]): string | undefined {
  for (const value of values) {
    if (typeof value === "string" && value.trim()) return value;
    if (typeof value === "number" && Number.isFinite(value)) return String(value);
  }
  return undefined;
}

export function attachmentItemKey(data: any, source?: AttachmentItemSource): string | undefined {
  const id = identifier(data?.id, data?.Id, data?.ID);
  const sku = identifier(data?.sku, data?.Sku, data?.SKU);
  if (source) {
    return JSON.stringify(["selected-item", itemType(data), source.actionName ?? source.messageId, source.collection ?? "$",
      ...(id !== undefined ? ["id", id] : sku !== undefined ? ["sku", sku]
        : ["position", source.messageId, source.index])]);
  }
  return undefined;
}

export function sameAttachment(left: AttachedItem, right: AttachedItem): boolean {
  if (left.type !== right.type) return false;
  if (left.selectionKey && right.selectionKey) return left.selectionKey === right.selectionKey;
  const leftId = identifier(left.data?.id, left.data?.Id, left.data?.ID);
  const rightId = identifier(right.data?.id, right.data?.Id, right.data?.ID);
  if (leftId !== undefined && rightId !== undefined) return leftId === rightId;
  const leftSku = identifier(left.data?.sku, left.data?.Sku, left.data?.SKU);
  const rightSku = identifier(right.data?.sku, right.data?.Sku, right.data?.SKU);
  return leftSku !== undefined && rightSku !== undefined ? leftSku === rightSku : left.data === right.data;
}

export function matchesAttachment(item: AttachedItem, key: string): boolean {
  if (!key) return false;
  return item.selectionKey === key
    || identifier(item.data?.id, item.data?.Id, item.data?.ID) === key
    || identifier(item.data?.sku, item.data?.Sku, item.data?.SKU) === key;
}

function itemType(item: any): string {
  const hasProductFields = item.sku || item.Sku || item.price !== undefined || item.Price !== undefined;
  const hasOrderFields = item.orderId || item.OrderId || item.orderNumber || item.OrderNumber;
  const hasReviewFields = item.rating !== undefined && (item.comment || item.Comment);
  const hasCouponFields = item.code || item.Code || item.discountType || item.DiscountType;
  return hasOrderFields ? "order" : hasProductFields ? "product"
    : hasReviewFields ? "review" : hasCouponFields ? "coupon" : "item";
}

export function attachedActionItem(item: any, source?: AttachmentItemSource): AttachedItem {
  return {
    type: itemType(item),
    // Preserve source fields and casing; aliases are presentation conveniences only.
    data: {
      ...item,
      id: item.id ?? item.Id ?? item.ID,
      sku: item.sku ?? item.Sku ?? item.SKU,
      name: item.name ?? item.Name ?? item.title ?? item.Title,
      title: item.title ?? item.Title,
      imageUrl: item.imageUrl ?? item.ImageUrl ?? item.image ?? item.Image,
    },
    selectionKey: attachmentItemKey(item, source),
  };
}
