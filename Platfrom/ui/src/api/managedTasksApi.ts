import { getPlatformApiBaseUrl, getStoredPlatformApiKey, platformCredentials, PlatformApiError, platformRequest, type DeploymentHumanReviewTask } from './platformApi'
import { parseExactJson, stringifyExactJson } from '../lib/losslessJson'
import { isJsonObject, type JsonObject } from '../actions/actionDefinitionModel'

export type AppliedManagedTask = {
  code: string; name: string; description: string; inputSchema: JsonObject
  outputMode: 'JSON' | 'TEXT'; outputSchema?: JsonObject; deadlineSeconds: number
  confirmationMode?: 'CALLER' | 'HUMAN_REVIEW'; writeActions?: string[]
}
export type AppliedManagedTasks = {
  configurationVersion: string; taskConfigurationHash: string; runtimeBaseUrl: string | null; tasks: AppliedManagedTask[]
}
export type ManagedTaskResources = {
  reviewAvailable: boolean
  knowledgeCollections: { id: string; name: string }[]
  actions: { id: string; name: string; accessMode: 'READ' | 'WRITE'; confirmationRequired: boolean }[]
  specialists: { ref: string; name: string; description: string; inputSchema: JsonObject; outputSchema: JsonObject
    outputMode: 'JSON'; vectorSpaces: string[]; readActions: string[]; writeActions: string[]
    deadlineSeconds: number; maxOutputTokens: number }[]
}
export type ManagedTaskDecision = {
  taskCode: string; configurationVersion: string; taskConfigurationHash: string; status: string
  decision: { receiptId: string; status: string | null; outcome?: unknown
    failure?: { reason: string; publicMessage: string; retryable: boolean } | null } | null
  actionExecutions: NonNullable<ManagedTaskResult['actionExecutions']>
  failure: ManagedTaskResult['failure']
}
export type ManagedTaskResult = {
  runId: string; taskCode: string; status: string; configurationVersion: string; taskConfigurationHash: string
  output: unknown; failure: { code: string; message: string } | null; evidence: unknown[]
  actionProposal?: { receiptId: string; actionName: string; confirmationMessage: string; status: string; createdAt: string; expiresAt: string } | null
  actionExecutions?: { executionId: string; actionName: string; status: string; outcome?: unknown; startedAt: string; completedAt?: string }[]
  sideEffectsStatus?: 'NONE' | 'EXECUTED' | 'OUTCOME_UNKNOWN'
  review?: DeploymentHumanReviewTask | null
  inputRequirement?: { message: string } | null
  sideEffectsExecuted: boolean; startedAt: string; completedAt: string; outputSchemaVersion?: string
  expiresAt?: string
}
const path = (id: string, suffix: string, target: string, expectedVersion?: string) =>
  `/api/deployments/${encodeURIComponent(id)}/tasks/${suffix}?${new URLSearchParams({ targetProfileId: target, ...(expectedVersion ? { expectedVersion } : {}) })}`

export function fetchAppliedManagedTasks(id: string, target: string, signal?: AbortSignal) {
  return platformRequest<AppliedManagedTasks>(path(id, 'applied', target), { signal })
}

export function fetchManagedTaskResources(id: string, signal?: AbortSignal) {
  return platformRequest<ManagedTaskResources>(`/api/deployments/${encodeURIComponent(id)}/tasks/resources`, { signal })
}

export function taskResultMatchesContext(value: Pick<ManagedTaskResult, 'taskCode' | 'configurationVersion' | 'taskConfigurationHash'>,
  code: string, version: string, hash: string): boolean {
  return value.taskCode === code && value.configurationVersion === version && value.taskConfigurationHash === hash
}

export async function decideManagedTaskProposal(id: string, code: string, target: string, receiptId: string,
  decision: 'CONFIRM' | 'REJECT', context: { configurationVersion: string; taskConfigurationHash: string; deadlineSeconds?: number },
  signal?: AbortSignal): Promise<ManagedTaskDecision> {
  const endpoint = path(id, `${encodeURIComponent(code)}/action-proposals/${encodeURIComponent(receiptId)}/decisions`, target, context.configurationVersion)
  signal?.throwIfAborted()
  const auth = await platformCredentials(endpoint, getStoredPlatformApiKey())
  signal?.throwIfAborted()
  const controller = new AbortController()
  const abort = () => controller.abort()
  signal?.addEventListener('abort', abort, { once: true })
  const timeout = setTimeout(abort, (Math.min(120, Math.max(5, context.deadlineSeconds || 45)) + 15) * 1000)
  try {
    const response = await fetch(`${getPlatformApiBaseUrl()}${endpoint}`, {
      method: 'POST', credentials: auth.credentials, redirect: 'error', signal: controller.signal,
      headers: { 'Content-Type': 'application/json', ...auth.headers }, body: stringifyExactJson({ decision }),
    })
    const source = await response.text()
    let value: unknown = null
    try { value = parseExactJson(source) } catch { /* Ordinary Platform errors retain their HTTP category below. */ }
    // Receipts and explicit uncertainty must belong to this exact applied task. HTTP status
    // alone cannot establish whether a write completed or whether a decision was rejected.
    if (isJsonObject(value) && taskResultMatchesContext(value as ManagedTaskDecision, code, context.configurationVersion, context.taskConfigurationHash)
      && typeof value.status === 'string' && Array.isArray(value.actionExecutions)) {
      const failure = isJsonObject(value.failure) && typeof value.failure.code === 'string' && typeof value.failure.message === 'string'
      const receipt = value.decision
      if ((value.failure === null || failure) && (isJsonObject(receipt) && receipt.receiptId === receiptId
        && (typeof receipt.status === 'string' || receipt.status === null && isJsonObject(receipt.failure)
          && typeof receipt.failure.reason === 'string' && typeof receipt.failure.publicMessage === 'string')
        || receipt === null && (failure || value.status === 'OUTCOME_UNKNOWN'))) return value as ManagedTaskDecision
    }
    if (!response.ok && (!isJsonObject(value) || !('taskCode' in value))) {
      throw new PlatformApiError(response.status,
        isJsonObject(value) && typeof value.message === 'string' ? value.message : `Action decision request failed with status ${response.status}.`,
        isJsonObject(value) && typeof value.errorCode === 'string' ? value.errorCode : null)
    }
    throw new Error('The action decision response did not match the applied task and receipt. The action outcome is unknown; no automatic retry was sent.')
  } catch (error) {
    if (controller.signal.aborted) throw new Error(signal?.aborted
      ? 'Stopped waiting for the action decision. The action outcome is unknown; no automatic retry was sent.'
      : 'Action decision request timed out. The action outcome is unknown; no automatic retry was sent.')
    throw error
  } finally {
    clearTimeout(timeout)
    signal?.removeEventListener('abort', abort)
  }
}

// Task deadlines can exceed the ordinary 30-second metadata timeout. Reuse first-party auth,
// but bound this one explicit inference request by the task deadline plus transport overhead.
export async function executeManagedTask(id: string, code: string, target: string,
  payload: { input: JsonObject; correlationId?: string }, deadlineSeconds: number, signal?: AbortSignal,
  expectedVersion?: string): Promise<ManagedTaskResult> {
  const endpoint = path(id, `${encodeURIComponent(code)}/execute`, target, expectedVersion)
  signal?.throwIfAborted()
  const auth = await platformCredentials(endpoint, getStoredPlatformApiKey())
  signal?.throwIfAborted()
  const controller = new AbortController()
  const abort = () => controller.abort()
  signal?.addEventListener('abort', abort, { once: true })
  const timeout = setTimeout(abort, (Math.min(120, Math.max(5, deadlineSeconds || 45)) + 15) * 1000)
  try {
    const response = await fetch(`${getPlatformApiBaseUrl()}${endpoint}`, {
      method: 'POST', credentials: auth.credentials, redirect: 'error', signal: controller.signal,
      headers: { 'Content-Type': 'application/json', ...auth.headers }, body: stringifyExactJson(payload),
    })
    const source = await response.text()
    let decoded: unknown = null
    try { decoded = parseExactJson(source) } catch { /* Platform error handling below retains the HTTP category. */ }
    if (!response.ok) {
      if (isJsonObject(decoded) && decoded.taskCode === code && typeof decoded.runId === 'string'
        && typeof decoded.status === 'string'
        && typeof decoded.configurationVersion === 'string' && typeof decoded.taskConfigurationHash === 'string'
        && typeof decoded.sideEffectsExecuted === 'boolean' && Array.isArray(decoded.evidence)
        && (isJsonObject(decoded.failure) || ['CONFIRMATION_REQUIRED', 'REVIEW_REQUIRED', 'INPUT_REQUIRED'].includes(decoded.status)
          || Array.isArray(decoded.actionExecutions))) return decoded as ManagedTaskResult
      let message = `Task request failed with status ${response.status}.`
      let errorCode: string | null = null
      try {
        const body = JSON.parse(source)
        message = body.message ?? body.error ?? message
        errorCode = typeof body.errorCode === 'string' ? body.errorCode : null
      } catch { /* Do not render arbitrary upstream response pages. */ }
      throw new PlatformApiError(response.status, message, errorCode)
    }
    if (!isJsonObject(decoded) || typeof decoded.runId !== 'string' || decoded.taskCode !== code || typeof decoded.status !== 'string') {
      throw new Error('The task service returned an invalid response. No automatic retry was sent.')
    }
    return decoded as ManagedTaskResult
  } catch (error) {
    if (controller.signal.aborted) throw new Error(signal?.aborted
      ? 'Stopped waiting. The runtime may still finish this task; no automatic retry was sent.'
      : 'Task request timed out. The runtime may still finish it; no automatic retry was sent.')
    throw error
  } finally {
    clearTimeout(timeout)
    signal?.removeEventListener('abort', abort)
  }
}
