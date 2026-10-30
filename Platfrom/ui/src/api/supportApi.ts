import { platformRequest } from './platformApi'

export type SupportResource = { type: 'DEPLOYMENT' | 'ENGAGE_APP' | 'CONNECTION'; id: string }
export type SupportDelivery = 'PENDING' | 'UNKNOWN' | 'USER_MESSAGE_PERSISTED' | 'COMPLETED' | 'FAILED'
export type SupportCase = {
  id: string; reference: string; summary: string; details: string | null; topic: string | null
  resources: SupportResource[]; status: 'OPEN' | 'RESOLVED' | 'CANCELLED'
  consent: { granted: boolean; version: number; changedAt: string; revokedAt: string | null }
  revision: number; initialMessageDelivery: SupportDelivery; deliveryObservedAt: string | null
  episodes: Array<{ id: string; number: number; conversationId: string; terminal: boolean; createdAt: string }>
  createdAt: string; updatedAt: string; closedAt: string | null
}
export type CreateSupportCase = { summary: string; details: string; topic: string; resources: SupportResource[]; diagnosticConsent: boolean }
export type SupportWorkspaceBootstrap = {
  conversationId: string; principalNamespace: string; apiBaseUrl: string; installationId: string
  workspaceAssetUrl: string; workspaceIntegrity: string; experienceModuleUrl: string; experienceIntegrity: string
  reference: string; status: SupportCase['status']; initialMessageDelivery: SupportDelivery
  assignmentRevision: string; configuration: Record<string, unknown>
  conversationHandover?: { enabled: boolean; [key: string]: unknown }
}
const collection = '/api/support/cases'
const path = (id: string) => `${collection}/${encodeURIComponent(id)}`
export const listSupportCases = (page = 0) => platformRequest<SupportCase[]>(`${collection}?page=${page}&size=25`)
export const getSupportCase = (id: string) => platformRequest<SupportCase>(path(id))
export const createSupportCase = (body: CreateSupportCase, key: string) => platformRequest<SupportCase>(collection, {
  method: 'POST', headers: { 'Idempotency-Key': key }, body: JSON.stringify(body),
})
export const changeSupportConsent = (supportCase: SupportCase, granted: boolean) => platformRequest<SupportCase>(`${path(supportCase.id)}/consent`, {
  method: 'PUT', body: JSON.stringify({ granted, expectedVersion: supportCase.consent.version }),
})
export const changeSupportStatus = (supportCase: SupportCase, action: 'resolve' | 'cancel' | 'reopen') => platformRequest<SupportCase>(`${path(supportCase.id)}/${action}`, {
  method: 'POST', body: JSON.stringify({ expectedRevision: supportCase.revision }),
})
export const updateSupportResources = (supportCase: SupportCase, resources: SupportResource[]) => platformRequest<SupportCase>(path(supportCase.id), {
  method: 'PATCH', body: JSON.stringify({ topic: supportCase.topic, resources, expectedRevision: supportCase.revision }),
})
export const getSupportWorkspace = (id: string) => platformRequest<SupportWorkspaceBootstrap>(`${path(id)}/workspace`)
// The server owns the immutable body and canonical idempotency key. Never pass a new message from the browser.
export const startSupportConversation = (id: string) => platformRequest<{ supportCase: SupportCase; runtimeStatus: number; response: unknown }>(`${path(id)}/start`, { method: 'POST' })

export type SupportStaffIntake = {
  caseId: string; reference: string; summary: string; details: string; status: string; version: number
  accountLabel: string; resourceLabels: string[]
  consent: { status: string; summary: string; updatedAt: string | null }
  initialMessageDelivery: SupportDelivery; createdAt: string; updatedAt: string
}

// This route authorizes the actual Supabase agent, independently of Console membership or API keys.
export const listSupportStaffIntake = (token: string, page = 0, size = 25) => platformRequest<SupportStaffIntake[]>(
  `/api/support/staff/intake?page=${page}&size=${size}`,
  { headers: { Authorization: `Bearer ${token}` }, credentials: 'omit' }, 'none',
)
