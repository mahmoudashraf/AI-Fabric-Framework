import { getWidgetConfig } from "@/config";
import type { ChatResult, CustomerAccountConnectAction, Document, ResultType } from "@/types";

const CUSTOMER_ACCOUNT_AUTH_ERROR_CODES = new Set([
  "CUSTOMER_ACCOUNT_AUTH_REQUIRED",
  "INVALID_CUSTOMER_ACCOUNT_SESSION",
]);

function trimToNull(value: unknown): string | undefined {
  if (typeof value !== "string") {
    return undefined;
  }
  const trimmed = value.trim();
  return trimmed ? trimmed : undefined;
}

function boolValue(value: unknown): boolean {
  return value === true || value === "true";
}

function readPath(value: any, ...path: string[]): unknown {
  let current = value;
  for (const segment of path) {
    if (!current || typeof current !== "object") {
      return undefined;
    }
    if (Array.isArray(current) && /^\d+$/.test(segment)) {
      current = current[Number(segment)];
    } else {
      current = current[segment];
    }
  }
  return current;
}

function firstText(...values: unknown[]): string | undefined {
  for (const value of values) {
    const text = trimToNull(value);
    if (text) {
      return text;
    }
  }
  return undefined;
}

function objectValue(value: unknown): Record<string, any> {
  return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, any> : {};
}

export function normalizeRuntimeDocument(doc: any, idx: number, options?: { entityType?: string; idPrefix?: string }): Document {
  const metadata = { ...objectValue(doc?.metadata) };
  let title = firstText(
    doc?.title, doc?.name, doc?.label, metadata.title, metadata.name,
    metadata._aiDocumentSourceName, metadata.originalFilename,
  );
  const content = firstText(doc?.content, doc?.contentText, doc?.text, doc?.summary, doc?.snippet, doc?.excerpt);

  if (!title && content) {
    const parts = content.split(" ");
    const titleParts = parts
      .slice(1, 8)
      .filter(
        (word: string) =>
          !word.match(/^[A-Z]+-[A-Z]+-\d+$/) && !word.match(/^\d+\.\d+$/) && word.toLowerCase() !== "usd",
      );
    title = titleParts.join(" ") + (parts.length > 8 ? "..." : "");
  }

  let docType = firstText(doc?.type, doc?.vectorSpace, metadata.vectorSpace, metadata.classification);
  const entityType = options?.entityType || "document";
  if (!docType) {
    docType = entityType.includes(",") ? entityType.split(",")[0].trim() : entityType;
  }

  const imageUrl = firstText(
    doc?.imageUrl,
    doc?.image,
    doc?.imageSrc,
    doc?.featuredImage,
    metadata.imageUrl,
    metadata.image_url,
    metadata.featuredImage,
    metadata.featured_image,
    metadata.image,
  );
  const imageAltText = firstText(doc?.imageAltText, doc?.altText, metadata.imageAltText, metadata.altText, metadata.image_alt_text);
  const url = firstText(doc?.url, doc?.link, doc?.storefrontUrl, metadata.url, metadata.link, metadata.storefrontUrl);
  const storefrontUrl = firstText(doc?.storefrontUrl, metadata.storefrontUrl, url);
  const handle = firstText(doc?.handle, metadata.handle);

  if (imageUrl && !metadata.imageUrl) metadata.imageUrl = imageUrl;
  if (imageAltText && !metadata.imageAltText) metadata.imageAltText = imageAltText;
  if (url && !metadata.url) metadata.url = url;
  if (storefrontUrl && !metadata.storefrontUrl) metadata.storefrontUrl = storefrontUrl;
  if (handle && !metadata.handle) metadata.handle = handle;

  return {
    ...objectValue(doc),
    id: firstText(doc?.id, doc?.documentId, doc?.sourceId, doc?.url, doc?.link, title) || `${options?.idPrefix || "doc"}-${idx}`,
    title: title || `Document ${idx + 1}`,
    content: content || "No content available",
    type: docType || "document",
    metadata,
    imageUrl,
    imageAltText,
    url,
    storefrontUrl,
    handle,
    product_variant_id: firstText(doc?.product_variant_id, metadata.product_variant_id, metadata.variantId, metadata.variant_id),
    firstAvailableVariantTitle: firstText(doc?.firstAvailableVariantTitle, metadata.firstAvailableVariantTitle),
    priceRange: firstText(doc?.priceRange, metadata.priceRange, metadata.price, metadata.amount),
    availability: firstText(doc?.availability, metadata.availability, metadata.status),
    score: typeof doc?.score === "number" ? doc.score : typeof metadata.score === "number" ? metadata.score : undefined,
    similarity: typeof doc?.similarity === "number" ? doc.similarity : typeof metadata.similarity === "number" ? metadata.similarity : undefined,
  };
}

function resolveChatResultDocuments(payload: any, data: Record<string, any>): Document[] {
  const sources = Array.isArray(payload.sources) ? payload.sources : [];
  const documents: any[] = [
    payload.ragResponse?.documents,
    payload.documents,
    payload.result?.data?.documents,
    payload.result?.data?.ragResponse?.documents,
    data.documents,
    data.ragResponse?.documents,
  ].find((value) => Array.isArray(value) && value.length > 0) || [];
  const entityType = payload.ragResponse?.entityType
    || payload.result?.data?.ragResponse?.entityType || data.ragResponse?.entityType || "document";
  const documentsById = new Map<string, any[]>();
  for (const document of documents) {
    if (typeof document?.id !== "string" || !document.id) continue;
    const matches = documentsById.get(document.id) || [];
    matches.push(document);
    documentsById.set(document.id, matches);
  }

  // A nonempty citation list is the display boundary, even when a reference cannot be enriched.
  const displayDocuments = sources.length > 0 ? sources.map((source: any) => {
    if (firstText(source?.content, source?.contentText, source?.text, source?.summary, source?.snippet, source?.excerpt)) {
      return source;
    }
    const documentId = source?.documentId ?? source?.id;
    const matches = documentsById.get(documentId);
    if (matches?.length !== 1) return source;
    const document = matches[0];
    const sourceMetadata = objectValue(source?.metadata);
    const documentMetadata = objectValue(document.metadata);
    const knowledgeSourceIds = [
      source?.sourceId, source?.knowledgeSourceId, sourceMetadata.knowledgeSourceId,
      document.knowledgeSourceId, documentMetadata.knowledgeSourceId,
    ].filter((value) => typeof value === "string" && value.length > 0);
    if (new Set(knowledgeSourceIds).size > 1) return source;

    return {
      ...source,
      ...document,
      score: typeof source?.score === "number" ? source.score : document.score,
      similarity: typeof source?.similarity === "number" ? source.similarity : document.similarity,
      metadata: { ...sourceMetadata, ...documentMetadata },
    };
  }) : documents;
  return displayDocuments.map((doc: any, idx: number) => normalizeRuntimeDocument(doc, idx, { entityType }));
}

function firstErrorCode(payload: any): string | undefined {
  const value = firstText(
    readPath(payload, "actions", "0", "errorCode"),
    readPath(payload, "actions", "0", "actionResult", "errorCode"),
    readPath(payload, "fallbackReason"),
    readPath(payload, "errorCode"),
  );
  return value?.toUpperCase();
}

export function extractChatResultMessage(payload: any, fallback: string): string {
  return firstText(
    readPath(payload, "safeSummary"),
    readPath(payload, "answer"),
    readPath(payload, "response"),
  ) ?? fallback;
}

export function canonicalChatResult(payload: any): ChatResult | undefined {
  if (!payload || typeof payload !== "object") {
    return undefined;
  }
  const resultType = (firstText(payload.type) || "INFORMATION_PROVIDED") as ResultType;
  const success = typeof payload.success === "boolean" ? payload.success : true;
  const answer = extractChatResultMessage(payload, success ? "I processed your query successfully." : "I could not process that request.");
  const ragResponse = payload.ragResponse && typeof payload.ragResponse === "object" ? payload.ragResponse : undefined;
  const actions = Array.isArray(payload.actions) ? payload.actions : [];
  const data: Record<string, any> = {};
  if (answer) {
    data.answer = answer;
  }
  if (ragResponse) {
    data.ragResponse = ragResponse;
  }
  if (actions.length === 1 && actions[0] && typeof actions[0] === "object") {
    Object.assign(data, actions[0]);
  } else if (actions.length > 1) {
    data.actions = actions;
  }
  const documents = resolveChatResultDocuments(payload, data);
  if (documents.length > 0) {
    data.documents = documents;
  }
  return {
    type: resultType,
    success,
    sanitizedPayload: {
      type: resultType,
      success,
      message: answer,
      safeSummary: answer,
      answer,
      errorCode: firstErrorCode(payload),
      data,
    },
  };
}

export function extractCustomerAccountConnectAction(payload: any): CustomerAccountConnectAction | undefined {
  const topAction = Array.isArray(payload?.actions)
    ? payload.actions.find((action: any) => action && typeof action === "object" && (
      action.customerAccountAuthRequired || action.customerAccountAuth || action.errorCode
    ))
    : undefined;
  const authMarker = topAction?.customerAccountAuth;
  const errorCode = firstErrorCode(payload);
  const required = boolValue(topAction?.customerAccountAuthRequired)
    || boolValue(authMarker?.required)
    || (errorCode ? CUSTOMER_ACCOUNT_AUTH_ERROR_CODES.has(errorCode) : false);

  if (!required) {
    return undefined;
  }

  const hostAuth = getWidgetConfig().host?.customerAccountAuth;
  const startUrl = trimToNull(hostAuth?.startUrl);
  if (!startUrl) {
    return undefined;
  }

  return {
    startUrl,
    sessionUrl: trimToNull(hostAuth?.sessionUrl),
    shopperSessionId: trimToNull(hostAuth?.shopperSessionId),
    returnTo: trimToNull(hostAuth?.returnTo),
    label: "Connect store account",
    description: "Authorize the assistant to read your customer account securely, then ask again.",
  };
}

export function buildCustomerAccountConnectUrl(action: CustomerAccountConnectAction): string | null {
  const startUrl = trimToNull(action.startUrl);
  if (!startUrl) {
    return null;
  }
  try {
    const base = typeof window !== "undefined" ? window.location.href : "https://localhost/";
    const url = new URL(startUrl, base);
    const shopperSessionId = trimToNull(action.shopperSessionId);
    if (shopperSessionId && !url.searchParams.has("shopperSessionId")) {
      url.searchParams.set("shopperSessionId", shopperSessionId);
    }
    const returnTo = trimToNull(action.returnTo)
      ?? (typeof window !== "undefined" ? window.location.href : undefined);
    if (returnTo && !url.searchParams.has("returnTo")) {
      url.searchParams.set("returnTo", returnTo);
    }
    return url.toString();
  } catch {
    return null;
  }
}
