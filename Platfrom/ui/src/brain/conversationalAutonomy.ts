export const autonomyLimitFields = [
  ['maxElapsedSeconds', 'Time per request (seconds)', 5, 300, 120],
  ['segmentSeconds', 'Time per segment (seconds)', 5, 120, 30],
  ['maxActionsPerSegment', 'Tools per segment', 1, 64, 8],
  ['maxWritesPerSegment', 'Writes per segment', 0, 64, 3],
  ['maxModelCallsPerSegment', 'Model calls per segment', 1, 64, 12],
  ['maxCorrectionsPerSegment', 'Repairs per segment', 0, 8, 3],
  ['maxConsecutiveNoProgress', 'Consecutive steps without progress', 1, 100, 8],
  ['maxConsecutivePlannerFailures', 'Consecutive planning failures', 1, 20, 4],
] as const

export function autonomyRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {}
}

export function readConversationalAutonomy(provider: unknown) {
  return autonomyRecord(autonomyRecord(provider).conversationalAutonomy)
}

export function mergeConversationalAutonomy(provider: unknown, changes: Record<string, unknown>) {
  const result = { ...autonomyRecord(provider) }
  if (result.conversationalAutonomy != null && (typeof result.conversationalAutonomy !== 'object' || Array.isArray(result.conversationalAutonomy))) {
    throw new Error('The saved conversation policy is not a supported object. Review its Advanced configuration first.')
  }
  const policy = { ...readConversationalAutonomy(provider) }
  for (const [key, value] of Object.entries(changes)) {
    if (value === null) delete policy[key]
    else policy[key] = value
  }
  result.conversationalAutonomy = policy
  return result
}

export function conversationalAutonomyError(policy: Record<string, unknown>): string | null {
  for (const field of ['enabled', 'defaultEnabled', 'replanNonAuthorityReadFailures']) {
    if (policy[field] != null && typeof policy[field] !== 'boolean') return `${field} must be true or false.`
  }
  for (const [field, label, min, max] of autonomyLimitFields) {
    const value = policy[field]
    if (value != null && (typeof value !== 'number' || !Number.isInteger(value) || value < min || value > max)) return `${label} must be between ${min} and ${max}.`
  }
  if (Number(policy.maxWritesPerSegment ?? 3) > Number(policy.maxActionsPerSegment ?? 8)) return 'The write limit cannot exceed the tool limit.'
  if (policy.instructions != null && (typeof policy.instructions !== 'string' || policy.instructions.length > 8000)) return 'Instructions must be text up to 8000 characters.'
  return null
}
