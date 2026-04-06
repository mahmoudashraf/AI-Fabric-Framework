import { apiFetchJson, apiFetchResponse } from "./client";

export type SuggestionsResponse = {
  success?: boolean;
  message?: string;
  suggestions?: string[];
  raw?: any;
};

export async function getChatSuggestions(payload: {
  content: string;
  userId: string;
  maxSuggestions: number;
  attachments?: any[];
}) {
  return apiFetchJson<SuggestionsResponse>(`/chat/suggestions`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
}

export async function postChatQuery(payload: any) {
  const startedAt = performance.now();
  const response = await apiFetchResponse(`/chat/query`, {
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

