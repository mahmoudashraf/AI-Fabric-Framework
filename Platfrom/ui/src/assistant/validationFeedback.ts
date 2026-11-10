import type { AssistantStatus, AssistantTurn, ValidationFeedbackPolicy } from './types'

type Capability = AssistantStatus['validationFeedback']

export function feedbackCeiling(capability: Capability): number {
  return capability && Number.isInteger(capability.maxCorrections) ? Math.max(0, Math.min(2, capability.maxCorrections)) : 0
}

export function feedbackPolicy(capability: Capability, preference: Partial<ValidationFeedbackPolicy>, allowed = true): ValidationFeedbackPolicy {
  const ceiling = feedbackCeiling(capability)
  if (!allowed || !ceiling || !(preference.enabled ?? capability?.defaultEnabled)) return { enabled: false, maxCorrections: 0 }
  const requested = preference.maxCorrections
  return { enabled: true, maxCorrections: requested !== undefined && Number.isInteger(requested) ? Math.max(1, Math.min(ceiling, requested)) : ceiling }
}

export function feedbackProgress(turn: AssistantTurn): string | undefined {
  if (turn.status !== 'RUNNING' || !turn.validationFeedback) return
  const attempt = [...(turn.attempts ?? [])].reverse().find(item => item.status === 'RUNNING')
  const total = 1 + turn.validationFeedback.maxCorrections
  if (!attempt || !Number.isInteger(attempt.number) || attempt.number < 1 || !Number.isInteger(total) || attempt.number > total) return
  return `Attempt ${attempt.number} of ${total}`
}

export function feedbackReason(reason: string): string {
  const messages: Record<string, string> = {
    DISABLED: 'Automatic corrections are off.',
    CORRECTING_VALIDATION_ERRORS: 'AI is correcting the reported validation errors.',
    VALIDATION_PASSED: 'The available configuration checks passed.',
    CORRECTION_LIMIT_REACHED: 'Correction limit reached. Review the remaining findings or continue manually.',
    NO_CORRECTION_PROPOSED: 'No further correction was proposed. Review the remaining findings.',
    CHECK_NOT_AVAILABLE: 'No pre-save validation check is available for this configuration.',
    NEEDS_INPUT: 'AI needs your input before continuing.',
    EXPLAIN: 'Explanation complete; no automatic corrections.',
    UNSUPPORTED: 'This request needs a capability that is not available here.',
    FAILED: 'Correction stopped. Review the failure and retained attempt history.',
  }
  return messages[reason] ?? reason.split('_').join(' ').toLowerCase()
}
