import type { Conversation, ConversationDetail } from "../types";

import { apiFetchJson, apiFetchOk } from "./client";

function withOwnerId(path: string, ownerId?: string) {
  if (!ownerId) {
    return path;
  }
  const separator = path.includes("?") ? "&" : "?";
  return `${path}${separator}ownerId=${encodeURIComponent(ownerId)}`;
}

export function listConversations(ownerId?: string) {
  return apiFetchJson<Conversation[]>(withOwnerId(`/chat/conversations`, ownerId));
}

export function getConversation(conversationId: string, ownerId?: string) {
  return apiFetchJson<ConversationDetail>(withOwnerId(`/chat/conversations/${conversationId}`, ownerId));
}

export async function deleteConversation(conversationId: string, ownerId?: string) {
  await apiFetchOk(withOwnerId(`/chat/conversations/${conversationId}`, ownerId), { method: "DELETE" });
}
