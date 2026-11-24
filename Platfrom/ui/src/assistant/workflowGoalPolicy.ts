export type WorkflowGoalPolicy = {
  enabled: boolean; maxElapsedSeconds?: number; continuationDelaySeconds?: number
  maxConsecutiveNoProgress?: number; maxConsecutivePlannerFailures?: number; [key: string]: unknown
}

export const newGoalPolicy: WorkflowGoalPolicy = {
  enabled: true, maxElapsedSeconds: 86400, continuationDelaySeconds: 1,
  maxConsecutiveNoProgress: 8, maxConsecutivePlannerFailures: 4,
}

// Published compiler ceilings: segment bounds schedule continuation, not total goal calls.
export const goalPolicyFields = [
  ['maxElapsedSeconds', 'Goal lifetime (seconds)', 5, 2592000],
  ['continuationDelaySeconds', 'Continuation delay (seconds)', 0, 3600],
  ['maxConsecutiveNoProgress', 'No-progress pause threshold', 1, 1000],
  ['maxConsecutivePlannerFailures', 'Planner-failure pause threshold', 1, 100],
] as const

export function segmentPolicyFields(autonomous: boolean) {
  return [
    ['maxActionSteps', 'Tool steps', 1, autonomous ? 64 : 8],
    ['maxWriteSteps', 'Write attempts', 0, autonomous ? 64 : 3],
    ['maxModelCalls', 'Model calls', 1, autonomous ? 64 : 16],
    ['maxCorrections', 'Automatic corrections', 0, autonomous ? 8 : 2],
    ['deadlineSeconds', 'Time limit (seconds)', 5, autonomous ? 140 : 120],
  ] as const
}

export function validGoalPolicy(goal?: WorkflowGoalPolicy | null) {
  return !goal?.enabled || goalPolicyFields.every(([key, , min, max]) => {
    const value = goal[key]
    return typeof value === 'number' && Number.isInteger(value) && value >= min && value <= max
  })
}

export function effectiveQuickLimits(policy: Record<string, unknown>): Record<string, number | undefined> {
  return Object.fromEntries(segmentPolicyFields(false).map(([key, , min, max]) => {
    const stored = policy[key], storedMaximum = segmentPolicyFields(true).find(([field]) => field === key)![3]
    return [key, typeof stored === 'number' && Number.isInteger(stored) && stored >= min && stored <= storedMaximum ? Math.min(stored, max) : undefined]
  }))
}
