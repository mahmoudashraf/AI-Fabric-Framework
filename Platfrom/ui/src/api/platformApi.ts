export type DeploymentTemplateSummary = {
  id: string
  name: string
  description: string
  llmProvider: string
  vectorStrategy: string
  runtimeProfile: string
  connectorProfile: string
}

export type DeploymentSourceSummary = {
  repository: string
  branch: string
  repositoryOverride: string | null
  branchOverride: string | null
  overrideActive: boolean
}

export type DeploymentSummary = {
  id: string
  name: string
  environment: string
  templateId: string
  source: DeploymentSourceSummary
  status: string
  activeVersion: string
  runtimeBaseUrl: string | null
  connectorBaseUrl: string | null
  approvalRequiredForApply: boolean
  approvalRequiredForDelete: boolean
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
  source: DeploymentSourceSummary
  status: string
  activeVersion: string | null
  healthStatus: string
  healthSummary: string
  runtimeBaseUrl: string | null
  connectorBaseUrl: string | null
  approvalRequiredForApply: boolean
  approvalRequiredForDelete: boolean
  latestRelease: DeploymentLifecycleSnapshotSummary | null
  latestVerification: DeploymentVerificationSnapshotSummary | null
  archivedAt: string | null
  createdAt: string
  updatedAt: string
}

export type DeploymentWorkspaceDraftSummary = {
  id: string
  revisionNumber: number
  status: string
  updatedAt: string
}

export type DeploymentAssignmentSummary = {
  id: string
  deploymentId: string
  userId: string
  userEmail: string
  userDisplayName: string
  platformRole: string
  assignmentRole: string
  createdAt: string
  updatedAt: string
}

export type DeploymentOperationApprovalSummary = {
  id: string
  deploymentId: string
  operationType: string
  targetVersionId: string | null
  targetVersionLabel: string | null
  status: string
  requestedByActorId: string
  requestedByDisplayName: string | null
  requestedReason: string
  approvedByActorId: string | null
  approvedByDisplayName: string | null
  resolutionNote: string | null
  createdAt: string
  updatedAt: string
  approvedAt: string | null
  rejectedAt: string | null
  expiresAt: string | null
  consumedAt: string | null
}

export type BulkDeploymentActionItemSummary = {
  deploymentId: string
  deploymentName: string
  action: string
  status: string
  message: string
}

export type BulkDeploymentActionResponse = {
  action: string
  requestedCount: number
  succeededCount: number
  failedCount: number
  results: BulkDeploymentActionItemSummary[]
}

export type DeploymentWorkspaceSummary = {
  deployment: DeploymentOverviewSummary
  template: DeploymentTemplateSummary
  draft: DeploymentWorkspaceDraftSummary
  latestVersion: DeploymentVersionSummary | null
  latestRelease: DeploymentReleaseSummary | null
  latestVerificationRun: DeploymentVerificationRunSummary | null
  versionCount: number
  releaseCount: number
  verificationRunCount: number
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
  promptConfig: unknown
  createdAt: string
  updatedAt: string
}

export type DeploymentPromptRevisionSummary = {
  id: string
  deploymentId: string
  sourceDraftId: string
  revisionLabel: string
  revisionSummary: string | null
  createdByActorId: string
  createdByDisplayName: string | null
  populatedPromptCount: number
  createdAt: string
}

export type DeploymentPocChatTurnSummary = {
  timestamp: string | null
  userQuery: string | null
  aiResponse: string | null
}

export type DeploymentPocConversationResponse = {
  id: string | null
  ownerId: string | null
  status: string | null
  createdAt: string | null
  lastInteractionAt: string | null
  turns: DeploymentPocChatTurnSummary[]
}

export type DeploymentPocDatasetSummary = {
  configSource: string
  profileId: string
  profileLabel: string
  profileDescription: string
  upstreamBaseUrl: string | null
  entityTypes: string[]
}

export type DeploymentPocRuntimeIndexingSummary = {
  available: boolean
  vectorDb: string | null
  countsByEntityType: Record<string, number>
  totalVectors: number
  supportsVectorScan: boolean
}

export type DeploymentPocResetCapabilities = {
  clearRuntimeVectors: boolean
  resetConversation: boolean
}

export type DeploymentPocImportRecordRequest = {
  id: string
  content?: string
  entity?: Record<string, unknown>
  metadata?: Record<string, unknown>
}

export type DeploymentPocImportRequest = {
  datasetLabel?: string
  vectorSpace: string
  records: DeploymentPocImportRecordRequest[]
}

export type DeploymentPocImportRunSummary = {
  id: string
  deploymentId: string
  datasetLabel: string
  sourceType: string
  vectorSpace: string
  status: string
  recordCount: number
  importedCount: number
  failedCount: number
  errorMessage: string | null
  createdByActorId: string
  createdByDisplayName: string | null
  createdAt: string | null
}

export type DeploymentPocPromptSessionSummary = {
  id: string | null
  deploymentId: string
  actorId: string
  actorDisplayName: string | null
  sessionLabel: string | null
  active: boolean
  promptKeyCount: number
  promptKeys: string[]
  updatedAt: string | null
}

export type DeploymentPocWorkspaceSummary = {
  dataset: DeploymentPocDatasetSummary
  indexing: DeploymentPocRuntimeIndexingSummary
  resetCapabilities: DeploymentPocResetCapabilities
  recentImports: DeploymentPocImportRunSummary[]
  warnings: string[]
}

export type DeploymentPocRuntimeResetRequest = {
  confirm: boolean
  reason?: string
}

export type DeploymentPocRuntimeResetResponse = {
  success: boolean
  clearedVectors: boolean
  removedVectors: number
  message: string | null
  warnings: string[]
}

export type DeploymentPocScenarioSummary = {
  id: string
  source: string
  title: string
  category: string
  prompt: string
  expectedOutcome: string | null
  editable: boolean
  createdAt: string | null
}

export type UpsertDeploymentPocScenarioRequest = {
  title: string
  category?: string
  prompt: string
  expectedOutcome?: string
}

export type DeploymentPocChatQueryRequest = {
  query: string
  conversationId?: string
  mode?: string
  position?: string
  promptPreview?: Record<string, string>
}

export type UpdateDeploymentPocPromptSessionRequest = {
  sessionLabel?: string
  promptPreview: Record<string, string>
}

export type DeploymentPocTraceDocumentSummary = {
  id: string | null
  title: string | null
  vectorSpace: string | null
  score: number | null
  source: string | null
  url: string | null
}

export type DeploymentPocTraceSummary = {
  resultType: string | null
  success: boolean
  message: string | null
  errorCode: string | null
  executedAction: string | null
  answer: string | null
  actionSummary: string | null
  routingStrategy: string | null
  vectorSpaces: string[]
  candidateVectorSpaces: string[]
  childResultTypes: string[]
  documentCount: number
  documents: DeploymentPocTraceDocumentSummary[]
  actionValidation: unknown | null
}

export type DeploymentPocChatQueryResponse = {
  success: boolean
  message: string | null
  conversationId: string | null
  sessionId: string | null
  result: unknown
  traceSummary: DeploymentPocTraceSummary | null
}

export type DeploymentPocChatSuggestionsRequest = {
  content?: string
  maxSuggestions?: number
}

export type DeploymentPocChatSuggestionsResponse = {
  success: boolean
  message: string | null
  suggestions: string[]
  raw: string | null
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
  canManageUsers: boolean
  canManageSecrets: boolean
  canOperateDeployments: boolean
}

export type PlatformUserSummary = {
  id: string
  email: string
  displayName: string
  role: string
  status: string
  lastLoginAt: string | null
  createdAt: string
  updatedAt: string
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

export type UpdateDeploymentSourceRequest = {
  repository?: string
  branch?: string
}

export type UpdateDeploymentGuardrailsRequest = {
  approvalRequiredForApply: boolean
  approvalRequiredForDelete: boolean
}

export type UpdateDeploymentDraftRequest = {
  actionsConfig?: unknown
  entityConfig?: unknown
  routingConfig?: unknown
  providerConfig?: unknown
  securityConfig?: unknown
  promptConfig?: unknown
}

export type CreateDeploymentPromptRevisionRequest = {
  revisionLabel?: string
  revisionSummary?: string
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
const requestTimeoutMs = 30_000
let platformApiKey = ''

export class PlatformApiError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

export function getStoredPlatformApiKey(): string {
  return platformApiKey
}

export function setStoredPlatformApiKey(value: string) {
  platformApiKey = value.trim()
}

export function clearStoredPlatformApiKey() {
  platformApiKey = ''
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const apiKey = getStoredPlatformApiKey()
  const baseHeaders: HeadersInit = {
    'Content-Type': 'application/json',
    ...(apiKey ? { 'X-PLATFORM-API-KEY': apiKey } : {}),
  }
  const timeoutController = new AbortController()
  const timeoutHandle = globalThis.setTimeout(() => timeoutController.abort(), requestTimeoutMs)
  if (init?.signal) {
    init.signal.addEventListener('abort', () => timeoutController.abort(), { once: true })
  }

  let response: Response
  try {
    response = await fetch(`${apiBaseUrl}${path}`, {
      credentials: 'include',
      headers: {
        ...baseHeaders,
        ...(init?.headers ?? {}),
      },
      ...init,
      signal: timeoutController.signal,
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw new PlatformApiError(408, 'Request timed out.')
    }
    throw error
  } finally {
    globalThis.clearTimeout(timeoutHandle)
  }

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

  if (response.status === 204) {
    return undefined as T
  }

  const contentType = response.headers.get('content-type') ?? ''
  if (!contentType.includes('application/json')) {
    return undefined as T
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

export function restoreDeployment(deploymentId: string) {
  return request<DeploymentOverviewSummary>(`/api/deployments/${deploymentId}/restore`, {
    method: 'POST',
  })
}

export function deleteDeployment(deploymentId: string) {
  return request<void>(`/api/deployments/${deploymentId}`, {
    method: 'DELETE',
  })
}

export function deleteDeploymentWithApproval(deploymentId: string, approvalId?: string) {
  const suffix = approvalId ? `?approvalId=${encodeURIComponent(approvalId)}` : ''
  return request<void>(`/api/deployments/${deploymentId}${suffix}`, {
    method: 'DELETE',
  })
}

export function updateDeploymentSource(deploymentId: string, payload: UpdateDeploymentSourceRequest) {
  return request<DeploymentOverviewSummary>(`/api/deployments/${deploymentId}/source`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function updateDeploymentGuardrails(deploymentId: string, payload: UpdateDeploymentGuardrailsRequest) {
  return request<DeploymentOverviewSummary>(`/api/deployments/${deploymentId}/guardrails`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function fetchDeploymentDraft(deploymentId: string) {
  return request<DeploymentDraftResponse>(`/api/deployments/${deploymentId}/draft`)
}

export function fetchDeploymentWorkspace(deploymentId: string) {
  return request<DeploymentWorkspaceSummary>(`/api/deployments/${deploymentId}/workspace`)
}

export function fetchDeploymentAssignments(deploymentId: string) {
  return request<DeploymentAssignmentSummary[]>(`/api/deployments/${deploymentId}/assignments`)
}

export function fetchDeploymentApprovals(deploymentId: string) {
  return request<DeploymentOperationApprovalSummary[]>(`/api/deployments/${deploymentId}/approvals`)
}

export function createDeploymentApproval(deploymentId: string, payload: {
  operationType: string
  targetVersionId?: string
  reason: string
}) {
  return request<DeploymentOperationApprovalSummary>(`/api/deployments/${deploymentId}/approvals`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function approveDeploymentApproval(approvalId: string, note?: string) {
  return request<DeploymentOperationApprovalSummary>(`/api/deployment-approvals/${approvalId}/approve`, {
    method: 'POST',
    body: JSON.stringify({ note }),
  })
}

export function rejectDeploymentApproval(approvalId: string, note?: string) {
  return request<DeploymentOperationApprovalSummary>(`/api/deployment-approvals/${approvalId}/reject`, {
    method: 'POST',
    body: JSON.stringify({ note }),
  })
}

export function upsertDeploymentAssignment(deploymentId: string, payload: {
  userId: string
  assignmentRole: string
}) {
  return request<DeploymentAssignmentSummary>(`/api/deployments/${deploymentId}/assignments`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function deleteDeploymentAssignment(deploymentId: string, assignmentId: string) {
  return request<void>(`/api/deployments/${deploymentId}/assignments/${assignmentId}`, {
    method: 'DELETE',
  })
}

export function bulkDeploymentAction(payload: {
  action: string
  deploymentIds: string[]
}) {
  return request<BulkDeploymentActionResponse>('/api/deployments/bulk/actions', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
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

export function fetchPlatformUsers() {
  return request<PlatformUserSummary[]>('/api/platform/users')
}

export function createPlatformUser(payload: {
  email: string
  displayName: string
  password: string
  role: string
}) {
  return request<PlatformUserSummary>('/api/platform/users', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updatePlatformUser(userId: string, payload: {
  displayName: string
  role: string
  status: string
}) {
  return request<PlatformUserSummary>(`/api/platform/users/${userId}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function resetPlatformUserPassword(userId: string, payload: { password: string }) {
  return request<PlatformUserSummary>(`/api/platform/users/${userId}/reset-password`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
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

export function fetchDeploymentPromptRevisions(deploymentId: string) {
  return request<DeploymentPromptRevisionSummary[]>(`/api/deployments/${deploymentId}/prompt-revisions`)
}

export function createDeploymentPromptRevision(
  deploymentId: string,
  payload: CreateDeploymentPromptRevisionRequest,
) {
  return request<DeploymentPromptRevisionSummary>(`/api/deployments/${deploymentId}/prompt-revisions`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function restoreDeploymentPromptRevision(deploymentId: string, revisionId: string) {
  return request<DeploymentDraftResponse>(
    `/api/deployments/${deploymentId}/prompt-revisions/${revisionId}/restore`,
    {
      method: 'POST',
    },
  )
}

export function queryDeploymentPocChat(deploymentId: string, payload: DeploymentPocChatQueryRequest) {
  return request<DeploymentPocChatQueryResponse>(`/api/deployments/${deploymentId}/poc-chat/query`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function fetchDeploymentPocWorkspace(deploymentId: string) {
  return request<DeploymentPocWorkspaceSummary>(`/api/deployments/${deploymentId}/poc`)
}

export function fetchDeploymentPocPromptSession(deploymentId: string) {
  return request<DeploymentPocPromptSessionSummary>(`/api/deployments/${deploymentId}/poc/prompt-session`)
}

export function updateDeploymentPocPromptSession(
  deploymentId: string,
  payload: UpdateDeploymentPocPromptSessionRequest,
) {
  return request<DeploymentPocPromptSessionSummary>(`/api/deployments/${deploymentId}/poc/prompt-session`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function clearDeploymentPocPromptSession(deploymentId: string) {
  return request<void>(`/api/deployments/${deploymentId}/poc/prompt-session`, {
    method: 'DELETE',
  })
}

export function runDeploymentPocImport(deploymentId: string, payload: DeploymentPocImportRequest) {
  return request<DeploymentPocImportRunSummary>(`/api/deployments/${deploymentId}/poc/import-runs`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function fetchDeploymentPocChatSuggestions(
  deploymentId: string,
  payload: DeploymentPocChatSuggestionsRequest,
) {
  return request<DeploymentPocChatSuggestionsResponse>(`/api/deployments/${deploymentId}/poc-chat/suggestions`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function fetchDeploymentPocConversation(deploymentId: string, conversationId: string) {
  return request<DeploymentPocConversationResponse>(
    `/api/deployments/${deploymentId}/poc-chat/conversations/${encodeURIComponent(conversationId)}`,
  )
}

export function deleteDeploymentPocConversation(deploymentId: string, conversationId: string) {
  return request<void>(`/api/deployments/${deploymentId}/poc-chat/conversations/${encodeURIComponent(conversationId)}`, {
    method: 'DELETE',
  })
}

export function clearDeploymentPocRuntimeVectors(
  deploymentId: string,
  payload: DeploymentPocRuntimeResetRequest,
) {
  return request<DeploymentPocRuntimeResetResponse>(`/api/deployments/${deploymentId}/poc/reset/runtime-vectors`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function fetchDeploymentPocScenarios(deploymentId: string) {
  return request<DeploymentPocScenarioSummary[]>(`/api/deployments/${deploymentId}/poc/scenarios`)
}

export function createDeploymentPocScenario(
  deploymentId: string,
  payload: UpsertDeploymentPocScenarioRequest,
) {
  return request<DeploymentPocScenarioSummary>(`/api/deployments/${deploymentId}/poc/scenarios`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateDeploymentPocScenario(
  deploymentId: string,
  scenarioId: string,
  payload: UpsertDeploymentPocScenarioRequest,
) {
  return request<DeploymentPocScenarioSummary>(
    `/api/deployments/${deploymentId}/poc/scenarios/${encodeURIComponent(scenarioId)}`,
    {
      method: 'PUT',
      body: JSON.stringify(payload),
    },
  )
}

export function deleteDeploymentPocScenario(deploymentId: string, scenarioId: string) {
  return request<void>(`/api/deployments/${deploymentId}/poc/scenarios/${encodeURIComponent(scenarioId)}`, {
    method: 'DELETE',
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

export function applyDeploymentVersionWithApproval(deploymentId: string, versionId: string, approvalId?: string) {
  const suffix = approvalId ? `?approvalId=${encodeURIComponent(approvalId)}` : ''
  return request<DeploymentReleaseSummary>(`/api/deployments/${deploymentId}/apply/${versionId}${suffix}`, {
    method: 'POST',
  })
}
