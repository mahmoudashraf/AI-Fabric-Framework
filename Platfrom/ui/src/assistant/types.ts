export type AssistantTask = 'BRAIN_SETUP' | 'ACTIONS' | 'CONNECTIONS' | 'KNOWLEDGE' | 'SEARCH' | 'BEHAVIOUR' | 'MODELS' | 'HUMAN_SUPPORT' | 'SPECIALISTS' | 'KIT' | 'DATA_PLUGIN' | 'ENGAGE' | 'DOCUMENT_PREPARATION'
export type AssistantSection = AssistantTask | 'DOCUMENTS' | 'LIVE_DATA' | 'STORAGE' | 'INSTRUCTIONS' | 'ACTION_REVIEWS'
export type AssistantEditorContext = { section: AssistantSection; selectedDefinitionIds?: string[] }
export type AssistantScope = {
  resourceKind: 'DEPLOYMENT_DRAFT' | 'PRIVATE_DATA_PLUGIN' | 'ENGAGE_APP' | 'DOCUMENT_SOURCE'
  resourceId: string
  task: AssistantTask
  editorContext?: AssistantEditorContext
}
export type ValidationFeedbackPolicy = { enabled: boolean; maxCorrections: number }
export type AssistantExecutionMode = 'BOUNDED' | 'AUTONOMOUS'
export type AssistantGoalCommand = 'PAUSE' | 'RESUME' | 'CANCEL'
export type AssistantOperation = { operationId: string; action: string; status: string; published: boolean; savedAt?: string; receipt?: unknown; diff?: unknown; findings?: unknown; protectedBodyErased?: boolean }
export type AssistantOperationPage = { goalId: string; operations: AssistantOperation[]; offset: number; limit: number; total: number; hasMore: boolean; nextOffset?: number }
export type ProviderObservation = {
  operationId?: string | null; attemptId?: string | null; providerRequestId?: string | null
  operation?: 'CHAT_COMPLETION' | 'EMBEDDING' | 'UNSUPPORTED' | null; model?: string | null; responseModel?: string | null
  effectiveOutputCap?: number | null; finishReason?: string | null; inputTokens?: number | null; outputTokens?: number | null
  reasoningTokens?: number | null; contentLength?: number | null; contentEmpty?: boolean | null
}
export type AssistantProviderDiagnostics = { observations: ProviderObservation[]; droppedObservations?: number }
export type AssistantGoal = {
  id: string; turnId: string; executionMode: 'AUTONOMOUS'; state: string; reasonCode?: string | null
  authorityId?: string; goalId?: string; sessionId?: string
  terminal?: boolean
  reasonMessage?: string | null; nextAction?: string | null; cancellationEpoch?: number
  latestRequestUpdate?: { query: string; requestRevision: number; createdAt: string }
  originalRequest: string; resourceKind: AssistantScope['resourceKind']; resourceId: string; task: AssistantTask
  profileId?: string; profileName?: string; releaseId?: string; bundleHash?: string
  sourceIds?: string[]; operationIds?: string[]; operationCount?: number
  requestRevision: number; segment: number; workId?: string; nextEligibleAt?: string; updatedAt?: string
  canPause: boolean; canResume: boolean; canCancel: boolean
  progress?: {
    summary?: string
    workItems?: { id: string; label: string; state: string; operationId?: string; message?: string }[]
    remaining?: string[]; coverage?: { completed: number; total: number }
  }
  usage?: { actions?: number; writes?: number; modelCalls?: number; corrections?: number; [key: string]: unknown }
  operations?: AssistantOperation[]
  providerDiagnostics?: AssistantProviderDiagnostics
}
export type AssistantStatus = {
  enabled: boolean; ready: boolean; documentFetchEnabled: boolean; unavailableReason?: string
  autonomousEnabled?: boolean; autonomousUnavailableReason?: string
  executionModes?: AssistantExecutionMode[]
  engine?: 'LEGACY' | 'CONFIGURED_BRAIN'
  validationFeedback?: { defaultEnabled: boolean; maxCorrections: number }
}
export type TurnStatus = 'SAVED' | 'PREVIEW' | 'NEEDS_INPUT' | 'EXPLAIN' | 'UNSUPPORTED' | 'INVALID' | 'FAILED' | 'CANCELLED' | 'RUNNING' | 'PAUSED' | 'BLOCKED' | 'UNDONE' | 'READY'
export type AssistantSource = { id: string; title: string; mediaType: string; operations: unknown[]; warnings: string[]; characters: number }
export type AssistantTurn = {
  id: string; query: string; status: TurnStatus; message: string
  command?: 'PREPARE'; hint?: string; editorContext?: AssistantEditorContext
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
  providerDiagnostics?: AssistantProviderDiagnostics
  operations?: AssistantOperation[]
  workflow?: { workId?: string; state?: string; stopCode?: string; deadline?: string
    selection?: { profileId: string; name: string; specialistRef: string; releaseId: string; bundleHash: string }
    limits?: { actions: number; writes: number; modelCalls: number; corrections: number }
    usage?: { actions: number; writes: number; modelCalls: number; corrections: number }; steps?: { operationId: string; action: string; write: boolean; effect: string }[] }
}
export type AssistantSession = AssistantScope & {
  id: string; status: string; displayName: string; editable: boolean; readOnlyReason?: string
  activeTurnId?: string | null; expiresAt: string; sources: AssistantSource[]; turns: AssistantTurn[]
  engine?: 'LEGACY' | 'CONFIGURED_BRAIN'
  goal?: AssistantGoal | null
}
export type SourceInput = { title: string; mediaType: string; content?: string; base64?: string; url?: string }
export type ReusableSource = { sessionId: string; sourceId: string; title: string; task: string; mediaType: string; resourceKind: AssistantScope['resourceKind']; resourceId: string }
export type TurnInput = {
  query: string; previewOnly: boolean; sourceIds: string[]; operationIds: string[]; validationFeedback?: ValidationFeedbackPolicy
  command?: 'PREPARE'; editorContext?: AssistantEditorContext
  executionMode?: AssistantExecutionMode
}
