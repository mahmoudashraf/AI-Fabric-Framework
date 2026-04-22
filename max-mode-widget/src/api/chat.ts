import { getWidgetConfig } from "@/config";
import type { RuntimeAuthContextSummary, RuntimeShellConfigSummary } from "@/types";
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

function routeOverride(name: "chatQueryUrl" | "suggestionsUrl" | "authContextUrl" | "shellConfigUrl") {
  const routes = getWidgetConfig().apiConfig.runtimeRoutes;
  return trimToNull(routes?.[name]);
}

function queryPath() {
  const configured = routeOverride("chatQueryUrl");
  if (configured) {
    return normalizePath(configured);
  }
  return "/chat/me/query";
}

function suggestionsPath() {
  const configured = routeOverride("suggestionsUrl");
  if (configured) {
    return normalizePath(configured);
  }
  return "/chat/me/suggestions";
}

function authContextPath() {
  const configuredPath = routeOverride("authContextUrl")
    ?? trimToNull(getWidgetConfig().apiConfig.runtimeAuth?.authContextUrl);
  if (configuredPath) {
    return normalizePath(configuredPath);
  }
  return "/chat/me/auth-context";
}

function shellConfigPath() {
  const configuredPath = routeOverride("shellConfigUrl");
  if (configuredPath) {
    return normalizePath(configuredPath);
  }
  return "/chat/me/shell-config";
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
  maxSuggestions: number;
  attachments?: any[];
}) {
  return apiFetchJson<SuggestionsResponse>(suggestionsPath(), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
}

export async function postChatQuery(payload: any) {
  const path = queryPath();
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

export function resolvedChatQueryPath() {
  return queryPath();
}

export function resolvedChatQueryUrl() {
  return resolveUrl(queryPath());
}

export function resolvedSuggestionsPath() {
  return suggestionsPath();
}

export function resolvedSuggestionsUrl() {
  return resolveUrl(suggestionsPath());
}

export function resolvedAuthContextPath() {
  return authContextPath();
}

export function resolvedAuthContextUrl() {
  return resolveUrl(authContextPath());
}

export async function fetchRuntimeAuthContext() {
  return apiFetchJson<RuntimeAuthContextSummary>(authContextPath());
}

export function resolvedShellConfigPath() {
  return shellConfigPath();
}

export function resolvedShellConfigUrl() {
  return resolveUrl(shellConfigPath());
}

export async function fetchRuntimeShellConfig() {
  return apiFetchJson<RuntimeShellConfigSummary>(shellConfigPath());
}
