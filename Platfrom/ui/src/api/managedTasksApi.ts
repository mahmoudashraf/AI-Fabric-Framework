import { getPlatformApiBaseUrl, getStoredPlatformApiKey, platformCredentials, PlatformApiError, platformRequest } from './platformApi'
import { parseExactJson, stringifyExactJson } from '../lib/losslessJson'
import { isJsonObject, type JsonObject } from '../actions/actionDefinitionModel'

export type AppliedManagedTask = {
  code: string; name: string; description: string; inputSchema: JsonObject
  outputMode: 'JSON' | 'TEXT'; outputSchema?: JsonObject; deadlineSeconds: number
}
export type AppliedManagedTasks = {
  configurationVersion: string; taskConfigurationHash: string; runtimeBaseUrl: string | null; tasks: AppliedManagedTask[]
}
export type ManagedTaskResult = {
  runId: string; taskCode: string; status: string; configurationVersion: string; taskConfigurationHash: string
  output: unknown; failure: { code: string; message: string } | null; evidence: unknown[]
  sideEffectsExecuted: false; startedAt: string; completedAt: string; outputSchemaVersion?: string
}
const path = (id: string, suffix: string, target: string) =>
  `/api/deployments/${encodeURIComponent(id)}/tasks/${suffix}?${new URLSearchParams({ targetProfileId: target })}`

export function fetchAppliedManagedTasks(id: string, target: string, signal?: AbortSignal) {
  return platformRequest<AppliedManagedTasks>(path(id, 'applied', target), { signal })
}

// Task deadlines can exceed the ordinary 30-second metadata timeout. Reuse first-party auth,
// but bound this one explicit inference request by the task deadline plus transport overhead.
export async function executeManagedTask(id: string, code: string, target: string,
  payload: { input: JsonObject; correlationId?: string }, deadlineSeconds: number, signal?: AbortSignal): Promise<ManagedTaskResult> {
  const endpoint = path(id, `${encodeURIComponent(code)}/execute`, target)
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
        && typeof decoded.status === 'string' && decoded.status !== 'SUCCEEDED'
        && isJsonObject(decoded.failure) && typeof decoded.failure.code === 'string' && typeof decoded.failure.message === 'string'
        && decoded.sideEffectsExecuted === false && Array.isArray(decoded.evidence)) return decoded as ManagedTaskResult
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
