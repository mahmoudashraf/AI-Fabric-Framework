import type { AssistantGoal, AssistantOperation, AssistantScope, AssistantSession, AssistantStatus } from './types'

export function autonomousAvailability(status?: AssistantStatus) {
  // New goal admission requires the published opt-in, including during rollback.
  const enabled = status?.autonomousEnabled === true
  return { enabled, reason: enabled ? '' : status?.autonomousUnavailableReason || (status?.autonomousEnabled === false
    ? 'Autonomous is not enabled in the published assistant. Quick edit remains available.'
    : 'Autonomous availability is not reported by this server. Quick edit remains available.') }
}

export const goalRunning = (goal?: AssistantGoal | null) => Boolean(goal && !goal.terminal && ['QUEUED', 'RUNNING', 'YIELDED'].includes(goal.state))
export const goalOpen = (goal?: AssistantGoal | null) => Boolean(goal && !goal.terminal && !['COMPLETE', 'CANCELLED', 'FAILED'].includes(goal.state))
export const sessionRunning = (session: AssistantSession | null) => Boolean(session && (goalRunning(session.goal)
  || session.activeTurnId && session.activeTurnId !== session.goal?.turnId
  || !session.goal && session.status === 'RUNNING'
  || session.turns.some(turn => turn.status === 'RUNNING' && turn.id !== session.goal?.turnId)))
export const goalStatus = (goal: AssistantGoal) => goal.terminal && goal.state === 'PAUSED' ? 'Preparation stopped' : ({
  QUEUED: 'Queued', RUNNING: 'Preparing draft', YIELDED: 'Continuing preparation',
  PAUSED: 'Paused', WAITING_FOR_INPUT: 'Needs your input', BLOCKED: 'Remaining work blocked',
  COMPLETE: 'Draft preparation complete', CANCELLED: 'Cancelled', FAILED: 'Work stopped',
}[goal.state] ?? goal.state)

export function goalNextAction(goal: AssistantGoal): string | undefined {
  if (!goal.nextAction) return
  return ({ RESUME_AFTER_REMEDIATION: 'Resolve the reported issue, then resume.',
    ANSWER_AND_RESUME: 'Answer the outstanding question to continue.',
    VIEW_RESULT: 'Review the recorded result and remaining work.' } as Record<string, string>)[goal.nextAction] ?? goal.nextAction
}

export function goalPauseMessage(goal: AssistantGoal): string | undefined {
  if (goal.state === 'WAITING_FOR_INPUT') return 'Answer the outstanding question to continue the same goal.'
  if (goal.state !== 'PAUSED') return
  const reasons: Record<string, string> = {
    USER_PAUSED: 'Paused by you. Resume when ready.', USER_REQUESTED: 'Paused by you. Resume when ready.',
    FUNDING_EXHAUSTED: 'Paused for the existing funding allowance. Funding changes stay separate.',
    BUDGET_EXHAUSTED: 'Paused for the existing budget allowance. Funding changes stay separate.',
    TOKEN_BUDGET_EXHAUSTED: 'Paused for the existing token allowance.',
    AUTHORITY_EXPIRED: 'Paused for authentication. Sign in again, then resume.',
    AUTHENTICATION_REQUIRED: 'Paused for authentication. Sign in again, then resume.',
    REAUTHENTICATION_REQUIRED: 'Paused for authentication. Sign in again, then resume.',
    LOGOUT_REAUTHENTICATION_REQUIRED: 'Paused after sign-out. Sign in again, then resume.',
    AUTHORING_REAUTHENTICATION_REQUIRED: 'Paused for authentication. Sign in again, then resume.',
    AUTHORITY_UNAVAILABLE: 'Paused because the original work authorization is unavailable.',
    AUTHORING_AUTHORITY_UNAVAILABLE: 'Paused because the original work authorization is unavailable.',
    PROVIDER_UNAVAILABLE: 'Paused because the AI provider is unavailable.',
    SERVICE_UNAVAILABLE: 'Paused because the authoring service is unavailable.',
    NATIVE_STATUS_UNAVAILABLE: 'Paused because the authoring service status is unavailable.',
    AUTHORING_HOST_RESTORATION_UNAVAILABLE: 'Paused because the authoring service could not restore this work.',
    WORKFLOW_PLANNER_UNAVAILABLE: 'Paused because the AI planner is unavailable.',
    WORKFLOW_NO_PROGRESS: 'Paused after no further progress. Saved changes are retained.',
    WORKFLOW_GOAL_DEADLINE: 'Paused because the published goal lifetime ended. Saved changes are retained.',
    GOAL_DEADLINE_EXCEEDED: 'The published goal lifetime ended. Saved changes are retained.',
    WORKFLOW_PROFILE_CHANGED: 'Paused because the pinned assistant profile changed.',
    NO_PROGRESS: 'Paused after no further progress. Saved changes are retained.',
    WRITE_OUTCOME_UNKNOWN: 'Paused while a save outcome is unresolved. Saved operations will not be replayed.',
    RELEASE_UNAVAILABLE: 'Paused because the pinned assistant release is unavailable.',
  }
  return goal.reasonCode && reasons[goal.reasonCode] || 'Preparation is paused. Saved changes and remaining work are retained.'
}

export function goalCoverage(goal: AssistantGoal) {
  const coverage = goal.progress?.coverage
  if (!coverage || !Number.isSafeInteger(coverage.completed) || !Number.isSafeInteger(coverage.total)
    || coverage.completed < 0 || coverage.total < 0 || coverage.completed > coverage.total) return null
  return coverage
}

export function savedGoalOperations(session: AssistantSession): AssistantOperation[] {
  const operations = new Map<string, AssistantOperation>()
  const turns = session.goal ? session.turns.filter(turn => turn.id === session.goal!.turnId) : []
  for (const operation of [...turns.flatMap(turn => turn.operations ?? []), ...(session.goal?.operations ?? [])]) {
    if (operation.status === 'EXECUTED') operations.set(operation.operationId, operation)
  }
  return [...operations.values()]
}

export const sessionScope = (session: AssistantSession): AssistantScope => ({
  resourceKind: session.resourceKind, resourceId: session.resourceId, task: session.task,
  editorContext: session.turns.find(turn => turn.id === session.goal?.turnId)?.editorContext ?? session.editorContext,
})

export function defaultExecutionMode(scope: AssistantScope, preparing: boolean, selectedOperations: number) {
  return scope.task === 'BRAIN_SETUP' || selectedOperations > 1 || preparing && ['ACTIONS', 'CONNECTIONS', 'DATA_PLUGIN', 'DOCUMENT_PREPARATION'].includes(scope.task) ? 'AUTONOMOUS' as const : 'BOUNDED' as const
}
