import { z } from 'zod'
import type { PartnerApiClient } from '../auth/apiClient'
import { partnerEscalationSchema, partnerReplySchema, partnerThreadSchema } from './schemas'

export interface EscalationPayload {
  title: string
  severity: string
  description: string
  reproductionSteps?: string
  expectedBehavior?: string
  actualBehavior?: string
  impact?: string
  nextAction?: string
  dueAt?: string | null
  evidenceBundleIds?: string[]
}

export function listEscalations(api: PartnerApiClient) {
  return api.request('/api/partners/support/escalations', z.array(partnerEscalationSchema))
}

export function createEscalation(api: PartnerApiClient, storeId: string, payload: EscalationPayload) {
  return api.request(`/api/partners/stores/${encodeURIComponent(storeId)}/escalations`, partnerEscalationSchema, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function getEscalationThread(api: PartnerApiClient, escalationId: string) {
  return api.request(`/api/partners/escalations/${encodeURIComponent(escalationId)}/thread`, partnerThreadSchema)
}

export function addEscalationReply(api: PartnerApiClient, escalationId: string, bodyMarkdown: string) {
  return api.request(`/api/partners/escalations/${encodeURIComponent(escalationId)}/replies`, partnerReplySchema, {
    method: 'POST',
    body: JSON.stringify({ bodyMarkdown }),
  })
}
