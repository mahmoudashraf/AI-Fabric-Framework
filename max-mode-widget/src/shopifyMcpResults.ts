type ResultRecord = Record<string, any>;

const isRecord = (value: unknown): value is ResultRecord =>
  typeof value === "object" && value !== null && !Array.isArray(value);

const parseJsonRecord = (value: unknown): ResultRecord | null => {
  if (isRecord(value)) return value;
  if (typeof value !== "string" || !value.trim()) return null;
  try {
    const parsed = JSON.parse(value);
    return isRecord(parsed) ? parsed : null;
  } catch {
    return null;
  }
};

const extractToolPayload = (value: unknown): ResultRecord | null => {
  if (!isRecord(value)) return null;

  const toolResult = value.toolResult;
  if (isRecord(toolResult) && Array.isArray(toolResult.content)) {
    for (const entry of toolResult.content) {
      const parsed = parseJsonRecord(entry?.text ?? entry?.content ?? entry);
      if (parsed) return parsed;
    }
  }

  if (isRecord(value.actionResult)) {
    const nested = extractToolPayload(value.actionResult.data ?? value.actionResult);
    if (nested) return nested;
  }

  if (isRecord(value.data)) {
    const nested = extractToolPayload(value.data);
    if (nested) return nested;
  }

  return null;
};

const moneyText = (money: any): string | undefined => {
  if (!isRecord(money) || money.amount == null) return undefined;
  const numericAmount = Number(money.amount);
  if (!Number.isFinite(numericAmount)) return undefined;
  const majorAmount = Number.isInteger(numericAmount) && Math.abs(numericAmount) >= 100
    ? numericAmount / 100
    : numericAmount;
  const currency = typeof money.currency === "string" ? money.currency.toUpperCase() : "USD";
  const formatted = new Intl.NumberFormat("en-US", {
    style: "currency",
    currency,
    minimumFractionDigits: majorAmount % 1 === 0 ? 0 : 2,
    maximumFractionDigits: 2,
  }).format(majorAmount);
  return formatted;
};

const priceRangeText = (product: ResultRecord, firstVariant: ResultRecord | undefined): string | undefined => {
  const min = moneyText(product.price_range?.min ?? product.priceRange?.min ?? firstVariant?.price);
  const max = moneyText(product.price_range?.max ?? product.priceRange?.max);
  if (min && max && min !== max) return `${min} - ${max}`;
  return min || max;
};

const firstMediaUrl = (product: ResultRecord, firstVariant: ResultRecord | undefined): string | undefined => {
  const variantMedia = Array.isArray(firstVariant?.media) ? firstVariant?.media[0] : undefined;
  const productMedia = Array.isArray(product.media) ? product.media[0] : undefined;
  const media = variantMedia || productMedia;
  return typeof media?.url === "string" ? media.url : undefined;
};

const normalizeProduct = (product: unknown) => {
  if (!isRecord(product)) return null;
  const variants = Array.isArray(product.variants) ? product.variants.filter(isRecord) : [];
  const firstVariant = variants[0];
  const title = product.title || product.name || firstVariant?.title;
  if (typeof title !== "string" || !title.trim()) return null;

  const available = variants.some((variant) => variant.availability?.available === true)
    || product.availability?.available === true
    || product.available === true;
  const tags = Array.isArray(product.tags)
    ? product.tags.filter((tag: unknown) => typeof tag === "string" && tag.trim()).join(", ")
    : undefined;

  return {
    id: product.id || firstVariant?.id || title,
    title: title.trim(),
    price: priceRangeText(product, firstVariant),
    imageUrl: firstMediaUrl(product, firstVariant),
    available,
    productType: tags,
  };
};

export const normalizeShopifyMcpCatalogResult = (value: unknown) => {
  const payload = extractToolPayload(value);
  if (!payload || !Array.isArray(payload.products)) return null;
  const products = payload.products.map(normalizeProduct).filter(Boolean);
  if (!products.length) return null;
  return {
    products,
    returnedResults: products.length,
  };
};

export const summarizeShopifyMcpCatalogResult = (value: unknown): string | null => {
  const normalized = normalizeShopifyMcpCatalogResult(value);
  if (!normalized) return null;
  const names = normalized.products
    .map((product: any) => product.title)
    .filter(Boolean)
    .slice(0, 3);
  if (!names.length) return null;
  const suffix = normalized.products.length > names.length ? ` and ${normalized.products.length - names.length} more` : "";
  return `I found ${normalized.products.length} matching product${normalized.products.length === 1 ? "" : "s"}: ${names.join(", ")}${suffix}.`;
};
