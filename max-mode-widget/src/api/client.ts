import {
  getWidgetConfig,
} from "@/config";

type PublicRuntimeTokenState = {
  token?: string;
  expiresAt?: string;
  sessionId?: string;
  runtimeKey?: string;
};

type PublicRuntimeSessionBinding = {
  runtimeKey: string;
  sessionId: string;
};

const PUBLIC_RUNTIME_SESSION_BINDING_KEY = "maxmode_public_runtime_session_binding_v1";
const publicRuntimeTokenState: PublicRuntimeTokenState = {};
const publicRuntimeBootstrapPromises = new Map<string, Promise<string>>();
const publicRuntimeRenewalPromises = new Map<string, Promise<string>>();
const publicRuntimeSessionInvalidationListeners = new Set<(
  event: PublicRuntimeSessionInvalidation,
) => void>();

export type PublicRuntimeSessionInvalidationReason =
  | "expired"
  | "unauthorized"
  | "runtime-changed"
  | "identity-changed"
  | "conversation-access-denied";

export type PublicRuntimeSessionInvalidation = {
  reason: PublicRuntimeSessionInvalidationReason;
  previousSessionId?: string;
};

export class PublicRuntimeSessionInvalidatedError extends Error {
  readonly code = "PUBLIC_RUNTIME_SESSION_INVALIDATED";

  constructor(readonly reason: PublicRuntimeSessionInvalidationReason) {
    super("Your secure guest session changed. Please send your request again.");
    this.name = "PublicRuntimeSessionInvalidatedError";
  }
}

export function isPublicRuntimeSessionInvalidatedError(
  error: unknown,
): error is PublicRuntimeSessionInvalidatedError {
  return error instanceof PublicRuntimeSessionInvalidatedError;
}

export function subscribePublicRuntimeSessionInvalidation(
  listener: (event: PublicRuntimeSessionInvalidation) => void,
): () => void {
  publicRuntimeSessionInvalidationListeners.add(listener);
  return () => {
    publicRuntimeSessionInvalidationListeners.delete(listener);
  };
}

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

function getFetchCredentials(): RequestCredentials | undefined {
  return getWidgetConfig().apiConfig.fetchCredentials;
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

function runtimeCacheKey(baseUrl: string): string {
  const runtimeAuth = getWidgetConfig().apiConfig.runtimeAuth;
  const bootstrapUrl = trimToNull(runtimeAuth?.bootstrapUrl)
    ?? `${baseUrl}/public/chat/session`;
  const renewUrl = trimToNull(runtimeAuth?.renewUrl) ?? `${bootstrapUrl.replace(/\/$/, "")}/renew`;
  return `${baseUrl.trim().replace(/\/$/, "")}|${bootstrapUrl}|${renewUrl}`;
}

function loadPersistedPublicRuntimeSessionBinding(): PublicRuntimeSessionBinding | undefined {
  try {
    const raw = sessionStorage.getItem(PUBLIC_RUNTIME_SESSION_BINDING_KEY);
    if (!raw) {
      return undefined;
    }
    const parsed = JSON.parse(raw) as Partial<PublicRuntimeSessionBinding>;
    const persistedRuntimeKey = trimToNull(parsed.runtimeKey);
    const persistedSessionId = trimToNull(parsed.sessionId);
    if (!persistedRuntimeKey || !persistedSessionId) {
      return undefined;
    }
    return {
      runtimeKey: persistedRuntimeKey,
      sessionId: persistedSessionId,
    };
  } catch {
    return undefined;
  }
}

function persistPublicRuntimeSessionBinding(binding: PublicRuntimeSessionBinding): void {
  try {
    sessionStorage.setItem(PUBLIC_RUNTIME_SESSION_BINDING_KEY, JSON.stringify(binding));
  } catch {}
}

function clearPersistedPublicRuntimeSessionBinding(): void {
  try {
    sessionStorage.removeItem(PUBLIC_RUNTIME_SESSION_BINDING_KEY);
  } catch {}
}

type CachedPublicTokenStatus = "missing" | "runtime-changed" | "usable" | "renewable" | "expired";

function cachedPublicTokenStatus(baseUrl: string): CachedPublicTokenStatus {
  if (!trimToNull(publicRuntimeTokenState.token)) {
    return "missing";
  }
  if (publicRuntimeTokenState.runtimeKey !== runtimeCacheKey(baseUrl)) {
    return "runtime-changed";
  }
  const expiresAt = trimToNull(publicRuntimeTokenState.expiresAt);
  if (!expiresAt) {
    return "usable";
  }
  const expiresMs = Date.parse(expiresAt);
  if (Number.isNaN(expiresMs)) {
    return "usable";
  }
  if (expiresMs <= Date.now()) {
    return "expired";
  }
  return expiresMs > Date.now() + 15_000 ? "usable" : "renewable";
}

function clearCachedPublicRuntimeToken(): void {
  delete publicRuntimeTokenState.token;
  delete publicRuntimeTokenState.expiresAt;
  delete publicRuntimeTokenState.sessionId;
  delete publicRuntimeTokenState.runtimeKey;
}

function notifyPublicRuntimeSessionInvalidation(event: PublicRuntimeSessionInvalidation): void {
  publicRuntimeSessionInvalidationListeners.forEach((listener) => {
    try {
      listener(event);
    } catch {
      // One host listener must not prevent the identity boundary from being cleared.
    }
  });
}

function invalidatePublicRuntimeSession(
  reason: PublicRuntimeSessionInvalidationReason,
): void {
  const previousSessionId = trimToNull(publicRuntimeTokenState.sessionId);
  const hadRuntimeSession = Boolean(
    trimToNull(publicRuntimeTokenState.token)
      || previousSessionId
      || trimToNull(publicRuntimeTokenState.runtimeKey),
  );
  clearCachedPublicRuntimeToken();
  clearPersistedPublicRuntimeSessionBinding();
  if (!hadRuntimeSession) {
    return;
  }
  notifyPublicRuntimeSessionInvalidation({ reason, previousSessionId });
}

export function invalidateRuntimeConversation(
  reason: Extract<PublicRuntimeSessionInvalidationReason, "conversation-access-denied">,
): void {
  notifyPublicRuntimeSessionInvalidation({
    reason,
    previousSessionId: trimToNull(publicRuntimeTokenState.sessionId),
  });
}

async function bootstrapAnonymousRuntimeToken(baseUrl: string): Promise<string> {
  const expectedRuntimeKey = runtimeCacheKey(baseUrl);
  const existing = publicRuntimeBootstrapPromises.get(expectedRuntimeKey);
  if (existing) {
    return existing;
  }
  const pending = performAnonymousRuntimeBootstrap(baseUrl, expectedRuntimeKey);
  publicRuntimeBootstrapPromises.set(expectedRuntimeKey, pending);
  try {
    return await pending;
  } finally {
    if (publicRuntimeBootstrapPromises.get(expectedRuntimeKey) === pending) {
      publicRuntimeBootstrapPromises.delete(expectedRuntimeKey);
    }
  }
}

async function performAnonymousRuntimeBootstrap(baseUrl: string, expectedRuntimeKey: string): Promise<string> {
  const config = getWidgetConfig();
  const runtimeAuth = config.apiConfig.runtimeAuth;
  const bootstrapUrl = trimToNull(runtimeAuth?.bootstrapUrl) ?? `${baseUrl}/public/chat/session`;
  const bootstrap = runtimeAuth?.bootstrapAnonymous;
  const response = bootstrap
    ? await bootstrap()
    : await fetch(bootstrapUrl, {
      method: "POST",
      credentials: getFetchCredentials(),
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
  if (runtimeCacheKey(baseUrl) !== expectedRuntimeKey) {
    throw new PublicRuntimeSessionInvalidatedError("runtime-changed");
  }
  const previousBinding = loadPersistedPublicRuntimeSessionBinding();
  const sessionId = trimToNull(response?.sessionId);
  if (!sessionId) {
    throw new Error("Anonymous runtime bootstrap did not return a session identifier.");
  }
  publicRuntimeTokenState.token = token;
  publicRuntimeTokenState.expiresAt = trimToNull(response?.expiresAt);
  publicRuntimeTokenState.sessionId = sessionId;
  publicRuntimeTokenState.runtimeKey = expectedRuntimeKey;
  persistPublicRuntimeSessionBinding({ runtimeKey: expectedRuntimeKey, sessionId });

  if (previousBinding && (
    previousBinding.runtimeKey !== expectedRuntimeKey
      || previousBinding.sessionId !== sessionId
  )) {
    notifyPublicRuntimeSessionInvalidation({
      reason: previousBinding.runtimeKey === expectedRuntimeKey ? "identity-changed" : "runtime-changed",
      previousSessionId: previousBinding.sessionId,
    });
  }
  return token;
}

async function renewAnonymousRuntimeToken(baseUrl: string): Promise<string> {
  const expectedRuntimeKey = runtimeCacheKey(baseUrl);
  const existing = publicRuntimeRenewalPromises.get(expectedRuntimeKey);
  if (existing) {
    return existing;
  }
  const pending = performAnonymousRuntimeRenewal(baseUrl, expectedRuntimeKey);
  publicRuntimeRenewalPromises.set(expectedRuntimeKey, pending);
  try {
    return await pending;
  } finally {
    if (publicRuntimeRenewalPromises.get(expectedRuntimeKey) === pending) {
      publicRuntimeRenewalPromises.delete(expectedRuntimeKey);
    }
  }
}

async function performAnonymousRuntimeRenewal(baseUrl: string, expectedRuntimeKey: string): Promise<string> {
  const runtimeAuth = getWidgetConfig().apiConfig.runtimeAuth;
  const bootstrapUrl = trimToNull(runtimeAuth?.bootstrapUrl) ?? `${baseUrl}/public/chat/session`;
  const renewUrl = trimToNull(runtimeAuth?.renewUrl) ?? `${bootstrapUrl.replace(/\/$/, "")}/renew`;
  const currentToken = trimToNull(publicRuntimeTokenState.token);
  const currentSessionId = trimToNull(publicRuntimeTokenState.sessionId);
  if (!currentToken || !currentSessionId) {
    invalidatePublicRuntimeSession("unauthorized");
    throw new PublicRuntimeSessionInvalidatedError("unauthorized");
  }
  const authorizationHeader = trimToNull(runtimeAuth?.authorizationHeader) ?? "Authorization";
  const tokenScheme = trimToNull(runtimeAuth?.tokenScheme) ?? "Bearer";
  const response = await fetch(renewUrl, {
    method: "POST",
    credentials: getFetchCredentials(),
    headers: {
      "Content-Type": "application/json",
      [authorizationHeader]: normalizeTokenHeader(currentToken, tokenScheme),
    },
  });
  if (response.status === 401 || response.status === 403) {
    invalidatePublicRuntimeSession("unauthorized");
    throw new PublicRuntimeSessionInvalidatedError("unauthorized");
  }
  if (!response.ok) {
    const body = await readErrorBody(response);
    throw new Error(`Anonymous runtime renewal failed (${response.status}): ${body || response.statusText}`);
  }
  const renewed = await response.json() as import("@/config").MaxModeRuntimeBootstrapResult;
  const token = trimToNull(renewed.token);
  const sessionId = trimToNull(renewed.sessionId);
  if (!token
    || sessionId !== currentSessionId
    || publicRuntimeTokenState.token !== currentToken
    || runtimeCacheKey(baseUrl) !== expectedRuntimeKey) {
    invalidatePublicRuntimeSession("unauthorized");
    throw new PublicRuntimeSessionInvalidatedError("unauthorized");
  }
  publicRuntimeTokenState.token = token;
  publicRuntimeTokenState.expiresAt = trimToNull(renewed.expiresAt);
  publicRuntimeTokenState.sessionId = sessionId;
  publicRuntimeTokenState.runtimeKey = runtimeCacheKey(baseUrl);
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

  const tokenStatus = cachedPublicTokenStatus(baseUrl);
  if (tokenStatus === "runtime-changed" || tokenStatus === "expired") {
    const reason = tokenStatus === "runtime-changed" ? "runtime-changed" : "expired";
    invalidatePublicRuntimeSession(reason);
    throw new PublicRuntimeSessionInvalidatedError(reason);
  }

  const token = tokenStatus === "usable"
    ? trimToNull(publicRuntimeTokenState.token)
    : tokenStatus === "renewable"
      ? await renewAnonymousRuntimeToken(baseUrl)
      : await bootstrapAnonymousRuntimeToken(baseUrl);
  return {
    ...headers,
    [authorizationHeader]: normalizeTokenHeader(token, tokenScheme),
  };
}

async function resolveRequestHeaders(init?: RequestInit, baseUrl?: string): Promise<Record<string, string>> {
  const base = baseUrl ?? getChatBaseUrl();
  const staticHeaders = {
    ...normalizeHeaders(getWidgetConfig().apiConfig.defaultHeaders),
    ...normalizeHeaders(init?.headers),
  };
  return resolveSecureRuntimeHeaders(base, staticHeaders);
}

async function performFetch(path: string, init?: RequestInit, baseUrl?: string): Promise<Response> {
  const base = baseUrl ?? getChatBaseUrl();
  const headers = await resolveRequestHeaders(init, base);
  const requestUrl = isAbsoluteUrl(path) ? path : `${base}${path}`;
  const response = await fetch(requestUrl, {
    ...init,
    credentials: init?.credentials ?? getFetchCredentials(),
    headers,
  });

  const mode = getWidgetConfig().integrationMode ?? "backend-mediated-private-runtime";
  if (response.status === 401 && mode === "public-runtime-anonymous") {
    invalidatePublicRuntimeSession("unauthorized");
    throw new PublicRuntimeSessionInvalidatedError("unauthorized");
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
