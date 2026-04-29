import { z } from 'zod'
import type { PartnerApiClient } from '../auth/apiClient'
import { partnerEvidenceBundleSchema } from './schemas'

export function listEvidenceBundles(api: PartnerApiClient) {
  return api.request('/api/partners/evidence-bundles', z.array(partnerEvidenceBundleSchema))
}

export function listStoreEvidenceBundles(api: PartnerApiClient, storeId: string) {
  return api.request(`/api/partners/stores/${encodeURIComponent(storeId)}/evidence-bundles`, z.array(partnerEvidenceBundleSchema))
}

export function createStoreEvidenceBundle(api: PartnerApiClient, storeId: string, payload: { bundleKind: string; verificationRunId?: string | null }) {
  return api.request(`/api/partners/stores/${encodeURIComponent(storeId)}/evidence-bundles`, partnerEvidenceBundleSchema, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function getEvidenceBundle(api: PartnerApiClient, bundleId: string) {
  return api.request(`/api/partners/evidence-bundles/${encodeURIComponent(bundleId)}`, partnerEvidenceBundleSchema)
}

export function downloadEvidenceBundle(api: PartnerApiClient, bundleId: string) {
  return api.download(`/api/partners/evidence-bundles/${encodeURIComponent(bundleId)}/export`)
}
