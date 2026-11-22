import type { AttachedItem } from "@/types";
import type { MaxModePresentationResultReference } from "@/actionPresentation";
import { presentationReferenceAttachmentId } from "@/actionPresentation";
import { CURRENT_PAGE_ATTACHMENT_TYPE } from "@/pageAttachment";
import { explicitAttachmentVectorSpace } from "@/requestSemantics";
import { explicitAttachmentContent } from "@/attachmentContent";

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
  const content = explicitAttachmentContent(data);

  const sourceMetadata = { ...(explicitAttachmentContent(data.metadata || {}).value as Record<string, any>) };
  const vectorSpace = explicitAttachmentVectorSpace(data);

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
    attachmentKind: "EXPLICIT_USER_ITEM",
    trust: "USER_PROVIDED",
    actionEligible: false,
    contentTruncated: content.truncated,
  });

  return removeEmptyOptionalFields({
    id: cleanValue(String(data.id ?? data.orderId ?? data.sku ?? `attachment-${index}`)),
    vectorSpace,
    contentText: content.text,
    metadata,
    source: item.type,
    url: cleanValue(data.url),
    imageUrl: cleanValue(data.imageUrl || data.metadata?.imageUrl),
  });
}

export function toActionResultAttachedItem(reference: MaxModePresentationResultReference): AttachedItem {
  return {
    type: ACTION_RESULT_ATTACHMENT_TYPE,
    contextLabel: reference.label,
    data: {
      id: presentationReferenceAttachmentId(reference),
      title: reference.label,
      content: explicitAttachmentContent(reference.safeData).text,
      metadata: {
        attachmentKind: "ACTION_RESULT_CONTEXT",
        trust: "USER_PROVIDED",
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
    trust: "USER_PROVIDED",
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
  if (!attachment.vectorSpace) delete attachment.vectorSpace;
  if (!attachment.url) delete attachment.url;
  if (!attachment.imageUrl) delete attachment.imageUrl;
  return attachment;
}
