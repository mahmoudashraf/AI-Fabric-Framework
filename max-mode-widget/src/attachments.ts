import type { AttachedItem } from "@/types";
import type { MaxModePresentationResultReference } from "@/actionPresentation";
import { presentationReferenceAttachmentId } from "@/actionPresentation";
import { CURRENT_PAGE_ATTACHMENT_TYPE } from "@/pageAttachment";

export const ACTION_RESULT_ATTACHMENT_TYPE = "action-result-context";

export interface RuntimeAttachment {
  id: string;
  vectorSpace?: string;
  contentText: string;
  metadata: Record<string, any>;
  source: string;
  url?: string;
  imageUrl?: string;
}

export function toRuntimeAttachments(items: AttachedItem[]): RuntimeAttachment[] {
  return items
    .filter((item) => item.type !== "ai-search")
    .map((item, index) => toRuntimeAttachment(item, index));
}

export function toRuntimeAttachment(item: AttachedItem, index: number): RuntimeAttachment {
  if (item.type === CURRENT_PAGE_ATTACHMENT_TYPE) {
    return currentPageRuntimeAttachment(item, index);
  }
  if (item.type === ACTION_RESULT_ATTACHMENT_TYPE) {
    return actionResultRuntimeAttachment(item, index);
  }

  const data = item.data || {};
  const contentParts: string[] = [];
  if (data.sku) contentParts.push(`SKU: ${data.sku}`);
  if (data.name) contentParts.push(data.name);
  if (data.title) contentParts.push(data.title);
  if (data.description) contentParts.push(data.description);
  if (data.content) contentParts.push(data.content);
  if (data.price) contentParts.push(`Price: ${data.price} ${data.currency || "USD"}`);
  if (data.category) contentParts.push(`Category: ${data.category}`);
  if (data.availability) contentParts.push(`Availability: ${data.availability}`);
  if (data.status) contentParts.push(`Status: ${data.status}`);
  if (data.orderId) contentParts.push(`Order ID: ${data.orderId}`);
  if (data.orderNumber) contentParts.push(`Order #${data.orderNumber}`);

  const sourceMetadata: Record<string, any> = { ...(data.metadata || {}) };
  const explicitVectorSpace = firstString(
    data.vectorSpace,
    data.entityType,
    sourceMetadata.vectorSpace,
    sourceMetadata.entityType,
  );
  let vectorSpace = explicitVectorSpace || "product";
  if (!explicitVectorSpace && item.type === "order") {
    vectorSpace = "order";
  } else if (!explicitVectorSpace && item.type === "document") {
    const docCategory = data.metadata?.category?.toLowerCase();
    vectorSpace = docCategory === "order" ? "order" : "product";
  }

  delete sourceMetadata.productVariantId;
  delete sourceMetadata.firstAvailableVariantId;
  delete sourceMetadata.variantId;

  const metadata = removeUndefinedValues({
    ...sourceMetadata,
    id: data.id,
    sku: data.sku,
    category: data.category || data.type,
    name: data.name,
    title: data.title,
    price: data.price,
    availability: data.availability,
    product_variant_id: data.product_variant_id,
    firstAvailableVariantTitle: data.firstAvailableVariantTitle,
    totalPrice: data.totalPrice,
    quantity: data.quantity,
    status: data.status,
    orderId: data.orderId,
    orderNumber: data.orderNumber,
    productName: data.productName,
    currency: data.currency,
    createdAt: data.createdAt,
    rating: data.rating,
    code: data.code,
    discountType: data.discountType,
    discountValue: data.discountValue,
    score: data.score,
    similarity: data.similarity,
  });

  return removeEmptyOptionalFields({
    id: cleanValue(data.id || data.orderId?.toString() || data.sku || `attachment-${index}`),
    vectorSpace,
    contentText: contentParts.join(" | "),
    metadata,
    source: item.type,
    url: cleanValue(data.url),
    imageUrl: cleanValue(data.imageUrl || data.metadata?.imageUrl),
  });
}

/** Page context informs generation but must not silently change the host's routing mode. */
export function isRoutingTargetAttachment(item: AttachedItem): boolean {
  return item.type !== CURRENT_PAGE_ATTACHMENT_TYPE
    && item.type !== ACTION_RESULT_ATTACHMENT_TYPE
    && item.type !== "ai-search";
}

export function toActionResultAttachedItem(reference: MaxModePresentationResultReference): AttachedItem {
  return {
    type: ACTION_RESULT_ATTACHMENT_TYPE,
    contextLabel: reference.label,
    data: {
      id: presentationReferenceAttachmentId(reference),
      title: reference.label,
      content: summarizeSafeData(reference.safeData),
      metadata: {
        attachmentKind: "ACTION_RESULT_CONTEXT",
        trust: "REQUIRES_SERVER_RESOLUTION",
        actionEligible: false,
        resultReferenceKey: reference.key,
        resultScope: reference.scope,
        sourceMessageId: reference.sourceMessageId,
        sourceActionName: reference.sourceActionName,
        lookupValue: reference.lookupValue,
      },
    },
  };
}

function currentPageRuntimeAttachment(item: AttachedItem, index: number): RuntimeAttachment {
  const data = item.data || {};
  const title = firstString(data.title, data.metadata?.pageTitle) || "Current page";
  const content = firstString(data.content, data.contentText, data.text) || "";
  const metadata = removeUndefinedValues({
    ...(data.metadata || {}),
    attachmentKind: "CURRENT_PAGE",
    pageTitle: title,
  });

  return removeEmptyOptionalFields({
    id: cleanValue(data.id || `current-page-${index}`),
    contentText: `Page title: ${title}\n\n${content}`.trim(),
    metadata,
    source: CURRENT_PAGE_ATTACHMENT_TYPE,
    url: cleanValue(data.url),
  });
}

function actionResultRuntimeAttachment(item: AttachedItem, index: number): RuntimeAttachment {
  const data = item.data || {};
  const title = firstString(data.title) || "Selected result";
  const content = firstString(data.content) || "";
  const metadata = removeUndefinedValues({
    ...(data.metadata || {}),
    attachmentKind: "ACTION_RESULT_CONTEXT",
    trust: "REQUIRES_SERVER_RESOLUTION",
    actionEligible: false,
    title,
  });
  return removeEmptyOptionalFields({
    id: cleanValue(data.id || `action-result-${index}`),
    contentText: `Selected result: ${title}\n\n${content}`.trim(),
    metadata,
    source: ACTION_RESULT_ATTACHMENT_TYPE,
  });
}

function summarizeSafeData(value: Readonly<Record<string, unknown>>): string {
  return Object.entries(value)
    .filter(([key, entry]) => key !== "presentationKey" && isDisplayScalar(entry))
    .slice(0, 24)
    .map(([key, entry]) => `${formatKey(key)}: ${String(entry)}`)
    .join(" | ");
}

function isDisplayScalar(value: unknown): value is string | number | boolean {
  return typeof value === "string" || typeof value === "number" || typeof value === "boolean";
}

function formatKey(value: string): string {
  return value.replace(/([a-z])([A-Z])/g, "$1 $2").replace(/[_-]+/g, " ").trim();
}

function firstString(...values: unknown[]): string | undefined {
  for (const value of values) {
    if (typeof value !== "string") continue;
    const trimmed = value.trim();
    if (trimmed) return trimmed;
  }
  return undefined;
}

function cleanValue(value: unknown): string {
  return typeof value === "string" ? value.replace(/[\[\]\(\)"'`]/g, "").trim() : "";
}

function removeUndefinedValues(values: Record<string, any>): Record<string, any> {
  return Object.fromEntries(Object.entries(values).filter(([, value]) => value !== undefined));
}

function removeEmptyOptionalFields<T extends RuntimeAttachment>(attachment: T): T {
  if (!attachment.url) delete attachment.url;
  if (!attachment.imageUrl) delete attachment.imageUrl;
  return attachment;
}
