import type { Conversation, ConversationDetail } from "../types";

import { apiFetchJson, apiFetchOk } from "./client";

function conversationsBasePath(requestIdentityEnabled?: boolean) {
  return requestIdentityEnabled === false ? "/chat/me/conversations" : "/chat/conversations";
}

function withOwnerId(path: string, ownerId?: string, requestIdentityEnabled?: boolean) {
  if (requestIdentityEnabled === false || !ownerId) {
    return path;
  }
  const separator = path.includes("?") ? "&" : "?";
  return `${path}${separator}ownerId=${encodeURIComponent(ownerId)}`;
}

export function listConversations(ownerId?: string, requestIdentityEnabled?: boolean) {
  return apiFetchJson<Conversation[]>(
    withOwnerId(conversationsBasePath(requestIdentityEnabled), ownerId, requestIdentityEnabled),
  );
}

export function getConversation(conversationId: string, ownerId?: string, requestIdentityEnabled?: boolean) {
  return apiFetchJson<ConversationDetail>(
    withOwnerId(
      `${conversationsBasePath(requestIdentityEnabled)}/${conversationId}`,
      ownerId,
      requestIdentityEnabled,
    ),
  );
}

export async function deleteConversation(conversationId: string, ownerId?: string, requestIdentityEnabled?: boolean) {
  await apiFetchOk(
    withOwnerId(
      `${conversationsBasePath(requestIdentityEnabled)}/${conversationId}`,
      ownerId,
      requestIdentityEnabled,
    ),
    { method: "DELETE" },
  );
}
