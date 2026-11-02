import { platformRequest, type AIWorkspaceInstallationSummary, type AiStateStorageMode, type CustomerOfferSummary } from './platformApi'

export type ExperienceKitCode = 'ai-customer-experience' | 'dealership-ai-experience'
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
}

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
  operationKey: string
}

export type UpdateExperienceKitRequest = Pick<CreateExperienceKitRequest,
  'name' | 'workspaceName' | 'brandColor' | 'deliveryMode' | 'appSlug' | 'websiteOrigin' | 'humanSupportEnabled'> & {
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
}

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
