import {
  getWidgetConfig,
} from "@/config";

type PublicRuntimeTokenState = {
  token?: string;
  expiresAt?: string;
};

const publicRuntimeTokenState: PublicRuntimeTokenState = {};

function isAbsoluteUrl(value: string): boolean {
  return /^https?:\/\//i.test(value.trim());
}

async function readErrorBody(response: Response) {
  try {
    return await response.text();
  } catch {
    return "";
  }
}

function getChatBaseUrl(): string {
  return getWidgetConfig().apiConfig.chatBaseUrl;
}

function getCrudBaseUrl(): string {
  return getWidgetConfig().apiConfig.crudBaseUrl?.trim() || "";
}

function normalizeHeaders(input?: HeadersInit): Record<string, string> {
  if (!input) {
    return {};
  }
  if (input instanceof Headers) {
    return Object.fromEntries(input.entries());
  }
  if (Array.isArray(input)) {
    return Object.fromEntries(input);
  }
  return { ...input };
}

function hasHeader(headers: Record<string, string>, headerName: string): boolean {
  const expected = headerName.trim().toLowerCase();
  return Object.keys(headers).some((name) => name.trim().toLowerCase() === expected);
}

function trimToNull(value?: string | null): string | undefined {
  const trimmed = value?.trim();
  return trimmed ? trimmed : undefined;
}

function normalizeTokenHeader(token: string, tokenScheme?: string): string {
  const normalizedToken = token.trim();
  const scheme = trimToNull(tokenScheme) ?? "Bearer";
  const prefix = `${scheme} `;
  if (normalizedToken.toLowerCase().startsWith(prefix.toLowerCase())) {
    return normalizedToken;
  }
  return `${scheme} ${normalizedToken}`;
}

function isCachedPublicTokenUsable(): boolean {
  if (!trimToNull(publicRuntimeTokenState.token)) {
    return false;
  }
  const expiresAt = trimToNull(publicRuntimeTokenState.expiresAt);
  if (!expiresAt) {
    return true;
  }
  const expiresMs = Date.parse(expiresAt);
  if (Number.isNaN(expiresMs)) {
    return true;
  }
  return expiresMs > Date.now() + 15_000;
}

function clearCachedPublicRuntimeToken(): void {
  delete publicRuntimeTokenState.token;
  delete publicRuntimeTokenState.expiresAt;
}

async function bootstrapAnonymousRuntimeToken(baseUrl: string): Promise<string> {
  const config = getWidgetConfig();
  const runtimeAuth = config.apiConfig.runtimeAuth;
  const bootstrapUrl = trimToNull(runtimeAuth?.bootstrapUrl) ?? `${baseUrl}/public/chat/session`;
  const bootstrap = runtimeAuth?.bootstrapAnonymous;
  const response = bootstrap
    ? await bootstrap()
    : await fetch(bootstrapUrl, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
    }).then(async (res) => {
      if (!res.ok) {
        const body = await readErrorBody(res);
        throw new Error(
          `Anonymous runtime bootstrap failed (${res.status}): ${body || res.statusText}`,
        );
      }
      return res.json();
    });

  const token = trimToNull(response?.token);
  if (!token) {
    throw new Error("Anonymous runtime bootstrap did not return a token.");
  }
  publicRuntimeTokenState.token = token;
  publicRuntimeTokenState.expiresAt = trimToNull(response?.expiresAt);
  return token;
}

async function resolveSecureRuntimeHeaders(
  baseUrl: string,
  headers: Record<string, string>,
): Promise<Record<string, string>> {
  const config = getWidgetConfig();
  const runtimeAuth = config.apiConfig.runtimeAuth;
  const mode = config.integrationMode ?? "backend-mediated-private-runtime";
  const authorizationHeader = trimToNull(runtimeAuth?.authorizationHeader) ?? "Authorization";
  const tokenScheme = trimToNull(runtimeAuth?.tokenScheme) ?? "Bearer";

  if (hasHeader(headers, authorizationHeader)) {
    return headers;
  }

  if (mode === "public-runtime-authenticated" || mode === "public-runtime-anonymous") {
    const hostToken = await runtimeAuth?.getBearerToken?.();
    const normalizedHostToken = trimToNull(hostToken);
    if (normalizedHostToken) {
      return {
        ...headers,
        [authorizationHeader]: normalizeTokenHeader(normalizedHostToken, tokenScheme),
      };
    }
  }

  if (mode !== "public-runtime-anonymous") {
    return headers;
  }

  const cachedToken = isCachedPublicTokenUsable() ? trimToNull(publicRuntimeTokenState.token) : undefined;
  const token = cachedToken ?? await bootstrapAnonymousRuntimeToken(baseUrl);
  return {
    ...headers,
    [authorizationHeader]: normalizeTokenHeader(token, tokenScheme),
  };
}

async function resolveRequestHeaders(init?: RequestInit, baseUrl?: string): Promise<Record<string, string>> {
  const base = baseUrl ?? getChatBaseUrl();
  const staticHeaders = normalizeHeaders(init?.headers);
  return resolveSecureRuntimeHeaders(base, staticHeaders);
}

async function performFetch(path: string, init?: RequestInit, baseUrl?: string): Promise<Response> {
  const base = baseUrl ?? getChatBaseUrl();
  const headers = await resolveRequestHeaders(init, base);
  const requestUrl = isAbsoluteUrl(path) ? path : `${base}${path}`;
  const response = await fetch(requestUrl, {
    ...init,
    headers,
  });

  const mode = getWidgetConfig().integrationMode ?? "backend-mediated-private-runtime";
  if (response.status === 401 && mode === "public-runtime-anonymous") {
    clearCachedPublicRuntimeToken();
    const retryHeaders = await resolveRequestHeaders(init, base);
    return fetch(requestUrl, {
      ...init,
      headers: retryHeaders,
    });
  }

  return response;
}

export async function apiFetchJson<T>(
  path: string,
  init?: RequestInit,
  baseUrl?: string,
): Promise<T> {
  const response = await performFetch(path, init, baseUrl);
  if (!response.ok) {
    const body = await readErrorBody(response);
    throw new Error(
      `Request failed (${response.status}): ${body || response.statusText}`,
    );
  }
  return (await response.json()) as T;
}

export async function apiFetchOk(
  path: string,
  init?: RequestInit,
  baseUrl?: string,
): Promise<void> {
  const response = await performFetch(path, init, baseUrl);
  if (!response.ok) {
    const body = await readErrorBody(response);
    throw new Error(
      `Request failed (${response.status}): ${body || response.statusText}`,
    );
  }
}

export async function apiFetchResponse(
  path: string,
  init?: RequestInit,
  baseUrl?: string,
) {
  return performFetch(path, init, baseUrl);
}

/** Get the CRUD API base URL from widget config */
export function getCrudApiBaseUrl(): string | undefined {
  const baseUrl = getCrudBaseUrl();
  return baseUrl || undefined;
}

export function requireCrudApiBaseUrl(): string {
  const baseUrl = getCrudApiBaseUrl();
  if (!baseUrl) {
    throw new Error(
      "Max Mode widget cart/business CRUD is unavailable because apiConfig.crudBaseUrl is not configured.",
    );
  }
  return baseUrl;
}
