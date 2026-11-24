import type { AssistantProviderDiagnostics, ProviderObservation } from './types'

export const providerFieldLabels = {
  operationId: 'Operation ID', attemptId: 'Attempt ID', providerRequestId: 'Provider request ID', operation: 'Operation',
  model: 'Model', responseModel: 'Response model', effectiveOutputCap: 'Output cap', finishReason: 'Finish reason',
  inputTokens: 'Input tokens', outputTokens: 'Output tokens', reasoningTokens: 'Reasoning tokens',
  contentLength: 'Content characters', contentEmpty: 'Empty content',
} as const
const strings = new Set(['operationId', 'attemptId', 'providerRequestId', 'model', 'responseModel', 'finishReason'])
const numbers = new Set(['effectiveOutputCap', 'inputTokens', 'outputTokens', 'reasoningTokens', 'contentLength'])
const record = (value: unknown): value is Record<string, unknown> => Boolean(value && typeof value === 'object' && !Array.isArray(value))

export function providerDiagnostics(value: unknown): AssistantProviderDiagnostics | null {
  if (!record(value) || !Array.isArray(value.observations)) return null
  // Provider exchange metadata only. Never forward unknown fields or nested payloads to the renderer.
  const observations = value.observations.slice(-32).filter(record).map(observation => {
    const safe: Record<string, string | number | boolean | null> = {}
    for (const key of Object.keys(providerFieldLabels)) {
      const field = observation[key]
      if (field === null || strings.has(key) && typeof field === 'string' && field.length <= 256
        || numbers.has(key) && typeof field === 'number' && Number.isSafeInteger(field) && field >= 0
        || key === 'contentEmpty' && typeof field === 'boolean'
        || key === 'operation' && ['CHAT_COMPLETION', 'EMBEDDING', 'UNSUPPORTED'].includes(field as string)) safe[key] = field as string | number | boolean | null
    }
    return safe as ProviderObservation
  }).filter(observation => Object.keys(observation).length > 0)
  const dropped = value.droppedObservations
  return { observations, ...(typeof dropped === 'number' && Number.isSafeInteger(dropped) && dropped >= 0 ? { droppedObservations: dropped } : {}) }
}
