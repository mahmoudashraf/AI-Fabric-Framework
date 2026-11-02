import type { ChatMessage } from "./types";

/** Stored presentation is not evidence that the server still has the same pending action. */
export function restoreConfirmationHistory(messages: ChatMessage[]): ChatMessage[] {
  return messages.map((message) => message.resultType === "CONFIRMATION_REQUIRED"
    ? { ...message, confirmationReadOnly: true }
    : message);
}

export function isConfirmationActionable(
  message: ChatMessage | undefined,
  isLatest: boolean,
  status?: "pending" | "confirmed" | "rejected",
): boolean {
  return Boolean(message && message.type === "ai"
    && (!message.participantRole || message.participantRole === "ai")
    && message.resultType === "CONFIRMATION_REQUIRED"
    && !message.confirmationReadOnly && isLatest && !status);
}
