import type { PlatformUserAccessSummary } from '../api/platformApi'

export function workspaceMembers(users: PlatformUserAccessSummary[], customerId: string) {
  if (users.some(user => user.customerId !== customerId || user.role !== 'CUSTOMER_ADMIN')) {
    throw new Error('The team response does not match this workspace. Refresh before continuing.')
  }
  return users
}

export function filterMembers(users: PlatformUserAccessSummary[], search: string, status: string) {
  const term = search.trim().toLocaleLowerCase()
  return users.filter(user => (!status || user.status === status)
    && (!term || `${user.displayName} ${user.email}`.toLocaleLowerCase().includes(term)))
}

export function unchangedMember(users: PlatformUserAccessSummary[], customerId: string, expected: PlatformUserAccessSummary) {
  const current = workspaceMembers(users, customerId).find(user => user.id === expected.id)
  if (!current || current.status !== expected.status || current.displayName !== expected.displayName || current.email !== expected.email) {
    throw new Error('This member changed since you opened the editor. Refresh the team and reopen their details before saving.')
  }
  return current
}

export const memberCreatePayload = (customerId: string, form: { email: string; displayName: string; password: string }) => ({
  email: form.email.trim(), displayName: form.displayName.trim(), password: form.password,
  role: 'CUSTOMER_ADMIN', customerId,
})
export const memberUpdatePayload = (customerId: string, displayName: string, status: string) => ({
  displayName: displayName.trim(), status, role: 'CUSTOMER_ADMIN', customerId,
})

export const accountDate = (value: string | null | undefined) => value
  ? new Date(value).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' }) : 'Not recorded'
export const memberInitials = (name: string) => name.trim().split(/\s+/).slice(0, 2).map(word => word[0]).join('').toUpperCase()
export const isCurrentMember = (user: Pick<PlatformUserAccessSummary, 'email'>, actorId: string) => user.email.toLowerCase() === actorId.toLowerCase()
