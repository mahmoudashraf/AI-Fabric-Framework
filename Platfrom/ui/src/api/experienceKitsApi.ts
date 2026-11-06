import { platformRequest, type AIWorkspaceInstallationSummary, type AiStateStorageMode, type CustomerOfferSummary } from './platformApi'

export type ExperienceKitCode = 'ai-customer-experience' | 'dealership-ai-experience' | 'ai-support-experience'
export type ExperienceKitDeliveryMode = 'standalone' | 'embedded' | 'both'

export type ExperienceKitDefinition = {
  code: ExperienceKitCode
  version: string
  name: string
  summary: string
  description: string
  experiencePackCode: string
  defaultWorkspaceName: string
  features: string[]
  demoUrl: string | null
}

export type ExperienceKitCatalogueEntry = {
  definition: ExperienceKitDefinition
  offers: CustomerOfferSummary[]
  ticketProviders: TicketProviderOption[]
}

export type TicketProviderOption = { code: string; name: string; declaredOperations: string[] }

export type ExperienceKitSummary = {
  id: string
  kitCode: ExperienceKitCode
  kitVersion: string
  name: string
  deploymentId: string
  consumerId: string
  installationId: string | null
  embeddedInstallationId?: string | null
  offerId: string
  targetProfileId: string
  humanSupportEnabled: boolean
  ticketingEnabled: boolean
  ticketProvider: string | null
  revision: number
  createdAt: string
  updatedAt: string
  deliveryMode: ExperienceKitDeliveryMode
  workspace: AIWorkspaceInstallationSummary | null
  embeddedWorkspace?: AIWorkspaceInstallationSummary | null
}

export type CreateExperienceKitRequest = {
  kitCode: ExperienceKitCode
  name: string
  offerId: string
  targetProfileId: string
  workspaceProfileId: string
  aiStateStorageMode?: AiStateStorageMode
  workspaceName: string
  brandColor: string
  deliveryMode: ExperienceKitDeliveryMode
  appSlug?: string
  websiteOrigin?: string
  humanSupportEnabled: boolean
  ticketingEnabled: boolean
  ticketProvider: string | null
  operationKey: string
}

export type UpdateExperienceKitRequest = Pick<CreateExperienceKitRequest,
  'name' | 'workspaceName' | 'brandColor' | 'deliveryMode' | 'appSlug' | 'websiteOrigin' | 'humanSupportEnabled' | 'ticketingEnabled' | 'ticketProvider'> & {
    expectedRevision: number
    expectedWorkspaceRevision: number
    expectedEmbeddedWorkspaceRevision: number | null
  }

const collection = '/api/customer-experience-kits'
const itemPath = (id: string) => `${collection}/${encodeURIComponent(id)}`

export const experienceKitKeys = {
  public: ['public-experience-kits'] as const,
  customer: (customerId: string) => ['experience-kits', customerId] as const,
  catalogue: (customerId: string) => ['experience-kits', customerId, 'catalogue'] as const,
  list: (customerId: string) => ['experience-kits', customerId, 'list'] as const,
  detail: (customerId: string, id: string) => ['experience-kits', customerId, 'detail', id] as const,
  releases: (customerId: string, deploymentId: string) => ['experience-kits', customerId, 'releases', deploymentId] as const,
  supportSpace: (customerId: string, installationId: string, targetProfileId: string, releaseId: string) => ['experience-kits', customerId, 'support-space', installationId, targetProfileId, releaseId] as const,
}

export const staffExperienceKitKeys = {
  customer: (actorId: string, customerId: string) => ['staff-experience-kits', actorId, customerId] as const,
  catalogue: (actorId: string, customerId: string) => ['staff-experience-kits', actorId, customerId, 'catalogue'] as const,
  list: (actorId: string, customerId: string) => ['staff-experience-kits', actorId, customerId, 'list'] as const,
  detail: (actorId: string, customerId: string, id: string) => ['staff-experience-kits', actorId, customerId, 'detail', id] as const,
}

const staffCollection = (customerId: string) => `/api/staff/customers/${encodeURIComponent(customerId)}/experience-kits`
const staffItemPath = (customerId: string, id: string) => `${staffCollection(customerId)}/${encodeURIComponent(id)}`

export const fetchStaffExperienceKitCatalogue = (customerId: string, signal?: AbortSignal) =>
  platformRequest<ExperienceKitCatalogueEntry[]>(`${staffCollection(customerId)}/catalogue`, { signal })
export const prepareStaffExperienceKits = (customerId: string) =>
  platformRequest<unknown>(`${staffCollection(customerId)}/prepare`, { method: 'POST' })
export const fetchStaffExperienceKits = (customerId: string, signal?: AbortSignal) =>
  platformRequest<ExperienceKitSummary[]>(staffCollection(customerId), { signal })
export const fetchStaffExperienceKit = (customerId: string, id: string, signal?: AbortSignal) =>
  platformRequest<ExperienceKitSummary>(staffItemPath(customerId, id), { signal })
export const createStaffExperienceKit = (customerId: string, body: CreateExperienceKitRequest) =>
  platformRequest<ExperienceKitSummary>(staffCollection(customerId), { method: 'POST', body: JSON.stringify(body) })
export const updateStaffExperienceKit = (customerId: string, id: string, body: UpdateExperienceKitRequest) =>
  platformRequest<ExperienceKitSummary>(staffItemPath(customerId, id), { method: 'PUT', body: JSON.stringify(body) })

export type KitSupportSpaceStatus = {
  status: 'NOT_CONFIGURED' | 'CONFIGURED'
  assignmentRevision: string
  space: { spaceId: string; providerId: string; providerBindingId: string; version: string; enabled: boolean;
    humanHandoverEnabled: boolean; closedRetentionDays: number; capabilities: string[] } | null
}

export type ConfigureKitSupportSpaceRequest = {
  installationRevision: number
  assignmentRevision: string
  expectedVersion: string | null
  enabled: boolean
  humanHandoverEnabled: boolean
  ticketProvider: string
  closedRetentionDays: number | null
  operationKey: string
  targetProfileId: string | null
  releaseId: string | null
}

const supportSpacePath = (customerId: string, installationId: string) =>
  `/api/platform/customers/${encodeURIComponent(customerId)}/ai-workspace-installations/${encodeURIComponent(installationId)}/support-space`

export const fetchKitSupportSpace = (customerId: string, installationId: string, targetProfileId: string, releaseId: string, signal?: AbortSignal) =>
  platformRequest<KitSupportSpaceStatus>(`${supportSpacePath(customerId, installationId)}?${new URLSearchParams({ targetProfileId, releaseId })}`, { signal })

export const configureKitSupportSpace = (customerId: string, installationId: string, body: ConfigureKitSupportSpaceRequest) =>
  platformRequest<KitSupportSpaceStatus>(supportSpacePath(customerId, installationId), { method: 'PUT', body: JSON.stringify(body) })

export const fetchPublicExperienceKits = (signal?: AbortSignal) =>
  platformRequest<ExperienceKitDefinition[]>('/api/public/experience-kits', { signal }, 'none')
export const fetchExperienceKitCatalogue = (signal?: AbortSignal) =>
  platformRequest<ExperienceKitCatalogueEntry[]>(`${collection}/catalogue`, { signal })
export const fetchExperienceKits = (signal?: AbortSignal) =>
  platformRequest<ExperienceKitSummary[]>(collection, { signal })
export const fetchExperienceKit = (id: string, signal?: AbortSignal) =>
  platformRequest<ExperienceKitSummary>(itemPath(id), { signal })
export const createExperienceKit = (body: CreateExperienceKitRequest) =>
  platformRequest<ExperienceKitSummary>(collection, { method: 'POST', body: JSON.stringify(body) })
export const updateExperienceKit = (id: string, body: UpdateExperienceKitRequest) =>
  platformRequest<ExperienceKitSummary>(itemPath(id), { method: 'PUT', body: JSON.stringify(body) })
