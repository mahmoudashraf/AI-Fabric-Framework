import { platformRequest } from './platformApi'

export type CustomerStaffAccessSummary = {
  id: string
  customerId: string
  userId: string
  email: string
  displayName: string
  status: 'ACTIVE' | 'REVOKED'
  grantedAt: string
  revokedAt: string | null
}

export const customerStaffAccessKeys = {
  list: (actorId: string, customerId: string, authenticationMode: string) =>
    ['customer-staff-access', actorId, customerId, authenticationMode] as const,
}

const collection = '/api/customer/staff-access'

function ownAccess(value: CustomerStaffAccessSummary, customerId: string): CustomerStaffAccessSummary {
  if (!value || value.customerId !== customerId) throw new Error('Staff access does not match this customer workspace. Refresh before continuing.')
  if (!value.id || !value.userId || typeof value.email !== 'string' || typeof value.displayName !== 'string'
    || !['ACTIVE', 'REVOKED'].includes(value.status)) throw new Error('Staff access details are incomplete. Refresh before continuing.')
  return value
}

export async function fetchCustomerStaffAccess(customerId: string, signal?: AbortSignal) {
  signal?.throwIfAborted()
  const response = await platformRequest<CustomerStaffAccessSummary[]>(collection, { signal })
  signal?.throwIfAborted()
  if (!Array.isArray(response)) throw new Error('Staff access could not be loaded. Refresh before continuing.')
  return response.map(value => ownAccess(value, customerId))
}

export async function grantCustomerStaffAccess(customerId: string, email: string, signal?: AbortSignal) {
  signal?.throwIfAborted()
  const response = await platformRequest<CustomerStaffAccessSummary>(collection, {
    method: 'POST', body: JSON.stringify({ email }), signal,
  })
  signal?.throwIfAborted()
  const access = ownAccess(response, customerId)
  if (access.status !== 'ACTIVE' || access.email.toLowerCase() !== email.trim().toLowerCase()) {
    throw new Error('The returned staff access does not match the grant request. Refresh before continuing.')
  }
  return access
}

export async function revokeCustomerStaffAccess(customerId: string, id: string, signal?: AbortSignal) {
  signal?.throwIfAborted()
  const response = await platformRequest<CustomerStaffAccessSummary>(`${collection}/${encodeURIComponent(id)}`, { method: 'DELETE', signal })
  signal?.throwIfAborted()
  const access = ownAccess(response, customerId)
  if (access.id !== id || access.status !== 'REVOKED') throw new Error('The returned staff access does not match the revoke request. Refresh before continuing.')
  return access
}
