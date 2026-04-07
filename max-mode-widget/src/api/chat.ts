import { getWidgetConfig } from "@/config";
import type { RuntimeAuthContextSummary } from "@/types";
import { apiFetchJson, apiFetchResponse } from "./client";

export type SuggestionsResponse = {
  success?: boolean;
  message?: string;
  suggestions?: string[];
  raw?: any;
};

function trimToNull(value?: string | null): string | undefined {
  const trimmed = value?.trim();
  return trimmed ? trimmed : undefined;
}

function isAbsoluteUrl(value: string): boolean {
  return /^https?:\/\//i.test(value.trim());
}

function normalizePath(value: string): string {
  if (isAbsoluteUrl(value)) {
    return value.trim();
  }
  const trimmed = value.trim();
  return trimmed.startsWith("/") ? trimmed : `/${trimmed}`;
}

function routeOverride(name: "chatQueryUrl" | "suggestionsUrl" | "authContextUrl") {
  const routes = getWidgetConfig().apiConfig.runtimeRoutes;
  return trimToNull(routes?.[name]);
}

function queryPath(requestIdentityEnabled?: boolean) {
  const configured = routeOverride("chatQueryUrl");
  if (configured) {
    return normalizePath(configured);
  }
  return requestIdentityEnabled === false ? "/chat/me/query" : "/chat/query";
}

function suggestionsPath(requestIdentityEnabled?: boolean) {
  const configured = routeOverride("suggestionsUrl");
  if (configured) {
    return normalizePath(configured);
  }
  return requestIdentityEnabled === false ? "/chat/me/suggestions" : "/chat/suggestions";
}

function authContextPath(requestIdentityEnabled?: boolean) {
  const configuredPath = routeOverride("authContextUrl")
    ?? trimToNull(getWidgetConfig().apiConfig.runtimeAuth?.authContextUrl);
  if (configuredPath) {
    return normalizePath(configuredPath);
  }
  return requestIdentityEnabled === false ? "/chat/me/auth-context" : "/chat/auth-context";
}

function resolveUrl(path: string): string {
  if (isAbsoluteUrl(path)) {
    return path;
  }
  const baseUrl = getWidgetConfig().apiConfig.chatBaseUrl;
  return `${baseUrl}${path}`;
}

export async function getChatSuggestions(payload: {
  content: string;
  userId?: string;
  maxSuggestions: number;
  attachments?: any[];
}, requestIdentityEnabled?: boolean) {
  return apiFetchJson<SuggestionsResponse>(suggestionsPath(requestIdentityEnabled), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
}

export async function postChatQuery(payload: any, requestIdentityEnabled?: boolean) {
  const path = queryPath(requestIdentityEnabled);
  const startedAt = performance.now();
  const response = await apiFetchResponse(path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  const durationMs = Math.round(performance.now() - startedAt);

  const data = await response.json().catch(() => null);

  if (!response.ok) {
    const message = data?.message || data?.error || response.statusText;
    throw new Error(`Chat query failed (${response.status}): ${message}`);
  }

  return { data, status: response.status, durationMs };
}

export function resolvedChatQueryPath(requestIdentityEnabled?: boolean) {
  return queryPath(requestIdentityEnabled);
}

export function resolvedChatQueryUrl(requestIdentityEnabled?: boolean) {
  return resolveUrl(queryPath(requestIdentityEnabled));
}

export function resolvedSuggestionsPath(requestIdentityEnabled?: boolean) {
  return suggestionsPath(requestIdentityEnabled);
}

export function resolvedSuggestionsUrl(requestIdentityEnabled?: boolean) {
  return resolveUrl(suggestionsPath(requestIdentityEnabled));
}

export function resolvedAuthContextPath(requestIdentityEnabled?: boolean) {
  return authContextPath(requestIdentityEnabled);
}

export function resolvedAuthContextUrl(requestIdentityEnabled?: boolean) {
  return resolveUrl(authContextPath(requestIdentityEnabled));
}

export async function fetchRuntimeAuthContext(
  requestIdentityEnabled?: boolean,
  ownerId?: string,
) {
  const path = authContextPath(requestIdentityEnabled);
  const resolvedPath = requestIdentityEnabled !== false && ownerId
    ? `${path}?ownerId=${encodeURIComponent(ownerId)}`
    : path;
  return apiFetchJson<RuntimeAuthContextSummary>(resolvedPath);
}
