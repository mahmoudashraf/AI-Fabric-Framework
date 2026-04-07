import { getWidgetConfig } from "@/config";
import type { RuntimeAuthContextSummary } from "@/types";
import { apiFetchJson, apiFetchResponse } from "./client";

export type SuggestionsResponse = {
  success?: boolean;
  message?: string;
  suggestions?: string[];
  raw?: any;
};

function queryPath(requestIdentityEnabled?: boolean) {
  return requestIdentityEnabled === false ? "/chat/me/query" : "/chat/query";
}

function suggestionsPath(requestIdentityEnabled?: boolean) {
  return requestIdentityEnabled === false ? "/chat/me/suggestions" : "/chat/suggestions";
}

function authContextPath(requestIdentityEnabled?: boolean) {
  const configuredPath = getWidgetConfig().apiConfig.runtimeAuth?.authContextUrl?.trim();
  if (configuredPath) {
    return configuredPath.startsWith("/") ? configuredPath : `/${configuredPath}`;
  }
  return requestIdentityEnabled === false ? "/chat/me/auth-context" : "/chat/auth-context";
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

export function resolvedSuggestionsPath(requestIdentityEnabled?: boolean) {
  return suggestionsPath(requestIdentityEnabled);
}

export function resolvedAuthContextPath(requestIdentityEnabled?: boolean) {
  return authContextPath(requestIdentityEnabled);
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
