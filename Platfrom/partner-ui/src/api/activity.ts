import { z } from 'zod'
import type { PartnerApiClient } from '../auth/apiClient'
import { partnerActivityEventSchema } from './schemas'

export function listPartnerActivity(api: PartnerApiClient) {
  return api.request('/api/partners/activity', z.array(partnerActivityEventSchema))
}
