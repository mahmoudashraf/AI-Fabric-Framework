import type { ChatMessage } from "./types";

export type ClarificationState = "active" | "superseded" | "cancelled";

function actionName(value: unknown): string | undefined {
  return typeof value === "string" && value.trim() ? value.trim() : undefined;
}

/** Messages must be the ordered history of the current conversation, not a mixed portfolio. */
export function clarificationStates(messages: readonly ChatMessage[]): ReadonlyMap<string, ClarificationState> {
  const states = new Map<string, ClarificationState>();
  const active = new Map<string, string>();
  for (const message of messages) {
    if (message.type !== "ai" || (message.participantRole && message.participantRole !== "ai")) continue;
    const payload = message.result?.sanitizedPayload;
    const data = payload?.data;
    if (message.resultType === "CLARIFICATION_REQUIRED") {
      const action = actionName(data?.action);
      if (!action) continue;
      const previous = active.get(action);
      if (previous) states.set(previous, "superseded");
      states.set(message.id, "active");
      active.set(action, message.id);
    }
    const actions = [data, ...(Array.isArray(data?.actions) ? data.actions : [])];
    const successful = message.success !== false && message.result?.success === true && payload?.success === true;
    for (const evidence of actions) {
      const action = actionName(evidence?.action);
      if (!action) continue;
      // A pending confirmation normally has success:false; its explicit marker supersedes intake.
      const confirmation = message.resultType === "CONFIRMATION_REQUIRED" && evidence.confirmationRequired === true;
      const executed = message.resultType === "ACTION_EXECUTED" && successful && evidence.actionResult?.success !== false;
      const cancelled = message.resultType === "INFORMATION_PROVIDED" && successful && evidence.actionDraftCancelled === true;
      if (!confirmation && !executed && !cancelled) continue;
      const previous = active.get(action);
      if (previous) states.set(previous, cancelled ? "cancelled" : "superseded");
      active.delete(action);
    }
  }
  return states;
}
