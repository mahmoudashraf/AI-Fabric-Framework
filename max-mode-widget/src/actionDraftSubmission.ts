export type ActionDraftSubmission = {
  action: string;
  parameters: Record<string, unknown>;
};

export function withActionDraftSubmission<T extends Record<string, unknown>>(
  payload: T,
  action: string,
  parameters: Record<string, unknown>,
): T & { actionDraftSubmission: ActionDraftSubmission } {
  const normalizedAction = action.trim();
  if (!normalizedAction) {
    throw new Error("Action draft submission requires an action name.");
  }
  return {
    ...payload,
    actionDraftSubmission: {
      action: normalizedAction,
      parameters: { ...parameters },
    },
  };
}
