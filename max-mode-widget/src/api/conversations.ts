import type { Conversation, ConversationDetail } from "../types";

import { apiFetchJson, apiFetchOk } from "./client";

export function listConversations(ownerId: string) {
  return apiFetchJson<Conversation[]>(`/chat/conversations?ownerId=${encodeURIComponent(ownerId)}`);
}

export function getConversation(conversationId: string, ownerId: string) {
  return apiFetchJson<ConversationDetail>(`/chat/conversations/${conversationId}?ownerId=${encodeURIComponent(ownerId)}`);
}

export async function deleteConversation(conversationId: string, ownerId: string) {
  await apiFetchOk(`/chat/conversations/${conversationId}?ownerId=${encodeURIComponent(ownerId)}`, { method: "DELETE" });
}

