export type DeploymentTemplateSummary = {
  id: string
  name: string
  description: string
  llmProvider: string
  vectorStrategy: string
  runtimeProfile: string
  connectorProfile: string
}

export type DeploymentSummary = {
  id: string
  name: string
  environment: string
  templateId: string
  status: string
  activeVersion: string
  runtimeBaseUrl: string | null
  connectorBaseUrl: string | null
  createdAt: string
}

export type DeploymentLifecycleSnapshotSummary = {
  releaseId: string
  versionId: string
  status: string
  provisioningStatus: string
  verificationStatus: string
  currentStepKey: string | null
  currentStepDescription: string | null
  updatedAt: string
}

export type DeploymentVerificationSnapshotSummary = {
  verificationRunId: string
  status: string
  summaryMessage: string
  passedChecks: number
  warningChecks: number
  failedChecks: number
  skippedChecks: number
  completedAt: string | null
}

export type DeploymentOverviewSummary = {
  id: string
  name: string
  environment: string
  templateId: string
  status: string
  activeVersion: string | null
  healthStatus: string
  healthSummary: string
  runtimeBaseUrl: string | null
  connectorBaseUrl: string | null
  latestRelease: DeploymentLifecycleSnapshotSummary | null
  latestVerification: DeploymentVerificationSnapshotSummary | null
  archivedAt: string | null
  createdAt: string
  updatedAt: string
}

export type DeploymentDraftResponse = {
  id: string
  deploymentId: string
  revisionNumber: number
  status: string
  actionsConfig: unknown
  entityConfig: unknown
  routingConfig: unknown
  providerConfig: unknown
  securityConfig: unknown
  createdAt: string
  updatedAt: string
}

export type DeploymentVersionSummary = {
  id: string
  deploymentId: string
  sourceDraftId: string
  versionLabel: string
  status: string
  configHash: string
  reindexRequired: boolean
  publishedAt: string
}

export type RailwayEnvVarSummary = {
  key: string
  value: string
}

export type RailwayServicePlanSummary = {
  serviceName: string
  rootDir: string | null
  dockerfilePath?: string | null
  baseUrl: string
  env: RailwayEnvVarSummary[]
}

export type RailwayProvisioningStepSummary = {
  order: number
  key: string
  description: string
}

export type RailwayPreflightCheckSummary = {
  key: string
  status: string
  message: string
  details: string | null
}

export type RailwayPreflightSummary = {
  mode: string
  ready: boolean
  checkedAt: string
  publicBaseUrl: string
  workspaceId: string | null
  workspaceName: string | null
  repository: string
  branch: string
  checks: RailwayPreflightCheckSummary[]
}

export type RailwayProvisioningPlanSummary = {
  deploymentId: string
  deploymentName: string
  environment: string
  templateId: string
  versionId: string
  versionLabel: string
  configHash: string
  mode: string
  projectName: string
  repository: string
  branch: string
  workspaceId: string | null
  artifactStrategy: string
  artifactUrls: {
    actions: string
    entities: string
    routing: string
    manifest: string
  }
  services: {
    runtime: RailwayServicePlanSummary
    restConnector: RailwayServicePlanSummary
  }
  steps: RailwayProvisioningStepSummary[]
}

export type DeploymentReleaseSummary = {
  id: string
  deploymentId: string
  deploymentVersionId: string
  status: string
  verificationStatus: string
  provisioningStatus: string
  provisioningTarget: string
  currentStepKey: string | null
  currentStepDescription: string | null
  errorMessage: string | null
  verificationRunId: string | null
  provisioningDetails: unknown
  createdAt: string
  appliedAt: string
  updatedAt: string
}

export type DeploymentVerificationRunSummary = {
  id: string
  deploymentId: string
  releaseId: string
  deploymentVersionId: string
  verificationType: string
  status: string
  summaryMessage: string
  checks: unknown
  createdAt: string
  completedAt: string
}

export type DraftValidationIssue = {
  severity: string
  section: string
  code: string
  path: string
  message: string
}

export type DraftValidationResponse = {
  draftId: string
  deploymentId: string
  publishReady: boolean
  errorCount: number
  warningCount: number
  validatedAt: string
  issues: DraftValidationIssue[]
}

export type PlatformSecretSummary = {
  name: string
  displayName: string
  description: string
  required: boolean
  present: boolean
  source: string
  updatedAt: string | null
}

export type PlatformAuthSessionSummary = {
  enabled: boolean
  headerName: string
  authenticated: boolean
  actorId: string | null
  displayName: string | null
  role: string | null
  authenticationMode: string | null
  sessionAuthEnabled: boolean
  apiKeyAuthEnabled: boolean
  canManageSecrets: boolean
  canOperateDeployments: boolean
}

export type PlatformLoginRequest = {
  email: string
  password: string
}

export type PlatformAuditEventSummary = {
  id: string
  actorId: string
  actorRole: string
  action: string
  targetType: string
  targetId: string
  details: unknown
  createdAt: string
}

export type RailwayLogAttributeSummary = {
  key: string | null
  value: string | null
}

export type RailwayLogTagsSummary = {
  deploymentId: string | null
  deploymentInstanceId: string | null
  environmentId: string | null
  projectId: string | null
  serviceId: string | null
  snapshotId: string | null
}

export type RailwayLogEntrySummary = {
  timestamp: string | null
  severity: string | null
  message: string | null
  tags: RailwayLogTagsSummary | null
  attributes: RailwayLogAttributeSummary[]
}

export type DeploymentRailwayLogsResponse = {
  deploymentId: string
  releaseId: string | null
  deploymentVersionId: string | null
  releaseStatus: string | null
  provisioningTarget: string | null
  service: string
  source: string
  available: boolean
  message: string
  projectId: string | null
  environmentId: string | null
  serviceId: string | null
  serviceName: string | null
  railwayDeploymentId: string | null
  requestedLimit: number
  filter: string | null
  startDate: string | null
  endDate: string | null
  queriedAt: string
  entries: RailwayLogEntrySummary[]
}

export type CreateDeploymentRequest = {
  name: string
  environment: string
  templateId: string
}

export type UpdateDeploymentDraftRequest = {
  actionsConfig?: unknown
  entityConfig?: unknown
  routingConfig?: unknown
  providerConfig?: unknown
  securityConfig?: unknown
}

function resolveApiBaseUrl(): string {
  if (typeof window !== 'undefined') {
    const runtimeValue = window.__PLATFORM_RUNTIME_CONFIG__?.apiBaseUrl?.trim()
    if (runtimeValue) {
      return runtimeValue
    }
  }
  return import.meta.env.VITE_PLATFORM_API_BASE_URL ?? 'http://localhost:8088'
}

const apiBaseUrl = resolveApiBaseUrl()
const platformApiKeyStorageKey = 'ai-enablement-platform.apiKey'

export class PlatformApiError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

export function getStoredPlatformApiKey(): string {
  if (typeof window === 'undefined') {
    return ''
  }
  return window.localStorage.getItem(platformApiKeyStorageKey) ?? ''
}

export function setStoredPlatformApiKey(value: string) {
  if (typeof window === 'undefined') {
    return
  }
  const trimmed = value.trim()
  if (trimmed.length === 0) {
    window.localStorage.removeItem(platformApiKeyStorageKey)
    return
  }
  window.localStorage.setItem(platformApiKeyStorageKey, trimmed)
}

export function clearStoredPlatformApiKey() {
  if (typeof window === 'undefined') {
    return
  }
  window.localStorage.removeItem(platformApiKeyStorageKey)
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const apiKey = getStoredPlatformApiKey()
  const baseHeaders: HeadersInit = {
    'Content-Type': 'application/json',
    ...(apiKey ? { 'X-PLATFORM-API-KEY': apiKey } : {}),
  }
  const response = await fetch(`${apiBaseUrl}${path}`, {
    credentials: 'include',
    headers: {
      ...baseHeaders,
      ...(init?.headers ?? {}),
    },
    ...init,
  })

  if (!response.ok) {
    const contentType = response.headers.get('content-type') ?? ''

    if (contentType.includes('application/json')) {
      const payload = (await response.json()) as { message?: string; error?: string }
      throw new PlatformApiError(
        response.status,
        payload.message ?? payload.error ?? `Request failed with status ${response.status}`,
      )
    }

    const message = await response.text()
    throw new PlatformApiError(response.status, message || `Request failed with status ${response.status}`)
  }

  return response.json() as Promise<T>
}

export function fetchPlatformAuthSession() {
  return request<PlatformAuthSessionSummary>('/api/platform/auth/session')
}

export function loginToPlatform(payload: PlatformLoginRequest) {
  return request<PlatformAuthSessionSummary>('/api/platform/auth/login', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function logoutFromPlatform() {
  return request<PlatformAuthSessionSummary>('/api/platform/auth/logout', {
    method: 'POST',
  })
}

export function fetchPlatformAuditEvents() {
  return request<PlatformAuditEventSummary[]>('/api/platform/audit-events')
}

export function fetchDeploymentTemplates() {
  return request<DeploymentTemplateSummary[]>('/api/deployment-templates')
}

export function fetchDeployments() {
  return request<DeploymentSummary[]>('/api/deployments?includeArchived=false')
}

export function fetchDeploymentsByArchiveState(includeArchived = false) {
  return request<DeploymentSummary[]>(`/api/deployments?includeArchived=${includeArchived}`)
}

export function fetchDeploymentOverviews(includeArchived = false) {
  return request<DeploymentOverviewSummary[]>(`/api/deployments/overview?includeArchived=${includeArchived}`)
}

export function createDeployment(payload: CreateDeploymentRequest) {
  return request<DeploymentSummary>('/api/deployments', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function archiveDeployment(deploymentId: string) {
  return request<DeploymentOverviewSummary>(`/api/deployments/${deploymentId}/archive`, {
    method: 'POST',
  })
}

export function fetchDeploymentDraft(deploymentId: string) {
  return request<DeploymentDraftResponse>(`/api/deployments/${deploymentId}/draft`)
}

export function fetchDeploymentVersions(deploymentId: string) {
  return request<DeploymentVersionSummary[]>(`/api/deployments/${deploymentId}/versions`)
}

export function fetchRailwayProvisioningPlan(deploymentId: string, versionId: string) {
  return request<RailwayProvisioningPlanSummary>(
    `/api/deployments/${deploymentId}/versions/${versionId}/railway-plan`,
  )
}

export function fetchRailwayPreflight() {
  return request<RailwayPreflightSummary>('/api/platform/provisioning/railway/preflight')
}

export function fetchPlatformSecrets() {
  return request<PlatformSecretSummary[]>('/api/platform/secrets')
}

export function fetchDeploymentReleases(deploymentId: string) {
  return request<DeploymentReleaseSummary[]>(`/api/deployments/${deploymentId}/releases`)
}

export function fetchDeploymentVerificationRuns(deploymentId: string) {
  return request<DeploymentVerificationRunSummary[]>(`/api/deployments/${deploymentId}/verification-runs`)
}

export function fetchDeploymentRailwayLogs(
  deploymentId: string,
  options?: {
    releaseId?: string
    service?: string
    source?: string
    limit?: number
    filter?: string
    startDate?: string
    endDate?: string
  },
) {
  const params = new URLSearchParams()
  if (options?.releaseId) {
    params.set('releaseId', options.releaseId)
  }
  if (options?.service) {
    params.set('service', options.service)
  }
  if (options?.source) {
    params.set('source', options.source)
  }
  if (typeof options?.limit === 'number') {
    params.set('limit', String(options.limit))
  }
  if (options?.filter) {
    params.set('filter', options.filter)
  }
  if (options?.startDate) {
    params.set('startDate', options.startDate)
  }
  if (options?.endDate) {
    params.set('endDate', options.endDate)
  }
  const suffix = params.size > 0 ? `?${params.toString()}` : ''
  return request<DeploymentRailwayLogsResponse>(`/api/deployments/${deploymentId}/railway-logs${suffix}`)
}

export function rerunDeploymentVerification(deploymentId: string) {
  return request<DeploymentVerificationRunSummary>(
    `/api/deployments/${deploymentId}/verification-runs/recheck`,
    {
      method: 'POST',
    },
  )
}

export function publishDeploymentDraft(draftId: string) {
  return request<DeploymentVersionSummary>(`/api/deployment-drafts/${draftId}/publish`, {
    method: 'POST',
  })
}

export function validateDeploymentDraft(draftId: string) {
  return request<DraftValidationResponse>(`/api/deployment-drafts/${draftId}/validate`, {
    method: 'POST',
  })
}

export function updateDeploymentDraft(draftId: string, payload: UpdateDeploymentDraftRequest) {
  return request<DeploymentDraftResponse>(`/api/deployment-drafts/${draftId}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function updatePlatformSecret(name: string, value: string) {
  return request<PlatformSecretSummary>(`/api/platform/secrets/${name}`, {
    method: 'PUT',
    body: JSON.stringify({ value }),
  })
}

export function clearPlatformSecret(name: string) {
  return request<PlatformSecretSummary>(`/api/platform/secrets/${name}`, {
    method: 'DELETE',
  })
}

export function applyDeploymentVersion(deploymentId: string, versionId: string) {
  return request<DeploymentReleaseSummary>(`/api/deployments/${deploymentId}/apply/${versionId}`, {
    method: 'POST',
  })
}
