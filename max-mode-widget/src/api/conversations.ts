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

function conversationsBasePath(requestIdentityEnabled?: boolean) {
  const configured = routeOverride("conversationsUrl");
  if (configured) {
    return normalizePath(configured);
  }
  return "/chat/me/conversations";
}

function withOwnerId(path: string, ownerId?: string, requestIdentityEnabled?: boolean) {
  return path;
}

function conversationItemPath(conversationId: string, requestIdentityEnabled?: boolean) {
  const configuredTemplate = routeOverride("conversationItemUrlTemplate");
  if (!configuredTemplate) {
    return `${conversationsBasePath(requestIdentityEnabled)}/${encodeURIComponent(conversationId)}`;
  }
  const normalizedTemplate = normalizePath(configuredTemplate);
  if (normalizedTemplate.includes("{conversationId}")) {
    return normalizedTemplate.split("{conversationId}").join(encodeURIComponent(conversationId));
  }
  const separator = normalizedTemplate.endsWith("/") ? "" : "/";
  return `${normalizedTemplate}${separator}${encodeURIComponent(conversationId)}`;
}

export function listConversations(ownerId?: string, requestIdentityEnabled?: boolean) {
  return apiFetchJson<Conversation[]>(
    withOwnerId(conversationsBasePath(requestIdentityEnabled), ownerId, requestIdentityEnabled),
  );
}

export function getConversation(conversationId: string, ownerId?: string, requestIdentityEnabled?: boolean) {
  return apiFetchJson<ConversationDetail>(
    withOwnerId(
      conversationItemPath(conversationId, requestIdentityEnabled),
      ownerId,
      requestIdentityEnabled,
    ),
  );
}

export async function deleteConversation(conversationId: string, ownerId?: string, requestIdentityEnabled?: boolean) {
  await apiFetchOk(
    withOwnerId(
      conversationItemPath(conversationId, requestIdentityEnabled),
      ownerId,
      requestIdentityEnabled,
    ),
    { method: "DELETE" },
  );
}

function resolveUrl(path: string): string {
  if (isAbsoluteUrl(path)) {
    return path;
  }
  return `${getWidgetConfig().apiConfig.chatBaseUrl}${path}`;
}

export function resolvedConversationsUrl(requestIdentityEnabled?: boolean) {
  return resolveUrl(conversationsBasePath(requestIdentityEnabled));
}

export function resolvedConversationItemUrl(conversationId: string, requestIdentityEnabled?: boolean) {
  return resolveUrl(conversationItemPath(conversationId, requestIdentityEnabled));
}
