import type { AiBehaviourGroupKey, AiBehaviourMetadata, AiBehaviourSelections } from './api/platformApi'

export const AI_BEHAVIOUR_GROUP_KEYS: AiBehaviourGroupKey[] = ['tone', 'answerBreadth', 'answerLength', 'nextSteps']
export const AI_BEHAVIOUR_PROMPT_KEYS = [
  'systemPrompt', 'intentExtractionPrompt', 'actionSelectionPrompt', 'clarificationPrompt',
  'answerGenerationPrompt', 'retrievalPrompt', 'assistantUiPrompt',
] as const

export function promptConfigRecord(value: unknown): Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
    ? value as Record<string, unknown> : {}
}

export function readAiBehaviour(value: unknown): AiBehaviourMetadata | null {
  const metadata = promptConfigRecord(promptConfigRecord(value).aiBehaviour)
  return metadata.schemaVersion === 'loomai-ai-behaviour-v1' ? metadata as AiBehaviourMetadata : null
}

export function aiBehaviourPresetSelections(value: unknown): AiBehaviourSelections {
  const metadata = readAiBehaviour(value)
  return Object.fromEntries(AI_BEHAVIOUR_GROUP_KEYS.map(key => [key,
    metadata?.[key]?.presetId && metadata[key]?.presetId !== 'custom' ? metadata[key]!.presetId : '',
  ]))
}

export function aiBehaviourReviewInput(
  value: Record<string, unknown>, selections: AiBehaviourSelections,
  instructionEdits: Partial<Record<AiBehaviourGroupKey, string>>,
) {
  const metadata = readAiBehaviour(value)
  const promptConfig = { ...value }
  if (metadata) {
    promptConfig.aiBehaviour = { ...metadata, ...Object.fromEntries(
      AI_BEHAVIOUR_GROUP_KEYS.filter(key => instructionEdits[key] !== undefined && metadata[key])
        .map(key => [key, { ...metadata[key], instructions: instructionEdits[key] }]),
    ) }
  }
  const selected = Object.fromEntries(Object.entries(selections).filter(([, id]) => Boolean(id)))
  return { promptConfig, ...(Object.keys(selected).length ? { selections: selected } : {}) }
}

export function effectiveAiBehaviourPreview(value: Record<string, unknown>): Record<string, string> {
  return Object.fromEntries(AI_BEHAVIOUR_PROMPT_KEYS.flatMap(key =>
    typeof value[key] === 'string' && value[key].trim() ? [[key, value[key]]] : [],
  ))
}

// Compare configuration identity only; semantic decisions stay with the compiler/model.
export function promptConfigIdentity(value: unknown): string {
  if (Array.isArray(value)) return `[${value.map(promptConfigIdentity).join(',')}]`
  if (value && typeof value === 'object') {
    return `{${Object.entries(value).sort(([left], [right]) => left.localeCompare(right))
      .map(([key, item]) => `${JSON.stringify(key)}:${promptConfigIdentity(item)}`).join(',')}}`
  }
  return JSON.stringify(value) ?? 'undefined'
}

export function formatAiBehaviourChange(value: unknown): string {
  return typeof value === 'string' ? value : value === undefined || value === null ? '(not set)' : JSON.stringify(value, null, 2)
}
