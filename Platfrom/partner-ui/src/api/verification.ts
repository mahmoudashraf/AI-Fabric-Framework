import { z } from 'zod'
import type { PartnerApiClient } from '../auth/apiClient'
import { partnerVerificationPackSchema, partnerVerificationRunSchema } from './schemas'

export function listVerificationPacks(api: PartnerApiClient) {
  return api.request('/api/partners/verification-packs', z.array(partnerVerificationPackSchema))
}

export function getStoreVerificationPack(api: PartnerApiClient, storeId: string) {
  return api.request(`/api/partners/stores/${encodeURIComponent(storeId)}/verification-pack`, partnerVerificationPackSchema)
}

export function runStoreVerification(api: PartnerApiClient, storeId: string, packId: string) {
  return api.request(`/api/partners/stores/${encodeURIComponent(storeId)}/verification-runs`, partnerVerificationRunSchema, {
    method: 'POST',
    body: JSON.stringify({ packId }),
  })
}

export function listVerificationRuns(api: PartnerApiClient) {
  return api.request('/api/partners/verification-runs', z.array(partnerVerificationRunSchema))
}

export function listStoreVerificationRuns(api: PartnerApiClient, storeId: string) {
  return api.request(`/api/partners/stores/${encodeURIComponent(storeId)}/verification-runs`, z.array(partnerVerificationRunSchema))
}

export function getVerificationRun(api: PartnerApiClient, runId: string) {
  return api.request(`/api/partners/verification-runs/${encodeURIComponent(runId)}`, partnerVerificationRunSchema)
}

export function completeVerificationStep(
  api: PartnerApiClient,
  storeId: string,
  stepId: string,
  payload: { runId?: string; status: string; evidenceNote?: string; partnerSafeMessage?: string },
) {
  return api.request(
    `/api/partners/stores/${encodeURIComponent(storeId)}/verification-steps/${encodeURIComponent(stepId)}/complete`,
    partnerVerificationRunSchema,
    { method: 'POST', body: JSON.stringify(payload) },
  )
}
