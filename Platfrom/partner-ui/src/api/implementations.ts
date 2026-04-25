import type { PartnerApiClient } from '../auth/apiClient'
import { partnerClientImplementationSchema, partnerStoreAccessLinkSchema } from './schemas'

export interface ClientImplementationPayload {
  clientName: string
  contactEmail?: string
  shopDomain: string
  vertical?: string
  requestedTier: string
  requestedSurfaces: string[]
  knownIntegrations: string[]
  notes?: string
}

export function createClientImplementation(api: PartnerApiClient, payload: ClientImplementationPayload) {
  return api.request('/api/partners/client-implementations', partnerClientImplementationSchema, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function getClientImplementation(api: PartnerApiClient, requestId: string) {
  return api.request(`/api/partners/client-implementations/${encodeURIComponent(requestId)}`, partnerClientImplementationSchema)
}

export function createStoreAccessLink(api: PartnerApiClient, requestId: string) {
  return api.request(
    `/api/partners/client-implementations/${encodeURIComponent(requestId)}/store-access-links`,
    partnerStoreAccessLinkSchema,
    { method: 'POST' },
  )
}
