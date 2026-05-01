import { z } from 'zod'
import type { PartnerApiClient } from '../auth/apiClient'
import { partnerSessionSchema } from './schemas'

export function getPartnerSession(api: PartnerApiClient) {
  return api.request('/api/partners/session', partnerSessionSchema)
}

export function completePartnerSignup(api: PartnerApiClient, workspaceName: string) {
  return api.request('/api/partners/signup/complete', partnerSessionSchema, {
    method: 'POST',
    body: JSON.stringify({ workspaceName }),
  })
}

export const emptySchema = z.null()
