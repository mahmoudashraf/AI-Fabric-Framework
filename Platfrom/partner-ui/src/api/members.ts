import { z } from 'zod'
import type { PartnerApiClient } from '../auth/apiClient'
import { partnerMemberSchema } from './schemas'

export function listMembers(api: PartnerApiClient) {
  return api.request('/api/partners/members', z.array(partnerMemberSchema))
}

export function updateProfile(api: PartnerApiClient, payload: { displayName?: string; avatarUrl?: string }) {
  return api.request('/api/partners/profile', partnerMemberSchema, {
    method: 'PATCH',
    body: JSON.stringify(payload),
  })
}

export function updateMember(api: PartnerApiClient, memberId: string, payload: { role?: string; status?: string }) {
  return api.request(`/api/partners/members/${encodeURIComponent(memberId)}`, partnerMemberSchema, {
    method: 'PATCH',
    body: JSON.stringify(payload),
  })
}
