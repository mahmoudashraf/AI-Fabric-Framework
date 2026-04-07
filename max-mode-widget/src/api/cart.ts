import { apiFetchJson, apiFetchResponse, requireCrudApiBaseUrl } from "./client";

export type ActiveCart = any;

function withUserId(path: string, userId?: string) {
  if (!userId) {
    return path;
  }
  const separator = path.includes("?") ? "&" : "?";
  return `${path}${separator}userId=${encodeURIComponent(userId)}`;
}

export async function addCartItem(userId: string | undefined, sku: string, quantity = 1) {
  const crudBaseUrl = requireCrudApiBaseUrl();
  const payload = userId ? { userId, sku, quantity } : { sku, quantity };
  return apiFetchJson<ActiveCart>(
    `/carts/active/items`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
    crudBaseUrl,
  );
}

export async function getActiveCart(userId?: string) {
  const crudBaseUrl = requireCrudApiBaseUrl();
  const response = await apiFetchResponse(
    withUserId(`/carts/active`, userId),
    {
      method: "GET",
      headers: { "Content-Type": "application/json" },
    },
    crudBaseUrl,
  );

  if (response.ok) return (await response.json()) as ActiveCart;
  if (response.status === 404) return null;

  const errorText = await response.text().catch(() => "");
  throw new Error(
    `Failed to fetch cart (${response.status}): ${errorText || response.statusText}`,
  );
}

export async function removeCartItem(userId: string | undefined, sku: string) {
  const crudBaseUrl = requireCrudApiBaseUrl();
  const path = withUserId(`/carts/active/items`, userId);
  const separator = path.includes("?") ? "&" : "?";
  return apiFetchJson<ActiveCart>(
    `${path}${separator}sku=${encodeURIComponent(sku)}`,
    {
      method: "DELETE",
      headers: { "Content-Type": "application/json" },
    },
    crudBaseUrl,
  );
}
