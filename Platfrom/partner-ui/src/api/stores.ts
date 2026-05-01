import { z } from 'zod'
import type { PartnerApiClient } from '../auth/apiClient'
import { partnerStoreSchema } from './schemas'

export function listPartnerStores(api: PartnerApiClient) {
  return api.request('/api/partners/stores', z.array(partnerStoreSchema))
}

export function getPartnerStore(api: PartnerApiClient, storeId: string) {
  return api.request(`/api/partners/stores/${encodeURIComponent(storeId)}`, partnerStoreSchema)
}
