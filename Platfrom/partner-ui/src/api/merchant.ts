import type { PartnerApiClient } from '../auth/apiClient'
import { merchantApprovalSchema } from './schemas'

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
