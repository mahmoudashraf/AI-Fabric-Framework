export type DeploymentTemplateSummary = {
  id: string
  name: string
  description: string
  llmProvider: string
  embeddingProvider: string
  vectorStrategy: string
  runtimeProfile: string
  connectorProfile: string
  managedVectorProvisioningDefault: boolean
  managedVectorProvisioningMode: string
  managedVectorProvisioningSummary: string
}

export type DeploymentCuratedModuleSummary = {
  id: string
  name: string
  description: string
  runtimeCuratedPack: string | null
  promptPresetId: string
}

export type DeploymentSourceSummary = {
  repository: string
  branch: string
  repositoryOverride: string | null
  branchOverride: string | null
  overrideActive: boolean
}

export type DeploymentTenantBindingSummary = {
  customerId: string | null
  customerName: string
  customerSlug: string | null
  customerStatus: string
  customerPlatformManaged: boolean
  tenantId: string | null
  tenantName: string
  tenantSlug: string | null
  tenantStatus: string
  tenantPlatformManaged: boolean
  mutable: boolean
}

export type DeploymentSummary = {
  id: string
  name: string
  environment: string
  templateId: string
  binding: DeploymentTenantBindingSummary | null
  source: DeploymentSourceSummary
  status: string
  activeVersion: string
  runtimeBaseUrl: string | null
  connectorBaseUrl: string | null
  approvalRequiredForApply: boolean
  approvalRequiredForDelete: boolean
  createdAt: string
}

export type DeleteDeploymentRequest = {
  hardDelete?: boolean
  approvalId?: string
  reason?: string
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
  binding: DeploymentTenantBindingSummary | null
  source: DeploymentSourceSummary
  access: DeploymentWorkspaceAccessSummary
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

export type DeploymentWorkspaceLifecycleSummary = {
  savedDraftState: string
  liveState: string
  hasPublishedVersion: boolean
  hasLiveVersion: boolean
  savedDraftMatchesLatestPublished: boolean
  liveMatchesLatestPublished: boolean
  latestPublishedVersionId: string | null
  latestPublishedVersionLabel: string | null
  latestPublishedAt: string | null
  liveVersionId: string | null
  liveVersionLabel: string | null
  liveAppliedAt: string | null
  summaryMessage: string
}

export type DeploymentWorkspaceAccessSummary = {
  assignmentRole: string
  canOperate: boolean
  canEdit: boolean
  canAdmin: boolean
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
  access: DeploymentWorkspaceAccessSummary
  draft: DeploymentWorkspaceDraftSummary
  lifecycle: DeploymentWorkspaceLifecycleSummary
  latestVersion: DeploymentVersionSummary | null
  latestRelease: DeploymentReleaseSummary | null
  latestVerificationRun: DeploymentVerificationRunSummary | null
  versionCount: number
  releaseCount: number
  verificationRunCount: number
}

export type DeploymentConfigReferenceSummary = {
  stage: string
  referenceId: string | null
  referenceLabel: string
  configHash: string | null
  updatedAt: string | null
  available: boolean
}

export type DeploymentConfigSectionDiffSummary = {
  key: string
  label: string
  draftValue: string
  latestPublishedValue: string
  liveValue: string
  draftMatchesLatestPublished: boolean
  draftMatchesLive: boolean
  liveMatchesLatestPublished: boolean
  driftState: string
  summaryMessage: string
}

export type DeploymentConfigTemplateSourceSummary = {
  templateId: string
  templateName: string
  templateDescription: string
  llmProvider: string
  embeddingProvider: string
  vectorStrategy: string
  vectorProvisioningMode: string
  runtimeProfile: string
  connectorProfile: string
  repository: string
  branch: string
  repositoryOverride: string | null
  branchOverride: string | null
  overrideActive: boolean
}

export type DeploymentConfigDiffCenterSummary = {
  deploymentId: string
  deploymentName: string
  environment: string
  draft: DeploymentConfigReferenceSummary
  latestPublished: DeploymentConfigReferenceSummary
  live: DeploymentConfigReferenceSummary
  templateSource: DeploymentConfigTemplateSourceSummary
  sections: DeploymentConfigSectionDiffSummary[]
  summaryMessage: string
}

export type DeploymentArtifactBundleSummary = {
  deploymentId: string
  deploymentVersionId: string
  versionLabel: string
  configHash: string
  actionsArtifactUrl: string
  entityArtifactUrl: string
  routingArtifactUrl: string
  promptArtifactUrl: string
  manifestUrl: string
}

export type DeploymentServiceConfigFieldSummary = {
  key: string
  label: string
  valueSummary: string
  required: boolean
  configured: boolean
  source: string
  guidance: string
}

export type DeploymentServiceConfigIssueSummary = {
  severity: string
  code: string
  path: string
  message: string
}

export type DeploymentServiceConfigSummary = {
  key: string
  label: string
  surfaceType: string
  platformManaged: boolean
  purpose: string
  status: string
  baseUrl: string | null
  requiredFieldCount: number
  configuredRequiredFieldCount: number
  fields: DeploymentServiceConfigFieldSummary[]
  issues: DeploymentServiceConfigIssueSummary[]
  summaryMessage: string
}

export type DeploymentServiceConfigModelSummary = {
  deploymentId: string
  deploymentName: string
  environment: string
  services: DeploymentServiceConfigSummary[]
  summaryMessage: string
}

export type DeploymentNavigationProviderSummary = {
  provider: string
  mode: string
  projectName: string | null
  projectId: string | null
  projectUrl: string | null
  workspaceId: string | null
  repository: string
  branch: string
  available: boolean
  summaryMessage: string
}

export type DeploymentNavigationSurfaceSummary = {
  key: string
  label: string
  surfaceType: string
  platformManaged: boolean
  purpose: string
  serviceName: string | null
  rootDir: string | null
  dockerfilePath: string | null
  primaryUrl: string | null
  docsUrl: string | null
  openApiUrl: string | null
  adminUrl: string | null
  available: boolean
  summaryMessage: string
}

export type DeploymentNavigationRelationshipSummary = {
  key: string
  fromLabel: string
  toLabel: string
  flowType: string
  summaryMessage: string
}

export type DeploymentServiceNavigationSummary = {
  deploymentId: string
  deploymentName: string
  environment: string
  provider: DeploymentNavigationProviderSummary
  surfaces: DeploymentNavigationSurfaceSummary[]
  relationships: DeploymentNavigationRelationshipSummary[]
  summaryMessage: string
}

export type DeploymentRemediationActionSummary = {
  key: string
  label: string
  description: string
  category: string
  severity: string
  requiredRole: string
  available: boolean
  requiresConfirmation: boolean
  requiresReason: boolean
  requiresApproval: boolean
  blockedReason: string | null
  confirmationText: string
  operatorGuidance: string | null
}

export type DeploymentRemediationSummary = {
  deploymentId: string
  deploymentName: string
  environment: string
  actions: DeploymentRemediationActionSummary[]
  summaryMessage: string
  providerDriftDetected: boolean
  providerDriftStatus: string
  providerDriftMessage: string | null
  managedVectorDriftDetected: boolean
  managedVectorDriftStatus: string
  managedVectorDriftMessage: string | null
}

export type DeploymentRemediationExecutionSummary = {
  actionKey: string
  status: string
  message: string
  referenceType: string
  referenceId: string
}

export type DeploymentProductionReadinessAreaSummary = {
  key: string
  label: string
  status: string
  score: number
  message: string
}

export type DeploymentProductionReadinessOwnerSummary = {
  status: string
  totalAssigned: number
  adminCount: number
  operatorCount: number
  editorCount: number
  viewerCount: number
  message: string
}

export type DeploymentProductionReadinessScorecardSummary = {
  deploymentId: string
  deploymentName: string
  environment: string
  overallStatus: string
  overallScore: number
  latestReleaseStatus: string
  latestVerificationStatus: string
  ownership: DeploymentProductionReadinessOwnerSummary
  areas: DeploymentProductionReadinessAreaSummary[]
  summaryMessage: string
}

export type DeploymentProviderConnectivityProbeSummary = {
  key: string
  label: string
  status: string
  endpoint: string
  message: string
}

export type DeploymentProviderConnectivitySummary = {
  deploymentId: string
  deploymentName: string
  llmProvider: string
  embeddingProvider: string
  vectorStrategy: string
  vectorProvisioningMode: string
  managedVectorProvisioningEnabled: boolean
  managedVectorProvisioningMode: string
  managedVectorTargets: string[]
  managedVectorSummaryMessage: string
  probes: DeploymentProviderConnectivityProbeSummary[]
  summaryMessage: string
}

export type DeploymentSecretUsageItemSummary = {
  secretName: string
  displayName: string
  required: boolean
  present: boolean
  source: string
  status: string
  usedByServices: string[]
  configPaths: string[]
  summaryMessage: string
}

export type DeploymentSecretLiteralRiskSummary = {
  service: string
  path: string
  message: string
}

export type DeploymentSecretUsageSummary = {
  deploymentId: string
  secrets: DeploymentSecretUsageItemSummary[]
  literalRisks: DeploymentSecretLiteralRiskSummary[]
  missingRequiredCount: number
  literalRiskCount: number
  summaryMessage: string
}

export type DeploymentSecurityGovernanceCheckSummary = {
  key: string
  label: string
  status: string
  valueSummary: string
  message: string
  guidance: string
}

export type DeploymentSecurityGovernanceAreaSummary = {
  key: string
  label: string
  status: string
  blockedCount: number
  warningCount: number
  checks: DeploymentSecurityGovernanceCheckSummary[]
  summaryMessage: string
}

export type DeploymentSecurityGovernanceSummary = {
  deploymentId: string
  deploymentName: string
  environment: string
  areas: DeploymentSecurityGovernanceAreaSummary[]
  blockedCount: number
  warningCount: number
  summaryMessage: string
}

export type DeploymentSourceOfTruthGeneratedSummary = {
  provisioningMode: string | null
  artifactStrategy: string | null
  projectName: string | null
  repository: string | null
  branch: string | null
  runtimeServiceName: string | null
  runtimeDockerfilePath: string | null
  runtimeBaseUrl: string | null
  restConnectorServiceName: string | null
  restConnectorDockerfilePath: string | null
  connectorBaseUrl: string | null
}

export type DeploymentRailwayLiveFieldDriftSummary = {
  key: string
  label: string
  expectedValue: string | null
  actualValue: string | null
  driftState: string
  summaryMessage: string
}

export type DeploymentRailwayLiveEnvVarDriftSummary = {
  key: string
  sensitive: boolean
  driftState: string
  expectedValue: string | null
  actualValue: string | null
  summaryMessage: string
}

export type DeploymentRailwayLiveServiceSummary = {
  key: string
  label: string
  serviceId: string | null
  status: string
  summaryMessage: string
  rootDirectory: DeploymentRailwayLiveFieldDriftSummary
  dockerfilePath: DeploymentRailwayLiveFieldDriftSummary
  repository: DeploymentRailwayLiveFieldDriftSummary
  branch: DeploymentRailwayLiveFieldDriftSummary
  publicBaseUrl: DeploymentRailwayLiveFieldDriftSummary
  expectedEnvCount: number
  matchingEnvCount: number
  missingEnvCount: number
  mismatchedEnvCount: number
  envVars: DeploymentRailwayLiveEnvVarDriftSummary[]
}

export type DeploymentRailwayLiveReadbackSummary = {
  available: boolean
  status: string
  summaryMessage: string
  projectId: string | null
  projectName: string | null
  environmentId: string | null
  environmentName: string | null
  runtime: DeploymentRailwayLiveServiceSummary
  restConnector: DeploymentRailwayLiveServiceSummary
}

export type DeploymentManagedVectorResourceSummary = {
  id: string
  deploymentId: string
  deploymentVersionId: string | null
  deploymentReleaseId: string | null
  vendor: string
  vectorStrategy: string
  vectorProvisioningMode: string
  managedMode: string
  resourceType: string
  resourceName: string
  resourceReference: string | null
  endpoint: string | null
  resourceStatus: string
  provisioningState: string | null
  secretReferenceNames: string[]
  details: unknown
  driftState: string
  driftMessage: string | null
  createdAt: string
  updatedAt: string
}

export type DeploymentManagedVectorStateSummary = {
  status: string
  managedRequested: boolean
  vectorStrategy: string
  vectorProvisioningMode: string
  driftDetected: boolean
  driftStatus: string
  activeResourceCount: number
  detachedResourceCount: number
  driftedResourceCount: number
  resources: DeploymentManagedVectorResourceSummary[]
  driftMessage: string | null
  summaryMessage: string
}

export type DeploymentTenantScopedVectorRegistrySummary = {
  status: string
  recordId: string | null
  activeRecordCount: number
  historicalRecordCount: number
  lastUpdatedAt: string | null
  message: string
}

export type DeploymentTenantScopedVectorSummary = {
  status: string
  vectorStrategy: string
  vectorProvisioningMode: string
  vectorStoragePosture: string
  sharedStorage: boolean
  lifecycleOwner: string
  customerId: string | null
  customerName: string
  tenantId: string | null
  tenantName: string
  scopeType: string
  rootResourceLabel: string | null
  rootResourceValue: string | null
  scopePrefix: string | null
  tenantHandle: string | null
  scopePattern: string | null
  migrationLocked: boolean
  migrationMessage: string
  backupRestorePosture: string
  registry: DeploymentTenantScopedVectorRegistrySummary | null
  summaryMessage: string
}

export type DeploymentSourceOfTruthSummary = {
  deploymentId: string
  deploymentName: string
  environment: string
  templateSource: DeploymentConfigTemplateSourceSummary
  draft: DeploymentConfigReferenceSummary
  latestPublished: DeploymentConfigReferenceSummary
  live: DeploymentConfigReferenceSummary
  latestRelease: DeploymentReleaseSummary | null
  latestPublishedArtifacts: DeploymentArtifactBundleSummary | null
  liveArtifacts: DeploymentArtifactBundleSummary | null
  managedVector: DeploymentManagedVectorStateSummary
  tenantScopedVector: DeploymentTenantScopedVectorSummary
  generated: DeploymentSourceOfTruthGeneratedSummary
  liveRailwayReadback: DeploymentRailwayLiveReadbackSummary
  summaryMessage: string
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

export type DeploymentPromptBaselineSummary = {
  deploymentId: string
  versionId: string | null
  versionLabel: string | null
  publishedAt: string | null
  populatedPromptCount: number
  promptConfig: unknown
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

export type DeploymentPocMigrationSourceSummary = {
  key: string
  label: string
  description: string
}

export type DeploymentPocMigrationCheckSummary = {
  key: string
  label: string
  status: string
  message: string
}

export type DeploymentPocMigrationGuideSummary = {
  suggestedDatasetLabel: string
  maxRecordsPerRun: number
  maxContentLength: number
  defaultVectorSpace: string
  supportedVectorSpaces: string[]
  supportedSources: DeploymentPocMigrationSourceSummary[]
  readinessChecks: DeploymentPocMigrationCheckSummary[]
  warnings: string[]
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
  migration: DeploymentPocMigrationGuideSummary
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

export type RailwayWorkspaceCleanupOwnerSummary = {
  deploymentId: string
  deploymentName: string
  environment: string
  archived: boolean
}

export type RailwayWorkspaceOrphanServiceSummary = {
  serviceId: string
  serviceName: string
  projectId: string
  projectName: string
  ownershipState: string
  platformManagedCandidate: boolean
  deletable: boolean
  sourceRepository: string | null
  sourceBranch: string | null
  owners: RailwayWorkspaceCleanupOwnerSummary[]
  summaryMessage: string
}

export type RailwayWorkspaceProjectCleanupSummary = {
  projectId: string
  projectName: string
  ownershipState: string
  platformManagedCandidate: boolean
  deletable: boolean
  totalServiceCount: number
  owners: RailwayWorkspaceCleanupOwnerSummary[]
  orphanServices: RailwayWorkspaceOrphanServiceSummary[]
  summaryMessage: string
}

export type RailwayWorkspaceCleanupSummary = {
  available: boolean
  status: string
  workspaceId: string | null
  workspaceName: string | null
  projectCount: number
  orphanProjectCount: number
  orphanServiceCount: number
  projects: RailwayWorkspaceProjectCleanupSummary[]
  summaryMessage: string
}

export type RailwayWorkspaceCleanupExecutionSummary = {
  status: string
  message: string
  deletedProjectCount: number
  deletedServiceCount: number
  deletedProjectIds: string[]
  deletedServiceIds: string[]
  skippedIds: string[]
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
    prompts: string
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

export type DeploymentHostedVerificationRunSummary = {
  id: string
  deploymentId: string
  releaseId: string
  deploymentVersionId: string
  verificationProfile: string
  runnerType: string
  scriptPath: string
  status: string
  verifyWrite: boolean
  summaryMessage: string
  logOutput: string
  diagnostics: DeploymentHostedVerificationDiagnosticsSummary
  exitCode: number | null
  createdAt: string
  startedAt: string | null
  completedAt: string | null
}

export type DeploymentHostedVerificationStepSummary = {
  status: string
  section: string | null
  message: string
  rawLine: string
}

export type DeploymentHostedVerificationDiagnosticsSummary = {
  headline: string
  passCount: number
  warningCount: number
  failCount: number
  lastPassMessage: string | null
  lastWarningMessage: string | null
  lastFailureMessage: string | null
  steps: DeploymentHostedVerificationStepSummary[]
}

export type DeploymentHostedVerificationDispatchSummary = {
  deploymentId: string
  releaseId: string
  deploymentVersionId: string
  profile: string
  verifyWrite: boolean
  summaryMessage: string
  run: DeploymentHostedVerificationRunSummary
}

export type DeploymentVerificationRolloutItemSummary = {
  key: string
  displayName: string
  description: string
  verificationProfile: string
  writeVerificationSupported: boolean
  deploymentId: string | null
  environment: string
  exists: boolean
  archived: boolean
  verificationReady: boolean
  deploymentStatus: string | null
  activeVersionId: string | null
  latestReleaseStatus: string | null
  latestProvisioningStatus: string | null
  latestVerificationStatus: string | null
  runtimeBaseUrl: string | null
  connectorBaseUrl: string | null
  missingPrerequisites: string[]
}

export type DeploymentVerificationRolloutSummary = {
  summaryMessage: string
  items: DeploymentVerificationRolloutItemSummary[]
}

export type PlatformRailwayServiceDiscoverySummary = {
  available: boolean
  summaryMessage: string
  publicHost: string | null
  projectId: string | null
  projectName: string | null
  environmentId: string | null
  environmentName: string | null
  serviceId: string | null
  serviceName: string | null
  domain: string | null
  latestDeploymentId: string | null
  latestDeploymentStatus: string | null
  latestDeploymentUrl: string | null
  latestDeploymentStaticUrl: string | null
  latestDeploymentCreatedAt: string | null
  rootDirectory: string | null
  dockerfilePath: string | null
  healthcheckPath: string | null
  upstreamUrl: string | null
  sourceRepo: string | null
  sourceImage: string | null
  triggerRepository: string | null
  triggerBranch: string | null
}

export type PlatformRailwayLogsResponse = {
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

export type PlatformDiagnosticsSummary = {
  name: string
  stage: string
  currentPhase: string
  publicBaseUrl: string
  provisioningMode: string
  workspaceId: string
  repository: string
  branch: string
  summaryMessage: string
  railwayPreflight: RailwayPreflightSummary | null
  railwayPreflightError: string | null
  railwayService: PlatformRailwayServiceDiscoverySummary
  recentHostedVerificationRuns: DeploymentHostedVerificationRunSummary[]
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

export type DeploymentListViewPreferences = {
  showArchived: boolean
  searchTerm: string
  healthFilter: string
  roleFilter: string
  templateFilter: string
}

export type DeploymentWorkspacePreferences = {
  lastDeploymentId: string | null
  lastSection: string | null
}

export type DeploymentActivityViewPreferences = {
  categoryFilter: string
  actorRoleFilter: string
  searchTerm: string
}

export type DeploymentApprovalsViewPreferences = {
  statusFilter: string
  operationFilter: string
  mineOnly: boolean
  searchTerm: string
}

export type DeploymentRevisionsViewPreferences = {
  searchTerm: string
  versionStatusFilter: string
  releaseStatusFilter: string
  reindexFilter: string
}

export type PlatformUserPreferences = {
  deploymentListView: DeploymentListViewPreferences
  deploymentWorkspace: DeploymentWorkspacePreferences
  deploymentActivityView: DeploymentActivityViewPreferences
  deploymentApprovalsView: DeploymentApprovalsViewPreferences
  deploymentRevisionsView: DeploymentRevisionsViewPreferences
}

export type UpdatePlatformUserPreferencesRequest = {
  deploymentListView?: DeploymentListViewPreferences
  deploymentWorkspace?: DeploymentWorkspacePreferences
  deploymentActivityView?: DeploymentActivityViewPreferences
  deploymentApprovalsView?: DeploymentApprovalsViewPreferences
  deploymentRevisionsView?: DeploymentRevisionsViewPreferences
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

export type PlatformUserDeploymentAccessSummary = {
  assignmentId: string
  deploymentId: string
  deploymentName: string
  deploymentEnvironment: string
  deploymentStatus: string
  assignmentRole: string
  createdAt: string
  updatedAt: string
}

export type PlatformUserAccessSummary = PlatformUserSummary & {
  assignmentCount: number
  adminAssignmentCount: number
  editorAssignmentCount: number
  operatorAssignmentCount: number
  viewerAssignmentCount: number
  selectedDeploymentAssignment: PlatformUserDeploymentAccessSummary | null
  assignedDeployments: PlatformUserDeploymentAccessSummary[]
}

export type PlatformTenantSummary = {
  id: string
  customerId: string
  customerName: string
  name: string
  slug: string
  description: string | null
  status: string
  platformManaged: boolean
  boundDeploymentId: string | null
  boundDeploymentName: string | null
  boundDeploymentEnvironment: string | null
  sharedVector: PlatformTenantSharedVectorSummary | null
  createdAt: string
  updatedAt: string
}

export type PlatformTenantSharedVectorSummary = {
  activeHandleCount: number
  historicalHandleCount: number
  latestStatus: string
  latestVectorStrategy: string | null
  latestScopeType: string | null
  latestScopePattern: string | null
  latestUpdatedAt: string | null
  latestSummary: string
}

export type PlatformCustomerSummary = {
  id: string
  name: string
  slug: string
  description: string | null
  status: string
  platformManaged: boolean
  tenantCount: number
  deploymentCount: number
  createdAt: string
  updatedAt: string
  tenants: PlatformTenantSummary[]
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
  curatedModuleId: string
  vectorProvisioningMode: string
  customerId?: string
  tenantId?: string
}

export type UpdateDeploymentTenantBindingRequest = {
  customerId?: string
  tenantId?: string
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

export type UpdateDeploymentCuratedModuleRequest = {
  curatedModuleId: string
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

export function fetchPlatformUserPreferences() {
  return request<PlatformUserPreferences>('/api/platform/preferences')
}

export function updatePlatformUserPreferences(payload: UpdatePlatformUserPreferencesRequest) {
  return request<PlatformUserPreferences>('/api/platform/preferences', {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function fetchPlatformAuditEvents() {
  return request<PlatformAuditEventSummary[]>('/api/platform/audit-events')
}

export function fetchDeploymentActivity(deploymentId: string, limit = 100) {
  return request<PlatformAuditEventSummary[]>(
    `/api/deployments/${encodeURIComponent(deploymentId)}/activity?limit=${limit}`,
  )
}

export function fetchDeploymentTemplates() {
  return request<DeploymentTemplateSummary[]>('/api/deployment-templates')
}

export function fetchDeploymentCuratedModules() {
  return request<DeploymentCuratedModuleSummary[]>('/api/deployment-curated-modules')
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

export function updateDeploymentTenantBinding(
  deploymentId: string,
  payload: UpdateDeploymentTenantBindingRequest,
) {
  return request<DeploymentOverviewSummary>(`/api/deployments/${deploymentId}/tenant-binding`, {
    method: 'PUT',
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

export function deleteDeployment(deploymentId: string, payload?: DeleteDeploymentRequest) {
  return request<void>(`/api/deployments/${deploymentId}`, {
    method: 'DELETE',
    body: payload ? JSON.stringify(payload) : undefined,
  })
}

export function deleteDeploymentWithApproval(deploymentId: string, approvalId?: string) {
  return deleteDeployment(deploymentId, { approvalId })
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

export function fetchDeploymentConfigDiffCenter(deploymentId: string) {
  return request<DeploymentConfigDiffCenterSummary>(`/api/deployments/${deploymentId}/config-diff-center`)
}

export function fetchDeploymentServiceConfigModel(deploymentId: string) {
  return request<DeploymentServiceConfigModelSummary>(`/api/deployments/${deploymentId}/service-config-model`)
}

export function fetchDeploymentServiceNavigation(deploymentId: string) {
  return request<DeploymentServiceNavigationSummary>(`/api/deployments/${deploymentId}/service-navigation`)
}

export function fetchDeploymentRemediation(deploymentId: string) {
  return request<DeploymentRemediationSummary>(`/api/deployments/${deploymentId}/remediation`)
}

export function fetchDeploymentProductionReadiness(deploymentId: string) {
  return request<DeploymentProductionReadinessScorecardSummary>(
    `/api/deployments/${deploymentId}/production-readiness`,
  )
}

export function fetchDeploymentProviderConnectivity(deploymentId: string) {
  return request<DeploymentProviderConnectivitySummary>(
    `/api/deployments/${deploymentId}/provider-connectivity`,
  )
}

export function probeDeploymentProviderConnectivity(deploymentId: string, payload: {
  providerConfig: unknown
}) {
  return request<DeploymentProviderConnectivitySummary>(
    `/api/deployments/${deploymentId}/provider-connectivity/probe`,
    {
      method: 'POST',
      body: JSON.stringify(payload),
    },
  )
}

export function executeDeploymentRemediation(deploymentId: string, actionKey: string, payload: {
  confirm?: boolean
  reason?: string
  approvalId?: string
}) {
  return request<DeploymentRemediationExecutionSummary>(
    `/api/deployments/${deploymentId}/remediation/${actionKey}`,
    {
      method: 'POST',
      body: JSON.stringify(payload),
    },
  )
}

export function fetchDeploymentSecretUsage(deploymentId: string) {
  return request<DeploymentSecretUsageSummary>(`/api/deployments/${deploymentId}/secret-usage`)
}

export function fetchDeploymentSecurityGovernance(deploymentId: string) {
  return request<DeploymentSecurityGovernanceSummary>(`/api/deployments/${deploymentId}/security-governance`)
}

export function fetchDeploymentSourceOfTruth(deploymentId: string) {
  return request<DeploymentSourceOfTruthSummary>(`/api/deployments/${deploymentId}/source-of-truth`)
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

export function fetchPlatformDiagnostics() {
  return request<PlatformDiagnosticsSummary>('/api/platform/diagnostics')
}

export function fetchPlatformDiagnosticsLogs(options?: {
  source?: string
  limit?: number
  filter?: string
  startDate?: string
  endDate?: string
}) {
  const params = new URLSearchParams()
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
  return request<PlatformRailwayLogsResponse>(`/api/platform/diagnostics/logs${suffix}`)
}

export function fetchRailwayWorkspaceCleanup() {
  return request<RailwayWorkspaceCleanupSummary>('/api/platform/provisioning/railway/workspace-cleanup')
}

export function executeRailwayWorkspaceCleanup(payload: {
  confirm: boolean
  reason: string
  projectIds?: string[]
  serviceIds?: string[]
}) {
  return request<RailwayWorkspaceCleanupExecutionSummary>('/api/platform/provisioning/railway/workspace-cleanup', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function fetchPlatformSecrets() {
  return request<PlatformSecretSummary[]>('/api/platform/secrets')
}

export function fetchPlatformSecretAuditEvents() {
  return request<PlatformAuditEventSummary[]>('/api/platform/secrets/audit-events')
}

export function fetchPlatformUsers() {
  return request<PlatformUserSummary[]>('/api/platform/users')
}

export function fetchPlatformCustomers() {
  return request<PlatformCustomerSummary[]>('/api/platform/customers')
}

export function createPlatformCustomer(payload: {
  name: string
  description?: string
}) {
  return request<PlatformCustomerSummary>('/api/platform/customers', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updatePlatformCustomer(customerId: string, payload: {
  name: string
  description?: string
}) {
  return request<PlatformCustomerSummary>(`/api/platform/customers/${customerId}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function createPlatformTenant(customerId: string, payload: {
  name: string
  description?: string
}) {
  return request<PlatformTenantSummary>(`/api/platform/customers/${customerId}/tenants`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updatePlatformTenant(tenantId: string, payload: {
  name: string
  description?: string
}) {
  return request<PlatformTenantSummary>(`/api/platform/customers/tenants/${tenantId}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function fetchPlatformUserAccessOverview(deploymentId?: string) {
  const params = new URLSearchParams()
  if (deploymentId) {
    params.set('deploymentId', deploymentId)
  }
  const suffix = params.toString().length > 0 ? `?${params.toString()}` : ''
  return request<PlatformUserAccessSummary[]>(`/api/platform/users/access-overview${suffix}`)
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

export function reconcileDeploymentRelease(deploymentId: string) {
  return request<DeploymentReleaseSummary>(`/api/deployments/${deploymentId}/releases/reconcile`, {
    method: 'POST',
  })
}

export function fetchDeploymentVerificationRuns(deploymentId: string) {
  return request<DeploymentVerificationRunSummary[]>(`/api/deployments/${deploymentId}/verification-runs`)
}

export function fetchDeploymentHostedVerificationRuns(deploymentId: string) {
  return request<DeploymentHostedVerificationRunSummary[]>(
    `/api/deployments/${deploymentId}/hosted-verifications`,
  )
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

export function dispatchDeploymentHostedVerification(
  deploymentId: string,
  payload: {
    profile: string
    verifyWrite?: boolean
  },
) {
  return request<DeploymentHostedVerificationDispatchSummary>(
    `/api/deployments/${deploymentId}/hosted-verifications`,
    {
      method: 'POST',
      body: JSON.stringify(payload),
    },
  )
}

export function fetchDeploymentVerificationRollouts() {
  return request<DeploymentVerificationRolloutSummary>('/api/deployments/verification-rollouts')
}

export function recreateDeploymentVerificationRollouts() {
  return request<DeploymentVerificationRolloutSummary>('/api/deployments/verification-rollouts/recreate', {
    method: 'POST',
  })
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

export function applyDeploymentCuratedModule(
  deploymentId: string,
  payload: UpdateDeploymentCuratedModuleRequest,
) {
  return request<DeploymentDraftResponse>(`/api/deployments/${deploymentId}/curated-module`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function fetchDeploymentPromptRevisions(deploymentId: string) {
  return request<DeploymentPromptRevisionSummary[]>(`/api/deployments/${deploymentId}/prompt-revisions`)
}

export function fetchDeploymentPromptBaseline(deploymentId: string) {
  return request<DeploymentPromptBaselineSummary>(`/api/deployments/${deploymentId}/prompt-baseline`)
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
