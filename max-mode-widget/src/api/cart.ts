import { apiFetchJson, apiFetchResponse, getCrudApiBaseUrl } from "./client";

export type ActiveCart = any;

export async function addCartItem(userId: string, sku: string, quantity = 1) {
  return apiFetchJson<ActiveCart>(
    `/carts/active/items`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ userId, sku, quantity }),
    },
    getCrudApiBaseUrl(),
  );
}

export async function getActiveCart(userId: string) {
  const response = await apiFetchResponse(
    `/carts/active?userId=${encodeURIComponent(userId)}`,
    {
      method: "GET",
      headers: { "Content-Type": "application/json" },
    },
    getCrudApiBaseUrl(),
  );

  if (response.ok) return (await response.json()) as ActiveCart;
  if (response.status === 404) return null;

  const errorText = await response.text().catch(() => "");
  throw new Error(
    `Failed to fetch cart (${response.status}): ${errorText || response.statusText}`,
  );
}

export async function removeCartItem(userId: string, sku: string) {
  return apiFetchJson<ActiveCart>(
    `/carts/active/items?userId=${encodeURIComponent(userId)}&sku=${encodeURIComponent(sku)}`,
    {
      method: "DELETE",
      headers: { "Content-Type": "application/json" },
    },
    getCrudApiBaseUrl(),
  );
}
