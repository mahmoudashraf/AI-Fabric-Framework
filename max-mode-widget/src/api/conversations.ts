import type { Conversation, ConversationDetail } from "../types";

import { apiFetchJson, apiFetchOk } from "./client";

function conversationsBasePath(requestIdentityEnabled?: boolean) {
  return requestIdentityEnabled === false ? "/chat/me/conversations" : "/chat/conversations";
}

function withOwnerId(path: string, ownerId?: string) {
  if (!ownerId) {
    return path;
  }
  const separator = path.includes("?") ? "&" : "?";
  return `${path}${separator}ownerId=${encodeURIComponent(ownerId)}`;
}

export function listConversations(ownerId?: string, requestIdentityEnabled?: boolean) {
  return apiFetchJson<Conversation[]>(withOwnerId(conversationsBasePath(requestIdentityEnabled), ownerId));
}

export function getConversation(conversationId: string, ownerId?: string, requestIdentityEnabled?: boolean) {
  return apiFetchJson<ConversationDetail>(
    withOwnerId(`${conversationsBasePath(requestIdentityEnabled)}/${conversationId}`, ownerId),
  );
}

export async function deleteConversation(conversationId: string, ownerId?: string, requestIdentityEnabled?: boolean) {
  await apiFetchOk(
    withOwnerId(`${conversationsBasePath(requestIdentityEnabled)}/${conversationId}`, ownerId),
    { method: "DELETE" },
  );
}
