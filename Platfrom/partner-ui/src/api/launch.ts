import type { PartnerApiClient } from '../auth/apiClient'
import { partnerLaunchReadinessSchema, partnerProductionPromotionSchema } from './schemas'

export function getLaunchReadiness(api: PartnerApiClient, storeId: string) {
  return api.request(`/api/partners/stores/${encodeURIComponent(storeId)}/launch-readiness`, partnerLaunchReadinessSchema)
}

export function requestProductionPromotion(api: PartnerApiClient, storeId: string) {
  return api.request(`/api/partners/stores/${encodeURIComponent(storeId)}/production-promotions`, partnerProductionPromotionSchema, {
    method: 'POST',
  })
}
