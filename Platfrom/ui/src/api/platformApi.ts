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
  publishedVersionCount: number
  releaseCount: number
  bindingChangeStatus: string
  bindingChangeMessage: string
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
  connectorProvisioned: boolean
  approvalRequiredForApply: boolean
  approvalRequiredForDelete: boolean
  createdAt: string
}

export type DeleteDeploymentRequest = {
  hardDelete?: boolean
  approvalId?: string
  reason?: string
}

export type DeploymentDeletionStatusSummary = {
  operationId: string
  status: string
  message: string
  requestedAt: string | null
  failureMessage: string | null
}

export type DeploymentDeletionOperationSummary = {
  id: string
  deploymentId: string
  deploymentName: string
  environmentName: string
  customerId: string | null
  tenantId: string | null
  status: string
  statusMessage: string
  hardDelete: boolean
  approvalId: string | null
  requestReason: string | null
  requestedByActorId: string
  requestedByRole: string
  requestDetails: unknown
  resultDetails: unknown
  errorMessage: string | null
  createdAt: string
  startedAt: string | null
  completedAt: string | null
  updatedAt: string
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
  connectorProvisioned: boolean
  approvalRequiredForApply: boolean
  approvalRequiredForDelete: boolean
  latestRelease: DeploymentLifecycleSnapshotSummary | null
  latestVerification: DeploymentVerificationSnapshotSummary | null
  deletion: DeploymentDeletionStatusSummary | null
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

export type MarketplacePluginContributionSummary = {
  templateCuratedModuleId: string | null
  actionIds: string[]
  knowledgeSourceIds: string[]
  shellModuleIds: string[]
  shellCardIds: string[]
  automationWorkflowIds: string[]
  inferenceProfileIds: string[]
  inferenceEndpointProfileRefs: string[]
  inferenceManagedServiceRefs: string[]
}

export type PlatformManagedInferenceEndpointSummary = {
  id: string
  profileRef: string
  serviceId: string | null
  endpointPurpose: string | null
  displayName: string
  providerType: string
  protocolType: string | null
  baseUrl: string | null
  deploymentName: string | null
  apiVersion: string | null
  secretName: string | null
  status: string
}

export type PlatformManagedInferenceServiceSummary = {
  id: string
  serviceRef: string
  displayName: string
  serviceKind: string
  deploymentMode: string
  providerType: string
  protocolType: string
  modelId: string | null
  environmentScope: string | null
  tierScope: string | null
  deploymentId: string | null
  railwayProjectId: string | null
  railwayEnvironmentId: string | null
  railwayServiceId: string | null
  desiredReplicas: number | null
  actualReplicas: number | null
  minReplicas: number | null
  maxReplicas: number | null
  autoscalingMode: string | null
  baseUrl: string | null
  privateNetworkUrl: string | null
  healthPath: string | null
  secretName: string | null
  status: string
  secretConfigured: boolean
  lastDeploymentId: string | null
  lastReconciledAt: string | null
  lastReconcileStatus: string | null
  lastReconcileMessage: string | null
  lastHealthyAt: string | null
  lastProbeAt: string | null
  lastSuccessfulProbeAt: string | null
  lastFailedProbeAt: string | null
  lastProbeStatus: string | null
  lastProbeMessage: string | null
  lastVerifiedOperation: string | null
  lastVerifiedAt: string | null
  lastVerifiedStatus: string | null
  lastVerifiedMessage: string | null
  driftStatus: string | null
  driftMessage: string | null
  dependentDeploymentsCount: number
  dependentActiveDeploymentsCount: number
  endpoints: PlatformManagedInferenceEndpointSummary[]
}

export type UpdatePlatformManagedInferenceServiceScaleRequest = {
  desiredReplicas: number
}

export type RotatePlatformManagedInferenceServiceSecretRequest = {
  value: string
}

export type PlatformManagedInferenceProbeSummary = {
  key: string
  label: string
  status: string
  endpoint: string | null
  method: string
  message: string
  checkedAt: string
}

export type PlatformManagedInferenceHealthSummary = {
  serviceRef: string
  status: string
  railwayLifecycleManaged: boolean
  secretConfigured: boolean
  driftStatus: string
  driftMessage: string
  lastHealthyAt: string | null
  lastProbeAt: string | null
  lastSuccessfulProbeAt: string | null
  lastFailedProbeAt: string | null
  lastProbeStatus: string | null
  lastProbeMessage: string | null
  healthProbe: PlatformManagedInferenceProbeSummary
  inferenceProbe: PlatformManagedInferenceProbeSummary
}

export type PlatformManagedInferenceDependentDeploymentSummary = {
  deploymentId: string
  deploymentName: string
  environmentName: string
  deploymentStatus: string
  activeVersionId: string | null
  currentDraftUsesService: boolean
  activeVersionUsesService: boolean
  runtimeActive: boolean
  usages: string[]
}

export type PlatformManagedProductServiceSummary = {
  id: string
  serviceRef: string
  displayName: string
  productFamily: string
  serviceKind: string
  deploymentMode: string
  tenantMode: string
  environmentScope: string | null
  deploymentId: string | null
  railwayProjectId: string | null
  railwayEnvironmentId: string | null
  railwayServiceId: string | null
  desiredReplicas: number | null
  actualReplicas: number | null
  minReplicas: number | null
  maxReplicas: number | null
  baseUrl: string | null
  privateNetworkUrl: string | null
  healthPath: string | null
  serviceRoot: string | null
  dockerfilePath: string | null
  secretName: string | null
  status: string
  secretConfigured: boolean
  lastDeploymentId: string | null
  lastReconciledAt: string | null
  lastReconcileStatus: string | null
  lastReconcileMessage: string | null
  lastHealthyAt: string | null
  lastProbeAt: string | null
  lastSuccessfulProbeAt: string | null
  lastFailedProbeAt: string | null
  lastProbeStatus: string | null
  lastProbeMessage: string | null
  lastVerifiedOperation: string | null
  lastVerifiedAt: string | null
  lastVerifiedStatus: string | null
  lastVerifiedMessage: string | null
  driftStatus: string | null
  driftMessage: string | null
  dependentStoresCount: number
  activeDependentStoresCount: number
}

export type PlatformManagedProductServiceProbeSummary = {
  status: string
  method: string
  endpoint: string | null
  statusCode: number
  message: string
  checkedAt: string
}

export type PlatformManagedProductServiceHealthSummary = {
  serviceRef: string
  status: string
  railwayLifecycleManaged: boolean
  secretConfigured: boolean
  driftStatus: string
  driftMessage: string
  lastHealthyAt: string | null
  lastProbeAt: string | null
  lastSuccessfulProbeAt: string | null
  lastFailedProbeAt: string | null
  lastProbeStatus: string | null
  lastProbeMessage: string | null
  healthProbe: PlatformManagedProductServiceProbeSummary
}

export type PlatformManagedProductServiceOverviewSummary = {
  serviceRef: string
  status: string
  summaryMessage: string
  appName: string | null
  productFamily: string | null
  serviceKind: string | null
  environmentScope: string | null
  platformBaseUrl: string | null
  publicBaseUrl: string | null
  adminApiKeyConfigured: boolean
  serverStartedAt: string | null
  installs: {
    totalCount: number
    installedCount: number
    uninstalledCount: number
    credentialReadyCount: number
    lastAuthenticatedAt: string | null
    lastUninstalledAt: string | null
  }
  stores: {
    platformAccessStatus: string
    platformAccessMessage: string
    totalCount: number
    readyForGoLiveCount: number
    storefrontReadyCount: number
    liveCount: number
    blockedCount: number
    lastWebhookAt: string | null
  }
  webhookSubscriptions: {
    status: string
    message: string
    webhookUri: string | null
    expectedCount: number
    expectedTopics: string[]
  }
  billing: PlatformManagedProductServiceBillingSummary | null
  usage: {
    generatedAt: string | null
    lastActivityAt: string | null
    activeShopsToday: number
    activeShopsLast7Days: number
    totalToday: number
    totalLast7Days: number
    todayBreakdown: Array<{
      eventType: string
      count: number
    }>
    last7DayBreakdown: Array<{
      eventType: string
      count: number
    }>
  } | null
  capabilities: string[]
  notYetImplemented: string[]
}

export type PlatformManagedProductServiceBillingSummary = {
  mode: string | null
  tierKey: string | null
  planName: string | null
  status: string | null
  merchantApprovalRequired: boolean
  launchBlocked: boolean
  paidTier: boolean
  actionCapable: boolean
  catalogProductCap: number | null
  syncCadence: string | null
  poweredByBadgeRequired: boolean
  chatFallbackEnabled: boolean
  requiresExplicitConfirmation: boolean
  auditTrailAvailable: boolean
  actionPackages: string[]
  allowedSurfaces: string[]
  availablePlans: Array<{
    tierKey: string | null
    planName: string | null
    amount: string | null
    currencyCode: string | null
    interval: string | null
    active: boolean
    commerciallyAvailable: boolean
    merchantApprovalSupported: boolean
    actionCapable: boolean
    catalogProductCap: number | null
    syncCadence: string | null
    poweredByBadgeRequired: boolean
    chatFallbackEnabled: boolean
    requiresExplicitConfirmation: boolean
    auditTrailAvailable: boolean
    actionPackages: string[]
    allowedSurfaces: string[]
    message: string | null
  }>
  message: string | null
}

export type PlatformManagedProductServiceStoreBillingSummary = PlatformManagedProductServiceBillingSummary & {
  shopDomain: string | null
}

export type PlatformManagedProductServiceRailwayDeploymentSummary = {
  id: string
  status: string | null
  url: string | null
  staticUrl: string | null
  createdAt: string | null
}

export type PlatformManagedProductServiceDeploymentHistorySummary = {
  serviceRef: string
  available: boolean
  message: string
  railwayProjectId: string | null
  railwayEnvironmentId: string | null
  railwayServiceId: string | null
  generatedAt: string
  deployments: PlatformManagedProductServiceRailwayDeploymentSummary[]
}

export type PlatformManagedProductServiceRailwayLogsSummary = {
  serviceRef: string
  source: string
  available: boolean
  message: string
  railwayProjectId: string | null
  railwayEnvironmentId: string | null
  railwayServiceId: string | null
  railwayDeploymentId: string | null
  requestedLimit: number
  filter: string | null
  startDate: string | null
  endDate: string | null
  queriedAt: string
  entries: RailwayLogEntrySummary[]
}

export type PlatformManagedProductServiceWebhookSubscriptionSummary = {
  shopDomain: string | null
  status: string
  message: string
  webhookUri: string | null
  expectedCount: number
  readyCount: number
  missingCount: number
  driftedCount: number
  checkedAt: string | null
  topics: Array<{
    topic: string | null
    expectedName: string | null
    status: string
    subscriptionId: string | null
    subscriptionName: string | null
    subscriptionUri: string | null
    message: string | null
  }>
}

export type CreatePlatformManagedProductServiceRequest = {
  serviceRef: string
  displayName: string
  productFamily: string
  serviceKind: string
  deploymentMode: string
  tenantMode: string
  environmentScope: string | null
  deploymentId: string | null
  desiredReplicas: number | null
  minReplicas: number | null
  maxReplicas: number | null
  baseUrl: string | null
  healthPath: string | null
  serviceRoot: string | null
  dockerfilePath: string | null
  secretName: string | null
}

export type UpdatePlatformManagedProductServiceScaleRequest = {
  desiredReplicas: number
}

export type RotatePlatformManagedProductServiceSecretRequest = {
  value: string
}

export type ShopifyStoreSourcePreflightCategorySummary = {
  category: string
  enabled: boolean
  status: string
  itemCount: number
  message: string | null
}

export type ShopifyStoreSourcePreflightSummary = {
  overallStatus: string
  checkedAt: string | null
  categories: ShopifyStoreSourcePreflightCategorySummary[]
}

export type ShopifyStoreSyncSummary = {
  status: string
  checkedAt: string | null
  mode: string | null
  documentCount: number
  message: string | null
}

export type ShopifyStoreWidgetSummary = {
  status: string
  checkedAt: string | null
  channel: string | null
  message: string | null
  settings: {
    launcherLabel: string | null
    welcomeMessage: string | null
    shellModeProfile: string | null
    defaultConversationMode: string | null
    allowedConversationModes: string[]
    pageModeMappings: Record<string, string>
    enabledSurfaces: string[]
  } | null
}

export type ShopifyStoreCredentialSummary = {
  status: string
  accessTokenPresent: boolean
  refreshTokenPresent: boolean
  accessTokenSecretRef: string | null
  refreshTokenSecretRef: string | null
  checkedAt: string | null
  accessTokenExpiresAt: string | null
  refreshTokenExpiresAt: string | null
  scopesText: string | null
  expiring: boolean
}

export type ShopifyStoreWebhookSummary = {
  topic: string | null
  eventType: string | null
  sourceCategory: string | null
  receivedAt: string | null
  invalidateSync: boolean
  message: string | null
}

export type ShopifyStoreCapabilitySummary = {
  actionCount: number
  knowledgeSourceCount: number
  shellModuleCount: number
  marketplaceDatasetCount: number
  actionNames: string[]
  knowledgeSourceIds: string[]
  shellModuleIds: string[]
  marketplaceDatasetIds: string[]
}

export type ShopifyStoreLinkedCustomerSummary = {
  id: string
  name: string | null
  slug: string | null
  status: string | null
  platformManaged: boolean
  createdAt: string | null
  updatedAt: string | null
}

export type ShopifyStoreLinkedDeploymentSummary = {
  id: string
  name: string | null
  environment: string | null
  templateId: string | null
  status: string | null
  customerId: string | null
  tenantId: string | null
  activeVersionId: string | null
  runtimeBaseUrl: string | null
  connectorBaseUrl: string | null
  approvalRequiredForApply: boolean
  approvalRequiredForDelete: boolean
  createdAt: string | null
  updatedAt: string | null
}

export type ShopifyStoreLinkedConsumerSummary = {
  consumerId: string
  customerId: string | null
  displayName: string | null
  status: string | null
  boundDeploymentId: string | null
  boundDeploymentName: string | null
  boundDeploymentEnvironment: string | null
  boundDeploymentStatus: string | null
  lastBoundAt: string | null
  createdAt: string | null
  updatedAt: string | null
}

export type ShopifyStoreBindingInspectionSummary = {
  shopDomain: string
  productServiceRef: string
  customer: ShopifyStoreLinkedCustomerSummary | null
  deployment: ShopifyStoreLinkedDeploymentSummary | null
  consumer: ShopifyStoreLinkedConsumerSummary | null
  latestVersion: DeploymentVersionSummary | null
  latestRelease: DeploymentReleaseSummary | null
  warnings: string[]
}

export type ShopifyStoreConnectionSummary = {
  id: string
  shopDomain: string
  displayName: string | null
  productServiceId: string
  productServiceRef: string
  productServiceDisplayName: string
  customerId: string | null
  customerName: string | null
  deploymentId: string | null
  deploymentName: string | null
  deploymentStatus: string | null
  consumerId: string | null
  consumerDisplayName: string | null
  installStatus: string
  syncStatus: string
  sourceReadinessStatus: string
  widgetStatus: string
  onboardingStatus: string
  productsEnabled: boolean
  collectionsEnabled: boolean
  pagesEnabled: boolean
  policiesEnabled: boolean
  articlesEnabled: boolean
  metaobjectsEnabled: boolean
  credentials: ShopifyStoreCredentialSummary | null
  sourcePreflight: ShopifyStoreSourcePreflightSummary | null
  syncDetail: ShopifyStoreSyncSummary | null
  webhookDetail: ShopifyStoreWebhookSummary | null
  widgetDetail: ShopifyStoreWidgetSummary | null
  capabilities: ShopifyStoreCapabilitySummary | null
  readiness: {
    overallStatus: string
    goLiveEligible: boolean
    storefrontReady: boolean
    goLiveBlockingReasons: string[]
    storefrontBlockingReasons: string[]
    nextActions: string[]
  } | null
  latestVersion: DeploymentVersionSummary | null
  latestRelease: DeploymentReleaseSummary | null
  lastSourcePreflightAt: string | null
  lastSyncAt: string | null
  lastWebhookAt: string | null
  createdAt: string
  updatedAt: string
}

export type ShopifyCompanionPackageProfileSummary = {
  profileKey: string
  packageKey: string
  tierKey: string
  runtimeProfileKey: string
  vectorProfileKey: string
  displayName: string
  description: string | null
  costPosture: string | null
  templatePluginId: string | null
  templatePluginVersion: string | null
  deploymentTemplateId: string | null
  inferencePluginId: string | null
  vectorStrategy: string | null
  vectorProvisioningMode: string | null
  vectorStoragePosture: string | null
  verificationPackId: string | null
  status: string
  detailsJson: string | null
  createdAt: string | null
  updatedAt: string | null
}

export type ShopifyCompanionPackageProfileChoiceSummary = {
  value: string
  label: string
  description: string | null
  source: string
  status: string | null
  recommended: boolean
  packageKeys: string[]
  tierKeys: string[]
  runtimeProfileKeys: string[]
  vectorProfileKeys: string[]
  vectorStrategies: string[]
  vectorProvisioningModes: string[]
  vectorStoragePostures: string[]
}

export type ShopifyCompanionPackageProfileBlueprintSummary = {
  key: string
  label: string
  description: string
  profileKey: string
  packageKey: string
  tierKey: string
  runtimeProfileKey: string
  vectorProfileKey: string
  displayName: string
  costPosture: string
  templatePluginId: string
  templatePluginVersion: string | null
  deploymentTemplateId: string
  inferencePluginId: string
  vectorStrategy: string
  vectorProvisioningMode: string
  vectorStoragePosture: string
  verificationPackId: string
}

export type ShopifyCompanionPackageProfileCompatibilityRuleSummary = {
  packageKey: string
  tierKey: string
  costPosture: string
  defaultProfileKey: string
  defaultRuntimeProfileKey: string
  defaultVectorProfileKey: string
  defaultInferencePluginId: string
  defaultVectorStrategy: string
  defaultVectorProvisioningMode: string
  defaultVectorStoragePosture: string
  defaultDeploymentTemplateId: string
  defaultVerificationPackId: string
  allowedRuntimeProfileKeys: string[]
  allowedVectorProfileKeys: string[]
  allowedInferencePluginIds: string[]
  allowedVectorStrategies: string[]
  allowedVectorProvisioningModes: string[]
  allowedVectorStoragePostures: string[]
  allowedDeploymentTemplateIds: string[]
  allowedVerificationPackIds: string[]
}

export type ShopifyCompanionPackageProfileOptionsSummary = {
  profileKeys: ShopifyCompanionPackageProfileChoiceSummary[]
  packages: ShopifyCompanionPackageProfileChoiceSummary[]
  tiers: ShopifyCompanionPackageProfileChoiceSummary[]
  statuses: ShopifyCompanionPackageProfileChoiceSummary[]
  costPostures: ShopifyCompanionPackageProfileChoiceSummary[]
  runtimeProfiles: ShopifyCompanionPackageProfileChoiceSummary[]
  vectorProfiles: ShopifyCompanionPackageProfileChoiceSummary[]
  templatePlugins: ShopifyCompanionPackageProfileChoiceSummary[]
  templateVersions: ShopifyCompanionPackageProfileChoiceSummary[]
  deploymentTemplates: ShopifyCompanionPackageProfileChoiceSummary[]
  inferencePlugins: ShopifyCompanionPackageProfileChoiceSummary[]
  vectorStrategies: ShopifyCompanionPackageProfileChoiceSummary[]
  vectorProvisioningModes: ShopifyCompanionPackageProfileChoiceSummary[]
  vectorStoragePostures: ShopifyCompanionPackageProfileChoiceSummary[]
  verificationPacks: ShopifyCompanionPackageProfileChoiceSummary[]
  profileBlueprints: ShopifyCompanionPackageProfileBlueprintSummary[]
  compatibilityRules: ShopifyCompanionPackageProfileCompatibilityRuleSummary[]
  generatedAt: string
}

export type UpsertShopifyCompanionPackageProfileRequest = {
  packageKey: string
  tierKey: string
  runtimeProfileKey: string
  vectorProfileKey: string
  displayName: string
  description?: string | null
  costPosture: string
  templatePluginId: string
  templatePluginVersion?: string | null
  deploymentTemplateId: string
  inferencePluginId: string
  vectorStrategy: string
  vectorProvisioningMode: string
  vectorStoragePosture: string
  verificationPackId: string
  status: string
  detailsJson?: string | null
  reason?: string | null
}

export type UpdateShopifyCompanionPackageProfileStatusRequest = {
  status: string
  reason?: string | null
}

export type ShopifyStoreProvisioningJobSummary = {
  id: string
  shopDomain: string
  storeConnectionId: string | null
  jobType: string
  status: string
  phase: string
  requestedPackageKey: string | null
  requestedTierKey: string | null
  requestedRuntimeProfileKey: string | null
  requestedVectorProfileKey: string | null
  previousPackageKey: string | null
  previousRuntimeProfileKey: string | null
  previousVectorProfileKey: string | null
  requestedTemplatePluginId: string | null
  requestedTemplatePluginVersion: string | null
  requestedPluginIds: string[]
  profileChangeStrategy: string | null
  vectorReindexRequired: boolean
  installIntentId: string | null
  attemptCount: number
  maxAttempts: number
  lastErrorCode: string | null
  lastErrorMessage: string | null
  bootstrapDeploymentId: string | null
  verificationRunId: string | null
  nextAction: string | null
  summaryMessage: string | null
  readyAt: string | null
  failedAt: string | null
  cancelledAt: string | null
  createdAt: string
  updatedAt: string
}

export type ShopifyStoreProvisioningStatusSummary = {
  shopDomain: string
  status: string
  phase: string
  nextAction: string | null
  summaryMessage: string | null
  effectiveProfile: ShopifyCompanionPackageProfileSummary | null
  latestJob: ShopifyStoreProvisioningJobSummary | null
  recentJobs: ShopifyStoreProvisioningJobSummary[]
}

export type CreateShopifyStoreProvisioningJobRequest = {
  jobType?: string | null
  requestedPackageKey?: string | null
  requestedTierKey?: string | null
  requestedRuntimeProfileKey?: string | null
  requestedVectorProfileKey?: string | null
  requestedTemplatePluginId?: string | null
  requestedTemplatePluginVersion?: string | null
  requestedPluginIds?: string[] | null
  installIntentId?: string | null
  reason?: string | null
  processImmediately?: boolean | null
}

export type MerchantPartnerAccessRequestSummary = {
  requestId: string
  implementationRequestId: string
  partnerAccountId: string
  partnerAccountName: string
  clientName: string
  contactEmail: string | null
  storeConnectionId: string | null
  assignmentId: string | null
  shopDomain: string
  requestedTier: string | null
  requestedSurfaces: string[]
  notes: string | null
  approvalCode: string
  approvalUrl: string
  status: string
  createdAt: string
  expiresAt: string
  approvedAt: string | null
  revokedAt: string | null
  updatedAt: string
}

export type MerchantPartnerAccessDecisionRequest = {
  approverName: string
  approverEmail?: string | null
  approvedScope?: string | null
  decisionReason?: string | null
}

export type MerchantPartnerAccessDecisionSummary = {
  requestId: string
  assignmentId: string | null
  shopDomain: string
  status: string
  decidedAt: string
}

export type ShopifyStoreGovernedActionAuditSummary = {
  id: string
  actionType: string
  actionPackage: string
  surfaceId: string
  pageType: string
  productHandle: string | null
  productTitle: string | null
  variantId: string | null
  requestedQuantity: number | null
  targetQuantity: number | null
  resultingQuantity: number | null
  confirmationRequired: boolean
  confirmationAccepted: boolean
  shopperSessionRef: string | null
  status: string
  message: string | null
  createdAt: string
  expiresAt: string | null
  completedAt: string | null
}

export type UpsertShopifyStoreConnectionRequest = {
  shopDomain: string
  displayName: string | null
  productServiceRef: string
  customerId: string | null
  deploymentId: string | null
  consumerId: string | null
  installStatus: string | null
  syncStatus: string | null
  sourceReadinessStatus: string | null
  widgetStatus: string | null
  onboardingStatus: string | null
  productsEnabled: boolean | null
  collectionsEnabled: boolean | null
  pagesEnabled: boolean | null
  policiesEnabled: boolean | null
  articlesEnabled: boolean | null
  metaobjectsEnabled: boolean | null
}

export type BootstrapShopifyStoreRequest = {
  customerName?: string | null
  deploymentName?: string | null
  environment?: string | null
  consumerId?: string | null
  templatePluginId?: string | null
  templatePluginVersion?: string | null
  pluginIds?: string[] | null
}

export type ShopifyStoreBootstrapSummary = {
  shopDomain: string
  customerId: string
  deploymentId: string
  consumerId: string
  createdCustomer: boolean
  createdDeployment: boolean
  createdConsumer: boolean
  installedPluginIds: string[]
  store: ShopifyStoreConnectionSummary
}

export type RecordShopifyStoreSourcePreflightRequest = {
  categories: ShopifyStoreSourcePreflightCategorySummary[]
}

export type ShopifyStoreVectorizationRunSummary = {
  id: string
  reason: string
  status: string
  requestedStatus: string
  entityScope: string[]
  createdAt: string
  startedAt: string | null
  completedAt: string | null
  updatedAt: string
}

export type ShopifyStoreVectorizationSourcePolicySummary = {
  sourceCategory: string
  enabled: boolean
  manualIndexAllowed: boolean
  manualReindexAllowed: boolean
  autoIndexingEnabled: boolean
  createTriggerEnabled: boolean
  deleteTriggerEnabled: boolean
  updateTriggerMode: string
  selectedIndexedFields: string[]
  debounceWindowSeconds: number
  minimumRunIntervalSeconds: number
}

export type ShopifyStoreVectorizationPolicySummary = {
  policyVersion: number
  autoIndexingDefault: boolean
  sourcePolicies: ShopifyStoreVectorizationSourcePolicySummary[]
  updatedBy: string | null
  updatedAt: string | null
}

export type ShopifyStoreVectorizationIndexedFieldSummary = {
  fieldKey: string
  sourceCategory: string
  entityType: string
  sourceField: string
  label: string
  selectableForTriggerPolicy: boolean
}

export type ShopifyStoreVectorizationAutomationSummary = {
  autoIndexingHealthy: boolean
  queuedEvents: number
  leasedEvents: number
  dispatchedEvents: number
  skippedEvents: number
  failedEvents: number
  deadLetteredEvents: number
  lastAutoEventAt: string | null
  lastSuccessfulAutoIndexAt: string | null
  lastFailedAutoIndexAt: string | null
  lastAutoRunId: string | null
  degradedReasons: string[]
}

export type ShopifyStoreVectorizationEventSummary = {
  id: string
  sourceCategory: string
  entityType: string
  sourceObjectId: string | null
  shopifyTopic: string | null
  operation: string
  status: string
  triggerReason: string | null
  failureCode: string | null
  coalescedRunId: string | null
  shopifyWebhookId: string | null
  occurredAt: string
  queuedAt: string
  lastAttemptAt: string | null
  completedAt: string | null
  notes: string | null
}

export type ShopifyStoreVectorizationSummary = {
  shopDomain: string
  deploymentId: string | null
  bootstrapped: boolean
  selectedCategories: string[]
  selectedEntityTypes: string[]
  requiredPluginIds: string[]
  installedPluginIds: string[]
  missingPluginIds: string[]
  disabledPluginIds: string[]
  reconciliationRequired: boolean
  connectionConfigured: boolean
  sourceConnectionId: string | null
  sourceConnectionStatus: string | null
  sourceAdapterType: string | null
  planConfigured: boolean
  planId: string | null
  planStatus: string | null
  runnerConfigured: boolean
  runnerRegistrationId: string | null
  runnerRegistrationStatus: string | null
  deploymentApplyInProgress: boolean
  deploymentApplyStatus: string | null
  runnerMode: string | null
  syncState: string | null
  readyToRun: boolean
  blockingReasons: string[]
  lastRun: ShopifyStoreVectorizationRunSummary | null
  policy: ShopifyStoreVectorizationPolicySummary
  effectiveIndexedFields: ShopifyStoreVectorizationIndexedFieldSummary[]
  automation: ShopifyStoreVectorizationAutomationSummary
  recentEvents: ShopifyStoreVectorizationEventSummary[]
}

export type ShopifyStoreVectorizationSourcePolicyInput = {
  sourceCategory: string
  autoIndexingEnabled?: boolean
  createTriggerEnabled?: boolean
  deleteTriggerEnabled?: boolean
  updateTriggerMode?: string
  selectedIndexedFields?: string[]
  debounceWindowSeconds?: number
  minimumRunIntervalSeconds?: number
}

export type UpdateShopifyStoreVectorizationPolicyRequest = {
  policyVersion: number
  sourcePolicies: ShopifyStoreVectorizationSourcePolicyInput[]
}

export type ShopifyStoreVectorizationSelectedEntitiesRequest = {
  entityTypes: string[]
}

export type MarketplacePluginPricingSummary = {
  pricingModel: string
  amount: number | null
  currency: string | null
  billingInterval: string | null
  trialDays: number | null
  requiresEntitlement: boolean
}

export type MarketplacePluginSummary = {
  id: string
  slug: string
  displayName: string
  pluginType: string
  publisherSlug: string
  publisherDisplayName: string
  shortDescription: string
  status: string
  latestVersion: string | null
  pricing: MarketplacePluginPricingSummary
  categories: string[]
  contributions: MarketplacePluginContributionSummary | null
  updatedAt: string
}

export type MarketplacePluginVersionSummary = {
  id: string
  pluginId: string
  version: string
  releaseChannel: string
  status: string
  manifest: unknown
  pricing: MarketplacePluginPricingSummary
  compatibility: {
    minPlatformVersion: string | null
    maxPlatformVersion: string | null
    requiredCapabilities: string[]
    supportedDeploymentTargets: string[]
    supportedAuthModes: string[]
    supportedProviderModes: string[]
  }
  installForm: Array<{
    id: string
    label: string
    type: string
    required: boolean
    description: string | null
    options: string[]
  }>
  permissions: {
    contributesTemplate: boolean
    contributesActions: boolean
    contributesKnowledgeSources: boolean
    contributesProviders: boolean
    contributesShellPresentation: boolean
    requiresExternalHttpExecution: boolean
    requiresSharedDatasetAccess: boolean
    requiresDeploymentSecrets: boolean
  }
  contributions: MarketplacePluginContributionSummary
  recommendedPluginIds: string[]
  publishedAt: string
}

export type MarketplacePluginDetailSummary = {
  plugin: MarketplacePluginSummary
  versions: MarketplacePluginVersionSummary[]
}

export type MarketplaceCategorySummary = {
  id: string
  label: string
  pluginCount: number
}

export type MarketplacePublisherSummary = {
  id: string
  slug: string
  displayName: string
  contactEmail: string
  ownerUserId: string
  verificationStatus: string
  status: string
  submissionCount: number
  createdAt: string
  updatedAt: string
}

export type MarketplacePublisherSubmissionSummary = {
  pluginVersionId: string
  publisherId: string
  pluginId: string
  pluginSlug: string
  pluginDisplayName: string
  pluginType: string
  version: string
  releaseChannel: string
  status: string
  bundleSha256: string | null
  reviewNotes: string | null
  manifest: unknown
  submittedAt: string
  submittedByActorId: string | null
  reviewedAt: string | null
  reviewedByActorId: string | null
}

export type MarketplacePublisherDetailSummary = {
  publisher: MarketplacePublisherSummary
  submissions: MarketplacePublisherSubmissionSummary[]
}

export type CreateMarketplacePublisherRequest = {
  slug: string
  displayName: string
  contactEmail: string
}

export type UpdateMarketplacePublisherVerificationRequest = {
  verificationStatus?: string
  status?: string
}

export type CreateMarketplacePublisherSubmissionRequest = {
  pluginSlug?: string
  releaseChannel?: string
  manifest: unknown
}

export type ReviewMarketplacePublisherSubmissionRequest = {
  reviewNotes?: string | null
}

export type DeploymentMarketplaceInstallSummary = {
  id: string
  deploymentId: string
  pluginId: string
  pluginSlug: string
  pluginDisplayName: string
  pluginType: string
  pluginVersionId: string
  pluginVersion: string
  status: string
  config: unknown
  secretRefs: unknown
  contributions: MarketplacePluginContributionSummary
  readinessStatus: string
  warnings: string[]
  entitlement: {
    pricingModel: string
    amount: number | null
    currency: string | null
    billingInterval: string | null
    status: string
    requiresEntitlement: boolean
    entitledForCompilation: boolean
    graceEndsAt: string | null
    accessEndsAt: string | null
    note: string | null
    updatedAt: string | null
  }
  liveState: string
  createdAt: string
  updatedAt: string
}

export type DeploymentMarketplaceInstallImpactSummary = {
  installId: string
  pluginId: string
  pluginDisplayName: string
  pluginType: string
  pluginVersion: string
  actionIds: string[]
  knowledgeSourceIds: string[]
  shellModuleIds: string[]
  shellCardIds: string[]
  automationWorkflowIds: string[]
  inferenceProfileIds: string[]
  inferenceEndpointProfileRefs: string[]
}

export type DeploymentMarketplaceImpactSummary = {
  deploymentId: string
  totalInstalls: number
  actionPluginCount: number
  dataPluginCount: number
  templatePluginCount: number
  automationPluginCount: number
  inferenceProfilePluginCount: number
  installedPluginIds: string[]
  actionIds: string[]
  knowledgeSourceIds: string[]
  shellModuleIds: string[]
  shellCardIds: string[]
  automationWorkflowIds: string[]
  inferenceProfileIds: string[]
  inferenceEndpointProfileRefs: string[]
  installs: DeploymentMarketplaceInstallImpactSummary[]
  recommendedPluginIds: string[]
  warnings: string[]
}

export type DeploymentMarketplaceInstallResolutionSummary = {
  install: DeploymentMarketplaceInstallSummary
  impact: DeploymentMarketplaceImpactSummary
}

export type CreateDeploymentMarketplaceInstallRequest = {
  pluginId: string
  pluginVersion: string
  config?: unknown
  secretRefs?: unknown
}

export type UpdateDeploymentMarketplaceInstallRequest = {
  pluginVersion?: string
  status?: string
  config?: unknown
  secretRefs?: unknown
}

export type UpdateDeploymentMarketplaceEntitlementRequest = {
  status: string
  graceEndsAt?: string | null
  accessEndsAt?: string | null
  note?: string | null
}

export type CreateMarketplaceTemplateBootstrapRequest = {
  pluginVersion?: string
  name: string
  environment: string
  templateId?: string
  vectorProvisioningMode?: string
  customerId?: string
  tenantId?: string
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

export type DeploymentSecretResolutionSummary = {
  deploymentId: string
  secretPurpose: string
  displayName: string
  paired: boolean
  resolved: boolean
  bindingMode: string | null
  scopeType: string | null
  ownerType: string | null
  secretName: string | null
  secondarySecretName: string | null
  fallbackUsed: boolean
  reasonCode: string
  diagnosticMessage: string
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
  effectiveSecretResolutions: DeploymentSecretResolutionSummary[]
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
  secretPurpose: string | null
  effectiveResolution: DeploymentSecretResolutionSummary | null
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

export type DeploymentIntegrationSummary = {
  preferredIntegrationMode: string
  preferredChatBaseUrl: string | null
  preferredCrudBaseUrl: string | null
  preferredChatQueryUrl: string | null
  preferredSuggestionsUrl: string | null
  preferredConversationsUrl: string | null
  preferredConversationItemUrlTemplate: string | null
  preferredOperationalBaseUrl: string | null
  preferredConnectorOverviewUrl: string | null
  preferredConnectorHealthUrl: string | null
  preferredConnectorActionsOverviewUrl: string | null
  preferredConnectorConfigUrl: string | null
  preferredConnectorLogsUrl: string | null
  preferredAuthContextUrl: string | null
  preferredAuthOverviewUrl: string | null
  verifiedAuthContextRequired: boolean
  trustedBackendAuthorizationHeader: string | null
  privateRuntimeAssertionValidationConfigured: boolean
  privateRuntimeAuthorizationHeader: string | null
  privateRuntimeTokenScheme: string | null
  trustedBackendAcceptedIssuerPolicyConfigured: boolean
  trustedBackendAcceptedAudiencePolicyConfigured: boolean
  trustedBackendPlatformDefaultIssuerPolicy: boolean
  externalTrustedBackendIntegrationReady: boolean
  publicRuntimeBootstrapUrl: string | null
  publicRuntimeAuthorizationHeader: string | null
  publicRuntimeTokenScheme: string | null
  publicRuntimeTokenIssuerHint: string | null
  publicRuntimeDefaultAudience: string | null
  runtimeAuthMode: string | null
  hostBackedRuntimeRequired: boolean
  connectorInternalOnly: boolean
  trustedBackendCallerAuthConfigured: boolean
  publicRuntimeTokenValidationConfigured: boolean
  anonymousBootstrapSupported: boolean
  publicRuntimeAcceptedIssuerPolicyConfigured: boolean
  publicRuntimeAcceptedAudiencePolicyConfigured: boolean
  browserDirectRuntimeAccessSupported: boolean
  browserDirectChatBaseUrl: string | null
  browserDirectCrudBaseUrl: string | null
  backendMediatedRuntimeBaseUrl: string | null
  guidance: string | null
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
  connectorProvisioned: boolean
  restConnectorServiceName: string | null
  restConnectorDockerfilePath: string | null
  vectorizationRunnerServiceName: string | null
  vectorizationRunnerDockerfilePath: string | null
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
  vectorizationRunner: DeploymentRailwayLiveServiceSummary | null
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
  deletionStatus: string | null
  deletionOperationId: string | null
  deletionRequestedAt: string | null
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
  cleanupReadinessStatus: string
  cleanupReadinessMessage: string
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
  knowledgeSourceConfig: unknown
  shellConfig: unknown
  createdAt: string
  updatedAt: string
}

export type DeploymentWebhookDeliverySummary = {
  id: string
  actionName: string
  eventType: string
  targetRef: string
  status: string
  attemptCount: number
  maxAttempts: number
  lastStatusCode: number | null
  lastError: string | null
  deploymentId: string | null
  conversationId: string | null
  requestId: string | null
  nextAttemptAt: string | null
  claimedAt: string | null
  deliveredAt: string | null
  createdAt: string | null
  updatedAt: string | null
  retryEligible: boolean
}

export type DeploymentWebhookOverviewSummary = {
  success: boolean
  contractVersion: string
  retrySupported: boolean
  counts: Record<string, number>
  recentDeliveries: DeploymentWebhookDeliverySummary[]
  retryEligibleCount: number
  limit: number
}

export type DeploymentWebhookRetrySummary = {
  success: boolean
  deliveryId: string
  status: string
  nextAttemptAt: string | null
  summaryMessage: string
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
  subjectId: string | null
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

export type DeploymentPocAuthPath = 'PLATFORM_PRIVATE' | 'PUBLIC_AUTHENTICATED' | 'PUBLIC_ANONYMOUS'

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
  authPath?: DeploymentPocAuthPath
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
  runtimeRequestDurationMs: number | null
  runtimeAuthResolutionMs: number | null
  runtimeContextBuildMs: number | null
  runtimeOrchestrationCallDurationMs: number | null
  runtimeNonPipelineDurationMs: number | null
  pipelineDurationMs: number | null
  extractionProcessingTimeMs: number | null
  extractionProviderProcessingTimeMs: number | null
  extractionLlmCalls: number | null
  extractionAttempts: number | null
  extractionModel: string | null
  extractionPath: string | null
  retrievalProcessingTimeMs: number | null
  embeddingProcessingTimeMs: number | null
  embeddingProviderProcessingTimeMs: number | null
  embeddingCacheHit: boolean | null
  embeddingProviderName: string | null
  embeddingModel: string | null
  responseGenerationProcessingTimeMs: number | null
  responseGenerationProviderProcessingTimeMs: number | null
  responseGenerationModel: string | null
  responseGenerationPath: string | null
  searchProcessingTimeMs: number | null
  stepDurationsMs: Record<string, number>
  documentCount: number
  documents: DeploymentPocTraceDocumentSummary[]
  actionValidation: unknown | null
}

export type DeploymentPocRuntimeAuthContextSummary = {
  subjectId: string | null
  subjectType: string | null
  authMode: string | null
  callerType: string | null
  sessionId: string | null
  deploymentId: string | null
  customerId: string | null
  tenantId: string | null
  issuer: string | null
  expiresAt: string | null
  grantedScopes: string[]
  warnings: string[]
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
  authPath?: DeploymentPocAuthPath
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
  connectorProvisioned: boolean
  readinessMessage: string
  missingPrerequisites: string[]
}

export type DeploymentVerificationRolloutSummary = {
  summaryMessage: string
  items: DeploymentVerificationRolloutItemSummary[]
}

export type PlatformVerificationSuiteStageDefinitionSummary = {
  key: string
  label: string
  stageType: string
  targetRef: string | null
  blocking: boolean
  description: string
}

export type PlatformVerificationSuiteDefinitionSummary = {
  key: string
  label: string
  description: string
  releaseBlocking: boolean
  stages: PlatformVerificationSuiteStageDefinitionSummary[]
}

export type PlatformVerificationSuiteStageRunSummary = {
  id: string
  stageOrder: number
  stageKey: string
  stageLabel: string
  stageType: string
  targetRef: string | null
  blocking: boolean
  status: string
  summaryMessage: string
  details: unknown
  logOutput: string
  createdAt: string
  startedAt: string | null
  completedAt: string | null
}

export type PlatformVerificationSuiteRunSummary = {
  id: string
  suiteKey: string
  suiteLabel: string
  status: string
  releaseBlocking: boolean
  summaryMessage: string
  requestedByActorId: string
  requestedByRole: string
  createdAt: string
  startedAt: string | null
  completedAt: string | null
  stages: PlatformVerificationSuiteStageRunSummary[]
}

export type PlatformVerificationSuiteDispatchSummary = {
  suiteKey: string
  summaryMessage: string
  run: PlatformVerificationSuiteRunSummary
}

export type PlatformVerificationReleaseGateSummary = {
  suiteKey: string
  suiteLabel: string
  ready: boolean
  status: string
  summaryMessage: string
  freshnessWindow: string
  evaluatedAt: string
  expiresAt: string | null
  latestRun: PlatformVerificationSuiteRunSummary | null
}

export type ShopifyReadinessAuditChecklistItemSummary = {
  key: string
  label: string
  category: string
  blocking: boolean
  passCriteria: string
}

export type ShopifyReadinessAuditChecklistResultSummary = ShopifyReadinessAuditChecklistItemSummary & {
  status: string
  evidence: string
  notes: string
}

export type ShopifyReadinessAuditAnswerResultSummary = {
  queryId: string
  surface: string | null
  tierProfile: string | null
  expectedBehavior: string | null
  passed: boolean
  httpStatus: number
  failureCategory: string | null
  answerLength: number
  answerPreview: string | null
  checks: Record<string, boolean>
  forbiddenHits: string[]
  missingRequiredConcepts: string[]
  unsafeActionExecutionHits: string[]
}

export type ShopifyReadinessAuditEvidenceArtifactSummary = {
  name: string
  category: string
  path: string
  description: string
  status: string
}

export type ShopifyReadinessAuditQuerySummary = {
  queryId: string
  tierProfile: string
  surface: string
  query: string
  storefrontContext: Record<string, unknown>
  expectedBehavior: string
  requiredConcepts: string[]
  forbiddenClaims: string[]
  expectedDenial: boolean
  groundingRequired: boolean
}

export type ShopifyReadinessAuditDefinitionSummary = {
  suiteKey: string
  productRef: string
  displayName: string
  targetStore: string
  tierProfile: string
  artifactRoot: string
  decisionOptions: string[]
  rubric: string[]
  forbiddenInternalTerms: string[]
  checklist: ShopifyReadinessAuditChecklistItemSummary[]
  queryPack: ShopifyReadinessAuditQuerySummary[]
}

export type ShopifyReadinessAuditStateSummary = {
  definition: ShopifyReadinessAuditDefinitionSummary
  latestRun: PlatformVerificationSuiteRunSummary | null
  latestStage: PlatformVerificationSuiteStageRunSummary | null
  decision: string
  freshnessStatus: string
  evidenceCompletedAt: string | null
  blockers: string[]
  nextHandoff: string
  checklistResults: ShopifyReadinessAuditChecklistResultSummary[]
  answerResults: ShopifyReadinessAuditAnswerResultSummary[]
  evidenceArtifacts: ShopifyReadinessAuditEvidenceArtifactSummary[]
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

export type PlatformDeploymentOverrideSecretSummary = {
  name: string
  displayName: string
  secretPurpose: string
  deploymentId: string | null
  scopeType: string | null
  ownerType: string | null
  cleanupPolicy: string | null
  present: boolean
  bindingCount: number
  updatedAt: string | null
}

export type DeploymentProviderSecretBindingSummary = {
  id: string
  deploymentId: string
  secretPurpose: string
  displayName: string
  bindingMode: string
  secretName: string | null
  secondarySecretName: string | null
  effectiveResolution: DeploymentSecretResolutionSummary | null
  createdAt: string | null
  updatedAt: string | null
}

export type DeploymentProviderSecretBindingCatalogSummary = {
  deploymentId: string
  supportedPurposes: string[]
  availableOverrideSecrets: PlatformDeploymentOverrideSecretSummary[]
  bindings: DeploymentProviderSecretBindingSummary[]
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
  canManageUserDirectory: boolean
  canManageCustomers: boolean
  canCreateCustomers: boolean
  canManageSecrets: boolean
  canOperateDeployments: boolean
  customerId: string | null
  customerName: string | null
  customerSlug: string | null
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
  customerId: string | null
  customerName: string | null
  customerSlug: string | null
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
  cleanupReadinessStatus: string
  cleanupReadinessMessage: string
  latestSummary: string
}

export type PlatformTenantSharedVectorHandleSummary = {
  id: string
  resourceStatus: string
  vendor: string
  vectorStrategy: string
  vectorProvisioningMode: string
  vectorStoragePosture: string
  scopeType: string
  rootResourceLabel: string | null
  rootResourceValue: string | null
  scopePrefix: string | null
  tenantHandle: string | null
  scopePattern: string | null
  lifecycleOwner: string | null
  deploymentId: string | null
  deploymentVersionId: string | null
  deploymentReleaseId: string | null
  summaryStatus: string | null
  summaryMessage: string | null
  createdAt: string
  updatedAt: string
  cleanupEligible: boolean
}

export type PurgePlatformTenantSharedVectorHandlesRequest = {
  handleIds?: string[]
  purgeAllDetached?: boolean
  providerDeleteConfirmed?: boolean
  confirmationText?: string
  reason?: string
}

export type PurgePlatformTenantSharedVectorHandlesSummary = {
  tenantId: string
  tenantName: string
  purgedCount: number
  remainingHistoricalHandleCount: number
  status: string
  message: string
}

export type PlatformConsumerSummary = {
  consumerId: string
  customerId: string
  displayName: string
  description: string | null
  status: string
  boundDeploymentId: string | null
  boundDeploymentName: string | null
  boundDeploymentEnvironment: string | null
  boundDeploymentStatus: string | null
  lastBoundAt: string | null
  createdAt: string
  updatedAt: string
}

export type PlatformConsumerBindingHistorySummary = {
  consumerId: string
  customerId: string
  fromDeploymentId: string | null
  fromDeploymentName: string | null
  toDeploymentId: string | null
  toDeploymentName: string | null
  reason: string | null
  actorId: string
  actorRole: string
  createdAt: string
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
  consumerCount: number
  createdAt: string
  updatedAt: string
  tenants: PlatformTenantSummary[]
  consumers: PlatformConsumerSummary[]
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

export type PreviewDeploymentTenantMigrationRequest = {
  customerId?: string
  tenantId?: string
  proposedDeploymentName?: string
  proposedEnvironmentName?: string
}

export type CreateDeploymentTenantMigrationRequest = {
  customerId?: string
  tenantId?: string
  proposedDeploymentName?: string
  proposedEnvironmentName?: string
  reason: string
}

export type DeploymentTenantMigrationPreviewSummary = {
  sourceDeploymentId: string
  sourceDeploymentName: string
  sourceEnvironmentName: string
  sourceCustomerId: string
  sourceCustomerName: string
  sourceTenantId: string
  sourceTenantName: string
  targetCustomerId: string
  targetCustomerName: string
  targetTenantId: string | null
  targetTenantName: string | null
  autoCreatesTenant: boolean
  proposedDeploymentName: string
  proposedEnvironmentName: string
  publishedVersionCount: number
  releaseCount: number
  sourceConfigStrategy: string
  sharedVectorScopePresent: boolean
  sharedVectorStatus: string
  sharedVectorMessage: string
  rollbackPosture: string
  status: string
  message: string
}

export type DeploymentTenantMigrationExecutionSummary = {
  sourceDeploymentId: string
  deploymentId: string
  deploymentName: string
  environmentName: string
  customerId: string
  customerName: string
  tenantId: string
  tenantName: string
  autoCreatedTenant: boolean
  status: string
  message: string
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

export function getPlatformApiBaseUrl(): string {
  return apiBaseUrl
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

export function previewDeploymentTenantMigration(
  deploymentId: string,
  payload: PreviewDeploymentTenantMigrationRequest,
) {
  return request<DeploymentTenantMigrationPreviewSummary>(`/api/deployments/${deploymentId}/tenant-migration-preview`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function createDeploymentTenantMigration(
  deploymentId: string,
  payload: CreateDeploymentTenantMigrationRequest,
) {
  return request<DeploymentTenantMigrationExecutionSummary>(`/api/deployments/${deploymentId}/tenant-migrations`, {
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

export function deleteDeployment(deploymentId: string, payload?: DeleteDeploymentRequest) {
  return request<DeploymentDeletionOperationSummary>(`/api/deployments/${deploymentId}`, {
    method: 'DELETE',
    body: payload ? JSON.stringify(payload) : undefined,
  })
}

export function deleteDeploymentWithApproval(deploymentId: string, approvalId?: string) {
  return deleteDeployment(deploymentId, { approvalId })
}

export function fetchDeploymentDeletionNotifications(status?: string, limit = 100) {
  const params = new URLSearchParams()
  if (status && status.trim().length > 0) {
    params.set('status', status)
  }
  params.set('limit', String(limit))
  return request<DeploymentDeletionOperationSummary[]>(
    `/api/platform/notifications/deployment-deletions?${params.toString()}`,
  )
}

export function fetchDeploymentDeletionNotification(operationId: string) {
  return request<DeploymentDeletionOperationSummary>(
    `/api/platform/notifications/deployment-deletions/${encodeURIComponent(operationId)}`,
  )
}

export function retriggerRunningDeploymentDeletionOperation(operationId: string) {
  return request<DeploymentDeletionOperationSummary>(
    `/api/platform/notifications/deployment-deletions/${encodeURIComponent(operationId)}/retrigger-cleanup`,
    {
      method: 'POST',
    },
  )
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

export function fetchDeploymentWebhookOverview(deploymentId: string, limit = 50) {
  return request<DeploymentWebhookOverviewSummary>(
    `/api/deployments/${deploymentId}/webhooks/overview?limit=${encodeURIComponent(String(limit))}`,
  )
}

export function retryDeploymentWebhookDelivery(deploymentId: string, deliveryId: string) {
  return request<DeploymentWebhookRetrySummary>(
    `/api/deployments/${deploymentId}/webhooks/deliveries/${encodeURIComponent(deliveryId)}/retry`,
    {
      method: 'POST',
    },
  )
}

export function fetchMarketplacePlugins() {
  return request<MarketplacePluginSummary[]>('/api/marketplace/plugins')
}

export function fetchMarketplacePlugin(pluginId: string) {
  return request<MarketplacePluginDetailSummary>(`/api/marketplace/plugins/${encodeURIComponent(pluginId)}`)
}

export function fetchMarketplacePluginVersion(pluginId: string, version: string) {
  return request<MarketplacePluginVersionSummary>(
    `/api/marketplace/plugins/${encodeURIComponent(pluginId)}/versions/${encodeURIComponent(version)}`,
  )
}

export function fetchMarketplaceCategories() {
  return request<MarketplaceCategorySummary[]>('/api/marketplace/categories')
}

export function fetchMarketplacePublishers() {
  return request<MarketplacePublisherSummary[]>('/api/marketplace/publishers')
}

export function fetchMarketplacePublisher(publisherId: string) {
  return request<MarketplacePublisherDetailSummary>(`/api/marketplace/publishers/${encodeURIComponent(publisherId)}`)
}

export function fetchMarketplaceInferenceServices() {
  return request<PlatformManagedInferenceServiceSummary[]>('/api/marketplace/inference-services')
}

export function fetchMarketplaceInferenceService(serviceRef: string) {
  return request<PlatformManagedInferenceServiceSummary>(
    `/api/marketplace/inference-services/${encodeURIComponent(serviceRef)}`,
  )
}

export function fetchMarketplaceInferenceServiceDependents(serviceRef: string) {
  return request<PlatformManagedInferenceDependentDeploymentSummary[]>(
    `/api/marketplace/inference-services/${encodeURIComponent(serviceRef)}/dependents`,
  )
}

export function fetchMarketplaceInferenceServiceActivity(serviceRef: string) {
  return request<PlatformAuditEventSummary[]>(
    `/api/marketplace/inference-services/${encodeURIComponent(serviceRef)}/activity`,
  )
}

export function fetchMarketplaceInferenceServiceHealth(serviceRef: string) {
  return request<PlatformManagedInferenceHealthSummary>(
    `/api/marketplace/inference-services/${encodeURIComponent(serviceRef)}/health`,
  )
}

export function reconcileMarketplaceInferenceService(serviceRef: string) {
  return request<PlatformManagedInferenceServiceSummary>(
    `/api/marketplace/inference-services/${encodeURIComponent(serviceRef)}/reconcile`,
    {
      method: 'POST',
    },
  )
}

export function scaleMarketplaceInferenceService(
  serviceRef: string,
  payload: UpdatePlatformManagedInferenceServiceScaleRequest,
) {
  return request<PlatformManagedInferenceServiceSummary>(
    `/api/marketplace/inference-services/${encodeURIComponent(serviceRef)}/scale`,
    {
      method: 'PUT',
      body: JSON.stringify(payload),
    },
  )
}

export function restartMarketplaceInferenceService(serviceRef: string) {
  return request<PlatformManagedInferenceServiceSummary>(
    `/api/marketplace/inference-services/${encodeURIComponent(serviceRef)}/restart`,
    {
      method: 'POST',
    },
  )
}

export function forceRecreateMarketplaceInferenceService(serviceRef: string) {
  return request<PlatformManagedInferenceServiceSummary>(
    `/api/marketplace/inference-services/${encodeURIComponent(serviceRef)}/force-recreate`,
    {
      method: 'POST',
    },
  )
}

export function rotateMarketplaceInferenceServiceSecret(
  serviceRef: string,
  payload: RotatePlatformManagedInferenceServiceSecretRequest,
) {
  return request<PlatformManagedInferenceServiceSummary>(
    `/api/marketplace/inference-services/${encodeURIComponent(serviceRef)}/rotate-secret`,
    {
      method: 'PUT',
      body: JSON.stringify(payload),
    },
  )
}

export function fetchProductServices() {
  return request<PlatformManagedProductServiceSummary[]>('/api/product-services')
}

export function createProductService(payload: CreatePlatformManagedProductServiceRequest) {
  return request<PlatformManagedProductServiceSummary>('/api/product-services', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function fetchProductService(serviceRef: string) {
  return request<PlatformManagedProductServiceSummary>(`/api/product-services/${encodeURIComponent(serviceRef)}`)
}

export function fetchProductServiceDependents(serviceRef: string) {
  return request<ShopifyStoreConnectionSummary[]>(`/api/product-services/${encodeURIComponent(serviceRef)}/dependents`)
}

export function fetchProductServiceActivity(serviceRef: string) {
  return request<PlatformAuditEventSummary[]>(`/api/product-services/${encodeURIComponent(serviceRef)}/activity`)
}

export function fetchProductServiceHealth(serviceRef: string) {
  return request<PlatformManagedProductServiceHealthSummary>(`/api/product-services/${encodeURIComponent(serviceRef)}/health`)
}

export function fetchProductServiceOverview(serviceRef: string) {
  return request<PlatformManagedProductServiceOverviewSummary>(`/api/product-services/${encodeURIComponent(serviceRef)}/overview`)
}

export function fetchProductServiceWebhookSubscriptions(serviceRef: string, shopDomain: string) {
  return request<PlatformManagedProductServiceWebhookSubscriptionSummary>(
    `/api/product-services/${encodeURIComponent(serviceRef)}/stores/${encodeURIComponent(shopDomain)}/webhook-subscriptions`,
  )
}

export function fetchProductServiceStoreBillingSummary(serviceRef: string, shopDomain: string) {
  return request<PlatformManagedProductServiceStoreBillingSummary>(
    `/api/product-services/${encodeURIComponent(serviceRef)}/stores/${encodeURIComponent(shopDomain)}/billing-summary`,
  )
}

export function fetchProductServiceStoreBinding(serviceRef: string, shopDomain: string) {
  return request<ShopifyStoreBindingInspectionSummary>(
    `/api/product-services/${encodeURIComponent(serviceRef)}/stores/${encodeURIComponent(shopDomain)}/binding`,
  )
}

export function runProductServiceStoreSourcePreflight(serviceRef: string, shopDomain: string) {
  return request<ShopifyStoreConnectionSummary>(
    `/api/product-services/${encodeURIComponent(serviceRef)}/stores/${encodeURIComponent(shopDomain)}/run-source-preflight`,
    {
      method: 'POST',
    },
  )
}

export function fetchProductServiceDeploymentHistory(serviceRef: string, limit = 10) {
  return request<PlatformManagedProductServiceDeploymentHistorySummary>(
    `/api/product-services/${encodeURIComponent(serviceRef)}/railway/deployments?limit=${encodeURIComponent(String(limit))}`,
  )
}

export function fetchProductServiceRailwayLogs(options: {
  serviceRef: string
  source?: string
  deploymentId?: string
  limit?: number
  filter?: string
}) {
  const params = new URLSearchParams()
  if (options.source) params.set('source', options.source)
  if (options.deploymentId) params.set('deploymentId', options.deploymentId)
  if (options.limit != null) params.set('limit', String(options.limit))
  if (options.filter) params.set('filter', options.filter)
  const suffix = params.toString() ? `?${params.toString()}` : ''
  return request<PlatformManagedProductServiceRailwayLogsSummary>(
    `/api/product-services/${encodeURIComponent(options.serviceRef)}/railway/logs${suffix}`,
  )
}

export function reconcileProductService(serviceRef: string) {
  return request<PlatformManagedProductServiceSummary>(`/api/product-services/${encodeURIComponent(serviceRef)}/reconcile`, {
    method: 'POST',
  })
}

export function scaleProductService(serviceRef: string, payload: UpdatePlatformManagedProductServiceScaleRequest) {
  return request<PlatformManagedProductServiceSummary>(`/api/product-services/${encodeURIComponent(serviceRef)}/scale`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function restartProductService(serviceRef: string) {
  return request<PlatformManagedProductServiceSummary>(`/api/product-services/${encodeURIComponent(serviceRef)}/restart`, {
    method: 'POST',
  })
}

export function forceRecreateProductService(serviceRef: string) {
  return request<PlatformManagedProductServiceSummary>(`/api/product-services/${encodeURIComponent(serviceRef)}/force-recreate`, {
    method: 'POST',
  })
}

export function decommissionProductService(serviceRef: string) {
  return request<PlatformManagedProductServiceSummary>(`/api/product-services/${encodeURIComponent(serviceRef)}/decommission`, {
    method: 'POST',
  })
}

export function rotateProductServiceSecret(
  serviceRef: string,
  payload: RotatePlatformManagedProductServiceSecretRequest,
) {
  return request<PlatformManagedProductServiceSummary>(`/api/product-services/${encodeURIComponent(serviceRef)}/rotate-secret`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function fetchShopifyStores() {
  return request<ShopifyStoreConnectionSummary[]>('/api/shopify/stores')
}

export function fetchShopifyStore(shopDomain: string) {
  return request<ShopifyStoreConnectionSummary>(`/api/shopify/stores/${encodeURIComponent(shopDomain)}`)
}

export function fetchMerchantPartnerAccessRequests(shopDomain: string) {
  return request<MerchantPartnerAccessRequestSummary[]>(
    `/api/merchant/partner-access/requests?shopDomain=${encodeURIComponent(shopDomain)}`,
  )
}

export function revokeMerchantPartnerAccessRequest(
  shopDomain: string,
  requestId: string,
  payload: MerchantPartnerAccessDecisionRequest,
) {
  return request<MerchantPartnerAccessDecisionSummary>(
    `/api/merchant/partner-access/requests/${encodeURIComponent(requestId)}/revoke?shopDomain=${encodeURIComponent(shopDomain)}`,
    {
      method: 'POST',
      body: JSON.stringify(payload),
    },
  )
}

export function fetchShopifyStoreBinding(shopDomain: string) {
  return request<ShopifyStoreBindingInspectionSummary>(`/api/shopify/stores/${encodeURIComponent(shopDomain)}/binding`)
}

export function deleteShopifyStore(shopDomain: string, force = false) {
  return request<void>(`/api/shopify/stores/${encodeURIComponent(shopDomain)}?force=${force ? 'true' : 'false'}`, {
    method: 'DELETE',
  })
}

export function upsertShopifyStore(payload: UpsertShopifyStoreConnectionRequest) {
  return request<ShopifyStoreConnectionSummary>('/api/shopify/stores', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function bootstrapShopifyStore(shopDomain: string, payload: BootstrapShopifyStoreRequest = {}) {
  return request<ShopifyStoreBootstrapSummary>(`/api/shopify/stores/${encodeURIComponent(shopDomain)}/bootstrap`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function fetchShopifyStoreProvisioning(shopDomain: string) {
  return request<ShopifyStoreProvisioningStatusSummary>(`/api/shopify/stores/${encodeURIComponent(shopDomain)}/provisioning`)
}

export function fetchShopifyPackageProfiles(activeOnly = false) {
  return request<ShopifyCompanionPackageProfileSummary[]>(`/api/shopify/package-profiles?activeOnly=${activeOnly}`)
}

export function fetchShopifyPackageProfileOptions() {
  return request<ShopifyCompanionPackageProfileOptionsSummary>('/api/shopify/package-profiles/options')
}

export function fetchShopifyPackageProfile(profileKey: string) {
  return request<ShopifyCompanionPackageProfileSummary>(`/api/shopify/package-profiles/${encodeURIComponent(profileKey)}`)
}

export function upsertShopifyPackageProfile(profileKey: string, payload: UpsertShopifyCompanionPackageProfileRequest) {
  return request<ShopifyCompanionPackageProfileSummary>(`/api/shopify/package-profiles/${encodeURIComponent(profileKey)}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function updateShopifyPackageProfileStatus(
  profileKey: string,
  payload: UpdateShopifyCompanionPackageProfileStatusRequest,
) {
  return request<ShopifyCompanionPackageProfileSummary>(`/api/shopify/package-profiles/${encodeURIComponent(profileKey)}/status`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function enqueueShopifyStoreProvisioning(shopDomain: string, payload: CreateShopifyStoreProvisioningJobRequest = {}) {
  return request<ShopifyStoreProvisioningJobSummary>(`/api/shopify/stores/${encodeURIComponent(shopDomain)}/provisioning-jobs`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function goLiveShopifyStore(shopDomain: string) {
  return request<ShopifyStoreConnectionSummary>(`/api/shopify/stores/${encodeURIComponent(shopDomain)}/go-live`, {
    method: 'POST',
  })
}

export function recordShopifyStoreSourcePreflight(shopDomain: string, payload: RecordShopifyStoreSourcePreflightRequest) {
  return request<ShopifyStoreConnectionSummary>(`/api/shopify/stores/${encodeURIComponent(shopDomain)}/source-preflight`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function fetchShopifyStoreVectorization(shopDomain: string) {
  return request<ShopifyStoreVectorizationSummary>(`/api/shopify/stores/${encodeURIComponent(shopDomain)}/vectorization`)
}

export function fetchShopifyStoreGovernedActions(shopDomain: string, limit = 10) {
  return request<ShopifyStoreGovernedActionAuditSummary[]>(
    `/api/shopify/stores/${encodeURIComponent(shopDomain)}/actions/recent?limit=${limit}`,
  )
}

export function reconcileShopifyStoreVectorization(shopDomain: string) {
  return request<ShopifyStoreVectorizationSummary>(`/api/shopify/stores/${encodeURIComponent(shopDomain)}/vectorization/reconcile`, {
    method: 'POST',
  })
}

export function indexAllShopifyStoreVectorization(shopDomain: string) {
  return request<ShopifyStoreVectorizationSummary>(`/api/shopify/stores/${encodeURIComponent(shopDomain)}/vectorization/index-all`, {
    method: 'POST',
  })
}

export function reindexAllShopifyStoreVectorization(shopDomain: string) {
  return request<ShopifyStoreVectorizationSummary>(`/api/shopify/stores/${encodeURIComponent(shopDomain)}/vectorization/reindex-all`, {
    method: 'POST',
  })
}

export function reindexSelectedShopifyStoreVectorization(
  shopDomain: string,
  payload: ShopifyStoreVectorizationSelectedEntitiesRequest,
) {
  return request<ShopifyStoreVectorizationSummary>(
    `/api/shopify/stores/${encodeURIComponent(shopDomain)}/vectorization/reindex-selected`,
    {
      method: 'POST',
      body: JSON.stringify(payload),
    },
  )
}

export function updateShopifyStoreVectorizationPolicy(
  shopDomain: string,
  payload: UpdateShopifyStoreVectorizationPolicyRequest,
) {
  return request<ShopifyStoreVectorizationSummary>(`/api/shopify/stores/${encodeURIComponent(shopDomain)}/vectorization/policy`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function fetchShopifyStoreVectorizationEvents(shopDomain: string, limit = 20) {
  return request<ShopifyStoreVectorizationEventSummary[]>(
    `/api/shopify/stores/${encodeURIComponent(shopDomain)}/vectorization/events?limit=${limit}`,
  )
}

export function replayShopifyStoreVectorizationEvent(shopDomain: string, eventId: string) {
  return request<ShopifyStoreVectorizationSummary>(
    `/api/shopify/stores/${encodeURIComponent(shopDomain)}/vectorization/events/${encodeURIComponent(eventId)}/replay`,
    {
      method: 'POST',
    },
  )
}

export function retryLastFailedShopifyStoreVectorizationAutoRun(shopDomain: string) {
  return request<ShopifyStoreVectorizationSummary>(
    `/api/shopify/stores/${encodeURIComponent(shopDomain)}/vectorization/retry-last-failed-auto-run`,
    {
      method: 'POST',
    },
  )
}

export function createMarketplacePublisher(payload: CreateMarketplacePublisherRequest) {
  return request<MarketplacePublisherSummary>('/api/marketplace/publishers', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateMarketplacePublisherVerification(
  publisherId: string,
  payload: UpdateMarketplacePublisherVerificationRequest,
) {
  return request<MarketplacePublisherSummary>(`/api/marketplace/publishers/${encodeURIComponent(publisherId)}/verification`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function createMarketplacePublisherSubmission(
  publisherId: string,
  payload: CreateMarketplacePublisherSubmissionRequest,
) {
  return request<MarketplacePublisherSubmissionSummary>(
    `/api/marketplace/publishers/${encodeURIComponent(publisherId)}/submissions`,
    {
      method: 'POST',
      body: JSON.stringify(payload),
    },
  )
}

export function validateMarketplaceSubmission(
  pluginVersionId: string,
  payload: ReviewMarketplacePublisherSubmissionRequest,
) {
  return request<MarketplacePublisherSubmissionSummary>(
    `/api/marketplace/submissions/${encodeURIComponent(pluginVersionId)}/validate`,
    {
      method: 'POST',
      body: JSON.stringify(payload),
    },
  )
}

export function publishMarketplaceSubmission(
  pluginVersionId: string,
  payload: ReviewMarketplacePublisherSubmissionRequest,
) {
  return request<MarketplacePublisherSubmissionSummary>(
    `/api/marketplace/submissions/${encodeURIComponent(pluginVersionId)}/publish`,
    {
      method: 'POST',
      body: JSON.stringify(payload),
    },
  )
}

export function rejectMarketplaceSubmission(
  pluginVersionId: string,
  payload: ReviewMarketplacePublisherSubmissionRequest,
) {
  return request<MarketplacePublisherSubmissionSummary>(
    `/api/marketplace/submissions/${encodeURIComponent(pluginVersionId)}/reject`,
    {
      method: 'POST',
      body: JSON.stringify(payload),
    },
  )
}

export function bootstrapMarketplaceTemplatePlugin(
  pluginId: string,
  payload: CreateMarketplaceTemplateBootstrapRequest,
) {
  return request<DeploymentSummary>(`/api/marketplace/templates/${encodeURIComponent(pluginId)}/bootstrap`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function fetchDeploymentMarketplaceInstalls(deploymentId: string) {
  return request<DeploymentMarketplaceInstallSummary[]>(
    `/api/deployments/${encodeURIComponent(deploymentId)}/marketplace-installs`,
  )
}

export function fetchDeploymentMarketplaceImpact(deploymentId: string) {
  return request<DeploymentMarketplaceImpactSummary>(
    `/api/deployments/${encodeURIComponent(deploymentId)}/marketplace-impact`,
  )
}

export function createDeploymentMarketplaceInstall(
  deploymentId: string,
  payload: CreateDeploymentMarketplaceInstallRequest,
) {
  return request<DeploymentMarketplaceInstallSummary>(
    `/api/deployments/${encodeURIComponent(deploymentId)}/marketplace-installs`,
    {
      method: 'POST',
      body: JSON.stringify(payload),
    },
  )
}

export function updateDeploymentMarketplaceInstall(
  deploymentId: string,
  installId: string,
  payload: UpdateDeploymentMarketplaceInstallRequest,
) {
  return request<DeploymentMarketplaceInstallSummary>(
    `/api/deployments/${encodeURIComponent(deploymentId)}/marketplace-installs/${encodeURIComponent(installId)}`,
    {
      method: 'PUT',
      body: JSON.stringify(payload),
    },
  )
}

export function deleteDeploymentMarketplaceInstall(deploymentId: string, installId: string) {
  return request<void>(
    `/api/deployments/${encodeURIComponent(deploymentId)}/marketplace-installs/${encodeURIComponent(installId)}`,
    {
      method: 'DELETE',
    },
  )
}

export function updateDeploymentMarketplaceInstallEntitlement(
  deploymentId: string,
  installId: string,
  payload: UpdateDeploymentMarketplaceEntitlementRequest,
) {
  return request<DeploymentMarketplaceInstallSummary>(
    `/api/deployments/${encodeURIComponent(deploymentId)}/marketplace-installs/${encodeURIComponent(installId)}/entitlement`,
    {
      method: 'PUT',
      body: JSON.stringify(payload),
    },
  )
}

export function resolveDeploymentMarketplaceInstall(deploymentId: string, installId: string) {
  return request<DeploymentMarketplaceInstallResolutionSummary>(
    `/api/deployments/${encodeURIComponent(deploymentId)}/marketplace-installs/${encodeURIComponent(installId)}/resolve`,
    {
      method: 'POST',
    },
  )
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

export function fetchDeploymentIntegrationSummary(deploymentId: string) {
  return request<DeploymentIntegrationSummary>(`/api/deployments/${deploymentId}/integration-summary`)
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

export function fetchPlatformDeploymentOverrideSecrets() {
  return request<PlatformDeploymentOverrideSecretSummary[]>('/api/platform/secrets/deployment-overrides')
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

export function fetchPlatformConsumers(customerId: string) {
  return request<PlatformConsumerSummary[]>(`/api/platform/customers/${customerId}/consumers`)
}

export function createPlatformConsumer(customerId: string, payload: {
  consumerId: string
  displayName: string
  description?: string
  deploymentId?: string
  bindingReason?: string
}) {
  return request<PlatformConsumerSummary>(`/api/platform/customers/${customerId}/consumers`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updatePlatformConsumer(customerId: string, consumerId: string, payload: {
  displayName: string
  description?: string
  status: string
}) {
  return request<PlatformConsumerSummary>(`/api/platform/customers/${customerId}/consumers/${encodeURIComponent(consumerId)}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function updatePlatformConsumerBinding(customerId: string, consumerId: string, payload: {
  deploymentId?: string
  reason?: string
}) {
  return request<PlatformConsumerSummary>(
    `/api/platform/customers/${customerId}/consumers/${encodeURIComponent(consumerId)}/binding`,
    {
      method: 'PUT',
      body: JSON.stringify(payload),
    },
  )
}

export function deletePlatformConsumer(customerId: string, consumerId: string) {
  return request<void>(`/api/platform/customers/${customerId}/consumers/${encodeURIComponent(consumerId)}`, {
    method: 'DELETE',
  })
}

export function fetchPlatformConsumerBindingHistory(customerId: string, consumerId: string) {
  return request<PlatformConsumerBindingHistorySummary[]>(
    `/api/platform/customers/${customerId}/consumers/${encodeURIComponent(consumerId)}/history`,
  )
}

export function fetchPlatformTenantSharedVectorHandles(tenantId: string) {
  return request<PlatformTenantSharedVectorHandleSummary[]>(
    `/api/platform/customers/tenants/${tenantId}/shared-vector-handles`,
  )
}

export function purgePlatformTenantSharedVectorHandles(
  tenantId: string,
  payload: PurgePlatformTenantSharedVectorHandlesRequest,
) {
  return request<PurgePlatformTenantSharedVectorHandlesSummary>(
    `/api/platform/customers/tenants/${tenantId}/shared-vector-handles/purge`,
    {
      method: 'POST',
      body: JSON.stringify(payload),
    },
  )
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
  customerId?: string
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
  customerId?: string
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

export function recreateDeploymentVerificationRollouts(rolloutKeys?: string[]) {
  return request<DeploymentVerificationRolloutSummary>('/api/deployments/verification-rollouts/recreate', {
    method: 'POST',
    ...(rolloutKeys && rolloutKeys.length > 0
      ? { body: JSON.stringify({ rolloutKeys }) }
      : {}),
  })
}

export function fetchPlatformVerificationSuiteDefinitions() {
  return request<PlatformVerificationSuiteDefinitionSummary[]>('/api/verification-suites')
}

export function fetchPlatformVerificationSuiteRuns() {
  return request<PlatformVerificationSuiteRunSummary[]>('/api/verification-suites/runs')
}

export function fetchPlatformVerificationSuiteRun(runId: string) {
  return request<PlatformVerificationSuiteRunSummary>(`/api/verification-suites/runs/${runId}`)
}

export function fetchPlatformVerificationReleaseGate() {
  return request<PlatformVerificationReleaseGateSummary>('/api/verification-suites/release-gate')
}

export function fetchShopifyReadinessAuditState() {
  return request<ShopifyReadinessAuditStateSummary>('/api/shopify/readiness-audit/latest')
}

export function dispatchPlatformVerificationSuiteRun(
  suiteKey: string,
  payload?: {
    allowControlPlaneRepair?: boolean
  },
) {
  return request<PlatformVerificationSuiteDispatchSummary>(`/api/verification-suites/${suiteKey}/runs`, {
    method: 'POST',
    body: JSON.stringify({
      allowControlPlaneRepair: payload?.allowControlPlaneRepair ?? false,
    }),
  })
}

export function cleanupDeploymentVerificationRollouts(rolloutKeys?: string[]) {
  return request<DeploymentVerificationRolloutSummary>('/api/deployments/verification-rollouts/cleanup', {
    method: 'POST',
    ...(rolloutKeys && rolloutKeys.length > 0
      ? { body: JSON.stringify({ rolloutKeys }) }
      : {}),
  })
}

export function hardResetDeploymentVerificationRollouts(rolloutKeys?: string[]) {
  return request<DeploymentVerificationRolloutSummary>('/api/deployments/verification-rollouts/hard-reset', {
    method: 'POST',
    ...(rolloutKeys && rolloutKeys.length > 0
      ? { body: JSON.stringify({ rolloutKeys }) }
      : {}),
  })
}

export function rolloutEcommerceDemoDeployment() {
  return request<DeploymentOverviewSummary>('/api/deployments/ecommerce-demo/rollout', {
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

export function fetchDeploymentPocRuntimeAuthContext(deploymentId: string, authPath?: DeploymentPocAuthPath) {
  const suffix = authPath ? `?authPath=${encodeURIComponent(authPath)}` : ''
  return request<DeploymentPocRuntimeAuthContextSummary>(
    `/api/deployments/${deploymentId}/poc-chat/auth-context${suffix}`,
  )
}

export function fetchDeploymentPocConversation(
  deploymentId: string,
  conversationId: string,
  authPath?: DeploymentPocAuthPath,
) {
  const suffix = authPath ? `?authPath=${encodeURIComponent(authPath)}` : ''
  return request<DeploymentPocConversationResponse>(
    `/api/deployments/${deploymentId}/poc-chat/conversations/${encodeURIComponent(conversationId)}${suffix}`,
  )
}

export function deleteDeploymentPocConversation(
  deploymentId: string,
  conversationId: string,
  authPath?: DeploymentPocAuthPath,
) {
  const suffix = authPath ? `?authPath=${encodeURIComponent(authPath)}` : ''
  return request<void>(
    `/api/deployments/${deploymentId}/poc-chat/conversations/${encodeURIComponent(conversationId)}${suffix}`,
    {
      method: 'DELETE',
    },
  )
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

export function upsertPlatformDeploymentOverrideSecret(
  name: string,
  payload: {
    secretPurpose: string
    value: string
    deploymentId?: string
    cleanupPolicy?: string
  },
) {
  return request<PlatformDeploymentOverrideSecretSummary>(`/api/platform/secrets/deployment-overrides/${name}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function clearPlatformSecret(name: string) {
  return request<PlatformSecretSummary>(`/api/platform/secrets/${name}`, {
    method: 'DELETE',
  })
}

export function clearPlatformDeploymentOverrideSecret(name: string) {
  return request<PlatformDeploymentOverrideSecretSummary>(`/api/platform/secrets/deployment-overrides/${name}`, {
    method: 'DELETE',
  })
}

export function fetchDeploymentProviderSecretBindings(deploymentId: string) {
  return request<DeploymentProviderSecretBindingCatalogSummary>(
    `/api/deployments/${deploymentId}/provider-secret-bindings`,
  )
}

export function upsertDeploymentProviderSecretBinding(
  deploymentId: string,
  payload: {
    secretPurpose: string
    bindingMode: string
    secretName?: string
    secondarySecretName?: string
  },
) {
  return request<DeploymentProviderSecretBindingSummary>(
    `/api/deployments/${deploymentId}/provider-secret-bindings`,
    {
      method: 'PUT',
      body: JSON.stringify(payload),
    },
  )
}

export function clearDeploymentProviderSecretBinding(deploymentId: string, secretPurpose: string) {
  return request<void>(`/api/deployments/${deploymentId}/provider-secret-bindings/${encodeURIComponent(secretPurpose)}`, {
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

export type VectorizationSourceConnectionSummary = {
  id: string
  deploymentId: string
  name: string
  adapterType: string
  authMode: string
  status: string
  connectionConfig: unknown
  secretReferences: unknown
  discoverySummary: unknown
  createdAt: string
  updatedAt: string
}

export type VectorizationPlanRevisionSummary = {
  id: string
  revisionNumber: number
  status: string
  sourceConnectionId: string | null
  entityScope: unknown
  mappingConfig: unknown
  executionConfig: unknown
  indexedOutputHash: string | null
  createdAt: string
  updatedAt: string
}

export type VectorizationPlanSummary = {
  id: string
  deploymentId: string
  name: string
  status: string
  runnerMode: string
  syncState: string
  syncReasonCodes: string[]
  syncReasonDetails: unknown
  activeIndexedOutputHash: string | null
  lastSuccessfulIndexedOutputHash: string | null
  activeRevisionId: string | null
  sourceConnectionId: string | null
  lastRunId: string | null
  lastSuccessfulRunId: string | null
  manuallyConfirmedAt: string | null
  deferredReindexAt: string | null
  activeRevision: VectorizationPlanRevisionSummary | null
  createdAt: string
  updatedAt: string
}

export type VectorizationRunSummary = {
  id: string
  reason: string
  requestedStatus: string
  status: string
  runnerMode: string
  entityScope: string[]
  progressSummary: unknown
  checkpointSummary: unknown
  errorSummary: unknown
  claimedByRegistrationId: string | null
  claimedBySessionId: string | null
  runnerInstanceId: string | null
  productVersion: string | null
  compatibilityVersion: string | null
  leaseExpiresAt: string | null
  createdAt: string
  startedAt: string | null
  completedAt: string | null
  updatedAt: string
}

export type VectorizationCheckpointSummary = {
  id: string
  entityType: string | null
  checkpointType: string
  checkpointValue: string | null
  progress: unknown
  details: unknown
  createdAt: string
  updatedAt: string
}

export type VectorizationFailureBucketSummary = {
  id: string
  entityType: string | null
  errorCode: string
  summary: string
  sample: unknown
  occurrences: number
  createdAt: string
  updatedAt: string
}

export type VectorizationRunDetailsSummary = {
  deploymentId: string
  run: VectorizationRunSummary
  checkpoints: VectorizationCheckpointSummary[]
  failureBuckets: VectorizationFailureBucketSummary[]
}

export type VectorizationVerificationRunSummary = {
  id: string
  verificationType: string
  executionMode: string
  status: string
  entityScope: string[]
  summary: unknown
  linkedVectorizationRunId: string | null
  createdAt: string
  startedAt: string | null
  completedAt: string | null
  updatedAt: string
}

export type VectorizationVerificationStepSummary = {
  id: string
  stepKey: string
  stepName: string
  status: string
  details: unknown
  createdAt: string
  updatedAt: string
}

export type VectorizationVerificationRunDetailsSummary = {
  deploymentId: string
  verificationRun: VectorizationVerificationRunSummary
  steps: VectorizationVerificationStepSummary[]
  linkedVectorizationRun: VectorizationRunSummary | null
}

export type VectorizationRunnerSummary = {
  registrationId: string
  runnerMode: string
  registrationStatus: string
  compatibilityStatus: string
  tokenHint: string | null
  tokenExpiresAt: string | null
  runnerInstanceId: string | null
  productVersion: string | null
  compatibilityVersion: string | null
  lastConnectedAt: string | null
  lastSessionHeartbeatAt: string | null
  lastSessionExpiresAt: string | null
}

export type VectorizationOverviewSummary = {
  deploymentId: string
  customerId: string
  tenantId: string
  activeVersionId: string | null
  activeVersionLabel: string | null
  activeConfigHash: string | null
  sourceConnection: VectorizationSourceConnectionSummary | null
  plan: VectorizationPlanSummary | null
  runner: VectorizationRunnerSummary | null
  recentRuns: VectorizationRunSummary[]
}

export type VectorizationPreviewSummary = {
  deploymentId: string
  syncState: string
  sourceDiscovery: unknown
  entityScope: unknown
  mappingConfig: unknown
  executionConfig: unknown
  reindexOptions: unknown
  indexedOutputHash: string | null
}

export type VectorizationRunnerTokenSummary = {
  registrationId: string
  runnerMode: string
  registrationToken: string
  tokenHint: string
  expiresAt: string
}

export function fetchVectorizationOverview(deploymentId: string) {
  return request<VectorizationOverviewSummary>(`/api/deployments/${deploymentId}/vectorization`)
}

export function fetchVectorizationPreview(deploymentId: string) {
  return request<VectorizationPreviewSummary>(`/api/deployments/${deploymentId}/vectorization/preview`)
}

export function upsertVectorizationConnection(
  deploymentId: string,
  payload: {
    name: string
    adapterType: string
    authMode: string
    connectionConfig: unknown
    secretReferences?: unknown
    discoverySummary?: unknown
  },
) {
  return request<VectorizationSourceConnectionSummary>(`/api/deployments/${deploymentId}/vectorization/connection`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function upsertVectorizationPlan(
  deploymentId: string,
  payload: {
    name: string
    runnerMode: string
    sourceConnectionId?: string | null
    entityScope?: unknown
    mappingConfig?: unknown
    executionConfig?: unknown
  },
) {
  return request<VectorizationPlanSummary>(`/api/deployments/${deploymentId}/vectorization/plan`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function createVectorizationRun(
  deploymentId: string,
  payload: {
    reason: string
    entityTypes?: string[]
    note?: string
    executionOverrides?: unknown
  },
) {
  return request<VectorizationRunSummary>(`/api/deployments/${deploymentId}/vectorization/runs`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function fetchVectorizationRunDetails(deploymentId: string, runId: string) {
  return request<VectorizationRunDetailsSummary>(`/api/deployments/${deploymentId}/vectorization/runs/${encodeURIComponent(runId)}`)
}

export function fetchVectorizationVerificationRuns(deploymentId: string) {
  return request<VectorizationVerificationRunSummary[]>(`/api/deployments/${deploymentId}/vectorization/verifications`)
}

export function createVectorizationVerificationRun(
  deploymentId: string,
  payload: {
    verificationType: string
    entityTypes?: string[]
    note?: string
    counterpartDeploymentId?: string
  },
) {
  return request<VectorizationVerificationRunSummary>(`/api/deployments/${deploymentId}/vectorization/verifications`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function fetchVectorizationVerificationRunDetails(deploymentId: string, verificationRunId: string) {
  return request<VectorizationVerificationRunDetailsSummary>(
    `/api/deployments/${deploymentId}/vectorization/verifications/${encodeURIComponent(verificationRunId)}`,
  )
}

export function pauseVectorizationRun(deploymentId: string, runId: string) {
  return request<VectorizationRunSummary>(`/api/deployments/${deploymentId}/vectorization/runs/${runId}/pause`, {
    method: 'POST',
  })
}

export function resumeVectorizationRun(deploymentId: string, runId: string) {
  return request<VectorizationRunSummary>(`/api/deployments/${deploymentId}/vectorization/runs/${runId}/resume`, {
    method: 'POST',
  })
}

export function cancelVectorizationRun(deploymentId: string, runId: string) {
  return request<VectorizationRunSummary>(`/api/deployments/${deploymentId}/vectorization/runs/${runId}/cancel`, {
    method: 'POST',
  })
}

export function retryVectorizationRun(deploymentId: string, runId: string) {
  return request<VectorizationRunSummary>(`/api/deployments/${deploymentId}/vectorization/runs/${runId}/retry`, {
    method: 'POST',
  })
}

export function updateVectorizationSyncState(
  deploymentId: string,
  payload: {
    action: string
    reason?: string
  },
) {
  return request<VectorizationPlanSummary>(`/api/deployments/${deploymentId}/vectorization/sync-state`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function rotateVectorizationRunnerToken(
  deploymentId: string,
  payload?: {
    validityHours?: number
    runnerMode?: string
  },
) {
  return request<VectorizationRunnerTokenSummary>(`/api/deployments/${deploymentId}/vectorization/runner/token`, {
    method: 'POST',
    body: JSON.stringify(payload ?? {}),
  })
}
