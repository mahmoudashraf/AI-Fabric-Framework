export type AssistantTask = 'ACTIONS' | 'CONNECTIONS' | 'KNOWLEDGE' | 'BEHAVIOUR' | 'MODELS' | 'HUMAN_SUPPORT' | 'SPECIALISTS' | 'KIT' | 'DATA_PLUGIN' | 'ENGAGE'
export type AssistantScope = {
  resourceKind: 'DEPLOYMENT_DRAFT' | 'PRIVATE_DATA_PLUGIN' | 'ENGAGE_APP'
  resourceId: string
  task: AssistantTask
}
export type ValidationFeedbackPolicy = { enabled: boolean; maxCorrections: number }
export type AssistantStatus = {
  enabled: boolean; ready: boolean; documentFetchEnabled: boolean; unavailableReason?: string
  validationFeedback?: { defaultEnabled: boolean; maxCorrections: number }
}
export type TurnStatus = 'SAVED' | 'PREVIEW' | 'NEEDS_INPUT' | 'EXPLAIN' | 'UNSUPPORTED' | 'INVALID' | 'FAILED' | 'CANCELLED' | 'RUNNING' | 'UNDONE' | 'READY'
export type AssistantSource = { id: string; title: string; mediaType: string; operations: unknown[]; warnings: string[]; characters: number }
export type AssistantTurn = {
  id: string; query: string; status: TurnStatus; message: string
  changes?: unknown; diff?: unknown; questions?: { id: string; question: string; field?: string }[]
  findings?: unknown; validation?: unknown; receipt?: unknown; undoReceipt?: unknown; createdAt: string; usage?: unknown
  assumptions?: unknown; sourceReferences?: unknown; diagnostics?: unknown; notice?: string
  runtime?: { requestId?: string; modelCalls?: unknown; accountingReceipt?: unknown; usage?: unknown; [key: string]: unknown }
  validationFeedback?: ValidationFeedbackPolicy
  attempts?: {
    number: number; status: string; startedAt?: string; completedAt?: string; message?: string
    findings?: unknown; changes?: unknown; runtime?: unknown
  }[]
  feedbackStopReason?: string
}
export type AssistantSession = AssistantScope & {
  id: string; status: string; displayName: string; editable: boolean; readOnlyReason?: string
  activeTurnId?: string | null; expiresAt: string; sources: AssistantSource[]; turns: AssistantTurn[]
}
export type SourceInput = { title: string; mediaType: string; content?: string; base64?: string; url?: string }
export type ReusableSource = { sessionId: string; sourceId: string; title: string; task: string; mediaType: string }
export type TurnInput = { query: string; previewOnly: boolean; sourceIds: string[]; operationIds: string[]; validationFeedback?: ValidationFeedbackPolicy }
