import type { ChatMessage, ConversationDetail, RuntimeConversationMessage, ResultType } from "./types";
import { canonicalChatResult } from "./chatResult";

const RESTORABLE_RESULT_TYPES: ResultType[] = [
  "INFORMATION_PROVIDED", "ACTION_EXECUTED", "ACTION_DENIED", "COMPOUND_HANDLED", "OUT_OF_SCOPE", "ERROR",
];

/** Canonical message IDs bind read-only presentations; no query replay or text correlation. */
export function toChatMessages(data: ConversationDetail, conversationId: string): ChatMessage[] {
  if (Array.isArray(data.participantMessages) && data.participantMessages.length > 0) {
    const hasPresentationHistory = Array.isArray(data.completedTurns);
    const userIds = new Set(data.participantMessages.filter((message) => message.role === "USER").map((message) => message.messageId));
    const completed = new Map((data.completedTurns || [])
      .filter((turn) => turn.aiMessageId && userIds.has(turn.userMessageId))
      .map((turn) => [turn.aiMessageId, turn]));
    return [...data.participantMessages]
      .sort((left, right) => left.sequence - right.sequence)
      .map((message) => {
        const restored = toChatMessage(message);
        if (message.role !== "AI") return restored;
        const unavailable = hasPresentationHistory ? { ...restored, historyPresentationUnavailable: true } : restored;
        const turn = completed.get(message.messageId);
        if (!turn) return unavailable;
        const response = turn.response;
        if (!response || typeof response !== "object" || Array.isArray(response)
          || (response.conversationId && response.conversationId !== conversationId)) return unavailable;
        if (!RESTORABLE_RESULT_TYPES.includes(response.type || "INFORMATION_PROVIDED")) return unavailable;
        const result = canonicalChatResult({
          type: response.type, success: response.success,
          // Canonical text wins even when the retained response contains a different answer.
          answer: message.content, safeSummary: message.content,
          sources: response.sources, ragResponse: response.ragResponse,
          actions: response.actions, results: response.results,
        });
        if (!result) return unavailable;
        return {
          ...restored,
          ...(result ? {
            result,
            resultType: result.type,
            success: result.success,
          } : {}),
          ...(result?.sanitizedPayload.data?.documents ? {
            documents: result.sanitizedPayload.data.documents.map((document) => ({ ...document, messageId: message.messageId })),
          } : {}),
        };
      });
  }

  return (data.turns || []).flatMap((turn, index): ChatMessage[] => [
    { id: `${conversationId}-user-${index}`, type: "user", content: turn.userQuery, timestamp: turn.timestamp },
    { id: `${conversationId}-ai-${index}`, type: "ai", participantRole: "ai", content: turn.aiResponse, timestamp: turn.timestamp },
  ]);
}

function toChatMessage(message: RuntimeConversationMessage): ChatMessage {
  const role = message.role.toLowerCase() as "user" | "ai" | "human" | "system";
  return {
    id: message.messageId,
    type: role === "user" ? "user" : "ai",
    ...(role === "user" ? {} : { participantRole: role }),
    ...(message.participantDisplayName ? { participantDisplayName: message.participantDisplayName } : {}),
    content: message.content,
    timestamp: message.createdAt,
    success: true,
  };
}
