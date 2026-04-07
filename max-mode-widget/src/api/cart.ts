import { apiFetchJson, apiFetchResponse, requireCrudApiBaseUrl } from "./client";

export type ActiveCart = any;

export async function addCartItem(sku: string, quantity = 1) {
  const crudBaseUrl = requireCrudApiBaseUrl();
  return apiFetchJson<ActiveCart>(
    `/carts/active/items`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ sku, quantity }),
    },
    crudBaseUrl,
  );
}

export async function getActiveCart() {
  const crudBaseUrl = requireCrudApiBaseUrl();
  const response = await apiFetchResponse(
    `/carts/active`,
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

export async function removeCartItem(sku: string) {
  const crudBaseUrl = requireCrudApiBaseUrl();
  return apiFetchJson<ActiveCart>(
    `/carts/active/items?sku=${encodeURIComponent(sku)}`,
    {
      method: "DELETE",
      headers: { "Content-Type": "application/json" },
    },
    crudBaseUrl,
  );
}
