import { z } from 'zod'
import type { PartnerApiClient } from '../auth/apiClient'
import { partnerCatalogEntrySchema } from './schemas'

export function listCatalog(api: PartnerApiClient) {
  return api.request('/api/partners/catalog', z.array(partnerCatalogEntrySchema))
}
