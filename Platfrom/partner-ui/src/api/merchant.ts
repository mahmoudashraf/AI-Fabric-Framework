import type { PartnerApiClient } from '../auth/apiClient'
import { merchantApprovalSchema, merchantDecisionSchema, merchantRollbackRequestSchema, merchantWorkspaceSchema, partnerProductionPromotionSchema } from './schemas'

export interface MerchantApprovalPayload {
  approverName: string
  approverEmail?: string
  approvedScope?: string
}

export function approveMerchantAccess(api: PartnerApiClient, approvalCode: string, payload: MerchantApprovalPayload) {
  return api.request(`/api/merchant/partner-access/${encodeURIComponent(approvalCode)}/approve`, merchantApprovalSchema, {
    method: 'POST',
    anonymous: true,
    body: JSON.stringify(payload),
  })
}

export function getMerchantWorkspace(api: PartnerApiClient, approvalCode: string) {
  return api.request(`/api/merchant/partner-access/${encodeURIComponent(approvalCode)}/workspace`, merchantWorkspaceSchema, {
    anonymous: true,
  })
}

export function denyMerchantAccess(api: PartnerApiClient, approvalCode: string, payload: MerchantApprovalPayload & { decisionReason?: string }) {
  return api.request(`/api/merchant/partner-access/${encodeURIComponent(approvalCode)}/deny`, merchantDecisionSchema, {
    method: 'POST',
    anonymous: true,
    body: JSON.stringify(payload),
  })
}

export function revokeMerchantAccess(api: PartnerApiClient, approvalCode: string, payload: MerchantApprovalPayload & { decisionReason?: string }) {
  return api.request(`/api/merchant/partner-access/${encodeURIComponent(approvalCode)}/revoke`, merchantDecisionSchema, {
    method: 'POST',
    anonymous: true,
    body: JSON.stringify(payload),
  })
}

export function requestMerchantProductionPromotion(api: PartnerApiClient, approvalCode: string) {
  return api.request(`/api/merchant/partner-access/${encodeURIComponent(approvalCode)}/production-promotions`, partnerProductionPromotionSchema, {
    method: 'POST',
    anonymous: true,
  })
}

export function requestMerchantRollback(
  api: PartnerApiClient,
  approvalCode: string,
  payload: { requesterName: string; requesterEmail?: string; reason: string },
) {
  return api.request(`/api/merchant/partner-access/${encodeURIComponent(approvalCode)}/rollback-requests`, merchantRollbackRequestSchema, {
    method: 'POST',
    anonymous: true,
    body: JSON.stringify(payload),
  })
}
