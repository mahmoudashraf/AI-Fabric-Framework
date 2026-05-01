import { z } from 'zod'
import type { PartnerApiClient } from '../auth/apiClient'
import {
  partnerThinkerEscalationResultSchema,
  partnerThinkerSessionDetailSchema,
  partnerThinkerSessionSchema,
} from './schemas'

export function listStoreThinkerSessions(api: PartnerApiClient, storeId: string) {
  return api.request(`/api/partners/stores/${encodeURIComponent(storeId)}/thinker-sessions`, z.array(partnerThinkerSessionSchema))
}

export function getThinkerSession(api: PartnerApiClient, sessionId: string) {
  return api.request(`/api/partners/thinker-sessions/${encodeURIComponent(sessionId)}`, partnerThinkerSessionDetailSchema)
}

export function createThinkerEscalation(
  api: PartnerApiClient,
  sessionId: string,
  payload: { title?: string; severity?: string; nextAction?: string },
) {
  return api.request(`/api/partners/thinker-sessions/${encodeURIComponent(sessionId)}/escalations`, partnerThinkerEscalationResultSchema, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}
