import type { PartnerApiClient } from '../auth/apiClient'
import { z } from 'zod'
import { merchantInviteSchema, partnerClientImplementationSchema, partnerEligibleStoreSchema, partnerStoreAccessLinkSchema } from './schemas'

export interface ClientImplementationPayload {
  clientName: string
  contactEmail?: string
  storeConnectionId: string
  vertical?: string
  knownIntegrations: string[]
  notes?: string
}

export function createClientImplementation(api: PartnerApiClient, payload: ClientImplementationPayload) {
  return api.request('/api/partners/client-implementations', partnerClientImplementationSchema, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function listClientImplementations(api: PartnerApiClient) {
  return api.request('/api/partners/client-implementations', z.array(partnerClientImplementationSchema))
}

export function fetchEligibleStores(api: PartnerApiClient, query?: string) {
  const params = query?.trim() ? `?query=${encodeURIComponent(query.trim())}` : ''
  return api.request(`/api/partners/eligible-stores${params}`, z.array(partnerEligibleStoreSchema))
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

export function sendMerchantInvite(api: PartnerApiClient, requestId: string, recipientEmail?: string) {
  return api.request(`/api/partners/client-implementations/${encodeURIComponent(requestId)}/merchant-invites`, merchantInviteSchema, {
    method: 'POST',
    body: JSON.stringify({ recipientEmail }),
  })
}
