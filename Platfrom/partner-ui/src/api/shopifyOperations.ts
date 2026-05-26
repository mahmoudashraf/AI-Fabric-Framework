import type { PartnerApiClient } from '../auth/apiClient'
import { partnerShopifyOperationsSchema } from './schemas'
import type { PartnerShopifyOperations, PartnerShopifyVectorizationSourcePolicy } from './schemas'

export interface UpdatePartnerShopifyVectorizationPolicyPayload {
  policyVersion: number
  sourcePolicies: PartnerShopifyVectorizationSourcePolicy[]
}

const basePath = (storeId: string) => `/api/partners/stores/${encodeURIComponent(storeId)}/shopify-operations`

export function getShopifyOperations(api: PartnerApiClient, storeId: string): Promise<PartnerShopifyOperations> {
  return api.request(basePath(storeId), partnerShopifyOperationsSchema) as Promise<PartnerShopifyOperations>
}

export function reconcileShopifyKnowledge(api: PartnerApiClient, storeId: string): Promise<PartnerShopifyOperations> {
  return api.request(`${basePath(storeId)}/knowledge/reconcile`, partnerShopifyOperationsSchema, { method: 'POST' }) as Promise<PartnerShopifyOperations>
}

export function runShopifySourcePreflight(api: PartnerApiClient, storeId: string): Promise<PartnerShopifyOperations> {
  return api.request(`${basePath(storeId)}/source-preflight`, partnerShopifyOperationsSchema, { method: 'POST' }) as Promise<PartnerShopifyOperations>
}

export function indexAllShopifyKnowledge(api: PartnerApiClient, storeId: string): Promise<PartnerShopifyOperations> {
  return api.request(`${basePath(storeId)}/knowledge/index-all`, partnerShopifyOperationsSchema, { method: 'POST' }) as Promise<PartnerShopifyOperations>
}

export function reindexAllShopifyKnowledge(api: PartnerApiClient, storeId: string): Promise<PartnerShopifyOperations> {
  return api.request(`${basePath(storeId)}/knowledge/reindex-all`, partnerShopifyOperationsSchema, { method: 'POST' }) as Promise<PartnerShopifyOperations>
}

export function reindexSelectedShopifyKnowledge(
  api: PartnerApiClient,
  storeId: string,
  entityTypes: string[],
): Promise<PartnerShopifyOperations> {
  return api.request(`${basePath(storeId)}/knowledge/reindex-selected`, partnerShopifyOperationsSchema, {
    method: 'POST',
    body: JSON.stringify({ entityTypes }),
  }) as Promise<PartnerShopifyOperations>
}

export function updateShopifyKnowledgePolicy(
  api: PartnerApiClient,
  storeId: string,
  payload: UpdatePartnerShopifyVectorizationPolicyPayload,
): Promise<PartnerShopifyOperations> {
  return api.request(`${basePath(storeId)}/knowledge/policy`, partnerShopifyOperationsSchema, {
    method: 'PUT',
    body: JSON.stringify(payload),
  }) as Promise<PartnerShopifyOperations>
}

export function replayShopifyKnowledgeEvent(api: PartnerApiClient, storeId: string, eventId: string): Promise<PartnerShopifyOperations> {
  return api.request(`${basePath(storeId)}/knowledge/events/${encodeURIComponent(eventId)}/replay`, partnerShopifyOperationsSchema, {
    method: 'POST',
  }) as Promise<PartnerShopifyOperations>
}

export function retryLastFailedShopifyKnowledgeRun(api: PartnerApiClient, storeId: string): Promise<PartnerShopifyOperations> {
  return api.request(`${basePath(storeId)}/knowledge/retry-last-failed-auto-run`, partnerShopifyOperationsSchema, {
    method: 'POST',
  }) as Promise<PartnerShopifyOperations>
}
