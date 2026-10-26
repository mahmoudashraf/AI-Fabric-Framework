import type { DeskConfig } from "./types";

export class ApiError extends Error {
  constructor(
    message: string,
    public status: number,
    public code?: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

let csrf: Pick<DeskConfig, "csrfToken" | "csrfHeaderName"> = {};
export function configureCsrf(config: DeskConfig) {
  csrf = config;
}

export async function api<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const headers = new Headers(options.headers);
  headers.set("Accept", "application/json");
  if (options.body) headers.set("Content-Type", "application/json");
  if (options.method && options.method !== "GET") {
    const cookie = document.cookie
      .split("; ")
      .find((item) => item.startsWith("XSRF-TOKEN="))
      ?.slice(11);
    const token =
      csrf.csrfToken ?? (cookie ? decodeURIComponent(cookie) : undefined);
    if (token) headers.set(csrf.csrfHeaderName ?? "X-XSRF-TOKEN", token);
  }
  const response = await fetch(path, {
    ...options,
    credentials: "same-origin",
    headers,
    cache: "no-store",
  });
  if (
    path === "/logout" &&
    response.ok &&
    response.redirected &&
    new URL(response.url).origin === location.origin
  )
    return undefined as T;
  const data: unknown =
    response.status === 204
      ? undefined
      : await response.json().catch(() => undefined);
  if (!response.ok) {
    if (response.status === 401)
      window.dispatchEvent(new Event("desk:session-expired"));
    if (response.status === 403)
      window.dispatchEvent(new Event("desk:access-changed"));
    const message =
      data &&
      typeof data === "object" &&
      "message" in data &&
      typeof data.message === "string"
        ? data.message
        : response.status === 401
          ? "Your session has expired. Sign in again."
          : response.status === 403
            ? "You no longer have permission for this workspace."
            : `Request failed (${response.status}). Please try again.`;
    const code =
      data &&
      typeof data === "object" &&
      "code" in data &&
      typeof data.code === "string"
        ? data.code
        : undefined;
    throw new ApiError(message, response.status, code);
  }
  if (response.status !== 204 && data === undefined)
    throw new ApiError(
      "The service returned an unreadable response.",
      response.status,
    );
  return data as T;
}

export const bindingPath = (id: string) =>
  `/api/desk/v1/bindings/${encodeURIComponent(id)}`;
export const post = <T>(path: string, body: unknown) =>
  api<T>(path, { method: "POST", body: JSON.stringify(body) });
export const errorMessage = (error: unknown) =>
  error instanceof ApiError && error.code
    ? `${error.message} [${error.code}]`
    : error instanceof Error
      ? error.message
      : "Something went wrong. Please try again.";

// A retry of the same uncertain command keeps its idempotency identity.
export function commandKey(
  store: Map<string, string>,
  operation: string,
  body = "",
) {
  const key = `${operation}:${body}`;
  if (!store.has(key)) store.set(key, crypto.randomUUID());
  return { id: store.get(key)!, done: () => store.delete(key) };
}
