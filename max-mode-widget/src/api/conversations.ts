import type { Conversation, ConversationDetail } from "../types";

import { getWidgetConfig } from "../config";
import { apiFetchJson, apiFetchOk } from "./client";

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

function routeOverride(name: "conversationsUrl" | "conversationItemUrlTemplate") {
  return trimToNull(getWidgetConfig().apiConfig.runtimeRoutes?.[name]);
}

function conversationsBasePath() {
  const configured = routeOverride("conversationsUrl");
  if (configured) {
    return normalizePath(configured);
  }
  return "/chat/me/conversations";
}

function conversationItemPath(conversationId: string) {
  const configuredTemplate = routeOverride("conversationItemUrlTemplate");
  if (!configuredTemplate) {
    return `${conversationsBasePath()}/${encodeURIComponent(conversationId)}`;
  }
  const normalizedTemplate = normalizePath(configuredTemplate);
  if (normalizedTemplate.includes("{conversationId}")) {
    return normalizedTemplate.split("{conversationId}").join(encodeURIComponent(conversationId));
  }
  const separator = normalizedTemplate.endsWith("/") ? "" : "/";
  return `${normalizedTemplate}${separator}${encodeURIComponent(conversationId)}`;
}

export function listConversations() {
  return apiFetchJson<Conversation[]>(conversationsBasePath());
}

export function getConversation(conversationId: string) {
  return apiFetchJson<ConversationDetail>(conversationItemPath(conversationId));
}

export async function deleteConversation(conversationId: string) {
  await apiFetchOk(conversationItemPath(conversationId), { method: "DELETE" });
}

function resolveUrl(path: string): string {
  if (isAbsoluteUrl(path)) {
    return path;
  }
  return `${getWidgetConfig().apiConfig.chatBaseUrl}${path}`;
}

export function resolvedConversationsUrl() {
  return resolveUrl(conversationsBasePath());
}

export function resolvedConversationItemUrl(conversationId: string) {
  return resolveUrl(conversationItemPath(conversationId));
}
