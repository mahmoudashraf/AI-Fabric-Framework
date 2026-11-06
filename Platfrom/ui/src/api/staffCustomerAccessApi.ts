import { platformRequest } from './platformApi'

export type StaffCustomerAccessRequest = { email: string; displayName: string }
export type StaffCustomerAccess = StaffCustomerAccessRequest & {
  userId: string
  customerId: string
  identityLinked: boolean
  created: boolean
}

export function prepareStaffCustomerAccess(customerId: string, body: StaffCustomerAccessRequest, signal?: AbortSignal) {
  return platformRequest<StaffCustomerAccess>(`/api/staff/customers/${encodeURIComponent(customerId)}/access`, {
    method: 'POST', body: JSON.stringify(body), signal,
  })
}
